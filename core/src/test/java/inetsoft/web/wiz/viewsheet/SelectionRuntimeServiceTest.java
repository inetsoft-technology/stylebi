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
