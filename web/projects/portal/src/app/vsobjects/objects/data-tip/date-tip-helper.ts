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
const POP_UP_BACKGROUND_ZINDEX = 9996;
const POP_DIM_COLOR: string = "rgba(0, 0, 0, 0.2)";
// Added to an object's natural z-index to place it above the dim canvas (POP_UP_BACKGROUND_ZINDEX).
// Must be large enough to exceed POP_UP_BACKGROUND_ZINDEX even from the highest natural z-index.
const POP_UP_CONTENT_BOOST_ZINDEX = 99999;

export class DateTipHelper {
   public static get popDimColor() {
      return POP_DIM_COLOR;
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
}

