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
import { ChangeDetectorRef, NgModule, NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormGroup, FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { ColorComponentEditor } from "../../widget/color-picker/color-component-editor.component";
import { ColorEditorDialog } from "../../widget/color-picker/color-editor-dialog.component";
import { ColorEditor } from "../../widget/color-picker/color-editor.component";
import { ColorMap } from "../../widget/color-picker/color-map.component";
import { ColorPicker } from "../../widget/color-picker/color-picker.component";
import { ColorSlider } from "../../widget/color-picker/color-slider.component";
import { ColorPane } from "../../widget/color-picker/cp-color-pane.component";
import { MessageDialog } from "../../widget/dialog/message-dialog/message-dialog.component";
import { NewAggrDialog } from "../../widget/dialog/new-aggr-dialog/new-aggr-dialog.component";
import { ScriptPane } from "../../widget/dialog/script-pane/script-pane.component";
import { DefaultFocusDirective } from "../../widget/directive/default-focus.directive";
import { FixedDropdownDirective } from "../../widget/fixed-dropdown/fixed-dropdown.directive";
import { DropdownStackService } from "../../widget/fixed-dropdown/dropdown-stack.service";
import { FixedDropdownContextmenuComponent } from "../../widget/fixed-dropdown/fixed-dropdown-contextmenu.component";
import { FixedDropdownComponent } from "../../widget/fixed-dropdown/fixed-dropdown.component";
import { FixedDropdownService } from "../../widget/fixed-dropdown/fixed-dropdown.service";
import { FormulaEditorDialog } from "../../widget/formula-editor/formula-editor-dialog.component";
import { DragService } from "../../widget/services/drag.service";
import { TableStylePaneModel } from "../../widget/table-style/table-style-pane-model";
import { TableStylePane } from "../../widget/table-style/table-style-pane.component";
import { TreeDropdownComponent } from "../../widget/tree/tree-dropdown.component";
import { TreeNodeComponent } from "../../widget/tree/tree-node.component";
import { TreeSearchPipe } from "../../widget/tree/tree-search.pipe";
import { TreeComponent } from "../../widget/tree/tree.component";
import { GeneralPropPaneModel } from "../model/general-prop-pane-model";
import { SizePositionPaneModel } from "../model/size-position-pane-model";
import { TableViewGeneralPaneModel } from "../model/table-view-general-pane-model";
import { TitlePropPaneModel } from "../model/title-prop-pane-model";
import { BasicGeneralPane } from "./basic-general-pane.component";
import { GeneralPropPane } from "./general-prop-pane.component";
import { SizePositionPane } from "./size-position-pane.component";
import { TableViewGeneralPane } from "./table-view-general-pane.component";
import { TitlePropPane } from "./title-prop-pane.component";

let createModel: () => TableViewGeneralPaneModel = () => {
   return {
      showMaxRows: false,
      maxRows: 0,
      showSubmitOnChange: false,
      submitOnChange: false,
      generalPropPaneModel: <GeneralPropPaneModel> {
         basicGeneralPaneModel: {
            name: "MockGauge",
            visible: "false",
            shadow: false,
            enabled: false,
            primary: false,
            nameEditable: true,
            showShadowCheckbox: false,
            showEnabledCheckbox: false,
            showRefreshCheckbox: false,
            objectNames: []
         },
         showSubmitCheckbox: false,
         submitOnChange: false,
         showEnabledGroup: false
      },
      titlePropPaneModel: <TitlePropPaneModel> {
         visible: false,
         title: ""
      },
      tableStylePaneModel: <TableStylePaneModel> {
         tableStyle: "",
         styleTree: {}
      },
      sizePositionPaneModel: <SizePositionPaneModel> {
         top: 0,
         left: 0,
         width: 0,
         height: 0,
         container: false
      }
   };
};

@NgModule({
   declarations: [
      FixedDropdownComponent,
      FixedDropdownContextmenuComponent
   ],
   entryComponents: [
      FixedDropdownComponent,
      FixedDropdownContextmenuComponent
   ]
})
class TestModule {}

describe("TableViewGeneralPane Unit Test", () => {
   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [
            FormsModule, ReactiveFormsModule, NgbModule, TestModule
         ],
         declarations: [
            TableViewGeneralPane, TitlePropPane, TreeDropdownComponent,
            GeneralPropPane, TreeComponent, FormulaEditorDialog, ColorEditor, TableStylePane,
            BasicGeneralPane, TreeNodeComponent, NewAggrDialog, ColorPicker, ScriptPane,
            ColorEditorDialog, ColorMap, ColorSlider, ColorComponentEditor, ColorPane,
            MessageDialog, TreeSearchPipe, FixedDropdownDirective, DefaultFocusDirective,
            FixedDropdownDirective, SizePositionPane
         ],
         providers: [
            ChangeDetectorRef, NgbModal, DragService, FixedDropdownService,
            DropdownStackService
         ],
         schemas: [ NO_ERRORS_SCHEMA ]
      });
      TestBed.compileComponents();
   }));

   // Bug #10802 should have submit on change checkbox if set to show
   it("should show submit on change checkbox", (done) => {
      let fixture: ComponentFixture<TableViewGeneralPane> = TestBed.createComponent(TableViewGeneralPane);
      let model: TableViewGeneralPaneModel = createModel();
      model.showSubmitOnChange = true;
      fixture.componentInstance.model = model;
      fixture.componentInstance.form = new FormGroup({});
      fixture.detectChanges();

      fixture.whenStable().then(() => {
         let labels: any = fixture.nativeElement.querySelectorAll("label");
         let submitOnChangeLabel: any = Array.prototype.slice.call(labels).find(
            e => e.textContent.indexOf("Submit on Change") != -1);
         expect(submitOnChangeLabel).toBeTruthy();
         done();
      });
   });
});
