package org.bentocorp.api.ws;

public class Push<T> {
    // This is an optional request identifier provided by the client and returned by the server so that the client can
    // identify which push notifications correspond to which GET requests
    public String rid = "";

    public String from;

    // There are three possible recipient types - global, group, personal
    public String to;

    public String subject;

    public T body;

    public long timestamp;

    public Push() {
        timestamp = System.currentTimeMillis();
    }

    public Push(String subject, T body) {
        this.subject = subject;
        this.body = body;
    }

    public Push from(String sender) {
        from = sender;
        return this;
    }

    public Push toRecipient(String recipient) {
        // TODO - Must investigate how URL strings are interpreted as JSON >:@
        to = "[\""+recipient+"\"]";
        return this;
    }

    public Push toGroup(String group) {
        to = "\"" + group + "\"";
        return this;
    }

    public Push toAll() {
        to = "\"*\"";
        return this;
    }

    public Push rid(String rid) {
        this.rid = rid;
        return this;
    }
}
