package org.bentocorp.houston.dispatch.routific;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Input {

    public Map<String, Visit> visits;

    public Map<String, Driver> fleet;

    public Map<String, Object> options;

    public Input() {
        // Empty constructor so Jackson can deserialize
    }

    public Input(Map<String, Visit> visits, Map<String, Driver> fleet, Map<String, Object> options) {
        this.visits = visits;
        this.fleet = fleet;
        this.options = options;
    }
}
