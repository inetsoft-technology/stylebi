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
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormGroup, FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { UIContextService } from "../../common/services/ui-context.service";
import { DropDownTestModule } from "../../common/test/test-module";
import { RecentColorService } from "../../widget/color-picker/recent-color.service";
import { EnterSubmitDirective } from "../../widget/directive/enter-submit.directive";
import { FixedDropdownDirective } from "../../widget/fixed-dropdown/fixed-dropdown.directive";
import { AxisLabelPaneModel } from "../model/dialog/axis-label-panel-model";
import { AxisLinePaneModel } from "../model/dialog/axis-line-pane-model";
import { AxisPropertyDialogModel } from "../model/dialog/axis-property-dialog-model";
import { AliasPane } from "./alias-pane.component";
import { AxisLabelPane } from "./axis-label-pane.component";
import { AxisLinePane } from "./axis-line-pane.component";
import { AxisPropertyDialog } from "./axis-property-dialog.component";

let createLabelModel: () => AxisLabelPaneModel = () => {
   return {
      rotationRadioGroupModel: {rotation: "auto"},
      showAxisLabel: true
   };
};

let createLineModel: () => AxisLinePaneModel = () => {
   return {
      ignoreNull: true,
      truncate: true,
      logarithmicScale: true,
      showAxisLine: true,
      showAxisLineEnabled: true,
      showTicks: true,
      lineColorEnabled: true,
      reverse: true,
      shared: true,
      lineColor: "#ffffff",
      minimum: null,
      maximum: null,
      minorIncrement: null,
      increment: null,
      axisType: ""
   };
};

let createModel: () => AxisPropertyDialogModel = () => {
   return {
      axisLinePaneModel: createLineModel(),
      axisLabelPaneModel: createLabelModel(),
      aliasPaneModel: {aliasList: []},
      timeSeries: false,
      linear: true,
      outer: false,
      aliasSupported: false
   };
};

describe("Axis Property Dialog Unit Tests", () => {
   let fixture: ComponentFixture<AxisPropertyDialog>;
   let axisPropertyDialog: AxisPropertyDialog;
   let uiContextService: any;

   beforeEach(async(() => {
      uiContextService = {
         isVS: jest.fn(),
         isAdhoc: jest.fn(),
         getDefaultTab: jest.fn(),
         setDefaultTab: jest.fn()
      };

      TestBed.configureTestingModule({
         imports: [
            NgbModule, FormsModule, ReactiveFormsModule, DropDownTestModule
         ],
         declarations: [
            AxisPropertyDialog, AxisLinePane, FixedDropdownDirective,
            AliasPane, AxisLabelPane, EnterSubmitDirective
         ],
         providers: [
            NgbModal, RecentColorService,
            { provide: UIContextService, useValue: uiContextService }
         ],
         schemas: [ NO_ERRORS_SCHEMA ]
      });
      TestBed.compileComponents();

      fixture = TestBed.createComponent(AxisPropertyDialog);
      axisPropertyDialog = fixture.componentInstance;
      axisPropertyDialog.model = createModel();
      axisPropertyDialog.form = new FormGroup({lineForm: new FormGroup({})});
   }));

   // Bug #21124 should not pop up warning for correct value
   it("should not pop up warning for correct value", () => {
      axisPropertyDialog.model.axisLinePaneModel.increment = 0.5;
      axisPropertyDialog.model.axisLinePaneModel.minimum = 50;
      axisPropertyDialog.model.axisLinePaneModel.maximum = 150;
      fixture.detectChanges();

      expect(axisPropertyDialog.incrementValid).toBe(true);
      expect(axisPropertyDialog.minmaxValid).toBe(true);
   });
});