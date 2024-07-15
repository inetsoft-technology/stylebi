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
import { Component, Input, NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { ListValuesPaneModel } from "../../data/vs/list-values-pane-model";

import { ListValuesPane } from "./list-values-pane.component";
import { FeatureFlagsService } from "../../../../../../shared/feature-flags/feature-flags.service";

let createListModel: () => ListValuesPaneModel = () => {
   return {
         comboBoxEditorModel: {
            calendar: false,
            dataType: "string",
            embedded: true,
            query: true,
            noDefault: true,
            type: "ComboBox",
            valid: true,
            defaultValue: null,
            selectionListDialogModel: {
               selectionListEditorModel: {
                  column: "reseller",
                  dataType: "boolean",
                  form: false,
                  localizedTables: ["model"],
                  ltableDescriptions: [""],
                  table: "model",
                  tables: ["model"],
                  value: "reseller"
               },
            },
            variableListDialogModel: {
               dataType: "string",
               labels: ["A", "B"],
               values: ["A", "B"]
            }
         },
         embeddedDataDown: false,
         sortType: 1
      };
};

@Component({
   selector: "combo-box-editor",
   template: "<div></div>"
})
class ComboBoxEditor {
   @Input() model: any;
   @Input() runtimeId: string;
   @Input() enableDataType: boolean = true;
   @Input() showCalendar: boolean = false;
}

describe("list value component unit case: ", () => {
   let fixture: ComponentFixture<ListValuesPane>;
   let listPane: ListValuesPane;
   let featureFlagsService = { isFeatureEnabled: jest.fn() };

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [ReactiveFormsModule, FormsModule, NgbModule],
         providers: [
            { provide: FeatureFlagsService, useValue: featureFlagsService},
         ],
         declarations: [ListValuesPane, ComboBoxEditor],
         schemas: [NO_ERRORS_SCHEMA]
      }).compileComponents();

      fixture = TestBed.createComponent(ListValuesPane);
   });

   //Bug #20589
   it("should disabled list embedded checkbox", () => {
      listPane = <ListValuesPane>fixture.componentInstance;
      listPane.model = createListModel();

      fixture.detectChanges();
      let listEm = fixture.nativeElement.querySelector(".list-embedded_id");
      expect(listEm.getAttribute("ng-reflect-is-disabled")).toBeTruthy();
   });
});