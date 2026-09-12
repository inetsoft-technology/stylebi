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
 * ChartStylePane — Pass 1: Interaction
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] — ngOnInit/ngOnChanges/createStyles: build style rows from chartStyles input
 *   Group 2 [Risk 2] — updateChartType/multiChanged/stackMeasuresChanged: emit chartTypeChange events
 *   Group 3 [Risk 3] — stackChanged: preserve style index and emit new stacked chart type
 *   Group 4 [Risk 2] — applyClick: commit selected style to parent via chartTypeChange
 *
 * HTTP: no HTTP — styles supplied via @Input chartStyles
 *
 * Out of scope this pass: getCssIcon, stackEnabled, stackChecked, getImageBorder, multiDisabled, stylesToRows
 *   — covered in chart-style-pane.component.display.tl.spec.ts (Pass 3)
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render, screen } from "@testing-library/angular";
import userEvent from "@testing-library/user-event";
import { GraphTypes } from "../../common/graph-types";
import { UIContextService } from "../../common/services/ui-context.service";
import { ModelService } from "../../widget/services/model.service";
import { ChartStylePane, ChartStylesModel } from "./chart-style-pane.component";

const MOCK_STYLES: ChartStylesModel = {
   styles: [
      { label: "Bar", data: GraphTypes.CHART_BAR },
      { label: "Line", data: GraphTypes.CHART_LINE },
      { label: "Pie", data: GraphTypes.CHART_PIE },
      { label: "Custom", data: 999, custom: true }
   ],
   stackStyles: [
      { label: "Stack Bar", data: GraphTypes.CHART_BAR_STACK },
      { label: "Stack Line", data: GraphTypes.CHART_LINE_STACK }
   ]
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
         multiStyles: false,
         chartStyles: MOCK_STYLES,
         customChartTypes: ["999"],
         refName: null,
         stackMeasures: false,
         stackMeasuresVisible: true,
         stackMeasuresEnabled: true,
         popup: true,
         ...overrides
      }
   });
}

describe("ChartStylePane — createStyles [Group 1, Risk 2]", () => {
   it("should build stylesModel from non-stack styles on init", async () => {
      await renderPane();

      expect(screen.getByText("Bar")).toBeInTheDocument();
      expect(screen.getByText("Line")).toBeInTheDocument();
      expect(screen.getByText("Pie")).toBeInTheDocument();
      expect(screen.getByText("Custom")).toBeInTheDocument();
      expect(document.querySelectorAll(".chart-style-item")).toHaveLength(4);
   });

   it("should switch to stackStyles when stackChecked is true", async () => {
      await renderPane({ chartType: GraphTypes.CHART_BAR_STACK });

      expect(screen.getByText("Stack Bar")).toBeInTheDocument();
      expect(screen.getByText("Stack Line")).toBeInTheDocument();
      expect(screen.queryByText("Pie")).not.toBeInTheDocument();
   });

   it("should filter out custom styles not in customChartTypes", async () => {
      await renderPane({ customChartTypes: [] });

      expect(screen.queryByText("Custom")).not.toBeInTheDocument();
      expect(screen.getByText("Bar")).toBeInTheDocument();
   });
});

describe("ChartStylePane — emitters [Group 2, Risk 2]", () => {
   it("should emit changeChartType from updateChartType", async () => {
      const changed = vi.fn();
      const { fixture } = await renderPane();
      fixture.componentInstance.changeChartType.subscribe(changed);

      await userEvent.click(screen.getByTitle("Line"));

      expect(changed).toHaveBeenCalledWith(GraphTypes.CHART_LINE);
   });

   it("should emit multiStyles and reset chart type to AUTO on multiChanged", async () => {
      const multi = vi.fn();
      const type = vi.fn();
      const { fixture } = await renderPane();
      fixture.componentInstance.changeMultiStyles.subscribe(multi);
      fixture.componentInstance.changeChartType.subscribe(type);

      await userEvent.click(screen.getByLabelText("_#(Multiple Styles)"));

      expect(multi).toHaveBeenCalledWith(true);
      expect(type).toHaveBeenCalledWith(GraphTypes.CHART_AUTO);
   });

   it("should emit changeStackMeasures from stackMeasuresChanged", async () => {
      const stack = vi.fn();
      const { fixture } = await renderPane();
      fixture.componentInstance.changeStackMeasures.subscribe(stack);

      await userEvent.click(screen.getByLabelText("_#(Stack Measures)"));

      expect(stack).toHaveBeenCalledWith(true);
   });
});

describe("ChartStylePane — stackChanged [Group 3, Risk 3]", () => {
   it("should preserve selected index and emit stack chart type", async () => {
      const changed = vi.fn();
      const { fixture } = await renderPane({ chartType: GraphTypes.CHART_LINE });
      fixture.componentInstance.changeChartType.subscribe(changed);

      await userEvent.click(screen.getByLabelText("_#(Stack)"));

      expect(changed).toHaveBeenCalledWith(GraphTypes.CHART_LINE_STACK);
      expect(screen.getByText("Stack Line")).toBeInTheDocument();
   });
});

describe("ChartStylePane — applyClick [Group 4, Risk 2]", () => {
   it("should emit apply with false", async () => {
      const applied = vi.fn();
      const { fixture } = await renderPane();
      fixture.componentInstance.apply.subscribe(applied);

      await userEvent.click(screen.getByTitle("_#(Apply)"));

      expect(applied).toHaveBeenCalledWith(false);
   });
});
