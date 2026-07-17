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
 * CategoricalColorPane — Pass 2: Risk
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — shareColorsChange: HTTP reload syncs dialog and frame maps after toggle
 *   Group 2 [Risk 3] — resetted emit guard: skip when color maps unchanged (Tool.isEquals)
 *   Group 3 [Risk 3] — openDialog false timing: setTimeout after palette/mapping dialog close
 *   Group 4 [Risk 3] — constructor getCustomChartFrames: subscription without ngOnDestroy
 *
 * HTTP: ModelService mocks — shareColorsChange uses sendModel subscribe callback
 *
 * Suspected bugs (header only):
 *   Constructor getCustomChartFrames subscribe — no ngOnDestroy teardown; prescan async_zones=6
 *
 * Out of scope this pass: ngOnInit, clickPaletteButton, changeColor, reset, applyClick, isDimension
 *   — covered in categorical-color-pane.interaction.tl.spec.ts (Pass 1)
 */

import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { fireEvent, render, screen, waitFor } from "@testing-library/angular";
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
const SHARE_COLORS = "_#(Share Colors)";
const USE_COLUMN_VALUES = "_#(Use Column Values as Colors)";

function mockColorMap(option: string, color: string): ColorMap {
   return { option, color };
}

function createFrameModel(): CategoricalColorModel {
   const model = new CategoricalColorModel();
   model.colors = ["#aa0000"];
   model.cssColors = ["#aa0000"];
   model.defaultColors = ["#cc0000"];
   model.colorMaps = [mockColorMap("A", "#aa0000")];
   model.globalColorMaps = [mockColorMap("A", "#00ff00")];
   model.useGlobal = false;
   model.shareColors = false;
   return model;
}

function createSyncedMappingModel(): ColorMappingDialogModel {
   return {
      colorMaps: [mockColorMap("A", "#aa0000")],
      dimensionData: [],
      globalModel: {
         colorMaps: [mockColorMap("A", "#00ff00")],
         dimensionData: [],
         globalModel: null as any,
         useGlobal: false,
         shareColors: true
      },
      useGlobal: false,
      shareColors: false
   };
}

async function renderPane(mappingModel?: ColorMappingDialogModel, customFrames: string[] = ["ColorValueColorFrame"]) {
   const frameModel = createFrameModel();
   const field = {
      fullName: "Employee",
      dataInfo: Object.assign(TestUtils.createMockChartDimensionRef("Employee"), { classType: "dimension" }),
      frame: frameModel
   };

   const modelService = {
      getModel: vi.fn().mockReturnValue(of([])),
      sendModel: vi.fn().mockReturnValue(of({ body: mappingModel ?? createSyncedMappingModel() }))
   };
   const editorService = {
      getCustomChartFrames: vi.fn().mockReturnValue(of(customFrames))
   };

   const result = await render(CategoricalColorPane, {
      providers: [
         { provide: NgbModal, useValue: {} },
         { provide: ModelService, useValue: modelService },
         { provide: UIContextService, useValue: { isVS: () => true } },
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

   result.fixture.componentInstance.colorMappingDialogModel = mappingModel ?? createSyncedMappingModel();

   return { ...result, modelService, editorService, frameModel };
}

describe("CategoricalColorPane — shareColorsChange [Group 1, Risk 3]", () => {
   it("should reload mapping model and sync frame maps after enabling share", async () => {
      const { modelService, fixture } = await renderPane();

      // Direct call — avoid userEvent (Zone hang under loaded TL worker).
      fixture.componentInstance.shareColorsChange(true);
      fixture.detectChanges();

      await waitFor(() => expect(modelService.sendModel).toHaveBeenCalled());
      expect(fixture.componentInstance.frameModel.useGlobal).toBe(true);
      expect(fixture.componentInstance.frameModel.shareColors).toBe(true);
      expect(fixture.componentInstance.colorMappingDialogModel.shareColors).toBe(true);
      expect(fixture.componentInstance.frameModel.globalColorMaps).toEqual(
         fixture.componentInstance.colorMappingDialogModel.globalModel.colorMaps
      );
      expect(screen.getByLabelText(SHARE_COLORS)).toBeChecked();
   });
});

describe("CategoricalColorPane — resetted emit guard [Group 2, Risk 3]", () => {
   it("should not emit resetted when committed maps equal current maps", async () => {
      const mappingModel = createSyncedMappingModel();
      const { fixture } = await renderPane(mappingModel);
      const resetted = vi.fn();
      fixture.componentInstance.resetted.subscribe(resetted);
      let onCommit: (maps: ColorMap[]) => void = () => {};
      vi.spyOn(ComponentTool, "showDialog").mockImplementation((_m, _t, commit) => {
         onCommit = commit;
         return {} as any;
      });

      fixture.componentInstance.clickColorMappingButton();
      onCommit([mockColorMap("A", "#aa0000")]);
      fixture.detectChanges();

      expect(resetted).not.toHaveBeenCalled();
   });
});

describe("CategoricalColorPane — openDialog false timing [Group 3, Risk 3]", () => {
   it("should emit openDialog false on next tick after palette dialog commit", async () => {
      vi.useFakeTimers();
      const { fixture } = await renderPane();
      const events: boolean[] = [];
      fixture.componentInstance.openDialog.subscribe(v => events.push(v));
      vi.spyOn(ComponentTool, "showDialog").mockImplementation((_m, _t, commit) => {
         commit({ colors: ["#fff"] } as CategoricalColorModel);
         return {} as any;
      });

      fireEvent.click(screen.getByTitle(SELECT_PALETTE));
      vi.runAllTimers();

      expect(events).toEqual([true, false]);
      vi.useRealTimers();
   });
});

describe("CategoricalColorPane — constructor subscription [Group 4, Risk 3]", () => {
   it("should load custom chart frames in constructor but not implement ngOnDestroy", async () => {
      const editorService = {
         getCustomChartFrames: vi.fn().mockReturnValue(of(["ColorValueColorFrame"]))
      };
      const frameModel = createFrameModel();
      const field = {
         fullName: "Employee",
         dataInfo: Object.assign(TestUtils.createMockChartDimensionRef("Employee"), { classType: "dimension" }),
         frame: frameModel
      };

      const { fixture } = await render(CategoricalColorPane, {
         providers: [
            { provide: NgbModal, useValue: {} },
            { provide: ModelService, useValue: { getModel: vi.fn().mockReturnValue(of([])), sendModel: vi.fn() } },
            { provide: UIContextService, useValue: { isVS: () => true } },
            { provide: ChartEditorService, useValue: editorService }
         ],
         componentProperties: { frameModel, field, vsId: "vs1", assemblyName: "Chart1", assetId: "1^128^__NULL__^TEST" }
      });

      await waitFor(() => expect(editorService.getCustomChartFrames).toHaveBeenCalled());
      await waitFor(() => expect(screen.getByLabelText(USE_COLUMN_VALUES)).toBeInTheDocument());
      expect((fixture.componentInstance as any).ngOnDestroy).toBeUndefined();
   });
});
