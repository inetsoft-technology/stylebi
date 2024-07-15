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
package inetsoft.graph.guide.axis;

import inetsoft.graph.TextSpec;
import inetsoft.graph.guide.VLabel;

import java.awt.*;
import java.awt.geom.Point2D;

/**
 * VDimensionLabel is a dimesion label of default axis.
 *
 * @hidden
 * @version 10.0
 * @author InetSoft Technology
 */
public class VDimensionLabel extends VLabel {
   /**
    * Constructor.
    *
    * @param dname       dimension name.
    * @param axisType    axis type.
    * @param anchor anchor string if this label is abbreviated.
    */
   public VDimensionLabel(Object label, TextSpec textSpec, String dname, String axisType,
                          Object value, double weight, String anchor)
   {
      super(label, textSpec);
      this.dname = dname;
      this.axisType = axisType;
      this.value = value;
      this.weight = weight;
      this.anchor = anchor;

      setMaxSize(new Dimension(150, 150));
   }

   /**
    * Get dimension name.
    *
    * @return dimension name.
    */
   public String getDimensionName() {
      return dname;
   }

   /**
    * Get dimension label value.
    *
    * @return dimension label value.
    */
   @Override
   public Object getValue() {
      return value;
   }

   /**
    * Get axis type.
    */
   public String getAxisType() {
      return axisType;
   }

   /**
    * Set axis type.
    */
   public void setAxisType(String axisType) {
      this.axisType = axisType;
   }

   /**
    * Get the anchor string if this label is abbreviated.
    */
   public String getAnchor() {
      return anchor;
   }

   @Override
   protected Point2D alignText(Point2D pos, double width, double height,
                               double lwidth, double lheight, int alignx, int aligny)
   {
      TextSpec spec = getTextSpec();

      // auto layout for axis alignment, line up text with first row/column (facet)
      if(spec.getAlignment() == 0 && spec.getRotation() == 0 && weight > 0) {
         // align label to the first row in the facet, if the text can fit in the first row.
         // this won't be the case for multi-line text (e.g. date comparison on y axis).
         double rowH = height / weight;

         if(rowH >= lheight) {
            Point2D pos2 = new Point2D.Double(pos.getX(), pos.getY() + (weight - 1) * rowH);
            return super.alignText(pos2, width, rowH, lwidth, lheight, alignx, aligny);
         }
      }

      return super.alignText(pos, width, height, lwidth, lheight, alignx, aligny);
   }

   private String dname;
   private Object value;
   private String axisType;
   private double weight;
   private String anchor;
}
