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
import inetsoft.uql.erm.DataRef;
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
         h.service.setSelection("tok", principal(), "Filter1", null, null, "desc", null, null, "");

      assertEquals(0, result.get("sortCycles"));
      verify(h.selections, never()).sortSelection(anyString(), anyString(), any(),
                                                  any(Principal.class), any(), anyString());
   }

   /** Two cycles, one undo checkpoint — mutate owns the checkpoint, not each endpoint call. */
   @Test
   void cyclesTwiceInsideASingleMutate() throws Exception {
      Harness h = harness(list(XConstants.SORT_ASC, false, null));

      h.service.setSelection("tok", principal(), "Filter1", null, null, "specific", null, null, "");

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
         () -> h.service.setSelection("tok", principal(), "Slider1", null, null, "asc", null, null,
                                      ""));

      assertTrue(e.getMessage().contains("no sort order"), e.getMessage());
   }

   // ── the single-selection toggle ────────────────────────────────────────────

   /** The endpoint flips the flag, so a request matching the current state must do nothing. */
   @Test
   void togglesSelectionStyleOnlyWhenItDiffers() throws Exception {
      Harness already = harness(list(XConstants.SORT_ASC, true, null));

      already.service.setSelection("tok", principal(), "Filter1", null, null, null, true, null, "");

      verify(already.selections, never()).toggleSelectionStyle(anyString(), anyString(),
                                                               any(Principal.class), any(),
                                                               anyString());

      Harness differs = harness(list(XConstants.SORT_ASC, false, null));

      differs.service.setSelection("tok", principal(), "Filter1", null, null, null, true, null, "");

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
                                      List.of(List.of("East"), List.of("West")), null, null, null,
                                      null, ""));

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
                             List.of(List.of("East"), List.of("West")), null, null, false, null, "");

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

      h.service.setSelection("tok", principal(), "Filter1", List.of(List.of("East")), null, null,
                             null, null, "");

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

      h.service.setSelection("tok", principal(), "Tree1", List.of(List.of("East", "NY")), null, null,
                             null, null, "");

      ArgumentCaptor<ApplySelectionListEvent> sent =
         ArgumentCaptor.forClass(ApplySelectionListEvent.class);
      verify(h.selections).applySelection(anyString(), anyString(), sent.capture(),
                                          any(Principal.class), any(), anyString());

      assertArrayEquals(new String[]{ "East", "NY" }, sent.getValue().getValues().get(0).getValue());
   }

   /**
    * <b>Superseded by bug-76854.</b> This used to assert {@code scopedBySearch} fired merely
    * because a search string happened to be active — true here even though "East" actually
    * matches "Eas" and nothing would ever have been dropped, which is exactly the gap that let a
    * value NOT matching the search land unfiltered (the reporter's repro). The disclosure now
    * requires knowing something was actually excluded, which needs a real domain; this harness
    * cannot supply one ({@code SelectionList} cannot be mocked or constructed outside a Spring
    * context — see {@link #aSelectedCompositeWithNoSelectedChildrenStillProducesASelfOnlyPath}),
    * so the safe behaviour here is the same "cannot tell" skip
    * {@link #skipsValidationWhenTheDomainIsntKnownYet} already exercises for unmatched-value
    * validation. The actual filtering logic is unit tested directly against
    * {@link SelectionRuntimeService#filterBySearch(SelectionValue[], List, boolean, String)} below.
    */
   @Test
   void omitsScopedBySearchWhenTheDomainIsntKnownYetEvenWithAnActiveSearch() throws Exception {
      Harness h = harness(list(XConstants.SORT_ASC, false, "Eas"));

      Map<String, Object> result = h.service.setSelection(
         "tok", principal(), "Filter1", List.of(List.of("East")), null, null, null, null, "");

      assertFalse(result.containsKey("scopedBySearch"));
   }

   /** No search string, no scoping claim — presence of the key is the signal. */
   @Test
   void omitsTheSearchDisclosureWhenThereIsNoSearch() throws Exception {
      Harness h = harness(list(XConstants.SORT_ASC, false, null));

      Map<String, Object> result = h.service.setSelection(
         "tok", principal(), "Filter1", List.of(List.of("East")), null, null, null, null, "");

      assertFalse(result.containsKey("scopedBySearch"));
   }

   // ── bug-76758 (VFL-003): setting a search string via the 'search' overload ────────────────────

   /** {@code search} has to land on the assembly before the search-scoped disclosure is computed. */
   @Test
   void setsTheSearchStringBeforeApplying() throws Exception {
      SelectionListVSAssembly assembly = list(XConstants.SORT_ASC, false, null);
      SelectionListVSAssemblyInfo info = (SelectionListVSAssemblyInfo) assembly.getInfo();
      Harness h = harness(assembly);

      h.service.setSelection("tok", principal(), "Filter1", null, null, null, null, null, "Sm", "");

      verify(info).setSearchString("Sm");
   }

   /** The response has to name what it set — nothing else in the result would say so. */
   @Test
   void reportsSearchSetInTheResult() throws Exception {
      Harness h = harness(list(XConstants.SORT_ASC, false, null));

      Map<String, Object> result = h.service.setSelection(
         "tok", principal(), "Filter1", null, null, null, null, null, "Sm", "");

      assertEquals("Sm", result.get("searchSet"));
   }

   /**
    * <b>Superseded by bug-76854</b> the same way {@link #omitsScopedBySearchWhenTheDomainIsntKnownYetEvenWithAnActiveSearch}
    * is — {@code scopedBySearch} still reads {@code info.getSearchString()} live (a search set in
    * this same call still has to be visible to it, not just to {@code searchSet}), but firing the
    * disclosure now also needs to know something was dropped, which this domain-less harness can't
    * supply.
    */
   @Test
   void omitsScopedBySearchWhenSearchIsSetInTheSameCallButDomainIsntKnown() throws Exception {
      Harness h = harness(list(XConstants.SORT_ASC, false, "Sm"));

      Map<String, Object> result = h.service.setSelection(
         "tok", principal(), "Filter1", List.of(List.of("Smith")), null, null, null, null, "Sm",
         "");

      assertEquals("Sm", result.get("searchSet"));
      assertFalse(result.containsKey("scopedBySearch"));
   }

   /** A range slider has no search box -- the existing sortOrder refusal's counterpart. */
   @Test
   void refusesSearchOnARangeSlider() {
      TimeSliderVSAssembly slider = mock(TimeSliderVSAssembly.class);
      TimeSliderVSAssemblyInfo info = mock(TimeSliderVSAssemblyInfo.class);
      doReturn(info).when(slider).getInfo();
      Harness h = harness(slider);

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.setSelection("tok", principal(), "Slider1", null, null, null, null, null,
                                      "Sm", ""));

      assertTrue(e.getMessage().contains("no search box"), e.getMessage());
   }

   /** A search-only call never touches getConditionList()/getSelection() -- same exemption as sort. */
   @Test
   void allowsASearchOnlyCallOnAColumnlessSelectionList() throws Exception {
      SelectionListVSAssembly assembly = list(XConstants.SORT_ASC, false, null);
      when(assembly.getDataRef()).thenReturn(null);
      Harness h = harness(assembly);

      assertDoesNotThrow(() -> h.service.setSelection("tok", principal(), "Filter1", null, null,
         null, null, null, "Sm", ""));
   }

   /** The new overload's own "nothing to do" guard has one more field to check than the old one. */
   @Test
   void refusesACallThatAsksForNothingOnTheSearchOverloadToo() {
      Harness h = harness(list(XConstants.SORT_ASC, false, null));

      assertThrows(IllegalArgumentException.class,
         () -> h.service.setSelection("tok", principal(), "Filter1", null, null, null, null, null,
                                      null, ""));
   }

   /**
    * A multi-select assembly with nothing previously selected has nothing to diff away, so the
    * plain-apply behaviour above must be unchanged: exactly one {@code applySelection} call, not a
    * spurious empty deselect first.
    */
   @Test
   void sendsOnlyOneApplyWhenNothingWasPreviouslySelected() throws Exception {
      Harness h = harness(list(XConstants.SORT_ASC, false, null));

      h.service.setSelection("tok", principal(), "Filter1", List.of(List.of("West")), null, null,
                             null, null, "");

      verify(h.selections, times(1)).applySelection(anyString(), anyString(), any(),
                                                    any(Principal.class), any(), anyString());
   }

   /**
    * Single-select already gets a full reset for free via {@code unselectChildren}, so the new
    * diff-and-deselect step must not also fire for it — that would be redundant at best and could
    * race the reset at worst.
    *
    * <p>{@code getSelectionList()} is still called exactly once here — not zero times — because
    * value validation (bug-76544) reads the domain regardless of single-vs-multi select; a typo'd
    * value can be silently dropped on a single-select assembly the same way it can on a multi-select
    * one. What this test actually guards is that no second {@code applySelection} (the deselect)
    * fires.
    */
   @Test
   void skipsTheDiffStepForASingleSelectAssembly() throws Exception {
      SelectionListVSAssembly assembly = list(XConstants.SORT_ASC, true, null);
      Harness h = harness(assembly);

      h.service.setSelection("tok", principal(), "Filter1", List.of(List.of("West")), null, null,
                             null, null, "");

      verify(assembly, times(1)).getSelectionList();
      verify(h.selections, times(1)).applySelection(anyString(), anyString(), any(),
                                                    any(Principal.class), any(), anyString());
   }

   /**
    * A range slider always fully overwrites its own selection per call (index range against every
    * value), so it must never be <i>diffed</i> like a list/tree — that invariant is unaffected by
    * VFO-009 below and still holds via {@link #excludesARangeSliderFromTheDiff}.
    *
    * <p><b>Superseded by VFO-009 (2026-09-10):</b> this used to also assert
    * {@code verify(slider, never()).getSelectionList()} — true under the old (buggy) plain
    * value-array apply, which never looked at the slider's own bucket list at all and so never
    * actually filtered anything (see the range-slider section below). Resolving requested values
    * into a bucket-index range genuinely needs that list, so a slider now legitimately reads it.
    */
   @Test
   void throwsWhenARangeSliderHasNoBucketsToSelectFrom() throws Exception {
      TimeSliderVSAssembly slider = mock(TimeSliderVSAssembly.class);
      TimeSliderVSAssemblyInfo info = mock(TimeSliderVSAssemblyInfo.class);
      doReturn(info).when(slider).getInfo();
      Harness h = harness(slider);

      // SelectionList cannot be constructed or mocked outside a Spring context (see the
      // class-level note on selectedPaths(SelectionValue[]) below), so getSelectionList() returns
      // null here (Mockito's default) -- this exercises the integration wiring
      // (setSelection -> bucketsOf -> sliderBucketRange) without real bucket data. The
      // bucket-resolution logic itself is unit tested directly, over SelectionValue[], in the
      // range-slider section below.
      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.setSelection("tok", principal(), "Slider1", List.of(List.of("2")), null,
                                      null, null, null, ""));

      assertTrue(e.getMessage().contains("Slider1"), e.getMessage());
      verify(h.selections, never()).applySelection(anyString(), anyString(), any(),
                                                   any(Principal.class), any(), anyString());
   }

   // ── range slider bucket resolution (VFO-009: set_selection was a total no-op on a range
   // slider -- doApplySelection's TimeSliderVSAssembly branch never reads event.getValues(), only
   // event.getSelectStart()/getSelectEnd(), which the plain value-array apply above never set) ──

   /** Every requested value matches a bucket exactly, so nothing is clamped. */
   @Test
   void resolvesRangeSliderValuesIntoABucketIndexRange() {
      SelectionValue b0 = mock(SelectionValue.class);
      when(b0.getValue()).thenReturn("0");
      SelectionValue b1 = mock(SelectionValue.class);
      when(b1.getValue()).thenReturn("1");
      SelectionValue b2 = mock(SelectionValue.class);
      when(b2.getValue()).thenReturn("2");
      SelectionValue[] buckets = { b0, b1, b2 };

      List<Map<String, Object>> clamped = new ArrayList<>();
      int[] range = SelectionRuntimeService.sliderBucketRange("Slider1", buckets,
         List.of(List.of("0"), List.of("2")), clamped);

      assertArrayEquals(new int[]{ 0, 2 }, range);
      assertEquals(List.of(), clamped);
   }

   /**
    * A single requested value narrows to a one-bucket range ({@code start == end}), not the whole
    * slider — the exact shape the original bug reported as a total no-op.
    */
   @Test
   void narrowsToASingleBucketWhenOnlyOneValueIsRequested() {
      SelectionValue b0 = mock(SelectionValue.class);
      when(b0.getValue()).thenReturn("0");
      SelectionValue b1 = mock(SelectionValue.class);
      when(b1.getValue()).thenReturn("1");

      int[] range = SelectionRuntimeService.sliderBucketRange("Slider1",
         new SelectionValue[]{ b0, b1 }, List.of(List.of("1")), new ArrayList<>());

      assertArrayEquals(new int[]{ 1, 1 }, range);
   }

   /**
    * An out-of-range numeric bound clamps to the nearest bucket rather than being refused outright
    * — matching {@link #requireSortOrder}-style forgiving-on-unambiguous-intent — but the
    * substitution has to be disclosed, the same way {@code scopedBySearch} discloses an apply
    * landing on a narrower scope than the literal request.
    */
   @Test
   void clampsAnOutOfRangeNumericBoundToTheNearestBucketAndDisclosesIt() {
      SelectionValue b0 = mock(SelectionValue.class);
      when(b0.getValue()).thenReturn("0");
      SelectionValue b18 = mock(SelectionValue.class);
      when(b18.getValue()).thenReturn("18");

      List<Map<String, Object>> clamped = new ArrayList<>();
      int[] range = SelectionRuntimeService.sliderBucketRange("Slider1",
         new SelectionValue[]{ b0, b18 }, List.of(List.of("500")), clamped);

      assertArrayEquals(new int[]{ 1, 1 }, range, "500 is nearer to bucket 18 (index 1) than 0");
      assertEquals(1, clamped.size());
      assertEquals("500", clamped.get(0).get("requested"));
      assertEquals("18", clamped.get(0).get("applied"));
   }

   /** A value that neither matches a bucket nor parses as a number is refused by name. */
   @Test
   void refusesARangeSliderValueThatMatchesNoBucketAndIsntNumeric() {
      SelectionValue b0 = mock(SelectionValue.class);
      when(b0.getValue()).thenReturn("0");

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> SelectionRuntimeService.bucketIndex("Slider1", new SelectionValue[]{ b0 }, "nope"));

      assertTrue(e.getMessage().contains("nope"), e.getMessage());
   }

   /** A slider has no hierarchy, so a multi-segment path is refused rather than silently flattened. */
   @Test
   void refusesAMultiSegmentPathOnARangeSlider() {
      SelectionValue b0 = mock(SelectionValue.class);
      when(b0.getValue()).thenReturn("0");

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> SelectionRuntimeService.sliderBucketRange("Slider1", new SelectionValue[]{ b0 },
            List.of(List.of("0", "1")), new ArrayList<>()));

      assertTrue(e.getMessage().contains("no hierarchy"), e.getMessage());
   }

   /**
    * {@code doApplySelection} decrements {@code selectEnd} by one when the slider is not
    * upper-inclusive, so the sent value has to be one past the wanted index for that decrement to
    * land back on it; an upper-inclusive slider sends the index unchanged.
    */
   @Test
   void adjustsTheEndBucketForANonUpperInclusiveSlider() {
      assertEquals(5, SelectionRuntimeService.adjustedEnd(true, 5));
      assertEquals(6, SelectionRuntimeService.adjustedEnd(false, 5));
   }

   /** "Nothing filtered" on a range slider is every bucket selected, not an empty/null state. */
   @Test
   void treatsEveryBucketSelectedAsTheFullRange() {
      SelectionValue b0 = mock(SelectionValue.class);
      when(b0.isSelected()).thenReturn(true);
      SelectionValue b1 = mock(SelectionValue.class);
      when(b1.isSelected()).thenReturn(true);

      assertTrue(SelectionRuntimeService.isFullRangeSelected(new SelectionValue[]{ b0, b1 }));
      assertTrue(SelectionRuntimeService.isFullRangeSelected(new SelectionValue[0]),
                "a slider with no buckets at all has nothing to filter, so it counts as full range");
   }

   @Test
   void treatsAnyUnselectedBucketAsNotTheFullRange() {
      SelectionValue b0 = mock(SelectionValue.class);
      when(b0.isSelected()).thenReturn(true);
      SelectionValue b1 = mock(SelectionValue.class);
      when(b1.isSelected()).thenReturn(false);

      assertFalse(SelectionRuntimeService.isFullRangeSelected(new SelectionValue[]{ b0, b1 }));
   }

   /**
    * The integration-wiring half of the {@code clearedCount} fix: a range slider that reports
    * itself as already at full range (here, via the same "no buckets stubbed" shape
    * {@link #throwsWhenARangeSliderHasNoBucketsToSelectFrom} uses — {@code isFullRangeSelected}
    * treats zero buckets as full range) must report {@code clearedCount: 0} and never call
    * {@code applySelection} — unlike the old bookkeeping, which would have misreported "20 buckets
    * were filtering" on a slider nobody ever touched.
    */
   @Test
   void reportsZeroClearedCountForAnAlreadyFullRangeSlider() throws Exception {
      TimeSliderVSAssembly slider = mock(TimeSliderVSAssembly.class);
      Harness h = harness(slider);

      Map<String, Object> result = h.service.clearSelection("tok", principal(), "Slider1", "");

      assertEquals(0, result.get("clearedCount"));
      verifyNoInteractions(h.selections);
   }

   // ── value validation (bug-76544: a typo'd value is silently dropped, not refused) ─────────────

   /**
    * The direct regression test for the reported symptom: {@code updateSelectionOfChangedAssembly}
    * silently drops any value that doesn't exactly string-match a domain entry, with nothing at any
    * layer above it ever learning that happened. Refusing atomically, by name, before applying
    * anything is the fix — not reporting a corrected count for a partially-applied result.
    */
   @Test
   void refusesATypoedValueRatherThanSilentlyDroppingIt() {
      SelectionValue george = mock(SelectionValue.class);
      when(george.getValue()).thenReturn("George Services");
      SelectionValue interstate = mock(SelectionValue.class);
      when(interstate.getValue()).thenReturn("Interstate Shop");
      SelectionValue oldWorld = mock(SelectionValue.class);
      when(oldWorld.getValue()).thenReturn("Old World Insurance");

      SelectionValue[] domain = { george, interstate, oldWorld };

      List<List<String>> unmatched = SelectionRuntimeService.findUnmatchedPaths(domain,
         List.of(List.of("George Services"), List.of("Interstate Shop"),
                 List.of("OldWorld Insurance")),
         false);

      assertEquals(List.of(List.of("OldWorld Insurance")), unmatched);
   }

   /** Every requested value resolves, so nothing is unmatched — the happy path is unaffected. */
   @Test
   void matchesEveryRequestedValueOnTheHappyPath() {
      SelectionValue east = mock(SelectionValue.class);
      when(east.getValue()).thenReturn("East");
      SelectionValue west = mock(SelectionValue.class);
      when(west.getValue()).thenReturn("West");

      List<List<String>> unmatched = SelectionRuntimeService.findUnmatchedPaths(
         new SelectionValue[]{ east, west }, List.of(List.of("East"), List.of("West")), false);

      assertEquals(List.of(), unmatched);
   }

   /**
    * If the domain isn't known yet, that is a cannot-tell case, not a does-not-exist case — failing
    * closed here would reject calls that have nothing wrong with them. This is also what keeps every
    * existing test above passing, since none of the {@code list(...)}/{@code tree(...)} fixtures stub
    * {@code getSelectionList()}.
    */
   @Test
   void skipsValidationWhenTheDomainIsntKnownYet() throws Exception {
      Harness h = harness(list(XConstants.SORT_ASC, false, null));

      assertDoesNotThrow(() -> h.service.setSelection(
         "tok", principal(), "Filter1", List.of(List.of("Anything At All")), null, null, null, null,
         ""));
   }

   /**
    * The revision-round edge case: a tree path with more segments than the domain has levels, whose
    * resolvable prefix bottoms out at a non-composite leaf before the path ends. The real
    * {@code updateSelectionOfChangedAssembly} applies the selection to that leaf and silently ignores
    * the leftover segments (it does not require them to resolve to anything) — so this must be a
    * match, not a false rejection, or the validation would reject values the real backend already
    * accepts.
    */
   @Test
   void treatsAnOverLongPathThatBottomsOutAtALeafAsAMatch() {
      SelectionValue east = mock(SelectionValue.class);
      when(east.getValue()).thenReturn("East");
      // Not a CompositeSelectionValue: it has no children for the trailing "NY" segment to
      // resolve against, and the real apply code does not require it to.

      List<List<String>> unmatched = SelectionRuntimeService.findUnmatchedPaths(
         new SelectionValue[]{ east }, List.of(List.of("East", "NY")), false);

      assertEquals(List.of(), unmatched);
   }

   /**
    * ID-mode matches by {@code Tool.contains} against the whole path array — a flat "does this
    * node's value appear anywhere in the requested array" scan — not by segment-by-segment descent.
    * A domain node matching only the second element of a two-element requested path still counts as
    * matched, which a descent-based match (as used for non-ID-mode) would not allow.
    */
   @Test
   void matchesAnIdModeValueAnywhereInTheRequestedPathArray() {
      SelectionValue ny = mock(SelectionValue.class);
      when(ny.getValue()).thenReturn("NY");

      List<List<String>> unmatched = SelectionRuntimeService.findUnmatchedPaths(
         new SelectionValue[]{ ny }, List.of(List.of("East", "NY")), true);

      assertEquals(List.of(), unmatched);
   }

   /** An ID that appears nowhere in the domain is refused the same as any other unmatched value. */
   @Test
   void refusesAnIdModeValueThatMatchesNothing() {
      SelectionValue east = mock(SelectionValue.class);
      when(east.getValue()).thenReturn("East");

      List<List<String>> unmatched = SelectionRuntimeService.findUnmatchedPaths(
         new SelectionValue[]{ east }, List.of(List.of("Nope")), true);

      assertEquals(List.of(List.of("Nope")), unmatched);
   }

   // ── search-scoped filtering (bug-76854: set_selection applied a value the active search would
   // have hidden, and unconditionally claimed the write was scoped whether or not it actually was)

   /**
    * The reporter's own repro, isolated to the matching primitive: "Business" does not contain
    * "Ga" ({@code SelectionValue.match}'s case-insensitive substring rule), so it must be dropped
    * rather than passed through to the apply the old code sent unfiltered. {@code match} is
    * stubbed directly rather than relying on a mock executing the real
    * {@code getLabel().toLowerCase().contains(...)} body (a plain Mockito mock does not run
    * inherited real method logic) — this asserts {@code filterBySearch} correctly acts on whatever
    * {@code match} reports, the same way {@link #refusesATypoedValueRatherThanSilentlyDroppingIt}
    * stubs {@code getValue()} directly rather than exercising a real domain lookup.
    */
   @Test
   void excludesAValueThatDoesNotMatchTheActiveSearch() {
      SelectionValue business = mock(SelectionValue.class);
      when(business.getValue()).thenReturn("Business");
      when(business.match("Ga", true)).thenReturn(false);

      List<List<String>> matching = SelectionRuntimeService.filterBySearch(
         new SelectionValue[]{ business }, List.of(List.of("Business")), false, "Ga");

      assertEquals(List.of(), matching);
   }

   /**
    * The reporter's exact mixed-request repro (values Business and Games under search Ga) — only
    * Games matches, so only Games may land.
    */
   @Test
   void keepsOnlyTheMatchingValueFromAMixedRequest() {
      SelectionValue business = mock(SelectionValue.class);
      when(business.getValue()).thenReturn("Business");
      when(business.match("Ga", true)).thenReturn(false);
      SelectionValue games = mock(SelectionValue.class);
      when(games.getValue()).thenReturn("Games");
      when(games.match("Ga", true)).thenReturn(true);

      List<List<String>> matching = SelectionRuntimeService.filterBySearch(
         new SelectionValue[]{ business, games },
         List.of(List.of("Business"), List.of("Games")), false, "Ga");

      assertEquals(List.of(List.of("Games")), matching);
   }

   /** Nothing to drop when every requested value already matches — the happy path is unaffected. */
   @Test
   void keepsEveryValueWhenAllOfThemMatch() {
      SelectionValue games = mock(SelectionValue.class);
      when(games.getValue()).thenReturn("Games");
      when(games.match("Ga", true)).thenReturn(true);
      SelectionValue gadgets = mock(SelectionValue.class);
      when(gadgets.getValue()).thenReturn("Gadgets");
      when(gadgets.match("Ga", true)).thenReturn(true);

      List<List<String>> matching = SelectionRuntimeService.filterBySearch(
         new SelectionValue[]{ games, gadgets },
         List.of(List.of("Games"), List.of("Gadgets")), false, "Ga");

      assertEquals(List.of(List.of("Games"), List.of("Gadgets")), matching);
   }

   /**
    * {@code filterBySearch} must forward the search string to {@code match} exactly as given —
    * case-insensitivity is {@code SelectionValue.match}'s own contract (already covered at that
    * class's own level), not something this method should re-implement or subtly alter (e.g. by
    * re-casing it) before delegating.
    */
   @Test
   void forwardsTheSearchStringToMatchUnaltered() {
      SelectionValue games = mock(SelectionValue.class);
      when(games.getValue()).thenReturn("Games");
      when(games.match("GA", true)).thenReturn(true);

      List<List<String>> matching = SelectionRuntimeService.filterBySearch(
         new SelectionValue[]{ games }, List.of(List.of("Games")), false, "GA");

      assertEquals(List.of(List.of("Games")), matching);
      verify(games).match("GA", true);
   }

   /**
    * A whole composite node selected as itself (path length 1, not descending into a child) has to
    * be resolved with {@code recursive=true} on {@code CompositeSelectionValue.match} — the
    * semantics that let a parent whose own label doesn't match still count as matching because a
    * descendant does, the same as the widget's own search box keeps a parent expanded/visible when
    * one of its children matches. Stubbed directly rather than via a real child list: {@code
    * SelectionList} cannot be constructed or mocked outside a Spring context (see
    * {@link #aSelectedCompositeWithNoSelectedChildrenStillProducesASelfOnlyPath}), so this asserts
    * that {@code filterBySearch} calls {@code match} with recursive semantics, not that
    * {@code CompositeSelectionValue} itself recurses correctly (a different class, already covered
    * elsewhere).
    */
   @Test
   void matchesAWholeCompositeNodeRecursivelyViaADescendant() {
      CompositeSelectionValue east = mock(CompositeSelectionValue.class);
      when(east.getValue()).thenReturn("East");
      when(east.match("Ga", true)).thenReturn(true);

      List<List<String>> matching = SelectionRuntimeService.filterBySearch(
         new SelectionValue[]{ east }, List.of(List.of("East")), false, "Ga");

      assertEquals(List.of(List.of("East")), matching);
   }

   /** The mirror of the above: no descendant matches either, so the whole node is dropped too. */
   @Test
   void dropsAWholeCompositeNodeWhenNoDescendantMatches() {
      CompositeSelectionValue east = mock(CompositeSelectionValue.class);
      when(east.getValue()).thenReturn("East");
      when(east.match("Ga", true)).thenReturn(false);

      List<List<String>> matching = SelectionRuntimeService.filterBySearch(
         new SelectionValue[]{ east }, List.of(List.of("East")), false, "Ga");

      assertEquals(List.of(), matching);
   }

   /**
    * ID mode matches by scanning the whole tree for a node whose value appears anywhere in the
    * requested path array (mirroring {@code matchesAnywhere}) — the search-match test has to ride
    * along the same scan, not a segment-by-segment descent.
    */
   @Test
   void filtersAnIdModeValueByTheSameAnywhereScan() {
      SelectionValue games = mock(SelectionValue.class);
      when(games.getValue()).thenReturn("Games");
      when(games.match("Ga", true)).thenReturn(true);
      SelectionValue business = mock(SelectionValue.class);
      when(business.getValue()).thenReturn("Business");
      when(business.match("Ga", true)).thenReturn(false);

      List<List<String>> matching = SelectionRuntimeService.filterBySearch(
         new SelectionValue[]{ games, business },
         List.of(List.of("Games"), List.of("Business")), true, "Ga");

      assertEquals(List.of(List.of("Games")), matching);
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

   // ── deselectTargets (the ancestor-flag-residue fix) ─────────────────────

   /**
    * The direct regression test for the newly-found symptom: switching a SelectionTree's
    * selection to a sibling at the SAME depth left the old branch's top-level node accumulated
    * alongside the new one, because a leaf-level deselect never clears the ancestor flag a prior
    * select had set. Removing "NY/New York" with nothing else remaining under "NY" must resolve
    * to a single "NY"-level deselect target, not the leaf path — that's what reaches
    * {@code unselectChildren} and actually clears NY's own stale flag.
    */
   @Test
   void collapsesALeafDeselectToItsRootAncestorWhenNothingElseRemainsUnderIt() {
      List<List<String>> current = List.of(List.of("NY", "New York"));
      List<List<String>> toRemove = List.of(List.of("NY", "New York"));

      assertEquals(List.of(List.of("NY")),
                  SelectionRuntimeService.deselectTargets(current, toRemove),
                  "NY has no other selected child left, so clearing it at the NY level (which " +
                  "cascades via unselectChildren) is what actually clears NY's own residual flag");
   }

   /**
    * The precise case the fix has to get right in the other direction: a sibling under the SAME
    * ancestor must survive. Collapsing to the ancestor here would wrongly clear "NY/Buffalo" too,
    * so the exact leaf path is what has to be sent.
    */
   @Test
   void keepsTheExactLeafWhenASiblingUnderTheSameAncestorStaysSelected() {
      List<List<String>> current = List.of(List.of("NY", "New York"), List.of("NY", "Buffalo"));
      List<List<String>> toRemove = List.of(List.of("NY", "New York"));

      assertEquals(List.of(List.of("NY", "New York")),
                  SelectionRuntimeService.deselectTargets(current, toRemove),
                  "Buffalo is still selected under NY, so collapsing to the NY-level ancestor " +
                  "would clear a selection the caller never asked to remove");
   }

   /** Removing several branches at once collapses each independently and de-duplicates. */
   @Test
   void collapsesEachRemovedBranchIndependently() {
      List<List<String>> current = List.of(List.of("NY", "New York"), List.of("FL", "Orlando"));
      List<List<String>> toRemove = current;

      assertEquals(List.of(List.of("NY"), List.of("FL")),
                  SelectionRuntimeService.deselectTargets(current, toRemove));
   }

   /** A flat SelectionList has no ancestors to collapse to — every path is already one segment. */
   @Test
   void isANoOpForAFlatSelectionListsSingleSegmentPaths() {
      List<List<String>> current = List.of(List.of("East"), List.of("West"));
      List<List<String>> toRemove = List.of(List.of("East"));

      assertEquals(List.of(List.of("East")),
                  SelectionRuntimeService.deselectTargets(current, toRemove));
   }

   /**
    * The vacuous-truth gap (bug-76701): against an empty {@code current}, the shortest-prefix
    * search's {@code noneMatch} over an empty {@code remaining} is vacuously true at the very
    * first prefix, so before the fix this manufactured a target for a value that was never
    * actually selected. An empty {@code current} has nothing to remove at all.
    */
   @Test
   void isEmptyForAnyRequestedPathWhenNothingIsCurrentlySelected() {
      List<List<String>> toRemove = List.of(List.of("Germany"));

      assertEquals(List.of(), SelectionRuntimeService.deselectTargets(List.of(), toRemove));
   }

   /**
    * The broader shape of the same gap: a mismatched deselect value misfires even against a
    * perfectly healthy, non-empty selection, because the shortest-prefix search only asks whether
    * a prefix is still covered by something remaining -- never whether the path was ever a member
    * of {@code current} to begin with.
    */
   @Test
   void isEmptyForARequestedPathThatWasNeverPartOfCurrentSelection() {
      List<List<String>> current = List.of(List.of("USA West"), List.of("USA East"));
      List<List<String>> toRemove = List.of(List.of("Germany"));

      assertEquals(List.of(), SelectionRuntimeService.deselectTargets(current, toRemove));
   }

   @Test
   void everSelectedIsTrueWhenThePathOrAnAncestorOrDescendantIsInCurrent() {
      List<List<String>> current = List.of(List.of("NY", "New York"));

      assertTrue(SelectionRuntimeService.everSelected(current, List.of("NY", "New York")),
                "an exact match is trivially an overlap");
      assertTrue(SelectionRuntimeService.everSelected(current, List.of("NY")),
                "current holds a descendant of the requested ancestor path");
   }

   @Test
   void everSelectedIsFalseForAnUnrelatedPath() {
      List<List<String>> current = List.of(List.of("USA West"), List.of("USA East"));

      assertFalse(SelectionRuntimeService.everSelected(current, List.of("Germany")));
      assertFalse(SelectionRuntimeService.everSelected(List.of(), List.of("Germany")));
   }

   // ── additive and deselect (the accumulate-vs-replace design gap) ────────

   /**
    * additive:true is the caller explicitly opting into "just add" -- the automatic replace-diff
    * must not fire, so whatever was selected before survives alongside the new value.
    */
   /**
    * Asserting only "one applySelection call" here would pass even if the {@code additive} gate
    * were deleted entirely: with no populated {@code SelectionList} stubbed (the usual
    * {@code SelectionList}-cannot-be-mocked constraint), {@code toDeselect} is always empty
    * regardless of the flag, so the diff step firing or not never changes the apply count in
    * this harness. Asserting on {@code getSelectionList()}'s own call count instead is a real
    * discriminator: value-validation (bug-76544) reads it once unconditionally
    * ({@link #skipsTheDiffStepForASingleSelectAssembly} establishes that baseline), and the diff
    * step reads it a SECOND time via {@code selectedPaths(assembly)} -- but only when it
    * actually runs. additive:true must suppress that second read.
    */
   @Test
   void additiveTrueSkipsTheReplaceDiff() throws Exception {
      SelectionListVSAssembly assembly = list(XConstants.SORT_ASC, false, null);
      Harness h = harness(assembly);

      h.service.setSelection("tok", principal(), "Filter1", List.of(List.of("West")), null, null,
                             null, true, "");

      verify(assembly, times(1)).getSelectionList();
      verify(h.selections, times(1)).applySelection(anyString(), anyString(), any(),
                                                    any(Principal.class), any(), anyString());
   }

   /**
    * The other half of the same discriminator: without additive, the diff step's own
    * {@code selectedPaths(assembly)} call genuinely happens -- a SECOND {@code getSelectionList()}
    * read, on top of value-validation's own -- confirming additive being unset/false still runs
    * the diff step (bug-76548's own fix is unchanged, not silently bypassed). No prior selection
    * is stubbed, so there is nothing to actually diff away in this particular call; the point is
    * that the diff step's read happens at all, not what it finds -- the ancestor-collapse logic
    * itself is exercised end-to-end above
    * ({@code collapsesALeafDeselectToItsRootAncestorWhenNothingElseRemainsUnderIt} et al.) and by
    * bug-76548's own pre-existing tests.
    */
   @Test
   void defaultsToTheReplaceDiffWithoutAdditive() throws Exception {
      SelectionListVSAssembly assembly = list(XConstants.SORT_ASC, false, null);
      Harness h = harness(assembly);

      h.service.setSelection("tok", principal(), "Filter1", List.of(List.of("West")), null, null,
                             null, null, "");

      verify(assembly, times(2)).getSelectionList();
      verify(h.selections, times(1)).applySelection(anyString(), anyString(), any(),
                                                    any(Principal.class), any(), anyString());
   }

   /**
    * A deselect value the caller believes is selected but that {@code current} contains no trace
    * of (bug-76701: {@code getSelectionList()} is left unstubbed here, null, the same "domain not
    * yet known" shape every other integration test in this file uses — {@code SelectionList}
    * cannot be mocked outside a Spring context, confirmed by actually attempting it — so
    * {@code selectedPaths(assembly)} sees an empty current selection) must not be reported as
    * removed, and must not reach {@code applySelection} at all. Before the fix, an empty
    * {@code current} vacuously matched any requested path (see {@link #deselectTargets}), which is
    * exactly the shape of the reported bug: a fully unbound, always-empty assembly reporting
    * {@code deselected: 1} for a value it never actually held.
    *
    * <p>The genuinely-matched case — a value that really is in {@code current} — is covered
    * directly over {@code List<List<String>>} above ({@code deselectTargets}/{@code everSelected}
    * and the ancestor-collapse tests), since a real, populated {@code current} cannot be produced
    * through this harness at all.
    */
   @Test
   void explicitDeselectOfAValueNotInCurrentReportsNothingRemoved() throws Exception {
      Harness h = harness(list(XConstants.SORT_ASC, false, null));

      Map<String, Object> result = h.service.setSelection("tok", principal(), "Filter1", null,
         List.of(List.of("East")), null, null, null, "");

      assertFalse(result.containsKey("deselected"),
                 "East was never actually selected (current is empty), so nothing was removed");
      verifyNoInteractions(h.selections);
   }

   /** Naming the same value in both 'values' and 'deselect' is a contradiction, refused by name. */
   @Test
   void refusesAValueNamedInBothValuesAndDeselect() {
      Harness h = harness(list(XConstants.SORT_ASC, false, null));

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.setSelection("tok", principal(), "Filter1", List.of(List.of("East")),
                                      List.of(List.of("East")), null, null, null, ""));

      assertTrue(e.getMessage().contains("East"), e.getMessage());
   }

   /** 'deselect' only makes sense where the replace-diff already applies -- same gate, same reason. */
   @Test
   void refusesDeselectOnARangeSlider() {
      TimeSliderVSAssembly slider = mock(TimeSliderVSAssembly.class);
      TimeSliderVSAssemblyInfo info = mock(TimeSliderVSAssemblyInfo.class);
      doReturn(info).when(slider).getInfo();
      Harness h = harness(slider);

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.setSelection("tok", principal(), "Slider1", null,
                                      List.of(List.of("2")), null, null, null, ""));

      assertTrue(e.getMessage().contains("range slider"), e.getMessage());
   }

   /** 'deselect' alone (no 'values') is a legitimate call -- "just remove these" needs no new add. */
   @Test
   void acceptsDeselectAloneWithNoValues() throws Exception {
      Harness h = harness(list(XConstants.SORT_ASC, false, null));

      assertDoesNotThrow(() -> h.service.setSelection("tok", principal(), "Filter1", null,
         List.of(List.of("East")), null, null, null, ""));
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

   /**
    * bug-76875: an ID-mode tree's own {@code countSelected} counts every independently-selected
    * node directly, with no {@code isSelected()} gate on the recursion the way
    * {@link #selectedPaths(SelectionValue[])} has -- unlike a fixed-hierarchy select, an ID-mode
    * match never marks its ancestors, so gating on the composite's own flag (as
    * {@code selectedPaths} does) would miss every descendant under an unselected composite.
    *
    * <p>Asserted over the flat value array, same limitation as {@code selectedPaths}'s own tests:
    * {@code SelectionList} cannot be constructed or mocked outside a Spring context, so a real
    * composite-with-selected-children shape cannot be exercised here -- only the flat counting
    * logic and null-safety.
    */
   @Test
   void countsEveryIndependentlySelectedNodeRegardlessOfItsOwnValue() {
      SelectionValue east = mock(SelectionValue.class);
      when(east.isSelected()).thenReturn(true);

      SelectionValue west = mock(SelectionValue.class);
      when(west.isSelected()).thenReturn(false);

      SelectionValue nullValued = mock(SelectionValue.class);
      when(nullValued.isSelected()).thenReturn(true);
      when(nullValued.getValue()).thenReturn(null);

      int count = SelectionRuntimeService.countSelected(
         new SelectionValue[]{ east, west, null, nullValued });

      assertEquals(2, count, "unselected and null values must not be counted");
      assertEquals(0, SelectionRuntimeService.countSelected(null));
   }

   /** Nothing selected means nothing to send, rather than an empty apply. */
   @Test
   void doesNotCallTheEndpointWhenThereIsNothingSelected() throws Exception {
      Harness h = harness(list(XConstants.SORT_ASC, false, null));

      Map<String, Object> result = h.service.clearSelection("tok", principal(), "Filter1", "");

      assertEquals(0, result.get("clearedCount"));
      verifyNoInteractions(h.selections);
   }

   /**
    * bug-76875: an ID-mode tree must go through the {@code countSelected} branch, not
    * {@code selectedPaths}, and must not NPE when the underlying {@code SelectionList} is null
    * (unstubbed, same shape a domain-less/empty tree presents in production).
    */
   @Test
   void doesNotCallTheEndpointWhenAnIdModeTreeHasNothingSelected() throws Exception {
      SelectionTreeVSAssembly idTree = tree(XConstants.SORT_ASC, false);
      when(idTree.isIDMode()).thenReturn(true);
      Harness h = harness(idTree);

      Map<String, Object> result = h.service.clearSelection("tok", principal(), "Tree1", "");

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

   // ── bug-76758 (VFL-004): select_subtree collapsing to one leaf under singleSelection ──────────

   /**
    * A path shorter than the tree's own level count names an ancestor, not a leaf --
    * {@code VSSelectionService.setSubtree}'s single-selection short-circuit silently collapses that
    * to one arbitrary descendant while still reporting success, so this is refused up front instead.
    */
   @Test
   void refusesSelectingAnAncestorPathUnderSingleSelection() {
      SelectionTreeVSAssembly assembly = tree(XConstants.SORT_ASC, true);
      when(assembly.getDataRefs()).thenReturn(
         new DataRef[]{ mock(DataRef.class), mock(DataRef.class), mock(DataRef.class) });
      Harness h = harness(assembly);

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.selectSubtree("tok", principal(), "Tree1", List.of("USA East"), "select",
                                       ""));

      assertTrue(e.getMessage().contains("single-select"), e.getMessage());
      verifyNoInteractions(h.selections);
   }

   /** A path all the way down to a leaf is always exactly one node -- never ambiguous. */
   @Test
   void allowsSelectingAFullLeafPathUnderSingleSelection() throws Exception {
      SelectionTreeVSAssembly assembly = tree(XConstants.SORT_ASC, true);
      when(assembly.getDataRefs()).thenReturn(
         new DataRef[]{ mock(DataRef.class), mock(DataRef.class), mock(DataRef.class) });
      Harness h = harness(assembly);

      assertDoesNotThrow(() -> h.service.selectSubtree(
         "tok", principal(), "Tree1", List.of("USA East", "CT", "Hartford"), "select", ""));

      verify(h.selections).selectSubtree(anyString(), anyString(), any(), any(Principal.class),
                                         any(), anyString());
   }

   /** Not single-select -- an ancestor path is never ambiguous outside single-select. */
   @Test
   void allowsAnAncestorPathWhenNotSingleSelect() throws Exception {
      SelectionTreeVSAssembly assembly = tree(XConstants.SORT_ASC, false);
      when(assembly.getDataRefs()).thenReturn(
         new DataRef[]{ mock(DataRef.class), mock(DataRef.class), mock(DataRef.class) });
      Harness h = harness(assembly);

      assertDoesNotThrow(() -> h.service.selectSubtree(
         "tok", principal(), "Tree1", List.of("USA East"), "select", ""));
   }

   /** {@code mode:"clear"} never hits the single-selection collapse -- selected=false skips it. */
   @Test
   void allowsAnAncestorPathOnClearEvenUnderSingleSelect() throws Exception {
      SelectionTreeVSAssembly assembly = tree(XConstants.SORT_ASC, true);
      when(assembly.getDataRefs()).thenReturn(
         new DataRef[]{ mock(DataRef.class), mock(DataRef.class), mock(DataRef.class) });
      Harness h = harness(assembly);

      assertDoesNotThrow(() -> h.service.selectSubtree(
         "tok", principal(), "Tree1", List.of("USA East"), "clear", ""));
   }

   /**
    * Documents a known gap, not a passing behaviour to celebrate: an ID-mode path is matched by
    * value anywhere in the tree (see {@code matchesAnywhere}), not positionally from the root, so
    * {@code path.size()} says nothing about how many levels remain below the named node -- the
    * depth heuristic above would be actively wrong here, not just imprecise, so the guard is
    * skipped entirely for an ID-mode tree (see the caller's comment in {@code selectSubtree}).
    * That means an ID-mode single-select tree's own version of VFL-004 is still open: this locks
    * in that the call is currently let through, so a future fix that closes the gap has to update
    * this test deliberately rather than by accident.
    */
   @Test
   void treatsAnIdModeSingleSelectTreeAsUnguarded() throws Exception {
      SelectionTreeVSAssembly assembly = tree(XConstants.SORT_ASC, true);
      when(assembly.isIDMode()).thenReturn(true);
      when(assembly.getDataRefs()).thenReturn(
         new DataRef[]{ mock(DataRef.class), mock(DataRef.class), mock(DataRef.class) });
      Harness h = harness(assembly);

      assertDoesNotThrow(() -> h.service.selectSubtree(
         "tok", principal(), "Tree1", List.of("USA East"), "select", ""));

      verify(h.selections).selectSubtree(anyString(), anyString(), any(), any(Principal.class),
                                         any(), anyString());
   }

   /** The testable core of the guard, isolated from the assembly -- see the two tests above. */
   @Test
   void treeDepthGuardRefusesOnlyWhenShortOfALeaf() {
      assertThrows(IllegalArgumentException.class,
         () -> SelectionRuntimeService.refuseAmbiguousSingleSelectSubtree(
            3, "Tree1", List.of("USA East")));
      assertThrows(IllegalArgumentException.class,
         () -> SelectionRuntimeService.refuseAmbiguousSingleSelectSubtree(
            3, "Tree1", List.of("USA East", "CT")));
      assertDoesNotThrow(() -> SelectionRuntimeService.refuseAmbiguousSingleSelectSubtree(
         3, "Tree1", List.of("USA East", "CT", "Hartford")));
      assertDoesNotThrow(() -> SelectionRuntimeService.refuseAmbiguousSingleSelectSubtree(
         1, "Tree1", List.of("USA East")));
   }

   // ── the shared guards ─────────────────────────────────────────────────────

   @Test
   void refusesAnUnknownAssemblyRatherThanSucceedingSilently() {
      Harness h = harness(null);

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.setSelection("tok", principal(), "Nope", List.of(List.of("x")), null,
                                      null, null, null, ""));

      assertTrue(e.getMessage().contains("Nope"), e.getMessage());
      verifyNoInteractions(h.selections);
   }

   @Test
   void refusesANonSelectionAssemblyNamingItsType() {
      Harness h = harness(mock(ChartVSAssembly.class));

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.setSelection("tok", principal(), "Chart1", List.of(List.of("x")), null,
                                      null, null, null, ""));

      assertTrue(e.getMessage().contains("not a selection assembly"), e.getMessage());
      verifyNoInteractions(h.selections);
   }

   @Test
   void refusesACallThatAsksForNothing() {
      Harness h = harness(list(XConstants.SORT_ASC, false, null));

      assertThrows(IllegalArgumentException.class,
         () -> h.service.setSelection("tok", principal(), "Filter1", null, null, null, null, null,
                                      ""));
   }

   @Test
   void alwaysReportsThatTheStatePersists() throws Exception {
      Harness h = harness(list(XConstants.SORT_ASC, false, null));

      Map<String, Object> result = h.service.setSelection(
         "tok", principal(), "Filter1", List.of(List.of("East")), null, null, null, null, "");

      assertEquals(true, result.get("persistsOnSave"),
                   "writeStateContent writes the selection on the save path, so a caller must know");
   }

   // ── the unbound-column guard (bug-76701: select/deselect/clear all reported success while
   // never actually writing a filter, because the assembly had no column bound) ────────────────

   /**
    * A null {@code getDataRef()} means {@code getConditionList()}/{@code getSelection()} always
    * short-circuit to null/false -- nothing downstream of {@code setSelection} could ever produce
    * a real applied condition, so this has to be refused before any of it runs.
    */
   @Test
   void refusesSetSelectionOnAColumnlessSelectionList() {
      SelectionListVSAssembly assembly = list(XConstants.SORT_ASC, false, null);
      when(assembly.getDataRef()).thenReturn(null);
      Harness h = harness(assembly);

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.setSelection("tok", principal(), "Filter1", List.of(List.of("East")), null,
                                      null, null, null, ""));

      assertTrue(e.getMessage().contains("Filter1"), e.getMessage());
      assertTrue(e.getMessage().contains("no column bound"), e.getMessage());
      verifyNoInteractions(h.selections);
   }

   /**
    * A {@code sortOrder}-only or {@code singleSelect}-only call never reaches
    * {@code getConditionList()}/{@code getSelection()} -- it does not touch {@code values} or
    * {@code deselect} at all -- so it must succeed on a column-less assembly rather than being
    * refused for a problem it does not have.
    */
   @Test
   void allowsASortOrderOnlyCallOnAColumnlessSelectionList() throws Exception {
      SelectionListVSAssembly assembly = list(XConstants.SORT_ASC, false, null);
      when(assembly.getDataRef()).thenReturn(null);
      Harness h = harness(assembly);

      assertDoesNotThrow(() -> h.service.setSelection("tok", principal(), "Filter1", null, null,
         "desc", null, null, ""));
   }

   /** See {@link #allowsASortOrderOnlyCallOnAColumnlessSelectionList} -- same reasoning, singleSelect. */
   @Test
   void allowsASingleSelectOnlyCallOnAColumnlessSelectionList() throws Exception {
      SelectionListVSAssembly assembly = list(XConstants.SORT_ASC, false, null);
      when(assembly.getDataRef()).thenReturn(null);
      Harness h = harness(assembly);

      assertDoesNotThrow(() -> h.service.setSelection("tok", principal(), "Filter1", null, null,
         null, true, null, ""));
   }

   /** {@code getDataRefs()} returning an empty array is the same "no column bound" shape. */
   @Test
   void refusesSetSelectionOnATreeWithNoDataRefsAtAll() {
      SelectionTreeVSAssembly assembly = tree(XConstants.SORT_ASC, false);
      when(assembly.getDataRefs()).thenReturn(new DataRef[0]);
      Harness h = harness(assembly);

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.setSelection("tok", principal(), "Tree1", List.of(List.of("East")), null,
                                      null, null, null, ""));

      assertTrue(e.getMessage().contains("Tree1"), e.getMessage());
      verifyNoInteractions(h.selections);
   }

   /** {@code getDataRefs()} returning an array of nulls is the same "no column bound" shape. */
   @Test
   void refusesSetSelectionOnATreeWhoseDataRefsAreAllNull() {
      SelectionTreeVSAssembly assembly = tree(XConstants.SORT_ASC, false);
      when(assembly.getDataRefs()).thenReturn(new DataRef[]{ null });
      Harness h = harness(assembly);

      assertThrows(IllegalArgumentException.class,
         () -> h.service.setSelection("tok", principal(), "Tree1", List.of(List.of("East")), null,
                                      null, null, null, ""));
   }

   @Test
   void refusesClearSelectionOnAColumnlessSelectionList() {
      SelectionListVSAssembly assembly = list(XConstants.SORT_ASC, false, null);
      when(assembly.getDataRef()).thenReturn(null);
      Harness h = harness(assembly);

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.clearSelection("tok", principal(), "Filter1", ""));

      assertTrue(e.getMessage().contains("Filter1"), e.getMessage());
      verifyNoInteractions(h.selections);
   }

   @Test
   void refusesSelectSubtreeOnAColumnlessTree() {
      SelectionTreeVSAssembly assembly = tree(XConstants.SORT_ASC, false);
      when(assembly.getDataRefs()).thenReturn(null);
      Harness h = harness(assembly);

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.selectSubtree("tok", principal(), "Tree1", List.of("East"), "select", ""));

      assertTrue(e.getMessage().contains("Tree1"), e.getMessage());
      verifyNoInteractions(h.selections);
   }

   // ── fixtures ──────────────────────────────────────────────────────────────

   /**
    * Infos are mocked: their real constructors need SreeEnv and the Spring context. Stubs a bound
    * column by default -- {@code getDataRef()} -- since bug-76701's guard now refuses any of
    * these endpoints on a column-less assembly; a test of that guard itself overrides it back to
    * null (see {@code refusesAnUnboundSelectionList} et al.).
    */
   private static SelectionListVSAssembly list(int sortType, boolean single, String search,
                                               String... selected) {
      SelectionListVSAssembly assembly = mock(SelectionListVSAssembly.class);
      SelectionListVSAssemblyInfo info = mock(SelectionListVSAssemblyInfo.class);
      when(info.isSingleSelection()).thenReturn(single);
      when(info.getSortTypeValue()).thenReturn(sortType);
      when(info.getSearchString()).thenReturn(search);
      doReturn(info).when(assembly).getInfo();
      when(assembly.getDataRef()).thenReturn(mock(DataRef.class));
      return assembly;
   }

   /** See {@link #list}'s note on the default bound column. */
   private static SelectionTreeVSAssembly tree(int sortType, boolean single) {
      SelectionTreeVSAssembly assembly = mock(SelectionTreeVSAssembly.class);
      SelectionTreeVSAssemblyInfo info = mock(SelectionTreeVSAssemblyInfo.class);
      when(info.isSingleSelection()).thenReturn(single);
      when(info.getSortTypeValue()).thenReturn(sortType);
      doReturn(info).when(assembly).getInfo();
      when(assembly.getSelectionTreeInfo()).thenReturn(info);
      when(assembly.getDataRefs()).thenReturn(new DataRef[]{ mock(DataRef.class) });
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
