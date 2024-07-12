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
import { HttpClient, HttpParams } from "@angular/common/http";
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Observable } from "rxjs";
import { map } from "rxjs/operators";
import {ScheduleTaskModel} from "../../../../../../../shared/schedule/model/schedule-task-model";
import {TreeNodeModel} from "../../../../widget/tree/tree-node-model";
import {TaskFolderBrowserModel} from "../../model/task-folder-browser-model";
import {CheckTaskDuplicateRequest} from "../../../data/commands/check-task-duplicate-request";
import {CheckDuplicateResponse} from "../../../data/commands/check-duplicate-response";
import {ComponentTool} from "../../../../common/util/component-tool";
import {AssetEntry} from "../../../../../../../shared/data/asset-entry";
import { AssetType } from "../../../../../../../shared/data/asset-type";

const ROOT_LABEL: string = "_#(js:Tasks)";
const GET_TASK_FOLDER_URI: string = "../api/portal/schedule/task-folder-browser";
const CHECK_MOVE_DUPLICATE_URI: string = "../api/portal/schedule/move/checkDuplicate";

export const FAKE_ROOT_PATH: string = "_fake_root_";

@Component({
   selector: "move-task-dialog",
   templateUrl: "move-task-dialog.component.html"
})
export class MoveTaskDialogComponent implements OnInit {
   @Input() originalPaths: string[] = [];
   @Input() parentPath: string = "/";
   @Input() parentScope: number = 1;
   @Input() grandparentFolder: string = "/";
   @Input() multi: boolean = false;
   @Input() items: ScheduleTaskModel[];
   @Input() folders: string[];
   @Input() rootNode: TreeNodeModel;
   selectedNodes: TreeNodeModel[] = [];
   @Output() onCommit = new EventEmitter<AssetEntry>();
   @Output() onCancel = new EventEmitter<string>();
   selectedFolder: AssetEntry;

   constructor(private httpClient: HttpClient,
               private modalService: NgbModal) {
   }

   ngOnInit(): void {
   }

   private readonly fakeRootFolder: TreeNodeModel = {
      type: AssetType.FOLDER,
      label: "_#(js:data.datasets.home)",
      disabled: false,
      expanded: true,
      leaf: false,
      materialized: false,
      data: {
         path: FAKE_ROOT_PATH,
         alias: "",
         type: AssetType.SCHEDULE_TASK_FOLDER,
         scope: 1
      }
   };

   get rootLabel(): string {
      return ROOT_LABEL;
   }

   public ok(): void {
      let checkTaskDuplicateRequest: CheckTaskDuplicateRequest = {
         path: this.selectedFolder.path,
         items: this.items,
         folders: this.folders
      };

      this.httpClient.post(CHECK_MOVE_DUPLICATE_URI, checkTaskDuplicateRequest)
          .subscribe(
              (res: CheckDuplicateResponse) => {
                 if(res.duplicate) {
                    let errorMessage: string = "_#(js:common.duplicateName)";
                    ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)", errorMessage);
                 }
                 else {
                    this.onCommit.emit(this.selectedFolder);
                 }
              }
          );
   }

   /**
    * Close dialog without changing anything.
    */
   cancel(): void {
      this.onCancel.emit("cancel");
   }

   public openFolderRequest: (path: string) => Observable<TaskFolderBrowserModel> =
       (value: string) => {
          let params = new HttpParams();
          params = params.set("path", value);

          if(value === FAKE_ROOT_PATH) {
             params = params.set("home", true + "");
          }

          return this.httpClient.get(GET_TASK_FOLDER_URI, { params: params }).pipe(
              map((model: TaskFolderBrowserModel) => {
                 const folders = model.folderList
                    .filter((folder) =>
                       this.originalPaths.indexOf(folder.data.path) === -1 &&
                       this.folders.indexOf(folder.data.path) == -1);

                 // don't show folders that are being moved and files
                 return <TaskFolderBrowserModel> {
                    paths: [this.fakeRootFolder].concat(model.paths),
                    root: model.root,
                    folderList: folders,
                 };
              }));
       };

   /**
    * Set the folder path to move to, to the currently selected folder.
    * @param items   the selected items on the files browser
    */
   folderSelected(item: TreeNodeModel): void {
      this.selectedFolder = !item ? null : item.data;
   }

   isFolder(): boolean {
      return this.multi || (this.folders.length > 0);
   }

   getRequiredMessage(): string {
      return this.isFolder() ? "_#(js:data.datasets.targetFolderRequired)"
         : "_#(js:schedule.tasks.targetTaskRequired)";
   }
}
