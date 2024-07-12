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
import { HttpClientTestingModule } from "@angular/common/http/testing";
import { Component, NO_ERRORS_SCHEMA, Optional } from "@angular/core";
import { async, TestBed } from "@angular/core/testing";
import { FormsModule } from "@angular/forms";
import { By } from "@angular/platform-browser";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { of as observableOf } from "rxjs";
import { DownloadService } from "../../../../../../shared/download/download.service";
import { AssemblyAction } from "../../../common/action/assembly-action";
import { Rectangle } from "../../../common/data/rectangle";
import { DndService } from "../../../common/dnd/dnd.service";
import { TestUtils } from "../../../common/test/test-utils";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { ChartService } from "../../../graph/services/chart.service";
import { FixedDropdownService } from "../../../widget/fixed-dropdown/fixed-dropdown.service";
import { ModelService } from "../../../widget/services/model.service";
import { ScaleService } from "../../../widget/services/scale/scale-service";
import { DialogService } from "../../../widget/slide-out/dialog-service.service";
import { ComposerToken, ContextProvider, ViewerContextProviderFactory } from "../../context-provider.service";
import { RichTextService } from "../../dialog/rich-text-dialog/rich-text.service";
import { VSChartModel } from "../../model/vs-chart-model";
import { ShowHyperlinkService } from "../../show-hyperlink.service";
import { AdhocFilterService } from "../data-tip/adhoc-filter.service";
import { DataTipService } from "../data-tip/data-tip.service";
import { PopComponentService } from "../data-tip/pop-component.service";
import { MiniToolbar } from "../mini-toolbar/mini-toolbar.component";
import { VSChartService } from "./services/vs-chart.service";
import { VSChart } from "./vs-chart.component";

@Component({
   selector: "test-app",
   template: `<vs-chart [model]="mockObject"
                        [viewer]="true">
              </vs-chart>
   `
})
class TestApp {
   mockObject: VSChartModel = TestUtils.createMockVSChartModel("Chart1");
}

describe("VSChart Tests", () => {
   let chartService: any;
   let dialogService: any;
   let adhocFilterService: any;
   let richTextService: any;

   beforeEach(async(() => {
      let modelService: any = { getModel: jest.fn() };
      let viewsheetClientService: any = { sendEvent: jest.fn() };
      viewsheetClientService.commands = observableOf([]);
      let dndService = {};
      let dataTipService = {
         showDataTip: jest.fn(),
         isDataTip: jest.fn(),
         isFrozen: jest.fn(),
         hideDataTip: jest.fn()
      };
      let dropdownService = {};
      let downloadService = { download: jest.fn() };
      chartService = {
         getXTitle: jest.fn(),
         getX2Title: jest.fn(),
         getYTitle: jest.fn(),
         getY2Title: jest.fn(),
         getXBottomAxis: jest.fn(),
         getXTopAxis: jest.fn(),
         getYLeftAxis: jest.fn(),
         getYRightAxis: jest.fn()
      };
      const scaleService = { getScale: jest.fn(), setScale: jest.fn() };
      scaleService.getScale.mockImplementation(() => observableOf(1));
      dialogService = { open: jest.fn() };
      adhocFilterService = {
         showFilter: jest.fn(),
         adhocFilterShowing: false
      };
      richTextService = {
         showAnnotationDialog: jest.fn()
      };

      TestBed.configureTestingModule({
         imports: [
            FormsModule,
            NgbModule,
            HttpClientTestingModule
         ],
         declarations: [
            TestApp, VSChart, MiniToolbar,
         ],
         schemas: [NO_ERRORS_SCHEMA],
         providers: [
            VSChartService,
            { provide: ModelService, useValue: modelService },
            { provide: ViewsheetClientService, useValue: viewsheetClientService },
            { provide: DndService, useValue: dndService },
            { provide: DataTipService, useValue: dataTipService },
            { provide: VSChartService, useValue: chartService },
            { provide: ChartService, useValue: chartService },
            { provide: FixedDropdownService, useValue: dropdownService },
            { provide: DownloadService, useValue: downloadService },
            {
               provide: ContextProvider,
               useFactory: ViewerContextProviderFactory,
               deps: [[new Optional(), ComposerToken]]
            },
            { provide: ScaleService, useValue: scaleService },
            PopComponentService,
            ShowHyperlinkService,
            { provide: DialogService, useValue: dialogService },
            { provide: AdhocFilterService, useValue: adhocFilterService },
            { provide: RichTextService, useValue: richTextService }
         ]
      });

      TestBed.compileComponents();
   }));

   xit("should run callbacks for visible icons", () => {
      let fixture = TestBed.createComponent(TestApp);
      fixture.componentInstance.mockObject.axes.push({
         areaName: "axis",
         bounds: new Rectangle(0, 0, 0, 0),
         layoutBounds: new Rectangle(0, 0, 0, 0),
         tiles: [],
         regions: [],
         axisType: "",
         sortOp: "",
         axisOps: [],
         axisFields: [],
         axisSizes: [],
         secondary: false,
         sortField: "",
         drillLevels: []
      });
      fixture.detectChanges();

      let miniToolbar = fixture.debugElement.query(By.directive(MiniToolbar));
      let chartComponent = fixture.debugElement.query(By.directive(VSChart));
      let actions: AssemblyAction[]  = chartComponent.componentInstance.getActions();

      // Spy on getActions and stub out callback
      let getActionsSpy = jest.spyOn(chartComponent.componentInstance, "getActions")
         .mockImplementation(() => actions.map((action) => {
               action.action = jest.fn();
               return action;
         }));

      // Detect changes so getActions spy gets called
      fixture.detectChanges();

      // Click all buttons on the minitoolbar
      let buttons = miniToolbar.queryAll(By.css("i"));
      buttons.forEach((button) => {
         button.nativeElement.click();
      });

      // Check that the correct callbacks are called
      let chartActions: AssemblyAction[] = getActionsSpy.mock.results[getActionsSpy.mock.results.length - 1].value;
      chartActions
         .filter((action) => action.visible())
         .forEach((action) => {
            let iconClass = "." + action.icon();
            let iconElement = miniToolbar.query(By.css(iconClass));

            // Jasmine bug, can't use DebugElement directly in expect
            expect(iconElement != null).toBe(true);
            expect(action.action).toHaveBeenCalled();
      });
   });

   xit("should not run callbacks for invisible icons", () => {
      let fixture = TestBed.createComponent(TestApp);
      fixture.componentInstance.mockObject.axes.push({
         areaName: "axis",
         bounds: new Rectangle(0, 0, 0, 0),
         layoutBounds: new Rectangle(0, 0, 0, 0),
         tiles: [],
         regions: [],
         axisType: "",
         sortOp: "",
         axisOps: [],
         axisFields: [],
         axisSizes: [],
         secondary: false,
         sortField: "",
         drillLevels: []
      });
      fixture.detectChanges();

      let miniToolbar = fixture.debugElement.query(By.directive(MiniToolbar));
      let chartComponent = fixture.debugElement.query(By.directive(VSChart));
      let actions: AssemblyAction[]  = chartComponent.componentInstance.getActions();

      // Spy on getActions and stub out callback
      let getActionsSpy = jest.spyOn(chartComponent.componentInstance, "getActions")
         .mockImplementation(() => actions.map((action) => {
            action.action = jest.fn();
            return action;
         }));

      // Detect changes so getActions spy gets called
      fixture.detectChanges();

      // Click all buttons on the minitoolbar
      let buttons = miniToolbar.queryAll(By.css("i"));
      buttons.forEach((button) => {
         button.nativeElement.click();
      });

      // Check that the correct callbacks are called
      let chartActions: AssemblyAction[] = getActionsSpy.mock.results[getActionsSpy.mock.results.length - 1].value;
      chartActions
         .filter((action) => !action.visible())
         .forEach((action) => {
            let iconClass = "." + action.icon();
            let iconElement = miniToolbar.query(By.css(iconClass));

            // Jasmine bug, can't use DebugElement directly in expect
            expect(iconElement == null).toBe(true);
            expect(action.action).not.toHaveBeenCalled();
      });
   });
});
