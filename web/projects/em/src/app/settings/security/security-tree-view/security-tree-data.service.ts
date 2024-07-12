/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { Injectable, OnDestroy } from "@angular/core";
import {
   BehaviorSubject,
   combineLatest,
   Observable,
   of as observableOf,
   Subscription
} from "rxjs";
import { map } from "rxjs/operators";
import { SecurityTreeNode } from "./security-tree-node";
import { SearchComparator } from "../../../../../../portal/src/app/widget/tree/search-comparator";

@Injectable()
export class SecurityTreeDataService implements OnDestroy {
   private readonly _data = new BehaviorSubject<SecurityTreeNode[]>([]);
   private readonly _filter = new BehaviorSubject<string>("");
   private dataSubscription = Subscription.EMPTY;
   private _filterChange = false;

   public get dataChange(): Observable<SecurityTreeNode[]> {
      return this._data;
   }

   public get filterChange(): boolean {
      return this._filterChange;
   }

   public ngOnDestroy() {
      this.dataSubscription.unsubscribe();
   }

   get data(): SecurityTreeNode[] {
      return this._data.value;
   }

   public initialize(roots: SecurityTreeNode[]) {
      this._filterChange = false;
      this.updateDataSubscription(roots);
   }

   public refreshTreeData() {
      this._filterChange = false;
      this._data.next(this._data.value);
   }

   /**
    * Find all tree nodes
    */
   public filter(filterString: string) {
      this._filterChange = true;
      this._filter.next(filterString);
   }

   private updateDataSubscription(data: SecurityTreeNode[]): void {
      this.dataSubscription.unsubscribe();
      this.dataSubscription = observableOf(data).pipe(
         (_data) => combineLatest(_data, this._filter),
         map(([nodes, filterString]) => {
            if(filterString) {
               return nodes.map((node: SecurityTreeNode) => node.filter(filterString))
                  .filter((node) => node != null)
                  .sort((a, b) => new SearchComparator(filterString).searchSort(a, b));
            }

            return nodes;
         })
      ).subscribe((nodes) => this._data.next(nodes));
   }
}
