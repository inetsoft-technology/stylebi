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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { AssetItem } from "../model/datasources/database/asset-item";
import { SortOptions } from "../../../../../../shared/util/sort/sort-options";
import { SortTypes } from "../../../../../../shared/util/sort/sort-types";
import { MultiObjectSelectList } from "../../../common/util/multi-object-select-list";

export interface RouteLinkEntry {
   path: string;
   queryParams?: any;
}

export interface ListColumn {
   label: string;
   sortKey: string;
   widthPercentage: number;
   visible: boolean;
   value: (item: AssetItem) => string;
   routerLink?: (item: AssetItem) => RouteLinkEntry;
}

@Component({
   selector: "asset-item-list-view",
   templateUrl: "./asset-item-list-view.component.html",
   styleUrls: ["./asset-item-list-view.component.scss"]
})
export class AssetItemListViewComponent {
   @Input() set assets(assets: AssetItem[]) {
      this._assets = assets;
      this.multiObjectSelectList.setObjectsKeepSelection(assets);
   }

   get assets() {
      return this._assets;
   }

   @Input() columns: ListColumn[] = [];
   @Input() sortOptions: SortOptions;
   @Input() selectionOn: boolean = false;
   @Input() selectedItems: AssetItem[] = [];
   @Input() searchView: boolean = false;
   @Input() iconFunction: (item: AssetItem) => string;
   @Input() getParentPath: (item: AssetItem) => any;
   @Input() fetchChildrenFunc: (item: AssetItem) => AssetItem[];
   @Input() dragSupport: boolean = false;
   @Input() dragSupportFunc: (item: AssetItem) => boolean;
   @Output() onContextmenu = new EventEmitter<[AssetItem, MouseEvent]>();
   @Output() onClickItem: EventEmitter<AssetItem> = new EventEmitter();
   @Output() sortChanged: EventEmitter<null> = new EventEmitter();
   @Output() onSelectedChanged: EventEmitter<AssetItem[]> = new EventEmitter();
   @Output() dragAssets = new EventEmitter<{event: any, data: AssetItem[]}>();
   @Output() assetsDroped = new EventEmitter<AssetItem>();
   toggleItems: AssetItem[] = [];
   SortTypes = SortTypes;
   private _assets: AssetItem[] = [];
   private multiObjectSelectList: MultiObjectSelectList<AssetItem>;

   constructor() {
      this.multiObjectSelectList = new MultiObjectSelectList<AssetItem>();
   }

   /**
    * Update current sort options and make a call to sort view.
    * @param key
    */
   updateSortOptions(key: string): void {
      if(this.sortOptions.keys.indexOf(key) != -1) {
         this.sortOptions.type = this.sortOptions.type == SortTypes.ASCENDING ?
            SortTypes.DESCENDING : SortTypes.ASCENDING;
      }
      else {
         this.sortOptions.keys = [key];
         this.sortOptions.type = SortTypes.ASCENDING;
      }

      this.sortChanged.emit();
   }

   /**
    * Update the selected state of an asset item.
    * @param item    the asset item being updated
    */
   updateSelection(item: AssetItem): void {
      const index: number = this.selectedItems.indexOf(item);

      if(index != -1) {
         this.selectedItems.splice(index, 1);
      }
      else {
         this.selectedItems.push(item);
      }

      this.onSelectedChanged.emit(this.selectedItems);
   }

   hasChildren(item: AssetItem): boolean {
      let children = this.getChildren(item);
      return children?.length > 0;
   }

   toggleItem(item: AssetItem): void {
      let index = this.toggleItems.indexOf(item);

      if(index >= 0) {
         this.toggleItems.splice(index, 1);
      }
      else {
         this.toggleItems.push(item);
      }
   }

   isToggled(item: AssetItem) {
      return this.toggleItems.includes(item);
   }

   getChildren(item: AssetItem): AssetItem[] {
      if(!this.fetchChildrenFunc) {
         return [];
      }

      return this.fetchChildrenFunc(item);
   }

   getToggleIcon(item: AssetItem): string {
      if(!this.fetchChildrenFunc) {
         return "";
      }

      return this.toggleItems.includes(item) ? "caret-down-icon icon-lg" :
         "caret-right-icon icon-lg";
   }

   clickItem(item: AssetItem, emit: boolean) {
      if(emit) {
         this.onClickItem.emit(item);
      }
   }

   selectAll(): void {
      if(this.selectAllChecked) {
         this.selectedItems = [];
      }
      else {
         this.selectedItems = [];
         this.assets.forEach(asset => {
            this.selectedItems.push(asset);

            if(!!this.fetchChildrenFunc) {
               let children = this.fetchChildrenFunc(asset);

               if(!!children) {
                  this.selectedItems.push(...children);
               }
            }
         });
      }

      this.onSelectedChanged.emit(this.selectedItems);
   }

   get selectAllChecked(): boolean {
      return this.selectedItems.length > 0 &&
         this.assets.every(item => this.selectedItems.indexOf(item) !== -1);
   }

   dragAsset(event: any, asset: AssetItem) {
      let dragAssets: AssetItem[];

      if(this.selectionOn) {
         dragAssets= this.selectedItems.includes(asset) ? this.selectedItems : [asset];
      }
      else {
         let selectedObjects = this.multiObjectSelectList.getSelectedObjects();
         dragAssets= selectedObjects.includes(asset) ? selectedObjects : [asset];
      }

      if(this.dragSupportFunc) {
         dragAssets = dragAssets.filter(item => this.dragSupportFunc(item));
      }

      this.dragAssets.emit({event: event, data: dragAssets});
   }

   dropAssets(asset: AssetItem) {
      this.assetsDroped.emit(asset);
   }

   updateAssetSelection(asset: AssetItem, event: MouseEvent) {
      if(this.selectionOn || !this.dragSupport) {
         return;
      }

      this.multiObjectSelectList.selectWithEvent(asset, event);
   }

   isSelectedItem(asset: AssetItem): boolean {
      if(this.selectionOn || !this.dragSupport) {
         return false;
      }

      return this.multiObjectSelectList.isSelected(asset);
   }

   supportDrag(asset: AssetItem): boolean {
      return this.dragSupport && (!this.dragSupportFunc || this.dragSupportFunc(asset));
   }
}
