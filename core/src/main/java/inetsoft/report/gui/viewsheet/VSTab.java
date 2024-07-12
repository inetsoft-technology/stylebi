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
package inetsoft.report.gui.viewsheet;

import inetsoft.graph.GraphConstants;
import inetsoft.graph.internal.GTool;
import inetsoft.report.Size;
import inetsoft.report.StyleConstants;
import inetsoft.report.internal.Bounds;
import inetsoft.report.internal.Common;
import inetsoft.report.io.viewsheet.VSFontHelper;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TabVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * VSTab for view sheet exporting.
 *
 * @author InetSoft Technology Corp
 * @version 8.5, 11/13/2006
 */
public class VSTab extends VSFloatable {
   /**
    * Constructor.
    */
   public VSTab(Viewsheet vs) {
      super(vs);
   }

   /**
    * Get the margin of component.
    */
   public Insets getMargin() {
      return new Insets(0, 0, 0, 0);
   }

   /**
    * Paint the vstab.
    */
   @Override
   public void paintComponent(Graphics2D g) {
      VSAssemblyInfo info = getAssemblyInfo();

      if(info == null) {
         return;
      }

      Dimension size = getPixelSize();
      Graphics2D g2 = (Graphics2D) g.create();

      g2.clipRect(0, 0, size.width, size.height);

      VSCompositeFormat format = info.getFormat();
      VSCompositeFormat activeFormat = info.getFormatInfo()
         .getFormat(TabVSAssemblyInfo.ACTIVE_TAB_PATH, false);

      String sel = ((TabVSAssemblyInfo) info).getSelected();
      String[] assms = ((TabVSAssemblyInfo) info).getAssemblies();
      String[] labels = ((TabVSAssemblyInfo) info).getLabels();
      int x = 0;

      // ignore tab if only one visible (same as gui)
      if(assms.length > 1) {
         for(int i = 0; i < assms.length; i++) {
            boolean selected = assms[i].equals(sel);

            if(i < labels.length && labels[i] != null) {
               x += paintTab(g2, x, labels[i], selected);
            }
            else {
               x += paintTab(g2, x, assms[i], selected);
            }
         }

         if(format.getBorders() != null && (((TabVSAssemblyInfo) info).isRoundTopCornersOnly() ||
            (format.getRoundCorner() == 0 && activeFormat.getRoundCorner() == 0)))
         {
            // draw the bottom border here since we know where the tabs end
            g2.setColor(format.getBorderColors().bottomColor);
            int borderStyle = format.getBorders().bottom;
            float borderWidth = Common.getLineWidth(borderStyle);
            float y = size.height - borderWidth;
            // clip to the border width so that the line is the same height as the tab border
            g2.setClip(0, (int) y, size.width, (int) borderWidth);

            // dashed line seems to drawn off by 1px, adjust it here so that it's within clip bounds
            if((borderStyle & GraphConstants.DASH_MASK) != 0) {
               y += 1;
            }

            Common.drawHLine(g2, y, x, size.width, borderStyle, 0, 0);
         }
      }

      g2.dispose();
   }

   /**
    * Paint a tab and return the width of the tab.
    */
   private int paintTab(Graphics2D g, int x, String label, boolean selected) {
      TabVSAssemblyInfo info = (TabVSAssemblyInfo) getAssemblyInfo();
      VSCompositeFormat format = selected ?
         info.getFormatInfo().getFormat(TabVSAssemblyInfo.ACTIVE_TAB_PATH, false) :
         info.getFormat();

      if(format.getFont() != null) {
         g.setFont(format.getFont());
      }
      else {
         g.setFont(VSFontHelper.getDefaultFont());
      }

      FontMetrics fm = g.getFontMetrics();
      int labelW = fm.stringWidth(label);
      Dimension size = getPixelSize();
      int tabW = labelW + GAP * 2;
      int tabH = size.height;
      int tabY = size.height - tabH;
      Point tabPos = new Point(x, tabY);
      Dimension tabSize = new Dimension(tabW, tabH);

      drawTabShape(g, tabPos, tabSize, format);

      // draw the text
      int align = format.getAlignment();
      // always align to the center horizontally
      align = align | StyleConstants.H_CENTER;

      Insets borders1 = info.getFormat().getBorders();
      Insets borders2 = info.getFormatInfo().getFormat(TabVSAssemblyInfo.ACTIVE_TAB_PATH, false)
         .getBorders();
      float maxTopBorderWidth = 0;
      float maxBottomBorderWidth = 0;

      if(borders1 != null && borders2 != null) {
         maxTopBorderWidth = Math.max(Common.getLineWidth(borders1.top),
                                      Common.getLineWidth(borders2.top));
         maxBottomBorderWidth = Math.max(Common.getLineWidth(borders1.bottom),
                                         Common.getLineWidth(borders2.bottom));
      }

      Bounds bounds = Common.alignCell(
         new Bounds(tabPos.x, tabPos.y, tabSize.width,
                    tabSize.height - maxTopBorderWidth - maxBottomBorderWidth),
         new Size(labelW, fm.getHeight()), align);
      g.setColor(format.getForeground());
      Common.drawString(g, label, bounds.x, bounds.y + maxTopBorderWidth + fm.getAscent());

      return tabW;
   }

   private void drawTabShape(Graphics2D g, Point pos, Dimension size, VSCompositeFormat format) {
      Insets borders = format.getBorders();
      float leftBorderWidth = 0;
      float rightBorderWidth = 0;
      float topBorderWidth = 0;
      float bottomBorderWidth = 0;
      int borderRadius = format.getRoundCorner();

      if(borders != null) {
         leftBorderWidth = Common.getLineWidth(borders.left);
         rightBorderWidth = Common.getLineWidth(borders.right);
         topBorderWidth = Common.getLineWidth(borders.top);
         bottomBorderWidth = Common.getLineWidth(borders.bottom);
      }

      TabVSAssemblyInfo info = (TabVSAssemblyInfo) getAssemblyInfo();
      Shape shape = getTabShape(pos, size, format);

      g = (Graphics2D) g.create();

      // used to create a gap between tabs when no border is set
      int leftAdj = leftBorderWidth == 0 ? 1 : 0;
      int rightAdj = rightBorderWidth == 0 ? 1 : 0;

      // draw the background
      if(format.getBackground() != null) {
         g.setClip(pos.x + leftAdj, pos.y, size.width - leftAdj - rightAdj, size.height);
         g.setColor(format.getBackground());
         g.fill(shape);
      }

      BorderColors borderColors = format.getBorderColors();
      Rectangle clipBounds;

      // adjust the borders too if it's a round shape
      if(!info.isRoundTopCornersOnly() && borderRadius > 0) {
         clipBounds = new Rectangle(pos.x + leftAdj, pos.y, size.width - leftAdj - rightAdj,
                                    size.height);
      }
      else {
         clipBounds = new Rectangle(pos.x, pos.y, size.width, size.height);
      }

      int borderRadiusWidth = (int) Math.ceil(borderRadius / 2d);

      if(borders != null) {
         // left
         g.setClip(clipBounds.x, clipBounds.y, (int) (leftBorderWidth + borderRadiusWidth),
                   clipBounds.height);
         drawTabBorder(g, shape, borders.left, borderColors.leftColor);

         // right
         g.setClip((int) (clipBounds.getMaxX() - (rightBorderWidth + borderRadiusWidth)),
                   clipBounds.y, (int) (rightBorderWidth + borderRadiusWidth), clipBounds.height);
         drawTabBorder(g, shape, borders.right, borderColors.rightColor);

         // top
         g.setClip(clipBounds.x, clipBounds.y, clipBounds.width,
                   (int) (topBorderWidth + borderRadiusWidth));
         drawTabBorder(g, shape, borders.top, borderColors.topColor);

         // bottom
         borderRadiusWidth = info.isRoundTopCornersOnly() ? 0 : borderRadiusWidth;
         g.setClip(clipBounds.x, (int) (clipBounds.getMaxY() - (bottomBorderWidth + borderRadiusWidth)),
                   clipBounds.width, (int) (bottomBorderWidth + borderRadiusWidth));
         drawTabBorder(g, shape, borders.bottom, borderColors.bottomColor);
      }

      g.dispose();
   }

   private void drawTabBorder(Graphics2D g, Shape shape, int borderStyle, Color borderColor) {
      if(borderStyle == StyleConstants.NO_BORDER || borderColor == null) {
         return;
      }

      Stroke stroke = GTool.getStroke(borderStyle);
      Shape strokeShape = (borderStyle & GraphConstants.DASH_MASK) != 0 ?
         GTool.getStroke(GraphConstants.THIN_LINE).createStrokedShape(shape) :
         stroke.createStrokedShape(shape);

      // clip to the shape so it doesn't spill outside the shape bounds
      g.clip(shape);
      g.setStroke(stroke);
      g.setColor(borderColor);
      g.draw(strokeShape);
   }

   private Shape getTabShape(Point pos, Dimension size, VSCompositeFormat format) {
      TabVSAssemblyInfo info = (TabVSAssemblyInfo) getAssemblyInfo();

      float x = pos.x;
      float y = pos.y;
      float width = size.width;
      float height = size.height;
      int radius = format.getRoundCorner();

      if(info.isRoundTopCornersOnly()) {
         Path2D path = new Path2D.Float();
         path.moveTo(x + radius, y); // Top-left corner
         path.lineTo(x + width - radius, y); // Top-right corner
         path.quadTo(x + width, y, x + width, y + radius); // Top-right curve
         path.lineTo(x + width, y + height); // Bottom-right corner
         path.lineTo(x, y + height); // Bottom-left corner
         path.lineTo(x, y + radius); // Top-left corner
         path.quadTo(x, y, x + radius, y); // Top-left curve
         path.closePath();
         return path;
      }
      else {
         return new RoundRectangle2D.Float(x, y, width, height, radius * 2, radius * 2);
      }
   }

   @Override
   protected void drawBorders(Graphics g) {
      // do nothing here
   }

   @Override
   protected void drawBackground(Graphics g) {
      // each tab draws its own background so no need to do anything here
   }

   @Override
   protected Point getBorderGap() {
      return new Point(0, 0);
   }

   private static int GAP = 6;
}
