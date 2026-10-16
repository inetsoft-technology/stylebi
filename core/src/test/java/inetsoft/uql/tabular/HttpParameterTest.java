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
package inetsoft.uql.tabular;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class HttpParameterTest {
   @Test
   void equalParametersHaveEqualHashCodes() {
      HttpParameter a = token("value");
      HttpParameter b = token("value");

      assertEquals(a, b);
      assertEquals(a.hashCode(), b.hashCode());
      assertEquals(1, new HashSet<>(Arrays.asList(a, b)).size());
   }

   @Test
   void parametersWithDifferentValuesAreNotEqual() {
      assertNotEquals(token("a"), token("b"));
   }

   private static HttpParameter token(String value) {
      return HttpParameter.builder()
         .type(HttpParameter.ParameterType.HEADER)
         .name("X-Shopify-Access-Token")
         .value(value)
         .build();
   }
}
