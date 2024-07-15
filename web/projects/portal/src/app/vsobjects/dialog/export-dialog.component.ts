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
import { HttpClient, HttpParams } from "@angular/common/http";
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { UntypedFormGroup } from "@angular/forms";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Tool } from "../../../../../shared/util/tool";
import { MessageCommand } from "../../common/viewsheet-client/message-command";
import { ExportDialogModel } from "../model/export-dialog-model";
import { LocalStorage } from "../../common/util/local-storage.util";
import { FileFormatPaneModel } from "../model/file-format-pane-model";
import { ComponentTool } from "../../common/util/component-tool";
import { FileFormatType } from "../model/file-format-type";

const CHECK_EXPORT_VALID_URI: string = "../api/vs/check-export-valid/";
const modelKey = "filePaneModel";

@Component({
   selector: "export-dialog",
   templateUrl: "export-dialog.component.html",
})
export class ExportDialog implements OnInit {
   @Input() model: ExportDialogModel;
   @Input() runtimeId: string;
   @Input() exportTypes: {label: string, value: string}[] = [];
   @Output() onCommit = new EventEmitter<ExportDialogModel>();
   @Output() onCancel = new EventEmitter<string>();
   form: UntypedFormGroup;

   constructor(private http: HttpClient, private modalService: NgbModal) {
   }

   ngOnInit(): void {
      this.initForm();
      this.getStorageModel();
   }

   initForm(): void {
      this.form = new UntypedFormGroup({
         exportForm: new UntypedFormGroup({}),
      });
   }

   isEmptyTable(): boolean {
      return this.model && this.model.fileFormatPaneModel &&
         this.model.fileFormatPaneModel.formatType == FileFormatType.EXPORT_TYPE_CSV &&
         Tool.isEmpty(this.model.fileFormatPaneModel.tableDataAssemblies);
   }

   close(): void {
      this.onCancel.emit("cancel");
   }

   ok() {
      if(!this.model.fileFormatPaneModel.includeCurrent &&
         this.model.fileFormatPaneModel.selectedBookmarks.length == 0 &&
         this.model.fileFormatPaneModel.formatType != FileFormatType.EXPORT_TYPE_SNAPSHOT)
      {
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
            "_#(js:common.fileformatPane.notvoid)");
         return;
      }

      this.http.get("../export/check/" + Tool.byteEncode(this.runtimeId))
         .subscribe(
            (data: MessageCommand) => {
               if(data.type == "OK") {
                  this.ok2();
               }
               else {
                  ComponentTool.showMessageDialog(this.modalService, data.type, data.message);
               }
            },
            (err) => {
               // TODO handle error
               console.error("Failed to check if export valid: ", err);
            });
   }

   private ok2() {
      this.saveToStorage();
      this.onCommit.emit(this.model);
   }

   /**
    * Get a saved model from local storage if available.
    */
   private getStorageModel(): void {
      let defaultMatchLayout = null;

      if (this.model.fileFormatPaneModel.matchLayout != null) {
         defaultMatchLayout = this.model.fileFormatPaneModel.matchLayout;
      }

      const fileModel: FileFormatPaneModel = JSON.parse(LocalStorage.getItem(modelKey));

      if(fileModel) {
         const bookmarks: string[] = Tool.clone(this.model.fileFormatPaneModel.allBookmarks);
         const expandEnabled = this.model.fileFormatPaneModel.expandEnabled;
         const tableAssemblies = this.model.fileFormatPaneModel ?
            this.model.fileFormatPaneModel.tableDataAssemblies : null;
         fileModel.hasPrintLayout = this.model.fileFormatPaneModel.hasPrintLayout;
         this.model.fileFormatPaneModel = fileModel;
         this.model.fileFormatPaneModel.allBookmarks = bookmarks;
         this.model.fileFormatPaneModel.selectedBookmarks = [];
         this.model.fileFormatPaneModel.expandEnabled = expandEnabled;
         this.model.fileFormatPaneModel.tableDataAssemblies = tableAssemblies;
         this.model.fileFormatPaneModel.exportAllTabbedTables =
            true == this.model.fileFormatPaneModel.exportAllTabbedTables;

         if(!expandEnabled) {
            if (this.model.fileFormatPaneModel.matchLayout == null) {
                this.model.fileFormatPaneModel.matchLayout = true;
            }
         }
         else {
            //only overwrite if matchLayout = false because then default export.ExpandEnabled = true
            if (!defaultMatchLayout) {
               this.model.fileFormatPaneModel.matchLayout = defaultMatchLayout;
            }
         }
      }
   }

   /**
    * Save model to local storage for future presets.
    */
   private saveToStorage(): void {
      LocalStorage.setItem(modelKey, JSON.stringify(this.model.fileFormatPaneModel));
   }

   getExportTypes(): {label: string, value: string}[] {
      return this.exportTypes;
   }
}
