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
import inetsoft.uql.viewsheet.CompositeSelectionValue;
import inetsoft.uql.viewsheet.SelectionList;
import inetsoft.uql.viewsheet.SelectionTreeVSAssembly;
import inetsoft.uql.viewsheet.SelectionValue;
import inetsoft.uql.viewsheet.internal.SelectionTreeVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * bug-78349: select_subtree mode=clear must report what the clear actually did. Under an active
 * search the native merge-back keeps the selected values the search hides, so the result states
 * the subtree's own cleared / retained counts, scoped to the subtree (a selection elsewhere in
 * the tree is not "kept from this clear"). The merge-back itself is simulated by the mocked
 * {@code VSSelectionService.selectSubtree}; the SelectionList/CompositeSelectionValue are real.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SelectionRuntimeServiceSubtreeClearReportTest {
   private static SelectionValue leaf(String name, int level, boolean selected) {
      SelectionValue value = new SelectionValue(name, name);
      value.setLevel(level);
      value.setSelected(selected);
      return value;
   }

   private static CompositeSelectionValue branch(String name, boolean selected,
                                                 SelectionValue... children)
   {
      CompositeSelectionValue composite = new CompositeSelectionValue(name, name);
      composite.setSelected(selected);
      SelectionList list = new SelectionList();

      for(SelectionValue child : children) {
         list.addSelectionValue(child);
      }

      composite.setSelectionList(list);
      return composite;
   }

   private static SelectionList domain(SelectionValue... top) {
      SelectionList domain = new SelectionList();

      for(SelectionValue value : top) {
         domain.addSelectionValue(value);
      }

      return domain;
   }

   /** East{NY, MA} and West{CA}; a parent is selected when any of its leaves is. */
   private static SelectionList eastWest(boolean ny, boolean ma, boolean ca) {
      return domain(branch("East", ny || ma, leaf("NY", 1, ny), leaf("MA", 1, ma)),
                    branch("West", ca, leaf("CA", 1, ca)));
   }

   private static SelectionRuntimeServiceTest.Harness harness(boolean idMode, SelectionList domain,
                                                              String search)
   {
      SelectionTreeVSAssembly assembly = SelectionRuntimeServiceTest.tree(0, false);
      when(assembly.isIDMode()).thenReturn(idMode);
      when(assembly.getSelectionList()).thenReturn(domain);
      when(((SelectionTreeVSAssemblyInfo) assembly.getInfo())
              .getSearchString()).thenReturn(search);
      return SelectionRuntimeServiceTest.harness(assembly);
   }

   private static void onApply(SelectionRuntimeServiceTest.Harness h, Runnable mergeBack) {
      try {
         doAnswer(inv -> {
            mergeBack.run();
            return null;
         }).when(h.selections()).selectSubtree(anyString(), anyString(), any(),
                                               any(Principal.class), any(), anyString());
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }
   }

   /** Simulates the apply the service now sends under a search (visible values only). */
   private static void onApplySelection(SelectionRuntimeServiceTest.Harness h, Runnable apply) {
      try {
         doAnswer(inv -> {
            apply.run();
            return null;
         }).when(h.selections()).applySelection(anyString(), anyString(), any(),
                                              any(Principal.class), any(), anyString());
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }
   }

   private static void deselect(SelectionList domain, int top, int... children) {
      CompositeSelectionValue parent = (CompositeSelectionValue) domain.getSelectionValue(top);

      for(int child : children) {
         parent.getSelectionList().getSelectionValue(child).setSelected(false);
      }
   }

   private static Map<String, Object> clear(SelectionRuntimeServiceTest.Harness h, String... path)
      throws Exception
   {
      return h.service().selectSubtree("tok", SelectionRuntimeServiceTest.principal(), "Tree1",
                                       List.of(path), "clear", "");
   }

   /** (a) MA is hidden by the search and so kept; only the visible NY is cleared (bug-78787). */
   @Test
   void clearUnderASearchReportsClearedAndRetainedScopedToTheSubtree() throws Exception {
      SelectionList domain = eastWest(true, true, true);
      SelectionRuntimeServiceTest.Harness h = harness(false, domain, "NY");
      onApplySelection(h, () -> deselect(domain, 0, 0));

      Map<String, Object> result = clear(h, "East");

      assertEquals(1, result.get("clearedCount"), "NY cleared");
      assertEquals(1, result.get("remainingUnderSubtree"));
      assertEquals("NY", result.get("scopedBySearch"));
      assertEquals(List.of(List.of("East", "MA")), result.get("scopedBySearchRetained"));
   }

   /** (b) The search hides everything selected under East: nothing cleared, retained named. */
   @Test
   void clearWhereTheSearchHidesEverythingReportsZeroClearedWithRetained() throws Exception {
      SelectionRuntimeServiceTest.Harness h =
         harness(false, eastWest(true, false, true), "MA");

      Map<String, Object> result = clear(h, "East");

      assertEquals(0, result.get("clearedCount"));
      assertEquals(1, result.get("remainingUnderSubtree"));
      assertEquals(List.of(List.of("East", "NY")), result.get("scopedBySearchRetained"));
   }

   /** (c) A branch with nothing selected is a no-op, and a selection elsewhere is not blamed. */
   @Test
   void clearOfAnEmptyBranchReportsZeroClearedAndNothingRetained() throws Exception {
      SelectionRuntimeServiceTest.Harness h =
         harness(false, eastWest(true, true, false), "NY");

      Map<String, Object> result = clear(h, "West");

      assertEquals(0, result.get("clearedCount"));
      assertEquals(0, result.get("remainingUnderSubtree"));
      assertFalse(result.containsKey("scopedBySearchRetained"));
      assertFalse(result.containsKey("scopedBySearch"));
   }

   @Test
   void clearWithoutASearchReportsTheFullCountAndNoRetainedKeys() throws Exception {
      SelectionList domain = eastWest(true, true, true);
      SelectionRuntimeServiceTest.Harness h = harness(false, domain, null);
      onApply(h, () -> {
         domain.getSelectionValue(0).setSelected(false);
         deselect(domain, 0, 0, 1);
      });

      Map<String, Object> result = clear(h, "East");

      assertEquals(2, result.get("clearedCount"));
      assertEquals(0, result.get("remainingUnderSubtree"));
      assertFalse(result.containsKey("scopedBySearchRetained"));
   }

   /** bug-78717: values:[] on a single-select list re-selects the first value and says so. */
   @Test
   void reportsTheValueASingleSelectListReselectsAfterEmptyValues() throws Exception {
      inetsoft.uql.viewsheet.SelectionListVSAssembly assembly = SelectionRuntimeServiceTest.list(inetsoft.uql.XConstants.SORT_ASC, true, null);
      SelectionList domain = new SelectionList();
      SelectionValue ca = new SelectionValue("CA", "CA");
      SelectionValue tx = new SelectionValue("TX", "TX");
      tx.setSelected(true);
      domain.addSelectionValue(ca);
      domain.addSelectionValue(tx);
      when(assembly.getSelectionList()).thenReturn(domain);
      SelectionRuntimeServiceTest.Harness h = SelectionRuntimeServiceTest.harness(assembly);
      doAnswer(inv -> {
         tx.setSelected(false);
         ca.setSelected(true);
         return null;
      }).when(h.selections()).applySelection(anyString(), anyString(), any(),
                                           any(Principal.class), any(), anyString());

      Map<String, Object> result = h.service().setSelection(
         "tok", SelectionRuntimeServiceTest.principal(), "Filter1", List.of(), null, null, null, null, "");

      assertEquals(List.of(List.of("CA")), result.get("reselectedBySingleSelect"));
      assertEquals(1, result.get("selectedTotal"));
   }

   /** bug-78717: a single-select tree re-selects West/CA inside the cleared subtree. */
   @Test
   void singleSelectClearThatReselectsInsideTheSubtreeReportsItByValue() throws Exception {
      SelectionList domain =
         domain(branch("West", true, leaf("CA", 1, false), leaf("WA", 1, true)));
      SelectionTreeVSAssembly assembly = SelectionRuntimeServiceTest.tree(0, true);
      when(assembly.isIDMode()).thenReturn(false);
      when(assembly.getSelectionList()).thenReturn(domain);
      SelectionRuntimeServiceTest.Harness h = SelectionRuntimeServiceTest.harness(assembly);
      onApply(h, () -> {
         CompositeSelectionValue west = (CompositeSelectionValue) domain.getSelectionValue(0);
         west.getSelectionList().getSelectionValue(1).setSelected(false);
         west.getSelectionList().getSelectionValue(0).setSelected(true);
      });

      Map<String, Object> result = clear(h, "West");

      assertEquals(1, result.get("clearedCount"), "WA is gone even though the count is unchanged");
      assertEquals(List.of(List.of("West", "CA")), result.get("reselectedBySingleSelect"));
   }

   @Test
   void selectModeResultIsUnchanged() throws Exception {
      SelectionRuntimeServiceTest.Harness h =
         harness(false, eastWest(false, false, false), null);

      Map<String, Object> result = h.service().selectSubtree(
         "tok", SelectionRuntimeServiceTest.principal(), "Tree1", List.of("East"), "select", "");

      assertFalse(result.containsKey("clearedCount"));
      assertFalse(result.containsKey("remainingUnderSubtree"));
   }

   /** ID mode: A{A1, A2} with A selected, and B elsewhere. */
   private static SelectionList idDomain() {
      return domain(branch("A", true, leaf("A1", 1, true), leaf("A2", 1, true)),
                    leaf("B", 0, true));
   }

   @Test
   void idModeClearUnderASearchScopesToTheSubtreeDescendantsAndExcludesTheRoot() throws Exception {
      SelectionList domain = idDomain();
      SelectionRuntimeServiceTest.Harness h = harness(true, domain, "A1");
      onApply(h, () -> deselect(domain, 0, 1));

      Map<String, Object> result = clear(h, "A");

      assertEquals(1, result.get("clearedCount"), "A2 cleared");
      assertEquals(1, result.get("remainingUnderSubtree"), "A1 only: not root A, not B");
      assertEquals(List.of(List.of("A1")), result.get("scopedBySearchRetained"));
   }

   /** The ID subtree root stays selected natively; that is not a search-retained value. */
   @Test
   void idModeClearThatLeavesOnlyTheSelectedRootReportsNothingRetained() throws Exception {
      SelectionList domain = idDomain();
      SelectionRuntimeServiceTest.Harness h = harness(true, domain, "A1");
      onApply(h, () -> deselect(domain, 0, 0, 1));

      Map<String, Object> result = clear(h, "A");

      assertEquals(2, result.get("clearedCount"));
      assertEquals(0, result.get("remainingUnderSubtree"));
      assertFalse(result.containsKey("scopedBySearchRetained"));
   }
}
