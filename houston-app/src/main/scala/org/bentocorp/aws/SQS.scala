package org.bentocorp.aws

import com.amazonaws.AbortedException
import com.amazonaws.auth.{BasicAWSCredentials, AWSCredentials}
import com.amazonaws.regions.{Regions, Region}
import com.amazonaws.services.sqs.model.{DeleteMessageRequest, Message, ReceiveMessageRequest}
import com.amazonaws.services.sqs.{AmazonSQSClient, AmazonSQS}
import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.bentocorp.api.APIResponse
import org.bentocorp.api.ws.OrderAction
import org.bentocorp._
import org.bentocorp.controllers.HttpController
import org.bentocorp.dispatch.OrderManager
import org.bentocorp.houston.config.BentoConfig
import org.bentocorp.houston.util.HttpUtils
import org.slf4j.LoggerFactory
import scala.collection.JavaConversions._

object SQS {

  final val Logger = LoggerFactory.getLogger(SQS.getClass)

  @volatile var service: SQS = null

  def start(controller: HttpController) {
    if (service != null) {
      throw new Exception("Error in execution flow! Trying to Amazon SQS Service when not null")
    }
    service = new SQS(controller)
    service.start()
  }

  def stop() {
    service.safeStop()
    service = null
  }
}

class SQS(controller: HttpController) extends Thread {

  final val Logger = LoggerFactory.getLogger(classOf[SQS])

  final val VISIBILITY_TIMEOUT = 30 // seconds

  val orderManager: OrderManager = controller.orderManager

  val mapper = new ObjectMapper().registerModule(DefaultScalaModule)

  var sqs: AmazonSQS = null
  var request: ReceiveMessageRequest = null

  var config: BentoConfig = controller.config
  val url = config.getString("aws.sqs.url")
    val credentials: AWSCredentials = new BasicAWSCredentials(
      config.getString("aws.id"), config.getString("aws.secret"))
    sqs = new AmazonSQSClient(credentials)
    val usWest2: Region = Region.getRegion(Regions.US_WEST_2)
    sqs.setRegion(usWest2)

    request = new ReceiveMessageRequest(url)
    request.setMaxNumberOfMessages(1)
    request.setVisibilityTimeout(VISIBILITY_TIMEOUT)
    request.setWaitTimeSeconds(20)


  def processOrder(): Int = {
    try {
      val messages: java.util.List[Message] = sqs.receiveMessage(request).getMessages
      if (Thread.interrupted()) {
        return 0
      }
      for (m: Message <- messages) {
        val body = mapper.readValue(m.getBody, classOf[MessageBody])
        val awsOrder = mapper.readValue(body.data, classOf[Order])
        Logger.debug(body.data)
        val order = org.bentocorp.Order.parse(awsOrder)

        Logger.debug("Got " + order.id)

        if (!orderManager.orders.contains(order.getOrderKey)) {
          orderManager.orders += (order.getOrderKey -> order)
          val p = OrderAction.make(OrderAction.Type.CREATE, order, -1L, null).from("houston").toGroup("atlas")
          val str = HttpUtils.get(
            config.getString("node.url") + "/api/push",
            Map("rid" -> p.rid, "from" -> p.from, "to" -> p.to, "subject" -> p.subject,
            "body" -> ScalaJson.stringify(p.body), "token" -> token))
          val res: APIResponse[String] = ScalaJson.parse(str, new TypeReference[APIResponse[String]]() { })
          if (res.code != 0) {
            Logger.error("Error sending SQS order to atlas group - " + res.msg)
          }
        }

        val messageReceiptHandle = m.getReceiptHandle
        sqs.deleteMessage(new DeleteMessageRequest()
          .withQueueUrl(url)
          .withReceiptHandle(messageReceiptHandle))
        // TODO - if send not successful do not delete message from SQS!!
      }
      messages.size()
    } catch {
      case abortedException: AbortedException =>
        // Thrown when an API is invoked while Thread.interrupt is set
        Logger.error("Interrupted!")
        0
      case e: Exception =>
        Logger.error(e.getMessage, e)
        0
    }
  }

  val token = controller.token

  @volatile var continue = false

  override def run() {
    Logger.info("Starting Amazon SQS Service")
    continue = true
    while (continue) {
      Logger.debug("Fetching data from queue")
      // May block for up to 20 seconds
      processOrder()
    }
    Logger.info("Amazon SQS Service stopped")
  }

  def safeStop() {
    Logger.info("Stopping Amazon SQS service (may take up to 20 seconds)")
    continue = false
  }
}
