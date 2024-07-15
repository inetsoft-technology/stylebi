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
import { AfterViewInit, ChangeDetectorRef, Component, Input, ViewChild } from "@angular/core";
import { AssetEntryHelper } from "../../../common/data/asset-entry-helper";
import { GuiTool } from "../../../common/util/gui-tool";
import { OutputColumnRefModel } from "../../../vsobjects/model/output-column-ref-model";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { TreeComponent } from "../../../widget/tree/tree.component";
import { CalendarDataPaneModel } from "../../data/vs/calendar-data-pane-model";

@Component({
   selector: "calendar-data-pane",
   templateUrl: "calendar-data-pane.component.html"
})
export class CalendarDataPane implements AfterViewInit {
   @Input() model: CalendarDataPaneModel;
   @ViewChild(TreeComponent) tree: TreeComponent;

   constructor(private changeDetectorRef: ChangeDetectorRef) {
   }

   ngAfterViewInit(): void {
      if(this.model.selectedColumn) {
         let node = this.tree.getNodeByData("column", this.model.selectedColumn);
         this.tree.selectAndExpandToNode(node);
         this.changeDetectorRef.detectChanges();
      }
   }

   selectColumn(column: TreeNodeModel): void {
      if(column.type == "columnNode") {
         const pnode = this.getParentTable(column);

         if(typeof pnode == "string") {
            this.model.selectedTable = pnode;
         }
         else if(pnode) {
            this.model.selectedTable = pnode.properties.table;

            if(!this.model.selectedTable) {
               this.model.selectedTable = AssetEntryHelper.getEntryName(pnode);
            }
         }
         else if(column.data && column.data.table) {
            this.model.selectedTable = column.data.table;
         }

         this.model.selectedColumn = column.data;
      }
      else {
         this.model.selectedColumn = null;
      }
   }

   getParentTable(column: TreeNodeModel): any {
      let parentNode: TreeNodeModel = this.tree.getParentNode(column);

      if(!parentNode) {
         return null;
      }

      while(parentNode && parentNode.type != "table" &&
            !(parentNode.data && parentNode.data.type == "PHYSICAL_TABLE"))
      {
         parentNode = this.tree.getParentNode(parentNode);
      }

      return parentNode ? parentNode.data : null;
   }

   public getCSSIcon(node: TreeNodeModel): string {
      return GuiTool.getTreeNodeIconClass(node, "");
   }

   getSelectedColumns(): OutputColumnRefModel[] {
      const selectedColumns: OutputColumnRefModel[] = [];

      if(this.model.selectedColumn != null) {
         selectedColumns.push(this.model.selectedColumn);
      }

      return selectedColumns;
   }

   additionalTablesChanged(additionalTables: string[]): void {
      this.model.additionalTables = additionalTables;
   }
}
