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
import { CategoricalColorModel } from "../../../common/data/visual-frame-model";
import {
   DARK_HEAD,
   LEGACY_HEAD,
   LEGACY_TAIL,
   MODERN_HEAD,
   palette40
} from "../../../widget/color-picker/palette-test-fixtures";
import { PaletteDialog } from "./palette-dialog.component";

const HEAT_HEAD: string[] = [
   "#663300", "#914800", "#bd5e00", "#e97400", "#ff8a15", "#ffa041", "#ffb66d", "#ffcc99"
];

function palette(name: string, head: string[], hidden = false): CategoricalColorModel {
   const model = new CategoricalColorModel();
   model.name = name;
   model.colors = palette40(head);
   model.hidden = hidden;
   return model;
}

function dialogWith(currentColors: string[], paletteOrder?: CategoricalColorModel[]): PaletteDialog {
   const dialog = new PaletteDialog();
   dialog.colorPalettes = paletteOrder || [
      palette("Default", LEGACY_HEAD),
      palette("Modern", MODERN_HEAD),
      palette("Modern Dark", DARK_HEAD)
   ];
   const curr = new CategoricalColorModel();
   curr.colors = currentColors;
   dialog.currPalette = curr;
   return dialog;
}

describe("PaletteDialog pre-selection", () => {
   it("pre-selects Modern for a chart rendering modern defaults", () => {
      const dialog = dialogWith(palette40(MODERN_HEAD));
      expect(dialog.displayPalette.name).toBe("Modern");
      expect(dialog._reversed).toBe(false);
   });

   it("pre-selects Modern Dark for a chart rendering dark defaults", () => {
      const dialog = dialogWith(palette40(DARK_HEAD));
      expect(dialog.displayPalette.name).toBe("Modern Dark");
      expect(dialog._reversed).toBe(false);
   });

   // Default is placed after index 0 here so a pass can only mean genuine color-equality
   // matching, not the index-0 fallback (which test 4 already covers).
   it("still pre-selects Default for a legacy chart", () => {
      const dialog = dialogWith(palette40(LEGACY_HEAD), [
         palette("Modern", MODERN_HEAD),
         palette("Modern Dark", DARK_HEAD),
         palette("Default", LEGACY_HEAD)
      ]);
      expect(dialog.displayPalette.name).toBe("Default");
      expect(dialog._reversed).toBe(false);
   });

   // Nothing matches => index 0. Default is declared first in defaults.css specifically so
   // this fallback is unchanged for existing installs.
   it("falls back to the first palette when nothing matches", () => {
      const custom = MODERN_HEAD.slice();
      custom[3] = "#123456";
      const dialog = dialogWith(custom.concat(LEGACY_TAIL));
      expect(dialog.displayPalette.name).toBe("Default");
   });
});

describe("PaletteDialog hidden palettes", () => {
   function dialogWithHidden(currentColors: string[]): PaletteDialog {
      const dialog = new PaletteDialog();
      dialog.colorPalettes = [
         palette("Default", LEGACY_HEAD),
         palette("Modern", MODERN_HEAD),
         palette("Heat 8", HEAT_HEAD, true)
      ];
      const curr = new CategoricalColorModel();
      curr.colors = currentColors;
      dialog.currPalette = curr;
      return dialog;
   }

   it("drops a hidden palette from the dropdown", () => {
      const dialog = dialogWithHidden(palette40(MODERN_HEAD));
      expect(dialog.paletteSelectOptions.map((o) => o.label)).toEqual(["Default", "Modern"]);
   });

   it("keeps a hidden palette in the dropdown when the chart is using it", () => {
      const dialog = dialogWithHidden(palette40(HEAT_HEAD));
      expect(dialog.displayPalette.name).toBe("Heat 8");
      expect(dialog.paletteSelectOptions.map((o) => o.label))
         .toEqual(["Default", "Modern", "Heat 8"]);
   });

   // hidden entry is non-terminal: a post-filter renumbering bug would still emit [0, 1]
   it("keeps option values aligned with the unfiltered array", () => {
      const dialog = new PaletteDialog();
      dialog.colorPalettes = [
         palette("Default", LEGACY_HEAD),
         palette("Heat 8", HEAT_HEAD, true),
         palette("Modern", MODERN_HEAD)
      ];
      const curr = new CategoricalColorModel();
      curr.colors = palette40(MODERN_HEAD);
      dialog.currPalette = curr;
      expect(dialog.paletteSelectOptions.map((o) => o.value)).toEqual([0, 2]);
   });

   it("represents a chart's own hidden palette correctly in the dropdown", () => {
      const dialog = dialogWithHidden(palette40(HEAT_HEAD));
      expect(dialog.paletteSelectOptions).toContainEqual({ value: 2, label: "Heat 8" });
   });
});
