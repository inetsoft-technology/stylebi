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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import {
   AbstractControl,
   UntypedFormBuilder,
   UntypedFormControl,
   UntypedFormGroup,
   FormGroupDirective,
   NgForm,
   ValidationErrors,
   Validators,
} from "@angular/forms";
import { ErrorStateMatcher } from "@angular/material/core";
import { MatDialog } from "@angular/material/dialog";
import { Observable } from "rxjs";
import { map, startWith } from "rxjs/operators";
import { TaskOptionsPaneModel } from "../../../../../../shared/schedule/model/task-options-pane-model";
import { ScheduleUsersService } from "../../../../../../shared/schedule/schedule-users.service";
import { FormValidators } from "../../../../../../shared/util/form-validators";
import { Tool } from "../../../../../../shared/util/tool";
import { KEY_DELIMITER, IdentityId } from "../../security/users/identity-id";
import { ExecuteAsDialogComponent } from "./execute-as-dialog.component";

export interface TaskOptionChanges {
   valid: boolean;
   model: TaskOptionsPaneModel;
}

export enum ExecuteAsType {
   USER = 0,
   GROUP = 1
}

export class GroupErrorState implements ErrorStateMatcher {
   isErrorState(control: UntypedFormControl | null, form: FormGroupDirective | NgForm | null): boolean {
      return form.form.errors && form.form.errors["dateGreaterThan"];
   }
}

@Component({
   selector: "em-task-options-pane",
   templateUrl: "./task-options-pane.component.html",
   styleUrls: ["./task-options-pane.component.scss"]
})
export class TaskOptionsPane {
   @Input()
   get model(): TaskOptionsPaneModel {
      return this._model;
   }

   set model(value: TaskOptionsPaneModel) {
      if(value) {
         this._model = Object.assign({}, value);

         if(this.model.startFrom) {
            this.optionsForm.get("startDate").setValue(new Date(this.model.startFrom));
         }
         else {
            this.optionsForm.get("startDate").setValue(null);
         }

         if(this.model.stopOn) {
            this.optionsForm.get("endDate").setValue(new Date(this.model.stopOn));
         }
         else {
            this.optionsForm.get("endDate").setValue(null);
         }

         this.optionsForm.get("taskEnabled").setValue(this.model.enabled || false);
         this.optionsForm.get("deleteIfNotScheduledToRun").setValue(this.model.deleteIfNotScheduledToRun || false);
         this.optionsForm.get("description").setValue(this.model.description);
         this.optionsForm.get("locale").setValue(this.model.locale || TaskOptionsPane.DEFAULT_LOCALE);
         this.optionsForm.get("owner").setValue(this.model.owner);
         const idName: string = this.model.idName || this._model.owner;

         if(this.adminName && !this.model.securityEnabled) {
            this._executeAs = null;
         }
         else if(this.adminName && idName === "anonymous") {
            this._executeAs = "";
         }
         else {
            this._executeAs = idName;
         }

         this.refreshExecuteAsLabel()
         this.updateDisabledState();
      }
   }

   @Input() internal: boolean = false;
   @Output() modelChanged = new EventEmitter<TaskOptionChanges>();

   optionsForm: UntypedFormGroup;
   executeAsName: string;
   filteredUsers: Observable<IdentityId[]>;
   private _model: TaskOptionsPaneModel;
   private owners: IdentityId[];
   public adminName: string = "";
   private _executeAs: string;
   public groupErrorState = new GroupErrorState();
   public static DEFAULT_LOCALE: string = "Default";

   constructor(public dialog: MatDialog, fb: UntypedFormBuilder,
               private usersService: ScheduleUsersService, private http: HttpClient)
   {
      this.optionsForm = fb.group(
         {
            taskEnabled: [true],
            deleteIfNotScheduledToRun: [false],
            startDate: [new Date()],
            endDate: [new Date()],
            description: [null],
            locale: [TaskOptionsPane.DEFAULT_LOCALE],
            owner: [null, [Validators.required, this.validUser]],
            executeAs: [null]
         },
         {
            validator: FormValidators.dateSmallerThan("startDate", "endDate")
         }
      );

      usersService.getOwners().subscribe(
         value => this.owners = value
      );

      this.http.get<string>("../api/em/navbar/organization")
         .subscribe((org) => {
            this.owners = this.owners.filter((user) => user.organization === org)
         });

      usersService.getAdminName().subscribe(
         value => {
            this.adminName = value;
            this.updateDisabledState();
         }
      );

      this.filteredUsers = this.optionsForm.controls["owner"].valueChanges
         .pipe(
            startWith(""),
            map((input: string) => {
               if(!this.model || !this.owners) {
                  return [];
               }

               if(input) {
                  const filterValue = input.toLowerCase();
                  return this.owners
                     .filter((user) => user.name.toLowerCase().startsWith(filterValue))
                     .slice(0, 100);
               }
               else {
                  return this.owners.slice(0, 100);
               }
            })
         );
   }

   fireModelChanged(): void {
      this.model.enabled = !!this.optionsForm.get("taskEnabled").value;
      this.model.deleteIfNotScheduledToRun = !!this.optionsForm.get("deleteIfNotScheduledToRun").value;
      let date: Date = this.optionsForm.get("startDate").value;
      this.model.startFrom = date ? date.getTime() : 0;
      date = this.optionsForm.get("endDate").value;
      this.model.stopOn = date ? date.getTime() : 0;
      this.model.description = this.optionsForm.get("description").value;
      const locale = this.optionsForm.get("locale").value;
      this.model.locale = locale === TaskOptionsPane.DEFAULT_LOCALE ? null : locale;
      this.model.owner = this.optionsForm.get("owner").value;
      this.model.idName = this.optionsForm.get("executeAs").value;

      this.modelChanged.emit({
         valid: this.formValidIgnoreOwner(),
         model: this.model
      });
   }

   /**
    * ignore the owner because the sso user do not in the provider.
    */
   private formValidIgnoreOwner(): boolean {
      if(this.optionsForm.valid) {
         return true;
      }

      for(let controlsKey in this.optionsForm.controls) {
         let control = this.optionsForm.controls[controlsKey];

         if(!control || "owner" == controlsKey) {
            continue;
         }

         if(control.invalid) {
            return false;
         }
      }

      return true;
   }

   clearDates(): void {
      this.model.startFrom = 0;
      this.optionsForm.get("startDate").setValue(null);
      this.model.stopOn = 0;
      this.optionsForm.get("endDate").setValue(null);
      this.fireModelChanged();
   }

   public clearUser(): void {
      this._executeAs = "";
      this.refreshExecuteAsLabel();
   }

   public openExecuteAsDialog(): void {
      const dialogRef = this.dialog.open(ExecuteAsDialogComponent, {
         width: "40vw",
         height: "fit-content",
         data: {
            owner: this.optionsForm.get("owner").value,
            idName: this.optionsForm.get("executeAs").value,
            idType: this.model.idType
         }
      });

      dialogRef.afterClosed().subscribe((data: any) => {
         if(data !== undefined) {
            this._executeAs = data.idName;
            this.refreshExecuteAsLabel();
            this.model.idType = data.idType;
            this.fireModelChanged();
         }
      });
   }

   refreshExecuteAsLabel(): void {
      let executeAsLabel = this._executeAs;

      // don't display organization for organization
      if(!!this.model.organizationName && this._executeAs != null &&
         this.model.idType == ExecuteAsType.GROUP)
      {
         let idx = this._executeAs.lastIndexOf(KEY_DELIMITER);
         let groupBaseName = idx != -1 ? this._executeAs.substring(0, idx) : this._executeAs;
         executeAsLabel = groupBaseName;
      }

      this.optionsForm.get("executeAs").setValue(executeAsLabel);
   }

   private validUser = (control: AbstractControl): ValidationErrors | null => {
      if("INETSOFT_SYSTEM" == control.value) {
         return null;
      }

      return this.model && this.owners &&
         this.owners.findIndex(u => u.name == control.value) === -1 ? {invalid: true} :
         null;
   };

   private updateDisabledState() {
      if(!this.adminName) {
         this.optionsForm.get("owner").disable();
         this.optionsForm.get("executeAs").disable();
      }
      else {
         this.optionsForm.get("owner").enable();
         this.optionsForm.get("executeAs").enable();
      }
   }
}
