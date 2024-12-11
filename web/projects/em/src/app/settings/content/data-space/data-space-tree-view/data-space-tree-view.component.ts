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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { DataSpaceTreeDataSource } from "../data-space-tree-data-source";
import { DataSpaceTreeNode } from "../data-space-tree-node";
import { FlatTreeNode, FlatTreeNodeMenuItem } from "../../../../common/util/tree/flat-tree-model";
import { FlatTreeSelectNodeEvent } from "../../../../common/util/tree/flat-tree-view.component";

@Component({
   selector: "em-data-space-tree-view",
   templateUrl: "./data-space-tree-view.component.html",
   styleUrls: ["./data-space-tree-view.component.scss"]
})
export class DataSpaceTreeViewComponent implements OnInit {
   @Input() dataSource: DataSpaceTreeDataSource;
   @Input() smallDevice = false;
   @Input() selectedNodes: FlatTreeNode<DataSpaceTreeNode>[];
   @Input() currentNode: FlatTreeNode<DataSpaceTreeNode>;
   @Output() nodeSelected = new EventEmitter<FlatTreeSelectNodeEvent>();
   @Output() editNode = new EventEmitter<void>();
   @Output() deleteSelectedNodes = new EventEmitter<void>();
   get deleteDisabled(): boolean {
      if(!this.selectedNodes || this.selectedNodes.length == 0) {
         return true;
      }

      return this.selectedNodes.some(node => node.data.path === "/" ||
         node.data.path.toLowerCase() === "cache" ||
         node.data.path.toLowerCase() === "portal");
   }

   constructor() {
   }

   ngOnInit() {
      this.dataSource.nodeSelected().subscribe((node) => {
         this.handleNodeSelected({node: node, event: null});
      });
   }

   handleNodeSelected(evt: FlatTreeSelectNodeEvent) {
      this.nodeSelected.emit(evt);
   }

   deleteNode() {
      this.deleteSelectedNodes.emit();
   }
}
