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
package inetsoft.graph.guide;

import com.inetsoft.build.tern.*;
import inetsoft.graph.*;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.internal.LabelValue;
import inetsoft.util.MessageFormat;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.*;
import java.math.BigDecimal;
import java.text.*;
import java.util.*;

/**
 * Visual label is a base class for all visual object rendering label.
 * The position of the label is the bottom-left corner of the text. Both
 * position and size are in math coordinate.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
@TernClass(url = "#cshid=VLabel")
public class VLabel extends BoundedVisualizable {
   /**
    * No movement.
    */
   @TernField
   public static final int MOVE_NONE = -1;
   /**
    * Free movement layout.
    */
   @TernField
   public static final int MOVE_FREE = 0;
   /**
    * Hint to only move horizontally to the right.
    */
   @TernField
   public static final int MOVE_RIGHT = 1;
   /**
    * Hint to only move vertically to the top.
    */
   @TernField
   public static final int MOVE_UP = 2;
   /**
    * Hint to only move along the perimeter of an arc.
    */
   @TernField
   public static final int MOVE_ARC = 3;

   // vertical alignments
   private static final int V_MASK = GraphConstants.TOP_ALIGNMENT |
      GraphConstants.MIDDLE_ALIGNMENT |
      GraphConstants.BOTTOM_ALIGNMENT;
   // horizontal alignments
   private static final int H_MASK = GraphConstants.LEFT_ALIGNMENT |
      GraphConstants.CENTER_ALIGNMENT |
      GraphConstants.RIGHT_ALIGNMENT;

   /**
    * Constructor.
    * @param label the label value, could be string or array of string.
    */
   public VLabel(Object label) {
      this.label = label;
   }

   /**
    * Set the label value.
    */
   @TernMethod
   public void setLabel(Object label) {
      this.label = label;
      dlabel = null;
      dlabels0 = null;
   }

   /**
    * Constructor.
    * @param label the output string.
    * @param textSpec new text spec.
    */
   @TernConstructor
   public VLabel(Object label, TextSpec textSpec) {
      this(label);
      setTextSpec(textSpec);
   }

   @Override
   public void paint(Graphics2D g) {
      paint(g, true);
   }

   /**
    * Paint the title.
    * @param bg true to paint background.
    */
   public void paint(Graphics2D g, boolean bg) {
      Graphics2D g2 = (Graphics2D) g.create();
      Font font = getFont();
      FontMetrics fm = GTool.getFontMetrics(font);
      String[] dlabels = getDisplayLabel();
      boolean horizontal = (spec.getRotation() % 180) == 0;
      boolean vertical = ((spec.getRotation() + 90) % 180) == 0;
      Shape transBounds = getTransformedBounds();
      Rectangle2D bounds = getBounds();

      clipGraphics(g2);

      if(bg && spec.getBackground() != null) {
         g2.setColor(spec.getBackground());

         // @by larryl, rotated label could be out of the bounds set by
         // setBounds, use transformed bounds to avoid text outside of bg
         if(!horizontal && !vertical) {
            g2.fill(transBounds);
         }
         else {
            g2.fill(bounds);
         }
      }

      // use transform and rotation
      Point2D p2d = getTextPosition();
      double x = p2d.getX();
      double y = p2d.getY();
      double angle = Math.toRadians(spec.getRotation());
      double yIncr = fm.getHeight();
      Color clr = getColor();

      g2.setFont(font);
      g2.setColor(clr);

      double spacing = spec.getLineSpacing();

      // space out lines across height/width
      if(vertical && !Double.isNaN(spacing)) {
         // the equation for solving the gap and bar width:
         // width = gap * (n + 1) + bar * n
         // where bar = gap / spacing
         int n = dlabels.length;
         double gap = spacing == 0 ? 0 : bounds.getWidth() / (n + 1 + n / spacing);
         double bar = spacing == 0 ? bounds.getWidth() / n : gap / spacing;
         // center the text in the middle of the bar
         x = bounds.getMaxX() - gap - (bar - fm.getHeight()) / 2;
         yIncr = gap + bar;
      }
      else if(horizontal && !Double.isNaN(spacing)) {
         int n = dlabels.length;
         double gap = spacing == 0 ? 0 : bounds.getHeight() / (n + 1 + n / spacing);
         double bar = spacing == 0 ? bounds.getHeight() / n : gap / spacing;
         y = bounds.getY() + gap + (bar - fm.getHeight()) / 2;
         yIncr = gap + bar;
      }

      g2.translate(x, y);
      g2.rotate(angle);
      g2.translate(0, fm.getDescent());

      for(int i = dlabels.length - 1; i >= 0; i--) {
         double tx = 0;

         if("Others".equals(dlabels[i])) {
            dlabels[i] = Catalog.getCatalog().getString("Others");
         }

         // align if there are multiple lines
         if(dlabels.length > 1) {
            double mw = GTool.stringWidth(dlabels, font);
            int alignx = getAlignmentX();

            if(alignx == GraphConstants.RIGHT_ALIGNMENT) {
               tx = mw - fm.stringWidth(dlabels[i]);
            }
            else if(alignx == GraphConstants.CENTER_ALIGNMENT) {
               tx = (mw - fm.stringWidth(dlabels[i])) / 2;
            }
         }

         GTool.drawString(g2, dlabels[i], tx, 0);
         g2.translate(0, yIncr);
      }

      g2.dispose();
   }

   // clip the graphics for painting if necessary
   protected void clipGraphics(Graphics2D g2) {
      // allow drawing to be out of bounds for rotated labels
      if(clipWidth || clipHeight) {
         FontMetrics fm = GTool.getFontMetrics(getFont());
         Rectangle2D box = getBounds();
         double bx = box.getX();
         double by = box.getY();
         double bw = box.getWidth();
         double bh = box.getHeight();
         boolean horizontal = (spec.getRotation() % 180) == 0;
         boolean vertical = ((spec.getRotation() + 90) % 180) == 0;

         if(!clipWidth) {
            bx = bx - 10000;
            bw = bw + 20000;
         }

         if(!clipHeight) {
            by = by - 10000;
            bh = bh + 20000;
         }

         if(horizontal) {
            // the axis labels are set to size that are divided by the
            // number of ticks on the axis. but when the label is laid out,
            // the overlapping labels are removed, so the clipping on the
            // height shouldn't cut the line in half
            if(clipHeight && box.getHeight() < fm.getHeight()) {
               double diff = Math.ceil((fm.getHeight() - box.getHeight()) / 2);
               by -= diff;
               bh += diff * 2 + 1;
            }

            // increase the width by a little to avoid clipping the text
            // unnecessarily, same reason as above
            if(clipWidth) {
               // if the label is moved away, account for the space
               if(offset.getX() < 0) {
                  bx += offset.getX();
                  bw -= offset.getX();
               }
               else {
                  bx -= 1;
                  bw += 2;
               }
            }
         }
         else if(vertical) {
            // @see above
            if(clipWidth && box.getWidth() < fm.getHeight()) {
               double diff = Math.ceil((fm.getHeight() - box.getWidth()) / 2);
               bx -= diff;
               bw += diff * 2 + 1;
            }

            if(clipHeight) {
               // if the label is moved away, account for the space
               if(offset.getY() < 0) {
                  by += offset.getY();
                  bh -= offset.getY();
               }
               else {
                  by -= 1;
                  bh += 2;
               }
            }
         }

         g2.clip(new Rectangle2D.Double(bx, by, bw, bh));
      }
   }

   /**
    * Get the text position of vlabel (bottom-left). The text position may be
    * different from the label position due to alignments.
    */
   @TernMethod
   public Point2D getTextPosition() {
      double height = getSize().getHeight();
      double width = getSize().getWidth();
      String[] dlabels = getDisplayLabel();
      Point2D pos = getPosition();
      // use transform and rotation
      Rectangle2D tbounds = getTransformedBounds0(0, 0, dlabels).getBounds2D();
      double lwidth = tbounds.getWidth();
      double lheight = tbounds.getHeight();

      if(offset != null) {
         pos = new Point2D.Double(pos.getX() + offset.getX(),
                                  pos.getY() + offset.getY());
      }

      int alignx = getAlignmentX0();
      int aligny = getAlignmentY0();

      return alignText(pos, width, height, lwidth, lheight, alignx, aligny);
   }

   /**
    * Get the text position within label bounds.
    * @param pos label outer bounds position.
    * @param width label outer bounds width.
    * @param height label outer bounds height.
    * @param lwidth label text width;
    * @param lheight label text height;
    * @param alignx horizontal alignment.
    * @param aligny vertical alignment.
    */
   protected Point2D alignText(Point2D pos, double width, double height,
                               double lwidth, double lheight, int alignx, int aligny)
   {
      Insets insets = getTransformedInsets(this.insets, -spec.getRotation());
      double x = 0;

      if(alignx == GraphConstants.LEFT_ALIGNMENT) {
         x = pos.getX() + insets.left;
      }
      else if(alignx == GraphConstants.RIGHT_ALIGNMENT) {
         x = pos.getX() + width - lwidth + insets.left;
      }
      else if(alignx == GraphConstants.CENTER_ALIGNMENT) {
         x = pos.getX() + width / 2 - lwidth / 2 + insets.left;
      }

      double y = 0;

      if(aligny == GraphConstants.TOP_ALIGNMENT) {
         y = pos.getY() + height - lheight + insets.bottom;
      }
      else if(aligny == GraphConstants.BOTTOM_ALIGNMENT) {
         y = pos.getY() + insets.bottom;
      }
      else if(aligny == GraphConstants.MIDDLE_ALIGNMENT) {
         y = pos.getY() + height / 2 - lheight / 2 + insets.bottom;
      }

      return new Point2D.Double(x, y);
   }

   /**
    * Get the string to be displayed. The text may be truncated to fit in the
    * assigned space.
    * @hidden
    * @return lines of text to display in the label.
    */
   public String[] getDisplayLabel() {
      if(dlabels0 != null && getBounds().equals(dbounds0)) {
         return dlabels0;
      }

      String label = getText();
      dbounds0 = (Rectangle2D) getBounds().clone();
      dlabels0 = getLines(label);
      truncated = false;

      // truncating a number and append with .. is meaningless. The resulting
      // string doesn't convey any information.
      if(isNumeric()) {
         return dlabels0;
      }

      boolean verticalLabel = (spec.getRotation() + 90) % 180 == 0;
      boolean horizontalLabel = spec.getRotation() % 180 == 0;

      if(!truncate) {
         if(!verticalLabel && !horizontalLabel) {
            return dlabels0;
         }

         return dlabels0.length == 1 ? wrapLine(label, verticalLabel) : dlabels0;
      }

      // wrap if no explicit newline and height is tall enough
      if(dlabels0.length == 1) {
         dlabels0 = wrapLine(label, verticalLabel);
      }

      Rectangle2D tbounds = getTransformedBounds0(0, 0, dlabels0).getBounds2D();
      double dlabelWidth = tbounds.getWidth();
      double dlabelHeight = tbounds.getHeight();
      Dimension2D size = getSize();
      double width = size.getWidth();
      double height = size.getHeight();
      String[] dlabels = dlabels0;

      // too small to truncate, ignore
      if(label.length() <= 2 || width == 0 ||
         ((dlabelWidth <= width + 1 || verticalLabel && dlabels.length == 1) &&
          (dlabelHeight <= height + 1 || horizontalLabel && dlabels.length == 1)))
      {
         int lineTotal = Arrays.stream(dlabels).mapToInt(d -> d.length() + 1).sum() - 1;

         if(lineTotal < label.length() && dlabels.length > 0) {
            String last = dlabels[dlabels.length - 1].trim();
            dlabels[dlabels.length - 1] = last.substring(0, Math.max(0, last.length() - 2)) + "...";
         }

         return dlabels;
      }

      // rotation at an angle should allow it to draw out of bounds, otherwise
      // rotating 45 degrees would reduce the string to very short
      if(spec.getRotation() % 90 != 0) {
         if(maxSize == null || size.getWidth() < maxSize.getWidth() &&
            size.getHeight() < maxSize.getHeight())
         {
            return dlabels;
         }

         // if truncating rotated label, allow it to stretch out of bounds
         // as it's more efficient use of available space
         if(maxSize != null) {
            width = Math.max(width, maxSize.getWidth());
            height = Math.max(height, maxSize.getHeight());
         }
      }

      dlabels = truncate(dlabels, width, height);

      return dlabels0 = dlabels;
   }

   private String[] truncate(String[] dlabels, double width, double height) {
      // inner bounding box width/height without insets
      double iwidth = width - insets.left - insets.right;
      double iheight = height - insets.top - insets.bottom;
      double angle = Math.toRadians(spec.getRotation());
      boolean vertical = (spec.getRotation() + 90) % 180 == 0;
      boolean horizontal = spec.getRotation() % 180 == 0;
      // truncated text width/height (.. subtracted)
      double twidth = vertical ?
         iwidth : iwidth - getPreferredWidth("..") * Math.abs(Math.cos(angle));
      double theight = horizontal ?
         iheight : iheight - getPreferredHeight("..") * Math.abs(Math.sin(angle));

      if(twidth <= 0) {
         twidth = getSize().getWidth();
      }

      if(theight <= 0) {
         theight = getSize().getHeight();
      }

      double totalSize = 0;
      double maxSize = horizontal ? iheight : iwidth;
      boolean truncateLines = false;

      for(int i = 0; i < dlabels.length; i++) {
         String dlabel = dlabels[i];
         Rectangle2D dbounds = getTransformedBounds0(0, 0, dlabel).getBounds2D();
         double size = horizontal ? dbounds.getHeight() : dbounds.getWidth();
         totalSize += size;
         // check if next line will be truncated. for lastOrAll, if a line is truncated,
         // we only keep the first line even if there may be more lines that can fit.
         truncateLines = this.truncateLines &&
            (lastOrAll && i == dlabels.length - 1 && size * dlabels.length > maxSize ||
               !lastOrAll && i < dlabels.length - 1 && totalSize + size > maxSize);
         boolean truncateIt = dbounds.getWidth() > iwidth && !vertical ||
            dbounds.getHeight() > iheight && !horizontal || truncateLines;

         // only truncate and add '..' if its wider than inner width.
         if(truncateIt) {
            while((dbounds.getWidth() > twidth && !vertical ||
               dbounds.getHeight() > theight && !horizontal) && dlabel.length() > 1) {
               dlabel = dlabel.substring(0, dlabel.length() - 1);
               dbounds = getTransformedBounds0(0, 0, dlabel).getBounds2D();
            }

            dlabels[i] = dlabel + "..";
            truncated = true;
         }

         // truncate rest of lines if they are out of bounds.
         if(truncateLines) {
            if(lastOrAll) {
               dlabels = new String[] {
                  spec.isReverseLines() ? dlabels[0] : dlabels[dlabels.length - 1]
               };

               if(dlabels[0].endsWith("..")) {
                  dlabels[0] = ".." + dlabels[0].substring(0, dlabels[0].length() - 2);
               }
               else if(truncated) {
                  dlabels[0] = ".." + dlabels[0];
               }
            }
            else {
               dlabels = Arrays.copyOfRange(dlabels, 0, i + 1);
            }
            break;
         }
      }

      return dlabels;
   }

   /**
    * Check if the label is truncated (and appended with ..).
    */
   @TernMethod
   public boolean isTruncated() {
      getDisplayLabel();
      return truncated;
   }

   private String[] wrapLine(String label, boolean verticalLabel) {
      String[] lines = getLines(label);

      if(wrap) {
         double width = verticalLabel ? getBounds().getHeight() : getBounds().getWidth();
         double height = verticalLabel ? getBounds().getWidth() : getBounds().getHeight();
         Font font = getFont();
         FontMetrics fm = GTool.getFontMetrics(font);

         // wrap if there are space for two lines
         if(height > fm.getHeight() * 1.6) {
            dlabels0 = GTool.breakLine(label, font, 0, width, height);
            return dlabels0;
         }
      }

      return lines;
   }

   /**
    * Get min height.
    */
   @Override
   protected double getMinHeight0() {
      if(isNumeric()) {
         return getPreferredHeight0();
      }

      String txt = getText();

      if(txt.length() > 1) {
         txt = txt.substring(0, 1);
      }

      double h = getPreferredHeight(getLines(txt));

      if(maxSize != null) {
         h = Math.min(h, maxSize.getHeight());
      }

      return h;
   }

   /**
    * Get min width.
    */
   @Override
   protected double getMinWidth0() {
      if(isNumeric()) {
         return getPreferredWidth0();
      }

      String txt = getText();

      if(txt.length() > 1) {
         txt = txt.substring(0, 1);
      }

      double w = getPreferredWidth(getLines(txt));

      if(maxSize != null) {
         w = Math.min(w, maxSize.getWidth());
      }

      return w;
   }

   /**
    * Get preferred width.
    */
   @Override
   protected double getPreferredWidth0() {
      double pwidth = getPreferredWidth(getLinesForSize());

      if(isNumeric() || maxSize == null) {
         return pwidth;
      }

      return Math.min(maxSize.getWidth(), pwidth);
   }

   /**
    * Get preferred height.
    */
   @Override
   protected double getPreferredHeight0() {
      double pheight = getPreferredHeight(getLinesForSize());

      if(isNumeric() || maxSize == null) {
         return pheight;
      }

      return Math.min(maxSize.getHeight(), pheight);
   }

   private String[] getLines(String str) {
      String[] lines = CoreTool.split(str, '\n');

      if(lines.length > 1 && getTextSpec().isReverseLines()) {
         String[] rlines = new String[lines.length];

         for(int i = 0; i < lines.length; i++) {
            rlines[rlines.length - i - 1] = lines[i];
         }

         return rlines;
      }

      return lines;
   }

   private String[] getLinesForSize() {
      String[] lines = getLines(getText());

      // if lines can be truncated to a single line, only use one line to calculate size.
      // if may truncate lines, reserve space for '..' at the beginning of the last line,
      // so a '2018 Apr\n...\n2021 Apr' will become '..2021 Apr'.
      if(lines.length > 1 && lastOrAll) {
         String lastLine = getTextSpec().isReverseLines() ? lines[0] : lines[lines.length - 1];
         lines = new String[] { ".." + lastLine };
      }

      return lines;
   }

   /**
    * Calculate preferred height with wrapping.
    */
   @TernMethod
   public double getPreferredHeight(double width, double height) {
      Font font = getFont();
      String[] lines = GTool.breakLine(getText(), font, 0, width, height);
      return getPreferredHeight(lines);
   }

   /**
    * Get specified string's preferred width.
    * @hidden
    */
   public double getPreferredWidth(String... str) {
      return getTransformedBounds0(0, 0, str).getBounds2D().getWidth();
   }

   /**
    * Get specified string's preferred height.
    * @hidden
    */
   public double getPreferredHeight(String... str) {
      return getTransformedBounds0(0, 0, str).getBounds2D().getHeight();
   }

   /**
    * Get the text bounds after screen transformation.
    */
   public Shape getTransformedBounds() {
      Point2D pt = getTextPosition();
      return getTransformedBounds0(pt.getX(), pt.getY(), getDisplayLabel());
   }

   /**
    * Get the text bounds after screen transformation.
    */
   private Shape getTransformedBounds0(double x, double y, String... lines) {
      FontMetrics fm = GTool.getFontMetrics(getFont());
      double lwidth = GTool.stringWidth(lines, getFont());
      double lheight = fm.getHeight() * lines.length;
      Shape bounds = new Rectangle2D.Double(
         x - insets.left, y - insets.bottom,
         lwidth + insets.left + insets.right,
         lheight + insets.top + insets.bottom);

      if(spec.getRotation() != 0) {
         double angle = Math.toRadians(spec.getRotation());
         AffineTransform trans = AffineTransform.getRotateInstance(angle, x, y);

         bounds = trans.createTransformedShape(bounds);
      }

      return bounds;
   }

   /**
    * Check if this label text is completely contained in the outer shape.
    * @param xmoe margin of error on x direction.
    * @param ymoe margin of error on y direction.
    * @hidden
    */
   public boolean isContained(Shape outer, int xmoe, int ymoe) {
      Shape shape = getTransformedBounds();

      // optimization
      if(shape instanceof RectangularShape && outer instanceof RectangularShape) {
         RectangularShape rect = (RectangularShape) shape;
         final double width = Math.max(1, rect.getWidth() - 2 * xmoe);
         final double height = Math.max(1, rect.getHeight() - 2 * ymoe);
         return outer.contains(rect.getX() + xmoe, rect.getY() + ymoe, width, height);
      }

      Area oarea = new Area(outer);

      for(PathIterator iter = shape.getPathIterator(null); !iter.isDone(); iter.next()) {
         double[] coords = new double[6];
         int type = iter.currentSegment(coords);

         if(type != PathIterator.SEG_MOVETO && type != PathIterator.SEG_LINETO) {
            continue;
         }

         if(!outer.contains(coords[0], coords[1])) {
            // ignore very small overlapping
            Area area = (Area) oarea.clone();
            Rectangle2D ptrect = new Rectangle2D.Double(coords[0] - 1, coords[1] - 1, 2, 2);
            area.intersect(new Area(ptrect));
            Rectangle2D overlap = area.getBounds2D();

            if(overlap.getWidth() > 1 && overlap.getHeight() > 1 ||
               overlap.getWidth() == 0 && overlap.getHeight() == 0)
            {
               return false;
            }
         }
      }

      return true;
   }

   /**
    * Get the label max size.
    */
   @TernMethod
   public Dimension2D getMaxSize() {
      return maxSize;
   }

   /**
    * Set the label max size.
    */
   @TernMethod
   public void setMaxSize(Dimension2D maxSize) {
      if(!Objects.equals(this.maxSize, maxSize)) {
         this.maxSize = maxSize;
         invalidate();
      }
   }

   /**
    * Get the formatted text string.
    */
   @TernMethod
   public String getText() {
      if(dlabel != null) {
         return dlabel;
      }

      Format fmt = spec.getFormat();
      Object label = this.label;

      if(label instanceof LabelValue) {
         label = ((LabelValue) label).getText();
      }
      else if(label instanceof Object[]) {
         Object[] arr = (Object[]) label;
         boolean hasNull = Arrays.stream(arr).anyMatch(a -> a == null);

         if(hasNull || !MessageFormat.isMessageFormat(fmt)) {
            return formatArray((Object[]) label);
         }
      }

      if(fmt != null) {
         try {
            return dlabel = GTool.format(fmt, label);
         }
         catch(Exception ex) {
            if(LOG.isDebugEnabled()) {
               LOG.warn("Failed to format label: " + label, ex);
            }
            else {
               LOG.info("Failed to format label: " + label + " " + ex);
            }
         }
      }

      return dlabel = formatValue(label);
   }

   private String formatValue(Object label) {
      if(label instanceof Object[]) {
         return formatArray((Object[]) label);
      }
      else if(label instanceof Date) {
         if(label instanceof java.sql.Time) {
            return TIME.get().format(label);
         }
         else if(label instanceof java.sql.Timestamp) {
            return DATE_TIME.get().format(label);
         }

         return DATE.get().format(label);
      }
      else if(isFloat(label)) {
         return DECIMAL.get().format(label);
      }

      return CoreTool.toString(label);
   }

   private String formatArray(Object[] labels) {
      StringBuilder buf = new StringBuilder();
      Format[] fmts = spec.getFormat() instanceof java.text.MessageFormat
         ? ((java.text.MessageFormat) spec.getFormat()).getFormatsByArgumentIndex() : new Format[0];

      for(int i = 0; i < labels.length; i++) {
         if(labels[i] == null) {
            continue;
         }

         if(i > 0) {
            buf.append("\n");
         }

         if(i < fmts.length) {
            try {
               buf.append(fmts[i].format(labels[i]));
               continue;
            }
            catch(Exception ex) {
               // ignore
            }
         }

         buf.append(formatValue(labels[i]));
      }

      return buf.toString();
   }

   /**
    * Check if is float.
    */
   private boolean isFloat(Object label) {
      return label instanceof Double || label instanceof Float ||
         label instanceof BigDecimal;
   }

   /**
    * Set the default horizontal alignment. The alignment is used if the
    * alignment in the TextSpec is not set.
    */
   @TernMethod
   public void setAlignmentX(int alignx) {
      this.alignx = alignx;
   }

   /**
    * Set the default vertical alignment. The alignment is used if the
    * alignment in the TextSpec is not set.
    */
   @TernMethod
   public void setAlignmentY(int aligny) {
      this.aligny = aligny;
   }

   /**
    * Gets the the horizontal alignment.
    */
   @TernMethod
   public int getAlignmentX() {
      int align = spec.getAlignment() & H_MASK;
      return (align == 0) ? alignx : align;
   }

   /**
    * Gets the the vertical alignment.
    */
   @TernMethod
   public int getAlignmentY() {
      int align = spec.getAlignment() & V_MASK;
      return (align == 0) ? aligny : align;
   }

   /**
    * Get the x alignment after rotation.
    */
   private int getAlignmentX0() {
      double rotate = spec.getRotation();

      // if it's a label form we shouldn't change the alignment so the
      // user specified position is honored
      if(rotate == 0 || isLabelForm()) {
         return getAlignmentX();
      }

      Point pt = getRotatedPos();

      if(pt.x < 0) {
         return GraphConstants.LEFT_ALIGNMENT;
      }
      else if(pt.x > 0) {
         return GraphConstants.RIGHT_ALIGNMENT;
      }

      return GraphConstants.CENTER_ALIGNMENT;
   }

   /**
    * Get the y alignment after rotation.
    */
   private int getAlignmentY0() {
      double rotate = spec.getRotation();

      if(rotate == 0) {
         return getAlignmentY();
      }

      Point pt = getRotatedPos();

      if(pt.y < 0) {
         return GraphConstants.BOTTOM_ALIGNMENT;
      }
      else if(pt.y > 0) {
         return GraphConstants.TOP_ALIGNMENT;
      }

      return GraphConstants.MIDDLE_ALIGNMENT;
   }

   /**
    * Get the rotated position.
    */
   private Point getRotatedPos() {
      int alignx = getAlignmentX();
      int aligny = getAlignmentY();
      double rotate = spec.getRotation() * Math.PI / 180;
      Point pt = new Point(0, 0);

      switch(alignx) {
      case GraphConstants.CENTER_ALIGNMENT:
         break;
      case GraphConstants.RIGHT_ALIGNMENT:
         pt.x = 100;
         break;
      case GraphConstants.LEFT_ALIGNMENT:
      default:
         pt.x = -100;
      }

      switch(aligny) {
      case GraphConstants.MIDDLE_ALIGNMENT:
         break;
      case GraphConstants.TOP_ALIGNMENT:
         pt.y = 100;
         break;
      case GraphConstants.BOTTOM_ALIGNMENT:
      default:
         pt.y = -100;
      }

      return (Point) AffineTransform.getRotateInstance(rotate).transform(pt, pt);
   }

   /**
    * Get label value. The value may be formatted to get the text string if
    * a format is specified.
    */
   @TernMethod
   public Object getLabel() {
      return label;
   }

   /**
    * Get the unformatted value of the label.
    */
   @TernMethod
   public Object getValue() {
      return label;
   }

   /**
    * Set the text attributes.
    */
   @TernMethod
   public void setTextSpec(TextSpec spec) {
      if(spec == null) {
         spec = new TextSpec();
      }

      this.spec = spec;
   }

   /**
    * Get the text attributes.
    */
   @TernMethod
   public TextSpec getTextSpec() {
      return spec;
   }

   /**
    * Get the text color of this label.
    */
   @TernMethod
   public Color getColor() {
      return autoBG != null ? GTool.getTextColor(autoBG) : spec.getColor(label);
   }

   /**
    * Get the font of this label.
    */
   @TernMethod
   public Font getFont() {
      return (font != null) ? font : spec.getFont(label);
   }

   /**
    * Set the font for this label, which overrides the font in text spec.
    */
   @TernMethod
   public void setFont(Font font) {
      this.font = font;
   }

   /**
    * Get the background color for calculating text color.
    */
   @TernMethod
   public Color getAutoBackground() {
      return autoBG;
   }

   /**
    * Set the background color for calculating text color. If the color is set, the
    * color in TextSpec is ignored. The text will be black or white depending on the
    * background to achieve maximum contrast.
    */
   @TernMethod
   public void setAutoBackground(Color bg) {
      this.autoBG = bg;
   }

   /**
    * Get the offset resulting from the rotation. The offset is the distance
    * from the edge of the label after rotation to the label position. Which
    * edge is decided by how a label is supposed to align with the axis.
    * @hidden
    * @param lpos the position the label should be aligned to.
    * @param side the placement of label relative to lpos, TOP, LEFT, BOTTOM,
    * or RIGHT.
    */
   public Point2D getRotationOffset(Point2D lpos, int side) {
      if(spec.getRotation() == 0) {
         return new Point2D.Double(0, 0);
      }

      // should not accumulate offsets
      offset = new Point2D.Double(0, 0);

      double angle = Math.toRadians(spec.getRotation());
      Font font = getFont();
      FontMetrics fm = GTool.getFontMetrics(font);
      String[] dlabels = getDisplayLabel();
      double strw = GTool.stringWidth(dlabels, font);
      double fontH = fm.getHeight() * dlabels.length;
      Point2D tpos = getTextPosition();
      Point2D[] pts = {
         new Point2D.Double(tpos.getX(), tpos.getY()),
         new Point2D.Double(tpos.getX() + strw, tpos.getY()),
         new Point2D.Double(tpos.getX() + strw, tpos.getY() + fontH),
         new Point2D.Double(tpos.getX(), tpos.getY() + fontH),
      };
      AffineTransform trans = AffineTransform.getRotateInstance(
         angle, tpos.getX(), tpos.getY());
      Point2D pt;

      for(int i = 0; i < pts.length; i++) {
         pts[i] = trans.transform(pts[i], null);
      }

      if(side == GraphConstants.RIGHT) {
         pt = findCorner(pts, new Insets(0, 1, 0, 0));
      }
      else if(side == GraphConstants.BOTTOM) {
         pt = findCorner(pts, new Insets(1, 0, 0, 0));
      }
      else if(side == GraphConstants.LEFT) {
         pt = findCorner(pts, new Insets(0, 0, 0, 1));
      }
      else if(side == GraphConstants.TOP) {
         pt = findCorner(pts, new Insets(0, 0, 1, 0));
      }
      else {
         Point2D right = findCorner(pts, new Insets(0, 1, 0, 0));
         Point2D bottom = findCorner(pts, new Insets(1, 0, 0, 0));
         Point2D left = findCorner(pts, new Insets(0, 0, 0, 1));
         Point2D top = findCorner(pts, new Insets(0, 0, 1, 0));
         pt =  new Point2D.Double((left.getX() + right.getX()) / 2,
            (top.getY() + bottom.getY()) / 2);
      }

      return new Point2D.Double(lpos.getX() - pt.getX(), lpos.getY()-pt.getY());
   }

   /**
    * Find the corner point at top, left, bottom, or right side. The side is
    * marked by a non-zero value in insets.
    */
   private Point2D findCorner(Point2D[] corners, Insets side) {
      double v = (side.bottom != 0 || side.left != 0)
         ? Integer.MAX_VALUE : Integer.MIN_VALUE;
      Vector pts = new Vector(); // matching points

      for(int i = 0; i < corners.length; i++) {
         if(side.top != 0) {
            if(corners[i].getY() > v) {
               v = corners[i].getY();
               pts.removeAllElements();
            }

            if(corners[i].getY() == v) {
               pts.add(corners[i]);
            }
         }
         else if(side.left != 0) {
            if(corners[i].getX() < v) {
               v = corners[i].getX();
               pts.removeAllElements();
            }

            if(corners[i].getX() == v) {
               pts.add(corners[i]);
            }
         }
         else if(side.bottom != 0) {
            if(corners[i].getY() < v) {
               v = corners[i].getY();
               pts.removeAllElements();
            }

            if(corners[i].getY() == v) {
               pts.add(corners[i]);
            }
         }
         else if(side.right != 0) {
            if(corners[i].getX() > v) {
               v = corners[i].getX();
               pts.removeAllElements();
            }

            if(corners[i].getX() == v) {
               pts.add(corners[i]);
            }
         }
      }

      double x = 0, y = 0;

      // if there are multiple points at the edge, use the center point
      for(int i = 0; i < pts.size(); i++) {
         Point2D pt = (Point2D) pts.get(i);

         x += pt.getX();
         y += pt.getY();
      }

      return new Point2D.Double(x / pts.size(), y / pts.size());
   }

   /**
    * Get the offset resulting from the rotation. The offset is the distance
    * from the edge of the label after rotation to the label position. Which
    * edge is decided by how a label is supposed to align with the axis.
    * @hidden
    * @param lpos the position to align to.
    * @param tick the angle from the center of the polar coord.
    */
   public Point2D getRotationOffset(Point2D lpos, double tick) {
      if(spec.getRotation() == 0) {
         return new Point2D.Double(0, 0);
      }

      double angle = Math.toRadians(spec.getRotation());
      Font font = getFont();
      FontMetrics fm = GTool.getFontMetrics(font);
      String[] dlabels = getDisplayLabel();
      double strw = GTool.stringWidth(dlabels, font);
      double fontH = fm.getHeight() * dlabels.length;
      Point2D tpos = getTextPosition();
      Point2D pos0 = getPosition();
      Rectangle2D rect = new Rectangle2D.Double(tpos.getX(), tpos.getY(),
                                                strw, fontH);
      AffineTransform trans = AffineTransform.getRotateInstance(
         angle, tpos.getX(), tpos.getY());

      rect = trans.createTransformedShape(rect).getBounds2D();

      // make sure the tick is positive
      while(tick < 0) {
         tick += Math.PI * 2;
      }

      // from 0 - 360
      while(tick > Math.PI * 2) {
         tick -= Math.PI * 2;
      }

      double x = 0, y = 0;

      // top
      if(Math.abs(tick - Math.PI / 2) < 0.1) {
         // get the bottom center point
         x = rect.getX() + rect.getWidth() / 2;
         y = rect.getY();
      }
      // bottom
      else if(Math.abs(tick - Math.PI * 1.5) < 0.1) {
         // get the top center point
         x = rect.getX() + rect.getWidth() / 2;
         y = rect.getY() + rect.getHeight();
      }
      // upper left
      else if(tick > Math.PI / 2 && tick <= Math.PI) {
         // get the bottom right point
         x = rect.getX() + rect.getWidth();
         y = rect.getY();
      }
      // upper right
      else if(tick >= 0 && tick < Math.PI / 2) {
         // get the bottom left point
         x = rect.getX();
         y = rect.getY();
      }
      // bottom left
      else if(tick > Math.PI && tick < Math.PI * 1.5) {
         // get the upper right point
         x = rect.getX() + rect.getWidth();
         y = rect.getY() + rect.getHeight();
      }
      // bottom right
      else {
         // get the upper left point
         x = rect.getX();
         y = rect.getY() + rect.getHeight();
      }

      return new Point2D.Double(lpos.getX() - x, lpos.getY() - y);
   }

   /**
    * Adjust offset to align the text in the specified bounds.
    * @hidden
    */
   public void alignOffset(double x, double y, double w, double h) {
      if(offset == null || offset.getX() == 0 && offset.getY() == 0) {
         return;
      }

      Rectangle2D b2 = getTransformedBounds().getBounds2D();
      int alignx = getAlignmentX0();
      int aligny = getAlignmentY0();
      double x2 = x - b2.getX();
      double y2 = y - b2.getY();

      switch(alignx) {
      case GraphConstants.CENTER_ALIGNMENT:
         x2 += (w - b2.getWidth()) / 2;
         break;
      case GraphConstants.RIGHT_ALIGNMENT:
         x2 += w - b2.getWidth();
         break;
      }

      switch(aligny) {
      case GraphConstants.MIDDLE_ALIGNMENT:
         y2 += (h - b2.getHeight()) / 2;
         break;
      case GraphConstants.TOP_ALIGNMENT:
         y2 += h - b2.getHeight();
         break;
      }

      offset = new Point2D.Double(offset.getX() + x2, offset.getY() + y2);
   }

   /**
    * Set offset.
    * @hidden
    */
   public void setOffset(Point2D offset) {
      this.offset = offset;
   }

   /**
    * Get the offset for rotation.
    * @hidden
    */
   public Point2D getOffset() {
      return offset;
   }

   /**
    * Get visual label width when it displays specified count char. If count is
    * greater than label char count, ".." displays at the end of the label.
    * @hidden
    * @return specified count char label width.
    */
   public double getWidth(int count) {
      String label = getText();

      if(label.length() > count) {
         label = label.substring(0, count) + "..";
      }

      return getPreferredWidth(label);
   }

   /**
    * Set the label insets.
    */
   @TernMethod
   public void setInsets(Insets insets) {
      this.insets = insets;
   }

   /**
    * Get the label insets.
    */
   @TernMethod
   public Insets getInsets() {
      return insets;
   }

   /**
    * Get the text collision resolution option.
    */
   @TernMethod
   public int getCollisionModifier() {
      return modifier;
   }

   /**
    * Set the text collision resolution option.
    */
   @TernMethod
   public void setCollisionModifier(int modifier) {
      this.modifier = modifier;
   }

   /**
    * Check if label is numeric.
    */
   private boolean isNumeric() {
      return this.label instanceof Number &&
         (spec.getFormat() == null || spec.getFormat() instanceof NumberFormat);
   }

   /**
    * Check whether label could be truncated with "..".
    */
   @TernMethod
   public boolean isTruncate() {
      return truncate;
   }

   /**
    * Set whethert label could be truncated. If set to true, the label will be
    * truncated with ".." when there is no splace to dislay it.
    */
   @TernMethod
   public void setTruncate(boolean truncate) {
      this.truncate = truncate;
   }

   /**
    * Check if text can be wrapped into multiple lines.
    */
   @TernMethod
   public boolean isWrapping() {
      return wrap;
   }

   /**
    * Set if text can be wrapped into multiple lines.
    */
   @TernMethod
   public void setWrapping(boolean wrap) {
      this.wrap = wrap;
   }

   /**
    * Check if multi-line text exceeds height, should clipped lines be truncated.
    */
   @TernMethod
   public boolean isTruncateLines() {
      return truncateLines;
   }

   /**
    * Set whether multi-line text exceeds height, should clipped lines be truncated.
    */
   @TernMethod
   public void setTruncateLines(boolean truncateLines) {
      this.truncateLines = truncateLines;
   }

   /**
    * Check if to keep only one line or all lines in case of truncating multi-line text.
    */
   @TernMethod
   public boolean isLastOrAll() {
      return lastOrAll;
   }

   /**
    * Set in the case a line is truncated from a multi-line text, whether to keep only
    * the last line or as many as can fit.
    */
   @TernMethod
   public void setLastOrAll(boolean lastOrAll) {
      this.lastOrAll = lastOrAll;
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      return "VLabel" + System.identityHashCode(this) + "[" + label + "]";
   }

   /**
    * Check whether label could be a LabelForm.
    */
   @TernMethod
   public boolean isLabelForm() {
      return labelForm;
   }

   /**
    * Set whether label could be a LabelForm.
    */
   @TernMethod
   public void setLabelForm(boolean flag) {
      this.labelForm = flag;
   }

   /**
    * Check if the label should clip the width.
    * @hidden
    */
   public boolean isClipWidth() {
      return clipWidth;
   }

   /**
    * Set if the label should clip the width.
    * @hidden
    */
   public void setClipWidth(boolean flag) {
      this.clipWidth = flag;
   }

   /**
    * Check if the label should clip the height.
    * @hidden
    */
   public boolean isClipHeight() {
      return clipHeight;
   }

   /**
    * Set if the label should clip the height.
    * @hidden
    */
   public void setClipHeight(boolean flag) {
      this.clipHeight = flag;
   }

   /**
    * Check if this label can be removed by text layout.
    */
   @TernMethod
   public boolean isRemovable() {
      return true;
   }

   /**
    * Transform the insets given the rotation
    */
   public static Insets getTransformedInsets(Insets insets, double rotation) {
      if(rotation == 0) {
         return insets;
      }

      double angle = Math.toRadians(-rotation);
      AffineTransform trans = AffineTransform.getRotateInstance(angle, 0, 0);

      double left = 0;
      double right = 0;
      double top = 0;
      double bottom = 0;

      Point2D[] points = { new Point2D.Double(0, insets.top), new Point2D.Double(-insets.left, 0),
                           new Point2D.Double(0, -insets.bottom), new Point2D.Double(insets.right, 0) };
      Point2D[] dest = new Point2D[4];
      trans.transform(points, 0, dest, 0, dest.length);

      for(Point2D point : dest) {
         left += point.getX() < 0 ? Math.abs(point.getX()) : 0;
         right += point.getX() > 0 ? point.getX() : 0;
         top += point.getY() > 0 ? point.getY() : 0;
         bottom += point.getY() < 0 ? Math.abs(point.getY()) : 0;
      }

      return new Insets((int) top, (int) left, (int) bottom, (int) right);
   }

   private static final ThreadLocal<Format> DATE = new ThreadLocal() {
      @Override
      protected Format initialValue() {
         return new SimpleDateFormat("yyyy-MM-dd");
      }
   };
   private static final ThreadLocal<Format> DATE_TIME = new ThreadLocal() {
      @Override
      protected Format initialValue() {
         return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
      }
   };
   private static final ThreadLocal<Format> TIME = new ThreadLocal() {
      @Override
      protected Format initialValue() {
         return new SimpleDateFormat("HH:mm:ss");
      }
   };
   private static final ThreadLocal<Format> DECIMAL = new ThreadLocal() {
      @Override
      protected Format initialValue() {
         return new ExtendedDecimalFormat("0.####");
      }
   };

   private Object label;
   private TextSpec spec = new TextSpec();
   private Dimension2D maxSize = null;
   private int alignx = GraphConstants.CENTER_ALIGNMENT;
   private int aligny = GraphConstants.MIDDLE_ALIGNMENT;
   private Point2D offset = new Point2D.Double(0, 0);
   private Insets insets = new Insets(0, 0, 0, 0);
   private int modifier = MOVE_NONE;
   private boolean truncate = true;
   private boolean wrap = false;
   private boolean truncateLines = false;
   private boolean lastOrAll = false;
   private boolean labelForm = false;
   private boolean clipWidth = false;
   private boolean clipHeight = false;
   private Font font = null;
   private Color autoBG;

   private transient String[] dlabels0; // cached display label (lines)
   private transient Rectangle2D dbounds0; // bounds of cached label
   private transient String dlabel; // cached label (getText)
   private transient boolean truncated;
   private static final Logger LOG = LoggerFactory.getLogger(VLabel.class);
}
