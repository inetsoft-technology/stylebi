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

import inetsoft.report.TableLens;
import inetsoft.report.internal.Util;
import inetsoft.report.lens.TextSizeLimitTableLens;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.OutputVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Catalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.text.Format;
import java.util.ArrayList;
import java.util.List;

/**
 * OutputVSAQuery, the output viewsheet assembly query.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class OutputVSAQuery extends VSAQuery {
   /**
    * Create an output viewsheet assembly query.
    * @param box the specified viewsheet sandbox.
    * @param vname the specified viewsheet assembly to be processed.
    */
   public OutputVSAQuery(ViewsheetSandbox box, String vname) {
      super(box, vname);
   }

   /**
    * Get the table assembly that contains binding info.
    * @param analysis <tt>true</tt> if is for analysis, <tt>false</tt> for
    * runtime.
    */
   private TableAssembly getTableAssembly(boolean analysis) throws Exception {
      OutputVSAssembly assembly = (OutputVSAssembly) getAssembly();
      ScalarBindingInfo binding = assembly.getScalarBindingInfo();
      Worksheet ws = getWorksheet();

      if(ws == null || binding == null) {
         return null;
      }

      String tname = binding.getTableName();

      if(tname == null || tname.length() == 0) {
         return null;
      }

      ColumnRef column0 = (ColumnRef) binding.getColumn();
      ColumnRef column = VSUtil.getVSColumnRef(column0);
      TableAssembly tassembly = getVSTableAssembly(tname);
      validateCalculateRef(ws, tname);
      tassembly = box.getBoundTable(tassembly, vname, isDetail());

      if(column == null && VSUtil.isDynamicValue(binding.getColumnValue()) || tassembly == null) {
         return tassembly;
      }

      if(column == null) {
         String colval = binding.getColumnValue();
         LOG.warn("Column not found: " + colval);
         return tassembly;
      }

      ColumnRef column2 = (ColumnRef) binding.getSecondaryColumn();
      column2 = VSUtil.getVSColumnRef(column2);

      AggregateFormula form = binding.getAggregateFormula();
      ChartVSAssembly chart = analysis ? null : box.getBrushingChart(vname);
      normalizeTable(tassembly);
      ws.addAssembly(tassembly);

      if(AssetUtil.isCubeTable(tassembly) && !isWorksheetCube()) {
         appendAggCalcField(tassembly, tname);
      }

      boolean isAggCalc = VSUtil.isAggregateCalc(column);
      CalculateRef cref = isAggCalc ? (CalculateRef) column : null;

      // @by stephenwebster, Fix bug1398097704325
      // Avoid adding an AggregateInfo with a None formula.
      // This will cause the data to come back unsummarized, from which
      // the last row can be selected as documented.  If we do not do this
      // then the results in the summarized table will be nulls.
      if(form != null && (!form.getName().equals("None") || isAggCalc)) {
         ColumnSelection columns = tassembly.getColumnSelection();
         column = (ColumnRef) columns.getAttribute(column.getName());

         if(column2 != null) {
            ColumnRef ref = (ColumnRef) columns.getAttribute(column2.getName());
            column2 = ref == null ? column2 : ref;
         }

         AggregateInfo info = new AggregateInfo();

         if(isAggCalc) {
            cref = (CalculateRef) cref.clone();
            ExpressionRef eref = (ExpressionRef) cref.getDataRef();
            List<String> matchNames = new ArrayList<>();
            String expression = eref.getExpression();
            Viewsheet vs = getViewsheet();
            // add the calculate field sub aggregate ref to base table
            List<AggregateRef> aggs = VSUtil.findAggregate(vs, tname, matchNames, expression);
            String newex = expression;

            for(int i = 0; i < aggs.size(); i++) {
               AggregateRef aref = VSUtil.createAliasAgg(aggs.get(i), true);
               ColumnRef colref = (ColumnRef) aref.getDataRef();
               columns.addAttribute(colref);
               fixUserAggregateRef(aref, columns);

               if(!info.containsAggregate(aref)) {
                  info.addAggregate(aref);
               }

               newex = newex.replace(matchNames.get(i),
                  VSUtil.getAggregateString(aggs.get(i), false));
            }

            // replace the script and setVIsrtual to false to execute on
            // FormulaTableLens
            eref.setExpression(newex);
            eref.setVirtual(false);

            tassembly.resetColumnSelection();
            tassembly.setAggregateInfo(info);
            setSharedCondition(chart, tassembly);
         }
         else {
            AggregateRef aref = new AggregateRef(column, column2, form);
            aref.setN(binding.getN());
            info.addAggregate(aref);
            tassembly.setAggregateInfo(info);
         }
      }

      setSharedCondition(chart, tassembly);

      if(AssetUtil.isCubeTable(tassembly) && !isWorksheetCube()) {
         ColumnSelection columns = tassembly.getColumnSelection(false);
         ColumnSelection columns2 = (ColumnSelection) columns.clone();
         columns.clear();

         DataRef ref = column;

         if(ref instanceof DataRefWrapper) {
            DataRef ref0 = ((DataRefWrapper) ref).getDataRef();

            if(ref0 == null) {
               LOG.warn("Column not found: " + ref);
               throw new ColumnNotFoundException("Column not found: " + ref);
            }

            ref = ref0;
         }

         column = ref == null ? null :
            (ColumnRef) columns2.getAttribute(ref.getName());

         if(column != null) {
            column.setVisible(true);
            columns.addAttribute(column);
         }

         if(column2 != null) {
            column2.setVisible(true);
            columns.addAttribute(column2);
         }

         for(int i = 0; i < columns2.getAttributeCount(); i++) {
            ColumnRef columnx = (ColumnRef) columns2.getAttribute(i);

            if(!columns.containsAttribute(columnx)) {
               columnx.setVisible(false);
               columns.addAttribute(columnx);
            }
         }

         if(form != null) {
            AggregateInfo info = new AggregateInfo();
            AggregateRef aref = new AggregateRef(column, column2, form);
            info.addAggregate(aref);
            tassembly.setAggregateInfo(info);
         }

         tassembly.setColumnSelection(columns);
         tassembly.setProperty("noEmpty", "false");

         if(!isWorksheetCube()) {
            CubeVSAQuery.fixAggregateInfo(tassembly, getAssembly());
         }
      }

      if((form == null || !isAggCalc)) {
         return tassembly;
      }

      String mname = Assembly.TABLE_VS + vname + "_mirror";
      MirrorTableAssembly mtable = new MirrorTableAssembly(ws, mname, null, false, tassembly);
      // @by stephenwebster, For Bug #9172
      // Mark this assembly as temporary to signal to other code not to modify it.
      mtable.setProperty("output.temp.table", "true");
      normalizeTable(mtable);
      ws.addAssembly(mtable);
      ColumnSelection mcols = mtable.getColumnSelection();
      DataRef ref0 = mcols.getAttribute(cref.getName());

      if(ref0 != null) {
         mcols.removeAttribute(ref0);
      }

      if(isAggCalc) {
         validateCalculateRef(cref);
      }

      mcols.addAttribute(cref);
      mtable.resetColumnSelection();

      return mtable;
   }

   /**
    * Get the data.
    * @return the data of the query.
    */
   @Override
   public Object getData() throws Exception {
      TableAssembly tassembly = getTableAssembly(false);
      OutputVSAssembly assembly = (OutputVSAssembly) getAssembly();
      OutputVSAssemblyInfo oinfo = (OutputVSAssemblyInfo) assembly.getInfo();
      ScalarBindingInfo binding = assembly.getScalarBindingInfo();

      // no base table assembly in worksheet?
      if(tassembly == null) {
         boolean noBinding = binding == null || binding.isEmpty();

         if(noBinding) {
            oinfo.setXDrillInfo(null);
         }

         if(assembly instanceof TextVSAssembly) {
            return null;
         }
         else if(assembly instanceof ImageVSAssembly) {
            return ((ImageVSAssembly) assembly).getImage();
         }
         else if(noBinding) {
            return null;
         }
         else {
            assembly.setValue(null);
            throw new BoundTableNotFoundException(Catalog.getCatalog().getString
               ("common.notTable", binding.getTableName()));
         }
      }

      TableLens table = getTableLens(tassembly);

      if(table == null) {
         return null;
      }

      ColumnRef column = (ColumnRef) binding.getColumn();
      table.moreRows(Integer.MAX_VALUE);
      checkMaxRowLimit(table);
      int col = AssetUtil.findColumn(table, column);

      if(col < 0) {
         LOG.warn("Column not found: " + column);
         return null;
      }

      // don't use the column header as value, which is meaningless
      if(!table.moreRows(table.getHeaderRowCount())) {
         return null;
      }

      if(table instanceof TableLens) {
         table = new TextSizeLimitTableLens(table, Util.getOrganizationMaxCellSize());
      }

      table.moreRows(XTable.EOT);
      Object value = table.getObject(table.getRowCount() - 1, col);
      Format format = table.getDefaultFormat(table.getRowCount() - 1, col);
      XDrillInfo dinfo = table.getXDrillInfo(table.getRowCount() - 1, col);
      int scale = binding.getScale();

      if(isValidDefaultFormat(binding, format)) {
         oinfo.setDefaultFormat(format);
      }

      oinfo.setXDrillInfo(dinfo);

      if(scale > 0 && scale != 1 && value instanceof Number) {
         value = Double.valueOf(((Number) value).doubleValue() / scale);
      }

      return value;
   }

   private boolean isValidDefaultFormat(ScalarBindingInfo binding, Format dfmt) {
      AggregateFormula formula = binding == null ? null : binding.getAggregateFormula();

      if(formula == null || !(dfmt instanceof DateFormat)) {
         return true;
      }

      String fname = formula == null ? null : formula.getName();

      return AggregateFormula.NONE.getName().equals(fname) ||
         AggregateFormula.FIRST.getName().equals(fname) ||
         AggregateFormula.LAST.getName().equals(fname) ||
         AggregateFormula.MAX.getName().equals(fname) ||
         AggregateFormula.MIN.getName().equals(fname) ||
         AggregateFormula.NTH_LARGEST.getName().equals(fname) ||
         AggregateFormula.NTH_SMALLEST.getName().equals(fname) ||
         AggregateFormula.NTH_MOST_FREQUENT.getName().equals(fname);
   }

   private static final Logger LOG = LoggerFactory.getLogger(OutputVSAQuery.class);
}
