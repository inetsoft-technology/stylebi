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
package inetsoft.report.composition.execution;

import inetsoft.uql.viewsheet.SelectionTreeVSAssembly;

/**
 * Value object associating a tree and a data ref level.
 *
 * @since 13.1
 */
public class SelectionTreeLevelTuple {
   public SelectionTreeLevelTuple(SelectionTreeVSAssembly tree, int level) {
      this.tree = tree;
      this.level = level;
   }

   public SelectionTreeVSAssembly getTree() {
      return tree;
   }

   public int getLevel() {
      return level;
   }

   private final SelectionTreeVSAssembly tree;
   private final int level;
}
