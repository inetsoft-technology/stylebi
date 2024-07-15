/*
 * inetsoft-web - StyleBI is a business intelligence web application.
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
import { DependencyType } from "../../../data/ws/dependency-type";

export const DEPENDENCY_TYPE_OVERLAY_ID = "connection-dependency-type";
const dependencyLabels = new Map<DependencyType, string>();

dependencyLabels.set(DependencyType.EXPRESSION,
`<div class="formula-icon icon-size-medium"
      title="_#(js:Expression)"></div>`);
dependencyLabels.set(DependencyType.GROUPING,
`<div class="grouping-icon icon-size-medium"
      title="_#(js:Grouping)"></div>`);
dependencyLabels.set(DependencyType.SUBQUERY_FILTER,
`<div class="condition-with-query-icon icon-size-medium"
      title="_#(js:Subquery Filter)"></div>`);
dependencyLabels.set(DependencyType.VARIABLE_FILTER,
`<div class="condition-with-variable-icon icon-size-medium"
      title="_#(js:Variable Filter)"></div>`);
dependencyLabels.set(DependencyType.INNER_JOIN,
`<div class="inner-join-icon icon-size-medium"
      title="_#(js:Inner Join)"></div>`);
dependencyLabels.set(DependencyType.OUTER_JOIN,
`<div class="outer-join-icon icon-size-medium"
      title="_#(js:Outer Join)"></div>`);
dependencyLabels.set(DependencyType.SECONDARY_JOIN,
`<div class="other-join-icon icon-size-medium"
      title="_#(js:Secondary Join)"></div>`);
dependencyLabels.set(DependencyType.CROSS_JOIN,
`<div class="cross-join-icon icon-size-medium"
      title="_#(js:Cross Join)"></div>`);
dependencyLabels.set(DependencyType.BASE_CONCATENATED_TABLE,
`<div class="concat-icon icon-size-medium"
      title="_#(js:Base Concatenated Table)"></div>`);
dependencyLabels.set(DependencyType.UNION,
`<div class="union-tables-icon icon-size-medium"
      title="_#(js:Union)"></div>`);
dependencyLabels.set(DependencyType.INTERSECTION,
`<div class="intersect-tables-icon icon-size-medium"
      title="_#(js:Intersection)"></div>`);
dependencyLabels.set(DependencyType.MINUS,
`<div class="minus-tables-icon icon-size-medium"
      title="_#(js:Minus)"></div>`);
dependencyLabels.set(DependencyType.MERGE,
`<div class="merge-join-icon icon-size-medium"
      title="_#(js:Merge)"></div>`);
dependencyLabels.set(DependencyType.MIRROR,
`<div class="mirror-icon icon-size-medium"
      title="_#(js:Mirror)"></div>`);
dependencyLabels.set(DependencyType.ROTATION,
`<div class="rotate-icon icon-size-medium"
      title="_#(js:Rotation)"></div>`);
dependencyLabels.set(DependencyType.UNPIVOT,
`<div class="unpivot-icon icon-size-medium"
      title="_#(js:Unpivot)"></div>`);
dependencyLabels.set(DependencyType.SQL_CONDITION_VARIABLE,
`<div class="condition-with-variable-icon icon-size-medium"
      title="_#(js:SQL Condition Variable)"></div>`);
dependencyLabels.set(DependencyType.TABULAR_SUBQUERY,
`<div class="tabular-subquery-icon icon-size-medium"
      title="_#(js:Tabular Subquery)"></div>`);
dependencyLabels.set(DependencyType.VARIABLE_SUBQUERY,
`<div class="variable-with-query-icon icon-size-medium"
      title="_#(js:Variable Subquery)"></div>`);
dependencyLabels.set(DependencyType.JOIN,
   `<div class="join-icon cursor-pointer icon-size-medium"
      title="_#(js:Join)"></div>`);

export namespace JSPlumbDependencyTypeOverlays {
   export function getOverlayLabel(type: DependencyType) {
      return dependencyLabels.get(type);
   }

   export function getOverlays(dependencies: (keyof typeof DependencyType)[]): any {
      if(dependencies.length === 0) {
         return null;
      }

      let label = "";

      for(let dep of dependencies) {
         label += dependencyLabels.get(DependencyType[dep]);
      }

      return [
         "Label", {
            // label becomes innerhtml of overlay
            label: label,
            id: DEPENDENCY_TYPE_OVERLAY_ID,
            cssClass: "dependency-type-overlay-container"
         }
      ];
   }
}
