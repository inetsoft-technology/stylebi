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
import { CommonModule } from "@angular/common";
import { HttpClientTestingModule } from "@angular/common/http/testing";
import { Component, NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, fakeAsync, TestBed, tick } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { VariableInfo } from "../../../../common/data/variable-info";
import { DropDownTestModule } from "../../../../common/test/test-module";
import { TestUtils } from "../../../../common/test/test-utils";
import { StompClientService, ViewsheetClientService } from "../../../../common/viewsheet-client";
import { DateValueEditorComponent } from "../../../../widget/date-type-editor/date-value-editor.component";
import { TimeInstantValueEditorComponent } from "../../../../widget/date-type-editor/time-instant-value-editor.component";
import { TimeValueEditorComponent } from "../../../../widget/date-type-editor/time-value-editor.component";
import { TimepickerComponent } from "../../../../widget/date-type-editor/timepicker.component";
import { MessageDialog } from "../../../../widget/dialog/message-dialog/message-dialog.component";
import { VariableListDialog } from "../../../../widget/dialog/variable-list-dialog/variable-list-dialog.component";
import { VariableListEditor } from "../../../../widget/dialog/variable-list-dialog/variable-list-editor/variable-list-editor.component";
import { VariableValueEditor } from "../../../../widget/dialog/variable-list-dialog/variable-value-editor/variable-value-editor.component";
import { EnterSubmitDirective } from "../../../../widget/directive/enter-submit.directive";
import { ActionsContextmenuAnchorDirective } from "../../../../widget/fixed-dropdown/actions-contextmenu-anchor.directive";
import { LargeFormFieldComponent } from "../../../../widget/large-form-field/large-form-field.component";
import { ModelService } from "../../../../widget/services/model.service";
import { DialogService } from "../../../../widget/slide-out/dialog-service.service";
import { WSVariableAssembly } from "../../../data/ws/ws-variable-assembly";
import { VariableAssemblyDialog } from "../../../dialog/ws/variable-assembly-dialog.component";
import { VariableTableListDialog } from "../../../dialog/ws/variable-table-list-dialog.component";
import { VariableThumbnail } from "./variable-thumbnail.component";
import { WSAssemblyThumbnailTitleComponent } from "./ws-assembly-thumbnail-title.component";
import createMockWSAssemblyModel = TestUtils.createMockWSAssemblyModel;

@Component({
   selector: "ws-assembly-test",
   template: `
     <variable-thumbnail [variable]="variable"></variable-thumbnail>
   `
})
class WSAssemblyThumbnailTest {
   variable: WSVariableAssembly;
}

describe("WSAssemblyThumbnail Tests", () => {
   let dialogService: any;


   beforeEach(() => {
      dialogService = { open: jest.fn() };
      TestBed.configureTestingModule({
         imports: [
            NgbModule, FormsModule, ReactiveFormsModule, CommonModule, DropDownTestModule,
            HttpClientTestingModule
         ],
         declarations: [
            VariableThumbnail, VariableAssemblyDialog, WSAssemblyThumbnailTest,
            VariableValueEditor, VariableListDialog, VariableTableListDialog,
            VariableListEditor, MessageDialog, DateValueEditorComponent, TimeValueEditorComponent,
            TimeInstantValueEditorComponent, ActionsContextmenuAnchorDirective,
            WSAssemblyThumbnailTitleComponent, EnterSubmitDirective,
            LargeFormFieldComponent, TimepickerComponent
         ],
         providers: [
            ModelService, ViewsheetClientService, StompClientService,
            { provide: DialogService, useValue: dialogService }
         ],
         schemas: [ NO_ERRORS_SCHEMA ]
      });
      TestBed.compileComponents();
   });

   xit("should have valid width/height before and after assembly changes", fakeAsync(() => { // broken test
      const originalAssembly: WSVariableAssembly = {
         ...createMockWSAssemblyModel(),
         name: "first assembly",
         variableInfo: {
            description: "desc",
            value: ["value"]
         } as VariableInfo,
         classType: "VariableAssembly"
      };

      const fixture: ComponentFixture<WSAssemblyThumbnailTest> =
         TestBed.createComponent(WSAssemblyThumbnailTest);
      fixture.componentInstance.variable = originalAssembly;
      fixture.detectChanges(true);
      tick();

      expect(originalAssembly.width).toBeGreaterThan(0);
      expect(originalAssembly.height).toBeGreaterThan(0);

      const newAssembly: WSVariableAssembly = {
         ...createMockWSAssemblyModel(),
         name: "second assembly",
         variableInfo: {
            description: "desc",
            value: ["value"]
         } as VariableInfo,
         classType: "VariableAssembly"
      };

      fixture.componentInstance.variable = newAssembly;
      fixture.detectChanges(true);
      tick();

      expect(newAssembly.width).toBeGreaterThan(0);
      expect(newAssembly.height).toBeGreaterThan(0);
   }));
});
