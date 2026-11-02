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
import { GuiTool } from "../../common/util/gui-tool";

export type TailSide = "top" | "bottom" | "left" | "right";
export type TailAxis = "vertical" | "horizontal";

// The tailed card's SVG chrome draws the outline in place of the box's own background, border
// and shadow (.widget__card-tooltip--tailed), so this is its one visible radius and must track
// .widget__card-tooltip's border-radius: 8px gate-off, 6px under .viz-modern (--inet-radius-xl).
// An SVG path needs a plain number, so the token cannot be referenced — keep the two in step.
// Deliberately unexported: reach these through tailRadius(), or the gate is bypassed.
const TAIL_RADIUS = 8;
const TAIL_RADIUS_MODERN = 6;

/** Reads the shell state at call time; the body class toggles at runtime. */
export function tailRadius(): number {
   return GuiTool.isVizShell() ? TAIL_RADIUS_MODERN : TAIL_RADIUS;
}

/** How far the tail projects past the box edge, and so the gap between the box and the mark. */
export const TAIL_LENGTH = 12;
/** Half the width of the tail's opening on the box edge. */
export const TAIL_HALF_WIDTH = 6.5;
/** The inner box's margin; the host is this much larger on every side. */
export const TOOLTIP_INSET = 3;
/** How far the tip reaches past the mark's edge, so it reads as touching rather than floating. */
const TAIL_OVERLAP = 1;
/** Tip to opening centre, in half-widths; past 1 the tip hooks beyond the opening. */
const TAIL_SKEW = 1.8;
/** Depth of the near leg's curve control, as a fraction of the tail length. */
const NEAR_LEG_BEND = 0.75;
/** Depth of the far leg's curve control, as a fraction of the tail length. */
const FAR_LEG_BEND = 0.67;
/** The far leg's curve control, in half-widths past the opening's centre away from the tip. */
const FAR_LEG_LEAN = 0.18;

/**
 * The mark a tail points at: its visual middle, plus where its outline crosses the vertical and
 * horizontal lines through that middle. The tip lands TAIL_OVERLAP inside the crossing that faces
 * the box; a missing crossing means the middle.
 */
export interface TailAnchor {
   x: number;
   y: number;
   top?: number;
   bottom?: number;
   left?: number;
   right?: number;
}

export interface TailPlacement {
   /** Host left, viewport coordinates. */
   x: number;
   /** Host top, viewport coordinates. */
   y: number;
   tailSide: TailSide;
   /** Distance from the box's leading edge to the tail's tip. */
   tailOffset: number;
}

export interface TailPlacementInput {
   anchor: TailAnchor;
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

   const place = (anchor: TailAnchor) => input.axis === "horizontal"
      ? placeHorizontal(anchor, bw, bh, bounds)
      : placeVertical(anchor, bw, bh, bounds);

   // A mark with no room past its edges, such as a bar reaching the top of the window, takes the
   // box over its middle instead.
   return place(input.anchor) ?? place({ x: input.anchor.x, y: input.anchor.y });
}

function placeVertical(anchor: TailAnchor, bw: number, bh: number,
                       bounds: Bounds): TailPlacement | null
{
   const top = intoMark(anchor.top, anchor.y);
   const bottom = intoMark(anchor.bottom, anchor.y);
   const needed = bh + TAIL_LENGTH;
   const topRoom = top - bounds.top;
   const bottomRoom = bounds.bottom - bottom;
   const below = topRoom < needed && (bottomRoom >= needed || bottomRoom > topRoom);
   const tipY = below ? bottom : top;
   // Shift sideways when below so the cursor is less likely to cover the text.
   const belowOffset = Math.min(Math.max(24, bw * 0.18), 56);
   const bias = !below ? 0
      : (bounds.right - anchor.x >= anchor.x - bounds.left ? belowOffset : -belowOffset);
   const bx = clamp(anchor.x + bias - bw / 2, bounds.left, bounds.right - bw);
   const by = clamp(below ? tipY + TAIL_LENGTH : tipY - TAIL_LENGTH - bh,
      bounds.top, bounds.bottom - bh);

   if(engulfs(tipY, by, bh)) {
      return null;
   }

   return {
      x: bx - TOOLTIP_INSET,
      y: by - TOOLTIP_INSET,
      tailSide: below ? "top" : "bottom",
      tailOffset: seatTail(anchor.x - bx, bw).tip
   };
}

function placeHorizontal(anchor: TailAnchor, bw: number, bh: number,
                         bounds: Bounds): TailPlacement | null
{
   const left = intoMark(anchor.left, anchor.x);
   const right = intoMark(anchor.right, anchor.x);
   const toRight = bounds.right - right >= left - bounds.left;
   const tipX = toRight ? right : left;
   const bx = clamp(toRight ? tipX + TAIL_LENGTH : tipX - TAIL_LENGTH - bw,
      bounds.left, bounds.right - bw);

   if(engulfs(tipX, bx, bw)) {
      return null;
   }

   const by = clamp(anchor.y - bh / 2, bounds.top, bounds.bottom - bh);

   return {
      x: bx - TOOLTIP_INSET,
      y: by - TOOLTIP_INSET,
      tailSide: toRight ? "left" : "right",
      tailOffset: seatTail(anchor.y - by, bh).tip
   };
}

/** An outline crossing moved TAIL_OVERLAP toward the middle, never past it; the middle if absent. */
function intoMark(edge: number | undefined, middle: number): number {
   if(edge == null) {
      return middle;
   }

   return edge < middle ? Math.min(edge + TAIL_OVERLAP, middle)
      : Math.max(edge - TAIL_OVERLAP, middle);
}

function engulfs(anchor: number, start: number, size: number): boolean {
   return anchor > start && anchor < start + size;
}

function clamp(value: number, lo: number, hi: number): number {
   return Math.min(hi, Math.max(lo, value));
}

/** Where a tail sits on one edge, as distances from the edge's leading end. */
interface TailSeat {
   tip: number;
   /** Centre of the tail's opening. */
   base: number;
   halfWidth: number;
   radius: number;
   /** -1 when the tip sweeps toward the edge's leading end, 1 toward its trailing end. */
   sweep: number;
}

/**
 * Seats the tail so its tip lands as near the wanted offset as the edge allows. The tip sweeps
 * toward the nearer corner, so the opening sits on its inward side, and the opening stays on the
 * straight part of the edge between the corner arcs. Seating a seated tip leaves it unchanged,
 * so the placement and the chrome agree on one offset.
 */
function seatTail(tip: number, edge: number): TailSeat {
   // An edge shorter than 2 * (radius + half-width) cannot hold the opening between the corner
   // arcs. Shrink the half-width, then the radius, so the opening always clears them. Both are
   // inert on every box the current padding can produce.
   const halfWidth = Math.min(TAIL_HALF_WIDTH, edge / 2);
   const radius = Math.max(0, Math.min(tailRadius(), edge / 2 - halfWidth));
   const skew = halfWidth * TAIL_SKEW;
   const sweep = tip <= edge / 2 ? -1 : 1;
   const base = clamp(tip - sweep * skew, radius + halfWidth, edge - radius - halfWidth);

   return { tip: base + sweep * skew, base, halfWidth, radius, sweep };
}

export interface ChromeGeometry {
   /** SVG viewport size, TAIL_LENGTH larger than the box on every side. */
   width: number;
   height: number;
   radius: number;
   /** The box and its tail as one closed outline, so no seam shows where they meet. */
   outlinePath: string;
}

type Point = [number, number];

/** Clockwise from the top-left corner, edge i runs from corner i to corner i + 1. */
const EDGE_INDEX: Record<TailSide, number> = { top: 0, right: 1, bottom: 2, left: 3 };

export function buildChromePaths(boxWidth: number, boxHeight: number, tailSide: TailSide,
                                 tailOffset: number): ChromeGeometry
{
   const l = TAIL_LENGTH;
   const vertical = tailSide === "top" || tailSide === "bottom";
   const seat = seatTail(tailOffset, vertical ? boxWidth : boxHeight);
   const r = seat.radius;
   const bL = l, bT = l, bR = l + boxWidth, bB = l + boxHeight;
   const corners: Point[] = [[bL, bT], [bR, bT], [bR, bB], [bL, bB]];
   const edge = EDGE_INDEX[tailSide];
   // u runs along the tailed edge from its leading end, v outward from the box.
   const at = (u: number, v: number): Point => {
      switch(tailSide) {
      case "top":
         return [bL + u, bT - v];
      case "bottom":
         return [bL + u, bB + v];
      case "left":
         return [bL - v, bT + u];
      default:
         return [bR + v, bT + u];
      }
   };
   const lead = seat.base - seat.halfWidth;
   const trail = seat.base + seat.halfWidth;
   // Clockwise runs along the top and right edges in u order, and against it on the other two.
   const forward = tailSide === "top" || tailSide === "right";
   const enter = forward ? lead : trail;
   const exit = forward ? trail : lead;
   // As on a speech bubble, the leg nearer the tip drops square off the edge before bending out
   // to it, and the far leg sweeps in to meet it.
   const nearControl = at(seat.sweep < 0 ? lead : trail, NEAR_LEG_BEND * l);
   const farControl = at(seat.base - seat.sweep * FAR_LEG_LEAN * seat.halfWidth, FAR_LEG_BEND * l);
   const enterIsNear = forward === seat.sweep < 0;
   let outlinePath = `M${p(at(exit, 0))}`;

   for(let i = 1; i <= 4; i++) {
      const corner = corners[(edge + i) % 4];
      outlinePath += ` L${p(toward(corner, corners[(edge + i + 3) % 4], r))}` +
         ` Q${p(corner)} ${p(toward(corner, corners[(edge + i + 1) % 4], r))}`;
   }

   outlinePath += ` L${p(at(enter, 0))}` +
      ` Q${p(enterIsNear ? nearControl : farControl)} ${p(at(seat.tip, l))}` +
      ` Q${p(enterIsNear ? farControl : nearControl)} ${p(at(exit, 0))} Z`;

   return {
      width: boxWidth + 2 * l,
      height: boxHeight + 2 * l,
      radius: r,
      outlinePath
   };
}

/** A corner moved d along its edge toward a neighbouring corner. */
function toward(from: Point, to: Point, d: number): Point {
   return [from[0] + Math.sign(to[0] - from[0]) * d, from[1] + Math.sign(to[1] - from[1]) * d];
}

function p(point: Point): string {
   return `${n(point[0])},${n(point[1])}`;
}

function n(value: number): number {
   return Math.round(value * 100) / 100;
}
