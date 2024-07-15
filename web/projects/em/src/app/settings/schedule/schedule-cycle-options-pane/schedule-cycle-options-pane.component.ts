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
import {
   Component,
   EventEmitter,
   Input,
   OnInit,
   Output,
} from "@angular/core";
import { ScheduleUsersService } from "../../../../../../shared/schedule/schedule-users.service";
import { IdentityId } from "../../security/users/identity-id";
import { CycleInfo } from "../model/schedule-cycle-dialog-model";
import { UntypedFormBuilder, UntypedFormGroup, Validators } from "@angular/forms";
import { FormValidators } from "../../../../../../shared/util/form-validators";

@Component({
   selector: "em-schedule-cycle-options-pane",
   templateUrl: "./schedule-cycle-options-pane.component.html",
   styleUrls: ["./schedule-cycle-options-pane.component.scss"]
})
export class ScheduleCycleOptionsPaneComponent implements OnInit {
   @Input() info: CycleInfo;
   @Output() infoChange = new EventEmitter<boolean>();
   form: UntypedFormGroup;
   emailUsers: IdentityId[] = [];
   groups: IdentityId[] = [];

   private change: boolean = false;

   constructor(private formBuilder: UntypedFormBuilder,
               private usersService: ScheduleUsersService)
   {
      this.initForm();
      usersService.getEmailUsers().subscribe(value => this.emailUsers = value);
      usersService.getGroups().subscribe(value => this.groups = value);
   }

   ngOnInit() {
      if(this.info) {
         this.form.patchValue(this.info);
         this.updateForm();
      }
   }

   initForm() {
      this.form = this.formBuilder.group({
         startNotify: [true],
         startEmail: [""],
         endNotify: [true],
         endEmail: [""],
         failureNotify: [true],
         failureEmail: [""],
         exceedNotify: [true],
         exceedEmail: [""],
         threshold: [0]
      });

      this.form.get("startNotify").valueChanges.subscribe((startNotify) => {
         if(startNotify != this.info.startNotify) {
            this.info.startNotify = startNotify;
            this.change = true;
         }
      });

      this.form.get("startEmail").valueChanges.subscribe((startEmail) => {
         if(startEmail != this.info.startEmail) {
            this.info.startEmail = startEmail;
            this.change = true;
         }
      });

      this.form.get("endNotify").valueChanges.subscribe((endNotify) => {
         if(this.info.endNotify != endNotify) {
            this.info.endNotify = endNotify;
            this.change = true;
         }
      });

      this.form.get("endEmail").valueChanges.subscribe((endEmail) => {
         if(this.info.endEmail != endEmail) {
            this.info.endEmail = endEmail;
            this.change = true;
         }
      });

      this.form.get("failureNotify").valueChanges.subscribe((failureNotify) => {
         if(this.info.failureNotify != failureNotify) {
            this.info.failureNotify = failureNotify;
            this.change = true;
         }
      });

      this.form.get("failureEmail").valueChanges.subscribe((failureEmail) => {
         if(this.info.failureEmail != failureEmail) {
            this.info.failureEmail = failureEmail;
            this.change = true;
         }
      });

      this.form.get("exceedNotify").valueChanges.subscribe((exceedNotify) => {
         if(this.info.exceedNotify != exceedNotify) {
            this.info.exceedNotify = exceedNotify;
            this.change = true;
         }
      });

      this.form.get("exceedEmail").valueChanges.subscribe((exceedEmail) => {
         if(this.info.exceedEmail != exceedEmail) {
            this.info.exceedEmail = exceedEmail;
            this.change = true;
         }
      });

      this.form.get("threshold").valueChanges.subscribe((threshold) => {
         if(this.info.threshold != threshold) {
            this.info.threshold = threshold;
            this.change = true;
         }
      });
   }

   updateForm() {
      if(this.form.get("startNotify").value) {
         this.form.get("startEmail").setValidators(Validators.required);
      }
      else {
         this.form.get("startEmail").setValidators(null);
      }

      if(this.form.get("endNotify").value) {
         this.form.get("endEmail").setValidators(Validators.required);
      }
      else {
         this.form.get("endEmail").setValidators(null);
      }

      if(this.form.get("failureNotify").value) {
         this.form.get("failureEmail").setValidators(Validators.required);
      }
      else {
         this.form.get("failureEmail").setValidators(null);
      }

      if(this.form.get("exceedNotify").value) {
         this.form.get("exceedEmail").setValidators(Validators.required);
         this.form.get("threshold").setValidators([Validators.required,
            FormValidators.positiveNonZeroIntegerInRange, FormValidators.isInteger()]);
      }
      else {
         this.form.get("exceedEmail").setValidators(null);
         this.form.get("threshold").setValidators(null);
      }

      this.form.get("startEmail").updateValueAndValidity();
      this.form.get("endEmail").updateValueAndValidity();
      this.form.get("failureEmail").updateValueAndValidity();
      this.form.get("exceedEmail").updateValueAndValidity();
      this.form.get("threshold").updateValueAndValidity();
      this.onInfoChanged();
   }

   onInfoChanged(): void {
      this.form.updateValueAndValidity();

      if(this.change) {
         this.infoChange.emit(this.form.valid);
      }
   }
}
