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
import { AbstractTableAssembly } from "./abstract-table-assembly";
import { BoundTableAssembly } from "./bound-table-assembly";
import { ComposedTableAssembly } from "./composed-table-assembly";
import { CompositeTableAssembly } from "./composite-table-assembly";
import { ConcatenatedTableAssembly } from "./concatenated-table-assembly";
import { CubeTableAssembly } from "./cube-table-assembly";
import { DataTableAssembly } from "./data-table-assembly";
import { EmbeddedTableAssembly } from "./embedded-table-assembly";
import { MergeJoinTableAssembly } from "./merge-join-table-assembly";
import { MirrorTableAssembly } from "./mirror-table-assembly";
import { PhysicalBoundTableAssembly } from "./physical-bound-table-assembly";
import { QueryBoundTableAssembly } from "./query-bound-table-assembly";
import { RelationalJoinTableAssembly } from "./relational-join-table-assembly";
import { RotatedTableAssembly } from "./rotated-table-assembly";
import { SnapshotEmbeddedTableAssembly } from "./snapshot-embedded-table-assembly";
import { SQLBoundTableAssembly } from "./sql-bound-table-assembly";
import { TabularTableAssembly } from "./tabular-table-assembly";
import { UnpivotTableAssembly } from "./unpivot-table-assembly";
import { WSCompositeTableAssembly } from "./ws-composite-table-assembly";
import { WSConcatenatedTableAssembly } from "./ws-concatenated-table-assembly";
import { WSTableAssembly } from "./ws-table-assembly";

export class TableAssemblyFactory {
   public static getTables(tables: WSTableAssembly[]): AbstractTableAssembly[] {
      let res: AbstractTableAssembly[] = [];

      for(let table of tables) {
         res.push(TableAssemblyFactory.getTable(table));
      }

      return res;
   }

   public static getTable(table: WSTableAssembly): AbstractTableAssembly {
      let instance: AbstractTableAssembly;

      switch(table.tableClassType) {
         case "BoundTableAssembly":
            instance = new BoundTableAssembly(table);
            break;
         case "ComposedTableAssembly":
            instance = new ComposedTableAssembly(table);
            break;
         case "CompositeTableAssembly":
            instance = new CompositeTableAssembly(table as WSCompositeTableAssembly);
            break;
         case "ConcatenatedTableAssembly":
            instance = new ConcatenatedTableAssembly(table as WSConcatenatedTableAssembly);
            break;
         case "CubeTableAssembly":
            instance = new CubeTableAssembly(table);
            break;
         case "DataTableAssembly":
            instance = new DataTableAssembly(table);
            break;
         case "EmbeddedTableAssembly":
            instance = new EmbeddedTableAssembly(table);
            break;
         case "MergeJoinTableAssembly":
            instance = new MergeJoinTableAssembly(table as WSCompositeTableAssembly);
            break;
         case "MirrorTableAssembly":
            instance = new MirrorTableAssembly(table);
            break;
         case "PhysicalBoundTableAssembly":
            instance = new PhysicalBoundTableAssembly(table);
            break;
         case "QueryBoundTableAssembly":
            instance = new QueryBoundTableAssembly(table);
            break;
         case "RelationalJoinTableAssembly":
            instance = new RelationalJoinTableAssembly(table as WSCompositeTableAssembly);
            break;
         case "RotatedTableAssembly":
            instance = new RotatedTableAssembly(table);
            break;
         case "SnapshotEmbeddedTableAssembly":
            instance = new SnapshotEmbeddedTableAssembly(table);
            break;
         case "SQLBoundTableAssembly":
            instance = new SQLBoundTableAssembly(table);
            break;
         case "TabularTableAssembly":
            instance = new TabularTableAssembly(table);
            break;
         case "UnpivotTableAssembly":
            instance = new UnpivotTableAssembly(table);
            break;
         default:
            console.warn("No class found for " + table.tableClassType);
            instance = null;
      }

      return instance;
   }
}