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

import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.web.composer.model.vs.ComboBoxEditorModel;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The couplings the Composer's list-values editor ({@code combo-box-editor.component.ts}) keeps
 * between the fields of a CheckBox/ComboBox/RadioButton's {@link ComboBoxEditorModel}, applied
 * to a {@code set_assembly_properties} patch before {@code VSInputService.setListValues} stores
 * it (bug #78470). That setter trusts the model it gets, and the agent can write any one field
 * of it alone.
 *
 * <ul>
 *   <li>A change of the editor's {@code dataType} also sets
 *   {@code variableListDialogModel.dataType}, as the dialog's {@code updateType} does. Without it
 *   the static list keeps the old type, {@code ListInputVSAssemblyInfo.getDataType()} reads that
 *   type back while the list has no values, and the next read-modify-write of a ComboBox drops
 *   its calendar bounds. On a ComboBox the change also clears {@code minDate}/{@code maxDate},
 *   turns {@code calendar} off for a non-date type and turns {@code serverTZ} on for
 *   time/timeInstant, again as {@code updateType} does. Each of these is skipped for a field
 *   the patch writes itself, so an explicit value is never overridden.</li>
 *   <li>ComboBox bounds of a date/timeInstant ComboBox must be real values in the dialog's
 *   formats, and {@code minDate} must not be after {@code maxDate}. Equal bounds are allowed.
 *   This is stricter than the dialog, which only shows a warning. Variable and script bounds
 *   ({@code $(...)}, {@code =...}) are not checked. A valid bound without zero padding is
 *   stored padded.</li>
 *   <li>A ComboBox bound or calendar mode the store would drop or ignore for the dataType, and
 *   calendar mode together with a query-populated list, are refused instead of being stored
 *   silently.</li>
 * </ul>
 *
 * <p>The rules run only when the patch sets or changes what they check, so a combo left in a
 * legacy state (a malformed bound, a stale calendar flag) is not blocked on an unrelated write.
 */
final class ListInputEditorGuard {
   private ListInputEditorGuard() {
   }

   /** The editor's fields, copied before the patch loop mutates the editor in place. */
   record Snapshot(String dataType, boolean calendar, boolean query, String minDate,
                   String maxDate)
   {
   }

   /**
    * @return the editor's fields, or {@code null} when {@code type} is not a list-input type or
    *         the model has no editor.
    */
   static Snapshot snapshot(String type, Object model) {
      ComboBoxEditorModel editor = editor(type, model);

      return editor == null ? null :
         new Snapshot(editor.getDataType(), editor.isCalendar(), editor.isQuery(),
                      editor.getMinDate(), editor.getMaxDate());
   }

   /**
    * Applies the couplings and checks to the patched model.
    *
    * @param resolvedPaths the canonical paths the patch writes.
    *
    * @throws IllegalArgumentException naming the field, when the patch would store an invalid
    *                                  or silently dropped value.
    */
   static void apply(String type, Snapshot before, Object model,
                     Collection<String> resolvedPaths)
   {
      ComboBoxEditorModel editor = editor(type, model);

      if(before == null || editor == null) {
         return;
      }

      String editorPath = editorPath(type);
      boolean combobox = "combobox".equals(type);
      boolean dataTypeChanged = !Objects.equals(before.dataType(), editor.getDataType());

      if(dataTypeChanged) {
         syncDataType(editor, editorPath, combobox, resolvedPaths);
      }

      if(!combobox) {
         return;
      }

      String dataType = editor.getDataType();
      boolean calendarChanged = before.calendar() != editor.isCalendar();
      boolean queryChanged = before.query() != editor.isQuery();
      boolean minChanged = !Objects.equals(blankToNull(before.minDate()),
                                           blankToNull(editor.getMinDate()));
      boolean maxChanged = !Objects.equals(blankToNull(before.maxDate()),
                                           blankToNull(editor.getMaxDate()));

      if(editor.isCalendar() && !isCalendarType(dataType) && (calendarChanged || dataTypeChanged)) {
         throw new IllegalArgumentException(
            "set_assembly_properties: a ComboBox calendar needs a date, time or timeInstant " +
            "dataType, but '" + editorPath + ".dataType' is '" + dataType + "', so the calendar " +
            "would be ignored. Set the dataType in the same call, or calendar:false.");
      }

      if(editor.isCalendar() && editor.isQuery() && (calendarChanged || queryChanged)) {
         throw new IllegalArgumentException(
            "set_assembly_properties: a ComboBox calendar has no query-populated list (the " +
            "Composer turns query off when calendar is ticked). Set query:false in the same " +
            "call, or calendar:false.");
      }

      if(!isBoundType(dataType)) {
         requireNoDroppedBound(editorPath, "minDate", editor.getMinDate(), dataType,
                               minChanged || dataTypeChanged);
         requireNoDroppedBound(editorPath, "maxDate", editor.getMaxDate(), dataType,
                               maxChanged || dataTypeChanged);
         return;
      }

      boolean boundsInPlay = minChanged || maxChanged || dataTypeChanged || calendarChanged ||
         writes(resolvedPaths, editorPath + ".minDate") ||
         writes(resolvedPaths, editorPath + ".maxDate") ||
         writes(resolvedPaths, editorPath + ".dataType") ||
         writes(resolvedPaths, editorPath + ".calendar");

      if(boundsInPlay) {
         requireValidBounds(editor, editorPath, dataType);
      }
   }

   /** The dialog's {@code updateType}, minus every field the patch writes itself. */
   private static void syncDataType(ComboBoxEditorModel editor, String editorPath,
                                    boolean combobox, Collection<String> resolvedPaths)
   {
      String dataType = editor.getDataType();

      if(!writes(resolvedPaths, editorPath + ".variableListDialogModel.dataType")) {
         editor.getVariableListDialogModel().setDataType(dataType);
      }

      if(!combobox) {
         return;
      }

      if(!writes(resolvedPaths, editorPath + ".minDate")) {
         editor.setMinDate("");
      }

      if(!writes(resolvedPaths, editorPath + ".maxDate")) {
         editor.setMaxDate("");
      }

      if(!isCalendarType(dataType) && !writes(resolvedPaths, editorPath + ".calendar")) {
         editor.setCalendar(false);
      }

      if((XSchema.TIME.equals(dataType) || XSchema.TIME_INSTANT.equals(dataType)) &&
         !writes(resolvedPaths, editorPath + ".serverTZ"))
      {
         editor.setServerTZ(true);
      }
   }

   /** {@code setListValues} stores a bound only for date/timeInstant and nulls it otherwise. */
   private static void requireNoDroppedBound(String editorPath, String field, String value,
                                             String dataType, boolean changed)
   {
      if(changed && !isBlank(value)) {
         throw new IllegalArgumentException(
            "set_assembly_properties: ComboBox '" + editorPath + "." + field + "' (" + value +
            ") applies only to a date or timeInstant dataType, but '" + editorPath +
            ".dataType' is '" + dataType + "', so the bound would be dropped. Set the dataType " +
            "in the same call, or clear the bound.");
      }
   }

   private static void requireValidBounds(ComboBoxEditorModel editor, String editorPath,
                                          String dataType)
   {
      LocalDateTime min = parseBound(editorPath, "minDate", editor.getMinDate(), dataType);
      LocalDateTime max = parseBound(editorPath, "maxDate", editor.getMaxDate(), dataType);

      if(min != null && max != null && min.isAfter(max)) {
         throw new IllegalArgumentException(
            "set_assembly_properties: ComboBox minDate (" + editor.getMinDate() + ") is after " +
            "maxDate (" + editor.getMaxDate() + "), so the calendar would accept no date. Swap " +
            "them; equal bounds are allowed.");
      }

      if(min != null) {
         editor.setMinDate(format(min, dataType));
      }

      if(max != null) {
         editor.setMaxDate(format(max, dataType));
      }
   }

   /**
    * The dialog's {@code DATE_PATTERN}/{@code DATETIME_PATTERN} shapes (any whitespace between
    * date and time, 1-2 digit hour/minute/second), also taking a 1-digit month or day, and
    * checked for a real calendar day and clock time.
    *
    * @return the parsed bound, or {@code null} for a blank, variable or script bound.
    */
   private static LocalDateTime parseBound(String editorPath, String field, String value,
                                           String dataType)
   {
      if(isBlank(value) || VSUtil.isVariableValue(value) || VSUtil.isScriptValue(value)) {
         return null;
      }

      boolean instant = XSchema.TIME_INSTANT.equals(dataType);
      Matcher matcher = (instant ? DATETIME : DATE).matcher(value.trim());

      if(matcher.matches()) {
         try {
            LocalDate date = LocalDate.of(Integer.parseInt(matcher.group(1)),
                                          Integer.parseInt(matcher.group(2)),
                                          Integer.parseInt(matcher.group(3)));
            LocalTime time = !instant ? LocalTime.MIDNIGHT :
               LocalTime.of(Integer.parseInt(matcher.group(4)),
                            Integer.parseInt(matcher.group(5)),
                            Integer.parseInt(matcher.group(6)));

            return LocalDateTime.of(date, time);
         }
         catch(DateTimeException ignore) {
            // not a real day or clock time, refused below
         }
      }

      throw new IllegalArgumentException(
         "set_assembly_properties: ComboBox '" + editorPath + "." + field + "' (" + value +
         ") is not a valid " + dataType + " -- use " +
         (instant ? "yyyy-MM-dd HH:mm:ss" : "yyyy-MM-dd") + " with a real " +
         (instant ? "day and time" : "calendar day") + ", or a $(variable) or =script.");
   }

   private static String format(LocalDateTime value, String dataType) {
      return value.format(XSchema.TIME_INSTANT.equals(dataType) ? DATETIME_FORMAT :
                             DateTimeFormatter.ISO_LOCAL_DATE);
   }

   /** Whether the patch writes {@code path} itself, or a pane that contains it. */
   private static boolean writes(Collection<String> resolvedPaths, String path) {
      return resolvedPaths.stream().anyMatch(
         written -> path.equals(written) || path.startsWith(written + "."));
   }

   private static ComboBoxEditorModel editor(String type, Object model) {
      if(!PropertyAliases.isListInputType(type)) {
         return null;
      }

      return PropertyPath.get(model, editorPath(type)) instanceof ComboBoxEditorModel editor ?
         editor : null;
   }

   /** Located the way {@code AssemblyPropertyService.requireValidListInput} locates it. */
   private static String editorPath(String type) {
      String embeddedPath = PropertyAliases.resolve(type, "embedded");
      return embeddedPath.substring(0, embeddedPath.lastIndexOf('.'));
   }

   /** The types {@code setListValues} keeps bounds for. */
   private static boolean isBoundType(String dataType) {
      return XSchema.DATE.equals(dataType) || XSchema.TIME_INSTANT.equals(dataType);
   }

   /** The dialog's {@code calendarEnabled()}. */
   private static boolean isCalendarType(String dataType) {
      return isBoundType(dataType) || XSchema.TIME.equals(dataType);
   }

   private static boolean isBlank(String value) {
      return value == null || value.isBlank();
   }

   private static String blankToNull(String value) {
      return isBlank(value) ? null : value;
   }

   private static final Pattern DATE = Pattern.compile("(\\d{4})-(\\d{1,2})-(\\d{1,2})");
   private static final Pattern DATETIME = Pattern.compile(
      "(\\d{4})-(\\d{1,2})-(\\d{1,2})\\s+(\\d{1,2}):(\\d{1,2}):(\\d{1,2})");
   private static final DateTimeFormatter DATETIME_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
}
