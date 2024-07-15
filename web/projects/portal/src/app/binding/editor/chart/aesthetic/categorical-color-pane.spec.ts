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
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { of as observableOf } from "rxjs";
import { CategoricalColorModel } from "../../../../common/data/visual-frame-model";
import { UIContextService } from "../../../../common/services/ui-context.service";
import { TestUtils } from "../../../../common/test/test-utils";
import { ComponentTool } from "../../../../common/util/component-tool";
import { FixedDropdownDirective } from "../../../../widget/fixed-dropdown/fixed-dropdown.directive";
import { ModelService } from "../../../../widget/services/model.service";
import { ChartEditorService } from "../../../services/chart/chart-editor.service";
import { PaletteDialog } from "../palette-dialog.component";
import { CategoricalColorPane } from "./categorical-color-pane.component";
import { StaticColorEditor } from "./static-color-editor.component";

describe("Categorical Color Pane Unit Test", () => {
   let mockCategoricalColorModel: (field?: string) => CategoricalColorModel = (field?: string) => {
      return {
         clazz: "inetsoft.web.binding.model.graph.aesthetic.CategoricalColorModel",
         name: null,
         field: field,
         summary: false,
         changed: false,
         colors: ["#518db9", "#b9dbf4", "#62a640", "#ade095", "#fc8f2a", "#fde3a7", "#d64541", "#fda7a5", "#9368be", "#be90d4"],
         cssColors: [],
         defaultColors: [],
         colorMaps: [],
         globalColorMaps: [],
         useGlobal: true,
         shareColors: true,
         dateFormat: null
      };
   };

   let uiContextService: any;
   let modelService: any;
   let modalService: any;
   let chartEditorService: any;
   let fixture: ComponentFixture<CategoricalColorPane>;
   let categColorPane: CategoricalColorPane;

   beforeEach(() => {
      uiContextService = { isVS: jest.fn() };
      modelService = {
         sendModel: jest.fn(),
         getModel: jest.fn()
      };
      modalService = { open: jest.fn() };
      chartEditorService = { getCustomChartFrames: jest.fn() };
      chartEditorService.getCustomChartFrames.mockImplementation(() => observableOf([]));
   });

   function configureTestEnv(): void {
      TestBed.configureTestingModule({
         imports: [
            ReactiveFormsModule, FormsModule, NgbModule
         ],
         declarations: [
            CategoricalColorPane, StaticColorEditor
         ],
         providers: [{
            provide: UIContextService, useValue: uiContextService
         },
         {
            provide: ModelService, useValue: modelService
         },
         {
            provide: NgbModal, useValue: modalService
         },
         {
            provide: ChartEditorService, useValue: chartEditorService
         }],
         schemas: [NO_ERRORS_SCHEMA]
      }).compileComponents();
   }

   //for Bug #17613, Can't open 'Select Palette' dialog
   it("Can't open 'Select Palette' dialog", () => {
      let showDialog = jest.spyOn(ComponentTool, "showDialog");
      showDialog.mockImplementation(() => {
         return new PaletteDialog();
      });
      uiContextService.isVS.mockImplementation(() => false);
      categColorPane =
         new CategoricalColorPane(modalService, modelService, uiContextService, chartEditorService);
      categColorPane.clickPaletteButton();

      expect(showDialog).toHaveBeenCalled();
   });

   //for Bug #19213
   it("Should show right icon status on categorical color pane", () => {
      modelService.getModel.mockImplementation(() => observableOf([]));
      configureTestEnv();
      fixture = TestBed.createComponent(CategoricalColorPane);
      categColorPane = <CategoricalColorPane>fixture.componentInstance;
      let frameModel = mockCategoricalColorModel("Employee");
      categColorPane.frameModel = frameModel;
      categColorPane.vsId = "icon-15114052318720";
      categColorPane.assemblyName = "Chart1";
      categColorPane.field = {
         fullName: null,
         dataInfo: TestUtils.createMockChartDimensionRef("Employee"),
         frame: frameModel
      };
      fixture.detectChanges();

      //Bug #19213
      let leftIcon: Element = fixture.nativeElement.querySelector("i.chevron-circle-arrow-left-icon");
      expect(leftIcon.getAttribute("class")).toContain("fade");

      for(let i = 0; i < frameModel.colors.length - 1; i++) {
         categColorPane.showNext();
      }
      fixture.detectChanges();
      let rightIcon: Element = fixture.nativeElement.querySelector("i.chevron-circle-arrow-right-icon");
      expect(rightIcon.getAttribute("class")).toContain("fade");

      //Bug #19842
      let items = fixture.nativeElement.querySelectorAll("static-color-editor[ng-reflect-color]");
      expect(items.length).toEqual(8);

      //Bug #19721
      let nullItems = fixture.nativeElement.querySelectorAll("static-color-editor:not([ng-reflect-color])");
      expect(nullItems.length).toEqual(0);
   });

   //Bug #20751
   it("the dropdown color pane should be auto closed", () => {
      let staticColor: StaticColorEditor = new StaticColorEditor();
      let fixedDropdownService: any = { open: jest.fn() };
      let elemRef = { nativeElement: {} };
      let dropDownDirective = new FixedDropdownDirective(fixedDropdownService, elemRef);
      let close = jest.spyOn(dropDownDirective, "close");

      staticColor.dropdown = dropDownDirective;
      staticColor.changeColor("#00ffff");
      expect(close).toHaveBeenCalled();
   });
});