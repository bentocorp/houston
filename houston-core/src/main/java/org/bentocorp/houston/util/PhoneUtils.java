package org.bentocorp.houston.util;

public class PhoneUtils {

    public enum CountryCode {
        US
    }

    public static String normalize(String phone) {
        return PhoneUtils.normalize(phone, CountryCode.US);
    }

    public static String normalize(String phone, CountryCode cc) {
        String res = phone.replaceAll("\\(|\\)|\\-|\\s", "");
        if (!res.isEmpty() && cc == CountryCode.US) {
            if (res.charAt(0) != '+') {
                if (res.length() <= 10) {
                    res = "1" + res;
                }
                res = "+" + res;
            }
        }
       return res;
    }
}
