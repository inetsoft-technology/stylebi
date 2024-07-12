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
package inetsoft.uql.asset;

import inetsoft.web.composer.model.ws.DependencyType;

import java.util.Map;
import java.util.Set;

/**
 * Merge join table assembly, contains joined table assemblies and merge join operators.
 *
 * @author InetSoft Technology Corp
 * @version 12.3
 */
public class MergeJoinTableAssembly extends AbstractJoinTableAssembly {

   public MergeJoinTableAssembly() {
      super();
   }

   public MergeJoinTableAssembly(
      Worksheet ws, String name, TableAssembly[] tables,
      TableAssemblyOperator[] operators)
   {
      super(ws, name, tables, operators);
   }

   @Override
   public void getAugmentedDependeds(Map<String, Set<DependencyType>> dependeds) {
      super.getAugmentedDependeds(dependeds);

      for(String tname : tnames) {
         addToDependencyTypes(dependeds, tname, DependencyType.MERGE);
      }
   }
}
