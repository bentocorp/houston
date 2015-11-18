package org.bentocorp

import java.util
import javax.annotation.PostConstruct

import com.twilio.sdk.TwilioRestClient
import com.twilio.sdk.resource.instance.Account
import org.apache.http.NameValuePair
import org.apache.http.message.BasicNameValuePair
import org.bentocorp.houston.config.BentoConfig
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class SmsSender {
  final val Logger = LoggerFactory.getLogger(classOf[SmsSender])
  @Autowired
  var config: BentoConfig = null

  var twilio: TwilioRestClient = null
  var account: Account = null

  @PostConstruct
  def init() {
    val sid = config.getString("twilio.sid")
    val authToken = config.getString("twilio.token")
    twilio = new TwilioRestClient(sid, authToken)
    account = twilio.getAccount
  }

  def send(phone: String, body: String) {
    val factory = account.getMessageFactory
    val params = new util.ArrayList[NameValuePair]
    params.add(new BasicNameValuePair("To", phone))
    params.add(new BasicNameValuePair("From", "+12513173120"))
    params.add(new BasicNameValuePair("Body", body))
    val sms = factory.create(params) // ?? sends the message
//    Logger.debug("")
  }

}
