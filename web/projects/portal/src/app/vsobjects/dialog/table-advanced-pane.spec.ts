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
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { of as observableOf, Subject } from "rxjs";
import { UIContextService } from "../../common/services/ui-context.service";
import { DropDownTestModule } from "../../common/test/test-module";
import { TipCustomizeDialog } from "../../widget/dialog/tip-customize-dialog/tip-customize-dialog.component";
import { FixedDropdownDirective } from "../../widget/fixed-dropdown/fixed-dropdown.directive";
import { AlphaDropdown } from "../../widget/format/alpha-dropdown.component";
import { LargeFormFieldComponent } from "../../widget/large-form-field/large-form-field.component";
import { ModelService } from "../../widget/services/model.service";
import { TableAdvancedPaneModel } from "../model/table-advanced-pane-model";
import { TipPaneModel } from "../model/tip-pane-model";
import { TipPane } from "./graph/tip-pane.component";
import { TableAdvancedPane } from "./table-advanced-pane.component";
import { DebounceService } from "../../widget/services/debounce.service";

let createModel: () => TableAdvancedPaneModel = () => {
   return {
      formVisible: true,
      shrinkEnabled: true,
      shrink: false,
      enableAdhoc: false,
      form: false,
      insert: false,
      del: false,
      edit: false,
      writeBack: false,
      tipPaneModel: <TipPaneModel> {
         chart: false,
         tipOption: false,
         tipView: "null",
         alpha: "100",
         flyOverViews: [],
         flyOnClick: false,
         popComponents: [],
         tipCustomizeDialogModel: {
            customRB: "DEFAULT",
            combinedTip: false,
            lineChart: false,
            customTip: "",
            dataRefList: [],
            availableTipValues: []
         }
      }
   };
};

describe("Table Advanced Pane Unit Test", () => {
   let fixture: ComponentFixture<TableAdvancedPane>;
   let advancedPane: TableAdvancedPane;

   beforeEach(async(() => {
      let modelService = { getModel: jest.fn() };
      modelService.getModel.mockImplementation(() => observableOf([]));
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
            TableAdvancedPane, TipPane, TipCustomizeDialog, AlphaDropdown, FixedDropdownDirective, LargeFormFieldComponent
         ],
         providers: [
            NgbModal, DebounceService,
            {provide: ModelService, useValue: modelService},
            {provide: UIContextService, useValue: uiContextService}
         ],
         schemas: [NO_ERRORS_SCHEMA]
      });
      TestBed.compileComponents();
      fixture = TestBed.createComponent(TableAdvancedPane);
      advancedPane = <TableAdvancedPane> fixture.componentInstance;
      advancedPane.model = createModel();
      fixture.detectChanges();
   }));

   it("test disable and enable status on advanced pane", (done) => {
      let shrinkToFit: HTMLInputElement = fixture.nativeElement.querySelector(".shrinkToFit_id input[type=checkbox]");
      let enableAdhocEdit: HTMLInputElement = fixture.nativeElement.querySelector(".enableAdhoc_id input[type=checkbox]");
      let emTable: HTMLInputElement = fixture.nativeElement.querySelector(".embeddedTable_id input[type=checkbox]");
      let addRow: HTMLInputElement = fixture.nativeElement.querySelector(".addRow_id input[type=checkbox]");
      let deleteRow: HTMLInputElement = fixture.nativeElement.querySelector(".delRow_id input[type=checkbox]");
      let editRow: HTMLInputElement = fixture.nativeElement.querySelector(".editRow_id input[type=checkbox]");

      expect(shrinkToFit.getAttribute("ng-reflect-is-disabled")).toEqual("false");
      expect(enableAdhocEdit.getAttribute("ng-reflect-is-disabled")).toEqual("false");
      expect(emTable.getAttribute("ng-reflect-is-disabled")).toEqual("false");
      expect(addRow.getAttribute("ng-reflect-is-disabled")).toEqual("true");
      expect(deleteRow.getAttribute("ng-reflect-is-disabled")).toEqual("true");
      expect(editRow.getAttribute("ng-reflect-is-disabled")).toEqual("true");

      let enableTblEdit: HTMLInputElement =  fixture.nativeElement.querySelector(".enableTableEdit_id input[type=checkbox]");
      enableTblEdit.checked = true;
      enableTblEdit.dispatchEvent(new Event("change"));
      fixture.detectChanges();

      fixture.whenStable().then(() => {
         enableAdhocEdit = fixture.nativeElement.querySelector(".enableAdhoc_id input[type=checkbox]");
         addRow = fixture.nativeElement.querySelector(".addRow_id input[type=checkbox]");
         deleteRow = fixture.nativeElement.querySelector(".delRow_id input[type=checkbox]");
         editRow = fixture.nativeElement.querySelector(".editRow_id input[type=checkbox]");

         expect(enableAdhocEdit.getAttribute("ng-reflect-is-disabled")).toEqual("true");
         expect(addRow.getAttribute("ng-reflect-is-disabled")).toEqual("false");
         expect(deleteRow.getAttribute("ng-reflect-is-disabled")).toEqual("false");
         expect(editRow.getAttribute("ng-reflect-is-disabled")).toEqual("false");
         expect(addRow.checked).toBeTruthy();
         expect(deleteRow.checked).toBeTruthy();
         expect(editRow.checked).toBeTruthy();

         done();
      });
   });

});
