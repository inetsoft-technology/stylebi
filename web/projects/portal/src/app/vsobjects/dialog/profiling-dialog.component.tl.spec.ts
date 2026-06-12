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
 * ProfilingDialog — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3]  — ngOnInit: GET group-by → groupByFields populated; subscription not
 *                        cleaned up (no ngOnDestroy/takeUntilDestroyed — suspected leak)
 *   Group 2 [Risk 3]  — sortClicked: first click = DESC (init→ASC then advance); cycle DESC→NONE→ASC→DESC; reloadTable fires on each click
 *   Group 3 [Risk 2]  — showDetails setter: reloadTable called when toggled
 *   Group 4 [Risk 2]  — getSortLabel: all 4 branches (no sort info, ASC, DESC, NONE)
 *   Group 5 [baseline] — tableData setter: _tableData and tableHeight updated correctly
 *   Group 6 [baseline] — URI getters: groupByUri / chartUri / tableUri built correctly
 *   Group 7 [baseline] — cancel() → onCancel emitted
 *   Group 8 [baseline] — exportTable() → DownloadService.download called with correct URL
 *   Group 9 [baseline] — wheelScrollHandler(): scrollTop incremented by deltaY; preventDefault called
 *   Group 10 [baseline] — touchHScroll(): scrollLeft reduced by delta, clamped to 0
 *   Group 11 [baseline] — touchVScroll(): scrollY clamped at top (0) and bottom (scrollHeight−clientHeight)
 *
 * Confirmed bugs (it.fails): none
 *
 * Suspected bugs (header only):
 *   Suspicion A — ngOnInit subscribes to getModel() with no takeUntilDestroyed; if the dialog
 *     is closed while the group-by request is in flight, groupByFields will be written to a
 *     destroyed component instance.
 *
 * Out of scope:
 *   horizontalScroll()            — pure no-op; no observable side effect
 *   verticalScrollHandler()       — test value near zero: single-line assignment; broken scrollY
 *                                   fails visually and immediately
 *   updateVerticalScrollTooltip() — currentRow arithmetic trivial (Math.floor, no branch);
 *                                   tooltip placement branch gated by getBoundingClientRect()
 *                                   which always returns zero in jsdom
 *   isVScrollable()               — private; always returns false in jsdom, exercised transitively
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { render, waitFor } from "@testing-library/angular";
import { http, HttpResponse as MswHttpResponse } from "msw";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { server } from "@test-mocks/server";
import { ProfilingDialog } from "./profiling-dialog.component";
import { ModelService } from "../../widget/services/model.service";
import { DownloadService } from "../../../../../shared/download/download.service";
import { XConstants } from "../../common/util/xconstants";
import { BaseTableCellModel } from "../model/base-table-cell-model";

// ---------------------------------------------------------------------------
// Shared fixtures
// ---------------------------------------------------------------------------

function makeTableRow(cols = 2): BaseTableCellModel[] {
   return Array.from({ length: cols }, (_, i) => ({
      cellLabel: `cell-${i}`,
      row: 0,
      col: i,
   } as unknown as BaseTableCellModel));
}

const DOWNLOAD_MOCK = { download: vi.fn() };
const MODAL_MOCK = { open: vi.fn() };

beforeEach(() => {
   DOWNLOAD_MOCK.download.mockClear();
   MODAL_MOCK.open.mockClear();
});

async function renderComponent(opts: {
   objName?: string;
   isViewsheet?: boolean;
} = {}) {
   const { fixture } = await render(ProfilingDialog, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         provideHttpClient(),
         ModelService,
         { provide: NgbModal, useValue: MODAL_MOCK },
         { provide: DownloadService, useValue: DOWNLOAD_MOCK },
      ],
      componentInputs: {
         objName: opts.objName ?? "Chart1",
         isViewsheet: opts.isViewsheet ?? true,
      },
   });
   return { comp: fixture.componentInstance, fixture };
}

// ---------------------------------------------------------------------------
// Group 1 — ngOnInit: GET group-by → groupByFields populated [Risk 3]
// ---------------------------------------------------------------------------

describe("ProfilingDialog — ngOnInit: group-by fields", () => {
   // 🔁 Regression-sensitive: groupByFields drives the group-by selector in the template;
   // if the GET response is not applied the user sees only the default "Cycle Name" entry
   // even when the server provides additional grouping options.
   it("should populate groupByFields with 2 entries from the group-by API response", async () => {
      server.use(
         http.get("*/api/portal/profile/group-by*", () =>
            MswHttpResponse.json({
               fields: [
                  { label: "Cycle Name", value: "cycle" },
                  { label: "Query Name", value: "query" },
               ],
            })
         )
      );
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.groupByFields).toHaveLength(2));
   });

   it("should populate the second groupByField with the server-provided label and value", async () => {
      server.use(
         http.get("*/api/portal/profile/group-by*", () =>
            MswHttpResponse.json({
               fields: [
                  { label: "Cycle Name", value: "cycle" },
                  { label: "Query Name", value: "query" },
               ],
            })
         )
      );
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.groupByFields[1]).toEqual({ label: "Query Name", value: "query" }));
   });

   it("should apply the server label (not the i18n key) after HTTP response arrives", async () => {
      // The component initialises groupByFields with the i18n key "_#(js:Cycle Name)".
      // After ngOnInit's GET resolves, the server value "Cycle Name" must replace it.
      // Asserting on .label (not .value) distinguishes initial state from HTTP-applied state.
      const { comp } = await renderComponent();
      await waitFor(() => expect(comp.groupByFields[0].label).toBe("Cycle Name"));
   });
});

// ---------------------------------------------------------------------------
// Group 2 — sortClicked(): ASC→DESC→NONE→ASC cycle [Risk 3]
// ---------------------------------------------------------------------------

describe("ProfilingDialog — sortClicked(): sort cycle", () => {
   // 🔁 Regression-sensitive: the component initialises sortInfo with SORT_ASC then
   // immediately advances it inside the same sortClicked call, so the first click lands on
   // SORT_DESC; any change to the init value silently breaks the expected icon sequence.
   it("should set sortInfo.sortValue to DESC on first click (init→ASC then advance ASC→DESC)", async () => {
      server.use(http.put("*/api/portal/profile/table*", () => MswHttpResponse.json({ body: [] })));
      const { comp } = await renderComponent();
      comp.sortClicked(0);
      expect(comp.sortInfo.sortValue).toBe(XConstants.SORT_DESC);
   });

   it("should set sortInfo.col to the clicked column index on first click", async () => {
      server.use(http.put("*/api/portal/profile/table*", () => MswHttpResponse.json({ body: [] })));
      const { comp } = await renderComponent();
      comp.sortClicked(0);
      expect(comp.sortInfo.col).toBe(0);
   });

   it("should advance from DESC to NONE on second click of the same column", async () => {
      server.use(http.put("*/api/portal/profile/table*", () => MswHttpResponse.json({ body: [] })));
      const { comp } = await renderComponent();

      comp.sortClicked(0);
      comp.sortClicked(0);

      expect(comp.sortInfo.sortValue).toBe(XConstants.SORT_NONE);
   });

   it("should advance from NONE to ASC on third click of the same column", async () => {
      server.use(http.put("*/api/portal/profile/table*", () => MswHttpResponse.json({ body: [] })));
      const { comp } = await renderComponent();

      comp.sortClicked(0);
      comp.sortClicked(0);
      comp.sortClicked(0);

      expect(comp.sortInfo.sortValue).toBe(XConstants.SORT_ASC);
   });

   it("should call reloadTable (PUT) on each sort click", async () => {
      let putCount = 0;
      server.use(
         http.put("*/api/portal/profile/table*", () => {
            putCount++;
            return MswHttpResponse.json({ body: [] });
         })
      );
      const { comp } = await renderComponent();

      comp.sortClicked(0);
      comp.sortClicked(0);

      await waitFor(() => expect(putCount).toBeGreaterThanOrEqual(2));
   });
});

// ---------------------------------------------------------------------------
// Group 3 — showDetails setter: reloadTable triggered [Risk 2]
// ---------------------------------------------------------------------------

describe("ProfilingDialog — showDetails setter", () => {
   // 🔁 Regression-sensitive: toggling showDetails must trigger a table reload so the
   // detail/summary view reflects the new state; without a reload the table stays stale.
   it("should trigger a PUT reloadTable request when showDetails is set to true", async () => {
      let putCalled = false;
      server.use(
         http.put("*/api/portal/profile/table*", () => {
            putCalled = true;
            return MswHttpResponse.json({ body: [] });
         })
      );
      const { comp } = await renderComponent();

      comp.showDetails = true;

      await waitFor(() => expect(putCalled).toBe(true));
   });

   it("should trigger a PUT reloadTable request when showDetails is set to false", async () => {
      let putCalled = false;
      server.use(
         http.put("*/api/portal/profile/table*", () => {
            putCalled = true;
            return MswHttpResponse.json({ body: [] });
         })
      );
      const { comp } = await renderComponent();

      comp.showDetails = false;

      await waitFor(() => expect(putCalled).toBe(true));
   });
});

// ---------------------------------------------------------------------------
// Group 4 — getSortLabel(): all 4 branches [Risk 2]
// ---------------------------------------------------------------------------

describe("ProfilingDialog — getSortLabel(): all branches", () => {
   it("should return 'sort' when no sortInfo is set", async () => {
      const { comp } = await renderComponent();
      expect(comp.getSortLabel(0)).toBe("sort");
   });

   it("should return 'sort-descending' when sortInfo.col matches and sortValue is DESC", async () => {
      server.use(http.put("*/api/portal/profile/table*", () => MswHttpResponse.json({ body: [] })));
      const { comp } = await renderComponent();

      comp.sortClicked(1); // 1st click: init→ASC then advance→DESC

      expect(comp.getSortLabel(1)).toBe("sort-descending");
   });

   it("should return 'sort-ascending' when sortInfo.col matches and sortValue is ASC", async () => {
      server.use(http.put("*/api/portal/profile/table*", () => MswHttpResponse.json({ body: [] })));
      const { comp } = await renderComponent();

      comp.sortClicked(1); // DESC
      comp.sortClicked(1); // NONE
      comp.sortClicked(1); // ASC

      expect(comp.getSortLabel(1)).toBe("sort-ascending");
   });

   it("should return 'sort' when sortInfo exists but col does not match", async () => {
      server.use(http.put("*/api/portal/profile/table*", () => MswHttpResponse.json({ body: [] })));
      const { comp } = await renderComponent();

      comp.sortClicked(0); // sorts col 0

      expect(comp.getSortLabel(99)).toBe("sort"); // different col → default label
   });
});

// ---------------------------------------------------------------------------
// Group 5 — tableData setter [baseline]
// ---------------------------------------------------------------------------

describe("ProfilingDialog — tableData setter", () => {
   it("should store the assigned rows as tableData", async () => {
      const { comp } = await renderComponent();
      const rows = [makeTableRow(), makeTableRow()];
      comp.tableData = rows;
      expect(comp.tableData).toBe(rows);
   });

   it("should compute tableHeight as rowCount × cellHeight when rows are assigned", async () => {
      const { comp } = await renderComponent();
      const rows = [makeTableRow(), makeTableRow()];
      comp.tableData = rows;
      expect(comp.tableHeight).toBe(rows.length * comp.cellHeight);
   });

   it("should default _tableData to empty array when null is assigned", async () => {
      const { comp } = await renderComponent();

      comp.tableData = null as any;

      expect(comp.tableData).toEqual([]);
   });
});

// ---------------------------------------------------------------------------
// Group 6 — URI getters [baseline]
// ---------------------------------------------------------------------------

describe("ProfilingDialog — URI getters", () => {
   it("groupByUri should include objName and isViewsheet", async () => {
      const { comp } = await renderComponent({ objName: "MyChart", isViewsheet: true });

      expect(comp.groupByUri).toContain("name=" + encodeURIComponent("MyChart"));
      expect(comp.groupByUri).toContain("isViewsheet=true");
   });

   it("chartUri should include objName, showValue flag, and groupBy field", async () => {
      const { comp } = await renderComponent({ objName: "MyChart", isViewsheet: false });

      expect(comp.chartUri).toContain("name=" + encodeURIComponent("MyChart"));
      expect(comp.chartUri).toContain("showValue=true");   // showValue default is true
      expect(comp.chartUri).toContain("groupBy=cycle");    // groupBy default is "cycle"
      expect(comp.chartUri).toContain("isViewsheet=false");
   });

   it("tableUri should include isViewsheet and timeZone", async () => {
      const { comp } = await renderComponent({ isViewsheet: true });

      expect(comp.tableUri).toContain("isViewsheet=true");
      expect(comp.tableUri).toContain("timeZone=");
   });
});

// ---------------------------------------------------------------------------
// Group 7 — cancel() [baseline]
// ---------------------------------------------------------------------------

describe("ProfilingDialog — cancel()", () => {
   it("should emit onCancel with 'cancel' when cancel() is called", async () => {
      const { comp } = await renderComponent();
      const cancelled: string[] = [];
      comp.onCancel.subscribe((v: string) => cancelled.push(v));

      comp.cancel();

      expect(cancelled).toEqual(["cancel"]);
   });
});

// ---------------------------------------------------------------------------
// Group 8 — exportTable() [baseline]
// ---------------------------------------------------------------------------

describe("ProfilingDialog — exportTable()", () => {
   // 🔁 Regression-sensitive: exportTable() must pass the correct URL (with name, isViewsheet,
   // timeZone) to DownloadService; a wrong URL silently produces a failed download.
   it("should call DownloadService.download with a URL containing objName and isViewsheet", async () => {
      const { comp } = await renderComponent({ objName: "SalesChart", isViewsheet: true });

      comp.exportTable();

      expect(DOWNLOAD_MOCK.download).toHaveBeenCalledOnce();
      const url: string = DOWNLOAD_MOCK.download.mock.calls[0][0];
      expect(url).toContain("name=" + encodeURIComponent("SalesChart"));
      expect(url).toContain("isViewsheet=true");
      expect(url).toContain("timeZone=");
   });
});

// ---------------------------------------------------------------------------
// Group 9 — wheelScrollHandler() [baseline]
// ---------------------------------------------------------------------------

describe("ProfilingDialog — wheelScrollHandler()", () => {
   // 🔁 Regression-sensitive: removing event.preventDefault() silently routes wheel events to
   // the page instead of the table — no error is thrown and no immediate visual symptom appears
   // on a page that is not itself scrollable.
   it("should increment verticalScrollWrapper scrollTop by event.deltaY", async () => {
      const { comp, fixture } = await renderComponent();
      comp._showDetails = true;
      fixture.detectChanges();

      // scrollTop is a settable IDL attribute in jsdom; mock it for a robust assertion
      let scrollTop = 0;
      Object.defineProperty(comp.verticalScrollWrapper.nativeElement, "scrollTop", {
         get: () => scrollTop,
         set: (v: number) => { scrollTop = v; },
         configurable: true,
      });

      comp.wheelScrollHandler({ deltaY: 50, preventDefault: vi.fn() });

      expect(scrollTop).toBe(50);
   });

   it("should call event.preventDefault() to block native page scroll", async () => {
      const { comp, fixture } = await renderComponent();
      comp._showDetails = true;
      fixture.detectChanges();

      const mockEvent = { deltaY: 30, preventDefault: vi.fn() };
      comp.wheelScrollHandler(mockEvent);

      expect(mockEvent.preventDefault).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 10 — touchHScroll() [baseline]
// ---------------------------------------------------------------------------

describe("ProfilingDialog — touchHScroll()", () => {
   it("should reduce previewContainer scrollLeft by delta", async () => {
      const { comp, fixture } = await renderComponent();
      comp._showDetails = true;
      fixture.detectChanges();

      let scrollLeft = 100;
      Object.defineProperty(comp.previewContainer.nativeElement, "scrollLeft", {
         get: () => scrollLeft,
         set: (v: number) => { scrollLeft = v; },
         configurable: true,
      });

      comp.touchHScroll(30); // Math.max(0, 100 - 30) = 70

      expect(scrollLeft).toBe(70);
   });

   it("should clamp scrollLeft to 0 when delta exceeds current scrollLeft", async () => {
      const { comp, fixture } = await renderComponent();
      comp._showDetails = true;
      fixture.detectChanges();

      let scrollLeft = 20;
      Object.defineProperty(comp.previewContainer.nativeElement, "scrollLeft", {
         get: () => scrollLeft,
         set: (v: number) => { scrollLeft = v; },
         configurable: true,
      });

      comp.touchHScroll(50); // Math.max(0, 20 - 50) = 0

      expect(scrollLeft).toBe(0);
   });
});

// ---------------------------------------------------------------------------
// Group 11 — touchVScroll() [baseline]
// ---------------------------------------------------------------------------

describe("ProfilingDialog — touchVScroll()", () => {
   // touchVScroll applies two independent clamps:
   //   Math.max(0, scrollY - delta)          → prevents over-scrolling past the top
   //   Math.min(..., scrollHeight - clientHeight) → prevents over-scrolling past the bottom
   // scrollHeight / clientHeight are computed (always 0 in jsdom), so Object.defineProperty
   // is used to supply a realistic scrollable range for each test.
   async function renderWithScrollRange(scrollHeight = 200, clientHeight = 100) {
      const { comp, fixture } = await renderComponent();
      comp._showDetails = true;
      comp._tableData = [makeTableRow()]; // satisfies @if (tableData.length > 0) for #tableContainer
      fixture.detectChanges();
      const el = comp.tableContainer.nativeElement;
      Object.defineProperty(el, "scrollHeight", { get: () => scrollHeight, configurable: true });
      Object.defineProperty(el, "clientHeight", { get: () => clientHeight, configurable: true });
      return comp; // scrollable range: [0, scrollHeight − clientHeight]
   }

   it("should reduce scrollY by delta when within the scrollable range", async () => {
      const comp = await renderWithScrollRange(); // range [0, 100]
      comp.scrollY = 50;

      comp.touchVScroll(20); // Math.max(0, 30) = 30; Math.min(30, 100) = 30

      expect(comp.scrollY).toBe(30);
   });

   it("should clamp scrollY to 0 when delta exceeds current scrollY (top boundary)", async () => {
      const comp = await renderWithScrollRange();
      comp.scrollY = 10;

      comp.touchVScroll(30); // Math.max(0, -20) = 0; Math.min(0, 100) = 0

      expect(comp.scrollY).toBe(0);
   });

   it("should clamp scrollY to scrollHeight − clientHeight when scrolling past the bottom", async () => {
      const comp = await renderWithScrollRange(); // max = 200 − 100 = 100
      comp.scrollY = 50;

      comp.touchVScroll(-200); // Math.max(0, 250) = 250; Math.min(250, 100) = 100

      expect(comp.scrollY).toBe(100);
   });
});
