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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.XConstants;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Bug #80482: every selection write entry point asks for pending Form table edits before its first
 * write, lets {@code discardPending:true} through, and lets a write that leaves the condition as
 * it is pass. Only {@code FormUtil.pendingDependentForms} is stubbed; the service is the real one.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SelectionRuntimeServiceFormGuardTest {
   private static final String REFUSAL =
      "pending (not yet applied) row edits of Form table AdminTable";

   private static SelectionListVSAssembly list(boolean single, String... selected) {
      SelectionListVSAssembly assembly =
         SelectionRuntimeServiceTest.list(XConstants.SORT_ASC, single, null);
      SelectionList list = new SelectionList();
      List<String> chosen = List.of(selected);

      for(String value : List.of("a", "b", "c")) {
         SelectionValue v = new SelectionValue(value, value);
         v.setSelected(chosen.contains(value));
         list.addSelectionValue(v);
      }

      when(assembly.getSelectionList()).thenReturn(list);
      return assembly;
   }

   private static MockedStatic<FormUtil> pendingForm() {
      MockedStatic<FormUtil> util = mockStatic(FormUtil.class);
      util.when(() -> FormUtil.pendingDependentForms(any(RuntimeViewsheet.class), anyString()))
         .thenReturn(List.of("AdminTable"));
      return util;
   }

   private static Principal user() {
      return SelectionRuntimeServiceTest.principal();
   }

   private static List<List<String>> paths(String... values) {
      return Arrays.stream(values).map(v -> List.of(v)).toList();
   }

   // -- setSelection ----------------------------------------------------------

   @Test
   void setSelectionRefusesAChangeBeforeAnyWrite() {
      SelectionRuntimeServiceTest.Harness h =
         SelectionRuntimeServiceTest.harness(list(false, "a"));

      try(MockedStatic<FormUtil> util = pendingForm()) {
         IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> h.service().setSelection("tok", user(), "F", paths("b"), null, "desc", true,
                                           null, "x", ""));

         assertTrue(e.getMessage().contains(REFUSAL), e.getMessage());
         // sort, selection style, search and apply are all writes: none may have happened
         verifyNoInteractions(h.selections());
      }
   }

   @Test
   void setSelectionRefusesAnyValuesThatDifferFromTheHeldSelection() {
      SelectionRuntimeServiceTest.Harness h =
         SelectionRuntimeServiceTest.harness(list(false, "a"));

      try(MockedStatic<FormUtil> util = pendingForm()) {
         assertThrows(IllegalArgumentException.class,
            () -> h.service().setSelection("tok", user(), "F", paths("a", "b"), null, null, null,
                                           null, ""));
         assertThrows(IllegalArgumentException.class,
            () -> h.service().setSelection("tok", user(), "F", paths("b"), null, null, null,
                                           null, ""));
         assertThrows(IllegalArgumentException.class,
            () -> h.service().setSelection("tok", user(), "F", paths(), null, null, null, null,
                                           ""));
         verifyNoInteractions(h.selections());
      }
   }

   @Test
   void setSelectionRefusesADeselectAndASingleSelectChange() {
      SelectionRuntimeServiceTest.Harness h =
         SelectionRuntimeServiceTest.harness(list(false, "a"));

      try(MockedStatic<FormUtil> util = pendingForm()) {
         assertThrows(IllegalArgumentException.class,
            () -> h.service().setSelection("tok", user(), "F", null, paths("a"), null, null, null,
                                           ""));
         assertThrows(IllegalArgumentException.class,
            () -> h.service().setSelection("tok", user(), "F", null, null, null, true, null, ""));
         verifyNoInteractions(h.selections());
      }
   }

   @Test
   void setSelectionDiscardPendingReachesTheApplyWithoutAsking() throws Exception {
      SelectionRuntimeServiceTest.Harness h =
         SelectionRuntimeServiceTest.harness(list(false, "a"));

      try(MockedStatic<FormUtil> util = pendingForm()) {
         h.service().setSelection("tok", user(), "F", paths("b"), null, null, null, null, null,
                                  true, "");

         verify(h.selections(), atLeastOnce()).applySelection(anyString(), eq("F"), any(),
                                                              any(Principal.class), any(),
                                                              anyString());
         util.verifyNoInteractions();
      }
   }

   @Test
   void setSelectionResendingTheHeldSelectionPassesAndStillApplies() throws Exception {
      SelectionRuntimeServiceTest.Harness h =
         SelectionRuntimeServiceTest.harness(list(false, "a", "c"));

      try(MockedStatic<FormUtil> util = pendingForm()) {
         h.service().setSelection("tok", user(), "F", paths("c", "a"), null, null, null, null, "");

         verify(h.selections()).applySelection(anyString(), eq("F"), any(), any(Principal.class),
                                               any(), anyString());
      }
   }

   @Test
   void setSelectionThatLeavesTheConditionAloneIsNotAsked() throws Exception {
      SelectionRuntimeServiceTest.Harness h =
         SelectionRuntimeServiceTest.harness(list(false, "a"));

      try(MockedStatic<FormUtil> util = pendingForm()) {
         h.service().setSelection("tok", user(), "F", null, null, "desc", null, null, "");

         util.verifyNoInteractions();
      }
   }

   @Test
   void setSelectionWithNothingPendingNeverRefuses() throws Exception {
      SelectionRuntimeServiceTest.Harness h =
         SelectionRuntimeServiceTest.harness(list(false, "a"));

      try(MockedStatic<FormUtil> util = mockStatic(FormUtil.class)) {
         util.when(() -> FormUtil.pendingDependentForms(any(), anyString())).thenReturn(List.of());

         h.service().setSelection("tok", user(), "F", paths("b"), null, null, null, null, "");

         verify(h.selections(), atLeastOnce()).applySelection(anyString(), eq("F"), any(),
                                                              any(Principal.class), any(),
                                                              anyString());
      }
   }

   // -- clearSelection --------------------------------------------------------

   @Test
   void clearSelectionRefusesAFilteringSelectionBeforeAnyWrite() {
      SelectionRuntimeServiceTest.Harness h =
         SelectionRuntimeServiceTest.harness(list(false, "a"));

      try(MockedStatic<FormUtil> util = pendingForm()) {
         IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> h.service().clearSelection("tok", user(), "F", ""));

         assertTrue(e.getMessage().contains(REFUSAL), e.getMessage());
         verifyNoInteractions(h.selections());
      }
   }

   @Test
   void clearSelectionDiscardPendingProceeds() throws Exception {
      SelectionRuntimeServiceTest.Harness h =
         SelectionRuntimeServiceTest.harness(list(false, "a"));

      try(MockedStatic<FormUtil> util = pendingForm()) {
         h.service().clearSelection("tok", user(), "F", true, "");

         verify(h.selections(), atLeastOnce()).applySelection(anyString(), eq("F"), any(),
                                                              any(Principal.class), any(),
                                                              anyString());
         util.verifyNoInteractions();
      }
   }

   @Test
   void clearSelectionOfAnUnfilteredSelectionPasses() {
      SelectionRuntimeServiceTest.Harness h =
         SelectionRuntimeServiceTest.harness(list(false));

      try(MockedStatic<FormUtil> util = pendingForm()) {
         assertDoesNotThrow(() -> h.service().clearSelection("tok", user(), "F", ""));
      }
   }

   // -- selectSubtree ---------------------------------------------------------

   private static SelectionRuntimeServiceTest.Harness treeHarness() {
      SelectionTreeVSAssembly tree = SelectionRuntimeServiceTest.tree(0, false);
      SelectionList domain = new SelectionList();
      CompositeSelectionValue east = new CompositeSelectionValue("East", "East");
      SelectionList children = new SelectionList();
      children.addSelectionValue(new SelectionValue("NY", "NY"));
      east.setSelectionList(children);
      domain.addSelectionValue(east);
      when(tree.getSelectionList()).thenReturn(domain);
      return SelectionRuntimeServiceTest.harness(tree);
   }

   @Test
   void selectSubtreeRefusesBeforeAnyWrite() {
      SelectionRuntimeServiceTest.Harness h = treeHarness();

      try(MockedStatic<FormUtil> util = pendingForm()) {
         IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> h.service().selectSubtree("tok", user(), "T", List.of("East"), "select", ""));

         assertTrue(e.getMessage().contains(REFUSAL), e.getMessage());
         verifyNoInteractions(h.selections());
      }
   }

   @Test
   void selectSubtreeDiscardPendingProceeds() throws Exception {
      SelectionRuntimeServiceTest.Harness h = treeHarness();

      try(MockedStatic<FormUtil> util = pendingForm()) {
         // the mocked selections service never marks East selected, so the post-apply check
         // throws; that is after the write, which is all this test needs to see
         try {
            h.service().selectSubtree("tok", user(), "T", List.of("East"), "select", true, "");
         }
         catch(Exception e) {
            assertFalse(String.valueOf(e.getMessage()).contains(REFUSAL), e.getMessage());
         }

         verify(h.selections()).selectSubtree(anyString(), eq("T"), any(), any(Principal.class),
                                              any(), anyString());
         util.verifyNoInteractions();
      }
   }

   // -- the no-op probes ------------------------------------------------------

   @Test
   void sameSelectionIsExactSetEqualityIgnoringOrder() {
      assertTrue(SelectionRuntimeService.sameSelection(paths("a", "b"), paths("b", "a")));
      assertFalse(SelectionRuntimeService.sameSelection(paths("a"), paths("a", "b")));
      assertFalse(SelectionRuntimeService.sameSelection(paths("a", "b"), paths("a")));
      assertFalse(SelectionRuntimeService.sameSelection(paths("a"), paths("b")));
      assertFalse(SelectionRuntimeService.sameSelection(paths("a"), List.of()));
      assertTrue(SelectionRuntimeService.sameSelection(List.of(), List.of()));
      assertFalse(SelectionRuntimeService.sameSelection(List.of(List.of("East", "NY")),
                                                        List.of(List.of("East"))));
   }

   @Test
   void isFilteringTracksTheSelectedValues() {
      assertTrue(SelectionRuntimeService.isFiltering(list(false, "a")));
      assertFalse(SelectionRuntimeService.isFiltering(list(false)));
   }

   @Test
   void isFilteringReadsACalendarsDates() {
      CalendarVSAssembly cal = mock(CalendarVSAssembly.class);
      CalendarVSAssemblyInfo info = mock(CalendarVSAssemblyInfo.class);
      when(cal.getVSAssemblyInfo()).thenReturn(info);

      when(info.getDates()).thenReturn(new String[] { "2026-01-01" });
      assertTrue(SelectionRuntimeService.isFiltering(cal));

      when(info.getDates()).thenReturn(new String[0]);
      assertFalse(SelectionRuntimeService.isFiltering(cal));

      when(info.getDates()).thenReturn(null);
      assertFalse(SelectionRuntimeService.isFiltering(cal));
   }

   @Test
   void isFilteringAssumesAnIdModeTreeIsFiltering() {
      SelectionTreeVSAssembly tree = SelectionRuntimeServiceTest.tree(0, false);
      when(tree.isIDMode()).thenReturn(true);

      assertTrue(SelectionRuntimeService.isFiltering(tree));
   }
}
