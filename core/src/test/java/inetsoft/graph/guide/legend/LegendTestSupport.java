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
package inetsoft.graph.guide.legend;

import inetsoft.graph.EGraph;
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.coord.RectCoord;

import java.util.ArrayList;
import java.util.List;

/**
 * Fixtures shared by the legend tests: categorical frames with a fixed list of values, legends
 * built from them, and readers for the item grid of a laid out legend.
 */
final class LegendTestSupport {
   private LegendTestSupport() {
   }

   static CategoricalColorFrame color(String field, Object... vals) {
      CategoricalColorFrame frame = new CategoricalColorFrame();
      frame.setField(field);
      frame.init(vals);
      return frame;
   }

   static CategoricalShapeFrame shape(String field, Object... vals) {
      CategoricalShapeFrame frame = new CategoricalShapeFrame();
      frame.setField(field);
      frame.init(vals);
      return frame;
   }

   static CategoricalSizeFrame size(String field, Object... vals) {
      CategoricalSizeFrame frame = new CategoricalSizeFrame();
      frame.setField(field);
      frame.init(vals);
      return frame;
   }

   static CategoricalTextureFrame texture(String field, Object... vals) {
      CategoricalTextureFrame frame = new CategoricalTextureFrame();
      frame.setField(field);
      frame.init(vals);
      return frame;
   }

   static CategoricalLineFrame line(String field, Object... vals) {
      CategoricalLineFrame frame = new CategoricalLineFrame();
      frame.setField(field);
      frame.init(vals);
      return frame;
   }

   /**
    * Create a legend on a graph that has the given legend layout (a GraphConstants value).
    */
   static Legend legend(VisualFrame frame, int layout) {
      return legend(frame, graph(layout));
   }

   static Legend legend(VisualFrame frame, EGraph graph) {
      return Legend.createLegend(frame, graph);
   }

   static EGraph graph(int layout) {
      EGraph graph = new EGraph();
      graph.setLegendLayout(layout);
      // a graph always has a coordinate when it is laid out
      graph.setCoordinate(new RectCoord());
      return graph;
   }

   static void layout(Legend legend, double w, double h) {
      legend.setBounds(0, 0, w, h);
      legend.layout();
   }

   /**
    * The items of the item grid in row-major order, without the empty cells.
    */
   static List<LegendItem> flatten(LegendItem[][] grid) {
      List<LegendItem> list = new ArrayList<>();

      for(LegendItem[] row : grid) {
         for(LegendItem item : row) {
            if(item != null) {
               list.add(item);
            }
         }
      }

      return list;
   }

   static List<LegendItem> items(Legend legend) {
      return flatten(legend.getItems());
   }

   static List<String> labels(Legend legend) {
      List<String> labels = new ArrayList<>();

      for(LegendItem item : items(legend)) {
         labels.add(item.getLabel());
      }

      return labels;
   }

   /**
    * Widest preferred item width of the legend of the frame, measured on a throw away legend (the
    * grid of a legend is cached on first read, so the legend under test must not be used).
    */
   static double maxItemWidth(VisualFrame frame, int layout) {
      Legend probe = legend(frame, layout);
      layout(probe, 1, 1000);
      double w = 0;

      for(LegendItem item : items(probe)) {
         w = Math.max(w, item.getPreferredWidth());
      }

      return w;
   }

   /**
    * Preferred height of the first item of the legend of the frame.
    */
   static double itemHeight(VisualFrame frame, int layout) {
      Legend probe = legend(frame, layout);
      layout(probe, 1, 1000);
      return items(probe).get(0).getPreferredHeight();
   }

   static Object[] values(String prefix, int n) {
      Object[] vals = new Object[n];

      for(int i = 0; i < n; i++) {
         vals[i] = prefix + i;
      }

      return vals;
   }
}
