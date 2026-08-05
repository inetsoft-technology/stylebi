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
import { Rectangular } from "../../common/data/rectangle";

export type TailSide = "top" | "bottom" | "left" | "right";
export type TailAxis = "vertical" | "horizontal";

/** Corner radius of the tooltip box, matching the card skin. */
export const TAIL_RADIUS = 8;
/** How far the tail projects past the box edge. */
export const TAIL_LENGTH = 8;
/** Half the width of the tail's opening on the box edge. */
export const TAIL_HALF_WIDTH = 6.7;
/** Anchor-to-box gap on the horizontal axis. */
export const TAIL_GAP = 8;
/** The inner box's margin; the host is this much larger on every side. */
export const TOOLTIP_INSET = 3;

export interface TailPlacement {
   /** Host left, viewport coordinates. */
   x: number;
   /** Host top, viewport coordinates. */
   y: number;
   tailSide: TailSide;
   /** Distance from the box's leading edge to the tail's centre. */
   tailOffset: number;
}

export interface TailPlacementInput {
   anchor: { x: number, y: number };
   hostWidth: number;
   hostHeight: number;
   container: Rectangular;
   axis: TailAxis;
}

interface Bounds {
   left: number;
   right: number;
   top: number;
   bottom: number;
}

export function computeTailPlacement(input: TailPlacementInput): TailPlacement | null {
   const bw = input.hostWidth - 2 * TOOLTIP_INSET;
   const bh = input.hostHeight - 2 * TOOLTIP_INSET;
   const bounds: Bounds = {
      left: input.container.x + TOOLTIP_INSET,
      right: input.container.x + input.container.width - TOOLTIP_INSET,
      top: input.container.y + TOOLTIP_INSET,
      bottom: input.container.y + input.container.height - TOOLTIP_INSET
   };

   if(bw <= 0 || bh <= 0 || bounds.right - bounds.left < bw || bounds.bottom - bounds.top < bh) {
      return null;
   }

   return input.axis === "horizontal"
      ? placeHorizontal(input.anchor, bw, bh, bounds)
      : placeVertical(input.anchor, bw, bh, bounds);
}

function placeVertical(anchor: { x: number, y: number }, bw: number, bh: number,
                       bounds: Bounds): TailPlacement | null
{
   const needed = bh + TAIL_LENGTH;
   const topRoom = anchor.y - bounds.top;
   const bottomRoom = bounds.bottom - anchor.y;
   const below = topRoom < needed && (bottomRoom >= needed || bottomRoom > topRoom);
   // Shift sideways when below so the cursor is less likely to cover the text.
   const belowOffset = Math.min(Math.max(24, bw * 0.18), 56);
   const bias = !below ? 0
      : (bounds.right - anchor.x >= anchor.x - bounds.left ? belowOffset : -belowOffset);
   const bx = clamp(anchor.x + bias - bw / 2, bounds.left, bounds.right - bw);
   const by = clamp(below ? anchor.y + TAIL_LENGTH : anchor.y - TAIL_LENGTH - bh,
      bounds.top, bounds.bottom - bh);

   if(engulfs(anchor.y, by, bh)) {
      return null;
   }

   return {
      x: bx - TOOLTIP_INSET,
      y: by - TOOLTIP_INSET,
      tailSide: below ? "top" : "bottom",
      tailOffset: clampToEdge(anchor.x - bx, bw)
   };
}

function placeHorizontal(anchor: { x: number, y: number }, bw: number, bh: number,
                         bounds: Bounds): TailPlacement | null
{
   const right = bounds.right - anchor.x >= anchor.x - bounds.left;
   const bx = clamp(right ? anchor.x + TAIL_GAP : anchor.x - TAIL_GAP - bw,
      bounds.left, bounds.right - bw);

   if(engulfs(anchor.x, bx, bw)) {
      return null;
   }

   const by = clamp(anchor.y - bh / 2, bounds.top, bounds.bottom - bh);

   return {
      x: bx - TOOLTIP_INSET,
      y: by - TOOLTIP_INSET,
      tailSide: right ? "left" : "right",
      tailOffset: clampToEdge(anchor.y - by, bh)
   };
}

function engulfs(anchor: number, start: number, size: number): boolean {
   return anchor > start && anchor < start + size;
}

function clamp(value: number, lo: number, hi: number): number {
   return Math.min(hi, Math.max(lo, value));
}

/** Keep the tail on the straight part of the edge; centre it if the edge is too short. */
function clampToEdge(offset: number, edge: number): number {
   const lo = TAIL_RADIUS + TAIL_HALF_WIDTH;
   const hi = edge - TAIL_RADIUS - TAIL_HALF_WIDTH;
   return hi < lo ? (lo + hi) / 2 : clamp(offset, lo, hi);
}

export interface ChromeGeometry {
   /** SVG viewport size, TAIL_LENGTH larger than the box on every side. */
   width: number;
   height: number;
   boxX: number;
   boxY: number;
   boxWidth: number;
   boxHeight: number;
   radius: number;
   /** Rounded rect, open across the tail's base. */
   borderPath: string;
   /** Triangle closing that opening. */
   tailPath: string;
}

export function buildChromePaths(boxWidth: number, boxHeight: number, tailSide: TailSide,
                                 tailOffset: number): ChromeGeometry
{
   const r = TAIL_RADIUS;
   const tw = TAIL_HALF_WIDTH;
   const l = TAIL_LENGTH;
   const bL = l, bT = l, bR = l + boxWidth, bB = l + boxHeight;
   let borderPath: string;
   let tailPath: string;

   if(tailSide === "top" || tailSide === "bottom") {
      const tx = bL + tailOffset;

      if(tailSide === "top") {
         borderPath =
            `M${n(tx - tw)},${n(bT)} L${n(bL + r)},${n(bT)} Q${n(bL)},${n(bT)} ${n(bL)},${n(bT + r)}` +
            ` L${n(bL)},${n(bB - r)} Q${n(bL)},${n(bB)} ${n(bL + r)},${n(bB)}` +
            ` L${n(bR - r)},${n(bB)} Q${n(bR)},${n(bB)} ${n(bR)},${n(bB - r)}` +
            ` L${n(bR)},${n(bT + r)} Q${n(bR)},${n(bT)} ${n(bR - r)},${n(bT)} L${n(tx + tw)},${n(bT)}`;
         tailPath = `M${n(tx + tw)},${n(bT)} L${n(tx)},${n(bT - l)} L${n(tx - tw)},${n(bT)}`;
      }
      else {
         borderPath =
            `M${n(tx + tw)},${n(bB)} L${n(bR - r)},${n(bB)} Q${n(bR)},${n(bB)} ${n(bR)},${n(bB - r)}` +
            ` L${n(bR)},${n(bT + r)} Q${n(bR)},${n(bT)} ${n(bR - r)},${n(bT)}` +
            ` L${n(bL + r)},${n(bT)} Q${n(bL)},${n(bT)} ${n(bL)},${n(bT + r)}` +
            ` L${n(bL)},${n(bB - r)} Q${n(bL)},${n(bB)} ${n(bL + r)},${n(bB)} L${n(tx - tw)},${n(bB)}`;
         tailPath = `M${n(tx - tw)},${n(bB)} L${n(tx)},${n(bB + l)} L${n(tx + tw)},${n(bB)}`;
      }
   }
   else {
      const ty = bT + tailOffset;

      if(tailSide === "left") {
         borderPath =
            `M${n(bL)},${n(ty - tw)} L${n(bL)},${n(bT + r)} Q${n(bL)},${n(bT)} ${n(bL + r)},${n(bT)}` +
            ` L${n(bR - r)},${n(bT)} Q${n(bR)},${n(bT)} ${n(bR)},${n(bT + r)}` +
            ` L${n(bR)},${n(bB - r)} Q${n(bR)},${n(bB)} ${n(bR - r)},${n(bB)}` +
            ` L${n(bL + r)},${n(bB)} Q${n(bL)},${n(bB)} ${n(bL)},${n(bB - r)} L${n(bL)},${n(ty + tw)}`;
         tailPath = `M${n(bL)},${n(ty + tw)} L${n(bL - l)},${n(ty)} L${n(bL)},${n(ty - tw)}`;
      }
      else {
         borderPath =
            `M${n(bR)},${n(ty + tw)} L${n(bR)},${n(bB - r)} Q${n(bR)},${n(bB)} ${n(bR - r)},${n(bB)}` +
            ` L${n(bL + r)},${n(bB)} Q${n(bL)},${n(bB)} ${n(bL)},${n(bB - r)}` +
            ` L${n(bL)},${n(bT + r)} Q${n(bL)},${n(bT)} ${n(bL + r)},${n(bT)}` +
            ` L${n(bR - r)},${n(bT)} Q${n(bR)},${n(bT)} ${n(bR)},${n(bT + r)} L${n(bR)},${n(ty - tw)}`;
         tailPath = `M${n(bR)},${n(ty - tw)} L${n(bR + l)},${n(ty)} L${n(bR)},${n(ty + tw)}`;
      }
   }

   return {
      width: boxWidth + 2 * l,
      height: boxHeight + 2 * l,
      boxX: bL,
      boxY: bT,
      boxWidth,
      boxHeight,
      radius: r,
      borderPath,
      tailPath
   };
}

function n(value: number): number {
   return Math.round(value * 100) / 100;
}
