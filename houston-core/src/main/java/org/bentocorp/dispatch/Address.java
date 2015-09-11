package org.bentocorp.dispatch;

public class Address {
    public String street;
    public String city;
    public String region;
    public String zipCode;
    public String country;

    public Address() {

    }

    public Address(String street, String city, String region, String zipCode, String country) {
        this.street = street;
        this.city = city;
        this.region = region;
        this.zipCode = zipCode;
        this.country = country;
    }
}
