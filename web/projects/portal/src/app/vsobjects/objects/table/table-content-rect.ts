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
export interface TablePadding {
   top: number;
   left: number;
   bottom: number;
   right: number;
}

/**
 * The width a table's grid draws into: its card width minus the horizontal inset. The card rect
 * itself stays with the border, the background and the round-corner clip, which draw at the
 * assembly edge — the same split the chart has had since its own card inset shipped.
 *
 * Per axis rather than per rect, so that asking for a width never computes a height: the card
 * height is derived from live layout state, and a query for it at the wrong moment in the layout
 * pass reads a stale value.
 *
 * Clamped at zero: an assembly can be dragged smaller than its own inset, and a negative width
 * reaches the DOM as an invalid style that silently drops the binding.
 */
export function contentWidth(cardWidth: number, padding: TablePadding | null): number {
   return padding ? Math.max(0, cardWidth - padding.left - padding.right) : cardWidth;
}

/**
 * The height a table's grid draws into: its card height minus the vertical inset. Per axis and
 * clamped at zero for the same reasons as the width.
 */
export function contentHeight(cardHeight: number, padding: TablePadding | null): number {
   return padding ? Math.max(0, cardHeight - padding.top - padding.bottom) : cardHeight;
}
