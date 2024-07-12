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
package inetsoft.mv;

import inetsoft.mv.data.*;
import inetsoft.mv.fs.FSService;
import inetsoft.report.composition.WorksheetWrapper;
import inetsoft.report.composition.execution.AssetDataCache;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.ConditionUtil;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.util.Identity;
import inetsoft.uql.util.QueryManager;
import inetsoft.uql.viewsheet.Viewsheet;

import java.util.ArrayList;
import java.util.List;

/**
 * MVIncremental
 *
 * @version 12.2
 * @author InetSoft Technology Corp
 */
public abstract class MVIncremental {
   public MVIncremental(MVDef def) {
      this.mvdef = def;

      try {
         String file = MVStorage.getFile(mvdef.getName());
         this.mv = MVStorage.getInstance().get(file);
         this.mv.getDef().shareWith(this.mvdef);
      }
      catch(Exception e) {
         // throw mv execution exception to create newly mv
         throw new RuntimeException("Initialize mv error when incremental mv," +
            " nothing changed.", e);
      }
   }

   /**
    * Update the mv data.
    */
   public abstract void update() throws Exception;

   /**
    * Cancel this task.
    */
   public abstract void cancel();

   /**
    * Refresh MVDef last update time.
    */
   protected void refresh() throws Exception {
      try {
         MVManager manager = MVManager.getManager();

         // refresh MV's def
         mv.updateLastModifiedTime();
         // MVDef.lastModified only be used in script, and we
         // just need to refresh the MV.mvdef
         // refresh MVManager's def
         // @by davyc, for data cache
         mvdef.updateLastUpdateTime();
         manager.add(mvdef);

         // @by ChrisSpagnoli, for Bug #7250
         // Clear all assets from the cache, to prevent any "stale" data being used
         AssetDataCache.clearCache();
      }
      catch(Exception e) {
         // throw mv execution exception to create newly mv
         throw new MVExecutionException(e);
      }
   }

   /**
    * Merge the mv update conditions to table assembly condition.
    */
   protected void mergeAppendCondition(TableAssembly table,
      MVConditionListHandler handler)
   {
      ConditionListWrapper upPre = table.getMVUpdatePreConditionList();
      validateConditionList(upPre.getConditionList(), handler);
      ConditionListWrapper upPost = table.getMVUpdatePostConditionList();
      validateConditionList(upPost.getConditionList(), handler);
      ConditionListWrapper pre = table.getPreConditionList();
      ConditionListWrapper post = table.getPostConditionList();

      table.setPreConditionList(mergedCondition(upPre, pre));
      table.setPostConditionList(mergedCondition(upPost, post));

      // for composed table, make sure the MV append conditions are
      // applied for sub-tables so the condition is pushed to db
      if(table instanceof ComposedTableAssembly) {
         TableAssembly[] children =
            ((ComposedTableAssembly) table).getTableAssemblies(false);

         for(TableAssembly child : children) {
            mergeAppendCondition(child, handler);
         }
      }
   }

   /**
    * Get the merged condition list.
    */
   private ConditionListWrapper mergedCondition(ConditionListWrapper mvConds,
      ConditionListWrapper conds)
   {
      List<ConditionListWrapper> list = new ArrayList<>();
      list.add(mvConds);
      list.add(conds);

      return ConditionUtil.mergeConditionList(list, JunctionOperator.AND);
   }

   /**
    * Validate the mv update condition list.
    */
   private void validateConditionList(ConditionList mvcond,
      MVConditionListHandler handler)
   {
      for(int i = 0; i < mvcond.getSize(); i +=2) {
         ConditionItem item = mvcond.getConditionItem(i);
         XCondition cond = item.getXCondition();

         if(!(cond instanceof AssetCondition)) {
            continue;
         }

         AssetCondition acond = (AssetCondition) cond;
         ExpressionValue eval = null;
         DataRef ref = item.getAttribute();
         // first try self ref, then try original ref
         MVColumn mvcol = handler.getMVColumn(ref.getAttribute());

         if(mvcol == null) {
            String colName = MVDef.getMVHeader(ref);
            mvcol = handler.getMVColumn(colName);
         }

         if(acond.getValueCount() <= 0) {
            continue;
         }

         for(int j = 0; j < acond.getValueCount(); j++) {
            Object val = acond.getValue(j);

            if(val instanceof ExpressionValue) {
               eval = (ExpressionValue) val;
            }

            if(eval == null) {
               continue;
            }

            String exp = eval.getExpression();

            if(exp.contains("MV.")) {
               handler.executeScript(acond, eval, j, mvcol);
            }
         }
      }
   }

   /**
    * Get tablelens.
    */
   protected XTable getData(Worksheet ws, TableAssembly assembly,
      VariableTable mvParams) throws Exception
   {
      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      box.setWSName(mvdef.getWsId());
      box.setFixingAlias(false);
      Worksheet base = ws;

      while(base instanceof WorksheetWrapper) {
         base = ((WorksheetWrapper) base).getWorksheet();
      }

      VariableTable vars = Viewsheet.getVariableTable(base);
      vars.addBaseTable(mvParams);
      Identity[] users = mvdef.getUsers();
      XPrincipal user = users == null || users.length == 0 ? null : users[0].create();
      box.setBaseUser(user);
      box.setQueryManager(new QueryManager());
      box.refreshVariableTable(vars);
      String breakcol = mvdef.getBreakColumn();
      boolean desktop = FSService.getConfig().isDesktop();

      if(breakcol != null && !desktop) {
         String mname;
         mname = AssetUtil.getNextName(ws, "blk");
         MirrorTableAssembly mirror = new MirrorTableAssembly(ws, mname,
            assembly);
         SortInfo sortinfo = new SortInfo();
         SortRef sref = new SortRef(new AttributeRef(assembly.getName(), breakcol));

         sref.setOrder(XConstants.SORT_ASC);
         sortinfo.addSort(sref);
         mirror.setSortInfo(sortinfo);
         ws.addAssembly(mirror);
         assembly = mirror;
      }

      // set time as now, not to use cached data when create mv
      return AssetDataCache.getData(
         null, (TableAssembly) assembly.clone(), box, null, AssetQuerySandbox.RUNTIME_MODE, false,
         System.currentTimeMillis(), box.getQueryManager());
   }

   protected MV mv;
   protected MVDef mvdef;
}
