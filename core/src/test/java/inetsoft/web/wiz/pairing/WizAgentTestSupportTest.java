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
package inetsoft.web.wiz.pairing;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke tests for {@link WizAgentTestSupport} and {@link TestWorksheets}.
 *
 * [worksheetInstantiation]  Worksheet can be constructed within the SreeHome context
 * [tableWithColumns]        creates expected column structure
 * [withGroupSumAndSort]     sets AggregateInfo and SortInfo correctly
 */
@WizAgentTestSupport
class WizAgentTestSupportTest {

   @Test
   void worksheetCanBeInstantiatedWithSreeHome() {
      Worksheet ws = new Worksheet();
      assertNotNull(ws);
   }

   @Test
   void tableWithColumnsCreatesExpectedStructure() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t =
         TestWorksheets.tableWithColumns(ws, "Orders", "id", "amount", "status");

      ColumnSelection cs = t.getColumnSelection(false);
      assertNotNull(cs.getAttribute("id"),     "column 'id' should exist");
      assertNotNull(cs.getAttribute("amount"), "column 'amount' should exist");
      assertNotNull(cs.getAttribute("status"), "column 'status' should exist");
      assertEquals(3, cs.getAttributeCount(), "should have exactly 3 columns");
   }

   @Test
   void withGroupSumAndSortConfiguresAggregateAndSort() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t =
         TestWorksheets.tableWithColumns(ws, "Sales", "category", "revenue");

      TestWorksheets.withGroupSumAndSort(t, "category", "revenue");

      AggregateInfo ainfo = t.getAggregateInfo();
      assertNotNull(ainfo);
      assertEquals(1, ainfo.getGroupCount(),     "should have 1 group dimension");
      assertEquals(1, ainfo.getAggregateCount(), "should have 1 aggregate");
      assertEquals("category", ainfo.getGroup(0).getAttribute());
      assertEquals(AggregateFormula.SUM, ainfo.getAggregate(0).getFormula());

      SortInfo sinfo = t.getSortInfo();
      assertNotNull(sinfo);
      assertEquals(1, sinfo.getSortCount(), "should have 1 sort entry");
      assertEquals(XConstants.SORT_ASC, sinfo.getSort(0).getOrder());
      assertEquals("category", sinfo.getSort(0).getAttribute());
   }
}
