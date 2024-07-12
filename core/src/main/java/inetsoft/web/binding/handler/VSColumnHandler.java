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
import org.springframework.stereotype.Component;

@Component
public class VSColumnHandler {
   /** GetColumnSelectionEvent */
   public ColumnSelection getColumnSelection(
      RuntimeViewsheet rvs, ViewsheetService viewsheetService, String assembly, String tableName,
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
}