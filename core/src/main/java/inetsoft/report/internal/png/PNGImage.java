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
package inetsoft.report.internal.png;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.*;

public class PNGImage {
   public PNGImage(Image image, int bitDepth, boolean alpha) {
      this.image = image;
      this.bitDepth = bitDepth;
      this.alpha = alpha;

      int bytesPer = alpha ? 4 : 3;

      width = image.getWidth(null);
      height = image.getHeight(null);
      row = 0;
      imageData = new byte[width * bitDepth / 8 * bytesPer * height];

      for(int i = 0; i < imageData.length; i++) {
         imageData[i] = (byte) 0;
      }

      fetchImage();
   }

   private void fetchImage() {
      int[] pixels = new int[width * height];
      PixelGrabber grabber = new PixelGrabber(image, 0, 0, width, height,
         pixels, 0, width);

      try {
         if(image.getSource() == null) {
            LOG.error("Could not get PNG image producer");
         }
         else {
            grabber.grabPixels();
         }
      }
      catch(InterruptedException e) {
         LOG.error("Image fetch interrupted", e);
      }

      if((grabber.getStatus() & ImageObserver.ABORT) != 0) {
         LOG.error("Image fetch aborted or error");
      }

      ColorModel model = grabber.getColorModel();
      ColorModel defaultRGB = ColorModel.getRGBdefault();

      // optimize for default color model and 8 bit, very very tight loop
      if(model == defaultRGB && bitDepth == 8) {
         int red, green, blue, aa;

         for(int i = 0, j = -1; i < pixels.length; i++) {
            red = (pixels[i] >>> 16) & 0xFF;
            green = (pixels[i] >>> 8) & 0xFF;
            blue = pixels[i] & 0xFF;
            aa = (pixels[i] >>> 24) & 0xFF;

            if(aa == 0) {
               imageData[++j] = (byte) 255;
               imageData[++j] = (byte) 255;
               imageData[++j] = (byte) 255;
            }
            else {
               imageData[++j] = (byte) red;
               imageData[++j] = (byte) green;
               imageData[++j] = (byte) blue;
            }

            if(alpha) {
               imageData[++j] = (byte) aa;
            }
         }
      }
      // generic loop handle all color model and bit depth
      else {
         for(int i = 0, j = -1; i < pixels.length; i++) {
            int red, green, blue, aa;

            if(model == defaultRGB) {
               red = (pixels[i] >>> 16) & 0xFF;
               green = (pixels[i] >>> 8) & 0xFF;
               blue = pixels[i] & 0xFF;
               aa = (pixels[i] >>> 24) & 0xFF;
            }
            else {
               red = model.getRed(pixels[i]);
               green = model.getGreen(pixels[i]);
               blue = model.getBlue(pixels[i]);
               aa = model.getAlpha(pixels[i]);
            }

            if(bitDepth == 16) {
               imageData[++j] = (byte) ((red >> 8) & 0xff);
               imageData[++j] = (byte) (red & 0xff);
               imageData[++j] = (byte) ((green >> 8) & 0xff);
               imageData[++j] = (byte) (green & 0xff);
               imageData[++j] = (byte) ((blue >> 8) & 0xff);
               imageData[++j] = (byte) (blue & 0xff);

               if(alpha) {
                  imageData[++j] = (byte) ((aa >> 8) & 0xff);
                  imageData[++j] = (byte) (aa & 0xff);
               }
            }
            else {
               if(aa == 0) {
                  imageData[++j] = (byte) 255;
                  imageData[++j] = (byte) 255;
                  imageData[++j] = (byte) 255;
               }
               else {
                  imageData[++j] = (byte) red;
                  imageData[++j] = (byte) green;
                  imageData[++j] = (byte) blue;
               }

               if(alpha) {
                  imageData[++j] = (byte) aa;
               }
            }
         }
      }
   }

   public byte[] getScanLine() {
      if(row >= height) {
         return null;
      }

      int bytesPer = alpha ? 4 : 3;
      byte[] scanLine = new byte[width * bitDepth / 8 * bytesPer];
      int i = row * width * bitDepth / 8 * bytesPer;
      int j = 0;

      /*
       for(; j < scanLine.length; i++, j++) {
       scanLine[j] = imageData[i];
       }
       */
      System.arraycopy(imageData, i, scanLine, 0, scanLine.length);
      row++;

      return scanLine;
   }

   public void reset() {
      row = 0;
   }

   public int getWidth() {
      return width;
   }

   public int getHeight() {
      return height;
   }

   public int getBitDepth() {
      return bitDepth;
   }

   private Image image;
   private int width;
   private int height;
   private int row;
   private int bitDepth;
   private byte[] imageData;
   private boolean alpha = false;

   private static final Logger LOG =
      LoggerFactory.getLogger(PNGImage.class);
}

