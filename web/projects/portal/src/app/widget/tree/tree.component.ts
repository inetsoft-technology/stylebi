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
   AfterViewChecked,
   AfterViewInit,
   ChangeDetectorRef,
   Component,
   ElementRef,
   EventEmitter,
   forwardRef,
   Input,
   OnChanges,
   OnDestroy,
   OnInit,
   Output,
   SimpleChanges,
   ViewChild
} from "@angular/core";
import { Observable, Subject, Subscription } from "rxjs";
import { map } from "rxjs/operators";
import { Tool } from "../../../../../shared/util/tool";
import { DataRef } from "../../common/data/data-ref";
import { GuiTool } from "../../common/util/gui-tool";
import { TreeTool } from "../../common/util/tree-tool";
import { VSUtil } from "../../vsobjects/util/vs-util";
import { TreeNodeModel } from "./tree-node-model";
import { TreeNodeComponent } from "./tree-node.component";
import { VirtualScrollTreeDatasource } from "./virtual-scroll-tree-datasource";

export enum TreeView {
   FULL_VIEW,
   RECENT_VIEW
}

@Component({
   selector: "tree", // eslint-disable-line @angular-eslint/component-selector
   templateUrl: "tree.component.html",
   styleUrls: ["./tree.component.scss"]
})
export class TreeComponent implements OnInit, OnChanges, AfterViewChecked, AfterViewInit, OnDestroy {
   treeRef: TreeComponent = this;
   @Input() draggable: boolean = false;
   @Input() droppable: boolean = false;
   @Input() multiSelect: boolean = false;
   @Input() nodeSelectable: boolean = true;
   @Input() initExpanded: boolean = false;
   @Input() initSelectedNodesExpanded: boolean = false;
   @Input() iconFunction: (node: TreeNodeModel) => string;
   /** If true, select node on click; if false, select on mousedown. */
   @Input() selectOnClick: boolean = false;
   @Input() grayedOutFields: DataRef[];
   @Input() grayedOutValues: string[];
   @Input() selectedNodes: TreeNodeModel[] = [];
   @Input() contextmenu: boolean = false;
   @Input() showIcon: boolean = true;
   @Input() disabled: boolean = false;
   @Input() showTooltip: boolean = false;
   @Input() showFavoriteIcon: boolean = false;
   @Input() fillHeight: boolean = false;
   @Input() maxHeight: number = -1;
   @Input() isGrayFunction: (node: TreeNodeModel) => boolean;
   @Input() hasMenuFunction: (node: TreeNodeModel) => boolean;
   @Input() isRejectFunction: (nodes: TreeNodeModel[]) => boolean;
   @Input() isRepositoryTree: boolean = false;
   @Input() isPortalDataSourcesTree: boolean = false;
   @Input() checkboxEnable: boolean = false;
   @Input() isMobile: boolean = false;
   @Input() ellipsisOverflowText: boolean = false;
   @Input() hoverShowScroll: boolean = false;
   @Input() inputFocus: boolean = false;
   @Input() showOriginalName: boolean = false;
   @Input() helpURL: string = "";
   @Input() useVirtualScroll: boolean;
   @Input() outerScrollContainer: boolean;
   @Input() dataSource: VirtualScrollTreeDatasource;
   @Input() recentEnabled: boolean;
   @Input() getRecentTreeFun: () => Observable<TreeNodeModel[]>;
   @Input() searchEndNode: (node: TreeNodeModel) => boolean;
   @Input() nodeEqualsFun: (node1: TreeNodeModel, node2: TreeNodeModel) => boolean;
   @Output() nodeExpanded = new EventEmitter<TreeNodeModel>();
   @Output() nodeCollapsed = new EventEmitter<TreeNodeModel>();
   @Output() nodesSelected = new EventEmitter<TreeNodeModel[]>();
   @Output() nodeDrag = new EventEmitter<any>();
   @Output() nodeDrop = new EventEmitter<TreeNodeModel>();
   @Output() dblclickNode = new EventEmitter<TreeNodeModel>();
   @Output() nodeClicked = new EventEmitter<TreeNodeModel>();
   @Output() onContextmenu = new EventEmitter<[MouseEvent | any, TreeNodeModel, TreeNodeModel[]]>();
   @Output() searchStart = new EventEmitter<boolean>();
   _searchEnabled: boolean = false;
   @ViewChild(forwardRef(() => TreeNodeComponent)) rootNode: TreeNodeComponent;
   @ViewChild("treeContainer") treeContainer: ElementRef;
   @ViewChild("searchInput") searchInput: ElementRef;
   empty: boolean = false;
   vscrollY: number = null;
   highLightNodes: TreeNodeModel[];
   // For section 508 compatibility
   private focused: TreeNodeModel;
   private focusedSubject = new Subject<TreeNodeModel>();
   private vScrollSubscription = Subscription.EMPTY;
   private recentSubscription: Subscription = Subscription.EMPTY;
   private _searchStr: string = "";
   public virtualTop = 0;
   public virtualBot = 0;
   public virtualMid = 0;
   private viewInited: boolean = false;
   private _root: TreeNodeModel;
   private _showRoot: boolean = true;
   private treeView: TreeView = TreeView.FULL_VIEW;
   private recentRoot: TreeNodeModel;

   @Input()
   set root(root: TreeNodeModel) {
      this._root = root;

      if(!!this.selectedNodes && this.selectedNodes.length > 0) {
         for(let i = 0; i < this.selectedNodes.length; i++) {
            let node = this.selectedNodes[i];

            if(node == null) {
               continue;
            }

            let updatedNode;

            if(this.nodeEqualsFun) {
               updatedNode = GuiTool.findNode(root, (n) => this.nodeEqualsFun(node, n));
            }
            else {
               updatedNode = GuiTool.getNodeByPath(node.data?.path, root, node.data?.defaultOrgAsset);
            }

            node.data = !!updatedNode ? updatedNode.data : node.data;
         }
      }
   }

   get root(): TreeNodeModel {
      return this.treeView == TreeView.FULL_VIEW ? this._root : this.recentRoot;
   }

   @Input()
   set showRoot(showRoot: boolean) {
      this._showRoot = showRoot;
   }

   get showRoot(): boolean {
      return this.isRecentView ? true : this._showRoot;
   }

   @Input()
   set searchStr(str: string) {
      if(this.dataSource) {
         this.dataSource.filterValues(this.canUseVirtualScroll ? this.root : null, str);
      }

      this._searchStr = str;
   }

   get searchStr(): string {
      return this._searchStr;
   }

   get canUseVirtualScroll(): boolean {
      return this.useVirtualScroll && !!this.dataSource;
   }

   get isRecentView(): boolean {
      return this.treeView == TreeView.RECENT_VIEW;
   }

   constructor(private changeRef: ChangeDetectorRef) {
   }

   ngOnInit(): void {
      if(this.initSelectedNodesExpanded) {
         this.selectedNodes.forEach((selectedNode) => {
            this.expandToNode(selectedNode);
         });
      }

      if(this.recentEnabled && this.getRecentTreeFun) {
         this.recentRoot = {
            label: "_#(js:Recent)",
            children: [],
            expanded: true,
            expandedIcon: "folder-open-icon",
            icon: "folder-icon"
         };
      }
   }

   ngOnChanges(changes: SimpleChanges): void {
      let needRefreshVirtualScroll = false;

      if(changes["root"]) {
         if(!!this.searchStr?.trim()) {
            this.expandAll(this.root);
         }
         else {
            this.fixSelectedNodes();
         }

         needRefreshVirtualScroll = true;
      }

      let useVirtualScrollChange = changes["useVirtualScroll"];

      if(useVirtualScrollChange &&
         useVirtualScrollChange.currentValue != useVirtualScrollChange.previousValue &&
         useVirtualScrollChange.currentValue)
      {
         needRefreshVirtualScroll = true;
         this.subscribeVScroll();
      }

      if(useVirtualScrollChange && !useVirtualScrollChange.currentValue) {
         this.unSubscribeVScroll();
      }

      if(this.outerScrollContainer) {
         needRefreshVirtualScroll = false;
      }

      if(needRefreshVirtualScroll) {
         if(this.dataSource && this.viewInited) {
            this.dataSource.refreshByRoot(this.useVirtualScroll ? this.root : null, this.searchStr);
         }
      }
   }

   ngAfterViewInit(): void {
      this.viewInited = true;

      if(this.searchInput && this.inputFocus) {
         this.searchInput.nativeElement.focus();
      }

      if(this.useVirtualScroll) {
         setTimeout(() => this.subscribeVScroll());
      }

      if(!!this.dataSource && !!this.treeContainer && !this.outerScrollContainer) {
         setTimeout(() =>
            this.dataSource.refreshByRoot(this.useVirtualScroll ? this.root : null, this.searchStr)
         );
      }
   }

   ngAfterViewChecked(): void {
      const empty = !this.rootNode || !this.rootNode.nodes || this.rootNode.nodes.length == 0;

      if(empty != this.empty) {
         this.empty = empty;
         this.changeRef.detectChanges();
      }
   }

   ngOnDestroy(): void {
      this.unSubscribeVScroll();

      if(this.recentSubscription != null) {
         this.recentSubscription.unsubscribe();
         this.recentSubscription = null;
      }
   }

   @Input()
   set searchEnabled(searchEnabled: boolean) {
      this._searchEnabled = searchEnabled;

      if(!searchEnabled && !!this.searchStr) {
         this.searchStr = "";
         this.searchStart.emit(false);
      }
   }

   get treeContainerMaxHeight(): string {
      let maxH = "100%";

      if(this.maxHeight > 0) {
         maxH = this.maxHeight + "px";
      }

      return this._searchEnabled ? "calc(" + maxH + " + 30px)" : this.fillHeight ? maxH : null;
   }

   get showHelpLink(): boolean {
      return !!this.helpURL;
   }

   search(str: string) {
      let oldSearchStr = this.searchStr;
      this._searchStr = str;

      if(this.searchStr.trim()) {
         if(!oldSearchStr) {
            this.searchStart.emit(true);
         }

         setTimeout(() => {
            this.expandAll(this.root);

            if(this.dataSource && !this.outerScrollContainer) {
               this.dataSource.refreshByRoot(this.useVirtualScroll ? this.root : null, this.searchStr);
            }
         }, 0);
      }
      else {
         this.searchStart.emit(false);

         if(this.dataSource) {
            this.dataSource.filterValues(this.useVirtualScroll ? this.root : null, this.searchStr);
         }
      }
   }

   private calculateBounds(nodes: TreeNodeModel[]): TreeNodeModel[] {
      const nodeHeight = TreeTool.TREE_NODE_HEIGHT;
      const clientRect = this.treeContainer.nativeElement.getBoundingClientRect();
      const scrollTop = this.treeContainer.nativeElement.scrollTop;
      const nodesInView = Math.min(nodes.length, Math.round(clientRect.height / nodeHeight) + 3);
      let row = Math.max(Math.round(scrollTop / nodeHeight) - 1, 0);
      row = Math.min(nodes.length - nodesInView, row);
      this.virtualTop = row * nodeHeight;
      this.virtualBot = nodeHeight * (nodes.length - nodesInView - row);
      this.virtualMid = nodeHeight * nodesInView;
      this.changeRef.detectChanges();

      return nodes.slice(row, row + nodesInView);
   }

   private fixSelectedNodes() {
      let fixedSelectedNodes: TreeNodeModel[] = [];
      let nodes = this.selectedNodes;

      if(!!this.selectedNodes) {
         for(let selectedNode of this.selectedNodes) {
            if(!!selectedNode && !!selectedNode.data) {
               if(!selectedNode.data.hasOwnProperty("path")) {
                  return;
               }
               else if(GuiTool.getNodeByPath(selectedNode.data.path, this.root, selectedNode.data.defaultOrgAsset) != null) {
                  fixedSelectedNodes.push(selectedNode);
               }
            }
            else {
               fixedSelectedNodes.push(selectedNode);
            }
         }
      }

      this.selectedNodes = fixedSelectedNodes;
   }

   public expandAll(node: TreeNodeModel) {
      if(this.searchEndNode && this.searchEndNode(node)) {
         return;
      }

      if(!node.expanded && !node.leaf) {
         node.expanded = true;
      }

      if(node.children) {
         node.children.forEach(n => this.expandAll(n));
      }
   }

   public expandNode(node: TreeNodeModel): void {
      this.nodeExpanded.emit(node);
      this.refreshScroll();
   }

   collapseNode(node: TreeNodeModel): void {
      // Bug #23175 Do not deselect children when collapsing folder,
      // to be consistant with normal website custom.
      this.nodeCollapsed.emit(node);
      this.refreshScroll();
   }

   refreshScroll() {
      // if maxheight is setted, means parent of the tree has no specific height, height will be
      // changed after expanding/collapsing nodes, which means the tree container height will be
      // changed, so subscribe vscroll with the latest tree container height.
      if(this.useVirtualScroll && this.maxHeight > 0) {
         this.subscribeVScroll();
      }
   }

   doubleclickNode(node: TreeNodeModel): void {
      this.dblclickNode.emit(node);
   }

   clickNode(node: TreeNodeModel): void {
      this.nodeClicked.emit(node);
   }

   get isSelectedNode() {
      return (node: TreeNodeModel) => {
         if(this.selectedNodes) {
            for(let treeNode of this.selectedNodes) {
               if(this.nodeEquals(node, treeNode)) {
                  return true;
               }
            }
         }

         return false;
      };
   }

   /** Change the selection to only contain the given node */
   exclusiveSelectNode(node: TreeNodeModel): void {
      this.selectedNodes = [node];
      this.nodesSelected.emit(this.selectedNodes);
   }

   selectNode(node: TreeNodeModel, evt: MouseEvent | any, emit: boolean = true): void {
      const index = this.selectedNodes.indexOf(node);

      if(this.multiSelect) {
         if(!evt.ctrlKey && !evt.metaKey && !evt.shiftKey) {
            if(index < 0) {
               this.selectedNodes = [];
            }
         }

         if(evt.shiftKey) {
            this.addShiftNodes(node);
         }
         else if(evt.ctrlKey || evt.metaKey) {
            if(index >= 0) {
               this.selectedNodes.splice(index, 1);
            }
            else {
               this.addNodeToArray(node, this.selectedNodes);
            }
         }
         else {
            this.addNodeToArray(node, this.selectedNodes);
         }

         this.enforceSinglePathToRoot(this.root, node);
      }
      else if(this.checkboxEnable) {
         if(node.leaf) {
            if(this.selectedNodes.some((n) => Tool.isEquals(n, node))) {
               // remove field.
               this.selectedNodes = this.selectedNodes.filter((n) => !Tool.isEquals(n, node));
            }
            else {
               // add field.
               this.selectedNodes.push(node);
            }
         }
         else {
            emit = false;
         }
      }
      else if(this.nodeSelectable) {
         this.selectedNodes = [node];
      }

      if(emit) {
         this.nodesSelected.emit(this.selectedNodes);
      }
   }

   setHighLightNodes(node: TreeNodeModel): void {
      this.highLightNodes = [node];
   }

   /**
    * Validate selected nodes so that for any path from the root to any child node
    * there is at most one selected node. The
    *
    * @param root the root node
    * @param priorityNode the node which should have selection priority
    * @param deselect whether or not to remove this node from the selection
    * @returns true if root or a descendant is selected, false otherwise
    */
   private enforceSinglePathToRoot(root: TreeNodeModel, priorityNode: TreeNodeModel,
                                   deselect: boolean = false): boolean
   {
      let descendantSelected: boolean = false;
      let currentNodeHasPriority: boolean = priorityNode === root;

      if(root.children) {
         for(const child of root.children) {
            descendantSelected = this.enforceSinglePathToRoot(
               child, priorityNode, currentNodeHasPriority || deselect) ||
               descendantSelected;
         }
      }

      const index = this.selectedNodes.indexOf(root);

      if((descendantSelected || deselect) && index >= 0 && !currentNodeHasPriority) {
         this.selectedNodes.splice(index, 1);
      }

      return index >= 0 || descendantSelected;
   }

   onDrag(evt: any): void {
      this.nodeDrag.emit(evt);
   }

   // For some case, should reject node dropped, so should preventdefault according to some
   // condition: such as drag ws column, should reject on asset tree.
   onDragOver(evt: any): void {
      if(this.isRejectFunction == null || !this.isRejectFunction(this.selectedNodes)) {
         evt.preventDefault();
      }
   }

   onDrop(event: any): void {
      this.nodeDrop.emit(event);
   }

   // When use shift key, find node from last node to current select node.
   addShiftNodes(node: TreeNodeModel) {
      let shiftObj: any = {start: false, end: false, order: true, shiftNodes: []};

      if(this.selectedNodes == null || this.selectedNodes.length == 0) {
         this.addNodeToArray(node, this.selectedNodes);
         return;
      }

      let lastNode = this.selectedNodes[this.selectedNodes.length - 1];

      if(lastNode == node) {
         return;
      }

      this.findNodes(this.root, shiftObj, lastNode, node);
      this.addSelectedNodes(shiftObj);
   }

   findNodes(findNode: TreeNodeModel, shiftObj: any, lastNode: TreeNodeModel,
             node: TreeNodeModel)
   {
      if(shiftObj.start && !shiftObj.end) {
         this.addNodeToArray(findNode, shiftObj.shiftNodes);
      }

      let nodes: TreeNodeModel[];

      if(findNode.expanded) {
         nodes = findNode.children;
      }

      // Find first node, start is true, find the second node, end is true.
      // add node when start is true and end is false.
      if(findNode == lastNode || findNode == node) {
         // If select node from top to bottom, order is true, else false.
         if(!shiftObj.start) {
            shiftObj.order = findNode == lastNode;
         }

         shiftObj.end = shiftObj.start;
         shiftObj.start = true;
         this.addNodeToArray(findNode, shiftObj.shiftNodes);
      }

      if(nodes == null) {
         return;
      }

      for(let i = 0; i < nodes.length; i++) {
         this.findNodes(nodes[i], shiftObj, lastNode, node);
      }
   }

   addSelectedNodes(shiftObj: any) {
      let nodes = shiftObj.shiftNodes;

      if(!shiftObj.order) {
         for(let i = (nodes.length - 1); i >= 0; i--) {
            this.addNodeToArray(nodes[i], this.selectedNodes);
         }
      }
      else {
         for(let j = 0; j < nodes.length; j++) {
            this.addNodeToArray(nodes[j], this.selectedNodes);
         }
      }
   }

   addNodeToArray(node: TreeNodeModel, arr: TreeNodeModel[]) {
      let found = false;

      for(let i = 0; i < arr.length; i++) {
         if(this.nodeEquals(arr[i], node)) {
            found = true;
         }
      }

      if(!found) {
         arr.push(node);
      }
   }

   public getParentNode(childNode: TreeNodeModel, parentNode?: TreeNodeModel): TreeNodeModel {
      if(parentNode == null) {
         parentNode = this.root;
      }

      if(!parentNode || parentNode.children == null || parentNode.children.length == 0) {
         return null;
      }

      for(let i = 0; i < parentNode.children.length; i++) {
         let node: TreeNodeModel = parentNode.children[i];

         if(node === childNode) {
            return parentNode;
         }

         node = this.getParentNode(childNode, node);

         if(node != null) {
            return node;
         }
      }

      return null;
   }

   public getParentNodeByData(childData: any, parentNode?: TreeNodeModel,
                              compareFun?: (data0: any, data1: any) => boolean): TreeNodeModel
   {
      if(parentNode == null) {
         parentNode = this.root;
      }

      if(parentNode.children == null || parentNode.children.length == 0) {
         return null;
      }

      for(let i = 0; i < parentNode.children.length; i++) {
         let node: TreeNodeModel = parentNode.children[i];

         if(compareFun && compareFun(node.data, childData) ||
            this.nodeEquals(node.data, childData))
         {
            return parentNode;
         }

         node = this.getParentNodeByData(childData, node, compareFun);

         if(node != null) {
            return node;
         }
      }

      return null;
   }

   public getNodeByData(compareType: string, data: any, parentNode?: TreeNodeModel): TreeNodeModel {
      if(parentNode == null) {
         parentNode = this.root;
      }

      data = compareType === "column" ? {table: data.table, attribute: data.attribute} : data;

      for(let node of parentNode.children) {
         if(this.nodeEquals(this.getNodeData(node, compareType), data)) {
            return node;
         }
         else if(!node.leaf) {
            let result = this.getNodeByData(compareType, data, node);

            if(result) {
               return result;
            }
         }
      }

      return null;
   }

   private getNodeData(node: TreeNodeModel, compareType: string): any {
      switch (compareType) {
         case "column":
            let table = VSUtil.getTableName(node.data.table);
            return {table: table, attribute: node.data.attribute};
         case "data":
            return node.data;
         case "label":
            return node.label;
      }
   }

   public getTopAncestor(dataType: string, data: any,
                         exclusiveAssemblies: boolean = false): TreeNodeModel
   {
      if(this.root) {
         let children: TreeNodeModel[] = [];

         if(exclusiveAssemblies) {
            this.root.children.forEach(child => {
               if(child.data.path === "/Assemblies") {
                  children.push(...child.children);
               }
               else {
                  children.push(child);
               }
            });
         }
         else {
            children = this.root.children;
         }

         for(let potentialParent of children) {
            if(this.getNodeByData(dataType, data, potentialParent)) {
               return potentialParent;
            }
         }
      }

      return null;
   }

   public selectAndExpandToNode(node: TreeNodeModel) {
      this.selectedNodes = [node];
      this.expandToNode(node);
   }

   public expandToNode(node: TreeNodeModel) {
      let parentNode = this.getParentNode(node);

      while(parentNode) {
         parentNode.expanded = true;
         parentNode = this.getParentNode(parentNode);
      }
   }

   public isHostGlobalParent(node: TreeNodeModel): boolean {
      let parentNode = this.getParentNode(node);

      while(parentNode) {
         if(parentNode != null && parentNode.data != null &&
            GuiTool.isHostGlobalNode(parentNode.data))
         {
            return true;
         }

         parentNode = this.getParentNode(parentNode);
      }

      return false;
   }

   private nodeEquals(node1: any, node2: any): boolean {
      if(!!node1 && !!node2 && node1.defaultOrgAsset != node2.defaultOrgAsset) {
         return false;
      }

      if(!node1 || !node2) {
         return Tool.isEquals(node1, node2);
      }
      else if(typeof node2.data == "string" && Tool.isEquals(node2.data, node2.label)) {
         return node1.label == node2.label && node1.type == node2.type;
      }
      else if(!!node1.data || !!node2.data) {
         return (!node1.label || !node2.label || node1.label == node2.label) &&
            Tool.isEquals(node1.data, node2.data);
      }
      else if(!!node1.label || !!node2.label) {
         return node1.label == node2.label && node1.type == node2.type;
      }

      return Tool.isEquals(node1, node2);
   }

   public deselectAllNodes(): void {
      this.selectedNodes = [];
   }

   onScroll(event: any) {
      const scrolled = this.vscrollY > 0;
      this.vscrollY = this.treeContainer.nativeElement.scrollTop;
      const nscrolled = this.vscrollY > 0;

      if(scrolled != nscrolled) {
         this.changeRef.detectChanges();
      }
   }

   /**
    * Get the focused node observable.
    * @returns {Observable<TreeNodeModel>}
    */
   get focusedObservable(): Observable<TreeNodeModel> {
      return this.focusedSubject.asObservable();
   }

   /**
    * Check if specific node is currently focused.
    * @param {TreeNodeModel} node
    * @returns {boolean}
    */
   isFocusedNode(node: TreeNodeModel): boolean {
      return this.nodeEquals(node, this.focused);
   }

   /**
    * Listen for up and down keys to move the focus.
    * @param {KeyboardEvent} event
    */
   onKey(event: KeyboardEvent): void {
      const key: number = event.keyCode;

      // Select first item in tree
      if(event.keyCode == 40 && !this.focused) {
         this.focused = this.showRoot || !this.root || !this.root.children ||
         !this.root.children.length ? this.root : this.root.children[0];
         this.focusedSubject.next(this.focused);
      }
      // Move focus down
      else if(key == 40) { // down
         this.nextNode(this.focused);
      }
      // Move focus up
      else if(key == 38) {
         this.prevNode(this.focused);
      }
      // Right arrow = expand node
      else if(key == 39 && this.focused && !this.focused.leaf) {
         this.focused.expanded = true;
         this.expandNode(this.focused);
      }
      // Left arrow = collapse node
      else if(key == 37 && this.focused) {
         this.focused.expanded = false;
         this.collapseNode(this.focused);
      }
   }

   /**
    * Get next node to focus on.
    * @param {TreeNodeModel} node
    * @param {boolean} children
    */
   private nextNode(node: TreeNodeModel, children: boolean = true): void {
      if(children && node && node.expanded && node.children &&
         node.children.length)
      {
         this.focused = node.children[0];
         this.focusedSubject.next(this.focused);
         return;
      }

      const parent: TreeNodeModel = this.getParentNode(node);

      if(parent) {
         const index: number = parent.children
            .findIndex((child: TreeNodeModel) => this.nodeEquals(child, node));
         const next: number = index + 1;
         const max: number = parent.children.length;

         if(next < max) {
            this.focused = parent.children[next];
            this.focusedSubject.next(this.focused);
         }
         else {
            this.nextNode(parent, false);
         }
      }
   }

   /**
    * Get previous node to focus on.
    * @param {TreeNodeModel} node
    */
   private prevNode(node: TreeNodeModel): void {
      const parent: TreeNodeModel = this.getParentNode(node);

      if(parent) {
         const index: number = parent.children
            .findIndex((child: TreeNodeModel) => this.nodeEquals(child, node));
         const prev: number = index - 1;

         if(prev >= 0) {
            const prevNode: TreeNodeModel = parent.children[prev];

            if(prevNode && prevNode.expanded && prevNode.children && prevNode.children.length) {
               this.focused = prevNode.children[prevNode.children.length - 1];
            }
            else if(prevNode && !prevNode.expanded) {
               this.focused = prevNode;
            }

            this.focusedSubject.next(this.focused);
         }
         else {
            this.prevNode(parent);
         }
      }
      else {
         this.focused = node;
         this.focusedSubject.next(this.focused);
      }
   }

   clearSearchContent(): void {
      this.search("");
   }

   openHelp() {
      window.open(this.helpURL);
   }

   switchRecent(): void {
      if(this.recentEnabled && this.getRecentTreeFun) {
         if(this.treeView == TreeView.FULL_VIEW) {
            this.recentSubscription = this.getRecentTreeFun().subscribe(data => {
               this.recentRoot.children = data;
            });

            this.treeView = TreeView.RECENT_VIEW;
         }
         else {
            this.treeView = TreeView.FULL_VIEW;
         }
      }
   }

   private subscribeVScroll(): void {
      if(!this.canUseVirtualScroll || !this.treeContainer?.nativeElement) {
         return;
      }

      if(this.outerScrollContainer) {
         return;
      }

      this.unSubscribeVScroll();
      this.vScrollSubscription = this.dataSource.registerScrollContainer(this.treeContainer.nativeElement)
         .pipe(map(nodes => this.calculateBounds(nodes)))
         .subscribe(nodes => {
            this.dataSource.fireVirtualScroll(nodes);
         });

      this.vScrollSubscription.add(
         this.dataSource.scrollTop
            .subscribe(scrollTop => this.treeContainer.nativeElement.scrollTop = scrollTop)
      );
   }

   private unSubscribeVScroll(): void {
      if(this.vScrollSubscription) {
         this.vScrollSubscription.unsubscribe();
         this.vScrollSubscription = null;
      }
   }

   getVirtualBottom(): number {
      return this.virtualBot;
   }
}
