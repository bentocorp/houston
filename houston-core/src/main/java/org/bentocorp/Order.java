package org.bentocorp;

import com.fasterxml.jackson.annotation.*;
import org.bentocorp.dispatch.Address;

import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@JsonIgnoreProperties(ignoreUnknown = true)
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

    public final String id; // b-9670 or g-5678
    public final String orderType;
    public final long key;

    public final String name;

    public final Address address;

    public String phone;

    public final T item;

    public String orderString = "";

    //Irrelevant if orders are stored in distributed cache
//    @JsonIgnore
//    public final ReadWriteLock lock = new ReentrantReadWriteLock();

    private Long driverId = -1L;

    private Status status = Status.UNASSIGNED;
    //public int priority;

    static String normalize_phone(String phone) {
        if(phone.isEmpty()) {
            return "";
        }
        String res = phone.replaceAll("\\(|\\)|\\-|\\s", "");

        if (res.charAt(0) != '+') {
                if (res.length() <= 10) {
                    res = "1" + res;
                }
                res = "+" + res;
            }
        return res;
    }

    public static Order<Bento> parse(org.bentocorp.aws.Order o) throws Exception {
        Address address = Address.parse(o.details.address);
        address.lat = o.details.coords.lat;
        address.lng = o.details.coords.lng;
        Bento bentoOrder = new Bento();
        for (BentoBox box: o.items) {
            bentoOrder.add(box);
        }
        Order<Bento> order = new Order<Bento>(
            "o-" + o.orderId,
            o.user.firstname + " " + o.user.lastname,
            normalize_phone(o.user.phone),
            address,
            bentoOrder
        );
        order.orderString = o.orderString;
        return order;
    }

    @JsonCreator
    public Order(@JsonProperty("id") String id,
                 @JsonProperty("name") String name,
                 @JsonProperty("phone") String phone,
                 @JsonProperty("address") Address address,
                 @JsonProperty("item") T item) throws Exception {
        // Safety check for JSON deserialization
        if (id.isEmpty()) {
            String msg = "Invalid order ID %s - If this was thrown during JSON deserialization, check that the " +
                         "\"id\" field is present in the JSON.";
            throw new Exception(msg);
        }
        this.id = id;
        String[] parts = id.split("-");
        this.orderType = parts[0];
        this.key = Long.parseLong(parts[1]);
        this.name = name;
        this.phone = phone;
        this.address = address;
        this.item = item;
        // Should this be used at all? If so, modifications to an order's
        // position may trigger a database update for every other order
        // before or after. For now, ignore.
//        priority = 0;
    }

    @JsonIgnore
    public String getOrderType() {
        return orderType;
    }

    @JsonIgnore
    public long getOrderKey() {
        return key;
    }

    public Long getDriverId() {
        return driverId;
    }

    public Status getStatus() {
        return status;
    }

    // If argument is long and the JSON is null, Jackson will deserialize with 0
    // Use a primitive wrapper to force null cases to -1
    public void setDriverId(Long driverId) {
//        Lock w = lock.writeLock();
//        w.lock();
        try {
            if (driverId == null) {
                this.driverId = -1l;
            } else {
                this.driverId = driverId;
            }
        } finally {
//            w.unlock();
        }
    }

    public void setStatus(Status status) {
//        Lock w = lock.writeLock();
//        w.lock();
        try {
            this.status = status;
        } finally {
//            w.unlock();
        }
    }

    public void setDriverIdWithStatus(Long driverId, Status status) throws Exception {
        if (driverId != null && driverId >= 0 && (status == Order.Status.CANCELLED || status == Order.Status.UNASSIGNED)) {
            String msg = String.format("Incompatible order state - orderId=%s, driverId=%s, status=%s", id, driverId, status);
            throw new Exception(msg);
        }
//        Lock w = lock.writeLock();
//        w.lock();
        try {
            this.driverId = driverId;
            this.status = status;
        } finally {
//            w.unlock();
        }
    }

    @Override
    public String toString() {
        return String.format(
            "Order(id=%s,driverId=%s)",
            id, getDriverId()
        );
    }

    @JsonProperty("@class")
    public String includeTypeInfo() {
        return item.getClass().getSimpleName();
    }

    @JsonIgnore
    public String getLockId() {
        // redis-order#b-890
        return "redis-order#" + id;
    }
}
