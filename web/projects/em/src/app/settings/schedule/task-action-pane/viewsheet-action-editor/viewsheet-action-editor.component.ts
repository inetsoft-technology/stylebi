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
import { FlatTreeControl } from "@angular/cdk/tree";
import { HttpErrorResponse } from "@angular/common/http";
import { AfterContentChecked, Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import {
   UntypedFormControl,
   UntypedFormGroup,
   ValidationErrors,
   ValidatorFn,
   Validators
} from "@angular/forms";
import { MatSnackBar } from "@angular/material/snack-bar";
import { BehaviorSubject, Observable, of, throwError } from "rxjs";
import { catchError, map, tap } from "rxjs/operators";
import { AssetConstants } from "../../../../../../../portal/src/app/common/data/asset-constants";
import { ServerPathInfoModel } from "../../../../../../../portal/src/app/vsobjects/model/server-path-info-model";
import { VSBookmarkInfoModel } from "../../../../../../../portal/src/app/vsobjects/model/vs-bookmark-info-model";
import { ExpandStringDirective } from "../../../../../../../portal/src/app/widget/expand-string/expand-string.directive";
import { AssetEntry, createAssetEntry } from "../../../../../../../shared/data/asset-entry";
import { AssetType } from "../../../../../../../shared/data/asset-type";
import { CSVConfigModel } from "../../../../../../../shared/schedule/model/csv-config-model";
import { GeneralActionModel } from "../../../../../../../shared/schedule/model/general-action-model";
import { TaskActionPaneModel } from "../../../../../../../shared/schedule/model/task-action-pane-model";
import { Tool } from "../../../../../../../shared/util/tool";
import { ScheduleUsersService } from "../../../../../../../shared/schedule/schedule-users.service";
import { FlatTreeDataSource } from "../../../../common/util/tree/flat-tree-data-source";
import { FlatTreeNode, TreeDataModel } from "../../../../common/util/tree/flat-tree-model";
import { IdentityId } from "../../../security/users/identity-id";
import { BookmarkListModel } from "../../model/bookmark-list-model";
import { HighlightListModel, HighlightModel } from "../../model/highlight-list-model";
import { ReportOptions } from "../../model/reports-options";
import { ViewsheetParametersModel } from "../../model/viewsheet-parameters-model";
import { ViewsheetTreeListModel, ViewsheetTreeModel } from "../../model/viewsheet-tree-list-model";
import { Parameters } from "../../parameter-table/parameter-table.component";
import { DeliveryEmails } from "../delivery-emails/delivery-emails.component";
import { EmailListService } from "../email-list.service";
import { NotificationEmails } from "../notification-emails/notification-emails.component";
import { ScheduleAlerts } from "../schedule-alerts/schedule-alerts.component";
import { ServerSave, ServerSaveFile } from "../server-save/server-save.component";
import { TaskActionChanges } from "../task-action-pane.component";
import { ViewsheetActionService } from "../viewsheet-action.service";

export class ViewsheetFlatNode extends FlatTreeNode<ViewsheetTreeModel> {
   constructor(public expandable: boolean, public id: string, public name: string,
               public level: number, public data: ViewsheetTreeModel, public folder: boolean)
   {
      super(name, level, expandable, data, false);
   }
}

export class ViewsheetDataSource extends FlatTreeDataSource<ViewsheetFlatNode, ViewsheetTreeModel> {
   loading: boolean = false;

   constructor(treeControl: FlatTreeControl<ViewsheetFlatNode>,
               private viewsheetService: ViewsheetActionService, private snackBar: MatSnackBar)
   {
      super(treeControl);

      this.loading = true;
      this.viewsheetService.getFolders()
          .pipe(
             catchError(error => {
                this.loading = false;
                return this.handleFoldersError(error);
             }),
             map(model => {
                this.loading = false;
                return this.transform(model, 0);
             })
          )
          .subscribe(nodes => this.setData(nodes));
   }

   private setData(nodes: ViewsheetFlatNode[]){
      this.data = nodes.sort((a, b) => {
         const aFolder = a.folder ? 1 : 0;
         const bFolder = b.folder ? 1 : 0;
         return bFolder - aFolder;
      });
   }

   protected getChildren(node: ViewsheetFlatNode): Observable<TreeDataModel<ViewsheetTreeModel>> {
      return of({nodes: node.data.children});
   }

   protected transform(model: TreeDataModel<ViewsheetTreeModel>,
                       level: number): ViewsheetFlatNode[]
   {
      return model.nodes.map( (node) =>
         new ViewsheetFlatNode(node.folder, node.id, node.label, level, node, node.folder));
   }

   private handleFoldersError(error: HttpErrorResponse): Observable<ViewsheetTreeListModel> {
      this.snackBar.open(error.message, null, {
         duration: Tool.SNACKBAR_DURATION,
      });
      console.error("Failed to list folders: ", error);
      return throwError(error);
   }
}

@Component({
   selector: "em-viewsheet-action-editor",
   templateUrl: "./viewsheet-action-editor.component.html",
   styleUrls: ["./viewsheet-action-editor.component.scss"]
})
export class ViewsheetActionEditorComponent implements OnInit, AfterContentChecked {
   @Output() modelChanged = new EventEmitter<TaskActionChanges>();

   @Input()
   get model(): TaskActionPaneModel {
      return this._taskModel;
   }

   set model(value: TaskActionPaneModel) {
      this._taskModel = Object.assign({}, value);
   }

   @Input()
   get actionModel(): GeneralActionModel {
      return this._actionModel;
   }

   set actionModel(value: GeneralActionModel) {
      this._actionModel = Object.assign({}, value);
      this.selectedBookmarks = this._actionModel.bookmarks;
      this.selectedViewsheet = this._actionModel.sheet;

      if(this.selectedBookmarks == null) {
         this.selectedBookmarks = [];

         if(this.bookmarks != null && this.bookmarks.value.length > 0) {
            this.selectedBookmarks = [this.bookmarks.value[0]];
         }
      }

      if(this.bookmarksDB.value.length == 0) {
         this.bookmarksDB.next(this.selectedBookmarks);
      }

      if(this.form) {
         this.form.get("dashboardSelected").setValue(this.selectedViewsheet);
      }

      if(!this._actionModel.htmlMessage) {
         this._actionModel.htmlMessage = true;

         if(this._actionModel.message) {
            this._actionModel.message = this._actionModel.message.replace(/\r?\n/, "<br/>");
         }
      }
   }

   get modelValid(): boolean {
      return !!this.selectedViewsheet && !this.duplicateBookmark && this.notificationEmailsValid && this.deliveryEmailsValid &&
         this.serverSaveValid && this.bookmarksExist;
   }

   get bookmarksExist(): boolean {
      return !this.selectedBookmarks.filter(value => !!value)
         .some(value => value.owner == null && value.name == null);
   }

   get selectedViewsheetName(): string {
      if(!this.selectedViewsheet) {
         return "";
      }

      let entry: AssetEntry = createAssetEntry(this.selectedViewsheet);

      return "'" + entry.path + "'";
   }

   set selectedViewsheet(val: string) {
      if(this._selectedViewsheet === val) {
         this.updateBookmark();
         this.bookmarksDB.next([]);
         return;
      }

      let changeSheet = !!this._selectedViewsheet;
      this._selectedViewsheet = val;
      this.bookmarks.next([]);
      this.bookmarksDB.next([]);
      this.highlights.next([]);
      this.optionalParameters.next([]);
      this.tableDataAssemblies.next([]);
      this.hasPrintLayout.next(false);
      this.hasHighlights = false;

      if(val) {
         this.viewsheetService.getHighlights(val)
            .pipe(
               catchError(error => this.handleHighlightError(error)),
               tap(model => this.hasHighlights = model.highlights.length > 0),
               map(model => model.highlights)
            )
            .subscribe(hl => this.highlights.next(hl));
         this.viewsheetService.getParameters(val)
            .pipe(
               catchError(error => this.handleParameterError(error))
            )
            .subscribe(model => this.optionalParameters.next(model.parameters));

         this.viewsheetService.getViewsheets()
            .pipe(
               catchError(error => this.handleParameterError(error))
            )
            .subscribe(model => {
               this.model.dashboardMap = model;
               this.init();
            });

         this.viewsheetService.getTableDataAssemblies(val)
            .pipe(
               catchError(error => this.handleTableDataError(error))
            )
            .subscribe(assemblies => this.tableDataAssemblies.next(assemblies));

         this.viewsheetService.getBookmarks(val)
            .pipe(
               catchError(error => this.handleBookmarkError(error)),
               map(model => model.bookmarks)
            )
            .subscribe(b => {
               this.bookmarks.next(b);
               this.updateBookmark(changeSheet);
            });

         this.viewsheetService.hasPrintLayout(val)
            .subscribe(hasLayout => this.hasPrintLayout.next(hasLayout));
         this.updateTreeState();
      }
   }

   get selectedViewsheet(): string {
      return this._selectedViewsheet;
   }

   get duplicateBookmark(): VSBookmarkInfoModel {
      return this.selectedBookmarks.find((bookmark1, i) =>
         this.selectedBookmarks.findIndex((bookmark2) =>
            bookmark1 === bookmark2 ) !== i);
   }

   treeControl: FlatTreeControl<ViewsheetFlatNode>;
   dataSource: ViewsheetDataSource;
   bookmarks = new BehaviorSubject<VSBookmarkInfoModel[]>([]);
   selectedBookmark: VSBookmarkInfoModel = null;
   selectedBookmarks: VSBookmarkInfoModel[] = [];
   highlights = new BehaviorSubject<HighlightModel[]>([]);
   hasHighlights = false;
   optionalParameters = new BehaviorSubject<string[]>([]);
   tableDataAssemblies = new BehaviorSubject<string[]>([]);
   hasPrintLayout = new BehaviorSubject<boolean>(false);
   emailBrowserEnabled = false;
   form: UntypedFormGroup;
   emailUsers: IdentityId[];
   groups: IdentityId[] = [];
   columnsToDisplay = ["bookmark", "actions"];
   bookmarksDB = new BehaviorSubject<VSBookmarkInfoModel[]>([]);

   private _taskModel: TaskActionPaneModel;
   private _actionModel: GeneralActionModel;
   private _selectedViewsheet: string = null;
   private notificationEmailsValid = true;
   private deliveryEmailsValid = true;
   private serverSaveValid = true;

   type: string = "viewsheet";
   private _loaded: boolean = false;

   constructor(private viewsheetService: ViewsheetActionService, private snackBar: MatSnackBar,
               private emailListService: EmailListService,
               private usersService: ScheduleUsersService)
   {
      this.treeControl = new FlatTreeControl<ViewsheetFlatNode>(this.getLevel, this.isExpandable);
      this.dataSource =
         new ViewsheetDataSource(this.treeControl, this.viewsheetService, this.snackBar);

      this.emailListService.isEmailBrowserEnabled()
         .subscribe(value => this.emailBrowserEnabled = value);
      this.usersService.getEmailUsers().subscribe(value => this.emailUsers = value);
      this.usersService.getEmailGroups().subscribe(value => this.groups = value);
   }

   ngOnInit() {
      this.init();
   }

   ngAfterContentChecked() {
      if (this.treeControl.dataNodes && !this._loaded && this.selectedViewsheet) {
         this.updateTreeState();
         this._loaded = true;
      }
   }

   init(): void {
      this.form = new UntypedFormGroup({
         dashboardSelected: new UntypedFormControl(this.selectedViewsheet, [Validators.required,
            this.notExists(this.model.dashboardMap)])
      });
   }

   public notExists(dashboardMap: {[id: string]: string}): ValidatorFn {
      return (control: UntypedFormControl): ValidationErrors => {
         const value =  control.value;

         if(!value || !dashboardMap) {
            return null;
         }

         return !(dashboardMap[value]) ? {notExists: true} : null;
      };
   }

   onChange(value: any): void {
      this.selectedViewsheet = value;
      this.fireModelChanged();
   }

   onNotificationsChanged(change: NotificationEmails) {
      this.notificationEmailsValid = change.valid;
      this.actionModel.notificationEnabled = change.enabled;
      this.actionModel.notifications = change.emails;
      this.actionModel.notifyIfFailed = change.notifyIfFailed;
      this.actionModel.link = change.notifyLink;
      this.fireModelChanged();
   }

   onDeliveryChanged(change: DeliveryEmails) {
      this.deliveryEmailsValid = change.valid;
      this.actionModel.deliverEmailsEnabled = change.enabled;
      this.actionModel.fromEmail = change.sender;
      this.actionModel.to = change.recipients;
      this.actionModel.subject = change.subject;
      this.actionModel.bundledAsZip = change.bundledAsZip;
      this.actionModel.useCredential = change.useCredential;
      this.actionModel.secretId = change.secretId;
      this.actionModel.password = change.zipPassword;
      this.actionModel.attachmentName = change.attachmentName;
      this.actionModel.format = change.format;
      this.actionModel.emailMatchLayout = change.emailMatchLayout;
      this.actionModel.emailExpandSelections = change.emailExpandSelections;
      this.actionModel.emailOnlyDataComponents = change.emailOnlyDataComponents;
      this.actionModel.message = change.message;
      this.actionModel.htmlMessage = change.htmlMessage;
      this.actionModel.deliverLink = change.deliverLink;
      this.actionModel.ccAddress = change.ccAddress;
      this.actionModel.bccAddress = change.bccAddress;
      this.actionModel.exportAllTabbedTables = change.exportAllTabbedTables;

      if(this.actionModel.format == ReportOptions.CSV && !this.actionModel.csvExportModel) {
         this.actionModel.csvExportModel = new CSVConfigModel();
      }

      this.fireModelChanged();
   }

   onServerSaveChanged(change: ServerSave) {
      this.serverSaveValid = change.valid;
      this.actionModel.saveToServerEnabled = change.enabled;
      this.actionModel.saveMatchLayout = change.matchLayout;
      this.actionModel.saveExpandSelections = change.expandSelections;
      this.actionModel.saveOnlyDataComponents = change.saveOnlyDataComponents;
      this.actionModel.saveFormats = change.files.map(f => f.format);
      this.actionModel.filePaths = change.files.map(f => f.path);
      this.actionModel.serverFilePaths = change.files.map(this.convertToPathModel);
      this.actionModel.saveExportAllTabbedTables = change.saveExportAllTabbedTables;

      // find csv format
      let found = this.actionModel.saveFormats.find(value => value === "6");

      if(found && !this.actionModel.csvSaveModel) {
         this.actionModel.csvSaveModel = new CSVConfigModel();
      }

      this.fireModelChanged();
   }

   onParametersChanged(change: Parameters): void {
      this.actionModel.parameters = change.parameters;
      this.fireModelChanged();
   }

   onAlertsChanged(change: ScheduleAlerts): void {
      this.actionModel.highlightsSelected = change.enabled;
      this.actionModel.highlightAssemblies = change.highlights.map(hl => hl.element);
      this.actionModel.highlightNames = change.highlights.map(hl => hl.highlight);
      this.fireModelChanged();
   }

   fireModelChanged() {
      this.actionModel.sheet = this.selectedViewsheet;
      this.actionModel.bookmarks = this.selectedBookmarks;
      this.modelChanged.emit({
         valid: this.modelValid,
         model: this.actionModel
      });
   }

   private getLevel = (node: ViewsheetFlatNode) => node.level;
   private isExpandable = (node: ViewsheetFlatNode) => node.expandable;
   hasChild = (n: number, nodeData: ViewsheetFlatNode) => nodeData.expandable;
   compareBookmark =
      (o1: VSBookmarkInfoModel, o2: VSBookmarkInfoModel) => o1 && o2 && o1.name === o2.name && o1.owner === o2.owner;

   private handleHighlightError(error: HttpErrorResponse): Observable<HighlightListModel> {
      this.snackBar.open("_#(js:em.schedule.vs.listHighlightsError)", null, {
         duration: Tool.SNACKBAR_DURATION,
      });
      console.error("Failed to list viewsheet highlights: ", error);
      return throwError(error);
   }

   private handleParameterError(error: HttpErrorResponse): Observable<ViewsheetParametersModel> {
      this.snackBar.open(error.error.message, null, {
         duration: Tool.SNACKBAR_DURATION,
      });
      console.error("Failed to list viewsheet parameters: ", error);
      return throwError(error);
   }

   private handleTableDataError(error: HttpErrorResponse): Observable<string[]> {
      this.snackBar.open(error.error.message, null, {
         duration: Tool.SNACKBAR_DURATION,
      });
      console.error("Failed to list viewsheet table data assemblies: ", error);
      return throwError(error);
   }

   private handleBookmarkError(error: HttpErrorResponse): Observable<BookmarkListModel> {
      this.snackBar.open(error.error.message, null, {
         duration: Tool.SNACKBAR_DURATION,
      });
      console.error("Failed to list viewsheet bookmarks: ", error);
      return throwError(error);
   }

   private isPrivateVS(id: string): boolean {
      let entry: AssetEntry = createAssetEntry(id);

      return entry != null && entry.scope == AssetConstants.USER_SCOPE &&
         entry.type != AssetType.REPOSITORY_FOLDER;
   }

   notExistsMessage() {
      return ExpandStringDirective.expandString(
         "_#(js:em.scheduleRepletAction.dashboardNotExists)", [this.selectedViewsheetName]);
   }

   bookmarkNotExistsMessage(bk) {
      return ExpandStringDirective.expandString(
         "_#(js:em.scheduleRepletAction.bookmarkNotExists)", [bk]);
   }

   private updateTreeState(): void {
      if(this.isPrivateVS(this.selectedViewsheet)) {
         let folder = this.treeControl.dataNodes?.find(node => node.id.startsWith("4^4097^") && node.id.indexOf("^" + Tool.MY_REPORTS) > 0);

         if(folder && folder.expandable) {
            this.treeControl.expand(folder);
         }

         this.expandPrivatePath();

         return;
      }

      let idx = this.selectedViewsheet.lastIndexOf("^");
      let orgId: string = this.selectedViewsheet.substring(idx);
      let selectedViewsheet: string = this.selectedViewsheet.substring(0, idx);
      let parts: string[] = [];

      parts = parts.concat(selectedViewsheet.substring(
          selectedViewsheet.lastIndexOf("^") + 1).split("/"));

      for(let i = 0; i < parts.length; i++) {
         let path = parts.slice(0, i + 1).join("/") + orgId;
         let folder = this.treeControl.dataNodes?.find(node => node.id === "1^4097^__NULL__^" + path); // if no match,
         // then it doesn't exist or it has an identifier (asset)

         if(folder && folder.expandable) {
            this.treeControl.expand(folder);
         }
      }
   }

   private expandPrivatePath(): void {
      let parts: string[] = this.selectedViewsheet.split("^");

      if(parts == null || parts.length < 5) {
         return;
      }

      let orgName = "^" + parts[2] + "^";
      let vsPath = parts[3];
      let orgId = "^" + parts[4];

      if(vsPath.indexOf("/") < 0) {
         return;
      }

      let paths = vsPath.split("/");
      let path = "";

      for(let i = 0; i < paths.length; i++) {
         path += (i == 0 ? paths[i] : "/" + paths[i]);
         let folder = this.treeControl.dataNodes?.find(node =>
            node.id === "4^4097" + orgName + path + orgId);

         if(folder && folder.expandable) {
            this.treeControl.expand(folder);
         }
      }
   }

   private updateBookmark(changeSheet: boolean = false): void {
      let vsBookmarks = this.bookmarks.value;

      if(!vsBookmarks || vsBookmarks.length == 0) {
         return;
      }

      if(!!this.selectedBookmarks) {
         for(let i = 0; i < this.selectedBookmarks.length; i ++) {
            if(this.selectedBookmarks[i]) {
               let nbk = vsBookmarks.find((j) =>
                  j.name === this.selectedBookmarks[i].name &&
                  j.owner?.name === this.selectedBookmarks[i].owner?.name &&
                  j.owner?.orgID === this.selectedBookmarks[i].owner?.orgID);

               if(nbk != null) {
                  this.selectedBookmarks[i] = nbk;
               }
               else {
                  if(changeSheet) {
                     this.selectedBookmarks[i] = null;
                  }
                  else {
                     this.selectedBookmarks[i].name = null;
                     this.selectedBookmarks[i].owner = null;
                  }
               }
            }
         }

         this.selectedBookmarks = this.selectedBookmarks.filter(b => !!b);
         this.bookmarksDB.next(this.selectedBookmarks);
      }

      if(!this.selectedBookmarks || this.selectedBookmarks.length == 0) {
         let home = vsBookmarks.find(i => i.name === "(Home)");
         this.selectedBookmarks = !!home ? [home] : [];
         this.bookmarksDB.next(this.selectedBookmarks);
         this.fireModelChanged();
      }
   }

   addBookmark() {
      let home = this.bookmarks.value.find(i => i.name === "(Home)");

      if(!!home) {
         this.selectedBookmarks.push(this.bookmarks.value[0]);
         this.bookmarksDB.next(this.selectedBookmarks);
         this.fireModelChanged();
      }
   }

   removeBookmark(bookmark: VSBookmarkInfoModel) {
      const index = this.selectedBookmarks.findIndex(
         b => b.name == bookmark.name && b.type == bookmark.type &&
            b.owner?.name == bookmark.owner?.name && b.owner?.orgID == bookmark.owner?.orgID);

      if(index >= 0) {
         this.selectedBookmarks.splice(index, 1);
         this.bookmarksDB.next(this.selectedBookmarks);
         this.fireModelChanged();
      }
   }

   private convertToPathModel(change: ServerSaveFile) {
      let info: ServerPathInfoModel = {
         path: change.path,
         useCredential: !!change.useCredential,
         secretId: change.secretId,
         username: change.username,
         password: change.password,
         ftp: !!change.ftp,
         oldFormat: change.oldFormat,
      };

      return info;
   }
}
