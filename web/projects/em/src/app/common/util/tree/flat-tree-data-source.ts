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
import { CollectionViewer, DataSource, SelectionChange } from "@angular/cdk/collections";
import { FlatTreeControl } from "@angular/cdk/tree";
import { BehaviorSubject, merge, Observable, Subject, Subscription } from "rxjs";
import { map, tap } from "rxjs/operators";
import { FlatTreeNode, TreeDataModel, TreeDataNode } from "./flat-tree-model";

/**
 * Abstract base class for a tree data source. Handles caching and delegating to subclass
 * for lazy loading.
 *
 * @param <T> FlatTreeNode used for rendering and contains a data field for R
 * @param <R> TreeDataNode that maps to a flat node and has a list of itself for non-lazy loading
 */
export abstract class FlatTreeDataSource<T extends FlatTreeNode<R>, R extends TreeDataNode<R>>
   extends DataSource<T> {
   public treeControl: FlatTreeControl<T>;
   private controlSubscription: Subscription;
   protected dataChange = new BehaviorSubject<T[]>([]);
   private getLevel = (node: T) => node.level;
   private isExpandable = (node: T) => node.expandable;
   private _nodeToggled = new Subject<T>();

   get nodeToggled() {
      return this._nodeToggled;
   }

   protected constructor(treeControl?: FlatTreeControl<T>) {
      super();
      this.treeControl = treeControl || new FlatTreeControl<T>(this.getLevel, this.isExpandable);
   }

   public connect(collectionViewer: CollectionViewer): Observable<T[]> {
      if(this.treeControl.expansionModel.changed && !this.controlSubscription) {
         this.controlSubscription = this.treeControl.expansionModel.changed.subscribe(change => {
            if((change as SelectionChange<T>).added ||
               (change as SelectionChange<T>).removed)
            {
               this.handleTreeControl(change as SelectionChange<T>);
            }
         });
      }

      return this.dataChange.pipe(
         map(data => data.filter(node => node.visible))
      );
   }

   public disconnect(collectionViewer: CollectionViewer): void {
      if(!!this.controlSubscription) {
         this.controlSubscription.unsubscribe();
         this.controlSubscription = null;
      }
   }

   get data(): T[] {
      return this.dataChange.value;
   }

   set data(val: T[]) {
      const nodes = val || [];
      this.treeControl.dataNodes = nodes;
      this.dataChange.next(nodes);
   }

   /**
    * Map a tree node to its children for lazy loading.
    */
   protected abstract getChildren(node: T): Observable<TreeDataModel<R>>;

   /**
    * Transform a tree model into flat nodes at a given level.
    */
   protected abstract transform(model: TreeDataModel<R>, level: number, parent?: T): T[];

   private handleTreeControl(change: SelectionChange<T>): void {
      if(change.added) {
         change.added.forEach(node => this.toggleNode(node, true));
      }

      if(change.removed) {
         change.removed.slice().reverse().forEach(node => this.toggleNode(node, false));
      }
   }

   private toggleNode(node: T, expand: boolean): void {
      node.loading = true;
      const nodes = node.data && node.data.children;

      if(nodes != null && nodes.length > 0) {
         this.toggleNodeChildren(node, this.transform({nodes}, node.level + 1, node), expand);
      }
      else {
         this.getChildren(node)
             .pipe(
                tap((model) => node.data && (node.data.children = model.nodes)),
                map((model) => this.transform(model, node.level + 1), node)
             )
             .subscribe((children) => this.toggleNodeChildren(node, children, expand));
      }
   }

   private toggleNodeChildren(node: T, children: T[], expand: boolean): void {
      let index = this.data.indexOf(node);

      if(expand) {
         // was getting maximum call stack size exceeded with splice
         // need to add a portion at a time
         this.addToArray(this.data, children, index + 1);
      }
      else {
         let count = 0;

         for(let i = index + 1; i < this.data.length && this.data[i].level > node.level; i++) {
            count += 1;
         }

         this.data.splice(index + 1, count);
      }

      this.dataChange.next(this.data);
      this.nodeToggled.next(node);
      node.loading = false;
   }

   private addToArray(data: T[], children: T[], index): T[] {
      if(children && children.length) {
         for(let idx = Math.floor(children.length / 10000); idx >= 0; idx--) {
            // eslint-disable-next-line prefer-spread
            data.splice.apply(data, [index, 0].concat(
               children.slice(idx * 10000, (idx + 1) * 10000)
            ));
         }
      }

      return data;
   }
}

