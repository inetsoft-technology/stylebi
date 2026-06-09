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
package inetsoft.web.wiz.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("core")
class WizGeoMappingResolverTest {
   @Test
   void appliedPlusDroppedClearsAllUnmatched() {
      Set<String> unmatched = new LinkedHashSet<>(List.of("Runion", "Zzz"));
      Map<String, String> applied = Map.of("Runion", "FR-RE");
      Set<String> dropped = Set.of("Zzz");

      Set<String> remaining = WizGeoMappingResolver.stillUnmatched(unmatched, applied, dropped);

      assertTrue(remaining.isEmpty(), "expected no remaining unmatched values, got " + remaining);
   }

   @Test
   void unappliedUndroppedValueRemains() {
      Set<String> unmatched = new LinkedHashSet<>(List.of("Runion", "Foo"));
      Map<String, String> applied = Map.of("Runion", "FR-RE");
      Set<String> dropped = Set.of();

      Set<String> remaining = WizGeoMappingResolver.stillUnmatched(unmatched, applied, dropped);

      assertEquals(Set.of("Foo"), remaining);
   }
}
