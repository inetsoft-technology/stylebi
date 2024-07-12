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
/**
 * Receives image pixels for PSGr.
 *
 * Acknowledgement: This class is based on the class written by
 * E.J. Friedman-Hill at Sandia National Labs.
 *
 * (C) 1996 E.J. Friedman-Hill and Sandia National Labs
 * (C) 1998 Inetsoft Technology Corp
 *
 * @author E.J. Friedman-Hill (C)1996
 * @author      ejfried@ca.sandia.gov
 * @author      http://herzberg.ca.sandia.gov
 */
package inetsoft.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.*;

/**
 * The PixelConsumer reads in an image, and store it as a two dimensional
 * array of pixels.
 */
public class PixelConsumer implements ImageConsumer, java.io.Serializable {
   public int width, height;
   public int iwidth, iheight; // used to hold image width and height
   public int pixelw, pixelh;
   public int[][] pix;
   public byte[] smask;
   /**
    * Grab pixels from an image.
    * @param picture image to get pixels from.
    */
   public PixelConsumer(Image picture) {
      this.image = (picture instanceof ObjectWrapper) ?
         (Image) ((ObjectWrapper) picture).unwrap() : picture;
   }

   /**
    * Grab pixels from a region of an image.
    * @param picture image.
    * @param       sx1 the <i>x</i> coordinate of the first corner of the
    *                    source rectangle.
    * @param       sy1 the <i>y</i> coordinate of the first corner of the
    *                    source rectangle.
    * @param       sx2 the <i>x</i> coordinate of the second corner of the
    *                    source rectangle.
    * @param       sy2 the <i>y</i> coordinate of the second corner of the
    *                    source rectangle.
    */
   public PixelConsumer(Image picture, int sx1, int sy1, int sx2, int sy2) {
      this(picture);
      region = true;
      this.sx1 = sx1;
      this.sy1 = sy1;
      this.sx2 = sx2;
      this.sy2 = sy2;
   }

   public Image getImage() {
      return !region ? image : CoreTool.getBufferedImage(image)
         .getSubimage(sx1, sy1, sx2 - sx1 + 1, sy2 - sy1 + 1);
   }

   /**
    * Get a key object that uniquely identifies a image/section.
    */
   public Object getKey() {
      return new Key(this);
   }

   /**
    * Get the smask data from a image.
    */
   private void processTransparency() {
      java.awt.image.PixelGrabber pg =
         new java.awt.image.PixelGrabber(image, 0, 0, -1, -1, true);

      try {
         pg.grabPixels();

         int[] pixels = (int[]) pg.getPixels();
         boolean shades = false;
         this.pixelw = pg.getWidth();
         this.pixelh = pg.getHeight();
         byte[] smask = new byte[this.pixelw * this.pixelh];

         for(int j = 0; pixels != null && j < smask.length; j++) {
            byte alpha = smask[j] = (byte) ((pixels[j] >> 24) & 0xff);

            if(!shades) {
               if(alpha != 0 && alpha != -1) {
                  shades = true;
               }
            }
         }

         this.smask = shades ? smask : null;
      }
      catch(InterruptedException ex) {
         LOG.error("Failed to process transparency", ex);
      }
   }

   /**
    * Produce the pixels for the image. This only performs one production
    * per PixelConsumer object.
    */
   public void produce() {
      produce(false);
   }

   public void produce(boolean transparency) {
      if(!init) {
         init = true;
         this.processTransparency = transparency;
         produce(image);
      }
   }

   // always produce
   void produce(Image image) {
      if(image == null) {
         return;
      }

      ImageProducer src = image.getSource();

      src.removeConsumer(this);
      src.startProduction(this);

      synchronized(this) {
         while(!complete) {
            try {
               wait(1000);
            }
            catch(Exception e) {
            }
         }
         // this was put in for the Image.getScaledInstance(). The filtering
         // seems to be done twice. If don't wait here, the result of the
         // filtering may or may not show
         /*
          if(src instanceof FilteredImageSource) {
          complete = false;

          while(!complete) {
          try { wait(); } catch(Exception e) {}
          }
          }
          */
      }

      // this can not be in the synchronized block, otherwise it could
      // deadlock
      src.removeConsumer(this);

      if(processTransparency) {
         processTransparency();
      }
   }

   @Override
   public void setProperties(java.util.Hashtable param) {
   }

   @Override
   public void setColorModel(ColorModel param) {
   }

   @Override
   public void setHints(int param) {
   }

   /**
    * This is called when the image production is finished.
    */
   @Override
   public synchronized void imageComplete(int param) {
      complete = true;
      notifyAll();
   }

   /**
    * Called by image producer to set the size of the image.
    */
   @Override
   public void setDimensions(int x, int y) {
      if(region) {
         x = sx2 - sx1 + 1;
         y = sy2 - sy1 + 1;
      }

      if(pix == null || x != width || y != height) {
         pix = new int[width = x][height = y];
      }

      iwidth = width;
      iheight = height;
   }

   /**
    * Called by the image producer to set the values of pixels.
    */
   @Override
   public void setPixels(int x1, int y1, int w, int h, ColorModel model,
                         byte[] pixels, int off, int scansize) {
      int x = 0, y = 0, x2, y2, sx = off, sy = off;

      x2 = x1 + w;
      y2 = y1 + h;

      for(y = y1; y < y2; y++) {
         sx = sy;

         for(x = x1; x < x2 && sx < pixels.length; x++) {
            if(region) {
               if(x >= sx1 && x <= sx2 && y >= sy1 && y <= sy2) {
                  pix[x - sx1][y - sy1] = getInt(pixels[sx++]);

                  if(model != null) {
                     pix[x - sx1][y - sy1] =
                        model.getRGB(pix[x - sx1][y - sy1]);
                  }
               }
            }
            else {
               pix[x][y] = getInt(pixels[sx++]);
               if(model != null) {
                  pix[x][y] = model.getRGB(pix[x][y]);
               }
            }
         }

         sy += scansize;
      }
   }

   /**
    * Called by the image producer to set the values of pixels.
    */
   @Override
   public void setPixels(int x1, int y1, int w, int h, ColorModel model,
                         int[] pixels, int off, int scansize) {
      int x = 0, y = 0, x2, y2, sx, sy;

      x2 = x1 + w;
      y2 = y1 + h;
      sy = off;

      for(y = y1; y < y2; y++) {
         sx = sy;

         for(x = x1; x < x2 && sx < pixels.length; x++) {
            // @by larryl, the transparency seems to work correctly in jdk
            // 1.3 and 1.4 so no need for the work around (which causes problem
            // for png) anymore

            // transparent seems to get lost in getRGB, remember and reapply
            //int transparent = pixels[sx] & 0xFF000000;

            // here I set transparent to 0xFF, or the export file,for
            // example: pdf,rtf, will lost picture.
            // please access the feature1255152843.
            // peterx@inetsoftcorp.com
            //int transparent = 0xFF000000;

            if(region) {
               if(x >= sx1 && x <= sx2 && y >= sy1 && y <= sy2) {
                  pix[x - sx1][y - sy1] = model.getRGB(pixels[sx++]);
               }
            }
            else {
               pix[x][y] = model.getRGB(pixels[sx++]);
            }
         }

         sy += scansize;
      }
   }

   private static final int getInt(byte b) {
      return (b) & 0xFF;
   }

   public int hashCode() {
      return image.hashCode() + sx1 + sy1 + sx2 + sy2;
   }

   public boolean equals(Object obj) {
      if(!(obj instanceof PixelConsumer)) {
         return false;
      }

      PixelConsumer obj2 = (PixelConsumer) obj;

      return image == obj2.image && sx1 == obj2.sx1 && sy1 == obj2.sy1 &&
         sx2 == obj2.sx2 && sy2 == obj2.sy2;
   }

   // key for this image/region
   public static class Key {
      public Key(PixelConsumer cons) {
         this.image = cons.image;
         this.sx1 = cons.sx1;
         this.sx2 = cons.sx2;
         this.sy1 = cons.sy1;
         this.sy2 = cons.sy2;
      }

      public int hashCode() {
         return image.hashCode() + sx1 + sy1 + sx2 + sy2;
      }

      public boolean equals(Object obj) {
         try {
            Key k2 = (Key) obj;

            return image == k2.image && sx1 == k2.sx1 && sy1 == k2.sy1 &&
               sx2 == k2.sx2 && sy2 == k2.sy2;
         }
         catch(Exception e) {
            return false;
         }
      }

      Image image;
      int sx1, sy1, sx2, sy2;
   }

   private boolean complete = false;
   private boolean region = false; // true if only get a region of the image
   private Image image;
   private int sx1, sy1, sx2, sy2;
   private boolean init = false;
   private boolean processTransparency;
   private static final Logger LOG =
      LoggerFactory.getLogger(PixelConsumer.class);
}

