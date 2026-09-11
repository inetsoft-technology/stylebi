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
 * <p><b>An active search string silently narrows what an apply touches.</b> When one is set and the
 * assembly is not single-select, {@code applySelection} runs
 * {@code olist = olist.findAll(search, true)} before applying, so the write lands on the filtered
 * subset. This class reads it and reports it rather than pretending the apply was global. It does not
 * offer to <i>set</i> one: {@code setSearchString} writes both {@code search} and {@code search2},
 * only {@code search2} is persisted, and <b>nothing in the repository ever parses {@code search2}
 * back</b> — so a search string is a write-only field that never survives a reopen.
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
      requireName(assemblyName);

      boolean hasDeselect = deselect != null && !deselect.isEmpty();

      if(values == null && sortOrder == null && singleSelect == null && !hasDeselect) {
         throw new IllegalArgumentException(
            "Nothing to do — give at least one of 'values', 'deselect', 'sortOrder' or " +
            "'singleSelect'.");
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
         SelectionVSAssemblyInfo info = (SelectionVSAssemblyInfo) assembly.getInfo();

         result.put("assembly", assemblyName);
         result.put("type", describe(assembly));

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

            if(isValueMatchable(assembly)) {
               // doApplySelection matches each requested value against the live domain and
               // silently drops anything that doesn't resolve -- no exception, no counter,
               // nothing the caller could observe. Refuse a typo'd value by name, atomically,
               // before applying anything, rather than reporting an inflated count for a
               // partially-applied result.
               boolean idMode = assembly instanceof SelectionTreeVSAssembly tree && tree.isIDMode();
               List<List<String>> unmatched =
                  findUnmatchedPaths(selectionListOf(assembly), values, idMode);

               if(!unmatched.isEmpty()) {
                  throw new IllegalArgumentException(
                     "'" + assemblyName + "' has no value matching " + unmatched + " -- confirm " +
                     "the exact spelling via browse_condition_values before selecting it.");
               }
            }

            String search = searchString(info);

            if(search != null && !search.isBlank() && !single) {
               // Not a refusal: the apply is legitimate, but it lands on the filtered subset and
               // nothing in the result would otherwise say so.
               result.put("scopedBySearch", search);
            }

            if(!single && isPathDiffable(assembly) && !Boolean.TRUE.equals(additive)) {
               // Multi-select apply is a delta patch -- doApplySelection only ever turns matched
               // values on, so anything currently selected but missing from the new values has to
               // be turned off explicitly, or it stays selected alongside them. Single-select
               // already gets a full reset for free via unselectChildren. additive:true is the
               // caller explicitly opting out of this replace behaviour -- see the javadoc above.
               List<List<String>> currentPaths = selectedPaths(assembly);
               List<List<String>> toRemove = toDeselect(currentPaths, values);

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
               selections.applySelection(runtimeId, assemblyName, applyEvent(values), user, dispatcher,
                                         linkUri);
            }

            result.put("valuesSelected", values.size());
         }

         if(hasDeselect) {
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
               result.put("deselected", deselect.size());
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
    */
   static List<List<String>> deselectTargets(List<List<String>> current, List<List<String>> toRemove) {
      List<List<String>> remaining = new ArrayList<>(current);
      remaining.removeAll(toRemove);

      List<List<String>> targets = new ArrayList<>();

      for(List<String> path : toRemove) {
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
