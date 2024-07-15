/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import {
   ChangeDetectionStrategy,
   ChangeDetectorRef,
   Component,
   EventEmitter,
   Input, NgZone,
   OnChanges,
   OnDestroy,
   OnInit,
   Output,
   SimpleChange,
   SimpleChanges,
   TemplateRef,
   ViewChild
} from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { merge as observableMerge, Observable, throwError } from "rxjs";
import { catchError, finalize, map } from "rxjs/operators";
import { AssetEntry } from "../../../../../shared/data/asset-entry";
import { AssetType } from "../../../../../shared/data/asset-type";
import { Tool } from "../../../../../shared/util/tool";
import { AssetChangeEvent } from "../../common/asset-client/asset-change-event";
import { AssetClientService } from "../../common/asset-client/asset-client.service";
import { AssetConstants } from "../../common/data/asset-constants";
import { VariableInfo } from "../../common/data/variable-info";
import { CollectParametersOverEvent } from "../../common/event/collect-parameters-over-event";
import { GuiTool } from "../../common/util/gui-tool";
import { VariableInputDialogModel } from "../dialog/variable-input-dialog/variable-input-dialog-model";
import { DebounceService } from "../services/debounce.service";
import { TreeNodeModel } from "../tree/tree-node-model";
import { TreeComponent } from "../tree/tree.component";
import { AssetTreeService } from "./asset-tree.service";
import { LoadAssetTreeNodesEvent } from "./load-asset-tree-nodes-event";
import { LoadAssetTreeNodesValidator } from "./load-asset-tree-nodes-validator";
import { VirtualScrollTreeComponent } from "../tree/virtual-scroll-tree/virtual-scroll-tree.component";
import { TreeTool } from "../../common/util/tree-tool";

const isDataSource = (node: TreeNodeModel) => {
   const entry = node.data as AssetEntry;
   return entry && entry.type === AssetType.DATA_SOURCE_FOLDER;
};

@Component({
   selector: "asset-tree",
   templateUrl: "asset-tree.component.html",
   styleUrls: ["asset-tree.component.scss"],
   changeDetection: ChangeDetectionStrategy.OnPush,
   providers: [AssetClientService, DebounceService]
})
export class AssetTreeComponent implements OnInit, OnDestroy, OnChanges {
   @Input() reportRepositoryEnabled: boolean = false;
   @Input() datasources: boolean = true;
   @Input() columns: boolean = true;
   @Input() worksheets: boolean = true;
   @Input() viewsheets: boolean = true;
   @Input() tableStyles: boolean = false;
   @Input() scripts: boolean = false;
   @Input() library: boolean = false;
   @Input() draggable: boolean = false;
   @Input() physical: boolean = true;
   @Input() multiSelect: boolean = false;
   @Input() showContextmenu: boolean = false;
   @Input() hasMenuFunction: (node: TreeNodeModel) => boolean;
   @Input() isRejectFunction: (nodes: TreeNodeModel[]) => boolean;
   @Input() selectNodeOnLoadFn: (root: TreeNodeModel) => TreeNodeModel[];
   @Input() searchEnabled: boolean = false;
   @Input() dataSourcePath: string = null;
   @Input() dataSourceScope: number = null;
   @Input() defaultFolder: AssetEntry = null;
   @Input() readOnly = false;
   @Input() forceType: string = null;
   @Input() recentEnabled: boolean;
   @Input() getRecentTreeFun: () => Observable<TreeNodeModel[]>;
   @Input() initSelectedNodesExpanded: boolean;
   @Input() manyNodesUseVirtualScroll: boolean;
   @Output() nodesSelected = new EventEmitter<TreeNodeModel[]>();
   @Output() nodeSelected = new EventEmitter<TreeNodeModel>();
   @Output() pathSelected = new EventEmitter<TreeNodeModel[]>();
   @Output() dblclickNode = new EventEmitter<TreeNodeModel>();
   @Output() onContextmenu = new EventEmitter<[MouseEvent, TreeNodeModel, TreeNodeModel[]]>();
   @Output() nodeDrag = new EventEmitter<any>();
   @Output() nodeDrop = new EventEmitter<TreeNodeModel>();
   @ViewChild(VirtualScrollTreeComponent) virtualScrollTree: VirtualScrollTreeComponent;
   @ViewChild("variableInputDialog") variableInputDialog: TemplateRef<any>;

   root: TreeNodeModel;
   // activeRoot is same as root unless searching is being performed. in that case
   // the activeRoot is a copy of the root to avoid regular tree being expanded
   activeRoot: TreeNodeModel;
   selectedNodes: TreeNodeModel[] = [];
   dialogData: any;
   dataSourcesTree: TreeNodeModel;
   reportTree: TreeNodeModel;
   private readonly reportRoots = new Map<string, TreeNodeModel>();
   vSAdnWsLoadedAll: boolean = false;
   datasourceLoadedAll: boolean = false;
   nodesLoading: number = 0;
   useVirtualScroll: boolean = false;
   searchEndNode: (node: TreeNodeModel) => boolean = node =>  {
      return node?.data?.type == AssetType.PHYSICAL_TABLE || node?.data?.type == AssetType.TABLE ||
         node?.data?.type == AssetType.QUERY;
   };
   private loadDataSourcesAfterLoadRoot;

   get searchMode(): boolean {
      return this.root != this.activeRoot;
   }

   constructor(private assetTreeService: AssetTreeService,
               private changeDetector: ChangeDetectorRef,
               private assetClientService: AssetClientService,
               private modalService: NgbModal,
               private debounceService: DebounceService,
               private zone: NgZone)
   {
   }

   ngOnInit() {
      this.loadAssetTree();
      this.setupAssetClientService();
   }

   loadAssetTree() {
      const loadAssetTreeNodesEvent: LoadAssetTreeNodesEvent  = new LoadAssetTreeNodesEvent();

      if(this.dataSourcePath) {
         loadAssetTreeNodesEvent.setPath(this.dataSourcePath.split("/"));
         loadAssetTreeNodesEvent.setScope(this.dataSourceScope);
         loadAssetTreeNodesEvent.setIndex(-1);
      }
      else if(this.defaultFolder) {
         loadAssetTreeNodesEvent.setPath(this.defaultFolder.path.split("/"));
         loadAssetTreeNodesEvent.setScope(this.defaultFolder.scope);
         loadAssetTreeNodesEvent.setIndex(-1);
      }

      this.assetTreeService.getAssetTreeNode(loadAssetTreeNodesEvent, this.datasources,
                                             this.columns, this.worksheets, this.viewsheets,
                                             this.tableStyles, this.scripts, this.library,
                                             this.reportRepositoryEnabled,
                                             this.readOnly, this.physical)
         .subscribe((res) => {
            this.root = res.treeNodeModel;
            this.activeRoot = this.root;

            if(this.manyNodesUseVirtualScroll) {
               this.useVirtualScroll = TreeTool.needUseVirtualScroll(this.activeRoot);
            }

            this.revalidateExtraTrees();

            if(this.loadDataSourcesAfterLoadRoot) {
               this.addDeleteDataSources0();
            }

            if(this.defaultFolder) {
               const selectedNode: TreeNodeModel = this.getNodeByPath(
                  this.defaultFolder.path, this.root, this.defaultFolder.scope);

               if(selectedNode) {
                  this.selectNodes([selectedNode]);
               }
            }
            else if(this.selectNodeOnLoadFn) {
               this.selectNodes(this.selectNodeOnLoadFn(this.root));
            }

            if(this.dataSourcePath) {
               const selectedNode: TreeNodeModel = this.getNodeByPath(
                  this.dataSourcePath, this.root, this.dataSourceScope);

               if(selectedNode) {
                  this.selectNodes([selectedNode]);
               }
            }

            if(this.datasources) {
               this.dataSourcesTree = res.treeNodeModel.children.find(isDataSource);
            }

            this.changeDetector.markForCheck();
         },
         () => this.loadDataSourcesAfterLoadRoot = false,
         () => this.loadDataSourcesAfterLoadRoot = false
      );
   }

   ngOnDestroy() {
      this.assetClientService.disconnect();
   }

   ngOnChanges(changes: SimpleChanges): void {
      let refreshWsRoot = changes.hasOwnProperty("columns") && this.root != null &&
         changes["columns"].previousValue != changes["columns"].currentValue;

      if(changes.datasources && !changes.datasources.firstChange) {
         this.addDeleteDataSources();
      }
      else if(changes.dataSourcePath && !this.dataSourcePath) {
         this.selectedNodes = [];
      }

      if(refreshWsRoot) {
         this.refreshFromRoot(node => node.data.type == AssetType.FOLDER &&
            node.label == "_#(js:Global Worksheet)");
      }
   }

   addDeleteDataSources(refreshRoot?: boolean) {
      if(!this.root) {
         this.loadDataSourcesAfterLoadRoot = true;
         return;
      }

      this.addDeleteDataSources0(this.vSAdnWsLoadedAll && this.searchMode, refreshRoot);
   }

   private addDeleteDataSources0(loadAll: boolean = false, refreshRoot?: boolean): void {
      const rootEvent = this.createRefreshNodeEvent(this.root, false);
      this.setLoadingIndicator(this.root, true);
      const obs = this.assetTreeService.getAssetTreeNode(rootEvent, this.datasources,
         this.columns, this.worksheets, this.viewsheets, this.tableStyles, this.scripts, this.library, false,
         this.readOnly, this.physical);

      obs.subscribe(
         (res) => {
            this.removeExtraTrees();
            this.dataSourcesTree = res.treeNodeModel.children.find(isDataSource);
            this.addExtraTrees();
         },
         (error) => {
            console.error("Failed to get node children: ", error);
            this.setLoadingIndicator(this.root, false);
         },
         () => {
            this.setLoadingIndicator(this.root, false);
            this.refreshSelectedNodes();

            if(refreshRoot) {
               this.refreshFromRoot();
            }
            else if(loadAll) {
               const event = this.createRefreshNodeEvent(this.dataSourcesTree, true);
               this.refreshNodeChildren(this.dataSourcesTree, event);
            }
         });
   }

   private setupAssetClientService() {
      this.assetClientService.connect();
      this.assetClientService.assetChanged.subscribe((event) => {
         this.handleAssetChangeEvent(event);
      });
   }

   private handleAssetChangeEvent(event: AssetChangeEvent) {
      if(event.parentEntry == null) {
         this.refreshFromRoot();
         return;
      }

      let oldParentNode = this.findAssetTreeNodeParentFromIdentifier(this.root,
                                                                     event.oldIdentifier);
      let newParentNode = this.findAssetTreeNodeFromIdentifier(this.root,
                                                               event.parentEntry.identifier);
      let loadOldParentEvent = oldParentNode !== newParentNode ?
         this.createRefreshNodeEvent(oldParentNode) : null;
      let loadNewParentEvent = this.createRefreshNodeEvent(newParentNode, this.searchMode);

      if(loadOldParentEvent || loadNewParentEvent) {
         this.refreshParentNodes(oldParentNode, loadOldParentEvent,
            newParentNode, loadNewParentEvent);
      }
      // Could not find the assets in the tree.
      else {
         this.refreshFromRoot();
      }
   }

   private refreshParentNodes(rootNode1: TreeNodeModel, parentEvent1: LoadAssetTreeNodesEvent,
                              rootNode2: TreeNodeModel, parentEvent2: LoadAssetTreeNodesEvent)
   {
      let loadParent1 = !this.containsLoadAssetEvent(parentEvent2, parentEvent1);
      let loadParent2 = !this.containsLoadAssetEvent(parentEvent1, parentEvent2);
      let requests = [];

      if(loadParent1 && parentEvent1) {
         let request1 = this.refreshNodeChildren(rootNode1, parentEvent1);
         requests.push(request1);
      }

      if(loadParent2 && parentEvent2) {
         let request2 = this.refreshNodeChildren(rootNode2, parentEvent2);
         requests.push(request2);
      }

      // When parents are finished loading, refresh tree node selection.
      observableMerge(...requests)
         .subscribe(
            () => {},
            () => {},
            () => this.refreshSelectedNodes());
   }

   private refreshSelectedNodes() {
      if(this.selectedNodes.length > 0) {
         let newSelectedNodes: TreeNodeModel[] = [];
         let oldSelectedNodes = [...this.selectedNodes];
         this.recursiveRefreshSelectedNodes(this.root, newSelectedNodes, oldSelectedNodes);
         this.selectedNodes = newSelectedNodes.filter((node) => node != undefined);
      }
   }

   private recursiveRefreshSelectedNodes(
      updatedNode: TreeNodeModel, newSelectedNodes: TreeNodeModel[],
      oldSelectedNodes: TreeNodeModel[])
   {
      if(!updatedNode) {
         return;
      }

      if(updatedNode && updatedNode.data) {
         let entry: AssetEntry = updatedNode.data;

         let oldIndex = oldSelectedNodes.findIndex((oldNode) => {
            if(oldNode && oldNode.data) {
               let oldEntry: AssetEntry = oldNode.data;
               return oldEntry.identifier === entry.identifier;
            }

            return false;
         });

         if(oldIndex >= 0) {
            newSelectedNodes[oldIndex] = updatedNode;
         }
      }

      if(updatedNode && updatedNode.children) {
         for(let child of updatedNode.children) {
            this.recursiveRefreshSelectedNodes(child, newSelectedNodes, oldSelectedNodes);
         }
      }
   }

   /**
    * Send a request to the server to refresh the given tree node's children.
    *
    * @param root the tree node to refresh
    * @param rootEvent the event to send to the server
    * @returns an observable that emits the updated node
    */
   private refreshNodeChildren(
      root: TreeNodeModel,
      rootEvent: LoadAssetTreeNodesEvent, expandNode?: boolean): Observable<LoadAssetTreeNodesValidator>
   {
      this.setLoadingIndicator(root, true);
      const obs = this.assetTreeService.getAssetTreeNode(rootEvent, this.datasources,
                                                         this.columns, this.worksheets, this.viewsheets,
                                                         this.tableStyles, this.scripts, this.library,
                                                         this.reportRepositoryEnabled,
                                                         this.readOnly, this.physical);

      obs.pipe(
         catchError((error) => {
            console.error("Failed to get node children: ", error);
            this.setLoadingIndicator(root, true);
            return throwError(error);
         }),
         finalize(() => {
            if(rootEvent.isLoadAll()) {
               this.nodesLoading--;

               if(this.nodesLoading == 0) {
                  this.vSAdnWsLoadedAll = true;

                  if(this.datasources) {
                     this.datasourceLoadedAll = true;
                  }
               }

               // if finished loading the whole tree and in search mode
               if((this.vSAdnWsLoadedAll || this.datasourceLoadedAll) && this.searchMode) {
                  this.activeRoot = Tool.clone(this.root);
                  this.changeDetector.markForCheck();
               }
            }
         }))
         .subscribe((res) => {
            let searchMode = this.searchMode;

            if(res.parameters && res.parameters.length) {
               this.dialogData = {varInfos: res.parameters};

               this.modalService.open(this.variableInputDialog, {backdrop: "static"}).result
                  .then(
                     (model: VariableInputDialogModel) => {
                        let event: CollectParametersOverEvent = new CollectParametersOverEvent(model.varInfos);

                        if(rootEvent.getTargetEntry()?.type == AssetType.DATA_SOURCE &&
                           rootEvent.getTargetEntry()?.properties &&
                           rootEvent.getTargetEntry()?.properties["datasource.type"] == "text")
                        {
                           let variableInfo: VariableInfo =  {
                              name: "^Db_Name^",
                              value: [rootEvent.getTargetEntry().path]
                           };

                           event?.variables.push(variableInfo);
                        }

                        let set = this.assetTreeService.setConnectionVariables(event);

                        set.subscribe(
                           () => {},
                           () => {},
                           () => this.refreshNodeChildren(root, rootEvent)
                        );
                     },
                     () => {}
                  );
            }
            else {
               root.children = res.treeNodeModel.children;
            }

            this.setLoadingIndicator(root, false, () => {
               if(searchMode && !expandNode) {
                  this.activeRoot = Tool.clone(this.root);
                  this.updateUseVirtualScroll();
               }
            });

            this.updateUseVirtualScroll();

            if(this.virtualScrollTree && this.useVirtualScroll) {
               this.virtualScrollTree.nodeStateChanged(root, true);
            }

            this.changeDetector.markForCheck();
         });

      this.changeDetector.markForCheck();
      return obs;
   }

   private loadAll(justLoadDataSource: boolean): void {
      if(!justLoadDataSource) {
         this.vSAdnWsLoadedAll = false;
      }

      this.datasourceLoadedAll = false;
      this.nodesLoading = 0;
      this.root.children.forEach((node) => {
         if(!justLoadDataSource || node.data.type == AssetType.DATA_SOURCE_FOLDER && node.data.path == "/") {
            this.nodesLoading++;
            let event = this.createRefreshNodeEvent(node, true);
            this.refreshNodeChildren(node, event);
         }
      });
   }

   /**
    * Determines whether or not checkEvent is recursively contained within rootEvent.
    */
   private containsLoadAssetEvent(
      rootEvent: LoadAssetTreeNodesEvent,
      checkEvent: LoadAssetTreeNodesEvent): boolean
   {
      if(rootEvent == null || checkEvent == null) {
         return false;
      }

      if(rootEvent.getTargetEntry() === checkEvent.getTargetEntry()) {
         return true;
      }
      else {
         for(let descendant of rootEvent.getExpandedDescendants()) {
            if(this.containsLoadAssetEvent(descendant, checkEvent)) {
               return true;
            }
         }

         return false;
      }
   }

   /**
    * Refresh the entire asset tree.
    */
   private refreshFromRoot(filter?: (node: TreeNodeModel) => boolean) {
      const rootObservables: Observable<LoadAssetTreeNodesValidator>[] = [];
      let isSearchMode = this.searchMode;

      this.root.children
         .filter(node => !filter || filter(node))
         .forEach((child) => {
            const rootEvent = this.createRefreshNodeEvent(child, isSearchMode);
            rootObservables.push(this.refreshNodeChildren(child, rootEvent));
         });

      observableMerge(rootObservables).subscribe(
         () => {
         },
         () => {
         },
         () => this.refreshSelectedNodes());

      if(!isSearchMode) {
         this.vSAdnWsLoadedAll = false;
         this.datasourceLoadedAll = false;
      }
      else if(!this.datasources) {
         this.datasourceLoadedAll = false;
      }
   }

   private createRefreshNodeEvent(root: TreeNodeModel, loadAll: boolean = false): LoadAssetTreeNodesEvent {
      if(!root) {
         return null;
      }

      let entry: AssetEntry = root.data;
      let event = new LoadAssetTreeNodesEvent();
      event.setTargetEntry(entry);
      event.setLoadAll(loadAll);

      if(!loadAll && root && entry && !root.leaf) {
         if(root.expanded && root.children && root.children.length > 0) {
            root.children.forEach((child) => {
               if(!!child && child.data && child.expanded) {
                  event.getExpandedDescendants().push(this.createRefreshNodeEvent(child, loadAll));
               }
            });
         }
      }

      return event;
   }

   findAssetTreeNodeFromIdentifier(root: TreeNodeModel, identifier: string): TreeNodeModel {
      if(identifier == null) {
         return null;
      }

      let entry: AssetEntry = root.data;

      if(entry != null && entry.identifier === identifier) {
         return root;
      }

      if(!!root.children && root.children.length > 0) {
         for(let child of root.children) {
            let childNode = this.findAssetTreeNodeFromIdentifier(child, identifier);

            if(childNode != null) {
               return childNode;
            }
         }
      }

      return null;
   }

   findAssetTreeNodeParentFromIdentifier(root: TreeNodeModel, identifier: string): TreeNodeModel {
      if(identifier == null) {
         return null;
      }

      if(!!root.children && root.children.length > 0) {
         for(let child of root.children) {
            let childEntry: AssetEntry = child.data;

            if(childEntry && childEntry.identifier === identifier) {
               return root;
            }

            let childNode = this.findAssetTreeNodeParentFromIdentifier(child, identifier);

            if(childNode != null) {
               return childNode;
            }
         }
      }

      return null;
   }

   /**
    * If a node has not been populated before, attempt to populate it
    */
   nodeExpanded(node: TreeNodeModel): void {
      if(node.data && !node.leaf && ( !node.children || node.children.length === 0)) {
         let entry: AssetEntry = node.data;
         let event = new LoadAssetTreeNodesEvent();
         event.setTargetEntry(entry);
         this.refreshNodeChildren(node, event, true);
      }
   }

   selectNodes(nodes: TreeNodeModel[]): void {
      this.nodesSelected.emit(nodes);
      this.nodeSelected.emit(nodes[0]);
      this.selectedNodes = nodes;

      if(this.pathSelected.observers.length > 0 && nodes[0] != null) {
         this.pathSelected.emit(this.getPathToEntry(this.root, nodes[0].data));
      }
   }

   doubleclickNode(node: TreeNodeModel): void {
      this.dblclickNode.emit(node);
   }

   contextmenuTreeNode(event: [MouseEvent, TreeNodeModel, TreeNodeModel[]]) {
      this.onContextmenu.emit(event);
   }

   /**
    * Method for determining the css class of an entry by its AssetType
    */
   public getCSSIcon(node: TreeNodeModel): string {
      return GuiTool.getTreeNodeIconClass(node, "");
   }

   public getParentNode(childNode: TreeNodeModel, parentNode?: TreeNodeModel): TreeNodeModel {
      return this.virtualScrollTree.tree.getParentNode(childNode, parentNode);
   }

   public getNodeByData(compareType: string, data: any, parentNode?: TreeNodeModel): TreeNodeModel {
      return this.virtualScrollTree.tree.getNodeByData(compareType, data, parentNode);
   }

   searchStart(start: boolean): void {
      if(start) {
         if(!this.hasLoadedAllNode()) {
            if(!this.vSAdnWsLoadedAll) {
               this.activeRoot = <TreeNodeModel>{
                  leaf: false,
                  loading: true,
                  children: [
                     <TreeNodeModel>{
                        label: "_#(js:Loading)",
                        icon: "search-icon"
                     }
                  ]
               };
            }

            // don't start another load if one is already in progress
            if(this.nodesLoading <= 0) {
               this.loadAll(this.vSAdnWsLoadedAll && this.datasources);
            }
         }
         else {
            this.activeRoot = Tool.clone(this.root);
         }
      }
      else {
         this.activeRoot = this.root;
      }

      this.updateUseVirtualScroll();
   }

   private updateUseVirtualScroll(): void {
      this.useVirtualScroll = false;

      if(this.manyNodesUseVirtualScroll && this.hasLoadedAllNode()) {
         this.useVirtualScroll = TreeTool.needUseVirtualScroll(this.activeRoot);
      }
   }

   private hasLoadedAllNode(): boolean {
      return this.vSAdnWsLoadedAll && (!this.datasources || this.datasourceLoadedAll);
   }

   private getNodeByPath(path: string, root: TreeNodeModel, scope: number = null): TreeNodeModel {
      if(root && root.data && (path == root.data.path ||
         path == root.data.properties["prefix"] + "/" + root.data.properties["source"] &&
         (root.data.type == "QUERY" || root.data.type == "LOGIC_MODEL")))
      {
         if(scope == null || scope == root.data.scope || scope == AssetConstants.TEMPORARY_SCOPE) {
            return root;
         }
      }

      if(!root.leaf) {
         for(let child of root.children) {
            let node: TreeNodeModel = this.getNodeByPath(path, child, scope);

            if(node) {
               return node;
            }
         }
      }

      return null;
   }

   private getPathToEntry(root: TreeNodeModel, entry: AssetEntry): TreeNodeModel[] {
      if(root != null && root.data === entry) {
         return [root];
      }
      else if(root == null || (this.root != root && !root.expanded) || root.children.length == 0) {
         return null;
      }

      for(const child of root.children) {
         const path = this.getPathToEntry(child, entry);

         if(path != null) {
            path.push(root);
            return path;
         }
      }

      return null;
   }

   /**
    * Remove the extra trees from the root data tree. It is assumed that the extra tree field
    * references are unchanged at this point.
    */
   private removeExtraTrees() {
      if(this.root == null) {
         return;
      }

      if(this.dataSourcesTree) {
         const oldDataSourceIndex = this.root.children.findIndex(node =>
            node.data?.identifier === this.dataSourcesTree.data?.identifier);

         if(oldDataSourceIndex >= 0) {
            this.root.children.splice(oldDataSourceIndex, 1);
         }
      }

      if(this.reportTree) {
         const oldReportFolderIndex = this.root.children.indexOf(this.reportTree);

         if(oldReportFolderIndex >= 0) {
            this.root.children.splice(oldReportFolderIndex, 1);
         }
      }

      this.changeDetector.markForCheck();
   }

   /**
    * Add the extra trees to the root data tree.
    */
   private addExtraTrees() {
      if(this.root == null) {
         return;
      }

      if(this.dataSourcesTree != null) {
         this.root.children.splice(0, 0, this.dataSourcesTree);
      }

      if(this.reportTree != null) {
         this.root.children.splice(0, 0, this.reportTree);
      }

      if(!this.searchMode) {
         this.updateUseVirtualScroll();
      }

      this.changeDetector.markForCheck();
   }

   /**
    * Remove data source & report trees from the main root and then add the updated trees if they
    * exist.
    */
   private revalidateExtraTrees() {
      this.removeExtraTrees();
      this.addExtraTrees();
   }

   public refreshView() {
      this.changeDetector.markForCheck();
   }

   // bug #62118, prevent "flashing" of loading indicator
   private setLoadingIndicator(node: TreeNodeModel, loading: boolean, callBack?: () => any): void {
      if(node == null || node.data == null) {
         return;
      }

      if(loading && !node.data.loadingDebounced) {
         // immediately apply loading if not already debounced for this node
         node.loading = true;
      }
      else {
         node.data.loadingDebounced = true;
         this.debounceService.debounce(
            `loading:${node.dataLabel}`, (n, l) => this.updateLoadingIndicator(n, l, callBack),
            250, [node, loading]);
      }
   }

   private updateLoadingIndicator(node: TreeNodeModel, loading: boolean, callBack?: () => any): void {
      this.zone.run(() => {
         node.loading = loading;
         node.data.loadingDebounced = false;

         if(callBack) {
            callBack();
         }

         this.changeDetector.detectChanges();
      });
   }

   getNodeEqualsFun(): (node1: TreeNodeModel, node2: TreeNodeModel) => boolean {
      return (node1: TreeNodeModel, node2: TreeNodeModel) => {
         return node1?.data?.identifier == node2?.data?.identifier;
      };
   }
}
