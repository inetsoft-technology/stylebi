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
import inetsoft.util.Tool;
import inetsoft.web.viewsheet.event.ApplySelectionListEvent;
import inetsoft.web.viewsheet.event.SortSelectionListEvent;
import inetsoft.web.viewsheet.service.VSSelectionService;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;

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
 * when a search is active and the assembly is not single-select, {@link #setSelection(String,
 * Principal, String, List, List, String, Boolean, Boolean, String, String) setSelection} drops any
 * requested value the search would hide (the same case-insensitive substring match
 * {@code SelectionValue.match}/{@code CompositeSelectionValue.match} give the widget's own search
 * box) and discloses exactly what was dropped, rather than a bare, unconditional claim that
 * scoping happened (bug-76854). {@code setSelection}'s {@code search} parameter can set the search
 * string before that same apply runs, the same as typing into the widget's own search box
 * (bug-76758). Either way, {@code setSearchString} writes both {@code search} and {@code search2},
 * only {@code search2} is persisted, and <b>nothing in the repository ever parses {@code search2}
 * back</b> — so a search string never survives a reopen, exactly like the interactive widget's own
 * search box. {@code deselect} is deliberately left unscoped by search for now — see the note at
 * the {@code hasDeselect} block in {@code setSelection}.
 */
@Service
public class SelectionRuntimeService {
   public SelectionRuntimeService(ViewsheetSessionService sessions, VSSelectionService selections) {
      this.sessions = sessions;
      this.selections = selections;
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
    *                    On a list or non-ID-mode tree, {@code values} <b>replaces</b> the current
    *                    selection by default (bug-76548) — pass {@code additive:true} to make it
    *                    add to the current selection instead, without touching anything else.
    * @param deselect    values/paths to explicitly remove from the current selection, independent
    *                    of {@code additive} — the counterpart to {@code values} for a caller that
    *                    knows exactly what to un-check without needing to first read (or
    *                    re-specify) everything that should stay selected. Same shape as
    *                    {@code values}; null or empty leaves the selection alone. Only valid on a
    *                    list or non-ID-mode tree (the only assemblies {@code values}'s own replace
    *                    diff already applies to).
    * @param sortOrder   {@code asc} | {@code desc} | {@code specific}, or null to leave it.
    * @param singleSelect whether the assembly should accept one value only, or null to leave it.
    * @param additive    when true, {@code values} only adds — the automatic replace-diff (deselect
    *                    anything current but unmentioned) is skipped. Ignored where that diff
    *                    never ran anyway (single-select, a range slider, ID-mode tree, calendar).
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
                                           List<List<String>> values, List<List<String>> deselect,
                                           String sortOrder, Boolean singleSelect, Boolean additive,
                                           String search, String linkUri)
      throws Exception
   {
      requireName(assemblyName);

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

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         SelectionVSAssembly assembly = requireSelection(rvs, assemblyName);

         // Only the values/deselect paths ever touch getConditionList()/getSelection() -- a
         // sortOrder-only or singleSelect-only call never reaches them, so it must not be
         // refused for a problem it does not have.
         if(values != null || hasDeselect) {
            requireBoundColumn(assembly, assemblyName);
         }

         SelectionVSAssemblyInfo info = (SelectionVSAssemblyInfo) assembly.getInfo();

         result.put("assembly", assemblyName);
         result.put("type", describe(assembly));

         // Search first, same reasoning as selection style below: it changes how a values apply
         // is scoped (see scopedBySearch further down), so it has to be in place before that apply
         // runs, not after.
         if(search != null) {
            if(!(info instanceof SelectionBaseVSAssemblyInfo searchable)) {
               throw new IllegalArgumentException(
                  "'" + assemblyName + "' is " + describe(assembly) + ", which has no search box — " +
                  "only a selection list or tree can be scoped by search. Drop 'search'.");
            }

            searchable.setSearchString(search);
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
            // Sort lives on SelectionBaseVSAssemblyInfo, which a range slider's info is not — a
            // slider orders by its underlying range, so there is nothing to sort.
            if(!(info instanceof SelectionBaseVSAssemblyInfo sortable)) {
               throw new IllegalArgumentException(
                  "'" + assemblyName + "' is " + describe(assembly) + ", which has no sort order — " +
                  "it follows its range. Drop 'sortOrder'.");
            }

            int cycles = sortCycles(sortable.getSortTypeValue(), targetSort);

            for(int i = 0; i < cycles; i++) {
               selections.sortSelection(runtimeId, assemblyName, new SortSelectionListEvent(),
                                        user, dispatcher, linkUri);
            }

            result.put("sortOrder", sortOrder);
            result.put("sortCycles", cycles);
         }

         if(values != null) {
            boolean single = singleSelect != null ? singleSelect : info.isSingleSelection();

            if(single && values.size() > 1) {
               throw new IllegalArgumentException(
                  "'" + assemblyName + "' is single-select, so it cannot hold " + values.size() +
                  " values. Select one value, or pass singleSelect=false in the same call to make " +
                  "it multi-select first.");
            }

            boolean idMode = assembly instanceof SelectionTreeVSAssembly tree && tree.isIDMode();
            SelectionList domain = isValueMatchable(assembly) ? selectionListOf(assembly) : null;

            if(isValueMatchable(assembly)) {
               // doApplySelection matches each requested value against the live domain and
               // silently drops anything that doesn't resolve -- no exception, no counter,
               // nothing the caller could observe. Refuse a typo'd value by name, atomically,
               // before applying anything, rather than reporting an inflated count for a
               // partially-applied result.
               List<List<String>> unmatched = findUnmatchedPaths(domain, values, idMode);

               if(!unmatched.isEmpty()) {
                  throw new IllegalArgumentException(
                     "'" + assemblyName + "' has no value matching " + unmatched + " -- confirm " +
                     "the exact spelling via browse_condition_values before selecting it.");
               }
            }

            String activeSearch = searchString(info);
            List<List<String>> effectiveValues = values;

            if(activeSearch != null && !activeSearch.isBlank() && !single) {
               // Not a refusal: the apply is legitimate for whatever still matches, but
               // doApplySelection itself has no search-awareness at all -- it would otherwise
               // apply every requested value regardless of the active search (bug-76854). Filter
               // down to the subset SelectionValue.match (the same case-insensitive substring
               // rule the widget's own search box narrows by) would still show, and disclose
               // exactly what got dropped rather than a bare, unconditional echo of the search
               // string.
               List<List<String>> matching = filterBySearch(domain, values, idMode, activeSearch);
               List<List<String>> dropped = new ArrayList<>(values);
               dropped.removeAll(matching);

               if(!dropped.isEmpty()) {
                  result.put("scopedBySearch", activeSearch);
                  result.put("scopedBySearchDropped", dropped);
                  effectiveValues = matching;
               }
            }

            if(!single && isPathDiffable(assembly) && !Boolean.TRUE.equals(additive)) {
               // Multi-select apply is a delta patch -- doApplySelection only ever turns matched
               // values on, so anything currently selected but missing from the new values has to
               // be turned off explicitly, or it stays selected alongside them. Single-select
               // already gets a full reset for free via unselectChildren. additive:true is the
               // caller explicitly opting out of this replace behaviour -- see the javadoc above.
               // Diffs against effectiveValues (the post-search-filter set), not the raw request,
               // so a value the search dropped is not treated as "kept" and excluded from the
               // deselect it would otherwise need.
               List<List<String>> currentPaths = selectedPaths(assembly);
               List<List<String>> toRemove = toDeselect(currentPaths, effectiveValues);

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
               List<Map<String, Object>> clamped = new ArrayList<>();
               int[] range = sliderBucketRange(assemblyName, buckets, values, clamped);
               ApplySelectionListEvent event = new ApplySelectionListEvent();
               event.setType(ApplySelectionListEvent.Type.APPLY);
               event.setSelectStart(range[0]);
               event.setSelectEnd(adjustedEnd(slider.isUpperInclusive(), range[1]));
               selections.applySelection(runtimeId, assemblyName, event, user, dispatcher, linkUri);

               if(!clamped.isEmpty()) {
                  // A requested bound outside the slider's actual bucket range was silently
                  // substituted with the nearest one -- disclose which, the same way
                  // scopedBySearch discloses a write landing on a narrower scope than asked.
                  result.put("clampedBounds", clamped);
               }
            }
            else {
               selections.applySelection(runtimeId, assemblyName, applyEvent(effectiveValues), user,
                                         dispatcher, linkUri);
            }

            result.put("valuesSelected", effectiveValues.size());
         }

         if(hasDeselect) {
            // Deliberately not scoped by the active search the way values is above (bug-76854).
            // The charter's own repro only exercised values under search; scoping deselect too is
            // a reasonable extrapolation (a deselect names things by value the same way a select
            // does) but not a confirmed defect, and deselect's own semantics cut the other way --
            // "deselect a value the search box currently hides" is a plausible, useful call
            // (clearing a selection made before the caller searched), not obviously a mistake to
            // refuse. Left as its own, narrower-scoped follow-up rather than folded in here.
            if(!isPathDiffable(assembly)) {
               throw new IllegalArgumentException(
                  "'" + assemblyName + "' is " + describe(assembly) + " -- 'deselect' only works " +
                  "on a selection list or a non-ID-mode selection tree. Use clear_selection to " +
                  "remove everything, or set_selection's plain 'values' for a range slider.");
            }

            List<List<String>> currentPaths = selectedPaths(assembly);
            List<List<String>> targets = deselectTargets(currentPaths, deselect);

            if(!targets.isEmpty()) {
               selections.applySelection(runtimeId, assemblyName, deselectEvent(targets), user,
                                         dispatcher, linkUri);
               // Not deselect.size() -- that would count every requested value regardless of
               // whether it was ever actually selected (bug-76701). Report how many of them
               // deselectTargets actually found a match for in currentPaths.
               result.put("deselected",
                          (int) deselect.stream().filter(path -> everSelected(currentPaths, path))
                             .count());
            }
         }
      });

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

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         SelectionVSAssembly assembly = requireSelection(rvs, assemblyName);
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
            result.put("clearedCount", fullRange ? 0 : buckets.length);

            if(!fullRange && buckets.length > 0) {
               ApplySelectionListEvent event = new ApplySelectionListEvent();
               event.setType(ApplySelectionListEvent.Type.APPLY);
               event.setSelectStart(0);
               event.setSelectEnd(adjustedEnd(slider.isUpperInclusive(), buckets.length - 1));
               selections.applySelection(runtimeId, assemblyName, event, user, dispatcher, linkUri);
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
            result.put("clearedCount", cleared);

            if(cleared > 0) {
               ApplySelectionListEvent event = new ApplySelectionListEvent();
               event.setType(ApplySelectionListEvent.Type.APPLY);
               selections.applySelection(runtimeId, assemblyName, event, user, dispatcher,
                                         linkUri);
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
               selections.applySelection(runtimeId, assemblyName,
                                         deselectEvent(deselectTargets(current, current)), user,
                                         dispatcher, linkUri);
            }
         }
      });

      result.put("persistsOnSave", true);
      return result;
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

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         SelectionVSAssembly assembly = requireSelection(rvs, assemblyName);

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
         // updateIDSelectionTree/matchesAnywhere use for setSelection's own idMode branch; that
         // scan belongs to a values apply, not a subtree one. So the same positional predicate
         // (idMode=false) validates both tree shapes here, reusing the domain-existence check
         // already shipped for setSelection's values-mode fix (bug 76544): refuse a path that
         // resolves to nothing in the live tree -- for both select and clear -- before applying
         // anything, rather than a silent no-op reported back as ok:true.
         List<List<String>> unmatched = findUnmatchedPaths(selectionListOf(assembly),
            List.of(path), false);

         if(!unmatched.isEmpty()) {
            throw new IllegalArgumentException(
               "'" + assemblyName + "' has no subtree at " + path + " -- confirm the exact " +
               "spelling via browse_condition_values before selecting it.");
         }

         // Not extended to an ID-mode tree: findIDSubtreeRoot is positional (see above), not an
         // anywhere-in-the-tree scan, so this exclusion is no longer justified by path.size()
         // being meaningless for an ID-mode tree the way the depth heuristic below assumes -- it
         // is simply still tracked separately as an open gap (VFL-004), not fixed here. An ID-mode
         // single-select tree's own version of that bug is therefore still open; see
         // treatsAnIdModeSingleSelectTreeAsUnguarded in the test.
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

         // selectSubtree NPEs on an unknown name and CCEs on a non-tree; both are refused above.
         selections.selectSubtree(runtimeId, assemblyName, event, user, dispatcher, linkUri);
      });

      result.put("persistsOnSave", true);
      return result;
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
            "get_selection_state only applies to a selection list or selection tree.");
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
    * <p><b>Not called at all for an ID-mode tree</b> -- see the caller's comment. This guard does
    * not close VFL-004 for that case.
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
         return; // path already names a leaf -- always exactly one, safe under any selection style.
      }

      throw new IllegalArgumentException(
         "'" + assemblyName + "' is single-select, so selecting the whole subtree at " +
         String.join(" > ", path) + " -- " + (treeDepth - path.size()) + " level(s) short of a " +
         "leaf -- could silently collapse to just one descendant instead of the whole branch. " +
         "Name a full path down to a leaf instead, or pass singleSelect:false on set_selection " +
         "first to widen it.");
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

   private static ApplySelectionListEvent.Value value(List<String> path, boolean selected) {
      ApplySelectionListEvent.Value value = new ApplySelectionListEvent.Value();
      value.setValue(path.toArray(new String[0]));
      value.setSelected(selected);
      return value;
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
      if(values == null) {
         return List.of();
      }

      List<List<String>> paths = new ArrayList<>();

      for(SelectionValue value : values) {
         if(value == null || !value.isSelected()) {
            continue;
         }

         int level = value.getLevel();
         String ownValue = value.getValue() == null ? "" : value.getValue();

         if(value instanceof CompositeSelectionValue composite) {
            SelectionList childList = composite.getSelectionList();
            List<List<String>> childPaths =
               new ArrayList<>(selectedPaths(childList == null ? null :
                                             childList.getSelectionValues()));

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
    * same diff here would not be guaranteed correct.
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
    * <p>Mirrors exactly what {@code updateSelectionOfChangedAssembly}/{@code updateIDSelectionTree}
    * will do when the values are actually applied, rather than a stricter approximation of it -- the
    * point is to predict the real apply outcome, not to reject something the backend would have
    * happily accepted.
    */
   static List<List<String>> findUnmatchedPaths(SelectionValue[] domain, List<List<String>> values,
                                                boolean idMode)
   {
      List<List<String>> unmatched = new ArrayList<>();

      for(List<String> path : values) {
         String[] segments = path.toArray(new String[0]);
         boolean matched = idMode ? matchesAnywhere(domain, segments) : matchesPath(domain, segments, 0);

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
    * {@link #findUnmatchedPaths(SelectionValue[], List, boolean)} is. Reuses that method's own
    * path-resolution walk ({@link #matchesSearchPath}/{@link #matchesSearchAnywhere} mirror
    * {@link #matchesPath}/{@link #matchesAnywhere} exactly, substituting a search-match test for an
    * existence test at the point a path resolves to a value) so the two checks -- does this value
    * exist at all, does it match the active search -- stay in lockstep as the domain-walking logic
    * evolves.
    */
   static List<List<String>> filterBySearch(SelectionValue[] domain, List<List<String>> paths,
                                            boolean idMode, String search)
   {
      List<List<String>> matching = new ArrayList<>();

      for(List<String> path : paths) {
         String[] segments = path.toArray(new String[0]);
         boolean matches = idMode ? matchesSearchAnywhere(domain, segments, search)
                                   : matchesSearchPath(domain, segments, 0, search);

         if(matches) {
            matching.add(path);
         }
      }

      return matching;
   }

   /**
    * {@link #matchesPath} with a search-match test in place of the found/not-found test at the
    * point a path resolves to a value -- {@code recursive=true} on that final
    * {@code SelectionValue.match} call is what lets a whole parent node selected as a composite
    * match via a matching descendant, the same recursive semantics
    * {@code CompositeSelectionValue.match} gives the widget's own search box.
    */
   private static boolean matchesSearchPath(SelectionValue[] level, String[] path, int index,
                                            String search)
   {
      if(level == null) {
         return false;
      }

      SelectionValue value = findByValue(level, path[index]);

      if(value == null) {
         return false;
      }

      if(value instanceof CompositeSelectionValue composite && index < path.length - 1) {
         SelectionList childList = composite.getSelectionList();
         return matchesSearchPath(childList == null ? null : childList.getSelectionValues(), path,
                                  index + 1, search);
      }

      return value.match(search, true);
   }

   /** {@link #matchesAnywhere} with a search-match test alongside the existence test. */
   private static boolean matchesSearchAnywhere(SelectionValue[] level, String[] path,
                                                String search)
   {
      if(level == null) {
         return false;
      }

      for(SelectionValue value : level) {
         if(value == null) {
            continue;
         }

         if(Tool.contains(path, value.getValue(), true, true, true) && value.match(search, true)) {
            return true;
         }

         if(value instanceof CompositeSelectionValue composite) {
            SelectionList childList = composite.getSelectionList();

            if(matchesSearchAnywhere(childList == null ? null : childList.getSelectionValues(),
                                     path, search))
            {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Mirrors {@code updateSelectionOfChangedAssembly}: resolve {@code path[index]} at this level via
    * an exact-match lookup (the same match {@code SelectionList.findValue(val, false)} does); if it
    * resolves to a {@code CompositeSelectionValue} and segments remain, descend into its children;
    * otherwise -- found and not composite, or the last segment -- stop and call it matched. An
    * over-long path whose resolvable prefix bottoms out at a leaf before the path ends is still a
    * match, since that is what the real apply does with it (it just ignores the leftover segments).
    */
   private static boolean matchesPath(SelectionValue[] level, String[] path, int index) {
      if(level == null) {
         return false;
      }

      SelectionValue value = findByValue(level, path[index]);

      if(value == null) {
         return false;
      }

      if(value instanceof CompositeSelectionValue composite && index < path.length - 1) {
         SelectionList childList = composite.getSelectionList();
         return matchesPath(childList == null ? null : childList.getSelectionValues(), path,
                            index + 1);
      }

      return true;
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

   /**
    * Mirrors {@code updateIDSelectionTree}: a flat "does this path array contain a node's value
    * anywhere in the tree" scan, not a segment-by-segment descent -- ID-mode matches every node
    * whose value appears anywhere in the requested path array.
    */
   private static boolean matchesAnywhere(SelectionValue[] level, String[] path) {
      if(level == null) {
         return false;
      }

      for(SelectionValue value : level) {
         if(value == null) {
            continue;
         }

         if(Tool.contains(path, value.getValue(), true, true, true)) {
            return true;
         }

         if(value instanceof CompositeSelectionValue composite) {
            SelectionList childList = composite.getSelectionList();

            if(matchesAnywhere(childList == null ? null : childList.getSelectionValues(), path)) {
               return true;
            }
         }
      }

      return false;
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

      return "a " + assembly.getClass().getSimpleName();
   }

   private static final int[] RING = {
      XConstants.SORT_ASC, XConstants.SORT_DESC, XConstants.SORT_SPECIFIC
   };

   private final ViewsheetSessionService sessions;
   private final VSSelectionService selections;
}
