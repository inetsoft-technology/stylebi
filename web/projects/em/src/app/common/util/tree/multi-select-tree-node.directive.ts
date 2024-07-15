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
   Directive,
   EventEmitter,
   HostListener,
   Input,
   Output
} from "@angular/core";
import { FlatTreeNode } from "./flat-tree-model";
import { FlatTreeSelectNodeEvent } from "./flat-tree-view.component";
import { FlatTreeDataSource } from "./flat-tree-data-source";
import { FlatTreeControl } from "@angular/cdk/tree";

@Directive({
   selector: "[emMultiSelectTree]"
})
export class MultiSelectTreeNodeDirective {
   @Input() tree: FlatTreeDataSource<any, any>;
   @Input() currentSelection: FlatTreeNode[];
   @Input() multiTreeControl: FlatTreeControl<FlatTreeNode>;
   @Output() nodesSelected = new EventEmitter<FlatTreeNode[]>();

   @HostListener("nodeSelected", ["$event"])
   handleSelectNodeEvent(evt: FlatTreeSelectNodeEvent) {
      const nodeData = evt.node;
      const event: MouseEvent = evt.event;

      if(!event) {
         this.currentSelection = [nodeData];
         this.nodesSelected.emit(this.currentSelection);
         return;
      }

      let newSelection: FlatTreeNode[] = this.currentSelection.slice(0);

      if(!event) {
         this.currentSelection = [nodeData];
         this.nodesSelected.emit(this.currentSelection);
         return;
      }

      if(event.type === "dragstart") {
         if(!newSelection.some((n) => n.label === nodeData.label &&
            n.data === nodeData.data))
         {
            newSelection.push(nodeData);
         }
      }
      else if(!event.ctrlKey && !event.metaKey && !event.shiftKey) {
         newSelection = [nodeData];
      }
      else if(event.shiftKey) {
         if(newSelection.length == 0) {
            newSelection.push(nodeData);
         }
         else {
            // Get the last item in selectedNodes
            const last = newSelection[newSelection.length - 1];
            let data = !!this.multiTreeControl ? this.multiTreeControl.dataNodes : this.tree.data;
            const newIndex = data.indexOf(nodeData);
            const lastIndex = data.findIndex(treeNode =>
               treeNode.label === last.label && treeNode.data === last.data);

            newSelection = data
               .slice(Math.min(newIndex, lastIndex) + 1, Math.max(newIndex, lastIndex));

            newSelection.push(nodeData);

            // Keep the last item in selectedNodes unchanged
            if(newIndex != lastIndex) {
               newSelection.push(data[lastIndex]);
            }
         }
      }
      else if(event.ctrlKey) {
         const index = newSelection.findIndex((n) => n.data == nodeData.data &&
            n.label == nodeData.label && n.level == nodeData.level);

         if(index >= 0) {
            newSelection.splice(index, 1);
         }
         else {
            newSelection.push(nodeData);
         }
      }

      this.nodesSelected.emit(newSelection);
   }
}
