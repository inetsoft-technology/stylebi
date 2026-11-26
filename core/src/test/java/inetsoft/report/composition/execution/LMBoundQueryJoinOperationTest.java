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
package inetsoft.report.composition.execution;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How "Include all rows" (Bug #82313) turns a logical model relationship into an outer join.
 */
@Tag("core")
class LMBoundQueryJoinOperationTest {
   @Test
   void noIncludedTableKeepsTheRelationship() {
      assertEquals("=", LMBoundQuery.getJoinOperation("=", "ORDERS", "PRODUCTS", Set.of()));
   }

   @Test
   void includedDependentSideKeepsAllOfItsRows() {
      assertEquals("*=", LMBoundQuery.getJoinOperation("=", "ORDERS", "PRODUCTS", Set.of("ORDERS")));
   }

   @Test
   void includedIndependentSideKeepsAllOfItsRows() {
      assertEquals("=*",
                   LMBoundQuery.getJoinOperation("=", "ORDERS", "PRODUCTS", Set.of("PRODUCTS")));
   }

   @Test
   void bothSidesIncludedIsAFullOuterJoin() {
      assertEquals("*=*", LMBoundQuery.getJoinOperation(
         "=", "ORDERS", "PRODUCTS", Set.of("ORDERS", "PRODUCTS")));
   }

   @Test
   void onlyTheEqualJoinIsChanged() {
      assertEquals(">", LMBoundQuery.getJoinOperation(
         ">", "ORDERS", "PRODUCTS", Set.of("ORDERS", "PRODUCTS")));
   }

   @Test
   void aTableThatIsNotPartOfTheRelationshipChangesNothing() {
      assertEquals("=", LMBoundQuery.getJoinOperation("=", "ORDERS", "PRODUCTS", Set.of("REGIONS")));
   }
}
