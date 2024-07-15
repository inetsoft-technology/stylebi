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
package inetsoft.uql.viewsheet.internal;

import inetsoft.report.*;
import inetsoft.report.internal.binding.OrderInfo;
import inetsoft.uql.asset.AggregateInfo;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.Tool;
import inetsoft.util.css.CSSConstants;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.PrintWriter;
import java.util.List;

/**
 * CalcTableVSAssemblyInfo, the assembly info of a formula assembly.
 *
 * @version 11.3
 * @author InetSoft Technology Corp
 */
public class CalcTableVSAssemblyInfo extends TableDataVSAssemblyInfo {
   /**
    * Constructor.
    */
   public CalcTableVSAssemblyInfo() {
      super();

      tlayout = VSLayoutTool.createDefaultLayout();
      tlayout.setMode(TableLayout.CALC);
      setPixelSize(new Dimension(400, 240));
      fillWithZeroValue = new DynamicValue2("false", XSchema.BOOLEAN);
      sortOthersLast = new DynamicValue2("true", XSchema.BOOLEAN);
   }

   /**
    * Get the object css default type.
    */
   @Override
   public String getObjCSSType() {
      return CSSConstants.CALC_TABLE;
   }

   /**
    * Initialize the default format.
    */
   @Override
   public void initDefaultFormat() {
      setDefaultFormat(true, true);
   }

   @Override
   public void clearBinding() {
      super.clearBinding();

      if(tlayout != null) {
         for(int i = 0; i < tlayout.getRegionCount(); i++) {
            BaseLayout.Region region = tlayout.getRegion(i);

            if(region == null) {
               continue;
            }

            region.clearBinding();
         }
      }
   }

   /**
    * Get table layout.
    */
   public TableLayout getTableLayout() {
      return tlayout;
   }

   /**
    * Set table layout.
    */
   public void setTableLayout(TableLayout tlayout) {
      this.tlayout = tlayout;
   }

   /**
    * Get aggregate info.
    */
   public AggregateInfo getAggregateInfo() {
      return ainfo;
   }

   /**
    * Set aggregate info.
    */
   public void setAggregateInfo(AggregateInfo ainfo) {
      this.ainfo = ainfo;
   }

   /**
    * Set the number of header rows.
    */
   public void setHeaderRowCount(int nrow) {
      hrow = nrow;
   }

   /**
    * Update header row heights.
    */
   public void updateHeaderRowHeights(boolean isWrappedHeader) {
      if(hrow != getHeaderRowHeightsLength()) {
         int[] rowHeights = new int[hrow];

         for(int i = 0; i < hrow; i++) {
            rowHeights[i] = getHeaderRowHeight(i);
         }

         setHeaderRowHeights(rowHeights);
      }
   }

   @Override
   public int getDataRowHeight() {
      return super.getDataRowHeight(hrow);
   }

   /**
    * Set the number of header columns.
    */
   public void setHeaderColCount(int ncol) {
      hcol = ncol;
   }

   /**
    * Set the number of tail rows.
    */
   public void setTrailerRowCount(int nrow) {
      trow = nrow;
   }

   /**
    * Set the number of tail columns.
    */
   public void setTrailerColCount(int ncol) {
      tcol = ncol;
   }

   /**
    * Return the number of rows on the top of the table to be treated
    * as header rows.
    */
   public int getHeaderRowCount() {
      return hrow;
   }

   /**
    * Return the number of columns on the left of the table to be
    * treated as header columns.
    */
   public int getHeaderColCount() {
      return hcol;
   }

   /**
    * Return the number of rows on the bottom of the table to be treated
    * as tail rows.
    */
   public int getTrailerRowCount() {
      return trow;
   }

   /**
    * Return the number of columns on the right of the table to be
    * treated as tail columns.
    */
   public int getTrailerColCount() {
      return tcol;
   }

   /**
    * Set option to fill blank cell with zero. By default blank cells
    * are left blank. If this is true, the blank cells are filled with zero.
    * @param fill true to fill blank cell with zero.
    */
   public void setFillBlankWithZero(boolean fill) {
      fillWithZeroValue.setRValue(fill);
   }

   /**
    * Check if fill blank cell with zero.
    * @return <tt>true</tt> if should fill blank cell with zero,
    * <tt>false</tt> otherwise.
    */
   public boolean isFillBlankWithZero() {
      return Boolean.valueOf(fillWithZeroValue.getRuntimeValue(true) + "");
   }

   /**
    * Set option to fill blank cell with zero. By default blank cells
    * are left blank. If this is true, the blank cells are filled with zero.
    * @param fill true to fill blank cell with zero.
    */
   public void setFillBlankWithZeroValue(boolean fill) {
      this.fillWithZeroValue.setDValue(fill + "");
   }

   /**
    * Check if fill blank cell with zero.
    * @return <tt>true</tt> if should fill blank cell with zero,
    * <tt>false</tt> otherwise.
    */
   public boolean getFillBlankWithZeroValue() {
      return fillWithZeroValue.getBooleanValue(true, false);
   }

   /**
    * Check if 'Others' group should always be sorted as the last item.
    */
   public boolean isSortOthersLast() {
      return Boolean.parseBoolean(sortOthersLast.getRuntimeValue(true) + "");
   }

   /**
    * Set if 'Others' group should always be sorted as the last item.
    */
   public void setSortOthersLast(boolean sortOthersLast) {
      this.sortOthersLast.setRValue(sortOthersLast);
   }

   /**
    * Check if 'Others' group should always be sorted as the last item.
    */
   public boolean getSortOthersLastValue() {
      return sortOthersLast.getBooleanValue(true, true);
   }

   /**
    * Set if 'Others' group should always be sorted as the last item.
    */
   public void setSortOthersLastValue(boolean sortOthersLast) {
      this.sortOthersLast.setDValue(sortOthersLast + "");
   }

   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      writer.print(" hrow=\"" + hrow + "\"");
      writer.print(" hcol=\"" + hcol + "\"");
      writer.print(" trow=\"" + trow + "\"");
      writer.print(" tcol=\"" + tcol + "\"");
      writer.print(" colCount=\"" + ncol + "\"");
      writer.print(" fillWithZero=\"" + getFillBlankWithZeroValue() + "\"");
      writer.print(" sortOthersLast=\"" + getSortOthersLastValue() + "\"");
   }

   @Override
   protected void parseAttributes(Element elem) {
      super.parseAttributes(elem);
      String str;

      if((str = Tool.getAttribute(elem, "hrow")) != null) {
         hrow = Integer.parseInt(str);
      }

      if((str = Tool.getAttribute(elem, "hcol")) != null) {
         hcol = Integer.parseInt(str);
      }

      if((str = Tool.getAttribute(elem, "trow")) != null) {
         trow = Integer.parseInt(str);
      }

      if((str = Tool.getAttribute(elem, "tcol")) != null) {
         tcol = Integer.parseInt(str);
      }

      if((str = Tool.getAttribute(elem, "colCount")) != null) {
         ncol = Integer.parseInt(str);
      }

      if((str = Tool.getAttribute(elem, "fillWithZero")) != null) {
         setFillBlankWithZeroValue(str.equalsIgnoreCase("true"));
      }

      if((str = Tool.getAttribute(elem, "sortOthersLast")) != null) {
         setSortOthersLastValue(str.equalsIgnoreCase("true"));
      }
   }

   /**
    * Write the user defined styles as content.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(tlayout != null) {
         tlayout.writeXML(writer);
      }

      if(ainfo != null) {
         writer.println("<ainfo>");
         ainfo.writeXML(writer);
         writer.println("</ainfo>");
      }
   }

   /**
    * Parse the content part(child node) of XML segment.
    */
   @Override
   protected void parseContents(Element tag) throws Exception {
      Element layout = Tool.getChildNodeByTagName(tag, "tableLayout");

      if(layout != null) {
         tlayout = new TableLayout();
         tlayout.parseXML(layout);
      }

      super.parseContents(tag);

      Element anode = Tool.getChildNodeByTagName(tag, "ainfo");

      if(anode != null) {
         ainfo = new AggregateInfo();
         ainfo.parseXML(Tool.getFirstChildNode(anode));
      }

      //Fix bc issue, if has default border but no border colors, should set default border colors.
      if(getFormat() != null && getFormat().getBorders() != null &&
         getFormat().getBorders().top == 4097 &&  getFormat().getBorders().bottom == 4097 &&
         getFormat().getBorders().left == 4097 &&  getFormat().getBorders().right == 4097 &&
         getFormat().getBorderColors() == null)
      {
         BorderColors borderColors = new BorderColors();
         borderColors.topColor = new Color(218, 218, 218);
         borderColors.leftColor = new Color(218, 218, 218);
         borderColors.bottomColor = new Color(218, 218, 218);
         borderColors.rightColor = new Color(218, 218, 218);
         getFormat().getUserDefinedFormat().setBorderColorsValue(borderColors);
      }
   }

   /**
    * Copy the assembly info.
    * @param info the specified viewsheet assembly info.
    * @param deep whether it is simply copy the properties of the parent.
    * @return the hint to reset view, data or worksheet data.
    */
   @Override
   public int copyInfo(VSAssemblyInfo info, boolean deep) {
      TableLayout layout = null;

      if(info instanceof CalcTableVSAssemblyInfo) {
         layout = (TableLayout)
            ((CalcTableVSAssemblyInfo) info).getTableLayout().clone();
      }

      int hint = super.copyInfo(info, deep);

      if(deep) {
         CalcTableVSAssemblyInfo info0 = (CalcTableVSAssemblyInfo) info;

         if(!Tool.equals(layout, getTableLayout())) {
            setTableLayout(layout);
            hint |= VSAssembly.INPUT_DATA_CHANGED;
            hint |= VSAssembly.BINDING_CHANGED;
         }

         if(!Tool.equals(ainfo, info0.ainfo)) {
            setAggregateInfo(info0.ainfo);
            hint |= VSAssembly.INPUT_DATA_CHANGED;
            hint |= VSAssembly.BINDING_CHANGED;
         }

         if(hrow != info0.hrow || hcol != info0.hcol || trow != info0.trow ||
            tcol != info0.tcol)
         {
            hrow = info0.hrow;
            hcol = info0.hcol;
            trow = info0.trow;
            tcol = info0.tcol;
            hint |= VSAssembly.INPUT_DATA_CHANGED;
         }

         if(!Tool.equals(fillWithZeroValue, info0.fillWithZeroValue)) {
            this.fillWithZeroValue = info0.fillWithZeroValue;
            hint |= VSAssembly.OUTPUT_DATA_CHANGED;
         }

         if(!Tool.equals(sortOthersLast, info0.sortOthersLast)) {
            this.sortOthersLast = info0.sortOthersLast;
            hint |= VSAssembly.OUTPUT_DATA_CHANGED;
         }
      }

      return hint;
   }

   /**
    * Clone this object.
    * @param shallow <tt>true</tt> to perform shallow clone,
    * <tt>false</tt> to perform deep clone.
    * @return the cloned object.
    */
   @Override
   public CalcTableVSAssemblyInfo clone(boolean shallow) {
      CalcTableVSAssemblyInfo cinfo = (CalcTableVSAssemblyInfo) super.clone(shallow);
      cinfo.tlayout = (TableLayout) tlayout.clone();

      if(ainfo != null) {
         cinfo.ainfo = (AggregateInfo) ainfo.clone();
      }

      if(fillWithZeroValue != null) {
         cinfo.fillWithZeroValue = (DynamicValue2) fillWithZeroValue.clone();
      }

      if(sortOthersLast != null) {
         cinfo.sortOthersLast = (DynamicValue2) sortOthersLast.clone();
      }

      return cinfo;
   }

   /**
    * Copy the view part assembly info.
    * @param info the specified viewsheet assembly info.
    * @return <tt>true</tt> if changed, <tt>false</tt> otherwise.
    */
   @Override
   public boolean copyViewInfo(VSAssemblyInfo info, boolean deep) {
      boolean result = super.copyViewInfo(info, deep);

      if(deep) {
         CalcTableVSAssemblyInfo cinfo = (CalcTableVSAssemblyInfo) info;

         if(ncol != cinfo.ncol) {
            ncol = cinfo.ncol;
            result = true;
         }
      }

      return result;
   }

   /**
    * Get cell data path. This matches the CalcTableLens getCellDataPath.
    */
   public TableDataPath getCellDataPath(int row, int col) {
      if(tlayout.getMode() == TableLayout.CALC) {
         return new TableDataPath(-1, row < hrow ? TableDataPath.HEADER : TableDataPath.DETAIL,
                                  XSchema.STRING, new String[]{ "Cell [" + row + "," + col + "]" });
      }

      return tlayout.getCellDataPath(row, col);
   }

   /**
    * Reset runtime values.
    */
   @Override
   public void resetRuntimeValues() {
      super.resetRuntimeValues();

      fillWithZeroValue.setRValue(null);
      sortOthersLast.setRValue(null);
   }

   /**
    * Check whether to support sort 'Others' group as the last item.
    */
   public boolean isSortOthersLastEnabled() {
      List<TableCellBinding> groupCells =
         LayoutTool.getTableCellBindings(tlayout, TableCellBinding.GROUP);
      List<TableCellBinding> summaryCells =
         LayoutTool.getTableCellBindings(tlayout, TableCellBinding.SUMMARY);

      return summaryCells.size() > 0 && groupCells.stream()
         .filter(c -> c.getTopN(false) != null || c.getOrderInfo(false) != null)
         .anyMatch(c -> (c.getTopN(false) != null && !c.getTopN(false).isBlank() &&
            c.getTopN(false).isOthers()) || (c.getOrderInfo(false) != null &&
            c.getOrderInfo(false).getOthers() == OrderInfo.GROUP_OTHERS &&
            c.getOrderInfo(false).getNamedGroupInfo() != null &&
            !c.getOrderInfo(false).getNamedGroupInfo().isEmpty()));
   }

   private TableLayout tlayout;
   private AggregateInfo ainfo = null;
   private int hrow = 1, hcol;
   private int trow, tcol;
   private int ncol = 0;
   private DynamicValue2 fillWithZeroValue;
   private DynamicValue2 sortOthersLast;
}
