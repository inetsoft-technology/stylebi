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
 * ReportTabComponent - Pass 2: Risk
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - deletedEntry should only navigate away for the currently opened entry
 *
 * Mocking strategy:
 *   - route / router state -> helper stubs
 */

import {
   createReportTabComponent,
   makeReportEntry,
} from "./report-tab.component.test-helpers";

afterEach(() => {
   vi.restoreAllMocks();
});

describe("ReportTabComponent - risk", () => {
   it("deletedEntry should navigate back to the report tab only when the deleted entry is open", () => {
      const { comp, router } = createReportTabComponent();
      const openEntry = makeReportEntry({ path: "Examples/Sales" });
      comp.openedEntrys = [openEntry];

      comp.deletedEntry(openEntry);
      comp.deletedEntry(makeReportEntry({ path: "Examples/Other" }));

      expect(router.navigate).toHaveBeenCalledTimes(1);
      expect(router.navigate).toHaveBeenCalledWith(["/portal/tab/report"]);
   });
});
