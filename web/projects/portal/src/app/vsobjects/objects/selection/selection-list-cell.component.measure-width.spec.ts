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
import { SelectionListCell } from "./selection-list-cell.component";
import {
   createComp,
   makeListModel,
   makeSelectionValue
} from "./selection-list-cell.component.test-helpers";

/**
 * #79566: the measure-text box is border-box and carries `valuePadding` as padding-right
 * (selection-list-cell.component.html), so its content box is `_textWidth - valuePadding`.
 * setTextWidths must budget for that padding; otherwise the value is clipped on its left.
 *
 * Census geometry (live, Chromium): bar 40, "4.45M" measures 27.8px, measure ratio ~0.34.
 * Legacy list cell 171px, modernized (card inset 12px per side, 2 columns) 159px, 4-column 79.5px.
 * GuiTool.measureText needs a real canvas, so it is stubbed with the live Range width.
 */
describe("SelectionListCell measure-text box budget (#79566)", () => {
   const TEXT_WIDTH = 27.8;

   const measure = (cellWidth: number) => {
      const { comp } = createComp({
         model: makeListModel(),
         selectionValue: makeSelectionValue({ label: "Alabama", measureLabel: "4.45M" }),
         withInit: false
      });
      comp.cellWidth = cellWidth;
      comp.barWidth = 40;
      comp.textWidth = -1;
      comp.measureRatio = 0.34;
      comp.ngOnInit();
      return comp;
   };

   beforeEach(() => {
      vi.spyOn(GuiTool, "measureText").mockReturnValue(TEXT_WIDTH);
   });

   afterEach(() => vi.restoreAllMocks());

   it("control: legacy 171px cell leaves the value its full text width", () => {
      const comp = measure(171);

      expect(comp._textWidth - comp.valuePadding).toBeGreaterThanOrEqual(TEXT_WIDTH);
   });

   it("should keep the value content box >= measured text width in a modernized 159px cell", () => {
      const comp = measure(159);

      expect(comp._textWidth - comp.valuePadding).toBeGreaterThanOrEqual(TEXT_WIDTH);
   });

   it("should never give the label a negative width in a 4-column (79.5px) cell", () => {
      const comp = measure(79.5);

      expect(comp.labelWidth).toBeGreaterThanOrEqual(0);
   });
});
