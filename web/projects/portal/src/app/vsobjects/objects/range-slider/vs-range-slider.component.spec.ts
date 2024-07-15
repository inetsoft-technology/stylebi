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
import { By } from "@angular/platform-browser";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { NEVER } from "rxjs";
import { AssemblyActionEvent } from "../../../common/action/assembly-action-event";
import { TestUtils } from "../../../common/test/test-utils";
import { ComponentTool } from "../../../common/util/component-tool";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { DebounceService } from "../../../widget/services/debounce.service";
import { ModelService } from "../../../widget/services/model.service";
import { DialogService } from "../../../widget/slide-out/dialog-service.service";
import { RangeSliderActions } from "../../action/range-slider-actions";
import { ContextProvider, ViewerContextProviderFactory } from "../../context-provider.service";
import { RangeSliderEditDialog } from "../../dialog/range-slider-edit-dialog.component";
import { VSRangeSliderModel } from "../../model/vs-range-slider-model";
import { CheckFormDataService } from "../../util/check-form-data.service";
import { GlobalSubmitService } from "../../util/global-submit.service";
import { AdhocFilterService } from "../data-tip/adhoc-filter.service";
import { DataTipService } from "../data-tip/data-tip.service";
import { PopComponentService } from "../data-tip/pop-component.service";
import { VSPopComponentDirective } from "../data-tip/vs-pop-component.directive";
import { MiniToolbar } from "../mini-toolbar/mini-toolbar.component";
import { VSRangeSlider } from "./vs-range-slider.component";
import { FixedDropdownService } from "../../../widget/fixed-dropdown/fixed-dropdown.service";

describe("VSRangeSlider Test", () => {
   let fixture: ComponentFixture<VSRangeSlider>;
   let viewsheetClientService: any;
   let dataTipService: any;
   let dialogService: any;
   let adhocFilterService: any;
   let dropdownService: any;
   let globalSubmitService: any;

   viewsheetClientService = { sendEvent: jest.fn() };
   let modelService: any = { getModel: jest.fn() };
   let modalService: any = { open: jest.fn() };
   dataTipService = { isDataTip: jest.fn() };
   const formDataService = {
      checkFormData: jest.fn(),
      removeObject: jest.fn(),
      addObject: jest.fn(),
      replaceObject: jest.fn()
   };
   const contextProvider = {};
   dialogService = { open: jest.fn() };

   beforeEach(async(() => {
      adhocFilterService = {
         adhocFilterShowing: jest.fn(),
         showFilter: jest.fn()
      };
      globalSubmitService = {
         hasUnapplyData: false,
         updateState: jest.fn(),
         globalSubmit: jest.fn(() => NEVER),
         submitGlobal: jest.fn()
      };

      TestBed.configureTestingModule({
         imports: [ NgbModule ],
         declarations: [ VSRangeSlider, MiniToolbar, VSPopComponentDirective],
         providers: [
            { provide: ContextProvider, useValue: contextProvider },
            { provide: ViewsheetClientService, useValue: viewsheetClientService },
            { provide: ModelService, useValue: modelService },
            { provide: NgbModal, useValue: modalService },
            { provide: CheckFormDataService, useValue: formDataService },
            PopComponentService,
            DebounceService,
            { provide: DataTipService, useValue: dataTipService },
            { provide: DialogService, useValue: dialogService },
            { provide: AdhocFilterService, useValue: adhocFilterService },
            { provide: FixedDropdownService, useValue: dropdownService},
            { provide: GlobalSubmitService, useValue: globalSubmitService }
         ],
         schemas: [ NO_ERRORS_SCHEMA ]
      });
      TestBed.compileComponents();

      fixture = TestBed.createComponent(VSRangeSlider);
      fixture.componentInstance.model = TestUtils.createMockVSRangeSliderModel("rs1");
      fixture.detectChanges();
   }));

   it("should have \"..\" between the range values when the upperInclusive checkbox is checked and \"->\" when the upperInclusive checkbox is unchecked", async(() => {
      // set mock labels. They needed to not be set before to run previous tests as if right after onInit/before data selected
      fixture.componentInstance.model.labels = ["1", "2", "3", "4", "5"];
      fixture.detectChanges();

      fixture.whenStable().then(() => {
         expect(fixture.componentInstance.getCurrentLabel()).toMatch(/.*\.\..*/);

         fixture.componentInstance.model.upperInclusive = false;
         fixture.detectChanges();

         fixture.whenStable().then(() => {
            expect(fixture.componentInstance.getCurrentLabel()).toMatch(/.*->.*/);
         });
      });
   }));

   //Bug #17760 should be None for range slider title on default
   it("range slider title should be None on default status", () => {
      fixture.componentInstance.model.selectStart = 0;
      fixture.componentInstance.model.labels = ["AK", "NK", "PM"];
      fixture.componentInstance.model.selectEnd = 2;
      fixture.componentInstance.model.container = "mockContainer";

      //Bug #17760
      fixture.detectChanges();
      expect(fixture.componentInstance.getContainerLabel()).toBe("(none)");

      fixture.componentInstance.model.selectEnd = 1;
      fixture.detectChanges();
      expect(fixture.componentInstance.getContainerLabel()).toBe("AK..NK");
   });

   //Bug #18972 should apply format
   it("should apply format on rangeslider", () => {
      let vsFormats = TestUtils.createMockVSFormatModel();
      vsFormats.decoration = "underline";
      vsFormats.font = "15px";
      let vsrangeModel = TestUtils.createMockVSRangeSliderModel("range1");
      vsrangeModel.objectFormat = vsFormats;
      vsrangeModel.selectStart = 0;
      vsrangeModel.labels = ["AK", "NK", "PM"];
      vsrangeModel.selectEnd = 2;
      vsrangeModel.maxRangeBarWidth = 140;
      vsrangeModel.container = "mockContainer";
      fixture.componentInstance.model = vsrangeModel;

      fixture.detectChanges();
      let label = fixture.debugElement.query(By.css("div.vs-range-slider-body")).nativeElement;
      expect(label.style["text-decoration"]).toBe("underline");
   });

   //Bug #18115, should open range slider dialog on viewer page.
   it("should open range slider edit dialog on viewer page", () => {
      let showDialog = jest.spyOn(ComponentTool, "showDialog");
      showDialog.mockImplementation(() => new RangeSliderEditDialog());
      let rangeSliderActions: RangeSliderActions = new RangeSliderActions(TestUtils.createMockVSRangeSliderModel("rs1"), ViewerContextProviderFactory(false));
      fixture.componentInstance.actions = rangeSliderActions;
      fixture.componentInstance.selected = true;
      fixture.detectChanges();
      rangeSliderActions.onAssemblyActionEvent.emit(new AssemblyActionEvent<VSRangeSliderModel>("range-slider edit-range", TestUtils.createMockVSRangeSliderModel("rs1")));

      expect(showDialog).toHaveBeenCalled();
   });

   //Bug #20993 should display in correct position
   it("should display in correct position", () => {
      let model = TestUtils.createMockVSRangeSliderModel("rs1");
      model.objectFormat.height = 85;
      model.objectFormat.top = 104;
      model.objectFormat.left = 187;
      model.objectFormat.width = 140;

      fixture.componentInstance.model = model;
      fixture.detectChanges();

      let rangeSlider = fixture.debugElement.query(By.css("div.range-slider"));
      let rangeLine = fixture.debugElement.query(By.css("div.range-line"));
      let currentValue = fixture.debugElement.query(By.css("div.current-value"));
      let maxValue = fixture.debugElement.query(By.css("div.range-value.range-right"));
      let minValue = fixture.debugElement.query(By.css("div.range-value.range-left"));

      expect(rangeSlider.styles["top"]).toBe("39px");
      expect(rangeLine.styles["top"]).toBe("39px");
      expect(currentValue.styles["top"]).toBe("calc(39px - 1em)");
      expect(maxValue.styles["top"]).toBe("49px");
      expect(minValue.styles["top"]).toBe("49px");
   });
});
