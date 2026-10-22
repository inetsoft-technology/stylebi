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
package inetsoft.web.admin.ai;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Bug #79016: the shared escaper and hidden-input helpers behind the admin-ai plan hashes. */
@Tag("core")
class PlanHashInputsTest {
   @Test void escapeIsTheIdentityForOrdinaryValues() {
      String ordinary = "jdbc:mysql://host:3306/db?x-y_z.1";

      assertSame(ordinary, PlanHashInputs.escape(ordinary));
      assertEquals("", PlanHashInputs.escape(null));
      assertEquals("", PlanHashInputs.escape(""));
   }

   @Test void escapePrefixesEverySeparatorAndTheEscapeCharacter() {
      assertEquals("a\\;b\\=c\\,d\\\\e", PlanHashInputs.escape("a;b=c,d\\e"));
   }

   @Test void escapedValuesThatDifferOnlyByWhereASeparatorSitsStayDistinct() {
      String a = "url=" + PlanHashInputs.escape("A;driver=B") + ";driver=" + PlanHashInputs.escape("C");
      String b = "url=" + PlanHashInputs.escape("A") + ";driver=" + PlanHashInputs.escape("B;driver=C");

      assertNotEquals(a, b);
   }

   @Test void digestIsDeterministicDomainSeparatedAndDistinguishesNull() {
      assertEquals(PlanHashInputs.digest("d1\u0000", "secret"),
                   PlanHashInputs.digest("d1\u0000", "secret"));
      assertNotEquals(PlanHashInputs.digest("d1\u0000", "secret"),
                      PlanHashInputs.digest("d2\u0000", "secret"));
      assertNotEquals(PlanHashInputs.digest("d1\u0000", "a"),
                      PlanHashInputs.digest("d1\u0000", "b"));
      assertEquals("none", PlanHashInputs.digest("d1\u0000", null));
      assertNotEquals(PlanHashInputs.digest("d1\u0000", null),
                      PlanHashInputs.digest("d1\u0000", "null"));
      assertEquals(64, PlanHashInputs.digest("d1\u0000", "secret").length());
      assertFalse(PlanHashInputs.digest("d1\u0000", "secret").contains("secret"));
   }

   @Test void hiddenInputsAppendOnlyForAKeyThatHasOne() {
      Map<String, String> hidden = new LinkedHashMap<>();
      PlanHashInputs.addHidden(hidden, "k1", "pw", "abc");
      PlanHashInputs.addHidden(hidden, "k1", "secretId", "def");

      StringBuilder with = new StringBuilder("x");
      PlanHashInputs.appendHidden(with, '|', hidden, "k1");
      StringBuilder without = new StringBuilder("x");
      PlanHashInputs.appendHidden(without, '|', hidden, "k2");
      StringBuilder nullMap = new StringBuilder("x");
      PlanHashInputs.appendHidden(nullMap, '|', null, "k1");

      assertTrue(with.toString().startsWith("xhidden:pw:abc"));
      assertTrue(with.toString().contains("secretId:def"));
      assertTrue(with.toString().endsWith("|"));
      assertEquals("x", without.toString());
      assertEquals("x", nullMap.toString());
   }
}
