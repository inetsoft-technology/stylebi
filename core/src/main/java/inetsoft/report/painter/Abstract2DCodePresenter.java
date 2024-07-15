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
package inetsoft.report.painter;

import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import inetsoft.report.Presenter;
import inetsoft.sree.SreeEnv;
import inetsoft.util.Tool;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This is the base class for all 2D code presenter. It provides logic for
 * drawing a string using a code encoding.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public abstract class Abstract2DCodePresenter implements Presenter {
   /**
    * Create a presenter and set the defaults.
    */
   protected Abstract2DCodePresenter() {
      String prop = SreeEnv.getProperty("qrcode.width", "70");
      setWidth(Integer.parseInt(prop));
   }

   /**
    * Create a code object.
    */
   protected abstract BitMatrix createMatrix(String str, int width);

   /**
    * Create a code object.
    */
   private BitMatrix createMatrix0(String str) {
      if(str == null || str.length() == 0) {
         return null;
      }

      return createMatrix(str, getWidth());
   }

   /**
    * Sets the desired bar width for the code.
    * @param width the width of code.
    */
   public void setWidth(int width) {
      this.width = (width > 0) ? Integer.valueOf(width) : null;
   }

   /**
    * Get the code width.
    */
   public int getWidth() {
      return (width != null) ? width.intValue() : 0;
   }

   /**
    * Paint the value.
    * @param g graphical context.
    * @param v object value.
    * @param x x coordinate of the left edge of the paint area.
    * @param y y coordinate of the upper edge of the paint area.
    * @param w area width.
    * @param h area height.
    */
   @Override
   public void paint(Graphics g, Object v, int x, int y, int w, int h) {
      if(v == null) {
         return;
      }

      BitMatrix matrix = createMatrix0(v.toString());

      if(matrix != null) {
         if(offset != null) {
            x += offset.getWidth();
            y += offset.getHeight();
         }

         Graphics2D g2 = (Graphics2D) g;
         Dimension dim = new Dimension(matrix.getWidth(), matrix.getHeight());
         Rectangle clip = g.getClipBounds();
         double gw = (clip == null) ? Integer.MAX_VALUE : clip.width;
         double prefw = dim.width * getBarcodeSizeRate();

         gw = Math.min(w, gw);
         double xratio = 1;

         // @by larryl, if barcode is larger than the paintable area, scale so
         // it would fit. Drawing partial barcode never makes any sense. This
         // could also be fixed by changing barcode painter to be non-scalable.
         if(prefw > gw) {
            xratio = (gw - 1) / prefw;
            x = (int) (x / xratio);
         }

         double gh = (clip == null) ? Integer.MAX_VALUE : clip.height;
         double prefh = dim.height * getBarcodeSizeRate();

         gh = Math.min(h, gh);
         double yratio = 1;

         // @by larryl, if barcode is larger than the paintable area, scale so
         // it would fit. Drawing partial barcode never makes any sense. This
         // could also be fixed by changing barcode painter to be non-scalable.
         if(prefh > gh) {
            yratio = (gh - 1) / prefh;
            y = (int) (y / yratio);
         }

         if(xratio != 1 || yratio != 1) {
            // keep square
            double sameRatio = Math.min(xratio, yratio);
            g2 = (Graphics2D) g.create();
            g2.scale(sameRatio, sameRatio);
         }

         try {
            lock.lock();
            BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);
            Tool.waitForImage(image);
            g2.drawImage(image,
               (int) (x + dim.width * (getBarcodeSizeRate() - 1) / 2.0),
               (int) (y + dim.height * (getBarcodeSizeRate() - 1) / 2.0),
               null);
         }
         finally {
            lock.unlock();
         }

         if(g2 != g) {
            g2.dispose();
         }
      }
   }

   /**
    * Calculate the preferred size of the object representation.
    * @param v object value.
    * @return preferred size.
    */
   @Override
   public Dimension getPreferredSize(Object v) {
      if(v == null) {
         return new Dimension(0, 0);
      }

      BitMatrix matrix = createMatrix0(v.toString());

      Dimension dim =
         (matrix == null) ? new Dimension(0, 0) :
            new Dimension(matrix.getWidth(), matrix.getHeight());

      return new Dimension((int)(dim.width * getBarcodeSizeRate()),
                           (int)(dim.height * getBarcodeSizeRate()));
   }

   /**
    * Check if the presenter can handle this type of objects.
    * @param type object type.
    * @return true if the presenter can handle this type.
    */
   @Override
   public boolean isPresenterOf(Class type) {
      return true;
   }

   /**
    * Check if the presenter can handle this particular object. Normally
    * a presenter handles a class of objects, which is checked by the
    * isPresenterOf(Class) method. If this presenter does not care about
    * the value in the object, it can just call the isPresenterOf() with
    * the class of the object, e.g.<pre>
    *   if(type == null) {
    *      return false;
    *   }
    *   return isPresenterOf(obj.getClass());
    * </pre>
    * @param obj object type.
    * @return true if the presenter can handle this type.
    */
   @Override
   public boolean isPresenterOf(Object obj) {
      return obj != null;
   }

   /**
    * Check if this presenter should always fill the entire area of a cell.
    */
   @Override
   public boolean isFill() {
      return false;
   }

   /**
    * Set the font to use for this presenter. A table calls this function
    * before the cell is printed when a presenter is used.
    */
   @Override
   public void setFont(Font font) {
      this.font = font;
   }

   /**
    * Determine if this Presenter requires raw (unformatted) data.
    *
    * @return <code>true</code>.
    */
   @Override
   public boolean isRawDataRequired() {
      return false;
   }

   @Override
   public void setAlignmentOffset(Dimension offset) {
      this.offset = offset;
   }

   /**
    * There is some bugs aroung barbecue barcode generator which
    * will not return correct prefer width for small width setting.
    * Fixed it using a fixed correct rate.
    */
   protected double getBarcodeSizeRate() {
      return 1.0;
   }

   /**
    * Set the background.
    */
   @Override
   public void setBackground(Color bg) {
      this.bg = bg;
   }

   /**
    * Get the background.
    */
   public Color getBackground() {
      return bg;
   }

   private static Lock lock = new ReentrantLock();
   private Integer width;
   private Font font;
   private Color bg = null;
   private Dimension offset;
}

