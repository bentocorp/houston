package org.bentocorp.houston.util

import java.util

import org.apache.commons.io.IOUtils
import org.apache.http.NameValuePair
import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.client.HttpClient
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.HttpPost
import org.apache.http.impl.client.{BasicCredentialsProvider, HttpClientBuilder}
import org.apache.http.message.BasicNameValuePair
import org.slf4j.{Logger, LoggerFactory}

object EmailUtils {

    final val MAILGUN_API = "https://api.mailgun.net/v3/mg.bentonow.com/messages"

    val logger: Logger = LoggerFactory.getLogger(EmailUtils.getClass)

    /**
     * Send an email via POST request with basic username/password authentication
     */
    def send(username: String,
             password: String,
             from: String,
             to: String,
             subject: String,
             html: String): (Int, String) = {

        val credentials = new UsernamePasswordCredentials(username, password)

        val provider = new BasicCredentialsProvider
        provider.setCredentials(AuthScope.ANY, credentials)

        val client: HttpClient = HttpClientBuilder.create().setDefaultCredentialsProvider(provider).build()

        val nameValuePairs = new util.ArrayList[NameValuePair](4) // from, to, subject, html
        nameValuePairs.add(new BasicNameValuePair("from", from))
        nameValuePairs.add(new BasicNameValuePair("to"  , to))
        //nameValuePairs.add(new BasicNameValuePair("cc"  , ""))
        //nameValuePairs.add(new BasicNameValuePair("bcc" , ""))
        nameValuePairs.add(new BasicNameValuePair("subject", subject))
        //nameValuePairs.add(new BasicNameValuePair("text", ""))
        nameValuePairs.add(new BasicNameValuePair("html", html))

        val post = new HttpPost(MAILGUN_API)
        post.setEntity(new UrlEncodedFormEntity(nameValuePairs, "UTF-8"))

        val res = client.execute(post)

        (res.getStatusLine.getStatusCode, IOUtils.toString(res.getEntity.getContent))
    }
}
