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
import { HttpHeaders } from "@angular/common/http";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import { AssetEntryHelper } from "../../../common/data/asset-entry-helper";
import { AssetType } from "../../../../../../shared/data/asset-type";
import { Tool } from "../../../../../../shared/util/tool";
import { DataRefType } from "../../../common/data/data-ref-type";
import { ContextMenuActions } from "../../../widget/context-menu/context-menu-actions";
import { ModelService } from "../../../widget/services/model.service";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { SourceInfo } from "../../data/source-info";
import { TreeTool } from "../../../common/util/tree-tool";
import { BehaviorSubject, Observable } from "rxjs";
import { VirtualScrollTreeDatasource } from "../../../widget/tree/virtual-scroll-tree-datasource";

export abstract class BindingTreeService {
   headers: HttpHeaders;
   _bindingTreeModel: TreeNodeModel;
   private _loadingTree: boolean = false;
   private _needUseVirtualScroll: boolean = false;
   private _bindingTreeChanged = new BehaviorSubject<TreeNodeModel>(null);
   private _virtualScrollDataSource: VirtualScrollTreeDatasource = new VirtualScrollTreeDatasource();
   private searching: boolean = false;
   private recordedExpandedNodes: Map<string, boolean> = new Map<string, boolean>();

   constructor() {
      this.headers = new HttpHeaders({
         "Content-Type": "application/json"
      });
   }

   resetTreeModel(bindingTreeModel: TreeNodeModel, refreshVirtualScroll: boolean = true): void {
      this._bindingTreeModel = bindingTreeModel;
      this.expandRoot();
      this.refreshVirtualScroll(refreshVirtualScroll);
      this._bindingTreeChanged.next(this._bindingTreeModel);
   }

   get bindingTreeModel(): TreeNodeModel {
      return this._bindingTreeModel;
   }

   set bindingTreeModel(bindingTreeModel: TreeNodeModel) {
      this._bindingTreeModel = bindingTreeModel;
      this.expandRoot();
      this.refreshVirtualScroll();
      this._bindingTreeChanged.next(this._bindingTreeModel);
   }

   get needUseVirtualScroll(): boolean {
      return this._needUseVirtualScroll;
   }

   get virtualScrollDataSource() {
      return this._virtualScrollDataSource;
   }

   private expandRoot() {
      if(this._bindingTreeModel && this._bindingTreeModel.children.length == 1 &&
         this._bindingTreeModel.children[0])
      {
         this._bindingTreeModel.children[0].expanded = true;
         this.expandNode(this._bindingTreeModel.children[0]);
      }
   }

   /**
    * Get an entry of an item node.
    * @return an entry or null if id is invalid.
    */
   getEntry(identifier: string): AssetEntry {
      const node = this.getNode(identifier);
      return node ? node.data : null;
   }

   getNode(identifier: string): TreeNodeModel {
      let children: TreeNodeModel[] = this.bindingTreeModel.children;

      return this.getEntry0(identifier, children);
   }

   refreshVirtualScroll(refreshVirtualScroll: boolean = true): void {
      this._needUseVirtualScroll = TreeTool.needUseVirtualScroll(this._bindingTreeModel);

      if(this.needUseVirtualScroll && refreshVirtualScroll && this._virtualScrollDataSource) {
         this._virtualScrollDataSource.refreshByRoot(this.bindingTreeModel);
      }
   }

   private getEntry0(identifier: string, nodes: TreeNodeModel[]): TreeNodeModel {
      for(let node of nodes) {
         if(node.data && AssetEntryHelper.toIdentifier(node.data) == identifier) {
            return node;
         }

         let cnode: TreeNodeModel = this.getEntry0(identifier, node.children);

         if(cnode != null) {
            return cnode;
         }
      }

      return null;
   }

   /**
    * Get table name for the given asset entry.
    */
   getSourceInfo(entry: AssetEntry): any {
      let tname: string = entry.properties["table"];

      if(tname != null) {
         tname = tname.trim();

         if(tname != "") {
            return { source: tname,
                     prefix: entry.properties["prefix"],
                     type: SourceInfo.MODEL
                   };
         }
      }

      if(AssetEntryHelper.isTable(entry) || AssetEntryHelper.isPhysicalTable(entry)) {
         return { source: AssetEntryHelper.getEntryName(entry),
                  prefix: entry.properties["prefix"],
                  type: SourceInfo.PHYSICAL_TABLE
                };
      }

      if(AssetEntryHelper.isQuery(entry)) {
         return { source: AssetEntryHelper.getEntryName(entry),
                  prefix: entry.properties["prefix"],
                  type: SourceInfo.QUERY
                };
      }

      if(AssetEntryHelper.isLogicModel(entry)) {
         return { source:  entry.properties["source"],
                  prefix: entry.properties["prefix"],
                  type: SourceInfo.MODEL
                };
      }

      if(AssetEntryHelper.isFolder(entry)) {
         tname = entry.path;

         if(tname != null && tname != "") {
            return { source: tname,
                     prefix: entry.properties["prefix"],
                     type: SourceInfo.NONE
                   };
         }
      }

      if(AssetEntryHelper.isWorksheet(entry)) {
         return {
            source: entry.identifier,
            type: SourceInfo.ASSET
         };
      }

      if(AssetEntryHelper.isColumn(entry)) {
         let parentEntry: AssetEntry = this.getParentEntry(entry);

         if(!parentEntry) {
            return null;
         }

         while(!AssetEntryHelper.isTable(parentEntry) &&
               !AssetEntryHelper.isQuery(parentEntry) &&
               !AssetEntryHelper.isWorksheet(parentEntry)
               // should ignore 'Dimensions' and 'Measures'
               && (AssetEntryHelper.isFolder(parentEntry)
               && (this.getParentNodeByEntry(entry).label == "_#(js:Dimensions)"
               || this.getParentNodeByEntry(entry).label == "_#(js:Measures)")))
         {
            let parentEntry0: AssetEntry = this.getParentEntry(parentEntry);

            if(parentEntry0 == null) {
               break;
            }

            parentEntry = parentEntry0;
         }

         return { source: AssetEntryHelper.isWorksheet(parentEntry) ?
                     parentEntry.identifier :
                     AssetEntryHelper.getEntryName(parentEntry),
                  prefix: parentEntry.properties["prefix"],
                  type: this.getSourceType(parentEntry)
                };
      }

      return null;
   }

   private getSourceType(entry: AssetEntry): number {
      if(AssetEntryHelper.isTable(entry)) {
         if(entry.properties["mainType"] == "component") {
            return SourceInfo.REPORT;
         }

         return SourceInfo.PHYSICAL_TABLE;
      }
      else if(AssetEntryHelper.isLogicModel(entry)) {
         return SourceInfo.MODEL;
      }
      // local query
      else if(AssetEntryHelper.isQuery(entry) && entry.scope == 2) {
         return SourceInfo.LOCAL_QUERY;
      }
      else if(AssetEntryHelper.isQuery(entry)) {
         return SourceInfo.QUERY;
      }
      else if(AssetEntryHelper.isWorksheet(entry)) {
         return SourceInfo.ASSET;
      }
      else if(AssetEntryHelper.isFolder(entry)) {
         return SourceInfo.REPORT;
      }

      return SourceInfo.NONE;
   }

   getSourceTreeNode(sourceInfo: SourceInfo,
      treeNodeModel?: TreeNodeModel): TreeNodeModel
   {
      if(treeNodeModel == null) {
         treeNodeModel = this.bindingTreeModel;
      }

      return BindingTreeService.getTreeNodeBySourceInfo(sourceInfo, treeNodeModel);
   }

   static getTreeNodeBySourceInfo(sourceInfo: SourceInfo, treeNodeModel?: TreeNodeModel): TreeNodeModel {
      if(!treeNodeModel || treeNodeModel.children.length == 0 || !sourceInfo.source) {
         return null;
      }

      for(let i = 0; i < treeNodeModel.children.length; i++) {
         let childTreeNode: TreeNodeModel = treeNodeModel.children[i];

         if(childTreeNode.label == sourceInfo.source ||
            childTreeNode.data.identifier == sourceInfo.source ||
            childTreeNode.data.properties["classtype"] == sourceInfo.source)
         {
            return childTreeNode;
         }
         else if(childTreeNode.children.length > 0) {
            let nodemodel = BindingTreeService.getTreeNodeBySourceInfo(sourceInfo, childTreeNode);

            if(nodemodel){
               return nodemodel;
            }
         }
      }

      return null;
   }

   /**
    * Get table name for the given asset entry.
    */
   getTableName(entry: AssetEntry): string {
      let sourceInfo: any = this.getSourceInfo(entry);
      return sourceInfo ? sourceInfo.source : null;
   }

   /**
    * Find the parent entry in the tree model.
    */
   getParent(entry: AssetEntry): AssetEntry {
      let pentry0: AssetEntry = AssetEntryHelper.getParent(entry);

      if(pentry0 == null) {
         return null;
      }

      let identifier: string = AssetEntryHelper.toIdentifier(pentry0);
      let pentry: AssetEntry = this.getEntry(identifier);

      if(pentry == null) {
         pentry0 = <AssetEntry> {
            scope: pentry0.scope,
            type: AssetType.TABLE,
            path: pentry0.path,
            user: pentry0.user
         };

         pentry =
            this.getEntry(AssetEntryHelper.toIdentifier(pentry0));
      }

      return pentry || AssetEntryHelper.getParent(entry);
   }

   loadFullTree(): Promise<boolean> {
      return Promise.resolve(true);
   }

   changeSearchState(searching: boolean): void {
      this.searching = searching;
   }

   get isSearching(): boolean {
      return this.searching;
   }

   recordExpandedNodes(root?: TreeNodeModel, append: boolean = false) {
      if(!append) {
         this.recordedExpandedNodes.clear();
      }

      this.recordExpandedNodes0(root ? root : this.bindingTreeModel,
         this.recordedExpandedNodes);
   }

   private recordExpandedNodes0(node: TreeNodeModel, result: Map<string, boolean>) {
      if(node?.expanded && node.data?.identifier) {
         result.set(node.data.identifier, true);
      }

      if(node?.children) {
         node.children.forEach(cNode => this.recordExpandedNodes0(cNode, result));
      }
   }

   expandNodesCollapseOthersByRecord(): void {
      if(this.bindingTreeModel.children) {
         this.bindingTreeModel.children
            .forEach(node =>
               this.expandNodesCollapseOthersByRecord0(node, this.recordedExpandedNodes));
      }
   }

   expandNodesCollapseOthersByRecord0(node: TreeNodeModel, expandMap: Map<string, boolean>): void {
      if(!node?.data?.identifier) {
         return;
      }

      node.expanded = expandMap.get(node.data.identifier);

      if(node.children) {
         node.children.forEach(n =>
            this.expandNodesCollapseOthersByRecord0(n, expandMap));
      }
   }

   private getParentEntry(entry: AssetEntry): AssetEntry {
      let parentNode: TreeNodeModel = this.getParentNodeByEntry(entry);

      if(parentNode == null) {
         return null;
      }

      return parentNode.data;
   }

   private getParentNodeByEntry(childData: AssetEntry,
      parentNode?: TreeNodeModel): TreeNodeModel
   {
      if(parentNode == null) {
         parentNode = this.bindingTreeModel;
      }

      if(parentNode.children == null || parentNode.children.length == 0) {
         return null;
      }

      for(let i = 0; i < parentNode.children.length; i++) {
         let node: TreeNodeModel = parentNode.children[i];

         if(Tool.isEquals(node.data, childData)) {
            return parentNode;
         }

         node = this.getParentNodeByEntry(childData, node);

         if(node != null) {
            return node;
         }
      }

      return null;
   }

   protected copyExpanded(oroot: TreeNodeModel, root: TreeNodeModel): void {
      if(oroot.label == root.label) {
         root.expanded = oroot.expanded;

         if(oroot.children && root.children && oroot.children.length == root.children.length) {
            for(let i = 0; i < oroot.children.length; i++) {
               this.copyExpanded(oroot.children[i], root.children[i]);
            }
         }
      }
   }

   public expandAllChildren(node: TreeNodeModel): void {
      if(!node.leaf) {
         node.expanded = true;

         for(let child of node.children) {
            this.expandAllChildren(child);
         }
      }
   }

   unSelectNode(nodeNmae: string): void {
      // should be override if needed.
   }

   public getCubeColumnType(entry: AssetEntry): number {
      if(!entry) {
         return null;
      }

      let properties: any = entry.properties;
      let rtype = properties["refType"];
      let refType: number = rtype ? parseInt(rtype, 10) : DataRefType.NONE;

      if(entry && AssetEntryHelper.isColumn(entry) &&
         entry.type != AssetType.PHYSICAL_COLUMN && (refType & DataRefType.CUBE) == 0)
      {
         return parseInt(properties[AssetEntryHelper.CUBE_COL_TYPE], 10);
      }

      return null;
   }

   /**
    * Get property name from the entity, if the entity is a cube member, should
    * use entity + attribute as the name.
    */
   public getColumnValue(entry: AssetEntry): string {
      if(entry == null) {
         return "";
      }

      let properties: any = entry.properties;
      let cvalue: string = AssetEntryHelper.getEntryName(entry);
      let attribute: string = properties["attribute"];

      // normal chart entry not set entity and attribute properties,
      // cube entry set, the name should use entity + attribute
      if(attribute != null) {
         let entity: string = properties["entity"];
         cvalue = (entity != null ?  entity + "." : "") + attribute;
      }

      return cvalue;
   }

   public treeLoading(): boolean {
      return this._loadingTree;
   }

   public changeLoadingState(loading: boolean): void {
      this._loadingTree = loading;
   }

   public bindingTreeChanged(): Observable<TreeNodeModel> {
      return this._bindingTreeChanged.asObservable();
   }

   public expandNode(node: TreeNodeModel): void {
      if(this.needUseVirtualScroll) {
         this._virtualScrollDataSource.nodeExpanded(this.bindingTreeModel, node);
      }
   }

   public collapseNode(node: TreeNodeModel): void {
      if(this.needUseVirtualScroll) {
         this._virtualScrollDataSource.nodeCollapsed(this.bindingTreeModel, node);
      }
   }

   abstract getBindingTreeActions(selectedNode: TreeNodeModel, selectedNodes: TreeNodeModel[],
                                  dialogService: NgbModal, modelService: ModelService, service: any,
                                  bindingInfo?: any): ContextMenuActions;
}
