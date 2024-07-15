/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.graph.coord;

import inetsoft.graph.AxisSpec;
import inetsoft.graph.EGraph;
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.Scale;
import inetsoft.util.CoreTool;

import java.util.Arrays;

/**
 * A parallel coord for parabox graph.
 *
 * @version 13.4
 * @author InetSoft Technology Corp
 */
public class ParaboxCoord extends ParallelCoord {
   /**
    * Default constructor.
    */
   public ParaboxCoord() {
   }

   /**
    * Create a parallel coord.
    */
   public ParaboxCoord(Scale... scales) {
      this();
      setScales(scales);
   }

   /**
    * Set the minimum space in pixels between bubbles.
    */
   public void setSpacing(int spacing) {
      this.spacing = spacing;
   }

   /**
    * Get the minimum space between bubbles.
    */
   public int getSpacing() {
      return spacing;
   }

   @Override
   public void setScales(Scale... scales) {
      super.setScales(scales);

      for(Scale scale : scales) {
         scale.getAxisSpec().setLabelVisible(false);
      }
   }

   @Override
   public double getUnitMinWidth() {
      // minimum width is the sum of max width of ALL axis.
      return Arrays.stream(getScales()).mapToDouble(s -> calculateMinWidth(s)).max().orElse(0)
         * getScales().length;
   }

   @Override
   public double getUnitMinHeight() {
      // minimum height is the max of all axis minimum height.
      return Arrays.stream(getScales()).mapToDouble(s -> calculateMinHeight(s)).max().orElse(0);
   }

   // minimum width of an axis is the max width of button and label.
   private double calculateMinWidth(Scale scale) {
      if(!(scale instanceof CategoricalScale)) {
         return 10;
      }

      AxisSpec spec = scale.getAxisSpec();
      return Arrays.stream(scale.getValues())
         .mapToDouble(v -> getRadius(v, (CategoricalScale) scale) * 2 +
            GTool.stringWidth(new String[] {CoreTool.toString(v)}, spec.getFont(v)) + 3)
         .max().orElse(0);
   }

   // minimum height of an axis is the sum of all bubbles.
   private double calculateMinHeight(Scale scale) {
      if(!(scale instanceof CategoricalScale)) {
         return 10;
      }

      return Arrays.stream(scale.getValues())
         .mapToDouble(v -> getRadius(v, (CategoricalScale) scale) * 2 + spacing).sum();
   }

   private double getRadius(Object val, CategoricalScale scale) {
      SizeFrame sizes = getSizeFrame();
      double weight = scale.getWeight(val);
      // this should match PointVO
      return (int) (sizes.getSize(weight) + GShape.CIRCLE.getMinSize() / 2);
   }

   private SizeFrame getSizeFrame() {
      EGraph graph = getVGraph().getEGraph();
      return graph.getElementCount() > 0 ? graph.getElement(0).getSizeFrame()
         : new LinearSizeFrame();
   }

   @Override
   public double getUnitPreferredWidth() {
      return getUnitMinWidth() * 1.1;
   }

   @Override
   public double getUnitPreferredHeight() {
      return getUnitMinHeight() * 1.1;
   }

   private int spacing = 10;
}
