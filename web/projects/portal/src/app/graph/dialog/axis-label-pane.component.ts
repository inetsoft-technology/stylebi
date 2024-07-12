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
import { Component, Input, ViewChild, TemplateRef } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { UntypedFormGroup } from "@angular/forms";
import { AxisLabelPaneModel } from "../model/dialog/axis-label-panel-model";
import { StyleConstants } from "../../common/util/style-constants";

@Component({
   selector: "axis-label-pane",
   templateUrl: "axis-label-pane.component.html",
})
export class AxisLabelPane {
   @Input() model: AxisLabelPaneModel;
   form: UntypedFormGroup;

   constructor(private modalService: NgbModal) {
      this.form = new UntypedFormGroup({
         formatForm: new UntypedFormGroup({})
      });
   }
}
