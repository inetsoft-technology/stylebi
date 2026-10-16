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

import inetsoft.web.composer.model.vs.OutputColumnRefModel;
import inetsoft.web.composer.model.vs.SelectionTreePaneModel;

import java.util.*;

/**
 * Keeps {@code set_assembly_properties} out of a selection list/tree, time slider or calendar's
 * data source (bug #78480). The source pane is edited natively only through the binding tree,
 * and for the agent only through {@code set_selection_source}, which resolves the table and
 * columns and runs the additional-table checks. The dialog applies store a raw write to it
 * verbatim: an unknown or incompatible table, a column that is never re-resolved, a selection
 * tree emptied by a mode switch, a {@code null} additional-table list the slider apply then
 * dereferences.
 *
 * <p>Compares values before and after the patch loop rather than refusing paths, so every
 * spelling (alias, raw path, first-letter case variant) is covered at one site and an unchanged
 * write-back of what {@code raw:true} read is not refused. Deny by default: every leaf under the
 * source pane is compared except the allowlist below, so a sub-field the apply never reads
 * (a column's {@code table}, the target tree) is refused instead of reporting a silent no-op.
 */
final class SelectionSourceGuard {
   private SelectionSourceGuard() {
   }

   /** What a source pane held before the patch; {@code null} for every other assembly type. */
   record Snapshot(String pane, Map<String, Object> leaves, boolean boundTree) {
   }

   /**
    * Taken right after the model is read: the patch loop mutates the panes in place, so the
    * leaves are copied, never kept by reference.
    */
   static Snapshot snapshot(String type, Object model) {
      String pane = sourcePane(type);

      if(pane == null) {
         return null;
      }

      Object paneModel = PropertyPath.get(model, pane);
      return new Snapshot(pane, leaves(pane, paneModel), boundTree(paneModel));
   }

   /**
    * Refuses the patch when any compared leaf of the source pane changed. A tree's {@code mode}
    * is the one exception: on a tree with no bound column it may change (it binds nothing, and
    * the value is already domain-checked inside the patch loop); on a bound tree it is refused,
    * because the apply rebuilds the tree's columns from the other mode's fields and empties it.
    */
   static void requireSourceUnchanged(String type, Snapshot before, Object model) {
      if(before == null) {
         return;
      }

      Map<String, Object> after = leaves(before.pane(), PropertyPath.get(model, before.pane()));
      Set<String> paths = new TreeSet<>(before.leaves().keySet());
      paths.addAll(after.keySet());
      List<String> changed = new ArrayList<>();

      for(String path : paths) {
         if(before.leaves().containsKey(path) != after.containsKey(path) ||
            !Objects.equals(before.leaves().get(path), after.get(path)))
         {
            changed.add(path);
         }
      }

      String mode = before.pane() + ".mode";

      if(!before.boundTree()) {
         changed.remove(mode);
      }

      if(changed.isEmpty()) {
         return;
      }

      if(changed.contains(mode)) {
         throw new IllegalArgumentException(
            "set_assembly_properties: 'mode' (" + mode + ") can't change on a selection tree " +
            "that has bound columns -- StyleBI rebuilds the tree's columns from the other " +
            "mode's fields, which leaves the tree empty. Use set_selection_source: 'columns' " +
            "for a column tree, or 'parentIdColumn'/'idColumn'/'labelColumn' for an ID tree.");
      }

      throw new IllegalArgumentException(
         "set_assembly_properties: '" + String.join("', '", changed) + "' " +
         (changed.size() == 1 ? "is" : "are") + " part of this " + type + "'s data source, " +
         "which is not settable here -- a raw write skips the table and column resolution and " +
         "the additional-table checks. Use set_selection_source to change the table, the " +
         "additional tables or the bound column(s).");
   }

   private static String sourcePane(String type) {
      return switch(type) {
         case "selectionlist" -> "selectionListPaneModel";
         case "selectiontree" -> "selectionTreePaneModel";
         case "timeslider" -> "rangeSliderDataPaneModel";
         case "calendar" -> "calendarDataPaneModel";
         default -> null;
      };
   }

   /**
    * Not the data source: the measure is checked by {@code requireKnownSelectionMeasure},
    * {@code selectChildren} is an ID-tree display flag, {@code modelSource} is derived on read
    * (and refused as a dead field on write).
    */
   private static final Set<String> NOT_SOURCE =
      Set.of("selectionMeasurePaneModel", "selectChildren", "modelSource");

   private static boolean boundTree(Object paneModel) {
      if(!(paneModel instanceof SelectionTreePaneModel tree)) {
         return false;
      }

      OutputColumnRefModel[] columns = tree.getSelectedColumns();

      return columns != null && columns.length > 0 || tree.getParentIdRef() != null ||
         tree.getIdRef() != null || tree.getLabelRef() != null;
   }

   private static Map<String, Object> leaves(String pane, Object paneModel) {
      Map<String, Object> leaves = new LinkedHashMap<>();

      if(paneModel == null) {
         leaves.put(pane, null);
         return leaves;
      }

      for(String property : PropertyPath.propertiesOf(paneModel.getClass())) {
         if(!NOT_SOURCE.contains(property)) {
            addLeaf(leaves, pane + "." + property, PropertyPath.get(paneModel, property));
         }
      }

      return leaves;
   }

   /**
    * A column ref is expanded field by field (it has no {@code equals}, and a sub-field is
    * what a raw path writes); a null ref stays one leaf. The target trees are Immutables values
    * compared with their own {@code equals} as one leaf: a write into one rebuilds it, and an
    * unchanged one is the same instance, so the large binding tree is never walked.
    */
   private static void addLeaf(Map<String, Object> leaves, String path, Object value) {
      if(value instanceof OutputColumnRefModel column) {
         for(String property : PropertyPath.propertiesOf(OutputColumnRefModel.class)) {
            leaves.put(path + "." + property, copy(PropertyPath.get(column, property)));
         }
      }
      else {
         leaves.put(path, copy(value));
      }
   }

   private static Object copy(Object value) {
      if(value instanceof OutputColumnRefModel column) {
         Map<String, Object> fields = new LinkedHashMap<>();
         addLeaf(fields, "", column);
         return fields;
      }

      if(value instanceof Object[] array) {
         return Arrays.stream(array).map(SelectionSourceGuard::copy).toList();
      }

      if(value instanceof List<?> list) {
         return new ArrayList<>(list);
      }

      if(value instanceof Map<?, ?> map) {
         return new HashMap<>(map);
      }

      return value;
   }
}
