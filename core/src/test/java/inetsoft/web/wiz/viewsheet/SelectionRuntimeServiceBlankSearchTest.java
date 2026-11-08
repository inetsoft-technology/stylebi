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

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.XConstants;
import inetsoft.uql.viewsheet.SelectionList;
import inetsoft.uql.viewsheet.SelectionListVSAssembly;
import inetsoft.uql.viewsheet.SelectionValue;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

/**
 * bug-80652: a whitespace-only search string is a real search to core
 * ({@code SelectionList.findAll} only treats null/empty as "no search", and the widget's search
 * box sends the typed text untrimmed), so the wiz-side guards must treat it as one too and
 * disclose/scope by it instead of silently selecting a value the widget would hide.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SelectionRuntimeServiceBlankSearchTest {
   private static SelectionRuntimeServiceTest.Harness harnessWithSearch(String search) {
      SelectionList domain = new SelectionList();
      domain.addSelectionValue(new SelectionValue("Games", "Games"));
      domain.addSelectionValue(new SelectionValue("Toys", "Toys"));
      SelectionListVSAssembly assembly =
         SelectionRuntimeServiceTest.list(XConstants.SORT_ASC, false, search);
      when(assembly.getSelectionList()).thenReturn(domain);
      return SelectionRuntimeServiceTest.harness(assembly);
   }

   private static List<List<String>> games() {
      return List.of(Arrays.asList("Games"));
   }

   @Test
   void whitespaceOnlySearchScopesAndDisclosesAValuesApply() throws Exception {
      SelectionRuntimeServiceTest.Harness h = harnessWithSearch(" ");

      Map<String, Object> result = h.service().setSelection(
         "tok", SelectionRuntimeServiceTest.principal(), "Filter1", games(), null, null, null,
         null, "");

      assertEquals(" ", result.get("scopedBySearch"));
      assertEquals(games(), result.get("scopedBySearchDropped"));
   }

   @Test
   void emptySearchStillMeansNoSearch() throws Exception {
      SelectionRuntimeServiceTest.Harness h = harnessWithSearch("");

      Map<String, Object> result = h.service().setSelection(
         "tok", SelectionRuntimeServiceTest.principal(), "Filter1", games(), null, null, null,
         null, "");

      assertFalse(result.containsKey("scopedBySearch"));
   }
}
