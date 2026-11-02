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

import { TestUtils } from "../../../common/test/test-utils";
import { TitleCell } from "./title-cell.component";

function cell(padding: any = undefined, defaultTextLeft: number = null): TitleCell {
   const comp = new TitleCell({ viewer: true } as any);
   comp.titleFormat = Object.assign(TestUtils.createMockVSFormatModel(), { padding });
   comp.defaultTextLeft = defaultTextLeft;
   return comp;
}

describe("TitleCell text padding", () => {
   it("leaves the stylesheet's padding without a default", () => {
      expect(cell().getTextPaddingLeft()).toBeNull();
   });

   it("takes the caller's default", () => {
      expect(cell(undefined, 22).getTextPaddingLeft()).toBe(22);
   });

   it("lets a css title padding win, even zero", () => {
      expect(cell({ top: 0, left: 0, bottom: 0, right: 0 }, 22).getTextPaddingLeft()).toBe(0);
   });

   it("moves the edit input with the default", () => {
      expect(cell(undefined, 22).getEditPaddingLeft()).toBe(22);
   });

   it("keeps a legacy edit input as it was", () => {
      expect(cell({ top: 0, left: 5, bottom: 0, right: 0 }).getEditPaddingLeft()).toBeNull();
   });
});
