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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { Observable } from "rxjs";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ComponentTool } from "../../../../../../common/util/component-tool";
import { AssetItem } from "../../../../model/datasources/database/asset-item";
import { DataModelBrowserViewModel } from "../../../../model/data-model-browser-view-model";

@Component({
  selector: "data-models-browser",
  templateUrl: "./data-model-folder-browser.component.html",
  styleUrls: ["./data-model-folder-browser.component.scss"]
})
export class DataModelFolderBrowserComponent implements OnInit {
  @Input() browserView: DataModelBrowserViewModel;
  @Input() selectedFolders: AssetItem[] = [];
  @Input() folderIcon: string = "folder-icon";
  @Input() openFolderRequest: (path: string, assetType?: string) => Observable<DataModelBrowserViewModel>;
  @Input() openFolderError: string = "_#(js:admin.status.error)";
  @Input() initView: boolean = true;
  @Input() breadcrumbTooltip: string = null;
  @Input() rootLabel: string;
  @Output() selectionChange = new EventEmitter<AssetItem[]>();

  constructor(private modalService: NgbModal) {
  }

  ngOnInit(): void {
    if(this.initView) {
      // open root folder if should get view on init
      this.openFolder("/", null, true);
    }
  }

  /**
   * Open the selected folder path using the provided open folder request and set the browser view
   * to the result.
   * @param path       the path to open to
   * @param assetType  the type of asset opening
   * @param onInit     if this is being called from onInit
   */
  openFolder(path: string, assetType?: any, onInit: boolean = false): void {
    this.openFolderRequest(path, assetType).subscribe(
       data => {
         this.browserView = data;

         if(!onInit) {
           // reset selected items when opening new folder
           this.selectedFolders = [];
           this.notifySelectionChange();
         }
       },
       () => {
         ComponentTool.showMessageDialog(this.modalService, "_#(js:admin.status.error)", this.openFolderError);
       }
    );
  }

  /**
   * Select a folder path and notify parent of selection.
   * @param folder  the asset item selected
   */
  selectFolder(folder: AssetItem): void {
    this.selectedFolders = [folder];
    this.notifySelectionChange();
  }

  /**
   * Check if the item path is found in the related seleted items array.
   * @param item       the item to check
   * @param isFolder   if the item is of folder type
   * @returns {boolean}   true if the item is currently selected
   */
  isItemSelected(item: AssetItem, isFolder: boolean): boolean {
    return !!this.selectedFolders
       .find(folder => folder.path === item.path);
  }

  getFolderName(folder: AssetItem) {
    if(!folder || !folder.name || folder.path === "/") {
      return this.rootLabel;
    }

    return folder.name;
  }

  /**
   * Get the folder icon to use.
   * @param folder  the folder to get the icon for
   * @returns {string} the icon css
   */
  getFolderIcon(folder: AssetItem): string {
    return this.folderIcon;
  }

  /**
   * Emit the currently selected items.
   */
  notifySelectionChange(): void {
    this.selectionChange.emit(this.selectedFolders);
  }
}
