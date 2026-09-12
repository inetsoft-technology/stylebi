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
 * ChartDataEditor — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] — isPrimaryField: pie/stock/geo/treemap/mekko primary field routing
 *   Group 2 [Risk 2] — displayLabel: chart-type-specific drag hint i18n keys
 *   Group 3 [Risk 2] — dragOver/checkDropValid/getDropType: drop acceptance guards
 *   Group 4 [Risk 2] — dragOverField: Mekko chart forces replace on y-field drag
 *   Group 5 [Risk 1] — convert: delegate field conversion to editor service
 *
 * HTTP: no HTTP — chart binding drag-drop local state only
 *
 * Out of scope:
 *   getFieldComponents/drop — DOM QueryList drop target wiring
 *   grayedOutValues — @Input passthrough only, no transform logic in component
 */

import { ChangeDetectorRef } from "@angular/core";
import { DragEvent } from "../../../common/data/drag-event";
import { DndService } from "../../../common/dnd/dnd.service";
import { GraphTypes } from "../../../common/graph-types";
import { TestUtils } from "../../../common/test/test-utils";
import { BindingService } from "../../services/binding.service";
import { ChartEditorService } from "../../services/chart/chart-editor.service";
import { ChartDataEditor } from "./chart-data-editor.component";

function createEditor(options: {
   fieldType?: string;
   chartType?: number;
   refs?: object[];
} = {}) {
   const dservice = { setDragOverStyle: vi.fn(), processOnDrop: vi.fn() } as unknown as DndService;
   const editorService = {
      isDropPaneAccept: vi.fn().mockReturnValue(true),
      getDNDType: vi.fn().mockReturnValue(3),
      convert: vi.fn()
   } as unknown as ChartEditorService;
   const bindingService = { assemblyName: "Chart1", objectType: "CHART" } as BindingService;
   const comp = new ChartDataEditor(
      dservice,
      editorService,
      bindingService,
      { detectChanges: vi.fn() } as unknown as ChangeDetectorRef
   );
   comp.bindingModel = TestUtils.createMockChartBindingModel();
   comp.bindingModel.chartType = options.chartType ?? GraphTypes.CHART_BAR;
   comp.fieldType = options.fieldType ?? "yfields";
   comp.refs = options.refs ?? [];
   return { comp, dservice, editorService };
}

describe("ChartDataEditor — isPrimaryField [Group 1, Risk 2]", () => {
   it("should treat yfields as primary for pie and radar charts", () => {
      const pie = createEditor({ chartType: GraphTypes.CHART_PIE, fieldType: "yfields" }).comp;
      const xPie = createEditor({ chartType: GraphTypes.CHART_PIE, fieldType: "xfields" }).comp;

      expect(pie.isPrimaryField()).toBe(true);
      expect(xPie.isPrimaryField()).toBe(false);
   });

   it("should treat xfields as primary for stock charts and geofields for geo charts", () => {
      const stock = createEditor({ chartType: GraphTypes.CHART_STOCK, fieldType: "xfields" }).comp;
      const geo = createEditor({ chartType: GraphTypes.CHART_MAP, fieldType: "geofields" }).comp;

      expect(stock.isPrimaryField()).toBe(true);
      expect(geo.isPrimaryField()).toBe(true);
   });

   it("should treat groupfields as primary for treemap and mekko group axis", () => {
      const treemap = createEditor({
         chartType: GraphTypes.CHART_TREEMAP,
         fieldType: "groupfields"
      }).comp;
      const mekkoY = createEditor({
         chartType: GraphTypes.CHART_MEKKO,
         fieldType: "yfields"
      }).comp;

      expect(treemap.isPrimaryField()).toBe(true);
      expect(mekkoY.isPrimaryField()).toBe(true);
   });
});

describe("ChartDataEditor — displayLabel [Group 2, Risk 2]", () => {
   it("should return empty label when refs are already bound", () => {
      const { comp } = createEditor({ refs: [{}] });

      expect(comp.displayLabel).toBe("");
   });

   it("should return mekko-specific drag hints for xfields and yfields", () => {
      const x = createEditor({ chartType: GraphTypes.CHART_MEKKO, fieldType: "xfields" }).comp;
      const y = createEditor({ chartType: GraphTypes.CHART_MEKKO, fieldType: "yfields" }).comp;

      expect(x.displayLabel).toBe("_#(js:common.DataEditor.dragDimension)");
      expect(y.displayLabel).toBe("_#(js:common.DataEditor.dragMeasure)");
   });

   it("should return geo longitude/latitude hints for xfields and yfields", () => {
      const x = createEditor({ chartType: GraphTypes.CHART_MAP, fieldType: "xfields" }).comp;
      const y = createEditor({ chartType: GraphTypes.CHART_MAP, fieldType: "yfields" }).comp;

      expect(x.displayLabel).toBe("_#(js:common.DataEditor.dragDimensionOrLongitude)");
      expect(y.displayLabel).toBe("_#(js:common.DataEditor.dragDimensionOrLatitude)");
   });
});

describe("ChartDataEditor — drop guards [Group 3, Risk 2]", () => {
   it("should style drag-over when drop is accepted", () => {
      const { comp, dservice } = createEditor();
      const event = { preventDefault: vi.fn() } as unknown as DragEvent;

      comp.dragOver(event);

      expect(event.preventDefault).toHaveBeenCalled();
      expect(dservice.setDragOverStyle).toHaveBeenCalledWith(event, true);
   });

   it("should reject drop when pane is not accepted", () => {
      const { comp, editorService } = createEditor();
      (editorService.isDropPaneAccept as any).mockReturnValue(false);

      expect((comp as any).checkDropValid()).toBe(false);
   });

   it("should reject drop when DND type is invalid", () => {
      const { comp, editorService } = createEditor();
      (editorService.getDNDType as any).mockReturnValue(-1);

      expect((comp as any).checkDropValid()).toBe(false);
      expect((comp as any).getDropType()).toBe("-1");
   });
});

describe("ChartDataEditor — dragOverField Mekko [Group 4, Risk 2]", () => {
   it("should force replace at index 0 for mekko yfields when refs already exist", () => {
      const { comp } = createEditor({
         chartType: GraphTypes.CHART_MEKKO,
         fieldType: "yfields",
         refs: [{}]
      });
      const event = { preventDefault: vi.fn(), stopPropagation: vi.fn() } as unknown as DragEvent;

      comp.dragOverField(event, 2, false);

      expect(comp.activeIdx).toBe(0);
      expect(comp.replaceField).toBe(true);
   });
});

describe("ChartDataEditor — convert [Group 5, Risk 1]", () => {
   it("should delegate convert to editor service with binding model", () => {
      const { comp, editorService } = createEditor();
      const event = { name: "state", type: "dimension" };

      comp.convert(event);

      expect(editorService.convert).toHaveBeenCalledWith("state", "dimension", comp.bindingModel);
   });
});
