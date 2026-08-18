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
    * @param sortOrder   {@code asc} | {@code desc} | {@code specific}, or null to leave it.
    * @param singleSelect whether the assembly should accept one value only, or null to leave it.
    */
   public Map<String, Object> setSelection(String sessionToken, Principal user, String assemblyName,
                                           List<List<String>> values, String sortOrder,
                                           Boolean singleSelect, String linkUri)
      throws Exception
   {
      requireName(assemblyName);

      if(values == null && sortOrder == null && singleSelect == null) {
         throw new IllegalArgumentException(
            "Nothing to do — give at least one of 'values', 'sortOrder' or 'singleSelect'.");
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

            String search = searchString(info);

            if(search != null && !search.isBlank() && !single) {
               // Not a refusal: the apply is legitimate, but it lands on the filtered subset and
               // nothing in the result would otherwise say so.
               result.put("scopedBySearch", search);
            }

            selections.applySelection(runtimeId, assemblyName, applyEvent(values), user, dispatcher,
                                      linkUri);
            result.put("valuesSelected", values.size());
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
         List<List<String>> current = selectedPaths(assembly);

         result.put("assembly", assemblyName);
         result.put("type", describe(assembly));
         result.put("clearedCount", current.size());

         if(!current.isEmpty()) {
            // The client composes "unselect" the same way: send every currently selected value back
            // with selected=false. There is no single clear endpoint for one assembly.
            selections.applySelection(runtimeId, assemblyName, deselectEvent(current), user,
                                      dispatcher, linkUri);
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
    */
   private static SelectionVSAssembly requireSelection(RuntimeViewsheet rvs, String assemblyName) {
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
    */
   static List<List<String>> selectedPaths(SelectionValue[] values) {
      if(values == null) {
         return List.of();
      }

      List<List<String>> paths = new ArrayList<>();

      for(SelectionValue value : values) {
         if(value != null && value.isSelected()) {
            paths.add(List.of(value.getValue() == null ? "" : value.getValue()));
         }
      }

      return paths;
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
