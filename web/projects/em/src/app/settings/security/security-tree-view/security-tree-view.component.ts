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
import { FlatTreeControl } from "@angular/cdk/tree";
import {
   Component,
   EventEmitter,
   Input,
   OnChanges,
   OnDestroy,
   OnInit,
   Output,
   SimpleChanges
} from "@angular/core";
import { MatTreeFlatDataSource } from "@angular/material/tree";
import { Subscription } from "rxjs";
import { SelectionTransfer } from "../resource-permission/selection-transfer";
import { IdentityModel } from "../security-table-view/identity-model";
import { SecurityTreeDataService } from "./security-tree-data.service";
import { SecurityTreeFlattener } from "./security-tree-flattener";
import { FlatSecurityTreeNode, SecurityTreeNode } from "./security-tree-node";
import { IdentityType } from "../../../../../../shared/data/identity-type";
import {IdentityId} from "../users/identity-id";

@Component({
   selector: "em-security-tree-view",
   templateUrl: "./security-tree-view.component.html",
   styleUrls: ["./security-tree-view.component.scss"],
   providers: [SecurityTreeDataService]
})
export class SecurityTreeViewComponent implements OnInit, OnChanges, OnDestroy,
   SelectionTransfer<SecurityTreeNode>
{
   @Input() treeData: SecurityTreeNode[];
   @Input() selectedNodes: SecurityTreeNode[] = [];
   @Output() nodeSelected = new EventEmitter<SecurityTreeNode>();
   @Output() selectionChanged = new EventEmitter<SecurityTreeNode[]>();
   @Output() treeUpdated = new EventEmitter<FlatTreeControl<FlatSecurityTreeNode>>();
   public treeControl: FlatTreeControl<FlatSecurityTreeNode>;
   public treeFlattener: SecurityTreeFlattener;
   public dataSource: MatTreeFlatDataSource<SecurityTreeNode, FlatSecurityTreeNode>;
   public selectedIdentity: SecurityTreeNode;
   public filterString = "";
   private flattenTree: FlatSecurityTreeNode[];
   private subscriptions = new Subscription();
   private expandedState: FlatSecurityTreeNode[];
   onlyShowGroups: boolean = false;
   readonly nodePadding: number = 40;

   constructor(private database: SecurityTreeDataService) {
      this.treeControl =
         new FlatTreeControl<FlatSecurityTreeNode>(this._getLevel, this._isExpandable);
      this.treeFlattener = new SecurityTreeFlattener(this.transformer, this._getLevel,
         this._isExpandable, this.treeControl);
      this.dataSource = new MatTreeFlatDataSource(this.treeControl, this.treeFlattener);
   }

   ngOnInit(): void {
      this.subscriptions = new Subscription();
      this.subscriptions.add(
         this.database.dataChange
            .subscribe((data) => {
               // optimization, assigning to dataSource.data with new data takes a long time.
               // creating a new one is many times faster.
               if(this.dataSource.data != data) {
                  this.dataSource.data = [];
                  this.dataSource = new MatTreeFlatDataSource(this.treeControl, this.treeFlattener);
                  this.dataSource.data = data;
               }
            })
      );

      this.subscriptions.add(
         this.treeFlattener.flattenedDataChanged
            .subscribe((data) => {
               if(data.length === 0) {
                  this.expandedState = this.treeControl.expansionModel.selected.slice();
               }
               else {
                  this.restoreExpandedState(data, this.expandedState);
                  this.expandedState = null;
               }

               this.treeControl.dataNodes = data;
               this.flattenTree = data;
               this.treeUpdated.emit(this.treeControl);
            })
      );
   }

   public ngOnChanges(changes: SimpleChanges): void {
      if(changes.treeData && this.treeData != null) {
         this.database.initialize(this.treeData);
         this.onlyShowGroups = this.treeData.length == 1 && this.treeData[0].type == 1;
         this.filter(this.filterString);
      }
   }

   public ngOnDestroy(): void {
      this.subscriptions.unsubscribe();
   }

   restoreExpandedState(nodes: FlatSecurityTreeNode[], selected: FlatSecurityTreeNode[]): void {
      this.treeControl.expansionModel.clear();

      // Starting from the leftmost node expand the comparable node in the new list
      selected.sort((a, b) => a.level - b.level)
         .forEach((oldNode) => {
            const newNode = nodes.find((node) => oldNode.equals(node));

            if(newNode != null) {
               this.treeControl.expand(newNode);
            }
         });
   }

   sendSelection(): SecurityTreeNode[] {
      return this.selectedNodes;
   }

   receiveSelection(selection: SecurityTreeNode[]) {
      // NO-OP
   }

   public filter(filterString: string) {
      this.database.filter(filterString);
   }

   public toggleNode(node: FlatSecurityTreeNode) {
      this.treeControl.toggle(node);
      this.database.refreshTreeData();
   }

   selectNode(node: FlatSecurityTreeNode, event: MouseEvent) {
      const nodeData = node.getData();

      // For single click, set the selected identity
      if(!event.ctrlKey && !event.metaKey && !event.shiftKey) {
            this.selectedIdentity = nodeData;
            this.selectedNodes = [nodeData];
            this.nodeSelected.emit(this.selectedIdentity);
      }

      if(event.shiftKey) {
         if(this.selectedNodes.length == 0) {
            this.selectedNodes.push(nodeData);
         }
         else {
            // Get the last item in selectedNodes
            const last = this.selectedNodes[this.selectedNodes.length - 1];
            this.selectedNodes = [];

            const newIndex = this.flattenTree.indexOf(node);
            const lastIndex = this.flattenTree.findIndex(treeNode =>
               treeNode.getData().identityID.name === last.identityID.name && treeNode.getData().type === last.type);

            this.selectedNodes = this.flattenTree
               .slice(Math.min(newIndex, lastIndex) + 1, Math.max(newIndex, lastIndex))
               .map(n => n.getData());

            this.selectedNodes.push(node.getData());

            // Keep the last item in selectedNodes unchanged
            if(newIndex != lastIndex) {
               this.selectedNodes.push(this.flattenTree[lastIndex].getData());
            }
         }
      }
      else if(event.ctrlKey) {
         const index = this.selectedNodes.indexOf(nodeData);

         if(index >= 0) {
            this.selectedNodes.splice(index, 1);
         }
         else {
            this.selectedNodes.push(nodeData);
         }
      }

      this.selectionChanged.emit(this.selectedNodes.slice(0));
   }

   isSelected(node: FlatSecurityTreeNode): boolean {
      return node && this.selectedNodes
            .some((selectedNode) => selectedNode.identityID.name === node.getData().identityID.name &&
            selectedNode.type === node.getData().type);
   }

   public hasChild(_nodeData: FlatSecurityTreeNode) {
      if(this.onlyShowGroups) {
         return _nodeData.expandable &&
            _nodeData.getData().children.some(node => node.type == IdentityType.GROUP);
      }

      return _nodeData.expandable;
   }

   public isNotOrganization(type: number) {
      return type != IdentityType.ORGANIZATION;
   }

   public matchType(_nodeData: FlatSecurityTreeNode) {
      if(!this.onlyShowGroups) {
         return true;
      }

      return _nodeData.type == IdentityType.GROUP;
   }

   private transformer(node: SecurityTreeNode, level: number) {
      return new FlatSecurityTreeNode(node.hasChildren(), node, level);
   }

   private _getLevel(node: FlatSecurityTreeNode) {
      return node.level;
   }

   private _isExpandable(node: FlatSecurityTreeNode) {
      return node.expandable;
   }

   private isIdentityFolder(node: SecurityTreeNode): boolean {
      return ["_#(js:Users)", "_#(js:Groups)", "_#(js:Roles)"].includes(node.identityID.name);
   }

   trackByFn(index, node: FlatSecurityTreeNode) {
      return index;
   }

   dragStart(event: DragEvent, node: FlatSecurityTreeNode): boolean {
      const nodeData = node.getData();
      const singleSelect = this.selectedNodes.length === 1 && this.selectedNodes[0] === this.selectedIdentity;
      let dragNodes = singleSelect ? [] : this.selectedNodes.slice(0);

      if(!dragNodes.includes(nodeData)) {
         dragNodes.push(nodeData);
      }

      dragNodes = dragNodes.filter(n => !n.readOnly);

      if(dragNodes.length > 0) {
         const dragModels = dragNodes.filter(dragNode => !this.isIdentityFolder(dragNode))
            .map(dragNode => <IdentityModel>{identityID: {name: dragNode.identityID.name, organization: dragNode.organization}, type: dragNode.type});
         event.dataTransfer.setData("text", JSON.stringify(dragModels));
         return true;
      }
      else {
         event.preventDefault();
         return false;
      }
   }

   public clearTreeData() {
      if(this.dataSource) {
         this.dataSource.data = [];
      }
   }
}
