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

function palette(name: string, head: string[]): CategoricalColorModel {
   const model = new CategoricalColorModel();
   model.name = name;
   model.colors = palette40(head);
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
