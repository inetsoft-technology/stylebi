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
import { HttpClientTestingModule } from "@angular/common/http/testing";
import { Component, NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { By } from "@angular/platform-browser";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { DataRef } from "../../common/data/data-ref";
import { DropDownTestModule } from "../../common/test/test-module";
import { TestUtils } from "../../common/test/test-utils";
import { FixedDropdownDirective } from "../fixed-dropdown/fixed-dropdown.directive";
import { DragService } from "../services/drag.service";
import { TreeNodeComponent } from "../tree/tree-node.component";
import { TreeSearchPipe } from "../tree/tree-search.pipe";
import { TreeComponent } from "../tree/tree.component";
import { ConditionFieldComboModel } from "./condition-field-combo-model";
import { ConditionFieldComboComponent } from "./condition-field-combo.component";
import { ConditionFieldComboListComponent } from "./condition-field-combo-list.component";
import { SearchDataRefPipe } from "../pipe/search-data-ref.pipe";

let createModel: () => ConditionFieldComboModel = () => {
   return {
      list: null,
      tree: null
   };
};

@Component({
   selector: "test-component",
   template: `<condition-field-combo [field]="field"
                                     [fieldsModel]="fieldsModel"></condition-field-combo>`
})
class TestComponent {
   field: DataRef;
   fieldsModel: ConditionFieldComboModel;
}

describe ("condition field combo tree test", () => {
   let fixture: ComponentFixture<ConditionFieldComboComponent>;
   let conCombo: ConditionFieldComboComponent;
   let wizard: any;

   beforeEach(async(() => {
      wizard = {};
      TestBed.configureTestingModule({
         imports: [
            FormsModule,
            ReactiveFormsModule,
            HttpClientTestingModule,
            NgbModule,
            DropDownTestModule
         ],
         declarations: [
            ConditionFieldComboComponent,
            ConditionFieldComboListComponent,
            TreeComponent,
            TreeNodeComponent,
            TreeSearchPipe,
            SearchDataRefPipe,
            FixedDropdownDirective,
            TestComponent
         ],
         providers: [
            DragService
            //{provide: WizardService, useValue: wizard}
         ],
         schemas: [NO_ERRORS_SCHEMA]
      })
      .compileComponents();

      fixture = TestBed.createComponent(ConditionFieldComboComponent);
      conCombo = <ConditionFieldComboComponent>fixture.componentInstance;
      conCombo.displayList = true;
   }));

   it("after click the class of dropdownDiv will add 'show'", () => {
      conCombo.fieldsModel = { list: [{view: "001"}, {view: "002"}, {view: "003"}], tree: {children: []} };
      conCombo.field = null;
      conCombo.grayedOutFields = [];
      conCombo.addNoneItem = true;
      conCombo.enabled = true;
      fixture.detectChanges();

      let fixedDropdown = document.getElementsByTagName("fixed-dropdown")[0];
      let toggleDiv = fixture.debugElement.query(By.css("div.condition-field-combo-toggle")).nativeElement;
      toggleDiv.click();
      fixture.detectChanges();

      expect(fixedDropdown).not.toBeNull();
   });

   //Bug #20727
   it("after click switch icon will switch to list or tree", () => {
      const model = createModel();
      model.list = [{view: "A"}, {view: "B"}, {view: "C"}, {view: "D"}];
      model.tree = {children: [
         {label: "A", children: []},
         {label: "B", children: []}
      ]};
      conCombo.fieldsModel = model;
      fixture.detectChanges();

      const toggleDiv = fixture.debugElement.query(By.css("div.condition-field-combo-toggle")).nativeElement;
      toggleDiv.click();
      fixture.detectChanges();

      const listDiv = document.querySelector(
         "div.condition-field-combo-dropdown .condition-field-combo-list-item");
      expect(listDiv).not.toBeNull();

      const switchBtn = fixture.debugElement.query(By.css("button.field-list-icon")).nativeElement;
      switchBtn.click();

      fixture.detectChanges();
      const tree = document.querySelector(
         "fixed-dropdown div.condition-field-combo-dropdown tree");
      const labels = document.querySelectorAll(".tree-node-label");
      expect(tree).not.toBeNull();
      expect(labels[0].textContent).toContain("None");
      expect(labels[1].textContent).toContain("A");
      expect(labels[2].textContent).toContain("B");
   });

   it("aliased field should be correctly selected", () => {
      const fixture1 = TestBed.createComponent(TestComponent);
      const testComponent: TestComponent = fixture1.componentInstance;
      testComponent.field = {
         attribute: "attribute",
         entity: "table",
         view: "table.attribute"
      };
      testComponent.fieldsModel = {
         list: [{
            attribute: "attribute",
            entity: "table",
            view: "alias"
         }],
         tree: {children: []}
      };

      fixture1.detectChanges();
      const conditionFieldComboComponent: ConditionFieldComboComponent =
         fixture1.debugElement.query(By.directive(ConditionFieldComboComponent)).componentInstance;
      expect(conditionFieldComboComponent.field.view === "alias")
         .toBeTruthy();
   });

   //Bug #19127 the aggregate column display wrong.
   it("should display right field item for aggregate field", () => {
      let field1 = TestUtils.createMockGroupRef("state");
      field1.classType = "GroupRef";
      field1.dataType = "string";
      field1.dgroup = 0;
      field1.refType = 0;
      field1.view = "state";
      let field2 = TestUtils.createMockAggregateRef("id");
      field2.classType = "AggregateRef";
      field2.dataType = "double";
      field2.formulaName = "Sum";
      field2.refType = 0;
      field2.view = "Sum(id)";
      let model = createModel();
      model.list = [field1, field2];
      model.tree = {
         children: [{
            children: [
               {
                  data: field1,
                  label: "Sum(id)",
                  leaf: true},
               {
                  data: field2,
                  label: "state",
                  leaf: true
               }
            ],
            label: "Query Field",
            leaf: false
         }]
      };

      conCombo.fieldsModel = model;
      conCombo.listModel = model.list;
      conCombo.treeModel = model.tree;
      fixture.detectChanges();

      const toggleDiv = fixture.debugElement.query(By.css("div.condition-field-combo-toggle")).nativeElement;
      toggleDiv.click();
      fixture.detectChanges();
      let items = document.querySelectorAll(
         "div.condition-field-combo-list-item");

      expect(items[2].textContent).toContain("Sum(id)");
   });
});
