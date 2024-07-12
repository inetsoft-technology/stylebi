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
import { Component, Input, OnInit, ViewChild } from "@angular/core";
import { FeatureFlagValue } from "../../../../../shared/feature-flags/feature-flags.service";
import { TipPane } from "./graph/tip-pane.component";
import { CalcTableAdvancedPaneModel } from "../model/calc-table-advanced-pane-model";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { FormValidators } from "../../../../../shared/util/form-validators";
import { NotificationsComponent } from "../../widget/notifications/notifications.component";

@Component({
   selector: "calc-table-advanced-pane",
   templateUrl: "calc-table-advanced-pane.component.html",
})
export class CalcTableAdvancedPane implements OnInit {
   @Input() model: CalcTableAdvancedPaneModel;
   @Input() form: UntypedFormGroup;
   @Input() showHeaderRowWarning: boolean = false;
   @Input() showHeaderColumnWarning: boolean = false;
   readonly FeatureFlagValue = FeatureFlagValue;
   @ViewChild("notifications") notifications: NotificationsComponent;

   ngOnInit(): void {
      this.initForm();
   }

   initForm(): void {
      this.form.addControl("headerRowCount", new UntypedFormControl(this.model.headerRowCount, [
         FormValidators.positiveIntegerInRange,
         Validators.required,
      ]));
      this.form.addControl("headerColCount", new UntypedFormControl(this.model.headerColCount, [
         FormValidators.positiveIntegerInRange,
         Validators.required,
      ]));
      this.form.addControl("trailerRowCount", new UntypedFormControl(this.model.trailerRowCount, [
         FormValidators.positiveIntegerInRange,
         Validators.required,
      ]));
      this.form.addControl("trailerColCount", new UntypedFormControl(this.model.trailerColCount, [
         FormValidators.positiveIntegerInRange,
         Validators.required,
      ]));

   }

   shrinkChanged(event: boolean) {
      if(event) {
         this.notifications.success("_#(js:composer.dialog.shrinkToFit.hint)");
      }
   }
}
