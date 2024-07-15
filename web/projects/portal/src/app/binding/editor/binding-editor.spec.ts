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
import { HttpClient } from "@angular/common/http";
import {
   HttpClientTestingModule,
   HttpTestingController
} from "@angular/common/http/testing";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { of as observableOf } from "rxjs";
import { UIContextService } from "../../common/services/ui-context.service";
import { DropDownTestModule } from "../../common/test/test-module";
import { TestUtils } from "../../common/test/test-utils";
import { FixedDropdownDirective } from "../../widget/fixed-dropdown/fixed-dropdown.directive";
import { DynamicComboBox } from "../../widget/dynamic-combo-box/dynamic-combo-box.component";
import { ModelService } from "../../widget/services/model.service";
import { CrosstabBindingModel } from "../data/table/crosstab-binding-model";
import { BindingService } from "../services/binding.service";
import { TableEditorService } from "../services/table/table-editor.service";
import { SidebarTab } from "../widget/binding-tree/data-editor-tab-pane.component";
import { BindingEditor } from "./binding-editor.component";
import { CrosstabOption } from "./table/crosstab-option.component";

describe("Binding Editor Component Unit Test", () => {
   let createMockCrosstabBindingModel: () => CrosstabBindingModel = () => {
      let bindingModel =  TestUtils.createMockCrosstabBindingModel();
      bindingModel.aggregates = [TestUtils.createMockBAggregateRef("customer_id")];
      bindingModel.option = {
         colTotalVisibleValue: "false",
         rowTotalVisibleValue: "false",
         percentageByValue: "1",
         summarySideBySide: false
      };
      return bindingModel;
   };
   let uiContextService = {
      isVS: jest.fn(),
      isAdhoc: jest.fn()
   };
   let modelService = { getModel: jest.fn(() => observableOf({})) };

   let fixture: ComponentFixture<BindingEditor>;
   let bindingEditor: BindingEditor;
   let httpClient: HttpClient;
   let httpTestingController: HttpTestingController;

   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [
            FormsModule, ReactiveFormsModule, NgbModule, HttpClientTestingModule,
            DropDownTestModule
         ],
         declarations: [
            BindingEditor, CrosstabOption, DynamicComboBox, FixedDropdownDirective
         ],
         providers: [
            BindingService, TableEditorService,
            {
               provide: UIContextService, useValue: uiContextService
            },
            {
               provide: ModelService, useValue: modelService
            }
         ],
         schemas: [NO_ERRORS_SCHEMA]
      }).compileComponents();

      httpClient = TestBed.inject(HttpClient);
      httpTestingController = TestBed.inject(HttpTestingController);
   }));

   it("Crosstab should not have a percent by option", () => {
      uiContextService.isVS.mockImplementation(() => true);
      fixture = TestBed.createComponent(BindingEditor);
      bindingEditor = <BindingEditor>fixture.componentInstance;
      bindingEditor.objectModel = TestUtils.createMockVSObjectModel("VSCrosstab", "Crosstab1");
      bindingEditor.currentFormat = TestUtils.createMockFromatInfo();
      bindingEditor.linkUri = "/sree/";
      bindingEditor.runtimeId = "crosstab-15096061975720";
      bindingEditor.assemblyName = "Crosstab1";
      bindingEditor.objectType = "VSCrosstab";
      bindingEditor.formatPaneDisabled = false;
      bindingEditor.bindingModel = createMockCrosstabBindingModel();
      bindingEditor.variableValues = [];
      fixture.detectChanges();

      const percentByOption = fixture.nativeElement.querySelector(".percentBy_label_id");
      expect(percentByOption).toBe(null);
   });

   //Bug #20245
   it("should show table option on format tab", () => {
      uiContextService.isVS.mockImplementation(() => false);
      fixture = TestBed.createComponent(BindingEditor);
      bindingEditor = <BindingEditor>fixture.componentInstance;
      bindingEditor.bindingModel = TestUtils.createMockTableBindingModel();
      bindingEditor.currentFormat = TestUtils.createMockFromatInfo();
      bindingEditor.formatPaneDisabled = false;
      bindingEditor.assemblyName = "Table1";
      bindingEditor.objectType = "table";
      fixture.detectChanges();

      bindingEditor.switchTab(SidebarTab.FORMAT_PANE);
      expect(bindingEditor.formatPaneVisible).toBeTruthy();
   });

   //for Bug #20163
   it("current format status should be right", (done) => {
      uiContextService.isVS.mockImplementation(() => true);
      fixture = TestBed.createComponent(BindingEditor);
      bindingEditor = <BindingEditor>fixture.componentInstance;
      bindingEditor.objectModel = TestUtils.createMockVSObjectModel("VSChart", "Chart1");
      bindingEditor.currentFormat = TestUtils.createMockFromatInfo();
      bindingEditor.objectType = "VSChart";
      fixture.detectChanges();

      bindingEditor.onUpdateData.subscribe((action: string) => {
         expect(action).toEqual("getCurrentFormat");

         done();
      });
      bindingEditor.updateData("getCurrentFormat");
      expect(bindingEditor.hideFormatPane).toBeFalsy();
   });
});
