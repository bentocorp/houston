package org.bentocorp.dispatch;

import java.util.ArrayList;
import java.util.List;

public class Driver {

    public enum Status {
        OFFLINE, ONLINE
    }

    public long id;
    public String name;
    public String phone;
    public Status status;
    public List<Long> orderQueue;

    public Driver(long id, String name, String phone, Status status) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.status = status;
        this.orderQueue = new ArrayList<Long>();
    }
}
