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
package inetsoft.web.composer.ws.assembly.tableinfo;

import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.TableAssemblyOperator;
import inetsoft.uql.asset.internal.CompositeTableAssemblyInfo;
import inetsoft.uql.asset.internal.SchemaTableInfo;
import inetsoft.web.binding.drm.ColumnRefModel;
import inetsoft.web.composer.model.ws.TableAssemblyOperatorModel;

import java.util.*;

public class CompositeTableAssemblyInfoModel extends ComposedTableAssemblyInfoModel {
   public CompositeTableAssemblyInfoModel(CompositeTableAssemblyInfo info) {
      super(info);

      List<TableAssemblyOperator.Operator[]> ops = new ArrayList<>();
      List<String[]> tableList = new ArrayList<>();
      operatorGroups = new ArrayList<>();

      if(info != null) {
         Enumeration tbls = info.getOperatorTables();

         while(tbls.hasMoreElements()) {
            String[] tables = (String[]) tbls.nextElement();
            TableAssemblyOperator top = info.getOperator(tables[0], tables[1]);
            if(!top.getOperator(0).isMergeJoin() && !top.getOperator(0).isCrossJoin()) {
               ops.add(top.getOperators());
               tableList.add(tables);
            }
         }

         for(int i = 0; i < ops.size(); i++) {
            TableAssemblyOperator.Operator[] opGroup = ops.get(i);
            String[] tables = tableList.get(i);
            ArrayList<TableAssemblyOperatorModel> pairOperators = new ArrayList<>();

            for(int j = 0; j < opGroup.length; j++) {
               TableAssemblyOperator.Operator op = opGroup[j];
               TableAssemblyOperatorModel operator = new TableAssemblyOperatorModel();
               operator.setOperation(op.getOperation());
               operator.setDistinct(op.isDistinct());

               if("null".equals(op.getLeftTable()) || "null".equals(op.getRightTable()) ||
                  op.getLeftTable() == null || op.getRightTable() == null || 
                  "".equals(op.getLeftTable()) || "".equals(op.getRightTable()))
               {
                  operator.setLtable(tables[0]);
                  operator.setRtable(tables[1]);
               }
               else {
                  operator.setLtable(op.getLeftTable());
                  operator.setRtable(op.getRightTable());
               }

               if(op.getLeftAttribute() != null && op.getRightAttribute() != null) {
                  operator.setLref(new ColumnRefModel((ColumnRef) op.getLeftAttribute()));
                  operator.setRref(new ColumnRefModel((ColumnRef) op.getRightAttribute()));
               }

               pairOperators.add(operator);
            }

            if(pairOperators.size() > 0) {
               operatorGroups.add(pairOperators);
            }
         }

         this.schemaTableInfos = new HashMap<>();
         info.getSchemaTableInfos().forEach((String table, SchemaTableInfo schemaTableInfo) -> {
            SchemaTableInfoModel model = null;

            if(schemaTableInfo != null) {
               model = SchemaTableInfoModel.builder()
                  .left(schemaTableInfo.getLeft())
                  .top(schemaTableInfo.getTop())
                  .width(schemaTableInfo.getWidth())
                  .build();
            }

            this.schemaTableInfos.put(table, model);
         });
      }
   }

   public Map<String, SchemaTableInfoModel> getSchemaTableInfos() {
      return schemaTableInfos;
   }

   public ArrayList<ArrayList<TableAssemblyOperatorModel>> getOperatorGroups() {
      return operatorGroups;
   }

   public void setOperatorGroups(
      ArrayList<ArrayList<TableAssemblyOperatorModel>> operatorGroups)
   {
      this.operatorGroups = operatorGroups;
   }

   public String[] getSubTables() {
      return subTables;
   }

   public void setSubTables(String[] subTables) {
      this.subTables = subTables;
   }

   private Map<String, SchemaTableInfoModel> schemaTableInfos;
   // Each list in operatorGroups corresponds to a single join between a pair of tables.
   private ArrayList<ArrayList<TableAssemblyOperatorModel>> operatorGroups;
   private String[] subTables;
}
