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
import "@angular/compiler";
import { afterEach, describe, expect, it, vi } from "vitest";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { HighlightDialogModel } from "./highlight-dialog-model";
import { HighlightModel } from "./highlight-model";
import { HighlightPane } from "./highlight-pane.component";

function highlight(name: string): HighlightModel {
   return { name, applyRow: false, vsConditionDialogModel: { conditionList: [] } } as HighlightModel;
}

function createModel(chartAssembly: boolean, highlights: HighlightModel[] = []): HighlightDialogModel {
   return { highlights, tableName: "T1", fields: [], chartAssembly } as HighlightDialogModel;
}

function createPane(model: HighlightDialogModel, selected: HighlightModel | null = null) {
   const modalService = { open: vi.fn() };
   const comp = new HighlightPane(modalService as unknown as NgbModal);
   comp.model = model;
   comp.selectedHighlight = selected ?? model.highlights[0] ?? null;
   return comp;
}

describe("HighlightPane — semantic presets", () => {
   afterEach(() => {
      document.body.classList.remove("viz-modern");
   });

   it("shows presets for a non-chart highlight when the modern gate is on", () => {
      document.body.classList.add("viz-modern");
      const comp = createPane(createModel(false, [highlight("h1")]));
      expect(comp.showSemanticPresets).toBe(true);
   });

   it("hides presets when the modern gate is off", () => {
      const comp = createPane(createModel(false, [highlight("h1")]));
      expect(comp.showSemanticPresets).toBe(false);
   });

   it("hides presets for a chart highlight even when the gate is on", () => {
      document.body.classList.add("viz-modern");
      const comp = createPane(createModel(true, [highlight("h1")]));
      expect(comp.showSemanticPresets).toBe(false);
   });

   it("applies both foreground and background of a preset to the selected highlight", () => {
      const h = highlight("h1");
      const comp = createPane(createModel(false, [h]), h);
      const warning = comp.semanticPresets.find((p) => p.label.includes("Warning"))!;

      comp.applyPreset(warning);

      expect(h.foreground).toBe("#7A4E10");
      expect(h.background).toBe("#F8E8CC");
   });

   it("carries the exact modern Warning and Anomaly values", () => {
      const comp = createPane(createModel(false));
      const [warning, anomaly] = comp.semanticPresets;

      expect(warning).toMatchObject({ foreground: "#7A4E10", background: "#F8E8CC" });
      expect(anomaly).toMatchObject({ foreground: "#7F2E2E", background: "#F7DEDE" });
   });

   it("is a no-op when no highlight is selected", () => {
      const comp = createPane(createModel(false, []), null);
      expect(() => comp.applyPreset(comp.semanticPresets[0])).not.toThrow();
      expect(comp.selectedHighlight).toBeNull();
   });
});
