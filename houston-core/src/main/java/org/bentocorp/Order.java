package org.bentocorp;

import org.bentocorp.dispatch.Address;

public class Order<T> {

    public enum Status {
        REJECTED, UNASSIGNED, PENDING, ACCEPTED, MODIFIED, COMPLETE
    }

    public long id;
    public String name;
    public Address address;
    public T item;
    public Long driverId; // can be null if unassigned
    public Status status;
//    public int priority;

    public Order() {

    }
    public Order(long id, String name, Address address, T item) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.item = item;
        driverId = null;
        status = Status.UNASSIGNED;
        // Should this be used at all? If so, modifications to an order's
        // position may trigger a database update for every other order
        // before or after. For now, ignore.
//        priority = 0;
    }
}
