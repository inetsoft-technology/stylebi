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
import { HttpClient } from "@angular/common/http";
import {
   Component,
   EventEmitter,
   Input,
   OnChanges,
   OnDestroy,
   OnInit,
   Output,
   SimpleChanges,
   TemplateRef,
   ViewChild
} from "@angular/core";
import {
   AbstractControl,
   UntypedFormControl,
   UntypedFormGroup,
   ValidationErrors
} from "@angular/forms";
import { NgbModal, NgbModalOptions } from "@ng-bootstrap/ng-bootstrap";
import { Observable, Subscription } from "rxjs";
import { debounceTime, map, tap } from "rxjs/operators";
import { DashboardOptions } from "../../../../../../../../em/src/app/settings/schedule/model/dashboard-options";
import { ReportOptions } from "../../../../../../../../em/src/app/settings/schedule/model/reports-options";
import { IdentityId } from "../../../../../../../../em/src/app/settings/security/users/identity-id";
import { IdentityType } from "../../../../../../../../shared/data/identity-type";
import { AddParameterDialogModel } from "../../../../../../../../shared/schedule/model/add-parameter-dialog-model";
import { CSVConfigModel } from "../../../../../../../../shared/schedule/model/csv-config-model";
import { ExportFormatModel } from "../../../../../../../../shared/schedule/model/export-format-model";
import { GeneralActionModel } from "../../../../../../../../shared/schedule/model/general-action-model";
import { ServerLocation } from "../../../../../../../../shared/schedule/model/server-location";
import { TaskActionPaneModel } from "../../../../../../../../shared/schedule/model/task-action-pane-model";
import { ScheduleUsersService } from "../../../../../../../../shared/schedule/schedule-users.service";
import { FormValidators } from "../../../../../../../../shared/util/form-validators";
import { Tool } from "../../../../../../../../shared/util/tool";
import { FileTypes } from "../../../../../common/data/file-types";
import { ComponentTool } from "../../../../../common/util/component-tool";
import { GuiTool } from "../../../../../common/util/gui-tool";
import { ServerPathInfoModel } from "../../../../../vsobjects/model/server-path-info-model";
import { VSBookmarkInfoModel } from "../../../../../vsobjects/model/vs-bookmark-info-model";
import { EmailAddrDialogModel } from "../../../../../widget/email-dialog/email-addr-dialog-model";
import { EmailDialogData } from "../../../../../widget/email-dialog/email-addr-dialog.component";
import { TreeNodeModel } from "../../../../../widget/tree/tree-node-model";
import { ScheduleAlertModel } from "../../../model/schedule-alert-model";

@Component({
   selector: "action-accordion",
   templateUrl: "./action-accordion.component.html",
   styleUrls: ["./action-accordion.component.scss"]
})
export class ActionAccordion implements OnInit, OnChanges, OnDestroy {
   @Input() executeAsGroup: boolean;
   @Input() set parentForm(value: UntypedFormGroup) {
      this._parentForm = value;

      if(!!value && !!this.form) {
         this.parentForm.addControl("action", this.form);
      }
   }

   get parentForm(): UntypedFormGroup {
      return this._parentForm;
   }

   @Input() autoCompleteModel: string[];
   @Input() printers: string[];
   @Input() folders: string[];
   @Input() requiredParameters: string[];
   @Input() optionalParameters: string[];
   @Input() containsSheet: boolean;
   @Input() tableDataAssemblies: string[] = [];
   @Input() hasPrintLayout: boolean = false;
   @Input() bookmarks: VSBookmarkInfoModel[];
   @Input() bookmarkList: String[] = [];
   @Input() selectedBookmark: VSBookmarkInfoModel;
   @Input() set highlights(values: ScheduleAlertModel[]) {
      this._highlights = [];

      for(let i = 0; i < values.length; i++) {
         if(values[i]) {
            this._highlights.push(values[i]);
         }
      }

      this.updateDisabledHighlights();
   }

   get highlights(): ScheduleAlertModel[] {
      return this._highlights;
   }

   private _highlights: ScheduleAlertModel[] = [];
   disabledHighlights: ScheduleAlertModel[] = [];
   highlightSheet: string;

   @Output() onSetCreationParameters = new EventEmitter<void>();
   @ViewChild("emailAddrDialog") emailAddrDialog: TemplateRef<any>;
   initialAddresses: string = "";
   emailDialogModel: EmailAddrDialogModel;
   embeddedOnly: boolean = true;

   private _model: TaskActionPaneModel;
   private _action: GeneralActionModel;
   private _parentForm: UntypedFormGroup;
   private _isSelfUser: boolean = undefined;
   confirmPassword: string;
   emailUsers: IdentityId[] = [];
   groups: IdentityId[] = [];

   isIE = GuiTool.isIE();
   locationPath: string;
   filePath: string;
   ftp: boolean;
   useCredential: boolean;
   secretId: string;
   username: string;
   password: string;
   _saveFormat: string;
   selectedFormatIndex: number = -1;
   saveStrings: string[];
   form: UntypedFormGroup = null;
   allParameters: string[];
   formSubscriptions: Subscription = new Subscription();
   selectedBookmarkListIndex: number = -1;
   userAliases: Map<IdentityId, string>;

   @Input() set model(value: TaskActionPaneModel) {
      this._model = value;
      this.setSaveStrings();

      if(!!this._action) {
         this.initForm();
      }
   }

   get model(): TaskActionPaneModel {
      return this._model;
   }

   @Input() set action(value: GeneralActionModel) {
      this._action = value;
      this.confirmPassword = this._action.password;
      this.setSaveStrings();
      this.initForm();
      this.highlightSheet = value.sheet;

      if(value.deliverEmailsEnabled && value.format == "CSV" && !value.csvExportModel) {
         this._action.csvExportModel = new CSVConfigModel();
      }

      if(value.saveToServerEnabled && this.saveFormat === "3" && !value.csvSaveModel) {
         this._action.csvSaveModel = new CSVConfigModel();
      }
   }

   get action(): GeneralActionModel {
      return this._action;
   }

   get path(): string {
      this.form.valueChanges.subscribe();
      return this.buildPath(this.locationPath, this.filePath);
   }

   set path(value: string) {
      const fields = this.parsePath(value);
      this.locationPath = fields.locationPath;
      this.filePath = fields.filePath;
   }

   get locations(): ServerLocation[] {
      return this?.model.serverLocations;
   }

   get hasLocations(): boolean {
      return !!this.locations && this.locations.length > 0;
   }

   get deliveryMessage(): string {
      return this.action?.message ?? "";
   }

   set deliveryMessage(value: string) {
      if(this.action) {
         this.action.message = value;
         this.action.htmlMessage = true;
      }
   }

   constructor(private modalService: NgbModal, private http: HttpClient,
               private usersService: ScheduleUsersService)
   {
      usersService.getEmailUsers().subscribe(value => {
         if(this.emailUsers != value) {
            this.emailUsers = value;
            this.initForm();
         }
      });

      usersService.getEmailGroups().subscribe(value => {
         if(this.groups != value) {
            this.groups = value;
            this.initForm();
         }
      });

      usersService.getEmailUserAliases().subscribe(value => {
         if(this.userAliases != value) {
            this.userAliases = value;
            this.initForm();
         }
      });
   }

   ngOnInit(): void {
   }

   ngOnChanges(changes: SimpleChanges) {
      if(changes.hasOwnProperty("requiredParameters") ||
         changes.hasOwnProperty("optionalParameters"))
      {
         this.allParameters = [];

         if(this.requiredParameters) {
            this.allParameters.push(...this.requiredParameters);
         }

         if(this.optionalParameters) {
            this.allParameters.push(...this.optionalParameters);
         }
      }
   }

   ngOnDestroy(): void {
      this.unsubscribeForm();
   }

   set saveFormat(value: string) {
      this._saveFormat = value;

      if(this.isCSVFormat(value) && !this.action.csvSaveModel) {
         this.action.csvSaveModel = new CSVConfigModel();
      }
   }

   get saveFormat(): string {
      if(!this._saveFormat && this.model && this.action) {
         return this.saveFormats[0].type;
      }

      return this._saveFormat;
   }

   get parameters(): AddParameterDialogModel[] {
      return this.action.parameters;
   }

   set parameters(value: AddParameterDialogModel[]) {
      this.action.parameters = value;
   }

   get mailFormats(): ExportFormatModel[] {
      return this.model ? (this.isDashboard ?
            this.model.vsMailFormats : this.model.mailFormats) : [];
   }

   get saveFormats(): ExportFormatModel[] {
      return this.model ? (this.isDashboard ?
            this.model.vsSaveFileFormats : this.model.saveFileFormats) : [];
   }

   get fromEmail(): string {
      return this.model ? this.model.fromEmailEnabled ?
         this.action.fromEmail : this.model.defaultFromEmail : null;
   }

   set fromEmail(email: string) {
      this.action.fromEmail = email;
   }

   get showMatchAndExpand() {
      return this.isDashboard && this.action && this.action.saveFormats &&
         !this.action.saveFormats.every((format) => {
            return this.isHtmlFormat(format) || this.isVSCSVFormat(format);
         });
   }

   get showMatchMessage() {
      let hasHtml = false;
      let hasCSV = false;
      let hasOther = false;

      if(!this.action || !this.action.saveFormats) {
         return false;
      }

      this.action.saveFormats.forEach((format) => {
         if(this.isHtmlFormat(format) || this.isVSCSVFormat(format)) {
            hasHtml = true;
            hasCSV = true;
         }
         else {
            hasOther = true;
         }
      });

      return hasHtml && (hasOther || hasCSV);
   }

   public bookmarksEquivalent(original: VSBookmarkInfoModel,
                              other: VSBookmarkInfoModel): boolean
   {
      return original && other && original.label === other.label
         && original.type === other.type;
   }

   public editBookmark(index: number): void {
      this.selectedBookmarkListIndex = index;
      let selectedIndex = this.bookmarks.findIndex(b => b.label == this.bookmarkList[index]);

      if(selectedIndex != -1) {
         this.selectedBookmark = this.bookmarks[selectedIndex];
      }
   }

   public addBookmark(): void {
      if(this.action.bookmarks.findIndex(b =>
          b.name == this.selectedBookmark.name &&
          Tool.isEquals(b.owner, this.selectedBookmark.owner)) != -1)
      {
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
            "_#(js:em.scheduler.actions.bookmarkDuplicate)");
         return;
      }
      else {
         this.bookmarkList.push(this.selectedBookmark.label);
         this.action.bookmarks.push(this.selectedBookmark);
      }
   }

   public modifyBookmark(): void {
      if(this.action.bookmarks
         .findIndex(b => b.name == this.selectedBookmark.name &&
            b.owner == this.selectedBookmark.owner) != -1)
      {
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
            "_#(js:em.scheduler.actions.bookmarkDuplicate)");
         return;
      }
      else {
         this.bookmarkList[this.selectedBookmarkListIndex] = this.selectedBookmark.label;
         this.action.bookmarks[this.selectedBookmarkListIndex] = this.selectedBookmark;
      }
   }

   public removeBookmark(): void {
      this.bookmarkList.splice(this.selectedBookmarkListIndex, 1);
      this.action.bookmarks.splice(this.selectedBookmarkListIndex, 1);
      this.selectedBookmarkListIndex = -1;
   }

   private setSaveStrings(): void {
      this.saveStrings = [];

      if(this.action && this.action.saveFormats) {
         for(let i = 0; i < this.action.saveFormats.length; i++) {
            const path: string = this.action.filePaths[i];
            const formatType: string = this.action.saveFormats[i];
            const fmt: any =
               this.saveFormats.find((format) => format.type == formatType);

            if(fmt) {
               const stringFormat: string = fmt.label;
               const fields = this.parsePath(path);
               this.saveStrings.push(
                  this.createSaveString(fields.filePath, stringFormat, fields.locationPath));
            }
         }
      }
   }

   public addFile(): void {
      if(!this.action.filePaths) {
         this.action.filePaths = [];
      }

      if(!this.action.saveFormats) {
         this.action.saveFormats = [];
      }

      if(!this.isPathFormatValid() || !this.isVSCSVFileValid()) {
         return;
      }

      const fmt: any =
         this.saveFormats.find((format) => format.type == this.saveFormat);

      if(fmt) {
         const duplicate = this.saveStrings.some((saveString) => {
            const fields = this.parseSaveString(saveString);
            return this.filePath === fields.path && fmt.label === fields.format &&
               this.locationPath === fields.location;
         });

         if(duplicate) {
            ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
                                            "_#(js:em.scheduler.actions.diskFileDuplicate)");
            return;
         }

         let pathModel: ServerPathInfoModel = {
            path: this.path,
            ftp: this.ftp,
            useCredential: this.useCredential,
            secretId: this.secretId,
            username: this.username,
            password: this.password
         };
         pathModel = this.hasLocations && !!this.locationPath ?
            this.getLocationPathInfo(this.locationPath) : pathModel;
         pathModel.path = this.path;

         const addNewFile: any = () => {
            this.action.filePaths.push(this.path);
            this.action.saveFormats.push(fmt.type);

            if(this.action.serverFilePaths == null) {
               this.action.serverFilePaths = [];
            }

            this.action.serverFilePaths.push(pathModel);
            this.saveStrings.push(
               this.createSaveString(this.filePath, fmt.label, this.locationPath));
         };

         this.checkSameFormatFile(fmt, false, addNewFile);
         this.updateAvailableExportOptions();
      }
   }

   isVSCSVFileValid() {
      if(this.isVSCSVFormat(this.saveFormat) && (!this.action.csvSaveModel ||
         this.action.csvSaveModel.selectedAssemblies &&
         this.action.csvSaveModel.selectedAssemblies.length == 0))
      {
         return false;
      }

      return true;
   }

   private buildPath(locationPath: string, filePath: string): string {
      let _path: string;

      if(this.hasLocations && locationPath) {
         if(filePath) {
            _path = locationPath + "/" + filePath.replace(/^(([/\\]+)|([a-zA-Z]:[/\\]))/, "");
         }
         else {
            _path = locationPath;
         }
      }
      else {
         _path = filePath;
      }

      return _path;
   }

   private parsePath(value: string): { filePath: string, locationPath: string } {
      const result = {
         locationPath: null,
         filePath: value
      };

      if(this.hasLocations) {
         if(value) {
            const location = this.locations.find(l =>
               value.startsWith(l.path) &&
               (value == l.path || value[l.path.length] == "/" || value[l.path.length] == "\\"));

            if(location) {
               result.locationPath = location.path;
               result.filePath = value.substring(location.path.length + 1);
            }
         }
      }

      return result;
   }

   private parseSaveString(saveString: string): {path: string, format: string, location: string } {
      const match = /^((.+): )?(.+) - (.+)$/.exec(saveString);

      if(!!match) {
         return {
            path: match[3],
            format: match[4],
            location: match[5]
         };
      }

      return null;
   }

   private createSaveString(path: string, format: string, location: string) {
      const locationLabel = this.getLocationLabel(location);

      if(locationLabel) {
         return locationLabel + ": " + path + " - " + format;
      }
      else {
         return path + " - " + format;
      }
   }

   private getLocationLabel(locationPath: string) {
      if(this.hasLocations && locationPath) {
         const location = this.locations.find(l => l.path === locationPath);
         return location?.label;
      }

      return null;
   }

   private getLocationPathInfo(locationPath: string) {
      if(this.hasLocations && locationPath) {
         const location = this.locations.find(l => l.path === locationPath);
         return location?.pathInfoModel;
      }

      return null;
   }

   public modifyFile(): void {
      if(!this.isPathFormatValid() || !this.isVSCSVFileValid()) {
         return;
      }

      this.action.filePaths[this.selectedFormatIndex] = this.path;
      const fmt: any =
         this.saveFormats.find((format) => format.type == this.saveFormat);

      const pathModel: ServerPathInfoModel = this.hasLocations && !!this.locationPath ?
         this.getLocationPathInfo(this.locationPath) : {
            path: this.path,
            ftp: this.ftp,
            useCredential: this.useCredential,
            secretId: this.secretId,
            username: this.username,
            password: this.password
         };
      pathModel.path = this.path;

      if(fmt) {
         const modifyFile: any = () => {
            this.action.saveFormats[this.selectedFormatIndex] = fmt.type;
            this.action.serverFilePaths[this.selectedFormatIndex] = pathModel;
            this.saveStrings[this.selectedFormatIndex] =
               this.createSaveString(this.filePath, fmt.label, this.locationPath);
         };

         this.checkSameFormatFile(fmt, true, modifyFile);
         this.updateAvailableExportOptions();
      }
   }

   public deleteFile(): void {
      this.action.saveFormats.splice(this.selectedFormatIndex, 1);
      this.action.filePaths.splice(this.selectedFormatIndex, 1);
      this.action.serverFilePaths.splice(this.selectedFormatIndex, 1);
      this.saveStrings.splice(this.selectedFormatIndex, 1);
      this.updateAvailableExportOptions();
      this.selectedFormatIndex = -1;
   }

   public editFile(index: number): void {
      this.selectedFormatIndex = index;
      this.path = this.action.filePaths[this.selectedFormatIndex];
      this.useCredential = this.action.serverFilePaths[this.selectedFormatIndex].useCredential;
      this.secretId = this.action.serverFilePaths[this.selectedFormatIndex].secretId;
      this.username = this.action.serverFilePaths[this.selectedFormatIndex].username;
      this.password = this.action.serverFilePaths[this.selectedFormatIndex].password;
      this.ftp = this.action.serverFilePaths[this.selectedFormatIndex].ftp;
      this.saveFormat = this.action.saveFormats[this.selectedFormatIndex];
   }

   private checkSameFormatFile(fmt: any, modify: boolean, processFile: () => {}) {
      const sameFormat = this.saveStrings.find((saveString) => {
         const fields = this.parseSaveString(saveString);
         const sameEntry =  modify && saveString == this.saveStrings[this.selectedFormatIndex];
         return fields.format === fmt.label && !sameEntry;
      });

      if(sameFormat) {
         ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)",
                                         "_#(js:portal.schedule.task.action.duplicateFormat)")
            .then((buttonClicked: string) => {
               if(buttonClicked === "ok") {
                  const existingPath = sameFormat.split(" - ")[0];
                  const filePathIndex = this.action.filePaths.findIndex((fileName) => {
                     return fileName === existingPath;
                  });
                  const saveStringIndex = this.saveStrings.findIndex((saveString) => {
                     return saveString === sameFormat;
                  });

                  const pathModel: ServerPathInfoModel = {
                     path: this.path,
                     ftp: this.ftp,
                     useCredential: this.useCredential,
                     secretId: this.secretId,
                     username: this.username,
                     password: this.password
                  };

                  this.action.filePaths[filePathIndex] = this.path;
                  this.action.serverFilePaths[filePathIndex] = pathModel;
                  this.saveStrings[saveStringIndex] =
                     this.createSaveString(this.filePath, fmt.label, this.locationPath);

                  if(modify) {
                     this.action.filePaths.splice(this.selectedFormatIndex, 1);
                     this.action.saveFormats.splice(this.selectedFormatIndex, 1);
                     this.saveStrings.splice(this.selectedFormatIndex, 1);
                  }

                  return;
               }
               else {
                  return;
               }
            });
      }
      else {
         processFile();
      }
   }

   private updateAvailableExportOptions() {
      this.action.saveOnlyDataComponents = this.hasExcelFormat() ?
         this.action.saveOnlyDataComponents : false;
   }

   public updateEmailDataOnlyFormat() {
      this.action.emailOnlyDataComponents = this.action.emailMatchLayout ?
         false : this.action.emailOnlyDataComponents;
   }

   public updateDataOnlyFormat() {
      this.action.saveOnlyDataComponents = this.action.saveMatchLayout ?
         false : this.action.saveOnlyDataComponents;
   }

   public hasExcelFormat(): boolean {
      let result = false;

      this.action.saveFormats.forEach((format) => {
         if(format == "0") {
            result = true;
         }
      });

      return result;
   }

   private isPathFormatValid() {
      let filePath: string = this.path.toLowerCase();

      if(filePath.indexOf("ftp://") == 0) {
         let ftpFormat: RegExp = /^ftp:\/\/([^:]+):?([^/]+)?\/(.+)$/;

         if(!ftpFormat.test(filePath)) {
            ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)", "The FTP path format is invalid. "
               + "Please use ftp://<username>:<port>/<filename> .");
            return false;
         }

         filePath = ftpFormat.exec(filePath)[3];
      }

      let filePathRegExp: RegExp = !filePath.startsWith("sftp://") ?  /[*"|<>?]/g : /[*"|<>]/g;

      if(filePathRegExp.test(filePath) && !filePath.startsWith("sftp://")) {
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
            "The path contains invalid characters. Please avoid using " + '<>*?"|.');
         return false;
      }

      if(/.*([/:\\])$/.test(filePath)) {
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)", "Please specify a valid file name.");
         return false;
      }

      return true;
   }

   get isDashboard(): boolean {
      return this.action.actionType == "ViewsheetAction";
   }

   get bundledDisabled(): boolean {
      return this.action.format === ReportOptions.HTML_BUNDLE ||
         this.action.format === ReportOptions.HTML_BUNDLE_NO_PAGINATION ||
         this.action.format === ReportOptions.HTML_NO_PAGINATION_EMAIL ||
         this.action.format === DashboardOptions.PNG ||
         (this.action.format === ReportOptions.CSV && this.action.actionType === "ViewsheetAction");
   }

   updateEmailTo(emailTo: EmailDialogData) {
      let emailToStr: string;

      if(typeof emailTo == "string") {
         emailToStr = emailTo;
      }
      else {
         emailToStr = (<EmailDialogData>emailTo).emails;
      }

      this.updateDeliveryEmails(emailToStr);
      this.form.controls["to"].setValue(emailToStr);
      // for some reason the value is not reflected on gui unless form is re-init
      this.initForm();
   }

   private updateDisabledHighlights(): void {
      this.disabledHighlights = [];

      if(this.highlights && this.action.highlightAssemblies &&
         this.action.highlightAssemblies.length > 0 &&
         this.highlightSheet == this.action.sheet)
      {
         for(let i = 0; i < this.action.highlightAssemblies.length; i++) {
            const assembly: string = this.action.highlightAssemblies[i];
            const highlightName: string = this.action.highlightNames[i];

            const found: boolean = this.highlights
               .some((alert: ScheduleAlertModel) => {
                  const name: string = alert.highlight;
                  const splitIndex: number = name.indexOf(" (");
                  return assembly == alert.element && highlightName == alert.highlight;
               });

            if(!found) {
               this.disabledHighlights.push(
                  <ScheduleAlertModel>{element: assembly, highlight: highlightName});
            }
         }
      }
   }

   isHighlightSelected(assembly: string, highlightName: string): boolean {
      if(!this.action.highlightAssemblies || this.action.highlightAssemblies.length == 0) {
         return false;
      }

      const splitIndex: number = highlightName.indexOf(" (");
      const parcedName: string = splitIndex > -1 ? highlightName.substring(0, splitIndex)
         : highlightName;

      for(let i = 0; i < this.action.highlightAssemblies.length; i++) {
         if(this.action.highlightAssemblies[i] === assembly
            && this.action.highlightNames[i] == parcedName)
         {
            return true;
         }
      }

      return false;
   }

   highlightSelectionChange(assembly: string, highlightName: string): void {
      if(this.highlightSheet != this.action.sheet) {
         this.action.highlightNames = [];
         this.action.highlightAssemblies = [];
         this.highlightSheet = this.action.sheet;
      }

      const splitIndex: number = highlightName.indexOf(" (");
      const parcedName: string = splitIndex > -1 ? highlightName.substring(0, splitIndex)
         : highlightName;

      if(this.isHighlightSelected(assembly, highlightName)) {
         let selectedIndex: number = -1;

         for(let i = 0; i < this.action.highlightAssemblies.length; i++) {
            if(this.action.highlightAssemblies[i] === assembly
               && this.action.highlightNames[i] == parcedName)
            {
               selectedIndex = i;
               break;
            }
         }

         this.action.highlightAssemblies.splice(selectedIndex, 1);
         this.action.highlightNames.splice(selectedIndex, 1);
      }
      else {
         if(!this.action.highlightAssemblies || !this.action.highlightNames) {
            this.action.highlightAssemblies = [];
            this.action.highlightNames = [];
         }

         this.action.highlightAssemblies.push(assembly);
         this.action.highlightNames.push(parcedName);
      }
   }

   public getHighlightLabel(highlight: string, count: number): string {
      if(highlight.match(/^RangeOutput_Range_\d+$/)) {
         highlight = highlight.replace(/^RangeOutput_Range_(\d+)$/, "_#(js:Range) $1");
      }

      return count > 1 ? highlight + `(${count})` : highlight;
   }

   public getHighlightConditionLabel(condition: string): string {
      if(condition.match(/^RangeOutput_Range_\d+$/)) {
         return "_#(js:to) " + condition;
      }

      return condition;
   }

   public openEmailDialogNotify(): void {
      this.initialAddresses = this.action.notifications;

      this.isSelfUser.then(self => {
         this.embeddedOnly = true;

         this.openEmailDialog(!self).then(
            (result: EmailDialogData) => {
               this.updateNotificationEmails(result.emails);
               this.form.controls["notification"].setValue({value: result.emails});
            },
            () => {
               // canceled
            }
         );
      });
   }

   public openEmailDialogTo(): void {
      if(!this.action.deliverEmailsEnabled) {
         return;
      }

      this.initialAddresses = this.action.to;

      this.isSelfUser.then(self => {
         this.embeddedOnly = true;

         this.openEmailDialog(!self).then(
            (result: EmailDialogData) => {
               this.updateEmailTo(result);
            },
            () => {
               // canceled
            }
         );
      });
   }

   public openEmailDialogCC(bcc?: boolean): void {
      if(!this.action.deliverEmailsEnabled) {
         return;
      }

      this.initialAddresses = bcc ? this.action.bccAddress : this.action.ccAddress;

      this.isSelfUser.then(self => {
         this.embeddedOnly = true;

         this.openEmailDialog(!self).then(
            (result: EmailDialogData) => {
               if(bcc) {
                  this.action.bccAddress = result.emails;
               }
               else {
                  this.action.ccAddress = result.emails;
               }

               this.initForm();
            },
            () => {
               // canceled
            }
         );
      });
   }

   private openEmailDialog(showGroups: boolean = true): Promise<EmailDialogData> {
      const userRoot: TreeNodeModel = {
         label: "_#(js:Users)",
         data: "",
         type: String(IdentityType.USERS),
         leaf: false
      };

      const groupRoot: TreeNodeModel = {
         label: "_#(js:Groups)",
         data: "",
         type: String(IdentityType.GROUPS),
         leaf: false
      };

      const root: TreeNodeModel = {
         label: "_#(js:Root)",
         data: "",
         type: String(IdentityType.ROOT),
         children: !this.embeddedOnly || !showGroups ? [userRoot] : [userRoot, groupRoot],
         leaf: false
      };

      this.emailDialogModel = {
         rootTree: root
      };
      const options: NgbModalOptions = {
         backdrop: "static",
         windowClass: "email-addr-dialog"
      };

      return this.modalService.open(this.emailAddrDialog, options).result;
   }

   isPasswordDisable(): boolean {
      return this.model?.fipsMode || (!this.action.bundledAsZip && this.action.format != "HTML_BUNDLE"
         && this.action.format != "HTML_BUNDLE_NO_PAGINATION");
   }

   initForm(): void {
      if(!!!this.model || !!!this.action) {
         return;
      }

      this.unsubscribeForm();
      let controls: { [key: string]: AbstractControl;} = {
         "notification": new UntypedFormControl({value: this.action.notifications}),
         "from": new UntypedFormControl({value: this.fromEmail}),
         "secretId": new UntypedFormControl({
            value: this.action.secretId,
            disabled: this.isPasswordDisable()
         }),
         "password": new UntypedFormControl({
            value: this.action.password,
            disabled: this.isPasswordDisable()
         }),
         "confirmPassword": new UntypedFormControl({
            value: this.confirmPassword,
            disabled: this.isPasswordDisable()
         }),
         "attachmentName": new UntypedFormControl(this.action.attachmentName,
            FormValidators.isValidWindowsFileName),
      };

      if(this.model.emailDeliveryEnabled) {
         controls.to = new UntypedFormControl({value: this.action.to});
         controls.cc = new UntypedFormControl({value: this.action.ccAddress}, [
            FormValidators.emailList(",;", true, false, this.getEmailUsers(), true)
         ]);
         controls.bcc = new UntypedFormControl({value: this.action.bccAddress}, [
            FormValidators.emailList(",;", true, false, this.getEmailUsers(), true)
         ]);
      }

      this.form = new UntypedFormGroup(controls, this.passwordsMatch("password", "confirmPassword"));

      if(this.parentForm) {
         this.parentForm.setControl("action", this.form);
      }

      if(this.isIE) {
         this.form.addControl("deliveryMessage", new UntypedFormControl(this.deliveryMessage));
      }

      if(this.action.notificationEnabled) {
         this.updateNotificationStatus(true);
      }
      else {
         this.updateNotificationStatus(false);
      }

      if(this.model.emailDeliveryEnabled) {
         if(this.action.deliverEmailsEnabled) {
            this.updateEmailDeliveryStatus(true);
         }
         else {
            this.updateEmailDeliveryStatus(false);
         }
      }

      if(this.action.bundledAsZip || this.action.format === "HTML_BUNDLE" ||
         this.action.format === "HTML_BUNDLE_NO_PAGINATION")
      {
         this.updateBundledStatus(true);
      }
      else {
         this.updateBundledStatus(false);
      }

      if(this.model.emailDeliveryEnabled) {
         this.formSubscriptions.add(this.form.get("cc").valueChanges.subscribe(value =>
            this.action.ccAddress = value));
         this.formSubscriptions.add(this.form.get("bcc").valueChanges.subscribe(value =>
            this.action.bccAddress = value));
         this.formSubscriptions.add(this.form.get("to").valueChanges.subscribe(value => {
            this.updateDeliveryEmails(value);
         }));
      }
   }

   unsubscribeForm(): void {
      if(!!this.formSubscriptions) {
         this.formSubscriptions.unsubscribe();
         this.formSubscriptions = new Subscription();
      }
   }

   updateNotificationEmails(value: any): void {
      this.action.notifications = this.getEmailValue(value);

      if(!!this.action.notifications) {
         this.action.notifications = this.action.notifications.replace(/\s+/g, "");
      }
   }

   updateFromEmails(value: any): void {
      this.fromEmail = this.getEmailValue(value);
   }

   updateDeliveryEmails(value: any): void {
      this.action.to = this.getEmailValue(value);
   }

   updateDeliverMessage(value: any): void {
      this.deliveryMessage = value;
   }

   private getEmailUsers(): string[] {
      let identities: string[] = [];

      if(this.emailUsers) {
         identities = identities.concat(this.usersService.populateEmailUserAliases(this.emailUsers, this.userAliases));

      }

      if(this.groups) {
         identities = identities.concat(this.groups.map(g => g.name + Tool.GROUP_SUFFIX));
      }

      return identities;
   }

   private getEmailValue(value: any): string {
      if(!!value && value.hasOwnProperty("value")) {
         return value.value;
      }

      if(!!value) {
         return value + "";
      }

      return null;
   }

   public updateNotificationStatus(value: boolean): void {
      this.action.notificationEnabled = value;

      if(value) {
         this.form.controls["notification"].setValidators([
            FormValidators.emailListRequired(),
            FormValidators.emailList(",;", true, false, this.getEmailUsers(), true),
            FormValidators.duplicateTokens()
         ]);
         this.form.controls["notification"].updateValueAndValidity();
      }
      else {
         this.form.controls["notification"].setValidators(null);
         this.form.controls["notification"].updateValueAndValidity();
      }
   }

   public updateEmailDeliveryStatus(value: boolean): void {
      this.action.deliverEmailsEnabled = value;

      if(value) {
         if(this.model && !this.model.fromEmailEnabled) {
            this.form.controls["from"].disable();
         }
         else {
            this.form.controls["from"].enable();
         }

         this.form.controls["from"].updateValueAndValidity();

         this.form.controls["to"]?.setValidators([
            FormValidators.emailListRequired(),
            FormValidators.emailList(",;", true, false, this.getEmailUsers(), true),
            FormValidators.duplicateTokens()
         ]);
         this.form.controls["to"]?.updateValueAndValidity();

         if(this.isIE) {
            this.form.controls["deliveryMessage"].updateValueAndValidity();
         }

         if(!!!this.action.format && !Tool.isEmpty(this.mailFormats)) {
            this.updateFormatValue(this.mailFormats[0].type);
         }
      }
      else {
         this.form.controls["from"].setValidators(null);
         this.form.controls["from"].updateValueAndValidity();
         this.form.controls["to"]?.setValidators(null);
         this.form.controls["to"]?.updateValueAndValidity();

         if(this.isIE) {
            this.form.controls["deliveryMessage"].updateValueAndValidity();
         }
      }
   }

   public updateBundledValue(value: boolean): void {
      this.action.bundledAsZip = value;
      this.updateBundledStatus(value);
   }

   public updateUseCredentialValue(value: boolean): void {
      this.action.useCredential = value;
      this.updateBundledStatus(value);
   }

   public updateBundledStatus(value: boolean): void {
      this.form.controls["secretId"].reset({
         value: this.action.secretId,
         disabled: this.isPasswordDisable()
      });

      this.form.controls["password"].reset({
         value: this.action.password,
         disabled: this.isPasswordDisable()
      });

      this.form.controls["confirmPassword"].reset({
         value: this.confirmPassword,
         disabled: this.isPasswordDisable()
      });

      if(value && this.action.deliverEmailsEnabled) {
         this.form.controls["secretId"].updateValueAndValidity();
         this.form.controls["password"].updateValueAndValidity();
         this.form.controls["confirmPassword"].updateValueAndValidity();
         this.form.validator = this.passwordsMatch("password", "confirmPassword");
         this.form.updateValueAndValidity();
      }
      else {
         this.form.controls["secretId"].setValidators(null);
         this.form.controls["secretId"].updateValueAndValidity();
         this.form.controls["password"].setValidators(null);
         this.form.controls["password"].updateValueAndValidity();
         this.form.controls["confirmPassword"].setValidators(null);
         this.form.controls["confirmPassword"].updateValueAndValidity();
         this.form.validator = null;
         this.form.updateValueAndValidity();
      }
   }

   public updateFormatValue(value: string): void {
      this.action.format = value;

      if(this.bundledDisabled) {
         this.action.bundledAsZip = false;
         this.updateBundledStatus(true);
      }
      else {
         this.updateBundledStatus(this.action.bundledAsZip);
      }

      if(this.action.format === "CSV" && this.action.actionType === "ViewsheetAction") {
         this.action.bundledAsZip = true;
         this.updateBundledStatus(true);
      }

      if(this.action.format === "CSV" && !this.action.csvExportModel) {
         this.action.csvExportModel = new CSVConfigModel();
      }

      if(this.action.format !== "Excel") {
         this.action.emailOnlyDataComponents = false;
      }

      if(!this.model.expandEnabled) {
         this.action.emailMatchLayout = true;
      }
   }

   get formatValue(): string {
      if(!this.action.format && this.model) {
         return this.mailFormats[0].type;
      }

      return this.action.format;
   }

   public passwordsMatch(password: string, confirmPassword: string): any {
      return (group: UntypedFormGroup): ValidationErrors => {
         let passwordVal: any = group.controls[password].value;
         let confirmPasswordVal: any = group.controls[confirmPassword].value;

         if(!passwordVal && !confirmPasswordVal) {
            return null;
         }

         if(passwordVal === confirmPasswordVal) {
            return null;
         }
         else {
            return {passwordsDoNotMatch: true};
         }
      };
   }

   public searchNotify = (text: Observable<string>) => {
      return this.getAutoCompleteList(text);
   };

   public searchFrom = (text: Observable<string>) => {
      return this.getAutoCompleteList(text);
   };

   public searchTo = (text: Observable<string>) => {
      return this.getAutoCompleteList(text);
   };

   public formatEmails = (result: {value: string}) => result.value;

   private getAutoCompleteList(text: Observable<string>): Observable<any[]> {
      return text.pipe(
         debounceTime(200),
         map((term: string) => {
            const comma = term.lastIndexOf(",");
            let check = term.substring(term.lastIndexOf(",") + 1, term.length);
            const prefix = term.substring(0, comma + 1);

            if(check && this.autoCompleteModel) {
               check = check.toLowerCase();
               return this.autoCompleteModel
                  .filter(v => v.toLowerCase().indexOf(check) >= 0)
                  .map(v => ({label: v, value: prefix + v}))
                  .slice(0, 10);
            }

            return [];
         })
      );
   }

   get dataSizeOptionVisible(): boolean {
      return this.isDashboard && this.action.format !== "HTML" && this.action.format !== "CSV" &&
         (this.action.format !== "PDF" || !this.hasPrintLayout);
   }

   private isHtmlFormat(format: string) {
      return format === FileTypes.HTML + "";
   }

   isCSVFormat(format: string): boolean {
      return this.isVSCSVFormat(format) || format === "3";
   }

   isVSCSVFormat(format: string): boolean {
      return this.isDashboard && format === "6";
   }

   get isSelfUser(): Promise<boolean> {
      let promise: Promise<boolean> = Promise.resolve(this._isSelfUser)

      return promise.then((self) => {
         if(self == undefined) {
            return this.http.get<boolean>("../api/portal/schedule/isSelfOrgUser")
               .pipe(
                  tap(self => this._isSelfUser = self),
               ).toPromise();
         }
         else {
            return self;
         }
      });
   }

   get missingParameters(): string {
      if(!this.requiredParameters || this.requiredParameters.length === 0) {
         return null;
      }

      if(!this.parameters || this.parameters.length === 0) {
         return this.requiredParameters.join(", ");
      }

      let missingParameters0 = [];

      for(let i = 0; i < this.requiredParameters.length; i++) {
         const found = this.parameters.some((p) => p.name === this.requiredParameters[i]);

         if(!found) {
            missingParameters0.push(this.requiredParameters[i]);
         }
      }

      return missingParameters0.join(", ");
   }
}
