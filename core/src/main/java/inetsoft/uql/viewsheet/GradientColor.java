/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.viewsheet;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.util.Tool;

import java.awt.*;
import java.io.Serializable;
import java.util.List;
import java.util.*;

/**
 * Information about gradient color
 *
 * @version 13.1
 * @author InetSoft Technology Corp
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GradientColor implements Cloneable, Serializable {
   public boolean isApply() {
      return this.apply;
   }

   public void setApply(boolean apply) {
      this.apply = apply;
   }

   public String getDirection() {
      return this.direction;
   }

   public void setDirection(String direction) {
      this.direction = direction;
   }

   public int getAngle() {
      return angle;
   }

   public void setAngle(int angle) {
      this.angle = angle;
   }

   public ColorStop[] getColors() {
      return this.colors;
   }

   public void setColors(ColorStop[] colors) {
      this.colors = colors;
   }

   public Paint getPaint(int w, int h) {
      if(isLinear()) {
         return createLinearGradientPaint(w, h);
      }
      else if(isRadial()) {
         return createRadialGradientPaint(w, h);
      }

      return null;
   }

   public boolean isLinear() {
      return LINEAR_DIRECTION_VALUE.equals(direction);
   }

   public boolean isRadial() {
      return RADIAL_DIRECTION_VALUE.equals(direction);
   }

   private LinearGradientPaint createLinearGradientPaint(int w, int h) {
      double degree = -angle * Math.PI / 180;
      double centerX = w / 2;
      double centerY = h / 2;
      double startX = centerX - Math.cos(degree) * w / 2;
      double startY = centerY - Math.sin(degree) * h / 2;
      double endX = centerX + Math.cos(degree) * w / 2;
      double endY = centerY + Math.sin(degree) * h / 2;

      return new LinearGradientPaint((float) startX, (float) startY, (float) endX,
                                     (float) endY, getFractions(), getColorArray());
   }

   private RadialGradientPaint createRadialGradientPaint(int w, int h) {
      double degree = angle * Math.PI / 180;
      double centerX = w / 2;
      double centerY = h / 2;
      double radius = Math.max(centerX, centerY);
      return new RadialGradientPaint((float) centerX, (float) centerY, (float) radius,
                                     getFractions(), getColorArray());
   }

   public void setAlpha(int alpha) {
      this.alpha = alpha;
   }

   private float[] getFractions() {
      List<ColorStop> fixedColors = getExportColors();
      float[] fractions = new float[fixedColors.size()];

      for(int i = 0; i < fractions.length; i++) {
         float fraction = fixedColors.get(i).getOffset() / 100f;

         if(i > 0 && fractions[i -1] >= fraction) {
            fractions[i] = fractions[i -1] + 0.01f;
         }
         else {
            fractions[i] = fraction;
         }
      }

      return fractions;
   }

   private List<ColorStop> getExportColors() {
      List<ColorStop> colorsList = new ArrayList<>();

      if(this.colors != null) {
         Arrays.stream(colors).forEach(color -> {
            if(!colorsList.contains(color)) {
               colorsList.add(color);
            }
         });
      }

      return colorsList;
   }

   public Color[] getColorArray() {
      List<ColorStop> fixedColors = getExportColors();

      return fixedColors.stream()
         .map(c -> {
            try {
               Color color = GraphUtil.parseColor(c.color);

               if(alpha > 0) {
                  return new Color(color.getRed(), color.getGreen(), color.getBlue(),
                     Math.round(alpha * 255f / 100));
               }

               return color;
            }
            catch(Exception ex) {
               return Color.WHITE;
            }
         }).toArray(Color[]::new);
   }

   @Override
   public Object clone() {
      GradientColor clone = new GradientColor();
      clone.apply = this.apply;
      clone.direction = this.direction;
      clone.angle = this.angle;

      if(colors != null) {
         ColorStop[] cloneStops = new ColorStop[this.colors.length];

         for(int i = 0; i < cloneStops.length; i++) {
            cloneStops[i] = (ColorStop) this.colors[i].clone();
         }

         clone.colors = cloneStops;
      }

      return clone;
   }

   @Override
   public boolean equals(Object obj) {
      if(!(obj instanceof GradientColor)) {
         return false;
      }

      GradientColor gradienColor = (GradientColor) obj;

      return Tool.equals(colors, gradienColor.colors) &&
             Tool.equals(apply, gradienColor.apply) &&
             Tool.equals(direction, gradienColor.direction) &&
             Tool.equals(angle, gradienColor.angle);
   }

   @JsonIgnoreProperties(ignoreUnknown = true)
   public static class ColorStop implements Cloneable, Serializable {
      private String color;
      private int offset;

      public ColorStop() {
      }

      public ColorStop(String color, int offset) {
         this.color = color;
         this.offset = offset;
      }

      public String getColor() {
         return this.color;
      }

      public void setColor(String color) {
         this.color = color;
      }

      public int getOffset() {
         return this.offset;
      }

      public void setOffset(int offset) {
         this.offset = offset;
      }

      @Override
      public Object clone() {
         ColorStop clone = new ColorStop();
         clone.color = this.color;
         clone.offset = this.offset;

         return clone;
      }

      @Override
      public boolean equals(Object obj) {
         if(!(obj instanceof ColorStop)) {
            return false;
         }

         ColorStop stop = (ColorStop) obj;

         return Tool.equals(color, stop.color) && Tool.equals(offset, stop.offset);
      }
   }

   private boolean apply;
   private String direction;
   private int angle;
   private ColorStop[] colors;
   private int alpha = -1;

   public static final String LINEAR_DIRECTION_VALUE = "linear";
   public static final String RADIAL_DIRECTION_VALUE = "radial";
}
