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
import inetsoft.uql.viewsheet.SelectionListVSAssembly;
import inetsoft.uql.viewsheet.SelectionTreeVSAssembly;
import inetsoft.uql.viewsheet.SelectionVSAssembly;
import inetsoft.uql.viewsheet.SelectionValue;
import inetsoft.uql.viewsheet.TimeSliderVSAssembly;
import inetsoft.uql.viewsheet.internal.CalendarVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.SelectionTreeVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TimeSliderVSAssemblyInfo;
import inetsoft.web.viewsheet.event.ApplySelectionListEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
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
    * The refute's required case, driven end-to-end: an ID-mode tree and a path of two REAL ids
    * under the wrong parent ("W" does not hold "NY"). set_selection's ID-mode set semantics would
    * accept it (both ids exist); {@code selectSubtree} is positional and must refuse it. If it
    * ever threaded {@code tree.isIDMode()} through to the set check instead of hardcoding
    * {@code idMode=false}, this would wrongly pass instead of throwing.
    */
   @Test
   void selectRefusesRealIdsUnderTheWrongParentInAnIdModeTree() {
      SelectionTreeVSAssembly assembly = SelectionRuntimeServiceTest.tree(0, false);
      when(assembly.isIDMode()).thenReturn(true);
      when(assembly.getSelectionList()).thenReturn(idModeRegionDomain());
      SelectionRuntimeServiceTest.Harness h = SelectionRuntimeServiceTest.harness(assembly);
      Principal user = SelectionRuntimeServiceTest.principal();

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service().selectSubtree("tok", user, "Tree1", List.of("W", "NY"), "select", ""));

      assertTrue(e.getMessage().contains("has no subtree at"), e.getMessage());
      verifyNoInteractions(h.selections());
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

   // ── set_selection replace on an ID-mode tree (bug-76926) ───────────────────

   /** A{A1 selected} unselected, B selected: the shape an ID-mode select leaves behind. */
   private static SelectionList idModeDomainWithSelectedChildUnderUnselectedRoot() {
      SelectionList domain = new SelectionList();
      CompositeSelectionValue a = new CompositeSelectionValue("A", "A");
      SelectionList children = new SelectionList();
      SelectionValue a1 = new SelectionValue("A1", "A1");
      a1.setSelected(true);
      children.addSelectionValue(a1);
      a.setSelectionList(children);
      domain.addSelectionValue(a);
      SelectionValue b = new SelectionValue("B", "B");
      b.setSelected(true);
      domain.addSelectionValue(b);
      return domain;
   }

   @Test
   void idModeSetSelectionDeselectsAChildUnderAnUnselectedRootInTheSameApply() throws Exception {
      SelectionTreeVSAssembly assembly = SelectionRuntimeServiceTest.tree(0, false);
      when(assembly.isIDMode()).thenReturn(true);
      when(assembly.getSelectionList())
         .thenReturn(idModeDomainWithSelectedChildUnderUnselectedRoot());
      SelectionRuntimeServiceTest.Harness h = SelectionRuntimeServiceTest.harness(assembly);

      h.service().setSelection("tok", SelectionRuntimeServiceTest.principal(), "Tree1",
                               List.of(List.of("B")), null, null, null, null, "");

      ArgumentCaptor<ApplySelectionListEvent> sent =
         ArgumentCaptor.forClass(ApplySelectionListEvent.class);
      verify(h.selections()).applySelection(anyString(), anyString(), sent.capture(),
                                            any(Principal.class), any(), anyString());
      ApplySelectionListEvent event = sent.getValue();
      assertEquals(List.of("A1"), SelectionRuntimeServiceTest.deselectedIds(event));
      assertEquals(2, event.getValues().size(), "requested B plus the one leftover");
      assertTrue(event.getValues().get(0).isSelected());
      assertEquals("B", event.getValues().get(0).getValue()[0]);
   }

   // ── search-hidden selections (bug-78216) ───────────────────────────────────

   /** {@code East{NY}} (both selected, like a fixed-hierarchy select) and a selected top-level Beta. */
   private static SelectionList eastNyAndBeta() {
      SelectionList domain = new SelectionList();
      CompositeSelectionValue east = new CompositeSelectionValue("East", "East");
      east.setSelected(true);
      SelectionList children = new SelectionList();
      SelectionValue ny = new SelectionValue("NY", "NY");
      ny.setSelected(true);
      ny.setLevel(1);
      children.addSelectionValue(ny);
      east.setSelectionList(children);
      domain.addSelectionValue(east);
      CompositeSelectionValue beta = new CompositeSelectionValue("Beta", "Beta");
      beta.setSelected(true);
      domain.addSelectionValue(beta);
      return domain;
   }

   private static SelectionTreeVSAssembly searchedTree(boolean idMode, SelectionList domain,
                                                       String search)
   {
      SelectionTreeVSAssembly assembly = SelectionRuntimeServiceTest.tree(0, false);
      when(assembly.isIDMode()).thenReturn(idMode);
      when(assembly.getSelectionList()).thenReturn(domain);
      when(((SelectionTreeVSAssemblyInfo) assembly.getInfo()).getSearchString()).thenReturn(search);
      return assembly;
   }

   @Test
   void selectedTotalCountsLeafPathsOnANonIdTree() {
      SelectionValue[] tree = eastNyAndBeta().getSelectionValues();

      assertEquals(2, SelectionRuntimeService.selectedTotal(tree, true), "NY and Beta");
      assertEquals(3, SelectionRuntimeService.selectedTotal(tree, false), "nodes, ancestor too");
   }

   @Test
   void retainedSelectionTreatsAPrefixOfARequestedPathAsRequested() {
      SelectionValue[] tree = eastNyAndBeta().getSelectionValues();

      assertEquals(List.of(List.of("Beta")),
                   SelectionRuntimeService.retainedSelection(
                      tree, List.of(List.of("East", "NY")), false));
   }

   /** The merge-back leaves East/NY selected (the mock apply changes nothing): disclose it. */
   @Test
   void setSelectionUnderASearchReportsTheTrueTotalAndTheRetainedValue() throws Exception {
      SelectionTreeVSAssembly assembly = searchedTree(false, eastNyAndBeta(), "Beta");
      SelectionRuntimeServiceTest.Harness h = SelectionRuntimeServiceTest.harness(assembly);

      Map<String, Object> result = h.service().setSelection(
         "tok", SelectionRuntimeServiceTest.principal(), "Tree1", List.of(List.of("Beta")), null,
         null, null, null, "");

      assertEquals(1, result.get("valuesSelected"));
      assertEquals(2, result.get("selectedTotal"));
      assertEquals(List.of(List.of("East", "NY")), result.get("scopedBySearchRetained"));
      assertEquals("Beta", result.get("scopedBySearch"));
   }

   @Test
   void setSelectionUnderASearchOnAnIdTreeReportsTheRetainedNodeId() throws Exception {
      SelectionList domain = new SelectionList();
      SelectionValue alpha = new SelectionValue("Alpha", "Alpha");
      alpha.setSelected(true);
      SelectionValue beta = new SelectionValue("Beta", "Beta");
      beta.setSelected(true);
      domain.addSelectionValue(alpha);
      domain.addSelectionValue(beta);
      SelectionRuntimeServiceTest.Harness h =
         SelectionRuntimeServiceTest.harness(searchedTree(true, domain, "Beta"));

      Map<String, Object> result = h.service().setSelection(
         "tok", SelectionRuntimeServiceTest.principal(), "Tree1", List.of(List.of("Beta")), null,
         null, null, null, "");

      assertEquals(2, result.get("selectedTotal"));
      assertEquals(List.of(List.of("Alpha")), result.get("scopedBySearchRetained"));
      assertEquals("Beta", result.get("scopedBySearch"));
   }

   @Test
   void setSelectionReportsNoRetainedKeysWhenNothingStaysHidden() throws Exception {
      SelectionList domain = new SelectionList();
      SelectionValue beta = new SelectionValue("Beta", "Beta");
      beta.setSelected(true);
      domain.addSelectionValue(beta);
      SelectionRuntimeServiceTest.Harness h =
         SelectionRuntimeServiceTest.harness(searchedTree(true, domain, "Beta"));

      Map<String, Object> result = h.service().setSelection(
         "tok", SelectionRuntimeServiceTest.principal(), "Tree1", List.of(List.of("Beta")), null,
         null, null, null, "");

      assertEquals(1, result.get("selectedTotal"));
      assertFalse(result.containsKey("scopedBySearchRetained"));
      assertFalse(result.containsKey("scopedBySearch"));
   }

   /** A deselect the search merge-back undoes is retained, not counted as deselected. */
   @Test
   void deselectUnderASearchDoesNotCountAValueThatStaysSelected() throws Exception {
      SelectionRuntimeServiceTest.Harness h = SelectionRuntimeServiceTest.harness(
         searchedTree(false, eastNyAndBeta(), "Beta"));

      Map<String, Object> result = h.service().setSelection(
         "tok", SelectionRuntimeServiceTest.principal(), "Tree1", null,
         List.of(List.of("East", "NY")), null, null, null, "");

      assertEquals(0, result.get("deselected"));
      assertEquals(List.of(List.of("East", "NY")), result.get("scopedBySearchRetained"));
      assertEquals("Beta", result.get("scopedBySearch"));
   }

   /** Control: the apply really clears it (no merge-back), so it is deselected and not retained. */
   @Test
   void deselectThatSticksIsCountedAndNotRetained() throws Exception {
      SelectionList domain = eastNyAndBeta();
      SelectionRuntimeServiceTest.Harness h =
         SelectionRuntimeServiceTest.harness(searchedTree(false, domain, "Beta"));
      doAnswer(inv -> {
         CompositeSelectionValue east = (CompositeSelectionValue) domain.getSelectionValue(0);
         east.setSelected(false);
         east.getSelectionList().getSelectionValue(0).setSelected(false);
         return null;
      }).when(h.selections()).applySelection(anyString(), anyString(), any(),
                                             any(Principal.class), any(), anyString());

      Map<String, Object> result = h.service().setSelection(
         "tok", SelectionRuntimeServiceTest.principal(), "Tree1", null,
         List.of(List.of("East", "NY")), null, null, null, "");

      assertEquals(1, result.get("deselected"));
      assertFalse(result.containsKey("scopedBySearchRetained"));
   }

   // ── duplicate / overlapping entries (bug-78268) ───────────────────────────

   /** A path named twice in 'deselect' is one deselect: 1 removed, not 2. */
   @Test
   void deselectNamingTheSamePathTwiceCountsItOnce() throws Exception {
      SelectionList domain = eastNyAndBeta();
      SelectionRuntimeServiceTest.Harness h =
         SelectionRuntimeServiceTest.harness(searchedTree(false, domain, null));
      doAnswer(inv -> {
         domain.getSelectionValue(1).setSelected(false);
         return null;
      }).when(h.selections()).applySelection(anyString(), anyString(), any(),
                                             any(Principal.class), any(), anyString());

      Map<String, Object> result = h.service().setSelection(
         "tok", SelectionRuntimeServiceTest.principal(), "Tree1", null,
         List.of(List.of("Beta"), List.of("Beta")), null, null, null, "");

      assertEquals(1, result.get("deselected"));
      assertEquals(1, result.get("selectedTotal"));
   }

   /** [East] covers [East, NY]: one target, so one deselected, not 2. */
   @Test
   void deselectEntriesWhereOneCoversAnotherCountOnce() throws Exception {
      SelectionList domain = eastNyAndBeta();
      SelectionRuntimeServiceTest.Harness h =
         SelectionRuntimeServiceTest.harness(searchedTree(false, domain, null));
      doAnswer(inv -> {
         CompositeSelectionValue east = (CompositeSelectionValue) domain.getSelectionValue(0);
         east.setSelected(false);
         east.getSelectionList().getSelectionValue(0).setSelected(false);
         return null;
      }).when(h.selections()).applySelection(anyString(), anyString(), any(),
                                             any(Principal.class), any(), anyString());

      Map<String, Object> result = h.service().setSelection(
         "tok", SelectionRuntimeServiceTest.principal(), "Tree1", null,
         List.of(List.of("East"), List.of("East", "NY")), null, null, null, "");

      assertEquals(1, result.get("deselected"));
   }

   /** A value named twice in 'values' is one value. */
   @Test
   void valuesNamingTheSamePathTwiceReportsOne() throws Exception {
      SelectionRuntimeServiceTest.Harness h =
         SelectionRuntimeServiceTest.harness(searchedTree(false, eastNyAndBeta(), null));

      Map<String, Object> result = h.service().setSelection(
         "tok", SelectionRuntimeServiceTest.principal(), "Tree1",
         List.of(List.of("Beta"), List.of("Beta")), null, null, null, null, "");

      assertEquals(1, result.get("valuesSelected"));
   }

   /** Partly merged-back covering entry: only LA is really removed, so deselected is 1. */
   @Test
   void deselectOfACoveringEntryUnderASearchCountsOnlyWhatWasRemoved() throws Exception {
      SelectionList domain = new SelectionList();
      CompositeSelectionValue east = new CompositeSelectionValue("East", "East");
      east.setSelected(true);
      SelectionList children = new SelectionList();
      SelectionValue ny = new SelectionValue("NY", "NY");
      ny.setSelected(true);
      ny.setLevel(1);
      SelectionValue la = new SelectionValue("LA", "LA");
      la.setSelected(true);
      la.setLevel(1);
      children.addSelectionValue(ny);
      children.addSelectionValue(la);
      east.setSelectionList(children);
      domain.addSelectionValue(east);
      SelectionRuntimeServiceTest.Harness h =
         SelectionRuntimeServiceTest.harness(searchedTree(false, domain, "LA"));
      doAnswer(inv -> {
         la.setSelected(false);
         return null;
      }).when(h.selections()).applySelection(anyString(), anyString(), any(),
                                             any(Principal.class), any(), anyString());

      Map<String, Object> result = h.service().setSelection(
         "tok", SelectionRuntimeServiceTest.principal(), "Tree1", null,
         List.of(List.of("East"), List.of("East", "LA")), null, null, null, "");

      assertEquals(1, result.get("deselected"));
      assertEquals(1, result.get("selectedTotal"));
   }

   /** The reported shape: a flat list, deselect [A, A] after [A, B] removes one value. */
   @Test
   void flatListDeselectNamingAValueTwiceCountsItOnce() throws Exception {
      SelectionList domain = new SelectionList();
      SelectionValue a = new SelectionValue("A", "A");
      a.setSelected(true);
      SelectionValue b = new SelectionValue("B", "B");
      b.setSelected(true);
      domain.addSelectionValue(a);
      domain.addSelectionValue(b);
      inetsoft.uql.viewsheet.SelectionListVSAssembly list =
         SelectionRuntimeServiceTest.list(inetsoft.uql.XConstants.SORT_ASC, false, null);
      when(list.getSelectionList()).thenReturn(domain);
      SelectionRuntimeServiceTest.Harness h = SelectionRuntimeServiceTest.harness(list);
      doAnswer(inv -> {
         a.setSelected(false);
         return null;
      }).when(h.selections()).applySelection(anyString(), anyString(), any(),
                                             any(Principal.class), any(), anyString());

      Map<String, Object> result = h.service().setSelection(
         "tok", SelectionRuntimeServiceTest.principal(), "Filter1", null,
         List.of(List.of("A"), List.of("A")), null, null, null, "");

      assertEquals(1, result.get("deselected"));
      assertEquals(1, result.get("selectedTotal"));
   }

   // ── clear_selection under a search (bug-78294) ─────────────────────────────

   private static SelectionList alphaBeta() {
      SelectionList domain = new SelectionList();
      SelectionValue alpha = new SelectionValue("Alpha", "Alpha");
      alpha.setSelected(true);
      SelectionValue beta = new SelectionValue("Beta", "Beta");
      beta.setSelected(true);
      domain.addSelectionValue(alpha);
      domain.addSelectionValue(beta);
      return domain;
   }

   /** The merge-back clears only what the search shows: Beta goes, Alpha is put back. */
   private static void clearOnlyBeta(SelectionRuntimeServiceTest.Harness h, SelectionList domain) {
      try {
         doAnswer(inv -> {
            domain.getSelectionValue(1).setSelected(false);
            return null;
         }).when(h.selections()).applySelection(anyString(), anyString(), any(),
                                                any(Principal.class), any(), anyString());
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }
   }

   @Test
   void clearSelectionOnAnIdTreeUnderASearchReportsWhatActuallyCleared() throws Exception {
      SelectionList domain = alphaBeta();
      SelectionRuntimeServiceTest.Harness h =
         SelectionRuntimeServiceTest.harness(searchedTree(true, domain, "Beta"));
      clearOnlyBeta(h, domain);

      Map<String, Object> result = h.service().clearSelection(
         "tok", SelectionRuntimeServiceTest.principal(), "Tree1", "");

      assertEquals(1, result.get("clearedCount"));
      assertEquals(1, result.get("selectedTotal"));
      assertEquals(List.of(List.of("Alpha")), result.get("scopedBySearchRetained"));
      assertEquals("Beta", result.get("scopedBySearch"));
   }

   @Test
   void clearSelectionOnAnIdTreeReportsNothingClearedWhenEverythingIsPutBack() throws Exception {
      SelectionRuntimeServiceTest.Harness h =
         SelectionRuntimeServiceTest.harness(searchedTree(true, alphaBeta(), "Beta"));

      Map<String, Object> result = h.service().clearSelection(
         "tok", SelectionRuntimeServiceTest.principal(), "Tree1", "");

      assertEquals(0, result.get("clearedCount"));
      assertEquals(2, result.get("selectedTotal"));
   }

   @Test
   void clearSelectionOnANonIdTreeUnderASearchReportsWhatActuallyCleared() throws Exception {
      SelectionList domain = eastNyAndBeta();
      SelectionRuntimeServiceTest.Harness h =
         SelectionRuntimeServiceTest.harness(searchedTree(false, domain, "Beta"));
      doAnswer(inv -> {
         domain.getSelectionValue(1).setSelected(false);
         return null;
      }).when(h.selections()).applySelection(anyString(), anyString(), any(),
                                             any(Principal.class), any(), anyString());

      Map<String, Object> result = h.service().clearSelection(
         "tok", SelectionRuntimeServiceTest.principal(), "Tree1", "");

      assertEquals(1, result.get("clearedCount"), "Beta cleared, East/NY put back");
      assertEquals(1, result.get("selectedTotal"));
      assertEquals(List.of(List.of("East", "NY")), result.get("scopedBySearchRetained"));
      assertEquals("Beta", result.get("scopedBySearch"));
   }

   @Test
   void clearSelectionOnAMultiSelectListUnderASearchReportsWhatActuallyCleared() throws Exception {
      SelectionList domain = alphaBeta();
      SelectionListVSAssembly assembly =
         SelectionRuntimeServiceTest.list(0, false, "Beta");
      when(assembly.getSelectionList()).thenReturn(domain);
      SelectionRuntimeServiceTest.Harness h = SelectionRuntimeServiceTest.harness(assembly);
      clearOnlyBeta(h, domain);

      Map<String, Object> result = h.service().clearSelection(
         "tok", SelectionRuntimeServiceTest.principal(), "Filter1", "");

      assertEquals(1, result.get("clearedCount"));
      assertEquals(1, result.get("selectedTotal"));
      assertEquals(List.of(List.of("Alpha")), result.get("scopedBySearchRetained"));
   }

   /** Nothing retained: the same keys as before, plus only the additive selectedTotal of 0. */
   @Test
   void clearSelectionThatClearsEverythingReportsNoRetainedKeys() throws Exception {
      SelectionList domain = alphaBeta();
      SelectionRuntimeServiceTest.Harness h =
         SelectionRuntimeServiceTest.harness(searchedTree(true, domain, null));
      doAnswer(inv -> {
         domain.getSelectionValue(0).setSelected(false);
         domain.getSelectionValue(1).setSelected(false);
         return null;
      }).when(h.selections()).applySelection(anyString(), anyString(), any(),
                                             any(Principal.class), any(), anyString());

      Map<String, Object> result = h.service().clearSelection(
         "tok", SelectionRuntimeServiceTest.principal(), "Tree1", "");

      assertEquals(2, result.get("clearedCount"));
      assertEquals(0, result.get("selectedTotal"));
      assertFalse(result.containsKey("scopedBySearchRetained"));
      assertFalse(result.containsKey("scopedBySearch"));
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

   // ── over-long paths and fabricated ids (bug-78264 P1/P2, bug-78268 P1) ────────

   /** {@code West -> CA -> {SF, LA}}: the three-level Region/State/City shape of VFO-020. */
   private static SelectionList threeLevelDomain() {
      SelectionList domain = new SelectionList();
      CompositeSelectionValue west = new CompositeSelectionValue("West", "West");
      SelectionList states = new SelectionList();
      CompositeSelectionValue ca = new CompositeSelectionValue("CA", "CA");
      SelectionList cities = new SelectionList();
      cities.addSelectionValue(new SelectionValue("SF", "SF"));
      cities.addSelectionValue(new SelectionValue("LA", "LA"));
      ca.setSelectionList(cities);
      states.addSelectionValue(ca);
      west.setSelectionList(states);
      domain.addSelectionValue(west);
      return domain;
   }

   private static void assertRefusedNaming(Exception e, String segment) {
      assertTrue(e.getMessage().contains("'" + segment + "'"), e.getMessage());
   }

   @Test
   void setSelectionRefusesAnOverLongPathPastALeafOfARealThreeLevelTree() {
      SelectionTreeVSAssembly assembly = SelectionRuntimeServiceTest.tree(0, false);
      when(assembly.getSelectionList()).thenReturn(threeLevelDomain());
      SelectionRuntimeServiceTest.Harness h = SelectionRuntimeServiceTest.harness(assembly);
      Principal user = SelectionRuntimeServiceTest.principal();

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service().setSelection("tok", user, "Tree1",
                                        List.of(List.of("West", "CA", "SF", "FAKE4")), null, null,
                                        null, null, ""));

      assertRefusedNaming(e, "FAKE4");
      verifyNoInteractions(h.selections());
   }

   @Test
   void setSelectionStillAcceptsTheExactThreeLevelPath() {
      SelectionTreeVSAssembly assembly = SelectionRuntimeServiceTest.tree(0, false);
      when(assembly.getSelectionList()).thenReturn(threeLevelDomain());
      SelectionRuntimeServiceTest.Harness h = SelectionRuntimeServiceTest.harness(assembly);

      assertDoesNotThrow(() -> h.service().setSelection(
         "tok", SelectionRuntimeServiceTest.principal(), "Tree1",
         List.of(List.of("West", "CA", "SF")), null, null, null, null, ""));
   }

   @Test
   void selectSubtreeRefusesASegmentPastALeaf() {
      SelectionTreeVSAssembly assembly = SelectionRuntimeServiceTest.tree(0, false);
      when(assembly.getSelectionList()).thenReturn(plainDomain());
      SelectionRuntimeServiceTest.Harness h = SelectionRuntimeServiceTest.harness(assembly);
      Principal user = SelectionRuntimeServiceTest.principal();

      for(String mode : List.of("select", "clear")) {
         Exception e = assertThrows(IllegalArgumentException.class,
            () -> h.service().selectSubtree(
               "tok", user, "Tree1", List.of("East", "NY", "Fabricated"), mode, ""));

         assertRefusedNaming(e, "Fabricated");
      }

      verifyNoInteractions(h.selections());
   }

   @Test
   void setSelectionRefusesAMultiSegmentPathAgainstARealFlatList() {
      SelectionList domain = new SelectionList();
      domain.addSelectionValue(new SelectionValue("A", "A"));
      domain.addSelectionValue(new SelectionValue("B", "B"));
      var assembly = SelectionRuntimeServiceTest.list(0, false, null);
      when(assembly.getSelectionList()).thenReturn(domain);
      SelectionRuntimeServiceTest.Harness h = SelectionRuntimeServiceTest.harness(assembly);
      Principal user = SelectionRuntimeServiceTest.principal();

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service().setSelection("tok", user, "Filter1", List.of(List.of("A", "B")), null,
                                        null, null, null, ""));

      assertRefusedNaming(e, "B");
      verifyNoInteractions(h.selections());
   }

   /** {@code E -> {NY}, W -> {LA}}: node ids, ID-mode. */
   private static SelectionList idModeRegionDomain() {
      SelectionList domain = new SelectionList();
      CompositeSelectionValue e = new CompositeSelectionValue("E", "E");
      SelectionList eChildren = new SelectionList();
      eChildren.addSelectionValue(new SelectionValue("NY", "NY"));
      e.setSelectionList(eChildren);
      domain.addSelectionValue(e);
      CompositeSelectionValue w = new CompositeSelectionValue("W", "W");
      SelectionList wChildren = new SelectionList();
      wChildren.addSelectionValue(new SelectionValue("LA", "LA"));
      w.setSelectionList(wChildren);
      domain.addSelectionValue(w);
      return domain;
   }

   @Test
   void idModeSetSelectionRefusesARealIdPlusAFabricatedIdNamingTheFakeOne() {
      SelectionTreeVSAssembly assembly = SelectionRuntimeServiceTest.tree(0, false);
      when(assembly.isIDMode()).thenReturn(true);
      when(assembly.getSelectionList()).thenReturn(idModeRegionDomain());
      SelectionRuntimeServiceTest.Harness h = SelectionRuntimeServiceTest.harness(assembly);
      Principal user = SelectionRuntimeServiceTest.principal();

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service().setSelection("tok", user, "Tree1", List.of(List.of("NY", "FAKEID")),
                                        null, null, null, null, ""));

      assertRefusedNaming(e, "FAKEID");
      verifyNoInteractions(h.selections());
   }

   @Test
   void idModeSetSelectionStillAcceptsAWrongParentSetOfRealIds() {
      SelectionTreeVSAssembly assembly = SelectionRuntimeServiceTest.tree(0, false);
      when(assembly.isIDMode()).thenReturn(true);
      when(assembly.getSelectionList()).thenReturn(idModeRegionDomain());
      SelectionRuntimeServiceTest.Harness h = SelectionRuntimeServiceTest.harness(assembly);

      assertDoesNotThrow(() -> h.service().setSelection(
         "tok", SelectionRuntimeServiceTest.principal(), "Tree1", List.of(List.of("W", "NY")),
         null, null, null, null, ""));
   }

   // ── range slider (bug-78264 P3) ───────────────────────────────────────────

   /** Buckets "10".."60" step 10, with the given indexes selected. */
   private static SelectionList sliderBuckets(int... selected) {
      SelectionList buckets = new SelectionList();

      for(int i = 0; i < 6; i++) {
         SelectionValue bucket = new SelectionValue(String.valueOf((i + 1) * 10),
                                                    String.valueOf((i + 1) * 10));

         for(int s : selected) {
            if(s == i) {
               bucket.setSelected(true);
            }
         }

         buckets.addSelectionValue(bucket);
      }

      return buckets;
   }

   /** A slider whose apply flips the bucket flags the way the TimeSlider branch of the apply does. */
   private static SelectionRuntimeServiceTest.Harness slider(SelectionList buckets) {
      TimeSliderVSAssembly slider = mock(TimeSliderVSAssembly.class);
      TimeSliderVSAssemblyInfo info = mock(TimeSliderVSAssemblyInfo.class);
      doReturn(info).when(slider).getInfo();
      when(slider.getSelectionList()).thenReturn(buckets);
      when(slider.isUpperInclusive()).thenReturn(false);
      SelectionRuntimeServiceTest.Harness h = SelectionRuntimeServiceTest.harness(slider);

      try {
         doAnswer(invocation -> {
            ApplySelectionListEvent event = invocation.getArgument(2);
            int start = event.getSelectStart();
            int end = event.getSelectEnd() - 1;
            SelectionValue[] values = buckets.getSelectionValues();

            for(int i = 0; i < values.length; i++) {
               values[i].setSelected(i >= start && i <= end);
            }

            return null;
         }).when(h.selections()).applySelection(anyString(), anyString(), any(),
                                                any(Principal.class), any(), anyString());
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      return h;
   }

   private static Map<String, Object> selectRange(SelectionRuntimeServiceTest.Harness h,
                                                  String from, String to)
      throws Exception
   {
      return h.service().setSelection(
         "tok", SelectionRuntimeServiceTest.principal(), "Slider1",
         List.of(List.of(from), List.of(to)), null, null, null, null, "");
   }

   @Test
   void clearSelectionOnANarrowedSliderReportsTheSelectedBucketsNotTheTotal() throws Exception {
      SelectionRuntimeServiceTest.Harness h = slider(sliderBuckets(1, 2));

      Map<String, Object> result = h.service().clearSelection(
         "tok", SelectionRuntimeServiceTest.principal(), "Slider1", "");

      assertEquals(2, result.get("clearedCount"));
   }

   @Test
   void clearSelectionOnAFullRangeSliderReportsNothingCleared() throws Exception {
      SelectionRuntimeServiceTest.Harness h = slider(sliderBuckets(0, 1, 2, 3, 4, 5));

      Map<String, Object> result = h.service().clearSelection(
         "tok", SelectionRuntimeServiceTest.principal(), "Slider1", "");

      assertEquals(0, result.get("clearedCount"));
   }

   @Test
   void setSelectionOnASliderReportsTheRangeBeforeAndAfterAndTheBucketCount() throws Exception {
      // Narrowed before, whole range after.
      Map<String, Object> result = selectRange(slider(sliderBuckets(1, 2)), "10", "60");

      assertEquals(Boolean.FALSE, result.get("rangeWasFull"));
      assertEquals(Boolean.TRUE, result.get("fullRange"));
      assertEquals(6, result.get("selectedBuckets"));

      // Whole range before and after.
      SelectionRuntimeServiceTest.Harness h = slider(sliderBuckets(0, 1, 2, 3, 4, 5));
      result = selectRange(h, "10", "60");

      assertEquals(Boolean.TRUE, result.get("rangeWasFull"));
      assertEquals(Boolean.TRUE, result.get("fullRange"));

      // Whole range before, narrowed after: 20..30 is two buckets.
      h = slider(sliderBuckets(0, 1, 2, 3, 4, 5));
      result = selectRange(h, "20", "30");

      assertEquals(Boolean.TRUE, result.get("rangeWasFull"));
      assertEquals(Boolean.FALSE, result.get("fullRange"));
      assertEquals(2, result.get("selectedBuckets"));
   }

   // -- deselect path validation (bug 78565) --------------------------------------------------

   private Exception deselectRefusal(SelectionVSAssembly assembly, String name, List<String> path) {
      SelectionRuntimeServiceTest.Harness h = SelectionRuntimeServiceTest.harness(assembly);
      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service().setSelection("tok", SelectionRuntimeServiceTest.principal(), name, null,
                                        List.of(path), null, null, null, ""));
      verifyNoInteractions(h.selections());
      return e;
   }

   private static SelectionTreeVSAssembly plainTree() {
      SelectionTreeVSAssembly assembly = SelectionRuntimeServiceTest.tree(0, false);
      when(assembly.getSelectionList()).thenReturn(plainDomain());
      return assembly;
   }

   /** A path that runs past a real leaf is refused by naming the extra segment. */
   @Test
   void deselectRefusesAnOverLongPath() {
      Exception e = deselectRefusal(plainTree(), "Tree1", List.of("East", "NY", "Fabricated"));

      assertTrue(e.getMessage().contains("not found: 'Fabricated'"), e.getMessage());
      assertTrue(e.getMessage().contains("browse_condition_values"), e.getMessage());
   }

   @Test
   void deselectRefusesAFabricatedTopLevelSegment() {
      Exception e = deselectRefusal(plainTree(), "Tree1", List.of("Nowhere"));

      assertTrue(e.getMessage().contains("not found: 'Nowhere'"), e.getMessage());
   }

   @Test
   void deselectRefusesAFabricatedMidPathSegment() {
      Exception e = deselectRefusal(plainTree(), "Tree1", List.of("East", "Nowhere"));

      assertTrue(e.getMessage().contains("not found: 'Nowhere'"), e.getMessage());
   }

   @Test
   void deselectRefusesAFabricatedValueOnAList() {
      SelectionListVSAssembly assembly = SelectionRuntimeServiceTest.list(0, false, null);
      SelectionList domain = new SelectionList();
      domain.addSelectionValue(new SelectionValue("East", "East"));
      when(assembly.getSelectionList()).thenReturn(domain);

      Exception e = deselectRefusal(assembly, "Filter1", List.of("Nowhere"));

      assertTrue(e.getMessage().contains("not found: 'Nowhere'"), e.getMessage());
   }

   /** A real value that is simply not selected stays a legal no-op, as does a parent-only path. */
   @Test
   void deselectAcceptsARealButUnselectedPathAndAParentOnlyPath() throws Exception {
      SelectionRuntimeServiceTest.Harness h = SelectionRuntimeServiceTest.harness(plainTree());

      Map<String, Object> result = h.service().setSelection(
         "tok", SelectionRuntimeServiceTest.principal(), "Tree1", null,
         List.of(List.of("East", "NY"), List.of("East")), null, null, null, "");

      assertFalse(result.containsKey("deselected"));
      verifyNoInteractions(h.selections());
   }

   /** The refusal is not scoped by search: a real value the search hides is still resolvable. */
   @Test
   void deselectOfARealValueHiddenBySearchIsNotRefused() {
      SelectionRuntimeServiceTest.Harness h = SelectionRuntimeServiceTest.harness(
         searchedTree(false, eastNyAndBeta(), "Beta"));

      assertDoesNotThrow(() -> h.service().setSelection(
         "tok", SelectionRuntimeServiceTest.principal(), "Tree1", null,
         List.of(List.of("East", "NY")), null, null, null, ""));
   }
}
