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
 * TableDataEditor — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — isDropAccept: details zone rejects calc aggregates
 *   Group 2 [Risk 3] — isDropAccept: binding-tree dimension vs measure routing
 *   Group 3 [Risk 2] — isDropAccept: field reorder within same axis type
 *   Group 4 [Risk 2] — isDropAccept: crosstab cross-type acceptance rules
 *   Group 5 [Risk 3] — onDrop: embedded table calc / non-embedded column guards
 *
 * HTTP: no HTTP — table binding editor DnD only
 *
 * Out of scope:
 *   getColumnValue — private helper not invoked from this component
 *   dragOverField / dragLeave — inherited from DataEditor without override
 */

import { ChangeDetectorRef } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import { DataRefType } from "../../../common/data/data-ref-type";
import { TableTransfer } from "../../../common/data/dnd-transfer";
import { UIContextService } from "../../../common/services/ui-context.service";
import { ComponentTool } from "../../../common/util/component-tool";
import { TestUtils } from "../../../common/test/test-utils";
import { BindingService } from "../../services/binding.service";
import { DndService } from "../../../common/dnd/dnd.service";
import { TableDataEditor } from "./table-data-editor.component";

function createMockAssetEntry(dimension: boolean): AssetEntry {
   return {
      properties: {
         refType: dimension ? "1" : "0",
         "cube.column.type": dimension ? "0" : "1",
      },
   } as unknown as AssetEntry;
}

function createEditor(config: {
   fieldType?: string;
   objectType?: string;
   bindingModel?: any;
} = {}) {
   const dndMock = {
      getTransfer: vi.fn(),
      isCalcAggregate: vi.fn().mockReturnValue(false),
      processOnDrop: vi.fn(),
      setDragOverStyle: vi.fn(),
      containsCalc: vi.fn().mockReturnValue(false),
      isAllEmbeddedColumn: vi.fn().mockReturnValue(true),
   };
   const bindingModel = config.bindingModel ?? TestUtils.createMockTableBindingModel();
   const bindingMock = {
      assemblyName: "Table1",
      objectType: config.objectType ?? "vstable",
      bindingModel,
   } as BindingService;
   const changeRefMock = { detectChanges: vi.fn() } as unknown as ChangeDetectorRef;
   const comp = new TableDataEditor(
      dndMock as unknown as DndService,
      bindingMock,
      changeRefMock,
      {} as UIContextService,
      {} as NgbModal,
   );
   comp.fieldType = config.fieldType ?? "rows";
   comp.bindingModel = bindingModel;
   return { comp, dndMock, bindingMock };
}

function isDropAccept(comp: TableDataEditor): boolean {
   return (comp as any).isDropAccept();
}

afterEach(() => vi.restoreAllMocks());

describe("TableDataEditor — single pass", () => {

   describe("Group 1 — isDropAccept details zone [Risk 3]", () => {
      it("should reject calc aggregates dropped on details", () => {
         const { comp, dndMock } = createEditor({ fieldType: "details" });
         dndMock.getTransfer.mockReturnValue({});
         dndMock.isCalcAggregate.mockReturnValue(true);

         expect(isDropAccept(comp)).toBe(false);
      });

      it("should accept non-calc drops on details", () => {
         const { comp, dndMock } = createEditor({ fieldType: "details" });
         dndMock.getTransfer.mockReturnValue(null);

         expect(isDropAccept(comp)).toBe(true);
      });
   });

   describe("Group 2 — isDropAccept binding tree [Risk 3]", () => {
      it("should accept dimension columns on row and col zones", () => {
         const { comp, dndMock } = createEditor({ fieldType: "rows" });
         dndMock.getTransfer.mockReturnValue({
            column: [createMockAssetEntry(true)],
            tableName: "Orders",
         });

         expect(isDropAccept(comp)).toBe(true);
      });

      it("should accept measure columns on aggregate zone", () => {
         const { comp, dndMock } = createEditor({ fieldType: "aggregates" });
         dndMock.getTransfer.mockReturnValue({
            column: [createMockAssetEntry(false)],
            tableName: "Orders",
         });

         expect(isDropAccept(comp)).toBe(true);
      });

      it("should reject measure columns on dimension zones", () => {
         const { comp, dndMock } = createEditor({ fieldType: "cols" });
         dndMock.getTransfer.mockReturnValue({
            column: [createMockAssetEntry(false)],
            tableName: "Orders",
         });

         expect(isDropAccept(comp)).toBe(false);
      });
   });

   describe("Group 3 — isDropAccept field reorder [Risk 2]", () => {
      it("should accept reorder within dimension zones", () => {
         const { comp, dndMock } = createEditor({ fieldType: "cols" });
         dndMock.getTransfer.mockReturnValue({
            dragSource: new TableTransfer("rows", 0, "Table1"),
         });

         expect(isDropAccept(comp)).toBe(true);
      });

      it("should reject cross-type field reorder", () => {
         const { comp, dndMock } = createEditor({ fieldType: "rows" });
         dndMock.getTransfer.mockReturnValue({
            dragSource: new TableTransfer("aggregates", 0, "Table1"),
         });

         expect(isDropAccept(comp)).toBe(false);
      });
   });

   describe("Group 4 — isDropAccept crosstab rules [Risk 2]", () => {
      it("should accept binding-tree drops on crosstab when not cube or assembly", () => {
         const { comp, dndMock } = createEditor({
            fieldType: "aggregates",
            objectType: "vscrosstab",
         });
         dndMock.getTransfer.mockReturnValue({
            column: [createMockAssetEntry(false)],
            tableName: "Orders",
         });

         expect(isDropAccept(comp)).toBe(true);
      });

      it("should accept field reorder on crosstab across dimension and measure zones", () => {
         const { comp, dndMock } = createEditor({
            fieldType: "aggregates",
            objectType: "vscrosstab",
         });
         dndMock.getTransfer.mockReturnValue({
            dragSource: new TableTransfer("rows", 0, "Table1"),
         });

         expect(isDropAccept(comp)).toBe(true);
      });

      it("should reject cube dimension columns on aggregate zone", () => {
         const { comp, dndMock } = createEditor({
            fieldType: "aggregates",
            objectType: "vscrosstab",
         });
         dndMock.getTransfer.mockReturnValue({
            column: [createMockAssetEntry(true)],
            tableName: "___inetsoft_cube_sales",
         });

         expect(isDropAccept(comp)).toBe(false);
      });
   });

   describe("Group 5 — onDrop embedded guards [Risk 3]", () => {
      it("should block embedded table drops that contain calc fields", () => {
         const model = TestUtils.createMockTableBindingModel();
         model.embedded = true;
         const { comp, dndMock } = createEditor({ bindingModel: model });
         dndMock.containsCalc.mockReturnValue(true);
         const messageSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("ok");
         comp.activeIdx = 0;
         const event = { preventDefault: vi.fn() };

         comp.onDrop(event as any);

         expect(messageSpy).toHaveBeenCalled();
         expect(dndMock.processOnDrop).not.toHaveBeenCalled();
         expect(comp.activeIdx).toBe(-1);
      });

      it("should block embedded table drops with non-embedded columns", () => {
         const model = TestUtils.createMockTableBindingModel();
         model.embedded = true;
         const { comp, dndMock } = createEditor({ bindingModel: model });
         dndMock.isAllEmbeddedColumn.mockReturnValue(false);
         const messageSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("ok");
         comp.activeIdx = 0;

         comp.onDrop({ preventDefault: vi.fn() } as any);

         expect(messageSpy).toHaveBeenCalled();
         expect(dndMock.processOnDrop).not.toHaveBeenCalled();
      });

      it("should process drop when embedded guards pass", () => {
         const model = TestUtils.createMockTableBindingModel();
         model.embedded = true;
         const { comp, dndMock } = createEditor({ bindingModel: model });
         dndMock.getTransfer.mockReturnValue({
            dragSource: new TableTransfer("rows", 0, "Table1"),
         });
         comp.activeIdx = 0;

         comp.onDrop({ preventDefault: vi.fn() } as any);

         expect(dndMock.processOnDrop).toHaveBeenCalled();
         expect(comp.activeIdx).toBe(-1);
      });
   });
});
