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
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.graph.ChartCreationDefaults;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.erm.DataRef;
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

         if(sandbox == null) {
            return null;
         }

         TableLens lens = sandbox.getTableLens(source.getSource(), AssetQuerySandbox.RUNTIME_MODE);

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

   private final ViewsheetSandbox box;
   private final SourceInfo source;
   private static final Logger LOG = LoggerFactory.getLogger(ChartSourceOrder.class);
}
