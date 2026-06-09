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

import inetsoft.uql.XCondition;
import inetsoft.web.wiz.model.WorksheetConstructionModel.FilterOperator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("core")
class WizFilterOperationTest {
   @Test
   void inMapsToOneOfNotContains() {
      // The bug: IN mapped to CONTAINS (single-value substring), honoring only the first value of a
      // multi-value IN filter. It must be ONE_OF (multi-value in-list).
      assertEquals(XCondition.ONE_OF, WizFilterOperation.operation(FilterOperator.IN));
      assertNotEquals(XCondition.CONTAINS, WizFilterOperation.operation(FilterOperator.IN));
   }

   @Test
   void comparisonOperatorsMapToStrictOps() {
      assertEquals(XCondition.EQUAL_TO, WizFilterOperation.operation(FilterOperator.EQ));
      assertEquals(XCondition.GREATER_THAN, WizFilterOperation.operation(FilterOperator.GT));
      assertEquals(XCondition.GREATER_THAN, WizFilterOperation.operation(FilterOperator.GE));
      assertEquals(XCondition.LESS_THAN, WizFilterOperation.operation(FilterOperator.LT));
      assertEquals(XCondition.LESS_THAN, WizFilterOperation.operation(FilterOperator.LE));
      assertEquals(XCondition.BETWEEN, WizFilterOperation.operation(FilterOperator.BETWEEN));
      assertEquals(XCondition.LIKE, WizFilterOperation.operation(FilterOperator.LIKE));
   }

   @Test
   void onlyGeLeAreInclusiveBounds() {
      assertTrue(WizFilterOperation.isInclusiveBound(FilterOperator.GE));
      assertTrue(WizFilterOperation.isInclusiveBound(FilterOperator.LE));
      assertFalse(WizFilterOperation.isInclusiveBound(FilterOperator.GT));
      assertFalse(WizFilterOperation.isInclusiveBound(FilterOperator.LT));
      assertFalse(WizFilterOperation.isInclusiveBound(FilterOperator.EQ));
      assertFalse(WizFilterOperation.isInclusiveBound(FilterOperator.IN));
   }
}
