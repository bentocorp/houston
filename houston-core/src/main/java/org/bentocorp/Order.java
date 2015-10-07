package org.bentocorp;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.bentocorp.dispatch.Address;

import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Order<T> {

    public enum Status {
        // protected "value" property required because of how existing order statuses are stored in the database
        REJECTED("Rejected"), UNASSIGNED("Open"), PENDING("Assigned"), ACCEPTED("En Route"), MODIFIED("Modified"),
        COMPLETE("Delivered"), CANCELLED("Cancelled");

        String value;

        Status(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }

        public static Status parse(String value) throws Exception {
            Status[] values = Status.values();
            for (Status S: values) {
                if (S.value.equals(value)) {
                    return S;
                }
            }
            throw new Exception(String.format("Status(%s) not found" , value));
        }
    }

    public final long id;

    public final String name;

    public final Address address;

    public String phone;

    public final T item;

    @JsonIgnore
    public final ReadWriteLock lock = new ReentrantReadWriteLock();

    private Long driverId = null;

    private Status status = Status.UNASSIGNED;
    //public int priority;


    public static Order<List<BentoBox>> parse(org.bentocorp.aws.Order o) throws Exception {
        Address address = Address.parse(o.details.address);
        address.lat = o.details.coords.lat;
        address.lng = o.details.coords.lng;
        return null;/*
        return new Order<String>(
            o.orderId,
            o.user.firstname + " " + o.user.lastname,
            o.user.phone,
            address,
            ""
        );*/
    }

    @JsonCreator
    public Order(@JsonProperty("id") long id,
                 @JsonProperty("name") String name,
                 @JsonProperty("phone") String phone,
                 @JsonProperty("address") Address address,
                 @JsonProperty("item") T item) throws Exception {
        // Safety check for JSON deserialization
        if (id <= 0) {
            String msg = "Invalid order ID %s - If this was thrown during JSON deserialization, check that the " +
                         "\"id\" field is present in the JSON.";
            throw new Exception(msg);
        }
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.address = address;
        this.item = item;
        // Should this be used at all? If so, modifications to an order's
        // position may trigger a database update for every other order
        // before or after. For now, ignore.
//        priority = 0;
    }

    public Long getDriverId() {
        return driverId;
    }

    public Status getStatus() {
        return status;
    }

    public void setDriverId(long driverId) {
        Lock w = lock.writeLock();
        w.lock();
        try {
            this.driverId = driverId;
        } finally {
            w.unlock();
        }
    }

    public void setStatus(Status status) {
        Lock w = lock.writeLock();
        w.lock();
        try {
            this.status = status;
        } finally {
            w.unlock();
        }
    }

    public void setDriverIdWithStatus(Long driverId, Status status) throws Exception {
        if (driverId != null && driverId > 0 && (status == Order.Status.CANCELLED || status == Order.Status.UNASSIGNED)) {
            String msg = String.format("Incompatible order state - driverId=%s, status=%s", driverId, status);
            throw new Exception(msg);
        }
        Lock w = lock.writeLock();
        w.lock();
        try {
            this.driverId = driverId;
            this.status = status;
        } finally {
            w.unlock();
        }
    }

    @Override
    public String toString() {
        return String.format(
            "Order(id=%s,driverId=%s)",
            id, getDriverId()
        );
    }
}
