package org.bentocorp.dispatch;

public class Driver {

    public enum Status {
        ONLINE, OFFLINE
    }

    public Driver(String id, String name, String phone, Status status) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.status = status;
    }
    public String id;
    public String name;
    public String phone;
    public Status status;
}
