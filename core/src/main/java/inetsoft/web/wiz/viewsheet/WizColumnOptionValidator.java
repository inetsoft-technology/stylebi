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

import inetsoft.util.Tool;
import inetsoft.web.composer.model.vs.DateEditorModel;
import inetsoft.web.composer.model.vs.EditorModel;
import inetsoft.web.composer.model.vs.FloatEditorModel;
import inetsoft.web.composer.model.vs.IntegerEditorModel;

import java.util.Date;

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
