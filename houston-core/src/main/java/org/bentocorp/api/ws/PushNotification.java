package org.bentocorp.api.ws;

public class PushNotification<T> {
    public String origin;
    public String target;
    public String subject;
    public T body;

    public PushNotification(String subject, T body) {
        this.subject = subject;
        this.body = body;
    }

    public PushNotification from(String sender) {
        origin = sender;
        return this;
    }

    public PushNotification to(String recipient) {
        target = recipient;
        return this;
    }
}
