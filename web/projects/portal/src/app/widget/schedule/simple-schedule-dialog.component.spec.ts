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
import { HttpClient } from "@angular/common/http";
import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { TimeConditionType } from "../../../../../shared/schedule/model/time-condition-model";
import { TestUtils } from "../../common/test/test-utils";
import { ComponentTool } from "../../common/util/component-tool";
import { EmailValidationResponse } from "../../vsobjects/dialog/email/email-validation-response";
import { SimpleScheduleDialogModel } from "../../vsobjects/model/schedule/simple-schedule-dialog-model";
import { ViewsheetActionModel } from "../../vsobjects/model/schedule/viewsheet-action-model";
import { AssetTreeComponent } from "../asset-tree/asset-tree.component";
import { DateValueEditorComponent } from "../date-type-editor/date-value-editor.component";
import { TimeInstantValueEditorComponent } from "../date-type-editor/time-instant-value-editor.component";
import { TimeValueEditorComponent } from "../date-type-editor/time-value-editor.component";
import { TimepickerComponent } from "../date-type-editor/timepicker.component";
import { VariableCollectionSelector } from "../dialog/variable-collection-selector/variable-collection-selector.component";
import { VariableInputDialog } from "../dialog/variable-input-dialog/variable-input-dialog.component";
import { VariableValueEditor } from "../dialog/variable-list-dialog/variable-value-editor/variable-value-editor.component";
import { EnterSubmitDirective } from "../directive/enter-submit.directive";
import { EmailAddrDialog } from "../email-dialog/email-addr-dialog.component";
import { EmbeddedEmailPane } from "../email-dialog/embedded-email-pane.component";
import { QueryEmailPane } from "../email-dialog/query-email-pane.component";
import { IdentityTreeComponent } from "../identity-tree/identity-tree.component";
import { ShuffleListComponent } from "../shuffle-list/shuffle-list.component";
import { TreeNodeComponent } from "../tree/tree-node.component";
import { TreeSearchPipe } from "../tree/tree-search.pipe";
import { TreeComponent } from "../tree/tree.component";
import { SimpleScheduleDialog } from "./simple-schedule-dialog.component";
import { StartTimeEditor } from "./start-time-editor.component";

let createModel: () => SimpleScheduleDialogModel = () => {
   return {
      userDialogEnabled: false,
      timeProp: null,
      twelveHourSystem: false,
      taskName: null,
      isSecurity: false,
      formatTypes: [],
      timeConditionModel: TestUtils.createMockTimeConditionModel(),
      actionModel: createVSActionModel(),
      emailAddrDialogModel: null,
      expandEnabled: false,
      emailButtonVisible: false,
      emailDeliveryEnabled: true,
      timeRanges: [],
      startTimeEnabled: true,
      timeRangeEnabled: true
   };
};

let createVSActionModel: () => ViewsheetActionModel = () => {
   return {
      type: "viewsheet",
      bookmarkName: "(Home)",
      bookmarkType: 1,
      bookmarkUser: "anonymous",
      emailInfoModel: {
         attachmentName: "date",
         emails: null,
         formatType: 0,
         formatStr: "",
         fromAddress: "reportserver@inetsoft.com",
         matchLayout: true,
         message: "",
         subject: "",
         expandSelections: false,
         csvConfigModel: {
            delimiter:  "",
            quote:  null,
            keepHeader:  false,
            tabDelimited:  false
         },
         onlyDataComponents: false
      },
      viewsheet: ""
   };
};

describe("simple schedule dialog component unit case test", function() {
   let modalService: any;
   let simpleDialog: SimpleScheduleDialog;
   let fixture: ComponentFixture<SimpleScheduleDialog>;
   let httpClient: HttpClient;
   let httpTestingController: HttpTestingController;

   beforeEach(() => {
      modalService = { open: jest.fn() };

      TestBed.configureTestingModule({
         imports: [
            ReactiveFormsModule, FormsModule, NgbModule, HttpClientTestingModule
         ],
         declarations: [
            SimpleScheduleDialog, IdentityTreeComponent, ShuffleListComponent,
            TreeComponent, TreeSearchPipe, TreeNodeComponent, EmailAddrDialog,
            EmbeddedEmailPane, QueryEmailPane, AssetTreeComponent, VariableInputDialog,
            VariableValueEditor, VariableCollectionSelector,
            TimeInstantValueEditorComponent, TimeValueEditorComponent, DateValueEditorComponent,
            TimepickerComponent, EnterSubmitDirective, StartTimeEditor
         ],
         providers: [
            {provide: NgbModal, useValue: modalService}
         ],
         schemas: [NO_ERRORS_SCHEMA]
      }).compileComponents();

      httpClient = TestBed.inject(HttpClient);
      httpTestingController = TestBed.inject(HttpTestingController);
      fixture = TestBed.createComponent(SimpleScheduleDialog);
      simpleDialog = <SimpleScheduleDialog>fixture.componentInstance;
      simpleDialog.model = createModel();
   });

   //Bug #19467
   xit("should not pop up warning", () => {
      simpleDialog.model.taskName = "date";
      simpleDialog.model.timeProp = "HH:mm:ss";
      simpleDialog.model.timeConditionModel.conditionType = "TimeCondition";
      simpleDialog.model.timeConditionModel.hour = 1;
      simpleDialog.model.timeConditionModel.minute = 30;
      simpleDialog.model.timeConditionModel.second = 0;
      simpleDialog.model.actionModel.emailInfoModel.emails = "bonnieshi@inetsoft.com";

      fixture.detectChanges();
      simpleDialog.ok();

      const body: EmailValidationResponse = {
         addressHistory: ["bonnieshi@inetsoft.com"],
         messageCommand: {
            events: null,
            message: null,
            type: "OK"
         }
      };
      const requests = httpTestingController.match(() => true);
      requests.forEach(req => req.flush(body));

      let showMessageDialog = jest.spyOn(ComponentTool, "showMessageDialog");
      showMessageDialog.mockImplementation(() => Promise.resolve("ok"));
      expect(showMessageDialog).not.toHaveBeenCalled();
   });

   //Bug #19722
   xit("should show right status when select monthly", (done) => {
      simpleDialog.model.taskName = "task1";
      simpleDialog.model.timeProp = "HH:mm:ss";
      simpleDialog.model.timeConditionModel.type = 7;
      simpleDialog.startTimeData = {
         startTime: {
            hour: 1,
            minute: 30,
            second: 0
         },
         timeRange: null,
         startTimeSelected: true,
         valid: true
      };
      simpleDialog.selectConditionType(TimeConditionType.EVERY_MONTH);
      fixture.detectChanges();
      fixture.whenStable().then(() => {
         let radio = fixture.nativeElement.querySelectorAll("input[name='emailFreq']");
         expect(radio[0].checked).toBeTruthy();
         expect(radio[1].checked).toBeFalsy();
         done();
      });
   });
});