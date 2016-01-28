package org.bentocorp.controllers

import java.lang.{Integer => JInt}
import java.security.SecureRandom
import javax.annotation.PostConstruct
import javax.net.ssl._

import com.fasterxml.jackson.core.`type`.TypeReference
import com.twilio.sdk.TwilioRestException
import io.socket.client.{Ack, IO, Manager, Socket}
import io.socket.emitter.Emitter.Listener
import io.socket.engineio.client.{EngineIOException, Transport}
import org.bentocorp._
import org.bentocorp.api.APIResponse._
import org.bentocorp.api.ws.{OrderAction, OrderStatus, Push, Stat}
import org.bentocorp.api.{APIResponse, Authenticate, Track}
import org.bentocorp.aws.SQS
import org.bentocorp.db.{Database, GenericOrderDao}
import org.bentocorp.dispatch._
import org.bentocorp.houston.app.service.SystemEta
import org.bentocorp.houston.config.BentoConfig
import org.bentocorp.houston.util.{HttpUtils, PhoneUtils}
import org.bentocorp.redis.Redis
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.{RequestMapping, RequestParam, RestController}

import scala.collection.JavaConversions._

@RestController
@RequestMapping(Array("/api"))
class HttpController {

  final val Logger = LoggerFactory.getLogger(classOf[HttpController])

  @Autowired
  var config: BentoConfig = null

  @Autowired
  var driverManager: DriverManager = null

  @Autowired
  val orderManager: OrderManager = null

  final val HTTP_OK = 200

  def str(obj: Object) = ScalaJson.stringify(obj)

  private var socket: Socket = _
  var token = ""

  final var NODE_URL = ""

  @Autowired
  var systemEtaService: SystemEta = _

  @Autowired
  var phpService: PhpService = null

  @PostConstruct
  def init() {
    HttpUtils.configureFor(config.getString("env"))
    NODE_URL = config.getString("node.url")
    socket = {
      val opts = new IO.Options
      opts.secure = true
      // If not on prod, configure an SSLContext with a TrustManager that accepts all certificates
      if (config.getString("env") != "prod") {
        Logger.info("\n" +
          "***************\n" +
          "** ATTENTION **\n" +
          "***************\n" +
          "Configuring SSLContext with TrustManager that accepts all certificates")
        val customTrustManager = HttpUtils.acceptAllCertsTrustManager

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(Array.empty[KeyManager], Array(customTrustManager), new SecureRandom)

        opts.sslContext = sslContext
        opts.hostnameVerifier = HttpUtils.relaxedHostnameVerifier
      }
      IO.socket(NODE_URL, opts)
    }
    socket.on(Socket.EVENT_CONNECT, new Listener {
      override def call(args: Object*) {
        Logger.info("Connected to Node server")
        val username = config.getString("node.username")
        val password = config.getString("node.password")
        Logger.info("Authenticating as %s" format username)
        socket.emit("get", "/api/authenticate?username=%s&password=%s&type=system" format (username, password), new Ack() {
          override def call(args: Object*) {
            val res: APIResponse[Authenticate] = ScalaJson.parse(args(0).toString, new TypeReference[APIResponse[Authenticate]]() { })
            if (res.code != 0) {
              Logger.info("Error authenticating - " + res.msg)
            } else {
              Logger.info("Successfully authenticated")
              token = res.ret.token
              Logger.debug("received access token " + token)
              val drivers = driverManager.drivers.toMap
              drivers.values foreach { d => track(d.id) }
              if (!config.getBoolean("ignore-sqs")) {
                SQS.start(HttpController.this)
              }
              // TODO - There should be an elegant way to update token for background services
              systemEtaService.start(token)
            }
          }
        })
      }
    })

    socket.on("stat", new Listener {
      override def call(args: Object*) {
        Logger.debug("stat - " + args(0))
        // Node server will write to this channel as clients connect / disconnect
        val obj = ScalaJson.parse(args(0).asInstanceOf[String], classOf[Stat])
        val parts = obj.clientId.split("-")
        parts(0) match {
          case "d" =>
            val driverId = parts(1).toLong
            if (obj.status.equals("connected")) {
              Logger.info("Setting driver %s as %s" format (driverId, Driver.Status.ONLINE))
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
        val msg = "Disconnected from Node server due to: " + args(0)
//        println(msg)
        Logger.info(msg)
        SQS.stop()
      }
    })

    socket.on(Socket.EVENT_CONNECT_TIMEOUT, new Listener {
      override def call(args: Object*) {
        Logger.error("Error - EVENT_CONNECT_TIMEOUT")
      }
    })

    socket.on(Socket.EVENT_CONNECT_ERROR, new Listener {
      override def call(args: Object*) {
        Logger.error("Error - EVENT_CONNECT_ERROR")
      }
    })

    socket.on(Socket.EVENT_RECONNECT_ERROR, new Listener {
      override def call(args: Object*) {
        Logger.error("Error - EVENT_RECONNECT_ERROR")
      }
    })

    /* Certain connectivity problems can be identified by inspecting the underlying Transport object of the Socket.IO
       client–for example, failure to upgrade to the WebSocket protocol due to SSL issues. */
    val transportLogger = LoggerFactory.getLogger("socket-io-transport")

    import java.util.{Map => JMap}

    socket.io().on(Manager.EVENT_TRANSPORT, new Listener {

      override def call(args: AnyRef*) {
        val transport: Transport = args(0).asInstanceOf[Transport]
        // Implicitly convert java.util.Map to a Scala Map using toMap()
        val parts = (transport.query.toMap.toList map {
          case (k, v) => k + "=" + v
        }).mkString("&")
        // Listen for errors
        transport.on(Transport.EVENT_ERROR, new Listener {
          override def call(args: AnyRef*) {
            val ex = args(0).asInstanceOf[EngineIOException]
            transportLogger.error(
              "Transport (%s) error - %s" format (transport.name, ex.getMessage),
              ex
            )
          }
        })
      }
    });

    Logger.info("Trying to connect to Node server at " + NODE_URL)

    socket.connect()
  }

  @RequestMapping(Array("/push"))
  def send[T](p: Push[T]): APIResponse[String] = {
    val params = Map("rid"  -> p.rid, "from" -> p.from, "to" -> p.to, "subject" -> p.subject,
      "body" -> ScalaJson.stringify(p.body), "token" -> token)
    val str = HttpUtils.get(
      config.getString("node.url") + "/api/push", params
    )
    ScalaJson.parse(str, new TypeReference[APIResponse[String]]() { })
  }

  /* Atlas */

  @RequestMapping(Array("/driver/getAll"))
  def getDrivers = {
    success(driverManager.drivers.toMap.values.toArray)
  }

  @RequestMapping(Array("/order/getAll"))
  def getOrders = {
    // Important - must convert to java object for proper serialization since success() is written in java
    val orders: java.util.Map[String, Order[_]] = new java.util.HashMap[String, Order[_]]
    orderManager.orders.toMap.values foreach { order =>
      orders.put(order.id, order)
    }
    orderManager.genericOrders.toMap.values foreach { order =>
      orders.put(order.id, order)
    }
    success(orders)
  }

  @Autowired
  var genericOrderDao: GenericOrderDao = null

  @RequestMapping(Array("/order/create"))
  def create(@RequestParam(value = "token"  ) token  : String,
             @RequestParam(value = "firstName") firstName: String,
             @RequestParam(value = "lastName" ) lastName : String,
             @RequestParam(value = "phone"  ) phone  : String,
             @RequestParam(value = "street" ) street : String,
             @RequestParam(value = "city"   ) city   : String,
             @RequestParam(value = "state"  ) state  : String,
             @RequestParam(value = "zipCode") zipCode: String,
             @RequestParam(value = "country") country: String,
             @RequestParam(value = "lat"    ) lat    : String,
             @RequestParam(value = "lng"    ) lng    : String,
             @RequestParam(value = "body"   ) body   : String,
             @RequestParam(value = "notes"  ) notes  : String,
             @RequestParam(value = "driverId", defaultValue = "") driverId: String): String = {
    try {
      val order = genericOrderDao.insert(Database.Map(
        "fk_Driver" -> (if (driverId.isEmpty) -1L else driverId.toLong), "firstname" -> firstName, "lastname" -> lastName,
        "phone" -> PhoneUtils.normalize(phone), "street" -> street, "city" -> city, "region" -> state,
        "zip_code" -> zipCode, "country" -> country, "lat" -> lat, "lng" -> lng, "body" -> body, "notes_for_driver" -> notes))
      orderManager.genericOrders += order.getOrderKey -> order
      this.send(OrderAction.make(OrderAction.Type.CREATE, order, null, null).from("houston").toGroup("atlas")) // check response?
      if (!driverId.isEmpty) {
        val modifiedOrder = orderManager.assign(order.id, driverId.toLong, null, token)
        val push = OrderAction.make(OrderAction.Type.ASSIGN, modifiedOrder, driverId.toLong, null).from("houston")
        this.send(push.toGroup("atlas"))
        this.send(push.toRecipient("d-" + driverId))
      }
      success("OK")
    } catch {
      case e: Exception =>
        Logger.error(e.getMessage, e.getStackTrace)
        error(1, e.getMessage)
    }

  }

  @RequestMapping(Array("/order/update"))
  def updateOrder(@RequestParam(value = "rid", defaultValue = "") rid: String,
                  @RequestParam(value = "orderId") orderId: String,
//                  @RequestParam(value = "firstName", defaultValue = "") firstName: String,
//                  @RequestParam(value = "lastName" , defaultValue = "") lastName : String,
                  @RequestParam(value = "phone"    , defaultValue = "") phone    : String,
                  @RequestParam(value = "street"   , defaultValue = "") street   : String,
                  @RequestParam(value = "city"     , defaultValue = "") city     : String,
                  @RequestParam(value = "state"    , defaultValue = "") state    : String,
                  @RequestParam(value = "zipCode"  , defaultValue = "") zipCode  : String,
//                  @RequestParam(value = "country"  , defaultValue = "") country  : String,
                  @RequestParam(value = "lat"      , defaultValue = "") lat      : String,
                  @RequestParam(value = "lng"      , defaultValue = "") lng      : String,
                  @RequestParam(value = "notes"    , defaultValue = "") notes    : String): String = {
    try {
      val order = orderManager.getOrder(orderId)

      // Safety check and redundancy - only consider non-null & non-empty values
      val valid: (String) => Boolean = (arg0: String) => arg0 != null && !arg0.isEmpty

      // Cannot modify first and last name due to the way Bento orders are stored in the database
//      if (valid(firstName)) order.firstName = firstName
//      if (valid( lastName)) order.lastName  = lastName
      if (valid(    phone)) order.phone     = phone
      if (valid(   street)) order.address.street  = street
      if (valid(     city)) order.address.city    = city
      if (valid(    state)) order.address.region  = state
      if (valid(  zipCode)) order.address.zipCode = zipCode
      // Table schema for Bento orders does not have country column
//      if (valid(  country)) order.address.country = country
      if (valid(      lat)) order.address.lat     = lat.toFloat
      if (valid(      lng)) order.address.lng     = lng.toFloat
      if (valid(    notes)) order.notes     = notes

      orderManager.update(order)
      send(OrderAction.make(OrderAction.Type.MODIFY, order, -1l, null).rid(rid).from("houston").toGroup("atlas"))
      if (order.getDriverId != null && order.getDriverId > 0) {
        send(OrderAction.make(OrderAction.Type.MODIFY, order, -1l, null).rid(rid).from("houston").toRecipient("d-" + order.getDriverId))

      }
      success(order)
    } catch {
      case e: Exception =>
        Logger.error(e.getMessage, e)
        error(1, e.getMessage)
    }
  }

  @Autowired
  var smsSender: SmsSender = null
/*
  @RequestMapping(Array("/sms/send"))
  def sendSms(@RequestParam("to") to: String, @RequestParam("body") body: String) {
    smsSender.send(to, body)
  }
*/
  @RequestMapping(Array("/order/delete"))
  def delete(@RequestParam(value = "orderId") orderId: String, @RequestParam(value="token") token: String) = {
    try {
      val order = orderManager.getOrder(orderId)
      orderManager.delete(orderId, token)
      send(OrderAction.make(OrderAction.Type.DELETE, order, -1L, null).from("houston").toGroup("atlas"))
      success("OK")
    } catch {
      case e: Exception => error(1, e.getMessage)
    }
  }

  @RequestMapping(Array("/order/assign"))
  def assign(@RequestParam(value = "token") token: String,
             @RequestParam(value = "rid", defaultValue = "") rid: String,
             @RequestParam(value = "orderId" ) orderId : String,
             @RequestParam(value = "driverId") driverId: java.lang.Long,
             @RequestParam(value = "afterId" ) afterId : String): String = {
    try {
      // protect against deprecated assignment format
      if ("-1".equals(afterId)) {
        return error(1, "Old order assignment format. Parameter \"afterId\" cannot be -1")
      }
      Logger.debug("assign(%s, %s, %s, %s)" format (rid, orderId, driverId, afterId))
      val order = orderManager.getOrder(orderId)
      Logger.debug("driverId=" + order.getDriverId)
      if (driverId != null && driverId > 0 && driverManager.getDriver(driverId).getStatus != Driver.Status.ONLINE) {
        throw new Exception("Error - driver %s is not online!" format driverId)
      }
      val modifiedOrder: Order[_] =
      //  TODO - driver 0 is valid now (so change to < 0)
      if ((order.getDriverId == null || order.getDriverId <= 0) && driverId != null && driverId > 0) {
        // assign order
        val assignedOrder = orderManager.assign(orderId, driverId, afterId, token)
        // XXX: TODO - Fix this so calling OrderManager#assign modifies the order object
        send(OrderAction.make(OrderAction.Type.ASSIGN, assignedOrder, driverId, afterId).from("houston").toRecipient("d-" + driverId))
        assignedOrder
      } else if (driverId == null || driverId < 0) {
        if (order.getDriverId == null || order.getDriverId < 0) {
          // If unassigning an unassigned order, return success right away
          return success("OK")
        }
        // unassign order
        val cd = order.getDriverId
        val unassignedOrder = orderManager.unassign(orderId, token)
        send(OrderAction.make(OrderAction.Type.UNASSIGN, unassignedOrder, null, null).from("houston").toRecipient("d-" + cd))
        unassignedOrder
      } else if (order.getDriverId == driverId) {
        // TODO - Dragging a rejected order into the driver name should unassign then reassign (not reprioritize)
        // reprioritize
        val cd = order.getDriverId
        val reprioritizedOrder = orderManager.reprioritize(orderId, afterId)
        send(OrderAction.make(OrderAction.Type.REPRIORITIZE, order, null, afterId).from("houston").toRecipient("d-" + cd))
        reprioritizedOrder
      } else {
        throw new Exception("Oops assign(null, %s, %s, %s) - Operation not supported" format (orderId, driverId, afterId))
      }
      // Publish to all other atlas instances
      // TODO - OrderAction.Type doesn't matter to atlas?

      val res = send(OrderAction.make(OrderAction.Type.ASSIGN, modifiedOrder, driverId, afterId).rid(rid).from("houston").toGroup("atlas"))
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
  
  protected def track(driverId: Long): Driver.Status = {
    val str = HttpUtils.get(NODE_URL + "/api/track", Map("clientId" -> ("d-"+driverId), "token" -> token))
    val res: APIResponse[Track] = ScalaJson.parse(str, new TypeReference[APIResponse[Track]] { })
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
      // TODO - This check is not foolproof because another thread may be in the process of modifying the order
      val actingDriverId = token.split("-")(1).toLong
      val orderDriverId = orderManager.getOrder(orderId).getDriverId
      if (orderDriverId != actingDriverId) {
        throw new Exception("Error - This order is assigned to a different driver (%s)" format orderDriverId)
      }
      val order = orderManager.updateStatus(orderId, Order.Status.ACCEPTED)
      val push = OrderStatus.make(orderId, Order.Status.ACCEPTED).from("houston").toGroup("atlas")
      send(push)
      // Let the customer know that a driver is on the way
      val greeting = {
        if (order.firstName != null && !order.firstName.isEmpty) {
          "Hi %s,\n" format order.firstName
        } else {
          "Hi!\n"
        }
      }
      /*
      val eta =
        try {
          // At this point, log any Exceptions but otherwise return a successful response
          // Get driver's current location from Node
          val res = HttpUtils.get(NODE_URL + "/api/gloc", Map("token"->token, "clientId" -> ("d-"+order.getDriverId)))
          val driverCurrentLoc: APIResponse[WayPoint] = ScalaJson.parse(res, new TypeReference[APIResponse[WayPoint]]() { })
          val wayPoints: Array[WayPoint] = Array(
            driverCurrentLoc.ret,
            new WayPoint(order.address.lng, order.address.lat)
          )
          val eta = MapboxService.getEta(wayPoints)*3 // Mapbox ETA's are highly inaccurate?
          Logger.info("Mapbox ETA for driver %s (%s) to order %s (%s) is %s" format (
            order.getDriverId,
            driverCurrentLoc.ret.toString,
            order.id,
            order.address.lng + "," + order.address.lat,
            eta))
          if (eta <= 0 || eta >= 45) {
            throw new Exception("Warning - Mapbox ETA (%s) inaccurate?" format eta)
          }
          eta
        } catch {
          case ex: Exception =>
            Logger.error(ex.getMessage, ex)
            30 // Default to 30 minutes
        }
        */
      val msg = greeting + "Your Bento server is about 15-25 minutes away. You’ll receive another text message shortly when they arrive."
      smsSender.send(order.phone, msg)
      success("OK")
    } catch {
      case twilioRestException: TwilioRestException =>
        error(2, twilioRestException.getMessage)
      case e: Exception =>
        Logger.error(e.getMessage, e)
        error(1, e.getMessage)
    }
  }

  @RequestMapping(Array("/order/reject"))
  def orderReject(@RequestParam("orderId") orderId: String, @RequestParam("token") token: String) = {
    try {
      // TODO - This check is not foolproof because another thread may be in the process of modifying the order
      val actingDriverId = token.split("-")(1).toLong
      val orderDriverId = orderManager.getOrder(orderId).getDriverId
      if (orderDriverId != actingDriverId) {
        throw new Exception("Error - This order is assigned to a different driver (%s)" format orderDriverId)
      }
      orderManager.updateStatus(orderId, Order.Status.REJECTED)
      val push = OrderStatus.make(orderId, Order.Status.REJECTED).from("houston").toGroup("atlas")
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
      // TODO - This check is not foolproof because another thread may be in the process of modifying the order
      val actingDriverId = token.split("-")(1).toLong
      val orderDriverId = orderManager.getOrder(orderId).getDriverId
      if (orderDriverId != actingDriverId) {
        throw new Exception("Error - This order is assigned to a different driver (%s)" format orderDriverId)
      }
      orderManager.updateStatus(orderId, Order.Status.COMPLETE)
      driverManager.removeOrder(driverId, orderId)
      val push = OrderStatus.make(orderId, Order.Status.COMPLETE).from("houston").toGroup("atlas")
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
      val str = "Your Bento has arrived. Woot! Look for the car with the green flags & please meet your server curbside. Thanks and enjoy your meal!"
      smsSender.send(order.phone, str)
      success("OK")
    } catch {
      case twilioRestException: TwilioRestException =>
        error(2, twilioRestException.getMessage)
      case e: Exception =>
        Logger.error(e.getMessage, e)
        error(1, e.getMessage)
    }
  }

  // TODO - Move all sms endpoints to another controller
  @RequestMapping(Array("/sms/send"))
  def sms(@RequestParam("orderId") orderId: String, @RequestParam("msg") msg: String) = {
    try {
      val order = orderManager.getOrder(orderId)
      if (msg.isEmpty) {
        throw new Exception("Error - You cannot send an empty SMS msg!")
      }
      Logger.info("%s -> %s" format (order.phone, msg))
      smsSender.send(order.phone, msg)
      success("OK")
    } catch {
      case twilioRestException: TwilioRestException =>
        error(2, twilioRestException.getMessage)
      case e: Exception =>
        Logger.error(e.getMessage, e)
        error(1, e.getMessage)
    }
  }

  @RequestMapping(Array("/sms/eta"))
  def eta(@RequestParam("orderId") orderId: String, @RequestParam("minutes") minutes: Int) = {
    try {
      val order = orderManager.getOrder(orderId)
      val firstname = order.firstName
      if (minutes <= 0) {
        throw new Exception("Invalid minutes %s" format minutes)
      }
      if (firstname == null || firstname.isEmpty) {
        throw new Exception("Invalid first name %s" format firstname)
      }
      val str = "Hi %s,\nThanks for ordering Bento! Your order should arrive in about %s minutes. We'll message you once your order is on its way." format (
        firstname,  minutes
      )
      Logger.info("%s -> %s" format (order.phone, str))
      smsSender.send(order.phone, str)
      success("OK")
    } catch {
      case e: Exception =>
        Logger.error(e.getMessage, e)
        error(1, e.getMessage)
    }
  }

  @RequestMapping(Array("/php/assign"))
  def phpAssign(@RequestParam("orderId")  orderId : String,
                @RequestParam("driverId") driverId: Long,
                @RequestParam("afterId")  afterId : String,
                @RequestParam("token")    token   : String) = {
    phpService.assign(orderId, driverId, afterId, token)
  }

  @Autowired
  var redis: Redis = null

  @RequestMapping(Array("/flushdb"))
  def flushdb() = {
    try {
      Logger.info("Flushing redis")
      redis.flushdb(List(8))
      success("OK")
    } catch {
      case e: Exception => error(1, e.getMessage)
    }
  }

  @RequestMapping(Array("/syncOrders"))
  def syncOrders() = {
    try {
      orderManager.syncOrders()
      success("OK")
    } catch {
      case e: Exception => error(1, e.getMessage)
    }
  }

  @RequestMapping(Array("/syncDrivers"))
  def syncDrivers() = {
    try {
      driverManager.syncDrivers()
      success("OK")
    } catch {
      case e: Exception => error(1, e.getMessage)
    }
  }

  @RequestMapping(Array("/driver/setShiftType"))
  def changeDriverShift(@RequestParam(value = "rid", defaultValue = "") rid: String,
                        @RequestParam("driverId")  driverId        : Long,
                        @RequestParam("shiftType") shiftTypeOrdinal: Int): String = {
    try {
      var driver = driverManager.getDriver(driverId)
      if (driver.shiftType.ordinal() == shiftTypeOrdinal) {
        throw new Exception("Driver(%s).shiftType is already %s" format (driverId, shiftTypeOrdinal))
      }
      val shiftType = Shift.Type.values()(shiftTypeOrdinal)
      if (driverManager.setShiftType(driverId, shiftType)) {
        // Retrieve the updated driver from Redis and send a push to Atlas indicating what changed
        driver = driverManager.getDriver(driverId)
        val push = new Push("driver_shift", driver).rid(rid).from("houston").toGroup("atlas")
        send(push)
        success("OK")
      } else {
        throw new Exception("Error - driverManager.setShiftType(%s, %s) = false" format (driverId, shiftType))
      }
    } catch {
      case e: Exception => Logger.error(e.getMessage, e); error(1, e.getMessage)
    }
  }
}
