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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The strict parse shared by set_column_options (#78154) and, later, set_parameters (#78156). */
@Tag("core")
class WizStrictValueParserTest {
   @Test
   void parsesValidValuesToTheirTypes() {
      assertEquals(15, WizStrictValueParser.parse("15", "integer"));
      assertEquals(15, WizStrictValueParser.parse(" 15.0 ", "Integer"));
      assertEquals((short) 7, WizStrictValueParser.parse("7", "short"));
      assertEquals((byte) 7, WizStrictValueParser.parse("7", "byte"));
      assertEquals(5000000000L, WizStrictValueParser.parse("5000000000", "long"));
      assertEquals(1.5d, WizStrictValueParser.parse("1.5", "double"));
      assertEquals(1.5f, WizStrictValueParser.parse("1.5", "float"));
      assertEquals(Boolean.TRUE, WizStrictValueParser.parse("TRUE", "boolean"));
      assertEquals(Boolean.FALSE, WizStrictValueParser.parse("false", "boolean"));
      assertEquals("abc", WizStrictValueParser.parse("abc", "string"));
      assertEquals("abc", WizStrictValueParser.parse("abc", null));
      assertEquals('x', WizStrictValueParser.parse("x", "character"));
      assertNotNull(WizStrictValueParser.parse("2026-01-31", "date"));
   }

   @Test
   void nullAndTheDeliberateNullMarkerParseToNull() {
      assertNull(WizStrictValueParser.parse(null, "integer"));
      assertNull(WizStrictValueParser.parse("__null__", "integer"));
      assertNull(WizStrictValueParser.parse("__null__", "boolean"));
   }

   /** Tool.getData turns each of these into null, FALSE, a truncated or a wrapped number. */
   @Test
   void refusesWhatToolGetDataSilentlyMangles() {
      for(String[] bad : new String[][] {
         { "abc", "integer" }, { "12abc", "integer" }, { "1.5", "integer" }, { "1.5", "short" },
         { "300", "byte" }, { "99999", "short" }, { "3000000000", "integer" },
         { "abc", "boolean" }, { "yes", "boolean" }, { "1", "boolean" },
         { "abc", "double" }, { "NaN", "double" }, { "Infinity", "float" }, { "abc", "long" },
         { "", "integer" }, { "xy", "character" }, { "not-a-date", "date" } })
      {
         IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> WizStrictValueParser.parse(bad[0], bad[1]), bad[0] + " as " + bad[1]);
         assertTrue(e.getMessage().contains("'" + bad[0] + "'"), e.getMessage());
         assertTrue(e.getMessage().contains(bad[1]), e.getMessage());
      }
   }

   @Test
   void refusesAnUnknownDataType() {
      Exception e = assertThrows(IllegalArgumentException.class,
         () -> WizStrictValueParser.parse("1", "widget"));
      assertTrue(e.getMessage().contains("widget"), e.getMessage());
   }
}
