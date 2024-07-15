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
import { Component, Input, OnInit } from "@angular/core";
import { ChartPlotOptionsPaneModel } from "../model/dialog/chart-plot-options-pane-model";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { FormValidators } from "../../../../../shared/util/form-validators";

@Component({
   selector: "chart-plot-options-pane",
   templateUrl: "chart-plot-options-pane.component.html",
   styleUrls: ["chart-plot-options-pane.component.scss"]
})

export class ChartPlotOptionsPaneComponent implements OnInit {
   @Input() model: ChartPlotOptionsPaneModel;
   @Input() form: UntypedFormGroup;
   alphaInvalid: boolean = false;
   contourEdgeAlphaInvalid: boolean = false;

   ngOnInit() {
      this.initForm();
   }

   initForm() {
      const rules: any = {};

      if(this.model.bandingXVisible) {
         this.form.addControl("bandingXSize", new UntypedFormControl(
            {value: this.model.bandingXSize, disabled: !this.model.hasXDimension},
            [FormValidators.positiveNonZeroIntegerInRange, FormValidators.isInteger()]
         ));
      }

      if(this.model.bandingYVisible) {
         this.form.addControl("bandingYSize", new UntypedFormControl(
            {value: this.model.bandingYSize, disabled: !this.model.hasYDimension},
            [FormValidators.positiveNonZeroIntegerInRange, FormValidators.isInteger()]
         ));
      }

      if(this.model.contourEnabled) {
         this.form.addControl("contourLevels" , new UntypedFormControl(
            {value: this.model.contourLevels},
            [FormValidators.positiveNonZeroIntegerInRange, FormValidators.isInteger()]
         ));

         this.form.addControl("contourBandwidth" , new UntypedFormControl(
            {value: this.model.contourBandwidth},
            [FormValidators.positiveNonZeroIntegerInRange, FormValidators.isInteger()]
         ));

         this.form.addControl("contourCellSize" , new UntypedFormControl(
            {value: this.model.contourCellSize},
            [FormValidators.positiveNonZeroIntegerInRange, FormValidators.isInteger()]
         ));
      }

      if(this.model.bandingYVisible) {
         this.form.get("bandingYSize").valueChanges.subscribe((v) => {
            this.model.bandingYSize = v;
         });
      }

      if(this.model.bandingXVisible) {
         this.form.get("bandingXSize").valueChanges.subscribe((v) => {
            this.model.bandingXSize = v;
         });
      }

      if(this.model.wordCloud) {
         this.form.addControl("fontScale" , new UntypedFormControl(
            {value: this.model.wordCloudFontScale},
            [control => !(control.value >= 1 && control.value <= 5)
             ? { multiplierRange: true} : null,
             FormValidators.isFloat()]
         ));
      }

      if(this.model.explodedPieVisible) {
         this.form.addControl("pieRatio" , new UntypedFormControl(
            {value: this.model.pieRatio},
            [control => control.value != null && !(control.value >= 0.1 && control.value <= 1)
             ? { pieRatioRange: true} : null,
             FormValidators.isFloat()]
         ));
      }
   }
}
