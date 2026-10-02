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

import inetsoft.uql.XConstants;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.VSFormat;
import inetsoft.uql.viewsheet.XDimensionRef;
import inetsoft.uql.viewsheet.graph.ChartRef;

import java.util.List;
import java.util.Set;

/**
 * Type checks shared by the wiz format endpoints, so a format that cannot take effect on the
 * values it would reach is refused instead of being stored and reported as a success.
 */
public final class WizFormatChecks {
   private WizFormatChecks() {
   }

   /**
    * The data type of the values {@code ref} actually renders, as opposed to its source column's
    * type. A date dimension grouped at a level renders that level's values: a part level such as
    * {@code MonthOfYear} renders integers (1-12), a full level such as {@code Month} renders
    * timestamps, and no grouping keeps the column's own type ({@link DateRangeRef#getDataType}).
    * Anything else is the ref's declared type.
    *
    * @return the effective type, or null/empty when the type is not known; callers treat an
    *         unknown type as "allow" rather than refusing on a guess.
    */
   public static String effectiveType(DataRef ref) {
      if(ref == null) {
         return null;
      }

      String type;

      try {
         type = ref.getDataType();

         if(ref instanceof XDimensionRef dim && XSchema.isDateType(type)) {
            type = DateRangeRef.getDataType(dim.getDateLevel(), type);
         }
      }
      catch(RuntimeException e) {
         // A dynamic level that cannot be evaluated here, or a ref that cannot report its type:
         // unknown, so the check fails open.
         return null;
      }

      return type;
   }

   /** Whether {@code ref}'s effective values are numbers. False when the type is unknown. */
   public static boolean isNumeric(DataRef ref) {
      return XSchema.isNumericType(effectiveType(ref));
   }

   /** Whether {@code ref}'s effective values are dates or times. False when unknown. */
   public static boolean isDateLike(DataRef ref) {
      return XSchema.isDateType(effectiveType(ref));
   }

   /** Formats that only mean something on a numeric field. */
   public static final Set<String> NUMERIC_ONLY_FORMATS = Set.of(
      XConstants.PERCENT_FORMAT, XConstants.CURRENCY_FORMAT, XConstants.DECIMAL_FORMAT);

   /**
    * Rejects a format that cannot mean anything for the chart field it names -- a numeric format
    * on a string dimension, or a date format on something that is not a date. StyleBI accepts
    * such a format without complaint and renders the value exactly as before (or, for a date
    * format on numbers, as epoch dates), so nothing downstream would ever reveal that the
    * request had no effect.
    *
    * <p>The field is judged by its {@link #effectiveType effective type}: a date dimension
    * grouped at a part level such as {@code MonthOfYear} holds integers, so a date format is
    * refused there and a numeric format allowed.
    *
    * <p>Only a KNOWN mismatch is rejected. A name that matches no ref is reported separately by
    * the caller, and a ref with no declared data type is left alone: refusing a valid edit on a
    * guess is worse than letting an odd one through. DurationFormat and MessageFormat are not
    * constrained -- the composer's Format pane offers every type for every field, so this must
    * not be stricter than the pane except where the outcome is provably nothing.
    *
    * <p>Moved here from {@code WizAutoBindingService} (bug #77597) so the agent
    * {@code set_format} field target and the wizard's chart-format endpoint share one rule.
    */
   public static void checkFormatFitsFieldType(String fullName, VSFormat format,
                                               List<ChartRef> refs)
   {
      String formatValue = format.getFormatValue();

      if(formatValue == null) {
         return;
      }

      String dataType = refs.stream()
         .filter(ref -> fullName.equals(ref.getFullName()))
         .map(WizFormatChecks::effectiveType)
         .filter(type -> type != null && !type.isEmpty())
         .findFirst()
         .orElse(null);

      if(dataType == null) {
         return;
      }

      boolean mismatch = NUMERIC_ONLY_FORMATS.contains(formatValue) && !XSchema.isNumericType(dataType)
         || XConstants.DATE_FORMAT.equals(formatValue) && !XSchema.isDateType(dataType);

      if(mismatch) {
         throw new IllegalArgumentException(
            "Format '" + formatValue + "' does not apply to field '" + fullName +
            "' (data type " + dataType + ")");
      }
   }
}
