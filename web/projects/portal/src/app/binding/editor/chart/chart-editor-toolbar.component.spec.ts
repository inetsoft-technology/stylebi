/*!
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
import { Component, Input } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { By } from "@angular/platform-browser";
import { GraphTypes } from "../../../common/graph-types";
import { ChartTypeButton } from "../../widget/chart-type-button.component";
import { ChartEditorService } from "../../services/chart/chart-editor.service";
import { ChartEditorToolbar } from "./chart-editor-toolbar.component";

@Component({ selector: "chart-type-button", template: "", standalone: true })
class StubChartTypeButton {
   @Input() toolbar: boolean;
   @Input() chartType: number;
   @Input() multiStyles: boolean;
   @Input() stackMeasures: boolean;
}

describe("ChartEditorToolbar", () => {
   let fixture: ComponentFixture<ChartEditorToolbar>;
   let editorService: any;

   function create(model: any): HTMLElement {
      fixture.componentInstance.bindingModel = {
         chartType: GraphTypes.CHART_BAR,
         rtchartType: GraphTypes.CHART_BAR,
         separated: false,
         multiStyles: false,
         stackMeasures: false,
         xfields: [],
         yfields: [],
         ...model
      } as any;
      fixture.detectChanges();
      return fixture.nativeElement;
   }

   beforeEach(() => {
      editorService = { changeSeparateStatus: vi.fn(), swapXYBinding: vi.fn() };

      TestBed.configureTestingModule({
         providers: [{ provide: ChartEditorService, useValue: editorService }]
      }).overrideComponent(ChartEditorToolbar, {
         remove: { imports: [ChartTypeButton] },
         add: { imports: [StubChartTypeButton] }
      });

      fixture = TestBed.createComponent(ChartEditorToolbar);
   });

   it("groups the buttons and gives them real button semantics", () => {
      const el = create({});
      const group = el.querySelector(".chart-toolbar-group");

      expect(group.getAttribute("role")).toBe("group");
      expect(group.querySelector("chart-type-button")).toBeTruthy();

      const buttons = Array.from(group.querySelectorAll("button"));
      expect(buttons.length).toBe(2);
      buttons.forEach(b => {
         expect(b.getAttribute("type")).toBe("button");
         expect(b.getAttribute("aria-label")).toBeTruthy();
         // the icon is decoration inside the button, not a nested link
         expect(b.querySelector("a")).toBeNull();
      });
   });

   it("exposes the separate-graphs state as aria-pressed", () => {
      const off = create({ separated: false });
      expect(off.querySelector("button[aria-pressed]").getAttribute("aria-pressed")).toBe("false");

      fixture.componentInstance.bindingModel.separated = true;
      fixture.detectChanges();
      expect(off.querySelector("button[aria-pressed]").getAttribute("aria-pressed")).toBe("true");
   });

   it("marks separate-graphs unavailable with aria-disabled and ignores the click", () => {
      const el = create({ chartType: GraphTypes.CHART_PIE, rtchartType: GraphTypes.CHART_PIE });
      const button = el.querySelector("button[aria-pressed]") as HTMLButtonElement;

      if(GraphTypes.isMergedGraphType(GraphTypes.CHART_PIE)) {
         expect(button.getAttribute("aria-disabled")).toBe("true");
         button.click();
         expect(editorService.changeSeparateStatus).not.toHaveBeenCalled();
      }
      else {
         expect(button.hasAttribute("aria-disabled")).toBe(false);
      }
   });

   it("keeps the Swap XY title that the smoke test selects by", () => {
      const el = create({});
      const swap = el.querySelector("button[title=\"_#(Swap XY)\"]") as HTMLButtonElement;

      expect(swap).toBeTruthy();
      swap.click();
      expect(editorService.swapXYBinding).toHaveBeenCalledTimes(1);
   });

   it("hides Swap XY for chart types that cannot swap", () => {
      const el = create({ rtchartType: GraphTypes.CHART_RADAR });
      expect(el.querySelector("button[title=\"_#(Swap XY)\"]")).toBeNull();
   });
});
