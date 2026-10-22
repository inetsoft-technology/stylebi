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
import { CommonModule } from "@angular/common";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { waitForAsync, ComponentFixture, TestBed } from "@angular/core/testing";
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
import { AlphaDropdown } from "../../widget/format/alpha-dropdown.component";
import { StyleDropdown } from "../../widget/format/style-dropdown.component";
import { FormulaEditorDialog } from "../../widget/formula-editor/formula-editor-dialog.component";
import { FontService } from "../../widget/services/font.service";
import { TreeNodeComponent } from "../../widget/tree/tree-node.component";
import { TreeSearchPipe } from "../../widget/tree/tree-search.pipe";
import { TreeComponent } from "../../widget/tree/tree.component";
import { LegendFormatGeneralPaneModel } from "../model/dialog/legend-format-general-pane-model";
import { LegendFormatGeneralPane } from "./legend-format-general-pane.component";
import { NumberStepperComponent } from "../../widget/number-stepper/number-stepper.component";
import { DebounceService } from "../../widget/services/debounce.service";
import { FixedDropdownDirective } from "../../widget/fixed-dropdown/fixed-dropdown.directive";

let createModel: () => LegendFormatGeneralPaneModel = () => {
   return {
      title: "",
      titleValue: "",
      style: 0,
      fillColor: "",
      position: "",
      visible: false,
      notShowNull: false,
      notShowNullVisible: false,
      symbolSize: 0,
      gap: 0,
      roundCorners: false,
      symbolRoundCorners: false,
      symbolRoundCornersVisible: true,
      padding: { top: 0, left: 4, bottom: 1, right: 4, followsDefault: true }
   };
};

const PADDING_SIDES = ["top", "bottom", "left", "right"];

let paddingSteppers = (fixture: ComponentFixture<LegendFormatGeneralPane>): HTMLElement[] =>
   PADDING_SIDES.map(side =>
      fixture.nativeElement.querySelector(`[data-test="legend-padding-${side}"]`));

describe("LegendFormatGeneralPane Unit Tests", () => {
   let uiContextService: any;

   beforeEach(waitForAsync(() => {
      uiContextService = {
         isVS: vi.fn(),
         isAdhoc: vi.fn(),
         getDefaultTab: vi.fn(),
         setDefaultTab: vi.fn()
      };
      uiContextService.isAdhoc.mockImplementation(() => false);
      TestBed.configureTestingModule({
         imports: [
            CommonModule,
            NgbModule,
            FormsModule,
            ReactiveFormsModule,
            DropDownTestModule,
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
            FixedDropdownDirective,
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
   it("should have Ignore Null checkbox", () => new Promise<void>((done) => {
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
   }));

   it("labels the legend distance 'Distance from Chart'", () => {
      const fixture = TestBed.createComponent(LegendFormatGeneralPane);
      fixture.componentInstance.model = createModel();
      fixture.componentInstance.form = new FormGroup({});
      fixture.detectChanges();

      const labels: string[] = Array.from(fixture.nativeElement.querySelectorAll("label"))
         .map((e: HTMLElement) => e.textContent.trim());
      expect(labels).toContain("_#(Distance from Chart)");
      expect(labels).not.toContain("_#(Legend Gap)");
   });

   async function render(model: LegendFormatGeneralPaneModel):
      Promise<ComponentFixture<LegendFormatGeneralPane>>
   {
      const fixture = TestBed.createComponent(LegendFormatGeneralPane);
      fixture.componentInstance.model = model;
      fixture.componentInstance.form = new FormGroup({});
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
      return fixture;
   }

   it("captions the legend padding 'Row Spacing' and 'Side Indent'", async () => {
      const fixture = await render(createModel());

      const labels: string[] = Array.from(fixture.nativeElement.querySelectorAll("label"))
         .map((e: HTMLElement) => e.textContent.trim());
      expect(labels).toEqual(expect.arrayContaining([
         "_#(Row Spacing)", "_#(Side Indent)", "_#(Top)", "_#(Bottom)", "_#(Left)", "_#(Right)"
      ]));
   });

   it("disables the four spacing steppers while following the default", async () => {
      const fixture = await render(createModel());

      for(const stepper of paddingSteppers(fixture)) {
         expect(stepper).toBeTruthy();
         expect(stepper.classList.contains("disabled")).toBe(true);
      }

      const box: HTMLInputElement = fixture.nativeElement.querySelector("#legendPaddingFollowsDefault");
      expect(box).toBeTruthy();
      expect(box.checked).toBe(true);
   });

   it("enables the four spacing steppers once the box is unticked", async () => {
      const model = createModel();
      model.padding.followsDefault = false;
      const fixture = await render(model);

      for(const stepper of paddingSteppers(fixture)) {
         expect(stepper).toBeTruthy();
         expect(stepper.classList.contains("disabled")).toBe(false);
      }
   });

   it("toggles the four spacing steppers from the padding follow-default box", async () => {
      const fixture = await render(createModel());
      const box: HTMLInputElement = fixture.nativeElement.querySelector("#legendPaddingFollowsDefault");

      box.click();
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(fixture.componentInstance.model.padding.followsDefault).toBe(false);
      expect(fixture.componentInstance.model.gapFollowsDefault).toBeUndefined();

      for(const stepper of paddingSteppers(fixture)) {
         expect(stepper.classList.contains("disabled")).toBe(false);
      }

      box.click();
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(fixture.componentInstance.model.padding.followsDefault).toBe(true);

      for(const stepper of paddingSteppers(fixture)) {
         expect(stepper.classList.contains("disabled")).toBe(true);
      }
   });

   it("hides the padding follow-default box on an unmarked chart", async () => {
      const model = createModel();
      model.padding.followsDefault = undefined;
      const fixture = await render(model);

      expect(fixture.nativeElement.querySelector("#legendPaddingFollowsDefault")).toBeNull();

      for(const stepper of paddingSteppers(fixture)) {
         expect(stepper).toBeTruthy();
         expect(stepper.classList.contains("disabled")).toBe(false);
      }
   });
});
