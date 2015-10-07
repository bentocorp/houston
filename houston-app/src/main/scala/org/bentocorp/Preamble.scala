package org.bentocorp

import java.net.URLEncoder

import org.apache.commons.io.IOUtils
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import org.slf4j.LoggerFactory

object Preamble {

  object Http {

    final val Logger = LoggerFactory.getLogger(Http.getClass)

    private val client = HttpClientBuilder.create().build()

    def get(url: String, params: (String, Any)*): String = {
      val queryString = (params map {
        case (key, value) => key + "=" + URLEncoder.encode(value.toString, "UTF-8")
      }).mkString("&")
      //Logger.debug(url + "?" + queryString)
      val res = client.execute(new HttpGet(url + "?" + queryString))
      val statusCode = res.getStatusLine.getStatusCode
      if (statusCode != 200) {
        val stackTrace = Thread.currentThread.getStackTrace
        Logger.error("Error - %s?%s failed with status code %s" format (url, queryString, statusCode), stackTrace)
        null
      } else {
        IOUtils.toString(res.getEntity.getContent)
      }
    }
  }

  def normalize_phone(phone: String, ccIso: String = "US") = {
    var res = phone.replaceAll("\\(|\\)|\\-|\\s", "")
    if ("US".equals(ccIso)) {
      if (res.charAt(0) != '+') {
        if (res.length <= 10) {
          res = "1" + res
        }
        res = "+" + res
      }
    }
    res
  }
}
