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
   ChangeDetectorRef,
   Component,
   HostBinding,
   Input,
   NgZone,
   OnChanges,
   OnDestroy,
   OnInit,
   Optional,
   SimpleChanges
} from "@angular/core";
import { DragEvent } from "../../../common/data/drag-event";
import { DataRef } from "../../../common/data/data-ref";
import { GuiTool } from "../../../common/util/gui-tool";
import { DomService } from "../../../widget/dom-service/dom.service";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { Viewsheet } from "../../data/vs/viewsheet";
import { toolbox, toolboxDeployed } from "./toolbox.config";
import { VirtualScrollService } from "../../../widget/tree/virtual-scroll.service";
import { map } from "rxjs/operators";
import { Subscription } from "rxjs";
import { TreeTool } from "../../../common/util/tree-tool";
import { VirtualScrollTreeDatasource } from "../../../widget/tree/virtual-scroll-tree-datasource";

@Component({
   selector: "composer-toolbox-pane",
   templateUrl: "toolbox-pane.component.html"
})
export class ToolboxPane implements OnChanges, OnInit, OnDestroy {
   @HostBinding("hidden")
   @Input() inactive: boolean;
   @Input() deployed: boolean;
   @Input() focusedViewsheet: Viewsheet;
   @Input() grayedOutFields: DataRef[];
   @Input() containerView: any;
   useVirtualScroll: boolean = true;
   toolbox: TreeNodeModel = toolbox;
   private vScrollSubscription = Subscription.EMPTY;
   private combinationTreeRoot: TreeNodeModel;
   virtualTop = 0;
   virtualBot = 0;
   virtualMid = 0;
   virtualScrollTreeDatasource: VirtualScrollTreeDatasource = new VirtualScrollTreeDatasource();

   constructor(private cd: ChangeDetectorRef,
               private zone: NgZone,
               private domService: DomService){
   }

   ngOnInit() {
      if(this.deployed) {
         this.toolbox = toolboxDeployed;
      }
      else {
         this.toolbox = toolbox;
      }

      TreeTool.expandAllNodes(this.toolbox);
   }

   ngOnChanges(changes: SimpleChanges) {
      if(this.inactive) {
         this.cd.detach();
      }
      else {
         if(changes["inactive"]) {
            this.subscribeVScroll();
         }

         this.cd.detectChanges();
      }

      if(changes["containerView"] && changes["containerView"].currentValue) {
         this.subscribeVScroll();
      }
   }

   ngOnDestroy(): void {
      if(this.vScrollSubscription) {
         this.vScrollSubscription.unsubscribe();
      }
   }

   nodeDrag(event: DragEvent): void {
      const textData = event.dataTransfer.getData("text");

      if(textData) {
         const jsonData = JSON.parse(textData);
         const labels = jsonData.dragName.map(n => this.findLabel(n));
         const elem = GuiTool.createDragImage(labels, jsonData.dragName);
         GuiTool.setDragImage(event, elem, this.zone, this.domService);
      }
   }

   expandNode(node: TreeNodeModel) {
      this.virtualScrollTreeDatasource.nodeExpanded(this.combinationTreeRoot, node);
   }

   collapseNode(node: TreeNodeModel) {
      this.virtualScrollTreeDatasource.nodeCollapsed(this.combinationTreeRoot, node);
   }

   treeNodesLoaded(bindingRoot: TreeNodeModel) {
      let combinationTree = <TreeNodeModel> {
         children: [],
         expanded: true
      };

      if(bindingRoot) {
         combinationTree.children.push(bindingRoot);
      }

      combinationTree.children.push(this.toolbox);
      this.combinationTreeRoot = combinationTree;

      setTimeout(() => {
         this.useVirtualScroll = TreeTool.needUseVirtualScroll(this.combinationTreeRoot);
         this.virtualScrollTreeDatasource.refreshByRoot(this.combinationTreeRoot);
      });
   }

   private findLabel(dragName: string): string {
      const label = this.findLabel0(this.toolbox, dragName);
      return label ? label : dragName;
   }

   private findLabel0(root: TreeNodeModel, dragName: string): string {
      if(root.dragName == dragName) {
         return root.label;
      }

      if(root.children) {
         for(let child of root.children) {
            const label = this.findLabel0(child, dragName);

            if(label) {
               return label;
            }
         }
      }

      return null;
   }

   private subscribeVScroll(): void {
      if(!this.containerView) {
         return;
      }

      if(this.vScrollSubscription) {
         this.vScrollSubscription.unsubscribe();
      }

      this.vScrollSubscription = this.virtualScrollTreeDatasource.registerScrollContainer(this.containerView)
         .pipe(map(nodes => this.calculateBounds(nodes)))
         .subscribe(nodes => {
            this.virtualScrollTreeDatasource.fireVirtualScroll(nodes);
         });
   }

   private calculateBounds(nodes: TreeNodeModel[]): TreeNodeModel[] {
      const nodeHeight = TreeTool.TREE_NODE_HEIGHT;
      const clientRect = this.containerView.getBoundingClientRect();
      const scrollTop = this.containerView.scrollTop;
      const nodesInView = Math.min(nodes.length, Math.round(clientRect.height / nodeHeight) + 5);
      let row = Math.max(Math.round(scrollTop / nodeHeight) - 1, 0);
      row = Math.min(nodes.length - nodesInView, row);
      this.virtualTop = row * nodeHeight;
      this.virtualBot = nodeHeight * (nodes.length - nodesInView - row);
      this.virtualMid = nodeHeight * nodesInView;

      return nodes.slice(row, row + nodesInView);
   }
}
