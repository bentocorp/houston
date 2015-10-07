package org.bentocorp;

import java.util.ArrayList;
import java.util.List;

public class Bento {

    public final List<BentoBox> bentoBoxes = new ArrayList<BentoBox>();

    public Bento() { }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("");
        for (int i = 0; i < bentoBoxes.size(); i++) {
            List<BentoBox.Item> items = bentoBoxes.get(i).items;
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
}
