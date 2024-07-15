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
import { ChangeDetectorRef, Component, NO_ERRORS_SCHEMA, ViewChild } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { DndService } from "../../../common/dnd/dnd.service";
import { DropDownTestModule } from "../../../common/test/test-module";
import { TestUtils } from "../../../common/test/test-utils";
import { BindingService } from "../../services/binding.service";
import { ChartEditorService } from "../../services/chart/chart-editor.service";
import { ChartDataEditor } from "./chart-data-editor.component";

@Component({
   selector: "chart-data-editor-test-app",
   template: `
     <chart-data-editor [bindingModel]="model"
                        [grayedOutValues]="grayedOutValues"
                        [refs]="refs"></chart-data-editor>`
})
class TestApp {
   @ViewChild(ChartDataEditor, {static: true}) chartDataEditor: ChartDataEditor;
   model = TestUtils.createMockChartBindingModel();
   grayedOutValues = [];
   refs = [];
}

describe("chart data editor unit case", function() {
   let dservice: any;
   let editorService: any;
   let bindingService: any;
   let changeRef: any;
   let fixture: ComponentFixture<TestApp>;
   let chartDataEditor: any;


   beforeEach(() => {
      dservice = {
         setDragOverStyle: jest.fn(),
         processOnDrop: jest.fn()
      };
      editorService = {
         isDropPaneAccept: jest.fn(),
         getDNDType: jest.fn(),
         convert: jest.fn()
      };
      changeRef = { detectChanges: jest.fn() };

      TestBed.configureTestingModule({
         imports: [DropDownTestModule, NgbModule],
         declarations: [TestApp, ChartDataEditor],
         providers: [
            {provide: DndService, useValue: dservice},
            {provide: ChartEditorService, useValue: editorService},
            {provide: BindingService, useValue: bindingService},
            {provide: ChangeDetectorRef, useValue: changeRef}
         ],
         schemas: [NO_ERRORS_SCHEMA]
      }).compileComponents();

      fixture = TestBed.createComponent(TestApp);
      chartDataEditor = fixture.componentInstance.chartDataEditor;
      fixture.detectChanges();
   });

   //@Temp Bug #18959 only make sure chart-data-editor component can get right grayout fileds
   it("should transform right gray out field", () => {
      fixture.componentInstance.grayedOutValues = ["col1", "col2"];
      fixture.detectChanges();
      let grayCols = chartDataEditor.grayedOutValues;
      expect(grayCols).toEqual(["col1", "col2"]);
   });
});