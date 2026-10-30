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

import inetsoft.uql.viewsheet.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Bug #79829: a refusal must say why; the option's generated default text is not a reason. */
@Tag("core")
class FormTableRowServiceRefusalMessageTest {
   private static final IllegalArgumentException PARSER =
      new IllegalArgumentException("'10:30' is not a valid time value.");

   @Test
   void unconfiguredTextOptionDoesNotBlameANullPattern() {
      String msg = FormTableRowService.refusalMessage(
         new TextColumnOption(null, null, true), "10:30", PARSER);
      assertEquals(PARSER.getMessage(), msg);
      assertFalse(msg.contains("null"), msg);
   }

   @Test
   void unconfiguredDateOptionUsesTheParserReason() {
      String msg = FormTableRowService.refusalMessage(
         new DateColumnOption(null, null, null, true), "2026-02-30", PARSER);
      assertEquals(PARSER.getMessage(), msg);
   }

   @Test
   void configuredMessageIsUsedWithTheValueSubstituted() {
      assertEquals("nope 7", FormTableRowService.refusalMessage(
         new TextColumnOption("a", "nope {0}", true), "7", PARSER));
   }

   @Test
   void noOptionFallsBackToTheParserReason() {
      assertEquals(PARSER.getMessage(), FormTableRowService.refusalMessage(null, "x", PARSER));
   }
}
