/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { Component, EventEmitter, Input, OnDestroy, Output, } from "@angular/core";
import {
   UntypedFormBuilder,
   UntypedFormControl,
   UntypedFormGroup,
   FormGroupDirective,
   NgForm,
   Validators
} from "@angular/forms";
import { ErrorStateMatcher } from "@angular/material/core";
import { Subscription } from "rxjs";
import { ServerLocation } from "../../../../../../shared/schedule/model/server-location";
import { TimeRange } from "../../../../../../shared/schedule/model/time-condition-model";
import { ScheduleUsersService } from "../../../../../../shared/schedule/schedule-users.service";
import { FormValidators } from "../../../../../../shared/util/form-validators";
import { IdentityId } from "../../security/users/identity-id";
import { ScheduleConfigurationModel } from "../model/schedule-configuration-model";
import { MatDialog } from "@angular/material/dialog";
import { ScheduleClasspathDialogComponent } from "./schedule-classpath-dialog/schedule-classpath-dialog.component";

export interface ScheduleConfiguration {
   model: ScheduleConfigurationModel;
   valid: boolean;
}

@Component({
   selector: "em-schedule-configuration-view",
   templateUrl: "./schedule-configuration-view.component.html",
   styleUrls: ["./schedule-configuration-view.component.scss"]
})
export class ScheduleConfigurationViewComponent implements OnDestroy {
   @Output() onChange = new EventEmitter<ScheduleConfiguration>();
   form: UntypedFormGroup;
   errorStateMatcher: ErrorStateMatcher;
   emailEditable = false;
   emailUsers: IdentityId[] = [];
   groups: IdentityId[] = [];
   private _model: ScheduleConfigurationModel;
   private formSubscription = new Subscription();

   @Input() set model(model: ScheduleConfigurationModel) {
      this._model = model;

      if(this.model) {
         this.form.patchValue(model, {emitEvent: false});

         if(model.notifyIfDown || model.notifyIfTaskFailed) {
            this.form.get("emailAddress").enable({emitEvent: false});
            this.emailEditable = true;
         }
         else {
            this.form.get("emailAddress").disable({emitEvent: false});
            this.emailEditable = false;
         }
      }
   }

   get model(): ScheduleConfigurationModel {
      return this._model;
   }

   constructor(fb: UntypedFormBuilder,
               defaultErrorMatcher: ErrorStateMatcher,
               private usersService: ScheduleUsersService,
               private dialog: MatDialog)
   {
      this.form = fb.group({
         concurrency: [1, [Validators.required, FormValidators.isInteger, Validators.min(1)]],
         rmiPort: [0, [Validators.required, FormValidators.isInteger, Validators.min(0), Validators.max(65535)]],
         classpath: ["", [Validators.required]],
         autoStart: [],
         autoStop: [],
         notificationEmail: [],
         saveToDisk: [],
         emailDelivery: [],
         enableEmailBrowser: [],
         minMemory: [1, [Validators.required, FormValidators.isInteger, Validators.min(1)]],
         maxMemory: [1, [Validators.required, FormValidators.isInteger, Validators.min(1)]],
         emailAddress: [{value: "", disabled: true}],
         emailSubject: [],
         emailMessage: [],
         notifyIfDown: [false],
         notifyIfTaskFailed: [false],
         shareTaskInSameGroup: [],
         deleteTaskOnlyByOwner: [],
         timeRanges: [[]],
         serverLocations: [[]],
         saveAutoSuffix: [""],
         securityEnable: [false]
      }, {validator: [FormValidators.smallerThan("minMemory", "maxMemory", false)]});

      usersService.getEmailUsers().subscribe(value => this.emailUsers = value);
      usersService.getGroups().subscribe(value => this.groups = value);

      this.formSubscription.add(this.form.get("notifyIfDown").valueChanges
         .subscribe(change => this.updateEmailControl(change, "notifyIfTaskFailed")));

      this.formSubscription.add(this.form.get("notifyIfTaskFailed").valueChanges
         .subscribe(change => this.updateEmailControl(change, "notifyIfDown")));

      // IE may trigger a change event immediately on populating the form
      setTimeout(() => {
         this.formSubscription.add(this.form.valueChanges.subscribe(() => this.fireChange()));
      }, 200);

      this.errorStateMatcher = {
         isErrorState: (control: UntypedFormControl | null, form: FormGroupDirective | NgForm | null) =>
            !!this.form.errors && !!this.form.errors.greaterThan ||
            defaultErrorMatcher.isErrorState(control, form)
      };
   }

   ngOnDestroy() {
      this.formSubscription.unsubscribe();
   }

   onTimeRangesChanged(ranges: TimeRange[]): void {
      this.form.get("timeRanges").setValue(ranges);
      this.fireChange();
   }

   onServerLocationsChanged(locations: ServerLocation[]): void {
      this.form.get("serverLocations").setValue(locations);
      this.fireChange();
   }

   editClassPath() {
      const dialogRef = this.dialog.open(ScheduleClasspathDialogComponent, {
         role: "dialog",
         width: "750px",
         maxWidth: "100%",
         maxHeight: "100%",
         disableClose: true,
         data: {
            title: "_#(js:Schedule Classpath)",
            classpath: this.model.classpath,
            separator: this.model.pathSeparator
         }
      });

      dialogRef.afterClosed().subscribe((data: any) => {
         if(data) {
            this.model.classpath = data;
            let classpathForm = this.form.get("classpath");

            if(!!classpathForm) {
               classpathForm.setValue(data, { emitEvent: true });
            }
         }
      });
   }

   private updateEmailControl(change: any, controlName: string): void {
      if(!!change || !!this.form.get(controlName).value) {
         this.form.get("emailAddress").enable();
         this.emailEditable = true;
      }
      else {
         this.form.get("emailAddress").disable();
         this.emailEditable = false;
      }
   }

   private fireChange(): void {
      if(!this.model) {
         return;
      }

      const changedModel = <ScheduleConfigurationModel> this.form.value;
      changedModel.emailAddress = this.form.get("emailAddress").value;
      changedModel.emailAddress = !!changedModel.emailAddress ? changedModel.emailAddress : "";
      changedModel.logFile = this.model.logFile;

      let newModel = this.form.value;

      if(!!newModel && !!this.model) {
         newModel.pathSeparator = this.model.pathSeparator;
      }

      this.onChange.emit({model: this.form.value, valid: this.form.valid});
   }
}
