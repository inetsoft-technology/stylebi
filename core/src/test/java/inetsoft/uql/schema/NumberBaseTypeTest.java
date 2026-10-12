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

package inetsoft.uql.schema;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class NumberBaseTypeTest {
   // Bug #77500, an empty format must not leave unlimited fraction digits
   @Test
   void emptyFormatUsesDefaultPattern() {
      NumberBaseType type = new NumberBaseType("n");
      type.setFormat("");
      assertEquals("", type.getFormat());
      assertEquals(3, type.fmtObj.getMaximumFractionDigits());
      assertEquals("#,##0.###", type.fmtObj.toPattern());
   }

   @Test
   void nonEmptyFormatIsUnchanged() {
      NumberBaseType type = new NumberBaseType("n");
      type.setFormat("0.00");
      assertEquals(new java.text.DecimalFormat("0.00").toPattern(), type.fmtObj.toPattern());
      type.setFormat(null);
      assertNull(type.fmtObj);
   }
}
