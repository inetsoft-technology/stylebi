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
import inetsoft.web.wiz.worksheet.model.WorksheetPropertiesModel;
import org.junit.jupiter.api.*;

import java.util.List;

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
      assertEquals("a", tm.aggregates().groups().get(0).field());
      assertNull(tm.aggregates().groups().get(0).dateLevel());
      assertEquals(1, tm.aggregates().aggregates().size());
      assertFalse(tm.sorts().isEmpty());
   }

   @Test
   void readsDateGroupLevelOnGroupedColumn() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "orderDate", "total");
      ((ColumnRef) t.getColumnSelection(false).getAttribute("orderDate"))
         .setDataType(inetsoft.uql.schema.XSchema.DATE);
      ws.addAssembly(t);

      // Round-trip through the actual production mutator (not a hand-built GroupRef)
      // so this exercises the real shape applyAggregateInfo produces for a dateLevel
      // group - a GroupRef wrapping ColumnRef(DateRangeRef(...)), not a plain ColumnRef
      // with only setDateGroup() called - which is what WorksheetReadService's
      // field-extraction branch actually has to unwrap.
      WorksheetMutationSupport.applyAggregateInfo(t,
         List.of(new WorksheetMutationSupport.GroupSpec("orderDate", "QUARTER")),
         List.of(new WorksheetMutationSupport.AggregateSpec("total", "SUM", null)));

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      WorksheetModel m = new WorksheetReadService().read(rws);
      WorksheetModel.AggregateModel.GroupModel group = m.tables().get(0).aggregates().groups().get(0);
      assertEquals("orderDate", group.field());
      assertEquals("QUARTER", group.dateLevel());
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

   @Test
   void tableTypeDistinguishesSnapshotFromEditableEmbedded() {
      // SnapshotEmbeddedTableAssembly extends EmbeddedTableAssembly, so the snapshot branch has
      // to run first. Reported as plain "EMBEDDED", an agent had no way to tell before a write
      // that edit_cell/insert_row/delete_row would be refused on this table.
      Worksheet ws = new Worksheet();
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "E", "col"));
      ws.addAssembly(TestWorksheets.snapshotTableWithColumns(ws, "S", "col"));
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      WorksheetModel m = new WorksheetReadService().read(rws);

      assertEquals("EMBEDDED",
         typeOf(m, "E"), "a plain embedded table must keep its existing type name");
      assertEquals("EMBEDDED_SNAPSHOT",
         typeOf(m, "S"), "a snapshot must not be collapsed into EMBEDDED");
   }

   private static String typeOf(WorksheetModel m, String name) {
      return m.tables().stream()
         .filter(t -> name.equals(t.name()))
         .findFirst()
         .orElseThrow()
         .type();
   }

   // -------------------------------------------------------------------------
   // Worksheet properties
   // -------------------------------------------------------------------------

   @Test
   void readPropertiesReturnsTheWorksheetInfoValues() {
      Worksheet ws = new Worksheet();
      WorksheetInfo winfo = ws.getWorksheetInfo();
      winfo.setAlias("Quarterly revenue");
      winfo.setDescription("Set by the agent");

      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "Folder/ws1", null);
      entry.setAlias("entry alias");
      entry.setProperty("description", "entry description");
      entry.setReportDataSource(true);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getEntry()).thenReturn(entry);

      WorksheetPropertiesModel p = new WorksheetReadService().readProperties(rws);

      assertEquals("ws1", p.name());
      assertEquals("Quarterly revenue", p.alias(),
         "WorksheetInfo wins over the AssetEntry -- that is the side the properties POST writes, "
            + "and the side the Composer dialog reads first");
      assertEquals("Set by the agent", p.description());
      assertTrue(p.dataSource());
   }

   @Test
   void readPropertiesFallsBackToTheAssetEntryWhenWorksheetInfoIsUnset() {
      Worksheet ws = new Worksheet();
      assertNull(ws.getWorksheetInfo().getAlias(), "precondition: nothing set on WorksheetInfo");

      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "Folder/ws2", null);
      entry.setAlias("entry alias");
      entry.setProperty("description", "entry description");
      // Set explicitly to the non-default value: isReportDataSource() reads true whenever the
      // property is absent, so only an explicit false proves the flag is read rather than assumed.
      entry.setReportDataSource(false);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getEntry()).thenReturn(entry);

      WorksheetPropertiesModel p = new WorksheetReadService().readProperties(rws);

      assertEquals("ws2", p.name());
      assertEquals("entry alias", p.alias());
      assertEquals("entry description", p.description());
      assertFalse(p.dataSource());
   }
}
