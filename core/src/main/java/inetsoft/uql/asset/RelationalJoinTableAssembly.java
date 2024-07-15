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
package inetsoft.uql.asset;

import inetsoft.util.Tool;
import inetsoft.web.composer.model.ws.DependencyType;

import java.util.*;

/**
 * Relational join table assembly, contains joined table assemblies and relational
 * operators (i.e. cross, inner, and outer joins).
 *
 * @version 12.3
 * @author InetSoft Technology Corp
 */
public class RelationalJoinTableAssembly extends AbstractJoinTableAssembly {

   public RelationalJoinTableAssembly() {
      super();
   }

   public RelationalJoinTableAssembly(Worksheet ws, String name,
                                      TableAssembly[] tables, TableAssemblyOperator[] operators)
   {
      super(ws, name, tables, operators);
   }

   @Override
   public void getAugmentedDependeds(Map<String, Set<DependencyType>> dependeds) {
      super.getAugmentedDependeds(dependeds);

      Enumeration tbls = getOperatorTables();

      while(tbls.hasMoreElements()) {
         String[] tables = (String[]) tbls.nextElement();
         String leftTable = tables[0];
         String rightTable = tables[1];
         TableAssemblyOperator top = getOperator(leftTable, rightTable);

         for(TableAssemblyOperator.Operator operator : top.getOperators()) {
            int operation = operator.getOperation();

            if((operation & TableAssemblyOperator.LEFT_JOIN) == TableAssemblyOperator.LEFT_JOIN) {
               addToDependencyTypes(dependeds, leftTable, DependencyType.OUTER_JOIN);
               addToDependencyTypes(dependeds, rightTable, DependencyType.INNER_JOIN);
            }

            if((operation & TableAssemblyOperator.RIGHT_JOIN) == TableAssemblyOperator.RIGHT_JOIN) {
               addToDependencyTypes(dependeds, leftTable, DependencyType.INNER_JOIN);
               addToDependencyTypes(dependeds, rightTable, DependencyType.OUTER_JOIN);
            }

            if((operation & TableAssemblyOperator.GREATER_JOIN) == TableAssemblyOperator.GREATER_JOIN ||
               (operation & TableAssemblyOperator.LESS_JOIN) == TableAssemblyOperator.LESS_JOIN ||
               operation == TableAssemblyOperator.NOT_EQUAL_JOIN)
            {
               addToDependencyTypes(dependeds, leftTable, DependencyType.SECONDARY_JOIN);
               addToDependencyTypes(dependeds, rightTable, DependencyType.SECONDARY_JOIN);
            }
            else if(operation == TableAssemblyOperator.INNER_JOIN) {
               addToDependencyTypes(dependeds, leftTable, DependencyType.INNER_JOIN);
               addToDependencyTypes(dependeds, rightTable, DependencyType.INNER_JOIN);
            }
            else if(operation == TableAssemblyOperator.CROSS_JOIN) {
               addToDependencyTypes(dependeds, leftTable, DependencyType.CROSS_JOIN);
               addToDependencyTypes(dependeds, rightTable, DependencyType.CROSS_JOIN);
            }
         }
      }

      for(String tname : tnames) {
         Set<DependencyType> dependencyTypes = dependeds.get(tname);
         boolean found = false;

         // Keep only the DependencyType of the highest priority as defined in dependencyTypeOrder
         for(DependencyType dependencyType : dependencyTypeOrder) {
            if(found) {
               dependencyTypes.remove(dependencyType);
            }
            else if(dependencyTypes.contains(dependencyType)) {
               found = true;
            }
         }
      }
   }

   /**
    * Remove the table cross joins.
    *
    * @return the rest of the tables.
    */
   public String[] removeCrossJoinOperator() {
      List<String> tables = getCompositeTableInfo().removeCrossJoinOperator();
      return updateTablesByOperator(tables);
   }

   /**
    * Update the tables by operators.
    *
    * @return the rest of the tables.
    */
   private String[] updateTablesByOperator(List<String> removedTables) {
      if(removedTables == null || removedTables.isEmpty()) {
         return getTableNames();
      }

      TableAssembly[] tableAssemblies = getTableAssemblies();

      if(tableAssemblies == null) {
         return getTableNames();
      }

      List<TableAssembly> newTableAssemblies = new ArrayList<>();

      for(TableAssembly table : tableAssemblies) {
         if(table == null) {
            continue;
         }

         if(!removedTables.contains(table.getName()) || Tool.equals(table, getName()) ||
            (getOperator(table.getName()) != null &&
            getOperator(table.getName()).getOperatorCount() > 0))
         {
            newTableAssemblies.add(table);
         }
      }

      setTableAssemblies(newTableAssemblies.toArray(new TableAssembly[0]));

      return newTableAssemblies.stream().map(TableAssembly::getName).toArray(String[]::new);
   }

   /**
    * Remove the table cross joins.
    *
    * @return the rest of the tables.
    */
   public String[] removeOperator(String ltable, String rtable,
                                  TableAssemblyOperator.Operator removeOperator)
   {
      TableAssemblyOperator assemblyOperator = getOperator(ltable, rtable);
      boolean removed = false;

      if(assemblyOperator != null) {
         for(int i = assemblyOperator.getOperatorCount() - 1; i >= 0; i--) {
            TableAssemblyOperator.Operator operator = assemblyOperator.getOperator(i);

            if(operator == null) {
               continue;
            }

            if(removeOperator.equals(operator)) {
               assemblyOperator.removeOperator(i);
               removed = true;
            }
         }
      }

     if(!removed) {
        return getTableNames();
     }

     List<String> removedTables = new ArrayList<>();
     removedTables.add(ltable);
     removedTables.add(rtable);

     return updateTablesByOperator(removedTables);
   }

   // Outer join > inner join > secondary join > cross join
   private static final DependencyType[] dependencyTypeOrder = {
      DependencyType.OUTER_JOIN,
      DependencyType.INNER_JOIN,
      DependencyType.SECONDARY_JOIN,
      DependencyType.CROSS_JOIN
   };
}
