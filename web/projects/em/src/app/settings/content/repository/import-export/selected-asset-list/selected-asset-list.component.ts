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
import { SelectionModel } from "@angular/cdk/collections";
import { Component, EventEmitter, Input, OnInit, Output, ViewChild } from "@angular/core";
import { MatSort } from "@angular/material/sort";
import { MatTableDataSource } from "@angular/material/table";
import { DateTypeFormatter } from "../../../../../../../../shared/util/date-type-formatter";
import { Tool } from "../../../../../../../../shared/util/tool";
import { SelectedAssetModel } from "../selected-asset-model";

@Component({
   selector: "em-selected-asset-list",
   templateUrl: "./selected-asset-list.component.html",
   styleUrls: ["./selected-asset-list.component.scss"]
})
export class SelectedAssetListComponent implements OnInit {
   @Input()
   get assets(): SelectedAssetModel[] {
      return this._assets;
   }

   set assets(value: SelectedAssetModel[]) {
      this._assets = value || [];
      this.dataSource.data = this._assets;
   }

   @Input()
   get selectedAssets(): SelectedAssetModel[] {
      return this.selection.selected || [];
   }

   set selectedAssets(value: SelectedAssetModel[]) {
      this.selection.clear();

      if(value && value.length) {
         this.selection.select(...value);
      }
   }

   get displayColumns(): string[] {
      return this.selectedTargetFolder ? ["name", "type", "appliedTargetLabel", "lastModifiedTime"] :
         ["name", "type", "lastModifiedTime"];
   }

   @Input() selectedTargetFolder: boolean;
   @Output() selectedAssetsChange = new EventEmitter<SelectedAssetModel[]>();
   @ViewChild(MatSort, { static: true }) sort: MatSort;

   dataSource = new MatTableDataSource<SelectedAssetModel>([]);
   selection = new SelectionModel<SelectedAssetModel>(true);

   private _assets: SelectedAssetModel[] = [];

   constructor() {
   }

   ngOnInit() {
      this.dataSource.sort = this.sort;
   }

   isAllSelected(): boolean {
      return this.selection.selected.length === this.assets.length;
   }

   getLabel(path: string, index: number) {
      const assetType = this.assets[index]?.typeName;

      if(assetType != "AUTOSAVEVS" && assetType != "AUTOSAVEWS") {
         return path;
      }

      if(path != null && path.indexOf("^") != -1) {
         let paths = path.split("^");

         if(paths.length > 3) {
            if(Tool.isEquals(paths[2], "_NULL_")) {
               paths[2] = "anonymous";
            }

            return paths[2] + "/" + paths[3];
         }
      }

      return path;
   }

   getTimeLabel(date: number, fmt: string) {
      return DateTypeFormatter.getLocalTime(date, fmt);
   }

   isSelectedRow(row: SelectedAssetModel) {
      return this.selectedAssets.indexOf(row) != -1;
   }

   onSelectedAssets(row: SelectedAssetModel, event: MouseEvent) {
      if(event.ctrlKey && this.selectedAssets?.indexOf(row) != -1) {
         this.selectedAssets.splice(this.selectedAssets.indexOf(row), 1);
      }
      else if(event.ctrlKey && this.selectedAssets?.indexOf(row) == -1) {
         this.selectedAssets = [...this.selectedAssets, row];
      }
      else if(event.shiftKey) {
         this.addShiftAssets(row);
      }
      else {
         this.selectedAssets = this.selectedAssets?.indexOf(row) != -1 ? [] : [row];
      }

      this.selectedAssetsChange.emit(this.selection.selected || []);
   }

   addShiftAssets(asset: SelectedAssetModel) {
      if(this.selectedAssets == null || this.selectedAssets.length == 0) {
         this.selectedAssets = [asset];
         return;
      }

      let lastAsset = this.selectedAssets[this.selectedAssets.length - 1];

      if(lastAsset == asset) {
         return;
      }

      let start = this.assets.indexOf(lastAsset);
      let end = this.assets.indexOf(asset);

      this.selectedAssets = start < end ? this.getSelectedAssets(start, end) : start > end
         ? this.getSelectedAssets(end, start) : [asset];
   }

   getSelectedAssets(start: number, end: number) {
      let selectedArray: SelectedAssetModel[] = [];

      for(let i = start; i <= end; i++) {
         selectedArray.push(this.assets[i]);
      }

      return selectedArray;
   }
}
