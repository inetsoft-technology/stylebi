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

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.PhysicalBoundTableAssembly;
import inetsoft.uql.asset.WindowExpressionRef;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.web.wiz.model.WorksheetTable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression/contract test for {@link WorksheetTableService#applyWindowColumns}, the structured
 * counterpart of {@code applyExpressionColumns}: a {@code windowColumns} entry on the
 * {@code /ws/table} request must build a {@link ColumnRef} wrapping a {@link WindowExpressionRef}
 * whose synthesized {@code getExpression()} matches the pushdown-parity text produced by wiz's
 * {@code expandWindowColumns} helper.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WorksheetTableServiceWindowColumnsTest {
   private static final ObjectMapper MAPPER = new ObjectMapper();

   private static WorksheetTableService service() {
      // applyWindowColumns uses only its parameters, never instance state, so null
      // dependencies are safe here (mirrors WorksheetTableServiceConditionTest).
      return new WorksheetTableService(null, null, null, null, null, null, null, null, null);
   }

   private static WorksheetTable request(String json) throws Exception {
      return MAPPER.readValue(json, WorksheetTable.class);
   }

   @Test
   void rowNumberWindowColumnBuildsWindowExpressionRef() throws Exception {
      WorksheetTable req = request("""
         {
           "windowColumns": [
             {
               "name": "rn",
               "fn": "ROW_NUMBER",
               "partitionBy": ["stage"],
               "orderBy": [ { "field": "amount", "direction": "DESC" } ]
             }
           ]
         }
         """);

      Worksheet worksheet = new Worksheet();
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(worksheet, "deals");
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "stage")));
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "amount")));
      table.setColumnSelection(cs);

      service().applyWindowColumns(table, req.getWindowColumns());

      ColumnSelection result = table.getColumnSelection(false);
      DataRef added = result.getAttribute("rn");

      assertNotNull(added, "expected a 'rn' column in the private column selection");
      assertInstanceOf(ColumnRef.class, added);
      ColumnRef colRef = (ColumnRef) added;
      assertTrue(colRef.isSQL(), "window column must be marked SQL so PreAssetQuery inlines it");
      assertInstanceOf(WindowExpressionRef.class, colRef.getDataRef());

      WindowExpressionRef winRef = (WindowExpressionRef) colRef.getDataRef();
      assertEquals(
         "ROW_NUMBER() OVER (PARTITION BY field['stage'] ORDER BY field['amount'] DESC)",
         winRef.getExpression());
      assertTrue(winRef.isSQL());
   }

   @Test
   void framedWindowColumnBuildsRowsClause() throws Exception {
      WorksheetTable req = request("""
         { "windowColumns":[ {"name":"ma","fn":"AVG","column":"amount",
           "orderBy":[{"field":"amount","direction":"ASC"}],
           "frame":{"startBound":"PRECEDING","startOffset":2,"endBound":"CURRENT_ROW"}} ] }
         """);

      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, "deals");
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "amount")));
      table.setColumnSelection(cs);

      service().applyWindowColumns(table, req.getWindowColumns());

      ColumnRef added = (ColumnRef) table.getColumnSelection(false).getAttribute("ma");
      assertTrue(((WindowExpressionRef) added.getDataRef()).getExpression()
         .contains("ROWS BETWEEN 2 PRECEDING AND CURRENT ROW"));
   }

   @Test
   void frameOnRowNumberThrows() throws Exception {
      WorksheetTable req = request("""
         { "windowColumns":[ {"name":"rn","fn":"ROW_NUMBER",
           "orderBy":[{"field":"amount","direction":"ASC"}],
           "frame":{"startBound":"PRECEDING","startOffset":2,"endBound":"CURRENT_ROW"}} ] }
         """);

      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, "deals");
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "amount")));
      table.setColumnSelection(cs);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service().applyWindowColumns(table, req.getWindowColumns()));
      assertTrue(ex.getMessage().contains("rn"));
   }

   @Test
   void boundedFrameWithoutOrderByThrows() throws Exception {
      WorksheetTable req = request("""
         { "windowColumns":[ {"name":"ma","fn":"AVG","column":"amount",
           "frame":{"startBound":"PRECEDING","startOffset":2,"endBound":"CURRENT_ROW"}} ] }
         """);

      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, "deals");
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "amount")));
      table.setColumnSelection(cs);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service().applyWindowColumns(table, req.getWindowColumns()));
      assertTrue(ex.getMessage().contains("ma"));
   }

   @Test
   void strayOffsetOnFixedBoundThrows() throws Exception {
      // CURRENT_ROW is a fixed bound; it must not carry an offset. Silently ignoring a stray
      // offset (the pre-fix behavior) would mask a caller bug — mirrors the wiz TS validator.
      WorksheetTable req = request("""
         { "windowColumns":[ {"name":"ma","fn":"AVG","column":"amount",
           "orderBy":[{"field":"amount","direction":"ASC"}],
           "frame":{"startBound":"PRECEDING","startOffset":2,
                     "endBound":"CURRENT_ROW","endOffset":5}} ] }
         """);

      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, "deals");
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "amount")));
      table.setColumnSelection(cs);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service().applyWindowColumns(table, req.getWindowColumns()));
      assertTrue(ex.getMessage().contains("ma"));
      assertTrue(ex.getMessage().contains("CURRENT_ROW"));
   }

   @Test
   void unboundedFollowingAsStartBoundThrows() throws Exception {
      // Per ANSI ROWS grammar, frame start can never be UNBOUNDED_FOLLOWING, regardless of the
      // end bound — the {@code frameBoundRank} comparison alone misses this case because both
      // UNBOUNDED_PRECEDING and UNBOUNDED_FOLLOWING as start/end can independently rank as
      // +/-MAX.
      WorksheetTable req = request("""
         { "windowColumns":[ {"name":"ma","fn":"AVG","column":"amount",
           "orderBy":[{"field":"amount","direction":"ASC"}],
           "frame":{"startBound":"UNBOUNDED_FOLLOWING","endBound":"UNBOUNDED_FOLLOWING"}} ] }
         """);

      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, "deals");
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "amount")));
      table.setColumnSelection(cs);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service().applyWindowColumns(table, req.getWindowColumns()));
      assertTrue(ex.getMessage().contains("ma"));
      assertTrue(ex.getMessage().contains("UNBOUNDED_FOLLOWING"));
   }

   @Test
   void unboundedPrecedingAsEndBoundThrows() throws Exception {
      WorksheetTable req = request("""
         { "windowColumns":[ {"name":"ma","fn":"AVG","column":"amount",
           "orderBy":[{"field":"amount","direction":"ASC"}],
           "frame":{"startBound":"UNBOUNDED_PRECEDING","endBound":"UNBOUNDED_PRECEDING"}} ] }
         """);

      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, "deals");
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "amount")));
      table.setColumnSelection(cs);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service().applyWindowColumns(table, req.getWindowColumns()));
      assertTrue(ex.getMessage().contains("ma"));
      assertTrue(ex.getMessage().contains("UNBOUNDED_PRECEDING"));
   }

   // ─── Phase 4: RANGE / GROUPS frame mode + offsetUnit ──────────────────────

   @Test
   void rangeDateFrame_buildsRangeExpressionWithInterval() throws Exception {
      WorksheetTable req = request("""
         { "windowColumns":[ {"name":"s","fn":"SUM","column":"amount","partitionBy":["stage"],
           "orderBy":[{"field":"d","direction":"ASC"}],
           "frame":{"mode":"RANGE","startBound":"PRECEDING","startOffset":7,
                     "endBound":"CURRENT_ROW","offsetUnit":"day"}} ] }
         """);

      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, "deals");
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "amount")));
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "stage")));
      AttributeRef dRef = new AttributeRef(null, "d");
      dRef.setDataType(inetsoft.uql.schema.XSchema.TIME_INSTANT);
      cs.addAttribute(new ColumnRef(dRef));
      table.setColumnSelection(cs);

      service().applyWindowColumns(table, req.getWindowColumns());

      ColumnRef added = (ColumnRef) table.getColumnSelection(false).getAttribute("s");
      String expr = ((WindowExpressionRef) added.getDataRef()).getExpression();
      // Phase 5 Task 3: getExpression() now emits the canonical __WIZ_WINFRAME_INTERVAL token, not
      // the pre-rendered Postgres literal -- the dialect-rewrite seam
      // (PreAssetQuery.getExpressionColumn) expands it to "INTERVAL '7 day'" for Postgres at
      // render time. Finding B (PR #4237 review): __WIZ_ prefix namespaces the token.
      assertTrue(
         expr.contains("RANGE BETWEEN __WIZ_WINFRAME_INTERVAL(7,day) PRECEDING AND CURRENT ROW"),
         "unexpected expression: " + expr);
   }

   @Test
   void groupsFrame_buildsGroupsExpression() throws Exception {
      WorksheetTable req = request("""
         { "windowColumns":[ {"name":"s","fn":"SUM","column":"amount","partitionBy":["stage"],
           "orderBy":[{"field":"d","direction":"ASC"}],
           "frame":{"mode":"GROUPS","startBound":"PRECEDING","startOffset":2,
                     "endBound":"CURRENT_ROW"}} ] }
         """);

      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, "deals");
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "amount")));
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "stage")));
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "d")));
      table.setColumnSelection(cs);

      service().applyWindowColumns(table, req.getWindowColumns());

      ColumnRef added = (ColumnRef) table.getColumnSelection(false).getAttribute("s");
      String expr = ((WindowExpressionRef) added.getDataRef()).getExpression();
      assertTrue(expr.contains("GROUPS BETWEEN 2 PRECEDING AND CURRENT ROW"),
                 "unexpected expression: " + expr);
   }

   @Test
   void rangeValueOffset_requiresSingleOrderKey() throws Exception {
      WorksheetTable req = request("""
         { "windowColumns":[ {"name":"s","fn":"SUM","column":"amount","partitionBy":["stage"],
           "orderBy":[{"field":"d","direction":"ASC"},{"field":"amount","direction":"ASC"}],
           "frame":{"mode":"RANGE","startBound":"PRECEDING","startOffset":7,
                     "endBound":"CURRENT_ROW","offsetUnit":"day"}} ] }
         """);

      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, "deals");
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "amount")));
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "stage")));
      AttributeRef dRef = new AttributeRef(null, "d");
      dRef.setDataType(inetsoft.uql.schema.XSchema.TIME_INSTANT);
      cs.addAttribute(new ColumnRef(dRef));
      table.setColumnSelection(cs);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service().applyWindowColumns(table, req.getWindowColumns()));
      assertTrue(ex.getMessage().contains("s"));
   }

   @Test
   void offsetUnit_onRows_throws() throws Exception {
      WorksheetTable req = request("""
         { "windowColumns":[ {"name":"s","fn":"SUM","column":"amount","partitionBy":["stage"],
           "orderBy":[{"field":"d","direction":"ASC"}],
           "frame":{"mode":"ROWS","startBound":"PRECEDING","startOffset":7,
                     "endBound":"CURRENT_ROW","offsetUnit":"day"}} ] }
         """);

      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, "deals");
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "amount")));
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "stage")));
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "d")));
      table.setColumnSelection(cs);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service().applyWindowColumns(table, req.getWindowColumns()));
      assertTrue(ex.getMessage().contains("s"));
   }

   @Test
   void groupsOrRangeValue_withoutOrderBy_throws() throws Exception {
      WorksheetTable req = request("""
         { "windowColumns":[ {"name":"s","fn":"SUM","column":"amount","partitionBy":["stage"],
           "frame":{"mode":"GROUPS","startBound":"PRECEDING","startOffset":2,
                     "endBound":"CURRENT_ROW"}} ] }
         """);

      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, "deals");
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "amount")));
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "stage")));
      table.setColumnSelection(cs);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service().applyWindowColumns(table, req.getWindowColumns()));
      assertTrue(ex.getMessage().contains("s"));
   }

   @Test
   void invalidFrameMode_throws() throws Exception {
      WorksheetTable req = request("""
         { "windowColumns":[ {"name":"s","fn":"SUM","column":"amount",
           "orderBy":[{"field":"amount","direction":"ASC"}],
           "frame":{"mode":"BOGUS","startBound":"PRECEDING","startOffset":2,
                     "endBound":"CURRENT_ROW"}} ] }
         """);

      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, "deals");
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "amount")));
      table.setColumnSelection(cs);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service().applyWindowColumns(table, req.getWindowColumns()));
      assertTrue(ex.getMessage().contains("s"));
   }

   @Test
   void offsetUnit_onNonDateOrderKey_throws() throws Exception {
      WorksheetTable req = request("""
         { "windowColumns":[ {"name":"s","fn":"SUM","column":"amount","partitionBy":["stage"],
           "orderBy":[{"field":"amount","direction":"ASC"}],
           "frame":{"mode":"RANGE","startBound":"PRECEDING","startOffset":7,
                     "endBound":"CURRENT_ROW","offsetUnit":"day"}} ] }
         """);

      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, "deals");
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "amount")));
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "stage")));
      table.setColumnSelection(cs);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service().applyWindowColumns(table, req.getWindowColumns()));
      assertTrue(ex.getMessage().contains("s"));
   }

   @Test
   void invalidOffsetUnit_throws() throws Exception {
      WorksheetTable req = request("""
         { "windowColumns":[ {"name":"s","fn":"SUM","column":"amount","partitionBy":["stage"],
           "orderBy":[{"field":"d","direction":"ASC"}],
           "frame":{"mode":"RANGE","startBound":"PRECEDING","startOffset":7,
                     "endBound":"CURRENT_ROW","offsetUnit":"fortnight"}} ] }
         """);

      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, "deals");
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "amount")));
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "stage")));
      AttributeRef dRef = new AttributeRef(null, "d");
      dRef.setDataType(inetsoft.uql.schema.XSchema.TIME_INSTANT);
      cs.addAttribute(new ColumnRef(dRef));
      table.setColumnSelection(cs);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service().applyWindowColumns(table, req.getWindowColumns()));
      assertTrue(ex.getMessage().contains("s"));
   }

   @Test
   void rowsFrame_byteParityWithOmittedMode() throws Exception {
      // A ROWS/omitted-mode frame must produce identical output to Phase 3 (no regression).
      WorksheetTable req = request("""
         { "windowColumns":[ {"name":"ma","fn":"AVG","column":"amount",
           "orderBy":[{"field":"amount","direction":"ASC"}],
           "frame":{"startBound":"PRECEDING","startOffset":2,"endBound":"CURRENT_ROW"}} ] }
         """);

      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, "deals");
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "amount")));
      table.setColumnSelection(cs);

      service().applyWindowColumns(table, req.getWindowColumns());

      ColumnRef added = (ColumnRef) table.getColumnSelection(false).getAttribute("ma");
      String expr = ((WindowExpressionRef) added.getDataRef()).getExpression();
      assertTrue(expr.contains("ROWS BETWEEN 2 PRECEDING AND CURRENT ROW"));
      assertFalse(expr.contains("RANGE"));
      assertFalse(expr.contains("GROUPS"));
   }

   // ─── Task 2 review fixes ───────────────────────────────────────────────────

   @Test
   void rangeWholePartition_offsetUnit_noOrderBy_throwsIllegalArgument() throws Exception {
      // Whole-partition RANGE frame is exempt from the Phase-3 "bounded frame requires orderBy"
      // guard. Before the fix, offsetUnit validation unconditionally called
      // requireDateOrderKey(colName, orderRefs) -> orderRefs.get(0) on an EMPTY list, throwing
      // IndexOutOfBoundsException instead of a field-named IllegalArgumentException.
      WorksheetTable req = request("""
         { "windowColumns":[ {"name":"s","fn":"SUM","column":"amount","partitionBy":["stage"],
           "frame":{"mode":"RANGE","startBound":"UNBOUNDED_PRECEDING",
                     "endBound":"UNBOUNDED_FOLLOWING","offsetUnit":"day"}} ] }
         """);

      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, "deals");
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "amount")));
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "stage")));
      table.setColumnSelection(cs);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service().applyWindowColumns(table, req.getWindowColumns()));
      assertTrue(ex.getMessage().contains("s"));
   }

   @Test
   void offsetUnit_withoutValueOffsetBound_throws() throws Exception {
      // CURRENT_ROW .. CURRENT_ROW carries no PRECEDING/FOLLOWING value-offset bound, so
      // offsetUnit is meaningless even though orderBy is present and date-typed.
      WorksheetTable req = request("""
         { "windowColumns":[ {"name":"s","fn":"SUM","column":"amount","partitionBy":["stage"],
           "orderBy":[{"field":"d","direction":"ASC"}],
           "frame":{"mode":"RANGE","startBound":"CURRENT_ROW",
                     "endBound":"CURRENT_ROW","offsetUnit":"day"}} ] }
         """);

      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, "deals");
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "amount")));
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "stage")));
      AttributeRef dRef = new AttributeRef(null, "d");
      dRef.setDataType(inetsoft.uql.schema.XSchema.TIME_INSTANT);
      cs.addAttribute(new ColumnRef(dRef));
      table.setColumnSelection(cs);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service().applyWindowColumns(table, req.getWindowColumns()));
      assertTrue(ex.getMessage().contains("s"));
   }

   @Test
   void groupsWholePartition_noOrderBy_throws() throws Exception {
      // Review fix (Fix 1): GROUPS mode ALWAYS requires an ORDER BY, even for a whole-partition
      // frame (UNBOUNDED_PRECEDING..UNBOUNDED_FOLLOWING). Unlike ROWS/RANGE, GROUPS defines its
      // peer groups purely from the order-by key — with no orderBy there is no notion of a
      // "group" at all, so a whole-partition exemption (as used for the general bounded-frame
      // check) is invalid SQL on Postgres/ANSI and diverges from the wiz TS validator, which
      // rejects ANY GROUPS frame without orderBy. This test used to assert the OPPOSITE
      // ("...succeeds") — that encoded the bug (a `!isWholePartitionFrame(frame)` exemption on
      // the GROUPS branch); it is inverted here to lock in the corrected, always-required
      // behavior.
      WorksheetTable req = request("""
         { "windowColumns":[ {"name":"s","fn":"SUM","column":"amount","partitionBy":["stage"],
           "frame":{"mode":"GROUPS","startBound":"UNBOUNDED_PRECEDING",
                     "endBound":"UNBOUNDED_FOLLOWING"}} ] }
         """);

      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, "deals");
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "amount")));
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "stage")));
      table.setColumnSelection(cs);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service().applyWindowColumns(table, req.getWindowColumns()));
      assertTrue(ex.getMessage().contains("s"));
      assertTrue(ex.getMessage().contains("GROUPS"), "unexpected message: " + ex.getMessage());
   }

   @Test
   void offsetUnit_caseInsensitive_accepted() throws Exception {
      WorksheetTable req = request("""
         { "windowColumns":[ {"name":"s","fn":"SUM","column":"amount","partitionBy":["stage"],
           "orderBy":[{"field":"d","direction":"ASC"}],
           "frame":{"mode":"RANGE","startBound":"PRECEDING","startOffset":7,
                     "endBound":"CURRENT_ROW","offsetUnit":"Day"}} ] }
         """);

      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, "deals");
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "amount")));
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "stage")));
      AttributeRef dRef = new AttributeRef(null, "d");
      dRef.setDataType(inetsoft.uql.schema.XSchema.TIME_INSTANT);
      cs.addAttribute(new ColumnRef(dRef));
      table.setColumnSelection(cs);

      service().applyWindowColumns(table, req.getWindowColumns());

      ColumnRef added = (ColumnRef) table.getColumnSelection(false).getAttribute("s");
      String expr = ((WindowExpressionRef) added.getDataRef()).getExpression();
      // Case-normalization happens before frame storage; the emitted token is unaffected either way.
      assertTrue(expr.contains("__WIZ_WINFRAME_INTERVAL(7,day)"), "unexpected expression: " + expr);
   }

   // ─── PR #4235 review Fix C: numeric RANGE offset on a date/time order key ────────────────

   @Test
   void rangeValueOffset_onDateOrderKey_missingOffsetUnit_throws() throws Exception {
      // Converse of requireDateOrderKey: a RANGE value-offset (PRECEDING/FOLLOWING n) whose
      // single ORDER BY key ("d") is date/time-typed but carries NO offsetUnit is meaningless —
      // a bare numeric offset against a date column needs an INTERVAL, not a raw number. Before
      // the fix this silently passed validation; in memory it would treat the offset as raw
      // milliseconds (WindowTableLens.dateOffsetMs's unit==null branch) and a pushdown would
      // emit an invalid date RANGE.
      WorksheetTable req = request("""
         { "windowColumns":[ {"name":"s","fn":"SUM","column":"amount","partitionBy":["stage"],
           "orderBy":[{"field":"d","direction":"ASC"}],
           "frame":{"mode":"RANGE","startBound":"PRECEDING","startOffset":7,
                     "endBound":"CURRENT_ROW"}} ] }
         """);

      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, "deals");
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "amount")));
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "stage")));
      AttributeRef dRef = new AttributeRef(null, "d");
      dRef.setDataType(inetsoft.uql.schema.XSchema.TIME_INSTANT);
      cs.addAttribute(new ColumnRef(dRef));
      table.setColumnSelection(cs);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service().applyWindowColumns(table, req.getWindowColumns()));
      assertTrue(ex.getMessage().contains("s"));
      assertTrue(ex.getMessage().contains("offsetUnit"), "unexpected message: " + ex.getMessage());
   }

   @Test
   void rangeValueOffset_onNumericOrderKey_missingOffsetUnit_stillSucceeds() throws Exception {
      // Byte-parity / no-regression check: a RANGE value-offset on a NON-date (numeric) order
      // key with no offsetUnit is the normal, long-supported case (a plain numeric threshold)
      // and must still succeed — the new Fix 2 guard only rejects a non-numeric, non-date order
      // key. NOTE: the order key must be explicitly typed numeric here — AttributeRef.getDataType()
      // defaults an untyped ref to XSchema.STRING, which would otherwise (correctly) trip the
      // new guard and turn this byte-parity case into a false failure.
      WorksheetTable req = request("""
         { "windowColumns":[ {"name":"s","fn":"SUM","column":"amount","partitionBy":["stage"],
           "orderBy":[{"field":"amount","direction":"ASC"}],
           "frame":{"mode":"RANGE","startBound":"PRECEDING","startOffset":5,
                     "endBound":"CURRENT_ROW"}} ] }
         """);

      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, "deals");
      ColumnSelection cs = new ColumnSelection();
      AttributeRef amountRef = new AttributeRef(null, "amount");
      amountRef.setDataType(inetsoft.uql.schema.XSchema.DOUBLE);
      cs.addAttribute(new ColumnRef(amountRef));
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "stage")));
      table.setColumnSelection(cs);

      service().applyWindowColumns(table, req.getWindowColumns());

      ColumnRef added = (ColumnRef) table.getColumnSelection(false).getAttribute("s");
      String expr = ((WindowExpressionRef) added.getDataRef()).getExpression();
      assertTrue(expr.contains("RANGE BETWEEN 5 PRECEDING AND CURRENT ROW"),
                 "unexpected expression: " + expr);
   }

   @Test
   void numericRangeValueOffset_stringOrderKey_throws() throws Exception {
      // Review fix (Fix 2): the converse of rangeValueOffset_onDateOrderKey_missingOffsetUnit_throws
      // above. A RANGE value-offset (PRECEDING/FOLLOWING n) with no offsetUnit is a bare numeric
      // threshold; ordering by a non-numeric, non-date key (e.g. a plain string column) makes
      // that threshold meaningless and must fail loud, matching the wiz TS validator, instead of
      // silently emitting a nonsensical "RANGE BETWEEN n PRECEDING" against a string column.
      WorksheetTable req = request("""
         { "windowColumns":[ {"name":"s","fn":"SUM","column":"amount","partitionBy":["stage"],
           "orderBy":[{"field":"stage","direction":"ASC"}],
           "frame":{"mode":"RANGE","startBound":"PRECEDING","startOffset":5,
                     "endBound":"CURRENT_ROW"}} ] }
         """);

      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, "deals");
      ColumnSelection cs = new ColumnSelection();
      AttributeRef amountRef = new AttributeRef(null, "amount");
      amountRef.setDataType(inetsoft.uql.schema.XSchema.DOUBLE);
      cs.addAttribute(new ColumnRef(amountRef));
      AttributeRef stageRef = new AttributeRef(null, "stage");
      stageRef.setDataType(inetsoft.uql.schema.XSchema.STRING);
      cs.addAttribute(new ColumnRef(stageRef));
      table.setColumnSelection(cs);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service().applyWindowColumns(table, req.getWindowColumns()));
      assertTrue(ex.getMessage().contains("s"));
      assertTrue(ex.getMessage().contains("numeric order key"),
                 "unexpected message: " + ex.getMessage());
   }

   @Test
   void windowColumnDescriptionIsPersistedOnOutputColumn() throws Exception {
      WorksheetTable req = request("""
         {
           "windowColumns": [
             {
               "name": "running_total",
               "fn": "SUM",
               "column": "amount",
               "partitionBy": ["stage"],
               "orderBy": [ { "field": "amount", "direction": "ASC" } ],
               "description": "Cumulative amount within each stage"
             }
           ]
         }
         """);

      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, "deals");
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "stage")));
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "amount")));
      table.setColumnSelection(cs);

      service().applyWindowColumns(table, req.getWindowColumns());

      // Private selection carries the description directly...
      ColumnRef added = (ColumnRef) table.getColumnSelection(false).getAttribute("running_total");
      assertNotNull(added);
      assertEquals("Cumulative amount within each stage", added.getDescription());

      // ...and it survives to the PUBLIC selection (the clone that /ws/structure reads back).
      ColumnRef output = (ColumnRef) table.getColumnSelection(true).getAttribute("running_total");
      assertNotNull(output, "window column should appear in the public output selection");
      assertEquals("Cumulative amount within each stage", output.getDescription());
   }
}
