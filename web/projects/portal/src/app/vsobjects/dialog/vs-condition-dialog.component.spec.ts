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
import { HttpClient, HttpResponse } from "@angular/common/http";
import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { Component, EventEmitter, Input, NO_ERRORS_SCHEMA, Output } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { of as observableOf } from "rxjs";
import { Condition } from "../../common/data/condition/condition";
import { VSConditionDialogModel } from "../../common/data/condition/vs-condition-dialog-model";
import { TestUtils } from "../../common/test/test-utils";
import { ComponentTool } from "../../common/util/component-tool";
import { isValidCondition } from "../../common/util/condition.util";
import { ConditionPane } from "../../widget/condition/condition-pane.component";
import { ConditionPipe } from "../../widget/condition/condition.pipe";
import { JunctionOperatorPipe } from "../../widget/condition/junction-operator.pipe";
import { ModelService } from "../../widget/services/model.service";
import { VSConditionDialog } from "./vs-condition-dialog.component";

@Component({
   selector: "condition-item-pane",
   template: "<div></div>"
})
class ConditionItemPane {
   @Input() subqueryTables: null;
   @Input() condition: Condition;
   @Output() conditionChange: EventEmitter<Condition> = new EventEmitter<Condition>();
   @Input() provider: any;
}

let createModel: () => VSConditionDialogModel = () => {
   let state = TestUtils.createMockDataRef("state");
   let id = TestUtils.createMockDataRef("id");
   id.dataType = "integer";
   let date = TestUtils.createMockDataRef("date");
   date.dataType = "timeInstant";

   return {
      tableName: "table",
      fields: [state, id, date],
      conditionList: []
   };
};

let checkTrap: (callback: () => void, conditionModel) => void = () => {
};

describe("vs condition dialog component", () => {
   let modelService: any;
   let modalService: any;
   let vsConditionDialog: VSConditionDialog;
   let fixture: ComponentFixture<VSConditionDialog>;
   let httpClient: HttpClient;
   let httpTestingController: HttpTestingController;

   beforeEach(() => {
      modelService = { sendModel: jest.fn(), text: jest.fn() };
      modalService = { open: jest.fn() };
      modelService.sendModel.mockImplementation(() => observableOf(new HttpResponse({body: null})));

      TestBed.configureTestingModule({
         imports: [ReactiveFormsModule, FormsModule, NgbModule, HttpClientTestingModule],
         declarations: [VSConditionDialog, ConditionPane, ConditionItemPane, ConditionPipe, JunctionOperatorPipe],
         providers: [
            {provide: ModelService, useValue: modelService},
            {provide: NgbModal, useValue: modalService}],
         schemas: [NO_ERRORS_SCHEMA]
      }).compileComponents();

      fixture = TestBed.createComponent(VSConditionDialog);
      vsConditionDialog = <VSConditionDialog>fixture.componentInstance;
      vsConditionDialog.runtimeId = "table1";
      vsConditionDialog.assemblyName = "TableView1";
      vsConditionDialog.variableValues = [];
      vsConditionDialog.model = createModel();
      vsConditionDialog.checkTrap = checkTrap;

      httpClient = TestBed.inject(HttpClient);
      httpTestingController = TestBed.inject(HttpTestingController);
   });

   //Bug #19008 pending Bug
   it("should pop up confirm dialog when no append condition", () => {
      let con1 = TestUtils.createMockCondition();
      let temp = {
         selectedIndex: null,
         condition: con1
      };
      fixture.detectChanges();
      vsConditionDialog.conditionChanged(temp);
      // select condition
      vsConditionDialog.conditionPane.condition = con1;
      fixture.detectChanges();

      let showConfirmDialog = jest.spyOn(ComponentTool, "showConfirmDialog");
      showConfirmDialog.mockImplementation(() => Promise.resolve("ok"));
      vsConditionDialog.ok();

      if(isValidCondition(con1)) {
         expect(showConfirmDialog).toHaveBeenCalled();
      }
   });

});