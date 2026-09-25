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
import inetsoft.uql.viewsheet.CalendarVSAssembly;
import inetsoft.uql.viewsheet.CompositeSelectionValue;
import inetsoft.uql.viewsheet.SelectionList;
import inetsoft.uql.viewsheet.SelectionTreeVSAssembly;
import inetsoft.uql.viewsheet.SelectionValue;
import inetsoft.uql.viewsheet.internal.CalendarVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * bug-76544 (P3), end-to-end sibling of {@link SelectionRuntimeServiceTest}'s own
 * "select_subtree domain-existence validation" section.
 *
 * <p>Every test in that sibling class exercises {@code findUnmatchedPaths} directly, never
 * {@code SelectionRuntimeService.selectSubtree(...)} itself with a non-null domain -- because a
 * real {@code SelectionList} cannot be constructed OR mocked in a plain-Mockito test class:
 * {@code SelectionList extends XSwappable}, and {@code XSwappable}'s static initialiser eagerly
 * calls {@code SreeEnv.getProperty(...)}, which throws {@code ShutdownException: Spring
 * application context is not available} the moment the class is first touched -- confirmed live
 * with a throwaway probe (both {@code new SelectionList()} and {@code mock(SelectionList.class)}
 * failed identically, the latter also failing bytebuddy instrumentation for the same underlying
 * reason). That is a genuine blocker, not merely an assumed one.
 *
 * <p>It is not, however, an unfixable one: {@link inetsoft.uql.viewsheet.SelectionTreeVSAssemblyTest}
 * already boots a minimal Spring context specifically so it CAN construct real
 * {@code SelectionList}/{@code SelectionValue}/{@code CompositeSelectionValue} trees outside the
 * full server -- {@code @SreeHome} plus {@code BaseTestConfiguration}/{@code SwapperTestConfiguration}
 * wires up just enough of {@code ConfigurationContext} for {@code SreeEnv.getProperty} to resolve
 * instead of throwing. This class reuses that exact recipe, and reuses
 * {@link SelectionRuntimeServiceTest}'s own {@code tree(...)}/{@code harness(...)}/
 * {@code principal()} fixtures (now package-visible for that reuse, not duplicated here) to build
 * the rest of the call -- only the domain ({@code SelectionList}) needs to be real; the assembly,
 * session and selections collaborators stay ordinary Mockito mocks exactly as in the sibling
 * class, since none of those extend {@code XSwappable}.
 *
 * <p>This is what actually pins the production call site: that {@code selectSubtree} calls
 * {@code findUnmatchedPaths} with {@code idMode} hardcoded {@code false} (not threaded from
 * {@code tree.isIDMode()}), and that the guard block is genuinely present and reached, for both
 * {@code select} and {@code clear}, and for both a plain and an ID-mode tree -- none of which the
 * sibling class's own static-helper-only tests can prove, since they choose their own
 * {@code idMode} argument by hand rather than letting the production code choose it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SelectionRuntimeServiceSubtreeDomainTest {
   /** {@code East -> {NY, CT}}, the shape every non-ID-mode case below shares. */
   private static SelectionList plainDomain() {
      SelectionList domain = new SelectionList();
      CompositeSelectionValue east = new CompositeSelectionValue("East", "East");
      SelectionList eastChildren = new SelectionList();
      eastChildren.addSelectionValue(new SelectionValue("NY", "NY"));
      eastChildren.addSelectionValue(new SelectionValue("CT", "CT"));
      east.setSelectionList(eastChildren);
      domain.addSelectionValue(east);
      return domain;
   }

   /** A flat, single-leaf ID-mode domain: only "RealChild" exists, at the top level. */
   private static SelectionList idModeDomainWithOnlyRealChildAtTheTopLevel() {
      SelectionList domain = new SelectionList();
      domain.addSelectionValue(new SelectionValue("RealChild", "RealChild"));
      return domain;
   }

   /**
    * The reported gap, driven through {@code selectSubtree} itself rather than the static helper:
    * a path whose root segment names nothing in the live domain must now throw, for
    * {@code mode:"select"}.
    */
   @Test
   void selectRefusesAFabricatedPathAgainstARealDomain() {
      SelectionTreeVSAssembly assembly = SelectionRuntimeServiceTest.tree(0, false);
      when(assembly.getSelectionList()).thenReturn(plainDomain());
      SelectionRuntimeServiceTest.Harness h = SelectionRuntimeServiceTest.harness(assembly);
      Principal user = SelectionRuntimeServiceTest.principal();

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service().selectSubtree(
            "tok", user, "Tree1", List.of("Nonexistent"), "select", ""));

      assertTrue(e.getMessage().contains("has no subtree at"), e.getMessage());
   }

   /** Same fabricated path, {@code mode:"clear"} -- the check is placed before the mode branch. */
   @Test
   void clearRefusesAFabricatedPathAgainstARealDomain() {
      SelectionTreeVSAssembly assembly = SelectionRuntimeServiceTest.tree(0, false);
      when(assembly.getSelectionList()).thenReturn(plainDomain());
      SelectionRuntimeServiceTest.Harness h = SelectionRuntimeServiceTest.harness(assembly);
      Principal user = SelectionRuntimeServiceTest.principal();

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service().selectSubtree(
            "tok", user, "Tree1", List.of("Nonexistent"), "clear", ""));

      assertTrue(e.getMessage().contains("has no subtree at"), e.getMessage());
   }

   /**
    * The refute's required case, driven end-to-end: an ID-mode tree, a typo'd parent segment, and
    * a child segment ("RealChild") that genuinely exists in the domain -- just not under that
    * parent. If {@code selectSubtree} ever silently reverted to threading {@code tree.isIDMode()}
    * through to {@code matchesAnywhere} (the diagnosis's original, refute-rejected proposal)
    * instead of hardcoding {@code idMode=false}, this would wrongly pass instead of throwing.
    */
   @Test
   void selectRefusesATypoedParentInAnIdModeTreeEvenWhenTheChildExistsElsewhere() {
      SelectionTreeVSAssembly assembly = SelectionRuntimeServiceTest.tree(0, false);
      when(assembly.isIDMode()).thenReturn(true);
      when(assembly.getSelectionList()).thenReturn(idModeDomainWithOnlyRealChildAtTheTopLevel());
      SelectionRuntimeServiceTest.Harness h = SelectionRuntimeServiceTest.harness(assembly);
      Principal user = SelectionRuntimeServiceTest.principal();

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service().selectSubtree(
            "tok", user, "Tree1", List.of("TypoParent", "RealChild"), "select", ""));

      assertTrue(e.getMessage().contains("has no subtree at"), e.getMessage());
   }

   /**
    * The refute's other required case: a valid prefix path (an ancestor, not a leaf) must NOT be
    * refused, and must actually reach the apply -- {@code selections.selectSubtree(...)} is
    * invoked, not just "no exception thrown".
    */
   @Test
   void selectAllowsAValidPrefixPathAndReachesTheApply() throws Exception {
      SelectionTreeVSAssembly assembly = SelectionRuntimeServiceTest.tree(0, false);
      when(assembly.getSelectionList()).thenReturn(plainDomain());
      SelectionRuntimeServiceTest.Harness h = SelectionRuntimeServiceTest.harness(assembly);
      Principal user = SelectionRuntimeServiceTest.principal();

      assertDoesNotThrow(() -> h.service().selectSubtree(
         "tok", user, "Tree1", List.of("East"), "select", ""));

      verify(h.selections()).selectSubtree(anyString(), anyString(), any(), any(Principal.class),
                                           any(), anyString());
   }

   // ── clear_selection on a calendar ──────────────────────────────────────────
   //
   // Here rather than in SelectionRuntimeServiceTest: CalendarVSAssemblyInfo can only be built
   // inside this class's Spring context.

   private static CalendarVSAssembly calendar(String... dates) {
      CalendarVSAssemblyInfo info = new CalendarVSAssemblyInfo();
      info.setDates(dates);
      CalendarVSAssembly assembly = mock(CalendarVSAssembly.class);
      doReturn(info).when(assembly).getInfo();
      doReturn(info).when(assembly).getVSAssemblyInfo();
      return assembly;
   }

   /**
    * A calendar keeps its selection as dates, not a SelectionList, so clear_selection's generic
    * branch always reported "nothing selected" and left the dates in place. It now clears the
    * way clear_calendar does.
    */
   @Test
   void clearSelectionClearsACalendarsDatesThroughClearCalendar() throws Exception {
      SelectionRuntimeServiceTest.Harness h =
         SelectionRuntimeServiceTest.harness(calendar("d2024-0-1", "d2024-0-2"));

      Map<String, Object> result = h.service().clearSelection(
         "tok", SelectionRuntimeServiceTest.principal(), "Calendar1", "");

      assertEquals(2, result.get("clearedCount"));
      verify(h.calendars()).clearCalendar(eq("rt1"), eq("Calendar1"), any(), any(), eq(""));
      verifyNoInteractions(h.selections());
   }

   @Test
   void clearSelectionDoesNotClearACalendarWithNoDates() throws Exception {
      SelectionRuntimeServiceTest.Harness h = SelectionRuntimeServiceTest.harness(calendar());

      Map<String, Object> result = h.service().clearSelection(
         "tok", SelectionRuntimeServiceTest.principal(), "Calendar1", "");

      assertEquals(0, result.get("clearedCount"));
      verifyNoInteractions(h.calendars());
      verifyNoInteractions(h.selections());
   }
}
