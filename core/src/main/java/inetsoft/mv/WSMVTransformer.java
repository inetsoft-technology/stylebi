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
package inetsoft.mv;

import inetsoft.mv.trans.*;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.ConditionList;
import inetsoft.uql.asset.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public final class WSMVTransformer {
   /**
    * Transform the specified table assembly.
    */
   public static TableAssembly transform(TableAssembly table) throws Exception {
      TransformationDescriptor desc =
         new TransformationDescriptor(null, table.getWorksheet(),
                                      TransformationDescriptor.RUN_MODE);
      desc.reset(null, table.getName(), table.getName(), null);

      if(table instanceof BoundTableAssembly) {
         TableAssembly newTable = desc.getTable(table.getName());
         newTable.setRuntimeMV(table.getRuntimeMV());

         // prevent the conditions from applying twice
         // for crosstab, detail data is saved so need to apply conditions later
         if(!((BoundTableAssembly) newTable).isCrosstab()) {
            newTable.setPreConditionList(new ConditionList());
            newTable.setPreRuntimeConditionList(new ConditionList());
            newTable.setPostConditionList(new ConditionList());
            newTable.setPostRuntimeConditionList(new ConditionList());
            newTable.setRankingConditionList(new ConditionList());
            newTable.setRankingRuntimeConditionList(new ConditionList());
         }

         return newTable;
      }
      else if(table instanceof ComposedTableAssembly) {
         if(!joinedTablesContainCommonAncestor((ComposedTableAssembly) table, new ArrayList<>())) {
            AbstractTransformer transformer = new WSMVSelectionDownTransformer();
            transformer.transform(desc);

            transformer = new WSMVAggregateDownTransformer();
            transformer.transform(desc);
         }
      }

      if("true".equals(SreeEnv.getProperty("mv_debug"))) {
         LOG.debug("Transformation info: " + desc.getInfo());
      }

      return desc.getTable(table.getName());
   }

   /**
    * Check if this table assembly contains ws runtime mv.
    */
   public static boolean containsWSRuntimeMV(TableAssembly table) {
      if(table == null) {
         return false;
      }

      RuntimeMV info = table.getRuntimeMV();

      // check for ws mv
      if(info != null && info.isWSMV()) {
         return true;
      }

      if(!(table instanceof ComposedTableAssembly)) {
         return false;
      }

      ComposedTableAssembly ctable = (ComposedTableAssembly) table;

      for(TableAssembly childTable : ctable.getTableAssemblies(false)) {
         if(containsWSRuntimeMV(childTable)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Looks at the composition of the given table and if two or more joined tables contain
    * a common ancestor then this method returns true.
    * <pre>
    *     B
    *   /   \
    *  M1    M2
    *   \   /
    *     Q
    * </pre>
    * In the example above, Q joins two tables M1 and M2 which are mirrors of the bound table B.
    * M1 and M2 have a common ancestor of B. In this case, it's not possible to move down the
    * conditions from M1 or M2 to B as it could affect the result of either table and further
    * affect the final result of Q.
    * <p>
    * One possibility to avoid this problem might be to have it so that M1 is based on a clone of B
    * and M2 is based on another clone of B. This way pushing down the conditions to B will not
    * affect the final result as the conditions will be pushed down to different versions of B.
    * There might be some cases where this will not work so for now transformations will not be
    * performed if we encounter this scenario.
    */
   private static boolean joinedTablesContainCommonAncestor(ComposedTableAssembly table,
                                                            List<String> childTableNames)
   {
      TableAssembly[] childTables = table.getTableAssemblies(false);

      for(TableAssembly childTable : childTables) {
         String childTableName = childTable.getName();

         if(childTableNames.contains(childTableName)) {
            return true;
         }

         childTableNames.add(childTableName);

         if(childTable instanceof ComposedTableAssembly) {
            if(joinedTablesContainCommonAncestor((ComposedTableAssembly) childTable,
                                                 childTableNames))
            {
               return true;
            }
         }
      }

      return false;
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(WSMVTransformer.class);
}
