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
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormGroup, FormsModule, ReactiveFormsModule } from "@angular/forms";
import { By } from "@angular/platform-browser";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { GeneralPropPane } from "../../../vsobjects/dialog/general-prop-pane.component";
import { SizePositionPane } from "../../../vsobjects/dialog/size-position-pane.component";
import { TitlePropPane } from "../../../vsobjects/dialog/title-prop-pane.component";
import { SelectionGeneralPaneModel } from "../../data/vs/selection-general-pane-model";
import { SelectionGeneralPane } from "./selection-general-pane.component";
import { TestUtils } from "../../../common/test/test-utils";
import { FeatureFlagsService } from "../../../../../../shared/feature-flags/feature-flags.service";


let createModel: () => SelectionGeneralPaneModel = () => {
   return {
      showType: 0,
      listHeight: 6,
      sortType: 8,
      submitOnChange: true,
      singleSelection: false,
      suppressBlank: false,
      showNonContainerProps: true,
      generalPropPaneModel: {
         basicGeneralPaneModel: null,
         showSubmitCheckbox: false,
         submitOnChange: false,
         showEnabledGroup: true,
         enabled: "True",
         popLocation: null
      },
      titlePropPaneModel: {visible: true, title: "Category"},
      sizePositionPaneModel: {
         top: 117,
         left: 319,
         width: 70,
         height: 108,
         cellHeight: 18,
         container: false,
         locked: false,
         scaleVertical: false,
         titleHeight: null
      },
      inSelectionContainer: false
   };
};

describe("Selection General Pane Unit Tests", () => {
   let fixture: ComponentFixture<SelectionGeneralPane>;
   let selectGeneralPane: SelectionGeneralPane;
   let featureFlagsService = { isFeatureEnabled: jest.fn() };

   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [
            NgbModule, FormsModule, ReactiveFormsModule
         ],
         declarations: [
            SelectionGeneralPane, GeneralPropPane, TitlePropPane, SizePositionPane
         ],
         providers: [
            { provide: FeatureFlagsService, useValue: featureFlagsService},
         ],
         schemas: [ NO_ERRORS_SCHEMA ]
      });
      TestBed.compileComponents();

      fixture = TestBed.createComponent(SelectionGeneralPane);
      selectGeneralPane = fixture.componentInstance;
      selectGeneralPane.model = createModel();
      selectGeneralPane.form = new FormGroup({});
      fixture.detectChanges();
   }));

   // Bug #21146 should pop up warning for invalid height number
   it("should pop up warning for invalid height number", () => {
      let dropdownBtn = fixture.nativeElement.querySelectorAll("input[name=show-as]")[1];
      dropdownBtn.click();
      fixture.detectChanges();

      let height = fixture.debugElement.query(By.css("input#listHeight")).nativeElement;
      height.value = "-2";
      height.dispatchEvent(new Event("input"));
      fixture.detectChanges();

      let warnings = fixture.debugElement.query(By.css("div.alert.alert-danger")).nativeElement;
      expect(TestUtils.toString(warnings.textContent)).toBe("height.positive.nonZero");
   });
});