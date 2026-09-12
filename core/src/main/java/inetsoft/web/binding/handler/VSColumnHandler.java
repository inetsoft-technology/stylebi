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
package inetsoft.web.binding.handler;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.AssetQuery;
import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Objects;

@Component
public class VSColumnHandler {
   @Autowired
   public VSColumnHandler(ViewsheetService viewsheetService) {
      this.viewsheetService = viewsheetService;
   }

   /** GetColumnSelectionEvent */
   public ColumnSelection getColumnSelection(
      RuntimeViewsheet rvs, String assembly, String tableName,
      String dimensionOf, boolean measureOnly, boolean includeCalc, boolean exclude,
      boolean ignoreToCheckPerm, boolean embedded, boolean includeAggregate) throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();
      String vsName = null;

      if(assembly != null && assembly.indexOf('.') > 0) {
         vsName = assembly.substring(0, assembly.lastIndexOf('.'));
      }

      // get the real viewsheet name, if this event is fired by an embedded vs
      if(vsName != null) {
         vs = (Viewsheet) vs.getAssembly(vsName);
      }

      if(vs == null) {
         return null;
      }

      Worksheet ws = vs.getBaseWorksheet();
      ColumnSelection selection;

      if(ws != null && (ignoreToCheckPerm || VSEventUtil.checkBaseWSPermission(
         vs, null, viewsheetService.getAssetRepository(), ResourceAction.READ)))
      {
         TableAssembly table = null;

         if(VSUtil.isVSAssemblyBinding(tableName)) {
            selection = VSUtil.getColumnsForVSAssemblyBinding(rvs, tableName);
         }
         else {
            table = (TableAssembly) ws.getAssembly(tableName);
            selection = table == null ? new ColumnSelection() :
                        (ColumnSelection) table.getColumnSelection(true).clone();
            XUtil.addDescriptionsFromSource(table, selection);
         }

         AggregateInfo dimonly = null;

         if(dimensionOf != null) {
            Assembly obj = vs.getAssembly(dimensionOf);

            if(obj instanceof CubeVSAssembly) {
               dimonly = ((CubeVSAssembly) obj).getAggregateInfo();
            }
         }

         if(embedded || measureOnly || exclude || dimonly != null ||
            !includeCalc)
         {
            for(int i = selection.getAttributeCount() - 1; i >= 0; i--) {
               ColumnRef ref = (ColumnRef) selection.getAttribute(i);
               boolean exculdeCalc = !includeCalc &&
                  ref instanceof CalculateRef;
               boolean excludeAggCalc = exclude && VSUtil.isAggregateCalc(ref);

               if(embedded && ref.isExpression() ||
                  measureOnly &&
                  (ref.getRefType() & DataRef.CUBE) == DataRef.CUBE &&
                  (ref.getRefType() & DataRef.MEASURE) != DataRef.MEASURE ||
                  dimonly != null && !isDimensionRef(ref, dimonly) ||
                  exculdeCalc || excludeAggCalc)
               {
                  selection.removeAttribute(i);
               }
            }
         }

         if(includeCalc) {
            CalculateRef[] calcs = vs.getCalcFields(tableName);

            if(calcs != null && calcs.length > 0) {
               for(CalculateRef ref : calcs) {
                  if(ref.isDcRuntime() && selection.containsAttribute(ref)) {
                     selection.removeAttribute(ref);
                     continue;
                  }

                  if(!ref.isDcRuntime() && (!exclude || ref.isBaseOnDetail()) &&
                     !(selection.containsAttribute(ref) ||
                     selection.getAttribute(ref.getName()) != null))
                  {
                     selection.addAttribute(ref);
                  }
               }
            }
         }

         selection = VSUtil.getVSColumnSelection(selection);
         selection = VSUtil.sortColumns(selection);

         if(includeAggregate) {
            ColumnSelection aggregate = VSEventUtil.getAggregateColumnSelection(vs, tableName);

            for(int i = 0; i < aggregate.getAttributeCount(); i++) {
               selection.addAttribute(aggregate.getAttribute(i), false);
            }
         }

         AssetQuery query = AssetUtil.handleMergeable(rvs.getRuntimeWorksheet(), table);

         if(table != null && query != null) {
            selection.setProperty("sqlMergeable", query.isQueryMergeable(false));
         }
      }
      else {
         selection = new ColumnSelection();
      }

      return selection;
   }

   /**
    * Called by selection list editor
    * @param rvs        RuntimeViewsheet instance
    * @param table      table
    * @param principal  the principal user
    * @return the column selection
    * @throws Exception if can't retrieve columns
    */
   public ColumnSelection getTableColumns(RuntimeViewsheet rvs, String table,
                                          Principal principal)
      throws Exception
   {
      return getTableColumns(rvs, table, null, null, null, false,
                             false, false, false, false, false, principal);
   }

   /**
    * Called by data input pane
    * @param rvs        RuntimeViewsheet instance
    * @param table      table
    * @param embedded   get embedded tables
    * @param principal  the principal user
    * @return the column selection
    * @throws Exception if can't retrieve columns
    */
   public ColumnSelection getTableColumns(RuntimeViewsheet rvs, String table,
                                          boolean embedded, Principal principal)
      throws Exception
   {
      return getTableColumns(rvs, table, null, null, null, embedded,
                             false, false, false, false, false, principal);
   }

   /**
    * Get the Column Selection for a table, Mimic of GetColumnSelectionEvent
    * @param rvs                    Runtime Viewsheet instance
    * @param table                  the table
    * @param assembly               assembly name
    * @param vsName                 viewsheet name
    * @param dimensionOf            dimension name
    * @param embedded               get embedded tables
    * @param measureOnly            only get measures
    * @param includeCalc            include calc columns
    * @param ignoreToCheckPerm      ignore check ?
    * @param includeAggregate       include aggregate columns
    * @param excludeAggregateCalc   exclude aggregate calc columns
    * @param principal              principal user
    * @return the ColumnSelection
    * @throws Exception if can't retrieve the columns
    */
   public ColumnSelection getTableColumns(RuntimeViewsheet rvs, String table, String assembly,
                                          String vsName, String dimensionOf, boolean embedded,
                                          boolean measureOnly, boolean includeCalc,
                                          boolean ignoreToCheckPerm, boolean includeAggregate,
                                          boolean excludeAggregateCalc, Principal principal)
      throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return new ColumnSelection();
      }

      if(vsName == null && assembly != null && assembly.indexOf('.') > 0) {
         vsName = assembly.substring(0, assembly.lastIndexOf('.'));
      }

      // get the real viewsheet name, if this event is fired by an embedded vs
      if(vsName != null) {
         vs = (Viewsheet) vs.getAssembly(vsName);
      }

      if(vs == null) {
         return new ColumnSelection();
      }

      Worksheet ws = vs.getBaseWorksheet();
      ColumnSelection selection;

      if(ws != null && ignoreToCheckPerm ||
         VSEventUtil.checkBaseWSPermission(vs, principal, viewsheetService.getAssetRepository(), ResourceAction.READ))
      {
         Assembly wsobj = Objects.requireNonNull(ws).getAssembly(table);

         if(!(wsobj instanceof TableAssembly tableAssembly)) {
            return new ColumnSelection();
         }

         TableAssembly tableAssembly0 = tableAssembly;

         while(tableAssembly0 instanceof MirrorTableAssembly) {
            tableAssembly0 =
               ((MirrorTableAssembly) tableAssembly0).getTableAssembly();
         }

         // still get the original column selection, otherwise the column selection will not match
         // the runtime column selection and things like TableVSAQuery.getQueryData() may fail
         selection = table == null ? new ColumnSelection() :
            tableAssembly.getColumnSelection(true).clone();
         XUtil.addDescriptionsFromSource(tableAssembly0, selection);
         AggregateInfo dimonly = null;

         if(dimensionOf != null) {
            Assembly obj = vs.getAssembly(dimensionOf);

            if(obj instanceof CubeVSAssembly) {
               dimonly = ((CubeVSAssembly) obj).getAggregateInfo();
            }
         }

         for(int i = selection.getAttributeCount() - 1; i >= 0; i--) {
            ColumnRef ref = (ColumnRef) selection.getAttribute(i);
            boolean excludeCalc = !includeCalc && ref instanceof CalculateRef;
            boolean excludeAggCalc = excludeAggregateCalc && VSUtil.isAggregateCalc(ref);

            if(VSUtil.isPreparedCalcField(ref) || embedded && ref.isExpression() || measureOnly &&
               (ref.getRefType() & DataRef.CUBE) == DataRef.CUBE &&
               (ref.getRefType() & DataRef.MEASURE) != DataRef.MEASURE ||
               dimonly != null && !isDimensionRef(ref, dimonly) ||
               excludeCalc || excludeAggCalc ||
               ref instanceof CalculateRef && ((CalculateRef) ref).isDcRuntime())
            {
               selection.removeAttribute(i);
            }
         }

         if(includeCalc) {
            CalculateRef[] calcs = vs.getCalcFields(table);

            if(calcs != null && calcs.length > 0) {
               for(CalculateRef ref : calcs) {
                  if(VSUtil.isPreparedCalcField(ref)) {
                     continue;
                  }

                  if(!ref.isDcRuntime() && (!excludeAggregateCalc || ref.isBaseOnDetail()) &&
                     !(selection.containsAttribute(ref) ||
                        selection.getAttribute(ref.getName()) != null))
                  {
                     selection.addAttribute(ref);
                  }
               }
            }
         }

         selection = VSUtil.getVSColumnSelection(selection);
         selection = VSUtil.sortColumns(selection);

         if(includeAggregate) {
            ColumnSelection aggregate = VSEventUtil.getAggregateColumnSelection(vs, table);

            for(int i = 0; i < aggregate.getAttributeCount(); i++) {
               selection.addAttribute(aggregate.getAttribute(i), false);
            }
         }

         AssetQuery query = AssetUtil.handleMergeable(rvs.getRuntimeWorksheet(), tableAssembly);

         if(tableAssembly != null && query != null) {
            selection.setProperty("sqlMergeable", query.isQueryMergeable(false));
         }
      }
      else {
         selection = new ColumnSelection();
      }

      return selection;
   }

   /**
    * Check if ref is a group in the aggregate info.
    */
   private boolean isDimensionRef(ColumnRef ref, AggregateInfo ainfo) {
      // ignore table name in vs binding
      DataRef ref2 = new AttributeRef(ref.getAttribute());
      boolean dim = ainfo.containsGroup(new GroupRef(ref2));

      if(dim) {
         return true;
      }

      boolean agg = ainfo.containsAggregate(ref2);

      if(agg) {
         return false;
      }

      return !VSEventUtil.isMeasure(ref);
   }

   private final ViewsheetService viewsheetService;
}