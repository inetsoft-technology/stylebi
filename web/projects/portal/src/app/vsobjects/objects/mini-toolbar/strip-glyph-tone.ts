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
import { VSObjectModel } from "../../model/vs-object-model";

/** WCAG 1.4.11 floor for a meaningful non-text control. */
const MIN_CONTRAST = 3;
/** Resting alphas. The ink is raised above these only where the floor demands it. */
const LIGHT_BASE_ALPHA = 0.55;
const DARK_BASE_ALPHA = 0.8;
/** Climb step, within half a percent of the lowest alpha that clears the floor. */
const ALPHA_STEP = 0.005;
const CANVAS_LIGHT = "#FFFFFF";
const CANVAS_DARK = "#252428";
/** Distinct backgrounds per session are few; the cap only stops unbounded growth. */
const MEMO_LIMIT = 64;

/** Which ground the glyph sits on: light means dark ink, dark means light ink. */
export interface StripGlyphTone {
   tone: "light" | "dark";
   alpha: number;
}

function contrast(ink: string, alpha: number, background: string): number {
   return readability(new TinyColor(ink).setAlpha(alpha).onBackground(background), background);
}

/**
 * The lowest alpha at or above base that clears the floor. Returns base untouched when the floor
 * is already met, so an unlifted alpha compares exactly.
 */
function climb(ink: string, base: number, background: string): number {
   let alpha = base;

   while(contrast(ink, alpha, background) < MIN_CONTRAST && alpha < 1) {
      alpha = Math.min(1, alpha + ALPHA_STEP);
   }

   return alpha;
}

/**
 * The ink that measures higher against the background at its resting alpha, raised only as far as
 * the contrast floor requires. Deliberately no luminance threshold: a fixed one picks the
 * lower-contrast ink for every background between L 0.235 and 0.45, which is where saturated teals
 * and greens sit.
 *
 * The ink is chosen before the climb, not after. Where both inks start under the floor they both
 * climb to just above it, and comparing the climbed ratios decides the tone on fourth-decimal noise
 * — so two backgrounds a viewer cannot tell apart get opposite glyphs. The base comparison is the
 * stable one.
 */
export function stripGlyphInk(background: string): StripGlyphTone {
   const lightBase = contrast("#000000", LIGHT_BASE_ALPHA, background);
   const darkBase = contrast("#FFFFFF", DARK_BASE_ALPHA, background);

   return lightBase >= darkBase
      ? { tone: "light", alpha: climb("#000000", LIGHT_BASE_ALPHA, background) }
      : { tone: "dark", alpha: climb("#FFFFFF", DARK_BASE_ALPHA, background) };
}

const toneMemo = new Map<string, StripGlyphTone>();

/** Present, parseable, and not fully transparent. */
function usable(color: string): boolean {
   if(!color) {
      return false;
   }

   const parsed = new TinyColor(color);
   return parsed.isValid && parsed.getAlpha() > 0;
}

/**
 * What the strip actually sits on. The anchored strip lives in the title lane, so the lane is the
 * topmost layer, composited over the card behind it; the lane is unfilled on some assemblies and
 * is due to become unfilled on all of them, at which point the card shows through unchanged. The
 * base of the stack follows the dark flag rather than assuming white, so a cleared card in a dark
 * org does not get dark ink on a dark canvas.
 *
 * Every layer is composited onto that base rather than returned on its own, because the author's
 * Format-pane Alpha field can leave either layer translucent — the model only ever carries
 * "rgba(r,g,b,alpha)" strings (VSCSSUtil.getBackgroundRGBA) — and TinyColor's contrast maths reads
 * a colour's own alpha as opaque. Compositing first turns every layer into the opaque colour a
 * viewer actually sees. A layer at full alpha is passed through as the string it arrived in
 * instead of being re-emitted by TinyColor, so an already-opaque case resolves bit for bit as
 * before rather than picking up TinyColor's lowercase hex formatting.
 */
export function resolveStripBackground(model: VSObjectModel): string {
   const titled = <any> model;
   const layers = [titled?.titleFormat?.background, titled?.objectFormat?.background]
      .filter(usable);
   let ground = titled?.vizDark ? CANVAS_DARK : CANVAS_LIGHT;

   for(let i = layers.length - 1; i >= 0; i--) {
      const layer = new TinyColor(layers[i]);
      ground = layer.getAlpha() >= 1 ? layers[i] : layer.onBackground(ground).toHexString();
   }

   return ground;
}

/**
 * The glyph treatment for an assembly's strip, memoized on the colours it derives from so the
 * alpha climb runs once per distinct background rather than once per change-detection pass. The
 * same object identity comes back for a repeated background, which keeps the template binding
 * stable.
 */
export function stripGlyphTone(model: VSObjectModel): StripGlyphTone {
   const titled = <any> model;
   const key = [titled?.titleFormat?.background, titled?.objectFormat?.background,
                titled?.vizDark].join("|");
   let tone = toneMemo.get(key);

   if(!tone) {
      tone = stripGlyphInk(resolveStripBackground(model));

      if(toneMemo.size >= MEMO_LIMIT) {
         toneMemo.clear();
      }

      toneMemo.set(key, tone);
   }

   return tone;
}
