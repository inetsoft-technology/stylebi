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
import { ViewsheetInfo } from "../../data/viewsheet-info";
import { createCalcTableComponent } from "./vs-calctable.component.test-helpers";
import { createCrosstabComponent } from "./vs-crosstab.component.test-helpers";
import { createTableComponent } from "./vs-table.component.test-helpers";

const tabs = (bottomTabs = true) =>
   new ViewsheetInfo([{ absoluteName: "tab1", bottomTabs }] as any, null, false, "vs1");

// lets the deferred model writes land
const flush = () => Promise.resolve();

const inBottomTab: any = { shrink: true, scrollHeight: 54, containerType: "VSTab", container: "tab1" };

afterEach(() => vi.restoreAllMocks());

// A shrunk table in a bottom-tabs tab draws its card below objectFormat.top; it publishes that
// shift so the container can place the strip and the focus outline on the card.
describe("BaseTable card top shift", () => {
   it("publishes a shrunk table's bottom-tabs shift after the pass, not during it", async () => {
      // 300x200 at top 50, 20px title and header: tableHeight 160, card 200 - 160 + 54 = 94
      const { comp } = createTableComponent({ model: inBottomTab });
      comp.vsInfo = tabs();

      expect(comp.getObjectTop()).toBe(50 + 106);
      expect(comp.model.cardTopShift).toBeUndefined();

      await flush();

      expect(comp.model.cardTopShift).toBe(106);
   });

   it("marks the view for check when it publishes", async () => {
      const { comp, changeDetectorRef } = createTableComponent({ model: inBottomTab });
      comp.vsInfo = tabs();
      vi.spyOn(comp as any, "getCardHeight").mockReturnValue(120);
      changeDetectorRef.markForCheck.mockClear();

      comp.getObjectTop();
      await flush();

      expect(comp.model.cardTopShift).toBe(80);
      expect(changeDetectorRef.markForCheck).toHaveBeenCalled();
   });

   it("publishes nothing for a table in a top-tabs tab", async () => {
      const { comp, changeDetectorRef } = createTableComponent({ model: inBottomTab });
      comp.vsInfo = tabs(false);
      vi.spyOn(comp as any, "getCardHeight").mockReturnValue(120);
      changeDetectorRef.markForCheck.mockClear();

      expect(comp.getObjectTop()).toBe(50);
      await flush();

      expect(comp.model.cardTopShift).toBeUndefined();
      expect(changeDetectorRef.markForCheck).not.toHaveBeenCalled();
   });

   it("publishes nothing outside the viewer", async () => {
      const { comp } = createTableComponent({
         contextProvider: {
            viewer: false, preview: false, binding: false, composer: true,
            vsWizard: false, vsWizardPreview: false, embedAssembly: false,
         },
         model: inBottomTab,
      });
      comp.vsInfo = tabs();

      expect(comp.getObjectTop()).toBe(50);
      await flush();

      expect(comp.model.cardTopShift).toBeUndefined();
   });

   it("publishes zero once the parent switches to top tabs", async () => {
      const { comp } = createTableComponent({ model: inBottomTab });
      const info = tabs();
      comp.vsInfo = info;
      vi.spyOn(comp as any, "getCardHeight").mockReturnValue(120);

      comp.getObjectTop();
      await flush();
      expect(comp.model.cardTopShift).toBe(80);

      (info.vsObjects[0] as any).bottomTabs = false;
      comp.getObjectTop();
      await flush();

      expect(comp.model.cardTopShift).toBe(0);
   });

   it("publishes zero once its rows fill the card", async () => {
      const { comp } = createTableComponent({ model: inBottomTab });
      comp.vsInfo = tabs();
      const cardHeight = vi.spyOn(comp as any, "getCardHeight").mockReturnValue(120);

      comp.getObjectTop();
      await flush();
      expect(comp.model.cardTopShift).toBe(80);

      cardHeight.mockReturnValue(comp.model.objectFormat.height);
      expect(comp.getObjectTop()).toBe(50);
      await flush();

      expect(comp.model.cardTopShift).toBe(0);
   });

   it("publishes zero in max mode", async () => {
      const { comp } = createTableComponent({ model: inBottomTab });
      comp.vsInfo = tabs();
      vi.spyOn(comp as any, "getCardHeight").mockReturnValue(120);

      comp.getObjectTop();
      await flush();
      comp.model.maxMode = true;
      comp.getObjectTop();
      await flush();

      expect(comp.model.cardTopShift).toBe(0);
   });

   it("publishes the latest shift when several passes run before the write lands", async () => {
      const { comp } = createTableComponent({ model: inBottomTab });
      comp.vsInfo = tabs();
      const cardHeight = vi.spyOn(comp as any, "getCardHeight").mockReturnValue(120);

      comp.getObjectTop();
      cardHeight.mockReturnValue(150);
      comp.getObjectTop();
      await flush();

      expect(comp.model.cardTopShift).toBe(50);
   });

   it("does not write after the table is destroyed", async () => {
      const { comp } = createTableComponent({ model: inBottomTab });
      comp.vsInfo = tabs();
      vi.spyOn(comp as any, "getCardHeight").mockReturnValue(120);

      comp.getObjectTop();
      (comp as any).destroyed = true;
      await flush();

      expect(comp.model.cardTopShift).toBeUndefined();
   });

   it("publishes a crosstab's shift with its hidden title taken out of the card", async () => {
      // tableHeight 200 - 20 header - 20 title = 160; the base card 94 is what objectHeight carries,
      // the crosstab draws 94 - 20 = 74 with its title hidden
      const { comp } = createCrosstabComponent({ model: { ...inBottomTab, titleVisible: false } });
      comp.vsInfo = tabs();

      expect(comp.getObjectTop()).toBe(50 + 126);
      await flush();

      expect(comp.model.cardTopShift).toBe(126);
   });

   it("publishes a freehand table's shift", async () => {
      // 300x180 at top 20, 24px title, 20px header: tableHeight 136, card 180 - 136 + 54 = 98
      const { comp } = createCalcTableComponent({ model: inBottomTab });
      comp.vsInfo = tabs();

      expect(comp.getObjectTop()).toBe(20 + 82);
      await flush();

      expect(comp.model.cardTopShift).toBe(82);
   });
});

describe("BaseTable cell height follow", () => {
   const composer = {
      viewer: false, preview: false, binding: false, composer: true,
      vsWizard: false, vsWizardPreview: false, embedAssembly: false,
   };
   const cell = (row: number, rowSpan = 1): any => ({ row, col: 0, rowSpan, colSpan: 1 });
   const table = (model: any, contextProvider: any = composer) =>
      createTableComponent({
         contextProvider,
         model: { vizModern: true, headerRowCount: 1, ...model },
      }).comp as any;

   it("a data cell follows while data rows are not pinned", () => {
      expect(table({ dataRowHeightPinned: false }).cellHeightFollowsDensity(cell(1))).toBe(true);
      expect(table({ dataRowHeightPinned: true }).cellHeightFollowsDensity(cell(1))).toBe(false);
   });

   it("a header cell reads the header pin", () => {
      const comp = table({ headerRowHeightPinned: true, dataRowHeightPinned: false });

      expect(comp.cellHeightFollowsDensity(cell(0))).toBe(false);
   });

   it("a span cell starting in the header reads the header pin", () => {
      const comp = table({ headerRowCount: 2, headerRowHeightPinned: false, dataRowHeightPinned: true });

      expect(comp.cellHeightFollowsDensity(cell(0, 3))).toBe(true);
   });

   it("hides the box in the viewer", () => {
      const comp = createTableComponent({ model: { vizModern: true, headerRowCount: 1 } }).comp as any;

      expect(comp.cellHeightFollowsDensity(cell(1))).toBeNull();
   });

   it("hides the box in preview", () => {
      const comp = table({}, { ...composer, preview: true });

      expect(comp.cellHeightFollowsDensity(cell(1))).toBeNull();
   });

   it("hides the box on an unmarked table", () => {
      expect(table({ vizModern: false }).cellHeightFollowsDensity(cell(1))).toBeNull();
   });

   it("sends followsDensity with the cell size event", () => {
      const { comp, viewsheetClient } = createTableComponent({
         contextProvider: composer, model: { vizModern: true, headerRowCount: 1 },
      });

      (comp as any).loadedRows = { start: 0, end: 0 };
      (comp as any).updateCellSize({ width: 96, height: 16, followsDensity: true }, cell(1));

      expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
         "/events/composer/viewsheet/table/changeCellSize",
         expect.objectContaining({ followsDensity: true }));
   });

   it("sends no followsDensity when the dialog had no box", () => {
      const { comp, viewsheetClient } = createTableComponent({
         model: { vizModern: true, headerRowCount: 1 },
      });

      (comp as any).loadedRows = { start: 0, end: 0 };
      (comp as any).updateCellSize({ width: 96, height: 16 }, cell(1));

      const event = viewsheetClient.sendEvent.mock.calls.find(
         (c: any[]) => c[0] === "/events/composer/viewsheet/table/changeCellSize")[1];
      expect(event.followsDensity).toBeUndefined();
   });
});
