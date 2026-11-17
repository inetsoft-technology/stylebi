/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.report.composition.graph;

import inetsoft.graph.EGraph;
import inetsoft.graph.LegendSpec;
import inetsoft.graph.aesthetic.LinearColorFrame;
import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.graph.guide.legend.LegendGroup;
import inetsoft.graph.internal.GDefaults;
import inetsoft.graph.internal.GTool;

import java.util.HashSet;
import java.util.Set;

/**
 * Counts the entries a graph's legends show, to decide whether a modern legend is crowded.
 */
public final class LegendEntryCount {
   /**
    * A legend with more entries than this is crowded.
    */
   public static final int CROWDED_ABOVE = 5;

   private LegendEntryCount() {
   }

   /**
    * The entries across every drawn legend, after hidden values and the max count.
    */
   public static int count(EGraph graph) {
      // the same limit Legend.createItems applies
      int max = Integer.parseInt(GTool.getProperty("graph.legend.maxcount",
                                                   GDefaults.LEGEND_MAX_COUNT + ""));
      int total = 0;
      Set<String> added = new HashSet<>();

      for(VisualFrame candidate : graph.getVisualFrames()) {
         // expanded and de-duplicated as LegendGroup builds its legends
         for(VisualFrame frame : LegendGroup.getLegendFrames(candidate)) {
            String id = frame.getUniqueId() + ":" + frame.getClass().getName();

            // a ramp is decided by the binding
            if(!added.add(id) || frame instanceof LinearColorFrame) {
               continue;
            }

            total += count(frame, max);
         }
      }

      return total;
   }

   // the items Legend.createItems makes for one legend frame
   private static int count(VisualFrame frame, int max) {
      Object[] labels = frame.getLabels();
      Object[] values = frame.getValues();
      LegendSpec spec = frame.getLegendSpec();
      int cnt = Math.min(labels.length, max);
      int total = 0;

      for(int i = 0; i < cnt; i++) {
         Object label = labels[i] == null ? values[i] : labels[i];

         if(spec.isVisible(label)) {
            total++;
         }
      }

      return total;
   }

   /**
    * Whether the legends show more than {@link #CROWDED_ABOVE} entries.
    */
   public static boolean crowded(EGraph graph) {
      return count(graph) > CROWDED_ABOVE;
   }
}
