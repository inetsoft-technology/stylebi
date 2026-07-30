/*
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
import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { CHART_COLOR_PALETTE_URI, ChartPaletteService } from "./chart-palette.service";
import { DefaultPalette } from "./default-palette";
import { MODERN_HEAD, palette40 } from "./palette-test-fixtures";

const MODERN_40: string[] = palette40(MODERN_HEAD);

describe("ChartPaletteService", () => {
   let service: ChartPaletteService;
   let httpMock: HttpTestingController;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [HttpClientTestingModule],
         providers: [ChartPaletteService]
      });

      // constructing the service fires the fetch
      service = TestBed.inject(ChartPaletteService);
      httpMock = TestBed.inject(HttpTestingController);
   });

   afterEach(() => httpMock.verify());

   it("starts on the legacy fallback before the fetch resolves", () => {
      expect(service.chartPalette).toBe(DefaultPalette.chart);
      httpMock.expectOne(CHART_COLOR_PALETTE_URI).flush(MODERN_40);
   });

   it("chunks 40 colors into a 5x8 grid", () => {
      httpMock.expectOne(CHART_COLOR_PALETTE_URI).flush(MODERN_40);

      const grid = service.chartPalette;
      expect(grid.length).toBe(5);
      expect(grid[0].length).toBe(8);
      expect(grid[0][0]).toBe("#00d4e8");
      expect(grid[0][7]).toBe("#64748b");
      expect(grid[4][7]).toBe("#cccc33");
   });

   it("exposes a flat 40-entry list", () => {
      httpMock.expectOne(CHART_COLOR_PALETTE_URI).flush(MODERN_40);

      expect(service.flatColors().length).toBe(40);
      expect(service.flatColors()[0]).toBe("#00d4e8");
   });

   it("falls back to the legacy grid on HTTP error", () => {
      httpMock.expectOne(CHART_COLOR_PALETTE_URI)
         .flush("boom", { status: 500, statusText: "Server Error" });

      expect(service.chartPalette).toBe(DefaultPalette.chart);
   });

   it("falls back when the response is not 40 colors", () => {
      httpMock.expectOne(CHART_COLOR_PALETTE_URI).flush(["#00d4e8", "#00b87a"]);

      expect(service.chartPalette).toBe(DefaultPalette.chart);
   });

   it("falls back when the response has more than 40 colors", () => {
      const tooMany = MODERN_40.concat("#000000");
      httpMock.expectOne(CHART_COLOR_PALETTE_URI).flush(tooMany);

      expect(service.chartPalette).toBe(DefaultPalette.chart);
   });

   it("fetches only once across multiple subscribers", () => {
      httpMock.expectOne(CHART_COLOR_PALETTE_URI).flush(MODERN_40);

      service.chartPalette$.subscribe();
      service.chartPalette$.subscribe();

      httpMock.verify();
   });

   it("ensureLoaded() is idempotent — repeated calls issue exactly one request", () => {
      // the constructor already called ensureLoaded() once; these must not add another
      service.ensureLoaded();
      service.ensureLoaded();
      service.ensureLoaded();

      httpMock.expectOne(CHART_COLOR_PALETTE_URI).flush(MODERN_40);
   });
});
