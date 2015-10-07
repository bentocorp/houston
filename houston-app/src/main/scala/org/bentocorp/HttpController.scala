package org.bentocorp

import java.net.URLEncoder
import javax.annotation.PostConstruct

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.github.nkzawa.emitter.Emitter.Listener
import com.github.nkzawa.socketio.client.{Ack, Socket, IO}
import org.bentocorp.Preamble.Http
import org.bentocorp.api.{Track, Authenticate, APIResponse}
import org.bentocorp.api.ws.{Stat, OrderStatus, Push, OrderAction}
import org.bentocorp.aws.SQS
import org.bentocorp.db.{Database, GenericOrderDao}
import org.bentocorp.dispatch._
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.{Value, Autowired}
import org.springframework.context.annotation.{Configuration, PropertySource}
import org.springframework.core.env.Environment
import org.springframework.web.bind.annotation.{RequestParam, RequestMapping, RestController}
import org.bentocorp.api.APIResponse._
import scala.collection.JavaConversions._
import scala.collection.mutable.{ListBuffer => List, Map => Map}

@RestController
@RequestMapping(Array("/api"))
class HttpController {

  final val Logger = LoggerFactory.getLogger(classOf[HttpController])

  @Autowired
  var config: Config = null

  @Autowired
  var driverManager: DriverManager = null

  @Autowired
  val orderManager: OrderManager = null

  final val HTTP_OK = 200

  JSON.registerModule(DefaultScalaModule)

  def str(obj: Object) = JSON.serialize(obj)

  private var socket: Socket = _
  var token = ""

  final var NODE_URL = ""

  @PostConstruct
  def init() {
    NODE_URL = "http://%s:%s" format (config().getString("node.host"), config().getString("node.port"))
    socket = {
      val opts = new IO.Options
      val url = NODE_URL
      IO.socket(url, opts)
    }
    socket.on(Socket.EVENT_CONNECT, new Listener {
      override def call(args: Object*) {
        println("Connected to Node server")
        val username = config().getString("node.username")
        val password = config().getString("node.password")
        println("Authenticating as %s" format username)
        socket.emit("get", "/api/authenticate?username=%s&password=%s&type=system" format (username, password), new Ack() {
          override def call(args: Object*) {
            val res: APIResponse[Authenticate] = JSON.deserialize(args(0).toString, new TypeReference[APIResponse[Authenticate]]() { })
            if (res.code != 0) {
              println("Error authenticating - " + res.msg)
            } else {
              println("Successfully authenticated")
              token = res.ret.token
              driverManager.drivers.values foreach { d => track(d.id) }
              SQS.start(HttpController.this)
            }
          }
        })
      }
    })

    socket.on("stat", new Listener {
      override def call(args: Object*) {
        Logger.debug("stat - " + args(0))
        // Node server will write to this channel as clients connect / disconnect
        val obj = JSON.deserialize(args(0).asInstanceOf[String], classOf[Stat])
        val parts = obj.clientId.split("-")
        parts(0) match {
          case "d" =>
            val driverId = parts(1).toLong
            if (obj.status.equals("connected")) {
              println("Setting driver %s as %s" format (driverId, Driver.Status.ONLINE))
              driverManager.setStatus(driverId, Driver.Status.ONLINE)
            } else if (obj.status.equals("disconnected")) {
              driverManager.setStatus(driverId, Driver.Status.OFFLINE)
            } else {
              Logger.warn("Unrecognized status event %s for driver %s" format (obj.status, driverId))
            }
          case t => Logger.warn("Unrecognized client type %s" format t)
        }
      }
    })

    socket.on(Socket.EVENT_DISCONNECT, new Listener {
      override def call(args: Object*) {
        println(">> foo disconnected from server")
        Logger.info("Disconnected from Node server")
        SQS.stop()
      }
    })

    println("Attempting to connect to Node server")

    socket.connect()
  }

  @RequestMapping(Array("/push"))
  def send[T](p: Push[T]): APIResponse[String] = {
    val str = Http.get("http://%s:%s/api/push" format (config().getString("node.host"), config().getString("node.port")), "rid" -> p.rid, "from" -> p.from, "to" -> p.to, "subject" -> p.subject,
      "body" -> JSON.serialize(p.body), "token" -> token)
    JSON.deserialize(str, new TypeReference[APIResponse[String]]() { })
  }

  /*
  val redis = new RedisClient("localhost", 6379)

  def _orderCacheKey(orderId: Long) = {
    "order_" + orderId
  }

  def _getOrder(orderId: Long): Option[Order] = {
    redis.get(_orderCacheKey(orderId)) match {
      case Some(str) => Some(_mapper.readValue(str, classOf[Order]))
      case _ => None
    }
  }

  def _setOrder(order: Order) {
    redis.set(_orderCacheKey(order.id), _mapper.writeValueAsString(order))
  }
*/

  /* Atlas */

  @RequestMapping(Array("/driver/getAll"))
  def getDrivers = {
    success(driverManager.drivers.values.toArray)
  }

  @RequestMapping(Array("/order/getAll"))
  def getOrders = {
    // Important - must convert to java object for proper serialization since success() is written in java
    val orders: java.util.Map[String, Order[_]] = new java.util.HashMap[String, Order[_]]
    orderManager.orders.values foreach { order =>
      orders.put(order.id, order)
    }
    orderManager.genericOrders.values foreach { order =>
      orders.put(order.id, order)
    }
    success(orders)
  }

  @Autowired
  var genericOrderDao: GenericOrderDao = null

  import org.bentocorp.Preamble._
  @RequestMapping(Array("/order/create"))
  def create(@RequestParam(value = "name"   ) name   : String,
             @RequestParam(value = "phone"  ) phone  : String,
             @RequestParam(value = "street" ) street : String,
             @RequestParam(value = "city"   ) city   : String,
             @RequestParam(value = "state"  ) state  : String,
             @RequestParam(value = "zipCode") zipCode: String,
             @RequestParam(value = "country") country: String,
             @RequestParam(value = "lat"    ) lat    : String ,
             @RequestParam(value = "lng"    ) lng    : String ,
             @RequestParam(value = "body"   ) body   : String,
             @RequestParam(value = "driverId", defaultValue = "") driverId: String): String = {
    val order = genericOrderDao.insert(Database.Map(
      "fk_Driver" -> (if (driverId.isEmpty) 0L else driverId.toLong), "name" -> name, "phone" -> normalize_phone(phone), "street" -> street, "city" -> city, "region" -> state,
      "zip_code" -> zipCode, "country" -> country, "lat" -> lat, "lng" -> lng, "body" -> body))
    orderManager.genericOrders += order.getOrderKey -> order
    this.send(OrderAction.make(OrderAction.Type.CREATE, order, null, null).from("houston").toRecipient("a-11")) // check reponse?
    if (!driverId.isEmpty) {
      orderManager.assign(order.id, driverId.toLong)
      val push = OrderAction.make(OrderAction.Type.ASSIGN, order, driverId.toLong, null).from("houston")
      this.send(push.toRecipient("a-11"))
      this.send(push.toRecipient("d-" + driverId))
    }
    success("OK")
  }

  @Autowired
  var smsSender: SmsSender = null

  @RequestMapping(Array("/sms/send"))
  def sendSms(@RequestParam("to") to: String, @RequestParam("body") body: String) {
    smsSender.send(to, body)
  }

  @RequestMapping(Array("/order/delete"))
  def delete(@RequestParam(value = "orderId") orderId: String) = {
    val order = orderManager.getOrder(orderId)
    orderManager.delete(orderId)
    send(OrderAction.make(OrderAction.Type.DELETE, order, -1L, null).from("houston").toRecipient("a-11"))
  }

  @RequestMapping(Array("/order/assign"))
  def assign(@RequestParam(value = "rid", defaultValue = "") rid: String,
             @RequestParam(value = "orderId" ) orderId : String,
             @RequestParam(value = "driverId") driverId: java.lang.Long,
             @RequestParam(value = "afterId" ) afterId : String): String = {
    try {
      // protect against deprecated assignment format
      if ("-1".equals(afterId)) {
        return error(1, "Old order assignment format. Parameter \"afterId\" cannot be -1")
      }
      println("assign(%s, %s, %s, %s)" format (rid, orderId, driverId, afterId))
      val order = orderManager.getOrder(orderId)
      if (driverId != null && driverId > 0 && driverManager.getDriver(driverId).getStatus != Driver.Status.ONLINE) {
        throw new Exception("Error - driver %s is not online!" format driverId)
      }
      if (order.getDriverId == null && driverId != null && driverId > 0) {
        // assign order
        orderManager.assign(orderId, driverId, afterId)
        send(OrderAction.make(OrderAction.Type.ASSIGN, order, driverId, afterId).from("houston").toRecipient("d-" + driverId))
      } else if (driverId == null || driverId < 0) {
        if (order.getDriverId == null || order.getDriverId < 0) {
          // If unassigning an unassigned order, return success right away
          return success("OK")
        }
        // unassign order
        val cd = order.getDriverId
        orderManager.unassign(orderId)
        send(OrderAction.make(OrderAction.Type.UNASSIGN, order, null, null).from("houston").toRecipient("d-" + cd))
      } else if (order.getDriverId == driverId) {
        // reprioritize
        val cd = order.getDriverId
        orderManager.reprioritize(orderId, afterId)
        send(OrderAction.make(OrderAction.Type.REPRIORITIZE, order, null, afterId).from("houston").toRecipient("d-" + cd))
      } else {
        throw new Exception("Error - assign(null, %s, %s, %s) - Operation not supported" format (orderId, driverId, afterId))
      }
      // Publish to all other atlas instances
      // TODO - OrderAction.Type doesn't matter to atlas?

      val res = send(OrderAction.make(OrderAction.Type.ASSIGN, order, driverId, afterId).from("houston").toRecipient("a-11"))
      if (res.code != 0) {
        Logger.debug("Warning - Failed to push order update to atlas - " + res.msg)
      }
      success("OK")
    } catch {
      case e: Exception =>
        e.printStackTrace()
        error(1, e.getMessage)
    }
  }

  @RequestMapping(Array("/test"))
  def test() {
    send(new Push("test_subject", "Hello, World!").from("houston").toRecipient("a-11"))
  }

  protected def track(driverId: Long): Driver.Status = {
    val str = Http.get(NODE_URL + "/api/track", "clientId" -> ("d-"+driverId), "token" -> token)
    val res: APIResponse[Track] = JSON.deserialize(str, new TypeReference[APIResponse[Track]] { })
    if (res.code != 0) {
      throw new Exception("Error tracking %s - %s" format (driverId, res.msg))
    } else {
      val driver = driverManager.getDriver(driverId)
      val status = if (res.ret.connected) Driver.Status.ONLINE else Driver.Status.OFFLINE
      if (status != driver.getStatus) {
        Logger.warn("Warning - driver status inconsistency - db=%s, node=%s" format (driver.getStatus, status))
        driverManager.setStatus(driverId, status)
      }
      Logger.debug("Tracking driver %s - %s" format (driverId, status))
      status
    }
  }

  /* Driver app */

  @RequestMapping(Array("/order/getAllAssigned"))
  def orderGetAllAssigned(@RequestParam(value = "token") token: String): String = {
    // To be moved to Spring's authentication filters
    val driverId = token.split("-")(1).toLong
    try {
      val driver = driverManager.getDriver(driverId)
      // needs to be a java object for proper deserialization in houston-core
      val orders = new java.util.ArrayList[Order[_]]
      driver.getOrderQueue foreach { orderId =>
        orders.add(orderManager.getOrder(orderId))
      }
      success(orders)
    } catch {
      case e: Exception =>
        Logger.error("Error during execution of /api/order/getAllAssigned", e)
        error(1, e.getClass + "-" + e.getMessage)
    }
  }

  @RequestMapping(Array("/order/accept"))
  def orderAccept(@RequestParam("orderId") orderId: String, @RequestParam("token") token: String) = {
    try {
      orderManager.updateStatus(orderId, Order.Status.ACCEPTED)
      val push = OrderStatus.make(orderId, Order.Status.ACCEPTED).from("houston").toRecipient("a-11")
      send(push)
      success("OK")
    } catch {
      case e: Exception => error(1, e.getMessage)
    }
  }

  @RequestMapping(Array("/order/reject"))
  def orderReject(@RequestParam("orderId") orderId: String, @RequestParam("token") token: String) = {
    try {
      orderManager.updateStatus(orderId, Order.Status.REJECTED)
      val push = OrderStatus.make(orderId, Order.Status.REJECTED).from("houston").toRecipient("a-11")
      send(push)
      success("OK")
    } catch {
      case e: Exception => error(1, e.getMessage)
    }
  }

  @RequestMapping(Array("/order/complete"))
  def orderComplete(@RequestParam("orderId") orderId: String, @RequestParam("token") token: String) = {
    // To be moved to Spring's authentication filters
    val driverId = token.split("-")(1).toLong
    try {
      orderManager.updateStatus(orderId, Order.Status.COMPLETE)
      driverManager.removeOrder(driverId, orderId)
      val push = OrderStatus.make(orderId, Order.Status.COMPLETE).from("houston").toRecipient("a-11")
      send(push)
      success("OK")
    } catch {
      case e: Exception => error(1, e.getMessage)
    }
  }

  @RequestMapping(Array("/sms/bento-here"))
  def bentoIsHere(@RequestParam("orderId") orderId: String, @RequestParam("token") token: String) = {
    try {
      val order = orderManager.getOrder(orderId)
      val str = "Hey! Your Bento is here =)"
      smsSender.send(order.phone, str)
      success("OK")
    } catch {
      case e: Exception =>
        Logger.error(e.getMessage, e)
        error(1, e.getMessage)
    }
  }
}
