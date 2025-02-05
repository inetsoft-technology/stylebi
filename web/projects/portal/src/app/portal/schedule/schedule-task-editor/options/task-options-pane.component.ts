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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { UntypedFormControl, UntypedFormGroup } from "@angular/forms";
import { NgbDateStruct, NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { IdentityId } from "../../../../../../../em/src/app/settings/security/users/identity-id";
import { IdentityType } from "../../../../../../../shared/data/identity-type";
import { TaskOptionsPaneModel } from "../../../../../../../shared/schedule/model/task-options-pane-model";
import { ScheduleUsersService } from "../../../../../../../shared/schedule/schedule-users.service";
import { FormValidators } from "../../../../../../../shared/util/form-validators";
import { ComponentTool } from "../../../../common/util/component-tool";
import { ExecuteAsDialog } from "../execute-as-dialog/execute-as-dialog.component";
import { Observable } from "rxjs";
import { ScheduleTaskDialogModel } from "../../../../../../../shared/schedule/model/schedule-task-dialog-model";

@Component({
   selector: "task-options-pane",
   templateUrl: "./task-options-pane.component.html",
   styleUrls: ["./task-options-pane.component.scss"]
})
export class TaskOptionsPane implements OnInit {
   @Input() set model(value: TaskOptionsPaneModel) {
      this._model = value;
      const start: Date = new Date(value.startFrom);
      this.startDate = value.startFrom ? {
         year: start.getFullYear(),
         month: start.getMonth() + 1,
         day: start.getDate()
      } : null;
      const end: Date = new Date(value.stopOn);
      this.endDate = value.stopOn ? {
         year: end.getFullYear(),
         month: end.getMonth() + 1,
         day: end.getDate()
      } : null;

      this.getExecuteAsName();
      this.executeAsType = value.idType;

      if(this.form) {
         this.form.get("start").setValue(this.startDate);
         this.form.get("stop").setValue(this.endDate);
      }
   }
   @Input() oldTaskName: string;
   @Input() taskName: string;
   @Input() parentForm: UntypedFormGroup;
   @Input() newTask: boolean;
   @Input() saveTask: () =>  Promise<any>;
   @Output() doneLoading = new EventEmitter<ScheduleTaskDialogModel>();
   @Output() updateTaskName = new EventEmitter<string>();
   @Output() closeEditor = new EventEmitter<TaskOptionsPaneModel>();
   @Output() cancelTask = new EventEmitter();
   _model: TaskOptionsPaneModel;
   owners: IdentityId[];
   groups: IdentityId[];
   groupBaseNames: string[];
   adminName: string;
   startDate: NgbDateStruct;
   endDate: NgbDateStruct;
   form: UntypedFormGroup = null;
   executeAsName: string;
   executeAsType: number;
   public static DEFAULT_LOCALE: string = "Default";
   executeAsTypes: any[] = [
      {value: IdentityType.USER, label: "_#(js:User)"},
      {value: IdentityType.GROUP, label: "_#(js:Group)"}
   ];

   constructor(private modalService: NgbModal, private usersService: ScheduleUsersService,
               private http: HttpClient)
   {
      usersService.getOwners().subscribe(value => this.owners = value);
      usersService.getGroups().subscribe(value => this.groups = value);
      usersService.getGroupBaseNames().subscribe(value => this.groupBaseNames = value);
      usersService.getAdminName().subscribe(value => this.adminName = value);
   }

   ngOnInit() {
      this.initForm();
   }

   get locale(): string {
      return !!this._model.locale ? this._model.locale : TaskOptionsPane.DEFAULT_LOCALE;
   }

   set locale(loc: string) {
      this._model.locale = loc != TaskOptionsPane.DEFAULT_LOCALE ? loc : null;
   }

   public startDateChange(date: NgbDateStruct): void {
      if(date) {
         this.startDate = date;
         this._model.startFrom = new Date(date.year, date.month - 1, date.day, 0, 0, 0)
            .getTime();
      }
   }

   public endDateChange(date: NgbDateStruct): void {
      if(date) {
         this.endDate = date;
         this._model.stopOn = new Date(date.year, date.month - 1, date.day, 0, 0, 0)
            .getTime();
      }
   }

   public clearStartDate(): void {
      this.startDate = null;
      this._model.startFrom = 0;
      this.form.get("start").setValue(this.startDate);
   }

   public clearEndDate(): void {
      this.endDate = null;
      this._model.stopOn = 0;
      this.form.get("stop").setValue(this.endDate);
   }

   public clearUser(): void {
      this.executeAsName = "";
      this._model.idName = null;
      this._model.idType = null;
   }

   private getExecuteAsName(): void {
      const idName: string = this._model.idName == null ? this._model.owner
         : this._model.idName;
      let _executeAsName = "";

      if(this.adminName && !this._model.securityEnabled) {
         _executeAsName = "";
      }
      else if(this.adminName) {
         _executeAsName = idName === "anonymous" ? "" : idName;
      }
      else {
         _executeAsName = idName;
      }

      if(_executeAsName.length > 0 && !!this.groupBaseNames && this.groupBaseNames.length > 0) {
         let idx = this.groups.findIndex(g => g.name == _executeAsName);
         this.executeAsName = idx == -1 ? _executeAsName : this.groupBaseNames[idx];
      }
      else {
         this.executeAsName = _executeAsName;
      }
   }

   public getExecuteAsType(): string {
      const idName: string = this._model.idName == null ? this._model.owner
         : this._model.idName;

      if(this.adminName && !this._model.securityEnabled) {
         return "";
      }
      else if(this.adminName) {
         return !idName ? "" : this._model.idType == IdentityType.USER ? "_#(js:User)" : "_#(js:Group)";
      }
      else {
         return this._model.idType == IdentityType.USER ? "_#(js:User)" : "_#(js:Group)";
      }
   }

   public disableExecuteAs(): boolean {
      return !(this.adminName && this._model.securityEnabled && !this._model.selfOrg) || this.loadingUsers;
   }

   public updateExecuteAs(name: string) {
      this.executeAsName = name;

      if(this.executeAsName == "" || this.executeAsName == this._model.owner) {
         this._model.idName = null;
      }
      else {
         this._model.idName = this.executeAsName;
      }
   }

   public save(): void {
      this.saveTask().then(() => {
         this.form.markAsPristine();
         this.updateTaskName.emit(this.taskName);
      });
   }

   public openExecuteAsDialog(): void {
      let dialog: ExecuteAsDialog = ComponentTool.showDialog(this.modalService, ExecuteAsDialog,
         (identity: {name: string, type: number}) => {
            this._model.idName = identity.name;
            this._model.idType = identity.type;
            this.getExecuteAsName();
         });

      dialog.users = this.owners;
      dialog.groups = this.getGroupModel();
      dialog.type = this.executeAsType;
   }

   getGroupModel(): IdentityId[] {
      return this.groups;
   }

   initForm() {
      this.form = new UntypedFormGroup({
         "start": new UntypedFormControl(this.startDate, []),
         "stop": new UntypedFormControl(this.endDate, [])
      }, FormValidators.dateSmallerThan("start", "stop"));

      this.form.get("start").valueChanges.subscribe((date) => {
         this.startDateChange(date);
      });

      this.form.get("stop").valueChanges.subscribe((date) => {
         this.endDateChange(date);
      });
   }

   get loadingUsers(): boolean {
      return this.usersService.isLoading && this._model.securityEnabled;
   }
}
