package org.bentocorp;

import com.fasterxml.jackson.annotation.*;
import org.bentocorp.dispatch.Address;
import org.bentocorp.houston.util.PhoneUtils;
import org.bentocorp.houston.util.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Order<T> {

    Logger logger = LoggerFactory.getLogger(this.getClass());

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

    public String firstName;

    public String lastName;

    public Address address;

    public String phone;

    public final T item;

    // This public field is a JSON placeholder for what will show in the Driver App. The string is constructed in the
    // PHP backend and set here during JSON deserialization
    public String orderString = "";

    public String notes = "";

    //Irrelevant if orders are stored in distributed cache
//    @JsonIgnore
//    public final ReadWriteLock lock = new ReentrantReadWriteLock();

    private Long driverId = -1L;

    private Status status = Status.UNASSIGNED;
    //public int priority;

    public Long createdAt = null;

    /* Order-Ahead */
    public boolean isOrderAhead = false;
    public Long scheduledWindowStart = null; // Stored in the database as Epoch
    public Long scheduledWindowEnd   = null;
    public String scheduledTimeZone = null;

    public static Order<Bento> parse(org.bentocorp.aws.Order o) throws Exception {
        Address address = Address.parse(o.details.address);
        address.lat = o.details.coords.lat;
        address.lng = o.details.coords.lng;
        Bento bentoOrder = new Bento();
        for (Bento.BentoObjectWrapper box: o.items) {
            bentoOrder.add(box);
        }
        Order<Bento> order = new Order<Bento>(
            "o-" + o.orderId,
            o.user.firstname,
            o.user.lastname,
            PhoneUtils.normalize(o.user.phone),
            address,
            bentoOrder
        );
        order.orderString = o.orderString;

        /* Order Ahead */
        if (Integer.parseInt(o.orderType) == 2) {
            order.isOrderAhead = true;
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            format.setTimeZone(TimeZone.getTimeZone(o.scheduledTimeZone));
            order.scheduledWindowStart = format.parse(o.date + " " + o.scheduledWindowStart).getTime();
            order.scheduledWindowEnd = format.parse(o.date + " " + o.scheduledWindowEnd).getTime();
            order.scheduledTimeZone = o.scheduledTimeZone;
        }

        return order;
    }

    @JsonCreator
    public Order(@JsonProperty("id")        String  id,
                 @JsonProperty("firstName") String  firstName,
                 @JsonProperty("lastName")  String  lastName,
                 @JsonProperty("phone")     String  phone,
                 @JsonProperty("address")   Address address,
                 @JsonProperty("item")      T       item) throws Exception {
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
        this.firstName = firstName;
        this.lastName = lastName;
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

    // For backward compatibility so a code change is not required in the driver app
    @JsonProperty("name")
    public String getName() {
        return firstName + " " + lastName;
    }

    // Serialize only. Useful for Atlas to determine which shift this order belongs to
    @JsonProperty("shift")
    public Integer getShift() {
        try {
            if (isOrderAhead) {
                return Shift.parse(scheduledWindowStart, TimeZone.getTimeZone(scheduledTimeZone)).ordinal();
            }
        } catch (Exception e) {
            // Log something here
            logger.error(e.getMessage() + " (" + id + ")", e);
        }
        // Not enough information available to determine shift, or error occurred during calculations
        return null;
    }

    @JsonProperty("date")
    public String getDate() {
        try {
            if (isOrderAhead) {
                return TimeUtils.getLocalDate(scheduledWindowStart, TimeZone.getTimeZone(scheduledTimeZone)).format(
                        DateTimeFormatter.ofPattern("MM/dd/yyyy")
                );
            }
        } catch (Exception e) {
            logger.error(e.getMessage() + " (" + id + ")", e);
        }
        return null;

    }
}
