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
import { Component, Input, OnInit } from "@angular/core";
import { RangeSliderSizePane } from "./range-slider-size-pane.component";
import { SliderLabelPane } from "./slider-label-pane.component";
import { RangeSliderAdvancedPaneModel } from "../model/range-slider-advanced-pane-model";
import { UntypedFormGroup } from "@angular/forms";
import { RangeSliderDataPaneModel } from "../model/range-slider-data-pane-model";

@Component({
   selector: "range-slider-advanced-pane",
   templateUrl: "range-slider-advanced-pane.component.html",
})

export class RangeSliderAdvancedPane implements OnInit {
   @Input() model: RangeSliderAdvancedPaneModel;
   @Input() dataModel: RangeSliderDataPaneModel;
   @Input() form: UntypedFormGroup;

   ngOnInit(): void {
      this.form.addControl("rangeSliderSizeForm", new UntypedFormGroup({}));
   }
}
