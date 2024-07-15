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
import { of as observableOf } from "rxjs";
import { BindingTreeService } from "../../../binding/widget/binding-tree/binding-tree.service";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { FixedDropdownService } from "../../../widget/fixed-dropdown/fixed-dropdown.service";
import { DebounceService } from "../../../widget/services/debounce.service";
import { ModelService } from "../../../widget/services/model.service";
import { VSWizardBindingTreeService } from "../../services/vs-wizard-binding-tree.service";
import { WizardBindingTree } from "./wizard-binding-tree.component";

describe("WizardBindingTre", () => {
   let component: WizardBindingTree;
   let fixture: ComponentFixture<WizardBindingTree>;

   beforeEach(async(() => {
      const bindingTreeService = {
         refreshSubject: observableOf(null),
         recommenderSubject: observableOf(null),
         refresh: jest.fn()
      };
      const viewsheetClientService = {
         sendEvent: jest.fn()
      };
      const dropdownService = {};
      const debounceService = {
         debounce: jest.fn()
      };
      const treeService = {
         getTableName: jest.fn(() => "Table")
      };
      const modelService = {};
      TestBed.configureTestingModule({
         declarations: [
            WizardBindingTree
         ],
         providers: [
            { provide: VSWizardBindingTreeService, useValue: bindingTreeService },
            { provide: ViewsheetClientService, useValue: viewsheetClientService },
            { provide: FixedDropdownService, useValue: dropdownService },
            { provide: DebounceService, useValue: debounceService },
            { provide: BindingTreeService, useValue: treeService },
            { provide: ModelService, useValue: modelService }
         ],
         schemas: [
            NO_ERRORS_SCHEMA
         ]
      }).compileComponents();
   }));

   beforeEach(() => {
      fixture = TestBed.createComponent(WizardBindingTree);
      component = fixture.componentInstance;
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });
});
