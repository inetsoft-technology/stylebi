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
   Component,
   ElementRef,
   EventEmitter,
   HostListener,
   Input,
   NgZone,
   OnDestroy,
   OnInit,
   Output,
   ViewChild
} from "@angular/core";
import { Router } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { RepositoryEntry } from "../../../../../shared/data/repository-entry";
import { RepositoryEntryType } from "../../../../../shared/data/repository-entry-type.enum";
import { ResourceAction } from "../../../../../shared/util/security/resource-permission/resource-action.enum";
import { Tool } from "../../../../../shared/util/tool";
import { AssemblyActionGroup } from "../../common/action/assembly-action-group";
import { Point } from "../../common/data/point";
import { RepositoryClientService } from "../../common/repository-client/repository-client.service";
import { ComponentTool } from "../../common/util/component-tool";
import { GuiTool } from "../../common/util/gui-tool";
import { LocalStorage } from "../../common/util/local-storage.util";
import { DomService } from "../dom-service/dom.service";
import { ActionsContextmenuComponent } from "../fixed-dropdown/actions-contextmenu.component";
import { DropdownOptions } from "../fixed-dropdown/dropdown-options";
import { FixedDropdownService } from "../fixed-dropdown/fixed-dropdown.service";
import { DebounceService } from "../services/debounce.service";
import { DragService } from "../services/drag.service";
import { ModelService } from "../services/model.service";
import { TreeNodeModel } from "../tree/tree-node-model";
import { TreeComponent } from "../tree/tree.component";
import { RepositoryBaseComponent } from "./repository-base-component";
import { RepositoryTreeAction } from "./repository-tree-action.enum";
import { RepositoryTreeService } from "./repository-tree.service";

@Component({
   selector: "repository-tree",
   templateUrl: "repository-tree.component.html",
   styleUrls: ["repository-tree.component.scss"],
   providers: [
      RepositoryClientService
   ]
})
export class RepositoryTreeComponent extends RepositoryBaseComponent implements OnInit, AfterViewChecked, OnDestroy {
   @Input() showRoot: boolean;
   @Input() permission: ResourceAction = null;
   @Input() selector: number = null;
   @Input() detailType: string = null;
   @Input() disabled: boolean = false;
   @Input() showContextMenu: boolean = false;
   @Input() expandAll: boolean = false;
   @Input() showTooltip: boolean = false;
   @Input() showFavoriteIcon: boolean = false;
   @Input() searchMode: boolean = false;
   @Input() searchStr: string;
   @Input() autoRefreshEnabled: boolean = true;
   @Input() multiSelect: boolean = false;
   @Input() draggable: boolean = false;
   @Input() initExpanded: boolean = false;
   @Input() autoExpandToSelectedNode: boolean = false;
   @Input() checkDetailType: boolean = false;
   @Input() isReport: boolean = true;
   @Input() showBurstReport: boolean = true;
   @Input() isPortalData: boolean = false;
   @Input() showVS: boolean = false;
   @Input() isMobile: boolean = false;
   @Output() nodeSelected = new EventEmitter<TreeNodeModel>();
   @Output() nodeClicked = new EventEmitter<TreeNodeModel>();
   @Output() autoRefreshTriggered = new EventEmitter();
   @Output() entryDeleted = new EventEmitter<RepositoryEntry>();
   @Output() updateRootNode = new EventEmitter<TreeNodeModel>();
   @ViewChild(TreeComponent) tree: TreeComponent;
   _selectedNode: TreeNodeModel;
   _root: TreeNodeModel;
   pendingRoot: TreeNodeModel; // root holding tree during loading of branches
   loading: boolean = false;

   @Input() set root(root: TreeNodeModel) {
      this._root = root;

      if(root && this.expandAll) {
         this.expandAllNodes(root);
      }
   }

   get root(): TreeNodeModel {
      return this._root;
   }

   @Input() set selectedNode(node: TreeNodeModel) {
      this._selectedNode = node;

      if(this.autoExpandToSelectedNode && !!node) {
         let nodePath = node.data.path;
         this.selectAndExpandToPath(nodePath, this.root);
      }
   }

   get selectedNode(): TreeNodeModel {
      return this._selectedNode;
   }

   constructor(protected repositoryTreeService: RepositoryTreeService,
               private dropdownService: FixedDropdownService,
               protected router: Router,
               protected modalService: NgbModal,
               protected modelService: ModelService,
               private repositoryClient: RepositoryClientService,
               private dragService: DragService,
               private debounceService: DebounceService,
               private zone: NgZone,
               private domService: DomService,
               private nativeElement: ElementRef)
   {
      super(repositoryTreeService, modalService, modelService, router);
   }

   ngOnInit() {
      this.repositoryClient.connect();
      this.repositoryClient.repositoryChanged.subscribe(() => {
         if(this.autoRefreshEnabled) {
            this.refreshTree();
         }
         else {
            this.autoRefreshTriggered.emit();
         }
      });
   }

   ngAfterViewChecked() {
      let scroll = Number(LocalStorage.getItem("repository-tree-scroll-position"));

      if(scroll) {
         this.nativeElement.nativeElement.scrollTop = scroll;
      }
   }

   isArchive(node: TreeNodeModel) {
      return node.data.classType == "CompositeArchiveEntry";
   }

   ngOnDestroy() {
      this.repositoryClient.disconnect();
   }

   nodeExpanded(node: TreeNodeModel): void {
      if(node == this.root || node.leaf || (node.children && node.children.length > 0)) {
         return;
      }

      node.loading = true;

      this.repositoryTreeService.getFolder(node.data.path, this.permission, this.selector,
         this.detailType, this.isFavoritesTree, this.isArchive(node), this.checkDetailType,
         this.isReport, false, this.isPortalData, this.showVS, this.showBurstReport)
         .subscribe(
            (data) => {
               node.children = data.children;
               node.loading = false;
            }, () => node.loading = false);
   }

   selectNode(node: TreeNodeModel): void {
      if(this.selectedNode == node) {
         return;
      }
      else {
         this.selectedNode = node;
         this.nodeSelected.emit(node);
      }
   }

   public selectAndExpandToPath(path: string, node: TreeNodeModel = this.root): void {
      let index: number = -1;

      // if root
      if(node.data.path === "/") {
         index = path.indexOf("/");
      }
      else {
         index = path.indexOf("/", node.data.path.length + 1);
      }

      let nextPath = index != -1 ? path.substring(0, index) : path;

      for(let child of node.children) {
         if(child.data.path === nextPath) {
            if(this.tree && nextPath === path) {
               this.tree.selectAndExpandToNode(child);
               this._selectedNode = child;
            }
            else {
               if(child.children && child.children.length > 0) {
                  this.selectAndExpandToPath(path, child);
               }
               else {
                  this.repositoryTreeService.getFolder(
                     nextPath, this.permission, this.selector, this.detailType,
                     this.isFavoritesTree, null, this.checkDetailType, this.isReport,
                     false, this.isPortalData, this.showVS, this.showBurstReport)
                     .subscribe((data) => {
                        child.children = data.children;
                        this.selectAndExpandToPath(path, child);
                     });
               }
            }

            break;
         }
      }
   }

   /**
    * Method for determining the css class of an entry by its AssetType
    */
   public getCSSIcon = (node: TreeNodeModel): string => {
      const entry: RepositoryEntry = node.data;
      return this.repositoryTreeService.getCSSIcon(entry, node.expanded);
   };

   public getParentNode(childNode: TreeNodeModel, parentNode?: TreeNodeModel): TreeNodeModel {
      return this.tree.getParentNode(childNode, parentNode);
   }

   openTreeContextmenu(event: [MouseEvent | TouchEvent, TreeNodeModel, TreeNodeModel[]]) {
      let options: DropdownOptions = {
         position : new Point(),
         contextmenu: true
      };

      if(event[0] instanceof MouseEvent) {
         options.position = {x: (<MouseEvent> event[0]).clientX + 1,
                             y: (<MouseEvent> event[0]).clientY};
      }
      else if(event[0] instanceof TouchEvent) {
         options.position = {x: (<TouchEvent> event[0]).targetTouches[0].pageX,
            y: (<TouchEvent> event[0]).targetTouches[0].pageY};
      }

      let contextmenu: ActionsContextmenuComponent =
         this.dropdownService.open(ActionsContextmenuComponent, options).componentInstance;
      contextmenu.sourceEvent = event[0];
      contextmenu.actions = this.createActions(event as any);
   }

   private createActions(event: [MouseEvent, TreeNodeModel, TreeNodeModel[]]): AssemblyActionGroup[] {
      return super.createActions0(event[1], event[2]);
   }

   hasMenuFunction(): any {
      return (node) => this.hasMenu(node);
   }

   hasMenu(node: TreeNodeModel): boolean {
      const actions = this.createActions([null, node, [node]]);
      return actions.some(group => group.visible);
   }

   getEntryIcon(classType: string): string {
      if(classType === "RepletFolderEntry") {
         return "folder-icon";
      }
      else if(classType === "ViewsheetEntry") {
         return "viewsheet-icon";
      }
      else if(classType === "RepletEntry") {
         return "report-icon";
      }
      else {
         return "archive-icon";
      }
   }

   getEntryLabel(entry: any): string {
      let textLabel = entry.label;
      let entryIconFn = this.getEntryIcon.bind(this);

      return `
      <div>
      <span>
        <span class="${entryIconFn(entry.classType)}">
        </span>
        <label>
        ${textLabel}
        </label>
      </span>
      </div>`;
   }

   nodeDrag(event: any): void {
      const textData = event.dataTransfer.getData("text");

      if(textData) {
         const jsonData = JSON.parse(textData);
         const entryLabelFn = this.getEntryLabel;

         if(this.multiSelect) {
            const labels = jsonData.RepositoryEntry.map(entryLabelFn.bind(this));
            const elem = GuiTool.createDragImage(labels, jsonData.dragName, 1, true);
            (<HTMLElement> elem).style.display = "flex";
            (<HTMLElement> elem).style.flexDirection = "column";
            (<HTMLElement> elem).style.lineHeight = "0.5";
            (<HTMLElement> elem).style.alignItems = "left";
            GuiTool.setDragImage(event, elem, this.zone, this.domService);
         }
      }
   }

   /**
    * Node drop event handler.
    */
   nodeDrop(event: any): void {
      let node: TreeNodeModel = event.node;
      let entries: RepositoryEntry[] = [];

      if(event.node == null) {
         return;
      }

      let parent: RepositoryEntry = <RepositoryEntry> node.data;

      // if the entry on which the drop happened is not a folder then get the parent entry
      if(parent.type != RepositoryEntryType.FOLDER &&
         parent.type != RepositoryEntryType.WORKSHEET_FOLDER)
      {
         let parentNode: TreeNodeModel = this.tree.getParentNode(node);

         if(parentNode) {
            parent = <RepositoryEntry> parentNode.data;
         }
      }

      if(parent.type != RepositoryEntryType.FOLDER &&
         parent.type != RepositoryEntryType.WORKSHEET_FOLDER ||
         parent.path == Tool.BUILT_IN_ADMIN_REPORTS)
      {
         return;
      }

      let dragData = this.dragService.getDragData();

      for(let key of Object.keys(dragData)) {
         let dragEntries: RepositoryEntry[] = JSON.parse(dragData[key]);

         if(dragEntries && dragEntries.length > 0) {
            for(let entry of dragEntries) {
               if(Tool.isEquals(parent, entry) || this.getParentPath(entry) === parent.path) {
                  continue;
               }

               const entryPath = entry.path.endsWith("/") ? entry.path : entry.path + "/";

               if(parent.path.startsWith(entryPath)) {
                  // trying to move entry to descendant, don't allow
                  continue;
               }

               if(entry.path === Tool.MY_REPORTS ||
                  (entry.path.startsWith("My Dashboards/") &&
                   !parent.path.startsWith(Tool.MY_REPORTS)))
               {
                  // composer.changeMyReports
                  ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
                     "Cannot change ''My Dashboards'' to the other folder!");
                  return;
               }

               if((parent.path == Tool.MY_REPORTS ||
                  parent.path && parent.path.startsWith(Tool.MY_REPORTS + "/")) &&
                  !entry.path.startsWith(Tool.MY_REPORTS + "/"))
               {
                  // composer.changeToMyReports
                  ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
                                                  "_#(js:composer.changeToMyDashboards)");
                  return;
               }

               // make sure the drag entries are from this tree
               if(this.tree.getNodeByData("data", entry)) {
                  entries.push(entry);
               }
            }
         }
      }

      if(entries.length > 0) {
         let someMatch = false;
         let allMatch = true;

         entries.forEach((entry) => {
            if(entry.op.includes(RepositoryTreeAction.CHANGE_FOLDER)) {
               someMatch = true;
            }
            else {
               allMatch = false;
            }
         });

         if(!someMatch) {
            ComponentTool.showMessageDialog(this.modalService, "Unauthorized",
               "_#(js:em.security.permit.move)");
            return;
         }

         let confirm: string;

         if(!allMatch) {
            confirm = "_#(js:em.reports.drag.partial.move)";
         }
         else {
            confirm = "_#(js:em.reports.drag.confirm)";
         }

         ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)", confirm).then((buttonClicked) =>
             {
               if(buttonClicked === "ok") {
                  if(!node.leaf) {
                     node.expanded = true;
                  }

                  for(let entry of entries) {
                     if(entry.op.includes(RepositoryTreeAction.CHANGE_FOLDER)) {
                        this.dispatchChangeEntryEvent(parent, entry);
                     }
                  }
               }
         });
      }
   }

   private getParentPath(entry: RepositoryEntry): string {
      if(entry.path === "/") {
         return null;
      }

      let index = entry.path.lastIndexOf("/");
      return index >= 0 ? entry.path.substring(0, index) : "/";
   }

   public refreshTree(): void {
      if(this.searchMode && !this.isFavoritesTree) {
         this.autoRefreshTriggered.emit();
      }
      else {
         this.debounceService.debounce("refreshTree", () => {
            this.zone.run(() => {
               let expandedQueue: TreeNodeModel[] = [this.root];
               this.addExpandedNodes(this.root, expandedQueue);
               let pendingNode = this.pendingRoot ? Tool.clone(this.pendingRoot) : null;
               this.fetchNode(expandedQueue, pendingNode);
            });
         }, 600, []);
      }
   }

   private fetchNode(expandedQueue: TreeNodeModel[], pendingNode: TreeNodeModel): void {
      let node = expandedQueue.shift();

      if(!node) {
         return;
      }

      // hold changes on the tree until it's completed so the tree doesn't open
      // close folders during reloading of tree.
      if(!pendingNode) {
         pendingNode = Tool.clone(this.root);
      }

      this.repositoryTreeService.getFolder(node.data.path, this.permission, this.selector,
         this.detailType, this.isFavoritesTree, null, this.checkDetailType,
         this.isReport, false, this.isPortalData, this.showVS, this.showBurstReport)
         .subscribe(
            (data) => {
               let treeNode = GuiTool.getNodeByPath(data.data.path, pendingNode);

               if(treeNode) {
                  treeNode.children = data.children;
                  treeNode.expanded = true;
               }

               if(expandedQueue.length == 0) {
                  this.root = pendingNode;
                  this.updateRootNode.emit(pendingNode);
                  pendingNode = null;
               }

               this.fetchNode(expandedQueue, pendingNode);
            });
   }

   private addExpandedNodes(node: TreeNodeModel, queue: TreeNodeModel[]) {
      if(node.leaf || !node.children) {
         return;
      }

      let q2 = [];

      for(let child of node.children) {
         if(child.expanded) {
            queue.push(child);
            q2.push(child);
         }
      }

      for(let child of q2) {
         this.addExpandedNodes(child, queue);
      }
   }

   private expandAllNodes(node: TreeNodeModel): void {
      node.expanded = true;

      for(let child of node.children) {
         if(!child.leaf) {
            this.repositoryTreeService.getFolder(child.data.path, this.permission,
               this.selector, this.detailType, this.isFavoritesTree, null, this.checkDetailType,
               this.isReport, false, this.isPortalData, this.showVS, this.showBurstReport)
               .subscribe((data) => {
                     child.children = data.children;
                     this.expandAllNodes(child);
               });
         }
      }
   }

   public deselectAllNodes(): void {
      this._selectedNode = null;
      this.tree.deselectAllNodes();
   }

   @HostListener("scroll")
   updateScrollPos() {
      LocalStorage.setItem("repository-tree-scroll-position",
         String(this.nativeElement.nativeElement.scrollTop));
   }
}
