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
package inetsoft.web.wiz.viewsheet;

import inetsoft.uql.viewsheet.SelectionValue;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * bug-78808: the snapshot used to report another filter's selection being removed by Association
 * counts a selected value only while it is not excluded (multi-select "applied" rule).
 */
@Tag("core")
class SelectionRuntimeServiceAppliedPathsTest {
   private static SelectionValue value(String name, boolean selected, boolean excluded) {
      SelectionValue value = new SelectionValue(name, name);
      value.setSelected(selected);
      value.setExcluded(excluded);
      return value;
   }

   @Test
   void skipExcludedDropsSelectedButExcludedValues() {
      SelectionValue[] values = {
         value("East", true, true), value("West", true, false), value("North", false, false)
      };

      assertEquals(List.of(List.of("East"), List.of("West")),
                   SelectionRuntimeService.selectedPaths(values, false));
      assertEquals(List.of(List.of("West")),
                   SelectionRuntimeService.selectedPaths(values, true));
   }
}
