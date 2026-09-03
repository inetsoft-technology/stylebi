/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.uql.gdata;

import com.google.api.services.sheets.v4.model.CellData;
import com.google.api.services.sheets.v4.model.CellFormat;
import com.google.api.services.sheets.v4.model.ExtendedValue;
import inetsoft.uql.schema.XSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps Sheets' per-CELL value/format shape onto {@link XSchema}'s type vocabulary.
 *
 * Sheets has no per-column type -- a date is a number plus a number format, both live on the
 * cell, not the column ({@code GDataRuntime.java:127-180} derives a value, never a type). This
 * class is the reusable half of that logic, re-targeted at {@code XSchema} instead of the runtime
 * value classes ({@code java.sql.Date}/{@code Timestamp}/{@code Time}/{@code Double}/
 * {@code Boolean}/{@code String}) {@code runQuery} actually puts in the table -- see
 * {@code 01-design.md} Part D.4 for the full branch-by-branch mapping table.
 */
final class GDataColumnTypes {
   private GDataColumnTypes() {
   }

   /**
    * Google's own documented {@code NumberFormatType} values. {@code NumberFormat.getType()}
    * returns a plain {@code java.lang.String} on this jar (no enum shipped by the client
    * library), so this enum exists purely to give the mapping below an exhaustive {@code switch}
    * with no {@code default} -- a local addition that forgets a case fails the compile, which is
    * as much of a compile-time canary as this connector can have. It does NOT catch Google adding
    * a brand-new type; {@link #numberTypeOf} handles that at runtime instead.
    */
   enum SheetsNumberFormat {
      NUMBER_FORMAT_TYPE_UNSPECIFIED, TEXT, NUMBER, PERCENT, CURRENCY, DATE, TIME, DATE_TIME,
      SCIENTIFIC
   }

   /**
    * The mapping table from {@code 01-design.md} Part D.4, on the enum above. {@code DATE_TIME}
    * maps to {@link XSchema#TIME_INSTANT}, not {@link XSchema#DATE}: {@code XSchema.isDateType}
    * drives {@code TabularCatalogService.toDataset}'s dimension marking, and a datetime column
    * genuinely is a time dimension.
    */
   static String xschemaTypeOf(SheetsNumberFormat format) {
      return switch(format) {                        // exhaustive over OUR enum -- no default
         case DATE -> XSchema.DATE;
         case DATE_TIME -> XSchema.TIME_INSTANT;
         case TIME -> XSchema.TIME;
         case NUMBER, PERCENT, CURRENCY, SCIENTIFIC, TEXT, NUMBER_FORMAT_TYPE_UNSPECIFIED ->
            XSchema.DOUBLE;
      };
   }

   /**
    * The lookup that meets the live server: {@code googleFormatType} is a raw string from a 2022
    * jar ({@code google-api-services-sheets:v4-rev20220927}) talking to a server Google can extend
    * at any time. An unrecognised value maps to {@link XSchema#DOUBLE} -- the same fallback
    * {@code runQuery}'s own {@code else} branch uses for every format it does not special-case
    * (X4-faithful, not a guess) -- and is WARNed once per distinct unrecognised string rather than
    * thrown on: refusing to describe an entire sheet because one cell uses a format type added
    * after this jar was pinned would be strictly worse than reporting DOUBLE and saying so in the
    * log.
    */
   static String numberTypeOf(String googleFormatType) {
      if(googleFormatType == null) {
         return XSchema.DOUBLE;                       // guard clause, never a switch default
      }

      try {
         return xschemaTypeOf(SheetsNumberFormat.valueOf(googleFormatType));
      }
      catch(IllegalArgumentException ex) {
         if(UNKNOWN_FORMATS_WARNED.add(googleFormatType)) {
            LOG.warn("Unrecognised Google Sheets number format type '{}'; reporting the column " +
               "as {}. The pinned google-api-services-sheets jar predates it.", googleFormatType,
               XSchema.DOUBLE);
         }

         return XSchema.DOUBLE;
      }
   }

   /**
    * The type signal one cell contributes, or {@code null} for "no signal from this cell" (an
    * absent value, or an error value -- F5). Never inspects {@code cell.getFormulaValue()}: that
    * field cannot appear inside {@code effectiveValue} (that is the point of "effective" -- a
    * formula cell's effective value is its computed result), so no separate branch is needed.
    */
   static String typeOfCell(CellData cell) {
      if(cell == null || cell.getEffectiveValue() == null) {
         return null;
      }

      ExtendedValue value = cell.getEffectiveValue();

      if(value.getErrorValue() != null) {
         return null;                                 // F5: an error is not a type
      }
      if(value.getNumberValue() != null) {
         CellFormat format = cell.getEffectiveFormat();
         return numberTypeOf(format == null || format.getNumberFormat() == null
            ? null : format.getNumberFormat().getType());
      }
      if(value.getBoolValue() != null) {
         return XSchema.BOOLEAN;
      }

      return value.getStringValue() == null ? null : XSchema.STRING;
   }

   // A log-dedup set holding no source data -- NOT a cache of anything read from a spreadsheet, so
   // X5's "a cache must be static and tested with two instances" does not apply to it (there is
   // nothing dataset-specific in it to go stale). static so a WARN is deduplicated across every
   // GDataRuntime instance TabularUtil.createRuntime hands out, not just within one describeDataset
   // call.
   private static final Set<String> UNKNOWN_FORMATS_WARNED = ConcurrentHashMap.newKeySet();

   private static final Logger LOG = LoggerFactory.getLogger(GDataColumnTypes.class);
}
