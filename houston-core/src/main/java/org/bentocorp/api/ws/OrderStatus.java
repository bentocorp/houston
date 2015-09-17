package org.bentocorp.api.ws;

import org.bentocorp.Order;

public class OrderStatus {
    public static final String SUBJECT = "order_status";

    public long orderId;
    public Order.Status status;

    public OrderStatus() {

    }

    public static Push<OrderStatus> make(long orderId, Order.Status status) {
        OrderStatus p = new OrderStatus();
        p.orderId = orderId;
        p.status = status;
        return new Push<OrderStatus>(SUBJECT, p);
    }
}
