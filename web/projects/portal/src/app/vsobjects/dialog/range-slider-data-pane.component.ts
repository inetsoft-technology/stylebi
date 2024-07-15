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
import {
   AfterViewInit,
   ChangeDetectorRef,
   Component,
   EventEmitter,
   Input,
   Output,
   ViewChild
} from "@angular/core";
import { SourceInfoType } from "../../binding/data/source-info-type";
import { DataRefType } from "../../common/data/data-ref-type";
import { XSchema } from "../../common/data/xschema";
import { GuiTool } from "../../common/util/gui-tool";
import { TimeInfoType } from "../../composer/data/vs/time-info-type";
import { TreeDataPane } from "../../composer/dialog/vs/tree-data-pane";
import { TreeNodeModel } from "../../widget/tree/tree-node-model";
import { TreeComponent } from "../../widget/tree/tree.component";
import { OutputColumnRefModel } from "../model/output-column-ref-model";
import { RangeSliderDataPaneModel } from "../model/range-slider-data-pane-model";
import { RangeSliderSizePaneModel } from "../model/range-slider-size-pane-model";
import { VSUtil } from "../util/vs-util";
import { DataTreeValidatorService } from "./data-tree-validator.service";

interface CompositeNodeInfo {
   readonly label: string;
   readonly column: OutputColumnRefModel;
}

@Component({
   selector: "range-slider-data-pane",
   templateUrl: "range-slider-data-pane.component.html"
})
export class RangeSliderDataPane extends TreeDataPane implements AfterViewInit {
   @Input() model: RangeSliderDataPaneModel;
   @Input() sizeModel: RangeSliderSizePaneModel;
   @Output() validData = new EventEmitter<boolean>();
   @ViewChild(TreeComponent) tree: TreeComponent;
   compositeNodes: CompositeNodeInfo[] = [];
   selectedCompositeNodeIndex: number = -1;
   selectedTreeCompositeNodes: TreeNodeModel[] = [];
   cubeString = "__inetsoft_cube_";
   levelTooltips: string[] = [];

   constructor(private changeDetectorRef: ChangeDetectorRef,
               protected treeValidator: DataTreeValidatorService)
   {
      super(treeValidator);
   }

   ngAfterViewInit(): void {
      if(this.model.selectedColumns && this.model.selectedColumns.length > 0) {
         if(!this.model.composite) {
            const node = this.tree.getNodeByData("column", this.model.selectedColumns[0]);
            this.tree.selectAndExpandToNode(node);
         }
         else {
            for(let selectedColumn of this.model.selectedColumns) {
               let node: TreeNodeModel = this.tree.getNodeByData("column", selectedColumn);

               if(node) {
                  this.tree.expandToNode(node);
                  const column: OutputColumnRefModel = node.data;
                  this.compositeNodes.push({label: node.label, column});
                  this.levelTooltips.push(node.tooltip);
               }
            }

            // don't submit hidden (non-existent) columns. this can happen if source is changed
            // and the old fbinding columns no longer exist
            this.model.selectedColumns = this.model.selectedColumns
               .filter(c => {
                  return this.compositeNodes
                     .findIndex((n) => n.column.attribute === c.attribute) >= 0;
               });
         }

         this.changeDetectorRef.detectChanges();
      }
   }

   /**
    * Reset selected objects, called when switch single/composite type
    */
   switchType(isComposite: boolean): void {
      if(isComposite !== this.model.composite) {
         this.sizeModel.length = 3;
         this.model.selectedTable = null;
         this.tree.collapseNode(isComposite ?
            this.model.compositeTargetTree : this.model.targetTree);
         this.model.selectedColumns = [];
         this.selectedTreeCompositeNodes = [];
         this.compositeNodes = [];
         this.levelTooltips = [];
         this.selectedCompositeNodeIndex = -1;
         this.model.composite = isComposite;
         this.updateSourceType();
      }
   }

   /**
    * Called when user select node on table tree in single select
    * @param node The selected tree node
    */
   selectColumn(node: TreeNodeModel): void {
      if(!node) {
         return;
      }

      this.validData.emit(node.type !== "columnNode");

      if(node.type === "columnNode") {
         // If cube column, set table to table property, else parent model
         this.model.selectedTable = this.getParentFolderLabel(node);
         this.model.selectedColumns = [node.data];
         let cRef: OutputColumnRefModel = node.data;
         let type: string = cRef.dataType;

         if((cRef.refType & DataRefType.CUBE_DIMENSION) == DataRefType.CUBE_DIMENSION) {
            this.model.selectedTable = node.data.table;
            this.sizeModel.rangeType = TimeInfoType.MEMBER;
            this.sizeModel.rangeSize = 5;
         }
         else if(XSchema.isNumericType(type)) {
            this.sizeModel.rangeType = TimeInfoType.NUMBER;
         }
         else if(type == XSchema.TIME) {
            this.sizeModel.rangeType = TimeInfoType.MINUTE_OF_DAY;
            this.sizeModel.rangeSize = 60;
         }
         else if(XSchema.isDateType(type)) {
            this.sizeModel.rangeType = TimeInfoType.MONTH;
            this.sizeModel.rangeSize = 3;
         }
      }
      else {
         this.model.selectedColumns = [];
      }

      this.updateSourceType();
   }

   /**
    * Called when user select node on table tree in composite select
    * @param nodes The selected tree nodes
    */
   selectTreeCompositeNodes(nodes: TreeNodeModel[]): void {
      this.selectedTreeCompositeNodes = [];
      this.validData.emit(false);

      for(let node of nodes) {
         if(node.type == "columnNode" || node.type == "table" || node.type == "folder" ||
            node.type == "worksheet")
         {
            this.selectedTreeCompositeNodes.push(node);
         }
         else {
            this.selectedTreeCompositeNodes.push(<TreeNodeModel> {});
         }
      }
   }

   /**
    * Called when user select add in composite select
    */
   addCompositeNodes(): void {
      for(let node of this.selectedTreeCompositeNodes) {
         this.addCompositeNode(node);
      }
   }

   /**
    * Add a composite node to level. Called by function addCompositeNodes
    * @param {TreeNodeModel} selectedCompositeNode
    */
   private addCompositeNode(selectedCompositeNode: TreeNodeModel): void {
      if(selectedCompositeNode.type === "columnNode") {
         if(this.compositeNodes.findIndex((n) => n.label === selectedCompositeNode.label) != -1 ||
            !this.isSameSourceOfTwoNodes(selectedCompositeNode))
         {
            return;
         }

         const column: OutputColumnRefModel = selectedCompositeNode.data;
         this.compositeNodes.push({label: selectedCompositeNode.label, column});
         this.levelTooltips.push(selectedCompositeNode.tooltip);
         this.selectedCompositeNodeIndex = this.compositeNodes.length - 1;
         // If cube column, set table to table property, else parent model
         this.model.selectedTable = selectedCompositeNode.data.table ?
            selectedCompositeNode.data.table : this.getParentFolderLabel(selectedCompositeNode);
         this.model.selectedColumns = [...this.model.selectedColumns, column];
      }
      else {
         let tableString: string;

         for(let child of selectedCompositeNode.children) {
            if(this.compositeNodes.findIndex((n) => n.label === child.label) != -1 ||
               !this.isSameSourceOfTwoNodes(selectedCompositeNode))
            {
               continue;
            }

            tableString = child.data.table;
            const column: OutputColumnRefModel = child.data;
            this.compositeNodes.push({label: child.label, column});
            this.levelTooltips.push(child.tooltip);
            this.model.selectedColumns = [...this.model.selectedColumns, column];
         }

         if(this.model.selectedColumns.length > 0) {
            this.selectedCompositeNodeIndex = this.compositeNodes.length - 1;
            this.model.selectedTable = tableString ? tableString : selectedCompositeNode.label;
         }
         else {
            this.selectedCompositeNodeIndex = -1;
         }
      }

      this.updateSourceType();
   }

   getParentFolder(column: TreeNodeModel): TreeNodeModel {
      let parentNode: TreeNodeModel = this.tree.getParentNode(column);
      // direct parent of selected node has to have type handled differently depending on
      // whether or not it's a descendant of a worksheet or a logic model
      let topAncestor: TreeNodeModel = this.tree.getTopAncestor("data", column.data);
      let parentType: string;

      if(topAncestor.type == "worksheet" || topAncestor.type == "table" || column.data &&
         column.data.properties && column.data.properties["type"] == SourceInfoType.VS_ASSEMBLY)
      {
         parentType = "table";
      }
      else{ // Cubes or Logic Model
         parentType = "folder";
      }

      while(parentType != parentNode.type) {
         parentNode = this.tree.getParentNode(parentNode);
      }

      return parentNode;
   }

   getParentFolderLabel(column: TreeNodeModel): string {
      let parentNode: TreeNodeModel = this.getParentFolder(column);
      return parentNode == null ? null : parentNode.label;
   }

   updateSourceType(): void {
      if(!this.model) {
         return;
      }

      if(this.model.selectedColumns && this.model.selectedColumns.length > 0) {
         let node = this.tree.getNodeByData("column", this.model.selectedColumns[0]);
         let tnode = this.getParentFolder(node);
         let pnode = tnode != null ? this.tree.getParentNode(tnode) : null;
         this.model.assemblySource = pnode != null && pnode.label == "_#(js:Components)";
      }
      else {
         this.model.assemblySource = false;
      }
   }

   selectCompositeNode(i: number): void {
      this.selectedCompositeNodeIndex = i;
   }

   /**
    * Check whether the columns in level and selected columns in table have same source
    */
   isSameSource(): boolean {
      for(let node of this.selectedTreeCompositeNodes) {
         if(!this.isSameSourceOfTwoNodes(node)) {
            return false;
         }
      }

      return true;
   }

   /**
    * Checked whether input column has same source with columns in level.
    * Called by function isSameSource
    * @param {TreeNodeModel} selectedNode
    * @returns {boolean}
    */
   private isSameSourceOfTwoNodes(selectedNode: TreeNodeModel): boolean {
      if(selectedNode && selectedNode.type) {
         // If select table/folder, check if has children that are column nodes
         if((selectedNode.type == "folder" || selectedNode.type == "table") &&
            selectedNode.children && selectedNode.children.length > 0 &&
            selectedNode.children[0].type == "columnNode")
         {
            //if no selected columns, return true
            if(this.model.selectedColumns.length == 0) {
               return true;
            }

            //if is table node, check if it is same table as current selected table
            if(selectedNode.type == "table" &&
               selectedNode.data == VSUtil.getTableName(this.model.selectedTable))
            {
               return true;
            }

            //if is folder, check if is same folder as current selected nodes
            if(selectedNode ==
               this.tree.getParentNodeByData(this.model.selectedColumns[0], selectedNode))
            {
               return true;
            }
         }
         else if(selectedNode.type == "columnNode") {
            if(this.compositeNodes.length == 0) {
               return true;
            }
            //Check if it is a cube table and tables of nodes match
            else if(this.model.selectedTable &&
                    VSUtil.getTableName(this.model.selectedTable).indexOf(this.cubeString) != -1)
            {
               //Check if parent nodes of selected node and current nodes are the same
               if(this.tree.getParentNodeByData(this.model.selectedColumns[0]) ==
                  this.tree.getParentNode(selectedNode))
               {
                  return true;
               }
            }
            //check if node has same parent model as current selected table
            else if(this.getParentTable(selectedNode, this.tree) == this.model.selectedTable) {
               return true;
            }
         }
      }

      return false;
   }

   deleteCompositeNode(): void {
      this.model.selectedColumns.splice(this.selectedCompositeNodeIndex, 1);
      this.compositeNodes.splice(this.selectedCompositeNodeIndex, 1);
      this.levelTooltips.splice(this.selectedCompositeNodeIndex, 1);
      this.model.selectedColumns = [...this.model.selectedColumns];

      if(this.selectedCompositeNodeIndex >= this.compositeNodes.length) {
         this.selectedCompositeNodeIndex = this.compositeNodes.length - 1;
      }
   }

   moveNodeUp(): void {
      const tempNode = this.model.selectedColumns[this.selectedCompositeNodeIndex];
      this.model.selectedColumns[this.selectedCompositeNodeIndex] = this.model.selectedColumns[this.selectedCompositeNodeIndex - 1];
      this.model.selectedColumns[this.selectedCompositeNodeIndex - 1] = tempNode;
      this.model.selectedColumns = [...this.model.selectedColumns];

      const tempCompositeNode = this.compositeNodes[this.selectedCompositeNodeIndex];
      this.compositeNodes[this.selectedCompositeNodeIndex] = this.compositeNodes[this.selectedCompositeNodeIndex - 1];
      this.compositeNodes[this.selectedCompositeNodeIndex - 1] = tempCompositeNode;

      const tempToolTips = this.levelTooltips[this.selectedCompositeNodeIndex];
      this.levelTooltips[this.selectedCompositeNodeIndex] = this.levelTooltips[this.selectedCompositeNodeIndex - 1];
      this.levelTooltips[this.selectedCompositeNodeIndex - 1] = tempToolTips;

      this.selectedCompositeNodeIndex = this.selectedCompositeNodeIndex - 1;
   }

   moveNodeDown(): void {
      const tempNode = this.model.selectedColumns[this.selectedCompositeNodeIndex];
      this.model.selectedColumns[this.selectedCompositeNodeIndex] = this.model.selectedColumns[this.selectedCompositeNodeIndex + 1];
      this.model.selectedColumns[this.selectedCompositeNodeIndex + 1] = tempNode;
      this.model.selectedColumns = [...this.model.selectedColumns];

      const tempCompositeNode = this.compositeNodes[this.selectedCompositeNodeIndex];
      this.compositeNodes[this.selectedCompositeNodeIndex] = this.compositeNodes[this.selectedCompositeNodeIndex + 1];
      this.compositeNodes[this.selectedCompositeNodeIndex + 1] = tempCompositeNode;

      const tempToolTips = this.levelTooltips[this.selectedCompositeNodeIndex];
      this.levelTooltips[this.selectedCompositeNodeIndex] = this.levelTooltips[this.selectedCompositeNodeIndex + 1];
      this.levelTooltips[this.selectedCompositeNodeIndex + 1] = tempToolTips;

      this.selectedCompositeNodeIndex = this.selectedCompositeNodeIndex + 1;
   }

   public getCSSIcon(node: TreeNodeModel): string {
      return GuiTool.getTreeNodeIconClass(node, "");
   }

   additionalTablesChanged(additionalTables: string[]): void {
      this.model.additionalTables = additionalTables;
   }
}
