package org.bentocorp

import java.net.URLEncoder

import org.apache.commons.io.IOUtils
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import org.slf4j.LoggerFactory

object Preamble {

  object Http {

    final val Logger = LoggerFactory.getLogger(Http.getClass)

    final val HTTP_OK = 200

    private val _client = HttpClientBuilder.create().build()

    def get(url: String, params: (String, Any)*): String = {
      val queryString = params.map(_ match {
        case (key, value) => key + "=" + URLEncoder.encode(value.toString, "UTF-8")
      }).mkString("&")
      val res = _client.execute(new HttpGet(url + "?" + queryString))
      val statusCode = res.getStatusLine.getStatusCode
      if (statusCode != HTTP_OK) {
        Logger.error("")
        null
      } else {
        IOUtils.toString(res.getEntity.getContent)
      }
    }
  }
}
