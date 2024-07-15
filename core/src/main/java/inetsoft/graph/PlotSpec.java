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
package inetsoft.graph;

import com.inetsoft.build.tern.TernClass;
import com.inetsoft.build.tern.TernMethod;

import java.awt.*;
import java.io.Serializable;
import java.util.Objects;

/**
 * This class contains the plot painting attributes.
 * <br>
 * The background image is always drawn in the original orientation regardless
 * the rotation of the coordiante. If the background image is mapped to the
 * scale positions (with the x/y min/max set), the mapping is to the horizontal
 * and vertical axis regardless of the rotation.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
@TernClass(url = "#cshid=PlotSpec")
public class PlotSpec implements Cloneable, Serializable {
   /**
    * Set the plot background color.
    */
   @TernMethod
   public void setBackground(Color bg) {
      this.bgColor = bg;
   }

   /**
    * Get the plot background color.
    */
   @TernMethod
   public Color getBackground() {
      return bgColor;
   }

   /**
    * Set the plot background image.
    */
   @TernMethod
   public void setBackgroundImage(Image img) {
      this.bgImage = img;
   }

   /**
    * Get the plot background image.
    */
   @TernMethod
   public Image getBackgroundImage() {
      return bgImage;
   }

   /**
    * Get the background painter for plot.
    */
   @TernMethod
   public BackgroundPainter getBackgroundPainter() {
      return bgPainter;
   }

   /**
    * Set the background painter for plot. This overrides the background image.
    */
   @TernMethod
   public void setBackgroundPainter(BackgroundPainter bgPainter) {
      this.bgPainter = bgPainter;
   }

   /**
    * Set if the image's aspect ratio should be locked.
    */
   @TernMethod
   public void setLockAspect(boolean lock) {
      this.lockAspect = lock;
   }

   /**
    * Check if the image's aspect ratio should be locked.
    */
   @TernMethod
   public boolean isLockAspect() {
      return lockAspect;
   }

   /**
    * Set the x min position the bottom of the background image corresponds to
    * the x scale.
    */
   @TernMethod
   public void setXMin(double xmin) {
      this.xmin = xmin;
   }

   /**
    * Get the x min position the bottom of the background image corresponds to
    * the x scale.
    */
   @TernMethod
   public double getXMin() {
      return xmin;
   }

   /**
    * Set the x max position the bottom of the background image corresponds to
    * the x scale.
    */
   @TernMethod
   public void setXMax(double xmax) {
      this.xmax = xmax;
   }

   /**
    * Get the x max position the bottom of the background image corresponds to
    * the x scale.
    */
   @TernMethod
   public double getXMax() {
      return xmax;
   }

   /**
    * Set the y min position the bottom of the background image corresponds to
    * the y scale.
    */
   @TernMethod
   public void setYMin(double ymin) {
      this.ymin = ymin;
   }

   /**
    * Get the y min position the bottom of the background image corresponds to
    * the y scale.
    */
   @TernMethod
   public double getYMin() {
      return ymin;
   }

   /**
    * Set the y max position the bottom of the background image corresponds to
    * the y scale.
    */
   @TernMethod
   public void setYMax(double ymax) {
      this.ymax = ymax;
   }

   /**
    * Get the y max position the bottom of the background image corresponds to
    * the y scale.
    */
   @TernMethod
   public double getYMax() {
      return ymax;
   }

   /**
    * Set the alpha of the background image or color.
    * @param alpha a value between 0 and 1.
    */
   @TernMethod
   public void setAlpha(double alpha) {
      this.alpha = alpha;
   }

   /**
    * Get the alpha of the background image or color.
    */
   @TernMethod
   public double getAlpha() {
      return alpha;
   }

   /**
    * Set the banding color for vertical bands.
    */
   @TernMethod
   public void setXBandColor(Color color) {
      this.xband = color;
   }

   /**
    * Get the banding color for vertical bands.
    */
   @TernMethod
   public Color getXBandColor() {
      return xband;
   }

   /**
    * Set the banding color for horizontal bands.
    */
   @TernMethod
   public void setYBandColor(Color color) {
      this.yband = color;
   }

   /**
    * Get the banding color for horizontal bands.
    */
   @TernMethod
   public Color getYBandColor() {
      return yband;
   }

   /**
    * Set the vertical band size as the multiple of unit (default) size.
    */
   @TernMethod
   public void setXBandSize(double multiple) {
      this.xbandSize = multiple;
   }

   /**
    * Get the vertical band size as the multiple of unit (default) size.
    */
   @TernMethod
   public double getXBandSize() {
      return xbandSize;
   }

   /**
    * Set the horizontal band size as the multiple of unit (default) size.
    */
   @TernMethod
   public void setYBandSize(double multiple) {
      this.ybandSize = multiple;
   }

   /**
    * Get the horizontal band size as the multiple of unit (default) size.
    */
   @TernMethod
   public double getYBandSize() {
      return ybandSize;
   }

   private Color bgColor = null;

   @Override
   public boolean equals(Object o) {
      if(this == o) return true;
      if(o == null || getClass() != o.getClass()) return false;
      PlotSpec plotSpec = (PlotSpec) o;
      return lockAspect == plotSpec.lockAspect && Double.compare(alpha, plotSpec.alpha) == 0 &&
         Double.compare(xmin, plotSpec.xmin) == 0 && Double.compare(xmax, plotSpec.xmax) == 0 &&
         Double.compare(ymin, plotSpec.ymin) == 0 && Double.compare(ymax, plotSpec.ymax) == 0 &&
         Double.compare(xbandSize, plotSpec.xbandSize) == 0 &&
         Double.compare(ybandSize, plotSpec.ybandSize) == 0 &&
         Objects.equals(bgColor, plotSpec.bgColor) &&
         Objects.equals(bgImage, plotSpec.bgImage) &&
         Objects.equals(bgPainter, plotSpec.bgPainter) &&
         Objects.equals(xband, plotSpec.xband) && Objects.equals(yband, plotSpec.yband);
   }

   @Override
   public int hashCode() {
      return Objects.hash(bgColor, bgImage, bgPainter, lockAspect, alpha, xmin, xmax, ymin, ymax,
                          xband, yband, xbandSize, ybandSize);
   }

   private Image bgImage = null;
   private BackgroundPainter bgPainter;
   private boolean lockAspect = false;
   private double alpha = 1;
   private double xmin = Double.NaN;
   private double xmax = Double.NaN;
   private double ymin = Double.NaN;
   private double ymax = Double.NaN;
   private Color xband = null;
   private Color yband = null;
   private double xbandSize = 1;
   private double ybandSize = 1;

   private static final long serialVersionUID = 1L;
}
