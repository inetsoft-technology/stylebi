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
import { HttpClientTestingModule } from "@angular/common/http/testing";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { ConditionExpression } from "../../../common/data/condition/condition-expression";
import { TestUtils } from "../../../common/test/test-utils";
import { ComponentTool } from "../../../common/util/component-tool";
import { ConditionDialogService } from "../../../widget/condition/condition-dialog.service";
import { ConditionPane } from "../../../widget/condition/condition-pane.component";
import { ConditionPipe } from "../../../widget/condition/condition.pipe";
import { JunctionOperatorPipe } from "../../../widget/condition/junction-operator.pipe";
import { LargeFormFieldComponent } from "../../../widget/large-form-field/large-form-field.component";
import { OrderModel } from "../../data/table/order-model";
import { NameInputDialog } from "../name-input-dialog.component";
import { CalcNamedGroupDialog } from "./calc-named-group-dialog.component";
import mock = jest.mock;
import { ExpertNamedGroupDialog } from "./expert-named-group-dialog.component";
import { NamedGroupInfo } from "../../data/named-group-info";

describe("Expert Named Group Dialog Unit Test", () => {
   let createNamedGroupIfno: () => NamedGroupInfo = () => {
      return {
         conditions: [],
         groups: [],
         name: null,
         type: 0
      };
   };
   let createConditionExpression: (name: string, hasCons?: boolean) => ConditionExpression = (name: string, hasCons?: boolean) => {
      let conditions: ConditionExpression = {
         name: name,
         list: []
      };
      if(!!hasCons) {
         conditions.list = [{
            jsonType: "condition",
            field: null,
            operation: null,
            values: [],
            level: 0,
            equal: false,
            negated: false
         }];
      }
      return conditions;
   };

   let http: any;
   let modalService: any;
   let fixture: ComponentFixture<ExpertNamedGroupDialog>;
   let expertNamedGroupDialog: ExpertNamedGroupDialog;
   let conditionDialogService: ConditionDialogService;

   beforeEach(async(() => {
      http = {};
      modalService = { open: jest.fn() };
      const mockConditionDialogService = {
         dirtyCondition: null,
         checkDirtyConditions: jest.fn(() => false)
      };

      TestBed.configureTestingModule({
         imports: [
            FormsModule, ReactiveFormsModule, NgbModule, HttpClientTestingModule
         ],
         declarations: [
            ExpertNamedGroupDialog, ConditionPane, LargeFormFieldComponent, NameInputDialog, ConditionPipe, JunctionOperatorPipe
         ],
         providers: [
            { provide: NgbModal, useValue: modalService },
            { provide: ConditionDialogService, useValue: mockConditionDialogService }
         ],
         schemas: [NO_ERRORS_SCHEMA]
      }).compileComponents();
      conditionDialogService = TestBed.inject(ConditionDialogService);
   }));

   it("Should dispaly warning information if there is no group condition", () => {
      let info = createNamedGroupIfno();
      info.conditions = [createConditionExpression("g1")];
      fixture = TestBed.createComponent(ExpertNamedGroupDialog);
      fixture.componentInstance.namedGroupInfo = info;
      fixture.componentInstance.field = TestUtils.createMockDataRef("state");
      fixture.componentInstance.editing = false;
      fixture.detectChanges();

      let msg: Element = fixture.nativeElement.querySelector(".danger_emptygroup_id");
      let okButton: HTMLButtonElement = fixture.nativeElement.querySelector("button.ok_id");
      expect(msg).not.toBeNull();
      expect(okButton.disabled).toBeTruthy();

      let fixture1 = TestBed.createComponent(ExpertNamedGroupDialog);
      expertNamedGroupDialog = <ExpertNamedGroupDialog>fixture1.componentInstance;
      info.conditions = [createConditionExpression("g1", true)];
      expertNamedGroupDialog.namedGroupInfo = info;
      expertNamedGroupDialog.field = TestUtils.createMockDataRef("state");
      expertNamedGroupDialog.editing = false;
      fixture1.detectChanges();

      msg = fixture1.nativeElement.querySelector(".danger_emptygroup_id");
      okButton = fixture1.nativeElement.querySelector("button.ok_id");
      expect(msg).toBeNull();
      expect(okButton.hasAttribute("disabled")).toBeFalsy();
   });

   //Bug #20279
   it("should pop up warning if there is empty group", () => {
      let info = createNamedGroupIfno();
      info.conditions = [createConditionExpression("g1"), createConditionExpression("g2", true)];
      fixture = TestBed.createComponent(ExpertNamedGroupDialog);
      expertNamedGroupDialog = <ExpertNamedGroupDialog>fixture.componentInstance;
      expertNamedGroupDialog.namedGroupInfo = info;
      expertNamedGroupDialog.field = TestUtils.createMockDataRef("state");
      expertNamedGroupDialog.editing = false;
      fixture.detectChanges();

      let msg: Element = fixture.nativeElement.querySelector(".danger_emptygroup_id");
      let okButton: HTMLButtonElement = fixture.nativeElement.querySelector("button.ok_id");
      expect(msg).not.toBeNull();
      expect(okButton.disabled).toBeTruthy();
   });

   //Bug #20144
   it("Duplicate group name issue", () => {
      let info = createNamedGroupIfno();
      info.conditions = [createConditionExpression("g1")];
      expertNamedGroupDialog = new ExpertNamedGroupDialog(http, modalService, conditionDialogService);
      expertNamedGroupDialog.namedGroupInfo = info;
      let showDialog = jest.spyOn(ComponentTool, "showDialog");
      showDialog.mockImplementation(() => new NameInputDialog());
      expertNamedGroupDialog.ngOnInit();
      // expect(expertNamedGroupDialog.getGroupName()).toContain("g1");

      let getGroupName = jest.spyOn(expertNamedGroupDialog, "getGroupName");
      expertNamedGroupDialog.addGroup();
      expect(showDialog).toHaveBeenCalled();
      expect(getGroupName).toHaveBeenCalled();
   });

   //Bug #20289
   it("Rename group name", () => {
      let nameInputDialog = new NameInputDialog();
      let showDialog = jest.spyOn(ComponentTool, "showDialog");
      showDialog.mockImplementation(() => nameInputDialog);

      let condExp1 = {name: "A1", list: []};
      let condExp2 = {name: "A2", list: []};
      expertNamedGroupDialog = new ExpertNamedGroupDialog(http, modalService, conditionDialogService);
      expertNamedGroupDialog.groups = [condExp1, condExp2];
      expertNamedGroupDialog.selectGroup(condExp1);
      expertNamedGroupDialog.renameGroup();

      // expect(nameInputDialog.existedNames[0]).toBe("A1");
      expect(nameInputDialog.existedNames[0]).toBe("A2");
      expect(nameInputDialog.inputName).toBe("A1");
   });
});