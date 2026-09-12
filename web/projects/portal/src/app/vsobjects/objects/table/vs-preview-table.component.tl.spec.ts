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
 * VSPreviewTable - single-pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - processLoadPreviewTableCommand field population, prototype hydration, and modal/slide-out branching
 *   Group 2 [Risk 2] - openModal lifecycle and showStyle output emission
 *   Group 3 [Risk 1] - export URI construction and assembly identity getter
 */

import { Subject } from "rxjs";
import { VSPreviewTable } from "./vs-preview-table.component";
import { BaseTableCellModel } from "../../model/base-table-cell-model";
import { LoadPreviewTableCommand } from "../../command/load-preview-table-command";
import { SortInfo } from "./sort-info";
import { XConstants } from "../../../common/util/xconstants";

afterEach(() => vi.restoreAllMocks());

interface PreviewTableTestOverrides {
   context?: {
      viewer?: boolean;
      preview?: boolean;
   };
   modalService?: {
      open?: any;
   };
   ngbModalService?: {
      open?: any;
   };
   downloadService?: {
      download?: any;
   };
   baseHrefService?: {
      getBaseHref?: any;
   };
}

function makeCell(overrides: Partial<BaseTableCellModel> = {}): BaseTableCellModel {
   return {
      cellData: "value",
      cellLabel: "value",
      row: 0,
      col: 0,
      protoIdx: -1,
      hyperlinks: [],
      ...overrides,
   } as BaseTableCellModel;
}

function makeCommand(overrides: Partial<LoadPreviewTableCommand> = {}): LoadPreviewTableCommand {
   const tableData = [
      [makeCell({ protoIdx: 1 })],
   ];

   return {
      tableData,
      worksheetId: "worksheet-1",
      sortInfo: { col: 0, sortValue: XConstants.SORT_ASC } as SortInfo,
      colWidths: [120],
      styleModel: { name: "style" } as any,
      prototypeCache: [null, { cellData: "prototype", cellLabel: "prototype" }],
      ...overrides,
   } as LoadPreviewTableCommand;
}

function createComponent(overrides: PreviewTableTestOverrides = {}) {
   const viewsheetClient = { runtimeId: "viewsheet-1" };
   const downloadService = {
      download: vi.fn(),
      ...overrides.downloadService,
   };
   const modalService = {
      open: vi.fn(),
      ...overrides.modalService,
   };
   const ngbModalService = {
      open: vi.fn(),
      ...overrides.ngbModalService,
   };
   const context = {
      viewer: false,
      preview: false,
      ...overrides.context,
   };
   const baseHrefService = {
      getBaseHref: vi.fn(() => "/base"),
      ...overrides.baseHrefService,
   };
   const zone = { run: (fn: any) => fn() };

   const comp = new VSPreviewTable(
      viewsheetClient as any,
      downloadService as any,
      modalService as any,
      ngbModalService as any,
      zone as any,
      context as any,
      baseHrefService as any,
   );

   return {
      comp,
      viewsheetClient,
      downloadService,
      modalService,
      ngbModalService,
      context,
      baseHrefService,
   };
}

describe("VSPreviewTable", () => {
   describe("Command handling", () => {
      it("should return the assembly name", () => {
         const { comp } = createComponent();
         comp.assemblyName = "Crosstab1";

         expect(comp.getAssemblyName()).toBe("Crosstab1");
      });

      it("should ignore load commands with no table data", () => {
         const { comp } = createComponent();
         const openModalSpy = vi.spyOn(comp, "openModal").mockResolvedValue(undefined);

         comp.processLoadPreviewTableCommand(makeCommand({ tableData: null }));

         expect(openModalSpy).not.toHaveBeenCalled();
         expect(comp.tableData).toBeUndefined();
      });

      it("should hydrate prototypes and open the modal when no slide-out is active", () => {
         const { comp } = createComponent({
            context: { viewer: false, preview: false },
         });
         const openModalSpy = vi.spyOn(comp, "openModal").mockResolvedValue(undefined);

         comp.processLoadPreviewTableCommand(makeCommand());

         expect(comp.tableData[0][0].cellData).toBe("prototype");
         expect(comp.tableData[0][0].protoIdx).toBeUndefined();
         expect(openModalSpy).toHaveBeenCalled();
      });

      it("should expand the current slide-out when it is on top and collapsed", () => {
         const { comp } = createComponent({
            context: { viewer: false, preview: false },
         });
         const slideOut = {
            isOnTop: vi.fn(() => true),
            isExpanded: vi.fn(() => false),
            setExpanded: vi.fn(),
         } as any;
         comp.slideOut = slideOut;

         comp.processLoadPreviewTableCommand(makeCommand());

         expect(slideOut.setExpanded).toHaveBeenCalledWith(true);
      });
   });

   describe("Modal and export flows", () => {
      it("should export the worksheet with the expected download URI", () => {
         const { comp, downloadService, baseHrefService, viewsheetClient } = createComponent();
         comp.assemblyName = "Crosstab1";
         comp.worksheetId = "sheet-1";
         viewsheetClient.runtimeId = "vs-7";

         comp.exportTable();

         expect(baseHrefService.getBaseHref).toHaveBeenCalled();
         expect(downloadService.download).toHaveBeenCalledWith(
            "/base/../export/worksheet/sheet-1/Data?fileName=Crosstab1_Data_ExportedData&viewsheetId=vs-7",
         );
      });

      it("should clear slideOut and emit close after openModal resolves", async () => {
         const slideOut = { result: Promise.resolve("closed") };
         const { comp, modalService } = createComponent({
            modalService: { open: vi.fn(() => slideOut) },
         });
         const emitSpy = vi.spyOn(comp.onPreviewClose, "emit");
         comp.assemblyName = "Crosstab1";

         await comp.openModal();

         expect(modalService.open).toHaveBeenCalled();
         expect(comp.slideOut).toBeNull();
         expect(emitSpy).toHaveBeenCalled();
      });

      it("should emit a style change when the style dialog resolves", async () => {
         const { comp, ngbModalService } = createComponent({
            ngbModalService: {
               open: vi.fn(() => ({
                  result: Promise.resolve("detail-style"),
               })),
            },
         });
         comp.worksheetId = "sheet-1";
         const emitSpy = vi.spyOn(comp.onChange, "emit");

         comp.showStyle();
         await Promise.resolve();

         expect(ngbModalService.open).toHaveBeenCalled();
         expect(emitSpy).toHaveBeenCalledWith({
            sortInfo: null,
            format: null,
            column: [],
            str: "sheet-1",
            detailStyle: "detail-style",
            dndInfo: null,
         });
      });
   });
});
