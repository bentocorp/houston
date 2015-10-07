package org.bentocorp.aws

import javax.annotation.PostConstruct

import com.amazonaws.{AmazonClientException, AmazonServiceException}
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.regions.{Regions, Region}
import com.amazonaws.services.sqs.model.{Message, ReceiveMessageRequest}
import com.amazonaws.services.sqs.{AmazonSQSClient, AmazonSQS}
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule

import org.bentocorp.aws
import org.bentocorp.dispatch.{OrderManager, Address}
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.AutoConfigureBefore
import org.springframework.stereotype.Component
import scala.collection.JavaConversions._

@Component
class SQS {

  final val VISIBILITY_TIMEOUT = 30 // seconds

  @Autowired
  val orderManager: OrderManager = null

  val mapper = new ObjectMapper().registerModule(DefaultScalaModule)

  //@PostConstruct
  def start() {
    try {
      val credentials: AWSCredentials = new ProfileCredentialsProvider("prod").getCredentials
      val sqs: AmazonSQS = new AmazonSQSClient(credentials)
      val usWest2: Region = Region.getRegion(Regions.US_WEST_2)
      sqs.setRegion(usWest2)
      val url = "https://sqs.us-west-2.amazonaws.com/457902237154/bento-prod-orders"
      //while (true) {
      val request = new ReceiveMessageRequest(url)
      request.setMaxNumberOfMessages(1)
      request.setVisibilityTimeout(VISIBILITY_TIMEOUT)
      request.setWaitTimeSeconds(20)
      val messages: java.util.List[Message] = sqs.receiveMessage(request).getMessages
      for (m: Message <- messages) {
        println("received a message!")
        val body = mapper.readValue(m.getBody, classOf[MessageBody])
        val awsOrder = mapper.readValue(body.data, classOf[Order])
        val order = org.bentocorp.Order.parse(awsOrder)
        println(body.data)
        //order.item = orderManager.stringifyItems(awsOrder.items)
      }
      //}
    } catch {
      case e: Exception => e.printStackTrace()
    }
  }
}
