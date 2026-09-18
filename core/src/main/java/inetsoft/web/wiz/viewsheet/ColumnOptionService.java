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
import inetsoft.web.composer.model.vs.ColumnOptionDialogModel;
import inetsoft.web.composer.model.vs.EditorModel;
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
   public ColumnOptionService(ViewsheetSessionService sessions, VSInputService inputs) {
      this.sessions = sessions;
      this.inputs = inputs;
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

      ColumnOptionDialogModel model = new ColumnOptionDialogModel();
      model.setEnableColumnEditing(enableColumnEditing);
      model.setInputControl(inputControl);
      model.setEditor(editor);

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         TableVSAssemblyInfo info = requireTable(rvs, assemblyName);
         int index = resolveColumn(info, col, "set_column_options");
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
}
