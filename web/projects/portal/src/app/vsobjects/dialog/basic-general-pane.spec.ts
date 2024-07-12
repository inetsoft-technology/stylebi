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
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormGroup, FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { DropDownTestModule } from "../../common/test/test-module";
import { FixedDropdownDirective } from "../../widget/fixed-dropdown/fixed-dropdown.directive";
import { BasicGeneralPaneModel } from "../model/basic-general-pane-model";
import { BasicGeneralPane } from "./basic-general-pane.component";

describe("Basic general pane Test", () => {
   const createModel: () => BasicGeneralPaneModel = () => {
      return {
         name: "",
         visible: "Show",
         shadow: false,
         enabled: true,
         sendSelections: false,
         primary: true,
         refresh: false,
         editable: false,
         nameEditable: true,
         showShadowCheckbox: false,
         showEnabledCheckbox: true,
         showRefreshCheckbox: false,
         showEditableCheckbox: false,
         showSendSelectionsCheckbox: false,
         objectNames: []
      };
   };

   let fixture: ComponentFixture<BasicGeneralPane>;
   let basicGeneralPane: BasicGeneralPane;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [
            FormsModule, ReactiveFormsModule, NgbModule, DropDownTestModule
         ],
         declarations: [
            BasicGeneralPane, FixedDropdownDirective
         ],
         providers: [
            NgbModal
         ],
         schemas: [NO_ERRORS_SCHEMA]
      });
      TestBed.compileComponents();
   });

   //Bug #18527,should not display 'Visible' and 'Visible in external viewsheets'
   //for selection container children assembly
   //Bug #16581 the first element should be focused
   it("check general pane ui", () => {
      fixture = TestBed.createComponent(BasicGeneralPane);
      basicGeneralPane = <BasicGeneralPane> fixture.componentInstance;
      basicGeneralPane.model = createModel();
      basicGeneralPane.form = new FormGroup({});
      fixture.detectChanges();

      let name = fixture.nativeElement.querySelector("input#basicGeneralPaneName");
      expect(name.hasAttribute("defaultfocus")).toBeTruthy();

      basicGeneralPane.model.containerType = "VSSelectionContainer";
      fixture.detectChanges();

      let visibleInput = fixture.nativeElement.querySelector("dynamic-combo-box#visible");
      let visibleInExVS = fixture.nativeElement.querySelector(
         "input.visible_in_external_vs_id");

      expect(visibleInput).toBeNull();
      expect(visibleInExVS).toBeNull();

      basicGeneralPane.model.containerType = "VSGroupContainer";
      fixture.detectChanges();

      visibleInput = fixture.nativeElement.querySelector("dynamic-combo-box#visible");
      visibleInExVS = fixture.nativeElement.querySelector(
         "input.visible_in_external_vs_id");

      expect(visibleInput).not.toBeNull();
      expect(visibleInExVS).not.toBeNull();
   });
});
