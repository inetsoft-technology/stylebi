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

import inetsoft.report.Presenter;
import inetsoft.sree.SreeEnv;
import net.sourceforge.barbecue.Barcode;

import java.awt.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This is the base class for all barcode presenter. It provides logic for
 * drawing a string using a barcode encoding.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public abstract class AbstractBarcodePresenter implements Presenter {
   /**
    * Create a presenter and set the defaults.
    */
   protected AbstractBarcodePresenter() {
      String prop;

      if((prop = SreeEnv.getProperty("barcode.bar.width")) != null) {
         setBarWidth(Double.valueOf(prop).doubleValue());
      }

      if((prop = SreeEnv.getProperty("barcode.resolution")) != null) {
         setResolution(Integer.parseInt(prop));
      }
   }

   /**
    * Create a barcode object.
    */
   protected abstract Barcode createBarcode(String str);

   /**
    * Create a barcode object.
    */
   private Barcode createBarcode0(String str) {
      Barcode barcode = createBarcode(str);

      if(barcode != null) {
         if(barWidth != null) {
            barcode.setBarWidth(barWidth.doubleValue());
         }

         if(barHeight != null) {
            barcode.setBarHeight(barHeight.doubleValue());
         }

         if(resolution != null) {
            barcode.setResolution(resolution.intValue());
         }
      }

      return barcode;
   }

   /**
    * Sets the desired bar width for the barcode. This is the width (in pixels)
    * of the thinnest bar in the barcode. Other bars will change their size
    * relative to this.
    * @param barWidth The desired width of the thinnest bar in pixels
    */
   public void setBarWidth(double barWidth) {
      this.barWidth = (barWidth > 0) ? Double.valueOf(barWidth) : null;
   }

   /**
    * Get the bar width.
    */
   public double getBarWidth() {
      return (barWidth != null) ? barWidth.doubleValue() : 0;
   }

   /**
    * Sets the desired height for the bars in the barcode (in pixels).
    * Note that some barcode implementations will not allow the height
    * to go below a minimum size. This is not the height of the component
    * as a whole, as it does not specify the height of
    * any text that may be drawn and does not include borders.
    * Note that changing this setting after a barcode has been drawn
    * will invalidate the component and may force a refresh.
    * @param barHeight The desired height of the barcode bars in pixels
    */
   public void setBarHeight(double barHeight) {
      this.barHeight = (barHeight > 0) ? Double.valueOf(barHeight) : null;
   }

   /**
    * Get the bar height.
    */
   public double getBarHeight() {
      return (barHeight != null) ? barHeight.doubleValue() : 0;
   }

   /**
    * Sets the desired output resolution for the barcode. This method should
    * be used in cases where the barcode is either being outputted to a device
    * other than the screen, or the barcode is being generated on a headless
    * machine (e.g. a rack mounted server) and the screen resolution cannot be
    * determined. The default resolution is 72 dpi.
    * @param resolution The desired output resolution (in dots per inch)
    */
   public void setResolution(int resolution) {
      this.resolution = Integer.valueOf(resolution);
   }

   /**
    * Get the ouput resolution.
    */
   public int getResolution() {
      return (resolution != null) ? resolution.intValue() : 72;
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

      Barcode code = createBarcode0(v.toString());

      if(code != null) {
         if(offset != null) {
            x += offset.getWidth();
            y += offset.getHeight();
         }

         Graphics2D g2 = (Graphics2D) g;
         Dimension dim = code.getPreferredSize();
         Rectangle clip = g.getClipBounds();
         double gw = (clip == null) ? Integer.MAX_VALUE : clip.width;
         double prefw = dim.width * getBarcodeSizeRate();

         gw = Math.min(w, gw);
         double xratio = 1;

         // @by larryl, if barcode is larger than the paintable area, scale so
         // it would fit. Drawing partial barcode never makes any sense. This
         // could also be fixed by changing barcode painter to be non-scalable.
         if(prefw > gw) {
            // @by stephenwebster, For bug1411590049166
            // If a barcode is rotated, the ratios may become negative.
            xratio = Math.abs((gw - 1) / prefw);
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
            // @by stephenwebster, For bug1411590049166
            // If a barcode is rotated, the ratios may become negative.
            yratio = Math.abs((gh - 1) / prefh);
            y = (int) (y / yratio);
         }

         if(xratio != 1 || yratio != 1) {
            g2 = (Graphics2D) g.create();
            g2.scale(xratio, yratio);
         }

         try {
            lock.lock();
            code.setFont((font == null) ? g2.getFont() : font);
            code.draw(g2,
                      (int) (x + dim.width * (getBarcodeSizeRate() - 1) / 2.0),
                      (int) (y + dim.height * (getBarcodeSizeRate() - 1) / 2.0));
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

      Barcode code = createBarcode0(v.toString());

      if(code != null) {
         code.setFont(font);
      }

      Dimension dim =
         (code == null) ? new Dimension(0, 0) : code.getPreferredSize();

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

   @Override
   public void setAlignmentOffset(Dimension offset) {
      this.offset = offset;
   }

   private static Lock lock = new ReentrantLock();
   private Double barWidth;
   private Double barHeight;
   private Integer resolution;
   private Font font;
   private Color bg = null;
   private Dimension offset;
}

