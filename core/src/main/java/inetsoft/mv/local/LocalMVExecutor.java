/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.mv.local;

import inetsoft.mv.*;
import inetsoft.mv.fs.*;
import inetsoft.mv.mr.XJobPool;
import inetsoft.report.internal.table.XTableLens;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.util.Tool;

import java.util.List;

/**
 * DefaultMVExecutor, default executor implementation which uses MVJob
 * and XJobPool
 *
 * @version 12.2
 * @author InetSoft Technology Corp
 */
public class LocalMVExecutor extends AbstractMVExecutor {
   /**
    * Creates a new instance of <tt>LocalMVExecutor</tt>.
    *
    * @param table  the bound table assembly.
    * @param mvName the view name.
    * @param vars   the query parameters.
    * @param user   a principal that identifies the user executing the query.
    */
   public LocalMVExecutor(TableAssembly table, String mvName, VariableTable vars,
                          XPrincipal user)
   {
      super(table, mvName, vars, user);
   }

   @Override
   public XTable getData() throws Exception {
      Cluster.getInstance().lockRead("mv.exec." + mvName);
      MVBenchmark.startBenchmark();

      try {
         XTable result = getData0();
         MVBenchmark.time("moreRows", () -> {
            result.moreRows(XTable.EOT);
            return (long) result.getRowCount();
         });
         MVBenchmark.stopBenchmark(this::populateBenchmarkContext);
         return result;
      }
      finally {
         Cluster.getInstance().unlockRead("mv.exec." + mvName);
      }
   }

   private XTable getData0() throws Exception {
      ColumnSelection ocols = table.getColumnSelection(true);
      XFileSystem fs = FSService.getServer().getFSystem();
      XFile file = fs == null ? null : fs.get(mvName);
      List<SBlock> blocks = file == null ? null : file.getBlocks();

      if(blocks == null || blocks.size() == 0) {
         MVBenchmark.startTimer("createEmptyTable");
         Object[][] arr = new Object[1][];
         int cnt = ocols.getAttributeCount();
         String[] hdrs = new String[cnt];
         String[] ids = new String[cnt];
         String[] types = new String[cnt];

         for(int i = 0; i < cnt; i++) {
            ColumnRef col = (ColumnRef) ocols.getAttribute(i);
            ids[i] = AssetUtil.getAttributeString(col);
            String alias = col.getAlias();
            hdrs[i] = alias != null && alias.length() > 0 ? alias : col.getAttribute();
            types[i] = col.getDataType();
            types[i] = types[i] == null ? XSchema.STRING : types[i];
         }

         arr[0] = hdrs;
         XEmbeddedTable etable = new XEmbeddedTable(types, arr);

         for(int i = 0; i < cnt; i++) {
            etable.setColumnIdentifier(i, ids[i]);
         }

         try {
            return new XTableLens(etable);
         }
         finally {
            MVBenchmark.stopTimer("createEmptyTable");
         }
      }

      MVBenchmark.startTimer("createJob");
      job = new MVJob(table, mvName, vars, user);
      MVBenchmark.stopTimer("createJob");
      MVBenchmark.startTimer("executeJob");

      try {
         return (XTable) XJobPool.execute(job);
      }
      finally {
         MVBenchmark.stopTimer("executeJob");
      }
   }

   @Override
   public void cancel() {
      if(job != null) {
         job.cancel();
      }
   }

   private void populateBenchmarkContext(MVBenchmark.BenchmarkContext context) {
      AggregateInfo ainfo = table.getAggregateInfo();
      context.put("benchmarkType", "MV");
      context.put("mvType", "Local");
      context.put("mvName", mvName);
      context.put("groups", Tool.arrayToString(ainfo.getGroups()));
      context.put("aggregates", Tool.arrayToString(ainfo.getAggregates()));

      if(table.getPreRuntimeConditionList() == null) {
         context.put("condition", "");
      }
      else {
         context.put("condition", table.getPreRuntimeConditionList().toString());
      }
   }

   private MVJob job;
}
