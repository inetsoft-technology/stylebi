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
package inetsoft.report.io.viewsheet;

import inetsoft.report.TableDataPath;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Catalog;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * SelectionList helper.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class VSSelectionListHelper extends ExporterHelper {
   /**
    * Write the selection list to powerpoint.
    * @param assembly get assembly info.
    */
   public void write(SelectionListVSAssembly assembly) {
      SelectionListVSAssemblyInfo info =
         (SelectionListVSAssemblyInfo) assembly.getVSAssemblyInfo();

      if(info == null) {
         return;
      }

      boolean incs =
         assembly.getContainer() instanceof CurrentSelectionVSAssembly;
      Point containerPos = null;

      if(incs) {
         containerPos = assembly.getContainer().getPixelOffset();
      }

      List<SelectionValue> values = getSelectedValues(info);
      initBoundsList(assembly, containerPos);

      VSCompositeFormat format = info.getFormat();
      writeObjectBackground(assembly, format);

      if(info.getShowType() == SelectionVSAssemblyInfo.LIST_SHOW_TYPE) {
         writeList(assembly, values);
      }

      if(!info.isTitleVisible() && (assembly.getContainer() == null || !(assembly.getContainer() instanceof CurrentSelectionVSAssembly))) {
         return;
      }

      if(incs) {
         FormatInfo finfo = info.getFormatInfo();

         if(finfo != null) {
            format = finfo.getFormat(
               new TableDataPath(-1, TableDataPath.TITLE), false);
         }

         CurrentSelectionVSAssemblyInfo cinfo = (CurrentSelectionVSAssemblyInfo)
            assembly.getContainer().getInfo();
         writeTitleInContainer(assembly, format,
            assembly.getDisplayValue(true, true), cinfo.getTitleRatio(),
            info.getTitlePadding());
      }
      else {
         writeTitle(info, values);
      }
   }

   /**
    * Init boundsList.
    */
   protected void initBoundsList(SelectionListVSAssembly assembly,
                                 Point containerPos)
   {
      SelectionListVSAssemblyInfo info =
         (SelectionListVSAssemblyInfo) assembly.getVSAssemblyInfo();
      boundsList = cHelper.prepareBounds(assembly, totalHeight,
                                         info.getColumnCount());
   }

   protected Rectangle2D getValueLabelBounds(SelectionListVSAssemblyInfo info,
                                             Rectangle2D cellBounds)
   {
      if(cellBounds == null) {
         return cellBounds;
      }

      int barTextSize = 0;
      double cellWidth = cellBounds.getWidth();
      double barSize = 0;

      if(info.isShowBar() && info.getMeasure() != null) {
         barSize = info.getBarSize();

         if(barSize <= 0) {
            barSize = Math.ceil(cellWidth / 4);
         }

         barTextSize += barSize;
      }

      if(info.isShowText() && info.getMeasure() != null) {
         double textWidth = info.getMeasureSize();

         if(textWidth < 0) {
            double measureRatio = info.getMeasureTextRatio();
            textWidth = Math.ceil((cellWidth - barSize) * measureRatio);
         }

         barTextSize += textWidth;
      }

      return new Rectangle2D.Double(cellBounds.getX(), cellBounds.getY(),
         cellBounds.getWidth() - barTextSize, cellBounds.getHeight());
   }

   /**
    * Write title which in container.
    */
   protected void writeTitleInContainer(SelectionListVSAssembly assembly,
                                        VSCompositeFormat format, String title, double titleRatio,
                                        Insets padding)
   {
   }

   /**
    * Write the object background.
    */
   protected void writeObjectBackground(SelectionListVSAssembly assembly,
                                        VSCompositeFormat format) {
   }

   /**
    * Get selected values.
    * @param info the specified assembly info.
    * @return the vector contains selected values.
    */
   protected List<SelectionValue> getSelectedValues(SelectionListVSAssemblyInfo info) {
      List<SelectionValue> values = new ArrayList<>();
      SelectionList slist = info.getSelectionList();

      if(slist != null) {
         SelectionValue[] list = slist.getSelectionValues();

         for(int i = 0; i < list.length; i++) {
            if(!list[i].isExcluded()) {
               values.add(list[i]);
            }
         }
      }

      return values;
   }

   /**
    * Write the title. There are two type of the title.
    */
   protected void writeTitle(SelectionListVSAssemblyInfo info, List<SelectionValue> values) {
   }

   /**
    * Write the list.
    */
   protected void writeList(SelectionListVSAssembly assembly, List<SelectionValue> values) {
   }

   /**
    * Paint the measure text and bar.
    */
   protected void paintMeasure(SelectionValue value, Rectangle2D bounds,
                               SelectionList slist,
                               SelectionBaseVSAssemblyInfo info)
   {
      if(value.getMeasureLabel() != null) {
         FormatInfo finfo = info.getFormatInfo();
         double mmin = slist.getMeasureMin();
         double mmax = slist.getMeasureMax();
         double barsize = info.isShowBar() ? info.getBarSize() : 0;
         barsize = barsize <= 0 && info.isShowBar() ? Math.ceil(bounds.getWidth() / 4) : barsize;
         double mtextratio = info.getMeasureTextRatio();

         if(info.isShowBar() && barsize >= 1) {
            Image img = paintBar(value, (int) barsize, (int) bounds.getHeight(), slist, info);
            Rectangle2D barbox = new Rectangle2D.Double(
               bounds.getX() + bounds.getWidth() - barsize, bounds.getY(),
               barsize, bounds.getHeight());
            writePicture(img, barbox);
         }

         if(info.isShowText()) {
            TableDataPath datapath =
               SelectionBaseVSAssemblyInfo.getMeasureTextPath(value.getLevel());
            VSCompositeFormat format = finfo.getFormat(datapath);

            double textsize = Math.ceil((bounds.getWidth() - barsize) * mtextratio);
            Rectangle2D textbox = new Rectangle2D.Double(
               bounds.getX() + bounds.getWidth() - barsize - textsize,
               bounds.getY(), textsize, bounds.getHeight());
            writeText(value.getMeasureLabel(), textbox, format);
         }
      }
   }

   /**
    * Paint the measure bar.
    */
   public static Image paintBar(SelectionValue sval, int bw, int bh,
                                SelectionList slist,
                                SelectionBaseVSAssemblyInfo info)
   {
      BufferedImage img = new BufferedImage(bw, bh, BufferedImage.TYPE_INT_ARGB);
      Graphics g = img.getGraphics();
      FormatInfo finfo = info.getFormatInfo();
      TableDataPath datapath = (sval.getMeasureValue() < 0)
         ? SelectionBaseVSAssemblyInfo.getMeasureNBarPath(sval.getLevel())
         : SelectionBaseVSAssemblyInfo.getMeasureBarPath(sval.getLevel());
      VSCompositeFormat format = finfo.getFormat(datapath);
      double mmin = slist.getMeasureMin();
      double mmax = slist.getMeasureMax();
      int barx = (mmin < 0) ?
         (int) (mmax > 0 ? bw * -mmin / (mmax - mmin) : bw) : 0;
      int barw = (mmin < 0) ? (mmax > 0 ? bw - barx : bw) : bw;
      double mval = sval.getMeasureValue();
      int w = (int) ((mval < 0 && barx != barw ? bw - barw : barw) * mval);
      int gap = 3;

      if(format != null && format.getBackground() != null) {
         g.setColor(format.getBackground());
         g.fillRect(0, 0, bw, bh);
      }

      g.setColor(format != null ? format.getForeground() : new Color(0x8888FF));

      if(w < 0) {
         g.fillRect(barx + w, gap, -w, bh - gap * 2);
      }
      else {
         g.fillRect(barx, gap, w, bh - gap * 2);
      }

      // draw baseline for negative
      if(mmin < 0 && mmax > 0) {
         g.setColor(new Color(0xCFCFCF));
         g.drawLine(barx, 0, barx, bh);
      }

      g.dispose();
      return img;
   }

   /**
    * Paint an image at the bounds.
    */
   protected void writePicture(Image img, Rectangle2D bounds) {
      // implemented by subclass
   }

   /**
    * Draw a text at the bounds.
    */
   protected void writeText(String txt, Rectangle2D bounds,
                            VSCompositeFormat format)
   {
      // implemented by subclass
   }

   /**
    * Get invisible title height.
    */
   protected double getInvisibleTitleHeight(SelectionListVSAssembly assembly) {
      if(!(assembly.getContainer() instanceof CurrentSelectionVSAssembly)) {
         return 0;
      }

      SelectionListVSAssemblyInfo info =
         (SelectionListVSAssemblyInfo) assembly.getVSAssemblyInfo();

      return info.isTitleVisible() ? 0 :
         cHelper.getBounds(assembly, CoordinateHelper.TITLE).getHeight();
   }

   /**
    * Apply any formatting to selection list/tree format according to
    * item status.
    * @param hasSelected true if some items on the list/tree are selected.
    */
   public static VSCompositeFormat getValueFormat(SelectionValue value,
                                                  VSCompositeFormat format,
                                                  boolean hasSelected)
   {
      if(value != null) {
         if(value.isExcluded() || !value.isSelected()) {
            format = (format == null)
               ? new VSCompositeFormat() :  (VSCompositeFormat) format.clone();

            if(value.isExcluded()) {
               format.getUserDefinedFormat().setForeground(new Color(0x888888));
            }
            else if(!value.isSelected() && hasSelected) {
               format.getUserDefinedFormat().setForeground(new Color(0x888888));
            }
         }
      }

      return format;
   }

   protected Catalog catalog = Catalog.getCatalog();
   protected List boundsList = null;
   protected int[] totalHeight = {0};
   protected CoordinateHelper cHelper = null;
}
