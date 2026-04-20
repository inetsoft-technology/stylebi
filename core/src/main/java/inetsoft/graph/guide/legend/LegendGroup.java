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
package inetsoft.graph.guide.legend;

import inetsoft.graph.*;
import inetsoft.graph.aesthetic.StackedMeasuresFrame;
import inetsoft.graph.aesthetic.VisualFrame;

import java.awt.*;
import java.awt.geom.Dimension2D;
import java.awt.geom.Rectangle2D;
import java.util.HashSet;
import java.util.Set;

/**
 * Visual legends.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public class LegendGroup extends BoundedContainer {
   /**
    * Constructor.
    * @param frames, array of VisualFrame to create each legend.
    * @param graph the graph corresponding to this legend.
    */
   public LegendGroup(VisualFrame[] frames, EGraph graph) {
      this.layout = graph.getLegendLayout();
      Set added = new HashSet();

      for(int i = 0; i < frames.length; i++) {
         for(VisualFrame frame : getLegendFrames(frames[i])) {
            // same frame type (e.g. color) may have different frames and should
            // show each legend. (57333)
            String id = frame.getUniqueId() + ":" + frame.getClass().getName();

            if(!added.contains(id)) {
               addVisual(Legend.createLegend(frame, graph));
               added.add(id);
            }
         }
      }
   }

   /**
    * Get the frames used for creating legend. This expands nested frames inside
    * MultiplexFrame.
    */
   public static VisualFrame[] getLegendFrames(VisualFrame frame) {
      if(frame instanceof StackedMeasuresFrame) {
         return ((StackedMeasuresFrame<?>) frame).getLegendFrames();
      }

      return new VisualFrame[] { frame };
   }

   /**
    * Get min width.
    * @return legends min width.
    */
   @Override
   protected double getMinWidth0() {
      return minW;
   }

   /**
    * Get min height.
    * @return legends min height.
    */
   @Override
   protected double getMinHeight0() {
      return minH;
   }

   /**
    * Get Preferred width.
    * @return legends preferred width.
    */
   @Override
   protected double getPreferredWidth0() {
      return prefW;
   }

   /**
    * Get preferred height.
    * @return legends preferred height.
    */
   @Override
   protected double getPreferredHeight0() {
      return prefH;
   }

   /**
    * Layout. Set legend size and position.
    */
   public void layout() {
      if(layout == GraphConstants.LEFT || layout == GraphConstants.RIGHT) {
         layoutLR();
      }
      else if(layout == GraphConstants.TOP || layout == GraphConstants.BOTTOM) {
         layoutTB();
      }
   }

   /**
    * Layout function for legend lay on left or right side of chart.
    */
   private void layoutLR() {
      Rectangle2D bounds = getBounds();
      int gap = getLegendGap();
      double w = bounds.getWidth() - gap;
      double h = bounds.getHeight();
      double x = bounds.getX();
      double y = bounds.getY() + h;
      double lasth = h;
      double maxh = h;
      int xOffset = layout == GraphConstants.RIGHT ? gap : 0;

      refixRatios(w, h, true);

      for(int i = getVisualCount() - 1; i >= 0; i--) {
         Legend legend = (Legend) getVisual(i);
         double height = h * ratios[i];

         height = Math.min(Math.min(lasth, legend.getPreferredHeight(w, maxh)),
                           height);

         height = legend.getEntireItemHeight(height);

         if(i > 0) {
            lasth = lasth - height;
         }

         y -= height;
         legend.setBounds(x + xOffset, y, w, height);
         legend.layout();
      }
   }

   /**
    * Layout function for legend lay on left or right side of chart.
    * @param width is the legends width.
    * @param height is the legends height.
    * @param pos is the legends position.
    */
   private void layoutTB() {
      Rectangle2D bounds = getBounds();
      int gap = getLegendGap();
      double x = bounds.getX();
      double y = bounds.getY();
      double w = bounds.getWidth();
      double h = bounds.getHeight() - gap;
      double totalw = 0;
      int yOffset = layout == GraphConstants.TOP ? gap : 0;

      refixRatios(w, h, false);

      for(int i = 0; i < getVisualCount(); i++) {
         Legend legend = (Legend) getVisual(i);
         double width = w * ratios[i];

         // if multiple legends, fill the whole width looks better
         if(getVisualCount() == 1) {
            width = Math.min(width, legend.getPreferredWidth(h, w));
         }

         legend.setBounds(x, y + yOffset, width, h);
         x += width;
         totalw += width;
      }

      // center
      double offset = (w - totalw) / 2;

      for(int i = 0; i < getVisualCount(); i++) {
         Legend legend = (Legend) getVisual(i);

         if(offset > 0) {
            Rectangle2D box = legend.getBounds();
            legend.setBounds(box.getX() + offset, box.getY(),
                             box.getWidth(), box.getHeight());
         }

         legend.layout();
      }
   }

   /**
    * Refix the ratios, such as when layout is RIGHT or LEFT to avoid some
    * legends too height to display, but some legends are not height enough to
    * display.
    * @param w the really width to display all legends.
    * @param h the really height to display all legends.
    * @param isLR the legend layout, if true, layout is RIGHT or LEFT, else,
    *  layout is TOP or BOTTOM.
    */
   private void refixRatios(double w, double h, boolean isLR) {
      double extentsize = isLR ? h : w;
      double[] msizes = new double[ratios.length];
      double[] psizes = new double[ratios.length];
      double mtotal = 0;
      double ptotal = 0;
      double maxw = isLR ? w / 2 : w;
      double maxh = isLR ? h : h / 2;

      for(int i = 0; i < getVisualCount(); i++) {
         Legend legend = (Legend) getVisual(i);
         double msize = isLR ? legend.getMinHeight() : legend.getMinWidth();
         double psize = isLR ? legend.getPreferredHeight(w, maxh)
            : legend.getPreferredWidth(h, maxw);

         msizes[i] = msize;
         psizes[i] = psize;
         mtotal += msize;
         ptotal += psize;
      }

      double[] sizes;
      double total = 0;

      // proportional to min size
      if(mtotal > extentsize) {
         sizes = msizes;
         total = mtotal;
      }
      // use min size + preferred size
      else if(ptotal > extentsize) {
         double avail = extentsize - mtotal;

         sizes = new double[ratios.length];

         for(int i = 0; i < sizes.length; i++) {
            sizes[i] = msizes[i] + (psizes[i] - msizes[i]) * avail /
               (ptotal - mtotal);
         }

         total = extentsize;
      }
      // use pref size
      else {
         sizes = psizes;
         total = ptotal;
      }

      // calculate ratio
      for(int i = 0; i < sizes.length; i++) {
         ratios[i] = sizes[i] / total;
      }
   }

   /**
    * Init sizes.
    */
   private void initSizes() {
      invalidate(); // clear cached sizes

      double areaSum = 0;
      double[] areas = new double[getVisualCount()];

      for(int i = 0; i < getVisualCount(); i++) {
         Legend legend = (Legend) getVisual(i);
         areas[i] = legend.getPreferredWidth() * legend.getPreferredHeight();
         areaSum = areaSum + areas[i];
      }

      ratios = new double[areas.length];

      for(int i = 0; i < ratios.length; i++) {
         ratios[i] = areas[i] / areaSum;
      }

      int gap = getLegendGap();

      if(layout == GraphConstants.LEFT || layout == GraphConstants.RIGHT) {
         // fix ration to avoid legend's height too small
         for(int i = 0; i < getVisualCount(); i++) {
            Legend legend = (Legend) getVisual(i);
            double aheight = asize.getHeight() * ratios[i];
            double mheight = legend.getMinHeight();

            if(aheight < mheight && mheight < asize.getHeight()) {
               double newRatio = mheight / asize.getHeight();
               int maxIndex = getMaxIndex();
               ratios[maxIndex] = ratios[maxIndex] - (newRatio - ratios[i]);
               ratios[i] = newRatio;
            }
         }

         double maxw = asize.getWidth();
         prefW = 0;

         for(int i = 0; i < getVisualCount(); i++) {
            Legend legend = (Legend) getVisual(i);
            minW = Math.max(minW, legend.getMinWidth());
            minH += legend.getMinHeight();
            double aheight = asize.getHeight() * ratios[i];
            prefW = Math.max(prefW, legend.getPreferredWidth(aheight, maxw));
         }

         prefH = asize.getHeight();
         prefW += gap;
      }
      else if(layout == GraphConstants.TOP || layout == GraphConstants.BOTTOM) {
         double maxh = asize.getHeight();
         prefH = 0;

         for(int i = 0; i < getVisualCount(); i++) {
            Legend legend = (Legend) getVisual(i);

            minW += legend.getMinWidth();
            minH = Math.max(legend.getMinHeight(), minH);

            double awidth = asize.getWidth() * ratios[i];
            prefH = Math.max(legend.getPreferredHeight(awidth, maxh), prefH);
         }

         prefW = asize.getWidth();
         prefH += gap;
      }
   }

   /**
    * Get max ratio index.
    */
   private int getMaxIndex() {
      if(ratios.length == 0) {
         return -1;
      }

      int maxIndex = 0;

      for(int j = 1; j < ratios.length; j++) {
         if(ratios[j] > ratios[maxIndex]) {
            maxIndex = j;
         }
      }

      return maxIndex;
   }

   /**
    * Paint the visual object on the graphics.
    * @param g is the graphics context.
    */
   @Override
   public void paint(Graphics2D g) {
      for(int i = 0; i < getVisualCount(); i++) {
         Legend legend = (Legend) getVisual(i);
         legend.paint(g);
      }
   }

   /**
    * Get legend.
    * @param index specified legend index.
    */
   public Legend getLegend(int index) {
      return index < getLegendCount() ? (Legend) getVisual(index) : null;
   }

   /**
    * Get legend count.
    * @return legend count.
    */
   public int getLegendCount() {
      return getVisualCount();
   }

   /**
    * Get legends layout.
    */
   public int getLayout() {
      return layout;
   }

   /**
    * Set available size for laying out legend.
    */
   public void setLayoutSize(Dimension2D size) {
      this.asize = size;

      if(size != null) {
         initSizes();
      }
   }

   /**
    * Get available size for laying out legend.
    */
   public Dimension2D getLayoutSize() {
      return asize;
   }

   private int getLegendGap() {
      for(int i = 0; i < getVisualCount(); i++) {
         Legend legend = (Legend) getVisual(i);
         LegendSpec spec = legend.getVisualFrame().getLegendSpec();

         if(spec != null) {
            return spec.getGap();
         }
      }

      return 0;
   }

   private static final double GAP = 2;
   private double minW = 0;
   private double minH = 0;
   private double prefW = 0;
   private double prefH = 0;
   private double maxW = 0;
   private double maxH = 0;
   private Dimension2D asize; // available size
   private double[] ratios = new double[0];
   private int layout; // legend layout
}
