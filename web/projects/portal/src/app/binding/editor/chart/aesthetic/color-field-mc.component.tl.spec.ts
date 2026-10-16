/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

/**
 * ColorFieldMc — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] — getField/getFrames: colorField vs aggregate vs radar/contour frame paths
 *   Group 2 [Risk 2] — changeColorFrame: update field or binding frame and submit on change
 *   Group 3 [Risk 2] — getEditPaneId: CategoricalColor vs LinearColor vs StaticColor vs CombinedColor
 *   Group 4 [Risk 2] — isPrimaryField/isContour/getHint/isDropPaneAccept: chart-type guards
 *   Group 5 [Risk 2] — reset/setAestheticRef/syncAllChartAggregateInfo: aggregate sync contracts
 *   Group 6 [Risk 3] — Assign Fixed Mapping dialog dismissed (Cancel / header X / Escape):
 *                      frame unchanged and nothing submitted when the dropdown closes (Bug #78706)
 *
 * HTTP: no HTTP — chart binding editor local state only (Group 6 mocks ModelService.sendModel
 *       with a server-shaped ColorMappingDialogModel)
 *
 * Out of scope:
 *   dragFieldMCComplete/drop/convert — DnD orchestration owned by AestheticFieldMc base
 *   openChanged/submitIfChanged — dropdown lifecycle, covered via changeColorFrame submit path
 */

import { ComponentFixture } from "@angular/core/testing";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { render, screen, waitFor } from "@testing-library/angular";
import userEvent from "@testing-library/user-event";
import { of } from "rxjs";
import { DndService } from "../../../../common/dnd/dnd.service";
import { GraphTypes } from "../../../../common/graph-types";
import {
   CategoricalColorModel,
   GradientColorModel,
   StaticColorModel
} from "../../../../common/data/visual-frame-model";
import { TestUtils } from "../../../../common/test/test-utils";
import { UIContextService } from "../../../../common/services/ui-context.service";
import { AestheticInfo } from "../../../data/chart/aesthetic-info";
import { ChartEditorService } from "../../../services/chart/chart-editor.service";
import { ModelService } from "../../../../widget/services/model.service";
import { FIELD_MC_PROVIDERS } from "./field-mc-test-helpers";
import { ColorFieldMc } from "./color-field-mc.component";

const EDIT_COLOR = "_#(Edit Color)";

async function renderColorFieldMc(
   bindingModel = TestUtils.createMockChartBindingModel(),
   extra: Record<string, unknown> = {}
) {
   const editorService = {
      changeChartAesthetic: vi.fn(),
      isDropPaneAccept: vi.fn().mockReturnValue(true),
      getDNDType: vi.fn().mockReturnValue(0),
      getCustomChartFrames: vi.fn().mockReturnValue(of([]))
   };
   const result = await render(ColorFieldMc, {
      providers: [
         ...FIELD_MC_PROVIDERS,
         { provide: ChartEditorService, useValue: editorService },
         { provide: DndService, useValue: {} },
         { provide: UIContextService, useValue: { isVS: () => false } },
         { provide: ModelService, useValue: { getModel: vi.fn().mockReturnValue(of([])), sendModel: vi.fn().mockReturnValue(of({ body: null })) } }
      ],
      componentProperties: {
         bindingModel,
         assemblyName: "Chart1",
         ...extra
      }
   });
   return { ...result, editorService, bindingModel };
}

async function openColorDropdown(container: HTMLElement, fixture: ComponentFixture<ColorFieldMc>) {
   const trigger = container.querySelector(".visual-cell-container, [data-test='colorIcon']");
   if(trigger) {
      await userEvent.click(trigger);
      fixture.detectChanges();
      await fixture.whenStable();
   }
}

describe("ColorFieldMc — getField and getFrames [Group 1, Risk 2]", () => {
   it("should return colorField frame when aesthetic field is bound", async () => {
      const bindingModel = TestUtils.createMockChartBindingModel();
      const frame = new CategoricalColorModel();
      bindingModel.colorField = {
         fullName: "state",
         dataInfo: TestUtils.createMockChartDimensionRef("state"),
         frame
      };
      const { container } = await renderColorFieldMc(bindingModel);

      expect(container.querySelector('[data-test="color-field-container"]')).toBeTruthy();
      expect(container.querySelector("color-cell")).toBeTruthy();
      expect(container.querySelector("[data-test='colorIcon']")).toBeFalsy();
   });

   it("should use binding colorFrame for radar charts without color field", async () => {
      const bindingModel = TestUtils.createMockChartBindingModel();
      bindingModel.chartType = GraphTypes.CHART_RADAR;
      bindingModel.colorFrame = new StaticColorModel();
      const { container } = await renderColorFieldMc(bindingModel);

      expect(container.querySelector("[data-test='colorIcon']")).toBeTruthy();
   });
});

describe("ColorFieldMc — changeColorFrame [Group 2, Risk 2]", () => {
   it("should update field frame and emit onChange when frames change", async () => {
      const bindingModel = TestUtils.createMockChartBindingModel();
      const frame = new CategoricalColorModel();
      bindingModel.colorField = {
         fullName: "state",
         dataInfo: TestUtils.createMockChartDimensionRef("state"),
         frame
      };
      const { fixture, editorService } = await renderColorFieldMc(bindingModel);
      const nextFrame = new GradientColorModel();
      const changed = vi.fn();
      fixture.componentInstance.onChange.subscribe(changed);
      fixture.componentInstance.openChanged(true);

      fixture.componentInstance.changeColorFrame(nextFrame);
      fixture.componentInstance.submitIfChanged();

      expect(bindingModel.colorField.frame).toBe(nextFrame);
      expect(changed).toHaveBeenCalled();
      expect(editorService.changeChartAesthetic).toHaveBeenCalledWith("color");
   });
});

describe("ColorFieldMc — getEditPaneId [Group 3, Risk 2]", () => {
   it("should return LinearColor for contour charts", async () => {
      const bindingModel = TestUtils.createMockChartBindingModel();
      bindingModel.chartType = GraphTypes.CHART_MAP_CONTOUR;
      bindingModel.yfields = [TestUtils.createMockChartAggregateRef("Sum(qty)")];
      bindingModel.colorFrame = new GradientColorModel();
      const { container, fixture } = await renderColorFieldMc(bindingModel);

      await openColorDropdown(container, fixture);

      expect(document.querySelector("linear-color-pane")).toBeTruthy();
   });

   it("should return CategoricalColor for discrete dimension color field", async () => {
      const bindingModel = TestUtils.createMockChartBindingModel();
      const dim = TestUtils.createMockChartDimensionRef("state");
      const frame = new CategoricalColorModel();
      frame.colors = ["#aa0000"];
      frame.cssColors = ["#aa0000"];
      frame.defaultColors = ["#aa0000"];
      frame.colorMaps = [];
      frame.globalColorMaps = [];
      bindingModel.colorField = { fullName: "state", dataInfo: dim, frame };
      const { container, fixture } = await renderColorFieldMc(bindingModel, { assetId: "1^128^__NULL__^TEST" });

      await openColorDropdown(container, fixture);

      expect(document.querySelector("categorical-color-pane")).toBeTruthy();
   });

   it("should return StaticColor for unbound color with single frame", async () => {
      const bindingModel = TestUtils.createMockChartBindingModel();
      bindingModel.yfields = [TestUtils.createMockChartAggregateRef("Sum(qty)")];
      bindingModel.colorFrame = new StaticColorModel();
      const { container, fixture } = await renderColorFieldMc(bindingModel);

      await openColorDropdown(container, fixture);

      expect(document.querySelector("static-color-pane")).toBeTruthy();
   });
});

describe("ColorFieldMc — chart-type guards [Group 4, Risk 2]", () => {
   it("should mark pie charts as primary color field", async () => {
      const bindingModel = TestUtils.createMockChartBindingModel();
      bindingModel.chartType = GraphTypes.CHART_PIE;
      const { container } = await renderColorFieldMc(bindingModel);

      expect(container.querySelector(".primary-label")).toBeTruthy();
   });

   it("should disable drop pane and show contour hint for contour charts", async () => {
      const bindingModel = TestUtils.createMockChartBindingModel();
      bindingModel.chartType = GraphTypes.CHART_MAP_CONTOUR;
      bindingModel.yfields = [TestUtils.createMockChartAggregateRef("Sum(qty)")];
      bindingModel.colorFrame = new GradientColorModel();
      const { fixture } = await renderColorFieldMc(bindingModel);
      const comp = fixture.componentInstance as any;

      expect(comp.isDropPaneAccept()).toBe(false);
      expect(comp.getHint()).toBe("Edit Color");
   });
});

describe("ColorFieldMc — aggregate sync [Group 5, Risk 2]", () => {
   it("should set colorField on binding model via setAestheticRef", async () => {
      const bindingModel = TestUtils.createMockChartBindingModel();
      const { fixture, container } = await renderColorFieldMc(bindingModel);
      const ref = {
         fullName: "qty",
         dataInfo: TestUtils.createMockChartAggregateRef("qty"),
         frame: null
      } as AestheticInfo;

      fixture.componentInstance.setAestheticRef(ref);
      fixture.componentInstance.ngOnChanges(null);
      fixture.detectChanges();

      expect(bindingModel.colorField).toBe(ref);
      expect(container.querySelector('[data-test="color-field-container"] chart-fieldmc')).toBeTruthy();
   });

   it("should clear oframes on reset to force submit", async () => {
      const { fixture } = await renderColorFieldMc();
      (fixture.componentInstance as any).oframes = JSON.stringify([{ clazz: "x" }]);

      fixture.componentInstance.reset();

      expect((fixture.componentInstance as any).oframes).toBe("");
   });
});

type DismissMode = "cancel" | "x" | "esc";

function categoricalFrame(colorMaps: any[], dateFormat: number | null,
                          useGlobal = false): CategoricalColorModel
{
   const frame = new CategoricalColorModel();
   frame.colors = ["#aa0000", "#00aa00"];
   frame.cssColors = ["#aa0000", "#00aa00"];
   frame.defaultColors = ["#aa0000", "#00aa00"];
   frame.colorMaps = colorMaps;
   frame.globalColorMaps = [];
   frame.useGlobal = useGlobal;
   frame.shareColors = useGlobal;
   frame.dateFormat = dateFormat;
   return frame;
}

/**
 * Opens the real fixedDropdown, clicks "Assign Fixed Mapping" in the real CategoricalColorPane,
 * dismisses the real ColorMappingDialog (opened through ComponentTool.showDialog and NgbModal),
 * then closes the dropdown so AestheticFieldMc.submitIfChanged() runs.
 */
async function dismissColorMappingDialog(mode: DismissMode, frame: CategoricalColorModel,
                                         serverMaps: any[], useGlobal = false)
{
   const bindingModel = TestUtils.createMockChartBindingModel();
   const dim: any = TestUtils.createMockChartDimensionRef("state");
   dim.classType = "dimension";
   dim.dateLevel = "5";
   bindingModel.colorField = { fullName: "state", dataInfo: dim, frame } as any;
   const dimensionData = [{ label: "CA", value: "CA" }, { label: "NY", value: "NY" }];
   // server shape: an unmapped field still gets one colourless row
   const dialogModel: any = {
      colorMaps: useGlobal ? [] : serverMaps,
      dimensionData,
      globalModel: {
         colorMaps: useGlobal ? serverMaps : [{ option: "CA", color: null }],
         dimensionData,
         globalModel: null,
         useGlobal: true,
         shareColors: useGlobal
      },
      useGlobal,
      shareColors: useGlobal
   };
   const editorService = {
      changeChartAesthetic: vi.fn(),
      isDropPaneAccept: vi.fn().mockReturnValue(true),
      getDNDType: vi.fn().mockReturnValue(0),
      getCustomChartFrames: vi.fn().mockReturnValue(of([]))
   };
   const { container, fixture } = await render(ColorFieldMc, {
      providers: [
         ...FIELD_MC_PROVIDERS,
         { provide: NgbModal, useClass: NgbModal },
         { provide: ChartEditorService, useValue: editorService },
         { provide: DndService, useValue: {} },
         { provide: UIContextService, useValue: { isVS: () => true, isAdhoc: () => false } },
         {
            provide: ModelService,
            useValue: {
               getModel: vi.fn().mockReturnValue(of([])),
               sendModel: vi.fn().mockReturnValue(of({ body: dialogModel }))
            }
         }
      ],
      componentProperties: {
         bindingModel,
         assemblyName: "Chart1",
         vsId: "vs1",
         assetId: "1^128^__NULL__^TEST"
      } as any
   });
   const snapshot = () => JSON.stringify({
      colorMaps: frame.colorMaps,
      globalColorMaps: frame.globalColorMaps,
      dateFormat: frame.dateFormat,
      useGlobal: frame.useGlobal
   });
   const before = snapshot();

   const trigger = container.querySelector(".visual-cell-container") as HTMLElement;
   await userEvent.click(trigger);
   fixture.detectChanges();
   await fixture.whenStable();
   const icon = await waitFor(() => {
      const found = document.querySelector(
         "categorical-color-pane [title='_#(Assign Fixed Mapping)']") as HTMLElement;
      expect(found).toBeTruthy();
      return found;
   });
   await userEvent.click(icon);
   const dialog = await waitFor(() => {
      const found = document.querySelector(".modal-content") as HTMLElement;
      expect(found?.querySelector(".modal-footer")).toBeTruthy();
      return found;
   });

   if(mode === "cancel") {
      const cancel = Array.from(dialog.querySelectorAll(".modal-footer button"))
         .find(b => b.textContent.includes("Cancel")) as HTMLElement;
      await userEvent.click(cancel);
   }
   else if(mode === "x") {
      await userEvent.click(dialog.querySelector("modal-header .btn-close") as HTMLElement);
   }
   else {
      // NgbModal listens for Escape on its window element
      const win = document.querySelector("ngb-modal-window") as HTMLElement;
      win.focus();
      win.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", code: "Escape", bubbles: true }));
      win.dispatchEvent(new KeyboardEvent("keyup", { key: "Escape", code: "Escape", bubbles: true }));
   }

   await waitFor(() => expect(document.querySelector(".modal-content")).toBeFalsy(),
                 { timeout: 3000 });
   await new Promise(resolve => setTimeout(resolve, 50));
   fixture.detectChanges();
   const afterDialog = snapshot();

   // close the dropdown, which runs openChanged(false) -> submitIfChanged()
   await userEvent.click(trigger);
   fixture.detectChanges();
   await fixture.whenStable();

   return { before, afterDialog, editorService };
}

describe("ColorFieldMc — fixed mapping dialog dismissed [Group 6, Risk 3]", () => {
   afterEach(() => {
      document.body.innerHTML = "";
   });

   // 🔁 Regression-sensitive: Bug #78706 — Cancel / header X used to run the OK callback with the
   // initial rows, so an unmapped field got a colourless row and a dateFormat, and was submitted
   for(const mode of ["cancel", "x", "esc"] as DismissMode[]) {
      it(`should leave an unmapped field unchanged and submit nothing on ${mode}`, async () => {
         const frame = categoricalFrame([], null);

         const { before, afterDialog, editorService } =
            await dismissColorMappingDialog(mode, frame, [{ option: "CA", color: null }]);

         expect(afterDialog).toBe(before);
         expect(editorService.changeChartAesthetic).not.toHaveBeenCalled();
      });
   }

   it("should not set dateFormat on a mapped field when cancelled", async () => {
      const frame = categoricalFrame([{ option: "CA", color: "#ff0000" }], null);

      const { before, afterDialog, editorService } =
         await dismissColorMappingDialog("cancel", frame, [{ option: "CA", color: "#ff0000" }]);

      expect(afterDialog).toBe(before);
      expect(frame.dateFormat).toBeNull();
      expect(editorService.changeChartAesthetic).not.toHaveBeenCalled();
   });

   it("should leave shared colours unchanged when cancelled with Share Colors on", async () => {
      const frame = categoricalFrame([], null, true);

      const { before, afterDialog, editorService } =
         await dismissColorMappingDialog("cancel", frame, [{ option: "CA", color: null }], true);

      expect(afterDialog).toBe(before);
      expect(editorService.changeChartAesthetic).not.toHaveBeenCalled();
   });
});
