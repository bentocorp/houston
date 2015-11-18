package org.bentocorp.houston.util

object PhoneUtils {
  def normalize_phone(phone: String, ccIso: String = "US"): String = {
    if(phone.isEmpty) {
      return ""
    }
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
