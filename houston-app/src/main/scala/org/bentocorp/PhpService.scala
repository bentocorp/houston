package org.bentocorp

import java.net.URLEncoder
import java.security.cert.X509Certificate
import javax.annotation.PostConstruct
import javax.net.ssl.{HostnameVerifier, SSLContext, SSLSocketFactory}

import org.apache.commons.io.IOUtils
import org.apache.http.client.HttpClient
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.config.{Registry, RegistryBuilder}
import org.apache.http.conn.socket.{PlainConnectionSocketFactory, ConnectionSocketFactory}
import org.apache.http.conn.ssl.{SSLConnectionSocketFactory, TrustStrategy}
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.ssl.SSLContextBuilder
import org.bentocorp.houston.config.BentoConfig
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class PhpService {

  final val Logger = LoggerFactory.getLogger(classOf[PhpService])

  @Autowired
  var config: BentoConfig = null

  var phpUrl: String = null

  var client: HttpClient = null

  @PostConstruct
  def init() {
    phpUrl = config.getString("php.host")
    val builder = HttpClientBuilder.create()

      if (!"prod".equals(config.getString("env"))) {
        Logger.info("Using custom-configured HttpClient that accepts all certificates and ignores host name")
        // Set up TrustStrategy that allows all certificates
        val sslContext: SSLContext = new SSLContextBuilder().loadTrustMaterial(null,  new TrustStrategy {
          override def isTrusted(x509Certificates: Array[X509Certificate], s: String): Boolean = true
        }).build()
        builder.setSSLContext(sslContext)
        // Don't check hostnames
        val hostnameVerifier: HostnameVerifier = SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER
        val sslSocketFactory: SSLConnectionSocketFactory = new SSLConnectionSocketFactory(sslContext, hostnameVerifier)

        val socketFactoryRegistry: Registry[ConnectionSocketFactory] = RegistryBuilder.create[ConnectionSocketFactory]()
          .register("http", PlainConnectionSocketFactory.getSocketFactory())
          .register("https", sslSocketFactory)
          .build()
        // now, we create connection-manager using our Registry.
        //      -- allows multi-threaded use
        val connMgr: PoolingHttpClientConnectionManager = new PoolingHttpClientConnectionManager( socketFactoryRegistry)
        builder.setConnectionManager( connMgr)

      }
    client = builder.build()
  }

  def delete(orderId: String, token: String): Boolean = {
//    return true
    val url = "https://%s/adminapi/order/cancel/%s?api_token=%s" format (phpUrl, orderId, token)
    Logger.debug(url)
    val httpGet = new HttpGet(url)
    val config = RequestConfig.custom().setConnectionRequestTimeout(1000).setConnectTimeout(1000).build()
    httpGet.setConfig(config)
    try {
      val res = client.execute(httpGet)
      val statusCode = res.getStatusLine.getStatusCode
      if (statusCode != 200) {
        Logger.debug("PhpService#delete failed with status code " + statusCode)
        false
      } else {
        Logger.debug("PhpService#delete success - " + IOUtils.toString(res.getEntity.getContent))
        true
      }
    } finally {
      httpGet.releaseConnection()
    }
  }

  def assign(orderId: String, driverId: Long, afterId: String, token: String): Boolean = {
//    return true
//    Logger.debug("PhpService#assign(%s, %s, %s)" format (orderId, driverId, afterId))
    // Normalize
    val normalizedAfterId = if (afterId == null) "-1" else afterId;

    val url = "https://%s/adminapi/order/assign/%s/%s/%s?api_token=%s".format(
      phpUrl,orderId, driverId, normalizedAfterId, URLEncoder.encode(token, "UTF-8"))
    Logger.debug(url)
    val request = new HttpGet(url)
    val config = RequestConfig.custom()
                              .setConnectTimeout(1000)
                              .setConnectionRequestTimeout(1000)
                              .build()
    request.setConfig(config)
    try {
      val res = client.execute(request)
      val statusCode = res.getStatusLine.getStatusCode
      if (statusCode != 200) {
        Logger.debug("Failed with status code " + statusCode)
        false
      } else {
        Logger.debug("Success - " + IOUtils.toString(res.getEntity.getContent))
        true
      }
    } finally {
      request.releaseConnection()
    }
  }
}
