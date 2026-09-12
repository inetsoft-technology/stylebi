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

/**
 * CalcTableCellComponent — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] — cell setter / createCellWords: text split on whitespace for rendering
 *   Group 2 [Risk 2] — dragStarted: CalcTableTransfer payload on drag start
 *   Group 3 [Risk 3] — dragOver / dragLeave / dropOnCell: DnD accept/reject and drop target wiring
 *   Group 4 [Risk 2] — getDivStyle: drag-over border and span-cell border trimming
 *   Group 5 [Risk 1] — resizeCell: emit resize on primary mouse button only
 *
 * HTTP: no HTTP — calc table editor local DnD only
 *
 * Out of scope:
 *   getClientRect / getResizeColStyle / getResizeRowStyle — require parentElement layout (jsdom)
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { fireEvent, render, screen } from "@testing-library/angular";
import { Tool } from "../../../../../shared/util/tool";
import { DataRefType } from "../../common/data/data-ref-type";
import { CalcDropTarget, CalcTableTransfer } from "../../common/data/dnd-transfer";
import { TableCell } from "../../common/data/tablelayout/table-cell";
import { DndService } from "../../common/dnd/dnd.service";
import { TestUtils } from "../../common/test/test-utils";
import { VSCalcTableEditorService } from "../../binding/services/table/vs-calc-table-editor.service";
import { CalcTableCellComponent } from "./calc-table-cell.component";

const editorServiceMock = {
   getSelectCells: vi.fn(() => []),
   getTableLayout: vi.fn(() => ({ selectedRect: null })),
   setSelectCells: vi.fn(),
};

const dndServiceMock = {
   setDragStartStyle: vi.fn(),
   getTransfer: vi.fn(() => ({})),
   processOnDrop: vi.fn(),
};

function createTableCell(overrides: Partial<TableCell> = {}): TableCell {
   return Object.assign({
      row: 0,
      col: 0,
      text: "hello world",
      vsFormat: TestUtils.createMockVSFormatModel(),
      span: null,
      baseInfo: null,
   }, overrides) as TableCell;
}

async function renderCell(props: Record<string, unknown> = {}) {
   const cell = (props.cell as TableCell) ?? createTableCell();
   return render(CalcTableCellComponent, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: VSCalcTableEditorService, useValue: editorServiceMock },
         { provide: DndService, useValue: dndServiceMock },
      ],
      componentProperties: {
         assemblyName: "CalcTable1",
         columnWidth: 100,
         rowHeight: 24,
         rowIndex: 0,
         columnIndex: 0,
         cell,
         ...props,
      },
   });
}

function getCellDiv(container: HTMLElement): HTMLElement {
   return container.querySelector("[draggable='true']") as HTMLElement;
}

const acceptableTransfer = { dragSource: { classType: "TableTransfer" } };

afterEach(() => vi.restoreAllMocks());

describe("CalcTableCellComponent — single pass", () => {

   describe("Group 1 — cell setter / createCellWords [Risk 2]", () => {
      it("should split cell text into words when cell is assigned", async () => {
         const cell = createTableCell({ text: "hello  world" });
         await renderCell({ cell });

         expect(screen.getByText("hello")).toBeInTheDocument();
         expect(screen.getByText(/world/)).toBeInTheDocument();
      });

      it("should render no text spans for blank text", async () => {
         const cell = createTableCell({ text: "" });
         const { container } = await renderCell({ cell });

         expect(getCellDiv(container).querySelectorAll("span")).toHaveLength(0);
      });
   });

   describe("Group 2 — dragStarted [Risk 2]", () => {
      it("should set CalcTableTransfer on drag start", async () => {
         const cell = createTableCell({ text: "Revenue" });
         const { container } = await renderCell({ cell });
         const setTransferSpy = vi.spyOn(Tool, "setTransferData").mockImplementation(() => {});

         fireEvent.dragStart(getCellDiv(container), { dataTransfer: {} });

         expect(setTransferSpy).toHaveBeenCalled();
         const transfer = setTransferSpy.mock.calls[0][1].dragSource;
         expect(transfer).toBeInstanceOf(CalcTableTransfer);
         expect(dndServiceMock.setDragStartStyle).toHaveBeenCalledWith(expect.anything(), "Revenue");
      });
   });

   describe("Group 3 — dragOver / dragLeave / dropOnCell [Risk 3]", () => {
      it("should set isDragOver and emit when transfer is acceptable", async () => {
         dndServiceMock.getTransfer.mockReturnValue(acceptableTransfer);
         const cell = createTableCell();
         const { fixture, container } = await renderCell({ cell });
         const emitSpy = vi.spyOn(fixture.componentInstance.dragOverChange, "emit");

         fireEvent.dragOver(getCellDiv(container));

         expect(getCellDiv(container).style.borderTop).toBe("2px solid rgb(102, 221, 102)");
         expect(emitSpy).toHaveBeenCalledWith(cell);
      });

      it("should reject cube column transfer", async () => {
         dndServiceMock.getTransfer.mockReturnValue({
            column: [{ properties: { refType: DataRefType.CUBE + "" } }],
         });
         const cell = createTableCell();
         const { container } = await renderCell({ cell });

         fireEvent.dragOver(getCellDiv(container));

         expect(getCellDiv(container).style.borderTop).toBe("2px solid transparent");
      });

      it("should clear drag-over on dragLeave", async () => {
         const cell = createTableCell({ isDragOver: true });
         const { fixture, container } = await renderCell({ cell });
         const emitSpy = vi.spyOn(fixture.componentInstance.dragOverChange, "emit");

         fireEvent.dragLeave(getCellDiv(container));

         expect(getCellDiv(container).style.borderTop).toBe("2px solid transparent");
         expect(emitSpy).toHaveBeenCalled();
      });

      it("should process drop when transfer is acceptable", async () => {
         dndServiceMock.getTransfer.mockReturnValue(acceptableTransfer);
         const cell = createTableCell({ isDragOver: true });
         const { container } = await renderCell({ cell });

         fireEvent.drop(getCellDiv(container), { dataTransfer: {} });

         expect(dndServiceMock.processOnDrop).toHaveBeenCalled();
         const dropTarget = dndServiceMock.processOnDrop.mock.calls[0][1];
         expect(dropTarget).toBeInstanceOf(CalcDropTarget);
         expect(editorServiceMock.setSelectCells).toHaveBeenCalled();
      });
   });

   describe("Group 4 — getDivStyle [Risk 2]", () => {
      it("should show drag-over border when cell is drag target", async () => {
         const cell = createTableCell({ isDragOver: true });
         const { container } = await renderCell({ cell });

         expect(getCellDiv(container).style.borderTop).toBe("2px solid rgb(102, 221, 102)");
      });

      it("should trim inner borders for span cells", async () => {
         const spanCell = createTableCell({ row: 0, col: 0, span: { width: 2, height: 2 } as any });
         const innerCell = createTableCell({ row: 0, col: 1, span: null });
         const { container } = await renderCell({ cell: innerCell, spanCell });

         expect(getCellDiv(container).style.borderLeftStyle).toBe("none");
      });
   });

   describe("Group 5 — resizeCell [Risk 1]", () => {
      it("should emit cellResize on primary mouse button", async () => {
         const { fixture, container } = await renderCell();
         const emitSpy = vi.spyOn(fixture.componentInstance.cellResize, "emit");

         const zone = container.querySelector(".resize-col-zone") as HTMLElement;
         const event = new MouseEvent("mousedown", { button: 0, bubbles: true });
         Object.defineProperty(event, "pageX", { value: 10 });
         Object.defineProperty(event, "pageY", { value: 20 });
         zone.dispatchEvent(event);

         expect(emitSpy).toHaveBeenCalledWith({ x: 10, y: 20, op: "ResizeColumn" });
      });

      it("should ignore non-primary mouse button", async () => {
         const { fixture, container } = await renderCell();
         const emitSpy = vi.spyOn(fixture.componentInstance.cellResize, "emit");

         fireEvent.mouseDown(container.querySelector(".resize-col-zone")!, { button: 2 });

         expect(emitSpy).not.toHaveBeenCalled();
      });
   });
});
