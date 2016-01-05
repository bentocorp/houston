package org.bentocorp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.ArrayList;

public class Bento extends ArrayList<Bento.BentoObjectWrapper> {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "item_type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = BentoBox.class, name = "CustomerBentoBox"),
            @JsonSubTypes.Type(value = AddOnList.class, name = "AddonList")
    })
    public static abstract class BentoObjectWrapper {
        // For polymorphic-type deserialization purposes
    }

    public Bento() {
        super();
    }

    /*
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("");
        for (int i = 0; i < this.size(); i++) {
            List<BentoBox.Item> items = this.get(i).items;
            builder.append(String.format(
                "Bento Box %s\n" +
                "-----------\n" +
                "main : (%s) %s\n" +
                "side1: (%s) %s\n" +
                "side2: (%s) %s\n" +
                "side3: (%s) %s\n" +
                "side4: (%s) %s\n\n",
                i + 1,
                items.get(0).label, items.get(0).name,
                items.get(1).label, items.get(1).name,
                items.get(2).label, items.get(2).name,
                items.get(3).label, items.get(3).name,
                items.get(4).label, items.get(4).name
            ));
        }
        return builder.toString();
    }
    */
}
