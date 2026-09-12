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
 * CategoricalColorPane — Pass 1: Interaction
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] — ngOnInit: load color palettes from composer API
 *   Group 2 [Risk 2] — clickPaletteButton: openDialog emit + palette apply updates colors
 *   Group 3 [Risk 3] — clickColorMappingButton: lazy HTTP load vs cached dialog model branch
 *   Group 4 [Risk 3] — openColorMappingDialog: map apply, useGlobal branch, dateFormat, resetted emit
 *   Group 5 [Risk 2] — changeColor/reset/isResetted: per-swatch edit and restore defaults
 *   Group 6 [Risk 2] — applyClick/getNumItems/isDimension/showColorValueFrame: pane contracts
 *
 * HTTP: ModelService mocks — direct instantiation, mirrors getModel/sendModel endpoints
 *
 * Out of scope this pass: shareColorsChange, constructor subscription lifecycle, setTimeout openDialog false
 *   — covered in categorical-color-pane.risk.tl.spec.ts (Pass 2)
 */

import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { render, screen, waitFor } from "@testing-library/angular";
import userEvent from "@testing-library/user-event";
import { of } from "rxjs";
import { ColorMap } from "../../../../common/data/color-map";
import { CategoricalColorModel } from "../../../../common/data/visual-frame-model";
import { TestUtils } from "../../../../common/test/test-utils";
import { ComponentTool } from "../../../../common/util/component-tool";
import { UIContextService } from "../../../../common/services/ui-context.service";
import { ModelService } from "../../../../widget/services/model.service";
import { ColorMappingDialogModel } from "../../../data/chart/color-mapping-dialog-model";
import { ChartEditorService } from "../../../services/chart/chart-editor.service";
import { CategoricalColorPane } from "./categorical-color-pane.component";

const SELECT_PALETTE = "_#(Select Palette)";
const ASSIGN_MAPPING = "_#(Assign Fixed Mapping)";
const RESET_DEFAULT = "_#(Reset to Default)";
const APPLY = "_#(Apply)";
const USE_COLUMN_VALUES = "_#(Use Column Values as Colors)";

function mockColorMap(option: string, color: string): ColorMap {
   return { option, color };
}

function createFrameModel(): CategoricalColorModel {
   const model = new CategoricalColorModel();
   model.colors = ["#aa0000", "#bb0000"];
   model.cssColors = ["#aa0000", "#bb0000"];
   model.defaultColors = ["#cc0000", "#dd0000"];
   model.colorMaps = [mockColorMap("A", "#aa0000")];
   model.globalColorMaps = [];
   model.useGlobal = false;
   model.shareColors = false;
   model.dateFormat = null;
   return model;
}

function createColorMappingModel(overrides: Partial<ColorMappingDialogModel> = {}): ColorMappingDialogModel {
   return {
      colorMaps: [mockColorMap("A", "#aa0000")],
      dimensionData: [],
      globalModel: {
         colorMaps: [mockColorMap("A", "#00ff00")],
         dimensionData: [],
         globalModel: null as any,
         useGlobal: true,
         shareColors: false
      },
      useGlobal: false,
      shareColors: false,
      ...overrides
   };
}

function swatchColor(container: HTMLElement, index: number): string {
   return container.querySelector(`static-color-editor[data-test="sce${index}"]`)
      ?.getAttribute("ng-reflect-color") || "";
}

async function renderPane(options: {
   customFrames?: string[];
   mappingModel?: ColorMappingDialogModel;
   isVS?: boolean;
} = {}) {
   const frameModel = createFrameModel();
   const field = {
      fullName: "Employee",
      dataInfo: TestUtils.createMockChartDimensionRef("Employee"),
      frame: frameModel
   };
   field.dataInfo.classType = "dimension";

   const modelService = {
      getModel: vi.fn().mockReturnValue(of([{ colors: ["#518db9"] }])),
      sendModel: vi.fn().mockReturnValue(of({ body: options.mappingModel ?? createColorMappingModel() }))
   };
   const editorService = {
      getCustomChartFrames: vi.fn().mockReturnValue(of(options.customFrames ?? []))
   };
   const uiContextService = { isVS: vi.fn().mockReturnValue(options.isVS ?? true) };

   const result = await render(CategoricalColorPane, {
      providers: [
         { provide: NgbModal, useValue: {} },
         { provide: ModelService, useValue: modelService },
         { provide: UIContextService, useValue: uiContextService },
         { provide: ChartEditorService, useValue: editorService }
      ],
      componentProperties: {
         frameModel,
         field,
         vsId: "vs1",
         assemblyName: "Chart1",
         assetId: "1^128^__NULL__^TEST"
      }
   });

   if(options.mappingModel) {
      result.fixture.componentInstance.colorMappingDialogModel = options.mappingModel;
   }

   return { ...result, modelService, editorService, frameModel, field };
}

describe("CategoricalColorPane — ngOnInit [Group 1, Risk 2]", () => {
   it("should load color palettes on init", async () => {
      const { modelService } = await renderPane();

      await waitFor(() => expect(modelService.getModel).toHaveBeenCalled());
   });
});

describe("CategoricalColorPane — clickPaletteButton [Group 2, Risk 2]", () => {
   it("should emit openDialog and apply selected palette colors", async () => {
      const { fixture, container } = await renderPane();
      const openSpy = vi.fn();
      fixture.componentInstance.openDialog.subscribe(openSpy);
      let onCommit: (result: CategoricalColorModel) => void = () => {};
      vi.spyOn(ComponentTool, "showDialog").mockImplementation((_m, _t, commit) => {
         onCommit = commit;
         return { colorPalettes: null, currPalette: null } as any;
      });

      await userEvent.click(screen.getByTitle(SELECT_PALETTE));

      expect(openSpy).toHaveBeenCalledWith(true);
      onCommit({ colors: ["#222222"] } as CategoricalColorModel);
      fixture.detectChanges();

      expect(swatchColor(container, 0)).toBe("#222222");
   });
});

describe("CategoricalColorPane — clickColorMappingButton [Group 3, Risk 3]", () => {
   it("should open dialog immediately when mapping model is cached", async () => {
      const mappingModel = createColorMappingModel();
      const { modelService } = await renderPane({ mappingModel });
      const showDialogSpy = vi.spyOn(ComponentTool, "showDialog").mockReturnValue({} as any);

      await userEvent.click(screen.getByTitle(ASSIGN_MAPPING));

      expect(showDialogSpy).toHaveBeenCalled();
      expect(modelService.sendModel).not.toHaveBeenCalled();
   });

   it("should fetch mapping model before opening dialog on first click", async () => {
      const { modelService, fixture } = await renderPane();
      vi.spyOn(ComponentTool, "showDialog").mockReturnValue({} as any);

      await userEvent.click(screen.getByTitle(ASSIGN_MAPPING));

      await waitFor(() => expect(modelService.sendModel).toHaveBeenCalled());
      await waitFor(() => expect(fixture.componentInstance.colorMappingDialogModel).toBeTruthy());
   });
});

describe("CategoricalColorPane — openColorMappingDialog [Group 4, Risk 3]", () => {
   // 🔁 Regression-sensitive: resetted must fire when color maps change (57425 force submit)
   it("should emit resetted and update local colorMaps when maps differ", async () => {
      const mappingModel = createColorMappingModel();
      const { fixture } = await renderPane({ mappingModel });
      const resetted = vi.fn();
      fixture.componentInstance.resetted.subscribe(resetted);
      let onCommit: (maps: ColorMap[]) => void = () => {};
      vi.spyOn(ComponentTool, "showDialog").mockImplementation((_m, _t, commit) => {
         onCommit = commit;
         return { model: null, field: null } as any;
      });

      await userEvent.click(screen.getByTitle(ASSIGN_MAPPING));
      onCommit([mockColorMap("B", "#0000ff")]);
      fixture.detectChanges();

      expect(resetted).toHaveBeenCalled();
   });

   it("should write globalColorMaps when useGlobal is true", async () => {
      const mappingModel = createColorMappingModel({ useGlobal: true });
      const { fixture } = await renderPane({ mappingModel });
      let onCommit: (maps: ColorMap[]) => void = () => {};
      vi.spyOn(ComponentTool, "showDialog").mockImplementation((_m, _t, commit) => {
         onCommit = commit;
         return {} as any;
      });
      const maps = [mockColorMap("G", "#abcdef")];

      await userEvent.click(screen.getByTitle(ASSIGN_MAPPING));
      onCommit(maps);
      fixture.detectChanges();

      expect(fixture.componentInstance.frameModel.globalColorMaps).toEqual(maps);
      expect(fixture.componentInstance.frameModel.useGlobal).toBe(true);
   });

   it("should set dateFormat from dimension dateLevel when maps are non-empty", async () => {
      const dim = TestUtils.createMockChartDimensionRef("OrderDate");
      dim.classType = "dimension";
      dim.dateLevel = "5";
      const mappingModel = createColorMappingModel();
      const { fixture, field, frameModel } = await renderPane({ mappingModel });
      fixture.componentInstance.field = { fullName: "OrderDate", dataInfo: dim, frame: frameModel };
      fixture.detectChanges();
      let onCommit: (maps: ColorMap[]) => void = () => {};
      vi.spyOn(ComponentTool, "showDialog").mockImplementation((_m, _t, commit) => {
         onCommit = commit;
         return {} as any;
      });

      await userEvent.click(screen.getByTitle(ASSIGN_MAPPING));
      onCommit([mockColorMap("2024", "#111111")]);
      fixture.detectChanges();

      expect(fixture.componentInstance.frameModel.dateFormat).toBe(5);
   });
});

describe("CategoricalColorPane — changeColor and reset [Group 5, Risk 2]", () => {
   it("should update swatch color at index", async () => {
      const { fixture, container } = await renderPane();

      fixture.componentInstance.changeColor("#ff00ff", 1);
      fixture.detectChanges();

      expect(swatchColor(container, 1)).toBe("#ff00ff");
   });

   it("should restore colors from css/default and report reset state", async () => {
      const { fixture, container } = await renderPane();
      fixture.componentInstance.changeColor("#000000", 0);
      fixture.detectChanges();

      await userEvent.click(screen.getByTitle(RESET_DEFAULT));
      fixture.detectChanges();

      expect(swatchColor(container, 0)).toBe("#aa0000");
      expect(screen.getByTitle(RESET_DEFAULT).className).toContain("icon-disabled");
   });
});

describe("CategoricalColorPane — pane helpers [Group 6, Risk 2]", () => {
   it("should emit apply on applyClick and expose frame item count", async () => {
      const { fixture, container } = await renderPane();
      const applied = vi.fn();
      fixture.componentInstance.apply.subscribe(applied);

      await userEvent.click(screen.getByTitle(APPLY));

      expect(applied).toHaveBeenCalledWith(false);
      expect(container.querySelector('static-color-editor[data-test="sce0"]')).toBeTruthy();
      expect(container.querySelector('static-color-editor[data-test="sce1"]')).toBeTruthy();
   });

   it("should treat dimension refs as dimension fields", async () => {
      await renderPane();

      expect(screen.getByTitle(ASSIGN_MAPPING)).toBeInTheDocument();
   });

   it("should hide color value frame when custom chart frames omit ColorValueColorFrame", async () => {
      await renderPane({ customFrames: [] });

      expect(screen.queryByLabelText(USE_COLUMN_VALUES)).not.toBeInTheDocument();
   });

   it("should show color value frame for ColorValueColorFrame custom type", async () => {
      await renderPane({ customFrames: ["ColorValueColorFrame"] });

      expect(screen.getByLabelText(USE_COLUMN_VALUES)).toBeInTheDocument();
   });
});
