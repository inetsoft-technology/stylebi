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
package inetsoft.report.composition.execution;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;

/**
 * TableMetaDataKey as the key identifies a TableMetaData.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class TableMetaDataKey implements Cloneable, Serializable {
   /**
    * Create a TableMetaDataKey.
    * @param tname the specified table assembly name in this worksheet.
    * @param vs the specified viewsheet.
    * @param mode viewsheet mode.
    * @return the created TableMetaDataKey if any, <tt>null</tt> otherwise.
    */
   public static TableMetaDataKey createKey(String tname, Viewsheet vs, int mode) {
      Worksheet ws = vs.getBaseWorksheet();

      if(ws == null) {
         return null;
      }

      TableAssembly table = VSAQuery.getVSTableAssembly(tname, false, vs, ws);

      if(table == null) {
         return null;
      }

      String vname = vs.getAbsoluteName();
      ColumnSelection selection = table.getColumnSelection(true);
      TableAssembly stable = (TableAssembly) ws.getAssembly(VSAssembly.SELECTION + tname);
      ColumnSelection oselection = stable == null ? null : stable.getColumnSelection(false);
      selection = VSUtil.getVSColumnSelection(selection);
      VSUtil.appendCalcFields(selection, tname, vs, true);
      Set<DataRef> columns = new HashSet<>();
      List<String> selections = new ArrayList<>();
      List<AggregateRef> aggrs = new ArrayList<>();
      boolean colChanged = false;
      final Set<SelectionVSAssembly> dependingSelectionAssemblies =
         SelectionVSUtil.getDependingSelectionAssemblies(vs, tname);

      for(SelectionVSAssembly sassembly : dependingSelectionAssemblies) {
         SelectionVSAssemblyInfo info = (SelectionVSAssemblyInfo) sassembly.getInfo();

         DataRef[] refs = sassembly.getDataRefs();
         selections.add(sassembly.getName());

         for(DataRef dataRef : refs) {
            ColumnRef ref = VSUtil.getVSColumnRef((ColumnRef) dataRef);
            String name = ((ColumnRef) dataRef).getRealName();

            if(ref != null && !selection.containsAttribute(ref)) {
               VSUtil.renameDataRef(ref, ref.getDataRef().getName());
            }

            if(ref != null && !selection.containsAttribute(ref)) {
               if(name.startsWith("Column [")) {
                  name = "";
               }

               VSUtil.renameDataRef(ref, name);
            }

            if(ref != null && selection.containsAttribute(ref)) {
               DataRef base = selection.findAttribute(ref);

               if(stable != null) {
                  colChanged = addCalcRefs(base, oselection, selection) || colChanged;
               }

               columns.add(dataRef);
            }
         }

         if(info instanceof SelectionBaseVSAssemblyInfo) {
            SelectionBaseVSAssemblyInfo binfo = (SelectionBaseVSAssemblyInfo) info;
            String measure = binfo.getMeasure();
            String formula = binfo.getFormula();

            if(measure != null && formula != null && measure.length() > 0) {
               DataRef ref = selection.getAttribute(measure, true);
               AggregateFormula fobj = AggregateFormula.getFormula(formula);

               if(ref != null && fobj != null) {
                  aggrs.add(new AggregateRef(ref, fobj));
               }
            }
         }
      }

      if(colChanged) {
         stable.resetColumnSelection();
      }

      if(columns.size() == 0) {
         return null;
      }

      return new TableMetaDataKey(tname, columns, mode, vname, selections, aggrs);
   }

   /**
    * Add refered calculate refs.
    */
   private static boolean addCalcRefs(DataRef base, ColumnSelection oselection,
                                      ColumnSelection selection)
   {
      if(oselection.containsAttribute(base)) {
         return false;
      }

      if(!(base instanceof CalculateRef)) {
         return false;
      }

      CalculateRef cref = (CalculateRef) base;

      if(!cref.isBaseOnDetail()) {
         return false;
      }

      oselection.addAttribute(cref);
      Enumeration children = cref.getExpAttributes();

      while(children.hasMoreElements()) {
         AttributeRef aref = (AttributeRef) children.nextElement();
         DataRef oref = selection.getAttribute(aref.getName());

         if(oref != null) {
            addCalcRefs(oref, oselection, selection);
         }
      }

      return true;
   }

   /**
    * Create a <tt>TableMetaDataKey</tt>.
    * @param table the specified table.
    * @param columns the specified columns.
    * @param mode viewsheet mode.
    */
   private TableMetaDataKey(String table, Set<DataRef> columns, int mode,
                            String vname, List<String> selections,
                            List<AggregateRef> aggrs)
   {
      super();

      this.table = table;
      this.columns = columns;
      this.mode = mode;
      this.vname = vname;
      this.selections = selections;
      this.aggrs = aggrs;
   }

   /**
    * Get the table.
    * @return the table of this TableMetaDataKey.
    */
   public String getTable() {
      return table;
   }

   /**
    * Get the columns.
    * @return the columns of this TableMetaDataKey.
    */
   public Set<DataRef> getColumns() {
      return columns;
   }

   /**
    * Get the aggregates used in selection measures.
    */
   public List<AggregateRef> getAggregates() {
      return aggrs;
   }

   /**
    * Get the column headers.
    * @return the headers of the columns.
    */
   public String[] getHeaders() {
      String[] arr = new String[columns.size()];
      int counter = 0;

      for(DataRef ref : columns) {
         arr[counter++] = ref.getName();

         if(ref instanceof ColumnRef) {
            arr[counter - 1] = ((ColumnRef) ref).getRealName();
         }
      }

      return arr;
   }

   /**
    * Clone this object.
    * @return the cloned the object.
    */
   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ex) {
         LOG.error("Failed to clone table meta data key", ex);
      }

      return null;
   }

   /**
    * Get the hash code value.
    * @return the hash code value.
    */
   public int hashCode() {
      return table.hashCode() + columns.hashCode() + mode + aggrs.hashCode();
   }

   /**
    * Check if equals another object.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals, <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof TableMetaDataKey)) {
         return false;
      }

      TableMetaDataKey key2 = (TableMetaDataKey) obj;

      if(aggrs.size() != key2.aggrs.size()) {
         return false;
      }

      for(int i = 0; i < aggrs.size(); i++) {
         if(!aggrs.get(i).equalsAggregate(key2.aggrs.get(i))) {
            return false;
         }
      }

      return table.equals(key2.table) && columns.equals(key2.columns) &&
         mode == key2.mode;
   }

   /**
    * Get the string representation.
    * @return the string representation.
    */
   public String toString() {
      return "TableMetaDataKey[" + table + "][" + columns + ";" + aggrs + "]";
   }

   /**
    * Get the name.
    */
   public String getName() {
      StringBuilder sb = new StringBuilder();
      sb.append("__tableMetaData__");

      for(int i = 0; i < selections.size(); i++) {
         if(i > 0) {
            sb.append(',');
         }

         sb.append(selections.get(i));
      }

      return sb.toString();
   }

   /**
    * Get the absolute name.
    */
   public String getAbsoluteName() {
      StringBuilder sb = new StringBuilder();

      if(vname != null) {
         sb.append(vname);
         sb.append(".");
      }

      sb.append(getName());
      return sb.toString();
   }

   private String table;
   private Set<DataRef> columns;
   private int mode;
   private List<String> selections;
   private String vname;
   private List<AggregateRef> aggrs;

   private static final Logger LOG = LoggerFactory.getLogger(TableMetaDataKey.class);
}
