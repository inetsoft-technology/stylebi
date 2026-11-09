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
import inetsoft.uql.viewsheet.internal.FormUtil;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Bug #80482: a selection write that changes the condition resets the Form tables on its table and
 * drops their pending edits, so it is refused unless the caller passes discardPending.
 */
@Tag("core")
class SelectionRuntimeServicePendingFormEditsTest {
   /** The shape the regression case (vwb-006) accepts as naming the pending loss. */
   private static final Pattern NAMES_PENDING_LOSS = Pattern.compile(
      "\\b(pending|uncommitted|unapplied|unsaved|not yet applied)\\b[^.]*\\b(edit|row|change)s?\\b",
      Pattern.CASE_INSENSITIVE);

   private final RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);

   @Test
   void refusesNamingTheFormTableAndThePendingEdits() {
      try(MockedStatic<FormUtil> util = mockStatic(FormUtil.class)) {
         util.when(() -> FormUtil.pendingDependentForms(rvs, "CategoryFilter"))
            .thenReturn(List.of("AdminTable"));

         IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> SelectionRuntimeService.refuseUnlessDiscardable(rvs, null, "this selection", null, "CategoryFilter"));

         assertTrue(e.getMessage().contains("AdminTable"), e.getMessage());
         assertTrue(NAMES_PENDING_LOSS.matcher(e.getMessage()).find(), e.getMessage());
         assertTrue(e.getMessage().contains("form_table_apply"), e.getMessage());
         assertTrue(e.getMessage().contains("discardPending: true"), e.getMessage());
      }
   }

   @Test
   void discardPendingLetsTheWriteThroughWithoutLookingAtTheSheet() {
      try(MockedStatic<FormUtil> util = mockStatic(FormUtil.class)) {
         SelectionRuntimeService.refuseUnlessDiscardable(rvs, true, "this selection", null, "F");

         util.verifyNoInteractions();
      }
   }

   @Test
   void explicitFalseStillRefuses() {
      try(MockedStatic<FormUtil> util = mockStatic(FormUtil.class)) {
         util.when(() -> FormUtil.pendingDependentForms(any(), eq("F")))
            .thenReturn(List.of("AdminTable"));

         assertThrows(IllegalArgumentException.class,
            () -> SelectionRuntimeService.refuseUnlessDiscardable(rvs, false, "x", null, "F"));
      }
   }

   @Test
   void passesWhenNoDependentFormHasPendingEdits() {
      try(MockedStatic<FormUtil> util = mockStatic(FormUtil.class)) {
         util.when(() -> FormUtil.pendingDependentForms(any(), any())).thenReturn(List.of());

         assertDoesNotThrow(
            () -> SelectionRuntimeService.refuseUnlessDiscardable(rvs, null, "x", null, "F", "G"));
      }
   }

   @Test
   void anUnchangedConditionPassesOnlyWhenSomethingIsPending() {
      try(MockedStatic<FormUtil> util = mockStatic(FormUtil.class)) {
         util.when(() -> FormUtil.pendingDependentForms(any(), eq("F")))
            .thenReturn(List.of("AdminTable"));

         assertDoesNotThrow(() -> SelectionRuntimeService.refuseUnlessDiscardable(
            rvs, null, "x", () -> true, "F"));
         assertThrows(IllegalArgumentException.class,
            () -> SelectionRuntimeService.refuseUnlessDiscardable(
               rvs, null, "x", () -> false, "F"));
      }
   }

   @Test
   void theUnchangedProbeIsNotAskedWhenNothingIsPending() {
      try(MockedStatic<FormUtil> util = mockStatic(FormUtil.class)) {
         util.when(() -> FormUtil.pendingDependentForms(any(), any())).thenReturn(List.of());

         assertDoesNotThrow(() -> SelectionRuntimeService.refuseUnlessDiscardable(
            rvs, null, "x", () -> { throw new AssertionError("probe must stay lazy"); }, "F"));
      }
   }

   @Test
   void namesEachFormOnceAcrossSeveralFilters() {
      try(MockedStatic<FormUtil> util = mockStatic(FormUtil.class)) {
         util.when(() -> FormUtil.pendingDependentForms(any(), eq("F")))
            .thenReturn(List.of("A", "B"));
         util.when(() -> FormUtil.pendingDependentForms(any(), eq("G")))
            .thenReturn(List.of("B"));

         IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> SelectionRuntimeService.refuseUnlessDiscardable(rvs, null, "x", null, "F", "G"));

         assertTrue(e.getMessage().contains("A, B."), e.getMessage());
      }
   }
}
