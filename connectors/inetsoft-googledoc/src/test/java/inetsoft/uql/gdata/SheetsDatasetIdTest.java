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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers {@link SheetsDatasetId}. Assertion A4: the composite id must round-trip and must never
 * contain a {@code '.'} (TabularDatasetRef.id's contract).
 */
class SheetsDatasetIdTest {
   @Test
   void composeThenParseRoundTrips() {
      String id = SheetsDatasetId.compose("1AbCdEf-driveFileId", "12345");
      SheetsDatasetId.Parsed parsed = SheetsDatasetId.parse(id);

      assertEquals("1AbCdEf-driveFileId", parsed.spreadsheetId());
      assertEquals("12345", parsed.sheetId());
      assertFalse(id.contains("."));
   }

   @Test
   void composeEscapesDotsSeparatorAndPercent_thenRoundTrips() {
      // These characters cannot occur in today's Drive file id / numeric sheetId grammar, but the
      // escaping must hold anyway -- see the class javadoc: satisfied by construction, not by
      // trusting Google's grammar to stay dot-free forever.
      String spreadsheetId = "1Ab.Cd~Ef%1";
      String sheetId = "0";
      String id = SheetsDatasetId.compose(spreadsheetId, sheetId);

      assertFalse(id.contains("."));
      SheetsDatasetId.Parsed parsed = SheetsDatasetId.parse(id);
      assertEquals(spreadsheetId, parsed.spreadsheetId());
      assertEquals(sheetId, parsed.sheetId());
   }

   @Test
   void composeRoundTripsNonAsciiComponent() {
      // Non-ASCII is not part of today's Drive file id grammar either, but the escaping/round-trip
      // discipline must not assume ASCII.
      String spreadsheetId = "驱动文件Id-1";
      String sheetId = "999";
      String id = SheetsDatasetId.compose(spreadsheetId, sheetId);

      SheetsDatasetId.Parsed parsed = SheetsDatasetId.parse(id);
      assertEquals(spreadsheetId, parsed.spreadsheetId());
      assertEquals(sheetId, parsed.sheetId());
   }

   @Test
   void sheetIdZeroRoundTrips() {
      // "sheetId = 0" is Sheets' real default first sheet -- the first falsy-but-valid identity
      // component any connector on this SPI has had. A blank check on the raw string, not a
      // truthiness check, must be what parse() uses -- "0" is non-blank.
      String id = SheetsDatasetId.compose("1AbCdEf", "0");
      SheetsDatasetId.Parsed parsed = SheetsDatasetId.parse(id);

      assertEquals("0", parsed.sheetId());
   }

   @Test
   void parseRejectsMissingSeparator() {
      assertThrows(IllegalArgumentException.class,
         () -> SheetsDatasetId.parse("1AbCdEf12345"));
   }

   @Test
   void parseRejectsBlankSpreadsheetHalf() {
      assertThrows(IllegalArgumentException.class,
         () -> SheetsDatasetId.parse("~12345"));
   }

   @Test
   void parseRejectsBlankSheetHalf() {
      assertThrows(IllegalArgumentException.class,
         () -> SheetsDatasetId.parse("1AbCdEf~"));
   }

   @Test
   void parseRejectsAStringThatDoesNotRoundTrip() {
      // A raw, unescaped second separator elsewhere in the string: naive substring-splitting on
      // the first '~' would silently produce the wrong halves rather than reject this.
      assertThrows(IllegalArgumentException.class,
         () -> SheetsDatasetId.parse("1Ab~CdEf~12345"));
   }
}
