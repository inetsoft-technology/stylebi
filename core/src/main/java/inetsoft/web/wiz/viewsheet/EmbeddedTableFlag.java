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
package inetsoft.web.wiz.viewsheet;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.EmbeddedTableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.uql.asset.MirrorTableAssembly;
import inetsoft.uql.asset.WSAssembly;

/**
 * Bug #80479: an Embedded Table assembly created through the agent add path is a bare
 * {@link EmbeddedTableVSAssembly}, which {@code VSUtil.isDynamicTable} does not recognise, so
 * {@code Viewsheet.createMirrorTable} wraps the worksheet table it is bound to in a mirror and the
 * cell edit tools see a mirror instead of the embedded table. Flagging the assembly, as the
 * Composer's own conversion does, keeps the worksheet table raw.
 */
public final class EmbeddedTableFlag {
   private EmbeddedTableFlag() {
   }

   /**
    * Marks a newly created embedded table assembly as an embedded table. The flag is set on the
    * live info: copying it into another info ({@code copyInfo}) drops it.
    */
   public static void flag(Assembly assembly) {
      if(assembly instanceof EmbeddedTableVSAssembly table &&
         table.getVSAssemblyInfo() instanceof TableVSAssemblyInfo info)
      {
         info.setEmbeddedTable(true);
      }
   }

   /**
    * Un-wraps the mirror that was built around the bound worksheet table before the assembly was
    * flagged or bound. The worksheet is only re-prepared by {@code Viewsheet.resetWS()}, which an
    * in-place binding edit does not reliably trigger, so it is called here; the cached lenses of
    * the table are then dropped, as {@code ViewsheetSandbox.syncSavedEmbeddedData} does.
    *
    * @return true if the worksheet was changed
    */
   public static boolean unwrap(RuntimeViewsheet rvs, String assemblyName) {
      Viewsheet vs = rvs.getViewsheet();
      Assembly assembly = vs == null ? null : vs.getAssembly(assemblyName);

      if(!(assembly instanceof EmbeddedTableVSAssembly table) ||
         !((TableVSAssemblyInfo) table.getVSAssemblyInfo()).isEmbeddedTable())
      {
         return false;
      }

      String tableName = table.getTableName();

      if(tableName == null || vs.getBaseWorksheet() == null ||
         !(vs.getBaseWorksheet().getAssembly(tableName) instanceof MirrorTableAssembly))
      {
         return false;
      }

      vs.resetWS();

      if(vs.getBaseWorksheet().getAssembly(tableName) instanceof MirrorTableAssembly) {
         return false;
      }

      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElse(null);
      AssetQuerySandbox wbox = box == null ? null : box.getAssetQuerySandbox();

      if(wbox != null) {
         for(String name : new String[] { tableName, tableName + Viewsheet.OUTER_TABLE_SUFFIX }) {
            Assembly ws = vs.getBaseWorksheet().getAssembly(name);

            if(ws instanceof WSAssembly wsa) {
               wbox.reset(wsa.getAssemblyEntry(), true);
            }
         }
      }

      return true;
   }
}
