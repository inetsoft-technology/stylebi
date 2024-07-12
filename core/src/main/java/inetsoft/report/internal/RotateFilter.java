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
package inetsoft.report.internal;

import java.awt.image.*;

/**
 * This filter rotates an image 90 degrees.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
class RotateFilter extends ImageFilter {
   /**
    * Filters the information provided in the setDimensions method
    * of the ImageConsumer interface.
    * @see ImageConsumer#setDimensions
    */
   @Override
   public void setDimensions(int width, int height) {
      consumer.setDimensions(this.height = height, this.width = width);
      buffer = new int[width][height];
   }

   /**
    * Filters the information provided in the setPixels method of the
    * ImageConsumer interface which takes an array of integers.
    * @see ImageConsumer#setPixels
    */
   @Override
   public void setPixels(int x, int y, int w, int h,
                         ColorModel model, int[] pixels, int off,
                         int scansize) {
      this.model = model;
      for(int i = 0; i < pixels.length; i++) {
         buffer[x + i][height - y - 1] = pixels[i];
         // consumer.setPixels(height-y-1, x+i, 1, 1, model, pixels, i, 1);
      }
   }

   /**
    * Filters the information provided in the imageComplete method of
    * the ImageConsumer interface.
    * @see ImageConsumer#imageComplete
    */
   @Override
   public void imageComplete(int status) {
      for(int i = 0; i < buffer.length; i++) {
         consumer.setPixels(0, i, buffer[i].length, 1, model, buffer[i], 0,
            buffer[i].length);
      }

      consumer.imageComplete(status);
   }

   private int width, height;	// width/height of the ORIGINAL image
   private int[][] buffer;   // pixel buffer
   private ColorModel model;
}

