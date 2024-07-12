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
package inetsoft.uql.viewsheet.internal;

import inetsoft.report.*;
import inetsoft.report.internal.table.TableHighlightAttr;
import inetsoft.report.internal.table.TableHyperlinkAttr;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.SortInfo;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.Tool;
import inetsoft.util.css.CSSDictionary;
import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.*;
import java.io.PrintWriter;
import java.util.List;
import java.util.*;

/**
 * TableDataVSAssemblyInfo, the assembly info of a table data assembly.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public abstract class TableDataVSAssemblyInfo extends DataVSAssemblyInfo
   implements CompositeVSAssemblyInfo, TipVSAssemblyInfo
{
   /**
    * Constructor.
    */
   public TableDataVSAssemblyInfo() {
      super();
      shrinkValue = new DynamicValue2("false", XSchema.BOOLEAN);
      tipOptionValue = new DynamicValue2(TOOLTIP_OPTION + "", XSchema.INTEGER);
      flyoverValue = new ClazzHolder<>();
      flyClickValue = new DynamicValue("false", XSchema.BOOLEAN);
      enableAdhocValue = new DynamicValue2("false", XSchema.BOOLEAN);
      explicitTableWidth = new DynamicValue2("false", XSchema.BOOLEAN);
      sinfo = new SortInfo();
      rowHeights = new HashMap<>();
      colWidths = new HashMap<>();
      rcolWidths = new HashMap<>();
      headerRowHeights = new int[] { AssetUtil.defh };
   }

   /**
    * Get the style of the target table.
    * @return the style of the target table.
    */
   public String getTableStyle() {
      return styleValue.getRValue() != null ? styleValue.getRValue() + "" : null;
   }

   /**
    * Set the style of the target table.
    * @param style the specified style of the target table.
    */
   public void setTableStyle(String style) {
      styleValue.setRValue(style);
   }

   /**
    * Get the style of the target table.
    * @return the style of the target table.
    */
   public String getTableStyleValue() {
      return styleValue.getDValue();
   }

   /**
    * Set the style of the target table.
    * @param style the specified style of the target table.
    */
   public void setTableStyleValue(String style) {
      styleValue.setDValue(style);
   }

   /**
    * Get the group title.
    * @return the title of the checkbox assembly.
    */
   @Override
   public String getTitle() {
      return titleInfo.getTitle(getFormatInfo().getFormat(TITLEPATH), getViewsheet(), getName());
   }

   /**
    * Set option to fill blank space.
    * @param fit true to fill blank space.
    */
   public void setShrink(boolean fit) {
       shrinkValue.setRValue(fit);
   }

   /**
    * Check if shrink cell with space.
    * @return <tt>true</tt> if should fill blank space,
    * <tt>false</tt> otherwise.
    */
   public boolean isShrink(){
      return Boolean.parseBoolean(shrinkValue.getRuntimeValue(true) + "");
   }

   /**
    * If this option is true,they are shrinked the space.
    * @param fit true to fill blank space.
    */
   public void setShrinkValue(boolean fit){
      this.shrinkValue.setDValue(fit + "");
   }

   /**
    * Check if shrink cell with space.
    * @return <tt>true</tt> if should fill blank space,
    * <tt>false</tt> otherwise.
    */
   public boolean getShrinkValue() {
      return shrinkValue.getBooleanValue(true, false);
   }

   /**
    * Set enable adhoc.
    */
   public void setEnableAdhoc(boolean enable) {
      enableAdhocValue.setRValue(enable);
   }

   /**
    * Check if adhoc is enabled.
    */
   public boolean isEnableAdhoc() {
      return Boolean.parseBoolean(enableAdhocValue.getRuntimeValue(true) + "");
   }

   /**
    * Set enable adhoc.
    */
   public void setEnableAdhocValue(boolean enable) {
      enableAdhocValue.setDValue(enable + "");
   }

   /**
    * Check if adhoc is enabled.
    */
   public boolean getEnableAdhocValue() {
      return enableAdhocValue.getBooleanValue(true, false);
   }

   /**
    * Set the group title value.
    * @param value the specified group title.
    */
   @Override
   public void setTitle(String value) {
      titleInfo.setTitle(value);
   }

   /**
    * Get the group title value.
    * @return the title value of the checkbox assembly.
    */
   @Override
   public String getTitleValue() {
      return titleInfo.getTitleValue();
   }

   /**
    * Set the group title value.
    * @param value the specified group title.
    */
   @Override
   public void setTitleValue(String value) {
      this.titleInfo.setTitleValue(value);
   }

   /**
    * Set the table titleVisible value.
    * @param visible the value the specified table title.
    */
   @Override
   public void setTitleVisible(boolean visible) {
      titleInfo.setTitleVisible(visible);
   }

   /**
    * Check whether the table title is visible.
    * @return the title visible of the table assembly.
    */
   @Override
   public boolean isTitleVisible() {
      return titleInfo.isTitleVisible();
   }

   /**
    * Set the run time table title visible value.
    * @param visible the title visible value of the table assembly.
    */
   @Override
   public void setTitleVisibleValue(boolean visible) {
      titleInfo.setTitleVisibleValue(visible + "");
   }

   /**
    * Get the design time table title visible value.
    * @return the title visible value of the table assembly.
    */
   @Override
   public boolean getTitleVisibleValue() {
      return titleInfo.getTitleVisibleValue();
   }

   /**
    * Get the table title height.
    * @return the title height of the table assembly.
    */
   @Override
   public int getTitleHeight() {
      return titleInfo.getTitleHeight();
   }

   /**
    * Get the table title height value.
    * @return the title height value of the table assembly.
    */
   @Override
   public int getTitleHeightValue() {
      return titleInfo.getTitleHeightValue();
   }

   /**
    * Set the table title height value.
    * @param value the specified table title height.
    */
   @Override
   public void setTitleHeightValue(int value) {
      titleInfo.setTitleHeightValue(value);
   }

   /**
    * Set the table title height.
    * @param value the specified table title height.
    */
   @Override
   public void setTitleHeight(int value) {
      titleInfo.setTitleHeight(value);
   }

   @Override
   public Insets getTitlePadding() {
      return titleInfo.getPadding();
   }

   @Override
   public void setTitlePadding(Insets padding, CompositeValue.Type type) {
      titleInfo.setPadding(padding, type);
   }

   /**
    * Get the run time tip option.
    */
   @Override
   public int getTipOption() {
      return tipOptionValue.getIntValue(false, TOOLTIP_OPTION);
   }

   /**
    * Set the run time tip option.
    */
   @Override
   public void setTipOption(int tipOption) {
      tipOptionValue.setRValue(tipOption);
   }

   /**
    * Get the design time tip option.
    */
   @Override
   public int getTipOptionValue() {
      return tipOptionValue.getIntValue(true, TOOLTIP_OPTION);
   }

   /**
    * Set the design time tip option.
    */
   @Override
   public void setTipOptionValue(int tipOption) {
      tipOptionValue.setDValue(tipOption + "");
   }

   /**
    * Get the run time tip view.
    */
   @Override
   public String getTipView() {
      Object tipView = tipViewValue.getRValue();
      return tipView == null ? null : tipView + "";
   }

   /**
    * Set the run time tip view.
    */
   @Override
   public void setTipView(String tipView) {
      tipViewValue.setRValue(tipView);
   }

   /**
    * Get the design time tip view.
    */
   @Override
   public String getTipViewValue() {
      return tipViewValue.getDValue();
   }

   /**
    * Set the design time tip view.
    */
   @Override
   public void setTipViewValue(String tipView) {
      tipViewValue.setDValue(tipView);
   }

   /**
    * Get the run time alpha.
    */
   @Override
   public String getAlpha() {
      Object alpha = alphaValue.getRValue();
      return alpha == null ? null : alpha + "";
   }

   /**
    * Set the run time alpha.
    */
   @Override
   public void setAlpha(String alpha) {
      alphaValue.setRValue(alpha);
   }

   /**
    * Get the design time alpha.
    */
   @Override
   public String getAlphaValue() {
      return alphaValue.getDValue();
   }

   /**
    * Set the design time alpha.
    */
   @Override
   public void setAlphaValue(String alpha) {
      alphaValue.setDValue(alpha);
   }

   /**
    * Get the views to apply filtering on mouse flyover over this assembly.
    */
   @Override
   public String[] getFlyoverViews() {
      return flyoverValue.getRValue();
   }

   /**
    * Set the views to apply filtering on mouse flyover over this assembly.
    */
   @Override
   public void setFlyoverViews(String[] views) {
      flyoverValue.setRValue(views);
   }

   /**
    * Get the views to apply filtering on mouse flyover over this assembly.
    */
   @Override
   public String[] getFlyoverViewsValue() {
      return flyoverValue.getDValue();
   }

   /**
    * Set the views to apply filtering on mouse flyover over this assembly.
    */
   @Override
   public void setFlyoverViewsValue(String[] views) {
      flyoverValue.setDValue(views);
   }

   /**
    * Check if only apply flyover when clicked.
    */
   public String getFlyOnClickValue() {
      return flyClickValue.getDValue();
   }

   /**
    * Set if only apply flyover when clicked.
    */
   public void setFlyOnClickValue(String val) {
      flyClickValue.setDValue(val);
   }

   /**
    * Check if only apply flyover when clicked.
    */
   public boolean isFlyOnClick() {
      return (Boolean) flyClickValue.getRuntimeValue(true);
   }

   /**
    * Set if only apply flyover when clicked.
    */
   public void setFlyOnClick(boolean val) {
      flyClickValue.setRValue(val);
   }

   /**
    * Get hyperlink attr.
    */
   public TableHyperlinkAttr getHyperlinkAttr() {
      return hyperlinkAttr;
   }

   /**
    * Set hyperlink attr.
    */
   public void setHyperlinkAttr(TableHyperlinkAttr hattr) {
      this.hyperlinkAttr = hattr;
   }

   /**
    * Get hyperlink for each row of the table.
    */
   public Hyperlink getRowHyperlink() {
      return null;
   }

   /**
    * Set hyperlink for each row of the table.
    */
   public void setRowHyperlink(Hyperlink rowHyperlink) {
      // nothing, need to be override
   }

   /**
    * Get highlight attr.
    */
   public TableHighlightAttr getHighlightAttr() {
      return highlightAttr;
   }

   /**
    * Set highlight attr.
    */
   public void setHighlightAttr(TableHighlightAttr hattr) {
      this.highlightAttr = hattr;
   }

   /**
    * Set row height.
    * @param row the row index.
    * @param n the height of the row as a ratio to the total height.
    */
   public void setRowHeight(int row, double n) {
      // to default size
      if(n < 0) {
         rowHeights.remove(row);
      }
      else {
         rowHeights.put(row, n);
      }
   }

   /**
    * Get the row height.
    */
   public double getRowHeight(int row) {
      Double nobj = rowHeights.get(row);

      if(nobj != null) {
         return nobj;
      }

      return Double.NaN;
   }

   /**
    * Get script defined row heights.
    */
   public Map<Integer, Double> getRowHeights() {
      return rowHeights;
   }

   public boolean isUserDefinedWidth() {
      return rcolWidths.size() > 0 || colWidths.size() > 0 ||
         colWidths2.size() > 0 || rcolWidths2.size() > 0;
   }

   /**
    * Set column width.
    * @param col the column index.
    * @param n the width of the column
    */
   public void setColumnWidth(int col, double n) {
      rcolWidths.put(col, n);
   }

   /**
    * Get the column width.
    */
   public double getColumnWidth(int col) {
      Double nobj = rcolWidths.get(col);

      if(nobj != null) {
         return nobj;
      }

      return getColumnWidthValue(col);
   }

   /**
    * Get the column with from either explicitly set on column index or table data path.
    */
   public double getColumnWidth2(int col, XTable lens) {
      double w = getColumnWidth(col);

      if((Double.isNaN(w) || w < 0) && lens != null) {
         TableDataPath cpath = lens.getDescriptor().getColDataPath(col);
         Double w2 = rcolWidths2.get(cpath);

         if(w2 == null) {
            w2 = colWidths2.get(cpath);
         }

         if(w2 != null) {
            w = w2;
         }
      }

      return w;
   }

   /**
    * Get runtime column width2.
    */
   public Map<TableDataPath, Double> getRColumnWidths2() {
      return rcolWidths2;
   }

   /**
    * Set runtime column width2.
    */
   public void setRColumnWidth2(TableDataPath path, double width, XTable lens) {
      clearColWidths(lens, path);
      rcolWidths2.put(path, width);
   }

   /**
    * Set column width.
    * @param col the column index.
    * @param width the width of the column, decimal as a ratio to the total width.
    */
   public void setColumnWidthValue(int col, double width) {
      rcolWidths.remove(col);

      if(Double.isNaN(width)) {
         colWidths.remove(col);
      }
      else {
         colWidths.put(col, width);
      }
   }

   /**
    * Set the runtime column width for columns matching the same column data path as the column at
    * the index of the table lens.
    */
   public void setColumnWidth2(int col, double width, XTable lens) {
      if(lens == null) {
         setColumnWidth(col, width);
         return;
      }

      TableDataPath path = lens.getDescriptor().getColDataPath(col);

      if(path == null) {
         setColumnWidth(col, width);
         return;
      }

      clearColWidths(lens, path);
      rcolWidths2.put(path, width);
   }

   /**
    * Set the column width for columns matching the same column data path as the column at
    * the index of the table lens.
    */
   public void setColumnWidthValue2(int col, double width, XTable lens) {
      if(lens == null) {
         setColumnWidthValue(col, width);
         return;
      }

      TableDataPath path = lens.getDescriptor().getColDataPath(col);

      if(path == null) {
         setColumnWidthValue(col, width);
         return;
      }

      clearColWidths(lens, path);
      rcolWidths2.remove(path);
      colWidths2.put(path, width);
   }

   private void clearColWidths(XTable lens, TableDataPath path) {
      for(int i = 0; i < lens.getColCount(); i++) {
         if(Objects.equals(lens.getDescriptor().getColDataPath(i), path)) {
            rcolWidths.remove(i);
            colWidths.remove(i);
         }
      }
   }

   /**
    * Get the column width.
    */
   public double getColumnWidthValue(int col) {
      Double nobj = colWidths.get(col);

      if(nobj != null) {
         return nobj;
      }

      return Double.NaN;
   }

   /**
    * Get the row height size.
    */
   public int getHeightSize() {
      return rowHeights.size();
   }

   /**
    * Get the column width size.
    */
   public int getColumnWidthSize() {
      return colWidths.size();
   }

   /**
    * Get the header row height of the specified row.
    */
   public int[] getHeaderRowHeights(boolean isWrappedHeader) {
      updateHeaderRowHeights(isWrappedHeader);

      return getTableHeaderRowHeights();
   }

   /**
    * Get the table header row height
    */
   protected int[] getTableHeaderRowHeights() {
      if(headerRowHeights == null) {
         return new int[0];
      }

      // don't set headerRowHeights from getRowHeight() permanently. (49767)
      int[] headerRowHeights = this.headerRowHeights.clone();

      for(int i = 0; i < headerRowHeights.length; i++) {
         double h = getRowHeight(i);

         if(!Double.isNaN(h)) {
            headerRowHeights[i] = (int) h;
         }
      }

      return headerRowHeights;
   }

   /**
    * Get the header row height of the specified row.
    */
   public int[] getHeaderRowHeights() {
      return getHeaderRowHeights(false);
   }

   /**
    * The length of the header row array.
    */
   public int getHeaderRowHeightsLength() {
      return headerRowHeights.length;
   }

   /**
    * Get the header row height of the specified row.
    */
   public int getHeaderRowHeight(int row) {
      if(headerRowHeights == null || headerRowHeights.length == 0
         || headerRowHeights.length <= row)
      {
         return AssetUtil.defh;
      }

      return headerRowHeights[row];
   }

   /**
    * Set the header row heights.
    */
   public void setHeaderRowHeights(int[] headerRowHeights) {
      this.headerRowHeights = headerRowHeights;
   }

   /**
    * Set the header row height for specified row
    */
   public void setHeaderRowHeight(int row, int value) {
      this.headerRowHeights[row] = value;
   }

   /**
    * Call when table headers have been updated, to update the heights.
    * isWrapped parameter could be false/true, only crosstable would use.
    */
   public abstract void updateHeaderRowHeights(boolean isWrappedHeader);

   /**
    * Get the data row height.
    */
   public int getDataRowHeight() {
      return getDataRowHeight(1);
   }

   /**
    * Get the data row height.
    */
   public int getDataRowHeight(int headerRowCount) {
      return getRowHeights().entrySet().stream()
         .filter((entry) -> entry.getKey() >= headerRowCount)
         .map(Map.Entry::getValue)
         .findAny()
         // need to support setting row height to 0 (for date comparison)
         .filter(d -> !d.isNaN() && d > 0)
         .map(Double::intValue)
         .orElse(dataRowHeight);
   }

   /**
    * Set the data row height.
    */
   public void setDataRowHeight(int dataRowHeight) {
      this.dataRowHeight = dataRowHeight;
   }

   /**
    * Set flag that table width is explicitly defined,
    * and shouldn't be manipulated.
    *
    * @param value <tt>true</tt> for user sized table width
    */
   public void setExplicitTableWidth(boolean value) {
       explicitTableWidth.setRValue(value);
   }

   /**
    * Return whether the table has been explicitly sized by the user.
    *
    * @return <tt>true</tt> if the user has explicitly sized the table and
    *                       therefore, should not be programmatically changed,
    *         <tt>false</tt> otherwise.
    */
   public boolean isExplicitTableWidth(){
      return Boolean.parseBoolean(explicitTableWidth.getRuntimeValue(true) + "");
   }

   /**
    * Check if table width is explicitly defined for design time.
    */
   public boolean getExplicitTableWidthValue() {
      return explicitTableWidth.getBooleanValue(true, false);
   }

   /**
    * Set flag that table width is explicitly defined,
    * and shouldn't be manipulated for design time.
    *
    * @param value <tt>true</tt> for user sized table width
    */
   public void setExplicitTableWidthValue(boolean value) {
      explicitTableWidth.setDValue(value + "");
   }

   /**
    * Get the sort info.
    * @return the sort info.
    */
   public SortInfo getSortInfo() {
      return sinfo;
   }

   /**
    * Set the sort info.
    * @param info the specified sort info.
    */
   public void setSortInfo(SortInfo info) {
      this.sinfo = info == null ? new SortInfo() : info;
   }

   public boolean isUserHeaderRowHeight() {
      return userHeaderRowHeight;
   }

   public void setUserHeaderRowHeight(boolean userHeaderRowHeight) {
      this.userHeaderRowHeight = userHeaderRowHeight;
   }

   public boolean isUserDataRowHeight() {
      return userDataRowHeight;
   }

   public void setUserDataRowHeight(boolean userDataRowHeight) {
      this.userDataRowHeight = userDataRowHeight;
   }

   /**
    * Rename the depended. This method should be called when an assembly or
    * other named variables are renamed. It updates of the dynamic references
    * to use the new name.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   @Override
   public void renameDepended(String oname, String nname, Viewsheet vs) {
      super.renameDepended(oname, nname, vs);

      titleInfo.renameDepended(oname, nname, vs);
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      writer.print(" shrink=\"" + isShrink() + "\"");
      writer.print(" shrinkValue=\"" + getShrinkValue() + "\"");
      writer.print(" tipOption=\"" + getTipOption() + "\"");
      writer.print(" tipOptionValue=\"" + getTipOptionValue() + "\"");
      writer.print(" flyClick=\"" + isFlyOnClick() + "\"");
      writer.print(" flyClickValue=\"" + getFlyOnClickValue() + "\"");
      writer.print(" enableAdhoc=\"" + isEnableAdhoc() + "\"");
      writer.print(" enableAdhocValue=\"" + getEnableAdhocValue() + "\"");
      writer.print(" explicitTableWidth=\"" + isExplicitTableWidth() + "\"");
      writer.print(" explicitTableWidthValue=\"" + getExplicitTableWidthValue() + "\"");
      writer.print(" headerRowHeights=\"" + Tool.arrayToString(getHeaderRowHeights()) + "\"");
      writer.print(" dataRowHeight=\"" + dataRowHeight + "\"");
      writer.print(" userHeaderRowHeight=\"" + isUserHeaderRowHeight() + "\"");
      writer.print(" userDataRowHeight=\"" + isUserDataRowHeight() + "\"");
   }

   /**
    * Parse attributes.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseAttributes(Element elem) {
      super.parseAttributes(elem);
      String prop;

      if((prop = getAttributeStr(elem, "shrink", "false")) != null) {
         setShrinkValue(prop.equalsIgnoreCase("true"));
      }

      if(Tool.getAttribute(elem, "tipOption") != null) {
         prop = getAttributeStr(elem, "tipOption", "" + TOOLTIP_OPTION);
         setTipOptionValue(Integer.parseInt(prop));
      }

      setFlyOnClickValue(Tool.getAttribute(elem, "flyClickValue"));

      if((prop = getAttributeStr(elem, "enableAdhoc", "false")) != null) {
         setEnableAdhocValue(prop.equalsIgnoreCase("true"));
      }

      if((prop = getAttributeStr(elem, "explicitTableWidth", "false")) != null) {
         setExplicitTableWidthValue(prop.equalsIgnoreCase("true"));
      }

      if(Tool.getAttribute(elem, "headerRowHeights") != null) {
         prop = getAttributeStr(elem, "headerRowHeights", "");

         if(!prop.isEmpty()) {
            String[] heights = Tool.split(prop, ',');
            int[] heightInts = new int[heights.length];

            for(int i = 0; i < heightInts.length; i++) {
               heightInts[i] = Integer.parseInt(heights[i]);
            }

            setHeaderRowHeights(heightInts);
         }

         boolean defUserHeaderRowHeight = headerRowHeights != null &&
            Arrays.stream(headerRowHeights).filter((h) -> h != AssetUtil.defh).findFirst().isPresent();
         prop = getAttributeStr(elem, "userHeaderRowHeight", defUserHeaderRowHeight + "");
         setUserHeaderRowHeight("true".equalsIgnoreCase(prop));
      }

      if(Tool.getAttribute(elem, "dataRowHeight") != null) {
         prop = getAttributeStr(elem, "dataRowHeight", AssetUtil.defh + "");
         setDataRowHeight(Integer.parseInt(prop));

         prop = getAttributeStr(elem, "userDataRowHeight", (getDataRowHeight() != AssetUtil.defh) + "");
         setUserDataRowHeight("true".equalsIgnoreCase(prop));
      }
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(titleInfo != null) {
         titleInfo.writeXML(writer, getFormatInfo().getFormat(TITLEPATH),
            getViewsheet(), getName());
      }

      if(getTableStyle() != null) {
         writer.print("<style>");
         writer.print("<![CDATA[" + getTableStyle() + "]]>");
         writer.println("</style>");
      }

      if(styleValue.getDValue() != null) {
         writer.print("<styleValue>");
         writer.print("<![CDATA[" + styleValue.getDValue() + "]]>");
         writer.println("</styleValue>");
      }

      if(hyperlinkAttr != null) {
         writer.print("<hyperlinkAttr>");
         hyperlinkAttr.writeXML(writer);
         writer.println("</hyperlinkAttr>");
      }

      if(highlightAttr != null) {
         writer.print("<highlightAttr>");
         highlightAttr.writeXML(writer);
         writer.println("</highlightAttr>");
      }

      if(tipViewValue != null && tipViewValue.getDValue() != null) {
         writer.print("<tipViewValue>");
         writer.print("<![CDATA[" + tipViewValue.getDValue() + "]]>");
         writer.println("</tipViewValue>");
      }

      if(tipViewValue != null && getTipView() != null) {
         writer.print("<tipView>");
         writer.print("<![CDATA[" + getTipView() + "]]>");
         writer.println("</tipView>");
      }

      if(alphaValue != null && alphaValue.getDValue() != null) {
         writer.print("<alphaValue>");
         writer.print("<![CDATA[" + alphaValue.getDValue() + "]]>");
         writer.println("</alphaValue>");
      }

      if(alphaValue != null && getAlpha() != null) {
         writer.print("<alpha>");
         writer.print("<![CDATA[" + getAlpha() + "]]>");
         writer.println("</alpha>");
      }

      if(flyoverValue.getDValue() != null) {
         writer.println("<flyoverViewValues>");

         for(String view : flyoverValue.getDValue()) {
            writer.println("<flyoverViewValue><![CDATA[" + view +
                           "]]></flyoverViewValue>");
         }

         writer.println("</flyoverViewValues>");
      }

      if(flyoverValue.getRValue() != null) {
         writer.println("<flyoverViews>");

         for(String view : flyoverValue.getRValue()) {
            writer.println("<flyoverView><![CDATA[" + view + "]]></flyoverView>");
         }

         writer.println("</flyoverViews>");
      }

      writeColWidth(writer);
      writeRowHeight(writer, false);
      sinfo.writeXML(writer);
   }

   /**
    * Write column width.
    */
   public void writeColWidth(PrintWriter writer) {
      for(Map.Entry<Integer, Double> e : colWidths.entrySet()) {
         writer.print("<colWidth column=\"" + e.getKey() + "\" width=\"" +
                      e.getValue() + "\"/>");
      }

      for(Map.Entry<Integer, Double> e : rcolWidths.entrySet()) {
         writer.print("<rcolWidth column=\"" + e.getKey() + "\" width=\"" +
                      e.getValue() + "\"/>");
      }

      for(Map.Entry<TableDataPath, Double> e : colWidths2.entrySet()) {
         writer.print("<colWidth2 width=\"" + e.getValue() + "\">");
         e.getKey().writeXML(writer);
         writer.println("</colWidth2>");
      }
   }

   /**
    * Write row height.
    */
   public void writeRowHeight(PrintWriter writer, boolean all) {
      for(Map.Entry<Integer, Double> e : rowHeights.entrySet()) {
         // row height of 0 is only set at runtime and should not be persistent.
         if(all || e.getValue() > 0) {
            writer.print("<rowHeight row=\"" + e.getKey() + "\" height=\"" + e.getValue() + "\"/>");
         }
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      styleValue.setDValue(getContentsStr(elem, "style", null));
      titleInfo.parseXML(elem);
      Element node = Tool.getChildNodeByTagName(elem, "hyperlinkAttr");

      if(node != null) {
         hyperlinkAttr = new TableHyperlinkAttr();
         hyperlinkAttr.parseXML((Element) node.getFirstChild());
      }

      node = Tool.getChildNodeByTagName(elem, "highlightAttr");

      if(node != null) {
         highlightAttr = new TableHighlightAttr();
         highlightAttr.parseXML((Element) node.getFirstChild());
      }

      node = Tool.getChildNodeByTagName(elem, "tipViewValue");
      node =
         node == null ? Tool.getChildNodeByTagName(elem, "tipView") : node;

      if(Tool.getValue(node) != null) {
         tipViewValue.setDValue(Tool.getValue(node));
      }

      node = Tool.getChildNodeByTagName(elem, "alphaValue");
      node =
         node == null ? Tool.getChildNodeByTagName(elem, "alpha") : node;

      if(Tool.getValue(node) != null) {
         alphaValue.setDValue(Tool.getValue(node));
      }

      node = Tool.getChildNodeByTagName(elem, "flyoverViewValues");

      if(node != null) {
         NodeList views = Tool.getChildNodesByTagName(node, "flyoverViewValue");
         String[] arr = new String[views.getLength()];

         for(int i = 0; i < arr.length; i++) {
            arr[i] = Tool.getValue(views.item(i));
         }

         flyoverValue.setDValue(arr);
      }

      parseColWidth(elem);
      parseRowHeight(elem);
      Element cnode = Tool.getChildNodeByTagName(elem, "sortInfo");

      if(cnode != null) {
         sinfo.parseXML(cnode);
      }
   }

   /**
    * Parse row height.
    */
   public void parseRowHeight(Element elem) {
      rowHeights.clear();
      NodeList nodes = Tool.getChildNodesByTagName(elem, "rowHeight");

      for(int i = 0; i < nodes.getLength(); i++) {
         Element node = (Element) nodes.item(i);
         String rowstr = Tool.getAttribute(node, "row");
         String nstr = Tool.getAttribute(node, "height");
         assert rowstr != null && nstr != null;
         setRowHeight(Integer.parseInt(rowstr), Double.parseDouble(nstr));
      }
   }

   /**
    * Parse column width.
    */
   public void parseColWidth(Element elem) throws Exception {
      resetColumnWidths();
      String colWidths = Tool.getAttribute(elem, "colWidths");

      if(colWidths != null) {
         String[] widths = colWidths.split(",");

         for(int i = 0; i < widths.length; i++) {
            if(!widths[i].isEmpty()) {
               setColumnWidthValue(i, Double.parseDouble(widths[i]));
            }
         }
      }
      else {
         NodeList nodes = Tool.getChildNodesByTagName(elem, "colWidth");

         for(int i = 0; i < nodes.getLength(); i++) {
            Element node = (Element) nodes.item(i);
            String colstr = Tool.getAttribute(node, "column");
            String nstr = Tool.getAttribute(node, "width");
            assert colstr != null && nstr != null;
            setColumnWidthValue(Integer.parseInt(colstr), Double.parseDouble(nstr));
         }
      }

      NodeList nodes = Tool.getChildNodesByTagName(elem, "rcolWidth");

      for(int i = 0; i < nodes.getLength(); i++) {
         Element node = (Element) nodes.item(i);
         String colstr = Tool.getAttribute(node, "column");
         String nstr = Tool.getAttribute(node, "width");
         assert colstr != null && nstr != null;
         setColumnWidth(Integer.parseInt(colstr), Double.parseDouble(nstr));
      }

      nodes = Tool.getChildNodesByTagName(elem, "colWidth2");

      for(int i = 0; i < nodes.getLength(); i++) {
         Element node = (Element) nodes.item(i);
         String nstr = Tool.getAttribute(node, "width");
         assert nstr != null;
         Element pathNode = Tool.getChildNodeByTagName(node, "tableDataPath");
         TableDataPath path = new TableDataPath();
         path.parseXML(pathNode);
         colWidths2.put(path, Double.parseDouble(nstr));
      }
   }

   /**
    * Clone this object.
    * @param shallow <tt>true</tt> to perform shallow clone,
    * <tt>false</tt> to perform deep clone.
    * @return the cloned object.
    */
   @Override
   @SuppressWarnings("unchecked")
   public TableDataVSAssemblyInfo clone(boolean shallow) {
      try {
         TableDataVSAssemblyInfo info = (TableDataVSAssemblyInfo) super.clone(shallow);

         if(!shallow) {
            info.rowHeights = new HashMap<>(rowHeights);
            info.colWidths = new HashMap<>(colWidths);
            info.rcolWidths = new HashMap<>(rcolWidths);
            info.colWidths2 = new HashMap<>(colWidths2);
            info.rcolWidths2 = new HashMap<>(rcolWidths2);
            info.sinfo = (SortInfo) sinfo.clone();
         }

         if(hyperlinkAttr != null) {
            info.hyperlinkAttr = hyperlinkAttr.clone();
         }

         if(highlightAttr != null) {
            info.highlightAttr = highlightAttr.clone();
         }

         if(shrinkValue != null) {
            info.shrinkValue = (DynamicValue2) shrinkValue.clone();
         }

         if(enableAdhocValue != null) {
            info.enableAdhocValue = (DynamicValue2) enableAdhocValue.clone();
         }

         if(styleValue != null) {
            info.styleValue = (DynamicValue) styleValue.clone();
         }

         if(titleInfo != null) {
            info.titleInfo = (TitleInfo) titleInfo.clone();
         }

         if(tipOptionValue != null) {
            info.tipOptionValue = (DynamicValue2) tipOptionValue.clone();
         }

         if(tipViewValue != null) {
            info.tipViewValue = (DynamicValue) tipViewValue.clone();
         }

         if(alphaValue != null) {
            info.alphaValue = (DynamicValue) alphaValue.clone();
         }

         if(flyClickValue != null) {
            info.flyClickValue = (DynamicValue) flyClickValue.clone();
         }

         if(flyoverValue != null) {
            info.flyoverValue = flyoverValue.clone();
         }

         if(explicitTableWidth != null) {
            info.explicitTableWidth = (DynamicValue2)explicitTableWidth.clone();
         }

         info.headerRowHeights = headerRowHeights.clone();
         info.dataRowHeight = dataRowHeight;

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone TableDataVSAssemblyInfo", ex);
      }

      return null;
   }

   /**
    * Copy the view part assembly info.
    * @param info the specified viewsheet assembly info.
    * @return <tt>true</tt> if changed, <tt>false</tt> otherwise.
    */
   @Override
   public boolean copyViewInfo(VSAssemblyInfo info, boolean deep) {
      boolean result = super.copyViewInfo(info, deep);
      TableDataVSAssemblyInfo cinfo = (TableDataVSAssemblyInfo) info;

      if(!Tool.equals(styleValue, cinfo.styleValue) ||
         !Tool.equals(styleValue.getRValue(), cinfo.styleValue.getRValue()))
      {
         styleValue = cinfo.styleValue;
         result = true;
      }

      if(!Tool.equals(shrinkValue , cinfo.shrinkValue) ||
         isShrink() !=  cinfo.isShrink())
      {
         shrinkValue = cinfo.shrinkValue;
         result = true;
      }

      if(!Tool.equals(enableAdhocValue, cinfo.enableAdhocValue) ||
         isEnableAdhoc() != cinfo.isEnableAdhoc())
      {
         enableAdhocValue = cinfo.enableAdhocValue;
         result = true;
      }

      if(!Tool.equals(titleInfo, cinfo.titleInfo)) {
         titleInfo = cinfo.titleInfo;
         result = true;
      }

      if(!Tool.equals(tipOptionValue, cinfo.tipOptionValue) ||
         !Tool.equals(getTipOption(), cinfo.getTipOption()))
      {
         tipOptionValue = cinfo.tipOptionValue;
         result = true;
      }

      if(!Tool.equals(tipViewValue, cinfo.tipViewValue) ||
         !Tool.equals(getTipView(), cinfo.getTipView()))
      {
         tipViewValue = cinfo.tipViewValue;
         result = true;
      }

      if(!Tool.equals(alphaValue, cinfo.alphaValue) ||
         !Tool.equals(getAlpha(), cinfo.getAlpha()))
      {
         alphaValue = cinfo.alphaValue;
         result = true;
      }

      if(!Tool.equals(getFlyOnClickValue(), cinfo.getFlyOnClickValue()) ||
         !Tool.equals(isFlyOnClick(), cinfo.isFlyOnClick()))
      {
         flyClickValue = cinfo.flyClickValue;
         result = true;
      }

      if(!Tool.equals(getFlyoverViewsValue(), cinfo.getFlyoverViewsValue())) {
         flyoverValue = cinfo.flyoverValue;
         result = true;
      }

      if(!Tool.equals(hyperlinkAttr, cinfo.hyperlinkAttr)) {
         this.hyperlinkAttr = cinfo.hyperlinkAttr;
         result = true;
      }

      if(!Tool.equals(highlightAttr, cinfo.highlightAttr)) {
         this.highlightAttr = cinfo.highlightAttr;
         result = true;
      }

      if(!Tool.equals(rowHeights, cinfo.rowHeights)) {
         this.rowHeights = cinfo.rowHeights;
         result = true;
      }

      if(!Tool.equals(colWidths, cinfo.colWidths)  || !Tool.equals(rcolWidths, cinfo.rcolWidths)) {
         this.colWidths = cinfo.colWidths;
         this.rcolWidths = cinfo.rcolWidths;
         result = true;
      }

      if(!Tool.equals(colWidths2, cinfo.colWidths2) || !Tool.equals(rcolWidths2, cinfo.rcolWidths2)) {
         this.colWidths2 = cinfo.colWidths2;
         this.rcolWidths2 = cinfo.rcolWidths2;
         result = true;
      }

      if(!Tool.equals(explicitTableWidth, cinfo.explicitTableWidth) ||
         isExplicitTableWidth() != cinfo.isExplicitTableWidth())
      {
         explicitTableWidth = cinfo.explicitTableWidth;
         result = true;
      }

      if(!ArrayUtils.isEquals(headerRowHeights, cinfo.headerRowHeights)) {
         this.headerRowHeights = cinfo.headerRowHeights;
         result = true;
      }

      if(!Tool.equals(dataRowHeight, cinfo.dataRowHeight)) {
         this.dataRowHeight = cinfo.dataRowHeight;
         result = true;
      }

     return result;
   }

   /**
    * Copy the input data part assembly info.
    * @param info the specified viewsheet assembly info.
    * @return new hint.
    */
   @Override
   protected int copyInputDataInfo(VSAssemblyInfo info, int hint) {
      hint = super.copyInputDataInfo(info, hint);
      TableDataVSAssemblyInfo tinfo = (TableDataVSAssemblyInfo) info;

      if(!Tool.equalsContent(sinfo, tinfo.sinfo)) {
         sinfo = tinfo.sinfo;
         hint |= VSAssembly.INPUT_DATA_CHANGED;
      }

      return hint;
   }

   /**
    * Get the view dynamic values.
    * @return the view dynamic values.
    */
   @Override
   public List<DynamicValue> getViewDynamicValues(boolean all) {
      List<DynamicValue> list = super.getViewDynamicValues(all);
      list.addAll(titleInfo.getViewDynamicValues());
      return list;
   }

   /**
    * Get the dynamic property values. Dynamic properties are properties that
    * can be a variable or a script.
    * @return the dynamic values.
    */
   @Override
   public List<DynamicValue> getHyperlinkDynamicValues() {
      List<DynamicValue> list = new ArrayList<>();

      if(hyperlinkAttr != null) {
         Enumeration<Hyperlink> links = hyperlinkAttr.getAllHyperlinks();

         while(links.hasMoreElements()) {
            Hyperlink link = links.nextElement();

            if(link != null && VSUtil.isScriptValue(link.getLinkValue())) {
               list.add(link.getDLink());
            }
         }
      }

      return list;
   }

   /**
    * Reset runtime values.
    */
   @Override
   public void resetRuntimeValues() {
      super.resetRuntimeValues();

      styleValue.setRValue(null);
      titleInfo.resetRuntimeValues();
      shrinkValue.setRValue(null);
      flyoverValue.setRValue(null);
      enableAdhocValue.setRValue(null);
      explicitTableWidth.setRValue(null);
      resetSizeInfo();
   }

   /**
    * Reset row heights and column widths.
    */
   public void resetSizeInfo() {
      rowHeights.clear();
      rcolWidths.clear();
   }

   /**
    * Reset column widths.
    */
   public void resetColumnWidths() {
      colWidths.clear();
      colWidths2.clear();
   }

   public void resetRColumnWidths() {
      rcolWidths.clear();
      rcolWidths2.clear();
   }

   /**
    * Reset row heights.
    */
   public void resetRowHeights() {
      headerRowHeights = new int[] { AssetUtil.defh };
      dataRowHeight = AssetUtil.defh;
      setTitleHeightValue(AssetUtil.defh);
   }

   /**
    * Set the default vsobject format.
    */
   @Override
   protected void setDefaultFormat(boolean border, boolean setFormat, boolean fill) {
      super.setDefaultFormat(border, setFormat, fill);

      getFormat().getDefaultFormat().setBackgroundValue("#ffffff");

      // CSSDictionary.getDictionary() is for viewsheet ONLY
      if(LibManager.getManager().getTableStyle(DEFAULT_STYLE) != null
         && !CSSDictionary.getDictionary().checkPresent("TableStyle"))
      {
         setTableStyleValue(DEFAULT_STYLE);
      }
   }

   /**
    * Get column count.
    */
   public int getColumnCount() {
      return 0;
   }

   /**
    * Return the max mode size of the table.
    */
   @Override
   public Dimension getMaxSize() {
      return maxSize;
   }

   /**
    * set the max mode size of the table.
    */
   public void setMaxSize(Dimension maxSize) {
      this.maxSize = maxSize;
   }

   /**
    * @return the z-index value when in max mode
    */
   public int getMaxModeZIndex() {
      return maxModeZIndex > 0 ? maxModeZIndex : getZIndex();
   }

   /**
    * Set the z-index value when in max mode
    */
   public void setMaxModeZIndex(int maxModeZIndex) {
      this.maxModeZIndex = maxModeZIndex;
   }

   /**
    * Check if explicit height from table lens should be kept when printing a viewsheet.
    */
   public boolean isKeepRowHeightOnPrint() {
      return keepRowHeightOnPrint;
   }

   /**
    * Set if explicit height from table lens should be kept when printing a viewsheet.
    */
   public void setKeepRowHeightOnPrint(boolean keepRowHeightOnPrint) {
      this.keepRowHeightOnPrint = keepRowHeightOnPrint;
   }

   private static final String DEFAULT_STYLE = "Default Style";

   private DynamicValue styleValue = new DynamicValue();
   private TitleInfo titleInfo = new TitleInfo("Table");
   private DynamicValue2 shrinkValue;
   private DynamicValue2 tipOptionValue;
   private DynamicValue2 enableAdhocValue;
   private DynamicValue tipViewValue = new DynamicValue();
   private DynamicValue alphaValue = new DynamicValue();
   private ClazzHolder<String[]> flyoverValue;
   private DynamicValue flyClickValue;
   private Dimension maxSize = null;
   private TableHyperlinkAttr hyperlinkAttr;
   private TableHighlightAttr highlightAttr;
   private Map<Integer, Double> colWidths;
   private Map<Integer, Double> rcolWidths;
   private Map<TableDataPath, Double> colWidths2 = new HashMap<>();
   private Map<TableDataPath, Double> rcolWidths2 = new HashMap<>();
   private Map<Integer, Double> rowHeights;
   private DynamicValue2 explicitTableWidth;
   private SortInfo sinfo;
   private int[] headerRowHeights;
   private int dataRowHeight = AssetUtil.defh;
   private int maxModeZIndex = -1;
   private boolean userHeaderRowHeight = false;
   private boolean userDataRowHeight = false;
   private boolean keepRowHeightOnPrint = false;

   private static final Logger LOG =
      LoggerFactory.getLogger(TableDataVSAssemblyInfo.class);
}
