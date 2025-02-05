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
import { BehaviorSubject, Observable, Subject } from "rxjs";
import { TreeNodeModel } from "./tree-node-model";
import { TreeSearchPipe } from "./tree-search.pipe";
import { TreeTool } from "../../common/util/tree-tool";
import { SearchComparator } from "./search-comparator";

export class VirtualScrollTreeDatasource {
   private _virtualScroll = new Subject<TreeNodeModel[]>();
   private _virtualScrollNodes: TreeNodeModel[] = [];
   private _virtualScrollNodesParents:  Set<TreeNodeModel> =  new Set<TreeNodeModel>();
   public scrollTop = new BehaviorSubject<number>(0);
   private dispatcher = new BehaviorSubject<any[]>([]);
   private originalDispatcherValues: any[] = [];
   private searchFilter: string;
   private searchCollapsedValues: any[] = [];
   private _searchEndNode: (node: TreeNodeModel) => boolean;

   public setSearchEndNode(searchEndNode: (node: TreeNodeModel) => boolean): void {
      this._searchEndNode = searchEndNode;
   }

   public refresh(items?: any[]) {
      this.dispatcher.next(items);
   }

   public refreshByRoot(root: TreeNodeModel, filter?: string, ignoreNodes?: TreeNodeModel[]) {
      this.refreshByRoot0(root, filter, false, false, ignoreNodes);
   }

   private refreshByRoot0(root: TreeNodeModel, filter?: string, expandOrCollapsedNode?: boolean,
                          searchMode?: boolean, ignoreNodes?: TreeNodeModel[])
   {
      this.originalDispatcherValues = [];
      let searchString = filter == undefined ? this.searchFilter : filter;

      if(searchString && searchString.trim()) {
         this.filterValues0(root, filter == undefined ? this.searchFilter : filter,
            expandOrCollapsedNode, searchMode);
      }
      else {
         this.refresh(TreeTool.getFlattenedNodes(root, undefined, ignoreNodes));
      }
   }

   public nodeExpanded(root: TreeNodeModel, expandNode: TreeNodeModel,
                       ignoreNodes?: TreeNodeModel[]): void
   {
      if(this.searchMode && this.searchCollapsedValues) {
         let index = this.searchCollapsedValues.indexOf(expandNode);

         if(index >= 0) {
            this.searchCollapsedValues.splice(index, 1);
         }
      }

      this.refreshByRoot0(root, undefined, true, this.searchMode, ignoreNodes);
   }

   public nodeCollapsed(root: TreeNodeModel, collapsedNode: TreeNodeModel,
                        ignoreNodes?: TreeNodeModel[]): void
   {
      if(!this.searchCollapsedValues) {
         this.searchCollapsedValues = [];
      }

      if(this.searchMode && !this.searchCollapsedValues.includes(collapsedNode)) {
         this.searchCollapsedValues.push(collapsedNode);
      }

      this.refreshByRoot0(root, undefined, true, this.searchMode, ignoreNodes);
   }

   public registerScrollContainer(element: HTMLElement): Observable<any[]> {
      element.addEventListener("scroll", e => {
         this.dispatcher.next(this.dispatcher.value);

         if(e.target instanceof HTMLElement) {
            this.scrollTop.next(e.target.scrollTop);
         }
      });

      return this.dispatcher.asObservable();
   }

   /**
    * Super hack - unset then set the scroll top to work around scroll
    * anchoring type behavior
    */
   public restoreScrollTop() {
      const oldScrollTop = this.scrollTop.value;
      this.scrollTop.next(oldScrollTop + 1);
      this.scrollTop.next(oldScrollTop);
   }

   get virtualScroll(): Observable<TreeNodeModel[]> {
      return this._virtualScroll.asObservable();
   }

   get searchMode(): boolean {
      return !!this.searchFilter && !! this.searchFilter.trim();
   }

   public fireVirtualScroll(nodes: TreeNodeModel[]): void {
      this._virtualScrollNodes = nodes;
      let parents = new Set<TreeNodeModel>();

      for(let i = nodes.length; i >= 0; i--) {
         this.addParentNode(!!nodes[i] ? nodes[i].parent : null, parents);
      }

      this._virtualScrollNodesParents = parents;
      this._virtualScroll.next(nodes);
   }

   private addParentNode(node: TreeNodeModel, list: Set<TreeNodeModel>) {
      if(!node) {
         return;
      }

      if(!list.has(node)) {
         list.add(node);
      }

      if(!!node.parent) {
         this.addParentNode(node.parent, list);
      }
   }

   public inViewport(node: TreeNodeModel): boolean {
      return this._virtualScrollNodes && this._virtualScrollNodes.includes(node) ||
         this.searchMode && this.searchCollapsedValues?.includes(node);
   }

   public inSearchCollapsed(node: TreeNodeModel): boolean {
      return this.searchMode && this.searchCollapsedValues?.includes(node);
   }

   public nodeVisible(node: TreeNodeModel): boolean {
      if(this.inViewport(node)) {
         return true;
      }

      if(this._virtualScrollNodesParents && this._virtualScrollNodesParents.has(node)) {
         return true;
      }

      if(this.searchMode && this.searchCollapsedValues && this.searchCollapsedValues.includes(node)) {
         return true;
      }

      return false;
   }

   public filterValues(root: TreeNodeModel, str: string): void {
      this.filterValues0(root, str);
   }

   private filterValues0(root: TreeNodeModel, str: string, expandOrCollapsedNode?: boolean,
                         searchMode?: boolean, ignoreNodes?: TreeNodeModel[]): void
   {
      this.searchFilter = str;

      if(!root) {
         return;
      }

      let filterResult = [];

      if(!str || !str.trim()) {
         filterResult = TreeTool.getFlattenedNodes(root, undefined, ignoreNodes);
      }
      else {
         if(!expandOrCollapsedNode || !searchMode) {
            this.searchCollapsedValues = [];
         }

         let allNodes = TreeTool.getFlattenedNodes(root, new SearchComparator(str.trim()), ignoreNodes);
         let matchFilter = new Set<TreeNodeModel>();

         if(allNodes) {
            allNodes.forEach((value) => {
               if(TreeSearchPipe.nodeMatch(value, str.trim().toLocaleLowerCase()) ||
                  this.searchCollapsedValues && this.searchCollapsedValues.includes(value))
               {
                  this.addParentNode(value, matchFilter);

                  if(this._searchEndNode && this._searchEndNode(value)) {
                     this.addChildrenNodes(value, matchFilter);
                  }
               }

               if(value?.label && value.label.trim().toLowerCase() == str.trim().toLowerCase()) {
                  this.addChildrenNodes(value, matchFilter);
               }
            });

            filterResult = allNodes.filter(node => matchFilter.has(node));
         }
      }

      this.refresh(filterResult);
   }

   private addChildrenNodes(node: TreeNodeModel, list: Set<TreeNodeModel>): void {
      if(!node.children) {
         return;
      }

      for(let child of node.children) {
         list.add(child);
         this.addChildrenNodes(child, list);
      }
   }
}
