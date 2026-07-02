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
 * Shared test helpers for PreviewTableComponent multi-pass TL specs.
 *
 * PreviewTableComponent has a 7-parameter constructor — much simpler than VSTable —
 * but relies on seven @ViewChild references that must be manually seeded before the
 * tableData setter is invoked (tableData reads previewContainer.nativeElement.scrollLeft
 * and clientWidth on the first call).
 *
 * Direct instantiation (not ATL render()) is used throughout: the component's DI chain
 * includes ModelService (which requires HttpClient + NgbModal) making render() impractical.
 */

import { of } from "rxjs";
import { BaseTableCellModel } from "../../model/base-table-cell-model";
import { PreviewTableComponent } from "./preview-table.component";

// ── Cell / table-data factories ───────────────────────────────────────────────

export function makeCell(overrides: Partial<BaseTableCellModel> = {}): BaseTableCellModel {
   return Object.assign(
      {
         row: 0,
         col: 0,
         cellData: "value",
         cellLabel: "Header",
         cellWidth: null,
         colSpan: 1,
         rowSpan: 1,
         spanCell: false,
         field: "col0",
         dataPath: {
            level: 0, col: false, row: false, type: 0, dataType: "", path: [], index: 0, colIndex: 0,
         },
         hyperlinks: [],
         underline: false,
         grouped: false,
         vsFormatModel: null,
      } as BaseTableCellModel,
      overrides,
   );
}

export function makeTableData(rows: number = 2, cols: number = 3): BaseTableCellModel[][] {
   const result: BaseTableCellModel[][] = [];
   for (let r = 0; r < rows; r++) {
      const row: BaseTableCellModel[] = [];
      for (let c = 0; c < cols; c++) {
         row.push(makeCell({ row: r, col: c, cellData: `r${r}c${c}`, cellLabel: `H${c}` }));
      }
      result.push(row);
   }
   return result;
}

// ── Context / overrides interfaces ────────────────────────────────────────────

export interface PreviewTableTestContext {
   comp: PreviewTableComponent;
   renderer: any;
   hyperlinkService: any;
   contextProvider: any;
   dropdownService: any;
   modelService: any;
   changeRef: any;
   hostElement: any;
   /** Direct reference to the object backing previewContainer.nativeElement */
   previewContainerEl: Record<string, any>;
}

export interface PreviewTableTestOverrides {
   renderer?: any;
   hyperlinkService?: any;
   contextProvider?: any;
   dropdownService?: any;
   modelService?: any;
   changeRef?: any;
   hostElement?: any;
   /** Initial tableData — defaults to makeTableData(2,3) */
   tableData?: BaseTableCellModel[][];
}

// ── Component factory ─────────────────────────────────────────────────────────

export function createPreviewComponent(overrides: PreviewTableTestOverrides = {}): PreviewTableTestContext {
   const renderer = overrides.renderer ?? {
      setProperty: vi.fn(),
      listen: vi.fn().mockReturnValue(() => {}),
   };

   const hyperlinkService = overrides.hyperlinkService ?? {
      createHyperlinkActions: vi.fn().mockReturnValue({}),
      createActionsContextmenu: vi.fn(),
   };

   const contextProvider = overrides.contextProvider ?? {
      composer: false,
      viewer: true,
      preview: false,
      binding: false,
      vsWizard: false,
      vsWizardPreview: false,
      embedAssembly: false,
   };

   const dropdownService = overrides.dropdownService ?? {
      open: vi.fn().mockReturnValue({ componentInstance: {}, closed: true }),
   };

   const modelService = overrides.modelService ?? {
      putModel: vi.fn().mockReturnValue(of(null)),
   };

   const changeRef = overrides.changeRef ?? {
      detectChanges: vi.fn(),
      markForCheck: vi.fn(),
   };

   const hostElement = overrides.hostElement ?? {
      nativeElement: { contains: vi.fn().mockReturnValue(true) },
   };

   const comp = new PreviewTableComponent(
      renderer as any,
      hyperlinkService as any,
      contextProvider as any,
      dropdownService as any,
      modelService as any,
      changeRef as any,
      hostElement as any,
   );

   // @Input defaults — set before tableData setter to avoid undefined access
   comp.runtimeId = "vs1";
   comp.assemblyName = "Table1";
   comp.isDetails = true;
   comp.viewsheetClient = { runtimeId: "vs1" } as any;
   comp.linkUri = "/portal/link";
   comp.worksheetId = "ws1";

   // ── ViewChild mocks (must precede tableData assignment) ───────────────────
   // previewContainer.nativeElement is a plain object — scrollLeft / clientWidth are
   // readable properties in jsdom because they are IDL attributes on real elements, but
   // our plain-object mock exposes them as ordinary JS properties (read-write).
   const previewContainerEl: Record<string, any> = {
      scrollLeft: 0,
      clientWidth: 500,
   };
   (comp as any).previewContainer = { nativeElement: previewContainerEl };

   (comp as any).table = {
      nativeElement: {
         offsetHeight: 100,
         getBoundingClientRect: vi.fn().mockReturnValue({
            left: 0, right: 500, top: 0, bottom: 200,
         }),
      },
   };
   (comp as any).headerTable = {
      nativeElement: { offsetHeight: 28 },
   };
   (comp as any).tableContainer = {
      nativeElement: { clientHeight: 400 },
   };

   // Default: NOT scrollable (firstElementChild.height 50 < wrapper.height 100).
   // Tests that need scrollability should override firstElementChild.getBoundingClientRect.
   (comp as any).verticalScrollWrapper = {
      nativeElement: {
         scrollTop: 0,
         getBoundingClientRect: vi.fn().mockReturnValue({ right: 500, height: 100 }),
         firstElementChild: {
            getBoundingClientRect: vi.fn().mockReturnValue({ height: 50 }),
         },
      },
   };
   (comp as any).verticalScrollTooltip = {
      isOpen: vi.fn().mockReturnValue(false),
      open: vi.fn(),
      close: vi.fn(),
      ngbTooltip: "",
      placement: "right",
   };
   (comp as any).dropdowns = { forEach: vi.fn() };

   // Trigger tableData setter — requires previewContainer to already be set
   const data = overrides.tableData ?? makeTableData(2, 3);
   comp.tableData = data;

   return { comp, renderer, hyperlinkService, contextProvider, dropdownService, modelService, changeRef, hostElement, previewContainerEl };
}

/**
 * Make the component's verticalScrollWrapper appear scrollable
 * (firstElementChild.height > wrapper.height).
 */
export function makeScrollable(comp: PreviewTableComponent): void {
   const vsw = (comp as any).verticalScrollWrapper.nativeElement;
   vsw.firstElementChild.getBoundingClientRect.mockReturnValue({ height: 300 });
   vsw.getBoundingClientRect.mockReturnValue({ right: 500, height: 100 });
}
