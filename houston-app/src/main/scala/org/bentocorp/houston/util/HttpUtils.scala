package org.bentocorp.houston.util

import java.net.URLEncoder
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl._
import javax.security.cert.CertificateException

import org.apache.commons.io.IOUtils
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.{CloseableHttpClient, HttpClientBuilder}
import org.slf4j.LoggerFactory

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
}
