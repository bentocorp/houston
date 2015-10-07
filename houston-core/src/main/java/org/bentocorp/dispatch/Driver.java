package org.bentocorp.dispatch;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;

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

    public final ReadWriteLock lock = new ReentrantReadWriteLock();
    private Status status;
    private List<Long> orderQueue = new ArrayList<Long>();

    public Driver(long id, String name, String phone, Status status) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.status = status;
    }

    @JsonProperty("orderQueue")
    public List<Long> getOrderQueue() { return new ArrayList<Long>(orderQueue); }

    public void setOrderQueue(List<Long> orderQueue) {
        Lock w = lock.writeLock();
        w.lock();
        try {
            this.orderQueue = orderQueue;
        } finally {
            w.unlock();
        }
    }

    @JsonProperty("status")
    public Status getStatus() { return status; }

    public void setStatus(Status status) {
        Lock w = lock.writeLock();
        w.lock();
        try {
            this.status = status;
        } finally {
            w.unlock();
        }
    }

    @Override
    public String toString() {
        return String.format(
            "Driver %s - %s",
            id,
            StringUtils.join(orderQueue, ",")
        );
    }
}
