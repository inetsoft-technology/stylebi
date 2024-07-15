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
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.internal.GDefaults;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.scale.LinearScale;
import inetsoft.report.internal.Common;
import inetsoft.util.CoreTool;
import inetsoft.util.Tool;

import java.awt.*;
import java.awt.geom.Dimension2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.*;

/**
 * This is the base class of legend.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public abstract class Legend extends BoundedContainer {
   /**
    * Create a legend for an aesthetic frame.
    * @param frame the legend frame contain legend infomations.
    * @param graph the corresponding graph.
    */
   public static Legend createLegend(VisualFrame frame, EGraph graph) {
      Legend legend = null;

      if(frame instanceof ColorFrame) {
         legend = new ColorLegend(frame, graph);
      }
      else if(frame instanceof SizeFrame) {
         legend = new SizeLegend(frame, graph);
      }
      else if(frame instanceof ShapeFrame) {
         legend = new ShapeLegend(frame, graph);
      }
      else if(frame instanceof TextureFrame) {
         legend = new TextureLegend(frame, graph);
      }
      else if(frame instanceof LineFrame) {
         legend = new LineLegend(frame, graph);
      }

      return legend;
   }

   /**
    * Create a legend for the specified frame.
    * @param frame the legend frame contain legend infomations.
    * @param graph the egraph
    */
   protected Legend(VisualFrame frame, EGraph graph) {
      this.frame = frame;
      this.graph = graph;
      this.elem = findElement(graph, frame);

      isScalar = frame instanceof LinearColorFrame;

      if(frame.getLegendSpec().isTitleVisible()) {
         createTitle(frame.getLegendSpec().getTitleTextSpec());
      }

      int layout = graph.getLegendLayout();
      verticalLayout = layout == GraphConstants.LEFT || layout == GraphConstants.RIGHT;

      if(isScalar) {
         createBand();
      }
      else {
         createItems();
      }
   }

   /**
    * Get the legend frame of this legend.
    * @return frame is set in constructor.
    */
   public VisualFrame getVisualFrame() {
      return frame;
   }

   /**
    * Check if background in legendSpec/textSpec should be painted.
    */
   public boolean isPaintBackground() {
      return paintBackground;
   }

   /**
    * Set if background in legendSpec/textSpec should be painted.
    */
   public void setPaintBackground(boolean paintBackground) {
      this.paintBackground = paintBackground;
   }

   /**
    * Get legend min height.
    * @return min height double value.
    */
   @Override
   protected double getMinHeight0() {
      double minh = GAP;

      if(title != null) {
         minh += title.getMinHeight() + getBorderWidth();
      }

      if(isScalar) {
         minh += band.getMinHeight() + 2 * GAP +
            Math.max(minLabel == null ? 0 : minLabel.getMinHeight(),
                     maxLabel == null ? 0 : maxLabel.getMinHeight());
      }
      else {
         // if horizontal layout, prefer 1 row so items don't wrap
         // if not necessary
         int mincnt = verticalLayout ? 3 : 1;

         for(int i = 0; i < mincnt && i < items.size(); i++) {
            minh += items.get(i).getMinHeight();
         }
      }

      return minh;
   }

   /**
    * Get legend min width.
    * @return min width double value.
    */
   @Override
   protected double getMinWidth0() {
      double titlew = (title != null) ? title.getWidth(MIN_CHAR_COUNT) : 0;
      double minw;

      if(isScalar) {
         double bandw = band.getMinWidth() +
            ((minLabel == null ? 0 : minLabel.getWidth(MIN_CHAR_COUNT))
            + (maxLabel == null ? 0 : maxLabel.getWidth(MIN_CHAR_COUNT))) / 2;
         minw = Math.max(titlew, bandw);
      }
      else {
         double itemw = 0;

         for(LegendItem item : items) {
            itemw = Math.max(itemw, item.getMinWidth());
         }

         minw = Math.max(titlew, itemw);
      }

      return minw;
   }

   /**
    * Get legend preferred height, items are displayed in one column..
    * @return preferred height double value.
    */
   @Override
   protected double getPreferredHeight0() {
      double pheight = GAP;

      if(title != null) {
         pheight += title.getPreferredHeight();
      }

      if(isScalar) {
         double minLabelHeight = minLabel == null ? 0 : minLabel.getPreferredHeight();
         double maxLabelHeight = maxLabel == null ? 0 : maxLabel.getPreferredHeight();
         double bandHeight = band.getPreferredHeight();
         pheight += bandHeight + 2 * GAP + Math.max(minLabelHeight, maxLabelHeight);
      }
      else {
         for(LegendItem item : items) {
            pheight += item.getPreferredHeight();
         }
      }

      return pheight;
   }

   /**
    * Get legend preferred width, items are displayed in one column.
    * @return preferred width double value.
    */
   @Override
   protected double getPreferredWidth0() {
      double pwidth = 0;

      if(title != null) {
         pwidth = title.getWidth(PREFERRED_CHAR_COUNT);
      }

      if(isScalar) {
         double minw = (minLabel == null) ? 0 : minLabel.getWidth(PREFERRED_CHAR_COUNT);
         double maxw = (maxLabel == null) ? 0 : maxLabel.getWidth(PREFERRED_CHAR_COUNT);
         double bandw = band.getPreferredWidth() + Math.max(minw, maxw) / 2;

         pwidth = Math.max(pwidth, Math.max(bandw, minw + maxw) + 2 * GAP);
      }
      else {
         for(LegendItem item : items) {
            double iwidth = item.getPreferredWidth();
            pwidth = Math.max(pwidth, iwidth);
         }
      }

      return pwidth;
   }

   /**
    * Get item preferredSize width by column.
    * @param colCount count of columns.
    * @return item preferredsize's width.
    */
   private double getItemPreferredWidth(int colCount) {
      double w = 0;

      for(LegendItem item : items) {
         w = Math.max(item.getPreferredWidth(), w);
      }

      return w * colCount;
   }

   /**
    * Get row size by certain column size.
    */
   private int getRowCount(int colCount) {
      int itemCount = items.size();
      int rowCount = itemCount / colCount;
      rowCount = (rowCount * colCount == itemCount) ? rowCount : rowCount + 1;

      return rowCount;
   }

   /**
    * Get items.
    */
   public LegendItem[][] getItems() {
      if(itemsArr != null) {
         return itemsArr;
      }

      int rowCount = getRowCount(ncol);
      int incX = verticalLayout ? 1 : ncol;
      int incY = verticalLayout ? rowCount : 1;
      int icount = 0;
      int jcount = 0;

      for(int i = 0; i < rowCount; i++) {
         for(int j = 0; j < ncol; j++) {
            if(i * incX + j * incY >= items.size()) {
               break;
            }

            if(i == 0) {
               jcount++;
            }
         }

         icount++;
      }

      itemsArr = new LegendItem[icount][jcount];

      for(int i = 0; i < icount; i++) {
         for(int j = 0; j < jcount; j++) {
            if(i * incX + j * incY >= items.size()) {
               break;
            }

            LegendItem item = items.get(i * incX + j * incY);
            itemsArr[i][j] = item;
         }
      }

      return itemsArr;
   }

   /**
    * Get the bounding box of the title output.
    */
   public Rectangle2D getTitleBounds() {
      if(title == null) {
         return null;
      }

      Rectangle2D bounds = title.getBounds();
      double x = bounds.getX();
      double y = bounds.getY() - TITLE_LINE_GAP;
      double w = bounds.getWidth();
      double h = bounds.getHeight() + TITLE_LINE_GAP;

      return new Rectangle2D.Double(x, y, w, h);
   }

   /**
    * Get the bounding box of the content output.
    */
   public Rectangle2D getContentBounds() {
      Rectangle2D bounds = getBounds();
      Rectangle2D tbounds = getTitleBounds();
      double lw = getBorderWidth();
      double x = bounds.getX() + lw;
      double w = bounds.getWidth() - lw * 2;
      double y = bounds.getY() + lw;
      double h = bounds.getHeight() - lw * 2;

      if(title != null) {
         h = tbounds.getY() - bounds.getY() - lw;
      }

      return new Rectangle2D.Double(x, y, w, h);
   }

   /**
    * Get the legend content preferred size (for fitting all items).
    */
   public Rectangle2D getContentPreferredBounds() {
      Rectangle2D bounds = getBounds();
      double lw = getBorderWidth();
      double x = bounds.getX() + lw;
      double y = bounds.getY() + lw;
      double w = bounds.getWidth() - lw * 2;
      double h = bounds.getHeight() - lw * 2;

      double contentW;
      double contentH;
      double contentX;
      double contentY;

      if(isScalar) {
         contentW = w;
         contentH = band.getSize().getHeight() +
            (minLabel == null ? 0 :  minLabel.getSize().getHeight()) + GAP;
         contentX = x;
         contentY = title != null ?
            y + h - title.getSize().getHeight() - GAP - contentH :
            y + h - GAP - contentH;
      }
      else {
         LegendItem item0 = items.size() == 0 ? null : items.get(0);
         double itemh = item0 == null ? 20 : item0.getPreferredHeight();

         contentW = w;
         contentH = getItems().length * itemh;
         contentX = x;
         contentY = title != null ?
            y + h - title.getSize().getHeight() - GAP - contentH :
         // bug1433832994334, if title is not visible, then no GAP, so
         // we don't need minus the GAP height
            y + h - contentH;
      }

      return new Rectangle2D.Double(contentX, contentY, contentW, contentH);
   }

   /**
    * Layout, set all visual's size and position.
    */
   public void layout() {
      layoutTitle();

      if(isScalar) {
         layoutBand();
      }
      else {
         layoutItems();
      }
   }

   /**
    * Layout title.
    */
   private void layoutTitle() {
      if(title == null) {
         return;
      }

      Rectangle2D bounds = getBounds();
      double lw = getBorderWidth();
      double titlew = bounds.getWidth() - lw * 2;
      double titleh = title.getPreferredHeight();
      double titlex = bounds.getX();
      double titley = bounds.getY() + bounds.getHeight() - titleh;

      title.setBounds(titlex, titley, titlew, titleh);
   }

   /**
    * Layout band.
    */
   private void layoutBand() {
      Rectangle2D bounds = getBounds();
      double x = bounds.getX();
      double y = bounds.getY();
      double w = bounds.getWidth();
      double h = bounds.getHeight();
      double gap = 10;

      double bandW = w - gap * 2;
      double bandH = h * band.getPreferredHeight() / getPreferredHeight();
      double bandX = x + gap;
      double bandY = y + h - GAP - bandH;

      if(title != null) {
         bandY -= title.getSize().getHeight();
      }

      double labelH = Math.max(
         (minLabel == null) ? 0 : minLabel.getPreferredHeight(),
         (maxLabel == null) ? 0 : maxLabel.getPreferredHeight());
      labelH = Math.min(h - bandH, labelH);
      double minLabelW = minLabel == null ? 0 : minLabel.getPreferredWidth();
      double maxLabelW = maxLabel == null ? 0 : maxLabel.getPreferredWidth();

      // avoid overlapping
      maxLabelW = Math.min(Math.max(w / 2, w - minLabelW), maxLabelW);
      minLabelW = Math.min(minLabelW, w / 2);
      double minLabelX = Math.max(x + 1, bandX - minLabelW / 2);
      double maxLabelX = Math.max(minLabelX + minLabelW, x + w - maxLabelW - 1);

      double minLabelY = bandY - labelH - GAP;
      double maxLabelY = minLabelY;

      band.setBounds(bandX, bandY, bandW, bandH);

      if(minLabel != null) {
         minLabel.setBounds(minLabelX + GAP / 2, minLabelY, minLabelW, labelH);
         moveInBounds(minLabel, bounds);
      }

      if(maxLabel != null) {
         maxLabel.setBounds(maxLabelX - GAP / 2, maxLabelY, maxLabelW, labelH);
         moveInBounds(maxLabel, bounds);
      }
   }

   /**
    * Make sure the label is inside the bounds.
    */
   private void moveInBounds(VLabel label, Rectangle2D bounds) {
      Rectangle2D box0 = label.getBounds();
      Rectangle2D box = label.getTransformedBounds().getBounds();

      if(box.getX() < bounds.getX()) {
         box0.setRect(box0.getX() + (bounds.getX() - box.getX()),
                      box0.getY(), box0.getWidth(), box0.getHeight());
      }
      else if(box.getMaxX() > bounds.getMaxX()) {
         box0.setRect(box0.getX() - (bounds.getMaxX() - box.getMaxX()),
                      box0.getY(), box0.getWidth(), box0.getHeight());
      }

      label.setBounds(box0);
   }

   /**
    * Layout items, set its size and position.
    */
   private void layoutItems() {
      // in VLegendItem, when paint the item, has logic to move the item to
      // padding left 5 pixels, the logic should move here, because if in the
      // logic is in VLegendItem, then the bounds set for the item is wrong, and
      // the ncol will also be error
      Rectangle2D bounds = getBounds();
      double x = bounds.getX();
      double y = bounds.getY() - getBorderWidth() / 2;
      double w = bounds.getWidth();
      double h = bounds.getHeight();
      double lw = getBorderWidth();

      // when no item, layout is meaningless
      if(items.size() == 0) {
         return;
      }

      LegendItem item0 = items.get(0);
      double itemh = item0.getPreferredHeight();
      double titleh = GAP / 2;

      if(title != null) {
         titleh += title.getBounds().getHeight() + GAP / 2;
      }

      int leftPadding = getLeftPadding(false);
      int rightPadding = getRightPadding(false);

      x = x + leftPadding + lw;
      w = w - leftPadding - rightPadding;
      // reset ncol to 1
      ncol = 1;

      // see how many columns can be fit without clipping.
      // minus 0.5 to account for rounding error. (50647)
      while(w >= getItemPreferredWidth(ncol + 1) - 0.5) {
         ncol++;
      }

      // if not all items can fit, increase the column count to allow clipping
      // instead of let the items missing
      if(!frame.getLegendSpec().isPartial() && h > titleh + GAP) {
         while(Math.ceil((h - titleh - GAP) / itemh) * ncol < items.size()) {
            ncol++;
         }
      }

      int rowCount = getRowCount(ncol);
      int incX = verticalLayout ? 1 : ncol;
      int incY = verticalLayout ? rowCount : 1;
      // numeric grows larger upwards and string goes downwards
      boolean bottomup = frame.getScale() instanceof LinearScale && rowCount > 1;
      double itemw = (int) ((w - lw * 2) / ncol);
      double itemx = x;
      double itemy = y + h - titleh - itemh;

      // @by stephenwebster, For Bug #10019
      // For stack bar graphs, where the items have been reversed, we should
      // also reverse the order in which they are laid out.
      boolean reversed = isReversed();
      int colIndex;
      int colInc = reversed ? -1 : 1;
      List<LegendItem> items = new ArrayList<>(this.items);

      if(reversed) {
         Collections.reverse(items);
      }

      for(int i = 0; i < rowCount; i++) {
         colIndex = reversed ? ncol - 1 : 0;

         for(int j = 0; j < ncol; j++, colIndex += colInc) {
            int idx = i * incX + colIndex * incY;

            // numeric, reverse orders so the smallest value is at bottom
            if(bottomup) {
               idx = items.size() - idx - 1;
            }

            if(idx >= items.size() || idx < 0) {
               continue;
            }

            LegendItem item = items.get(idx);

            item.setBounds(itemx, itemy, itemw - 1, itemh);
            item.layout();
            itemx += itemw;
         }

         itemx = x;
         itemy -= itemh;
      }
   }

   /**
    * Create legend title and init the sizes.
    * @param titleSpec is the title's spec.
    */
   private void createTitle(TextSpec titleSpec) {
      if(titleSpec == null) {
         Font font = new Font("Dialog", Font.BOLD , 11);
         titleSpec = new TextSpec();
         titleSpec.setFont(font);
      }

      Insets padding = frame.getLegendSpec().getPadding();
      int paddingTop = padding != null ? padding.top : 0;
      int paddingBottom = padding != null ? padding.bottom : 0;

      title = new VLabel(Tool.localize(frame.getTitle()), titleSpec);
      title.setInsets(new Insets(paddingTop, getLeftPadding(true), paddingBottom,
                                 getRightPadding(true)));
      title.setAlignmentX(GraphConstants.LEFT_ALIGNMENT);
      addVisual(title);
   }

   /**
    * Create the band visual and init its sizes.
    */
   private void createBand() {
      TextSpec spec = frame.getLegendSpec().getTextSpec();
      Object[] labels = frame.getLabels();
      Object[] values = frame.getValues();
      boolean reversed = false;
      Object first = null;
      Object last = null;

      if(values.length > 0) {
         reversed = values[0] instanceof Comparable &&
            ((Comparable) values[0]).compareTo(values[values.length - 1]) > 0;
         first = labels[0];
         last = labels[labels.length - 1];
      }

      spec = (TextSpec) spec.clone();
      spec.setBackground(null); // background will be filled by content area

      band = new ColorLegendBand((LinearColorFrame) frame);

      if(elem != null) {
         Object alpha = elem.getHint(GraphElement.HINT_ALPHA);

         if(alpha != null) {
            band.setAlpha(Double.parseDouble(alpha + ""));
         }
      }

      addVisual(band);

      if(labels.length > 0) {
         Insets padding = frame.getLegendSpec().getPadding();
         minLabel = new VLabel(reversed ? last : first, spec);
         addVisual(minLabel);

         maxLabel = new VLabel(reversed ? first : last, spec);
         addVisual(maxLabel);

         if(padding != null) {
            minLabel.setInsets(padding);
            maxLabel.setInsets(padding);
         }
      }
   }

   /**
    * Indicates whether the Legend items should be in reverse order.
    */
   private boolean isReversed() {
      // ignore fake categorical frame.
      // only reverse for vertical legend layout
      return (frame instanceof CategoricalFrame || frame instanceof StackedMeasuresFrame) &&
         frame.getField() != null &&
         (verticalLayout || ncol == 1) &&
         !GTool.isPolar(graph.getCoordinate()) && elem != null && elem.isStack();
   }

   /**
    * Create legend items and init its sizes.
    */
   private void createItems() {
      Object[] labels = frame.getLabels();
      Object[] values = frame.getValues();

      // limit by max to prevent run-away graph causing out-of-memory
      String mstr = GTool.getProperty("graph.legend.maxcount",
                                      GDefaults.LEGEND_MAX_COUNT + "");
      int cnt = Math.min(labels.length, Integer.parseInt(mstr));

      for(int i = 0; i < cnt; i++) {
         labels[i] = labels[i] == null ? values[i] : labels[i];

         if(!frame.getLegendSpec().isVisible(labels[i])) {
            continue;
         }

         if(labels[i] instanceof String) {
            String str = (String) labels[i];

            //fix bug1324325850292
            if(str.contains("'")) {
               str = Tool.replaceAll(str, "'", "^_^");
               str = Tool.localize(str);
               str = Tool.replaceAll(str, "^_^", "'");
            }
            else {
               str = Tool.localize(str);
            }

            labels[i] = str;
         }

         LegendItem item = createLegendItem(labels[i], values[i]);
         Color itemColor = getElementColor(values[i]);

         if(itemColor != null) {
            item.setSymbolColor(itemColor);
         }

         if(elem != null) {
            Object alpha = elem.getHint(GraphElement.HINT_ALPHA);

            if(alpha != null) {
               item.setAlpha(Double.parseDouble(alpha + ""));
            }
         }

         items.add(item);
         addVisual(item);
      }
   }

   /**
    * Get the color for the item.
    */
   protected Color getElementColor(Object val) {
      if(elem != null) {
         ColorFrame frame = elem.getSharedColorFrame(getVisualFrame());

         if(frame != null) {
            // optimization
            if(sharedColor == null) {
               Object shareId = this.frame.getShareId();
               sharedColor = CoreTool.equals(shareId, frame.getShareId());
            }

            Color clr;

            if(sharedColor) {
               clr = frame.getColor(val);
            }
            else {
               Object[] vals = frame.getValues();

               if(vals != null && vals.length > 1) {
                  return null;
               }
               else {
                  clr = frame.getColor(val);
               }

               // static color frame may be changed to categorical color
               // frame with the measure name as the value
               if(clr == null && vals != null && vals.length == 1) {
                  clr = frame.getColor(vals[0]);
               }
            }

            // if color is too close the white, it will disappear from the view so just use
            // default color. (63063)
            if(clr != null) {
               Color bg = frame.getLegendSpec().getBackground();
               bg = bg == null ? Color.WHITE : bg;

               if(Math.abs(GTool.getLuminance(clr) - GTool.getLuminance(bg)) < 0.05) {
                  return null;
               }
            }

            return clr;
         }
      }

      return null;
   }

   /**
    * Get an id object to uniquely identify a visual frame. If two frames shares
    * the same ID, we can assume they are showing same data (even if the fields
    * are different).
    */
   public static String getDataId(VisualFrame frame) {
      if(frame instanceof StackedMeasuresFrame) {
         // should ignore stacked measure legend only if default frame is not set. (50344)
         if(((StackedMeasuresFrame) frame).getDefaultFrame() == null) {
            return "stacked-measure";
         }
      }

      return frame.getField() + ":" + CoreTool.arrayToString(frame.getValues());
   }

   /**
    * Find the element containing the visual frame.
    */
   private static GraphElement findElement(EGraph graph, VisualFrame frame) {
      GraphElement elem = null;

      if(graph != null && frame != null) {
         for(int i = 0; i < graph.getElementCount(); i++) {
            GraphElement elem0 = graph.getElement(i);
            VisualFrame[] frames = elem0.getVisualFrames();

            if(!elem0.supportsFrame(frame)) {
               continue;
            }

            for(VisualFrame obj : frames) {
               if(frame == obj) {
                  elem = graph.getElement(i);
                  // don't break outer loop, in cases there are multiple
                  // elements sharing the frame, we use the last one (on top)
                  break;
               }
               else if(obj instanceof CompositeVisualFrame) {
                  if(frame == ((CompositeVisualFrame) obj).getGuideFrame()) {
                     elem = graph.getElement(i);
                     break;
                  }
               }
            }
         }
      }

      return elem;
   }

   /**
    * Paint legend content.
    */
   public void paintContent(Graphics2D g) {
      paintBg(g, getContentPreferredBounds(),
              frame.getLegendSpec().getTextSpec().getBackground());

      if(isScalar) {
         paintBand(g);
      }
      else {
         paintItems(g);
      }
   }

   /**
    * Paint the items.
    */
   private void paintItems(Graphics2D g) {
      for(LegendItem item : items) {
         item.paint(g);
      }
   }

   /**
    * Paint the band.
    * @param g is the graphics context.
    */
   private void paintBand(Graphics2D g) {
      band.paint(g);

      if(minLabel != null) {
         minLabel.paint(g);
      }

      if(maxLabel != null) {
         maxLabel.paint(g);
      }
   }

   /**
    * Paint the items, this method will only paint specify row count.
    * @param g is the graphics context.
    */
   private void paintClippedItems(Graphics2D g) {
      Rectangle clip = g.getClipBounds();

      for(LegendItem item : items) {
         if(clip.intersects(item.getBounds())) {
            item.paint(g);
         }
      }
   }

   /**
    * Paint the visuals.
    * @param g is the graphics context.
    */
   @Override
   public void paint(Graphics2D g) {
      g = (Graphics2D) g.create();
      Shape clip = g.getClip();

      g.setClip(getBounds());

      // intersect the clip shape to avoid legend be painted out of the bounds
      if(clip != null) {
         g.clip(clip);
      }

      LegendSpec spec = frame.getLegendSpec();

      paintBg(g, getBounds(), spec.getBackground());
      paintTitle(g);
      // make sure content not paint to title, fix bug1244448217295
      Graphics2D g2 = (Graphics2D) g.create();
      g2.clip(getContentBounds());

      paintBg(g2, getContentBounds(), spec.getTextSpec().getBackground());

      if(isScalar) {
         paintBand(g2);
      }
      else {
         paintClippedItems(g2);
      }

      g2.dispose();

      if(spec.getBorder() != GraphConstants.NONE) {
         g.setClip(clip);
         g.setColor(spec.getBorderColor());
         Rectangle2D.Double bounds = (Rectangle2D.Double) getBounds();
         float lw = getBorderWidth();
         // @by davyc, the border should paint just in self bounds
         // see bug1239617954595
         /*
         bounds.x = bounds.x + lw;
         bounds.y = bounds.y + lw;
         bounds.width = bounds.width - 2 * lw;
         bounds.height = bounds.height - 2 * lw;
         g.setStroke(GTool.getStroke(spec.getBorder()));
         g.draw(bounds);
          */
         // support double line. (53529)
         // don't draw border outside of bounds. (56446)
         Common.drawRect(g, (float) bounds.x - lw + 1, (float) bounds.y,
                         (float) bounds.width, (float) bounds.height, spec.getBorder());
      }

      g.dispose();
   }

   /**
    * Get the legend border line width.
    */
   private float getBorderWidth() {
      LegendSpec spec = frame.getLegendSpec();
      return GTool.getLineWidth(spec.getBorder());
   }

   /**
    * Fill in background color.
    */
   private void paintBg(Graphics2D g, Rectangle2D bounds, Color bg) {
      if(bg != null && paintBackground) {
         g.setColor(bg);
         g.fill(bounds);
      }
   }

   /**
    * Paint title.
    */
   public void paintTitle(Graphics2D g) {
      if(title == null) {
         return;
      }

      Rectangle2D bounds = title.getBounds();
      double w = bounds.getWidth();
      double x = bounds.getX();
      double y = bounds.getY();
      Graphics2D g2 = (Graphics2D) g.create();
      LegendSpec spec = frame.getLegendSpec();

      // if title format changed, update it to title
      if(!CoreTool.equals(title.getTextSpec(), spec.getTitleTextSpec())) {
         title.setTextSpec(spec.getTitleTextSpec());
      }

      paintBg(g2, getTitleBounds(), spec.getTitleTextSpec().getBackground());
      title.paint(g2, paintBackground);

      if(spec.getBorder() != 0) {
         g2.setColor(spec.getBorderColor());
         /*
         g2.setStroke(GTool.getStroke(spec.getBorder()));
         g2.draw(new Line2D.Double(x, y, x + w, y));
         */
         // support double line. (53529)
         int style = spec.getBorder();
         Common.drawHLine(g2, (float) y, (float) x, (float) (x + w), style, 0, 0);
      }

      g2.dispose();
   }

   /**
    * Check if is scalar.
    * @return <tt>true</tt> if is scalar, <tt>false</tt> otherwise.
    */
   public boolean isScalar() {
      return isScalar;
   }

   /**
    * Get legend title.
    * @return legend title
    */
   public VLabel getTitle() {
      return title;
   }

   /**
    * Create a legend item to display a single value.
    * @param label the item show string.
    * @param value the symbol's type.
    * @return legend item been created.
    */
   protected abstract LegendItem createLegendItem(Object label, Object value);

   /**
    * Get preferred width with the specified height.
    * @param height the available height for legend to display.
    * @param maxw the maximum width available for legends.
    * @return preferred width.
    */
   public double getPreferredWidth(double height, double maxw) {
      Dimension2D psize = frame.getLegendSpec().getPreferredSize();

      if(psize != null && psize.getWidth() > 1) {
         return psize.getWidth();
      }

      if(height == lheight && pwidth >= 0) {
         return pwidth;
      }

      double titlew = 0, titleh = 0;

      if(title != null) {
         titlew = title.getWidth(PREFERRED_CHAR_COUNT) +
            getLeftPadding(true) + getRightPadding(true);
         titleh = title.getPreferredHeight();
      }

      if(isScalar) {
         pwidth = getPreferredWidth0();
         pwidth = Math.max(titlew, pwidth);
      }
      else {
         double aheight = height - titleh - GAP;
         double itemh = items.size() == 0 ? 20 :
            items.get(0).getPreferredHeight();
         int rowCount = Math.max(1, (int) Math.floor(aheight / itemh));
         int colCount = (int) Math.ceil(items.size() * 1.0 / rowCount);
         double itemsw;

         // if the width of columns exceed max width, reduce the column
         while(true) {
            double w = getItemPreferredWidth(colCount) + getLeftPadding(false) +
               getRightPadding(false);

            if(w < maxw || colCount == 1) {
               itemsw = w;
               break;
            }

            colCount--;
         }

         pwidth = Math.max(titlew, itemsw);
      }

      lheight = height;
      return pwidth;
   }

   /**
    * Get preferred height with the specified width.
    * @param width the available width for legend to display.
    * @param maxh the maximum height available for legends.
    * @return preferred height.
    */
   public double getPreferredHeight(double width, double maxh) {
      Dimension2D psize = frame.getLegendSpec().getPreferredSize();

      if(psize != null && psize.getHeight() > 1) {
         return psize.getHeight();
      }

      if(width == lwidth && pheight >= 0) {
         return pheight;
      }

      if(isScalar) {
         pheight = getPreferredHeight0();
      }
      else {
         double titleh = (title != null) ? title.getPreferredHeight() : 0;
         // minus ITEM_LEFT_PADDING, or the preferred height will be false
         double awidth = width - getLeftPadding(false) - getRightPadding(false);
         int colCount = (int) Math.floor(awidth / getItemPreferredWidth(1));
         colCount = colCount <= 0 ? 1 : colCount;

         int rowCount = (int) Math.ceil(items.size() * 1.0 / colCount);
         double itemh = items.size() == 0 ? 20 :
            items.get(0).getPreferredHeight();
         double itemsh = itemh * Math.min(rowCount, (int) (maxh / itemh));
         pheight = titleh + itemsh + GAP + getBorderWidth();
      }

      lwidth = width;
      return pheight;
   }

   /**
    * Get legend height for displaying entire items.
    */
   double getEntireItemHeight(double height) {
      if(items.size() <= 0) {
         return height;
      }

      double itemsh = height;

      if(title != null) {
         itemsh -= title.getPreferredHeight() + GAP + getBorderWidth();
      }
      else {
         itemsh -= GAP / 2 + getBorderWidth() / 2;
      }

      double itemh = items.size() == 0 ? 20 : items.get(0).getPreferredHeight();

      if(itemsh < itemh) {
         return height;
      }

      return height - itemsh % itemh;
   }

   private int getLeftPadding(boolean title) {
      Insets padding = frame.getLegendSpec().getPadding();

      if(padding != null) {
         return padding.left;
      }
      else {
         return title ? TITLE_LEFT_PADDING : ITEM_LEFT_PADDING;
      }
   }

   private int getRightPadding(boolean title) {
      Insets padding = frame.getLegendSpec().getPadding();

      if(padding != null) {
         return padding.right;
      }
      else {
         return title ? TITLE_RIGHT_PADDING : ITEM_RIGHT_PADDING;
      }
   }

   /**
    * Get the graph element containing this legend.
    */
   public GraphElement getGraphElement() {
      return elem;
   }

   private static final int GAP = 4;
   private static final int TITLE_LEFT_PADDING = 2;
   private static final int TITLE_RIGHT_PADDING = 1;
   private static final int ITEM_LEFT_PADDING = TITLE_LEFT_PADDING;
   private static final int ITEM_RIGHT_PADDING = 0;
   private static final int TITLE_LINE_GAP = 1;
   private static final int MIN_CHAR_COUNT = 4;
   private static final int PREFERRED_CHAR_COUNT = 30;

   private final VisualFrame frame;
   private final Vector<LegendItem> items = new Vector<>();
   private VLabel title;
   private ColorLegendBand band;
   private VLabel minLabel;
   private VLabel maxLabel;
   private final boolean isScalar;
   private int ncol = 1;
   private boolean verticalLayout = true;
   private LegendItem[][] itemsArr = null;
   private final EGraph graph;
   private GraphElement elem; // the element containing this frame

   private transient double pwidth = -1; // cached preferred width
   private transient double pheight = -1; // cached preferred height
   private transient double lwidth = -1; // last width for top and bottom
   private transient double lheight = -1; // last height for left and right
   private transient Boolean sharedColor = null; // true if color frame is shared
   private transient boolean paintBackground = true;
}
