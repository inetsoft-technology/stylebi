/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

import inetsoft.report.TableLens;
import inetsoft.report.composition.WorksheetWrapper;
import inetsoft.report.composition.execution.AssetQuery;
import inetsoft.report.composition.execution.AssetQueryCacheNormalizer;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.graph.ChartCreationDefaults;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.XQuery;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.VSDimensionRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Reads a chart category's values in the order they first appear in the worksheet table. */
public final class ChartSourceOrder implements ChartCreationDefaults.SourceOrder {
   /** The most data rows read. */
   public static final int MAX_ROWS = 10_000;

   public ChartSourceOrder(ViewsheetSandbox box, SourceInfo source) {
      this.box = box;
      this.source = source;
   }

   @Override
   public List<Object> valuesInSourceOrder(VSDimensionRef dim) {
      if(box == null || source == null || source.getSource() == null || dim == null ||
         (dim.getRefType() & DataRef.CUBE) != 0 || dim.isNameGroup())
      {
         return null;
      }

      try {
         AssetQuerySandbox sandbox = box.getAssetQuerySandbox();

         if(sandbox == null || sandbox.isDisposed()) {
            return null;
         }

         TableAssembly table = copyTable(sandbox.getWorksheet(), source.getSource());

         if(table == null) {
            return null;
         }

         TableLens lens = read(sandbox, table);

         if(lens == null) {
            return null;
         }

         int col = Util.findColumn(lens, dim.getGroupColumnValue());

         if(col < 0) {
            return null;
         }

         Set<Object> values = new LinkedHashSet<>();
         int start = lens.getHeaderRowCount();

         for(int row = start; row - start < MAX_ROWS && lens.moreRows(row); row++) {
            Object value = lens.getObject(row, col);

            if(value != null) {
               values.add(value);

               if(values.size() > ChartCreationDefaults.MAX_ORDERED_VALUES) {
                  return null;
               }
            }
         }

         return new ArrayList<>(values);
      }
      catch(Exception ex) {
         LOG.debug("Unable to read the source order of {}", source.getSource(), ex);
         return null;
      }
   }

   /**
    * A copy of the worksheet table, in a wrapper so the worksheet does not change, without the
    * dashboard's selections and with the row limit in its query.
    */
   private static TableAssembly copyTable(Worksheet ws, String name) {
      if(ws == null) {
         return null;
      }

      TableAssembly copy = null;

      // selections change the table in place under the worksheet lock
      synchronized(ws) {
         Assembly table = ws.getAssembly(name);

         if(table instanceof TableAssembly) {
            copy = (TableAssembly) ((TableAssembly) table).copyAssembly(COPY_PREFIX + name);
         }
      }

      if(copy == null) {
         return null;
      }

      copy.setPreRuntimeConditionList(null);
      copy.setPostRuntimeConditionList(null);
      new WorksheetWrapper(ws).addAssembly(copy);
      int max = copy.getMaxRows();
      copy.setMaxRows(max > 0 ? Math.min(max, MAX_ROWS) : MAX_ROWS);
      return copy;
   }

   /**
    * Runs the copy's query as the sandbox runs a table's, without caching the result.
    */
   private static TableLens read(AssetQuerySandbox sandbox, TableAssembly table)
      throws Exception
   {
      int mode = AssetQuerySandbox.RUNTIME_MODE;
      AssetQueryCacheNormalizer normalizer = new AssetQueryCacheNormalizer(table, sandbox, mode);
      AssetQuery query = AssetQuery.createAssetQuery(table, mode, sandbox, false, -1L, true, false);
      VariableTable vars = sandbox.getVariableTable();
      vars = vars == null ? new VariableTable() : vars.clone();
      XUtil.copyDBCredentials((XPrincipal) sandbox.getUser(), vars);
      vars.put(XQuery.HINT_PREVIEW, "false");
      return normalizer.transformTableLens(query.getTableLens(vars));
   }

   // a name the worksheet's own tables and cached results do not use
   private static final String COPY_PREFIX = "_source_order_";
   private final ViewsheetSandbox box;
   private final SourceInfo source;
   private static final Logger LOG = LoggerFactory.getLogger(ChartSourceOrder.class);
}
