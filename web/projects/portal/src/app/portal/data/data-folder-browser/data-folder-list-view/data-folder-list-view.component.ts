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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { AssetType } from "../../../../../../../shared/data/asset-type";
import { DateTypeFormatter } from "../../../../../../../shared/util/date-type-formatter";
import { SortOptions } from "../../../../../../../shared/util/sort/sort-options";
import { SortTypes } from "../../../../../../../shared/util/sort/sort-types";
import { AssetConstants } from "../../../../common/data/asset-constants";
import { AssetEntryHelper } from "../../../../common/data/asset-entry-helper";
import { WorksheetBrowserInfo } from "../../model/worksheet-browser-info";
import { DragService } from "../../../../widget/services/drag.service";
import {
   FeatureFlagsService,
   FeatureFlagValue
} from "../../../../../../../shared/feature-flags/feature-flags.service";
import { MultiObjectSelectList } from "../../../../common/util/multi-object-select-list";

@Component({
   selector: "data-folder-list-view",
   templateUrl: "data-folder-list-view.component.html",
   styleUrls: ["data-folder-list-view.component.scss"]
})
export class DataFolderListViewComponent {
   @Input() sortOptions: SortOptions;
   @Input() unauthorizedAccess: boolean = false;
   @Input() searchView: boolean = false;
   @Input() folderPathLength: number = 0;
   @Input() foldersInView: number = 0;
   @Input() selectionOn: boolean = false;
   @Input() selectAllChecked: boolean = false;
   @Input() selectedItems: WorksheetBrowserInfo[] = [];
   @Input() selectedFile: WorksheetBrowserInfo = null;
   @Output() openAsset = new EventEmitter<WorksheetBrowserInfo>();
   @Output() renameAsset = new EventEmitter<WorksheetBrowserInfo>();
   @Output() moveAsset = new EventEmitter<WorksheetBrowserInfo>();
   @Output() deleteAsset = new EventEmitter<WorksheetBrowserInfo>();
   @Output() editWorksheet = new EventEmitter<WorksheetBrowserInfo>();
   @Output() showDetails = new EventEmitter<WorksheetBrowserInfo>();
   @Output() materializeAsset = new EventEmitter<WorksheetBrowserInfo>();
   @Output() sortChanged = new EventEmitter<void>();
   @Output() selectAllChanged = new EventEmitter<boolean>();
   @Output() selectChanged = new EventEmitter<any>();
   @Output() dragAssets = new EventEmitter<{event: any, data: WorksheetBrowserInfo[]}>();
   @Output() assetsDroped = new EventEmitter<WorksheetBrowserInfo>();
   SortTypes = SortTypes;
   isMenuClick: boolean  = false;
   private _assets: WorksheetBrowserInfo[] = [];
   private multiObjectSelectList: MultiObjectSelectList<WorksheetBrowserInfo>;

   @Input() set assets(_assets: WorksheetBrowserInfo[]) {
      this._assets = _assets;
      this.multiObjectSelectList.setObjectsKeepSelection(this._assets);
   }

   get assets(): WorksheetBrowserInfo[] {
      return this._assets;
   }

   constructor() {
      this.multiObjectSelectList = new MultiObjectSelectList<WorksheetBrowserInfo>();
   }

   /**
    * Check if the asset can move from the current folder.
    * @param asset         the asset to check
    * @returns {boolean}   true if the asset can be moved, false otherwise
    */
   canMoveAsset(asset: WorksheetBrowserInfo): boolean {
      return this.folderPathLength > 0 && asset.deletable && asset.editable;
   }

   /**
    * Update the selected state of an asset item.
    * @param item    the asset item being updated
    */
   updateSelection(item: WorksheetBrowserInfo): void {
      const index: number = this.selectedItems.indexOf(item);

      if(index !== -1) {
         this.selectedItems.splice(index, 1);
      }
      else {
         this.selectedItems.push(item);
      }
   }

   /**
    * Get the router link params needed to go to the folder browser of this parent path.
    * @param parentPath    the parent path to open to
    * @param scope         the scope of the parent folder
    * @returns returns the router link params
    */
   getParentRouterLinkParams(parentPath: string, scope: number): any {
      const privWS = "_#(js:User Worksheet)";
      const isPrivate = scope == AssetEntryHelper.USER_SCOPE && !!parentPath && parentPath.indexOf(privWS) == 0;
      const privFolder = parentPath.length > privWS.length ? 1 : 0;

      return !parentPath ? {scope: scope} :
         {path: isPrivate ? parentPath.substring(privWS.length + privFolder) : parentPath, scope: scope + ""};
   }

   getIcon(asset: WorksheetBrowserInfo): string {
      if(asset.materialized) {
         return "materialized-worksheet-icon";
      }
      else if(asset.type === AssetType.FOLDER) {
         return "folder-icon";
      }
      else if(asset.workSheetType === AssetConstants.NAMED_GROUP_ASSET) {
         return " grouping-icon";
      }
      else if(asset.workSheetType === AssetConstants.VARIABLE_ASSET) {
         return " variable-icon";
      }
      else if(asset.workSheetType === AssetConstants.CONDITION_ASSET) {
         return " condition-icon";
      }
      else if(asset.workSheetType === AssetConstants.DATE_RANGE_ASSET) {
         return " date-range-icon";
      }
      else {
         return " worksheet-icon";
      }
   }

   /**
    * Update current sort options and make a call to sort view.
    * @param key
    */
   updateSortOptions(key: string): void {
      if(this.sortOptions.keys.includes(key)) {
         if(this.sortOptions.type === SortTypes.ASCENDING) {
            this.sortOptions.type = SortTypes.DESCENDING;
         }
         else {
            this.sortOptions.type = SortTypes.ASCENDING;
         }
      }
      else {
         this.sortOptions.keys = [key];
         this.sortOptions.type = SortTypes.ASCENDING;
      }

      this.sortChanged.emit();
   }

   /**
    * Select asset by click.
    * @param asset   the asset selected
    * @param event   the click event
    */
   selectAsset(asset: WorksheetBrowserInfo, event: any): void {
      if(this.isMenuClick) {
         this.isMenuClick = false;
         return;
      }

      if(asset.type === AssetType.WORKSHEET && asset.editable) {
         this.editWorksheet.emit(asset);
      }
      else {
         this.openAsset.emit(asset);
      }
   }

   clickMenu() {
      this.isMenuClick = true;
   }

   dragAsset(event: any, asset: WorksheetBrowserInfo) {
      let dragAssets: WorksheetBrowserInfo[];

      if(this.selectionOn) {
         dragAssets = this.selectedItems.includes(asset) ? this.selectedItems : [asset];
      }
      else {
         let selectedObjects = this.multiObjectSelectList.getSelectedObjects();
         dragAssets = selectedObjects.includes(asset) ? selectedObjects : [asset];
      }

      this.dragAssets.emit({
         event: event,
         data: dragAssets
      });
   }

   dropAssets(asset: WorksheetBrowserInfo, event: DragEvent) {
      if(asset != null && asset.type != AssetType.FOLDER) {
         return;
      }

      event.stopPropagation();
      this.assetsDroped.emit(asset);
   }

   updateAssetSelection(asset: WorksheetBrowserInfo, event: MouseEvent) {
      if(this.selectionOn) {
         return;
      }

      this.multiObjectSelectList.selectWithEvent(asset, event);
   }

   isSelectedItem(asset: WorksheetBrowserInfo): boolean {
      if(this.selectionOn) {
         return false;
      }

      return this.multiObjectSelectList.isSelected(asset);
   }

   public getDateLabel(dateNumber: number, dateFormat: string): string {
      return DateTypeFormatter.getLocalTime(dateNumber,  dateFormat);
   }
}
