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
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { By } from "@angular/platform-browser";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { of as observableOf, Subject } from "rxjs";
import { UIContextService } from "../../../common/services/ui-context.service";
import { DropDownTestModule } from "../../../common/test/test-module";
import { TestUtils } from "../../../common/test/test-utils";
import { TipCustomizeDialogModel } from "../../../widget/dialog/tip-customize-dialog/tip-customize-dialog-model";
import { TipCustomizeDialog } from "../../../widget/dialog/tip-customize-dialog/tip-customize-dialog.component";
import { FixedDropdownDirective } from "../../../widget/fixed-dropdown/fixed-dropdown.directive";
import { AlphaDropdown } from "../../../widget/format/alpha-dropdown.component";
import { LargeFormFieldComponent } from "../../../widget/large-form-field/large-form-field.component";
import { ModelService } from "../../../widget/services/model.service";
import { TipPaneModel } from "../../model/tip-pane-model";
import { TipPane } from "./tip-pane.component";
import { DebounceService } from "../../../widget/services/debounce.service";

let createModel: () => TipPaneModel = () => {
   return {
      chart: false,
      tipOption: false,
      tipView: "null",
      alpha: "100",
      flyOverViews: [],
      flyOnClick: false,
      popComponents: [],
      flyoverComponents: [],
      dataViewEnabled: true,
      tipCustomizeDialogModel: <TipCustomizeDialogModel> {
         customRB: "DEFAULT",
         combinedTip: false,
         lineChat: false,
         customTip: "",
         dataRefList: [],
         lineChart: false,
         availableTipValues: []
      }
   };
};

let fixture: ComponentFixture<TipPane>;
let model: TipPaneModel;
let element: any;

describe("Tip Pane Unit Test", () => {
   let modelService: any;

   beforeEach(() => {
      modelService = { getModel: jest.fn() };
      modelService.getModel.mockImplementation(() => observableOf({}));
      let uiContextService = {
         isVS: jest.fn(),
         isAdhoc: jest.fn(),
         getDefaultTab: jest.fn(),
         setDefaultTab: jest.fn(),
         getObjectChange: jest.fn()
      };
      uiContextService.getObjectChange.mockImplementation(() => new Subject<any>().asObservable());

      TestBed.configureTestingModule({
         imports: [
            FormsModule, ReactiveFormsModule, NgbModule, DropDownTestModule
         ],
         declarations: [
            TipPane, TipCustomizeDialog, AlphaDropdown, FixedDropdownDirective,
            LargeFormFieldComponent
         ],
         providers: [
            NgbModal, DebounceService,
            {provide: ModelService, useValue: modelService},
            {provide: UIContextService, useValue: uiContextService}
         ],
         schemas: [NO_ERRORS_SCHEMA]
      });
      TestBed.compileComponents();

      fixture = TestBed.createComponent(TipPane);
      model = createModel();
      fixture.componentInstance.model = model;
      element = fixture.nativeElement;
   });

   //add unit case for bug#16247
   it("test tip pane disable and enable status", () => {
      fixture.detectChanges();
      let tipViewFieldSet: any = element.querySelector("div.data_tip_view_id select");
      let alphaFieldSet: any = element.querySelector("div.data_tip_view_id alpha-dropdown");
      expect(tipViewFieldSet.getAttribute("ng-reflect-is-disabled")).toBe("true");
      expect(alphaFieldSet.getAttribute("ng-reflect-disabled")).toBe("true");

      model.tipOption = true;
      model.tipView = "TableView1";
      model.popComponents = ["TableView1"];
      fixture.detectChanges();
      expect(tipViewFieldSet.getAttribute("ng-reflect-is-disabled")).toBe("false");
      expect(alphaFieldSet.getAttribute("ng-reflect-disabled")).toBe("false");

      model.tipView = null;
      fixture.detectChanges();
      expect(tipViewFieldSet.getAttribute("ng-reflect-is-disabled")).toBe("false");
      expect(alphaFieldSet.getAttribute("ng-reflect-disabled")).toBe("true");
   });

   //add unit case for Bug #10676
   xit("tip view should be None when selected same assembly in flyover", () => {
      model.tipOption = true;
      model.tipView = "Gauge1";
      model.flyoverComponents = ["TableView1", "TableView2", "Gauge1"];
      fixture.detectChanges();
      let tipViewFieldSet: any = element.querySelector("div.data_tip_view_id");
      let flyoverContents: any = element.querySelectorAll("div.flyover_list_id label");
      let ftableView1: any = Array.prototype.slice.call(flyoverContents).filter(
         e => e.textContent == "Gauge1" )[0];

      ftableView1.click();
      fixture.detectChanges();
   });

   //Bug #20558
   it("test Clear and Select All button on flyover pane", () => {
      model.flyoverComponents = ["TableView1", "TableView2", "Gauge1"];
      model.popComponents = ["TableView1", "TableView2", "Gauge1"];
      fixture.detectChanges();

      let selectAllButton: HTMLButtonElement = element.querySelectorAll("w-large-form-field button")[0];
      let clearButton: HTMLButtonElement = element.querySelectorAll("w-large-form-field button")[1];
      expect(selectAllButton.disabled).toBeFalsy();
      expect(clearButton.disabled).toBeFalsy();

      selectAllButton.click();
      fixture.detectChanges();
      let selectedComponents: any = element.querySelectorAll(
         "div.flyover_list_id input[type='checkbox']:checked");
      expect(selectedComponents.length).toEqual(3);

      //Bug #20558
      let dataTipView = element.querySelector(".data_tip_view_id select > option:checked");
      expect(TestUtils.toString(dataTipView.textContent.trim())).toEqual("None");

      clearButton = fixture.debugElement
         .queryAll(By.css("w-large-form-field button"))[1].nativeElement;
      clearButton.click();
      fixture.detectChanges();
      selectedComponents = fixture.debugElement
         .queryAll(By.css("div.flyover_list_id input[type='checkbox']:checked"));
      expect(selectedComponents.length).toEqual(0);
   });
});
