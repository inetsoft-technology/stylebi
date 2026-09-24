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
import { contentRect } from "./table-content-rect";

describe("contentRect", () => {
   const card = { width: 400, height: 300 };
   const inset = { top: 12, left: 12, bottom: 12, right: 12 };

   it("should inset the card by the padding on all four edges", () => {
      expect(contentRect(card, inset)).toEqual({ width: 376, height: 276 });
   });

   it("should take each edge independently", () => {
      expect(contentRect(card, { top: 1, left: 2, bottom: 4, right: 8 }))
         .toEqual({ width: 390, height: 295 });
   });

   it("should return the card unchanged when there is no padding", () => {
      expect(contentRect(card, null)).toEqual({ width: 400, height: 300 });
   });

   it("should return the card unchanged for a zero padding", () => {
      expect(contentRect(card, { top: 0, left: 0, bottom: 0, right: 0 }))
         .toEqual({ width: 400, height: 300 });
   });

   it("should clamp at zero rather than going negative on a tiny assembly", () => {
      expect(contentRect({ width: 10, height: 10 }, inset)).toEqual({ width: 0, height: 0 });
   });

   it("should not mutate its arguments", () => {
      contentRect(card, inset);

      expect(card).toEqual({ width: 400, height: 300 });
      expect(inset).toEqual({ top: 12, left: 12, bottom: 12, right: 12 });
   });
});
