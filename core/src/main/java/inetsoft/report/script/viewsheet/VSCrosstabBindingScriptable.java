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
package inetsoft.report.script.viewsheet;

import inetsoft.report.filter.SortOrder;
import inetsoft.report.script.PropertyScriptable;
import inetsoft.uql.XCondition;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.CrosstabVSAssemblyInfo;
import inetsoft.util.Tool;
import org.mozilla.javascript.Scriptable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * This class represents an CrosstabInfo in the Javascript environment.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class VSCrosstabBindingScriptable extends PropertyScriptable {
   /**
    * Initialize the object.
    */
   VSCrosstabBindingScriptable(CrosstabVSAScriptable script) {
      this.script = script;
      init();
   }

   /**
    * Initialize the object.
    */
   protected void init() {
      if(inited) {
         return;
      }

      inited = true;
      Class[] sparams = {String.class};
      Class[] ssparams = {String.class, String.class};
      Class[] siparams = {String.class, int.class};
      Class[] sbparams = {String.class, boolean.class};
      Class[] sibparams = {String.class, int.class, boolean.class};
      Class[] sssparams = {String.class, String.class, String.class};
      Class[] sisparams = {String.class, int.class, String.class};
      Class[] siiparams = {String.class, int.class, int.class};
      Class[] siisparams = {String.class, int.class, int.class, String.class};

      try {
         addProperty("rowFields", "getRowFields", "setRowFields",
            String[].class, getClass(), this);
         addProperty("colFields", "getColFields", "setColFields",
            String[].class, getClass(), this);
         addProperty("measureFields", "getMeasureFields", "setMeasureFields",
            String[].class, getClass(), this);

         addProperty("percentageMode", "getPercentageMode",
            "setPercentageMode", String.class, getClass(), this);
         addProperty("showRowTotal", "isShowRowTotal",
            "setShowRowTotal", boolean.class, getClass(), this);
         addProperty("showColumnTotal", "isShowColumnTotal",
            "setShowColumnTotal", boolean.class, getClass(), this);

         // aggregate
         addFunctionProperty(getClass(), "setFormula", sssparams);
         addFunctionProperty(getClass(), "getFormula", sparams);
         addFunctionProperty(getClass(), "setPercentageType", ssparams);
         addFunctionProperty(getClass(), "getPercentageType", sparams);
         addFunctionProperty(getClass(), "setColumnOrder", siisparams);
         addFunctionProperty(getClass(), "getColumnOrder", siparams);
         addFunctionProperty(getClass(), "setTimeSeries", sbparams);
         addFunctionProperty(getClass(), "isTimeSeries", sparams);

         // group
         addFunctionProperty(getClass(), "setTopN", siiparams);
         addFunctionProperty(getClass(), "getTopN", siparams);
         addFunctionProperty(getClass(), "setTopNSummaryCol", sisparams);
         addFunctionProperty(getClass(), "getTopNSummaryCol", siparams);
         addFunctionProperty(getClass(), "setTopNReverse", sibparams);
         addFunctionProperty(getClass(), "isTopNReverse", siparams);
         addFunctionProperty(getClass(), "setGroupOthers", sibparams);
         addFunctionProperty(getClass(), "isGroupOthers", siparams);
         addFunctionProperty(getClass(), "setGroupOrder", siiparams);
         addFunctionProperty(getClass(), "getGroupOrder", siparams);
         addFunctionProperty(getClass(), "setGroupTotal", sisparams);
         addFunctionProperty(getClass(), "getGroupTotal", siparams);
      }
      catch(Exception e) {
         LOG.error("Failed to register crosstab binding properties and functions", e);
      }
   }

   /**
    * Get the crosstable info of the crosstable element.
    */
   protected VSCrosstabInfo getCrosstabInfo() {
      return script.getCrosstabInfo();
   }

   /**
    * Get the assembly info of current crosstab.
    */
   private CrosstabVSAssemblyInfo getInfo() {
      return script.getInfo();
   }

   /**
    * Create a data ref from a string field name.
    * The string can contains field with "entity.attribute" format.
    */
   private DataRef createDataRef(String name) {
      int dot = name.lastIndexOf('.');

      return new ColumnRef(dot < 0 ? new AttributeRef(null, name) :
         new AttributeRef(name.substring(0, dot), name.substring(dot + 1)));
   }

   /**
    * Removes a indexed property from this object.
    */
   @Override
   public void delete(int i) {
   }

   /**
    * Get an array of property ids.
    */
   @Override
   public Object[] getIds() {
      init();
      return super.getIds();
   }

   /**
    * Object is passed to PropertyDescriptor in this class.
    */
   @Override
   protected Object getObject() {
      return null;
   }

   /**
    * Sets a named property in this object.
    */
   @Override
   public void put(String name, Scriptable start, Object value) {
      init();

      super.put(name, start, value);
   }

   /**
    * Indicates whether or not a named property is defined in an object.
    */
   @Override
   public boolean has(String name, Scriptable start) {
      init();
      return super.has(name, start);
   }

   /**
    * Get a named property from the object.
    */
   @Override
   public Object get(String name, Scriptable start) {
      init();
      return super.get(name, start);
   }

   /**
    * Get the type of a named property from the object.
    */
   @Override
   public Class getType(String name, Scriptable start) {
      init();
      return super.getType(name, start);
   }

   /**
    * Get the Row Fields.
    */
   public List<String> getRowFields() {
      VSCrosstabInfo vscrosstab = getCrosstabInfo();

      if(vscrosstab instanceof VSCrosstabInfo) {
         DataRef[] dataref = vscrosstab.getDesignRowHeaders();

         if(dataref != null) {
            List<String> refname = new ArrayList<>();

            for(DataRef df:dataref) {
               refname.add(df.getName());
            }

            return refname;
         }
      }

      return null;
   }

   /**
    * Set the Row Fields.
    */
   public void setRowFields(String[] rowFields) {
      if(getInfo() instanceof CrosstabVSAssemblyInfo) {
         VSCrosstabInfo vscrosstab = getCrosstabInfo();
         List<DataRef> list = new ArrayList<>();
         rowFields = fieldUnique(rowFields);

         for(String row:rowFields) {
            VSDimensionRef vref = new VSDimensionRef();
            vref.setDataRef(createDataRef(row));
            vref.setGroupColumnValue(row);

            if(vref != null) {
               list.add(vref);
            }
         }

         DataRef[] refs = list.toArray(new DataRef[0]);
         vscrosstab.setDesignRowHeaders(refs);
         getInfo().setVSCrosstabInfo(vscrosstab);
      }
   }

   /**
    * Get the Column Fields.
    */
   public List<String> getColFields() {
      VSCrosstabInfo vscrosstab = getCrosstabInfo();

      if(vscrosstab instanceof VSCrosstabInfo) {
         DataRef[] dataref = vscrosstab.getDesignColHeaders();

         if(dataref != null) {
            List<String> refname = new ArrayList<>();

            for(DataRef df:dataref) {
               refname.add(df.getName());
            }

            return refname;
         }
      }

      return null;
   }

   /**
    * Set the Column Fields.
    */
   public void setColFields(String[] colFields) {
      if(getInfo() instanceof CrosstabVSAssemblyInfo) {
         VSCrosstabInfo vscrosstab = getCrosstabInfo();
         List<DataRef> list = new ArrayList<>();
         colFields = fieldUnique(colFields);

         for(String col:colFields) {
            VSDimensionRef vref = new VSDimensionRef();
            vref.setDataRef(createDataRef(col));
            vref.setGroupColumnValue(col);

            if(vref != null) {
               list.add(vref);
            }
         }

         DataRef[] refs = list.toArray(new DataRef[0]);
         vscrosstab.setDesignColHeaders(refs);
         getInfo().setVSCrosstabInfo(vscrosstab);
      }
   }

   /**
    * Get the Measure Fields.
    */
   public List<String> getMeasureFields() {
      VSCrosstabInfo vscrosstab = getCrosstabInfo();

      if(vscrosstab instanceof VSCrosstabInfo) {
         DataRef[] dataref = vscrosstab.getDesignAggregates();

         if(dataref != null) {
            List<String> dfname = new ArrayList<>();

            for(DataRef df : dataref) {
               dfname.add(df.getName());
            }

            return dfname;
         }
      }

      return null;
   }

   /**
    * Set the Measure Fields.
    */
   public void setMeasureFields(String[] meaFields) {
      if(getInfo() instanceof CrosstabVSAssemblyInfo) {
         VSCrosstabInfo vscrosstab = getCrosstabInfo();
         List<DataRef> list = new ArrayList<>();
         meaFields = fieldUnique(meaFields);

         for(String aggr:meaFields) {
            VSAggregateRef vref = new VSAggregateRef();
            vref.setDataRef(createDataRef(aggr));
            vref.setColumnValue(aggr);

            if(vref != null) {
               list.add(vref);
            }
         }

         DataRef[] refs = list.toArray(new DataRef[0]);
         vscrosstab.setDesignAggregates(refs);
         getInfo().setVSCrosstabInfo(vscrosstab);
      }
   }

   /**
    * get percentage by row/column.
    */
   public String getPercentageMode() {
      return getCrosstabInfo() == null ? null :
         getCrosstabInfo().getPercentageByValue();
   }

   /**
    * set percentage by row/column
    * @param percent the value of percentage by.
    */
   public void setPercentageMode(String percent) {
      VSCrosstabInfo info = getCrosstabInfo();

      if(info != null){
         info.setPercentageByValue(percent);
      }
   }

   /**
    * get if show row total.
    */
   public boolean isShowRowTotal() {
      return getCrosstabInfo() != null && getCrosstabInfo().isRowTotalVisible();
   }

   /**
    * set show/hidden row total
    * @param rowFlag true show, false hidden.
    */
   public void setShowRowTotal(boolean rowFlag) {
      VSCrosstabInfo info = getCrosstabInfo();

      if(info != null){
         info.setRowTotalVisibleValue(String.valueOf(rowFlag));
      }
   }

   /**
    * get if show column total.
    */
   public boolean isShowColumnTotal() {
      return getCrosstabInfo() != null && getCrosstabInfo().isColTotalVisible();
   }

   /**
    * set show/hidden column total
    * @param rowFlag true show, false hidden.
    */
   public void setShowColumnTotal(boolean rowFlag) {
      VSCrosstabInfo info = getCrosstabInfo();

      if(info != null){
         info.setColTotalVisibleValue(String.valueOf(rowFlag));
      }
   }

   /**
    * Get the formula specified for an measure field.
    * @param field the specified column name
    * @return formula.
    */
   public String getFormula(String field) {
      String formula = null;
      DataRef[] aggrs = getCrosstabInfo().getDesignAggregates();

      for(DataRef aggr : aggrs) {
         if(Tool.equals(aggr.getName(), field)) {
            formula = ((VSAggregateRef) aggr).getFormulaValue();
         }
      }

      return formula;
   }

   /**
    * Set the summarization formula for an measure field. The formula strings
    * are defined as constants in StyleReport.
    * @param field the specified column name
    * @param formula the formula string
    * @param field2 the secondary column name
    */
   public void setFormula(String field, String formula, String field2) {
      DataRef[] aggrs = getCrosstabInfo().getDesignAggregates();

      for(DataRef aggr : aggrs) {
         if(Tool.equals(aggr.getName(), field)) {
            if(field2 != null) {
               ((VSAggregateRef) aggr).setSecondaryColumnValue(field2);
            }

            ((VSAggregateRef) aggr).setFormulaValue(formula);
         }
      }

      getCrosstabInfo().setDesignAggregates(aggrs);
   }

   /**
    * Get the percentage type for an measure field.
    * @param field the specified column name
    * @return the percentage type
    */
   public String getPercentageType(String field) {
      String type = null;
      DataRef[] aggrs = getCrosstabInfo().getDesignAggregates();

      for(DataRef aggr : aggrs) {
         if(Tool.equals(aggr.getName(), field)) {
            type = ((VSAggregateRef) aggr).getPercentageOptionValue();
         }
      }

      return type;
   }

   /**
    * Set the percentage type for an measure field.
    * @param field the specified column name
    * @param type the specifed type, aesthetic or binding
    */
   public void setPercentageType(String field, String type) {
      DataRef[] aggrs = getCrosstabInfo().getDesignAggregates();

      for(DataRef aggr : aggrs) {
         if(Tool.equals(aggr.getName(), field)) {
            ((VSAggregateRef) aggr).setPercentageOptionValue(type);
         }
      }

      getCrosstabInfo().setDesignAggregates(aggrs);
   }

   /**
    * Get Column/Row header data.
    * @param header sign of row or col
    * @return DataRef
    */
   private DataRef[] getHeadersRef(int header) {
      if(header == XConstants.COL_HEADER) {
         return getCrosstabInfo().getDesignColHeaders();
      }
      else if(header == XConstants.ROW_HEADER) {
         return getCrosstabInfo().getDesignRowHeaders();
      }
      else{
         return new DataRef[0];
      }
   }

   /**
    * set Column/Row header data.
    * @param header sign of row or col
    * @param dataRef row or col dataRef
    */
   private void setHeadersRef(int header, DataRef[] dataRef) {
      if(header == XConstants.COL_HEADER) {
         getCrosstabInfo().setDesignColHeaders(dataRef);
      }
      else if(header == XConstants.ROW_HEADER) {
         getCrosstabInfo().setDesignRowHeaders(dataRef);
      }
   }


   /**
    * Get the sort order for a row/column header.
    * @param field the specified column name
    * @param header row or col
    * @return the sort order
    */
   public int getColumnOrder(String field, int header) {
      int order = 1;
      DataRef[] dims = getHeadersRef(header);

      for(DataRef dim : dims) {
         if(Tool.equals(dim.getName(), field)) {
            order = ((VSDimensionRef) dim).getOrder();
         }
      }

      return order;
   }

   /**
    * Set the sort order for a row/col header.
    * @param field the specified column name
    * @param header row or col
    * @param type the field column type
    * @param sortBy the sort by column for sorting by value.
    */
    public void setColumnOrder(String field, int header,
               int type, String sortBy)
   {
      DataRef[] dims = getHeadersRef(header);

      for(DataRef dim : dims) {
         if(Tool.equals(dim.getName(), field)) {
            ((VSDimensionRef) dim).setOrder(type);
            ((VSDimensionRef) dim).setSortByCol(sortBy);
            ((VSDimensionRef) dim).setSortByColValue(sortBy);
         }
      }

      setHeadersRef(header, dims);
   }

   /**
    * Set whether to treat a date column as a time series.
    * @param field the specified dimension column name
    * @param timeSeries <code>true</code> if is a time series dimension
    */
   public void setTimeSeries(String field, boolean timeSeries) {
      DataRef[] dims = getHeaderRefs(field);

      if(dims.length > 0) {
         ((VSDimensionRef) dims[0]).setTimeSeries(timeSeries);

         if(timeSeries) {
            ((VSDimensionRef) dims[0]).setOrder(SortOrder.SORT_ASC);
            ((VSDimensionRef) dims[0]).setRankingOptionValue(XCondition.NONE + "");
            ((VSDimensionRef) dims[0]).setGroupOthersValue("false");
         }
      }
      else {
         LOG.warn("Dimension column not found: " + field);
      }
   }

   /**
    * Get whether to treat a date column as a time series.
    * @param field the specified dimension column name
    * @return <code>true</code> if is a time series dimension
    */
   public boolean isTimeSeries(String field) {
      DataRef[] dims = getHeaderRefs(field);

      if(dims.length > 0) {
         return ((VSDimensionRef) dims[0]).isTimeSeries();
      }

      return false;
   }

   /**
    * Return header array which using the target field.
    * @param  field the field name.
    */
   private DataRef[] getHeaderRefs(String field) {
      DataRef ref = createDataRef(field);
      List<DataRef> list = new ArrayList<>();

      for(DataRef dim : getHeadersRef(XConstants.ROW_HEADER)) {
         if(Tool.equals(((VSDimensionRef) dim).getDataRef(), ref)) {
            list.add(dim);
         }
      }

      for(DataRef dim : getHeadersRef(XConstants.COL_HEADER)) {
         if(Tool.equals(((VSDimensionRef) dim).getDataRef(), ref)) {
            list.add(dim);
         }
      }

      return list.toArray(new DataRef[list.size()]);
   }

   /**
    * Get top n for a row/col header.
    * @param field the specified column
    * @param header row or col
    * @return the top n
    */
   public int getTopN(String field, int header) {
      int topN = 0;
      DataRef[] dims = getHeadersRef(header);

      for(DataRef dim : dims) {
         if(Tool.equals(dim.getName(), field)) {
            topN = Integer.parseInt(((VSDimensionRef) dim).getRankingNValue());
         }
      }

      return topN;
   }

   /**
    * Set top n for a row/col header.
    * @param field the specified column
    * @param header row or col
    * @param topN the top n
    */
   public void setTopN(String field, int header, int topN) {
      DataRef[] dims = getHeadersRef(header);

      for(DataRef dim : dims) {
         if(Tool.equals(dim.getName(), field)) {
            ((VSDimensionRef) dim).setRankingNValue(topN + "");
         }
      }

      setHeadersRef(header, dims);
   }

   /**
    * Get topn summary column for a row/col header.
    * @param field the specified column
    * @param header row or col
    * @return the summary column if exists, <code>null</code> otherwise
    */
   public String getTopNSummaryCol(String field, int header) {
      String sumfield = null;
      DataRef[] dims = getHeadersRef(header);

      for(DataRef dim : dims) {
         if(Tool.equals(dim.getName(), field)) {
            sumfield = ((VSDimensionRef) dim).getRankingColValue();
         }
      }

      return sumfield;
   }

   /**
    * Set topn summary column for a row/col header.
    * @param field the specified column
    * @param header row or col
    * @param sumfield the summary column
    */
   public void setTopNSummaryCol(String field, int header, String sumfield) {
      DataRef[] dims = getHeadersRef(header);

      for(DataRef dim : dims) {
         if(Tool.equals(dim.getName(), field)) {
            ((VSDimensionRef) dim).setRankingColValue(sumfield);
         }
      }

      setHeadersRef(header, dims);
   }

   /**
    * Get topn reverse for a row/col header.
    * @param field the specified column
    * @param header row or col
    * @return <code>true</code> if reverse, <code>false</code> means not found
    * or not reverse
    */
   public boolean isTopNReverse(String field, int header) {
      boolean reserve = false;
      DataRef[] dims = getHeadersRef(header);

      for(DataRef dim : dims) {
         if(Tool.equals(dim.getName(), field)) {
            reserve = ((VSDimensionRef) dim).getRankingOptionValue().
               equals("" + XCondition.BOTTOM_N);
         }
      }

      return reserve;
   }

   /**
    * Set topn reverse for a row/col header.
    * @param field the specified column
    * @param header row or col
    * @param reserve <code>true</code> if reverse
    */
   public void setTopNReverse(String field, int header, boolean reserve) {
      DataRef[] dims = getHeadersRef(header);

      for(DataRef dim : dims) {
         if(Tool.equals(dim.getName(), field)) {
            int op = reserve ? XCondition.BOTTOM_N : XCondition.TOP_N;
            ((VSDimensionRef) dim).setRankingOptionValue(op + "");
         }
      }

      setHeadersRef(header, dims);
   }

   /**
    * Get the group others value.
    * @param field the specified column
    * @param header row or col
    * @return others <code>true</code> if group others
    */
   public boolean isGroupOthers(String field, int header) {
      boolean others = false;
      DataRef[] dims = getHeadersRef(header);

      for(DataRef dim : dims) {
         if(Tool.equals(dim.getName(), field)) {
            others = Boolean.parseBoolean(
               ((VSDimensionRef) dim).getGroupOthersValue());
         }
      }

      return others;
   }

   /**
    * Set the group others value.
    * @param field the specified column
    * @param header row or col
    * @param others <code>true</code> if group others
    */
   public void setGroupOthers(String field, int header, boolean others) {
      DataRef[] dims = getHeadersRef(header);

      for(DataRef dim : dims) {
         if(Tool.equals(dim.getName(), field)) {
            ((VSDimensionRef) dim).setGroupOthersValue(String.valueOf(others));
         }
      }

      setHeadersRef(header, dims);
   }

   /**
    * Get the group order of a specified row/col header.
    * @param field the specified column
    * @param header row or col
    * @return order the group order
    */
   public int getGroupOrder(String field, int header) {
      int order = DateRangeRef.YEAR_INTERVAL;
      DataRef[] dims = getHeadersRef(header);

      for(DataRef dim : dims) {
         if(Tool.equals(dim.getName(), field)) {
            order = Integer.parseInt(((VSDimensionRef) dim).getDateLevelValue());
         }
      }

      return order;
   }

   /**
    * Set the group order of a specified row/col header.
    * @param field the specified column
    * @param header row or col
    * @param order the group order
    */
   public void setGroupOrder(String field, int header, int order) {
      DataRef[] dims = getHeadersRef(header);

      for(DataRef dim : dims) {
         if(Tool.equals(dim.getName(), field)) {
            ((VSDimensionRef) dim).setDateLevelValue(order + "");
         }
      }

      setHeadersRef(header, dims);
   }

   /**
    * Get the group total for specified row/col header.
    * @param field the specified column
    * @param header row or col
    * @return total the group total
    */
   public boolean getGroupTotal(String field, int header) {
      boolean total = false;
      DataRef[] dims = getHeadersRef(header);

      for(DataRef dim : dims) {
         if(Tool.equals(dim.getName(), field)) {
            total = Boolean.parseBoolean(
               ((VSDimensionRef) dim).getSubTotalVisibleValue());
         }
      }

      return total;
   }

   /**
    * Set the group total for specified row/col header.
    * @param field the specified column
    * @param header row or col
    * @param total the group total
    */
   public void setGroupTotal(String field, int header, String total) {
      DataRef[] dims = getHeadersRef(header);

      for(DataRef dim : dims) {
         if(Tool.equals(dim.getName(), field)) {
            String flag = total.equalsIgnoreCase("show") ? "true" :
               total.equalsIgnoreCase("hide") ? "false" : total;

            ((VSDimensionRef) dim).setSubTotalVisibleValue(flag);
         }
      }

      setHeadersRef(header, dims);
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "VSCrosstabInfo";
   }

   /**
    * made field unique.
    */
   private String[] fieldUnique(String[] field) {
      if(field == null) {
         return null;
      }
      else {
         List<String> list = new ArrayList<>();

         for(String item : field) {
            if(!list.contains(item)) {
               list.add(item);
            }
         }

         return list.toArray(new String[list.size()]);
      }
   }

   private CrosstabVSAScriptable script;
   private boolean inited = false;
   private static final Logger LOG =
      LoggerFactory.getLogger(VSCrosstabBindingScriptable.class);
}
