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

import inetsoft.report.TableDataPath;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.Tool;
import inetsoft.util.css.CSSConstants;
import inetsoft.util.xml.VersionControlComparators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.*;
import java.io.PrintWriter;
import java.util.List;
import java.util.*;

/**
 * CrosstabVSAssemblyInfo, the assembly info of a crosstab assembly.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class CrosstabVSAssemblyInfo extends CrossBaseVSAssemblyInfo
   implements DateCompareAbleAssemblyInfo
{
   /**
    * Constructor.
    */
   public CrosstabVSAssemblyInfo() {
      super();
      setPixelSize(new Dimension(400, 240));
      setEnableAdhocValue(true);
   }

   /**
    * Initialize the default format.
    */
   @Override
   public void initDefaultFormat() {
      setDefaultFormat(true, true); // crosstab with border
      // @by billh, do not set default background, otherwise there is no chance
      // to apply table style
      // getFormat().getDefaultFormat().setBackgroundValue("0xffffff");
      // getFormat().getDefaultFormat().setAlpha(30);
   }

   /**
    * Get the margin setting.
    * @return the margin setting of this assembly info.
    */
   public Insets getMargin() {
      return margin;
   }

   /**
    * Set the margin setting to this assembly info.
    * @param margin the specified margin setting.
    */
   public void setMargin(Insets margin) {
      this.margin = margin;
   }

   /**
    * Get the number of columns this crosstab occupies on grid.
    */
   @Override
   public int getColumnCount() {
      return ncol;
   }

   /**
    * Set the number of columns this crosstab occupies on grid.
    */
   public void setColumnCount(int ncol) {
      this.ncol = ncol;
   }

   /**
    * Get binding cube type if any.
    * @return binding cube type.
    */
   public String getCubeType() {
      return cubeType;
   }

   /**
    * Set binding cube type.
    * @param cubeType binding cube type.
    */
   public void setCubeType(String cubeType) {
      this.cubeType = cubeType;
   }

   /**
    * Clone this object.
    * @param shallow <tt>true</tt> to perform shallow clone,
    * <tt>false</tt> to perform deep clone.
    * @return the cloned object.
    */
   @Override
   public CrosstabVSAssemblyInfo clone(boolean shallow) {
      try {
         CrosstabVSAssemblyInfo info = (CrosstabVSAssemblyInfo) super.clone(shallow);

         if(!shallow) {
            if(margin != null) {
               info.margin = (Insets) margin.clone();
            }

            info.hiddenColumns = new HashSet<>(hiddenColumns);
            info.drillFilter = drillFilter.clone();
         }

         if(dateComparison != null) {
            info.dateComparison = dateComparison.clone();
         }

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone CrosstabVSAssemblyInfo", ex);
      }

      return null;
   }

   /**
    * Write attributes.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      writer.print(" colCount=\"" + ncol + "\"");

      if(cubeType != null) {
         writer.print(" cubeType=\"" + cubeType + "\"");
      }

      // for web
      if("true".equals(SreeEnv.getProperty("vs.crosstab.sortonheader"))) {
         writer.print(" sortOnHeader=\"true\"");
      }

      if("true".equals(SreeEnv.getProperty("sort.crosstab.aggregate"))) {
         writer.print(" sortAggregate=\"true\"");
      }

      if("true".equals(SreeEnv.getProperty("sort.crosstab.dimension"))) {
         writer.print(" sortDimension=\"true\"");
      }
   }

   /**
    * Parse attributes.
    */
   @Override
   protected void parseAttributes(Element element) {
      super.parseAttributes(element);
      String prop;

      if((prop = Tool.getAttribute(element, "colCount")) != null) {
         ncol = Integer.parseInt(prop);
      }

      cubeType = Tool.getAttribute(element, "cubeType");
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(margin != null) {
         writer.print("<margin");
         writer.print(" left=\"" + margin.left + "\"");
         writer.print(" right=\"" + margin.right + "\"");
         writer.print(" top=\"" + margin.top + "\"");
         writer.print(" bottom=\"" + margin.bottom + "\"");
         writer.println(" />");
      }

      drillFilter.writeXML(writer);
      writeHiddenColumns(writer);

      writer.print("<dateComparison comparisonShareFrom=\""+
              (comparisonShareFrom == null ? "" : comparisonShareFrom) + "\">");
      DateComparisonInfo dComparison = getDateComparisonInfo();

      if(dComparison != null) {
         dComparison.writeXML(writer);
      }

      writer.print("</dateComparison>");
   }

   public void writeHiddenColumns(PrintWriter writer) {
      if(hiddenColumns != null) {
         writer.println("<hiddenColumns>");
         VersionControlComparators.sortStringSets(hiddenColumns)
            .forEach(col -> writer.println("<hiddenColumn><![CDATA[" + col + "]]></hiddenColumn>"));
         writer.println("</hiddenColumns>");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element anode = Tool.getChildNodeByTagName(elem, "margin");

      if(anode != null) {
         int left = Integer.parseInt(Tool.getAttribute(anode, "left"));
         int right = Integer.parseInt(Tool.getAttribute(anode, "right"));
         int top = Integer.parseInt(Tool.getAttribute(anode, "top"));
         int bottom = Integer.parseInt(Tool.getAttribute(anode, "bottom"));

         margin = new Insets(top, left, bottom, right);
      }

      drillFilter.parseXML(elem);
      parseHiddenColumns(elem);

      Element dateComparisonNode = Tool.getChildNodeByTagName(elem, "dateComparison");

      if(dateComparisonNode != null) {
         comparisonShareFrom = Tool.getAttribute(dateComparisonNode, "comparisonShareFrom");
         DateComparisonInfo dcomparison = new DateComparisonInfo();
         dcomparison.parseXML(dateComparisonNode);

         if(dcomparison.getPeriods() != null && dcomparison.getInterval() != null) {
            dateComparison = dcomparison;
         }
      }
   }

   /**
    * Update the info to fill in runtime value.
    * @param vs the specified viewsheet.
    * @param columns the specified column selection.
    */
   @Override
   public void update(Viewsheet vs, ColumnSelection columns) throws Exception {
      super.update(vs, columns);
   }

   public Map<String, String> getFullLevelNameMap() {
      Map<String, String> map = new HashMap<>();
      collectFullLevelNameInfo(map, getVSCrosstabInfo().getRuntimeRowHeaders());
      collectFullLevelNameInfo(map, getVSCrosstabInfo().getRuntimeColHeaders());
      return map;
   }

   private void collectFullLevelNameInfo(Map<String, String> map, DataRef[] headers) {
      for(int i = 0; headers != null && i < headers.length; i++) {
         if(!(headers[i] instanceof VSDimensionRef)) {
            continue;
         }

         VSDimensionRef dim = (VSDimensionRef) headers[i];

         if(dim.isFullWeekDateLevel()) {
            map.put(DateComparisonUtil.getNormalLevelFullName(dim), dim.getFullName());
         }
      }
   }

   public void parseHiddenColumns(Element elem) {
      Element node = Tool.getChildNodeByTagName(elem, "hiddenColumns");

      if(node != null) {
         NodeList cols = Tool.getChildNodesByTagName(node, "hiddenColumn");
         hiddenColumns.clear();

         for(int i = 0; i < cols.getLength(); i++) {
            hiddenColumns.add(Tool.getValue(cols.item(i)));
         }
      }
   }

   /**
    * Copy the view part assembly info.
    * @param info the specified viewsheet assembly info.
    * @return <tt>true</tt> if changed, <tt>false</tt> otherwise.
    */
   @Override
   public boolean copyViewInfo(VSAssemblyInfo info, boolean deep) {
      boolean result = super.copyViewInfo(info, deep);
      CrosstabVSAssemblyInfo cinfo = (CrosstabVSAssemblyInfo) info;

      if(!Tool.equals(margin, cinfo.margin)) {
         margin = cinfo.margin;
         result = true;
      }

      if(ncol != cinfo.ncol) {
         ncol = cinfo.ncol;
         result = true;
      }

      return result;
   }

   @Override
   protected int copyInputDataInfo(VSAssemblyInfo info, int hint) {
      hint = super.copyInputDataInfo(info, hint);

      CrosstabVSAssemblyInfo cinfo = (CrosstabVSAssemblyInfo) info;

      if(!Tool.equals(drillFilter, cinfo.getDrillFilterInfo())) {
         drillFilter = cinfo.getDrillFilterInfo().clone();
         hint |= VSAssembly.BINDING_CHANGED;
      }

      if(!Tool.equals(dateComparison, cinfo.getDateComparisonInfo())) {
         dateComparison = cinfo.getDateComparisonInfo() != null ?
            cinfo.getDateComparisonInfo().clone() : null;
         hint |= VSAssembly.INPUT_DATA_CHANGED;
      }

      if(!Tool.equals(comparisonShareFrom, cinfo.getComparisonShareFrom())) {
         comparisonShareFrom = cinfo.getComparisonShareFrom();
         hint |= VSAssembly.INPUT_DATA_CHANGED;
      }

      return hint;
   }

   /**
    * @return if table has hidden column
    */
   public boolean hasHiddenColumn() {
      return hiddenColumns.size() > 0;
   }

   @Override
   public boolean isUserDefinedWidth() {
      return super.isUserDefinedWidth() || hasHiddenColumn();
   }

   public double getColumnWidth2(int col, XTable lens) {
      if(isColumnHidden(col, lens)) {
         return 0;
      }

      return super.getColumnWidth2(col, lens);
   }

   public boolean isColumnHidden(int col, XTable lens) {
      return !hiddenColumns.isEmpty() &&
         (hiddenColumns.contains(getColKey(lens, col, true)) ||
          // initial implementation in 13.3 only used header values to identify a column,
          // which may not be unique. should use col path in addition to column header.
          // this is corrected in later build of 13.3. (49128)
          hiddenColumns.contains(getColKey(lens, col, false)));
   }

   /**
    * Add hidden column index.
    */
   public void addHiddenColumn(int columnIndex, XTable lens) {
      hiddenColumns.add(getColKey(lens, columnIndex, true));
   }

   /**
    * clear hidden columns and remove colwidth in rcolWidths
    */
   public void clearHiddenColumns() {
      hiddenColumns.clear();
   }

   public ConditionList getDrillFilterConditionList(String field) {
      return drillFilter.getDrillFilterConditionList(field);
   }

   public void setDrillFilterConditionList(String field, ConditionList drillCondition) {
      drillFilter.setDrillFilterConditionList(field, drillCondition);
   }

   public DrillFilterInfo getDrillFilterInfo() {
      return drillFilter;
   }

   public void setDrillFilterInfo(DrillFilterInfo info) {
      drillFilter = info;
   }

   /**
    * Get the object css default type.
    */
   @Override
   public String getObjCSSType() {
      return CSSConstants.CROSSTAB;
   }

   @Override
   public List<DynamicValue> getDynamicValues() {
      List<DynamicValue> list = super.getDynamicValues();
      DateComparisonInfo rtDateComparison = DateComparisonUtil.getDateComparison(this, vs);

      if(rtDateComparison != null) {
         list.addAll(rtDateComparison.getDynamicValues());
      }

      return list;
   }

   @Override
   public void resetRuntimeValues() {
      super.resetRuntimeValues();
      setDateComparisonRef(null);
   }

   @Override
   public DateComparisonInfo getDateComparisonInfo() {
      return dateComparison;
   }

   @Override
   public void setDateComparisonInfo(DateComparisonInfo info) {
      dateComparison = info;
   }

   public void resetRuntimeDateComparisonInfo() {
      comparisonShareFrom = null;
      setDateComparisonRef(null);
      setDateComparisonInfo(null);

      if(getVSCrosstabInfo() != null) {
         getVSCrosstabInfo().setRuntimeDateComparisonRefs(null);
      }
   }

   @Override
   public String getComparisonShareFrom() {
      return comparisonShareFrom;
   }

   @Override
   public void setComparisonShareFrom(String assemblyFullName) {
      comparisonShareFrom = assemblyFullName;
   }

   @Override
   public void setDateComparisonRef(VSDataRef ref) {
      if(getVSCrosstabInfo() != null) {
         getVSCrosstabInfo().setDateComparisonRef(ref);
      }
   }

   @Override
   public VSDataRef getDateComparisonRef() {
      if(getVSCrosstabInfo() == null) {
         return null;
      }

      return getVSCrosstabInfo().getDateComparisonRef();
   }

   /**
    * Return fields which are temporarily generated for expand the data as dc required,
    * and this part of temp fields also used to date compare(other temp fields are not used
    * in date compare, just used to expand the data).
    */
   @Override
   public XDimensionRef[] getTempDateGroupRef() {
      DateComparisonInfo dinfo =
         DateComparisonUtil.getDateComparison(this, getViewsheet());
      return dinfo == null ? null : dinfo.getTempDateGroupRef(this);
   }

   @Override
   public boolean supportDateComparison() {
      return DateComparisonUtil.supportDateComparison(getVSCrosstabInfo(), true);
   }

   @Override
   public DataRef getDCBIndingRef(String refName) {
      if(DateComparisonUtil.getDateComparison(this, vs) == null || !supportDateComparison()) {
         return null;
      }

      VSCrosstabInfo crosstabInfo = getVSCrosstabInfo();

      if(crosstabInfo == null) {
         return null;
      }

      DataRef[] refs = crosstabInfo.getRuntimeDateComparisonRefs();

      if(refs == null) {
         return null;
      }

      for(DataRef ref : refs) {
         if(ref == null) {
            continue;
         }

         if(ref instanceof VSDimensionRef &&
            Tool.equals(((VSDimensionRef) ref).getFullName(), refName))
         {
            return ref;
         }
         else if(ref instanceof VSAggregateRef &&
            Tool.equals(VSUtil.getAggregateField(((VSDataRef) ref).getFullName(), ref), refName))
         {
            return ref;
         }
      }

      return null;
   }

   // get a string to uniquely identify a column
   private String getColKey(XTable lens, int col, boolean full) {
      TableDataPath path = lens.getDescriptor().getColDataPath(col);
      StringBuilder str = full ? new StringBuilder(path.toString()) : new StringBuilder();

      for(int i = 0; lens.moreRows(i) && i < lens.getHeaderRowCount(); i++) {
         if(i > 0) {
            str.append(";");
         }

         Object id = lens.getObject(i, col);
         str.append(id);
      }

      return str.toString();
   }

   private Insets margin = new Insets(0, 0, 0, 0);
   private int ncol = 0;
   private String cubeType = null;
   private Set<String> hiddenColumns = new HashSet<>();
   private DrillFilterInfo drillFilter = new DrillFilterInfo();
   private String comparisonShareFrom;
   private DateComparisonInfo dateComparison;

   private static final Logger LOG =
      LoggerFactory.getLogger(CrosstabVSAssemblyInfo.class);
}
