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
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormGroup, FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { UIContextService } from "../../common/services/ui-context.service";
import { DropDownTestModule } from "../../common/test/test-module";
import { ColorComponentEditor } from "../../widget/color-picker/color-component-editor.component";
import { ColorEditorDialog } from "../../widget/color-picker/color-editor-dialog.component";
import { ColorEditor } from "../../widget/color-picker/color-editor.component";
import { ColorMap } from "../../widget/color-picker/color-map.component";
import { ColorPicker } from "../../widget/color-picker/color-picker.component";
import { ColorSlider } from "../../widget/color-picker/color-slider.component";
import { ColorPane } from "../../widget/color-picker/cp-color-pane.component";
import { RecentColorService } from "../../widget/color-picker/recent-color.service";
import { MessageDialog } from "../../widget/dialog/message-dialog/message-dialog.component";
import { NewAggrDialog } from "../../widget/dialog/new-aggr-dialog/new-aggr-dialog.component";
import { ScriptPane } from "../../widget/dialog/script-pane/script-pane.component";
import { FixedDropdownDirective } from "../../widget/fixed-dropdown/fixed-dropdown.directive";
import { AlphaDropdown } from "../../widget/format/alpha-dropdown.component";
import { StyleDropdown } from "../../widget/format/style-dropdown.component";
import { FormulaEditorDialog } from "../../widget/formula-editor/formula-editor-dialog.component";
import { FontService } from "../../widget/services/font.service";
import { TreeNodeComponent } from "../../widget/tree/tree-node.component";
import { TreeSearchPipe } from "../../widget/tree/tree-search.pipe";
import { TreeComponent } from "../../widget/tree/tree.component";
import { LegendFormatGeneralPaneModel } from "../model/dialog/legend-format-general-pane-model";
import { LegendFormatGeneralPane } from "./legend-format-general-pane.component";
import { DebounceService } from "../../widget/services/debounce.service";

let createModel: () => LegendFormatGeneralPaneModel = () => {
   return {
      title: "",
      titleValue: "",
      style: 0,
      fillColor: "",
      position: "",
      visible: false,
      notShowNull: false,
      notShowNullVisible: false
   };
};

describe("LegendFormatGeneralPane Unit Tests", () => {
   let uiContextService: any;

   beforeEach(async(() => {
      uiContextService = {
         isVS: jest.fn(),
         isAdhoc: jest.fn(),
         getDefaultTab: jest.fn(),
         setDefaultTab: jest.fn()
      };
      uiContextService.isAdhoc.mockImplementation(() => false);
      TestBed.configureTestingModule({
         imports: [
            CommonModule,
            NgbModule,
            FormsModule,
            ReactiveFormsModule,
            DropDownTestModule,
         ],
         declarations: [
            LegendFormatGeneralPane,
            ColorEditor,
            AlphaDropdown,
            ColorPicker,
            ColorEditorDialog,
            ColorMap,
            ColorSlider,
            ColorComponentEditor,
            ColorPane,
            StyleDropdown,
            FormulaEditorDialog,
            TreeComponent,
            TreeNodeComponent,
            TreeSearchPipe,
            NewAggrDialog,
            MessageDialog,
            ScriptPane,
            FixedDropdownDirective
         ],
         providers: [
            NgbModal,
            RecentColorService,
            FontService,
            DebounceService,
            {provide: UIContextService, useValue: uiContextService},
         ],
         schemas: [ NO_ERRORS_SCHEMA ]
      });
      TestBed.compileComponents();
   }));

   // Bug #10107 Should have Ingore Null checkbox
   it("should have Ignore Null checkbox", (done) => {
      let fixture: ComponentFixture<LegendFormatGeneralPane> = TestBed.createComponent(LegendFormatGeneralPane);
      let model: LegendFormatGeneralPaneModel = createModel();
      model.notShowNullVisible = true;
      fixture.componentInstance.model = model;
      fixture.componentInstance.form = new FormGroup({});
      fixture.detectChanges();

      fixture.whenStable().then(() => {
         let labels: any = fixture.nativeElement.querySelectorAll("label");
         let ignoreNullLabel: any = Array.prototype.slice.call(labels).find(e => e.textContent.indexOf("Ignore Null") != -1);

         expect(ignoreNullLabel).toBeTruthy();
         done();
      });
   });
});
