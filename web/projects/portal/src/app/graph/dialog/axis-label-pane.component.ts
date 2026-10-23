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
import { Component, Input, OnInit, ViewChild, TemplateRef } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { UntypedFormGroup, FormsModule } from "@angular/forms";
import { AxisLabelPaneModel } from "../model/dialog/axis-label-panel-model";
import { StyleConstants } from "../../common/util/style-constants";
import { RotationRadioGroup } from "../../widget/format/rotation-radio-group.component";
import { NumberStepperComponent } from "../../widget/number-stepper/number-stepper.component";


@Component({
    selector: "axis-label-pane",
    templateUrl: "axis-label-pane.component.html",
    imports: [
    FormsModule,
    NumberStepperComponent,
    RotationRadioGroup
]
})
export class AxisLabelPane implements OnInit {
   @Input() model: AxisLabelPaneModel;
   form: UntypedFormGroup;
   showLabelGapFollowDefault: boolean;

   constructor(private modalService: NgbModal) {
      this.form = new UntypedFormGroup({
         formatForm: new UntypedFormGroup({})
      });
   }

   ngOnInit(): void {
      this.showLabelGapFollowDefault = this.model.labelGapFollowsDefault != null;
   }

   labelGapFollowDefaultChanged(follows: boolean): void {
      this.model.labelGapFollowsDefault = follows;
   }
}
