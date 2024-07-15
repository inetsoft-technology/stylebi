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
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { of as observableOf } from "rxjs";
import { UIContextService } from "../../../common/services/ui-context.service";
import { DropDownTestModule } from "../../../common/test/test-module";
import { BasicGeneralPane } from "../../../vsobjects/dialog/basic-general-pane.component";
import { GeneralPropPane } from "../../../vsobjects/dialog/general-prop-pane.component";
import { SizePositionPane } from "../../../vsobjects/dialog/size-position-pane.component";
import { MessageDialog } from "../../../widget/dialog/message-dialog/message-dialog.component";
import { NewAggrDialog } from "../../../widget/dialog/new-aggr-dialog/new-aggr-dialog.component";
import { ScriptPane } from "../../../widget/dialog/script-pane/script-pane.component";
import { VSAssemblyScriptPane } from "../../../widget/dialog/vsassembly-script-pane/vsassembly-script-pane.component";
import { DefaultFocusDirective } from "../../../widget/directive/default-focus.directive";
import { EnterSubmitDirective } from "../../../widget/directive/enter-submit.directive";
import { FixedDropdownDirective } from "../../../widget/fixed-dropdown/fixed-dropdown.directive";
import { FormulaEditorDialog } from "../../../widget/formula-editor/formula-editor-dialog.component";
import { TreeNodeComponent } from "../../../widget/tree/tree-node.component";
import { TreeSearchPipe } from "../../../widget/tree/tree-search.pipe";
import { TreeComponent } from "../../../widget/tree/tree.component";
import { UploadPropertyDialogModel } from "../../data/vs/upload-property-dialog-model";
import { LabelPropPane } from "./label-prop-pane.component";
import { UploadGeneralPane } from "./upload-general-pane.component";
import { UploadPropertyDialog } from "./upload-property-dialog.component";
import { PropertyDialogService } from "../../../vsobjects/util/property-dialog.service";

let createModel = () => {
   return <UploadPropertyDialogModel> {
      id: "",
      uploadGeneralPaneModel: {
         generalPropPaneModel: {
            basicGeneralPaneModel: {
               editable: false,
               enabled: false,
               name: "",
               objectNames: [],
               primary: true,
               refresh: true,
               shadow: false,
               showEditableCheckbox: false,
               showEnabledCheckbox: false,
               showShadowCheckbox: false,
               showRefreshCheckbox: true,
               visible: "",
               nameEditable: true
            },
            enabled: "true",
            showEnabledGroup: true,
            showSubmitCheckbox: false,
            submitOnChange: false
         },
         labelPropPaneModel: {
            label: "Submit"
         },
         sizePositionPaneModel: {
            top: 10,
            left: 10,
            width: 10,
            height: 10,
            container: false
         }
      },
      vsAssemblyScriptPaneModel: {
         expression: "",
         scriptEnabled: false
      }
   };
};

describe("Upload Integration Test", () => {
   let contextService: any;
   let dialogService: any;

   beforeEach(async(() => {
      contextService = {
         isVS: jest.fn(),
         isAdhoc: jest.fn(),
         getDefaultTab: jest.fn(),
         setDefaultTab: jest.fn(),
         getObjectChange: jest.fn(() => observableOf({}))
      };
      dialogService = { checkScript: jest.fn() };

      TestBed.configureTestingModule({
         imports: [
            FormsModule, ReactiveFormsModule, NgbModule, DropDownTestModule
         ],
         declarations: [
            UploadPropertyDialog, UploadGeneralPane, VSAssemblyScriptPane,
            GeneralPropPane, LabelPropPane, ScriptPane, BasicGeneralPane,
            TreeComponent, FormulaEditorDialog, TreeNodeComponent, NewAggrDialog, MessageDialog,
            TreeSearchPipe, EnterSubmitDirective, DefaultFocusDirective, FixedDropdownDirective,
            SizePositionPane
         ],
         providers: [
            { provide: UIContextService, useValue: contextService },
            { provide: PropertyDialogService, useValue: dialogService }
         ],
         schemas: [ NO_ERRORS_SCHEMA ]
      });
      TestBed.compileComponents();
   }));

   it("checks to see if input contains special characters", () => {
      let fixture: ComponentFixture<UploadPropertyDialog> =
         TestBed.createComponent(UploadPropertyDialog);
      let element = fixture.nativeElement;
      let dialog: UploadPropertyDialog = <UploadPropertyDialog> fixture.componentInstance;
      dialog.model = createModel();
      dialog.model.uploadGeneralPaneModel.generalPropPaneModel.basicGeneralPaneModel.name = "+fff";
      fixture.detectChanges(false);
      fixture.detectChanges(false);
      let applyBtn = element.querySelector("[id=upload-ok-btn]");
      expect(applyBtn.attributes["disabled"].value).toBe("");
   });

   it("check to see if input starts with a number", () => {
      let fixture: ComponentFixture<UploadPropertyDialog> =
         TestBed.createComponent(UploadPropertyDialog);
      let element = fixture.nativeElement;
      let dialog: UploadPropertyDialog = <UploadPropertyDialog> fixture.componentInstance;
      dialog.model = createModel();
      dialog.model.uploadGeneralPaneModel.generalPropPaneModel.basicGeneralPaneModel.name = "1fff";
      fixture.detectChanges(false);
      fixture.detectChanges(false);
      let applyBtn = element.querySelector("[id=upload-ok-btn]");
      expect(applyBtn.attributes["disabled"].value).toBe("");
   });

   it("checks to see if input is white space", () => {
      let fixture: ComponentFixture<UploadPropertyDialog> =
         TestBed.createComponent(UploadPropertyDialog);
      let element = fixture.nativeElement;
      let dialog: UploadPropertyDialog = <UploadPropertyDialog> fixture.componentInstance;
      dialog.model = createModel();
      dialog.model.uploadGeneralPaneModel.generalPropPaneModel.basicGeneralPaneModel.name = " ";
      fixture.detectChanges(false);
      fixture.detectChanges(false);
      let applyBtn = element.querySelector("[id=upload-ok-btn]");
      expect(applyBtn.attributes["disabled"].value).toBe("");
   });
});
