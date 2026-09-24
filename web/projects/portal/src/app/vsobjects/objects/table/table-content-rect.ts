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
export interface CardRect {
   width: number;
   height: number;
}

export interface TablePadding {
   top: number;
   left: number;
   bottom: number;
   right: number;
}

/**
 * The rect a table's grid draws into: its card minus the card inset. The card rect itself stays
 * with the border, the background and the round-corner clip, which draw at the assembly edge —
 * the same split the chart has had since its own card inset shipped.
 *
 * Clamped at zero: an assembly can be dragged smaller than its own inset, and a negative width
 * reaches the DOM as an invalid style that silently drops the binding.
 */
export function contentRect(card: CardRect, padding: TablePadding | null): CardRect {
   if(!padding) {
      return { width: card.width, height: card.height };
   }

   return {
      width: Math.max(0, card.width - padding.left - padding.right),
      height: Math.max(0, card.height - padding.top - padding.bottom)
   };
}
