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
import { animate, state, style, transition, trigger } from "@angular/animations";
import { SelectionModel } from "@angular/cdk/collections";
import { Component, EventEmitter, Input, OnInit, Output, ViewChild } from "@angular/core";
import { MatSort } from "@angular/material/sort";
import { MatTableDataSource } from "@angular/material/table";
import { DateTypeFormatter } from "../../../../../../../../shared/util/date-type-formatter";
import { RequiredAssetModel } from "../required-asset-model";

@Component({
   selector: "em-required-asset-list",
   templateUrl: "./required-asset-list.component.html",
   styleUrls: ["./required-asset-list.component.scss"],
   animations: [
      trigger("detailExpand", [
         state("collapsed, void", style({height: "0px", minHeight: "0"})),
         state("expanded", style({height: "*"})),
         transition("expanded <=> collapsed", animate("225ms cubic-bezier(0.4, 0.0, 0.2, 1)")),
         transition('expanded <=> void', animate('225ms cubic-bezier(0.4, 0.0, 0.2, 1)'))
      ]),
   ]
})
export class RequiredAssetListComponent implements OnInit {
   @Input()
   get assets(): RequiredAssetModel[] {
      return this._assets;
   }

   set assets(value: RequiredAssetModel[]) {
      this._assets = value || [];
      this.dataSource.data = this._assets;
   }

   @Input()
   get selectedAssets(): RequiredAssetModel[] {
      return this.selection.selected || [];
   }

   set selectedAssets(value: RequiredAssetModel[]) {
      this.selection.clear();

      if(value && value.length) {
         this.selection.select(...value);
      }
   }

   getTimeLabel(time: number, fmt: string) {
     return DateTypeFormatter.getLocalTime(time, fmt);
   }

   get displayColumns(): string[] {
      return this.selectedTargetFolder ? ["selected", "name", "type", "appliedTargetLabel", "lastModifiedTime"]
         : ["selected", "name", "type", "lastModifiedTime"];

   }

   @Input() selectedTargetFolder: boolean;
   @Output() selectedAssetsChange = new EventEmitter<RequiredAssetModel[]>();
   @ViewChild(MatSort, { static: true }) sort: MatSort;

   dataSource = new MatTableDataSource<RequiredAssetModel>([]);
   selection = new SelectionModel<RequiredAssetModel>(true);
   expandedElement: RequiredAssetModel | null;
   readonly expandingColumns: string[] = ["requiredBy"];

   private _assets: RequiredAssetModel[] = [];

   constructor() {
   }

   ngOnInit() {
      this.dataSource.sortingDataAccessor = (item, property) => {
         switch(property) {
         case "name": return item.label;
         case "type": return item.type;
         case "appliedTargetLabel": return item.type;
         case "lastModifiedTime": return item.lastModifiedTime;
         case "requiredBy": return item.requiredBy;
         default: return item[property];
         }
      };

      this.dataSource.sort = this.sort;
   }

   isAllSelected(): boolean {
      return this.selection.selected.length === this.assets.length;
   }

   toggleRow(row) {
      this.selection.toggle(row);
      this.selectedAssetsChange.emit(this.selection.selected || []);
   }

   masterToggle() {
      this.isAllSelected() ? this.selection.clear() : this.selection.select(...this.assets);
      this.selectedAssetsChange.emit(this.selection.selected || []);
   }
}
