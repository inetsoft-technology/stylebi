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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import {
   FormArray,
   UntypedFormBuilder,
   UntypedFormControl,
   UntypedFormGroup,
   ValidationErrors,
   Validators
} from "@angular/forms";
import { BehaviorSubject } from "rxjs";
import { FileTypes } from "../../../../../../../portal/src/app/common/data/file-types";
import { ServerPathInfoModel } from "../../../../../../../portal/src/app/vsobjects/model/server-path-info-model";
import { CSVConfigModel } from "../../../../../../../shared/schedule/model/csv-config-model";
import { ExportFormatModel } from "../../../../../../../shared/schedule/model/export-format-model";
import { ServerLocation } from "../../../../../../../shared/schedule/model/server-location";

export interface ServerSaveFile {
   format: string;
   path: string;
   filePath?: string;
   locationPath?: string;
   ftp?: boolean;
   username?: string;
   password?: string;
}

export interface ServerSave {
   valid: boolean;
   enabled: boolean;
   matchLayout: boolean;
   expandSelections: boolean;
   saveOnlyDataComponents: boolean;
   files: ServerSaveFile[];
   saveExportAllTabbedTables: boolean;
}

@Component({
   selector: "em-server-save",
   templateUrl: "./server-save.component.html",
   styleUrls: ["./server-save.component.scss"]
})
export class ServerSaveComponent implements OnInit {
   @Input() enabled = false;
   @Input() matchLayout = true;
   @Input() expandSelections = true;
   @Input() saveOnlyDataComponents = true;
   @Input() expandEnabled = true;
   @Input() type = "";
   @Input() formats: ExportFormatModel[] = [];
   @Input() csvSaveModel: CSVConfigModel;
   @Input() tableDataAssemblies: string[] = [];
   @Input() saveExportAllTabbedTables = false;
   @Output() serverSaveChanged = new EventEmitter<ServerSave>();

   @Input()
   get saveFormats(): string[] {
      return this._formats;
   }

   set saveFormats(val: string[]) {
      this._formats = val ? val.slice() : [];
      this.updateFiles();
      this.updateExportOptions();
   }

   @Input()
   get savePaths(): string[] {
      return this._paths;
   }

   set savePaths(val: string[]) {
      this._paths = val ? val.slice() : [];
      this.updateFiles();
   }

   @Input()
   get serverPaths(): ServerPathInfoModel[] {
      return this._pathModels;
   }

   set serverPaths(val: ServerPathInfoModel[]) {
      this._pathModels = val ? val.slice() : [];
      this.updateFiles();
   }

   @Input()
   get locations(): ServerLocation[] {
      return this._locations;
   }

   set locations(value: ServerLocation[]) {
      this._locations = value;
      this.updateFiles();
   }

   get files(): ServerSaveFile[] {
      return this._files;
   }

   get isDashboard(): boolean {
      return this.type === "viewsheet";
   }

   get findDuplicateFormat(): ServerSaveFile {
      let serverSaveFile: ServerSaveFile = this._files.find((file1, i) => {
         let index: number = this._files.findIndex((file2) =>
            file1.format === file2.format && file1.path != file2.path);

         return index != -1 && index !== i;
      });

      return serverSaveFile;
   }

   get findDuplicate(): ServerSaveFile {
      return this._files.find((file1, i) =>
         this._files.findIndex((file2) =>
            file1.format === file2.format && file1.path === file2.path) !== i);
   }

   get showMatchAndExpand() {
      return this.isDashboard && this._files && !this._files.every((file) => {
         return this.isHtmlFormat(file.format) || this.isVSCSVFormat(file.format);
      });
   }

   get showHtmlMatchMessage() {
      let hasHtmlOrCsv = false;
      let hasOther = false;

      if(!this._files) {
         return false;
      }

      this._files.forEach((file) => {
         if(this.isHtmlFormat(file.format) ||
            (this.isDashboard && this.isVSCSVFormat(file.format)))
         {
            hasHtmlOrCsv = true;
         }
         else {
            hasOther = true;
         }
      });

      return hasHtmlOrCsv && hasOther;
   }

   get hasExcelFormat(): boolean {
      // 0 is Excel
      return this.saveFormats.includes("0");
   }

   get hasCSVFormat(): boolean {
      // 3 is CSV
      return this.saveFormats.includes("3") ||
         this.isDashboard && this.saveFormats.includes("6");
   }

   get hasLocations(): boolean {
      return !!this.locations && this.locations.length > 0;
   }

   dataSource = new BehaviorSubject<ServerSaveFile[]>([]);
   columnsToDisplay = ["format", "ftp", "path", "actions"];

   private _formats: string[] = [];
   private _paths: string[] = [];
   private _pathModels: ServerPathInfoModel[] = [];
   private _files: ServerSaveFile[] = [];
   private _locations: ServerLocation[] = [];
   filesForm: UntypedFormGroup;
   pathForms: FormArray;
   usersForms: FormArray;
   passwordsForms: FormArray;

   constructor(private fb: UntypedFormBuilder) {
   }

   ngOnInit() {
      if(this.hasLocations) {
         this.columnsToDisplay = ["format", "path", "actions"];
      }
   }

   initForm() {
      let pathArray: UntypedFormControl[] = [];
      let userArray: UntypedFormControl[] = [];
      let passwordArray: UntypedFormControl[] = [];

      for(let i = 0; i < this._paths.length; i ++) {
         pathArray[i] = this.createPathForm(i);
      }

      for(let i = 0; i < this._pathModels.length; i ++) {
         userArray[i] = this.fb.control(this._pathModels[i].username);
         passwordArray[i] = this.fb.control(this._pathModels[i].password);
      }

      this.pathForms = this.fb.array(pathArray);
      this.usersForms = this.fb.array(userArray);
      this.passwordsForms = this.fb.array(passwordArray);
      this.filesForm = this.fb.group({
         paths: this.pathForms,
         usernames: this.usersForms,
         passwords: this.passwordsForms
      });
   }

   createPathForm(index: number): UntypedFormControl {
      let validators = [Validators.required, this.directoryPathValidator];

      if(this.hasLocations) {
         validators.push(this.relativePathValidator);
      }

      return this.fb.control(this._files[index]?.filePath, validators);
   }

   addFile() {
      let model: ServerPathInfoModel = {
         ftp: false,
         path: "",
         username: "",
         password: ""
      };

      this._formats.push(this.formats[0].type);
      this._paths.push("");
      this._pathModels.push(model);
      this.updateFiles();
   }

   removeFile(file: ServerSaveFile) {
      const index = this.files.findIndex(f => f === file);

      if(index >= 0) {
         this._formats.splice(index, 1);
         this._paths.splice(index, 1);
         this._pathModels.splice(index, 1);
         this.updateFiles();
         this.fireServerSaveChanged();
      }
   }

   fireServerSaveChanged() {
      this.updatePaths();
      this.saveOnlyDataComponents = this.matchLayout ? false : this.saveOnlyDataComponents;
      this.serverSaveChanged.emit({
         valid: this.isValid(),
         matchLayout: this.matchLayout,
         expandSelections: this.expandSelections,
         saveOnlyDataComponents: this.saveOnlyDataComponents,
         enabled: this.enabled,
         files: this.files,
         saveExportAllTabbedTables: this.saveExportAllTabbedTables
      });
   }

   private isValid(): boolean {
      if(this.enabled) {
         if(this.files.length === 0) {
            return false;
         }

         if(this.findDuplicate || this.findDuplicateFormat) {
            return false;
         }

         if(this.hasCSVFormat && this.csvSaveModel?.selectedAssemblies?.length == 0) {
            return false;
         }

         const checkLocation = this.hasLocations;

         if(this.files.find(f => !f.filePath || checkLocation && !f.locationPath)) {
            return false;
         }

         if(this.filesForm.invalid) {
            return false;
         }
      }

      return true;
   }

   private updateFiles() {
      const length = Math.min(this._formats.length, this._paths.length);
      this._files = [];

      for(let i = 0; i < length; i++) {
         let pathModel = !!this._pathModels ? this._pathModels[i] : null;

         this._files.push(this.createServerSaveFile(this._formats[i], this._paths[i], pathModel));
      }

      this.dataSource.next(this._files);
      this.initForm();
   }

   private createServerSaveFile(format: string, path: string, pathModel: ServerPathInfoModel): ServerSaveFile {
      const file: ServerSaveFile = { format, path, filePath: path, locationPath: null };

      if(this.hasLocations) {
         if(path) {
            const location = this.locations.find(l =>
               path.startsWith(l.path) &&
                  (path === l.path || path[l.path.length] == "/" || path[l.path.length] == "\\"));

            if(location) {
               file.locationPath = location.path;
               file.filePath = path.substring(location.path.length + 1);

               if(location.pathInfoModel) {
                  file.ftp = location.pathInfoModel.ftp;
                  file.username = location.pathInfoModel.username;
                  file.password = location.pathInfoModel.password;
               }
            }
         }
      }

      if(!!pathModel) {
         file.ftp = pathModel.ftp;
         file.username = pathModel.username;
         file.password = pathModel.password;
      }

      return file;
   }

   private updatePaths(): void {
      this._files.forEach(f => this.updatePath(f));
   }

   updatePath(file: ServerSaveFile): void {
      if(this.hasLocations && file.locationPath) {
         if(file.filePath) {
            file.path = file.locationPath + "/" +
               file.filePath.replace(/^(([/\\]+)|([a-zA-Z]:[/\\]))/, "");
         }
         else {
            file.path = file.locationPath;
         }

         const location: ServerLocation = this.locations.find(l => l.path == file.locationPath);

         if(!!location.pathInfoModel) {
            file.username = location.pathInfoModel.username;
            file.password = location.pathInfoModel.password;
         }

         file.ftp = file.ftp || location.pathInfoModel.ftp ||
            !!file.path && (file.path.startsWith("ftp:") || file.path.startsWith("sftp:"));
      }
      else {
         file.path = file.filePath;
         file.ftp = file.ftp ||
            !!file.path && (file.path.startsWith("ftp:") || file.path.startsWith("sftp:"));
      }
   }


   private updateExportOptions() {
      if(!this.hasExcelFormat) {
         this.saveOnlyDataComponents = false;
      }
   }

   private isHtmlFormat(format: string) {
      return format === FileTypes.HTML + "";
   }

   private isVSCSVFormat(format: string) {
      return format === "6";
   }
   updateUserForm(index: number) {
      this.dataSource.getValue()[index].username = this.usersForms.at(index).value;
      this.fireServerSaveChanged();
   }

   updatePasswordForm(index: number) {
      this.dataSource.getValue()[index].password = this.passwordsForms.at(index).value;
      this.fireServerSaveChanged();
   }

   updatePathForm(index: number) {
      this.dataSource.getValue()[index].filePath = this.pathForms.at(index).value;
      this.fireServerSaveChanged();
   }

   getPathErrors(index: number): ValidationErrors {
      let errors = this.pathForms.at(index).errors;
      return errors == null ? [] : errors;
   }

   relativePathValidator(control: UntypedFormControl): ValidationErrors {
      if(/^(([/\\])|([a-zA-Z]:[/\\])).*$/.test(control.value)) {
         return { relativePath: true };
      }

      return null;
   }

   directoryPathValidator(control: UntypedFormControl): ValidationErrors {
      if(/.*([/:\\])$/.test(control.value)) {
         return { directoryPath: true };
      }

      return null;
   }
}
