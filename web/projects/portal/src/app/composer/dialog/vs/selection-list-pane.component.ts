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
   AfterViewInit,
   ChangeDetectorRef,
   Component, EventEmitter,
   Input,
   Output,
   ViewChild
} from "@angular/core";
import { GuiTool } from "../../../common/util/gui-tool";
import { OutputColumnRefModel } from "../../../vsobjects/model/output-column-ref-model";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { TreeComponent } from "../../../widget/tree/tree.component";
import { SelectionListPaneModel } from "../../data/vs/selection-list-pane-model";
import { DataTreeValidatorService } from "../../../vsobjects/dialog/data-tree-validator.service";

@Component({
   selector: "selection-list-pane",
   templateUrl: "selection-list-pane.component.html",
})
export class SelectionListPane implements AfterViewInit {
   @Input() model: SelectionListPaneModel;
   @Input() runtimeId: string;
   @Input() variableValues: string[];
   @Input() grayedOutValues: string[];
   @Output() onSelectColumn = new EventEmitter();

   @ViewChild(TreeComponent) tree: TreeComponent;

   constructor(private changeDetectorRef: ChangeDetectorRef,
               private treeValidator: DataTreeValidatorService)
   {
   }

   ngAfterViewInit(): void {
      if(this.model.selectedColumn) {
         let node: TreeNodeModel = this.tree.getNodeByData("column", this.model.selectedColumn);
         this.tree.selectAndExpandToNode(node);
         this.changeDetectorRef.detectChanges();
      }
   }

   selectNode(node: TreeNodeModel): void {
      if(node.type == "columnNode") {
         // If cube column, set table to table property, else parent table
         this.model.selectedTable = node.data.table ? node.data.table : this.getParentTable(node);
         this.model.selectedColumn = node.data;
      }
      else {
         this.model.selectedTable = null;
         this.model.selectedColumn = null;
      }

      this.onSelectColumn.emit();
   }

   expandNode(node: TreeNodeModel) {
      this.treeValidator.validateTreeNode(node, this.runtimeId);
   }

   getParentTable(column: TreeNodeModel): string {
      let parentNode: TreeNodeModel = this.tree.getParentNode(column);

      while(parentNode.type != "table") {
         parentNode = this.tree.getParentNode(parentNode);
      }

      return parentNode.data;
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

   getSelectedTables(): string[] {
      const selectedTables: string[] = [];

      if(this.model.selectedTable != null) {
         selectedTables.push(this.model.selectedTable);
      }

      selectedTables.push(...this.model.additionalTables);
      return selectedTables;
   }

   additionalTablesChanged(additionalTables: string[]): void {
      this.model.additionalTables = additionalTables;
   }
}
