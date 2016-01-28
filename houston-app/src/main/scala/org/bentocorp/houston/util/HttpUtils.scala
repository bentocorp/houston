package org.bentocorp.houston.util

import java.net.URLEncoder
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util
import javax.net.ssl._
import javax.security.cert.CertificateException

import org.apache.commons.io.IOUtils
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.{HttpGet, HttpPost}
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.{CloseableHttpClient, HttpClientBuilder}
import org.apache.http.message.BasicNameValuePair
import org.apache.http.{HttpResponse, NameValuePair}
import org.slf4j.LoggerFactory
import org.springframework.web.context.request.{RequestAttributes, RequestContextHolder}

object HttpUtils {

  final val LOGGER = LoggerFactory.getLogger(HttpUtils.getClass)

  private var configuredFor: String = null

  def configureFor(env: String) {
    if (configuredFor != null && configuredFor != env) {
      throw new Exception("Error - HttpUtils has already been configured for %s by another bean" format configuredFor)
    }
    LOGGER.info("Configuring HttpUtils for " + env)
    configuredFor = env
  }

  private lazy val client: CloseableHttpClient = {
    if (configuredFor.isEmpty) {
      LOGGER.warn("HttpUtils is being used without first being configured - defaulting to prod")
      configureFor("prod")
    }
    val builder: HttpClientBuilder = HttpClientBuilder.create()
    if (configuredFor != "prod") {
      LOGGER.info("ATTENTION! Configuring insecure HTTP client")
      val sslContext = SSLContext.getInstance("TLS")
      sslContext.init(Array.empty[KeyManager], Array(acceptAllCertsTrustManager), new SecureRandom)
      builder.setSSLContext(sslContext)
             .setSSLHostnameVerifier(relaxedHostnameVerifier)
    }
    builder.build()
  }

  def acceptAllCertsTrustManager = new X509TrustManager() {

    def getAcceptedIssuers: Array[X509Certificate] = Array.empty[X509Certificate]

    @throws(classOf[CertificateException])
    def checkClientTrusted(certs: Array[X509Certificate], authType: String) {
      // Do nothing
    }

    @throws(classOf[CertificateException])
    def checkServerTrusted(certs: Array[X509Certificate], authType: String) {
      // Do nothing
    }
  }

  def relaxedHostnameVerifier = new HostnameVerifier {

    def verify(hostname: String, session: SSLSession) = true
  }

  def get(url: String, params: Map[String, Any], errorOnFailure: Boolean = false, logEndPoint: Boolean = true): String = {
    val queryString = (params map {
      case (key, value) => URLEncoder.encode(key, "UTF-8") + "=" + URLEncoder.encode(value.toString, "UTF-8")
    }).mkString("&")
    val endpoint = url + "?" + queryString
    if (logEndPoint) {
      LOGGER.debug(endpoint)
    }
    val res = client.execute(new HttpGet(endpoint))
    val statusCode = res.getStatusLine.getStatusCode
    if (statusCode != 200) {
      val errorMsg = "Error - %s failed with status code %s" format (endpoint, statusCode)
      if (errorOnFailure) {
        throw new Exception(errorMsg)
      }
      val stackTrace = Thread.currentThread.getStackTrace
      LOGGER.error(errorMsg, stackTrace)
      null
    } else {
      IOUtils.toString(res.getEntity.getContent)
    }
  }

  // The reason for breaking the POST helper function into two separate postStr() and postForm()
  // methods (instead of using reflection with a single function) is so that we can catch type errors during
  // compile time rather than runtime
  private def _post(url: String, headers: Map[String, Any], entity: StringEntity): (Int, String) = {
    val post = new HttpPost(url)
    // Set headers
    headers foreach {
      case (key, value) => post.addHeader(key, value.toString)
    }
    // Set body (entity)
    post.setEntity(entity)
    val res: HttpResponse = client.execute(post)
    (res.getStatusLine.getStatusCode, IOUtils.toString(res.getEntity.getContent))
  }

  @throws(classOf[Exception])
  def postStr (url: String, headers: Map[String, Any], body: String) =
    _post(url, headers, new StringEntity(body, "UTF-8"))

  @throws(classOf[Exception])
  def postForm(url: String, headers: Map[String, Any], body: Map[String, Any]) = {
    val nameValuePairs = new util.ArrayList[NameValuePair](body.size)
    body foreach {
      case (key, value) => nameValuePairs.add(new BasicNameValuePair(key, value.toString))
    }
    _post(url, headers, new UrlEncodedFormEntity(nameValuePairs, "UTF-8"))
  }

  def getRequestAttribute[T: Manifest](key: String): T = {
    RequestContextHolder.currentRequestAttributes()
                        .getAttribute(key, RequestAttributes.SCOPE_REQUEST)
                        .asInstanceOf[T]
  }
}
