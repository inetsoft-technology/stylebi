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
 * ChartStylePane — Pass 3: Display
 *
 * Risk-first coverage:
 *   Group 1 [Risk 1] — getCssIcon: chart type CSS class mapping for style tiles
 *   Group 2 [Risk 2] — stackEnabled/stackChecked: stack toggle visibility and checked state
 *   Group 3 [Risk 2] — getImageBorder/multiDisabled: selection border and multi-style guard
 *   Group 4 [Risk 1] — stylesToRows: chunk styles into rows of six for grid layout
 *
 * HTTP: no HTTP — display helpers only, no service calls
 *
 * Out of scope this pass: ngOnInit, createStyles, updateChartType, stackChanged, applyClick
 *   — covered in chart-style-pane.component.interaction.tl.spec.ts (Pass 1)
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render, screen } from "@testing-library/angular";
import { GraphTypes } from "../../common/graph-types";
import { UIContextService } from "../../common/services/ui-context.service";
import { ModelService } from "../../widget/services/model.service";
import { ChartStylePane, ChartStylesModel } from "./chart-style-pane.component";

const MOCK_STYLES: ChartStylesModel = {
   styles: Array.from({ length: 7 }, (_, i) => ({
      label: `Style${i}`,
      data: GraphTypes.CHART_BAR + i
   })),
   stackStyles: [{ label: "Stack Bar", data: GraphTypes.CHART_BAR_STACK }]
};

async function renderPane(overrides: Record<string, unknown> = {}) {
   return render(ChartStylePane, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: UIContextService, useValue: { isVS: () => true } },
         { provide: ModelService, useValue: {} }
      ],
      componentProperties: {
         chartType: GraphTypes.CHART_BAR,
         chartStyles: MOCK_STYLES,
         customChartTypes: [],
         multiStyles: false,
         refName: "Sum(id)",
         popup: false,
         ...overrides
      }
   });
}

describe("ChartStylePane — getCssIcon [Group 1, Risk 1]", () => {
   it("should map known chart types to CSS icon suffixes", async () => {
      const styles: ChartStylesModel = {
         styles: [
            { label: "Bar", data: GraphTypes.CHART_BAR },
            { label: "Pie", data: GraphTypes.CHART_PIE },
            { label: "Donut", data: GraphTypes.CHART_DONUT },
            { label: "Stack Bar", data: GraphTypes.CHART_BAR_STACK }
         ],
         stackStyles: []
      };
      const { container } = await renderPane({ chartStyles: styles });

      expect(container.querySelector(".chart-style-img-bar")).toBeTruthy();
      expect(container.querySelector(".chart-style-img-pie")).toBeTruthy();
      expect(container.querySelector(".chart-style-img-donut")).toBeTruthy();
      expect(container.querySelector(".chart-style-img-stackBar")).toBeTruthy();
   });
});

describe("ChartStylePane — stackEnabled / stackChecked [Group 2, Risk 2]", () => {
   it("should enable stack for bar inline types and hide for pie", async () => {
      const { container, rerender } = await renderPane({
         chartType: GraphTypes.CHART_BAR,
         multiStyles: false,
         refName: "Sum(id)"
      });

      expect(container.querySelector("#stack")).toBeTruthy();

      await rerender({
         componentProperties: {
            chartType: GraphTypes.CHART_PIE,
            chartStyles: MOCK_STYLES,
            customChartTypes: [],
            multiStyles: false,
            refName: "Sum(id)",
            popup: false
         }
      });

      expect(container.querySelector("#stack")).toBeFalsy();
   });

   // 🔁 Regression-sensitive: Bug #19389 / #19135 — multiStyles without refName must disable stack
   it("should disable stack when multiStyles without refName", async () => {
      const { container } = await renderPane({
         multiStyles: true,
         refName: null,
         chartType: GraphTypes.CHART_BAR
      });

      expect(container.querySelector("#stack")).toBeFalsy();
   });

   it("should allow stack when multiStyles with refName", async () => {
      const { container } = await renderPane({
         multiStyles: true,
         refName: "Sum(id)",
         chartType: GraphTypes.CHART_BAR
      });

      expect(container.querySelector("#stack")).toBeTruthy();
   });

   it("should detect stack chart types via stackChecked", async () => {
      const { container, rerender } = await renderPane({ chartType: GraphTypes.CHART_BAR_STACK });

      expect(container.querySelector<HTMLInputElement>("#stack")?.checked).toBe(true);

      await rerender({
         componentProperties: {
            chartType: GraphTypes.CHART_BAR,
            chartStyles: MOCK_STYLES,
            customChartTypes: [],
            multiStyles: false,
            refName: "Sum(id)",
            popup: false
         }
      });

      expect(container.querySelector<HTMLInputElement>("#stack")?.checked).toBe(false);
   });
});

describe("ChartStylePane — getImageBorder / multiDisabled [Group 3, Risk 2]", () => {
   it("should highlight selected chart type with border", async () => {
      const styles: ChartStylesModel = {
         styles: [
            { label: "Bar", data: GraphTypes.CHART_BAR },
            { label: "Pie", data: GraphTypes.CHART_PIE }
         ],
         stackStyles: []
      };
      const { container } = await renderPane({
         chartType: GraphTypes.CHART_BAR,
         chartStyles: styles
      });

      const barIcon = screen.getByTitle("Bar");
      const pieIcon = screen.getByTitle("Pie");

      expect(barIcon.style.borderWidth).toBe("2px");
      expect(pieIcon.style.borderWidth).not.toBe("2px");
   });

   it("should disable multiple-styles checkbox for merged graph types", async () => {
      const { container, rerender } = await renderPane({
         chartType: GraphTypes.CHART_RADAR,
         refName: null
      });

      expect(container.querySelector<HTMLInputElement>("#multi")?.disabled).toBe(true);

      await rerender({
         componentProperties: {
            chartType: GraphTypes.CHART_BAR,
            chartStyles: MOCK_STYLES,
            customChartTypes: [],
            multiStyles: false,
            refName: null,
            popup: false
         }
      });

      expect(container.querySelector<HTMLInputElement>("#multi")?.disabled).toBe(false);
   });
});

describe("ChartStylePane — stylesToRows [Group 4, Risk 1]", () => {
   it("should chunk styles into rows of at most 6 items", async () => {
      const { container } = await renderPane();

      const rows = container.querySelectorAll(".thumbnail-row");
      expect(rows).toHaveLength(2);
      expect(rows[0].querySelectorAll(".chart-style-item")).toHaveLength(6);
      expect(rows[1].querySelectorAll(".chart-style-item")).toHaveLength(1);
   });
});
