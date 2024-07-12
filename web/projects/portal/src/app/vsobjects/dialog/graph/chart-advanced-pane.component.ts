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
import { Component, Input, OnInit } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ChartAdvancedPaneModel } from "../../model/chart-advanced-pane-model";
import { Tool } from "../../../../../../shared/util/tool";
import { TargetInfo } from "../../../widget/target/target-info";
import { ChartConfig } from "../../../common/util/chart-config";
import { GraphTypes } from "../../../common/graph-types";
import { FormControl, UntypedFormGroup } from "@angular/forms";
import { FormValidators } from "../../../../../../shared/util/form-validators";

@Component({
   selector: "chart-advanced-pane",
   templateUrl: "chart-advanced-pane.component.html",
   styleUrls: ["chart-advanced-pane.component.scss"]
})
export class ChartAdvancedPane implements OnInit {
   @Input() model: ChartAdvancedPaneModel;
   @Input() variables: string[];
   @Input() viewer: boolean = false;
   @Input() vsId: string = null;
   @Input() chartType: number;
   @Input() form: UntypedFormGroup;
   alphaInvalid: boolean = false;

   ngOnInit(): void {
      this.initForm();
   }

   initForm() {
      this.form.addControl("chartPlotOptionsPaneForm", new UntypedFormGroup({}));
   }

   changeAlphaWarning(event) {
      this.alphaInvalid = event;
   }

   isMekko() {
      return GraphTypes.isMekko(this.chartType);
   }
}
