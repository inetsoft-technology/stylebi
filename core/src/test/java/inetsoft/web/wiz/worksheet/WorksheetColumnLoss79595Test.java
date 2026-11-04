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
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.QueryManager;
import inetsoft.util.CoreTool;
import inetsoft.util.UserMessage;
import inetsoft.web.wiz.pairing.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #79595 (re-opened): a column that vanished from a tabular source still silently degraded the
 * mirror/join/concat tables built on it. Covers the refresh-time snapshot/diff
 * ({@link WorksheetColumnLoss.StoredInputs}) and the preview-time check
 * ({@link WorksheetColumnLoss#missingUpstreamInputs}), plus the preview service wiring.
 */
@Tag("core")
@WizAgentTestSupport
class WorksheetColumnLoss79595Test {
   private static final class StubTabular extends TabularTableAssembly {
      StubTabular(Worksheet ws, String name) {
         super(ws, name);
      }

      @Override
      public void loadColumnSelection(VariableTable vtable, boolean addnew, QueryManager manager) {
      }
   }

   private static ColumnSelection cols(String... names) {
      ColumnSelection cols = new ColumnSelection();

      for(String name : names) {
         ColumnRef col = new ColumnRef(new AttributeRef(null, name));
         col.setDataType(XSchema.INTEGER);
         cols.addAttribute(col);
      }

      return cols;
   }

   private static void select(TableAssembly table, String... names) {
      table.setColumnSelection(cols(names), false);
      table.setColumnSelection(cols(names), true);
   }

   /** ev(id, v, n) tabular source with mirror ev_by_v = group by v, Sum(n). */
   private static Worksheet worksheet(boolean tabular) {
      Worksheet ws = new Worksheet();
      TableAssembly ev = tabular ? new StubTabular(ws, "ev") :
         new BoundTableAssembly(ws, "ev");
      select(ev, "id", "v", "n");
      ws.addAssembly(ev);
      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, "ev_by_v", ev);
      select(mirror, "id", "v", "n");
      TestWorksheets.withGroupSumAndSort(mirror, "v", "n");
      ws.addAssembly(mirror);
      return ws;
   }

   private static void makeCrosstab(Worksheet ws) {
      AggregateInfo info = ((TableAssembly) ws.getAssembly("ev_by_v")).getAggregateInfo();
      info.addGroup(new GroupRef(new ColumnRef(new AttributeRef(null, "id"))));
      info.setCrosstab(true);
      assertTrue(info.isCrosstab());
   }

   /** What a refresh does to the stored selections once the source lost column v. */
   private static void dropV(Worksheet ws) {
      select((TableAssembly) ws.getAssembly("ev"), "id", "n");
      select((TableAssembly) ws.getAssembly("ev_by_v"), "id", "n");
   }

   private static DefaultTableLens lens(String... headers) {
      Object[][] data = new Object[2][headers.length];
      data[0] = headers;

      for(int i = 0; i < headers.length; i++) {
         data[1][i] = i;
      }

      return new DefaultTableLens(data);
   }

   private static AssetQuerySandbox box(DefaultTableLens evLens) throws Exception {
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getTableLens(eq("ev"), anyInt())).thenReturn(evLens);
      return box;
   }

   // ---- refresh-time diff -------------------------------------------------------------------

   @Test
   void storedInputsReportTheGroupAggregateAndSortTheRefreshDropped() {
      Worksheet ws = worksheet(true);
      WorksheetColumnLoss.StoredInputs before = WorksheetColumnLoss.StoredInputs.take(ws);
      assertEquals(List.of(), before.lost(ws));

      dropV(ws);
      List<String> lost = before.lost(ws);

      assertTrue(lost.stream().anyMatch(m -> m.contains("ev_by_v") && m.contains("v")
                                             && m.contains("group")), "lost: " + lost);
      assertTrue(lost.stream().anyMatch(m -> m.contains("sort")), "lost: " + lost);
      assertFalse(lost.stream().anyMatch(m -> m.contains("column n")), "lost: " + lost);
   }

   @Test
   void storedInputsIgnoreCrosstabs() {
      Worksheet ws = worksheet(true);
      makeCrosstab(ws);
      WorksheetColumnLoss.StoredInputs before = WorksheetColumnLoss.StoredInputs.take(ws);
      dropV(ws);
      assertEquals(List.of(), before.lost(ws));
   }

   @Test
   void warnLostRaisesTheWarningOnTheCallingThread() {
      Worksheet ws = worksheet(true);
      WorksheetColumnLoss.StoredInputs before = WorksheetColumnLoss.StoredInputs.take(ws);
      dropV(ws);
      CoreTool.clearUserMessage();
      before.warnLost(ws);
      UserMessage message = CoreTool.getUserMessage();
      assertNotNull(message);
      assertTrue(message.getMessage().contains("ev_by_v"), message.getMessage());
   }

   // ---- preview-time check ------------------------------------------------------------------

   @Test
   void previewCheckWarnsWhenTheExecutedSourceLacksAStoredGroupColumn() throws Exception {
      Worksheet ws = worksheet(true);
      List<String> warnings = WorksheetColumnLoss.missingUpstreamInputs(
         ws, box(lens("id", "n")), "ev_by_v");

      assertTrue(warnings.stream().anyMatch(
         m -> m.contains("ev_by_v") && m.contains("v") && m.contains("group")),
         "warnings: " + warnings);
   }

   @Test
   void previewCheckIsSilentWhenTheSourceStillHasTheColumns() throws Exception {
      Worksheet ws = worksheet(true);
      assertEquals(List.of(), WorksheetColumnLoss.missingUpstreamInputs(
         ws, box(lens("id", "v", "n")), "ev_by_v"));
   }

   @Test
   void previewCheckSkipsChainsWithoutATabularSource() throws Exception {
      Worksheet ws = worksheet(false);
      assertEquals(List.of(), WorksheetColumnLoss.missingUpstreamInputs(
         ws, box(lens("id", "n")), "ev_by_v"));
   }

   @Test
   void previewCheckSkipsAHiddenSourceColumn() throws Exception {
      Worksheet ws = worksheet(true);
      TableAssembly ev = (TableAssembly) ws.getAssembly("ev");
      ColumnSelection cs = ev.getColumnSelection(false);
      ((ColumnRef) cs.getAttribute("v")).setVisible(false);
      ev.setColumnSelection(cs, false);

      assertEquals(List.of(), WorksheetColumnLoss.missingUpstreamInputs(
         ws, box(lens("id", "n")), "ev_by_v"));
   }

   @Test
   void previewCheckSkipsACrosstab() throws Exception {
      Worksheet ws = worksheet(true);
      makeCrosstab(ws);
      assertEquals(List.of(), WorksheetColumnLoss.missingUpstreamInputs(
         ws, box(lens("id", "n")), "ev_by_v"));
   }

   @Test
   void previewCheckSkipsWhenTheSourceResultCannotBeRead() throws Exception {
      Worksheet ws = worksheet(true);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getTableLens(eq("ev"), anyInt())).thenThrow(new IllegalStateException("boom"));
      assertEquals(List.of(), WorksheetColumnLoss.missingUpstreamInputs(ws, box, "ev_by_v"));
   }

   @Test
   void previewCheckAcceptsAQualifiedHeader() throws Exception {
      Worksheet ws = worksheet(true);
      assertEquals(List.of(), WorksheetColumnLoss.missingUpstreamInputs(
         ws, box(lens("ev.id", "ev.v", "ev.n")), "ev_by_v"));
   }

   // ---- preview service wiring --------------------------------------------------------------

   private static RuntimeWorksheet runtime(Worksheet ws, AssetQuerySandbox box) {
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      return rws;
   }

   /**
    * Both previews serve ev's result without raising any execution message (as a cache hit
    * would): the loss must be reported anyway, every time.
    */
   @Test
   void previewReportsTheLossOnEveryPreviewEvenWhenServedWithoutAnExecutionMessage()
      throws Exception
   {
      Worksheet ws = worksheet(true);
      AssetQuerySandbox box = box(lens("id", "n"));
      when(box.getTableLens(eq("ev_by_v"), anyInt())).thenReturn(lens("id", "v", "Sum(n)"));
      RuntimeWorksheet rws = runtime(ws, box);
      WorksheetPreviewService service = new WorksheetPreviewService();

      for(int i = 0; i < 2; i++) {
         CoreTool.clearUserMessage();
         List<String> warnings = service.preview(rws, "ev_by_v", 0, 50).warnings();
         assertEquals(1, warnings.stream().filter(
            m -> m.contains("ev_by_v") && m.contains("group")).count(),
            "preview " + i + " warnings: " + warnings);
      }
   }

   @Test
   void previewOfAHealthyTableHasNoWarnings() throws Exception {
      Worksheet ws = worksheet(true);
      AssetQuerySandbox box = box(lens("id", "v", "n"));
      when(box.getTableLens(eq("ev_by_v"), anyInt())).thenReturn(lens("v", "Sum(n)"));
      CoreTool.clearUserMessage();
      assertEquals(List.of(),
                   new WorksheetPreviewService().preview(runtime(ws, box), "ev_by_v", 0, 50)
                      .warnings());
   }
}
