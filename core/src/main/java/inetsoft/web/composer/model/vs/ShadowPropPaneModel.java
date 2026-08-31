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
package inetsoft.web.composer.model.vs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.uql.viewsheet.ShapeShadow;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ShadowPropPaneModel implements Serializable {
   public boolean isApply() {
      return apply;
   }

   public void setApply(boolean apply) {
      this.apply = apply;
   }

   public String getColor() {
      return color;
   }

   public void setColor(String color) {
      this.color = color;
   }

   public int getAlpha() {
      return alpha;
   }

   public void setAlpha(int alpha) {
      this.alpha = alpha;
   }

   public String getDirection() {
      return direction;
   }

   public void setDirection(String direction) {
      this.direction = direction;
   }

   public int getDistance() {
      return distance;
   }

   public void setDistance(int distance) {
      this.distance = distance;
   }

   public int getBlur() {
      return blur;
   }

   public void setBlur(int blur) {
      this.blur = blur;
   }

   @Override
   public String toString() {
      return "ShadowPropPaneModel{" +
         "apply=" + apply +
         ", color='" + color + '\'' +
         ", alpha=" + alpha +
         ", direction='" + direction + '\'' +
         ", distance=" + distance +
         ", blur=" + blur +
         '}';
   }

   private boolean apply;
   private String color = ShapeShadow.DEFAULT_COLOR;
   private int alpha = ShapeShadow.DEFAULT_ALPHA;
   private String direction = ShapeShadow.SOUTH_EAST;
   private int distance = ShapeShadow.DEFAULT_DISTANCE;
   private int blur = ShapeShadow.DEFAULT_BLUR;
}
