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
import { Component, Input, ViewChild, OnInit, Output, EventEmitter } from "@angular/core";
import { SelectionTreePaneModel } from "../../data/vs/selection-tree-pane-model";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { OutputColumnRefModel } from "../../../vsobjects/model/output-column-ref-model";
import { Tool } from "../../../../../../shared/util/tool";
import { SelectionTreeColumnsPane } from "./selection-tree-columns-pane.component";
import { SelectionTreeIdPane } from "./selection-tree-id-pane.component";
import { GuiTool } from "../../../common/util/gui-tool";

@Component({
   selector: "selection-tree-pane",
   templateUrl: "selection-tree-pane.component.html",
})
export class SelectionTreePane implements OnInit {
   @Input() model: SelectionTreePaneModel;
   @Input() runtimeId: string;
   @Input() variableValues: string[];
   @Input() grayedOutValues: string[] = [];
   @Input() singleSelection: boolean;
   @Output() onAddColumn = new EventEmitter<string | string[]>();
   @ViewChild(SelectionTreeColumnsPane) columnsPane: SelectionTreeColumnsPane;
   @ViewChild(SelectionTreeIdPane) idPane: SelectionTreeIdPane;
   targetIdTree: TreeNodeModel = <TreeNodeModel> {};
   idRefs: OutputColumnRefModel[] = [];
   measureTooltips: string[] = [];
   private localAdditionalTables: string[] = [];

   ngOnInit(): void {
      this.initIdTree();
   }

   initIdTree(): void {
      this.targetIdTree = Tool.clone(this.model.targetTree);

      for(let i = 0; i < this.targetIdTree.children.length; i++) {
         if(this.targetIdTree.children[i].label == "Cubes") {
            this.targetIdTree.children.splice(i, 1);
            break;
         }
      }
      this.removeTreeColumns(this.targetIdTree);
   }

   /**
    * Reset selected objects, called when switch columns/id type
    */
   switchType(mode: number): void {
      this.model.mode = mode;

      if(mode === 1) {
         this.model.selectedTable = this.columnsPane.localTable;
         this.model.additionalTables = this.localAdditionalTables;
      }
      else {
         this.model.selectedTable = this.idPane.localTable;
         this.localAdditionalTables = this.model.additionalTables;
         this.model.additionalTables = [];
      }
   }

   //Id tree only shows tables
   removeTreeColumns(parentNode: TreeNodeModel): void {
      for(let childNode of parentNode.children) {
         if(childNode.type == "table") {
            childNode.leaf = true;
            childNode.children = [];
            continue;
         }

         this.removeTreeColumns(childNode);
      }
   }

   updateIds(refs: OutputColumnRefModel[]): void {
      if(this.model.mode != 2) {
         return;
      }

      this.idRefs = refs;

      for(let ref of this.idRefs) {
         this.measureTooltips.push(ref.description);
      }
   }

   public getCSSIcon(node: TreeNodeModel): string {
      return GuiTool.getTreeNodeIconClass(node, "");
   }

   getSelectedTables(): string[] {
      const selectedTables: string[] = [];

      if(this.model.selectedTable != null) {
         selectedTables.push(this.model.selectedTable);
      }

      selectedTables.push(...this.model.additionalTables);
      return selectedTables;
   }
}
