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
 * PhysicalJoinEditPane — Pass 2: Risk
 *
 * Direct-instantiation tests (no ATL render). JoinThumbnailService is fully
 * mocked — no real jsPlumb initialization occurs, so this file is safe to
 * import MSW (though these tests do not require MSW; httpClient is mocked
 * directly because sendHeartBeat is fire-and-forget with no inspectable
 * response body).
 *
 *   Group 1 — ngAfterViewInit: calls thumbnailService.setContainer
 *   Group 2 — ngAfterViewChecked: isSuspendDrawing branch; populated flag cleared + setTimeout
 *   Group 3 — sendHeartBeat: GET with runtimeID param; skipped when runtimeID falsy
 *   Group 4 — reorderColumns: joins move matched columns to front; Set deduplication; no-join no-op
 *   Group 5 — movePosition: splice-swap at any positions
 */

import {
   createComp,
   makeColumn,
   makeJoin,
   makeJspMock,
   makeModel,
   makeTableModel,
} from "./physical-join-edit-pane.component.test-helpers";

// ─────────────────────────────────────────────────────────────────────────────

afterEach(() => vi.restoreAllMocks());

describe("PhysicalJoinEditPane — Pass 2: Risk", () => {

   // ── Group 1 — ngAfterViewInit ─────────────────────────────────────────────
   describe("Group 1 — ngAfterViewInit", () => {
      it("should call thumbnailService.setContainer with jspContainerMain.nativeElement", () => {
         const { comp, thumbnailService } = createComp();
         const nativeEl = (comp as any).jspContainerMain.nativeElement;

         comp.ngAfterViewInit();

         expect(thumbnailService.setContainer).toHaveBeenCalledWith(nativeEl);
      });
   });

   // ── Group 2 — ngAfterViewChecked ──────────────────────────────────────────
   describe("Group 2 — ngAfterViewChecked", () => {
      beforeEach(() => vi.useFakeTimers());
      afterEach(() => {
         vi.clearAllTimers();
         vi.useRealTimers();
      });

      it("should call makeSource and setSuspendDrawing(false, true) when isSuspendDrawing is true", () => {
         const jspMock = makeJspMock(true);
         const { comp } = createComp({ jspMock });

         comp.ngAfterViewChecked();

         expect(jspMock.makeSource).toHaveBeenCalledWith("edit-join-table-column");
         expect(jspMock.setSuspendDrawing).toHaveBeenCalledWith(false, true);
      });

      it("should not call makeSource or setSuspendDrawing when isSuspendDrawing is false", () => {
         const jspMock = makeJspMock(false);
         const { comp } = createComp({ jspMock });

         comp.ngAfterViewChecked();

         expect(jspMock.makeSource).not.toHaveBeenCalled();
         expect(jspMock.setSuspendDrawing).not.toHaveBeenCalled();
      });

      it("should clear populated flag and schedule clear+initJoinConnections via setTimeout", () => {
         const { comp, thumbnailService } = createComp();
         (comp as any).populated = true;

         comp.ngAfterViewChecked();

         expect((comp as any).populated).toBe(false);
         expect(thumbnailService.clear).not.toHaveBeenCalled();

         vi.runAllTimers();

         expect(thumbnailService.clear).toHaveBeenCalled();
         expect(thumbnailService.initJoinConnections).toHaveBeenCalled();
      });

      it("should not schedule clear+initJoinConnections when populated is false", () => {
         const { comp, thumbnailService } = createComp();
         (comp as any).populated = false;

         comp.ngAfterViewChecked();
         vi.runAllTimers();

         expect(thumbnailService.clear).not.toHaveBeenCalled();
         expect(thumbnailService.initJoinConnections).not.toHaveBeenCalled();
      });

      it("should not call initJoinConnections a second time if ngAfterViewChecked fires again before setTimeout resolves", () => {
         const { comp, thumbnailService } = createComp();
         (comp as any).populated = true;

         comp.ngAfterViewChecked(); // clears populated
         (comp as any).populated = false; // stays false
         comp.ngAfterViewChecked(); // second call — populated already false

         vi.runAllTimers();

         expect(thumbnailService.initJoinConnections).toHaveBeenCalledTimes(1);
      });
   });

   // ── Group 3 — sendHeartBeat ───────────────────────────────────────────────
   describe("Group 3 — sendHeartBeat", () => {
      it("should call GET with runtimeID as 'id' param when runtimeID is present", () => {
         const { comp, httpClient } = createComp({ runtimeID: "rt-123" });

         (comp as any).sendHeartBeat("rt-123");

         expect(httpClient.get).toHaveBeenCalledWith(
            expect.stringContaining("physicalmodel/heartbeat"),
            expect.objectContaining({ params: expect.anything() }),
         );
      });

      it("should not call GET when runtimeID is empty string", () => {
         const { comp, httpClient } = createComp();

         (comp as any).sendHeartBeat("");

         expect(httpClient.get).not.toHaveBeenCalled();
      });

      it("should not call GET when runtimeID is null", () => {
         const { comp, httpClient } = createComp();

         (comp as any).sendHeartBeat(null);

         expect(httpClient.get).not.toHaveBeenCalled();
      });
   });

   // ── Group 4 — reorderColumns ──────────────────────────────────────────────
   describe("Group 4 — reorderColumns", () => {
      it("should move the matched join column to the front of left and right columns", () => {
         const { comp } = createComp();
         const leftTable = makeTableModel("orders", [
            makeColumn("id"), makeColumn("custId"), makeColumn("amount"),
         ], [makeJoin("custId", "id", "customers")]);
         const rightTable = makeTableModel("customers", [
            makeColumn("name"), makeColumn("id"), makeColumn("email"),
         ], []);
         comp.model = makeModel({ tables: [leftTable, rightTable] });

         (comp as any).reorderColumns();

         expect(leftTable.columns[0].name).toBe("custId");
         expect(rightTable.columns[0].name).toBe("id");
      });

      it("should not move columns when join.foreignTable does not match rightTable.name", () => {
         const { comp } = createComp();
         const leftTable = makeTableModel("orders", [
            makeColumn("id"), makeColumn("amount"),
         ], [makeJoin("id", "orderId", "shipments")]); // foreignTable != "customers"
         const rightTable = makeTableModel("customers", [
            makeColumn("id"), makeColumn("email"),
         ], []);
         comp.model = makeModel({ tables: [leftTable, rightTable] });
         const originalLeft = [...leftTable.columns.map(c => c.name)];
         const originalRight = [...rightTable.columns.map(c => c.name)];

         (comp as any).reorderColumns();

         expect(leftTable.columns.map(c => c.name)).toEqual(originalLeft);
         expect(rightTable.columns.map(c => c.name)).toEqual(originalRight);
      });

      it("should not process the same column twice (Set deduplication)", () => {
         const { comp } = createComp();
         const leftTable = makeTableModel("orders", [
            makeColumn("custId"), makeColumn("id"),
         ], [
            makeJoin("custId", "id", "customers"),
            makeJoin("custId", "id", "customers"), // duplicate
         ]);
         const rightTable = makeTableModel("customers", [makeColumn("id"), makeColumn("email")], []);
         comp.model = makeModel({ tables: [leftTable, rightTable] });

         (comp as any).reorderColumns();

         // custId stays at index 0, id stays at index 1 (no double-swap)
         expect(leftTable.columns[0].name).toBe("custId");
         expect(leftTable.columns[1].name).toBe("id");
      });

      it("should handle tables with no joins without throwing", () => {
         const { comp } = createComp();
         const leftTable = makeTableModel("orders", [makeColumn("id")], []); // no joins
         const rightTable = makeTableModel("customers", [makeColumn("id")], []);
         comp.model = makeModel({ tables: [leftTable, rightTable] });

         expect(() => (comp as any).reorderColumns()).not.toThrow();
      });

      it("should place multiple join columns in join order at the front", () => {
         const { comp } = createComp();
         const leftTable = makeTableModel("orders", [
            makeColumn("amount"), makeColumn("custId"), makeColumn("regionId"),
         ], [
            makeJoin("custId", "id", "customers"),
            makeJoin("regionId", "region", "customers"),
         ]);
         const rightTable = makeTableModel("customers", [
            makeColumn("email"), makeColumn("region"), makeColumn("id"),
         ], []);
         comp.model = makeModel({ tables: [leftTable, rightTable] });

         (comp as any).reorderColumns();

         expect(leftTable.columns[0].name).toBe("custId");
         expect(leftTable.columns[1].name).toBe("regionId");
         expect(rightTable.columns[0].name).toBe("id");
         expect(rightTable.columns[1].name).toBe("region");
      });
   });

   // ── Group 5 — movePosition ────────────────────────────────────────────────
   describe("Group 5 — movePosition", () => {
      it("should swap elements at from=2 and to=0, leaving middle unchanged", () => {
         const { comp } = createComp();
         const cols = [makeColumn("a"), makeColumn("b"), makeColumn("c")];

         (comp as any).movePosition(cols, 2, 0);

         // swap: cols[0]↔cols[2], cols[1] unchanged
         expect(cols[0].name).toBe("c");
         expect(cols[1].name).toBe("b");
         expect(cols[2].name).toBe("a");
      });

      it("should swap elements at from=0 and to=2, leaving middle unchanged", () => {
         const { comp } = createComp();
         const cols = [makeColumn("a"), makeColumn("b"), makeColumn("c")];

         (comp as any).movePosition(cols, 0, 2);

         // swap: cols[0]↔cols[2], cols[1] unchanged
         expect(cols[0].name).toBe("c");
         expect(cols[1].name).toBe("b");
         expect(cols[2].name).toBe("a");
      });

      it("should be a no-op when from === to", () => {
         const { comp } = createComp();
         const cols = [makeColumn("a"), makeColumn("b"), makeColumn("c")];

         (comp as any).movePosition(cols, 1, 1);

         expect(cols.map(c => c.name)).toEqual(["a", "b", "c"]);
      });

      it("should handle adjacent elements (from=1 to=0)", () => {
         const { comp } = createComp();
         const cols = [makeColumn("a"), makeColumn("b")];

         (comp as any).movePosition(cols, 1, 0);

         expect(cols[0].name).toBe("b");
         expect(cols[1].name).toBe("a");
      });
   });
});
