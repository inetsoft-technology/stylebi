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
import {Component, EventEmitter, Input, OnInit, Output} from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import {WorksheetBrowserInfo} from "../../../../data/model/worksheet-browser-info";
import {Observable} from "rxjs";
import {ScheduleTaskModel} from "../../../../../../../../shared/schedule/model/schedule-task-model";
import {TaskFolderBrowserModel} from "../../../model/task-folder-browser-model";
import {DataSourceInfo} from "../../../../data/model/data-source-info";
import {TreeNodeModel} from "../../../../../widget/tree/tree-node-model";

@Component({
   selector: "task-folder-browser",
   templateUrl: "task-folder-browser.component.html",
   styleUrls: ["task-folder-browser.component.scss"]
})
export class TaskFolderBrowserComponent implements OnInit{
   @Input() browserView: TaskFolderBrowserModel;
   @Input() selectedFolders: TreeNodeModel[] = [];
   @Input() folderSelectable: boolean = false;
   @Input() showBreadcrumb: boolean = true;
   @Input() multiSelect: boolean = false;
   @Input() folderIcon: string = "folder-icon";
   @Input() openFolderPath: string;
   @Input() openFolderScope: number;
   @Input() openFolderRequest: (path: string) => Observable<TaskFolderBrowserModel>;
   @Input() openFolderError: string = "_#(js:admin.status.error)";
   @Input() initView: boolean = true;
   @Input() breadcrumbTooltip: string = null;
   @Input() rootLabel: string;


   @Output() selectionChange = new EventEmitter<TreeNodeModel>();

   constructor(private modalService: NgbModal) {
   }

   ngOnInit(): void {
      if (this.initView) {
         // open root folder if should get view on init
         this.openFolder(this.openFolderPath || "/");
      }
   }

   openFolder(path: string): void {
      this.openFolderRequest(path).subscribe(
          data => {
             this.browserView = data;
             this.selectedFolders = [];

             this.selectionChange.emit(null);
          }
      );
   }

   getFolderName(folder: TreeNodeModel) {
      if(!folder || !folder.label || folder.data.path === "/") {
         return this.rootLabel;
      }

      return folder.label;
   }

   currentFolderName(): string {
      let name: string = "..";

      if(!!this.browserView.paths && this.browserView.paths.length > 0) {
         let parentNode = this.browserView.paths[this.browserView.paths.length - 1];

         if(!!parentNode && parentNode.label) {
            name = parentNode.label;
         }
         else if(!!parentNode && parentNode.data.path == "/") {
            name = this.rootLabel;
         }
      }

      return name;
   }

   /**
    * Emit the currently selected items.
    */
   notifySelectionChange(): void {
      this.selectionChange.emit(this.selectedFolders[0]);
   }

   openParentFolder(): void {
      const parent: TreeNodeModel = this.browserView.paths[this.browserView.paths.length - 2];
      this.openFolder(parent.data.path);
   }

   /**
    * Check if the item path is found in the related seleted items array.
    * @returns {boolean}   true if the item is currently selected
    */
   isItemSelected(item: TreeNodeModel): boolean {
      return !!this.selectedFolders
          .find(folder => folder.data.path === item.data.path);
   }

   /**
    * Select a folder path and notify parent of selection.
    * @param folder  the tree node selected
    */
   selectFolder(folder: TreeNodeModel): void {
      this.selectedFolders = [folder];
      this.notifySelectionChange();
   }
}
