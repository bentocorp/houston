package org.bentocorp.dispatch;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.bentocorp.aws.Order;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Address {

    public String street;

    public String residence;

    public String city;

    public String region; // province, state, or territory

    public String zipCode;

    public String country;

    public Float lat = null;

    public Float lng = null;

    public String formatForMapbox() {
        // 427 Stockton St, San Francisco, 94108, California, United States
        return String.join(", ", street, city, zipCode, region, country);
    }

    public static Address parse(Order.Address address) {
        return new Address(
            address.number + " " + address.street,
            null,
            address.city,
            address.state,
            address.zip,
            "United States"
        );
    }

    public static Integer extractNumberFromStreet(String street) {
        String[] parts = street.split(" ");
        if (parts.length >= 3) {
            return new Integer(parts[0]);
        } else {
            return null;
        }
        /*
        Pattern pattern = Pattern.compile("^([1-9][0-9a-zA-Z])\\s");
        Matcher matcher = pattern.matcher(street);
        if (matcher.find()) {
            return new Integer(matcher.group(0));
        } else {
            return null;
        }
        */
    }

    public static String extractNameFromStreet(String street) {
        Integer streetNumber = Address.extractNumberFromStreet(street);
        if (streetNumber != null) {
            return street.replaceFirst(streetNumber.toString(), "").trim();
        } else {
            return street;
        }
    }

    @JsonCreator
    public Address(@JsonProperty("street")    String street,
                   @JsonProperty("residence") String residence,
                   @JsonProperty("city")      String city,
                   @JsonProperty("region")    String region,
                   @JsonProperty("zipCode")   String zipCode,
                   @JsonProperty("country")   String country) {
        this.street = street;
        this.residence = residence;
        this.city = city;
        this.region = region;
        this.zipCode = zipCode;
        this.country = country;
    }
}
