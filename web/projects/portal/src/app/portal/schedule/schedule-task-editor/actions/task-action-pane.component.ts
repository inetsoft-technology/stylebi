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
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { createAssetEntry } from "../../../../../../../shared/data/asset-entry";
import { RepositoryEntry } from "../../../../../../../shared/data/repository-entry";
import { GeneralActionModel } from "../../../../../../../shared/schedule/model/general-action-model";
import { ScheduleActionModel } from "../../../../../../../shared/schedule/model/schedule-action-model";
import { TaskActionPaneModel } from "../../../../../../../shared/schedule/model/task-action-pane-model";
import { Tool } from "../../../../../../../shared/util/tool";
import { AssetEntryHelper } from "../../../../common/data/asset-entry-helper";
import { ComponentTool } from "../../../../common/util/component-tool";
import { LocalStorage } from "../../../../common/util/local-storage.util";
import { VSBookmarkInfoModel } from "../../../../vsobjects/model/vs-bookmark-info-model";
import { PortalModelService } from "../../../services/portal-model.service";
import { ScheduleAlertModel } from "../../model/schedule-alert-model";
import { SelectDashboardDialog } from "../select-dashboard-dialog/select-dashboard-dialog.component";
import { ScheduleTaskDialogModel } from "../../../../../../../shared/schedule/model/schedule-task-dialog-model";

const ACTION_URI = "../api/portal/schedule/task/action";
const EMAIL_AUTO_COMPLETE_KEY = LocalStorage.MAIL_HISTORY_KEY;

@Component({
   selector: "task-action-pane",
   templateUrl: "task-action-pane.component.html",
   styleUrls: ["task-action-pane.component.scss"]
})
export class TaskActionPane implements OnInit {
   @Input() executeAsGroup: boolean;
   @Input() taskOwner: string;
   @Input() multiCondition: boolean = true;
   @Input() newTask: boolean;
   @Input() saveTask: () => Promise<any>;
   @Output() cancelTask = new EventEmitter();
   @Input() set model(value: TaskActionPaneModel) {
      this._model = value;
      this.updateValues();
   }

   get model(): TaskActionPaneModel {
      return this._model;
   }

   get action(): ScheduleActionModel {
      let index = this.actionIndex == -1 ? this.model.actions.length - 1 : this.actionIndex;

      return this.model.actions && this.model.actions.length > index ?
         this.model.actions[index] : null;
   }

   set action(value: ScheduleActionModel) {
      let index = this.actionIndex == -1 ? this.model.actions.length - 1 : this.actionIndex;

      if(this.model.actions) {
         for(let i = this.model.actions.length; i <= index; i++) {
            this.model.actions.push(null);
         }

         this.model.actions[index] = value;
      }
      else {
         this.model.actions = [];

         for(let i = 0; i < index - 1; i++) {
            this.model.actions.push(null);
         }

         this.model.actions.push(value);
      }
   }

   get generalActionModel(): GeneralActionModel {
      return this.action as GeneralActionModel;
   }

   get actionNames(): string[] {
      return !this.model.actions ? [] : this.model.actions.map(a => a.label);
   }

   @Input() taskName: string;
   @Input() oldTaskName: string;
   @Input() parentForm: UntypedFormGroup;
   @Output() loaded: EventEmitter<ScheduleTaskDialogModel> = new EventEmitter<ScheduleTaskDialogModel>();
   @Output() updateTaskName: EventEmitter<string> = new EventEmitter<string>();
   @Output() closeEditor = new EventEmitter<TaskActionPaneModel>();
   private _model: TaskActionPaneModel;
   options: string[] = [
      "ViewsheetAction",
   ];
   optionLabels: string[] = [
      "_#(js:Dashboard)",
   ];
   optionDisabledList: boolean[] = [
      false
   ];

   bookmarks: VSBookmarkInfoModel[] = [];
   highlights: ScheduleAlertModel[] = [];
   hasPrintLayout: boolean = false;
   parameters: string[] = [];
   optionalParameters: string[] = [];
   containsSheet: boolean;

   listView: boolean = false;
   selectedActions: number[] = [];
   actionIndex: number = 0;

   form: UntypedFormGroup;
   autoCompleteModel: string[];

   // keep track of values entered for all actions types
   viewsheetAction: GeneralActionModel;
   dirtyFormByTs: boolean = false;
   tableDataAssemblies: string[] = [];

   bookmarkList: string[] = [];
   selectedBookmark: VSBookmarkInfoModel;

   get selectSheetError(): boolean {
      return (!!this.form.controls["dashboard"] && !!this.form.controls["dashboard"].errors);
   }

   constructor(private http: HttpClient,
      private modalService: NgbModal,
      private portalModelService: PortalModelService) {
   }

   ngOnInit() {
      this.autoCompleteModel = this.getAutoCompleteLists();
      this.getBookmarks();

      // get the highlights and parameters
      if(this.action.actionType === "ViewsheetAction") {
         this.getHighlights();
         this.getParameters();
         this.getPrintLayout();
         this.getTableDataAssemblies();
      }
      else {
         // Model will not update if no http call is made
         this.http.get("").subscribe().unsubscribe();
      }

      if(this.model.userDefinedClassLabels.length == 0) {
         this.optionLabels.splice(3, 1);
      }
   }

   private setSelectedOption(): void {
      const type = this.action.actionType;
      const index: number = this.options.indexOf(type);

      this.checkSelectedOption(index);
      this.initForm();
   }

   private checkSelectedOption(selectedIndex: number): void {
      this.optionDisabledList = [
         !this.model.viewsheetEnabled,
         false
      ];

      // Check if the currently selected option is allowed
      if(this.optionDisabledList[selectedIndex]) {
         const index = this.optionDisabledList.findIndex((opt) => !opt);
         this.changeActionType(index);
      }
   }

   isTabSelected(index: number): boolean {
      return this.options[index] == this.action.actionType;
   }

   public changeActionType(index: number): void {
      const label: string = this.optionLabels[index];
      const type = this.options[index];
      this.optionalParameters = [];

      if(this.action.actionType != type || this.actionIndex == -1) {
         let isDashboard = true;

         if(type === "ViewsheetAction") {
            this.action = this.viewsheetAction;
         }

         this.action.label = "_#(js:New Action)";
         this.initForm();
         this.getParameters();
      }
   }

   private changeViewsheet(sheet: string): void {
      this.clearHighlightConditions(sheet);
      (<GeneralActionModel>this.action).sheet = sheet;
      this.dirtyFormByTs = true;

      if(!sheet) {
         return;
      }

      this.getBookmarks(sheet);
      this.getPrintLayout(sheet);
      this.getHighlights();
      this.getParameters();
      this.getTableDataAssemblies();
   }

   private getPrintLayout(sheet: string = null) {
      if(!this.action) {
         return;
      }
      else if(!sheet) {
         sheet = (<GeneralActionModel>this.action).sheet;
      }

      if(!!sheet && this.action.actionType === "ViewsheetAction") {
         const params = new HttpParams().set("id", Tool.byteEncode(sheet));
         this.http.get<boolean>(ACTION_URI + "/hasPrintLayout", { params })
            .subscribe((result: boolean) => {
               this.hasPrintLayout = result;
            },
               (error: string) => {
                  // Error
               });
      }
   }

   private clearHighlightConditions(sheet: string): void {
      const action = <GeneralActionModel>this.action;

      if(action.sheet != sheet) {
         action.highlightAssemblies = [];
         action.highlightNames = [];
      }
   }

   public getBookmarks(sheet: string = null): void {
      if(!this.action) {
         return;
      }
      else if(!sheet) {
         sheet = (<GeneralActionModel>this.action).sheet;
      }

      if(!!sheet && this.action.actionType === "ViewsheetAction") {
         const params = new HttpParams().set("id", Tool.byteEncode(sheet));
         this.http.get<VSBookmarkInfoModel[]>(ACTION_URI + "/bookmarks", { params })
            .subscribe((bookmarks: VSBookmarkInfoModel[]) => {
               this.bookmarks = bookmarks;
               this.selectedBookmark = bookmarks.find(i => i.name === "(Home)");

               if(!!this.generalActionModel.bookmarks) {
                  this.generalActionModel.bookmarks = this.generalActionModel.bookmarks
                     .filter(b => !!bookmarks.find((i) =>
                        i.name === b.name &&
                        i.owner.name === b.owner.name &&
                        i.owner.orgID === b.owner.orgID));
               }

               if(!this.generalActionModel.bookmarks ||
                  this.generalActionModel.bookmarks.length == 0) {
                  let home = bookmarks.find(i => i.name === "(Home)");
                  this.generalActionModel.bookmarks = [];

                  if(!!home) {
                     this.generalActionModel.bookmarks.push(home);
                  }
               }

               this.bookmarkList = this.generalActionModel.bookmarks.map(b => b.label);
            },
               (error: string) => {
                  // Error
               });
      }
   }

   private getHighlights(): void {
      const sheet: string = (<GeneralActionModel>this.action).sheet;

      if(!sheet) {
         return;
      }

      const params = new HttpParams().set("id", Tool.byteEncode(sheet));
      const uri: string = ACTION_URI + "/viewsheet/highlights";

      this.http.get<ScheduleAlertModel[]>(uri, { params })
         .subscribe((highlights: ScheduleAlertModel[]) => {
            this.highlights = highlights;
         },
            (error: string) => {
               // Error
            });
   }

   private getParameters(): void {
      const sheet: string = (<GeneralActionModel>this.action).sheet;
      this.containsSheet = false;

      if(!sheet) {
         return;
      }

      const params = new HttpParams().set("id", Tool.byteEncode(sheet));
      const uri: string = ACTION_URI + "/viewsheet/parameters";

      this.http.get<string[]>(uri, { params })
         .subscribe((parameters: string[] | RepletParameters) => {
            this.parameters = parameters as string[];
         },
            (error: string) => {
               // Error
            });
   }

   private getTableDataAssemblies() {
      const sheet: string = (<GeneralActionModel>this.action).sheet;
      this.containsSheet = false;

      if(!sheet || this.action?.actionType !== "ViewsheetAction") {
         return;
      }

      const params = new HttpParams().set("id", Tool.byteEncode(sheet));

      const uri: string = ACTION_URI + "/viewsheet" + "/tableDataAssemblies";

      this.http.get<string[]>(uri, { params })
         .subscribe((tableAssemblies: string[]) => {
            this.tableDataAssemblies = tableAssemblies;
         },
            (error: string) => {
               // Error
            });
   }

   public get isGeneralAction(): boolean {
      return this.action.actionType === "ViewsheetAction";
   }

   public addAction(init: boolean = false): void {
      let action = this.getDefaultAction("ViewsheetAction");

      if(!init) {
         this.setDefaultAction();
      }

      if(this.model.actions) {
         this.model.actions.push(action);
      }
      else {
         this.model.actions = [action];
      }

      this.listView = false;
      this.actionIndex = -1;
      this.changeActionType(0);
   }

   public deleteAction(): void {
      const message: string = "_#(js:em.scheduler.actions.removeNote)";
      ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)", message).then(
         (result: string) => {
            if(result === "ok") {
               const params = new HttpParams()
                  .set("name", Tool.byteEncode(this.oldTaskName))
                  .set("owner", Tool.byteEncode(this.taskOwner));
               const actions: number[] = Tool.clone(this.selectedActions);
               actions.sort();
               actions.reverse();

               this.http.post(ACTION_URI + "/delete", actions, { params })
                  .subscribe(() => {
                     actions.sort();

                     for(let i = actions.length - 1; i >= 0; i--) {
                        this._model.actions.splice(actions[i], 1);
                     }

                     this.selectedActions = [];
                  },
                     (error: string) => {
                        // Error
                     });
            }
         });
   }

   public editAction(): void {
      if(this.selectedActions.length == 0) {
         return;
      }

      this.actionIndex = this.selectedActions[0];
      this.listView = false;
      this.action = this.model.actions[this.actionIndex];
      this.initActions();
      this.getBookmarks();

      if(this.action.actionType === "ViewsheetAction") {
         this.getHighlights();
         this.getParameters();
         this.getBookmarks();
         this.getPrintLayout();
         this.getTableDataAssemblies();
      }
   }

   public changeView(multi: boolean): void {
      this.listView = multi;
   }

   isValid(): boolean {
      return this.form.valid && this.parentForm.valid;
   }

   public save(ok: boolean): void {
      this.saveTask().then((taskModel: ScheduleTaskDialogModel) => {
         this.form.markAsPristine();
         this.updateEmailAutoComplete();
         this.updateTaskName.emit(this.taskName);
         this.dirtyFormByTs = false;

         if(ok && (this.multiCondition || taskModel.taskActionPaneModel.actions.length > 1)) {
            this.changeView(true);
         }
      }, (error: any) => {
         ComponentTool.showConfirmDialog(this.modalService, "_#(js:Error)", error, { "ok": "OK" });
      });
   }

   showSelectDashboardDialog(): void {
      const dialog = ComponentTool.showDialog(this.modalService, SelectDashboardDialog,
         (entry: RepositoryEntry) => {
            const id = entry.entry.identifier;
            this.changeViewsheet(id);

            if(!this.model.dashboardMap.hasOwnProperty(id)) {
               this.model.dashboardMap[id] = entry.path;
            }
         });

      dialog.title = "_#(js:Dashboard)";
      dialog.legend = "_#(js:Select Viewsheet)";
      dialog.path = this.getSheetPath((<GeneralActionModel>this.action).sheet);
      dialog.showVS = true;
      const identifier: string = (<GeneralActionModel>this.action).sheet;

      if(!!identifier) {
         let actualIdentifier = identifier.substring(0, identifier.lastIndexOf("^"));
         let actualPath = actualIdentifier.substring(actualIdentifier.lastIndexOf("^") + 1);

         if(createAssetEntry(identifier).scope == AssetEntryHelper.USER_SCOPE) {
            actualPath = "My Dashboards/" + actualPath;
         }

         dialog.actualPath = actualPath;
      }
   }

   private updateValues(): void {
      this.initActions();
      this.listView = this.model.actions.length > 1;

      if(!this.action) {
         this.addAction(true);
      }

      this.setSelectedOption();
   }

   initForm() {
      this.form = new UntypedFormGroup({
         "dashboard": new UntypedFormControl((<GeneralActionModel>this.action).sheet)
      });

      if(this.action.actionType == "ViewsheetAction") {
         this.form.controls["dashboard"].setValidators(Validators.required);
         this.form.controls["dashboard"].updateValueAndValidity();
      }

      this.dirtyFormByTs = false;
   }

   private updateEmailAutoComplete(): void {
      if(this.action.actionClass === "GeneralActionModel" && this.model.mailHistoryEnabled) {
         const genAction: GeneralActionModel = this.action as GeneralActionModel;
         const attr: string = "viewsheet";

         if(genAction.notificationEnabled) {
            const emails: string[] = this.getEmails(genAction.notifications);
            this.addToAutoCompleteList(attr + "Notify", emails);
         }

         if(genAction.deliverEmailsEnabled) {
            const fromEmails: string[] = this.getEmails(genAction.fromEmail);
            const toEmails: string[] = this.getEmails(genAction.to);
            this.addToAutoCompleteList(attr + "From", fromEmails);
            this.addToAutoCompleteList(attr + "MailTo", toEmails);
         }
      }
   }

   private getEmails(mails: string): string[] {
      if(!mails) {
         return null;
      }

      mails = mails.replace(";", ",");
      mails = mails.replace(" ", ",");
      const addrs: string[] = mails.split(",");

      return addrs;
   }

   private getAutoCompleteLists(): string[] {
      return Tool.getHistoryEmails(this.model.mailHistoryEnabled);
   }

   private addToAutoCompleteList(attr: string, newEmails: string[]): void {
      const emails = this.getAutoCompleteLists() || [];

      if(newEmails && newEmails.length > 0) {
         newEmails.forEach((email) => {
            if(emails.indexOf(email) === -1) {
               emails.push(email);
            }
         });
      }

      LocalStorage.setItem(EMAIL_AUTO_COMPLETE_KEY, JSON.stringify(emails));
   }

   private initActions(): void {
      // clear actions
      this.viewsheetAction = null;

      // set the current action
      this.setCurrentAction();

      // if action is null then initialize it
      this.viewsheetAction = this.viewsheetAction ||
         <GeneralActionModel>this.getDefaultAction("ViewsheetAction");
   }

   private setDefaultAction() {
      this.viewsheetAction = <GeneralActionModel>this.getDefaultAction("ViewsheetAction");
   }

   private setCurrentAction() {
      if(this.action) {
         const type = this.action.actionType;

         if(type === "ViewsheetAction") {
            this.viewsheetAction = <GeneralActionModel>this.action;
         }
      }
   }

   private getDefaultAction(actionType: string): ScheduleActionModel {
      let action: ScheduleActionModel = null;

      if(actionType === "ViewsheetAction") {
         action = <GeneralActionModel>{
            actionType: "ViewsheetAction",
            actionClass: "GeneralActionModel",
            fromEmail: this.model.defaultFromEmail,
            format: this.model.vsMailFormats == null || this.model.vsMailFormats.length == 0 ? null :
               this.model.vsMailFormats[0].type,
            emailMatchLayout: true,
            saveMatchLayout: true
         };
      }

      return action;
   }

   public getSheetPath(sheetId: string): string {
      return this.model.dashboardMap[sheetId];
   }
}

interface RepletParameters {
   requiredParameters: string[];
   optionalParameters: string[];
   containsSheet: boolean;
}
