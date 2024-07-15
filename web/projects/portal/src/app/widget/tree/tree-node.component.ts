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
   ChangeDetectionStrategy,
   ChangeDetectorRef,
   Component,
   ElementRef,
   EventEmitter,
   HostListener,
   Input,
   OnChanges,
   OnDestroy,
   OnInit,
   Output,
   QueryList,
   SimpleChanges,
   ViewChild,
   ViewChildren
} from "@angular/core";
import { Subscription } from "rxjs";
import { AssetEntry } from "../../../../../shared/data/asset-entry";
import { Tool } from "../../../../../shared/util/tool";
import { AssetEntryHelper } from "../../common/data/asset-entry-helper";
import { GuiTool } from "../../common/util/gui-tool";
import { DragService } from "../services/drag.service";
import { TreeNodeModel } from "./tree-node-model";
import { TreeComponent } from "./tree.component";
import { SearchComparator } from "./search-comparator";
import { VirtualScrollTreeDatasource } from "./virtual-scroll-tree-datasource";

@Component({
   selector: "tree-node",
   templateUrl: "tree-node.component.html",
   styleUrls: ["tree-node.component.scss"],
   changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TreeNodeComponent implements OnInit, OnDestroy, OnChanges {
   @Input() tree: TreeComponent;
   @Input() node: TreeNodeModel;
   @Input() showRoot: boolean = true;
   @Input() draggable: boolean = false;
   @Input() droppable: boolean = false;
   @Input() multiSelect: boolean = false;
   @Input() nodeSelectable: boolean = true;
   @Input() iconFunction: (node: TreeNodeModel) => string;
   @Input() isSelectedNode: (node: TreeNodeModel) => boolean;
   @Input() isRepositoryTree: boolean = false;
   @Input() isPortalDataSourcesTree: boolean = false;
   @Input() selectOnClick: boolean = false;
   @Input() contextmenu: boolean = false;
   @Input() searchStr: string = "";
   @Input() showIcon: boolean = true;
   @Input() forceMatch: boolean = false;
   @Input() showTooltip: boolean = false;
   @Input() showFavoriteIcon: boolean = false;
   @Input() checkboxEnable: boolean = false;
   @Input() indentLevel: number = 0;
   @Input() ellipsisOverflowText: boolean = false;
   @Input() showOriginalName: boolean = false;
   @Input() dataSource: VirtualScrollTreeDatasource;
   @Input() useVirtualScroll: boolean;
   @Input() parentLoading: boolean = false;
   @Input() searchEndNode: (node: TreeNodeModel) => boolean;
   @Output() onContextmenu = new EventEmitter<[MouseEvent | any, TreeNodeModel]>();
   @ViewChildren(TreeNodeComponent) nodes: QueryList<TreeNodeComponent>;
   @ViewChild("nodeElement") nodeElement: ElementRef;
   @ViewChild("toggleElement") toggleElement: ElementRef;
   readonly INDENT_SIZE: number = 15;
   readonly favoriteIcon: string = "star-icon";
   // For section 508
   private focusSub: Subscription;
   private timeOutEvent: any = 0;
   private subscription = Subscription.EMPTY;
   public inViewport = true;

   constructor(private dragService: DragService, private cdRef: ChangeDetectorRef) {
   }

   ngOnInit() {
      if(this.tree) {
         this.focusSub = this.tree
            .focusedObservable
            .subscribe((node: TreeNodeModel) => {
               if(this.tree && this.nodeElement && this.tree.isFocusedNode(this.node)) {
                  this.nodeElement.nativeElement.focus();
               }
            });
      }
   }

   ngOnDestroy() {
      if(this.focusSub) {
         this.focusSub.unsubscribe();
      }

      this.subscription.unsubscribe();
   }

   ngOnChanges(changes: SimpleChanges) {
      if(this.node && (changes["node"] || changes["tree"] || changes["dataSource"] ||
         changes["useVirtualScroll"]))
      {
         this.subscription.unsubscribe();
         let virtualScrollChange = changes["useVirtualScroll"];
         // force detach when change form visual scroll to not visual scroll.
         this.updateInViewport(null, !this.node.leaf && virtualScrollChange && virtualScrollChange.previousValue && !virtualScrollChange.currentValue);

         if(this.useVirtualScroll && this.dataSource) {
            if(this.subscription) {
               this.subscription.unsubscribe();
            }

            this.subscription = this.dataSource.virtualScroll.subscribe(visibleNodes => {
               this.updateInViewport(visibleNodes);
            });
         }

         if(!this.showRoot && !this.node.expanded) {
            this.node.expanded = true;
            this.tree.expandNode(this.node);
         }
         if(this.tree.initExpanded) {
            this.node.expanded = true;
         }
      }
   }

   @HostListener("contextmenu", ["$event"])
   contextmenuListener(event: MouseEvent) {
      if(this.contextmenu) {
         if(event.button == 2) {
            event.preventDefault();
         }

         if(!this.tree.isSelectedNode(this.node) && !this.checkboxEnable) {
            this.selectNode(event, this.checkboxEnable);
         }

         if(this.checkboxEnable) {
            this.tree.setHighLightNodes(this.node);
         }

         event.stopPropagation();
         this.onContextmenu.emit([event, this.node]);
      }
   }

   @HostListener("touchstart", ["$event"])
   touchstartListener(event: any) {
      event.stopPropagation();

      this.timeOutEvent = setTimeout(() => {
         if(this.contextmenu && this.isRepositoryTree) {
            if(!this.tree.isSelectedNode(this.node) && !this.checkboxEnable) {
               this.selectNode(event, false);
            }

            this.onContextmenu.emit([event, this.node]);
         }

         this.timeOutEvent = 0;
      }, 500);
   }

   @HostListener("touchmove", ["$event"])
   touchmoveListener(event: any) {
      clearTimeout(this.timeOutEvent);
      this.timeOutEvent = 0;
   }

   @HostListener("touchend", ["$event"])
   touchendListener(event: any) {
      clearTimeout(this.timeOutEvent);

      if(this.timeOutEvent != 0) {
         this.clickSelectNode(event, true);
      }
   }

   toggleNode(): void {
      if(!this.hasChildren()) {
         return;
      }

      this.node.expanded = !this.node.expanded;

      if(this.node.expanded && !this.tree.initExpanded) {
         this.tree.expandNode(this.node);
      }
      else {
         this.tree.collapseNode(this.node);
         this.cdRef.detectChanges();
      }
   }

   clickSelectNode(event: MouseEvent | any, isTouch: boolean = false): void {
      if(this.isToggleElementEventTarget(event)) {
         return;
      }

      if(!this.nodeSelectable) {
         return;
      }

      if(this.selectOnClick) {
         this.selectNode(event);
      }

      if(this.checkboxEnable) {
         this.tree.setHighLightNodes(null);
      }

      // In mobile, when touch one node, should select the node and clear original node.
      if(isTouch) {
         event.stopPropagation();
         this.tree.selectNode(this.node, event, true);
         this.tree.clickNode(this.node);
         return;
      }

      // If regular click and this node is part of the selection, only select this node
      if(!this.selectOnClick && !event.ctrlKey && !event.metaKey && !event.shiftKey &&
         this.multiSelect && this.tree.selectedNodes.length > 1 &&
         this.tree.isSelectedNode(this.node))
      {
         this.tree.exclusiveSelectNode(this.node);
      }

      if(!this.multiSelect || (!event.ctrlKey && !event.metaKey && !event.shiftKey)) {
         this.tree.clickNode(this.node);
      }
   }

   mousedownSelectNode(event: MouseEvent): void {
      if(this.isToggleElementEventTarget(event)) {
         return;
      }

      if(!this.nodeSelectable) {
         return;
      }

      if(!this.selectOnClick && event.button === 0 || event.button == 1) {
         this.selectNode(event);
      }
   }

   get tooltip() {
      return !!this.node.tooltip && this.showOriginalName ?
         this.node.tooltip : !!this.node.tooltip ?
         this.node.label + "\n" + this.node.tooltip : this.node.label;
   }

   selectNode(event: MouseEvent | any, emit: boolean = true): void {
      if(!this.node.leaf) {
         event.stopPropagation();
      }

      this.tree.selectNode(this.node, event, emit);
   }

   doubleClickNode(event: MouseEvent) {
      if(this.isToggleElementEventTarget(event)) {
         return;
      }

      this.toggleNode();
      this.tree.doubleclickNode(this.node);
   }

   hasChildren(): boolean {
      return this.node && ((this.node.children && this.node.children.length > 0) ||
         this.node.leaf === false);
   }

   notExpandableType(): boolean {
      return this.node && !!this.node.data && this.node.data.type === "VARIABLE";
   }

   getIcon(): string {
      if(!this.showIcon || this.node.type == "custom" || this.node.type == "region") {
         return "";
      }

      let icon: string = null;

      if(this.iconFunction != null) {
         icon = this.iconFunction(this.node);
      }

      if(this.hasChildren()) {
         if(this.node.type == "table") {
            return this.node.expandedIcon || this.node.icon || icon ||
               "data-table-icon";
         }
         else if(this.node.expanded) {
            return this.node.expandedIcon || this.node.icon || icon ||
               "folder-open-icon";
         }
         else {
            return this.node.collapsedIcon || this.node.icon || icon ||
               "folder-icon";
         }
      }
      else if(this.node.data != "") {
         if(this.node.materialized) {
            return this.node && this.node.icon || icon ||
               "materialized-worksheet-icon";
         }
         else if(this.node.type == "^UPLOADED^") {
            return this.node && this.node.icon || icon ||
               "image-icon";
         }
         else if(this.node.type == "^SKIN^") {
            return this.node && this.node.icon || icon ||
               "image-icon";
         }
         else if(this.node.type) {
            const icon0 = GuiTool.getTreeNodeIconClass(this.node, "");

            if(icon0) {
               return this.node && this.node.icon || icon || icon0;
            }
         }

         return this.node && this.node.icon || icon ||
            "worksheet-icon";
      }
      else {
         return this.node && this.node.icon || icon ||
            "binding-tree-image composer-component-tree-file server-file-icon";
      }
   }

   getToggleIcon(): string {
      if(this.node.expanded) {
         return this.node && this.node.toggleExpandedIcon || "caret-down-icon icon-lg";
      }
      else {
         return this.node && this.node.toggleCollapsedIcon || "caret-right-icon icon-lg";
      }
   }

   get nodeLabel(): string {
      if(!this.node) {
         return "";
      }

      if(this.node.alias) {
         return this.node.alias;
      }

      if(this.node.baseLabel) {
         return this.node.baseLabel;
      }

      if(!!this.node.data && !!this.node.data.properties && !!this.node.data.properties.localStr) {
         return this.node.data.properties.localStr;
      }

      return this.node.label;
   }

   get favoritesUser(): boolean {
      if(this.showFavoriteIcon && !!this.node && !!this.node.data && !!this.node.data.favoritesUser) {
         return this.node.data.favoritesUser;
      }

      return false;
   }

   dragStarted(event: any): void {
      if(this.tree.selectedNodes == null || !this.isSelected()) {
         this.selectNode(event, false);
      }

      this.dragService.reset();

      if(!this.multiSelect) {
         // If no drag data is specified then just stringify the node data itself
         let dragData: any = [this.node.dragData || this.node.data];
         let data: any = {
            dragName: [this.node.dragName]
         };
         data[this.node.dragName] = dragData;
         Tool.setTransferData(event.dataTransfer, data);
         this.dragService.put(this.node.dragName, JSON.stringify(dragData));
      }
      else {
         let map: Map<string, any[]> = new Map();

         for(let node of this.tree.selectedNodes) {
            let data: any;

            if(node.dragData == null) {
               data = node.data;
            }
            else {
               data = node.dragData;
            }

            if(map.get(node.dragName) == undefined) {
               map.set(node.dragName, [data]);
            }
            else {
               map.get(node.dragName).push(data);
            }
         }

         const transferData: any = {
            dragName: []
         };

         map.forEach((value, key) => {
            transferData.dragName.push(key);
            transferData[key] = value;
            let data = JSON.stringify(value);
            this.dragService.put(key, data);
         });

         Tool.setTransferData(event.dataTransfer, transferData);
      }

      this.tree.onDrag(event);
   }

   dragOver(event: any): void {
      this.tree.onDragOver(event);
   }

   get isDraggable(): boolean {
      return this.node && this.node.dragName && this.draggable;
   }

   isHighLight() {
      let nodes = this.tree.highLightNodes;

      if(nodes == null) {
         return false;
      }

      return nodes.indexOf(this.node) !== -1;
   }

   isSelected() {
      let nodes = this.tree.selectedNodes;

      if(nodes == null) {
         return false;
      }

      if(this.isSelectedNode != null && this.isSelectedNode(this.node)) {
         return true;
      }

      if(!this.multiSelect) {
         return this.node && nodes && this.node == nodes[0];
      }

      return nodes.indexOf(this.node) !== -1;
   }

   isGrayedOut(): boolean {
      let grayedOutFields: any[] = this.tree.grayedOutFields;

      if(this.node && grayedOutFields) {
         for(let i = 0; grayedOutFields && i < grayedOutFields.length; i++) {
            if(this.node && this.node.data) {
               let name: string = null;

               if(typeof this.node.data == "string") {
                  name = this.node.data;
               }
               else if(this.node.data.name) {
                  name = this.node.data.name;
               }
               else {
                  let entry: AssetEntry = <AssetEntry> this.node.data;
                  name = this.getFieldName(entry);
               }

               if(name == grayedOutFields[i] || name == grayedOutFields[i].name) {
                  return true;
               }
            }
         }

         if(this.tree.isGrayFunction) {
            return this.tree.isGrayFunction(this.node);
         }
      }

      return false;
   }

   private getFieldName(entry: AssetEntry): string {
      if(entry == null) {
         return "";
      }

      let cvalue: string = AssetEntryHelper.getEntryName(entry);
      let attribute: string = entry.properties?.attribute;

      if(attribute == null && !!(<any> entry).attribute) {
         attribute = (<any> entry).attribute;
      }

      // normal chart entry not set entity and attribute properties,
      // cube entry set, the name should use entity + attribute
      if(attribute != null) {
         let entity: string = entry.properties["entity"];
         cvalue = (entity != null ? entity + "." : "") + attribute;
      }

      let idx: number = cvalue?.indexOf(":");

      // logical model?
      if(idx >= 0) {
         cvalue = cvalue.substring(0, idx) + "." +
            cvalue.substring(idx + 1, cvalue.length);
      }
      // worksheet?
      else {
         let assembly: string = entry.properties?.assembly;

         if(assembly != null && entry.properties?.isCalc != "true") {
            cvalue = assembly + "." + attribute;
         }
      }

      return cvalue;
   }

   @HostListener("drop", ["$event"])
   onDrop(event: any): void {
      event.preventDefault();
      event.stopPropagation();
      this.tree.onDrop({node: this.node, evt: event});
   }

   get keepAllChildren(): boolean {
      return this.forceMatch || this.searchStr && this.node.label && this.showRoot &&
         // if search found exact folder, show all children otherwise
         // finding a folder is kind of useless
         this.node.label.toLowerCase() == this.searchStr.toLowerCase();
   }

   getVirtualScrollShowChildren(): TreeNodeModel[] {
      if(!this.node) {
         return [];
      }

      let oChildren = this.node.children;
      let result = [];

      if(!this.useVirtualScroll || !this.dataSource) {
         return this.getSort(oChildren);
      }

      for(let node of oChildren) {
         if(this.dataSource.nodeVisible(node)) {
            result.push(node);
         }
      }

      return this.getSort(result);
   }

   getSort(nodes: TreeNodeModel[]) {
      if(this.searchStr && this.searchStr.trim()) {
         let searchSort = new SearchComparator(this.searchStr);

         return nodes.sort((a, b) => searchSort.searchSort(a, b));
      }

      return nodes;
   }

   /**
    * Checks whether the toggle element is the target of the event.
    *
    * @param event the event to check the target of
    * @returns true if the toggle element is the event target, false otherwise
    */
   private isToggleElementEventTarget(event: Event): boolean {
      return this.toggleElement && this.toggleElement.nativeElement === event.target;
   }

   private updateInViewport(visibleNodes?: TreeNodeModel[], forceRefresh?: boolean): void {
      if(this.useVirtualScroll && this.dataSource) {
         this.inViewport = !!visibleNodes ? visibleNodes.includes(this.node) ||
            this.dataSource.inSearchCollapsed(this.node) : this.dataSource.inViewport(this.node);
      }
      else if(!this.useVirtualScroll ) {
         this.inViewport = true;
      }

      if (this.inViewport || this.useVirtualScroll && this.dataSource &&
         this.dataSource.nodeVisible(this.node))
      {
         this.cdRef.reattach();
         this.cdRef.detectChanges();
      }
      else if(forceRefresh){
         this.cdRef.detach();
         this.cdRef.detectChanges();
      }
   }

   hasMenu(): boolean {
      return this.contextmenu && (!this.tree.hasMenuFunction ||
                                  this.tree.hasMenuFunction(this.node))
         && !GuiTool.isMobileDevice();
   }
}
