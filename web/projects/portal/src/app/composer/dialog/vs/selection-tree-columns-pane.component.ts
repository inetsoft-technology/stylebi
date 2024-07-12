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
import {
   AfterViewInit,
   ChangeDetectorRef,
   Component, EventEmitter,
   Input, Output,
   ViewChild
} from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ComponentTool } from "../../../common/util/component-tool";
import { OutputColumnRefModel } from "../../../vsobjects/model/output-column-ref-model";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { TreeComponent } from "../../../widget/tree/tree.component";
import { SelectionTreePaneModel } from "../../data/vs/selection-tree-pane-model";
import { VSUtil } from "../../../vsobjects/util/vs-util";
import { TreeDataPane } from "./tree-data-pane";
import { DataTreeValidatorService } from "../../../vsobjects/dialog/data-tree-validator.service";

@Component({
   selector: "selection-tree-columns-pane",
   templateUrl: "selection-tree-columns-pane.component.html",
})
export class SelectionTreeColumnsPane extends TreeDataPane implements AfterViewInit {
   @Input() model: SelectionTreePaneModel;
   @Input() variableValues: string[];
   @Input() iconFunction: (node: TreeNodeModel) => string;
   @Output() onAddColumn = new EventEmitter<string | string[]>();
   @ViewChild("columnsTree") columnsTree: TreeComponent;
   cubeString: string = "__inetsoft_cube_";
   levelNames: string[] = [];
   levelNameToolTips: string[] = [];
   selectedLevelNameIndex: number = -1;
   selectedLevelNode: TreeNodeModel = <TreeNodeModel> {};
   localTable: string;

   constructor(private changeDetectorRef: ChangeDetectorRef,
               private modalService: NgbModal,
               protected treeValidator: DataTreeValidatorService)
   {
      super(treeValidator);
   }

   ngAfterViewInit(): void {
      if(this.model.mode == 1 && this.model.selectedColumns &&
         this.model.selectedColumns.length > 0)
      {
         for(let selectedColumn of this.model.selectedColumns) {
            let node: TreeNodeModel = this.columnsTree.getNodeByData("column", selectedColumn);

            if(node) {
               this.columnsTree.expandToNode(node);
               this.levelNames.push(node.label);
               this.levelNameToolTips.push(node.tooltip);
            }
         }

         this.localTable = this.model.selectedTable;
         this.changeDetectorRef.detectChanges();
      }
   }

   /**
    * Called when user select node on table tree in columns select
    * @param node The selected tree node
    */
   selectLevelColumn(node: TreeNodeModel): void {
      if(!!node && (node.type == "columnNode" || node.type == "table" || node.type == "folder")) {
         this.selectedLevelNode = node;
      }
      else {
         this.selectedLevelNode = <TreeNodeModel> {};
      }
   }

   /**
    * Called when user select add in columns levels
    */
   addLevelNode(): void {
      if(this.selectedLevelNode.type == "columnNode") {
         if(this.levelNames.indexOf(this.selectedLevelNode.label) != -1) {
            return;
         }

         let parentNode: TreeNodeModel = this.columnsTree.getParentNode(this.selectedLevelNode);

         // If adding cube column, check if they added all higher level nodes first
         // Bug #18998 When calculated field is added under logic model,
         // logic model type is folder so we should not check high levels at this case.
         if(parentNode.type == "folder" && (!parentNode.data || parentNode.data.type == "FOLDER")) {
            let index = parentNode.children.indexOf(this.selectedLevelNode);

            if(index > 0 && index != this.model.selectedColumns.length) {
               ComponentTool.showMessageDialog(this.modalService,
                  "Add Level Error", "You must add all higher levels of the dimension first.");
               return;
            }
         }

         this.levelNames.push(this.selectedLevelNode.label);
         this.levelNameToolTips.push(this.selectedLevelNode.tooltip);
         this.selectedLevelNameIndex = this.levelNames.length - 1;
         // If cube column, set table to table property, else parent table
         this.model.selectedTable = this.selectedLevelNode.data.table
            ? this.selectedLevelNode.data.table
            : this.getParentTable(this.selectedLevelNode, this.columnsTree);
         this.localTable = this.model.selectedTable;
         const column = this.createTableNode(this.selectedLevelNode.data);
         this.model.selectedColumns = [...this.model.selectedColumns, column];
         this.onAddColumn.emit(this.selectedLevelNode.label);
      }
      else {
         let tableString: string;

         for(let child of this.selectedLevelNode.children) {
            if(this.levelNames.indexOf(child.label) != -1) {
               continue;
            }

            tableString = child.data.table;
            this.levelNames.push(child.label);
            this.levelNameToolTips.push(child.tooltip);
            const column = this.createTableNode(child.data);
            this.model.selectedColumns = [...this.model.selectedColumns, column];
         }

         if(this.model.selectedColumns.length > 0) {
            this.selectedLevelNameIndex = this.levelNames.length - 1;
            this.model.selectedTable = tableString ? tableString : this.selectedLevelNode.data;
            this.localTable = this.model.selectedTable;
         }
         else {
            this.selectedLevelNameIndex = -1;
         }
      }
   }

   deleteLevelNode(): void {
      this.model.selectedColumns.splice(this.selectedLevelNameIndex, 1);
      this.model.selectedColumns = [...this.model.selectedColumns];

      this.levelNames.splice(this.selectedLevelNameIndex, 1);
      this.levelNameToolTips.splice(this.selectedLevelNameIndex, 1);
      this.selectedLevelNameIndex = Math.min(this.selectedLevelNameIndex,
                                             this.model.selectedColumns.length - 1);
   }

   moveNodeUp(): void {
      let tempNode: OutputColumnRefModel = this.model.selectedColumns[this.selectedLevelNameIndex];
      this.model.selectedColumns[this.selectedLevelNameIndex] = this.model.selectedColumns[this.selectedLevelNameIndex - 1];
      this.model.selectedColumns[this.selectedLevelNameIndex - 1] = tempNode;
      this.model.selectedColumns = [...this.model.selectedColumns];

      let tempNodeName: string = this.levelNames[this.selectedLevelNameIndex];
      this.levelNames[this.selectedLevelNameIndex] = this.levelNames[this.selectedLevelNameIndex - 1];
      this.levelNames[this.selectedLevelNameIndex - 1] = tempNodeName;

      let tempNodeToolTip: string = this.levelNameToolTips[this.selectedLevelNameIndex];
      this.levelNameToolTips[this.selectedLevelNameIndex] = this.levelNameToolTips[this.selectedLevelNameIndex - 1];
      this.levelNameToolTips[this.selectedLevelNameIndex - 1] = tempNodeToolTip;

      this.selectedLevelNameIndex = this.selectedLevelNameIndex - 1;
   }

   moveNodeDown(): void {
      let tempNode: OutputColumnRefModel = this.model.selectedColumns[this.selectedLevelNameIndex];
      this.model.selectedColumns[this.selectedLevelNameIndex] =
         this.model.selectedColumns[this.selectedLevelNameIndex + 1];
      this.model.selectedColumns[this.selectedLevelNameIndex + 1] = tempNode;
      this.model.selectedColumns = [...this.model.selectedColumns];

      let tempNodeName: string = this.levelNames[this.selectedLevelNameIndex];
      this.levelNames[this.selectedLevelNameIndex] =
         this.levelNames[this.selectedLevelNameIndex + 1];
      this.levelNames[this.selectedLevelNameIndex + 1] = tempNodeName;

      let tempNodeToolTip: string = this.levelNameToolTips[this.selectedLevelNameIndex];
      this.levelNameToolTips[this.selectedLevelNameIndex] =
         this.levelNameToolTips[this.selectedLevelNameIndex + 1];
      this.levelNameToolTips[this.selectedLevelNameIndex + 1] = tempNodeToolTip;

      this.selectedLevelNameIndex = this.selectedLevelNameIndex + 1;
   }

   selectLevelName(i: number): void {
      this.selectedLevelNameIndex = i;
   }

   isSameSource(): boolean {
      if(this.selectedLevelNode && this.selectedLevelNode.type) {
         // If select table/folder, check if has children that are column nodes
         if((this.selectedLevelNode.type == "folder" || this.selectedLevelNode.type == "table") &&
            this.selectedLevelNode.children && this.selectedLevelNode.children.length > 0 &&
            this.selectedLevelNode.children[0].type == "columnNode")
         {
            //if no selected columns, return true
            if(this.model.selectedColumns.length == 0) {
               return true;
            }

            //if is table node, check if it is same table as current selected table
            if(this.selectedLevelNode.type == "table" &&
               this.selectedLevelNode.data == VSUtil.getTableName(this.localTable))
            {
               return true;
            }

            // if is folder, check if is same folder as current selected nodes
            if(this.selectedLevelNode ==
               this.getParentNodeByData(this.model.selectedColumns[0], this.selectedLevelNode))
            {
               return true;
            }
         }
         else if(this.selectedLevelNode.type == "columnNode") {
            if(this.levelNames.length == 0) {
               return true;
            }
            // check if it is a cube table and tables of nodes match
            else if(VSUtil.getTableName(this.localTable).indexOf(this.cubeString) != -1) {
               //Check if parent nodes of selected node and current nodes are the same
               if(this.getParentNodeByData(this.model.selectedColumns[0]) ==
                  this.columnsTree.getParentNode(this.selectedLevelNode))
               {
                  return true;
               }
            }
            // check if node has same parent table as current selected table
            else if(this.columnsTree &&
                    this.getParentTable(this.selectedLevelNode, this.columnsTree) ==
                    VSUtil.getTableName(this.localTable))
            {
               return true;
            }
         }
      }

      return false;
   }

   private getParentNodeByData(column: OutputColumnRefModel,
      parentNode?: TreeNodeModel): TreeNodeModel
   {
      return this.columnsTree.getParentNodeByData(column, parentNode,
         (data0: any, data1: any) => {
            return !data0 && !data1 || !!data0 && !!data1 &&
               data0.entity == data1.entity &&
               data0.attribute == data1.attribute &&
               data0.dataType == data1.dataType &&
               data0.name == data1.name &&
               data0.view == data1.view && data0.table == data1.table &&
               data0.alias == data1.alias && data0.refType == data1.refType &&
               data0.description == data1.description;
         });
   }

   isAddDisabled(): boolean {
      return this.selectedLevelNode.data.type == "LOGIC_MODEL";
   }

   isDeleteDisabled(): boolean {
      return this.localTable && this.localTable.indexOf(this.cubeString) != -1 &&
         this.selectedLevelNameIndex != this.levelNames.length - 1;
   }

   additionalTablesChanged(additionalTables: string[]): void {
      this.model.additionalTables = additionalTables;
   }

   // make sure table name is valid
   createTableNode(data: any): any {
      return data.table ? {...data, table: VSUtil.getTableName(data.table)} : data;
   }
}
