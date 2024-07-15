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
package inetsoft.report.gui.viewsheet;

import inetsoft.report.StyleConstants;
import inetsoft.report.StyleFont;
import inetsoft.report.internal.Common;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.report.io.viewsheet.ExportUtil;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;

import java.awt.*;
import java.text.Format;

/**
 * VSObject component for view sheet.
 *
 * @version 8.5, 05/29/2006
 * @author InetSoft Technology Corp
 */
public abstract class VSObject {
   /**
    * Constructor.
    */
   public VSObject(Viewsheet vs) {
      super();

      setViewsheet(vs);
   }

   /**
    * Get the viewsheet.
    */
   public Viewsheet getViewsheet() {
      return vs;
   }

   /**
    * Set the viewsheet.
    */
   public void setViewsheet(Viewsheet vs) {
      this.vs = vs;
   }

   /**
    * Set the Info.
    */
   public void setAssemblyInfo(VSAssemblyInfo info) {
      this.info = info;
   }

   /**
    * Get the Info.
    */
   public VSAssemblyInfo getAssemblyInfo() {
      return info;
   }

   /**
    * Get the vsobject position.
    */
   public Point getPixelPosition() {
      return vs.getPixelPosition(info);
   }

   /**
    * Set the pixel size.
    */
   public void setPixelSize(Dimension psize) {
      this.psize = psize;
   }

   /**
    * Get pixel size.
    */
   public Dimension getPixelSize() {
      if(psize != null) {
         return psize;
      }

      psize = vs.getPixelSize(info);
      return psize;
   }

   /**
    * Set data tip view.
    */
   public void setDataTipView(Boolean dataTip) {
      this.dataTip = dataTip;
   }

   /**
    * Check if is tip view.
    */
   public boolean isDataTipView() {
      return dataTip;
   }

   /**
    * Get content x.
    * @return the content x position.
    */
   public int getContentX() {
      return 0;
   }

   /**
    * Get content y.
    * @return the content y position.
    */
   public int getContentY() {
      return 0;
   }

   /**
    * Get content width.
    * @return the content width.
    */
   public int getContentWidth() {
      return getPixelSize().width;
   }

   /**
    * Get content height.
    * @return the content height.
    */
   public int getContentHeight() {
      return getPixelSize().height;
   }

   /**
    * Get the row height of the grid.
    */
   public int getRowHeight(int r) {
      // Deprecated exporter logic, needs updating
      // @damianwysocki, Bug #9543
      // Removed grid, so always use default value for now
      // This method is used when creating an image for export
      return AssetUtil.defh;
   }

   /**
    * Get the column width of the grid.
    */
   public int getColWidth(int c) {
      // Deprecated exporter logic, needs updating
      // @damianwysocki, Bug #9543
      // Removed grid, so always use default value for now
      // This method is used when creating an image for export
      return AssetUtil.defw;
   }

   /**
    * Get the flex theme.
    */
   public FlexTheme getTheme() {
      return theme;
   }

   /**
    * Set the flex theme.
    */
   public void setTheme(FlexTheme theme) {
      this.theme = theme;
   }

   /**
    * Reduce the font size by one.
    */
   protected Font reduceSize(Font font) {
      // @by arrowz to avoid losing the underline and strikeline info
      // and to fix bug1166181609750.
      if(font instanceof StyleFont) {
         return new StyleFont(font.getName(), font.getStyle(),
            font.getSize() - 1, ((StyleFont) font).getUnderlineStyle(),
            ((StyleFont) font).getStrikelineStyle());
      }

      return new StyleFont(font.getName(), font.getStyle(), font.getSize() - 1);
   }

   /**
    * Format a value for display.
    */
   protected String format(Object val, VSCompositeFormat fmt) {
      if(fmt != null && fmt.getFormat() != null) {
         Format fobj = TableFormat.getFormat(fmt.getFormat(),
                                             fmt.getFormatExtent());

         if(fobj != null) {
            return fobj.format(val);
         }
      }

      return (val == null) ? "" : val + "";
   }

   /**
    * Draw a string using the format alignment, font, and color.
    */
   protected void drawString(Graphics g, int x, int y, int w, int h,
                             String label, VSCompositeFormat format)
   {
      Graphics g2 = g.create(x, y, w, h);

      if(format != null && format.getFont() != null) {
         g2.setFont(format.getFont());
      }

      if(format != null && format.getForeground() != null) {
         g2.setColor(format.getForeground());
      }
      else {
         g2.setColor(Color.black);
      }

      FontMetrics fm = g2.getFontMetrics();
      int labelX = 3;
      float labelY = 1;

      if(format != null) {
         if((format.getAlignment() & StyleConstants.H_CENTER) != 0) {
            labelX = (w - fm.stringWidth(label)) / 2;
         }
         else if((format.getAlignment() & StyleConstants.H_RIGHT) != 0) {
            labelX = w - fm.stringWidth(label);
         }

         if((format.getAlignment() & StyleConstants.V_CENTER) != 0) {
            labelY = (h - fm.getHeight()) / 2f + fm.getAscent();
         }
         else if((format.getAlignment() & StyleConstants.V_BOTTOM) != 0) {
            labelY = h - fm.getDescent();
         }
         else {
            labelY = fm.getAscent();
         }
      }

      Common.drawString(g2, label, labelX, labelY);
      g2.dispose();
   }

   /**
    * Set the scale the image dpi is changed.
    */
   public void setDpiScale(double dpiScale) {
      this.dpiScale = dpiScale;
   }

   /**
    * Draw Borders.
    */
   protected void drawBorders(Graphics g) {
      VSAssemblyInfo vinfo = getAssemblyInfo();

      if(vinfo != null) {
         VSCompositeFormat format = vinfo.getFormat();
         Point pos = new Point(Math.max(0, getContentX()),
                               Math.max(0, getContentY()));
         int w = getContentWidth();
         int h = getContentHeight();
         Dimension size = new Dimension((int) (w * dpiScale),
                                        (int) (h * dpiScale));
         drawBorders(g, format, pos, size);
      }
   }

   /**
    * Draw Borders.
    */
   protected void drawBorders(Graphics g, VSCompositeFormat format,
                              Point pos, Dimension size)
   {
      if(format == null) {
         return;
      }

      Insets borders = format.getBorders();
      BorderColors borderColors = format.getBorderColors();

      if(borders == null) {
         return;
      }

      // set the default border color
      if(borderColors == null) {
         borderColors = new BorderColors(
            VSAssemblyInfo.DEFAULT_BORDER_COLOR,
            VSAssemblyInfo.DEFAULT_BORDER_COLOR,
            VSAssemblyInfo.DEFAULT_BORDER_COLOR,
            VSAssemblyInfo.DEFAULT_BORDER_COLOR);
      }

      float leftBorderWidth = Common.getLineWidth(borders.left);
      float rightBorderWidth = Common.getLineWidth(borders.right);
      float topBorderWidth = Common.getLineWidth(borders.top);
      float bottomBorderWidth = Common.getLineWidth(borders.bottom);
      int fixedLeftDot = leftBorderWidth == 3 ? 1 : 0;
      int fixedRightDot = rightBorderWidth == 3 ? 1 : 0;
      int fixedTopDot = topBorderWidth == 3 ? 1 : 0;
      int fixedBottomDot = bottomBorderWidth == 3 ? 1 : 0;

      // it would be set clip as large as the bounds when export to PDF,
      // so adjust the position and size to show all the borders
      int x = pos.x + fixedLeftDot;
      int y = pos.y + fixedTopDot;
      float width =
         size.width - fixedLeftDot - rightBorderWidth + fixedRightDot;
      float height =
         size.height - fixedTopDot - bottomBorderWidth + fixedBottomDot;
      ExportUtil.drawBorders(g, new Point(x, y),
                             new Dimension((int) width, (int) height), borderColors,
                             borders, format.getRoundCorner());
   }

   /**
    * Get the borders line width for specified position, TOP/LEFT/BOTTOM/RIGHT.
    */
   protected float getBW(int pos) {
      VSAssemblyInfo vinfo = getAssemblyInfo();
      VSCompositeFormat format = vinfo == null ? null : vinfo.getFormat();
      Insets borders = format == null ? null : format.getBorders();

      if(borders == null) {
         return 0;
      }

      float lw = 0;

      switch(pos) {
      case TOP:
         lw = Common.getLineWidth(borders.top);
         break;
      case LEFT:
         lw = Common.getLineWidth(borders.left);
         break;
      case BOTTOM:
         lw = Common.getLineWidth(borders.bottom);
         break;
      case RIGHT:
         lw = Common.getLineWidth(borders.right);
         break;
      }

      // see Common.drawBorders
      return lw == 3 ? lw - 1 : lw;
   }

   protected static final int TOP = 0;
   protected static final int LEFT = 1;
   protected static final int BOTTOM = 2;
   protected static final int RIGHT = 3;
   private double dpiScale = 1;
   private VSAssemblyInfo info; // assembly info
   private Viewsheet vs; // viewsheet context
   private Dimension psize; // pixel size
   private FlexTheme theme;
   private Boolean dataTip = false;
}
