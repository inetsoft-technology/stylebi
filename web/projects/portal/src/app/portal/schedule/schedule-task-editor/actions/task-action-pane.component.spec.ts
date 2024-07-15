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
import { HttpClientTestingModule } from "@angular/common/http/testing";
import { Component, Input, NO_ERRORS_SCHEMA, ViewChild } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { ControlValueAccessor, FormGroup, FormsModule, ReactiveFormsModule } from "@angular/forms";
import { By } from "@angular/platform-browser";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { BehaviorSubject } from "rxjs";
import { GeneralActionModel } from "../../../../../../../shared/schedule/model/general-action-model";
import { TaskActionPaneModel } from "../../../../../../../shared/schedule/model/task-action-pane-model";
import { ScheduleUsersService } from "../../../../../../../shared/schedule/schedule-users.service";
import { TestUtils } from "../../../../common/test/test-utils";
import { ComponentTool } from "../../../../common/util/component-tool";
import { AssetTreeComponent } from "../../../../widget/asset-tree/asset-tree.component";
import { DateValueEditorComponent } from "../../../../widget/date-type-editor/date-value-editor.component";
import { TimeInstantValueEditorComponent } from "../../../../widget/date-type-editor/time-instant-value-editor.component";
import { TimeValueEditorComponent } from "../../../../widget/date-type-editor/time-value-editor.component";
import { TimepickerComponent } from "../../../../widget/date-type-editor/timepicker.component";
import { VariableCollectionSelector } from "../../../../widget/dialog/variable-collection-selector/variable-collection-selector.component";
import { VariableInputDialog } from "../../../../widget/dialog/variable-input-dialog/variable-input-dialog.component";
import { VariableValueEditor } from "../../../../widget/dialog/variable-list-dialog/variable-value-editor/variable-value-editor.component";
import { EnterClickDirective } from "../../../../widget/directive/enter-click.directive";
import { EnterSubmitDirective } from "../../../../widget/directive/enter-submit.directive";
import { EmailAddrDialog } from "../../../../widget/email-dialog/email-addr-dialog.component";
import { EmbeddedEmailPane } from "../../../../widget/email-dialog/embedded-email-pane.component";
import { QueryEmailPane } from "../../../../widget/email-dialog/query-email-pane.component";
import { GenericSelectableList } from "../../../../widget/generic-selectable-list/generic-selectable-list.component";
import { IdentityTreeComponent } from "../../../../widget/identity-tree/identity-tree.component";
import { NotificationsComponent } from "../../../../widget/notifications/notifications.component";
import { ReplaceAllPipe } from "../../../../widget/pipe/replace-all.pipe";
import { CSVConfigPane } from "../../../../widget/schedule/csv-config-pane.component";
import { ShuffleListComponent } from "../../../../widget/shuffle-list/shuffle-list.component";
import { TooltipDirective } from "../../../../widget/tooltip/tooltip.directive";
import { TreeNodeComponent } from "../../../../widget/tree/tree-node.component";
import { TreeSearchPipe } from "../../../../widget/tree/tree-search.pipe";
import { TreeComponent } from "../../../../widget/tree/tree.component";
import { PortalModelService } from "../../../services/portal-model.service";
import { AddParameterDialog } from "../add-parameter-dialog/add-parameter-dialog.component";
import { EditableTableComponent } from "../editable-table/editable-table.component";
import { ParameterTable } from "../parameter-table/parameter-table.component";
import { ActionAccordion } from "./action-accordian/action-accordion.component";
import { TaskActionPane } from "./task-action-pane.component";

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

const createModel: () => TaskActionPaneModel = () => {
   return {
      actions: [createVSActionModel()],
      adminVisible: false,
      administrator: true,
      dashboardMap: {"1^128^__NULL__^table1": "table1"},
      defaultFromEmail: "reportserver@inetsoft.com",
      fromEmailEnabled: true,
      emailDeliveryEnabled: true,
      emailButtonVisible: false,
      endUser: null,
      expandEnabled: true,
      folderPaths: ["My Reports", "/", "Examples", "Tutorial"],
      folderLabels: ["My Reports", "/", "Dashboards", "Tutorial"],
      cvsEnabled: false,
      mailFormats: [{type: "PDF", label: "PDF"}],
      notificationEmailEnabled: false,
      printers: ["Microsoft XPS Document Writer"],
      saveFileFormats: [{type: "9", label: "PDF"}],
      saveToDiskEnabled: true,
      securityEnabled: true,
      userDefinedClasses: [],
      userDefinedClassLabels: [],
      viewsheetEnabled: true,
      vsMailFormats: [{type: "PDF", label: "PDF"}],
      vsSaveFileFormats: [{type: "0", label: "Excel"}],
      fonts: []
   };
};

@Component({
   selector: "test-app",
   template: `<task-action-pane [model]="model" [taskName]="taskName" [parentForm]="form"></task-action-pane>`
})
class TestApp {
   @ViewChild(TaskActionPane, {static: true}) taskActionPane: TaskActionPane;
   model = createModel();
   taskName = "Task1";
   form = new FormGroup({});
}

@Component({
   selector: "ckeditor",
   template: "<div></div>"
})
class MockCkeditorComponent implements ControlValueAccessor {
   @Input()
   config: any;

   registerOnChange(fn: any): void {
   }

   registerOnTouched(fn: any): void {
   }

   setDisabledState(isDisabled: boolean): void {
   }

   writeValue(obj: any): void {
   }

}

describe("Task Action Pane Unit Test", () => {
   let portalModelService = {
      isDashboardEnabled: jest.fn(() => true),
      isReportEnabled: jest.fn(() => true)
   };
   let scheduleUsersService = {
      init: jest.fn(),
      getOwners: jest.fn(() => new BehaviorSubject([]) ),
      getGroups: jest.fn(() => new BehaviorSubject([]) ),
      getRoles: jest.fn(() => new BehaviorSubject([]) ),
      getEmailUsers: jest.fn(() => new BehaviorSubject([]) ),
      getEmailGroups: jest.fn(() => new BehaviorSubject([]) ),
      getAdminName: jest.fn(() => new BehaviorSubject("admin") ),
   };

   let fixture: ComponentFixture<TestApp>;
   let taskActionPane: TaskActionPane;

   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [
            FormsModule, ReactiveFormsModule, NgbModule, HttpClientTestingModule
         ],
         declarations: [
            TestApp, ReplaceAllPipe, TaskActionPane, ActionAccordion, GenericSelectableList,
            ParameterTable, AddParameterDialog, EnterSubmitDirective,
            EditableTableComponent, EmailAddrDialog, EmbeddedEmailPane, QueryEmailPane,
            IdentityTreeComponent, ShuffleListComponent, AssetTreeComponent, TreeComponent,
            TreeNodeComponent, TooltipDirective, TreeSearchPipe, VariableInputDialog,
            VariableValueEditor, VariableCollectionSelector, TimeInstantValueEditorComponent,
            TimeValueEditorComponent, DateValueEditorComponent, TimepickerComponent, CSVConfigPane,
            EnterClickDirective, NotificationsComponent, MockCkeditorComponent
         ],
         providers: [
            NgbModal,
            { provide: PortalModelService, useValue: portalModelService},
            { provide: ScheduleUsersService, useValue: scheduleUsersService},
         ],
         schemas: [
            NO_ERRORS_SCHEMA
         ]
      });
      TestBed.compileComponents();

      fixture = TestBed.createComponent(TestApp);
      taskActionPane = <TaskActionPane>fixture.componentInstance.taskActionPane;
      fixture.detectChanges();
   }));

   //Bug #19890 should pop up warning when to delete action
   it("should pop up warning when to delete action", () => {
      let showConfirmDialog = jest.spyOn(ComponentTool, "showConfirmDialog");
      showConfirmDialog.mockImplementation(() => Promise.resolve("ok"));
      taskActionPane.deleteAction();

      expect(showConfirmDialog).toHaveBeenCalled();
   });
});
