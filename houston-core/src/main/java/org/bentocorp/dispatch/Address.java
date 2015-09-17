package org.bentocorp.dispatch;

public class Address {
    public String street;
    public String residence;
    public String city;
    public String region;
    public String zipCode;
    public String country;
    public Float lat;
    public Float lng;

    public String formatForMapbox() {
        // 427 Stockton St, San Francisco, 94108, California, United States
        return String.join(", ", street, city, zipCode, region, country);
    }

    public Address() {

    }

    public Address(String street, String residence, String city, String region, String zipCode, String country) {
        this.street = street;
        this.residence = residence;
        this.city = city;
        this.region = region;
        this.zipCode = zipCode;
        this.country = country;
        lat = null;
        lng = null;
    }
}
