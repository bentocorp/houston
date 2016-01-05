package org.bentocorp;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class AddOnList extends Bento.BentoObjectWrapper {

    public List<AddOn> items = new ArrayList<AddOn>();

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AddOn {
        public final long id;
        public final String name;
        public final int qty;

        @JsonCreator
        public AddOn(@JsonProperty("id")   long   id,
                     @JsonProperty("name") String name,
                     @JsonProperty("qty")  int    qty) {
            this.id = id;
            this.name = name;
            this.qty = qty;
        }
    }
}
