package com.bento

import java.net.URLEncoder

import com.bento.dispatch.{Dispatcher, DriverManager}
import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.nkzawa.emitter.Emitter
import com.github.nkzawa.emitter.Emitter.Listener
import com.github.nkzawa.socketio.client.{Socket, IO}
import com.tagged.core.{WebSocketData, APIResponse}
import com.tagged.core.APIResponse.Track
import org.apache.commons.io.IOUtils
import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import org.springframework.web.bind.annotation.{RequestParam, RequestMapping, RestController}
import com.bento.Preamble._

@RestController
@RequestMapping(Array("/api"))
class HttpController {

  final val CLIENT_ID = "onfleet_replacement"
  //final val NODE_SERVER_HOST = "54.191.141.101"
  final val NODE_SERVER_HOST = "127.0.0.1"
  final val NODE_SERVER_PORT = 8081
  final val HTTP_OK = 200

  private val _mapper = new ObjectMapper

  private val _http = HttpClientBuilder.create().build()

  private val _socket = {
    val opts = new IO.Options
    opts.query = "clientId=" + CLIENT_ID
    IO.socket("http://" + NODE_SERVER_HOST + ":" + NODE_SERVER_PORT, opts)
  }
  _socket.on(Socket.EVENT_CONNECT, new Listener {
    override def call(args: Object*) {
      println("Connected to Node server as " + CLIENT_ID)
    }
  })
  _socket.on("stat", new Listener {
    override def call(args: Object*) {
      // Node server will write to this channel as clients connect / disconnect.
      val o = _mapper.readValue(args(0).toString, classOf[WebSocketData.Status])
      if (o.status == "DISCONNECTED") {
        println(s"${o.clientId} disconnected")
        DriverManager.setDriverOffline(o.clientId)
        println(s"Removed ${o.clientId} from Dispatcher queue")
      }
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
  def push(origin: String, target: String, subject: String, body: String): String = {
    val params = Map("origin" -> origin, "target" -> target, "subject" -> subject, "body" -> body)
    val str = _httpGet("/api/push", params)
    val res: APIResponse[String] = _mapper.readValue(str, new TypeReference[APIResponse[String]] {
    })
    if (res.code == 0) {
      "OK"
    } else {
      "ERROR"
    }
  }

  @RequestMapping(Array("/available"))
  def available(@RequestParam(value="uid")uid: String): String = {
    println("Client " + uid + " has just reported that it is available to work")
    if (!DriverManager.isRegistered(uid)) {
      return "BAD_USERNAME"
    }
    println("Checking with Node server")
    val str = _httpGet("/api/track", Map("uid" -> CLIENT_ID, "clientId" -> uid))
    if (str == null) {
      return "BAD_RESPONSE"
    }
    val res: APIResponse[Track] = _mapper.readValue(str, new TypeReference[APIResponse[Track]] {
    })
    if (res.ret.connected) {
      println("Confirmed that " + uid + " is online")
      DriverManager.setDriverOnline(uid)
      println("Registered " + uid + " with Dispatcher")
      println("Notifying admin01 that " + uid + " has joined the fleet")
      this.push(CLIENT_ID, """["admin01"]""", "CLIENT_ONLINE", uid)
    } else {
      println("Client is not connected to Node server; rejecting")
      "NOT_CONNECTED"
    }
  }

  // Proxy
  @RequestMapping(Array("/submit_order"))
  def submitOrder(@RequestParam(value="order") order: String): String = {
    System.out.println("Received order")
    val driver = Dispatcher.assign(order)
    if (driver == null) {
      println("No drivers available")
      return "EMPTY_FLEET"
    }
    this.push(CLIENT_ID, s"""["$driver"]""", "NEW_ORDER", order)
  }

}
