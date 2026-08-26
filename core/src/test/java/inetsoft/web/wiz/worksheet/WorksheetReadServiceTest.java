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
import inetsoft.report.internal.binding.BaseField;
import inetsoft.uql.Condition;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XCondition;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.web.wiz.pairing.TestWorksheets;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import inetsoft.web.wiz.worksheet.model.WorksheetModel;
import inetsoft.web.wiz.worksheet.model.WorksheetPropertiesModel;
import org.junit.jupiter.api.*;

import java.awt.Point;
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

   private static WorksheetModel.TableModel tableNamed(WorksheetModel m, String name) {
      return m.tables().stream().filter(t -> name.equals(t.name())).findFirst().orElseThrow();
   }

   private static WorksheetModel read(Worksheet ws) {
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      return new WorksheetReadService().read(rws);
   }

   private static ConcatenatedTableAssembly concat(Worksheet ws, String name, int operation,
                                                   TableAssembly... sources)
   {
      int[] operations = new int[sources.length - 1];

      for(int i = 0; i < operations.length; i++) {
         operations[i] = operation;
      }

      return concat(ws, name, operations, sources);
   }

   /** One operation per adjacent pair, so a mixed concatenation can be built. */
   private static ConcatenatedTableAssembly concat(Worksheet ws, String name, int[] operations,
                                                   TableAssembly... sources)
   {
      TableAssemblyOperator[] operators = new TableAssemblyOperator[sources.length - 1];

      for(int i = 0; i < operators.length; i++) {
         TableAssemblyOperator top = new TableAssemblyOperator();
         TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
         op.setOperation(operations[i]);
         top.addOperator(op);
         operators[i] = top;
      }

      return new ConcatenatedTableAssembly(ws, name, sources, operators);
   }

   /**
    * The order is the point, not just the membership: a concatenation takes its whole column list
    * from whichever subtable is first, so a caller that knows the names but not their order still
    * cannot tell which table decides the shape.
    */
   @Test
   void concatenationReportsItsSubtablesInOrder() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly a = TestWorksheets.tableWithColumns(ws, "A", "col");
      EmbeddedTableAssembly b = TestWorksheets.tableWithColumns(ws, "B", "col");
      ws.addAssembly(a);
      ws.addAssembly(b);
      ws.addAssembly(concat(ws, "U", TableAssemblyOperator.UNION, a, b));

      WorksheetModel.TableModel u = tableNamed(read(ws), "U");

      assertEquals("CONCAT", u.type());
      assertEquals(List.of("A", "B"), u.sources());
      assertEquals("UNION", u.concatType());
   }

   /**
    * A concatenation whose operator map disagrees with its subtable list must not take the whole
    * worksheet down with it.
    *
    * <p>{@code getOperatorCount()} reports the map's size while {@code getOperator(int)} indexes
    * {@code tnames}, so reading the operators by iterating {@code 0..getOperatorCount()} walks
    * off the end of the array as soon as the map holds a pair that is not adjacent. That is how
    * a single bad assembly turned every {@code read} of its worksheet into an HTTP 500 -- the
    * agent could no longer see ANY table, including the ones it needed in order to repair the
    * broken one.</p>
    *
    * <p>The way the map got into that state has been fixed (see
    * {@code CompositeTableAssembly.reorderTableAssemblies}), but the read must not depend on
    * that being the only way in: it is the one call an agent makes to find out what is wrong.
    * Subtable count is the honest bound, and it is what {@code readConcatCompatible} already
    * guards for the same reason.</p>
    */
   @Test
   void inconsistentOperatorMapDoesNotFailTheWholeRead() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly a = TestWorksheets.tableWithColumns(ws, "A", "col");
      EmbeddedTableAssembly b = TestWorksheets.tableWithColumns(ws, "B", "col");
      EmbeddedTableAssembly c = TestWorksheets.tableWithColumns(ws, "C", "col");
      ws.addAssembly(a);
      ws.addAssembly(b);
      ws.addAssembly(c);

      ConcatenatedTableAssembly u = concat(ws, "U", TableAssemblyOperator.UNION, a, b, c);
      ws.addAssembly(u);

      // (A,C) is not an adjacent pair, so the map now holds three operators for three subtables.
      u.setOperator("A", "C", unionOperator());

      WorksheetModel m = assertDoesNotThrow(() -> read(ws));

      assertEquals("UNION", tableNamed(m, "U").concatType());
      assertEquals(List.of("A", "B", "C"), tableNamed(m, "U").sources());
      assertEquals(4, m.tables().size(), "the other tables must still be readable");
   }

   private static TableAssemblyOperator unionOperator() {
      TableAssemblyOperator top = new TableAssemblyOperator();
      TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
      op.setOperation(TableAssemblyOperator.UNION);
      top.addOperator(op);

      return top;
   }

   @Test
   void concatenationReportsIntersectAndMinus() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly a = TestWorksheets.tableWithColumns(ws, "A", "col");
      EmbeddedTableAssembly b = TestWorksheets.tableWithColumns(ws, "B", "col");
      ws.addAssembly(a);
      ws.addAssembly(b);
      ws.addAssembly(concat(ws, "I", TableAssemblyOperator.INTERSECT, a, b));
      ws.addAssembly(concat(ws, "M", TableAssemblyOperator.MINUS, a, b));

      WorksheetModel m = read(ws);

      assertEquals("INTERSECT", tableNamed(m, "I").concatType());
      assertEquals("MINUS", tableNamed(m, "M").concatType());
   }

   /**
    * Reports the flag's <em>effective</em> value, which for a mirror whose source lives in the same
    * worksheet is always {@code true} no matter what was set: {@code MirrorAssemblyImpl} answers
    * {@code auto || !isOuterMirror()} and its setter returns early for anything that is not an
    * outer mirror, so the assignment is dropped without a word. That is exactly why this field is
    * worth exposing — a caller that sets the flag and reads back {@code true} can tell the setting
    * did not take, which was previously unknowable through any API.
    */
   @Test
   void mirrorReportsItsSourceAndEffectiveAutoUpdateFlag() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly base = TestWorksheets.tableWithColumns(ws, "BASE", "col");
      ws.addAssembly(base);
      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, "M", base);
      ws.addAssembly(mirror);
      mirror.setAutoUpdate(false);

      WorksheetModel.TableModel m = tableNamed(read(ws), "M");

      assertEquals("MIRROR", m.type());
      assertEquals(List.of("BASE"), m.sources());
      assertEquals(Boolean.TRUE, m.autoUpdate());
      assertNull(m.concatType());
   }

   /**
    * A hidden column stays in the model but leaves the data, and is also excluded from the public
    * column selection a concatenation counts. Without this flag a caller comparing the model
    * against real rows reads a hidden column as a missing one, and a caller comparing source
    * column counts gets a number the server would not agree with.
    */
   @Test
   void hiddenColumnIsReportedButMarkedNotVisible() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "keep", "hide");
      ((ColumnRef) t.getColumnSelection(false).getAttribute("hide")).setVisible(false);
      ws.addAssembly(t);

      WorksheetModel.TableModel tm = tableNamed(read(ws), "T");

      assertEquals(2, tm.columns().size());
      assertTrue(columnNamed(tm, "keep").visible());
      assertFalse(columnNamed(tm, "hide").visible());
   }

   private static WorksheetModel.ColumnModel columnNamed(WorksheetModel.TableModel t, String name) {
      return t.columns().stream().filter(c -> name.equals(c.name())).findFirst().orElseThrow();
   }

   /**
    * A plain table has no sources, and must not be confused with a composite whose sources simply
    * were not reported — which is exactly the ambiguity this field exists to remove.
    */
   @Test
   void plainTableReportsNoSourcesAndNoCompositeFields() {
      Worksheet ws = new Worksheet();
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "T", "col"));

      WorksheetModel.TableModel t = tableNamed(read(ws), "T");

      assertTrue(t.sources().isEmpty());
      assertNull(t.concatType());
      assertNull(t.concatCompatible());
      assertNull(t.autoUpdate());
   }

   /**
    * What the agent actually receives on the wire. A {@code null} field tells an LLM nothing that an
    * absent field does not, and there are three of them on every plain table; an <em>empty list</em>
    * is a real answer ("built on nothing", "no predicates") and has to survive, which is why the
    * model asks for {@code NON_NULL} and not {@code NON_EMPTY}.
    */
   @Test
   void serializedModelOmitsNullFieldsButKeepsEmptyLists() throws Exception {
      Worksheet ws = new Worksheet();
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "T", "col"));

      String json = new com.fasterxml.jackson.databind.ObjectMapper()
         .writeValueAsString(read(ws));

      assertFalse(json.contains("concatType"), json);
      assertFalse(json.contains("concatCompatible"), json);
      assertFalse(json.contains("autoUpdate"), json);
      assertFalse(json.contains("aggregates"), json);
      assertTrue(json.contains("\"sources\":[]"), json);
      assertTrue(json.contains("\"joins\":[]"), json);
   }

   /**
    * A rotate has exactly one source but is <em>not</em> a {@code CompositeTableAssembly} — it
    * extends {@code ComposedTableAssembly} directly, as does an unpivot. A source lookup written
    * against composites and mirrors alone reports both as having no sources at all, which is the
    * ambiguity this field exists to remove, on table types {@code add_rotate} / {@code add_unpivot}
    * let an agent create.
    */
   @Test
   void rotatedTableReportsItsSource() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly base = TestWorksheets.tableWithColumns(ws, "BASE", "col");
      ws.addAssembly(base);
      ws.addAssembly(new RotatedTableAssembly(ws, "R", base));

      WorksheetModel.TableModel r = tableNamed(read(ws), "R");

      assertEquals("ROTATED", r.type());
      assertEquals(List.of("BASE"), r.sources());
      assertNull(r.autoUpdate());
   }

   @Test
   void unpivotTableReportsItsSource() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly base = TestWorksheets.tableWithColumns(ws, "BASE", "a", "b");
      ws.addAssembly(base);
      ws.addAssembly(new UnpivotTableAssembly(ws, "P", base));

      WorksheetModel.TableModel p = tableNamed(read(ws), "P");

      assertEquals("UNPIVOT", p.type());
      assertEquals(List.of("BASE"), p.sources());
      assertNull(p.autoUpdate());
   }

   /**
    * A concatenation holds one operator per adjacent pair and Composer sets the operation per
    * connection, so {@code A UNION B MINUS C} is a legal worksheet. Reporting the first pair's
    * operation as though it described the whole assembly hands the caller a confidently wrong
    * answer about row semantics — worse than reporting nothing, since the caller cannot tell it is
    * wrong.
    */
   @Test
   void concatenationWithDifferentOperationPerPairReportsMixed() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly a = TestWorksheets.tableWithColumns(ws, "A", "col");
      EmbeddedTableAssembly b = TestWorksheets.tableWithColumns(ws, "B", "col");
      EmbeddedTableAssembly c = TestWorksheets.tableWithColumns(ws, "C", "col");
      ws.addAssembly(a);
      ws.addAssembly(b);
      ws.addAssembly(c);
      ws.addAssembly(concat(ws, "X",
         new int[]{ TableAssemblyOperator.UNION, TableAssemblyOperator.MINUS }, a, b, c));

      WorksheetModel.TableModel x = tableNamed(read(ws), "X");

      assertEquals(List.of("A", "B", "C"), x.sources());
      assertEquals("MIXED", x.concatType());
   }

   /**
    * Sources are combined by position, so a pair that lines up numerically but not by type produces
    * a column carrying two unrelated kinds of value. Composer computes exactly this predicate into
    * a non-blocking warning ({@code ConcatenatedTableAssemblyModel.concatenationWarning}); without
    * it on the read model, an agent cannot see the problem in a concatenation it did not create,
    * since {@code add_concatenation} now refuses to build one.
    */
   @Test
   void concatenationReportsWhetherItsSourcesLineUpByType() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly a = TestWorksheets.tableWithColumns(ws, "A", "col");
      EmbeddedTableAssembly b = TestWorksheets.tableWithColumns(ws, "B", "col");
      EmbeddedTableAssembly c = TestWorksheets.tableWithColumns(ws, "C", "col");
      ((ColumnRef) c.getColumnSelection(false).getAttribute("col"))
         .setDataType(inetsoft.uql.schema.XSchema.INTEGER);
      // The public selection holds clones taken when the private one was installed, and it is the
      // public selection the compatibility check reads — so it has to be regenerated for the new
      // type to reach it.
      c.setColumnSelection(c.getColumnSelection(false), false);
      ws.addAssembly(a);
      ws.addAssembly(b);
      ws.addAssembly(c);
      ws.addAssembly(concat(ws, "OK", TableAssemblyOperator.UNION, a, b));
      ws.addAssembly(concat(ws, "BAD", TableAssemblyOperator.UNION, a, c));

      WorksheetModel m = read(ws);

      assertEquals(Boolean.TRUE, tableNamed(m, "OK").concatCompatible());
      assertEquals(Boolean.FALSE, tableNamed(m, "BAD").concatCompatible());
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

   // The write tools set description, maxRows, distinct, mode and position, and none of them came
   // back in the model -- so a caller could not tell a working write from a dropped one. That is
   // what left L1 case 1.19 unable to round-trip and 1.16 unable to verify a position at all.

   @Test
   void reportsTheTablePropertiesTheWriteToolsSet() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "col");
      t.setDescription("what this table is for");
      t.setMaxRows(25);
      t.setDistinct(true);
      ws.addAssembly(t);

      WorksheetModel.TableModel m = tableNamed(read(ws), "T");

      assertEquals("what this table is for", m.description());
      assertEquals(Integer.valueOf(25), m.maxRows());
      assertTrue(m.distinct());
   }

   /**
    * -1 is how the assembly stores "unlimited", and reporting it verbatim would read back as a
    * real limit of -1. 0 is the same unlimited, not a limit of zero rows -- the engine applies a
    * limit only when it is positive -- so everything {@code <= 0} reports null.
    *
    * <p>What this asserts holds only with no global cap configured, which is this suite's state:
    * getMaxRows() runs the stored value through Util.getQueryLocalRuntimeMaxrow, so with
    * query.runtime.maxrow or an organization row limit set, a table stored as unlimited reports
    * that cap instead of null. The read is deliberately the effective limit -- the Composer's own
    * dialog shows the same number -- so this is the documented behaviour, not a gap. Asserting the
    * capped case would mean mutating global state from a unit test; it is verifiable on a
    * configured server.
    */
   @Test
   void anUnlimitedRowLimitIsReportedAsNullRatherThanMinusOne() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "col");
      t.setMaxRows(-1);
      ws.addAssembly(t);

      assertNull(tableNamed(read(ws), "T").maxRows());
   }

   @Test
   void reportsThePixelOffsetSoAPositionWriteCanBeVerified() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "col");
      t.setPixelOffset(new Point(120, 340));
      ws.addAssembly(t);

      WorksheetModel.TableModel m = tableNamed(read(ws), "T");

      assertEquals(Integer.valueOf(120), m.x());
      assertEquals(Integer.valueOf(340), m.y());
   }

   /**
    * Mode has no field of its own -- set_table_mode writes liveData, runtime and editMode per mode,
    * so the read derives it from the same three. It reports the state the table is in, which is not
    * always the word that was written: see tableMode's own note.
    */
   @Test
   void derivesTheDisplayModeFromTheFlagsSetTableModeWrites() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "col");
      ws.addAssembly(t);

      t.setEditMode(true);
      assertEquals("edit", tableNamed(read(ws), "T").mode());

      t.setEditMode(false);
      t.setLiveData(true);
      t.setRuntime(true);
      assertEquals("live", tableNamed(read(ws), "T").mode());

      t.setRuntime(false);
      assertEquals("detail", tableNamed(read(ws), "T").mode());

      t.setLiveData(false);
      assertEquals("full", tableNamed(read(ws), "T").mode());
   }

   /** Both directions, since a one-sided assertion would pass against a hardcoded {@code false}. */
   @Test
   void reportsWhetherTheTableIsExposedToViewsheets() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "col");
      ws.addAssembly(t);

      t.setVisibleTable(true);
      assertTrue(tableNamed(read(ws), "T").visibleInViewsheet());

      t.setVisibleTable(false);
      assertFalse(tableNamed(read(ws), "T").visibleInViewsheet());
   }

   // ---------------------------------------------------------------------------
   // Named groups (Bug #76097 review follow-up)
   // ---------------------------------------------------------------------------

   private static DefaultNamedGroupAssembly namedGroup(
      Worksheet ws, String name, SourceInfo attachedSource, DataRef attachedAttribute,
      String groupName, WorksheetMutationSupport.GroupMapping mapping) throws Exception
   {
      NamedGroupInfo ngi = new NamedGroupInfo();
      ngi.setOthers(XConstants.LEAVE_OTHERS);
      DataRef conditionRef = attachedAttribute != null ? attachedAttribute
         : new BaseField("this");
      ngi.setGroupCondition(groupName,
         WorksheetMutationSupport.buildGroupConditionList(XSchema.STRING, conditionRef, mapping));

      DefaultNamedGroupAssembly assembly = new DefaultNamedGroupAssembly(ws, name);
      assembly.setNamedGroupInfo(ngi);

      if(attachedAttribute != null) {
         assembly.setAttachedType(AttachedAssembly.COLUMN_ATTACHED);
         assembly.setAttachedSource(attachedSource);
         assembly.setAttachedAttribute(attachedAttribute);
      }
      else {
         assembly.setAttachedType(AttachedAssembly.DATA_TYPE_ATTACHED);
         assembly.setAttachedDataType(XSchema.STRING);
      }

      ws.addAssembly(assembly);
      return assembly;
   }

   private static WorksheetModel.NamedGroupModel namedGroupNamed(WorksheetModel m, String name) {
      return m.namedGroups().stream().filter(g -> name.equals(g.name())).findFirst().orElseThrow();
   }

   @Test
   void readsWorksheetTableAttachedGroupAsTableColumnNotDatasourceScoped() throws Exception {
      Worksheet ws = new Worksheet();
      ColumnRef col = new ColumnRef(new AttributeRef(null, "State"));
      col.setDataType(XSchema.STRING);
      SourceInfo attachedSource = new SourceInfo(SourceInfo.ASSET, null, "Customer1");
      namedGroup(ws, "StateNGroup", attachedSource, col, "N",
         new WorksheetMutationSupport.GroupMapping("N", List.of("N"), "STARTING_WITH"));

      WorksheetModel.NamedGroupModel ng = namedGroupNamed(read(ws), "StateNGroup");
      assertEquals("Customer1", ng.table());
      assertEquals("State", ng.column());
      assertNull(ng.datasource());
      assertNull(ng.logicalModel());
      assertNull(ng.sourceTable());
      assertNull(ng.attribute());
      assertEquals("STARTING_WITH", ng.groupMappings().get(0).operation());
   }

   @Test
   void readsLogicalModelScopedGroupAsDatasourceNotTableColumn() throws Exception {
      Worksheet ws = new Worksheet();
      ColumnRef col = new ColumnRef(new AttributeRef("Customer", "State"));
      col.setDataType(XSchema.STRING);
      SourceInfo attachedSource = new SourceInfo(SourceInfo.MODEL, "Examples/Orders", "Order Model");
      namedGroup(ws, "State N Group", attachedSource, col, "N",
         new WorksheetMutationSupport.GroupMapping("N", List.of("NJ", "NY", "NV")));

      WorksheetModel.NamedGroupModel ng = namedGroupNamed(read(ws), "State N Group");
      assertNull(ng.table());
      assertNull(ng.column());
      assertEquals("Examples/Orders", ng.datasource());
      assertEquals("Order Model", ng.logicalModel());
      assertEquals("Customer", ng.sourceTable());
      assertEquals("State", ng.attribute());
      // No operation given at creation -- must round-trip as explicit equality, not be silently
      // omitted (which would look identical to "couldn't be determined").
      assertEquals("EQUAL_TO", ng.groupMappings().get(0).operation());
   }

   @Test
   void readsPhysicalTableScopedGroupAsDatasourceWithNoLogicalModel() throws Exception {
      Worksheet ws = new Worksheet();
      ColumnRef col = new ColumnRef(new AttributeRef(null, "STATE"));
      col.setDataType(XSchema.STRING);
      SourceInfo attachedSource = new SourceInfo(SourceInfo.PHYSICAL_TABLE, "MyDatasource", "SA.CUSTOMERS");
      namedGroup(ws, "PhysGroup", attachedSource, col, "N",
         new WorksheetMutationSupport.GroupMapping("N", List.of("N"), "STARTING_WITH"));

      WorksheetModel.NamedGroupModel ng = namedGroupNamed(read(ws), "PhysGroup");
      assertNull(ng.table());
      assertNull(ng.column());
      assertEquals("MyDatasource", ng.datasource());
      assertNull(ng.logicalModel());
      assertEquals("SA.CUSTOMERS", ng.sourceTable());
      assertEquals("STATE", ng.attribute());
   }

   @Test
   void readsNegatedEqualityOperationOnStandaloneGroup() throws Exception {
      Worksheet ws = new Worksheet();
      namedGroup(ws, "NotNYNJ", null, null, "NotNYNJ",
         new WorksheetMutationSupport.GroupMapping("NotNYNJ", List.of("NY", "NJ"), "!="));

      WorksheetModel.NamedGroupModel ng = namedGroupNamed(read(ws), "NotNYNJ");
      assertNull(ng.table());
      assertNull(ng.datasource());
      assertEquals("NOT_ONE_OF", ng.groupMappings().get(0).operation());
   }

   /**
    * PR #4765 review follow-up: {@code add_named_group}'s own vocabulary can never create a
    * negated {@code STARTING_WITH}/{@code CONTAINS}/{@code LIKE}/{@code BETWEEN}/comparison (there
    * is no {@code NOT_STARTING_WITH} etc.), but a human can, via the Composer's general condition
    * editor ({@code Condition.isNegatedChangeable()} is unconditionally {@code true}) -- and
    * {@code readNamedGroup} runs over every {@code DefaultNamedGroupAssembly} in the worksheet,
    * not just wizard-created ones. Reporting the positive string for such a condition would
    * silently flip its meaning if read back and fed into add_named_group/edit_named_group, so
    * {@code operation} must come back {@code null} ("can't be expressed") rather than
    * {@code "STARTING_WITH"}.
    */
   @Test
   void readsNullOperationForNegatedStartingWithBeyondThisVocabulary() {
      Worksheet ws = new Worksheet();
      ColumnRef col = new ColumnRef(new AttributeRef(null, "State"));
      col.setDataType(XSchema.STRING);

      Condition c = new Condition(XSchema.STRING);
      c.setOperation(XCondition.STARTING_WITH);
      c.setNegated(true);
      c.addValue("N");
      ConditionList conds = new ConditionList();
      conds.append(new ConditionItem(col, c, 0));

      NamedGroupInfo ngi = new NamedGroupInfo();
      ngi.setOthers(XConstants.LEAVE_OTHERS);
      ngi.setGroupCondition("NotN", conds);

      DefaultNamedGroupAssembly assembly = new DefaultNamedGroupAssembly(ws, "HumanMadeGroup");
      assembly.setNamedGroupInfo(ngi);
      assembly.setAttachedType(AttachedAssembly.COLUMN_ATTACHED);
      assembly.setAttachedSource(new SourceInfo(SourceInfo.ASSET, null, "Customer1"));
      assembly.setAttachedAttribute(col);
      ws.addAssembly(assembly);

      WorksheetModel.NamedGroupModel ng = namedGroupNamed(read(ws), "HumanMadeGroup");
      assertNull(ng.groupMappings().get(0).operation(),
         "a negated STARTING_WITH has no round-trippable operation string and must not be " +
            "reported as the plain positive one");
   }
}
