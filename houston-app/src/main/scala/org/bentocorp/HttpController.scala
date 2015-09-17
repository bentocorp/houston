package org.bentocorp

import java.net.URLEncoder

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.github.nkzawa.emitter.Emitter
import com.github.nkzawa.emitter.Emitter.Listener
import com.github.nkzawa.socketio.client.{Ack, Socket, IO}
import com.redis.RedisClient
import org.apache.commons.io.IOUtils
import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import org.bentocorp.api.{Track, Authenticate, APIResponse}
import org.bentocorp.api.ws.{OrderStatus, Push, OrderAction}
import org.bentocorp.dispatch.{Address, Dispatcher, DriverManager, OrderManager}
import org.springframework.beans.factory.annotation.{Value, Autowired}
import org.springframework.context.annotation.{Configuration, PropertySource}
import org.springframework.core.env.Environment
import org.springframework.web.bind.annotation.{RequestParam, RequestMapping, RestController}
import org.bentocorp.api.APIResponse._
import scala.collection.JavaConversions._

@RestController
@RequestMapping(Array("/api"))
@Configuration
@PropertySource(Array("classpath:private-NO-COMMIT.properties"))
class HttpController {

  @Autowired
  var E: Environment = _

  @Value("${env}")
  var env: String = _               // Injection occurs after bean instantiation!
  private def _conf(key: String) = E.getProperty(env + "." + key)

  final val CLIENT_ID = "houston01";//_conf("node.client_id")
  final val NODE_SERVER_HOST = "54.191.141.101"
  //final val NODE_SERVER_HOST = "127.0.0.1"
  final val NODE_SERVER_PORT = 8081
  final val HTTP_OK = 200

  var uid = "";

  private val _mapper = (new ObjectMapper).registerModule(DefaultScalaModule)

  def str(obj: Object) = _mapper.writeValueAsString(obj)

  private val _http = HttpClientBuilder.create().build()

  private val _socket = {
    val opts = new IO.Options
    opts.query = "uid=" + CLIENT_ID
    IO.socket("http://" + NODE_SERVER_HOST + ":" + NODE_SERVER_PORT, opts)
  }
  var token = ""


  _socket.on(Socket.EVENT_CONNECT, new Listener {
    override def call(args: Object*) {
      println("Connected to Node server as " + CLIENT_ID)
      println("Authenticating")
      _socket.emit("get", "/api/authenticate?username=houston01&password=password&type=b", new Ack() {
        override def call(args: Object*) {
          val res: APIResponse[Authenticate] = _mapper.readValue(args(0).toString, new TypeReference[APIResponse[Authenticate]]() { })
          if (res.code != 0) {
            println("Error authenticating - " + res.msg)
          } else {
            println("Successfully authenticated")
            token = res.ret.token
            uid = res.ret.uid;
          }
        }
      })
    }
  })

  _socket.on("stat", new Listener {
    override def call(args: Object*) {
      // Node server will write to this channel as clients connect / disconnect.

    }
  })

  _socket.on(Socket.EVENT_DISCONNECT, new Listener {
    override def call(args: Object*) {
      println("Disconnected from Node server")
    }
  })

  println("Attempting to connect to Node server")

  _socket.connect()

  private def _makeQueryString(params: Map[String, Any]): String =
    params.map(_ match {
      case (key, value) => key + "=" + URLEncoder.encode(value.toString, "UTF-8")
    }).mkString("&")

  private def _httpGet(path: String, params: Map[String, Any]): String = {
    val url = "http://" + NODE_SERVER_HOST + ":" + NODE_SERVER_PORT + path + "?" + _makeQueryString(params)
    val res = _http.execute(new HttpGet(url))
    val statusCode = res.getStatusLine.getStatusCode
    if (statusCode != HTTP_OK)
      null
    else
      IOUtils.toString(res.getEntity.getContent)
  }

  @RequestMapping(Array("/push"))
  def send[T](p: Push[T]): APIResponse[String] = {
    val params = Map("rid" -> p.rid, "from" -> p.from, "to" -> p.to, "subject" -> p.subject,
      "body" -> _mapper.writeValueAsString(p.body), "uid" -> uid, "token" -> token)
    val str = _httpGet("/api/push", params)
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

  @RequestMapping(Array("/driver/get_all"))
  def getDrivers = {
    success(DriverManager.get)
  }

  @RequestMapping(Array("/order/get_all"))
  def getOrders = {
    // Important - must convert to java object for proper serialization since success() is written in java
    val orders: java.util.Map[Long, Order[String]] = OrderManager.get
    success(orders)
  }

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
    val order = new Order[String](OrderManager.next_id(), name, address, body)
    if (driverId != "") {
      order.status = Order.Status.PENDING
      order.driverId = driverId.toLong
      if (!DriverManager.is_registered(driverId.toLong)) {
        return error(1, "Unregistered driver")
      } else {
        DriverManager(driverId.toLong).orderQueue.add(order.id)
      }
    }
    OrderManager.orders += order.id -> order
    val push = OrderAction.make(OrderAction.Type.CREATE, order, -1L , -1L).from(CLIENT_ID).to(str(Array("a11")))
    val p = new Push[Order[String]]("new_order", order).from(CLIENT_ID).to(str(Array("d"+driverId)));
    this.send(p);
    str(this.send(push))
  }

  @RequestMapping(Array("/order/assign"))
  def assign(@RequestParam(value = "rid", defaultValue = "") rid: String, @RequestParam(value = "order_id") orderId: Long, @RequestParam(value = "driver_id") driverId: Long, @RequestParam(value = "after_id") afterId: Long): String = {
    try {
      println("Assigning order %s to driver %s before %s" format (orderId, driverId, afterId))
      if (driverId >= 0 && !DriverManager(driverId).orderQueue.contains(orderId)) {
        println("pushing new order notification to " + driverId)
        val p = new Push[Order[String]]("new_order", OrderManager(orderId)).from(CLIENT_ID).to(str(Array("d"+driverId)));
        this.send(p);
      }
      val order = OrderManager.move(orderId, driverId, afterId)
      val push = OrderAction.make(OrderAction.Type.ASSIGN, order, driverId, afterId).from(CLIENT_ID).to(str(Array("a11")))
      if (driverId >= 0) {
        println(DriverManager(driverId).orderQueue)
      }


      str(this.send(push))
    } catch {
      case e: Exception =>
        e.printStackTrace()
        error(1, e.getMessage)
    }
  }

  /* Driver app */

  @RequestMapping(Array("/available"))
  def available(@RequestParam(value = "driver_id") driverId: String): String = {
    println("Driver %s has just reported that he/she is available to work" format driverId)
    if (!DriverManager.is_registered(driverId.substring(1).toLong)) {
      return error(1, "Driver %s not found" format driverId)
    }
    println("Checking node server")
    val str = _httpGet("/api/track", Map("uid" -> uid, "client_id" -> driverId, "token" -> token))

    val res: APIResponse[Track] = _mapper.readValue(str, new TypeReference[APIResponse[Track]] { })

    if (res.code != 0) {
      return error(1, "Bad node response for /api/track")
    }

    if (res.ret.connected) {
      println("Confirmed that driver %s is online" format driverId)
      DriverManager.set_online(driverId.substring(1).toLong)
      println("Registered %s with Dispatcher" format driverId)
      // Notify atlas that driver has joined the fleet?
      success("OK")
    } else {
      println("Driver %s is not connected to Node server; rejecting" format driverId)
      error(1, "User not connected to node")
    }
  }

  @RequestMapping(Array("/order/accept"))
  def orderAccept(@RequestParam("orderId") orderId: Long, @RequestParam("driverId") driverId: String) = {
    try {
      //val before = null
      val after = OrderManager(orderId)
      after.status = Order.Status.ACCEPTED
      after.driverId = driverId.substring(1).toLong
      send(OrderStatus.make(orderId, Order.Status.ACCEPTED).from(CLIENT_ID).to(str(Array("a11"))))
    } catch {
      case e: Exception => error(1, e.getMessage)
    }
  }

  @RequestMapping(Array("/order/reject"))
  def orderReject(@RequestParam("orderId") orderId: Long, @RequestParam("driverId") driverId: String) = {
    try {
      //val before = null;
      val after = OrderManager(orderId)
      after.status = Order.Status.REJECTED
      // Add driver to rejectors set
      send(OrderStatus.make(orderId, Order.Status.REJECTED).from(CLIENT_ID).to(str(Array("a11"))))
    } catch {
      case e: Exception => error(1, e.getMessage)
    }
  }

  @RequestMapping(Array("/order/complete"))
  def orderComplete(@RequestParam("orderId") orderId: Long, @RequestParam("driverId") driverId: String) = {
    try {
      //val before = null;
      val after = OrderManager(orderId)
      after.status = Order.Status.COMPLETE
      // Other stuff
      send(OrderStatus.make(orderId, Order.Status.COMPLETE).from(CLIENT_ID).to(str(Array("a11"))))
    } catch {
      case e: Exception => error(1, e.getMessage)
    }
  }
}
