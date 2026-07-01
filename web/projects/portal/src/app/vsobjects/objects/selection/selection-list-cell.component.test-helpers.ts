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
 * Shared test helpers for SelectionListCell P1/P3 specs.
 *
 * Uses direct instantiation (no ATL render). All 5 constructor dependencies
 * (VSSelection, DomSanitizer, Renderer2, ContextProvider, NgbModal) are mocked.
 *
 * Canvas mock must be set up in each spec's beforeAll:
 *   vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue({
 *     font: "", measureText: () => ({ width: 0 })
 *   } as any);
 */

import { SelectionListCell } from "./selection-list-cell.component";
import { SelectionValueModel } from "../../model/selection-value-model";
import { VSFormatModel } from "../../model/vs-format-model";
import { ContextProvider } from "../../context-provider.service";

// ─────────────────────────────────────────────────────────────────────────────
// Internal shape types (avoids importing full model hierarchy)
// ─────────────────────────────────────────────────────────────────────────────

interface MockFormat {
   alpha: number;
   background: string;
   border: { bottom: string | null; left: string | null; right: string | null; top: string | null };
   decoration: string;
   font: string;
   foreground: string;
   hAlign: string;
   height: number;
   left: number;
   top: number;
   vAlign: string;
   justifyContent: string;
   alignItems: string;
   width: number;
   wrapping: { overflow: string | null; whiteSpace: string; wordWrap: string | null };
   zIndex: number;
   bringToFrontEnabled: boolean;
   sendToBackEnabled: boolean;
   position: string;
}

interface MockMeasureFormats {
   [key: string]: MockFormat;
}

interface MockListModel {
   objectType: string;
   measureFormats: MockMeasureFormats;
   singleSelection: boolean;
   singleSelectionLevels: number[] | null;
   mode?: number;
   showText: boolean;
   showBar: boolean;
   measure: string | null;
   quickSwitchAllowed: boolean;
   cellHeight: number;
   contextMenuCell?: SelectionValueModel;
}

// ─────────────────────────────────────────────────────────────────────────────
// Model factories
// ─────────────────────────────────────────────────────────────────────────────

export function makeSelectionValue(overrides: Partial<SelectionValueModel> = {}): SelectionValueModel {
   return {
      formatIndex: 0,
      label: "AMG Logistics",
      level: 0,
      measureLabel: "19",
      measureValue: 0.68,
      maxLines: 0,
      state: 0,
      value: "AMG Logistics",
      others: false,
      more: false,
      excluded: false,
      parentNode: null,
      path: null,
      ...overrides,
   };
}

function makeFormat(overrides: Partial<MockFormat> = {}): MockFormat {
   return {
      alpha: 1,
      background: "",
      border: { bottom: null, left: null, right: null, top: null },
      decoration: "",
      font: "10px Arial",
      foreground: "#2b2b2b",
      hAlign: "left",
      height: 0,
      left: 0,
      top: 0,
      vAlign: "center",
      justifyContent: "flex-start",
      alignItems: "stretch",
      width: 0,
      wrapping: { overflow: null, whiteSpace: "nowrap", wordWrap: null },
      zIndex: 0,
      bringToFrontEnabled: false,
      sendToBackEnabled: false,
      position: "",
      ...overrides,
   };
}

export function makeMeasureFormats(level = 0, vAlign = "center"): MockMeasureFormats {
   const fmt = makeFormat({ vAlign });
   return {
      [`Measure Text${level}`]: { ...fmt },
      [`Measure Bar${level}`]: { ...fmt },
      [`Measure Bar(-)${level}`]: { ...fmt },
   };
}

export function makeListModel(overrides: Partial<MockListModel> = {}): MockListModel {
   return {
      objectType: "VSSelectionList",
      measureFormats: makeMeasureFormats(),
      singleSelection: false,
      singleSelectionLevels: null,
      showText: true,
      showBar: true,
      measure: "Sales",
      quickSwitchAllowed: false,
      cellHeight: 18,
      ...overrides,
   };
}

// ─────────────────────────────────────────────────────────────────────────────
// Component factory
// ─────────────────────────────────────────────────────────────────────────────

export interface SelectionListCellContext {
   comp: SelectionListCell;
   vsSelection: any;
   sanitization: any;
   renderer: any;
   modalService: any;
}

export function makeViewerContext(): ContextProvider {
   // viewer=true, all others false
   return new ContextProvider(true, false, false, false, false, false, false, false, false, false, false);
}

export function createComp(opts: {
   contextProvider?: ContextProvider;
   model?: MockListModel;
   selectionValue?: SelectionValueModel;
   withInit?: boolean;
} = {}): SelectionListCellContext {
   const model = opts.model ?? makeListModel();

   const vsSelection: any = {
      model,
      controller: {
         getCellFormat: vi.fn().mockReturnValue(makeFormat() as unknown as VSFormatModel),
         isNodeOpen: vi.fn().mockReturnValue(false),
         toggleNode: vi.fn(),
      },
      getIdentifier: vi.fn().mockReturnValue("test-identifier"),
      getAssemblyName: vi.fn().mockReturnValue("SelectionList1"),
      cellHeight: 18,
      setQuickSwitchHover: vi.fn(),
      clearQuickSwitchHoverIfOwner: vi.fn(),
      isQuickSwitchRetainTarget: vi.fn().mockReturnValue(false),
      startResize: vi.fn(),
      updateCellHeight: vi.fn(),
      folderToggled: vi.fn(),
   };

   const sanitization: any = {
      bypassSecurityTrustStyle: vi.fn().mockImplementation((s: string) => s),
      bypassSecurityTrustHtml: vi.fn().mockImplementation((s: string) => s),
   };

   const renderer: any = { setStyle: vi.fn() };
   const modalService: any = { open: vi.fn() };

   const contextProvider = opts.contextProvider
      ?? new ContextProvider(false, false, false, false, false, false, false, false, false, false, false);

   const comp = new SelectionListCell(
      vsSelection,
      sanitization,
      renderer,
      contextProvider,
      modalService,
   );

   comp.selectionValue = opts.selectionValue ?? makeSelectionValue();
   comp.indent = 4;
   comp.cellWidth = 200;
   comp.barWidth = 50;
   comp.textWidth = -1;
   comp.measureRatio = 0.5;
   comp.measureMin = 0;
   comp.measureMax = 1;
   comp.scrollbarWidth = 15;
   comp.minWidth = 20;

   (comp as any).cell = { nativeElement: document.createElement("div") };

   if(opts.withInit !== false) {
      comp.ngOnInit();
   }

   return { comp, vsSelection, sanitization, renderer, modalService };
}
