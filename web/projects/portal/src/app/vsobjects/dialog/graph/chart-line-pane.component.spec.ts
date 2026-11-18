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
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { ChartLinePaneModel } from "../../model/chart-line-pane-model";
import { ChartLinePane } from "./chart-line-pane.component";

const createModel = (): ChartLinePaneModel => ({
   xGridLineStyle: 0,
   xGridLineColor: "#d6d2c8",
   yGridLineStyle: 4097,
   yGridLineColor: "#d6d2c8",
   gridLineVisible: true,
   quadrantGridLineStyle: 0,
   quadrantGridLineColor: "#d6d2c8",
   diagonalLineStyle: 0,
   diagonalLineColor: "#d6d2c8",
   innerLineVisible: false,
   trendLineType: "NONE",
   trendPerColor: false,
   trendLineStyle: 0,
   trendLineColor: "#d6d2c8",
   trendLineVisible: false,
   projectForward: 0,
   projectForwardEnabled: false,
   lineTabVisible: true,
   facetGrid: false,
   facetGridColor: "#d6d2c8",
   facetGridVisible: false,
   facetGridEnabled: false,
   trendLineMeasures: [],
   measures: []
});

describe("ChartLinePane grid rows", () => {
   let fixture: ComponentFixture<ChartLinePane>;

   const render = (model: ChartLinePaneModel): HTMLElement => {
      fixture.componentInstance.model = model;
      fixture.detectChanges();
      return fixture.nativeElement as HTMLElement;
   };

   beforeEach(async () => {
      await TestBed.configureTestingModule({
         imports: [ChartLinePane],
         schemas: [NO_ERRORS_SCHEMA]
      }).overrideComponent(ChartLinePane, {
         set: {
            imports: [FormsModule, ReactiveFormsModule],
            schemas: [NO_ERRORS_SCHEMA]
         }
      }).compileComponents();

      fixture = TestBed.createComponent(ChartLinePane);
   });

   it("should hide the X grid row but keep the Y one when xGridLineVisible is false", () => {
      const model = createModel();
      model.xGridLineVisible = false;
      const el = render(model);

      expect(el.querySelector("[data-test=\"x-grid-line\"]")).toBeNull();
      expect(el.querySelector("[data-test=\"x-grid-line-color\"]")).toBeNull();
      expect(el.querySelector("[data-test=\"y-grid-line\"]")).not.toBeNull();
   });

   it("should show both rows when xGridLineVisible is undefined", () => {
      const el = render(createModel());

      expect(el.querySelector("[data-test=\"x-grid-line\"]")).not.toBeNull();
      expect(el.querySelector("[data-test=\"y-grid-line\"]")).not.toBeNull();
   });

   it("should show both rows when xGridLineVisible is true", () => {
      const model = createModel();
      model.xGridLineVisible = true;
      const el = render(model);

      expect(el.querySelector("[data-test=\"x-grid-line\"]")).not.toBeNull();
      expect(el.querySelector("[data-test=\"y-grid-line\"]")).not.toBeNull();
   });

   it("should show neither row when gridLineVisible is false", () => {
      const model = createModel();
      model.gridLineVisible = false;
      model.xGridLineVisible = false;
      const el = render(model);

      expect(el.querySelector("[data-test=\"x-grid-line\"]")).toBeNull();
      expect(el.querySelector("[data-test=\"y-grid-line\"]")).toBeNull();
   });
});
