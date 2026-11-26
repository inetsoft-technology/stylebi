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
import { NgClass } from "@angular/common";
import {
   ChangeDetectorRef,
   Component,
   HostBinding,
   inject,
   Input,
   NgZone,
   OnChanges,
   OnDestroy,
   OnInit,
   Optional,
   SimpleChanges
} from "@angular/core";
import { Tool } from "../../../../../../shared/util/tool";
import { DragService } from "../../../widget/services/drag.service";
import { DataRef } from "../../../common/data/data-ref";
import { GuiTool } from "../../../common/util/gui-tool";
import { DomService } from "../../../widget/dom-service/dom.service";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { Viewsheet } from "../../data/vs/viewsheet";
import { toolbox, toolboxDeployed } from "./toolbox.config";
import { map } from "rxjs/operators";
import { Subscription } from "rxjs";
import { TreeTool } from "../../../common/util/tree-tool";
import { VirtualScrollTreeDatasource } from "../../../widget/tree/virtual-scroll-tree-datasource";
import { ComposerBindingTree } from "./composer-binding-tree.component";
import { BindingTreeService } from "../../../binding/widget/binding-tree/binding-tree.service";

@Component({
    selector: "composer-toolbox-pane",
    templateUrl: "toolbox-pane.component.html",
    styleUrls: ["toolbox-pane.component.scss"],
    imports: [ComposerBindingTree, NgClass]
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
   private bindingTreeSubscription = Subscription.EMPTY;
   private combinationTreeRoot: TreeNodeModel;
   private doNotShowNodes: TreeNodeModel[] = [];
   virtualTop = 0;
   virtualBot = 0;
   virtualMid = 0;
   virtualScrollTreeDatasource: VirtualScrollTreeDatasource = new VirtualScrollTreeDatasource();
   // whether the pinned components section is folded to its header; remembered per browser
   componentsCollapsed = false;
   private readonly dragService = inject(DragService);
   private static readonly COLLAPSED_KEY = "inetsoft.composer.toolbox.componentsCollapsed";

   constructor(private cd: ChangeDetectorRef,
               private zone: NgZone,
               private domService: DomService,
               private treeService: BindingTreeService){
   }

   ngOnInit() {
      if(this.deployed) {
         this.toolbox = toolboxDeployed;
      }
      else {
         this.toolbox = toolbox;
      }

      TreeTool.expandAllNodes(this.toolbox);

      try {
         this.componentsCollapsed = localStorage.getItem(ToolboxPane.COLLAPSED_KEY) === "true";
      }
      catch(e) {
         // storage can be blocked; the section then starts open
      }

      // Consume the (replaying) binding tree here rather than via the child's output, so that
      // useVirtualScroll is settled before this view is first checked (avoids NG0100).
      this.bindingTreeSubscription = this.treeService.bindingTreeChanged()
         .subscribe((root) => this.treeNodesLoaded(root));
   }

   ngOnChanges(changes: SimpleChanges) {
      if(this.inactive) {
         this.cd.detach();
      }
      else {
         if(changes["inactive"]) {
            this.subscribeVScroll();
         }

         this.cd.reattach();
      }

      if(changes["containerView"] && changes["containerView"].currentValue) {
         this.subscribeVScroll();
      }
   }

   ngOnDestroy(): void {
      this.virtualScrollTreeDatasource.cleanup();

      if(this.vScrollSubscription) {
         this.vScrollSubscription.unsubscribe();
      }

      this.bindingTreeSubscription.unsubscribe();
   }

   /**
    * The component groups to show as tiles: the toolbox's groups (Data View, Filter, ...), or, for
    * the deployed toolbox, which has no groups, its components in one unlabelled row.
    */
   get componentGroups(): { label: string, items: TreeNodeModel[] }[] {
      const children = this.toolbox?.children || [];

      if(children.every(c => c.leaf)) {
         return [{ label: null, items: children }];
      }

      return children.map(g => ({ label: g.label, items: g.children || [] }));
   }

   toggleComponents(): void {
      this.componentsCollapsed = !this.componentsCollapsed;

      try {
         localStorage.setItem(ToolboxPane.COLLAPSED_KEY, String(this.componentsCollapsed));
      }
      catch(e) {
         // storage can be blocked; the choice then lasts until reload
      }
   }

   /**
    * Start dragging a component tile, with the same transfer data the toolbox tree set for its leaf
    * nodes, so the canvas drops it as before.
    */
   tileDragStarted(event: any, item: TreeNodeModel): void {
      this.dragService.reset();
      const dragData = [item.dragData || item.data];
      const data: any = { dragName: [item.dragName] };
      data[item.dragName] = dragData;
      Tool.setTransferData(event.dataTransfer, data);
      this.dragService.put(item.dragName, JSON.stringify(dragData));

      const elem = GuiTool.createDragImage([item.label], [item.dragName]);
      GuiTool.setDragImage(event, elem, this.zone, this.domService);
   }

   expandNode(node: TreeNodeModel) {
      this.virtualScrollTreeDatasource.nodeExpanded(this.combinationTreeRoot, node, this.doNotShowNodes);
   }

   collapseNode(node: TreeNodeModel) {
      this.virtualScrollTreeDatasource.nodeCollapsed(this.combinationTreeRoot, node, this.doNotShowNodes);
   }

   treeNodesLoaded(bindingRoot: TreeNodeModel) {
      let combinationTree = <TreeNodeModel> {
         children: [],
         expanded: true
      };

      this.doNotShowNodes = [];

      if(bindingRoot) {
         combinationTree.children.push(bindingRoot);
         this.doNotShowNodes.push(bindingRoot);
      }

      // the components are tiles above the tree now, not rows of it
      this.combinationTreeRoot = combinationTree;
      this.useVirtualScroll = TreeTool.needUseVirtualScroll(this.combinationTreeRoot);
      this.virtualScrollTreeDatasource.refreshByRoot(this.combinationTreeRoot, undefined, this.doNotShowNodes);
   }

   private subscribeVScroll(): void {
      if(!this.containerView) {
         return;
      }

      if(this.vScrollSubscription) {
         this.vScrollSubscription.unsubscribe();
      }

      this.vScrollSubscription = this.virtualScrollTreeDatasource.registerScrollContainer(this.containerView, this.zone)
         .pipe(map(nodes => this.calculateBounds(nodes)))
         .subscribe(nodes => {
            this.virtualScrollTreeDatasource.fireVirtualScroll(nodes);
         });
   }

   private calculateBounds(nodes: TreeNodeModel[]): TreeNodeModel[] {
      const nodeHeight = TreeTool.TREE_NODE_HEIGHT;

      if(!this.useVirtualScroll) {
         this.virtualTop = 0;
         this.virtualBot = 0;
         this.virtualMid = nodes.length * nodeHeight;

         return nodes;
      }

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
