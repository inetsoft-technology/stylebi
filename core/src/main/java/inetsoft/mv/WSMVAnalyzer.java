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
package inetsoft.mv;

import inetsoft.mv.trans.*;
import inetsoft.report.composition.WorksheetWrapper;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.ConditionList;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.util.Identity;
import inetsoft.util.Catalog;

import java.util.*;

/**
 * WSMVAnalyzer, analyzes a worksheet and generates mv candidates.
 *
 * @author InetSoft Technology Corp
 * @version 13.1
 */
public class WSMVAnalyzer implements MVAnalyzer {
   public WSMVAnalyzer(String wsId, Worksheet ws, Identity user, boolean bypass) {
      super();

      this.wsId = wsId;
      this.ws = ws;
      this.user = user;
      this.desc = new TransformationDescriptor();
      this.bypass = bypass;
   }

   public MVDef[] analyze() {
      WorksheetWrapper wsWrapper = new WorksheetWrapper(ws);
      List<MVDef> mvDefs = new ArrayList<>();
      String defaultCycle = MVManager.getManager().getDefaultCycle();
      Assembly[] assemblies = ws.getAssemblies();
      Set<BoundTableAssembly> boundTables = new LinkedHashSet<>();

      for(Assembly assembly : assemblies) {
         if(assembly instanceof BoundTableAssembly) {
            boundTables.add((BoundTableAssembly) assembly);
         }
      }

      for(Assembly assembly : assemblies) {
         checkValid(assembly, boundTables);
      }

      for(BoundTableAssembly table : boundTables) {
         // if the table is a crosstab then save the detail data instead
         if(table.isCrosstab()) {
            table.setAggregateInfo(new AggregateInfo());
            table.setPreConditionList(new ConditionList());
            table.setPreRuntimeConditionList(new ConditionList());
            table.setPostConditionList(new ConditionList());
            table.setPostRuntimeConditionList(new ConditionList());
            table.setRankingConditionList(new ConditionList());
            table.setRankingRuntimeConditionList(new ConditionList());
            table.resetColumnSelection();
         }

         MVDef def = new MVDef(null, wsId, table.getName(), table.getName(),
                               null, wsWrapper, user, null, false, false, bypass);

         if(defaultCycle != null && !defaultCycle.equals("")) {
            def.setCycle(defaultCycle);
         }

         mvDefs.add(def);
      }

      return mvDefs.toArray(new MVDef[0]);
   }

   private void checkValid(Assembly assembly, Set<BoundTableAssembly> boundTables) {
      // cube table? not accepted
      if(assembly instanceof CubeTableAssembly) {
         boundTables.remove(assembly);
         String tableName = assembly.getName();
         String wsPath = AssetEntry.createAssetEntry(wsId).getPath();
         UserInfo uinfo = new UserInfo(wsPath, tableName,
                                       Catalog.getCatalog().getString("mv.vs.cube", wsPath));
         desc.addUserInfo(uinfo);
         desc.addFault(TransformationFault.containsCubeTable(tableName, wsPath));
         return;
      }

      if(assembly instanceof BoundTableAssembly) {
         return;
      }

      if(assembly instanceof EmbeddedTableAssembly) {
         String tableName = assembly.getName();
         String wsPath = AssetEntry.createAssetEntry(wsId).getPath();
         UserInfo uinfo = new UserInfo(wsPath, tableName,
                                       Catalog.getCatalog().getString("mv.ws.embed.table",
                                                                      tableName));
         desc.addUserInfo(uinfo);
         desc.addFault(TransformationFault.containsEmbeddedTable(tableName, wsPath));
         return;
      }

      // check for sql expressions and remove any depended bound tables from the mv candidates
      if(assembly instanceof ComposedTableAssembly) {
         ComposedTableAssembly composedTable = (ComposedTableAssembly) assembly;

         if(containsSQLFormula(composedTable)) {
            Set<BoundTableAssembly> childBoundTables =
               getChildBoundTables(composedTable, new LinkedHashSet<>());

            for(BoundTableAssembly childBoundTable : childBoundTables) {
               if(boundTables.contains(childBoundTable)) {
                  boundTables.remove(childBoundTable);
                  String tableName = childBoundTable.getName();
                  String wsPath = AssetEntry.createAssetEntry(wsId).getPath();
                  UserInfo uinfo = new UserInfo(wsPath, tableName,
                                                Catalog.getCatalog()
                                                   .getString("mv.ws.sql.formula", tableName,
                                                              composedTable.getName()));
                  desc.addUserInfo(uinfo);
                  desc.addFault(TransformationFault
                                   .containsSQLFormula(tableName, composedTable.getName(), wsPath));
               }
            }
         }
      }
   }

   @Override
   public boolean isNotHitMVWarned() {
      return false;
   }

   @Override
   public String getInfo(MVDef[] defs) {
      String blank = "     ";
      StringBuilder buf = new StringBuilder();
      buf.append(blank).append("Top MV:");
      int idx = 1;

      for(MVDef mvDef : defs) {
         TableAssembly tableAssembly = (TableAssembly) ws.getAssembly(mvDef.getBoundTable());
         buf.append("\n").append(blank).append(blank).append(idx).append(". ")
            .append(mvDef.getBoundTable());
         buf.append(",").append(blank).append("[source:").append(tableAssembly.getSource())
            .append("]");
         idx++;
      }

      return buf.toString();
   }

   @Override
   public TransformationDescriptor getDescriptor() {
      return desc;
   }

   private boolean containsSQLFormula(TableAssembly table) {
      ColumnSelection cols = table.getColumnSelection(true);
      int cnt = cols.getAttributeCount();

      for(int i = 0; i < cnt; i++) {
         ColumnRef col = (ColumnRef) cols.getAttribute(i);
         DataRef ref = col.getDataRef();

         if(ref instanceof ExpressionRef) {
            ExpressionRef eref = (ExpressionRef) ref;

            if((col.isSQL() || eref.isSQL()) && !eref.isVirtual() &&
               eref.isExpressionEditable())
            {
               return true;
            }
         }
      }

      return false;
   }

   private Set<BoundTableAssembly> getChildBoundTables(ComposedTableAssembly composedTable,
                                                       Set<BoundTableAssembly> boundTables)
   {
      for(TableAssembly table : composedTable.getTableAssemblies(false)) {
         if(table instanceof BoundTableAssembly) {
            boundTables.add((BoundTableAssembly) table);
         }
         else if(table instanceof ComposedTableAssembly) {
            getChildBoundTables((ComposedTableAssembly) table, boundTables);
         }
      }

      return boundTables;
   }

   private final String wsId;
   private final Worksheet ws;
   private final Identity user;
   private final TransformationDescriptor desc;
   private final boolean bypass;
}
