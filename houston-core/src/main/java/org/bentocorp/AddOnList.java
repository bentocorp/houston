package org.bentocorp;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AddOnList extends Bento.BentoObjectWrapper {

    public List<AddOn> items = new ArrayList<AddOn>();

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AddOn
    {
        public final long id;
        public final String name;
        public final int qty;
        public Long pk_OrderItem;

        @JsonCreator
        public AddOn(@JsonProperty("id")   long   id,
                     @JsonProperty("name") String name,
                     @JsonProperty("qty")  int    qty) {
            this.id = id;
            this.name = name;
            this.qty = qty;
        }

        @Override
        public boolean equals(Object o) {
            if ( o == null || !(o instanceof AddOn) ) {
                return false;
            }
            AddOn that = (AddOn) o;
            return (this.id == that.id);
        }

        @Override
        public int hashCode() {
            return (int)(this.id % Integer.MAX_VALUE);
        }

        public void setPkOrderItem(Long pk)
        {
            this.pk_OrderItem = pk;
        }
    }

    public void add(AddOn item)
    {
        items.add(item);
    }


    public String printPkOrderItem()
    {
        if (items.size() > 0) {
            StringBuffer buffer = new StringBuffer(items.get(0).pk_OrderItem + "");
            for (int i = 1; i < items.size(); i++) {
                buffer.append("," + items.get(i).pk_OrderItem);
            }
            return buffer.toString();
        }
        return "empty";
    }

}
