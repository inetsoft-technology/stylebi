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

import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.composer.model.vs.FilterModel;
import inetsoft.web.composer.model.vs.LocalizationComponent;
import inetsoft.web.composer.model.vs.LocalizationPaneModel;
import inetsoft.web.composer.model.vs.ViewsheetPropertyDialogModel;

import java.util.*;

/**
 * Writes the two list-valued tabs of the Viewsheet Properties dialog that
 * {@code set_viewsheet_properties} accepts by raw path: the shared filters
 * ({@link PropertyAliases#FILTERS_SHARED_PATH}) and the localized component text ids
 * ({@link PropertyAliases#LOCALIZED_PATH}).
 *
 * <p>{@link PropertyPath} cannot build a list of beans from JSON, so these two paths are
 * applied here instead. Each is a full replace, matching what the dialog submits:
 * {@code ViewsheetPropertyDialogService#setViewsheetInfo} clears every filter id named in
 * {@code filtersPane.filters} (which {@code getViewsheetInfo} fills with every selection/input
 * assembly) and then applies {@code sharedFilters}; it removes every local id and then applies
 * {@code localized}. The lists are validated against the same model that read produced, before
 * anything is submitted, so a refusal writes nothing.
 */
final class SheetPaneListWriter {
   private SheetPaneListWriter() {
   }

   /** True for the two paths this class applies, so the generic path writer skips them. */
   static boolean handles(String resolvedPath) {
      return PropertyAliases.FILTERS_SHARED_PATH.equals(resolvedPath) ||
         PropertyAliases.LOCALIZED_PATH.equals(resolvedPath);
   }

   /**
    * Validates and applies the list values the patch carries, in place on the model's panes.
    *
    * @param model    the model {@code getViewsheetInfo} just read.
    * @param resolved input key to resolved path.
    * @param patch    input key to value.
    */
   static void apply(ViewsheetPropertyDialogModel model, Map<String, String> resolved,
                     Map<String, Object> patch)
   {
      for(Map.Entry<String, String> entry : resolved.entrySet()) {
         Object value = patch.get(entry.getKey());

         if(PropertyAliases.FILTERS_SHARED_PATH.equals(entry.getValue())) {
            model.filtersPane().setSharedFilters(sharedFilters(model, entry.getKey(), value));
         }
         else if(PropertyAliases.LOCALIZED_PATH.equals(entry.getValue())) {
            localized(model, entry.getKey(), value);
         }
      }
   }

   private static List<FilterModel> sharedFilters(ViewsheetPropertyDialogModel model, String key,
                                                  Object value)
   {
      List<?> items = requireList(key, value, "{column, filterId}");
      Set<String> valid = new TreeSet<>();

      // getViewsheetInfo lists every selection/input assembly in filters, minus the shared ones
      // only in the dialog UI, so the union is the candidate set either way.
      addColumns(valid, model.filtersPane().getFilters());
      addColumns(valid, model.filtersPane().getSharedFilters());

      Set<String> seen = new HashSet<>();
      List<FilterModel> result = new ArrayList<>();

      for(int i = 0; i < items.size(); i++) {
         Map<?, ?> item = requireItem(key, items.get(i), i, "{column, filterId}");
         String column = text(item, "column", "assemblyName", "assembly", "name");

         if(column == null) {
            throw refusal(key + "[" + i + "] needs 'column': the name of a selection or input " +
                          "assembly. Valid names: " + valid + ".");
         }

         if(!valid.contains(column)) {
            throw refusal(key + "[" + i + "].column '" + column + "' is not a selection or " +
                          "input assembly of this sheet (a filter id is assigned to an " +
                          "assembly, not to a data column). Valid names: " + valid + ".");
         }

         if(!seen.add(column)) {
            throw refusal(key + " names '" + column + "' more than once. Give each assembly " +
                          "one entry.");
         }

         String filterId = column;

         if(item.containsKey("filterId") && item.get("filterId") != null) {
            filterId = requireText(key + "[" + i + "].filterId", item.get("filterId"));
         }

         FilterModel filter = new FilterModel();
         filter.setColumn(column);
         filter.setFilterId(filterId);
         result.add(filter);
      }

      return result;
   }

   private static void localized(ViewsheetPropertyDialogModel model, String key, Object value) {
      List<?> items = requireList(key, value, "{name, textId}");
      LocalizationPaneModel pane = model.localizationPane();

      if(pane == null) {
         throw refusal("This sheet's Localization tab is not available to the current user (it " +
                       "is only offered to a site administrator when security is multi-tenant), " +
                       "so '" + key + "' cannot be written. Nothing was changed.");
      }

      Set<String> valid = new TreeSet<>();
      collectComponents(pane.getComponents(), valid);

      Set<String> seen = new HashSet<>();
      List<LocalizationComponent> result = new ArrayList<>();

      for(int i = 0; i < items.size(); i++) {
         Map<?, ?> item = requireItem(key, items.get(i), i, "{name, textId}");
         String name = text(item, "name", "component", "column");

         if(name == null) {
            throw refusal(key + "[" + i + "] needs 'name': a component key such as " +
                          "B5Chart^_^Title. Valid names: " + valid + ".");
         }

         if(!valid.contains(name)) {
            throw refusal(key + "[" + i + "].name '" + name + "' is not a localizable " +
                          "component of this sheet. Valid names: " + valid + ".");
         }

         if(!seen.add(name)) {
            throw refusal(key + " names '" + name + "' more than once. Give each component " +
                          "one entry.");
         }

         if(item.get("textId") == null) {
            throw refusal(key + "[" + i + "] needs a non-empty 'textId'. To remove a " +
                          "component's text id, leave its entry out of the list.");
         }

         LocalizationComponent component = new LocalizationComponent();
         component.setName(name);
         component.setTextId(requireText(key + "[" + i + "].textId", item.get("textId")));
         result.add(component);
      }

      pane.setLocalized(result);
   }

   private static void collectComponents(TreeNodeModel node, Set<String> names) {
      if(node == null) {
         return;
      }

      // The key the Composer dialog stores for a selected node: its data when it has one (a
      // sub-component, "Chart1^_^Title"), else its label (an assembly with no sub-components,
      // such as Text, is a childless leaf keyed by the assembly name).
      if(node.leaf()) {
         Object key = node.data() != null ? node.data() : node.label();

         if(key != null) {
            names.add(String.valueOf(key));
         }
      }

      for(TreeNodeModel child : node.children()) {
         collectComponents(child, names);
      }
   }

   private static void addColumns(Set<String> names, List<FilterModel> filters) {
      if(filters == null) {
         return;
      }

      for(FilterModel filter : filters) {
         if(filter.getColumn() != null) {
            names.add(filter.getColumn());
         }
      }
   }

   private static List<?> requireList(String key, Object value, String shape) {
      if(!(value instanceof List)) {
         throw refusal("'" + key + "' takes an array of " + shape + " objects, the complete " +
                       "list (an empty array clears it); got " +
                       (value == null ? "null" : value.getClass().getSimpleName()) + ".");
      }

      return (List<?>) value;
   }

   private static Map<?, ?> requireItem(String key, Object item, int index, String shape) {
      if(!(item instanceof Map)) {
         throw refusal(key + "[" + index + "] must be a " + shape + " object.");
      }

      return (Map<?, ?>) item;
   }

   /** The first non-null of the given keys, as a trimmed non-empty string; null when absent. */
   private static String text(Map<?, ?> item, String... keys) {
      for(String name : keys) {
         Object value = item.get(name);

         if(value != null) {
            return requireText(name, value);
         }
      }

      return null;
   }

   private static String requireText(String what, Object value) {
      String text = value instanceof String ? ((String) value).trim() : null;

      if(text == null || text.isEmpty()) {
         throw refusal("'" + what + "' must be a non-empty string; got " + value + ".");
      }

      return text;
   }

   private static IllegalArgumentException refusal(String message) {
      return new IllegalArgumentException(message + " Nothing was changed.");
   }
}
