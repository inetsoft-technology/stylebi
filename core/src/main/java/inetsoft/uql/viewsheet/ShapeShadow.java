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
package inetsoft.uql.viewsheet;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.util.Tool;

import java.awt.*;
import java.io.Serializable;

/**
 * Shadow settings for a shape assembly (rectangle/oval). The direction plus
 * distance pair is the single source of truth for the shadow offset, so the
 * browser, the export rasterizer and the export canvas insets cannot drift
 * apart.
 *
 * @version 14.0
 * @author InetSoft Technology Corp
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShapeShadow implements Cloneable, Serializable {
   /**
    * North (shadow cast above the shape).
    */
   public static final String NORTH = "N";
   /**
    * North-east.
    */
   public static final String NORTH_EAST = "NE";
   /**
    * East.
    */
   public static final String EAST = "E";
   /**
    * South-east, the default.
    */
   public static final String SOUTH_EAST = "SE";
   /**
    * South.
    */
   public static final String SOUTH = "S";
   /**
    * South-west.
    */
   public static final String SOUTH_WEST = "SW";
   /**
    * West.
    */
   public static final String WEST = "W";
   /**
    * North-west.
    */
   public static final String NORTH_WEST = "NW";

   /**
    * The supported directions, in dialog order.
    */
   public static final String[] DIRECTIONS = {
      NORTH, NORTH_EAST, EAST, SOUTH_EAST, SOUTH, SOUTH_WEST, WEST, NORTH_WEST
   };

   /**
    * Get the shadow color, as a hex or integer string.
    */
   public String getColor() {
      return color;
   }

   /**
    * Set the shadow color, as a hex or integer string.
    */
   public void setColor(String color) {
      this.color = color == null || color.isEmpty() ? DEFAULT_COLOR : color;
   }

   /**
    * Get the shadow opacity, 0-100.
    */
   public int getAlpha() {
      return alpha;
   }

   /**
    * Set the shadow opacity, 0-100.
    */
   public void setAlpha(int alpha) {
      this.alpha = Math.max(0, Math.min(100, alpha));
   }

   /**
    * Get the direction the shadow is cast in, one of DIRECTIONS.
    */
   public String getDirection() {
      return direction;
   }

   /**
    * Set the direction the shadow is cast in, one of DIRECTIONS.
    */
   public void setDirection(String direction) {
      this.direction = direction;
   }

   /**
    * Get how far the shadow is offset, in pixels. For a diagonal direction
    * this applies to both axes.
    */
   public int getDistance() {
      return distance;
   }

   /**
    * Set how far the shadow is offset, in pixels. Clamped to the range the
    * dialog allows: these values size the exported shadow image, so an
    * out-of-range value arriving from a script or a raw dialog save must not
    * be able to inflate the allocation.
    */
   public void setDistance(int distance) {
      this.distance = Math.max(0, Math.min(MAX_LENGTH, distance));
   }

   /**
    * Get the blur radius, in pixels.
    */
   public int getBlur() {
      return blur;
   }

   /**
    * Set the blur radius, in pixels. Clamped, see setDistance.
    */
   public void setBlur(int blur) {
      this.blur = Math.max(0, Math.min(MAX_LENGTH, blur));
   }

   /**
    * Get the horizontal shadow offset in pixels, positive to the right.
    */
   @JsonIgnore
   public int getOffsetX() {
      if(NORTH_EAST.equals(direction) || EAST.equals(direction) ||
         SOUTH_EAST.equals(direction))
      {
         return distance;
      }

      if(NORTH_WEST.equals(direction) || WEST.equals(direction) ||
         SOUTH_WEST.equals(direction))
      {
         return -distance;
      }

      return 0;
   }

   /**
    * Get the vertical shadow offset in pixels, positive downwards.
    */
   @JsonIgnore
   public int getOffsetY() {
      if(SOUTH_EAST.equals(direction) || SOUTH.equals(direction) ||
         SOUTH_WEST.equals(direction))
      {
         return distance;
      }

      if(NORTH_EAST.equals(direction) || NORTH.equals(direction) ||
         NORTH_WEST.equals(direction))
      {
         return -distance;
      }

      return 0;
   }

   /**
    * Get the gaussian blur radius to paint with on the server.
    *
    * VSFaceUtil's blur derives sigma as radius/3, while a css blur-radius of b
    * corresponds to sigma b/2, so scale up to keep the exported shadow as soft
    * as the one the browser draws.
    */
   @JsonIgnore
   public int getBlurRadius() {
      return blur <= 0 ? 0 : Math.max(1, Math.round(blur * 1.5f));
   }

   /**
    * Get the shadow color with the opacity applied, for server side painting.
    */
   @JsonIgnore
   public Color getShadowColor() {
      Color clr = null;

      if(color != null && !color.isEmpty()) {
         try {
            // returns null rather than throwing for "null" and other
            // unparseable values
            clr = GraphUtil.parseColor(color);
         }
         catch(Exception ex) {
            clr = null;
         }
      }

      if(clr == null) {
         clr = Color.BLACK;
      }

      int a = Math.max(0, Math.min(100, alpha));

      return new Color(clr.getRed(), clr.getGreen(), clr.getBlue(),
                       Math.round(a * 255f / 100));
   }

   @Override
   public Object clone() {
      ShapeShadow clone = new ShapeShadow();
      clone.color = this.color;
      clone.alpha = this.alpha;
      clone.direction = this.direction;
      clone.distance = this.distance;
      clone.blur = this.blur;

      return clone;
   }

   @Override
   public boolean equals(Object obj) {
      if(!(obj instanceof ShapeShadow)) {
         return false;
      }

      ShapeShadow shadow = (ShapeShadow) obj;

      return Tool.equals(color, shadow.color) &&
             alpha == shadow.alpha &&
             Tool.equals(direction, shadow.direction) &&
             distance == shadow.distance &&
             blur == shadow.blur;
   }

   @Override
   public int hashCode() {
      int hash = alpha + distance * 31 + blur * 131;

      if(color != null) {
         hash += color.hashCode();
      }

      if(direction != null) {
         hash += direction.hashCode();
      }

      return hash;
   }

   @Override
   public String toString() {
      return "ShapeShadow{" +
         "color=" + color +
         ", alpha=" + alpha +
         ", direction=" + direction +
         ", distance=" + distance +
         ", blur=" + blur +
         '}';
   }

   private String color = DEFAULT_COLOR;
   private int alpha = DEFAULT_ALPHA;
   private String direction = SOUTH_EAST;
   private int distance = DEFAULT_DISTANCE;
   private int blur = DEFAULT_BLUR;

   // defaults chosen to approximate the shadow that was hardcoded before the
   // settings existed: box-shadow: 5px 5px 3px 3px rgba(0,0,0,0.3)
   public static final String DEFAULT_COLOR = "#000000";
   public static final int DEFAULT_ALPHA = 30;
   public static final int DEFAULT_DISTANCE = 5;
   public static final int DEFAULT_BLUR = 6;
   /**
    * The largest distance/blur the dialog allows, in pixels. Mirrors
    * MAX_LENGTH in shadow-prop-pane.component.ts.
    */
   public static final int MAX_LENGTH = 50;
}
