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
import { ColorPalette } from "../../widget/color-picker/color-classes";
import {
   DARK_HEAD,
   grid40,
   MODERN_HEAD,
   palette40
} from "../../widget/color-picker/palette-test-fixtures";
import { ColorFieldPane } from "./color-field-pane.component";

const GRID: ColorPalette = grid40(palette40(MODERN_HEAD));
const OTHER: ColorPalette = grid40(palette40(DARK_HEAD));

describe("ColorFieldPane", () => {
   it("seeds its palette from the chart palette service", () => {
      const pane = new ColorFieldPane({ chartPalette: GRID } as any);
      expect(pane.palette).toBe(GRID);
   });

   it("allows an explicit palette to override the service default", () => {
      const pane = new ColorFieldPane({ chartPalette: GRID } as any);
      pane.palette = OTHER;
      expect(pane.palette).toBe(OTHER);
      expect(pane.palette).not.toBe(GRID);
   });
});
