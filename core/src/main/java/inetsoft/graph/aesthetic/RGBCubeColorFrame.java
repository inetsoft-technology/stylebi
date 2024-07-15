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
package inetsoft.graph.aesthetic;

import com.inetsoft.build.tern.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

/**
 * This class defines a color frame for continuous numeric values. This is the
 * base class for color frames that using the RGB cube. The color cube is
 * described on p285 Winkinson.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
@TernClass(url = "#RGBCubeColorFrame")
public class RGBCubeColorFrame extends LinearColorFrame {
   /**
    * Constructor.
    */
   public RGBCubeColorFrame() {
      this(new double[0][0]);
   }

   /**
    * Create a color frame that contains RGB ranges (path through RGB cube).
    * @param colors the colors on the path to form the color space.
    */
   @TernConstructor
   public RGBCubeColorFrame(double[][] colors) {
      this.colors = colors;
   }

   /**
    * Get a color at the relative scale.
    * @param ratio a value from 0 to 1.
    */
   @Override
   @TernMethod
   public Color getColor(double ratio) {
      if(colors == null || colors.length == 0) {
         return process(new Color(0, 0, 0), getBrightness());
      }

      int n = colors.length - 1;
      int alpha = (int) (getAlpha(ratio) * 255);
      ratio = getMinRatio() + ratio * (getMaxRatio() - getMinRatio());

      if(ratio >= 1) {
         int r = (int) (colors[n][0] * 255);
         int g = (int) (colors[n][1] * 255);
         int b = (int) (colors[n][2] * 255);
         return process(new Color(r, g, b, alpha), getBrightness());
      }

      ratio = ratio * n;
      int idx = (int) ratio;
      ratio -= idx;

      double r1 = colors[idx][0];
      double g1 = colors[idx][1];
      double b1 = colors[idx][2];
      double r2 = colors[idx + 1][0];
      double g2 = colors[idx + 1][1];
      double b2 = colors[idx + 1][2];

      int r = (int) ((r1 + (r2 - r1) * ratio) * 255);
      int g = (int) ((g1 + (g2 - g1) * ratio) * 255);
      int b = (int) ((b1 + (b2 - b1) * ratio) * 255);

      r = Math.max(0, Math.min(255, r));
      g = Math.max(0, Math.min(255, g));
      b = Math.max(0, Math.min(255, b));

      return process(new Color(r, g, b, alpha), getBrightness());
   }

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      if(!(super.equals(obj))) {
         return false;
      }

      RGBCubeColorFrame frame2 = (RGBCubeColorFrame) obj;

      if(colors.length != frame2.colors.length) {
         return false;
      }

      for(int i = 0; i < colors.length; i++) {
         if(colors[i].length != frame2.colors[i].length) {
            return false;
         }

         for(int j = 0; j < colors[i].length; j++) {
            if(colors[i][j] != frame2.colors[i][j]) {
               return false;
            }
         }
      }

      return true;
   }

   /**
    * Get the minimum ratio to use in the color path.
    */
   protected double getMinRatio() {
      return 0;
   }

   /**
    * Get the maximum ratio to use in the color path.
    */
   protected double getMaxRatio() {
      return 1;
   }

   /**
    * Create a copy of this object.
    */
   @Override
   public Object clone() {
      try {
         RGBCubeColorFrame frame = (RGBCubeColorFrame) super.clone();
         frame.colors = (double[][]) colors.clone();
         return frame;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone color frame", ex);
         return null;
      }
   }

   private double[][] colors;

   private static final long serialVersionUID = 1L;
   private static final Logger LOG =
      LoggerFactory.getLogger(RGBCubeColorFrame.class);
}
