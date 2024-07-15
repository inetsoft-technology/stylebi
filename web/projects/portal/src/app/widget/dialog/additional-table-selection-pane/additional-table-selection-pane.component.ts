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
import {
   ChangeDetectionStrategy,
   Component,
   EventEmitter,
   Input,
   OnChanges,
   Output,
   SimpleChanges
} from "@angular/core";
import { SourceInfoType } from "../../../binding/data/source-info-type";
import { AssetUtil } from "../../../binding/util/asset-util";
import { DataRefType } from "../../../common/data/data-ref-type";
import { MultiSelectList } from "../../../common/util/multi-select-list";
import { OutputColumnRefModel } from "../../../vsobjects/model/output-column-ref-model";
import { TreeNodeModel } from "../../tree/tree-node-model";

@Component({
   selector: "additional-table-selection-pane",
   templateUrl: "additional-table-selection-pane.component.html",
   styleUrls: ["./additional-table-selection-pane.component.scss"],
   changeDetection: ChangeDetectionStrategy.OnPush
})
export class AdditionalTableSelectionPaneComponent implements OnChanges {
   @Input() tree: TreeNodeModel;
   @Input() selectedColumns: OutputColumnRefModel[];
   @Input() selectedTable: string;
   @Input() additionalTables: string[];
   @Output() onAdditionalTablesChanged = new EventEmitter<string[]>();
   cubeString: string = "___inetsoft_cube_";
   compatibleTables: string[];
   compatibleTableSelection = new MultiSelectList();
   additionalTableSelection = new MultiSelectList();
   columnsAreAssemblyType: boolean;
   private allTableNodes: TreeNodeModel[];

   ngOnChanges(changes: SimpleChanges): void {
      if(changes.hasOwnProperty("tree")) {
         this.allTableNodes = this.getAllTableNodes(this.tree);
      }

      if(changes.hasOwnProperty("selectedColumns") || changes.hasOwnProperty("additionalTables")) {
         this.columnsAreAssemblyType = this.areSelectedColumnsAssemblyType();
         const compatibleTableSet = this.findCompatibleTables();
         const compatibleAdditionalTables =
            this.additionalTables.filter((t) => compatibleTableSet.has(t));

         if(compatibleAdditionalTables.length !== this.additionalTables.length) {
            Promise.resolve(null)
               .then(() => this.additionalTablesChanged(compatibleAdditionalTables));
         }

         compatibleAdditionalTables.forEach((t) => compatibleTableSet.delete(t));
         this.compatibleTables = Array.from(compatibleTableSet);
         this.refreshListSelections();
      }
   }

   get selectedTableLabel(): string {
      if(this.selectedTable && this.selectedTable.startsWith(this.cubeString)) {
         return this.selectedTable.substring(this.cubeString.length);
      }

      return this.selectedTable;
   }

   additionalTablesChanged(selectedTables: string[]): void {
      this.onAdditionalTablesChanged.emit(selectedTables);
   }

   selectCompatibleTable(tableIndex: number, event: MouseEvent): void {
      this.compatibleTableSelection.selectWithEvent(tableIndex, event);
      this.additionalTableSelection.clear();
   }

   selectAdditionalTable(index: number, event: MouseEvent): void {
      this.additionalTableSelection.selectWithEvent(index, event);

      this.compatibleTableSelection.clear();
   }

   addAdditionalTables(): void {
      const tables = this.compatibleTableSelection.getSelectedIndices()
         .map(i => this.compatibleTables[i]);
      const additionalTables = this.additionalTables.concat(tables);
      this.additionalTablesChanged(additionalTables);
   }

   removeAdditionalTables(): void {
      let additionalTables = this.additionalTables.concat([]);
      this.additionalTableSelection.getSelectedIndices().forEach(i => additionalTables[i] = null);
      additionalTables = additionalTables.filter(t => t != null);
      this.additionalTablesChanged(additionalTables);
   }

   isMergeable(): boolean {
      return this.compatibleTables.length > 0 || this.additionalTables.length > 0;
   }

   /**
    * Find the tables compatible with the currently selected table and columns.
    */
   private findCompatibleTables(): Set<string> {
      const selectedTable = this.selectedTable;
      const columns = this.selectedColumns;
      const compatibleTables = new Set<string>();

      if(selectedTable == null || columns == null || columns.length === 0 ||
         this.columnsAreAssemblyType)
      {
         return compatibleTables;
      }

      for(let i = 0; i < columns.length; i++) {
         const column = columns[i];
         const colName = this.getColumnName(column);
         const colIsCube = (column.refType & DataRefType.CUBE) === DataRefType.CUBE;
         const dataType = column.dataType;

         for(const node of this.allTableNodes) {
            if(node.children != null) {
               let matchingCol: OutputColumnRefModel = null;
               let candidateTableName: string = null;

               for(const col of node.children) {
                  const candidateCol: OutputColumnRefModel = col.data;

                  if(candidateTableName == null) {
                     candidateTableName = this.getTableName(candidateCol);
                  }

                  const candidateColName = this.getColumnName(candidateCol);
                  const candidateIsCube =
                     (candidateCol.refType & DataRefType.CUBE) === DataRefType.CUBE;
                  const candidateDataType = candidateCol.dataType;
                  const candidateCompatible = colName === candidateColName &&
                     colIsCube === candidateIsCube &&
                     AssetUtil.isMergeable(dataType, candidateDataType);

                  if(candidateCompatible) {
                     matchingCol = candidateCol;
                     break;
                  }
               }

               if(i === 0 && matchingCol != null) {
                  const tableName = this.getTableName(matchingCol);

                  if(tableName != null) {
                     compatibleTables.add(tableName);
                  }
               }
               else if(i > 0 && matchingCol == null) {
                  compatibleTables.delete(candidateTableName);
               }
            }
         }
      }

      compatibleTables.delete(selectedTable);
      return compatibleTables;
   }

   private getColumnName(column: OutputColumnRefModel): string {
      if(column.alias != null) {
         return column.alias;
      }
      else {
         return column.attribute;
      }
   }

   private getTableName(column: OutputColumnRefModel): string {
      return column.table;
   }

   private getAllTableNodes(node: TreeNodeModel): TreeNodeModel[] {
      const tableNodes: TreeNodeModel[] = [];

      if(node.children != null) {
         for(const child of node.children) {
            tableNodes.push(...this.getAllTableNodes(child));
         }
      }

      if(node.type === "table") {
         tableNodes.push(node);
      }

      return tableNodes;
   }

   private refreshListSelections(): void {
      if(this.additionalTableSelection.size() !== this.additionalTables.length) {
         this.additionalTableSelection.setSize(this.additionalTables.length);
      }

      if(this.compatibleTableSelection.size() !== this.compatibleTables.length) {
         this.compatibleTableSelection.setSize(this.compatibleTables.length);
      }
   }

   private areSelectedColumnsAssemblyType(): boolean {
      return this.selectedColumns.findIndex(
         (c) => c.properties["type"] == SourceInfoType.VS_ASSEMBLY) >= 0;
   }
}
