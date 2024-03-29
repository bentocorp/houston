package org.bentocorp.aws;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.bentocorp.Bento;
import org.bentocorp.BentoBox;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Order {

    @JsonProperty("pk_Order")
    public long orderId;

    // Don't bother deserializing the Bento ourselves since the introduction of add-ons
    @JsonProperty("OrderItems")
    public Bento.BentoObjectWrapper[] items;

    @JsonProperty("OrderDetails")
    public OrderDetails details;

    @JsonProperty("OrderString")
    public String orderString;

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

    @JsonProperty("for_date")
    public String date;

    @JsonProperty("order_type")
    public String orderType;

    @JsonProperty("scheduled_window_start")
    public String scheduledWindowStart;

    @JsonProperty("scheduled_window_end")
    public String scheduledWindowEnd;

    @JsonProperty("scheduled_timezone")
    public String scheduledTimeZone;
}
