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
package inetsoft.report.io.viewsheet.html;

import inetsoft.report.TableDataPath;
import inetsoft.report.io.viewsheet.CoordinateHelper;
import inetsoft.report.io.viewsheet.VSSelectionTreeHelper;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.PrintWriter;
import java.util.Vector;

/**
 * Table helper used when exporting to HTML.
 */
public class HTMLSelectionTreeHelper extends VSSelectionTreeHelper{
   /**
    * Creates a new instance of <tt>HTMLSelectionTreeHelper</tt>.
    *
    * @param helper the coordinate helper.
    * @param vs     the viewsheet being exported.
    */
   public HTMLSelectionTreeHelper(HTMLCoordinateHelper helper, Viewsheet vs) {
      this.vs = vs;
      this.vHelper =  helper;
   }

   /**
    * Creates a new instance of <tt>HTMLSelectionTreeHelper</tt>.
    *
    * @param helper   the coordinate helper.
    * @param vs       the viewsheet being exported.
    * @param assembly the assembly being written.
    */
   public HTMLSelectionTreeHelper(HTMLCoordinateHelper helper, Viewsheet vs, VSAssembly assembly) {
      this(helper, vs);
      this.assembly = assembly;
   }

   public void write(PrintWriter writer, SelectionTreeVSAssembly assembly) {
      SelectionTreeVSAssemblyInfo info = (SelectionTreeVSAssemblyInfo) assembly.getVSAssemblyInfo();

      if(info == null) {
         return;
      }

      Rectangle2D bounds = vHelper.getBounds(assembly, CoordinateHelper.ALL);
      VSCompositeFormat fmt = info.getFormat();
      StringBuffer slist = new StringBuffer("");
      int titleH = !((TitledVSAssemblyInfo) info).isTitleVisible() ? 0 :
         ((TitledVSAssemblyInfo) info).getTitleHeight();
      double titleRatio = 0.5;

      if(assembly.getContainer() instanceof CurrentSelectionVSAssembly) {
         CurrentSelectionVSAssembly current = (CurrentSelectionVSAssembly)assembly.getContainer();
         CurrentSelectionVSAssemblyInfo cinfo =
            (CurrentSelectionVSAssemblyInfo) current.getVSAssemblyInfo();
         titleRatio = cinfo.getTitleRatio();
      }

      slist.append("<div style='");
      slist.append(vHelper.getCSSStyles(bounds, fmt, true));
      slist.append(";z-index:");
      slist.append(info.getZIndex());
      slist.append("'>");
      vHelper.appendContainerTitle(slist, info, titleH, titleRatio, null);
      appendSelections(slist, info, (int) bounds.getHeight() - titleH);
      slist.append("</div>");

      try {
         writer.write(slist.toString());
      }
      catch(Exception e) {
         LOG.error("Failed to write table: " + assembly, e);
      }
   }

   // Add two tables in table view. Onew show header will not scroll, data table can scroll.
   private void appendSelections(StringBuffer slist, SelectionTreeVSAssemblyInfo info, int dataH) {
      if(info.getShowTypeValue() == SelectionTreeVSAssemblyInfo.DROPDOWN_SHOW_TYPE) {
         return;
      }

      VSCompositeFormat fmt = info.getFormat();
      Vector dispList = new Vector();
      CompositeSelectionValue csv = info.getCompositeSelectionValue();
      info.visitCompositeChild(csv, dispList, true);  // populate dispList
      slist.append("<div style='overflow:auto;width:100%;height:" + dataH + "'>");

      for(int i = 1; i < dispList.size(); i++) {
         SelectionValue sv = (SelectionValue) dispList.get(i);
         writeSelectionValue(info, slist, sv);
      }

      slist.append("</div>");
   }

   private void writeSelectionValue(SelectionTreeVSAssemblyInfo info, StringBuffer slist,
      SelectionValue svalue)
   {
      VSCompositeFormat format = svalue.getFormat();
      int padding = svalue.getLevel() * 10;
      boolean showBar = info.isShowBar();
      boolean showText = info.isShowText();
      String measure = info.getMeasure();
      Rectangle2D bounds = vHelper.getBounds(info);
      double valueWidth = bounds.getWidth();
      double ratio = Math.max(0.25, info.getMeasureTextRatio());

      int barsize = (showBar && measure != null) ? info.getBarSize() : 0;
      int textsize = (showText && measure != null) ? info.getMeasureSize() : 0;
      double barWidth = barsize <= 0 && showBar ? valueWidth / 4 : barsize;
      double textArea = Math.max(0, valueWidth - barWidth - this.lableLeft - this.indentSize);
      double textWidth = (textsize < 0) ? textArea * ratio : textsize;
      textWidth = Math.min(valueWidth - barWidth, textWidth);
      double labelWidth = textArea - textWidth;
      String cellHeight = format.isWrapping() ? "auto" : info.getCellHeight() + "";
      Insets cellPadding = info.getCellPadding();

      slist.append("<div style='width:100%;position:relative;display:flex;height:" + cellHeight + ";");

      if(cellPadding != null) {
         slist.append("padding-top:" + cellPadding.top + "px;");
         slist.append("padding-bottom:" + cellPadding.bottom + "px;");
      }

      slist.append(vHelper.getCSSStyles(null, format));
      slist.append("'>");

      slist.append("<div style='padding-left:" + padding + "px'>");

      slist.append("<input type='");
      slist.append(isSingleSelectionLevel(info, svalue.getLevel()) ? "radio" : "checkbox");
      slist.append("' value='" + svalue + "'");

      if(svalue.isSelected()) {
         slist.append(" checked");
      }

      slist.append(" >");
      slist.append("</div>");
      String wrap = format.isWrapping() ? "normal" : "nowrap";
      slist.append("<div style='width:" + labelWidth + "px;overflow:hidden;white-space:" +
                      wrap + ";");

      if(cellPadding != null) {
         slist.append("padding-left:" + cellPadding.left + "px;");
         slist.append("padding-right:" + cellPadding.right + "px;");
      }

      slist.append("'>");
      slist.append(svalue.getLabel());
      slist.append("</div>");

      if(showText && svalue.getMeasureLabel() != null) {
         FormatInfo finfo = info.getFormatInfo();
         TableDataPath datapath =
            SelectionBaseVSAssemblyInfo.getMeasureTextPath(svalue.getLevel());
         VSCompositeFormat formatMT = finfo.getFormat(datapath);

         slist.append("<span style='width:" + textWidth + "px;float:right;position:absolute;right:" +
            barWidth + "px;text-align:right;");
         slist.append(vHelper.getCSSStyles(null, formatMT));
         slist.append("'>");
         slist.append(svalue.getMeasureLabel());
         slist.append("</span>");
      }

      if(showBar && svalue.getMeasureLabel() != null) {
         try {
            Image barImage = this.paintBar(svalue, (int)barWidth, 16,
               info.getCompositeSelectionValue().getSelectionList(), info);
            String style = "width:" + barWidth +
               "px;height:100%;float:right;position:absolute;right:0px";
            slist.append(vHelper.getImage((BufferedImage)barImage, null, style));
         }
         catch(Exception e) {
            LOG.error("Failed to write selection container: " + info.getName(), e);
         }
      }

      slist.append("</div>");
   }

   private boolean isSingleSelectionLevel(SelectionTreeVSAssemblyInfo info, int slevel) {
      return info.isSingleSelection()
         && (info.getMode() == SelectionTreeVSAssemblyInfo.ID ||
         info.getSingleSelectionLevels().stream().anyMatch((level) -> level == slevel));
   }

   private HTMLCoordinateHelper vHelper = null;
   private Viewsheet vs;
   private VSAssembly assembly;
   private static final Logger LOG = LoggerFactory.getLogger(HTMLSelectionTreeHelper.class);
   private int lableLeft = 18;
   private int indentSize = 8;
   private int measureBarHeight = 16; //1em in css
}
