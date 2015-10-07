package org.bentocorp.aws;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.bentocorp.BentoBox;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Order {

    @JsonProperty("pk_Order")
    public long orderId;

    @JsonProperty("OrderItems")
    public BentoBox[] items;

    @JsonProperty("OrderDetails")
    public OrderDetails details;

    @JsonProperty("User")
    public User user;

    public Order() { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrderDetails {
        public Address address;
        public Coords coords;
    }

    public static class Coords {

        public float lat;

        @JsonProperty("long")
        public float lng;

        public Coords() { }
    }

    public static class Address {
        public String number;
        public String street;
        public String city;
        public String state;
        public String zip;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class User {
        public String firstname;
        public String lastname;
        public String phone;
    }
}
