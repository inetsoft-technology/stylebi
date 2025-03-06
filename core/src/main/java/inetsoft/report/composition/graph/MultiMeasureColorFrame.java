/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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

import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.graph.data.DataSet;
import inetsoft.util.Catalog;

import java.awt.*;
import java.util.*;

/**
 * A color frame to assign a static color for each measure.
 */
public class MultiMeasureColorFrame extends CategoricalColorFrame {
   public MultiMeasureColorFrame(boolean force, String[] names, Color[] colors) {
      getLegendSpec().setTitle(Catalog.getCatalog().getString("Measures"));
      this.force = force;
      init(names, colors);
      setDefaultColor(colors[0]);

      for(int i = 0; i < names.length; i++) {
         mcolors.put(names[i], colors[i]);
      }
   }

   /**
    * Get the measures with colors defined in this frame.
    */
   public Set<String> getMeasures() {
      return mcolors.keySet();
   }

   @Override
   public boolean isVisible() {
      return getLegendSpec().isVisible() && (force || super.isVisible());
   }

   @Override
   public Color getColor(DataSet data, String col, int row) {
      col = getField() == null ? GraphUtil.getOriginalCol(col) : col;
      return super.getColor(data, col, row);
   }

   @Override
   public Color getColor(Object val) {
      String measure = val + "";
      Color clr = mcolors.get(measure);
      return clr != null ? process(clr, getBrightness(measure)) : super.getColor(val);
   }

   @Override
   public void setColor(Object val, Color color) {
      String measure = val + "";
      mcolors.put(measure, color);
   }

   /**
    * Set the brightness for the specified measure.
    */
   public void setBrightness(String measure, double bright) {
      brightnessMap.put(measure, bright);
   }

   /**
    * Get the brightness for the specified measure.
    */
   public double getBrightness(String measure) {
      return brightnessMap.getOrDefault(measure, getBrightness());
   }

   @Override
   public String toString() {
      return super.toString() + mcolors;
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      MultiMeasureColorFrame frame = (MultiMeasureColorFrame) obj;
      return mcolors.equals(frame.mcolors) && brightnessMap.equals(frame.brightnessMap);
   }

   private final boolean force;
   private Map<String, Color> mcolors = new HashMap<>();
   private Map<String, Double> brightnessMap = new HashMap<>();
}
