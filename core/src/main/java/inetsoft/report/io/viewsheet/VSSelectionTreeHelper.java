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

import inetsoft.report.*;
import inetsoft.report.io.viewsheet.excel.ExcelVSUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.SelectionTreeVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.util.Tool;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * SelectionTree helper for general purpose.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class VSSelectionTreeHelper extends VSSelectionListHelper {
   /**
    * Write the Selection tree assembly.
    */
   public void write(SelectionTreeVSAssembly assembly) {
      SelectionTreeVSAssemblyInfo info =
         (SelectionTreeVSAssemblyInfo) assembly.getVSAssemblyInfo();

      if(info == null) {
         return;
      }

      boundsList = cHelper.prepareBounds(assembly, totalHeight, 1);
      writeObjectBackground(info);
      StringBuilder sTitle = new StringBuilder();
      List<SelectionValue> dispList = new ArrayList<>();
      prepareDisplayList(info, dispList, sTitle, false);

      // should be writeTitle after writeTree to control ShowType()
      if(dispList.size() > 0) {
         writeTree(info, dispList);
      }

      if(info.isTitleVisible()) {
         writeTitle(info); // must be called after prepare
      }
   }

   /**
    * Prepare the list of displayed items for tree.
    * @param info the specified info.
    * @param dispList the list for items to be displayed.
    * @param sTitle the string buffer for the title.
    */
   protected void prepareDisplayList(SelectionTreeVSAssemblyInfo info,
                                     List<SelectionValue> dispList, StringBuilder sTitle,
                                     boolean displayAll)
   {
      STR_MORE = catalog.getString("More") + "...";
      MORE_VALUE = new SelectionValue(STR_MORE, STR_MORE);
      MORE_VALUE.setState(SelectionValue.STATE_SELECTED);
      MORE_VALUE.setLevel(0);

      CompositeSelectionValue csv = info.getCompositeSelectionValue();
      info.visitCompositeChild(csv, dispList, true);  // populate dispList

      for(SelectionValue sv : dispList) {
         if(info.isValueVisible(sv)) {
            sTitle.append(", ").append(sv.getLabel());
         }
      }

      // size include the title, and dispList include csv self, so
      // here makes them same, both include the first data
      int count = boundsList == null ?
         1 + (int) Math.round(((double) info.getPixelSize().height - info.getTitleHeight()) /
               info.getCellHeight()) :
         boundsList.size();

      if(boundsList != null) {
         int total = 0;

         for(int i = 0; i < boundsList.size(); i++) {
            Object obj = boundsList.get(i);
            total += ((Rectangle2D) obj).getHeight();

            if(total > totalHeight[0]) {
               count = i;
               break;
            }
         }
      }

      if(dispList.size() > count && !displayAll) {
         // do not use the bounds which just out of selection tree
         // to display STR_MORE
         for(int i = dispList.size() - 1; i >= count - 1 && i >= 0; i--) {
            dispList.remove(i);
         }

         if(count > 0) {
            dispList.add(MORE_VALUE);
         }
      }
   }

   /**
    * Write the object background.
    */
   protected void writeObjectBackground(SelectionTreeVSAssemblyInfo info) {
      Rectangle2D bounds = cHelper.getBounds(info);
      VSCompositeFormat format = info.getFormat();

      double height = 0;

      if(!info.isTitleVisible()) {
         Rectangle2D rec = (Rectangle2D) boundsList.get(0);
         height = rec.getHeight();
      }

      bounds.setFrame(bounds.getX(), bounds.getY() + height, bounds.getWidth(), totalHeight[0] - height);
      writeText(bounds, format, "", true);
   }

   /**
    * Write the tree content.
    * @param info the specified SelectionTreeVSAssemblyInfo.
    * @param dispList SelectionValues for display.
    */
   protected void writeTree0(SelectionTreeVSAssemblyInfo info, List<SelectionValue> dispList) {
      Dimension size = info.getPixelSize();
      SelectionValue sv;
      VSCompositeFormat lastLineFormat = null;
      int level;
      boolean hasSelected = info.getCompositeSelectionValue()
         .getSelectionValues(-1, SelectionValue.STATE_SELECTED, 0).size() > 0;
      int barTextSize = 0;

      if(info.isShowBar() && info.getMeasure() != null && info.getBarSize() > 0) {
         barTextSize += info.getBarSize();
      }

      if(info.isShowText() && info.getMeasure() != null && info.getMeasureSize() > 0) {
         barTextSize += info.getMeasureSize();
      }

      for(int i = 1; i < boundsList.size() && i < dispList.size(); i++) {
         String dispText;
         int cellType;
         Rectangle2D bounds = (Rectangle2D) boundsList.get(i);
         sv = dispList.get(i);
         level = sv.getLevel();
         StringBuilder sb = new StringBuilder();

         for(int k = 0; k < level; k++) {
            sb.append(INDENT_STR);
         }

         sb.append(sv.getLabel());
         dispText = sb.toString();
         VSCompositeFormat format = sv.getFormat();

         // set to gray if the parent itself is not selected
         format = VSSelectionListHelper.getValueFormat(sv, format, hasSelected);

         // using == should be more convience
         if(sv == MORE_VALUE) {
            format = lastLineFormat;

            if(format == null) {
               format = new VSCompositeFormat();
               Font tf = VSFontHelper.getDefaultFont();
               format.getDefaultFormat().setFont(new StyleFont(tf));
            }
         }

         // if the cell's background is null or the cell is null, use the
         // object's background
         if(format == null) {
            format = new VSCompositeFormat();
            format.getDefaultFormat().setBackground(info.getFormat().getBackground());
         }
         else if(format.getBackground() == null || i == dispList.size()) {
            format.getUserDefinedFormat().setBackground(info.getFormat().getBackground());
         }

         if(i == size.height - 1) {
            cellType = ExcelVSUtil.CELL_TAIL;
         }
         else if(i < dispList.size()) {
            cellType = ExcelVSUtil.CELL_HEADER;
         }
         else {
            cellType = ExcelVSUtil.CELL_CONTENT;
         }

         mergeFormat(format, lastLineFormat);
         lastLineFormat = format;
         Rectangle2D textBounds = bounds;

         if(barTextSize <= 0) {
            barTextSize = (int) (getDisplayBarWidth(info, bounds) +
               getDisplayMeasureTextWidth(info, bounds));
         }

         if(barTextSize > 0) {
            textBounds = new Rectangle2D.Double(bounds.getX(),
               bounds.getY(), bounds.getWidth() - barTextSize, bounds.getHeight());
         }

         writeText(bounds, textBounds, format, dispText, true, cellType, info.getFormat(),
                   info.getCellPadding());
         paintMeasure(sv, bounds, info.getCompositeSelectionValue().getSelectionList(), info);
      }
   }

   private double getDisplayBarWidth(SelectionTreeVSAssemblyInfo info, Rectangle2D bounds) {
      if(!info.isShowBar() || info.getMeasure() == null) {
         return 0;
      }

      double barsize = info.getBarSize();
      return barsize > 0 ? barsize : Math.ceil(bounds.getWidth() / 4);
   }

   private double getDisplayMeasureTextWidth(SelectionTreeVSAssemblyInfo info, Rectangle2D bounds) {
      if(!info.isShowText() || info.getMeasure() == null) {
         return 0;
      }

      double measureSize = info.getMeasureSize();
      double barsize = getDisplayBarWidth(info, bounds);
      double mtextratio = info.getMeasureTextRatio();

      return measureSize > 0 ? measureSize : Math.ceil((bounds.getWidth() - barsize) * mtextratio);
   }

   /**
    * Write the title.
    * @param info the specified SelectionTreeVSAssemblyInfo.
    */
   protected void writeTitle(SelectionTreeVSAssemblyInfo info) {
      FormatInfo finfo = info.getFormatInfo();
      VSCompositeFormat format = finfo.getFormat(new TableDataPath(-1, TableDataPath.TITLE), false);
      Rectangle2D bounds = (Rectangle2D) boundsList.get(0);
      writeText(bounds, format, Tool.localize(info.getTitle()), info.getFormat(),
                info.getTitlePadding());
   }

   /**
    * Draw the text box.
    *
    * @param bounds   the specified Bounds.
    * @param format   the specified VSFormat.
    * @param dispText the specified String.
    * @param ctype    cell type.
    * @param pfmt     assembly VSFormat.
    * @param padding
    */
   protected void writeText(Rectangle2D bounds, Rectangle2D textBounds, VSCompositeFormat format,
                            String dispText, boolean paintBackground,
                            int ctype, VSCompositeFormat pfmt, Insets padding)
   {
      writeText(bounds, textBounds, format, dispText, true, ctype, padding);
   }

   /**
    * Draw the text box.
    *
    * @param bounds   the specified Bounds.
    * @param fmt      the specified VSFormat.
    * @param dispText the specified String.
    * @param pfmt     assembly VSFormat.
    * @param padding
    */
   protected void writeText(Rectangle2D bounds, VSCompositeFormat fmt,
                            String dispText, VSCompositeFormat pfmt, Insets padding)
   {
      writeText(bounds, fmt, dispText, true, -1, padding);
   }

   /**
    * Draw the text box.
    * @param bounds the specified Bounds.
    * @param format the specified VSFormat.
    * @param dispText the specified String.
    */
   protected void writeText(Rectangle2D bounds, VSCompositeFormat format,
                            String dispText, boolean paintBackground) {
      writeText(bounds, format, dispText, paintBackground, -1, null);
   }

   /**
    * Draw the text box.
    *
    * @param bounds          the specified Bounds.
    * @param format          the specified VSFormat.
    * @param dispText        the specified String.
    * @param paintBackground specify used for PDF.
    * @param ctype           the cell value type, specify used for PPT,
    *                        is the value is -1, will not set for the cell.
    * @param padding
    */
   protected void writeText(Rectangle2D bounds, VSCompositeFormat format,
                            String dispText, boolean paintBackground,
                            int ctype, Insets padding)
   {
      writeText(bounds, bounds, format, dispText, paintBackground, ctype, padding);
   }

   /**
    * Draw the text box.
    *
    * @param bounds          the specified Bounds.
    * @param format          the specified VSFormat.
    * @param dispText        the specified String.
    * @param paintBackground specify used for PDF.
    * @param ctype           the cell value type, specify used for PPT,
    *                        is the value is -1, will not set for the cell.
    * @param padding
    */
   protected void writeText(Rectangle2D bounds, Rectangle2D textBounds, VSCompositeFormat format,
                            String dispText, boolean paintBackground,
                            int ctype, Insets padding)
   {
      // do nothing, will be implemented by pdf/ppt
   }

   /**
    * Write tree.
    */
   protected void writeTree(SelectionTreeVSAssemblyInfo info, List<SelectionValue> dispList) {
      // do nothing, will be implemented by pdf/ppt
   }

   /**
    * Merge format by the previous format.
    * @param cformat the current format to merge.
    * @param pformat the previous format used to merge.
    */
   private void mergeFormat(VSCompositeFormat cformat,
                            VSCompositeFormat pformat) {
      if(cformat == null || pformat == null) {
         return;
      }

      BorderColors cborderColors = cformat.getBorderColors();
      Insets cborders = cformat.getBorders();
      BorderColors pboderColors = pformat.getBorderColors();
      Insets pborders = pformat.getBorders();

      // not border, no bottom border
      if(pborders == null || pborders.bottom == StyleConstants.NO_BORDER) {
         return;
      }

      // if borders is not defined, or the top border is set to null,
      // or the borders color is not defined, or the top border color
      // is set to null, should merge the previous bottom border color
      // to current format top border color
      boolean mergeColor = false;

      // if not define borders or top border is none, use previous
      // format's bottom border
      if(cborders == null) {
         cborders = new Insets(pborders.bottom,
                               StyleConstants.NO_BORDER,
                               StyleConstants.NO_BORDER,
                               StyleConstants.NO_BORDER);
         cformat.getUserDefinedFormat().setBorders(cborders);
         mergeColor = true;
      }

      if(cborders.top == StyleConstants.NO_BORDER) {
         cborders.top = pborders.bottom;
         mergeColor = true;
      }

      Color pbtc = pboderColors == null ? VSAssemblyInfo.DEFAULT_BORDER_COLOR :
                                          pboderColors.bottomColor;

      if(cborderColors == null) {
         cborderColors = new BorderColors(pbtc,  null, null, null);
         cformat.getUserDefinedFormat().setBorderColors(cborderColors);
         mergeColor = true;
      }

      if(mergeColor || cborderColors.topColor == null) {
         cborderColors.topColor = pbtc;
      }
   }

   protected static final String INDENT_STR = "  ";
   protected String STR_MORE = null;
   protected SelectionValue MORE_VALUE;
}
