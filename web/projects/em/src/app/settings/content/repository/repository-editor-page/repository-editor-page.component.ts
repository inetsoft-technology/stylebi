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
import { HttpClient } from "@angular/common/http";
import { Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges } from "@angular/core";
import { RepositoryEntryType } from "../../../../../../../shared/data/repository-entry-type.enum";
import { RepositoryEditorModel } from "../../../../../../../shared/util/model/repository-editor-model";
import { ContentRepositoryService } from "../content-repository-page/content-repository.service";
import { RepositoryTreeNode } from "../repository-tree-node";

@Component({
   selector: "em-repository-editor-page",
   templateUrl: "./repository-editor-page.component.html",
   styleUrls: ["./repository-editor-page.component.scss"]
})
export class RepositoryEditorPageComponent implements OnChanges, OnInit {
   previousEditorPath: string;
   selectedTab = 0;
   @Input() editorModel: RepositoryEditorModel;
   @Input() editingNode: RepositoryTreeNode;
   @Input() smallDevice: boolean;
   @Output() cancel = new EventEmitter<void>();
   @Output() editorChanged = new EventEmitter<RepositoryEditorModel>();
   @Output() newDataSource = new EventEmitter<RepositoryEditorModel>();
   @Output() mvChanged = new EventEmitter<RepositoryEditorModel>();
   @Output() unsavedChanges = new EventEmitter<boolean>();
   public loading = false;
   hasMVPermission: boolean;

   get editorType(): string {
      if(!this.editorModel) {
         return null;
      }

      const type = this.editorModel.type;
      const path = this.editorModel.path;


      if(type == RepositoryEntryType.AUTO_SAVE_VS) {
         return "auto-save-vs";
      }

      if(type == RepositoryEntryType.AUTO_SAVE_WS) {
         return "auto-save-ws";
      }

      if(type == RepositoryEntryType.VS_AUTO_SAVE_FOLDER) {
         return "vs-auto-save-folder";
      }

      if(type == RepositoryEntryType.WS_AUTO_SAVE_FOLDER) {
         return "ws-auto-save-folder";
      }

      if(type == RepositoryEntryType.AUTO_SAVE_FOLDER) {
         return "auto-save-folder";
      }

      if(type === RepositoryEntryType.VIEWSHEET) {
         return "viewsheet";
      }

      if(type === RepositoryEntryType.WORKSHEET) {
         return "worksheet";
      }

      if(type === RepositoryEntryType.DASHBOARD) {
         return "dashboard";
      }

      if(this.repositoryService.isDataSource(type)) {
         return "data-source";
      }

      if(type === RepositoryEntryType.TRASHCAN_FOLDER) {
         return "folder-trashcan";
      }

      if(this.repositoryService.isTrashcanArchiveEntry(type)) {
         return "trashcan-archive";
      }

      if(type === RepositoryEntryType.RECYCLEBIN_FOLDER) {
         return !path ? "recycle-bin-root" : "folder-recycle-bin";
      }

      if(type === RepositoryEntryType.DASHBOARD_FOLDER) {
         return "folder-dashboard";
      }

      if(this.repositoryService.isFolder(type) && !this.repositoryService.isDataSourceEntry(type) &&
         !this.repositoryService.isLibraryEntry(type) &&
         !this.repositoryService.isInRecyleBin(path) &&
         type !== RepositoryEntryType.DATA_SOURCE_FOLDER &&
         type !== RepositoryEntryType.SCHEDULE_TASK_FOLDER &&
         !this.repositoryService.isDataModelFolderEntry(type)) {
         return "folder";
      }

      if(type === RepositoryEntryType.DATA_SOURCE_FOLDER && path !== "/") {
         return "data-source-folder";
      }

      if(type === RepositoryEntryType.SCHEDULE_TASK_FOLDER && path !== "/") {
         return "schedule-task-folder";
      }

      if(type === RepositoryEntryType.SCRIPT) {
         return "script";
      }

      if(this.repositoryService.isDataSourceEntry(type) ||
         this.repositoryService.isLibraryEntry(type) ||
         type === RepositoryEntryType.CUBE ||
         type === RepositoryEntryType.DATA_SOURCE_FOLDER && path === "/" ||
         type === RepositoryEntryType.SCHEDULE_TASK_FOLDER && path === "/" ||
         this.repositoryService.isDataModelFolderEntry(type)) {
         return "permission";
      }

      if(this.repositoryService.isInRecyleBin(path)) {
         return "recycle-bin";
      }

      return null;
   }

   get selectedNodeType(): RepositoryEntryType {
      return this.repositoryService.selectedNode?.type;
   }

   constructor(public repositoryService: ContentRepositoryService, private httpClient: HttpClient) {
   }

   ngOnInit(): void {
      this.repositoryService.hasMVPermission().subscribe(hasMVPermission => this.hasMVPermission = hasMVPermission);
   }

   ngOnChanges(changes: SimpleChanges) {
      if(changes.editorModel && changes.editorModel.previousValue) {
         this.previousEditorPath = changes.editorModel.previousValue.path;
      }

      if(this.previousEditorPath && this.editorModel) {
         if(this.previousEditorPath !== this.editorModel.path) {
            this.selectedTab = 0;
         }
      }
   }

   handleEditorChange(newName: string) {
      if(newName) {
         const index = this.editorModel.path.lastIndexOf("/");
         this.editorModel.path = index < 0 || newName === "/" ? newName :
            this.editorModel.path.substring(0, index + 1) + newName;
      }

      this.editorChanged.emit(this.editorModel);
   }

   handleDataSourceChange(newName: string) {
      //rename
      if((this.repositoryService.selectedNode.type & RepositoryEntryType.DATA_SOURCE) == RepositoryEntryType.DATA_SOURCE) {
         this.handleEditorChange(newName);
         return;
      }

      //new datasource
      if(newName) {
         this.editorModel.path = this.editorModel.path === "/" ? newName :
            this.editorModel.path + "/" + newName;
      }

      this.editorModel.type |= RepositoryEntryType.FOLDER;
      this.newDataSource.emit(this.editorModel);
   }

   public mangleAssets() {
      let timeout = setTimeout(() => this.loading = true, 1000);

      this.httpClient.delete("../api/em/repository/recycle-bin/entries")
         .subscribe(() => {
            clearTimeout(timeout);
            this.loading = false;
         });
   }
}
