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
 * The field icon buttons (edit measure, value type) are 30px bordered squares, the height of the
 * field box. The text layout designer's Field chip shrinks the value trigger to 22px, so it has to
 * shrink these too, or the chip grows taller than the Text and Spacer chips. Computed styles need a
 * real layout, so this guards the rule by name.
 */
import { readFileSync } from "fs";
import { dirname, join } from "path";
import { fileURLToPath } from "url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const read = (name: string): string => readFileSync(join(__dirname, name), "utf-8");

describe("text layout designer Field chip", () => {
   const scss = read("text-layout-designer.component.scss");
   const chip = scss.slice(scss.indexOf(".field-chip-wrap {"));

   it("sizes the edit-measure and value-type buttons to the 22px value trigger", () => {
      const rule = chip.match(/button\.fieldEditIcon,\s*\.dynamic-combo-box-body \.type-toggle\s*\{([^}]*)\}/);

      expect(rule, "no compact size rule for the field icon buttons").not.toBeNull();
      expect(rule[1]).toMatch(/width:\s*22px/);
      expect(rule[1]).toMatch(/height:\s*22px/);
   });

   it("keeps the value trigger at 22px", () => {
      expect(chip).toMatch(/\.dynamic-combo-box__trigger\.form-control[\s\S]*?height:\s*22px/);
   });
});

describe("field icon button", () => {
   const scss = read("../../fieldmc.component.scss");

   it("shows no hover chip on a disabled button, which [disabled] does not mark natively", () => {
      expect(scss).toMatch(/:hover:not\(:disabled\)[^{]*:not\(\[aria-disabled="true"\]\)[^{]*:not\(:has\(> i\.icon-disabled\)\)/);
   });

   it("shows the focus ring only for keyboard focus", () => {
      expect(scss).toMatch(/button\.fieldEditIcon\s*\{[\s\S]*?&:focus:not\(:focus-visible\)/);
   });
});
