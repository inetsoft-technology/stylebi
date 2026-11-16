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

package inetsoft.web.wiz.script;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The function list {@code get_pane_context} serves (bug #81445). */
@Tag("core")
class ScriptApiServiceFunctionNamesTest {
   private final ScriptApiService service = new ScriptApiService();

   @Test
   void listsCallableGlobalsSortedAndWithoutDuplicates() {
      List<String> names = service.functionNames();

      assertTrue(names.contains("runQuery"));
      assertTrue(names.contains("setCellValue"));

      List<String> sorted = new ArrayList<>(names);
      sorted.sort(null);
      assertEquals(sorted, names);
      assertEquals(names.size(), names.stream().distinct().count());
   }

   @Test
   void everyListedNameResolvesThroughLookup() {
      for(String name : service.functionNames()) {
         assertTrue(service.lookup(name).found(), name);
      }
   }

   @Test
   void leavesOutTypesPrototypeMethodsAndEditorOnlyGlobals() {
      List<String> names = service.functionNames();

      assertFalse(names.contains("AreaElement"), "a type entry is not a callable function");
      assertTrue(names.stream().noneMatch(n -> n.contains(".")));
      assertFalse(names.contains("showReport"), "report-only globals are hidden from viewsheets");
      assertTrue(names.stream().noneMatch(n -> n.startsWith("!")));
   }
}
