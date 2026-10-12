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
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.erm.DataRef;
import inetsoft.util.Tool;
import inetsoft.web.binding.handler.VSColumnHandler;
import inetsoft.web.composer.model.vs.DateEditorModel;
import inetsoft.web.composer.model.vs.EditorModel;
import inetsoft.web.composer.model.vs.FloatEditorModel;
import inetsoft.web.composer.model.vs.IntegerEditorModel;
import inetsoft.web.composer.model.vs.SelectionListEditorModel;
import inetsoft.web.composer.model.vs.VariableListDialogModel;

import java.security.Principal;
import java.util.Date;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Value checks for a Date/Integer/Float column-option editor, shared by both setter paths that
 * end in the same {@code *ColumnOption} builders: {@link ColumnOptionService} (a Table column's
 * Column Options) and {@link AssemblyPropertyService}'s TextInput hook
 * ({@code textInputColumnOptionPaneModel}). A guard on only one of them leaves the other
 * accepting the same bad input with {@code ok:true}.
 *
 * <p>Ordering policy: {@code minimum > maximum} is refused; {@code minimum == maximum} is
 * allowed (an exact-value range). The Composer dialog is stricter and also refuses equal
 * bounds -- this is a deliberate choice, the defect being inverted bounds.
 *
 * <p>The embedded-list and query-source checks are shared the same way: both setters end in
 * {@code VSInputService}'s lenient list sinks ({@code Tool.getData}, {@code updateBindingInfo}),
 * reached by {@link ColumnOptionService} for a column's ComboBox editor and by
 * {@link AssemblyPropertyService} for a CheckBox/ComboBox/RadioButton assembly (bug #78393).
 */
final class WizColumnOptionValidator {
   private WizColumnOptionValidator() {
   }

   /**
    * @param tool the tool name used to prefix errors.
    * @param base the field path prefix of {@code editor}, e.g. {@code "editor."}.
    */
   static void validate(EditorModel editor, String tool, String base) {
      if(editor instanceof DateEditorModel date) {
         Date min = parseDate(date.getMinimum(), tool, base + "minimum");
         Date max = parseDate(date.getMaximum(), tool, base + "maximum");

         if(min != null && max != null && min.after(max)) {
            throw inverted(tool, base, date.getMinimum(), date.getMaximum());
         }
      }
      else if(editor instanceof IntegerEditorModel integer) {
         Integer min = integer.getMinimum();
         Integer max = integer.getMaximum();

         if(min != null && max != null && min > max) {
            throw inverted(tool, base, min, max);
         }
      }
      else if(editor instanceof FloatEditorModel flt) {
         Float min = flt.getMinimum();
         Float max = flt.getMaximum();

         if(min != null && max != null && min > max) {
            throw inverted(tool, base, min, max);
         }
      }
   }

   /**
    * The native list sinks convert each embedded value with {@code Tool.getData}, which turns
    * unparseable text into {@code null}/{@code false}/a truncated number without complaint.
    * Parsing each value strictly first turns that into a named refusal. {@code __null__} stays a
    * legal deliberate null.
    *
    * @param prefix the error prefix up to the list's own field names, e.g.
    *               {@code "set_column_options: 'editor.variableListDialogModel."}.
    */
   static void requireStrictListValues(VariableListDialogModel list, String prefix) {
      String[] values = list.getValues();
      String[] labels = list.getLabels();

      if(labels.length != 0 && labels.length != values.length) {
         throw new IllegalArgumentException(
            prefix + "labels' has " + labels.length + " entries but 'values' has " +
            values.length + " -- they must match.");
      }

      for(int i = 0; i < values.length; i++) {
         try {
            WizStrictValueParser.parse(values[i], list.getDataType());
         }
         catch(IllegalArgumentException ex) {
            throw new IllegalArgumentException(
               prefix + "values[" + i + "]': " + ex.getMessage(), ex);
         }
      }
   }

   /**
    * {@code VSInputService.updateBindingInfo} silently leaves a query source's
    * {@code labelColumn}/{@code valueColumn} unset when the named table isn't a sibling assembly
    * of this viewsheet's base worksheet, or when the named column/value isn't one of that
    * table's columns -- no exception, {@code ok:true}, an input with no options. Resolving the
    * SAME way here, via the SAME {@code vsColumnHandler.getTableColumns} call, before ever
    * persisting the source, turns that into a named refusal instead. A missing {@code value}
    * defaults to {@code column} (one column serves as both).
    *
    * @param control the input named in errors, e.g. {@code "inputControl:\"ComboBox\""}.
    * @param base    the field path prefix of {@code source}, ending in {@code '.'}.
    */
   static void requireResolvableListSource(VSColumnHandler columnHandler, RuntimeViewsheet rvs,
                                           Principal user, SelectionListEditorModel source,
                                           String tool, String control, String base)
      throws Exception
   {
      String table = source.getTable();
      String column = source.getColumn();
      String value = source.getValue();

      if(table == null || table.isBlank()) {
         throw new IllegalArgumentException(
            tool + ": " + control + " with query:true requires '" + base + "table'.");
      }

      ColumnSelection selection = columnHandler.getTableColumns(rvs, table, user);

      if(selection == null || selection.getAttributeCount() == 0) {
         throw new IllegalArgumentException(
            tool + ": '" + table + "' does not resolve to a table assembly in this " +
            "viewsheet's base worksheet -- " + control + " query source must name an " +
            "existing worksheet table.");
      }

      if(column == null || column.isBlank()) {
         throw new IllegalArgumentException(
            tool + ": " + control + " with query:true requires '" + base + "column' (the " +
            "label column) -- a table alone binds nothing.");
      }

      if(value == null || value.isBlank()) {
         source.setValue(column);
         value = column;
      }

      for(String name : new String[] { column, value }) {
         if(findAttribute(selection, name) == null) {
            throw new IllegalArgumentException(
               tool + ": '" + table + "' has no column named '" + name + "' -- valid columns " +
               "are " + columnNames(selection) + ".");
         }
      }
   }

   private static DataRef findAttribute(ColumnSelection selection, String name) {
      for(int i = 0; i < selection.getAttributeCount(); i++) {
         DataRef ref = selection.getAttribute(i);

         if(name.equals(ref.getName())) {
            return ref;
         }
      }

      return null;
   }

   private static String columnNames(ColumnSelection selection) {
      return IntStream.range(0, selection.getAttributeCount())
         .mapToObj(i -> selection.getAttribute(i).getName())
         .collect(Collectors.joining(", "));
   }

   /**
    * {@code DateColumnOption.validate()} parses {@code minimum}/{@code maximum} with
    * {@link Tool#parseDate} on every call and swallows a {@code ParseException} into an
    * unconditional {@code return false} -- an unparseable bound therefore rejects every value,
    * permanently, once stored. Reusing {@code Tool.parseDate} itself guarantees this rejects
    * exactly the strings {@code validate()} would later choke on, and none it would accept.
    */
   private static Date parseDate(String value, String tool, String field) {
      if(value == null || value.isBlank()) {
         return null;
      }

      try {
         return Tool.parseDate(value);
      }
      catch(Exception ex) {
         throw new IllegalArgumentException(
            tool + ": '" + field + "' is not a recognizable date -- '" + value + "'.", ex);
      }
   }

   private static IllegalArgumentException inverted(String tool, String base, Object min,
                                                    Object max)
   {
      return new IllegalArgumentException(
         tool + ": '" + base + "minimum' (" + min + ") must not be greater than '" + base +
         "maximum' (" + max + ").");
   }
}
