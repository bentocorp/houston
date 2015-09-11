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
import org.bentocorp.api.ws.{PushNotification, OrderStatusPN}
import org.bentocorp.dispatch.{Dispatcher, DriverManager, OrderManager}
import org.springframework.beans.factory.annotation.{Value, Autowired}
import org.springframework.context.annotation.{Configuration, PropertySource}
import org.springframework.core.env.Environment
import org.springframework.web.bind.annotation.{RequestParam, RequestMapping, RestController}
import org.bentocorp.api.APIResponse._

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
  //final val NODE_SERVER_HOST = "54.191.141.101"
  final val NODE_SERVER_HOST = "127.0.0.1"
  final val NODE_SERVER_PORT = 8081
  final val HTTP_OK = 200

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
      _socket.emit("get", "/api/authenticate?username=atlas01&password=password", new Ack() {
        override def call(args: Object*) {
          val res: APIResponse[Authenticate] = _mapper.readValue(args(0).toString, new TypeReference[APIResponse[Authenticate]]() { })
          if (res.code != 0) {
            println("Error authenticating - " + res.msg)
          } else {
            println("Successfully authenticated")
            token = res.ret.token
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

  private def _makeQueryString(params: Map[String, String]): String =
    params.map(_ match {
      case (key, value) => key + "=" + URLEncoder.encode(value, "UTF-8")
    }).mkString("&")

  private def _httpGet(path: String, params: Map[String, String]): String = {
    val url = "http://" + NODE_SERVER_HOST + ":" + NODE_SERVER_PORT + path + "?" + _makeQueryString(params)
    val res = _http.execute(new HttpGet(url))
    val statusCode = res.getStatusLine.getStatusCode
    if (statusCode != HTTP_OK)
      null
    else
      IOUtils.toString(res.getEntity.getContent)
  }

  @RequestMapping(Array("/push"))
  def push[T](p: PushNotification[T]): APIResponse[String] = {
    val params = Map("origin" -> p.origin, "target" -> p.target, "subject" -> p.subject,
      "body" -> _mapper.writeValueAsString(p.body), "uid" -> CLIENT_ID, "token" -> token)
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





  @RequestMapping(Array("/assign"))
  def assign(@RequestParam(value="orderId") orderId: String, @RequestParam(value="driverId") driverId: String) = {
    println("Assigning " + orderId + " to " + driverId)
    val o: Order = _orderManager.assign(orderId.toLong, driverId)
    val pn = new PushNotification[Order]("NEW_ORDER", o).from(CLIENT_ID).to(str(Array(driverId)))
    val ret = push(pn)
    println(ret)
    success("")
  }


  @RequestMapping(Array("/available"))
  def available(@RequestParam(value = "uid") uid: String): String = {
    println("Client " + uid + " has just reported that it is available to work")
    if (!DriverManager.isRegistered(uid)) {
      return error(1, "User " + uid + " not found")
    }
    println("Checking node server")
    val str = _httpGet("/api/track", Map("uid" -> CLIENT_ID, "client_id" -> uid, "token" -> token))
    println(str);
    val res: APIResponse[Track] = _mapper.readValue(str, new TypeReference[APIResponse[Track]] { })

    if (res.code != 0) {
      return error(1, "Bad node response for /api/track")
    }

    println(_mapper.writeValueAsString(res))
    if (res.ret.connected) {
      println("Confirmed that " + uid + " is online")
      DriverManager.setDriverOnline(uid)
      println("Registered " + uid + " with Dispatcher")
      //println("Notifying admin01 that " + uid + " has joined the fleet")
      //push(CLIENT_ID, """["admin01"]""", "CLIENT_ONLINE", uid)
      success("OK")
    } else {
      println("Client is not connected to Node server; rejecting")
      error(1, "User not connected to node")
    }
  }

  @Autowired
  var dispatcher: Dispatcher = _

  @Autowired
  var _orderManager: OrderManager = _


/*
  // Proxy
  @RequestMapping(Array("/submit_order"))
  def submitOrder(@RequestParam(value="order") order: Long, @RequestParam(value="driver_id") driverId: Long): String = {
    System.out.println("Received order")
    val driver = dispatcher.assign(order, driverId)
    if (driver == null) {
      println("No drivers available")
      return "EMPTY_FLEET"
    }
    this.push(CLIENT_ID, s"""["$driver"]""", "NEW_ORDER", order+"")
  }
*/
  @RequestMapping(Array("/order_accept"))
  def orderAccept(@RequestParam("orderId") orderId: Long, @RequestParam("driverId") driverId: String) = {
    val o = _orderManager.orders(orderId)
    o.status = Order.Status.ACCEPTED
    o.driverId = driverId
    val pn = OrderStatusPN.make(orderId, Order.Status.ACCEPTED, driverId).from(CLIENT_ID).to(str(Array("atlas01")))
    push(pn)
    success("")
  }

  @RequestMapping(Array("/order_reject"))
  def orderReject(@RequestParam("orderId") orderId: Long, @RequestParam("driverId") driverId: String) = {
    val pn = OrderStatusPN.make(orderId, Order.Status.REJECTED, driverId).from(CLIENT_ID).to(str(Array("atlas01")))
    push(pn);
    success("")
  }

  @RequestMapping(Array("/order_complete"))
  def orderComplete(@RequestParam("orderId") orderId: Long, @RequestParam("driverId") driverId: String) = {
    println("order complete pushing");
    val pn = OrderStatusPN.make(orderId, Order.Status.COMPLETE, driverId).from(CLIENT_ID).to(str(Array("atlas01")))
    push(pn)
    success("")
  }

  @RequestMapping(Array("/get_drivers"))
  def getDrivers = {
    success(DriverManager.getDrivers)
  }

  @RequestMapping(Array("/get_orders"))
  def getOrders = {
    success(_orderManager.getOrders)
  }
}
