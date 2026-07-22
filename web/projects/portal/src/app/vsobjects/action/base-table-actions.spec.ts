/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
import { XConstants } from "../../common/util/xconstants";
import { BaseTableActions } from "./base-table-actions";

describe("BaseTableActions.sortIcon", () => {
   function iconFor(sortValue: number): string {
      const actions: any = Object.create(BaseTableActions.prototype);
      actions.model = { sortInfo: { sortValue } };
      return actions.sortIcon;
   }

   it("returns valid ineticons names for every sort state", () => {
      expect(iconFor(XConstants.SORT_ASC)).toBe("sort-ascending-icon");
      expect(iconFor(XConstants.SORT_DESC)).toBe("sort-descending-icon");
      expect(iconFor(XConstants.SORT_VALUE_ASC)).toBe("sort-value-ascending-icon");
      expect(iconFor(XConstants.SORT_VALUE_DESC)).toBe("sort-value-descending-icon");
      expect(iconFor(0)).toBe("sort-icon");
   });
});
