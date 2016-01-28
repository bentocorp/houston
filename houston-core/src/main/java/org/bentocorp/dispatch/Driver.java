package org.bentocorp.dispatch;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;
import org.bentocorp.Shift;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Driver {

    public enum Status {
        // Stored in database as integers equal to enum ordinals
        OFFLINE, ONLINE
    }

    public final long id;
    public final String name;
    public final String phone;

//    public final ReadWriteLock lock = new ReentrantReadWriteLock();
    private Status status;
    private List<String> orderQueue = new ArrayList<String>();

    /* Order Ahead */

    // Overall delivery speed (forwarded to Routific in JSON for routing)
    public enum Speed {

        FASTER(1.50F), FAST(1.25F), NORMAL(1.00F), SLOW(0.75F), VERY_SLOW(0.50F), BIKE(0.25F);

        public float value; // Raw value may be from 0.1 to 2 (inclusively)

        Speed(float value) {
            this.value = value;
        }
    }

    public float speed = Speed.NORMAL.value;

    public Shift.Type shiftType;

    public Driver(long id, String name, String phone, Status status, Shift.Type shiftType) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        // TODO - Remove status flag since we don't need it anymore if we re-track with Node after syncing
        this.status = status;
        this.shiftType = shiftType;
    }

    @JsonProperty("orderQueue")
    public List<String> getOrderQueue() { return new ArrayList<String>(orderQueue); }

    public void setOrderQueue(List<String> orderQueue) {
//        Lock w = lock.writeLock();
//        w.lock();
//        try {
            this.orderQueue = orderQueue;
//        } finally {
//            w.unlock();
//        }
    }

    @JsonProperty("status")
    public Status getStatus() { return status; }

    public void setStatus(Status status) {
//        Lock w = lock.writeLock();
//        w.lock();
//        try {
            this.status = status;
//        } finally {
//            w.unlock();
//        }
    }

    @Override
    public String toString() {
        return String.format(
            "Driver %s - %s",
            id,
            StringUtils.join(orderQueue, ",")
        );
    }

    @JsonIgnore
    public String getLockId() {
        // redis-driver#678
        return "redis-driver#" + id;
    }
}
