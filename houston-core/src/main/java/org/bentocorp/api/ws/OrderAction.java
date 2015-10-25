package org.bentocorp.api.ws;

import org.bentocorp.Order;

public class OrderAction {

    public static final String SUBJECT = "order_action";

    public enum Type {
        CREATE, ASSIGN, REPRIORITIZE, UNASSIGN, REASSIGN, UPDATE_STATUS, DELETE
    }

    public OrderAction.Type type;
    public Order<?> order;
    public Long driverId; // -1 if type=Type.UNASSIGN or type=Type.CREATE
    public String after; // null for insert at end

    public static Push<OrderAction> make(OrderAction.Type type, Order<?> order, Long driverId, String after) {
        OrderAction action = new OrderAction();
        action.type = type;
        action.order = order;
        action.driverId = driverId;
        action.after = after;
        return new Push<OrderAction>(SUBJECT, action);
    }
}
