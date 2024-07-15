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
package inetsoft.report.gui.viewsheet;

import inetsoft.report.*;
import inetsoft.report.internal.*;
import inetsoft.report.io.viewsheet.ExportUtil;
import inetsoft.report.io.viewsheet.VSFontHelper;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * VSCompound component for view sheet.
 *
 * @version 8.5, 07/26/2006
 * @author InetSoft Technology Corp
 */
public abstract class VSCompound extends VSObject {
   /**
    * Constructor.
    */
   public VSCompound(Viewsheet vs) {
      super(vs);
   }

   /**
    * Create specific JToggleButton.
    */
   protected abstract JComponent createComponent(int index);

   /**
    * Get the image for exporting.
    * @return the image for exporting.
    */
   public Image getImage() {
      Dimension size = getPixelSize();
      BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
      Graphics2DWrapper g = new Graphics2DWrapper((Graphics2D) image.getGraphics(), false);
      paint(g);
      g.dispose();

      return image;
   }

   /**
    * Paint the components in compound component.
    */
   public void paint(Graphics2D g) {
      VSAssemblyInfo info = getAssemblyInfo();

      if(info == null) {
         return;
      }

      FormatInfo formatInfo = info.getFormatInfo();
      TableDataPath datapath = getTableDataPath();
      VSCompositeFormat objfmt = info.getFormat();
      VSCompositeFormat format = (formatInfo != null) ? formatInfo.getFormat(datapath) : null;

      if(objfmt != null && objfmt.getBackground() != null) {
         g.setColor(objfmt.getBackground());
         g.fillRect(getContentX(), getContentY(), getContentWidth(), getContentHeight());
      }

      int x0 = getContentX() + BORDER_GAP;
      int startX = x0;

      if(format != null && format.getFont() != null) {
         g.setFont(format.getFont());
      }

      if(format != null && format.getForeground() != null) {
         g.setColor(format.getForeground());
      }

      FontMetrics metrics = g.getFontMetrics();
      boolean tvisible = ((CompoundVSAssemblyInfo) info).isTitleVisible();
      String title = tvisible ? ((CompoundVSAssemblyInfo) info).getTitle() : null;
      int startY = ((title == null) ? 0 :
         ((CompoundVSAssemblyInfo) info).getTitleHeight()) + BORDER_GAP;
      int totalHeight = info.getPixelSize().height;
      int ncol = getColumnCount();
      int rowHeight = (title == null) ? 0 : ((CompoundVSAssemblyInfo) info).getTitleHeight();
      int gw = info.getPixelSize().width;

      int componentNum = ((ListInputVSAssemblyInfo) info).getValues().length;
      JComponent[] components = new JComponent[componentNum];
      boolean needComponents = rowHeight < totalHeight;
      int rowH = ((ListInputVSAssemblyInfo) info).getCellHeight();

      // create components
      for(int i = 0, col = 0; i < componentNum && needComponents; i++) {
         JComponent com = createComponent(i);
         com.setLocation(new Point(startX, startY));
         components[i] = com;
         Dimension size = new Dimension(gw / ncol, rowH);
         String[] labels = ((ListInputVSAssemblyInfo) info).getLabels();
         String dispLabel = "";

         if(labels != null && labels.length > i) {
            dispLabel = (labels[i] == null) ? "" : labels[i];
         }

         int strW = metrics.stringWidth(dispLabel);
         dispLabel = Tool.localize(shrinkLabel(dispLabel, strW, size.width -
                                   metrics.getHeight() - LEAF_OFFSIDE));
         com.setSize(size);
         com.setFont(g.getFont());
         JLabel label = ((JLabel) com);

         if(g.getFont() instanceof StyleFont) {
            StyleFont font = (StyleFont) g.getFont();
            boolean u = (font.getStyle() & StyleFont.UNDERLINE) != 0 &&
               font.getUnderlineStyle() != StyleConstants.NONE;
            boolean d = (font.getStyle() & StyleFont.STRIKETHROUGH) != 0 &&
               font.getStrikelineStyle() != StyleConstants.NONE;

            if(u || d) {
               dispLabel = "<html>" + (u ? "<u>" : "") + (d ? "<strike>" : "") +
                  dispLabel + (d ? "</strike>" : "") + (u ? "</u>" : "") + "</html>";
            }
         }

         label.setText(dispLabel);
         Graphics g2 = g.create();
         // see bug1249554532182
         VSCompositeFormat cellFormat = ((ListInputVSAssemblyInfo) info).getFormats()[0];

         if(((ListInputVSAssemblyInfo) info).getFormats().length > i) {
            cellFormat = ((ListInputVSAssemblyInfo) info).getFormats()[i];
         }

         if(cellFormat.getForeground() != null) {
            label.setForeground(cellFormat.getForeground());
         }

         g2.translate(startX, startY);
         drawBackground(g2, cellFormat, size);
         g2.translate(-BORDER_GAP, 0);
         drawBorders(g2, cellFormat, new Point(0, 0), size);
         g2.dispose();

         startX += gw / ncol;
         col++;

         if(col >= ncol) {
            startX = x0;
            col = 0;
            startY += rowH;
         }
      }

      if(needComponents) {
         if(ncol != 0) {
            layoutComponents(components, format, (gw / ncol));
         }

         // paint components
         for(int i = 0; i < components.length; i++) {
            Point loc = components[i].getLocation();
            Graphics g2 = g.create();
            g2.translate(loc.x, loc.y);
            components[i].paint(g2);
            g2.dispose();
         }
      }

      drawBorders(g);
      paintTitleBorder(g, needDefaultBorders(info),
                       getTopBorderWidth(info.getFormat()));
   }

   /**
    * Get the top border's width.
    */
   private float getTopBorderWidth(VSCompositeFormat format) {
      if(format != null && format.getBorders() != null) {
         return Common.getLineWidth(format.getBorders().top);
      }

      return 0;
   }

   /**
    * Check if the default (line across title cell) borders need to be drawn.
    * Don't draw default border if borders are defined on the title cell
    * or the vsobject.
    */
   private boolean needDefaultBorders(VSAssemblyInfo info) {
      TableDataPath datapath = new TableDataPath(-1, TableDataPath.TITLE);
      VSCompositeFormat format =
         info.getFormatInfo().getFormat(datapath, false);

      return format.getBorders() == null ||
             format.getBorderColors() == null;
   }

   /**
    * Layout according to alignment.
    */
   private void layoutComponents(JComponent[] components,
      VSCompositeFormat vsfmt, int cellw)
   {
      if(vsfmt == null || vsfmt.getAlignment() == StyleConstants.H_LEFT) {
         return;
      }

      // layout columns
      for(int i = 0; i < components.length; i++) {
         int pwidth = components[i].getPreferredSize().width + 3;
         int shift = 0;

         if((vsfmt.getAlignment() & StyleConstants.H_CENTER) != 0) {
            shift = (cellw - pwidth) / 2;
         }
         else if((vsfmt.getAlignment() & StyleConstants.H_RIGHT) != 0) {
            shift = cellw - pwidth;
         }

         if(shift > 0) {
            Point loc = components[i].getLocation();
            components[i].setLocation(loc.x + shift, loc.y);
         }
      }

      // make sure the component's left edge line up
      int cnt = getColumnCount();

      for(int i = 0; i < cnt; i++) {
         int x = Integer.MAX_VALUE;

         for(int k = i; k < components.length; k += cnt) {
            x = Math.min(x, components[k].getLocation().x);
         }

         for(int k = i; k < components.length; k += cnt) {
            Point loc = components[k].getLocation();
            components[k].setLocation(x, loc.y);
         }
      }
   }

   /**
    * Get the number of columns (of components).
    */
   private int getColumnCount() {
      VSAssemblyInfo info = getAssemblyInfo();
      boolean tvisible = ((CompoundVSAssemblyInfo) info).isTitleVisible();
      double componentNum = ((ListInputVSAssemblyInfo) info).getValues().length;
      int rowH = ((ListInputVSAssemblyInfo) info).getCellHeight();
      double rowcount = (int) Math.floor((getContentHeight() -
         (tvisible ? getRowHeight(0) : 0.0)) / rowH);
      rowcount = rowcount == 0 ? 1.0 : rowcount;
      int columnCount = (int) Math.ceil(componentNum / rowcount);

      return Math.max(1, columnCount);
   }

   /**
    * Paint title border.
    */
   private void paintTitleBorder(Graphics2D g, boolean needDefaultBorder,
                                 float topBorderWidth)
   {
      VSAssemblyInfo info = getAssemblyInfo();
      String title = ((CompoundVSAssemblyInfo) info).isTitleVisible() ?
         Tool.localize(((CompoundVSAssemblyInfo) info).getTitle()) : null;
      int tHeight = ((CompoundVSAssemblyInfo) info).getTitleHeight();
      VSCompositeFormat titleFormat = new VSCompositeFormat();
      FormatInfo finfo = info.getFormatInfo();
      Dimension titleSize = new Dimension(getContentWidth(), tHeight);
      Graphics2D g2 = (Graphics2D) g.create();

      if(finfo != null) {
         titleFormat = finfo.getFormat(
            new TableDataPath(-1, TableDataPath.TITLE), false);
      }

      int startX = getContentX() + BORDER_GAP;
      int startY = getContentY() + BORDER_GAP;
      int endX = getContentX() + getContentWidth() - BORDER_GAP;
      int endY = getContentY() + getContentHeight() - BORDER_GAP;

      // set the default Color to avoid if the assemblt is empty can export.
      g2.setColor(new Color(128, 128, 128));
      VSCompositeFormat objectFormat = info.getFormat();

      if(title == null && objectFormat.getBorders() == null && needDefaultBorder) {
         g2.drawLine(startX, startY, endX, startY);
         g2.drawLine(startX, startY, startX, endY);
         g2.drawLine(startX, endY, endX, endY);
         g2.drawLine(endX, startY, endX, endY);
      }
      else if(title != null) {
         Color color = g2.getColor();
         g2.translate(BORDER_GAP, BORDER_GAP);
         drawBackground(g, titleFormat, titleSize);
         drawBorders(g, titleFormat, new Point(0, 0),
                     new Dimension(getContentWidth(), tHeight));
         g2.translate(-BORDER_GAP, -BORDER_GAP);
         g2.setColor(color);
         Point titlePoint = calulateTitle(g, titleFormat, title, titleSize);

         if(titleFormat != null) {
            g2.setColor(titleFormat.getForeground() == null ? Color.BLACK :
               titleFormat.getForeground());
            g2.setFont(titleFormat.getFont());
            topBorderWidth = Math.max(topBorderWidth, getTopBorderWidth(titleFormat));
         }

         int titleWidth = g2.getFontMetrics().stringWidth(title);
         float leftGap = TITLE_GAP;
         float topGap = BORDER_GAP + topBorderWidth;

         if((this instanceof VSRadioButton || this instanceof VSCheckBox)) {
            topGap = topBorderWidth;

            if((titleFormat.getAlignment() & Common.H_LEFT) != 0) {
               leftGap = RADIO_TITLE_GAP;
            }
         }

         Bounds titleBounds = new Bounds(leftGap, topGap, titleSize.width - leftGap,
                                         titleSize.height);
         Insets padding = ((CompoundVSAssemblyInfo) info).getTitlePadding();

         if(padding != null) {
            titleBounds.x += padding.left;
            titleBounds.y += padding.top;
            titleBounds.width -= padding.left + padding.right;
            titleBounds.height -= padding.top + padding.bottom;
         }

         Common.paintText(g2, title, titleBounds, titleFormat.getAlignment(),
            titleFormat.isWrapping(), false, 0);
         // set the default Color to replace the titleformat's color.
         g2.setColor(new Color(128, 128, 128));

         if(needDefaultBorder) {
            int titleHeight = g2.getFontMetrics().getHeight();
            startY += titlePoint.y - titleHeight / 2 +
               g2.getFontMetrics().getDescent();
            g2.drawLine(startX, startY, titlePoint.x, startY);
            g2.drawLine(Math.min(startX + titlePoint.x + TITLE_GAP +
               titleWidth, endX), startY, endX, startY);
            g2.drawLine(startX, startY, startX, endY);
            g2.drawLine(startX, endY, endX, endY);
            g2.drawLine(endX, startY, endX, endY);
         }
      }

      g2.dispose();
   }

   /**
    * Get the cell size of the component.
    */
   private Point calulateTitle(Graphics g, VSCompositeFormat titleFormat,
                               String title, Dimension size) {
      FontMetrics metrics =
         Common.getFontMetrics(titleFormat.getFont() == null ?
            VSFontHelper.getDefaultFont() : titleFormat.getFont());
      int align = titleFormat == null ?
         StyleConstants.H_LEFT & StyleConstants.V_CENTER :
         titleFormat.getAlignment();
      Point titlePoint = new Point(0, 0);
      int titleHeight = metrics.getHeight();
      int titleWidth = metrics.stringWidth(title) + 2 * TITLE_GAP;

      int alignH = align & 0x7;
      int alignV = align & 0x78;

      switch(alignH) {
      case StyleConstants.H_RIGHT:
         titlePoint.x = size.width - titleWidth - TITLE_LINE;
         break;
      case StyleConstants.H_CENTER:
         titlePoint.x = (size.width - titleWidth) / 2;
         break;
      default:
         titlePoint.x = TITLE_LINE;
      }

      switch(alignV) {
      case StyleConstants.V_CENTER:
         titlePoint.y = size.height / 2;
         break;
      case StyleConstants.V_BOTTOM:
         titlePoint.y = size.height - titleHeight / 2;
         break;
      case StyleConstants.V_TOP:
         titlePoint.y = TITLE_V_GAP + titleHeight / 2;
         break;
      default:
         titlePoint.y = TITLE_V_GAP + titleHeight / 2;
      }

      titlePoint.x = titlePoint.x < 0 ? 0 : titlePoint.x;
      titlePoint.y = titlePoint.y < 0 ? 0 : titlePoint.y;

      return titlePoint;
   }

   /**
    * Draw Background.
    */
   protected void drawBackground(Graphics g, VSCompositeFormat format,
      Dimension size)
   {
      if(format != null) {
         Color backgroundColor = format.getBackground();
         ExportUtil.drawBackground(g, new Point(0, 0), size, backgroundColor,
                                   format.getRoundCorner());
      }
   }

   /**
    * Get the cell data path.
    */
   private TableDataPath getTableDataPath() {
      return new TableDataPath(-1, TableDataPath.DETAIL);
   }

   /**
    * If the length of the label is more than the specified
    * threshold, then truncate it.
    *
    * @param label the label
    * @param labelWidth the amount of space reqd for the label to be painted.
    * @param threshold the maximum length the label can have.
    * @return the truncated label.
    */
   protected String shrinkLabel(String label, int labelWidth, int threshold) {
      if(labelWidth > threshold) {
         double shrinkRatio = threshold / (double) labelWidth;
         int strlen = Math.round(label.length() * (float) shrinkRatio);

         if(strlen >= 0) {
            label = label.substring(0, strlen).trim();
         }
      }

      return label;
   }

   private int getTitleRowHeight() {
      return getRowHeight(0) - 2;
   }

   private int getDataRowHeight() {
      return getRowHeight(0) - 3;
   }

   private static int TITLE_GAP = 4;
   private static int RADIO_TITLE_GAP = 15;
   private static int TITLE_V_GAP = 2;
   private static int LEAF_OFFSIDE = 14; // hight=width for graph icon
   private static int BORDER_GAP = 1;
   private static int TITLE_LINE = 12; // default line length
}
