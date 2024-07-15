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
import { Component, OnInit, Input, Output, EventEmitter } from "@angular/core";
import { DataSourceBrowserViewModel } from "../../model/data-source-browser-view-model";
import { SortTypes } from "../../../../../../../shared/util/sort/sort-types";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Observable } from "rxjs";
import { SortOptions } from "../../../../../../../shared/util/sort/sort-options";
import { Tool } from "../../../../../../../shared/util/tool";
import { ComponentTool } from "../../../../common/util/component-tool";
import { DataSourceInfo } from "../../model/data-source-info";

@Component({
   selector: "data-sources-browser",
   templateUrl: "data-sources-browser.component.html",
   styleUrls: ["data-sources-browser.component.scss"]
})
export class DataSourcesBrowser implements OnInit {
   @Input() browserView: DataSourceBrowserViewModel;
   @Input() selectedFolders: DataSourceInfo[] = [];
   @Input() selectedFiles: DataSourceInfo[] = [];
   @Input() folderSelectable: boolean = false;
   @Input() showBreadcrumb: boolean = true;
   @Input() multiSelect: boolean = false;
   @Input() folderIcon: string = "folder-icon";
   @Input() fileIcon: string = "table-icon";
   @Input() openFolderPath: string;
   @Input() openFolderRequest: (path: string, assetType?: string) => Observable<DataSourceBrowserViewModel>;
   @Input() openFolderError: string = "_#(js:admin.status.error)";
   @Input() initView: boolean = true;
   @Input() breadcrumbTooltip: string = null;
   @Input() rootLabel: string;
   @Output() selectionChange = new EventEmitter<DataSourceInfo[]>();
   bigDataEdition: boolean = false;

   constructor(private modalService: NgbModal) {
   }

   ngOnInit(): void {
      if(this.initView) {
         // open root folder if should get view on init
         this.openFolder(this.openFolderPath || "/", null, true);
      }
   }

   /**
    * Open the selected folder path using the provided open folder request and set the browser view
    * to the result.
    * @param path          the path to open to
    * @param hasSubFolder  whether there are subfolders
    * @param assetType     the type of asset opening
    * @param onInit        if this is being called from onInit
    */
   dblclickFolder(path: string, hasSubFolder: boolean, assetType?: any,
              onInit: boolean = false): void
   {
      if(!hasSubFolder) {
         return;
      }

      this.openFolder(path, assetType, onInit);
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
               this.selectedFiles = [];
               this.selectedFolders = [];

               let files = this.browserView.files.filter((file) => !!file && !!file.createdDate);
               files = Tool.sortObjects(files, new SortOptions(["createdDate"], SortTypes.DESCENDING));

               if(files.length > 0) {
                  this.selectedFiles = [files[0]];
               }

               this.notifySelectionChange();
            }
         },
         () => {
            ComponentTool.showMessageDialog(this.modalService, "_#(js:admin.status.error)", this.openFolderError);
         }
      );
   }

   /**
    * Open to parent folder path.
    */
   openParentFolder(): void {
      const parent: DataSourceInfo = this.browserView.path[this.browserView.path.length - 2];
      this.openFolder(parent.path, parent.type);
   }

   /**
    * Select a folder path and notify parent of selection.
    * @param folder  the asset item selected
    */
   selectFolder(folder: DataSourceInfo): void {
      if(this.multiSelect) {
         // use findIndex instead of indexOf since the selected item can be passed through @Input()
         // which can cause them to not be the same objects
         const index: number = this.selectedFolders.findIndex(item => item.path === folder.path);

         if(index !== -1) {
            this.selectedFolders.splice(index, 1);
         }
         else {
            this.selectedFolders.push(folder);
         }
      }
      else {
         this.selectedFiles = [];
         this.selectedFolders = [folder];
      }

      this.notifySelectionChange();
   }

   /**
    * Select a file path and notify parent of selection.
    * @param file the asset item selected
    */
   selectFile(file: DataSourceInfo): void {
      if(this.multiSelect) {
         // use findIndex instead of indexOf since the selected item can be passed through @Input()
         // which can cause them to not be the same objects
         const index: number = this.selectedFiles.findIndex(item => item.path === file.path);

         if(index !== -1) {
            this.selectedFiles.splice(index, 1);
         }
         else {
            this.selectedFiles.push(file);
         }
      }
      else {
         this.selectedFolders = [];
         this.selectedFiles = [file];
      }

      this.notifySelectionChange();
   }

   /**
    * Check if the item path is found in the related seleted items array.
    * @param item       the item to check
    * @param isFolder   if the item is of folder type
    * @returns {boolean}   true if the item is currently selected
    */
   isItemSelected(item: DataSourceInfo, isFolder: boolean): boolean {
      if(isFolder) {
         return !!this.selectedFolders
            .find(folder => folder.path === item.path);
      }
      else {
         return !!this.selectedFiles
            .find(file => file.path === item.path);
      }
   }

   getFolderName(folder: DataSourceInfo) {
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
   getFolderIcon(folder: DataSourceInfo): string {
      return this.folderIcon;
   }

   /**
    * Get the file icon to use.
    * @param file  the file to get the icon for
    * @returns {string} the icon css
    */
   getFileIcon(file: DataSourceInfo): string {
      return this.fileIcon;
   }

   /**
    * Emit the currently selected items.
    */
   notifySelectionChange(): void {
      this.selectionChange.emit(this.selectedFolders.concat(this.selectedFiles));
   }

   /**
    * Gets the name of the lowest level folder in the view
    */
   currentFolderName(): string {
      let name: string = "..";

      if(!!this.browserView.path && this.browserView.path.length > 0) {
         let parentNode = this.browserView.path[this.browserView.path.length - 1];

         if(!!parentNode && parentNode.name) {
            name = parentNode.name;
         }
         else if(!!parentNode && parentNode.path == "/") {
            name = this.rootLabel;
         }
      }

      return name;
   }
}
