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

import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.XDimensionRef;

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
}
