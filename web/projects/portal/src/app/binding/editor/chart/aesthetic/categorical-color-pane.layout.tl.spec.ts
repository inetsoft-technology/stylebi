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
 * CategoricalColorPane: layout
 *
 * The swatches are the colors the values take in order, so the pane numbers them. The first eight
 * are always shown. The rest of the palette sits behind a collapsed "More colors" row, which opens
 * by itself when a color in it has been customized.
 */

import { getDebugNode } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { render, screen } from "@testing-library/angular";
import userEvent from "@testing-library/user-event";
import { of } from "rxjs";
import { CategoricalColorModel } from "../../../../common/data/visual-frame-model";
import { TestUtils } from "../../../../common/test/test-utils";
import { UIContextService } from "../../../../common/services/ui-context.service";
import { ModelService } from "../../../../widget/services/model.service";
import { ChartEditorService } from "../../../services/chart/chart-editor.service";
import { CategoricalColorPane } from "./categorical-color-pane.component";

function hex(i: number): string {
   return "#" + (0x100000 + i * 0x101).toString(16).padStart(6, "0");
}

function frameModel(count: number, custom: { [index: number]: string } = {}): CategoricalColorModel {
   const model = new CategoricalColorModel();
   const defaults = Array.from({ length: count }, (_, i) => hex(i));
   model.defaultColors = defaults;
   model.cssColors = [];
   model.colors = defaults.map((c, i) => custom[i] ?? c);
   model.colorMaps = [];
   model.globalColorMaps = [];
   return model;
}

async function renderPane(model: CategoricalColorModel) {
   const field = {
      fullName: "Employee",
      dataInfo: TestUtils.createMockChartDimensionRef("Employee"),
      frame: model
   };
   field.dataInfo.classType = "dimension";

   return render(CategoricalColorPane, {
      providers: [
         { provide: NgbModal, useValue: {} },
         {
            provide: ModelService,
            useValue: {
               getModel: vi.fn().mockReturnValue(of([])),
               sendModel: vi.fn().mockReturnValue(of({}))
            }
         },
         { provide: UIContextService, useValue: { isVS: vi.fn().mockReturnValue(true) } },
         {
            provide: ChartEditorService,
            useValue: { getCustomChartFrames: vi.fn().mockReturnValue(of([])) }
         }
      ],
      componentProperties: {
         frameModel: model,
         field,
         vsId: "vs1",
         assemblyName: "Chart1",
         assetId: "1^128^__NULL__^TEST"
      }
   });
}

const swatches = (container: HTMLElement) =>
   Array.from(container.querySelectorAll("static-color-editor"));

const numbers = (container: HTMLElement) =>
   Array.from(container.querySelectorAll(".color-pane__number")).map(n => n.textContent.trim());

const toggle = (container: HTMLElement) =>
   container.querySelector<HTMLButtonElement>("[data-test='more-colors-toggle']");

describe("CategoricalColorPane: numbered swatches", () => {
   it("numbers each swatch with its position, and shows no more than eight to start", async () => {
      const { container } = await renderPane(frameModel(40));

      expect(swatches(container).length).toBe(8);
      expect(numbers(container)).toEqual(["1", "2", "3", "4", "5", "6", "7", "8"]);
   });

   it("has no More colors row when the palette has eight colors or fewer", async () => {
      const { container } = await renderPane(frameModel(8));

      expect(swatches(container).length).toBe(8);
      expect(toggle(container)).toBeNull();
   });

   it("shows only as many swatches as there are colors", async () => {
      const { container } = await renderPane(frameModel(3));

      expect(swatches(container).length).toBe(3);
   });
});

describe("CategoricalColorPane: More colors", () => {
   it("is collapsed to start, and names the positions it covers", async () => {
      const { container } = await renderPane(frameModel(40));

      expect(toggle(container).getAttribute("aria-expanded")).toBe("false");
      expect(toggle(container).textContent).toContain("9–40");
      expect(swatches(container).length).toBe(8);
   });

   it("opens to show the other colors, numbered on from the eighth, and closes again", async () => {
      const { container } = await renderPane(frameModel(40));

      await userEvent.click(toggle(container));
      expect(toggle(container).getAttribute("aria-expanded")).toBe("true");
      expect(swatches(container).length).toBe(40);
      expect(numbers(container)[8]).toBe("9");
      expect(numbers(container)[39]).toBe("40");

      await userEvent.click(toggle(container));
      expect(toggle(container).getAttribute("aria-expanded")).toBe("false");
      expect(swatches(container).length).toBe(8);
   });

   it("opens by itself when a color past the eighth has been customized", async () => {
      const { container } = await renderPane(frameModel(40, { 11: "#123456" }));

      expect(toggle(container).getAttribute("aria-expanded")).toBe("true");
      expect(swatches(container).length).toBe(40);
      const swatch = container.querySelector("static-color-editor[data-test='sce11']");
      expect(getDebugNode(swatch)?.componentInstance?.color).toBe("#123456");
   });

   it("stays collapsed when only a color among the first eight is customized", async () => {
      const { container } = await renderPane(frameModel(40, { 2: "#123456" }));

      expect(toggle(container).getAttribute("aria-expanded")).toBe("false");
      expect(swatches(container).length).toBe(8);
   });

   it("keeps the user's choice once they have closed an automatically opened row", async () => {
      const { container } = await renderPane(frameModel(40, { 11: "#123456" }));

      await userEvent.click(toggle(container));
      expect(toggle(container).getAttribute("aria-expanded")).toBe("false");
   });
});

describe("CategoricalColorPane: buttons", () => {
   it("has named buttons for the palette and fixed mapping, with Reset and Apply as text", async () => {
      await renderPane(frameModel(40));

      expect(screen.getByRole("button", { name: "_#(Select Palette)" })).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "_#(Assign Fixed Mapping)" })).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "_#(Reset to Default)" })).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "_#(Apply)" })).toBeInTheDocument();
   });

   it("disables Reset while every color is the palette's own", async () => {
      await renderPane(frameModel(40));

      expect(screen.getByRole("button", { name: "_#(Reset to Default)" })).toBeDisabled();
   });

   it("enables Reset once a color has been changed", async () => {
      await renderPane(frameModel(40, { 0: "#123456" }));

      expect(screen.getByRole("button", { name: "_#(Reset to Default)" })).toBeEnabled();
   });
});
