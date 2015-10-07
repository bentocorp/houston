package org.bentocorp;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BentoBox {

    public List<Item> items = new ArrayList<Item>();

    public static class Item {

        public enum Temp {
            // Enum fields required for finer-grain control over how these values are stored in the database
            HOT("hot"), COLD("cold");

            String value;

            Temp(String value) { this.value = value; }

            public static Temp parse(String value) throws Exception {
                Temp[] values = Temp.values();
                for (Temp T: values) {
                    if (T.value.equals(value)) {
                        return T;
                    }
                }
                throw new Exception(String.format("Temp(%s) not found", value));
            }
        }

        public enum Type {
            // Enum fields required for finer-grain control over how these values are stored in the database
            MAIN("main"), SIDE("side"), ADD_ON("add_on");

            String value;

            Type(String value) { this.value = value; }

            // We don't use valueOf because the database values don't always exactly match the identifiers used to
            // declare the enum constants
            public static Type parse(String value) throws Exception {
                Type[] values = Type.values();
                for (Type T: values) {
                    if (T.value.equals(value)) {
                        return T;
                    }
                }
                throw new Exception(String.format("Type(\"%s\") not found", value));
            }

            @JsonValue
            @Override
            public String toString() {
                return value;
            }
        }

        public final long id;

        public final String name;

        public final Type type;

        public final String label;

        @JsonCreator
        public Item(@JsonProperty("id")    long   id,
                    @JsonProperty("name")  String name,
                    @JsonProperty("type")  String type,
                    @JsonProperty("label") String label) throws Exception {
            this.id = id;
            this.name = name;
            this.type = Type.parse(type);
            this.label = label;
        }
    }

    public BentoBox() { }

    public BentoBox add(Item item) {
        items.add(item);
        return this;
    }
}
