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
package inetsoft.report.io.viewsheet.html;

import inetsoft.report.TableDataPath;
import inetsoft.report.io.viewsheet.CoordinateHelper;
import inetsoft.report.io.viewsheet.VSSelectionListHelper;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.PrintWriter;

/**
 * Table helper used when exporting to HTML.
 */
public class HTMLSelectionListHelper extends VSSelectionListHelper{
   /**
    * Creates a new instance of <tt>HTMLSelectionListHelper</tt>.
    *
    * @param helper the coordinate helper.
    * @param vs     the viewsheet being exported.
    */
   public HTMLSelectionListHelper(HTMLCoordinateHelper helper, Viewsheet vs) {
      this.vs = vs;
      this.vHelper =  helper;
   }

   /**
    * Creates a new instance of <tt>HTMLTableHelper</tt>.
    *
    * @param helper   the coordinate helper.
    * @param vs       the viewsheet being exported.
    * @param assembly the assembly being written.
    */
   public HTMLSelectionListHelper(HTMLCoordinateHelper helper, Viewsheet vs, VSAssembly assembly) {
      this(helper, vs);
      this.assembly = assembly;
   }

   public void write(PrintWriter writer, SelectionListVSAssembly assembly) {
      SelectionListVSAssemblyInfo info = (SelectionListVSAssemblyInfo) assembly.getVSAssemblyInfo();

      if(info == null) {
         return;
      }

      Rectangle2D bounds = vHelper.getBounds(assembly, CoordinateHelper.ALL);
      VSCompositeFormat fmt = info.getFormat();
      StringBuffer slist = new StringBuffer("");
      int titleH = !((TitledVSAssemblyInfo) info).isTitleVisible() ? 0 :
         ((TitledVSAssemblyInfo) info).getTitleHeight();
      String containerTitle = null;
      double titleRatio = 0.5;

      if(assembly.getContainer() instanceof CurrentSelectionVSAssembly) {
         CurrentSelectionVSAssembly current = (CurrentSelectionVSAssembly)assembly.getContainer();
         CurrentSelectionVSAssemblyInfo cinfo =
            (CurrentSelectionVSAssemblyInfo) current.getVSAssemblyInfo();
         containerTitle = assembly.getDisplayValue(true, true);
         titleRatio = cinfo.getTitleRatio();

         if(!cinfo.isTitleVisible()) {
            int containerTitleH = cinfo.getTitleHeight();
            bounds.setRect(bounds.getX(), bounds.getY() - containerTitleH,
               bounds.getWidth(), bounds.getHeight());
         }
      }

      slist.append("<div style='");
      slist.append(vHelper.getCSSStyles(bounds, fmt, true));
      slist.append(";z-index:");
      slist.append(info.getZIndex());
      slist.append("'>");

      if(titleH > 0) {
         vHelper.appendContainerTitle(slist, info, titleH, titleRatio, containerTitle);
      }

      appendSelections(slist, info, (int)bounds.getHeight() - titleH, bounds);
      slist.append("</div>");

      try {
         writer.write(slist.toString());
      }
      catch(Exception e) {
         LOG.error("Failed to write table: " + assembly, e);
      }
   }

   // Add two tables in table view. Onew show header will not scroll, data table can scroll.
   private void appendSelections(StringBuffer slist, SelectionListVSAssemblyInfo info, int dataH,
      Rectangle2D bounds)
   {
      if(info.getShowTypeValue() == SelectionListVSAssemblyInfo.DROPDOWN_SHOW_TYPE) {
         return;
      }

      VSCompositeFormat fmt = info.getFormat();
      SelectionList list = info.getSelectionList();

      if(list == null) {
         return;
      }

      SelectionValue[] svalues = list.getAllSelectionValues();
      slist.append("<div style='overflow:auto;width:100%;height:" + dataH + "'>");

      int ncol = info.getColumnCount();
      int nrow = svalues.length % ncol == 0 ? (int)(svalues.length / ncol) :
         (int)(svalues.length / ncol) + 1;

      SelectionValue[][] nvalues = new SelectionValue[nrow][ncol];
      int index = 0;
      double ratio = Math.max(0.25, info.getMeasureTextRatio());

      for(int i = 0; i < nrow; i++) {
         for(int j = 0; j < ncol; j++) {
            index = i * ncol + j;

            if(index >= svalues.length) {
               break;
            }

            nvalues[i][j] = svalues[index];
         }
      }

      //if have scroll bar, rowWidth = selectionWidth - scrollBarWidth(18) - leftAndRightBorder(2).
      double rowWidth = info.getCellHeight() * nrow > bounds.getHeight() ? bounds.getWidth() - 20 :
        bounds.getWidth() - 2;

      for(int k = 0; k < nrow; k++) {
         writeSelectionRow(info, slist, nvalues[k], rowWidth / ncol, ratio);
      }

      slist.append("</div>");
   }

   private void writeSelectionRow(SelectionListVSAssemblyInfo info, StringBuffer slist,
                                  SelectionValue[] valueRow, double valueWidth, double ratio)
   {
      slist.append("<div style='width:100%'>");

      for(int i = 0; i < valueRow.length; i++) {
         if(valueRow[i] == null) {
            continue;
         }

         writeSelectionValue(info, slist, valueRow[i], valueWidth, ratio);
      }

      slist.append("</div>");
   }

   private void writeSelectionValue(SelectionListVSAssemblyInfo info, StringBuffer slist,
      SelectionValue svalue, double valueWidth, double ratio)
   {
      String cellHeight = info.getCellHeight() + "";
      VSCompositeFormat format = svalue.getFormat();
      Insets padding = info.getCellPadding();

      if(format.isWrapping()) {
         cellHeight = "auto";
      }

      slist.append("<div style='width:" + valueWidth +"px;height:"+ cellHeight +
              "px;position:relative;padding-left:2px;display:flex;float:left;");

      if(padding != null) {
         slist.append("padding-top:" + padding.top + "px;");
         slist.append("padding-bottom:" + padding.bottom + "px;");
      }

      slist.append(vHelper.getCSSStyles(null, format));
      slist.append("'>");
      slist.append("<input type='");
      slist.append(info.isSingleSelection() ? "radio" : "checkbox");
      slist.append("' value='" + svalue + "'");
      boolean showBar = info.isShowBar();
      boolean showText = info.isShowText();
      String measure = info.getMeasure();

      int barsize = (showBar && measure != null) ? info.getBarSize() : 0;
      int textsize = (showText && measure != null) ? info.getMeasureSize() : 0;
      double barWidth = barsize <= 0 && showBar ? valueWidth / 4 : barsize;
      double textArea = Math.max(0, valueWidth - barWidth - this.lableLeft - this.indentSize);
      double textWidth = (textsize < 0) ? textArea * ratio : textsize;
      textWidth = Math.min(valueWidth - barWidth, textWidth);
      double labelWidth = textArea - textWidth;

      if(svalue.isSelected()) {
         slist.append(" checked");
      }

      slist.append(">");
      slist.append("<div style='width:" + labelWidth + "px;overflow:hidden;");
      slist.append(!format.isWrapping() ? "white-space:nowrap;" : "");

      if(padding != null) {
         slist.append("padding-left:" + padding.left + "px;");
         slist.append("padding-right:" + padding.right + "px;");
      }

      slist.append("'>");

      slist.append(svalue.getLabel());
      slist.append("</div>");

      if(showText && svalue.getMeasureLabel() != null) {
         FormatInfo finfo = info.getFormatInfo();
         TableDataPath datapath =
            SelectionBaseVSAssemblyInfo.getMeasureTextPath(svalue.getLevel());
         VSCompositeFormat formatMT = finfo.getFormat(datapath);

         slist.append("<div style='width:" + textWidth + "px;height:100%;float:right;position:absolute;right:" +
         barWidth + "px;text-align:right;");
         slist.append(vHelper.getCSSStyles(null, formatMT));
         slist.append(";align-items: center'>");
         slist.append(svalue.getMeasureLabel());
         slist.append("</div>");
      }

      if(showBar && svalue.getMeasureLabel() != null) {
         try {
            Image barImage = this.paintBar(svalue, (int)barWidth, 16,
               info.getSelectionList(), info);
            String style = "width:" + barWidth +
               "px;float:right;position:absolute;right:-2px";
            slist.append(vHelper.getImage((BufferedImage)barImage, null, style));
         }
         catch(Exception e) {
            LOG.error("Failed to write selection container: " + info.getName(), e);
         }
      }

      slist.append("</div>");
   }

   private HTMLCoordinateHelper vHelper = null;
   private Viewsheet vs;
   private VSAssembly assembly;
   private static final Logger LOG = LoggerFactory.getLogger(HTMLTableHelper.class);
   private int lableLeft = 18;
   private int indentSize = 8;
   private int measureBarHeight = 16; //1em in css
}
