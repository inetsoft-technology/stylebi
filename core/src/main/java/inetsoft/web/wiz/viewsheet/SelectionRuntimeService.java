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
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import inetsoft.web.viewsheet.controller.VSCalendarService;
import inetsoft.web.viewsheet.event.ApplySelectionListEvent;
import inetsoft.web.viewsheet.event.SortSelectionListEvent;
import inetsoft.web.viewsheet.service.VSSelectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A selection assembly's runtime state: which values are selected, how the list is sorted, and
 * whether it accepts one value or many.
 *
 * <p>This covers selection lists, selection trees and range sliders — {@code TimeSliderVSAssembly}
 * extends {@code AbstractSelectionVSAssembly} and rides the same {@code applySelection} path, so it
 * needs no separate surface.
 *
 * <p><b>Why these are authoring operations and not viewer exploration.</b> The state written here
 * persists: {@code SelectionListVSAssembly.writeStateContent} writes the selection, the sort order
 * and the single-vs-multi flag <i>unconditionally</i>, and {@code writeContents} calls it on the save
 * path. A default selection is a real design decision, and the state block treats sort and selection
 * style as authoring settings by sitting them beside it.
 *
 * <p><b>The endpoints are TOGGLES and CYCLES, not setters — this class converts desired state into
 * the right number of steps.</b> They were built for menu clicks, so:
 *
 * <ul>
 *   <li>{@code /selectionList/sort} takes no order. It advances a three-state ring:
 *       {@code SORT_ASC → SORT_DESC → SORT_SPECIFIC → SORT_ASC} ({@code nextSortType}). Reaching a
 *       requested order means computing the distance and cycling that many times.</li>
 *   <li>{@code /selectionList/toggle} takes no value — it flips {@code singleSelection}. So setting
 *       it means reading the current flag and acting only if it differs.</li>
 *   <li>{@code applySelection}'s own event <i>also</i> flips {@code singleSelection} when
 *       {@code toggle} or {@code toggleAll} is set, which is a third route to the same field. This
 *       class never sets those flags, so a value apply cannot silently change the selection style.</li>
 * </ul>
 *
 * <p>Every step runs inside one {@link ViewsheetSessionService#mutate} call, which owns the
 * checkpoint — so a request that needs two sort cycles is still <b>one undo step</b>, matching the
 * plugin's one-call-one-checkpoint contract.
 *
 * <p><b>A wrong assembly name or type fails three different ways, so this class checks first.</b>
 * Audited across {@code VSSelectionService}: {@code applySelection} and {@code sortSelection}
 * answer an unknown name with success and no change; {@code selectSubtree} and {@code unselectAll}
 * throw {@code NullPointerException}; and every one of them throws {@code ClassCastException} on a
 * name that exists but is not a selection assembly. There is no server behaviour worth forwarding.
 *
 * <p><b>An active search string narrows what a values apply touches.</b> {@code VSSelectionService}'s
 * own apply path ({@code doApplySelection}) has no search-awareness at all — its
 * {@code olist.findAll(search, true)} call runs afterwards, from {@code afterSelectionListUpdate},
 * and merges previously-selected-but-now-hidden values back in rather than filtering the new
 * request. So this class does the filtering itself, before ever calling {@code applySelection}:
 * when a search is active, {@link #setSelection(String,
 * Principal, String, List, List, String, Boolean, Boolean, String, String) setSelection} drops any
 * requested value the search would hide (resolved in the same {@code findAll(search, false)} tree
 * the widget renders, bug-78786) and discloses exactly what was dropped, rather than a bare,
 * unconditional claim that scoping happened (bug-76854). Single-select assemblies, whole-tree or
 * per level, are scoped too, and a single-select request the search empties changes nothing
 * (bug-78785, bug-78795). {@code setSelection}'s {@code search} parameter can set the search
 * string before that same apply runs, the same as typing into the widget's own search box
 * (bug-76758). Either way, {@code setSearchString} writes both {@code search} and {@code search2},
 * only {@code search2} is persisted, and <b>nothing in the repository ever parses {@code search2}
 * back</b> — so a search string never survives a reopen, exactly like the interactive widget's own
 * search box. {@code deselect} is deliberately left unscoped by search for now — see the note at
 * the {@code hasDeselect} block in {@code setSelection}.
 */
@Service
public class SelectionRuntimeService {
   private static final Logger LOG = LoggerFactory.getLogger(SelectionRuntimeService.class);

   public SelectionRuntimeService(ViewsheetSessionService sessions, VSSelectionService selections,
                                  VSCalendarService calendars)
   {
      this.sessions = sessions;
      this.selections = selections;
      this.calendars = calendars;
   }

   /** The sort orders a caller can ask for, mapped to StyleBI's constants. */
   public Map<String, Object> vocabulary() {
      return Map.of(
         "sortOrder", List.of("asc", "desc", "specific"),
         "sortOrderNote",
         "The runtime endpoint has no setter — it cycles asc → desc → specific. A request is " +
         "reached by cycling, all inside one undo checkpoint.",
         "subtreeMode", List.of("select", "clear"));
   }

   /**
    * Makes a selection assembly's state be what the caller asked for.
    *
    * @param values      the values to select. For a tree, each entry is a path from the root, so
    *                    {@code ["East","NY"]} selects NY under East. Null leaves the selection alone.
    *                    On a list or a multi-select tree (ID-mode included), {@code values} <b>replaces</b> the current
    *                    selection by default (bug-76548) — pass {@code additive:true} to make it
    *                    add to the current selection instead, without touching anything else.
    * @param deselect    values/paths to explicitly remove from the current selection, independent
    *                    of {@code additive} — the counterpart to {@code values} for a caller that
    *                    knows exactly what to un-check without needing to first read (or
    *                    re-specify) everything that should stay selected. Same shape as
    *                    {@code values}; null or empty leaves the selection alone. Only valid on a
    *                    list or non-ID-mode tree; an ID-mode tree, range slider or calendar is
    *                    refused.
    * @param sortOrder   {@code asc} | {@code desc} | {@code specific}, or null to leave it.
    * @param singleSelect whether the assembly should accept one value only, or null to leave it.
    * @param additive    when true, {@code values} only adds — the automatic replace-diff (deselect
    *                    anything current but unmentioned) is skipped. Ignored where that diff
    *                    never ran anyway (a list or tree that is single-select as a whole, a range
    *                    slider, calendar). A tree with single-select levels is diffed like a
    *                    multi-select one (bug-78795).
    *
    * <p>The result also carries {@code selectedTotal}, the true post-apply selected count of a
    * list or tree (unlike {@code valuesSelected}, which is the request size). With an active
    * search, a selected value the search hides cannot be changed by a replace or deselect --
    * {@code VSSelectionService.applySelection} adds it back, as the interactive search box does --
    * so it stays selected and is reported as {@code scopedBySearchRetained} (bug-78216).
    */
   public Map<String, Object> setSelection(String sessionToken, Principal user, String assemblyName,
                                           List<List<String>> values, List<List<String>> deselect,
                                           String sortOrder, Boolean singleSelect, Boolean additive,
                                           String linkUri)
      throws Exception
   {
      return setSelection(sessionToken, user, assemblyName, values, deselect, sortOrder,
                          singleSelect, additive, null, linkUri);
   }

   /**
    * Same as {@link #setSelection(String, Principal, String, List, List, String, Boolean, Boolean,
    * String)}, with a search string to set on the assembly before the rest of the request applies —
    * see bug-76758 (VFL-003). This is the only way to set one: {@code setSearchString} exists on the
    * assembly info, but nothing upstream of this class ever called it, so a caller had no way to
    * scope a write by search the way a person typing into the widget's own search box can. Like the
    * widget's own search box, it does not survive a reopen (see the class javadoc).
    *
    * @param search the search string to set before applying, or null to leave it as-is. Only valid
    *              on a selection list or tree — a range slider has no search box.
    */
   public Map<String, Object> setSelection(String sessionToken, Principal user, String assemblyName,
                                           List<List<String>> rawValues,
                                           List<List<String>> rawDeselect,
                                           String sortOrder, Boolean singleSelect, Boolean additive,
                                           String search, String linkUri)
      throws Exception
   {
      requireName(assemblyName);

      // A path named twice is one path: dedupe up front so every count, the overlap check and the
      // single-select guard see what the apply sees (bug-78268). Exact List.equals, the same
      // comparison the apply uses.
      final List<List<String>> values = distinctPaths(rawValues);
      final List<List<String>> deselect = distinctPaths(rawDeselect);

      boolean hasDeselect = deselect != null && !deselect.isEmpty();

      if(values == null && sortOrder == null && singleSelect == null && search == null &&
         !hasDeselect)
      {
         throw new IllegalArgumentException(
            "Nothing to do — give at least one of 'values', 'deselect', 'sortOrder', " +
            "'singleSelect' or 'search'.");
      }

      if(values != null && hasDeselect) {
         List<List<String>> overlap = new ArrayList<>(values);
         overlap.retainAll(deselect);

         if(!overlap.isEmpty()) {
            throw new IllegalArgumentException(
               "'values' and 'deselect' both name " + overlap + " — that's a contradiction: " +
               "select it or remove it, not both in the same call.");
         }
      }

      final Integer targetSort = sortOrder == null ? null : requireSortOrder(sortOrder);
      final Map<String, Object> result = new LinkedHashMap<>();

      List<String> warnings = sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         SelectionVSAssembly assembly = requireSelection(rvs, assemblyName);
         final Map<String, OtherSelection> othersBefore = snapshotOthers(rvs, assembly);

         if(assembly instanceof CalendarVSAssembly && (values != null || hasDeselect)) {
            // doApplySelection's Calendar branch only acts on a null event (its own "can only
            // un-select" clear), so a values/deselect apply here is a silent no-op that would
            // still report valuesSelected. A calendar's dates are not value paths -- their shape
            // depends on the calendar's mode -- so point at the tool that sets them for real.
            throw new IllegalArgumentException(
               "'" + assemblyName + "' is a calendar -- set_selection cannot select or deselect " +
               "its dates. Use set_calendar_dates to select dates, or clear_calendar / " +
               "clear_selection to clear them.");
         }

         // Only the values/deselect paths ever touch getConditionList()/getSelection() -- a
         // sortOrder-only or singleSelect-only call never reaches them, so it must not be
         // refused for a problem it does not have.
         if(values != null || hasDeselect) {
            requireBoundColumn(assembly, assemblyName);
         }

         SelectionVSAssemblyInfo info = (SelectionVSAssemblyInfo) assembly.getInfo();

         result.put("assembly", assemblyName);
         result.put("type", describe(assembly));

         // Every refusal that can be decided from the request and the current state is made here,
         // before the first write below (search string, selection style, sort, applies), so a
         // refused call leaves the assembly untouched (bug-78718). The relative order of the
         // checks, and their messages, are the same as when they ran interleaved with the writes.
         if(search != null && !(info instanceof SelectionBaseVSAssemblyInfo)) {
            throw new IllegalArgumentException(
               "'" + assemblyName + "' is " + describe(assembly) + ", which has no search box — " +
               "only a selection list or tree can be scoped by search. Drop 'search'.");
         }

         // Sort lives on SelectionBaseVSAssemblyInfo, which a range slider's info is not — a
         // slider orders by its underlying range, so there is nothing to sort.
         if(targetSort != null && !(info instanceof SelectionBaseVSAssemblyInfo)) {
            throw new IllegalArgumentException(
               "'" + assemblyName + "' is " + describe(assembly) + ", which has no sort order — " +
               "it follows its range. Drop 'sortOrder'.");
         }

         final boolean single = singleSelect != null ? singleSelect : info.isSingleSelection();
         final boolean idMode = assembly instanceof SelectionTreeVSAssembly tree && tree.isIDMode();
         // On a non-ID tree with single-select levels, doApplySelection keeps one value per single
         // level per parent and leaves the other levels multi (bug-78795); only a list, an ID-mode
         // tree, or a tree without effective levels is single as a whole. Read from the stored
         // levels, so it neither writes the info (getSingleSelectionLevels() does) nor misses
         // levels hidden while singleSelect:true is still to be switched on below.
         final List<Integer> singleLevels = singleLevelsOf(assembly, info, single, idMode);
         final boolean wholeTreeSingle = single && singleLevels.isEmpty();
         final List<List<String>> requested = singleLevels.isEmpty() || values == null ? values :
            dropPrefixPaths(values);
         int[] sliderRange = null;
         SelectionList domain = null;
         final List<Map<String, Object>> clamped = new ArrayList<>();

         if(values != null) {
            if(wholeTreeSingle && values.size() > 1) {
               throw new IllegalArgumentException(
                  "'" + assemblyName + "' is single-select, so it cannot hold " + values.size() +
                  " values. Select one value, or pass singleSelect=false in the same call to make " +
                  "it multi-select first.");
            }

            domain = isValueMatchable(assembly) ? selectionListOf(assembly) : null;

            if(isValueMatchable(assembly)) {
               // doApplySelection matches each requested value against the live domain and
               // silently drops anything that doesn't resolve -- no exception, no counter,
               // nothing the caller could observe. Refuse a typo'd value by name, atomically,
               // before applying anything, rather than reporting an inflated count for a
               // partially-applied result. A null domain (never rendered) cannot be checked, as
               // before; it is read once here and reused for the search filter below.
               requireResolvable(assemblyName, domain, values, idMode, "selecting");
            }

            if(!singleLevels.isEmpty()) {
               // After the resolvability check, so a typo'd value is named as such rather than
               // reported as a collision with its correctly spelled sibling.
               refuseSingleLevelCollision(assemblyName, requested, singleLevels,
                                          info.getLevelNames());
            }

            if(assembly instanceof TimeSliderVSAssembly slider) {
               // Pure: depends only on the slider's own buckets and the requested values.
               sliderRange = sliderBucketRange(assemblyName, bucketsOf(slider), values, clamped);
            }
         }

         if(hasDeselect) {
            if(!isPathDiffable(assembly)) {
               throw new IllegalArgumentException(
                  "'" + assemblyName + "' is " + describe(assembly) + " -- 'deselect' only works " +
                  "on a selection list or a non-ID-mode selection tree. Use clear_selection to " +
                  "remove everything, or set_selection's plain 'values' for a range slider.");
            }

            // Against the whole live domain, not the selected paths: a real but unselected value
            // stays a legal no-op, while a fabricated or over-long path is refused by name instead
            // of silently touching nothing. Unscoped by search, like the deselect itself.
            requireResolvable(assemblyName, selectionListOf(assembly), deselect, false,
                              "deselecting");
         }

         // Search first, same reasoning as selection style below: it changes how a values apply
         // is scoped (see scopedBySearch further down), so it has to be in place before that apply
         // runs, not after.
         if(search != null) {
            ((SelectionBaseVSAssemblyInfo) info).setSearchString(search);
            result.put("searchSet", search);
         }

         // Order matters. Selection style first: it changes how a value apply is interpreted
         // (single-select unselects siblings), so applying values under the old style and then
         // switching would leave a selection the caller did not ask for.
         if(singleSelect != null && singleSelect != info.isSingleSelection()) {
            selections.toggleSelectionStyle(runtimeId, assemblyName, user, dispatcher, linkUri);
            result.put("singleSelectChanged", true);
         }

         if(targetSort != null) {
            SelectionBaseVSAssemblyInfo sortable = (SelectionBaseVSAssemblyInfo) info;
            int cycles = sortCycles(sortable.getSortTypeValue(), targetSort);

            for(int i = 0; i < cycles; i++) {
               selections.sortSelection(runtimeId, assemblyName, new SortSelectionListEvent(),
                                        user, dispatcher, linkUri);
            }

            result.put("sortOrder", sortOrder);
            result.put("sortCycles", cycles);
         }

         // What the search-merge in VSSelectionService.applySelection put back after a replace or
         // deselect (bug-78216), collected from whichever branches below run.
         final List<List<String>> retained = new ArrayList<>();
         boolean applySkipped = false;

         // Snapshot for the single-select reselect report below: a single-select assembly cannot
         // stay empty, so the post-apply refresh re-selects its first value (bug-78717).
         // A tree with single-select levels may stay empty, so it only applies to whole-tree single.
         final boolean trackReselect = (values != null || hasDeselect) && isValueMatchable(assembly) &&
            wholeTreeSingle;
         final boolean unitIsId = assembly instanceof SelectionTreeVSAssembly idTree &&
            idTree.isIDMode();
         final List<List<String>> beforeSelected = !trackReselect ? List.of() :
            unitIsId ? selectedNodeIds(assembly) : selectedPaths(assembly);
         final List<List<String>> requestedValues = new ArrayList<>();

         if(values != null) {
            String activeSearch = searchString(info);
            List<List<String>> effectiveValues = requested;

            if(activeSearch != null && !activeSearch.isBlank()) {
               // Not a refusal: the apply is legitimate for whatever still matches, but
               // doApplySelection itself has no search-awareness at all -- it would otherwise
               // apply every requested value regardless of the active search (bug-76854). Filter
               // down to the subset SelectionValue.match (the same case-insensitive substring
               // rule the widget's own search box narrows by) would still show, and disclose
               // exactly what got dropped rather than a bare, unconditional echo of the search
               // string.
               List<List<String>> matching = filterBySearch(domain, requested, idMode, activeSearch);
               List<List<String>> dropped = new ArrayList<>(requested);
               dropped.removeAll(matching);

               if(!dropped.isEmpty()) {
                  result.put("scopedBySearch", activeSearch);
                  result.put("scopedBySearchDropped", dropped);
                  effectiveValues = matching;
               }
            }

            // A single-select request the search dropped entirely leaves the selection untouched:
            // an empty APPLY is the clear-all signal, after which single-select re-selects its
            // first value -- possibly the very value the search just dropped (bug-78785). An
            // explicit values:[] is a real clear and is not skipped.
            // Keyed on single, not wholeTreeSingle: a tree with single-select levels (which every
            // non-ID single tree now is, the legacy all-levels one included) is no more able to
            // pick a hidden value than a whole-tree single one, so a request the search empties is
            // a no-op there too -- neither the replace-diff deselect below nor the apply runs
            // (bug-78795).
            final boolean skipApply = single && !values.isEmpty() && effectiveValues.isEmpty();

            if(!skipApply && !wholeTreeSingle && isPathDiffable(assembly) &&
               !Boolean.TRUE.equals(additive))
            {
               // Multi-select apply is a delta patch -- doApplySelection only ever turns matched
               // values on, so anything currently selected but missing from the new values has to
               // be turned off explicitly, or it stays selected alongside them. Whole-tree
               // single-select already gets a full reset for free via unselectChildren; a tree with
               // single-select levels does not (doApplySelection only clears siblings on those
               // levels under the requested parents), so it is diffed too (bug-78795).
               // additive:true is the caller explicitly opting out of this replace behaviour --
               // see the javadoc above.
               // Diffs against effectiveValues (the post-search-filter set), not the raw request,
               // so a value the search dropped is not treated as "kept" and excluded from the
               // deselect it would otherwise need.
               // Under a search only the paths the widget shows are diffed: a search-hidden
               // selected path that shares a top-level ancestor with a visible match would be
               // dropped by VSSelectionService's merge-back once it is sent as selected=false,
               // unreported (bug-78983). deselectTargets still gets every selected path so a
               // target never collapses to a shared ancestor that would clear the hidden ones.
               // A tree with single-select levels keeps the full diff: its levels replace their
               // siblings by definition, which the search does not change.
               List<List<String>> currentPaths = selectedPaths(assembly);
               List<List<String>> diffed = singleLevels.isEmpty() ?
                  visibleWhenSearching(assembly, currentPaths) : currentPaths;
               List<List<String>> toRemove = keepAutoExtended(
                  toDeselect(diffed, effectiveValues), effectiveValues, singleLevels);

               if(!toRemove.isEmpty()) {
                  selections.applySelection(runtimeId, assemblyName,
                                            deselectEvent(deselectTargets(currentPaths, toRemove)),
                                            user, dispatcher, linkUri);
               }
            }

            if(assembly instanceof TimeSliderVSAssembly slider) {
               // doApplySelection's TimeSliderVSAssembly branch never reads event.getValues() --
               // it only reads event.getSelectStart()/getSelectEnd(), two bucket-index ints. A
               // plain value-array apply (what every other assembly type above uses) is a total
               // no-op here, silently: no exception, and the response still claims success.
               SelectionValue[] buckets = bucketsOf(slider);
               boolean rangeWasFull = isFullRangeSelected(buckets);
               int[] range = sliderRange;
               ApplySelectionListEvent event = new ApplySelectionListEvent();
               event.setType(ApplySelectionListEvent.Type.APPLY);
               event.setSelectStart(range[0]);
               event.setSelectEnd(adjustedEnd(slider.isUpperInclusive(), range[1]));
               selections.applySelection(runtimeId, assemblyName, event, user, dispatcher, linkUri);

               // The plugin cannot see the bucket flags, so report the range before and after
               // the apply and the bucket count: a range spanning every bucket filters nothing,
               // and "narrows" / "N values" would be false for it (bug-78264).
               SelectionValue[] after = bucketsOf(slider);
               result.put("rangeWasFull", rangeWasFull);
               result.put("fullRange", isFullRangeSelected(after));
               result.put("selectedBuckets", countSelected(after));
               result.put("totalBuckets", after.length);

               // The slider's own refresh may shorten the range the apply just selected (a span
               // wider than maxRangeSize), so compare what is selected now with the request.
               discloseNarrowedRange(after, range, slider.isUpperInclusive(),
                                     maxRangeSizeOf(slider), clamped);

               if(!clamped.isEmpty()) {
                  // A requested bound outside the slider's actual bucket range was silently
                  // substituted with the nearest one -- disclose which, the same way
                  // scopedBySearch discloses a write landing on a narrower scope than asked.
                  result.put("clampedBounds", clamped);
               }
            }
            else if(idMode && !wholeTreeSingle && !Boolean.TRUE.equals(additive)) {
               // An ID-mode tree has no path diff (see isPathDiffable), so replace by node id: the
               // leftovers ride in the same apply as selected=false, which doApplySelection honors
               // per value -- no transient empty state and a single refresh.
               SelectionList current = selectionListOf(assembly);
               selections.applySelection(
                  runtimeId, assemblyName,
                  idModeReplaceEvent(current == null ? null : current.getSelectionValues(),
                                     effectiveValues),
                  user, dispatcher, linkUri);
            }
            else if(!skipApply && !(Boolean.TRUE.equals(additive) && effectiveValues.isEmpty())) {
               // An empty APPLY is doApplySelection's clear-everything signal, so an additive call
               // that has nothing to add (values:[], or every value dropped by the search) must not
               // send one: "only add" is a no-op then, never a clear (bug-78718).
               selections.applySelection(runtimeId, assemblyName, applyEvent(effectiveValues), user,
                                         dispatcher, linkUri);
            }

            result.put("valuesSelected", effectiveValues.size());

            if(requested.size() < values.size()) {
               // Folded into the longer requested path that continues them (bug-78795).
               List<List<String>> collapsed = new ArrayList<>(values);
               collapsed.removeAll(requested);
               result.put("prefixPathsCollapsed", collapsed);
            }
            requestedValues.addAll(effectiveValues);
            applySkipped = skipApply;

            // A replace under an active search cannot clear a selected value the search hides:
            // VSSelectionService.applySelection adds every selected-but-filtered-out value back
            // (the filtered UI only sends the visible state). Measure what is left selected
            // after the apply rather than trusting the request (bug-78216).
            if(!skipApply && isValueMatchable(assembly) && !Boolean.TRUE.equals(additive) &&
               activeSearch != null && !activeSearch.isBlank())
            {
               SelectionList after = selectionListOf(assembly);
               retained.addAll(retainedSelection(after == null ? null : after.getSelectionValues(),
                                                 effectiveValues, idMode));
            }
         }

         if(hasDeselect) {
            // Deliberately not scoped by the active search the way values is above (bug-76854).
            // The charter's own repro only exercised values under search; scoping deselect too is
            // a reasonable extrapolation (a deselect names things by value the same way a select
            // does) but not a confirmed defect, and deselect's own semantics cut the other way --
            // "deselect a value the search box currently hides" is a plausible, useful call
            // (clearing a selection made before the caller searched), not obviously a mistake to
            // refuse. Left as its own, narrower-scoped follow-up rather than folded in here.
            // The path-diffable and resolvable refusals ran up front (bug-78718).
            List<List<String>> currentPaths = selectedPaths(assembly);
            List<List<String>> targets = deselectTargets(currentPaths, deselect);

            if(!targets.isEmpty()) {
               selections.applySelection(runtimeId, assemblyName, deselectEvent(targets), user,
                                         dispatcher, linkUri);
               // Not deselect.size() -- that would count every requested value regardless of
               // whether it was ever actually selected (bug-76701). Report how many of them
               // deselectTargets actually found a match for in currentPaths.
               // Measured after the apply: a value the active search hides is added back by
               // VSSelectionService.applySelection, so it is not deselected (bug-78216).
               List<List<String>> afterPaths = selectedPaths(assembly);
               // Counted as selected paths a requested entry touches that are really gone after
               // the apply, so duplicate or overlapping entries count once and a partly merged-back
               // branch counts only what was removed (bug-78268). Same leaf-path unit as
               // selectedTotal on a non-ID tree.
               // A requested path stays selected only if a surviving path equals or extends it;
               // a surviving self-only parent ([East] after [East, NY] was removed) is not it
               // (bug-78576).
               List<List<String>> stuck = deselect.stream()
                  .filter(path -> everSelected(currentPaths, path) &&
                     afterPaths.stream().anyMatch(p -> startsWithPath(p, path)))
                  .toList();
               result.put("deselected",
                          (int) currentPaths.stream()
                             .filter(p -> deselect.stream().anyMatch(r -> everSelected(List.of(p), r)))
                             .filter(p -> !afterPaths.contains(p))
                             .count());

               String activeSearch = searchString(info);

               if(activeSearch != null && !activeSearch.isBlank()) {
                  stuck.forEach(path -> {
                     if(!retained.contains(path)) {
                        retained.add(path);
                     }
                  });
               }
            }
         }

         if((values != null || hasDeselect) && isValueMatchable(assembly)) {
            SelectionList live = selectionListOf(assembly);
            result.put("selectedTotal",
                       selectedTotal(live == null ? null : live.getSelectionValues(),
                                     assembly instanceof SelectionTreeVSAssembly tree &&
                                        !tree.isIDMode()));

            if(!retained.isEmpty()) {
               result.putIfAbsent("scopedBySearch", searchString(info));
               result.put("scopedBySearchRetained", retained);
            }
         }

         if(trackReselect && !applySkipped) {
            List<List<String>> reselected = reselectedBySetSelection(
               beforeSelected, unitIsId ? selectedNodeIds(assembly) : selectedPaths(assembly),
               requestedValues, deselect == null ? List.of() : deselect, values != null,
               unitIsId);
            reselected = reselected.stream().filter(p -> !retained.contains(p)).toList();

            if(!reselected.isEmpty()) {
               result.put("reselectedBySingleSelect", reselected);
            }
         }
         reportOthersChanged(result, rvs, othersBefore, assembly);
      });
      ViewsheetSessionService.relayWarnings(result, warnings);

      result.put("persistsOnSave", true);
      return result;
   }

   /**
    * Clears every selection on the assembly.
    *
    * <p><b>Not the same as selecting every value, and the difference only shows up later.</b>
    * Clearing resets the state list to null, so {@code writeStateContent} writes no
    * {@code state_selectionList} block at all and the sheet reopens in its natural state. Selecting
    * every value writes a block enumerating each one, frozen at the values that existed when it was
    * written — so a value added to the data afterwards is <i>excluded</i> by that saved state, while
    * a cleared selection would have included it.
    */
   public Map<String, Object> clearSelection(String sessionToken, Principal user,
                                             String assemblyName, String linkUri)
      throws Exception
   {
      requireName(assemblyName);
      final Map<String, Object> result = new LinkedHashMap<>();

      List<String> warnings = sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         SelectionVSAssembly assembly = requireSelection(rvs, assemblyName);
         final Map<String, OtherSelection> othersBefore = snapshotOthers(rvs, assembly);
         requireBoundColumn(assembly, assemblyName);

         result.put("assembly", assemblyName);
         result.put("type", describe(assembly));

         if(assembly instanceof TimeSliderVSAssembly slider) {
            // "Nothing selected" on a range slider is represented as EVERY bucket marked
            // selected (the full range), not an empty/null state like a list/tree -- so the
            // list/tree clearedCount bookkeeping below (raw count of selected nodes) is
            // meaningless here: a genuinely untouched slider would misreport every one of its
            // buckets as "was filtering". And the list/tree deselect event is a no-op on a
            // slider for the same reason set_selection's plain value-array apply was (see
            // above) -- doApplySelection's TimeSlider branch never reads it -- so clearing an
            // actually-filtered slider needs its own full-range apply, not deselectEvent.
            SelectionValue[] buckets = bucketsOf(slider);
            boolean fullRange = isFullRangeSelected(buckets);
            result.put("clearedCount", fullRange ? 0 : countSelected(buckets));

            if(!fullRange && buckets.length > 0) {
               ApplySelectionListEvent event = new ApplySelectionListEvent();
               event.setType(ApplySelectionListEvent.Type.APPLY);
               event.setSelectStart(0);
               event.setSelectEnd(adjustedEnd(slider.isUpperInclusive(), buckets.length - 1));
               selections.applySelection(runtimeId, assemblyName, event, user, dispatcher, linkUri);
            }
         }
         else if(assembly instanceof CalendarVSAssembly) {
            // A calendar keeps its selection as dates on its info, not as a SelectionList, so
            // selectedPaths() below would always see nothing and send nothing. Clear it the way
            // clear_calendar does (CalendarDisplayService.clear). NOTE the argument order:
            // clearCalendar takes (..., principal, dispatcher, linkUri).
            String[] dates = ((CalendarVSAssemblyInfo) assembly.getVSAssemblyInfo()).getDates();
            int cleared = dates == null ? 0 : dates.length;
            result.put("clearedCount", cleared);

            if(cleared > 0) {
               calendars.clearCalendar(runtimeId, assemblyName, user, dispatcher, linkUri);
            }
         }
         else if(assembly instanceof SelectionTreeVSAssembly tree && tree.isIDMode()) {
            // selectedPaths()'s recursion only descends into a CompositeSelectionValue when the
            // composite itself is isSelected() -- correct for a fixed-hierarchy tree, where a
            // select always marks every ancestor along the way too, but wrong for an ID-mode
            // tree, where updateIDSelectionTree marks only the exact matched node(s) and never
            // propagates to ancestors (by design -- see its own doc comment: a node's value can
            // recur under different parents, so "parent selected" has no single meaning). A
            // selected subtree whose own root node isn't itself selected -- e.g. after
            // select_subtree, which deliberately leaves the ID-mode root unselected -- would make
            // selectedPaths() stop at that root and report nothing to clear at all. So an ID-mode
            // tree counts every selected node directly (countSelected has no isSelected() gate on
            // the recursion) and clears via a full wipe instead of a per-value deselect diff --
            // the same "empty values + APPLY" branch of doApplySelection that unselectAll already
            // uses (via event == null, the same branch per its own `||` guard) for a container's
            // native Clear gesture. clearedCount here therefore counts nodes (ancestors included
            // when independently selected), not leaf paths the way the non-ID branch's
            // selectedPaths()-based count does -- the two are not directly comparable.
            int cleared = countSelected(assembly);
            List<List<String>> beforeIds = selectedNodeIds(assembly);
            result.put("clearedCount", cleared);

            if(cleared > 0) {
               ApplySelectionListEvent event = new ApplySelectionListEvent();
               event.setType(ApplySelectionListEvent.Type.APPLY);
               selections.applySelection(runtimeId, assemblyName, event, user, dispatcher,
                                         linkUri);
               reportRetainedAfterClear(result, assembly, beforeIds, selectedNodeIds(assembly),
                                        true);
            }
         }
         else {
            List<List<String>> current = selectedPaths(assembly);
            result.put("clearedCount", current.size());

            if(!current.isEmpty()) {
               // The client composes "unselect" the same way: send every currently selected
               // value back with selected=false. There is no single clear endpoint for one
               // assembly. Cleared via deselectTargets (not the leaf paths directly) for the
               // same reason set_selection's own diff-deselect does -- see that javadoc.
               List<List<String>> targets = deselectTargets(current,
                  visibleWhenSearching(assembly, current));

               if(!targets.isEmpty()) {
                  selections.applySelection(runtimeId, assemblyName, deselectEvent(targets), user,
                                            dispatcher, linkUri);
               }

               reportRetainedAfterClear(result, assembly, current, selectedPaths(assembly), false);
            }
         }
         reportOthersChanged(result, rvs, othersBefore, assembly);
      });
      ViewsheetSessionService.relayWarnings(result, warnings);

      result.put("persistsOnSave", true);
      return result;
   }

   /**
    * Re-states clear_selection's result from the post-apply state. With an active search,
    * {@code VSSelectionService.applySelection} adds back every selected value the search hides --
    * the interactive Clear button does the same -- so the pre-apply count overstates what was
    * removed (bug-78294). A single-select assembly cannot stay empty either: the post-apply
    * refresh selects its first value, so comparing counts would report "nothing cleared" for a
    * selection that merely moved (bug-78566). {@code before} and {@code after} are therefore
    * compared by identity and must be in the same unit.
    */
   private static void reportRetainedAfterClear(Map<String, Object> result,
                                                SelectionVSAssembly assembly,
                                                List<List<String>> before,
                                                List<List<String>> after, boolean idMode)
   {
      result.put("clearedCount", clearedCount(before, after));
      result.put("selectedTotal", after.size());

      SelectionVSAssemblyInfo info = (SelectionVSAssemblyInfo) assembly.getInfo();
      String search = searchString(info);
      boolean searching = search != null && !search.isBlank();

      if(info.isSingleSelection() && !after.isEmpty()) {
         List<List<String>> reselected = reselectedAfterClear(before, after);

         if(reselected.isEmpty() && !searching) {
            reselected = after;
         }

         if(!reselected.isEmpty()) {
            result.put("reselectedBySingleSelect", reselected);
         }
      }

      if(!after.isEmpty() && searching) {
         SelectionList live = selectionListOf(assembly);
         result.put("scopedBySearch", search);
         result.put("scopedBySearchRetained",
                    retainedSelection(live == null ? null : live.getSelectionValues(),
                                      List.of(), idMode));
      }
   }

   /**
    * The {@code selected} paths of a non-ID selection tree that its active search still shows, so
    * a clear removes what the widget shows and leaves the search-hidden selection alone
    * (bug-78787). Resolved against the same {@code findAll(search,false)} tree as
    * {@link #filterBySearch}. Every other assembly, or no search, returns {@code selected}.
    */
   private static List<List<String>> visibleWhenSearching(SelectionVSAssembly assembly,
                                                          List<List<String>> selected)
   {
      String search = searchString((SelectionVSAssemblyInfo) assembly.getInfo());

      if(!(assembly instanceof SelectionTreeVSAssembly tree) || tree.isIDMode() ||
         search == null || search.isBlank())
      {
         return selected;
      }

      return filterBySearch(selectionListOf(assembly), selected, false, search);
   }

   /** How many of the {@code before} values are no longer selected, by identity. */
   static int clearedCount(List<List<String>> before, List<List<String>> after) {
      Set<List<String>> remaining = new HashSet<>(after);
      return (int) before.stream().distinct().filter(v -> !remaining.contains(v)).count();
   }

   /** The values selected after a clear that were not selected before it. */
   static List<List<String>> reselectedAfterClear(List<List<String>> before,
                                                  List<List<String>> after)
   {
      Set<List<String>> previous = new HashSet<>(before);
      return after.stream().filter(v -> !previous.contains(v)).toList();
   }

   /**
    * The values selected after a set_selection on a single-select assembly that the caller did not
    * ask for. A single-select assembly cannot stay empty, so removing its only value (values:[]
    * or deselect) makes the refresh select the first value instead -- which can be the very
    * value that was just deselected (bug-78717). Compared by value, not by count: an unrequested
    * after-value is reported when it is new, when {@code replaced} (a values list replaces the
    * whole selection), or when a deselect names it.
    *
    * @param requested the values the apply selected (empty for values:[] or deselect only).
    * @param idMode    {@code before}/{@code after} hold single node ids, and a requested or
    *                  deselected entry matches when it contains that id.
    */
   static List<List<String>> reselectedBySetSelection(List<List<String>> before,
                                                      List<List<String>> after,
                                                      List<List<String>> requested,
                                                      List<List<String>> deselected,
                                                      boolean replaced, boolean idMode)
   {
      List<List<String>> reselected = new ArrayList<>();

      for(List<String> path : after) {
         if(covers(requested, path, idMode)) {
            continue;
         }

         if(replaced || !before.contains(path) || covers(deselected, path, idMode)) {
            reselected.add(path);
         }
      }

      return reselected;
   }

   private static boolean covers(List<List<String>> entries, List<String> path, boolean idMode) {
      return entries.stream().anyMatch(e -> idMode ? e.contains(path.get(0)) :
                                          startsWithPath(path, e));
   }

   /**
    * What a single-select tree selected in place of the values a subtree clear removed. Judged on
    * the whole tree, because the refresh can land outside the cleared subtree; with the same
    * "first value was already the selection" fallback {@link #reportRetainedAfterClear} has.
    */
   static List<List<String>> reselectedAfterSubtreeClear(List<List<String>> wholeBefore,
                                                         List<List<String>> wholeAfter,
                                                         List<List<String>> subtreeBefore,
                                                         List<List<String>> subtreeAfter,
                                                         boolean searching)
   {
      List<List<String>> reselected = reselectedAfterClear(wholeBefore, wholeAfter);

      if(reselected.isEmpty() && !searching && !subtreeBefore.isEmpty()) {
         reselected = subtreeAfter;
      }

      return reselected;
   }

   /** The values selected before a subtree clear, outside the subtree, that are gone after it. */
   static List<List<String>> removedOutsideSubtree(List<List<String>> wholeBefore,
                                                   List<List<String>> wholeAfter,
                                                   List<List<String>> subtreeBefore)
   {
      Set<List<String>> kept = new HashSet<>(wholeAfter);
      kept.addAll(subtreeBefore);
      return wholeBefore.stream().filter(v -> !kept.contains(v)).toList();
   }

   /**
    * Re-states a subtree clear from the post-apply state, scoped to the subtree. With an active
    * search, {@code VSSelectionService.selectSubtree} merges back every selected value the search
    * hides, so a clear can leave values behind (bug-78349); a branch with nothing selected clears
    * nothing at all. {@code clearedCount} and {@code remainingUnderSubtree} count the subtree's own
    * selected values (leaf paths on a fixed-hierarchy tree, descendant node ids on an ID-mode
    * tree, whose subtree root is never deselected and is not counted).
    */
   private static void reportSubtreeClear(Map<String, Object> result,
                                          SelectionVSAssembly assembly, List<String> path,
                                          List<List<String>> before,
                                          List<List<String>> wholeBefore, boolean idMode)
   {
      List<List<String>> after = selectedUnderSubtree(selectionListOf(assembly), path, idMode);
      // By identity, not count: a single-select tree re-selects a value inside the same subtree
      // after the clear, which a count reads as "nothing cleared" (bug-78717).
      result.put("clearedCount", clearedCount(before, after));
      result.put("remainingUnderSubtree", after.size());
      SelectionVSAssemblyInfo info = (SelectionVSAssemblyInfo) assembly.getInfo();
      String search = searchString(info);
      List<List<String>> wholeAfter = idMode ? selectedNodeIds(assembly) : selectedPaths(assembly);
      // A clear changes nothing outside its subtree; a value that vanished there was lost, and
      // "no change" would hide it (bug-78869).
      List<List<String>> removedOutside = removedOutsideSubtree(wholeBefore, wholeAfter, before);

      if(!removedOutside.isEmpty()) {
         result.put("removedOutsideSubtree", removedOutside);
      }

      if(info.isSingleSelection()) {
         List<List<String>> reselected = reselectedAfterSubtreeClear(
            wholeBefore, wholeAfter, before, after, search != null && !search.isBlank());

         if(!reselected.isEmpty()) {
            result.put("reselectedBySingleSelect", reselected);
         }
      }

      if(!after.isEmpty() && search != null && !search.isBlank()) {
         result.put("scopedBySearch", search);
         result.put("scopedBySearchRetained", after);
      }
   }

   /**
    * The selected values under the subtree rooted at {@code path}. The root resolves positionally
    * from the top, exactly as {@code VSSelectionService.findSubtreeRoot}/{@code findIDSubtreeRoot}
    * do. A fixed-hierarchy tree returns the selected leaf paths that start with {@code path}; an
    * ID-mode tree returns one single-element path per selected descendant node id, root excluded.
    */
   static List<List<String>> selectedUnderSubtree(SelectionList list, List<String> path,
                                                  boolean idMode)
   {
      SelectionValue[] values = list == null ? null : list.getSelectionValues();

      if(!idMode) {
         return selectedPaths(values).stream()
            .filter(p -> p.size() >= path.size() && p.subList(0, path.size()).equals(path))
            .toList();
      }

      SelectionValue root = null;
      SelectionValue[] level = values;

      for(int i = 0; i < path.size() && level != null; i++) {
         root = null;

         for(SelectionValue candidate : level) {
            if(candidate != null && Objects.equals(candidate.getValue(), path.get(i))) {
               root = candidate;
               break;
            }
         }

         if(root == null) {
            return List.of();
         }

         SelectionList children = root instanceof CompositeSelectionValue composite ?
            composite.getSelectionList() : null;
         level = children == null ? null : children.getSelectionValues();
      }

      return selectedNodeIds(level).stream().map(List::of).toList();
   }

   /**
    * Selects or clears a whole subtree of a selection tree.
    *
    * @param path the subtree root, as a path from the tree's root.
    * @param mode {@code select} or {@code clear}.
    */
   public Map<String, Object> selectSubtree(String sessionToken, Principal user, String assemblyName,
                                            List<String> path, String mode, String linkUri)
      throws Exception
   {
      requireName(assemblyName);

      if(path == null || path.isEmpty()) {
         throw new IllegalArgumentException(
            "'path' is required — the subtree's root, as a path from the tree's root, e.g. " +
            "[\"East\"] or [\"East\",\"NY\"].");
      }

      final boolean select = requireSubtreeMode(mode);
      final Map<String, Object> result = new LinkedHashMap<>();

      List<String> warnings = sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         SelectionVSAssembly assembly = requireSelection(rvs, assemblyName);
         final Map<String, OtherSelection> othersBefore = snapshotOthers(rvs, assembly);

         if(!(assembly instanceof SelectionTreeVSAssembly)) {
            throw new IllegalArgumentException(
               "'" + assemblyName + "' is " + describe(assembly) + ", and subtrees only exist on a " +
               "selection tree. Use set_selection for a list or a range slider.");
         }

         requireBoundColumn(assembly, assemblyName);

         SelectionTreeVSAssembly tree = (SelectionTreeVSAssembly) assembly;

         // findSubtreeRoot/findIDSubtreeRoot (VSSelectionService) both resolve path positionally
         // from the root -- an exact match at each level, descending into a
         // CompositeSelectionValue's own children -- for a non-ID-mode AND an ID-mode tree alike.
         // A subtree apply is not the flat "does this value appear anywhere in the tree" scan
         // updateIDSelectionTree/missingIds use for setSelection's own idMode branch; that
         // scan belongs to a values apply, not a subtree one. So the same positional predicate
         // (idMode=false) validates both tree shapes here, reusing the domain-existence check
         // already shipped for setSelection's values-mode fix (bug 76544): refuse a path that
         // resolves to nothing in the live tree -- for both select and clear -- before applying
         // anything, rather than a silent no-op reported back as ok:true.
         List<List<String>> unmatched = findUnmatchedPaths(selectionListOf(assembly),
            List.of(path), false);

         if(!unmatched.isEmpty()) {
            if(tree.isIDMode()) {
               throw new IllegalArgumentException(
                  "'" + assemblyName + "' has no subtree at " + path +
                  idModeChainHint(selectionListOf(assembly), path));
            }

            throw new IllegalArgumentException(
               "'" + assemblyName + "' has no subtree at " + path +
               unresolvedDetail(selectionListOf(assembly), unmatched, false) +
               " -- confirm the exact spelling via browse_condition_values before selecting it.");
         }

         // The native UI hides Select Subtree on a single-select ID-hierarchy tree
         // (selection-tree-actions.ts), and the engine cannot apply it there: setSubtree stops at
         // the first unselected node, and the single-select query keeps only the first selected
         // node of whatever is written. Every such select is a silent no-op, at any path depth.
         if(select && tree.isIDMode()
            && ((SelectionVSAssemblyInfo) tree.getInfo()).isSingleSelection())
         {
            throw new IllegalArgumentException(
               "'" + assemblyName + "' is a single-select ID-hierarchy tree, which has no subtree " +
               "select (the native UI hides Select Subtree for it), so selecting the subtree at " +
               String.join(" > ", path) + " cannot apply. Use set_selection with values:[<id>] to " +
               "select one node, or pass singleSelect:false on set_selection first to select " +
               "several.");
         }

         // A multi-select ID-mode tree needs no depth guard: path.size() does not track levels
         // there, and a subtree select selects every descendant.
         if(select && !tree.isIDMode()) {
            refuseAmbiguousSingleSelectSubtree(tree, assemblyName, path);
         }

         result.put("assembly", assemblyName);
         result.put("path", path);
         result.put("mode", select ? "select" : "clear");

         ApplySelectionListEvent event = new ApplySelectionListEvent();
         event.setType(ApplySelectionListEvent.Type.APPLY);
         ApplySelectionListEvent.Value value = new ApplySelectionListEvent.Value();
         value.setValue(path.toArray(new String[0]));
         value.setSelected(select);
         event.setValues(List.of(value));

         if(select && !tree.isIDMode()) {
            SelectionVSAssemblyInfo info = (SelectionVSAssemblyInfo) tree.getInfo();
            List<Integer> singleLevels =
               singleLevelsOf(tree, info, info.isSingleSelection(), false);

            if(!singleLevels.isEmpty()) {
               // A leaf select on a single-select level is a replace on that level, like
               // setSelection's: the apply below clears only what the tree itself shows selected,
               // but peer assemblies on the same table and columns share one selection set, and
               // the single-select refresh keeps the first leaf of it -- a peer's selected leaf
               // that sorts before the requested one wins over it (bug-78868). Deselect what
               // conflicts with the request on a single level first; selections that differ only
               // on multi-select levels stay (the subtree select is additive there).
               List<List<String>> currentPaths = selectedPaths(tree);
               List<List<String>> toRemove = singleLevelConflicts(
                  keepAutoExtended(toDeselect(currentPaths, List.of(path)), List.of(path),
                                   singleLevels),
                  path, singleLevels);

               if(!toRemove.isEmpty()) {
                  selections.applySelection(runtimeId, assemblyName,
                                            deselectEvent(deselectTargets(currentPaths, toRemove)),
                                            user, dispatcher, linkUri);
               }
            }
         }

         List<List<String>> before = select ? List.of() :
            selectedUnderSubtree(selectionListOf(assembly), path, tree.isIDMode());

         List<List<String>> wholeBefore = select ? List.of() :
            tree.isIDMode() ? selectedNodeIds(assembly) : selectedPaths(assembly);

         String subtreeSearch = searchString((SelectionVSAssemblyInfo) tree.getInfo());

         if(!select && subtreeSearch != null && !subtreeSearch.isBlank()) {
            // selectSubtree clears the whole subtree, hidden nodes included, and then relies on a
            // merge-back that loses a search-hidden selection sharing a top-level ancestor with a
            // visible match (bug-78787, bug-78983). Like the widget, send only the visible
            // selected values. An ID-mode tree deselects the visible node ids one by one, as
            // idModeReplaceEvent does; its subtree root is not in before and is never deselected.
            List<List<String>> targets = tree.isIDMode() ?
               filterBySearch(selectionListOf(assembly), before, true, subtreeSearch) :
               deselectTargets(wholeBefore, visibleWhenSearching(assembly, before));

            if(!targets.isEmpty()) {
               selections.applySelection(runtimeId, assemblyName, deselectEvent(targets), user,
                                         dispatcher, linkUri);
            }
         }
         else {
            // selectSubtree NPEs on an unknown name and CCEs on a non-tree; both are refused above.
            selections.selectSubtree(runtimeId, assemblyName, event, user, dispatcher, linkUri);
         }

         if(select) {
            List<List<String>> selectedUnder =
               selectedUnderSubtree(selectionListOf(assembly), path, tree.isIDMode());
            result.put("selectedCount", selectedUnder.size());

            if(!tree.isIDMode()) {
               // Only after the apply: the selection has changed by now, so the refusal says so.
               requirePathSelected(assemblyName, path, selectionListOf(assembly));
               result.put("selectedUnderSubtree", selectedUnder);
            }
         }
         else {
            reportSubtreeClear(result, assembly, path, before, wholeBefore, tree.isIDMode());
         }
         reportOthersChanged(result, rvs, othersBefore, assembly);
      });
      ViewsheetSessionService.relayWarnings(result, warnings);

      result.put("persistsOnSave", true);
      return result;
   }

   /**
    * The first node on {@code path} that is missing or not selected, as the path down to it, or
    * {@code null} when every node on {@code path} is selected. The path resolves positionally from
    * the top, as {@code VSSelectionService.findSubtreeRoot} does, so this is for a fixed-hierarchy
    * tree only. An ID-mode tree never marks ancestors on a select, so its path nodes say nothing.
    * Only the path's own nodes are judged: a descendant can legitimately stay unselected (excluded
    * by an association, hidden by a search) after a valid subtree select.
    */
   static List<String> firstUnselectedOnPath(SelectionValue[] values, List<String> path) {
      SelectionValue[] level = values;

      for(int i = 0; i < path.size(); i++) {
         SelectionValue match = null;

         for(SelectionValue candidate : level == null ? new SelectionValue[0] : level) {
            if(candidate != null && Objects.equals(candidate.getValue(), path.get(i))) {
               match = candidate;
               break;
            }
         }

         if(match == null || !match.isSelected()) {
            return path.subList(0, i + 1);
         }

         SelectionList children = match instanceof CompositeSelectionValue composite ?
            composite.getSelectionList() : null;
         level = children == null ? null : children.getSelectionValues();
      }

      return null;
   }

   /**
    * Refuses a subtree select whose own path is not selected after the apply, rather than
    * reporting the different selection the single-select refresh left as ok:true. Re-selecting an
    * already selected path passes.
    */
   private static void requirePathSelected(String assemblyName, List<String> path,
                                           SelectionList list)
   {
      if(list == null) {
         return;
      }

      List<String> unselected = firstUnselectedOnPath(list.getSelectionValues(), path);

      if(unselected != null) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' did not keep " + String.join(" > ", unselected) +
            " selected after selecting the subtree at " + String.join(" > ", path) +
            ", and the selection has already changed. Read it with get_selection_state and set " +
            "the selection you want with set_selection values:[[...]].");
      }
   }

   /**
    * Every candidate value of a selection list or selection tree, annotated with its live
    * Association-narrowed state -- what StyleBI's own "Select All" toolbar action treats as
    * still selectable, and what it grays out. NOT available on a range slider, which has no
    * per-value included/excluded/compatible state (see class javadoc).
    *
    * <p>Never cached across calls -- this reads whatever the live RuntimeViewsheet's
    * SelectionList/CompositeSelectionValue tree currently holds, and that changes the moment any
    * OTHER selection filter on the same viewsheet changes.
    */
   public Map<String, Object> selectionState(String sessionToken, Principal user, String assemblyName)
      throws Exception
   {
      requireName(assemblyName);
      RuntimeViewsheet rvs = sessions.resolve(sessionToken, user);
      SelectionVSAssembly assembly = requireSelection(rvs, assemblyName);

      if(assembly instanceof TimeSliderVSAssembly) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is a range slider, which has no per-value included/excluded/" +
            "compatible state -- it selects a numeric bucket range, not discrete candidate values. " +
            "get_selection_state only applies to a selection list, selection tree or calendar.");
      }

      if(assembly instanceof CalendarVSAssembly) {
         return calendarState(assemblyName, assembly);
      }

      SelectionList list = selectionListOf(assembly);
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("assembly", assemblyName);
      result.put("type", describe(assembly));
      result.put("associationEnabled", rvs.getViewsheet().getViewsheetInfo().isAssociationEnabled());
      result.put("computed", list != null);

      List<Map<String, Object>> values = selectionState(
         list == null ? null : list.getSelectionValues());
      result.put("values", values);
      result.put("totalValues", countAll(values));
      return result;
   }

   /**
    * A calendar has no candidate set: its selection is the dates on its info, so this answers
    * with those instead of the list-shaped {@code values}/{@code computed}/{@code totalValues}
    * (which would read as "not computed yet"). {@code dates} holds ISO strings for day tokens --
    * the form set_calendar_dates takes -- and year/month/week tokens verbatim, since no ISO date
    * can express them; {@code tokens} holds the raw stored tokens.
    */
   private static Map<String, Object> calendarState(String assemblyName,
                                                    SelectionVSAssembly assembly)
   {
      CalendarVSAssemblyInfo info = (CalendarVSAssemblyInfo) assembly.getVSAssemblyInfo();
      String[] tokens = info.getDates() == null ? new String[0] : info.getDates();
      boolean dual = info.getViewMode() == CalendarVSAssemblyInfo.DOUBLE_CALENDAR_MODE;
      boolean period = dual && info.isPeriod();

      List<String> dates = new ArrayList<>();

      for(String token : tokens) {
         dates.add(dayTokenToIso(token));
      }

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("assembly", assemblyName);
      result.put("type", describe(assembly));
      result.put("mode", period ? "period" : dual ? "range" : "single");
      result.put("yearView", info.isYearView());
      result.put("dates", dates);
      result.put("tokens", Arrays.asList(tokens));
      return result;
   }

   /** {@code d2025-2-12} (0-based month) to {@code 2025-03-12}; any other token is returned as is. */
   private static String dayTokenToIso(String token) {
      if(token == null || !token.startsWith("d")) {
         return token;
      }

      String[] parts = token.substring(1).split("-");

      if(parts.length != 3) {
         return token;
      }

      return String.format("%04d-%02d-%02d", Integer.parseInt(parts[0]),
                           Integer.parseInt(parts[1]) + 1, Integer.parseInt(parts[2]));
   }

   // ── guards ────────────────────────────────────────────────────────────────

   private static void requireName(String assemblyName) {
      if(assemblyName == null || assemblyName.isBlank()) {
         throw new IllegalArgumentException("'assembly' is required — name the selection assembly.");
      }
   }

   /**
    * Resolves and type-checks before any endpoint is touched, because the endpoints answer a bad
    * name with silence, an NPE or a CCE depending on which one you call.
    *
    * <p>Public (reuse seam): {@code SelectionBindingService} type-checks the same four assembly
    * classes before it can bind a column to any of them, so it shares this rather than keeping a
    * second copy that could drift.
    */
   public static SelectionVSAssembly requireSelection(RuntimeViewsheet rvs, String assemblyName) {
      Viewsheet vs = rvs == null ? null : rvs.getViewsheet();
      VSAssembly assembly = vs == null ? null : vs.getAssembly(assemblyName);

      if(assembly == null) {
         throw new IllegalArgumentException(
            "Unknown assembly '" + assemblyName + "'. The selection endpoints answer an unknown " +
            "name with success and no change, or an internal error, so this is refused here.");
      }

      if(!(assembly instanceof SelectionVSAssembly selection)) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is a " + assembly.getClass().getSimpleName() +
            ", not a selection assembly. Selections exist on selection lists, selection trees and " +
            "range sliders.");
      }

      return selection;
   }

   /**
    * Refuses a selection list or tree that has no column bound. {@code SelectionListVSAssembly}'s
    * {@code getConditionList()}/{@code getSelection()} and {@code SelectionTreeVSAssembly}'s
    * (non-ID-mode) equivalents short-circuit to null/false whenever the column is unbound, so
    * without this guard select/deselect/clear all report success while never actually writing a
    * filter -- see bug-76701. {@code TimeSliderVSAssembly}'s column binding is a different
    * mechanism (a range/composite time info, not a single {@code DataRef}) and is left alone.
    */
   private static void requireBoundColumn(SelectionVSAssembly assembly, String assemblyName) {
      if(assembly instanceof SelectionListVSAssembly list && list.getDataRef() == null) {
         throw new IllegalArgumentException(unboundColumnMessage(assemblyName));
      }

      if(assembly instanceof SelectionTreeVSAssembly tree) {
         DataRef[] refs = tree.getDataRefs();

         if(refs == null || refs.length == 0 || Arrays.stream(refs).allMatch(Objects::isNull)) {
            throw new IllegalArgumentException(unboundColumnMessage(assemblyName));
         }
      }
   }

   private static String unboundColumnMessage(String assemblyName) {
      return "'" + assemblyName + "' has no column bound -- select/deselect/clear would silently " +
         "do nothing against it. Call set_selection_source first (or confirm its binding actually " +
         "took effect via get_assembly_properties).";
   }

   /**
    * Refuses {@code select_subtree(mode:"select")} on a whole-tree single-selection tree when the
    * named path is not already a leaf -- see bug-76758 (VFL-004).
    *
    * <p>{@code VSSelectionService.setSubtree}'s own recursive walk has an early return —
    * {@code if(selected && treeInfo.isSingleSelection() && treeInfo.containsLevel(level) &&
    * !value.isSelected())} — that, once {@code isSingleSelection()} is true, fires for every level
    * ({@code containsLevel} auto-populates every level when single-selection is not "mixed" per
    * level). That silently walks only the first child at each level, collapsing a "select this
    * whole branch" request down to one arbitrary leaf while still reporting {@code ok:true}.
    *
    * <p>Deliberately not fixed by changing that shared algorithm: it is also what the live
    * interactive UI's own "select subtree" context-menu action depends on (see
    * {@code VSSelectionListController}/{@code selection-tree-controller.ts}), and there is no
    * evidence the collapse is new there rather than long-standing. Refusing here only narrows this
    * agent-only entry point.
    *
    * <p><b>Deliberately conservative, not an exact leaf count.</b> Telling whether {@code path}
    * names exactly one leaf would mean walking the assembly's actual {@code SelectionList} --
    * which cannot be constructed or mocked in a plain unit test (confirmed by
    * {@code aSelectedCompositeWithNoSelectedChildrenStillProducesASelfOnlyPath}'s own note; Mockito
    * throws instrumenting it). Comparing {@code path}'s length against the tree's own level count
    * needs no such walk, and is exactly right for this bug's repro (and the overwhelmingly common
    * case): a path shorter than the tree's depth names an ancestor, which the recursive collapse
    * above always mishandles once it has more than one descendant. The one case this over-refuses
    * -- an ancestor whose every level below happens to hold exactly one value, so it is not
    * actually ambiguous -- is rare enough, and cheap enough to work around (name the leaf path
    * directly), to accept for a low-priority guard.
    *
    * <p><b>Not called at all for an ID-mode tree</b> -- an ID-mode single-select tree is refused
    * outright by the caller, and a multi-select one needs no depth guard.
    */
   private static void refuseAmbiguousSingleSelectSubtree(SelectionTreeVSAssembly tree,
                                                          String assemblyName, List<String> path)
   {
      SelectionTreeVSAssemblyInfo info = tree.getSelectionTreeInfo();

      if(!info.isSingleSelection()) {
         return;
      }

      DataRef[] refs = tree.getDataRefs();
      refuseAmbiguousSingleSelectSubtree(refs == null ? 0 : refs.length, assemblyName, path);
   }

   /**
    * Split out from {@link #refuseAmbiguousSingleSelectSubtree(SelectionTreeVSAssembly, String,
    * List)} so it is testable without a real assembly -- only called once the caller already knows
    * {@code info.isSingleSelection()} is true.
    */
   static void refuseAmbiguousSingleSelectSubtree(int treeDepth, String assemblyName,
                                                   List<String> path)
   {
      if(path.size() >= treeDepth) {
         return; // a leaf path names one node; selectSubtree deselects its single-level rivals.
      }

      throw new IllegalArgumentException(
         "'" + assemblyName + "' is single-select, so selecting the whole subtree at " +
         String.join(" > ", path) + " -- " + (treeDepth - path.size()) + " level(s) short of a " +
         "leaf -- could silently collapse to just one descendant instead of the whole branch. " +
         "Name a full path down to a leaf instead, or pass singleSelect:false on set_selection " +
         "first to widen it.");
   }

   /**
    * The single-select levels of a non-ID tree that is, or is about to be made, single-select;
    * empty for anything else, and for a tree that is single-select as a whole (see
    * {@link #effectiveSingleLevels}).
    */
   private static List<Integer> singleLevelsOf(SelectionVSAssembly assembly,
                                               SelectionVSAssemblyInfo info, boolean single,
                                               boolean idMode)
   {
      return single && !idMode && assembly instanceof SelectionTreeVSAssembly ?
         effectiveSingleLevels(info.getStoredSingleSelectionLevels(),
                               info.isMixedSingleSelection(), info.getLevelNames().size()) :
         List.of();
   }

   /**
    * The paths in {@code toRemove} that pick a different value than {@code requested} on a
    * single-select level: the first index where they part is a single level, so the level can hold
    * only one of them. Paths that part on a multi-select level can coexist with the request.
    */
   static List<List<String>> singleLevelConflicts(List<List<String>> toRemove,
                                                  List<String> requested,
                                                  Collection<Integer> singleLevels)
   {
      return toRemove.stream().filter(p -> {
         int n = Math.min(p.size(), requested.size());
         int d = 0;

         while(d < n && Objects.equals(p.get(d), requested.get(d))) {
            d++;
         }

         return d < n && singleLevels.contains(d);
      }).toList();
   }

   /**
    * The levels {@code doApplySelection} will treat as single-select on a non-ID tree once the
    * tree is single-select, computed from the stored levels the way
    * {@code SelectionVSAssemblyInfo.getSingleSelectionLevels()} does -- stored levels, or every
    * level when none are stored and they were not edited level by level -- but without that
    * getter's write, and also while the flag is still off (bug-78795). Sorted and distinct; the
    * stored order is not trusted. A stored index outside {@code [0, depth)} is dropped: levels go
    * stale when the tree is rebound to fewer columns, and one past the tree would let
    * {@link #keepAutoExtended} keep branches it should not. Empty means the tree is single-select
    * as a whole.
    */
   static List<Integer> effectiveSingleLevels(List<Integer> stored, boolean mixed, int depth) {
      TreeSet<Integer> levels = new TreeSet<>();

      if(stored != null && !stored.isEmpty()) {
         stored.stream().filter(l -> l != null && l >= 0 && l < depth).forEach(levels::add);
      }
      else if(!mixed) {
         for(int i = 0; i < depth; i++) {
            levels.add(i);
         }
      }

      return new ArrayList<>(levels);
   }

   /**
    * Drops every requested path that is a strict prefix of another requested path. On a tree with
    * single-select levels the runtime would extend such a path to the deepest single level with a
    * child of its own choosing, which can collide with the longer path; the longer path already
    * selects the shorter one's nodes (bug-78795). Order-preserving.
    */
   static List<List<String>> dropPrefixPaths(List<List<String>> paths) {
      return paths.stream()
         .filter(p -> paths.stream().noneMatch(q -> q.size() > p.size() && startsWithPath(q, p)))
         .toList();
   }

   /**
    * Refuses two requested paths that pick different values on a single-select level under the
    * same parent: they share {@code p[0..d-1]} and differ at index {@code d}, a single level. That
    * is the only thing per-level single selection forbids -- a single level holds one value per
    * parent, and the other levels stay multi-select (bug-78795). doApplySelection clears siblings
    * per requested value before applying them all, so two such values in one call would both end
    * up selected; this check is the only protection.
    *
    * @param paths        the requested paths, already past {@link #dropPrefixPaths}.
    * @param singleLevels the single-select level indexes, from {@link #effectiveSingleLevels}.
    * @param levelNames   the tree's level names, for the message.
    */
   static void refuseSingleLevelCollision(String assemblyName, List<List<String>> paths,
                                          Collection<Integer> singleLevels,
                                          List<String> levelNames)
   {
      for(int i = 0; i < paths.size(); i++) {
         for(int j = i + 1; j < paths.size(); j++) {
            List<String> a = paths.get(i);
            List<String> b = paths.get(j);
            int n = Math.min(a.size(), b.size());
            int d = 0;

            while(d < n && Objects.equals(a.get(d), b.get(d))) {
               d++;
            }

            if(d == n || !singleLevels.contains(d)) {
               continue; // one continues the other, or they part on a multi-select level
            }

            String level = levelNames != null && d < levelNames.size() ?
               levelNames.get(d) : "level " + (d + 1);
            String parent = d == 0 ? "at the top level" :
               "under " + String.join(" > ", a.subList(0, d));

            throw new IllegalArgumentException(
               "'" + assemblyName + "': level '" + level + "' is single-select, so it holds one " +
               "value per parent, but " + a + " and " + b + " pick two '" + level + "' values " +
               parent + ". Keep one of them, or change which levels are single-select with " +
               "set_assembly_properties singleSelectionLevels. singleSelect=false in the same " +
               "call also allows both, but makes the whole tree multi-select and turns its " +
               "single-select levels off (they stay stored and return with singleSelect=true).");
         }
      }
   }

   /**
    * On a tree with single-select levels, a requested path that stops above the deepest single
    * level is extended by the runtime into the currently selected child (the selection list
    * holds selected children first), so the currently selected paths it prefixes must not be
    * deselected by the replace-diff -- otherwise {@code [[East]]} with East > NY selected would
    * clear East's branch and the extension would land on another child (bug-78795). A no-op
    * without single levels, where replacing {@code [East]} legitimately drops East's children.
    */
   static List<List<String>> keepAutoExtended(List<List<String>> toRemove,
                                              List<List<String>> requested,
                                              Collection<Integer> singleLevels)
   {
      if(singleLevels.isEmpty() || toRemove.isEmpty()) {
         return toRemove;
      }

      int deepest = Collections.max(singleLevels);

      return toRemove.stream()
         .filter(p -> requested.stream().noneMatch(
            r -> r.size() <= deepest && p.size() > r.size() && startsWithPath(p, r)))
         .toList();
   }

   private static int requireSortOrder(String sortOrder) {
      return switch(sortOrder.trim().toLowerCase(Locale.ROOT)) {
         case "asc", "ascending" -> XConstants.SORT_ASC;
         case "desc", "descending" -> XConstants.SORT_DESC;
         case "specific", "manual" -> XConstants.SORT_SPECIFIC;
         default -> throw new IllegalArgumentException(
            "'sortOrder' must be asc, desc or specific, got '" + sortOrder + "'.");
      };
   }

   private static boolean requireSubtreeMode(String mode) {
      String key = mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT);

      return switch(key) {
         case "select" -> true;
         case "clear", "unselect", "deselect" -> false;
         default -> throw new IllegalArgumentException(
            "'mode' must be 'select' or 'clear', got '" + mode + "'.");
      };
   }

   // ── the toggle/cycle arithmetic ───────────────────────────────────────────

   /**
    * How many times to hit the sort endpoint to get from {@code current} to {@code target}.
    *
    * <p>The ring is {@code ASC → DESC → SPECIFIC → ASC}, and anything unrecognised lands on
    * {@code ASC} after one step, matching {@code nextSortType}'s {@code default}.
    */
   static int sortCycles(int current, int target) {
      int at = current;

      for(int steps = 0; steps < RING.length; steps++) {
         if(at == target) {
            return steps;
         }

         at = next(at);
      }

      // Unreachable for the three ring values; a caller asking for something outside it gets a
      // single step rather than a silent no-op.
      return 1;
   }

   private static int next(int sortType) {
      if(sortType == XConstants.SORT_ASC) {
         return XConstants.SORT_DESC;
      }
      else if(sortType == XConstants.SORT_DESC) {
         return XConstants.SORT_SPECIFIC;
      }

      return XConstants.SORT_ASC;
   }

   // ── event construction ────────────────────────────────────────────────────

   /**
    * Builds a plain value apply. {@code toggle}/{@code toggleAll} are deliberately left false —
    * either one would flip {@code singleSelection} instead of applying the values.
    */
   private static ApplySelectionListEvent applyEvent(List<List<String>> values) {
      ApplySelectionListEvent event = new ApplySelectionListEvent();
      event.setType(ApplySelectionListEvent.Type.APPLY);
      event.setValues(values.stream().map(path -> value(path, true)).toList());
      return event;
   }

   private static ApplySelectionListEvent deselectEvent(List<List<String>> values) {
      ApplySelectionListEvent event = new ApplySelectionListEvent();
      event.setType(ApplySelectionListEvent.Type.APPLY);
      event.setValues(values.stream().map(path -> value(path, false)).toList());
      return event;
   }

   /**
    * The number of selected values, as the caller would count them. A fixed-hierarchy (non-ID)
    * tree marks every ancestor of a selected value, so counting nodes would count one chosen
    * value as its whole chain; count leaf paths there, the way clear_selection's non-ID
    * {@code clearedCount} does. An ID-mode tree and a flat list count nodes directly.
    */
   static int selectedTotal(SelectionValue[] values, boolean nonIdTree) {
      return nonIdTree ? selectedPaths(values).size() : countSelected(values);
   }

   /**
    * What is still selected after a replace that the request did not name -- the values an active
    * search hid and {@code VSSelectionService.applySelection} therefore added back (bug-78216).
    * Measured over the post-apply {@code after} array, so a deselect that did stick is not
    * reported. ID mode compares node ids against the union of the requested path elements (the
    * same rule {@link #idModeReplaceEvent} uses); otherwise a selected path is retained unless a
    * requested path is a prefix of it or it of the request.
    */
   static List<List<String>> retainedSelection(SelectionValue[] after, List<List<String>> requested,
                                               boolean idMode)
   {
      List<List<String>> retained = new ArrayList<>();

      if(idMode) {
         Set<String> requestedIds = new HashSet<>();
         requested.forEach(requestedIds::addAll);

         for(String id : selectedNodeIds(after)) {
            if(!requestedIds.contains(id)) {
               retained.add(List.of(id));
            }
         }
      }
      else {
         for(List<String> path : selectedPaths(after)) {
            if(!everSelected(requested, path)) {
               retained.add(path);
            }
         }
      }

      return retained;
   }

   /**
    * The apply event that makes an ID-mode tree hold exactly {@code requested}: the requested paths
    * selected, plus one single-element path per currently selected node id that no requested path
    * names, deselected. A requested path selects every node (any depth) whose value is in the
    * path, so the requested ids are the union of all path elements.
    */
   static ApplySelectionListEvent idModeReplaceEvent(SelectionValue[] current,
                                                     List<List<String>> requested)
   {
      Set<String> requestedIds = new HashSet<>();
      requested.forEach(requestedIds::addAll);

      ApplySelectionListEvent event = applyEvent(requested);
      List<ApplySelectionListEvent.Value> values = new ArrayList<>(event.getValues());

      for(String id : selectedNodeIds(current)) {
         if(!requestedIds.contains(id)) {
            values.add(value(List.of(id), false));
         }
      }

      event.setValues(values);
      return event;
   }

   /**
    * The distinct values of every selected node, in walk order. Recurses into every composite
    * without an {@code isSelected()} gate, like {@link #countSelected(SelectionValue[])}, since an
    * ID-mode select never marks ancestors.
    */
   static List<String> selectedNodeIds(SelectionValue[] values) {
      Set<String> ids = new LinkedHashSet<>();
      collectSelectedNodeIds(values, ids);
      return new ArrayList<>(ids);
   }

   private static void collectSelectedNodeIds(SelectionValue[] values, Set<String> ids) {
      if(values == null) {
         return;
      }

      for(SelectionValue value : values) {
         if(value == null) {
            continue;
         }

         if(value.isSelected() && value.getValue() != null) {
            ids.add(value.getValue());
         }

         if(value instanceof CompositeSelectionValue composite) {
            SelectionList childList = composite.getSelectionList();
            collectSelectedNodeIds(childList == null ? null : childList.getSelectionValues(), ids);
         }
      }
   }

   private static List<List<String>> selectedNodeIds(SelectionVSAssembly assembly) {
      SelectionList list = selectionListOf(assembly);
      return selectedNodeIds(list == null ? null : list.getSelectionValues()).stream()
         .map(List::of).toList();
   }

   private static ApplySelectionListEvent.Value value(List<String> path, boolean selected) {
      ApplySelectionListEvent.Value value = new ApplySelectionListEvent.Value();
      value.setValue(path.toArray(new String[0]));
      value.setSelected(selected);
      return value;
   }

   // ── other-assembly side effects ───────────────────────────────────────────

   /** One other filter's applied selection, with its type captured at snapshot time. */
   record OtherSelection(String type, List<List<String>> paths) {
   }

   /**
    * Applied selection of every selection list/tree other than {@code target} and its same-field
    * cluster neighbors, keyed by absolute assembly name (embedded viewsheets share local names). A
    * selection write re-runs Association on the other filters, which can remove their selection
    * with no trace in the target's own result. An assembly whose state list is null is left out
    * (unknown, not cleared). Best effort: a viewsheet in an odd state yields an empty snapshot
    * rather than failing the write or masking its validation error.
    */
   private static Map<String, OtherSelection> snapshotOthers(RuntimeViewsheet rvs,
                                                             SelectionVSAssembly target)
   {
      Map<String, OtherSelection> snapshot = new LinkedHashMap<>();
      Viewsheet viewsheet = rvs.getViewsheet();
      Assembly[] assemblies = viewsheet == null ? null : viewsheet.getAssemblies(true);

      // An empty snapshot means "unknown -> report nothing": diffOthers iterates only the keys of
      // the "before" snapshot and skips any key missing from "after".
      if(assemblies == null) {
         return snapshot;
      }

      Set<String> skip = new HashSet<>();
      skip.add(target.getAbsoluteName());

      if(target.getViewsheet() != null) {
         try {
            SelectionVSUtil.getAssociatedSelectionCluster(target)
               .forEach(a -> skip.add(a.getAbsoluteName()));
         }
         catch(RuntimeException ex) {
            LOG.warn("Could not resolve the selection cluster of {}; other selections will not " +
                     "be reported", target.getAbsoluteName(), ex);
            return snapshot;
         }
      }

      for(Assembly other : assemblies) {
         if(other == null || skip.contains(other.getAbsoluteName()) ||
            !(other instanceof SelectionListVSAssembly ||
              other instanceof SelectionTreeVSAssembly))
         {
            continue;
         }

         SelectionVSAssembly selection = (SelectionVSAssembly) other;

         try {
            SelectionList list = selectionListOf(selection);

            if(list != null) {
               boolean multi = !((SelectionVSAssemblyInfo) selection.getInfo()).isSingleSelection();
               snapshot.put(selection.getAbsoluteName(), new OtherSelection(
                  describe(selection), selectedPaths(list.getSelectionValues(), multi)));
            }
         }
         catch(RuntimeException ex) {
            LOG.warn("Could not snapshot selection {} while writing {}; it will not be reported",
                     selection.getAbsoluteName(), target.getAbsoluteName(), ex);
         }
      }

      return snapshot;
   }

   /** Records {@code otherSelectionsChanged} for every assembly whose applied selection moved. */
   private static void reportOthersChanged(Map<String, Object> result, RuntimeViewsheet rvs,
                                           Map<String, OtherSelection> before,
                                           SelectionVSAssembly target)
   {
      List<Map<String, Object>> changed = diffOthers(before, snapshotOthers(rvs, target));

      if(!changed.isEmpty()) {
         result.put("otherSelectionsChanged", changed);
      }
   }

   /** Entries for each assembly present in both snapshots whose applied paths differ. */
   static List<Map<String, Object>> diffOthers(Map<String, OtherSelection> before,
                                               Map<String, OtherSelection> after)
   {
      List<Map<String, Object>> changed = new ArrayList<>();

      before.forEach((name, was) -> {
         OtherSelection now = after.get(name);

         if(now == null) {
            return;
         }

         Set<List<String>> wasSet = new HashSet<>(was.paths());
         Set<List<String>> nowSet = new HashSet<>(now.paths());

         if(wasSet.equals(nowSet)) {
            return;
         }

         Map<String, Object> entry = new LinkedHashMap<>();
         entry.put("assembly", name);
         entry.put("type", was.type());
         entry.put("before", was.paths());
         entry.put("after", now.paths());
         entry.put("removed", was.paths().stream().filter(p -> !nowSet.contains(p)).toList());
         entry.put("added", now.paths().stream().filter(p -> !wasSet.contains(p)).toList());
         changed.add(entry);
      });

      return changed;
   }

   // ── reads ─────────────────────────────────────────────────────────────────

   /** Every currently selected value, as paths — what "clear" has to send back deselected. */
   private static List<List<String>> selectedPaths(SelectionVSAssembly assembly) {
      SelectionList list = selectionListOf(assembly);
      return selectedPaths(list == null ? null : list.getSelectionValues());
   }

   /**
    * The value-to-path mapping, split out from its container so it is testable.
    *
    * <p>{@code SelectionList} cannot be constructed or mocked in a plain unit test — the class fails
    * to initialise outside a Spring context — so the logic worth asserting lives here, over the array
    * the container hands back.
    *
    * <p>Recurses into a selected {@link CompositeSelectionValue}'s own children, mirroring
    * {@code VSSelectionService.findSelectedPaths} (non-ID-mode shape) — a flat, single-level scan
    * cannot represent a nested selection tree path like {@code ["East","NY"]}. If a selected
    * composite has no selected child of its own, it still contributes a self-only path (matching
    * {@code findSelectedPaths}'s empty-fallback), otherwise selecting a whole parent node without
    * selecting any of its children would silently vanish from the result.
    */
   static List<List<String>> selectedPaths(SelectionValue[] values) {
      return selectedPaths(values, false);
   }

   /**
    * @param skipExcluded when true, a selected value that Association has since excluded does not
    *                     count (the "applied" selection of a multi-select assembly).
    */
   static List<List<String>> selectedPaths(SelectionValue[] values, boolean skipExcluded) {
      if(values == null) {
         return List.of();
      }

      List<List<String>> paths = new ArrayList<>();

      for(SelectionValue value : values) {
         if(value == null || !value.isSelected() || skipExcluded && value.isExcluded()) {
            continue;
         }

         int level = value.getLevel();
         String ownValue = value.getValue() == null ? "" : value.getValue();

         if(value instanceof CompositeSelectionValue composite) {
            SelectionList childList = composite.getSelectionList();
            List<List<String>> childPaths =
               new ArrayList<>(selectedPaths(childList == null ? null :
                                             childList.getSelectionValues(), skipExcluded));

            if(childPaths.isEmpty()) {
               childPaths.add(new ArrayList<>(Collections.nCopies(level + 1, (String) null)));
            }

            for(List<String> path : childPaths) {
               path.set(level, ownValue);
            }

            paths.addAll(childPaths);
         }
         else {
            List<String> path = new ArrayList<>(Collections.nCopies(level + 1, (String) null));
            path.set(level, ownValue);
            paths.add(path);
         }
      }

      return paths;
   }

   /** Recursive walk mirroring selectedPaths(SelectionValue[])'s shape -- see its own javadoc --
    *  but emitting every value (not just selected ones) with its full narrowing state. */
   static List<Map<String, Object>> selectionState(SelectionValue[] values) {
      if(values == null) {
         return List.of();
      }

      List<Map<String, Object>> out = new ArrayList<>();

      for(SelectionValue value : values) {
         if(value == null) {
            continue;
         }

         boolean excluded = value.isExcluded();
         boolean included = value.isIncluded();
         boolean compatible = (value.getState() & SelectionValue.STATE_COMPATIBLE) != 0;

         Map<String, Object> entry = new LinkedHashMap<>();
         entry.put("value", value.getValue());
         entry.put("label", value.getLabel());
         entry.put("selected", value.isSelected());
         // The one field a caller replicating "Select All" should use -- mirrors
         // vs-selection.component.ts:2121's own selectAll() predicate exactly (isIncluded ||
         // isCompatible), confirmed mutually exclusive with excluded by SelectionListVSAQuery's own
         // construction (refute-B.md, "Point 3"). Listed first: this is the load-bearing field.
         entry.put("selectableInSelectAll", included || compatible);
         entry.put("excluded", excluded);
         entry.put("included", included);
         entry.put("compatible", compatible);

         if(value instanceof CompositeSelectionValue composite) {
            SelectionList childList = composite.getSelectionList();
            entry.put("children", selectionState(
               childList == null ? null : childList.getSelectionValues()));
         }

         out.add(entry);
      }

      return out;
   }

   /** Flattened count including children -- cheap, mirrors set_selection's own count disclosures. */
   static int countAll(List<Map<String, Object>> values) {
      int count = 0;

      for(Map<String, Object> value : values) {
         count++;
         Object children = value.get("children");

         if(children instanceof List<?> childList) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> typed = (List<Map<String, Object>>) childList;
            count += countAll(typed);
         }
      }

      return count;
   }

   /** Every currently selected node, counted directly — what an ID-mode tree's "clear" counts. */
   private static int countSelected(SelectionVSAssembly assembly) {
      SelectionList list = selectionListOf(assembly);
      return countSelected(list == null ? null : list.getSelectionValues());
   }

   /**
    * The recursion {@link #selectedPaths(SelectionValue[])} cannot substitute for on an ID-mode
    * tree: that method only descends into a {@link CompositeSelectionValue} when the composite
    * itself is {@code isSelected()}, which an ID-mode select never guarantees (unlike a
    * fixed-hierarchy select, an ID-mode match never marks ancestors). This recurses into every
    * composite unconditionally and counts every node whose own flag is set, ancestors included --
    * so it counts nodes, not leaf paths, and is not directly comparable to the non-ID branch's
    * {@code selectedPaths()}-based count.
    */
   static int countSelected(SelectionValue[] values) {
      if(values == null) {
         return 0;
      }

      int count = 0;

      for(SelectionValue value : values) {
         if(value == null) {
            continue;
         }

         if(value.isSelected()) {
            count++;
         }

         if(value instanceof CompositeSelectionValue composite) {
            SelectionList childList = composite.getSelectionList();
            count += countSelected(childList == null ? null : childList.getSelectionValues());
         }
      }

      return count;
   }

   /**
    * What has to be turned off to make {@code current} become {@code requested} — split out from
    * its caller so the diff itself is testable without a live assembly.
    */
   static List<List<String>> toDeselect(List<List<String>> current, List<List<String>> requested) {
      List<List<String>> toDeselect = new ArrayList<>(current);
      toDeselect.removeAll(requested);
      return toDeselect;
   }

   /**
    * The actual paths to send in a deselect event for {@code toRemove}, given everything
    * currently selected ({@code current}) — not simply {@code toRemove} itself.
    *
    * <p><b>The bug this works around:</b> a deselect event only clears its exact TARGET (deepest)
    * node's own {@code selected} flag in the shared {@code updateSelectionOfChangedAssembly} —
    * unlike a select, which also marks every ancestor along the path selected via its own
    * {@code isParent && selected} branch, a deselect's matching condition is
    * {@code isParent && false}, always false, so an ancestor's own flag is never cleared there.
    * Left stale, that flag resurfaces on the very next read: {@link #selectedPaths} treats "a
    * composite selected as a whole with no selected children of its own" as a legitimate
    * top-level selection in its own right (an empty-fallback rule this file needs for the
    * opposite, genuinely-correct case of a whole parent node selected on purpose) — so an
    * ancestor whose one live child was just deselected gets reported right back as if it had been
    * freshly selected on its own, reappearing in the very next diff as a spurious "currently
    * selected" path that was never actually requested. A caller who changes one leaf under a
    * previously-selected branch (even to a sibling at the exact same depth) sees the old branch's
    * top level accumulate alongside the new selection instead of being replaced by it.
    *
    * <p><b>The fix:</b> for each path being removed, find the SHORTEST prefix (from its root)
    * that, once {@code toRemove} is applied, no remaining selected path shares — the highest
    * ancestor whose entire subtree is safe to fully clear. Deselecting there instead of at the
    * exact leaf reaches the same leaf via the shared method's own {@code unselectChildren}
    * cascade, but also correctly clears every ancestor flag along the way, since nothing under it
    * is meant to survive. Falls back to the leaf path itself if no such prefix is found (should
    * not happen in practice, since the leaf itself is always a valid, if maximally specific,
    * choice). Entirely a wiz-layer fix — {@code updateSelectionOfChangedAssembly} itself is
    * untouched, so this carries no risk to the Composer UI or any other caller sharing it.
    *
    * <p>A no-op for a flat {@code SelectionListVSAssembly}: every path there is already exactly
    * one segment, so the shortest-prefix search always lands on the path itself, unchanged.
    *
    * <p><b>A path absent from {@code current} entirely is skipped, not targeted.</b> The
    * shortest-prefix search above only asks "is this prefix still covered by something REMAINING
    * after the removal" — against an empty (or simply non-overlapping) {@code remaining}, that
    * question is vacuously true at the very first, shortest prefix, so a value that was never
    * actually selected would otherwise still manufacture a target and get reported as removed
    * (bug-76701). {@link #everSelected} answers the question this method's own loop never asks:
    * was {@code path} ever part of {@code current} to begin with.
    */
   static List<List<String>> deselectTargets(List<List<String>> current, List<List<String>> toRemove) {
      List<List<String>> remaining = new ArrayList<>(current);
      remaining.removeAll(toRemove);

      List<List<String>> targets = new ArrayList<>();

      for(List<String> path : toRemove) {
         if(!everSelected(current, path)) {
            continue;
         }

         List<String> target = path;

         for(int len = 1; len <= path.size(); len++) {
            List<String> prefix = path.subList(0, len);

            if(remaining.stream().noneMatch(p -> startsWithPath(p, prefix))) {
               target = prefix;
               break;
            }
         }

         List<String> copy = new ArrayList<>(target);

         if(!targets.contains(copy)) {
            targets.add(copy);
         }
      }

      return targets;
   }

   /** Order-preserving de-duplication of requested paths; null stays null. */
   static List<List<String>> distinctPaths(List<List<String>> paths) {
      if(paths == null) {
         return null;
      }

      List<List<String>> result = new ArrayList<>();

      for(List<String> path : paths) {
         if(!result.contains(path)) {
            result.add(path);
         }
      }

      return result;
   }

   /** Whether {@code path} was ever actually part of {@code current} -- either is a prefix of the other. */
   static boolean everSelected(List<List<String>> current, List<String> path) {
      return current.stream().anyMatch(p -> startsWithPath(p, path) || startsWithPath(path, p));
   }

   private static boolean startsWithPath(List<String> path, List<String> prefix) {
      if(path.size() < prefix.size()) {
         return false;
      }

      for(int i = 0; i < prefix.size(); i++) {
         if(!Objects.equals(path.get(i), prefix.get(i))) {
            return false;
         }
      }

      return true;
   }

   /**
    * Whether {@code doApplySelection} treats this assembly's apply as value-diffable (a per-value
    * delta patch that leaves unmentioned values untouched), so the new diff-and-deselect step is the
    * right fix for it.
    *
    * <p>{@code SelectionListVSAssembly} and a non-ID-mode {@code SelectionTreeVSAssembly} match; a
    * {@code TimeSliderVSAssembly} already fully overwrites its selection every call and a
    * {@code CalendarVSAssembly}'s values-apply path is a no-op, so neither needs (or should get) an
    * extra deselect call. ID-mode {@code SelectionTreeVSAssembly} is deliberately excluded: it
    * matches values by {@code Tool.contains} against the whole path array rather than the
    * depth-indexed walk {@link #selectedPaths(SelectionValue[])} produces paths for, so reusing the
    * same diff here would not be guaranteed correct. An ID-mode tree is instead replaced by a
    * node-id diff in {@code setSelection} ({@link #idModeReplaceEvent}). Known limitation, shared
    * with the path diff: a selected node hidden by an active tree search cannot be deselected.
    */
   static boolean isPathDiffable(SelectionVSAssembly assembly) {
      if(assembly instanceof SelectionListVSAssembly) {
         return true;
      }

      return assembly instanceof SelectionTreeVSAssembly tree && !tree.isIDMode();
   }

   /**
    * Whether {@code doApplySelection} matches this assembly's values against a live domain via
    * {@code updateSelectionOfChangedAssembly}/{@code updateIDSelectionTree} at all -- the only two
    * types the value-validation below can meaningfully apply to.
    *
    * <p>Mirrors {@code doApplySelection}'s own top-level branch exactly (unlike
    * {@link #isPathDiffable}, which further excludes ID-mode trees for a diff-specific reason, not
    * a matching one). A {@code TimeSliderVSAssembly} ignores the requested value paths entirely --
    * it selects by index range ({@code event.getSelectStart()}/{@code getSelectEnd()}) -- and a
    * {@code CalendarVSAssembly}'s values-apply path is a no-op, so validating value strings against
    * either would be meaningless.
    */
   private static boolean isValueMatchable(SelectionVSAssembly assembly) {
      return assembly instanceof SelectionListVSAssembly || assembly instanceof SelectionTreeVSAssembly;
   }

   /**
    * Refuses, by name and before anything is applied, a path that resolves to nothing in the live
    * domain. Skipped when the domain isn't known yet -- a cannot-tell case, not a does-not-exist
    * case.
    */
   private static void requireResolvable(String assemblyName, SelectionList domain,
                                         List<List<String>> values, boolean idMode, String action)
   {
      requireResolvable(assemblyName, domain == null ? null : domain.getSelectionValues(), values,
                        idMode, action);
   }

   /** Split out from its {@code SelectionList} container for the same reason as the overload below. */
   static void requireResolvable(String assemblyName, SelectionValue[] domain,
                                 List<List<String>> values, boolean idMode, String action)
   {
      if(domain == null) {
         return;
      }

      List<List<String>> unmatched = findUnmatchedPaths(domain, values, idMode);

      if(!unmatched.isEmpty()) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' has no value matching " + unmatched +
            unresolvedDetail(domain, unmatched, idMode) + " -- confirm the exact spelling via " +
            "browse_condition_values before " + action + " it.");
      }
   }

   /** Skipped when the domain isn't known yet -- a cannot-tell case, not a does-not-exist case. */
   private static List<List<String>> findUnmatchedPaths(SelectionList domain,
                                                         List<List<String>> values, boolean idMode)
   {
      if(domain == null) {
         return List.of();
      }

      return findUnmatchedPaths(domain.getSelectionValues(), values, idMode);
   }

   /**
    * Split out from its {@code SelectionList} container so it is testable -- {@code SelectionList}
    * cannot be constructed or mocked outside a Spring context (see {@link #selectedPaths(SelectionValue[])}).
    *
    * <p>Stricter than the apply on purpose: {@code updateSelectionOfChangedAssembly} and
    * {@code updateIDSelectionTree} silently skip whatever in a path does not resolve (a segment past
    * a leaf, an id that is no node) and still report success, so mirroring them would make this
    * check vacuous for exactly the input it exists to refuse. A non-ID-mode path must resolve
    * segment by segment, every one; an ID-mode path is a set of node ids ({@code updateIDSelectionTree}
    * selects every node whose id is in it), so every id must exist as a node but their order and
    * parentage are not checked.
    */
   static List<List<String>> findUnmatchedPaths(SelectionValue[] domain, List<List<String>> values,
                                                boolean idMode)
   {
      List<List<String>> unmatched = new ArrayList<>();

      for(List<String> path : values) {
         String[] segments = path.toArray(new String[0]);
         boolean matched = idMode ? missingIds(domain, segments).isEmpty()
                                   : matchesPath(domain, segments, 0);

         if(!matched) {
            unmatched.add(path);
         }
      }

      return unmatched;
   }

   /**
    * {@code paths} narrowed down to the ones that resolve to a value the active search string
    * would still show -- {@code doApplySelection} has no search-awareness of its own, so this is
    * what actually makes {@code search} scope a values apply rather than merely being disclosed as
    * having done so (bug-76854). Skipped (returns {@code paths} unchanged) when the domain isn't
    * known yet, the same cannot-tell convention {@link #findUnmatchedPaths(SelectionList, List, boolean)}
    * uses.
    */
   private static List<List<String>> filterBySearch(SelectionList domain, List<List<String>> paths,
                                                     boolean idMode, String search)
   {
      if(domain == null) {
         return paths;
      }

      return filterBySearch(domain.getSelectionValues(), paths, idMode, search);
   }
   /**
    * Split out from its {@code SelectionList} container so it is testable, the same reason
    * {@link #findUnmatchedPaths(SelectionValue[], List, boolean)} is. A path is kept iff the
    * widget would show it: the tree widget renders {@code findAll(search, false)}
    * ({@code VSSelectionTreeModel}), where a node whose own label matches is shown with its whole
    * subtree and any other node only through a matching descendant. So visibility is decided by
    * resolving the path in that filtered tree with the same walks the existence check uses -- a
    * non-ID path must resolve segment by segment, an ID-mode path must have every id present
    * (bug-78786).
    */
   static List<List<String>> filterBySearch(SelectionValue[] domain, List<List<String>> paths,
                                            boolean idMode, String search)
   {
      SelectionList all = new SelectionList();

      if(domain != null) {
         for(SelectionValue value : domain) {
            if(value != null) {
               all.addSelectionValue(value);
            }
         }
      }

      SelectionValue[] visible = all.findAll(search, false).getSelectionValues();
      List<List<String>> matching = new ArrayList<>();

      for(List<String> path : paths) {
         String[] segments = path.toArray(new String[0]);
         boolean matches = segments.length > 0 &&
            (idMode ? missingIds(visible, segments).isEmpty() : matchesPath(visible, segments, 0));

         if(matches) {
            matching.add(path);
         }
      }

      return matching;
   }

   /**
    * Resolves {@code path[index]} at this level via an exact-match lookup (the same match
    * {@code SelectionList.findValue(val, false)} does) and, while segments remain, descends into
    * the found value's children -- every segment must resolve. A path that runs past a leaf is
    * refused: {@code updateSelectionOfChangedAssembly} would select the leaf and silently ignore
    * the leftover segments, but nothing legitimate produces such a path (the client builds them
    * from real ancestry), so accepting it only hides a fabricated segment (bug-78264, bug-78268).
    */
   private static boolean matchesPath(SelectionValue[] level, String[] path, int index) {
      if(level == null || index >= path.length) {
         return false;
      }

      SelectionValue value = findByValue(level, path[index]);

      if(value == null) {
         return false;
      }

      if(index < path.length - 1) {
         if(!(value instanceof CompositeSelectionValue composite)) {
            return false;
         }

         SelectionList childList = composite.getSelectionList();
         return matchesPath(childList == null ? null : childList.getSelectionValues(), path,
                            index + 1);
      }

      return true;
   }

   /**
    * The elements of an ID-mode path that name no node anywhere in the tree. Compared with the
    * apply's own {@code Tool.contains}/{@code Tool.equals} semantics (case-sensitive), so what is
    * accepted here is exactly what {@code updateIDSelectionTree} would find.
    */
   static List<String> missingIds(SelectionValue[] domain, String[] path) {
      List<String> missing = new ArrayList<>();

      for(String id : path) {
         if(!hasNode(domain, id)) {
            missing.add(id);
         }
      }

      return missing;
   }

   private static boolean hasNode(SelectionValue[] level, String id) {
      if(level == null) {
         return false;
      }

      for(SelectionValue value : level) {
         if(value == null) {
            continue;
         }

         if(Tool.contains(new String[]{ id }, value.getValue(), true, true, true)) {
            return true;
         }

         if(value instanceof CompositeSelectionValue composite) {
            SelectionList childList = composite.getSelectionList();

            if(hasNode(childList == null ? null : childList.getSelectionValues(), id)) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Names what in each unmatched path cannot be resolved -- the missing ids of an ID-mode path, or
    * the first segment a positional path fails at -- so the refusal points at the fabricated part
    * instead of only echoing the whole path.
    */
   private static String unresolvedDetail(SelectionList domain, List<List<String>> unmatched,
                                          boolean idMode)
   {
      return unresolvedDetail(domain == null ? null : domain.getSelectionValues(), unmatched,
                              idMode);
   }

   private static String unresolvedDetail(SelectionValue[] values, List<List<String>> unmatched,
                                          boolean idMode)
   {
      List<String> named = new ArrayList<>();

      for(List<String> path : unmatched) {
         String[] segments = path.toArray(new String[0]);

         if(idMode) {
            missingIds(values, segments).forEach(id -> named.add("'" + id + "'"));
         }
         else {
            int index = firstUnresolvedSegment(values, segments);

            if(index < segments.length) {
               named.add("'" + segments[index] + "'");
            }
         }
      }

      return named.isEmpty() ? "" : " (not found: " + String.join(", ", named) + ")";
   }

   /**
    * The tail of an ID-mode subtree refusal. An ID-mode path is the full chain of ids from the
    * root, so the failing id is usually a real node asked for without its ancestors -- name the
    * chain(s) to use instead of hinting at a spelling problem. Package-private for tests.
    */
   static String idModeChainHint(SelectionList domain, List<String> path) {
      SelectionValue[] values = domain == null ? null : domain.getSelectionValues();
      String[] segments = path.toArray(new String[0]);
      int index = firstUnresolvedSegment(values, segments);
      String id = index < segments.length ? segments[index] : segments[segments.length - 1];
      List<List<String>> chains = new ArrayList<>();
      collectIdChains(values, id, new ArrayList<>(), chains);
      String rule = "an ID-mode path must be the full chain of ids from the root";

      if(chains.isEmpty()) {
         return " (not found: '" + id + "') -- " + rule + ".";
      }

      String named = chains.stream().map(List::toString).collect(Collectors.joining(" or "));
      return " -- " + rule + "; id '" + id + "' exists at " + named +
         ", pass that full chain from the root.";
   }

   private static void collectIdChains(SelectionValue[] level, String id, List<String> prefix,
                                       List<List<String>> out)
   {
      if(level == null) {
         return;
      }

      for(SelectionValue value : level) {
         if(value == null) {
            continue;
         }

         List<String> chain = new ArrayList<>(prefix);
         chain.add(value.getValue());

         if(Tool.equals(id, value.getValue())) {
            out.add(chain);
         }

         if(value instanceof CompositeSelectionValue composite) {
            SelectionList childList = composite.getSelectionList();
            collectIdChains(childList == null ? null : childList.getSelectionValues(), id, chain,
                            out);
         }
      }
   }

   /** Index of the first segment that does not resolve, or {@code path.length} if none fails. */
   private static int firstUnresolvedSegment(SelectionValue[] level, String[] path) {
      for(int index = 0; index < path.length; index++) {
         if(level == null) {
            return index;
         }

         SelectionValue value = findByValue(level, path[index]);

         if(value == null) {
            return index;
         }

         if(index < path.length - 1) {
            if(!(value instanceof CompositeSelectionValue composite)) {
               return index + 1;
            }

            SelectionList childList = composite.getSelectionList();
            level = childList == null ? null : childList.getSelectionValues();
         }
      }

      return path.length;
   }

   /** The one-level, non-recursive exact match {@code SelectionList.findValue(val, false)} does. */
   private static SelectionValue findByValue(SelectionValue[] level, String target) {
      for(SelectionValue value : level) {
         if(value != null && Tool.equals(target, value.getValue())) {
            return value;
         }
      }

      return null;
   }

   // ── range slider bucket resolution ──────────────────────────────────────────

   /**
    * The result of resolving one requested Range Slider bound to a bucket: which index it landed
    * on, the bucket's own value (what actually gets applied), and whether that differs from what
    * was requested (an out-of-range numeric bound clamped to the nearest bucket).
    */
   record BucketResolution(int index, String appliedValue, boolean clamped) {}

   /**
    * Resolves every requested value into the {@code [start, end]} bucket-index range a Range
    * Slider apply should select, collecting a disclosure entry for each bound that had to be
    * clamped. {@code values} is one bucket-value per single-segment path -- a slider has no
    * hierarchy, so anything longer is refused.
    */
   static int[] sliderBucketRange(String assemblyName, SelectionValue[] buckets,
                                  List<List<String>> values, List<Map<String, Object>> clampedOut)
   {
      if(buckets.length == 0) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' has no values to select from yet.");
      }

      int start = Integer.MAX_VALUE;
      int end = Integer.MIN_VALUE;

      for(List<String> path : values) {
         if(path.size() != 1) {
            throw new IllegalArgumentException(
               "'" + assemblyName + "' is a range slider, which has no hierarchy -- each value " +
               "must be a single bound, not a path of " + path.size() + ".");
         }

         BucketResolution resolved = bucketIndex(assemblyName, buckets, path.get(0));
         start = Math.min(start, resolved.index());
         end = Math.max(end, resolved.index());

         if(resolved.clamped()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("requested", path.get(0));
            entry.put("applied", resolved.appliedValue());
            clampedOut.add(entry);
         }
      }

      return new int[]{start, end};
   }

   /**
    * Resolves one requested bound to a bucket index: an exact match against a bucket's own raw
    * {@code SelectionValue.getValue()} first (the same value the assembly's own apply/clear paths
    * compare, per {@code TimeSliderSelection.populateNumberList}/{@code populateDateList}); failing
    * that, for a value that parses as a number, the numerically nearest bucket -- clamping an
    * out-of-range bound to the slider's actual extent rather than refusing it, matching this file's
    * existing forgiving-on-unambiguous-intent convention ({@link #requireSortOrder}'s aliases).
    * Anything else (unparseable, no exact match) fails loud by name rather than silently landing on
    * some default.
    */
   static BucketResolution bucketIndex(String assemblyName, SelectionValue[] buckets,
                                       String requested)
   {
      for(int i = 0; i < buckets.length; i++) {
         if(buckets[i] != null && Tool.equals(requested, buckets[i].getValue())) {
            return new BucketResolution(i, buckets[i].getValue(), false);
         }
      }

      Double requestedNumber = parseNumeric(requested);

      if(requestedNumber != null) {
         int nearest = -1;
         double nearestDistance = Double.MAX_VALUE;

         for(int i = 0; i < buckets.length; i++) {
            Double bucketNumber = buckets[i] == null ? null : parseNumeric(buckets[i].getValue());

            if(bucketNumber == null) {
               continue;
            }

            double distance = Math.abs(bucketNumber - requestedNumber);

            if(distance < nearestDistance) {
               nearestDistance = distance;
               nearest = i;
            }
         }

         if(nearest >= 0) {
            return new BucketResolution(nearest, buckets[nearest].getValue(), true);
         }
      }

      throw new IllegalArgumentException(
         "'" + assemblyName + "' has no bucket matching '" + requested + "'.");
   }

   private static Double parseNumeric(String value) {
      if(value == null) {
         return null;
      }

      try {
         return Double.parseDouble(value);
      }
      catch(NumberFormatException e) {
         return null;
      }
   }

   /** The slider's maxRangeSize, or 0 when it has none (only a single time info carries one). */
   private static double maxRangeSizeOf(TimeSliderVSAssembly slider) {
      return slider.getTimeInfo() instanceof SingleTimeInfo single ? single.getMaxRangeSize() : 0;
   }

   /**
    * Adds a disclosure entry for each bound of {@code requestedRange} that the slider moved after
    * the apply, so the response never claims a wider range than the one now selected. The slider
    * refresh always selects one bucket past the requested end when it is not upper-inclusive (the
    * exclusive boundary bucket), capped at the last bucket, so that is the end it is compared to
    * and the reported {@code applied} end is the bucket before that boundary. A bound already
    * disclosed as nearest-bucket clamped keeps its original {@code requested} and gets the new
    * {@code applied} instead of a second entry.
    */
   static void discloseNarrowedRange(SelectionValue[] after, int[] requestedRange,
                                     boolean upperInclusive, double maxRangeSize,
                                     List<Map<String, Object>> clampedOut)
   {
      int first = -1;
      int last = -1;

      for(int i = 0; i < after.length; i++) {
         if(after[i] != null && after[i].isSelected()) {
            first = first < 0 ? i : first;
            last = i;
         }
      }

      if(first < 0) {
         return;
      }

      int expectedLast = upperInclusive ? requestedRange[1] :
         Math.min(requestedRange[1] + 1, after.length - 1);
      String shortened = maxRangeSize > 0 ?
         "the range is limited to maxRangeSize " + (long) maxRangeSize :
         "the slider shortened the range";
      // The slider never selects fewer than two buckets (its length floor is 1), which explains
      // exactly one extra bucket past a single point. Any other widening (the slider keeps the
      // previous length for a single point) has no cause this code can prove, so it is not named.
      String floor = "the slider selects at least two buckets";
      String other = "the slider applied a different range than requested";

      if(first != requestedRange[0]) {
         discloseMovedBound(after, requestedRange[0], first,
                            first > requestedRange[0] ? shortened : other, clampedOut);
      }

      if(last != expectedLast) {
         int appliedEnd = upperInclusive ? last : Math.max(first, last - 1);
         String endReason = appliedEnd < requestedRange[1] ? shortened :
            appliedEnd == requestedRange[1] + 1 ? floor : other;
         discloseMovedBound(after, requestedRange[1], appliedEnd, endReason, clampedOut);
      }
   }

   private static void discloseMovedBound(SelectionValue[] buckets, int requestedIndex,
                                          int appliedIndex, String reason,
                                          List<Map<String, Object>> clampedOut)
   {
      String requested = buckets[requestedIndex].getValue();
      String applied = buckets[appliedIndex].getValue();

      for(Map<String, Object> entry : clampedOut) {
         if(Tool.equals(requested, entry.get("applied"))) {
            entry.put("applied", applied);
            entry.put("reason", reason);
            return;
         }
      }

      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("requested", requested);
      entry.put("applied", applied);
      entry.put("reason", reason);
      clampedOut.add(entry);
   }

   /**
    * {@code doApplySelection} decrements {@code selectEnd} by one when the slider is not
    * upper-inclusive, so the index actually wanted has to be sent one past itself for that
    * decrement to land back on it.
    */
   static int adjustedEnd(boolean upperInclusive, int endIndex) {
      return upperInclusive ? endIndex : endIndex + 1;
   }

   /**
    * Whether every bucket is currently selected -- the Range Slider's own representation of "not
    * filtering anything", unlike a list/tree's empty/null state.
    */
   static boolean isFullRangeSelected(SelectionValue[] buckets) {
      if(buckets.length == 0) {
         return true;
      }

      for(SelectionValue bucket : buckets) {
         if(bucket == null || !bucket.isSelected()) {
            return false;
         }
      }

      return true;
   }

   private static SelectionValue[] bucketsOf(TimeSliderVSAssembly slider) {
      SelectionList list = slider.getSelectionList();
      return list == null ? new SelectionValue[0] : list.getSelectionValues();
   }

   private static SelectionList selectionListOf(SelectionVSAssembly assembly) {
      if(assembly instanceof SelectionListVSAssembly list) {
         return list.getSelectionList();
      }
      else if(assembly instanceof SelectionTreeVSAssembly tree) {
         return tree.getSelectionList();
      }
      else if(assembly instanceof TimeSliderVSAssembly slider) {
         return slider.getSelectionList();
      }

      return null;
   }

   /** The search string, which is on the base selection info rather than per-type. */
   private static String searchString(SelectionVSAssemblyInfo info) {
      return info instanceof SelectionBaseVSAssemblyInfo base ? base.getSearchString() : null;
   }

   private static String describe(VSAssembly assembly) {
      if(assembly instanceof SelectionListVSAssembly) {
         return "a selection list";
      }
      else if(assembly instanceof SelectionTreeVSAssembly) {
         return "a selection tree";
      }
      else if(assembly instanceof TimeSliderVSAssembly) {
         return "a range slider";
      }
      else if(assembly instanceof CalendarVSAssembly) {
         return "a calendar";
      }

      return "a " + assembly.getClass().getSimpleName();
   }

   private static final int[] RING = {
      XConstants.SORT_ASC, XConstants.SORT_DESC, XConstants.SORT_SPECIFIC
   };

   private final ViewsheetSessionService sessions;
   private final VSSelectionService selections;
   private final VSCalendarService calendars;
}
