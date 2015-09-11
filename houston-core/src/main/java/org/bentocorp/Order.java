package org.bentocorp;

import org.bentocorp.dispatch.Address;

public class Order {

    public enum Status {
        REJECTED, UNASSIGNED, ACCEPTED, MODIFIED, COMPLETE
    }

    public Order() {
        this.address = new Address();
    }

    public Order(long id, String name, Address address, String body) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.body = body;
        this.driverId = null;
        this.status = Status.UNASSIGNED;
    }

    public long id;
    public String name;
    public Address address;
    public String body;
    public String driverId;
    public Status status;
}
