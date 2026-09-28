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

import { render, screen } from "@testing-library/angular";
import { TableDataPathTypes } from "../../../common/data/table-data-path-types";
import { BaseTableCellModel } from "../../model/base-table-cell-model";
import { VSSimpleCell } from "./vs-simple-cell.component";

afterEach(() => vi.restoreAllMocks());

// copied verbatim from vs-table-cell.component.tl.spec.ts:53 - keep the two in step
function makeCell(overrides: Partial<BaseTableCellModel> = {}): BaseTableCellModel {
   return {
      cellData: "value",
      cellLabel: "value",
      row: 0,
      col: 0,
      hyperlinks: [],
      underline: false,
      drillOp: null,
      period: false,
      grouped: false,
      isImage: false,
      presenter: null,
      editable: false,
      editorType: null,
      options: [],
      dataPath: {
         level: 0,
         col: false,
         row: false,
         type: TableDataPathTypes.DETAIL,
         dataType: "String",
         path: [],
         index: 0,
         colIndex: 0,
      } as any,
      vsFormatModel: {
         foreground: "#000000",
         background: "#ffffff",
         font: "12px Arial",
         decoration: "",
         alpha: 1,
         hAlign: "",
         vAlign: "",
         justifyContent: "flex-start",
         alignItems: "stretch",
         border: { top: "", bottom: "", left: "", right: "" },
         wrapping: { whiteSpace: "", wordWrap: "", overflow: "" },
         top: 0,
         left: 0,
         zIndex: 1,
         bringToFrontEnabled: true,
         sendToBackEnabled: true,
         position: ""
      } as any,
      ...overrides,
   } as BaseTableCellModel;
}

async function renderCell(padding: object | null) {
   const cell = makeCell({ vsFormatModel: { ...makeCell().vsFormatModel, padding } as any });
   const { container } = await render(VSSimpleCell, {
      componentInputs: { cell, width: 100, height: 20 },
   });
   return container.querySelector(".simple-cell-container") as HTMLElement;
}

describe("VSSimpleCell padding", () => {
   it("should apply the cell padding from the format model to each edge", async () => {
      const cellEl = await renderCell({ top: 4, left: 6, bottom: 4, right: 6 });

      expect(cellEl.style.paddingTop).toBe("4px");
      expect(cellEl.style.paddingLeft).toBe("6px");
      expect(cellEl.style.paddingBottom).toBe("4px");
      expect(cellEl.style.paddingRight).toBe("6px");
   });

   it("should leave the stylesheet default in place when no padding is defined", async () => {
      const cellEl = await renderCell(null);

      expect(cellEl.style.paddingTop).toBe("");
      expect(cellEl.style.paddingLeft).toBe("");
      expect(cellEl.style.paddingBottom).toBe("");
      expect(cellEl.style.paddingRight).toBe("");
   });

   it("should still render the label", async () => {
      await renderCell({ top: 4, left: 6, bottom: 4, right: 6 });

      expect(screen.getByText("value")).toBeInTheDocument();
   });
});
