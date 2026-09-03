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
import com.google.api.services.sheets.v4.model.NumberFormat;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers {@link GDataColumnTypes} -- assertion A8 (the type mapping is total and lands on an
 * {@code XSchema} constant per branch) plus its "live" case (an unrecognised
 * {@code numberFormat.getType()} string, which is a real value a live server can send, not a
 * theoretical one).
 */
class GDataColumnTypesTest {
   private static CellData numberCell(double value, String formatType) {
      ExtendedValue ev = new ExtendedValue().setNumberValue(value);
      CellData cell = new CellData().setEffectiveValue(ev);

      if(formatType != null) {
         NumberFormat nf = new NumberFormat().setType(formatType);
         cell.setEffectiveFormat(new CellFormat().setNumberFormat(nf));
      }

      return cell;
   }

   // ----- xschemaTypeOf / numberTypeOf: one case per branch -----

   @ParameterizedTest
   @CsvSource({
      "DATE," + XSchema.DATE,
      "DATE_TIME," + XSchema.TIME_INSTANT,
      "TIME," + XSchema.TIME,
      "NUMBER," + XSchema.DOUBLE,
      "PERCENT," + XSchema.DOUBLE,
      "CURRENCY," + XSchema.DOUBLE,
      "SCIENTIFIC," + XSchema.DOUBLE,
      "TEXT," + XSchema.DOUBLE,
      "NUMBER_FORMAT_TYPE_UNSPECIFIED," + XSchema.DOUBLE
   })
   void numberTypeOf_mapsEveryDocumentedFormatType(String googleFormatType, String expected) {
      assertEquals(expected, GDataColumnTypes.numberTypeOf(googleFormatType));
   }

   @Test
   void numberTypeOf_noFormatAtAll_isDouble() {
      assertEquals(XSchema.DOUBLE, GDataColumnTypes.numberTypeOf(null));
   }

   @Test
   void numberTypeOf_unknownFormatType_isDoubleNotAnException() {
      // A8's live case: a format type the pinned 2022 jar has never heard of is a real value a
      // live server can send. A `default` that throws, or a null return, both fail this.
      assertEquals(XSchema.DOUBLE, GDataColumnTypes.numberTypeOf("SOME_FUTURE_FORMAT_TYPE"));
   }

   @Test
   void numberTypeOf_unknownFormatType_warnsOnlyOncePerDistinctValue() {
      // Not a behavioural assertion on the log itself (no test seam for that), but calling it
      // twice with the same unrecognised value and once with a different one must not throw and
      // must keep returning DOUBLE -- pins that the dedup set never turns "unknown" into an error.
      assertEquals(XSchema.DOUBLE, GDataColumnTypes.numberTypeOf("REPEATED_UNKNOWN"));
      assertEquals(XSchema.DOUBLE, GDataColumnTypes.numberTypeOf("REPEATED_UNKNOWN"));
      assertEquals(XSchema.DOUBLE, GDataColumnTypes.numberTypeOf("ANOTHER_UNKNOWN"));
   }

   // ----- typeOfCell: per-cell derivation, X1 / F5 -----

   @Test
   void typeOfCell_dateFormattedNumber_isDate() {
      assertEquals(XSchema.DATE, GDataColumnTypes.typeOfCell(numberCell(45000, "DATE")));
   }

   @Test
   void typeOfCell_dateTimeFormattedNumber_isTimeInstant() {
      assertEquals(XSchema.TIME_INSTANT,
         GDataColumnTypes.typeOfCell(numberCell(45000.5, "DATE_TIME")));
   }

   @Test
   void typeOfCell_timeFormattedNumber_isTime() {
      assertEquals(XSchema.TIME, GDataColumnTypes.typeOfCell(numberCell(0.5, "TIME")));
   }

   @Test
   void typeOfCell_plainNumber_isDouble() {
      assertEquals(XSchema.DOUBLE, GDataColumnTypes.typeOfCell(numberCell(42, null)));
   }

   @Test
   void typeOfCell_boolean_isBoolean() {
      ExtendedValue ev = new ExtendedValue().setBoolValue(true);
      assertEquals(XSchema.BOOLEAN,
         GDataColumnTypes.typeOfCell(new CellData().setEffectiveValue(ev)));
   }

   @Test
   void typeOfCell_string_isString() {
      ExtendedValue ev = new ExtendedValue().setStringValue("hello");
      assertEquals(XSchema.STRING,
         GDataColumnTypes.typeOfCell(new CellData().setEffectiveValue(ev)));
   }

   @Test
   void typeOfCell_nullCell_isNoSignal() {
      assertNull(GDataColumnTypes.typeOfCell(null));
   }

   @Test
   void typeOfCell_emptyCell_isNoSignal() {
      // X1: an empty cell contributes no signal -- the caller (GDataCatalog) is what falls back to
      // STRING when every sampled cell in a column is like this.
      assertNull(GDataColumnTypes.typeOfCell(new CellData()));
   }

   @Test
   void typeOfCell_errorValuedCell_isNoSignal() {
      // F5: an error value (e.g. #REF!) has no string/number/bool value -- a naive implementation
      // that only checks "is effectiveValue null" would miss this and fall through to
      // getStringValue() == null, which happens to also be null here, but only by accident; this
      // pins the actual rejection path (getErrorValue() != null) rather than relying on that
      // coincidence.
      ExtendedValue ev = new ExtendedValue().setErrorValue(new com.google.api.services.sheets.v4.model.ErrorValue());
      assertNull(GDataColumnTypes.typeOfCell(new CellData().setEffectiveValue(ev)));
   }
}
