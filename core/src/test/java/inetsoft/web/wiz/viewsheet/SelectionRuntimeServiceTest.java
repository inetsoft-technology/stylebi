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
import inetsoft.uql.XConstants;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.viewsheet.event.ApplySelectionListEvent;
import inetsoft.web.viewsheet.service.VSSelectionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The endpoints behind this service are toggles and cycles rather than setters, and they answer a bad
 * assembly with silence, an NPE or a CCE depending on which verb was called. Both are what these
 * tests are for.
 */
@Tag("core")
class SelectionRuntimeServiceTest {
   // ── the sort ring, which is the part most likely to be wrong ──────────────

   /**
    * {@code nextSortType} is {@code ASC → DESC → SPECIFIC → ASC}, and its {@code default} branch
    * sends anything unrecognised to {@code ASC}. Getting the distance wrong means silently landing
    * on a different order than the caller asked for, which no assertion on "did it call sort" would
    * catch.
    */
   @Test
   void computesTheSortDistanceAroundTheRing() {
      assertEquals(0, SelectionRuntimeService.sortCycles(XConstants.SORT_ASC, XConstants.SORT_ASC));
      assertEquals(1, SelectionRuntimeService.sortCycles(XConstants.SORT_ASC, XConstants.SORT_DESC));
      assertEquals(2, SelectionRuntimeService.sortCycles(XConstants.SORT_ASC,
                                                        XConstants.SORT_SPECIFIC));
      assertEquals(1, SelectionRuntimeService.sortCycles(XConstants.SORT_DESC,
                                                        XConstants.SORT_SPECIFIC));
      assertEquals(2, SelectionRuntimeService.sortCycles(XConstants.SORT_DESC,
                                                        XConstants.SORT_ASC));
      assertEquals(1, SelectionRuntimeService.sortCycles(XConstants.SORT_SPECIFIC,
                                                        XConstants.SORT_ASC));
   }

   /** An order already in place must cost zero calls, not a full lap. */
   @Test
   void doesNotTouchTheSortEndpointWhenTheOrderAlreadyMatches() throws Exception {
      Harness h = harness(list(XConstants.SORT_DESC, false, null));

      Map<String, Object> result =
         h.service.setSelection("tok", principal(), "Filter1", null, "desc", null, "");

      assertEquals(0, result.get("sortCycles"));
      verify(h.selections, never()).sortSelection(anyString(), anyString(), any(),
                                                  any(Principal.class), any(), anyString());
   }

   /** Two cycles, one undo checkpoint — mutate owns the checkpoint, not each endpoint call. */
   @Test
   void cyclesTwiceInsideASingleMutate() throws Exception {
      Harness h = harness(list(XConstants.SORT_ASC, false, null));

      h.service.setSelection("tok", principal(), "Filter1", null, "specific", null, "");

      verify(h.selections, times(2)).sortSelection(anyString(), anyString(), any(),
                                                   any(Principal.class), any(), anyString());
      verify(h.sessions, times(1)).mutate(anyString(), any(Principal.class), any());
   }

   /** A range slider follows its range; asking it to sort is a mistake worth naming. */
   @Test
   void refusesASortOrderOnARangeSlider() {
      TimeSliderVSAssembly slider = mock(TimeSliderVSAssembly.class);
      TimeSliderVSAssemblyInfo info = mock(TimeSliderVSAssemblyInfo.class);
      doReturn(info).when(slider).getInfo();
      Harness h = harness(slider);

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.setSelection("tok", principal(), "Slider1", null, "asc", null, ""));

      assertTrue(e.getMessage().contains("no sort order"), e.getMessage());
   }

   // ── the single-selection toggle ────────────────────────────────────────────

   /** The endpoint flips the flag, so a request matching the current state must do nothing. */
   @Test
   void togglesSelectionStyleOnlyWhenItDiffers() throws Exception {
      Harness already = harness(list(XConstants.SORT_ASC, true, null));

      already.service.setSelection("tok", principal(), "Filter1", null, null, true, "");

      verify(already.selections, never()).toggleSelectionStyle(anyString(), anyString(),
                                                               any(Principal.class), any(),
                                                               anyString());

      Harness differs = harness(list(XConstants.SORT_ASC, false, null));

      differs.service.setSelection("tok", principal(), "Filter1", null, null, true, "");

      verify(differs.selections, times(1)).toggleSelectionStyle(anyString(), anyString(),
                                                                any(Principal.class), any(),
                                                                anyString());
   }

   /** Multiple values into a single-select assembly is a refusal, not a silent truncation. */
   @Test
   void refusesMultipleValuesOnASingleSelectAssembly() {
      Harness h = harness(list(XConstants.SORT_ASC, true, null));

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.setSelection("tok", principal(), "Filter1",
                                      List.of(List.of("East"), List.of("West")), null, null, ""));

      assertTrue(e.getMessage().contains("single-select"), e.getMessage());
   }

   /**
    * The same call may switch to multi-select and pass several values; the switch is what makes the
    * values legal, so the guard has to read the requested style rather than the stored one.
    */
   @Test
   void allowsMultipleValuesWhenTheSameCallSwitchesToMultiSelect() throws Exception {
      Harness h = harness(list(XConstants.SORT_ASC, true, null));

      h.service.setSelection("tok", principal(), "Filter1",
                             List.of(List.of("East"), List.of("West")), null, false, "");

      verify(h.selections, times(1)).applySelection(anyString(), anyString(), any(),
                                                    any(Principal.class), any(), anyString());
   }

   // ── the value apply ───────────────────────────────────────────────────────

   /**
    * {@code toggle}/{@code toggleAll} on the apply event flip {@code singleSelection} instead of
    * applying values — a third route to that field. Leaving them false is what keeps a value apply
    * from silently changing the selection style.
    */
   @Test
   void sendsAPlainApplyThatCannotFlipTheSelectionStyle() throws Exception {
      Harness h = harness(list(XConstants.SORT_ASC, false, null));

      h.service.setSelection("tok", principal(), "Filter1", List.of(List.of("East")), null, null, "");

      ArgumentCaptor<ApplySelectionListEvent> sent =
         ArgumentCaptor.forClass(ApplySelectionListEvent.class);
      verify(h.selections).applySelection(anyString(), anyString(), sent.capture(),
                                          any(Principal.class), any(), anyString());

      ApplySelectionListEvent event = sent.getValue();
      assertEquals(ApplySelectionListEvent.Type.APPLY, event.getType());
      assertFalse(event.isToggle(), "toggle=true would flip singleSelection instead of applying");
      assertFalse(event.isToggleAll(), "toggleAll=true would flip singleSelection too");
      assertEquals(1, event.getValues().size());
      assertArrayEquals(new String[]{ "East" }, event.getValues().get(0).getValue());
      assertTrue(event.getValues().get(0).isSelected());
   }

   /** A tree value is a path, so the array carries the whole hierarchy rather than a leaf name. */
   @Test
   void sendsATreeValueAsAPath() throws Exception {
      Harness h = harness(tree(XConstants.SORT_ASC, false));

      h.service.setSelection("tok", principal(), "Tree1", List.of(List.of("East", "NY")), null,
                             null, "");

      ArgumentCaptor<ApplySelectionListEvent> sent =
         ArgumentCaptor.forClass(ApplySelectionListEvent.class);
      verify(h.selections).applySelection(anyString(), anyString(), sent.capture(),
                                          any(Principal.class), any(), anyString());

      assertArrayEquals(new String[]{ "East", "NY" }, sent.getValue().getValues().get(0).getValue());
   }

   /**
    * <b>An active search string narrows what the apply touches</b> —
    * {@code olist = olist.findAll(search, true)} runs first — so the result has to say so. It is not
    * a refusal: the apply is legitimate, it just did not land on the whole list.
    */
   @Test
   void disclosesThatASearchStringScopedTheApply() throws Exception {
      Harness h = harness(list(XConstants.SORT_ASC, false, "Eas"));

      Map<String, Object> result = h.service.setSelection(
         "tok", principal(), "Filter1", List.of(List.of("East")), null, null, "");

      assertEquals("Eas", result.get("scopedBySearch"));
   }

   /** No search string, no scoping claim — presence of the key is the signal. */
   @Test
   void omitsTheSearchDisclosureWhenThereIsNoSearch() throws Exception {
      Harness h = harness(list(XConstants.SORT_ASC, false, null));

      Map<String, Object> result = h.service.setSelection(
         "tok", principal(), "Filter1", List.of(List.of("East")), null, null, "");

      assertFalse(result.containsKey("scopedBySearch"));
   }

   /**
    * A multi-select assembly with nothing previously selected has nothing to diff away, so the
    * plain-apply behaviour above must be unchanged: exactly one {@code applySelection} call, not a
    * spurious empty deselect first.
    */
   @Test
   void sendsOnlyOneApplyWhenNothingWasPreviouslySelected() throws Exception {
      Harness h = harness(list(XConstants.SORT_ASC, false, null));

      h.service.setSelection("tok", principal(), "Filter1", List.of(List.of("West")), null, null, "");

      verify(h.selections, times(1)).applySelection(anyString(), anyString(), any(),
                                                    any(Principal.class), any(), anyString());
   }

   /**
    * Single-select already gets a full reset for free via {@code unselectChildren}, so the new
    * diff-and-deselect step must not also fire for it — that would be redundant at best and could
    * race the reset at worst.
    */
   @Test
   void skipsTheDiffStepForASingleSelectAssembly() throws Exception {
      SelectionListVSAssembly assembly = list(XConstants.SORT_ASC, true, null);
      Harness h = harness(assembly);

      h.service.setSelection("tok", principal(), "Filter1", List.of(List.of("West")), null, null, "");

      verify(assembly, never()).getSelectionList();
      verify(h.selections, times(1)).applySelection(anyString(), anyString(), any(),
                                                    any(Principal.class), any(), anyString());
   }

   /**
    * A range slider always fully overwrites its own selection per call (index range against every
    * value), so it must never be diffed — reading its current selection at all would be wasted work
    * and, unlike list/tree, {@code TimeSliderVSAssembly} was never in scope for this fix.
    */
   @Test
   void neverReadsCurrentSelectionForARangeSlider() throws Exception {
      TimeSliderVSAssembly slider = mock(TimeSliderVSAssembly.class);
      TimeSliderVSAssemblyInfo info = mock(TimeSliderVSAssemblyInfo.class);
      doReturn(info).when(slider).getInfo();
      Harness h = harness(slider);

      h.service.setSelection("tok", principal(), "Slider1", List.of(List.of("2")), null, null, "");

      verify(slider, never()).getSelectionList();
      verify(h.selections, times(1)).applySelection(anyString(), anyString(), any(),
                                                    any(Principal.class), any(), anyString());
   }

   // ── the diff-and-deselect step (bug-76548: set_selection accumulated instead of replacing) ────

   /**
    * The direct regression test for the reported symptom: a value selected by an earlier call and
    * absent from the new values has to come back as a deselect, or the two calls' selections merge
    * into a union instead of the second one replacing the first.
    */
   @Test
   void computesADeselectForAValueDroppedFromTheNewSelection() {
      List<List<String>> current = List.of(List.of("East"));
      List<List<String>> requested = List.of(List.of("West"));

      assertEquals(List.of(List.of("East")), SelectionRuntimeService.toDeselect(current, requested),
                  "East was selected by a prior call and is absent from the new values, so it has " +
                  "to be turned off explicitly -- doApplySelection's multi-select branch never " +
                  "clears anything on its own");
   }

   /** Re-selecting exactly the current selection must not churn a spurious deselect/reselect. */
   @Test
   void leavesNothingToDeselectWhenTheRequestedValuesAlreadyMatchCurrent() {
      List<List<String>> current = List.of(List.of("East"), List.of("West"));

      assertEquals(List.of(), SelectionRuntimeService.toDeselect(current, current));
   }

   /** The diff has to work on whole paths, not just leaf names, once a tree is involved. */
   @Test
   void computesTheDeselectDiffForNestedTreePaths() {
      List<List<String>> current = List.of(List.of("East", "NY"));
      List<List<String>> requested = List.of(List.of("East", "LA"));

      assertEquals(List.of(List.of("East", "NY")),
                  SelectionRuntimeService.toDeselect(current, requested));
   }

   // ── the diffable-assembly gate ───────────────────────────────────────────

   @Test
   void treatsAFlatSelectionListAsDiffable() {
      assertTrue(SelectionRuntimeService.isPathDiffable(list(XConstants.SORT_ASC, false, null)));
   }

   @Test
   void treatsANonIdModeSelectionTreeAsDiffable() {
      assertTrue(SelectionRuntimeService.isPathDiffable(tree(XConstants.SORT_ASC, false)));
   }

   /**
    * ID mode matches values by {@code Tool.contains} against the whole path array rather than the
    * depth-indexed walk {@code selectedPaths} produces paths for, so it is deliberately excluded
    * rather than assumed to work with the same diff.
    */
   @Test
   void excludesAnIdModeSelectionTreeFromTheDiff() {
      SelectionTreeVSAssembly idTree = tree(XConstants.SORT_ASC, false);
      when(idTree.isIDMode()).thenReturn(true);

      assertFalse(SelectionRuntimeService.isPathDiffable(idTree));
   }

   @Test
   void excludesARangeSliderFromTheDiff() {
      assertFalse(SelectionRuntimeService.isPathDiffable(mock(TimeSliderVSAssembly.class)));
   }

   // ── selectedPaths recursion into a SelectionTree ────────────────────────

   /**
    * A leaf's path has to be sized and positioned by its own level, not hard-coded to length one —
    * otherwise a value one level deep in a tree would be reported at the wrong position (or with the
    * wrong path length) once it reaches {@code updateSelectionOfChangedAssembly}'s depth-indexed
    * matching.
    */
   @Test
   void sizesALeafPathByItsOwnLevelRatherThanAlwaysLengthOne() {
      SelectionValue ny = mock(SelectionValue.class);
      when(ny.isSelected()).thenReturn(true);
      when(ny.getValue()).thenReturn("NY");
      when(ny.getLevel()).thenReturn(1);

      List<List<String>> paths = SelectionRuntimeService.selectedPaths(new SelectionValue[]{ ny });

      assertEquals(List.of(Arrays.asList(null, "NY")), paths,
                  "a value at level 1 belongs one segment deep -- a flat length-1 path would name " +
                  "the wrong node once fed back into a tree apply");
   }

   /**
    * Mirrors {@code VSSelectionService.findSelectedPaths}'s own empty-fallback rule: a composite
    * selected as a whole, with none of its own children individually selected, must still produce a
    * self-only path rather than vanishing from the result -- otherwise replacing a whole selected
    * parent node (a common real operation) would compute zero deselect paths, reproducing a bug of
    * the same shape as the one this fix addresses.
    */
   @Test
   void aSelectedCompositeWithNoSelectedChildrenStillProducesASelfOnlyPath() {
      CompositeSelectionValue east = mock(CompositeSelectionValue.class);
      when(east.isSelected()).thenReturn(true);
      when(east.getValue()).thenReturn("East");
      when(east.getLevel()).thenReturn(0);
      // getSelectionList() is left unstubbed (null): SelectionList cannot be constructed or mocked
      // outside a Spring context (its XSwappable supertype's static initialiser reaches for
      // SreeEnv), confirmed by executing this exact call in this test class -- Mockito throws
      // "Cannot instrument class inetsoft.uql.viewsheet.SelectionList because it or one of its
      // supertypes could not be initialized". A null list is the same "no selected child" shape the
      // production code sees when a real SelectionList reports zero selected entries, so this
      // exercises the same branch without needing a real container.

      List<List<String>> paths = SelectionRuntimeService.selectedPaths(new SelectionValue[]{ east });

      assertEquals(List.of(List.of("East")), paths);
   }

   // ── clear ─────────────────────────────────────────────────────────────────

   /**
    * Clearing has to send every currently selected value back with {@code selected=false} — that is
    * how the client composes "unselect", since there is no single clear endpoint for one assembly.
    *
    * <p>Asserted over the value array rather than through the service: {@code SelectionList} cannot be
    * constructed or mocked outside a Spring context, so the container is not testable here. This
    * covers the mapping, which is the part that can be wrong; the wiring is covered by the
    * nothing-selected case below.
    */
   @Test
   void mapsOnlySelectedValuesIntoPathsToDeselect() {
      SelectionValue east = mock(SelectionValue.class);
      when(east.isSelected()).thenReturn(true);
      when(east.getValue()).thenReturn("East");

      SelectionValue west = mock(SelectionValue.class);
      when(west.isSelected()).thenReturn(false);
      when(west.getValue()).thenReturn("West");

      SelectionValue nullValued = mock(SelectionValue.class);
      when(nullValued.isSelected()).thenReturn(true);
      when(nullValued.getValue()).thenReturn(null);

      List<List<String>> paths = SelectionRuntimeService.selectedPaths(
         new SelectionValue[]{ east, west, null, nullValued });

      assertEquals(List.of(List.of("East"), List.of("")), paths,
                   "unselected values must not be sent, and a null value must not NPE");
      assertEquals(List.of(), SelectionRuntimeService.selectedPaths(null));
   }

   /** Nothing selected means nothing to send, rather than an empty apply. */
   @Test
   void doesNotCallTheEndpointWhenThereIsNothingSelected() throws Exception {
      Harness h = harness(list(XConstants.SORT_ASC, false, null));

      Map<String, Object> result = h.service.clearSelection("tok", principal(), "Filter1", "");

      assertEquals(0, result.get("clearedCount"));
      verifyNoInteractions(h.selections);
   }

   // ── subtree ───────────────────────────────────────────────────────────────

   /** selectSubtree NPEs on a non-tree, so a list has to be refused before dispatch. */
   @Test
   void refusesASubtreeOnAListInsteadOfLettingItCrash() {
      Harness h = harness(list(XConstants.SORT_ASC, false, null));

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.selectSubtree("tok", principal(), "Filter1", List.of("East"), "select", ""));

      assertTrue(e.getMessage().contains("selection tree"), e.getMessage());
      verifyNoInteractions(h.selections);
   }

   @Test
   void requiresASubtreePath() {
      Harness h = harness(tree(XConstants.SORT_ASC, false));

      assertThrows(IllegalArgumentException.class,
         () -> h.service.selectSubtree("tok", principal(), "Tree1", List.of(), "select", ""));
   }

   @Test
   void acceptsClearAsASubtreeModeAndItsNaturalAliases() throws Exception {
      for(String mode : List.of("clear", "unselect", "deselect", "CLEAR")) {
         Harness h = harness(tree(XConstants.SORT_ASC, false));

         Map<String, Object> result = h.service.selectSubtree(
            "tok", principal(), "Tree1", List.of("East"), mode, "");

         assertEquals("clear", result.get("mode"), "mode '" + mode + "' should normalise");
      }
   }

   @Test
   void refusesAnUnknownSubtreeMode() {
      Harness h = harness(tree(XConstants.SORT_ASC, false));

      assertThrows(IllegalArgumentException.class,
         () -> h.service.selectSubtree("tok", principal(), "Tree1", List.of("East"), "toggle", ""));
   }

   // ── the shared guards ─────────────────────────────────────────────────────

   @Test
   void refusesAnUnknownAssemblyRatherThanSucceedingSilently() {
      Harness h = harness(null);

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.setSelection("tok", principal(), "Nope", List.of(List.of("x")), null,
                                      null, ""));

      assertTrue(e.getMessage().contains("Nope"), e.getMessage());
      verifyNoInteractions(h.selections);
   }

   @Test
   void refusesANonSelectionAssemblyNamingItsType() {
      Harness h = harness(mock(ChartVSAssembly.class));

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.setSelection("tok", principal(), "Chart1", List.of(List.of("x")), null,
                                      null, ""));

      assertTrue(e.getMessage().contains("not a selection assembly"), e.getMessage());
      verifyNoInteractions(h.selections);
   }

   @Test
   void refusesACallThatAsksForNothing() {
      Harness h = harness(list(XConstants.SORT_ASC, false, null));

      assertThrows(IllegalArgumentException.class,
         () -> h.service.setSelection("tok", principal(), "Filter1", null, null, null, ""));
   }

   @Test
   void alwaysReportsThatTheStatePersists() throws Exception {
      Harness h = harness(list(XConstants.SORT_ASC, false, null));

      Map<String, Object> result = h.service.setSelection(
         "tok", principal(), "Filter1", List.of(List.of("East")), null, null, "");

      assertEquals(true, result.get("persistsOnSave"),
                   "writeStateContent writes the selection on the save path, so a caller must know");
   }

   // ── fixtures ──────────────────────────────────────────────────────────────

   /** Infos are mocked: their real constructors need SreeEnv and the Spring context. */
   private static SelectionListVSAssembly list(int sortType, boolean single, String search,
                                               String... selected) {
      SelectionListVSAssembly assembly = mock(SelectionListVSAssembly.class);
      SelectionListVSAssemblyInfo info = mock(SelectionListVSAssemblyInfo.class);
      when(info.isSingleSelection()).thenReturn(single);
      when(info.getSortTypeValue()).thenReturn(sortType);
      when(info.getSearchString()).thenReturn(search);
      doReturn(info).when(assembly).getInfo();
      return assembly;
   }

   private static SelectionTreeVSAssembly tree(int sortType, boolean single) {
      SelectionTreeVSAssembly assembly = mock(SelectionTreeVSAssembly.class);
      SelectionTreeVSAssemblyInfo info = mock(SelectionTreeVSAssemblyInfo.class);
      when(info.isSingleSelection()).thenReturn(single);
      when(info.getSortTypeValue()).thenReturn(sortType);
      doReturn(info).when(assembly).getInfo();
      return assembly;
   }

   private record Harness(SelectionRuntimeService service, ViewsheetSessionService sessions,
                          VSSelectionService selections) {}

   private static Harness harness(VSAssembly assembly) {
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenReturn(assembly);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);

      try {
         doAnswer(invocation -> {
            ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
            mutation.run(rvs, "rt1", null);
            return null;
         }).when(sessions).mutate(anyString(), any(Principal.class), any());
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      VSSelectionService selections = mock(VSSelectionService.class);
      return new Harness(new SelectionRuntimeService(sessions, selections), sessions, selections);
   }

   private static Principal principal() {
      return mock(Principal.class);
   }
}
