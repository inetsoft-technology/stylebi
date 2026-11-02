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
 * Bug #79665: unmapped data source types must not produce a "CreatingDataSetundefined"
 * help topic id; an empty cshid hides the help button in modal-header.
 */
import { makeComponent, makeDataSourceType } from "./tabular-query-dialog.component.test-helpers";

describe("TabularQueryDialog help topic id (#79665)", () => {
   it("returns an empty cshid for a type without a help mapping", () => {
      const {comp} = makeComponent({
         skipNgOnInit: true,
         dataSourceType: makeDataSourceType({name: "OneDrive", label: "OneDrive"})
      });
      expect(comp.helpCshid).toBe("");
      expect(comp.helpCshid).not.toContain("undefined");
   });

   it("keeps the topic id for a mapped type", () => {
      const {comp} = makeComponent({
         skipNgOnInit: true,
         dataSourceType: makeDataSourceType({name: "Rest.Asana", label: "Asana"})
      });
      expect(comp.helpCshid).toBe("CreatingDataSetAsana");
   });
});
