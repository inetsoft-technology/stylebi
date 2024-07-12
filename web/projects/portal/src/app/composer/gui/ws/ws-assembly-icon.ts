/*
 * inetsoft-web - StyleBI is a business intelligence web application.
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
import { CompositeTableAssembly } from "../../data/ws/composite-table-assembly";
import { EmbeddedTableAssembly } from "../../data/ws/embedded-table-assembly";
import { MirrorTableAssembly } from "../../data/ws/mirror-table-assembly";
import { SQLBoundTableAssembly } from "../../data/ws/sql-bound-table-assembly";
import { WSAssembly } from "../../data/ws/ws-assembly";
import { WSTableAssembly } from "../../data/ws/ws-table-assembly";

const DEFAULT_TABLE_CSS = "ws-assembly-thumbnail-image--table-default";
const EMBEDDED_TABLE_CSS = "ws-assembly-thumbnail-image--table-embedded";
const SQL_TABLE_CSS = "ws-assembly-thumbnail-image--table-sql";
const COMPOSITE_TABLE_CSS = "ws-assembly-thumbnail-image--table-composite";
const VARIABLE_CSS = "ws-assembly-thumbnail-image--variable";
const GROUPING_CSS = "ws-assembly-thumbnail-image--grouping";

export namespace WSAssemblyIcon {
   export function getIcon(assembly: WSAssembly): string {
      if(assembly == null) {
         return null;
      }

      let icon: string = null;

      switch(assembly.classType) {
         case "TableAssembly":
            icon = getTableIcon(assembly as WSTableAssembly);
            break;
         case "VariableAssembly":
            icon = getVariableIcon();
            break;
         case "GroupingAssembly":
            icon = getGroupingIcon();
            break;
         default:
         // no-op
      }

      return icon;
   }

   function getTableIcon(table: WSTableAssembly): string {
      let icon: string = null;

      if(table instanceof EmbeddedTableAssembly) {
         icon = EMBEDDED_TABLE_CSS;
      }
      else if(table instanceof SQLBoundTableAssembly) {
         icon = SQL_TABLE_CSS;
      }
      else if(table instanceof CompositeTableAssembly || table instanceof MirrorTableAssembly) {
         icon = COMPOSITE_TABLE_CSS;
      }
      else {
         icon = DEFAULT_TABLE_CSS;
      }

      return icon;
   }

   function getVariableIcon(): string {
      return VARIABLE_CSS;
   }

   function getGroupingIcon() {
      return GROUPING_CSS;
   }
}
