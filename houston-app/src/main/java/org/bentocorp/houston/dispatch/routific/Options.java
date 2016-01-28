package org.bentocorp.houston.dispatch.routific;

import java.util.HashMap;

public class Options extends HashMap<String, Object> {

    public Options with(String key, Object value) {
        this.put(key, value);
        return this;
    }
}
