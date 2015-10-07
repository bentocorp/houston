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

  private val _mapper = (new ObjectMapper).registerModule(DefaultScalaModule)

  def str(obj: Object) = _mapper.writeValueAsString(obj)

  private var socket: Socket = _
  private var token = ""

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
            val res: APIResponse[Authenticate] = _mapper.readValue(args(0).toString, new TypeReference[APIResponse[Authenticate]]() { })
            if (res.code != 0) {
              println("Error authenticating - " + res.msg)
            } else {
              println("Successfully authenticated")
              token = res.ret.token
              driverManager.drivers.values foreach { d => track(d.id) }
            }
          }
        })
      }
    })

    socket.on("stat", new Listener {
      override def call(args: Object*) {
        Logger.debug("stat - " + args(0))
        // Node server will write to this channel as clients connect / disconnect
        val obj = _mapper.readValue(args(0).asInstanceOf[String], classOf[Stat])
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
        Logger.info("Disconnected from Node server")
      }
    })

    println("Attempting to connect to Node server")

    socket.connect()
  }

  @RequestMapping(Array("/push"))
  def send[T](p: Push[T]): APIResponse[String] = {

    val str = Http.get("http://%s:%s/api/push" format (config().getString("node.host"), config().getString("node.port")), "rid" -> p.rid, "from" -> p.from, "to" -> p.to, "subject" -> p.subject,
      "body" -> _mapper.writeValueAsString(p.body), "token" -> token)
    _mapper.readValue(str, new TypeReference[APIResponse[String]]() { })
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
    val orders: java.util.Map[Long, Order[List[BentoBox]]] = orderManager.orders
    success(orders)
  }
/*
  @RequestMapping(Array("/order/create"))
  def create(@RequestParam(value = "name"   ) name   : String,
             @RequestParam(value = "street" ) street : String,
             @RequestParam(value = "city"   ) city   : String,
             @RequestParam(value = "state"  ) state  : String,
             @RequestParam(value = "zipCode") zipCode: String,
             @RequestParam(value = "country") country: String,
             @RequestParam(value = "lat"    ) lat    : String ,
             @RequestParam(value = "lng"    ) lng    : String ,
             @RequestParam(value = "body"   ) body   : String,
             @RequestParam(value = "driverId", defaultValue = "") driverId: String): String = {
    val address = new Address(street, "", city, state, zipCode, country)
    address.lat = lat.toFloat
    address.lng = lng.toFloat
    val order = new Order[ListBuffer[BentoBox]](OrderManager.next_id(), name, "", address, body)
    if (driverId != "") {
      order.status = Order.Status.PENDING
      order.driverId = driverId.toLong
      if (!driverManager.is_registered(driverId.toLong)) {
        return error(1, "Unregistered driver")
      } else {
        driverManager(driverId.toLong).orderQueue.add(order.id)
      }
    }
    OrderManager.orders += order.id -> order
    val push = OrderAction.make(OrderAction.Type.CREATE, order, -1L , -1L).from(username).to(str(Array("a11")))
    val p = new Push[Order[ListBuffer[BentoBox]]]("new_order", order).from(username).to(str(Array("d"+driverId)));
    this.send(p);
    str(this.send(push))
  }
*/

  @RequestMapping(Array("/order/assign"))
  def assign(@RequestParam(value = "rid", defaultValue = "") rid: String,
             @RequestParam(value = "orderId" ) orderId : Long,
             @RequestParam(value = "driverId") driverId: Long,
             @RequestParam(value = "afterId" ) afterId : Long): String = {
    try {
      println("assign(%s, %s, %s, %s)" format (rid, orderId, driverId, afterId))
      val order = orderManager(orderId)
      if (driverId > 0 && driverManager(driverId).getStatus != Driver.Status.ONLINE) {
        throw new Exception("Error - driver %s is not online!" format driverId)
      }
      if (order.getDriverId == null) {
        // New order assignment
        orderManager.assign(orderId, driverId, afterId)
      } else if (driverId < 0) {
        // Unassignment
        orderManager.unassign(orderId)
      } else if (order.getDriverId == driverId) {
        // reprioritize
        orderManager.reprioritize(orderId, afterId)
      } else {
        throw new Exception("Unsupported order operation - orderId=%s, driverId=%s, afterId=%s. Try unassigning first?" format (orderId, driverId, afterId))
      }
      // Publish to all other atlas instances
      val a = OrderAction.make(OrderAction.Type.ASSIGN, order, driverId, afterId).from("houston").to(str(Array("a-11")))
      send(a)
      val b = new Push[Order[List[BentoBox]]]("new_order", order).from("houston").to(str(Array("d-"+driverId)))
      str(this.send(b))
    } catch {
      case e: Exception =>
        e.printStackTrace()
        error(1, e.getMessage)
    }
  }

  /* Driver app */

  def track(driverId: Long): Driver.Status = {
    val str = Http.get(NODE_URL + "/api/track", "client_id" -> ("d-"+driverId), "token" -> token)
    val res: APIResponse[Track] = _mapper.readValue(str, new TypeReference[APIResponse[Track]] { })
    if (res.code != 0) {
      throw new Exception("Error tracking %s - %s" format (driverId, res.msg))
    } else {
      val driver = driverManager(driverId)
      val status = if (res.ret.connected) Driver.Status.ONLINE else Driver.Status.OFFLINE
      if (status != driver.getStatus) {
        Logger.warn("Warning - driver status inconsistency - db=%s, node=%s" format (driver.getStatus, status))
        driverManager.setStatus(driverId, status)
      }
      Logger.debug("Tracking driver %s - %s" format (driverId, status))
      status
    }
  }

  @RequestMapping(Array("/order/getAllAssigned"))
  def orderGetAllAssigned(@RequestParam(value = "token") token: String): String = {
    try {
      val driverId = token.split("-")(1).toLong
      val driver = driverManager(driverId)
      val orders = List.empty[Order[List[BentoBox]]]
      driver.getOrderQueue foreach { orderId =>
        orders += orderManager(orderId)
      }
      //Logger.debug(orders.map(_.toString).mkString("\n"))
      success(_mapper.writeValueAsString(orders))
    } catch {
      case e: Exception =>
        Logger.error("Error during execution of /api/order/getAllAssigned", e)
        error(1, e.getMessage)
    }
  }

  @RequestMapping(Array("/order/accept"))
  def orderAccept(@RequestParam("orderId") orderId: Long, @RequestParam("token") token: String) = {
    try {
      orderManager.updateStatus(orderId, Order.Status.ACCEPTED)
      send(OrderStatus.make(orderId, Order.Status.ACCEPTED).from("houston").to(str(Array("a-11"))))
    } catch {
      case e: Exception => error(1, e.getMessage)
    }
  }

  @RequestMapping(Array("/order/reject"))
  def orderReject(@RequestParam("orderId") orderId: Long, @RequestParam("token") token: String) = {
    try {
      orderManager.updateStatus(orderId, Order.Status.REJECTED)
      send(OrderStatus.make(orderId, Order.Status.REJECTED).from("houston").to(str(Array("a-11"))))
    } catch {
      case e: Exception => error(1, e.getMessage)
    }
  }

  @RequestMapping(Array("/order/complete"))
  def orderComplete(@RequestParam("orderId") orderId: Long, @RequestParam("token") token: String) = {
    try {
      orderManager.updateStatus(orderId, Order.Status.COMPLETE)
      val driverId = token.split("-")(1).toLong
      driverManager.removeOrder(driverId, orderId)
      send(OrderStatus.make(orderId, Order.Status.COMPLETE).from("houston").to(str(Array("a-11"))))
    } catch {
      case e: Exception => error(1, e.getMessage)
    }
  }
}
