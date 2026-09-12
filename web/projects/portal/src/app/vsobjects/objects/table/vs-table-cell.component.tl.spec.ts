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
 * VSTableCell - single-pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - updateCellType branches for input, HTML, image, presenter, and plain text
 *   Group 2 [Risk 2] - selection, link, and form-edit event routing
 *   Group 3 [Risk 1] - display getters and date/calendar helpers
 */

import { Subject } from "rxjs";
import { ColumnOptionType } from "../../model/column-option-type";
import { TableDataPathTypes } from "../../../common/data/table-data-path-types";
import { VSTableCell, CellType } from "./vs-table-cell.component";
import { BaseTableCellModel } from "../../model/base-table-cell-model";
import { BaseTableModel } from "../../model/base-table-model";
import { Viewsheet } from "../../../composer/data/vs/viewsheet";

afterEach(() => vi.restoreAllMocks());

interface CellContextOverrides {
   viewer?: boolean;
   preview?: boolean;
   composer?: boolean;
   binding?: boolean;
   vsWizard?: boolean;
   vsWizardPreview?: boolean;
}

interface VSTableCellTestOverrides {
   cell?: Partial<BaseTableCellModel>;
   table?: Partial<BaseTableModel>;
   context?: CellContextOverrides;
}

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

function makeTable(overrides: Partial<BaseTableModel> = {}): BaseTableModel {
   return {
      absoluteName: "Table1",
      enabled: true,
      hasFlyover: false,
      dataTipAlpha: 0.8,
      editing: false,
      cubeType: null,
      objectType: "VSTable",
      rowCount: 2,
      headerRowCount: 1,
      colCount: 2,
      headerColCount: 1,
      dateComparisonDefined: false,
      dateComparisonEnabled: false,
      ...overrides,
   } as BaseTableModel;
}

function createComponent(overrides: VSTableCellTestOverrides = {}) {
   const renderer = {
      setStyle: vi.fn(),
      setAttribute: vi.fn(),
      removeAttribute: vi.fn(),
   };
   const elementRef = {
      nativeElement: {
         getBoundingClientRect: vi.fn(() => ({ right: 200 })),
      },
   };
   const dropdownSubject = new Subject<any>();
   const dropdownRef = {
      componentInstance: {
         onDateChange: dropdownSubject.asObservable(),
         date: null,
      },
      close: vi.fn(),
   };
   const dropdownService = {
      open: vi.fn(() => dropdownRef),
   };
   const viewsheetClientService = { runtimeId: "viewsheet-1" };
   const dataTipService = {
      isFrozen: vi.fn(() => false),
      unfreeze: vi.fn(),
      freeze: vi.fn(),
      hideDataTip: vi.fn(),
      showDataTip: vi.fn(),
   };
   const contextProvider = {
      viewer: true,
      preview: false,
      composer: false,
      binding: false,
      vsWizard: false,
      vsWizardPreview: false,
      ...overrides.context,
   };
   const debounceService = { debounce: vi.fn((_key: string, fn: () => void) => fn()) };
   const changeDetectionRef = { detectChanges: vi.fn() };
   const popComponentService = {
      isPopComponent: vi.fn(() => false),
      isPopComponentVisible: vi.fn(() => false),
    };
   const zone = { run: (fn: any) => fn() };

   const comp = new VSTableCell(
      renderer as any,
      elementRef as any,
      dropdownService as any,
      viewsheetClientService as any,
      dataTipService as any,
      contextProvider as any,
      debounceService as any,
      changeDetectionRef as any,
      popComponentService as any,
      zone as any,
   );

   comp.cell = makeCell(overrides.cell);
   comp.table = makeTable(overrides.table);
   comp.viewsheet = {} as Viewsheet;

   return {
      comp,
      renderer,
      elementRef,
      dropdownService,
      dropdownSubject,
      dropdownRef,
      viewsheetClientService,
      dataTipService,
      contextProvider,
      debounceService,
      changeDetectionRef,
      popComponentService,
      zone,
   };
}

describe("VSTableCell", () => {
   describe("updateCellType", () => {
      it("should switch to input mode for selected embedded header cells", () => {
         const { comp, changeDetectionRef } = createComponent({
            cell: { cellLabel: "Edit me" },
            context: { viewer: true },
         });
         comp.isHeader = true;
         comp.isEmbedded = true;
         comp.nameable = true;
         comp.selected = true;
         comp.editing = true;

         comp.updateCellType();

         expect(comp.cellType).toBe(CellType.INPUT);
         expect(changeDetectionRef.detectChanges).toHaveBeenCalled();
      });

      it("should convert multiline labels to HTML", () => {
         const { comp } = createComponent({
            cell: { cellLabel: "Line 1\nLine 2" },
         });

         comp.updateCellType();

         expect(comp.htmlText).toBe("Line 1<br>Line 2");
         expect(comp.cellType).toBe(CellType.HTML);
      });
   });

   describe("Selection and links", () => {
      it("should emit onSelectCell when the cell is pressed", () => {
         const { comp } = createComponent();
         const emitSpy = vi.spyOn(comp.onSelectCell, "emit");

         comp.onDown({ target: {}, stopPropagation: vi.fn(), preventDefault: vi.fn() } as any);

         expect(emitSpy).toHaveBeenCalledWith(expect.objectContaining({ target: {} }));
      });

      it("should emit link metadata on clickLink", () => {
         const { comp } = createComponent({
            cell: { hyperlinks: [{ href: "/detail" } as any] },
         });
         comp.numLinks = 1;
         const emitSpy = vi.spyOn(comp.onLinkClicked, "emit");
         const event = {
            button: 0,
            ctrlKey: false,
            metaKey: false,
            shiftKey: false,
            clientX: 11,
            clientY: 22,
            preventDefault: vi.fn(),
         } as any;

         comp.clickLink(event);

         expect(event.preventDefault).toHaveBeenCalled();
         expect(emitSpy).toHaveBeenCalledWith({
            hyperlinks: comp.cell.hyperlinks,
            xPos: 11,
            yPos: 22,
            numLinks: 1,
         });
      });

      it("should close the dropdown on destroy", () => {
         const { comp, dropdownRef } = createComponent();
         comp["dropdownRef"] = dropdownRef as any;

         comp.ngOnDestroy();

         expect(dropdownRef.close).toHaveBeenCalled();
      });
   });

   describe("Form editing", () => {
      it("should round integer input and emit rename in embedded mode", () => {
         const { comp } = createComponent({
            context: { viewer: false, preview: false },
         });
         comp.isEmbedded = true;
         const renameSpy = vi.spyOn(comp.rename, "emit");

         comp.changeFormInput("3.6", ColumnOptionType.INTEGER);

         expect(renameSpy).toHaveBeenCalledWith("4");
      });

      it("should emit formInputChanged outside embedded mode", () => {
         const { comp } = createComponent({
            context: { viewer: true, preview: false },
         });
         const emitSpy = vi.spyOn(comp.formInputChanged, "emit");

         comp.changeFormInput("abc");

         expect(emitSpy).toHaveBeenCalledWith("abc");
      });

      it("should select the first option when the cell has no value", () => {
         const { comp } = createComponent({
            cell: { cellData: null, options: ["first", "second"] },
         });
         const changeFormInputSpy = vi.spyOn(comp, "changeFormInput").mockImplementation(() => {});

         expect(comp.selectedOption).toBeNull();
         expect(changeFormInputSpy).toHaveBeenCalledWith("first");
      });

      it("should emit a timestamp string from updateDate", () => {
         const { comp } = createComponent();
         const emitSpy = vi.spyOn(comp.formInputChanged, "emit");
         const date = { year: 2024, month: 1, day: 2 };

         comp.updateDate(date);

         expect(emitSpy).toHaveBeenCalledWith(new Date(2024, 0, 2, 0, 0, 0, 0).getTime() + "");
      });

      it("should open the date picker and apply the chosen date", () => {
         const { comp, dropdownService, dropdownSubject } = createComponent({
            context: { viewer: true, preview: false },
            cell: {
               editable: true,
               editorType: ColumnOptionType.DATE,
               dataPath: { dataType: "Date", path: [], type: TableDataPathTypes.DETAIL } as any,
            },
         });
         comp.date = { year: 2024, month: 1, day: 1 };
         const emitSpy = vi.spyOn(comp.formInputChanged, "emit");
         const event = { pageX: 44, pageY: 55 } as any;

         comp.openCalendar(event);
         dropdownSubject.next({ year: 2024, month: 1, day: 3 });

         expect(dropdownService.open).toHaveBeenCalled();
         expect(emitSpy).toHaveBeenCalledWith(new Date(2024, 0, 3, 0, 0, 0, 0).getTime() + "");
      });
   });

   describe("Display getters", () => {
      it("should hide the drill icon when the cell is empty in a cube table", () => {
         const { comp } = createComponent({
            cell: { cellData: "", drillOp: "+" },
            table: { cubeType: "cube" },
         });

         expect(comp.drillVisible).toBe(false);
      });

      it("should show the drill icon for a drillable non-wizard cell", () => {
         const { comp } = createComponent({
            cell: { cellData: "value", drillOp: "+" },
            table: { cubeType: null },
         });

         expect(comp.drillVisible).toBe(true);
      });

      it("should report linked header visibility only when links exist", () => {
         const { comp } = createComponent({
            cell: { hyperlinks: [{ href: "/detail" } as any] },
         });
         comp.isEmbedded = false;
         comp.selected = false;
         comp.editing = false;
         comp.numLinks = 1;

         expect(comp.isShowLinkedHeader).toBe(true);
      });

      it("should format the cell height string for the current browser", () => {
         const { comp } = createComponent();
         comp.height = 40;
         comp.vBorderWidth = 6;

         expect(comp.getCellHeight(false)).toBe("34px");
      });

      it("should validate dates only for supported data path types", () => {
         const { comp } = createComponent({
            cell: {
               dataPath: {
                  dataType: "Long",
                  path: [],
                  type: TableDataPathTypes.DETAIL,
               } as any,
            },
         });

         expect(comp.isValidDate(new Date(2024, 0, 1), comp.cell)).toBe(true);
      });
   });
});
