package org.bentocorp.dispatch;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.bentocorp.aws.Order;

public class Address {

    final public String street;

    final public String residence;

    final public String city;

    final public String region; // province, state, or territory

    final public String zipCode;

    final public String country;

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
