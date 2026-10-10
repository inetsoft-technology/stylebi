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
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.web.binding.handler.VSColumnHandler;
import inetsoft.web.composer.model.vs.ColumnOptionDialogModel;
import inetsoft.web.composer.model.vs.ComboBoxEditorModel;
import inetsoft.web.composer.model.vs.SelectionListEditorModel;
import inetsoft.web.composer.model.vs.EditorModel;
import inetsoft.web.composer.model.vs.VariableListDialogModel;
import inetsoft.web.viewsheet.service.VSInputService;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * A Table column's Column Options -- input editor type, validation rule and error message (the
 * Composer's own column-header right-click "Column Options" dialog) -- read and write, calling
 * the same {@link VSInputService#getColumnOptionDialogModel}/{@code setColumnOptionDialogModel}
 * the native dialog controller ({@code ColumnOptionDialogController}) already calls, the same
 * technique {@link FormTableRowService} uses for {@code VSFormTableService} and
 * {@link InputValueService} uses for {@code VSInputService}'s own input-value methods.
 *
 * <p><b>{@code col} accepts either a 0-based visible-column index or a column name, resolved
 * HERE against {@link TableVSAssemblyInfo#getVisibleColumns()}</b> -- the exact collection both
 * native methods already index into -- rather than in the wiz plugin. The wiz-exposed
 * {@code get_table_binding} lists a table's <em>unfiltered</em> {@code ColumnSelection} (hidden
 * columns included, in {@code VSTableBindingFactory}/{@code TableBindingModel}'s own storage
 * order), which is a DIFFERENT index space than the visible-only one this dialog uses --
 * resolving a name to an index client-side against that list would silently return the wrong
 * column whenever any column on the table is hidden. Resolving here instead, against the same
 * collection the underlying dialog itself reads/writes, cannot drift out of sync with it.
 */
@Service
public class ColumnOptionService {
   public ColumnOptionService(ViewsheetSessionService sessions, VSInputService inputs,
                              VSColumnHandler vsColumnHandler)
   {
      this.sessions = sessions;
      this.inputs = inputs;
      this.vsColumnHandler = vsColumnHandler;
   }

   /** {@code get_column_options}. */
   public ColumnOptionDialogModel get(String sessionToken, Principal user, String assemblyName,
                                      Object col) throws Exception
   {
      return sessions.read(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         TableVSAssemblyInfo info = requireTable(rvs, assemblyName);
         int index = resolveColumn(info, col, "get_column_options");
         return inputs.getColumnOptionDialogModel(runtimeId, assemblyName, index, user);
      });
   }

   /**
    * {@code set_column_options}. {@code enableColumnEditing:false} resets the column to a blank
    * default {@code TextColumnOption} -- {@code VSInputService.setColumnOptionDialogModel}'s own
    * behavior, not something reimplemented here -- so {@code inputControl}/{@code editor} are
    * unused in that case.
    */
   public void set(String sessionToken, Principal user, String assemblyName, Object col,
                   boolean enableColumnEditing, String inputControl, EditorModel editor,
                   String linkUri) throws Exception
   {
      if(enableColumnEditing && (inputControl == null || inputControl.isBlank())) {
         throw new IllegalArgumentException(
            "set_column_options requires 'inputControl' when 'enableColumnEditing' is true.");
      }

      if(enableColumnEditing && editor != null) {
         WizColumnOptionValidator.validate(editor, "set_column_options", "editor.");
      }

      if(enableColumnEditing && editor instanceof ComboBoxEditorModel comboEditor) {
         requireColumnOptionComboBoxFields(comboEditor);

         if(comboEditor.isEmbedded()) {
            requireValidEmbeddedList(comboEditor);
         }
      }

      ColumnOptionDialogModel model = new ColumnOptionDialogModel();
      model.setEnableColumnEditing(enableColumnEditing);
      model.setInputControl(inputControl);
      model.setEditor(editor);

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         TableVSAssemblyInfo info = requireFormTable(rvs, assemblyName);
         int index = resolveColumn(info, col, "set_column_options");

         if(enableColumnEditing && editor instanceof ComboBoxEditorModel comboEditor &&
            comboEditor.isQuery())
         {
            requireResolvableComboSource(rvs, user, comboEditor);
         }

         inputs.setColumnOptionDialogModel(runtimeId, assemblyName, index, model, user, dispatcher,
                                           linkUri);
      });
   }

   private static TableVSAssemblyInfo requireTable(RuntimeViewsheet rvs, String assemblyName) {
      Viewsheet vs = rvs == null ? null : rvs.getViewsheet();
      VSAssembly assembly = vs == null ? null : vs.getAssembly(assemblyName);

      if(assembly == null) {
         throw new IllegalArgumentException("Unknown assembly '" + assemblyName + "'.");
      }

      if(!(assembly instanceof TableVSAssembly)) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is a " + assembly.getClass().getSimpleName() +
            ", not a Table assembly -- Column Options only applies to a Table's columns.");
      }

      return (TableVSAssemblyInfo) assembly.getVSAssemblyInfo();
   }

   /**
    * The native Composer only shows the "Column Options" menu item at all for a Form table
    * ({@code SimpleTableModel.java}'s {@code form = info.isForm()} -> {@code VSTableModel.form}
    * -> {@code table-actions.ts}'s {@code columnOptionsVisible}) -- mirrors
    * {@link FormTableRowService#requireForm}'s exact guard for the same assembly type, since a
    * column option written on a non-form table does not survive (discarded on write, or
    * clobbered by a later form-flip's own column-selection reset) rather than merely being
    * unread.
    */
   private static TableVSAssemblyInfo requireFormTable(RuntimeViewsheet rvs, String assemblyName) {
      TableVSAssemblyInfo info = requireTable(rvs, assemblyName);

      if(!info.isForm()) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is not a Form table -- enable Table > Form Options > Form " +
            "in the Composer before column options can be set.");
      }

      return info;
   }

   /**
    * {@code ComboBoxEditorModel} is shared with the ComboBox <em>assembly</em>, which persists
    * calendar/minDate/maxDate/defaultValue/serverTZ/noDefault. A column's
    * {@code ComboBoxColumnOption} has no slot for any of them (and {@code valid} is
    * hard-coded true on read), so a non-default value would be accepted and never saved. Defaults
    * pass so a get -> set round-trip of the read-back still works.
    */
   private static void requireColumnOptionComboBoxFields(ComboBoxEditorModel editor) {
      String unsupported = null;

      if(editor.isCalendar()) {
         unsupported = "calendar";
      }
      else if(editor.isServerTZ()) {
         unsupported = "serverTZ";
      }
      else if(editor.isNoDefault()) {
         unsupported = "noDefault";
      }
      else if(!editor.isValid()) {
         unsupported = "valid";
      }
      else if(editor.getMinDate() != null && !editor.getMinDate().isEmpty()) {
         unsupported = "minDate";
      }
      else if(editor.getMaxDate() != null && !editor.getMaxDate().isEmpty()) {
         unsupported = "maxDate";
      }
      else if(editor.getDefaultValue() != null && !editor.getDefaultValue().isEmpty()) {
         unsupported = "defaultValue";
      }

      if(unsupported != null) {
         throw new IllegalArgumentException(
            "set_column_options: 'editor." + unsupported + "' is not supported by a column's " +
            "ComboBox editor and would not be saved. calendar, minDate, maxDate, defaultValue, " +
            "serverTZ and noDefault belong to the ComboBox assembly -- set them with " +
            "set_assembly_properties on a ComboBox assembly.");
      }
   }

   /**
    * {@code VSInputService.setColumnOptionDialogModel} converts each embedded value with
    * {@code Tool.getData}, which turns unparseable text into {@code null}/{@code false}/a
    * truncated number without complaint. Parsing each value strictly first turns that into a
    * named refusal. {@code __null__} stays a legal deliberate null.
    */
   private static void requireValidEmbeddedList(ComboBoxEditorModel editor) {
      String prefix = "set_column_options: 'editor.variableListDialogModel.";
      VariableListDialogModel list = editor.getVariableListDialogModel();
      String[] values = list.getValues();
      String[] labels = list.getLabels();

      if(labels.length != 0 && labels.length != values.length) {
         throw new IllegalArgumentException(
            prefix + "labels' has " + labels.length + " entries but 'values' has " +
            values.length + " -- they must match.");
      }

      String listType = list.getDataType();
      String editorType = editor.getDataType();

      if(listType != null && !listType.isBlank() && editorType != null &&
         !editorType.isBlank() && !listType.equalsIgnoreCase(editorType))
      {
         throw new IllegalArgumentException(
            prefix + "dataType' ('" + listType + "') disagrees with 'editor.dataType' ('" +
            editorType + "') -- use the same data type for both.");
      }

      for(int i = 0; i < values.length; i++) {
         try {
            WizStrictValueParser.parse(values[i], listType);
         }
         catch(IllegalArgumentException ex) {
            throw new IllegalArgumentException(
               prefix + "values[" + i + "]': " + ex.getMessage(), ex);
         }
      }
   }

   /**
    * {@code VSInputService.updateBindingInfo} silently leaves a ComboBox query source's
    * {@code labelColumn}/{@code valueColumn} unset when the named table isn't a sibling assembly
    * of this viewsheet's base worksheet, or when the named column/value isn't one of that
    * table's columns -- no exception, {@code ok:true}, an editor with no options. Resolving the
    * SAME way here, via the SAME {@code vsColumnHandler.getTableColumns} call, before ever
    * persisting the editor, turns that into a named refusal instead.
    */
   private void requireResolvableComboSource(RuntimeViewsheet rvs, Principal user,
                                             ComboBoxEditorModel comboEditor) throws Exception
   {
      SelectionListEditorModel source =
         comboEditor.getSelectionListDialogModel().getSelectionListEditorModel();
      String table = source.getTable();
      String column = source.getColumn();
      String value = source.getValue();

      if(table == null || table.isBlank()) {
         throw new IllegalArgumentException(
            "set_column_options: inputControl:\"ComboBox\" with query:true requires " +
            "'editor.selectionListDialogModel.selectionListEditorModel.table'.");
      }

      ColumnSelection selection = vsColumnHandler.getTableColumns(rvs, table, user);

      if(selection.getAttributeCount() == 0) {
         throw new IllegalArgumentException(
            "set_column_options: '" + table + "' does not resolve to a table assembly in this " +
            "viewsheet's base worksheet -- inputControl:\"ComboBox\" query source must name an " +
            "existing worksheet table.");
      }

      if(column == null || column.isBlank()) {
         throw new IllegalArgumentException(
            "set_column_options: inputControl:\"ComboBox\" with query:true requires " +
            "'editor.selectionListDialogModel.selectionListEditorModel.column' (the label " +
            "column) -- a table alone binds nothing.");
      }

      if(value == null || value.isBlank()) {
         // Same default as set_assembly_properties' list values: one column serves as both.
         source.setValue(column);
         value = column;
      }

      if(findAttribute(selection, column) == null) {
         throw new IllegalArgumentException(
            "set_column_options: '" + table + "' has no column named '" + column + "' -- " +
            "valid columns are " + columnNames(selection) + ".");
      }

      if(findAttribute(selection, value) == null) {
         throw new IllegalArgumentException(
            "set_column_options: '" + table + "' has no column named '" + value + "' -- " +
            "valid columns are " + columnNames(selection) + ".");
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

   /**
    * A pure-digit string is treated as an index, not a column name -- a column literally named
    * e.g. "2" is not a case worth trading away the overwhelmingly common "col: 3" usage for.
    */
   private static int resolveColumn(TableVSAssemblyInfo info, Object col, String tool) {
      ColumnSelection visible = info.getVisibleColumns();

      if(col instanceof Number number) {
         return requireInBounds(visible, number.intValue(), tool);
      }

      if(col instanceof String text) {
         String trimmed = text.trim();

         if(trimmed.matches("\\d+")) {
            return requireInBounds(visible, Integer.parseInt(trimmed), tool);
         }

         DataRef ref = visible.getAttribute(trimmed);

         if(ref != null) {
            int index = visible.indexOfAttribute(ref);

            if(index >= 0) {
               return index;
            }
         }

         throw new IllegalArgumentException(
            tool + ": no visible column named '" + trimmed + "' on this table. Columns are: " +
            columnNames(visible) + ".");
      }

      throw new IllegalArgumentException(
         tool + " requires 'col' -- a column name or 0-based visible column index.");
   }

   private static int requireInBounds(ColumnSelection visible, int index, String tool) {
      if(index < 0 || index >= visible.getAttributeCount()) {
         throw new IllegalArgumentException(
            tool + ": col " + index + " is out of range -- this table has " +
            visible.getAttributeCount() + " visible column(s).");
      }

      return index;
   }

   private static String columnNames(ColumnSelection visible) {
      return IntStream.range(0, visible.getAttributeCount())
         .mapToObj(i -> visible.getAttribute(i).getName())
         .collect(Collectors.joining(", "));
   }

   private final ViewsheetSessionService sessions;
   private final VSInputService inputs;
   private final VSColumnHandler vsColumnHandler;
}
