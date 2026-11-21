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

/** A shape in a texture tile, in tile units (1 unit is 1 pixel in a chart). */
export type TextureShape =
   { type: "rect", x: number, y: number, w: number, h: number } |
   { type: "dot", cx: number, cy: number, r: number } |
   { type: "ring", cx: number, cy: number, r: number } |
   { type: "outline", x: number, y: number, w: number, h: number } |
   { type: "poly", points: string };

/** One repeating tile of a texture pattern. */
export interface TexturePatternTile {
   w: number;
   h: number;
   shapes: TextureShape[];
}

const rect = (x: number, y: number, w: number, h: number): TextureShape =>
   ({ type: "rect", x, y, w, h });
const dot = (cx: number, cy: number, r: number): TextureShape => ({ type: "dot", cx, cy, r });

/**
 * The built-in texture patterns, in the order of the pattern numbers 0 to 19 (the solid fill,
 * -1, is drawn by the texture item itself). They are tiles of upright lines, dots and symmetric
 * shapes, with no diagonal lines, so a textured bar still reads as upright. The server draws the
 * same designs in charts; keep these in step with TexturePattern.java.
 */
export const TEXTURE_PATTERNS: TexturePatternTile[] = [
   // H_LINES_FINE
   { w: 5, h: 5, shapes: [rect(0, 0, 5, 1)] },
   // DOTS_FINE
   { w: 5, h: 5, shapes: [dot(2.5, 2.5, 1.1)] },
   // V_LINES_FINE
   { w: 5, h: 5, shapes: [rect(0, 0, 1, 5)] },
   // CHECKER_SMALL
   { w: 8, h: 8, shapes: [rect(0, 0, 4, 4), rect(4, 4, 4, 4)] },
   // GRID_FINE
   { w: 6, h: 6, shapes: [rect(0, 0, 6, 1), rect(0, 0, 1, 6)] },
   // H_LINES_BOLD
   { w: 8, h: 8, shapes: [rect(0, 0, 8, 3)] },
   // RINGS
   { w: 10, h: 10, shapes: [{ type: "ring", cx: 5, cy: 5, r: 2.6 }] },
   // V_LINES_BOLD
   { w: 8, h: 8, shapes: [rect(0, 0, 3, 8)] },
   // PLUS_MARKS
   { w: 10, h: 10, shapes: [rect(2, 4.4, 6, 1.2), rect(4.4, 2, 1.2, 6)] },
   // BRICK
   {
      w: 12, h: 8,
      shapes: [rect(0, 0, 12, 1), rect(0, 4, 12, 1), rect(0, 0, 1, 4), rect(6, 4, 1, 4)]
   },
   // DOTS_LARGE
   { w: 10, h: 10, shapes: [dot(5, 5, 2.4)] },
   // SQUARES_OPEN
   { w: 10, h: 10, shapes: [{ type: "outline", x: 1.6, y: 1.6, w: 6.8, h: 6.8 }] },
   // CHECKER_LARGE
   { w: 16, h: 16, shapes: [rect(0, 0, 8, 8), rect(8, 8, 8, 8)] },
   // TRIANGLES
   { w: 12, h: 12, shapes: [{ type: "poly", points: "6,2 10,9 2,9" }] },
   // GRID_BOLD
   { w: 12, h: 12, shapes: [rect(0, 0, 12, 2), rect(0, 0, 2, 12)] },
   // LADDER
   { w: 10, h: 8, shapes: [rect(0, 0, 1, 8), rect(5, 0, 1, 8), rect(0, 3, 6, 1)] },
   // DIAMONDS
   { w: 10, h: 10, shapes: [{ type: "poly", points: "5,1.6 8.4,5 5,8.4 1.6,5" }] },
   // H_THICK_THIN
   { w: 10, h: 10, shapes: [rect(0, 0, 10, 3), rect(0, 5, 10, 1)] },
   // V_THICK_THIN
   { w: 10, h: 10, shapes: [rect(0, 0, 3, 10), rect(5, 0, 1, 10)] },
   // HALFTONE
   { w: 12, h: 12, shapes: [dot(3, 3, 2.2), dot(9, 9, 1), dot(9, 3, 1), dot(3, 9, 1)] }
];
