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
import { readability, TinyColor } from "@ctrl/tinycolor";
import { resolveStripBackground, stripGlyphInk, stripGlyphTone } from "./strip-glyph-tone";

// Measured independently of the module under test: composite the returned ink at the returned alpha
// and read the ratio straight from TinyColor. The pick and the alpha climb are not reused here.
function achieved(background: string): number {
   const { tone, alpha } = stripGlyphInk(background);
   const ink = tone === "light" ? "#000000" : "#FFFFFF";
   const composited = new TinyColor(ink).setAlpha(alpha).onBackground(background);

   return readability(composited, background);
}

describe("stripGlyphInk", () => {
   it("leaves every seeded default at its base alpha", () => {
      // white card, the filled title lane, and both dark-mode surfaces
      expect(stripGlyphInk("#FFFFFF")).toEqual({ tone: "light", alpha: 0.55 });
      expect(stripGlyphInk("#F1EFEA")).toEqual({ tone: "light", alpha: 0.55 });
      expect(stripGlyphInk("#2D2B30")).toEqual({ tone: "dark", alpha: 0.8 });
      expect(stripGlyphInk("#252428")).toEqual({ tone: "dark", alpha: 0.8 });
   });

   it("picks dark ink on the mid-tone saturated colours a luminance threshold gets wrong", () => {
      // a fixed L > 0.45 threshold selects the light ink here and lands at 1.86:1
      expect(stripGlyphInk("#2FC4B2").tone).toBe("light");
      expect(stripGlyphInk("#E8563F").tone).toBe("light");
      expect(achieved("#2FC4B2")).toBeGreaterThanOrEqual(3);
      expect(achieved("#E8563F")).toBeGreaterThanOrEqual(3);
   });

   it("picks light ink on genuinely dark grounds", () => {
      expect(stripGlyphInk("#000000").tone).toBe("dark");
      expect(stripGlyphInk("#1C1B1F").tone).toBe("dark");
   });

   it("raises the alpha only where the floor demands it, and only slightly", () => {
      expect(stripGlyphInk("#EE1199").alpha).toBeGreaterThan(0.55);
      expect(stripGlyphInk("#EE1199").alpha).toBeLessThan(0.6);
      expect(stripGlyphInk("#DD5544").alpha).toBeGreaterThan(0.8);
      expect(stripGlyphInk("#DD5544").alpha).toBeLessThan(0.85);
      expect(stripGlyphInk("#778888").alpha).toBeGreaterThan(0.55);
      expect(stripGlyphInk("#778888").alpha).toBeLessThan(0.6);
   });

   it("never returns a pair below the contrast floor, across the colour cube", () => {
      const failures: string[] = [];

      for(let r = 0; r < 256; r += 17) {
         for(let g = 0; g < 256; g += 17) {
            for(let b = 0; b < 256; b += 17) {
               const bg = new TinyColor({ r, g, b }).toHexString();

               if(achieved(bg) < 3) {
                  failures.push(bg);
               }
            }
         }
      }

      expect(failures).toEqual([]);
   });
});

function model(over: any = {}): any {
   return Object.assign({ objectFormat: {}, vizModern: true, vizDark: false }, over);
}

describe("resolveStripBackground", () => {
   it("prefers the title lane, which is what the anchored strip physically sits in", () => {
      const m = model({
         titleFormat: { background: "#F1EFEA" },
         objectFormat: { background: "#FFFFFF" }
      });

      expect(resolveStripBackground(m)).toBe("#F1EFEA");
   });

   it("falls through to the card when the lane is unfilled", () => {
      expect(resolveStripBackground(model({ objectFormat: { background: "#252428" } })))
         .toBe("#252428");
      expect(resolveStripBackground(model({
         titleFormat: { background: null },
         objectFormat: { background: "#252428" }
      }))).toBe("#252428");
   });

   it("treats transparent, empty and unparseable values as absent", () => {
      const card = { background: "#FFFFFF" };

      expect(resolveStripBackground(model({ titleFormat: { background: "" }, objectFormat: card })))
         .toBe("#FFFFFF");
      expect(resolveStripBackground(
         model({ titleFormat: { background: "transparent" }, objectFormat: card }))).toBe("#FFFFFF");
      expect(resolveStripBackground(
         model({ titleFormat: { background: "rgba(0,0,0,0)" }, objectFormat: card }))).toBe("#FFFFFF");
      expect(resolveStripBackground(
         model({ titleFormat: { background: "not-a-colour" }, objectFormat: card }))).toBe("#FFFFFF");
   });

   it("ends on a default that follows the dark flag, not a fixed white", () => {
      expect(resolveStripBackground(model())).toBe("#FFFFFF");
      expect(resolveStripBackground(model({ vizDark: true }))).toBe("#252428");
   });

   it("does not throw on a model with no formats at all", () => {
      expect(resolveStripBackground(<any> {})).toBe("#FFFFFF");
      expect(resolveStripBackground(null)).toBe("#FFFFFF");
   });
});

describe("resolveStripBackground with translucent layers", () => {
   it("composites a translucent lane onto the card rather than treating it as opaque", () => {
      const m = model({
         titleFormat: { background: "rgba(45,43,48,0.3)" },
         objectFormat: { background: "#FFFFFF" }
      });

      // 30% of #2D2B30 over white is a light grey, not a near-black
      expect(new TinyColor(resolveStripBackground(m)).getLuminance()).toBeGreaterThan(0.5);
   });

   it("picks the ink that reads against the composited result, not the raw colour", () => {
      const m = model({
         titleFormat: { background: "rgba(45,43,48,0.3)" },
         objectFormat: { background: "#FFFFFF" }
      });

      expect(stripGlyphTone(m).tone).toBe("light");
   });

   it("clears the floor on a translucent lane over either card colour", () => {
      for(const card of ["#FFFFFF", "#252428"]) {
         for(const lane of ["rgba(45,43,48,0.3)", "rgba(255,255,255,0.25)",
                            "rgba(255,255,255,0.5)", "rgba(47,196,178,0.4)"]) {
            const m = model({ titleFormat: { background: lane }, objectFormat: { background: card } });
            const bg = resolveStripBackground(m);
            const { tone, alpha } = stripGlyphTone(m);
            const ink = tone === "light" ? "#000000" : "#FFFFFF";

            expect(readability(new TinyColor(ink).setAlpha(alpha).onBackground(bg), bg))
               .toBeGreaterThanOrEqual(3);
         }
      }
   });

   it("accepts the rgba string form the server actually sends for an opaque colour", () => {
      // VSCSSUtil.getBackgroundRGBA always emits rgba(...), never hex
      expect(stripGlyphTone(model({ titleFormat: { background: "rgba(255,255,255,1.0)" } })))
         .toEqual({ tone: "light", alpha: 0.55 });
      expect(stripGlyphTone(model({ titleFormat: { background: "rgba(37,36,40,1.0)" } })))
         .toEqual({ tone: "dark", alpha: 0.8 });
   });
});

describe("stripGlyphTone", () => {
   it("returns the ink for the resolved background", () => {
      expect(stripGlyphTone(model({ titleFormat: { background: "#252428" } })))
         .toEqual({ tone: "dark", alpha: 0.8 });
      expect(stripGlyphTone(model({ objectFormat: { background: "#FFFFFF" } })))
         .toEqual({ tone: "light", alpha: 0.55 });
   });

   it("returns the identical object for a repeated background, so change detection is cheap", () => {
      const a = stripGlyphTone(model({ objectFormat: { background: "#2FC4B2" } }));
      const b = stripGlyphTone(model({ objectFormat: { background: "#2FC4B2" } }));

      expect(b).toBe(a);
   });

   it("does not confuse two assemblies whose backgrounds differ only in the dark flag", () => {
      expect(stripGlyphTone(model({ vizDark: false })).tone).toBe("light");
      expect(stripGlyphTone(model({ vizDark: true })).tone).toBe("dark");
   });
});
