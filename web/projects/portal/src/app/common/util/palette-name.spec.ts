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
import { CategoricalColorModel } from "../data/visual-frame-model";
import { GraphPaletteDialog } from "../../widget/target/graph-palette-dialog.component";
import { LEGACY_PALETTE_NAME, paletteLabel } from "./palette-name";

function palette(name: string): CategoricalColorModel {
   const model = new CategoricalColorModel();
   model.name = name;
   model.colors = ["#000000"];
   return model;
}

describe("paletteLabel", () => {
   it("keeps the stored name of the original palette, which customer CSS overrides by name", () => {
      expect(LEGACY_PALETTE_NAME).toBe("Default");
   });

   it("shows the original palette as Legacy", () => {
      expect(paletteLabel("Default")).toBe("_#(js:Legacy)");
   });

   it("shows any other palette under its own name", () => {
      expect(paletteLabel("Modern")).toBe("Modern");
      expect(paletteLabel("Modern Dark")).toBe("Modern Dark");
      expect(paletteLabel("Heat 8")).toBe("Heat 8");
   });

   it("is case-sensitive, like the CSS name it stands for", () => {
      expect(paletteLabel("default")).toBe("default");
   });
});

describe("GraphPaletteDialog palette names", () => {
   it("labels the original palette Legacy and the others by name", () => {
      const dialog = new GraphPaletteDialog();
      dialog.colorPalettes = [palette("Default"), palette("Modern"), palette("Soft")];

      expect(dialog.paletteSelectOptions).toEqual([
         { label: "_#(js:Legacy)", value: 0 },
         { label: "Modern", value: 1 },
         { label: "Soft", value: 2 }
      ]);
   });
});
