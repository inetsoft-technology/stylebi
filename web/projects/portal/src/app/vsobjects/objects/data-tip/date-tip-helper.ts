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
import { GuiTool } from "../../../common/util/gui-tool";

// Mirrors $stacking-order in scss/internal/_directives.scss; these layers are assigned per
// object at runtime rather than from a class, so keep the two in step. Only the wrapper's pop
// content is clamped to the ceiling — the pop directive's own offsets (popZIndex + 99998
// / + 99999) and the data tip's (+ 99999, +1000 while showing) still assign directly.
//
// Effective order, lowest first:
//   scrim 9996 < source 9997 < content (99999 + natural, capped) < .fixed-dropdown 999900
//   < w-tooltip 999901 < data tip while showing (+1000 above its own offset)
const POP_UP_BACKGROUND_ZINDEX = 9996;
const POP_DIM_COLOR: string = "rgba(0, 0, 0, 0.2)";
// The system ships --inet-overlay-scrim-bg-color at rgba(0,0,0,0.3); this layer had its own 0.2
// for the same job. The scrim is painted into a canvas via fillStyle, so the token cannot be
// referenced — the value is mirrored here and gated in TS. Keep both in step.
const POP_DIM_COLOR_MODERN: string = "rgba(0, 0, 0, 0.3)";
// Added to an object's natural z-index to lift pop content above the scrim and the source.
const POP_UP_CONTENT_BOOST_ZINDEX = 99999;
// Ceiling for the boosted result — the .pop-component-content layer, one below
// .fixed-dropdown. Without it a large enough natural z-index would carry pop content over
// the dropdown layer, and a dropdown opened inside a data tip must stay above it.
const POP_UP_CONTENT_MAX_ZINDEX = 999899;

export class DateTipHelper {
   public static get popDimColor() {
      return GuiTool.isVizModern() ? POP_DIM_COLOR_MODERN : POP_DIM_COLOR;
   }

   public static getPopUpBackgroundZIndex(): number {
      return POP_UP_BACKGROUND_ZINDEX;
   }

   public static getPopUpSourceZIndex(): number {
      return POP_UP_BACKGROUND_ZINDEX + 1;
   }

   public static getPopUpContentBoostZIndex(): number {
      return POP_UP_CONTENT_BOOST_ZINDEX;
   }

   // naturalZIndex is optional because objectFormat.zIndex can be absent; || 0 also maps a
   // NaN sum to the base layer rather than letting the browser drop the style.
   public static getPopUpContentZIndex(naturalZIndex?: number): number {
      return Math.min(POP_UP_CONTENT_MAX_ZINDEX,
                      POP_UP_CONTENT_BOOST_ZINDEX + (naturalZIndex || 0));
   }
}

