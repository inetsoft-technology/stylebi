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
package inetsoft.web.wiz.worksheet;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.*;
import inetsoft.web.wiz.pairing.TestWorksheets;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import inetsoft.web.wiz.worksheet.model.WorksheetModel;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@WizAgentTestSupport
class WorksheetReadServiceTest {

   @Test
   void readsColumnsAggregatesConditionsSort() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a", "b");
      TestWorksheets.withGroupSumAndSort(t, "a", "b");
      ws.addAssembly(t);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      WorksheetModel m = new WorksheetReadService().read(rws);

      assertFalse(m.tables().isEmpty());
      WorksheetModel.TableModel tm = m.tables().get(0);
      assertEquals("T", tm.name());
      assertTrue(tm.columns().stream().anyMatch(c -> "a".equals(c.name())));
      assertNotNull(tm.aggregates());
      assertEquals(1, tm.aggregates().groups().size());
      assertEquals("a", tm.aggregates().groups().get(0));
      assertEquals(1, tm.aggregates().aggregates().size());
      assertFalse(tm.sorts().isEmpty());
   }

   @Test
   void nullOrEmptyAggregateInfoReturnsNullAggregates() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T2", "x");
      ws.addAssembly(t);
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      WorksheetModel m = new WorksheetReadService().read(rws);
      assertNull(m.tables().get(0).aggregates());
      assertTrue(m.tables().get(0).sorts().isEmpty());
   }

   @Test
   void tableTypeIsEmbeddedForEmbeddedTable() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "E", "col");
      ws.addAssembly(t);
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      WorksheetModel m = new WorksheetReadService().read(rws);
      assertEquals("EMBEDDED", m.tables().get(0).type());
   }
}
