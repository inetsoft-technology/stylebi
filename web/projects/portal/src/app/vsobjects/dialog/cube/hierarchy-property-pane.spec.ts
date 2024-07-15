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
import { TestBed, ComponentFixture } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule} from "@angular/forms";
import { HierarchyPropertyPane } from "./hierarchy-property-pane.component";
import { HierarchyPropertyPaneModel } from "../../model/hierarchy-property-pane-model";
import { HierarchyEditor } from "./hierarchy-editor.component";
import { HierarchyEditorModel } from "../../model/hierarchy-editor-model";
import { DateLevelExamplesService } from "../../../common/services/date-level-examples.service";
import { FeatureFlagsService } from "../../../../../../shared/feature-flags/feature-flags.service";
import { of as observableOf } from "rxjs";

let createModel: () => HierarchyPropertyPaneModel = () => {
   return {
      isCube: false,
      hierarchyEditorModel: <HierarchyEditorModel> {},
      columnList: [{
         entity: "",
         attribute: "",
         dataType: "string",
         name: "state",
         view: "",
         table: "customers",
         alias: "",
         refType: 0,
         properties: null,
         path: ""
      },
      {
         entity: "",
         attribute: "",
         dataType: "boolean",
         name: "reseller",
         view: "",
         table: "customers",
         alias: "",
         refType: 1,
         properties: null,
         path: ""
      }],
      dimensions: [{
         members: [{
            option: 0,
            dataRef: {
               entity: "",
               attribute: "",
               dataType: "integer",
               name: "customer_id",
               view: "",
               table: "customers",
               alias: "",
               refType: 0,
               properties: null,
               path: ""
            }
         }]
      }]
   };
};

let hierarchyPropPane: HierarchyPropertyPane;
let fixture: ComponentFixture<HierarchyPropertyPane>;

describe("Hierarchy Property Pane Unit Test", () => {
   beforeEach(() => {
      let examplesService = {loadDateLevelExamples: jest.fn(() => observableOf())};

      TestBed.configureTestingModule({
         imports: [
            FormsModule, ReactiveFormsModule
         ],
         declarations: [
            HierarchyPropertyPane, HierarchyEditor
         ],
         providers: [
            {provide: DateLevelExamplesService, useValue: examplesService}
         ]
      });
      TestBed.compileComponents();
      fixture = TestBed.createComponent(HierarchyPropertyPane);
      hierarchyPropPane = fixture.componentInstance;
      hierarchyPropPane.model = createModel();
   });
   //Bug 18600
   it("test clear button function", () => {
      fixture.detectChanges();
      let hierarchyColumns: any = fixture.nativeElement.querySelectorAll(".column_item_id");
      let hierarchyContents: any = fixture.nativeElement.querySelectorAll(".hierarchy_item_id");
      let clearButton: HTMLButtonElement = fixture.nativeElement.querySelector("button.btn-clear_id");
      fixture.whenStable().then(() => {
         expect(hierarchyColumns.length).toEqual(2);
         expect(hierarchyContents.length).toEqual(1);
      });

      clearButton.click();
      fixture.detectChanges();
      fixture.whenStable().then(() => {
         hierarchyContents = fixture.nativeElement.querySelectorAll(".hierarchy_item_id");
         expect(hierarchyContents.length).toEqual(0);
         let dateLevelRdios = fixture.nativeElement.querySelector("hierarchy-editor fieldset");
         expect(dateLevelRdios.disabled).toBeTruthy();
      });
   });

});
