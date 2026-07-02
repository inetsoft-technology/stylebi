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
 * PhysicalJoinEditPane — Pass 1: Interaction
 *
 * JoinThumbnailService is fully mocked to avoid any real jsPlumb initialization.
 * Direct instantiation (no ATL render) — 2-param constructor.
 *
 * See physical-join-edit-pane.component.risk.tl.spec.ts for Pass 2 (ngAfterViewInit,
 * ngAfterViewChecked, sendHeartBeat, reorderColumns, movePosition).
 *
 *   Group 1 — jsp getter: returns thumbnailService.jsPlumbInstance
 *   Group 2 — findColumnIndex: finds column by name; -1 when not found; empty array
 */

import {
   createComp,
   makeColumn,
} from "./physical-join-edit-pane.component.test-helpers";

// ─────────────────────────────────────────────────────────────────────────────

afterEach(() => vi.restoreAllMocks());

describe("PhysicalJoinEditPane — Pass 1: Interaction", () => {

   // ── Group 1 — jsp getter ──────────────────────────────────────────────────
   describe("Group 1 — jsp getter", () => {
      it("should return thumbnailService.jsPlumbInstance", () => {
         const { comp, jspMock } = createComp({ withModel: false });

         expect(comp.jsp).toBe(jspMock);
      });

      it("should return the same object on repeated calls (no new instance)", () => {
         const { comp } = createComp({ withModel: false });

         expect(comp.jsp).toBe(comp.jsp);
      });
   });

   // ── Group 2 — findColumnIndex ─────────────────────────────────────────────
   // Private method tested directly: findColumnIndex is a pure search utility called by
   // reorderColumns. Exhaustive edge-case coverage (empty array, not-found, duplicates)
   // is impractical through the reorderColumns entry point without complex table+join setup.
   describe("Group 2 — findColumnIndex", () => {
      it("should return the index of the column with the matching name", () => {
         const { comp } = createComp({ withModel: false });
         const columns = [makeColumn("id"), makeColumn("name"), makeColumn("region")];

         expect((comp as any).findColumnIndex(columns, "name")).toBe(1);
      });

      it("should return 0 when the matching column is the first element", () => {
         const { comp } = createComp({ withModel: false });
         const columns = [makeColumn("id"), makeColumn("name")];

         expect((comp as any).findColumnIndex(columns, "id")).toBe(0);
      });

      it("should return the last index when the matching column is the last element", () => {
         const { comp } = createComp({ withModel: false });
         const columns = [makeColumn("id"), makeColumn("name"), makeColumn("region")];

         expect((comp as any).findColumnIndex(columns, "region")).toBe(2);
      });

      it("should return -1 when no column matches the given name", () => {
         const { comp } = createComp({ withModel: false });
         const columns = [makeColumn("id"), makeColumn("name")];

         expect((comp as any).findColumnIndex(columns, "nonexistent")).toBe(-1);
      });

      it("should return -1 for an empty columns array", () => {
         const { comp } = createComp({ withModel: false });

         expect((comp as any).findColumnIndex([], "id")).toBe(-1);
      });

      it("should return the first matching index when duplicates exist", () => {
         const { comp } = createComp({ withModel: false });
         const columns = [makeColumn("name"), makeColumn("name")];

         expect((comp as any).findColumnIndex(columns, "name")).toBe(0);
      });
   });
});
