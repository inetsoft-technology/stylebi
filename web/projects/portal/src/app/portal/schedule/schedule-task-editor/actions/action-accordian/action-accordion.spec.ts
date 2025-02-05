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
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormGroup, FormsModule, ReactiveFormsModule } from "@angular/forms";
import { By } from "@angular/platform-browser";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { BehaviorSubject, Observable, Subject } from "rxjs";
import { GeneralActionModel } from "../../../../../../../../shared/schedule/model/general-action-model";
import { TaskActionPaneModel } from "../../../../../../../../shared/schedule/model/task-action-pane-model";
import { ScheduleUsersService } from "../../../../../../../../shared/schedule/schedule-users.service";
import { ComponentTool } from "../../../../../common/util/component-tool";
import { AssetTreeComponent } from "../../../../../widget/asset-tree/asset-tree.component";
import { DateValueEditorComponent } from "../../../../../widget/date-type-editor/date-value-editor.component";
import { TimeInstantValueEditorComponent } from "../../../../../widget/date-type-editor/time-instant-value-editor.component";
import { TimeValueEditorComponent } from "../../../../../widget/date-type-editor/time-value-editor.component";
import { TimepickerComponent } from "../../../../../widget/date-type-editor/timepicker.component";
import { VariableCollectionSelector } from "../../../../../widget/dialog/variable-collection-selector/variable-collection-selector.component";
import { VariableInputDialog } from "../../../../../widget/dialog/variable-input-dialog/variable-input-dialog.component";
import { VariableValueEditor } from "../../../../../widget/dialog/variable-list-dialog/variable-value-editor/variable-value-editor.component";
import { EnterSubmitDirective } from "../../../../../widget/directive/enter-submit.directive";
import { EmailAddrDialog } from "../../../../../widget/email-dialog/email-addr-dialog.component";
import { EmbeddedEmailPane } from "../../../../../widget/email-dialog/embedded-email-pane.component";
import { QueryEmailPane } from "../../../../../widget/email-dialog/query-email-pane.component";
import { GenericSelectableList } from "../../../../../widget/generic-selectable-list/generic-selectable-list.component";
import { IdentityTreeComponent } from "../../../../../widget/identity-tree/identity-tree.component";
import { ShuffleListComponent } from "../../../../../widget/shuffle-list/shuffle-list.component";
import { TooltipDirective } from "../../../../../widget/tooltip/tooltip.directive";
import { TreeNodeComponent } from "../../../../../widget/tree/tree-node.component";
import { TreeSearchPipe } from "../../../../../widget/tree/tree-search.pipe";
import { TreeComponent } from "../../../../../widget/tree/tree.component";
import { AddParameterDialog } from "../../add-parameter-dialog/add-parameter-dialog.component";
import { ParameterTable } from "../../parameter-table/parameter-table.component";
import { ActionAccordion } from "./action-accordion.component";
import { ValueTypes } from "../../../../../vsobjects/model/dynamic-value-model";

describe("Action Accordion Unit Test", () => {
   const createRepletActionModel: () => GeneralActionModel = () => {
      return {
         label: "test action",
         actionClass: "GeneralActionModel",
         actionType: "RepletAction",
         bundledAsZip: false,
         deliverEmailsEnabled: false,
         folderPermission: true,
         format: "PDF",
         fromEmail: "reportserver@inetsoft.com",
         notificationEnabled: false,
         printOnServerEnabled: false,
         saveToServerEnabled: false,
         ccAddress: "",
         bccAddress: ""
      };
   };

   const createVSActionModel: () => GeneralActionModel = () => {
      return {
         label: "test action",
         actionClass: "GeneralActionModel",
         actionType: "ViewsheetAction",
         bundledAsZip: false,
         deliverEmailsEnabled: false,
         format: "Excel",
         fromEmail: "reportserver@inetsoft.com",
         emailMatchLayout: true,
         notificationEnabled: false,
         sheet: "1^128^__NULL__^table1",
         printOnServerEnabled: false,
         saveToServerEnabled: false,
         ccAddress: "",
         bccAddress: ""
      };
   };

   const createBurstActionModel: () => GeneralActionModel = () => {
      return {
         label: "test action",
         actionClass: "GeneralActionModel",
         actionType: "BurstAction",
         bundledAsZip: false,
         deliverEmailsEnabled: false,
         format: "PDF",
         fromEmail: "reportserver@inetsoft.com",
         notificationEnabled: false,
         printOnServerEnabled: null,
         saveToServerEnabled: null,
         ccAddress: "",
         bccAddress: ""
      };
   };

   const createModel: () => TaskActionPaneModel = () => {
      return {
         securityEnabled: true,
         emailButtonVisible: false,
         endUser: null,
         administrator: false,
         defaultFromEmail: "reportserver@inetsoft.com",
         fromEmailEnabled: true,
         viewsheetEnabled: true,
         notificationEmailEnabled: true,
         saveToDiskEnabled: true,
         emailDeliveryEnabled: true,
         expandEnabled: true,
         cvsEnabled: false,
         action: createRepletActionModel(),
         actions: [],
         userDefinedClasses: [],
         userDefinedClassLabels: [],
         dashboardMap: {},
         printers: ["Microsoft XPS Document Writer"],
         folderPaths: ["My Reports", "/", "Examples", "Tutorial"],
         folderLabels: ["My Reports", "/", "Dashboards", "Tutorial"],
         mailFormats: [{type: "PDF", label: "PDF"}],
         vsMailFormats: [{type: "PDF", label: "PDF"}],
         saveFileFormats: [{type: "9", label: "PDF"}],
         vsSaveFileFormats: [{type: "0", label: "Excel"}],
         fonts: []
      };
   };

   let ngbService = { open: jest.fn() };
   let deObservable = { debounceTime: jest.fn() };
   let scheduleUsersService = {
      init: jest.fn(),
      getOwners: jest.fn(() => new BehaviorSubject([]) ),
      getGroups: jest.fn(() => new BehaviorSubject([]) ),
      getRoles: jest.fn(() => new BehaviorSubject([]) ),
      getEmailUsers: jest.fn(() => new BehaviorSubject([]) ),
      getEmailGroups: jest.fn(() => new BehaviorSubject([]) ),
      getAdminName: jest.fn(() => new BehaviorSubject("admin") ),
   };
   deObservable.debounceTime.mockImplementation(() => new Subject());

   let fixture: ComponentFixture<ActionAccordion>;
   let actionAccordion: ActionAccordion;

   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [
            FormsModule, ReactiveFormsModule, NgbModule, HttpClientTestingModule
         ],
         declarations: [
            ActionAccordion, GenericSelectableList, ParameterTable,
            AddParameterDialog, EnterSubmitDirective, EmailAddrDialog, EmbeddedEmailPane,
            QueryEmailPane, IdentityTreeComponent, ShuffleListComponent,
            AssetTreeComponent, TreeComponent, TreeNodeComponent, TooltipDirective,
            TreeSearchPipe, VariableInputDialog, VariableValueEditor, VariableCollectionSelector,
            TimeInstantValueEditorComponent, TimeValueEditorComponent, DateValueEditorComponent,
            TimepickerComponent
         ],
         providers: [
            { provide: NgbModal, useValue: ngbService },
            { provide: Observable, useValue: deObservable },
            { provide: ScheduleUsersService, useValue: scheduleUsersService}
         ]
      });
      TestBed.compileComponents();

      fixture = TestBed.createComponent(ActionAccordion);
      actionAccordion = <ActionAccordion>fixture.componentInstance;
      actionAccordion.parentForm = new FormGroup({});
      actionAccordion.model = createModel();
      actionAccordion.action = createRepletActionModel();
      fixture.detectChanges();
   }));

   //Bug #19603 clear all parameters
   //Bug #21202 should display correct info when asset has parameter
   it("check clear all parameters", () => {
      jest.spyOn(ComponentTool, "showConfirmDialog").mockImplementation(() => Promise.resolve("ok"));
      actionAccordion.parameters = [
         {name: "a", type: "string", value: {value: "a", type: ValueTypes.VALUE}, array: false},
         {name: "b", type: "string", value: {value: "b", type: ValueTypes.VALUE}, array: false}];
      fixture.detectChanges();

      let clearAllBtn = fixture.debugElement.query(By.css("parameter-table .clear_all_id")).nativeElement;
      clearAllBtn.click();
      fixture.detectChanges();

      let parameterTable = fixture.debugElement.query(By.css("parameter-table tr td")).nativeElement;
      expect(parameterTable.textContent.trim()).toBe("_#(Empty)");

      actionAccordion.parameters = [];
      actionAccordion.requiredParameters = ["A"];
      fixture.detectChanges();

      let infos = fixture.nativeElement.querySelectorAll("div.py-2 span");
      expect(infos[0].textContent.trim()).toBe("_#(Required Parameters):");
      expect(infos[1].textContent.trim()).toBe("A");
   });

   //Bug #19524 Deliver to Emails default status
   //Bug #19792 options for dashboard 'deliver to emails'
   //Bug #21304 should not display email browser button when set in em
   //Bug #21313 should deal with burst action
   xit("check Deliver to Emails status", () => {
      let match = fixture.debugElement.query(By.css("label.match-layout-id"));
      let expand = fixture.debugElement.query(By.css("label.expand-tables-and-charts-id"));
      let from = fixture.debugElement.query(By.css("input#from")).nativeElement;
      let format = fixture.debugElement.query(By.css("select#format")).nativeElement;
      let emailBtn = fixture.debugElement.query(By.css("button.btn.input-group-addon"));

      expect(match).toBeNull();
      expect(emailBtn).toBeNull();
      expect(expand).toBeNull();
      expect(from.getAttribute("ng-reflect-model")).toContain("reportserver@inetsoft.com");
      expect(format.getAttribute("ng-reflect-model")).toContain("PDF");

      actionAccordion.action = createVSActionModel();
      actionAccordion.model.actions = [createVSActionModel()];
      fixture.detectChanges();

      let match1 = fixture.debugElement.query(By.css("label.match-layout-id")).nativeElement;
      let expand1 = fixture.debugElement.query(By.css("label.expand-tables-and-charts-id")).nativeElement;
      expect(match1.textContent.trim()).toBe("Match Layout");
      expect(expand1.textContent.trim()).toBe("Expand Tables and Charts");

      actionAccordion.action = createBurstActionModel();
      actionAccordion.model.actions = [createBurstActionModel()];
      fixture.detectChanges();

      let to = fixture.debugElement.query(By.css("div.grayed-out-field")).nativeElement;
      expect(to.textContent.trim()).toBe(
         "user name(:email address)[,user name(:email address)]");
   });

   //Bug #21295 should get correct highlight name for alert
   it("should get correct highlight name for alert", () => {
      actionAccordion.highlights = [{
         element: "TableView1",
         highlight: "highlight1 (2)",
         condition: "[STATE] [is] [equal to] [NJ]",
         count: 1
      }];
      fixture.detectChanges()

      let underHighlight = fixture.nativeElement.querySelector(
         "input[name=underHighlightCondition]");
      underHighlight.click();
      fixture.detectChanges();

      let highlightCheck = fixture.nativeElement.querySelector(
         "tr td input[type=checkbox]");
      highlightCheck.click();
      fixture.detectChanges();

      expect(actionAccordion.action.highlightAssemblies[0]).toBe("TableView1");
      expect(actionAccordion.action.highlightNames[0]).toBe("highlight1");
   });
});