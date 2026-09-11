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
import { HttpClient } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { Observable, of } from "rxjs";
import { catchError, map, shareReplay, tap } from "rxjs/operators";
import { ColorPalette } from "./color-classes";
import { DefaultPalette } from "./default-palette";

// owned by inetsoft.web.portal.controller.ChartColorPaletteController
export const CHART_COLOR_PALETTE_URI: string = "../api/portal/chart-color-palette";

const ROWS: number = 5;
const COLUMNS: number = 8;

/**
 * Serves the categorical chart swatch grid, resolved server-side for the org's modern and dark
 * gates. Falls back to the legacy grid until the fetch resolves, and permanently if it fails.
 */
@Injectable({
   providedIn: "root"
})
export class ChartPaletteService {
   private grid: ColorPalette = DefaultPalette.chart;
   private readonly palette$: Observable<ColorPalette>;
   private loaded: boolean = false;

   constructor(private http: HttpClient) {
      this.palette$ = this.http.get<string[]>(CHART_COLOR_PALETTE_URI).pipe(
         map((colors) => ChartPaletteService.toGrid(colors)),
         catchError(() => of(DefaultPalette.chart)),
         tap((grid) => this.grid = grid),
         shareReplay(1)
      );
      this.ensureLoaded();
   }

   /**
    * Kicks off the palette fetch if it has not started yet. Safe to call more than once — the
    * bootstrap app components call this explicitly so the warm-up is not left resting on an
    * otherwise-unused constructor parameter; the request itself only ever fires once.
    */
   ensureLoaded(): void {
      if(this.loaded) {
         return;
      }

      this.loaded = true;
      this.palette$.subscribe();
   }

   get chartPalette(): ColorPalette {
      return this.grid;
   }

   get chartPalette$(): Observable<ColorPalette> {
      return this.palette$;
   }

   flatColors(): string[] {
      return this.grid.flat();
   }

   // ColorPalette is a strict 5x8 tuple, and the template iterates rows, so a ragged grid
   // would break rendering. Anything but exactly 40 colors keeps the fallback.
   private static toGrid(colors: string[]): ColorPalette {
      if(!colors || colors.length !== ROWS * COLUMNS) {
         return DefaultPalette.chart;
      }

      const rows: string[][] = [];

      for(let i = 0; i < colors.length; i += COLUMNS) {
         rows.push(colors.slice(i, i + COLUMNS));
      }

      return rows as ColorPalette;
   }
}
