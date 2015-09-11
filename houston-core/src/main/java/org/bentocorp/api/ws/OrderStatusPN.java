package org.bentocorp.api.ws;

import org.bentocorp.Order;

public class OrderStatusPN {
    public static final String SUBJECT = "order_status";

    public long orderId;
    public Order.Status status;
    public String driverId;

    public OrderStatusPN(long orderId, Order.Status status, String driverId) {
        this.orderId = orderId;
        this.status = status;
        this.driverId = driverId;
    }

    public static PushNotification<OrderStatusPN> make(long orderId, Order.Status status, String driverId) {
        return new PushNotification<OrderStatusPN>(
            SUBJECT, new OrderStatusPN(orderId, status, driverId)
        );
    }
}
