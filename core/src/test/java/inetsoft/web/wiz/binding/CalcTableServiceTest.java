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
package inetsoft.web.wiz.binding;

import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.binding.event.GetPredefinedNamedGroupEvent;
import inetsoft.web.binding.event.GetCellScriptEvent;
import inetsoft.web.binding.command.GetPredefinedNamedGroupCommand;
import inetsoft.web.binding.command.GetCellScriptCommand;
import inetsoft.report.internal.binding.AssetNamedGroupInfo;
import inetsoft.report.CellBinding;
import inetsoft.report.GroupableCellBinding;
import inetsoft.report.TableCellBinding;
import inetsoft.report.TableLayout;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.Condition;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.AggregateRef;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.AttachedAssembly;
import inetsoft.uql.asset.DefaultNamedGroupAssembly;
import inetsoft.uql.asset.NamedGroupInfo;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.asset.AbstractTableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.util.XNamedGroupInfo;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo;
import inetsoft.web.binding.controller.VSTableLayoutService;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.handler.TableLayoutHandler;
import inetsoft.web.binding.handler.VSColumnHandler;
import inetsoft.web.binding.event.CopyCutCalcCellEvent;
import inetsoft.web.binding.event.ModifyTableLayoutEvent;
import inetsoft.web.binding.event.SetCellBindingEvent;
import inetsoft.web.binding.model.table.CellBindingInfo;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.wiz.binding.model.BindableField;
import inetsoft.web.wiz.binding.model.BindableTable;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class CalcTableServiceTest {
   private static Map<String, Object> spec(Object... pairs) {
      Map<String, Object> spec = new LinkedHashMap<>();

      for(int i = 0; i < pairs.length; i += 2) {
         spec.put((String) pairs[i], pairs[i + 1]);
      }

      return spec;
   }

   private static Map<String, Object> columnCell(String column) {
      return spec("content", "column", "grouping", "group", "expand", "vertical",
                  "field", Map.of("column", column, "type", "dimension"));
   }

   @Test
   void readsTheGridDimensionsAndEveryCell() throws Exception {
      Harness h = harness(2, 3);

      Map<String, Object> layout = h.service.readLayout("tok", principal(), "Calc1");

      assertEquals(2, layout.get("rowCount"));
      assertEquals(3, layout.get("colCount"));
      @SuppressWarnings("unchecked")
      List<Object> cells = (List<Object>) layout.get("cells");
      assertEquals(6, cells.size());
      verify(h.sessions, never()).mutate(anyString(), any(Principal.class), any());
   }

   @Test
   void readsACellInTheTokenVocabulary() throws Exception {
      Harness h = harness(2, 2);
      CellBindingInfo info = new CellBindingInfo();
      info.setType(CellBinding.BIND_COLUMN);
      info.setBtype(CellBinding.GROUP);
      info.setExpansion(GroupableCellBinding.EXPAND_V);
      info.setRuntimeName("SomeName");
      when(h.layoutService.getCellBindingInfo(any(), eq(1), eq(0))).thenReturn(info);

      Map<String, Object> read = h.service.readCell("tok", principal(), "Calc1", 1, 0);

      @SuppressWarnings("unchecked")
      Map<String, Object> binding = (Map<String, Object>) read.get("binding");
      assertEquals("column", binding.get("content"));
      assertEquals("group", binding.get("grouping"));
      assertEquals("vertical", binding.get("expand"));
      assertEquals("SomeName", binding.get("runtimeName"));
   }

   /**
    * Regression test for the cross-cell-formula gap: {@code CellBindingInfo} already round-trips
    * a cell's explicit {@code name} ({@code setName}/{@code getName}) into
    * {@code TableCellBinding.cellName}, which {@code CalcTableScope.initRuntimeReferences}
    * registers at runtime as {@code "$" + name} so another formula cell can reference this one's
    * computed value. Before this fix, {@code CalcTableService.toCellBindingInfo} never called
    * {@code setName}, so there was no way to name a cell through this tool at all.
    */
   @Test
   void readsCellNameBackAfterDescribing() {
      CellBindingInfo info = new CellBindingInfo();
      info.setType(CellBinding.BIND_COLUMN);
      info.setName("OrderTotal");

      Map<String, Object> described = CalcCellVocabulary.describe(info);

      assertEquals("OrderTotal", described.get("name"));
   }

   /** @see #readsCellNameBackAfterDescribing() -- the write side of the same gap. */
   @Test
   void namesACellSoAnotherFormulaCellCanReferenceItAsADollarName() throws Exception {
      Harness h = harness(3, 3);

      h.service.setCellBinding("tok", principal(), "Calc1", 0, 1,
                               spec("content", "column", "grouping", "summary", "expand", "none",
                                    "formula", "Sum", "name", "OrderTotal",
                                    "field", Map.of("column", "PAID", "type", "measure")));

      ArgumentCaptor<SetCellBindingEvent> captor =
         ArgumentCaptor.forClass(SetCellBindingEvent.class);
      verify(h.layoutService).setCellBinding(eq("rt1"), captor.capture(), any(Principal.class),
                                             any());
      assertEquals("OrderTotal", captor.getValue().getBinding().getName());
   }

   @Test
   void setsACellBindingThroughTheCellAddressedEndpoint() throws Exception {
      Harness h = harness(3, 3);

      h.service.setCellBinding("tok", principal(), "Calc1", 2, 1, columnCell("Region"));

      ArgumentCaptor<SetCellBindingEvent> captor =
         ArgumentCaptor.forClass(SetCellBindingEvent.class);
      verify(h.layoutService).setCellBinding(eq("rt1"), captor.capture(), any(Principal.class),
                                             any());
      SetCellBindingEvent event = captor.getValue();
      assertEquals("Calc1", event.getName());
      assertEquals(2, event.getSelectCells()[0].getRow());
      assertEquals(1, event.getSelectCells()[0].getCol());
      assertEquals(CellBinding.BIND_COLUMN, event.getBinding().getType());
      assertEquals("Region", event.getBinding().getValue());
   }

   /**
    * rowGroup/colGroup aren't part of this seam's cell vocabulary, so a spec that omits them
    * must not leave the binding's group ancestry at null (StyleBI's "no ancestor, grand
    * total" sentinel). It must default to TableCellBinding.DEFAULT_GROUP instead, the same as
    * a freshly drag-and-dropped cell (TableLayoutHandler.createDefalutCellBinding), so
    * SUMMARY/GROUP/DETAIL cells built through this tool inherit their nearest enclosing
    * expand ancestor rather than silently becoming table-wide grand totals.
    */
   @Test
   void defaultsRowAndColGroupToTheDefaultGroupSentinelWhenOmitted() throws Exception {
      Harness h = harness(3, 3);

      h.service.setCellBinding("tok", principal(), "Calc1", 2, 1, columnCell("Region"));

      ArgumentCaptor<SetCellBindingEvent> captor =
         ArgumentCaptor.forClass(SetCellBindingEvent.class);
      verify(h.layoutService).setCellBinding(eq("rt1"), captor.capture(), any(Principal.class),
                                             any());
      SetCellBindingEvent event = captor.getValue();
      assertEquals(TableCellBinding.DEFAULT_GROUP, event.getBinding().getRowGroup());
      assertEquals(TableCellBinding.DEFAULT_GROUP, event.getBinding().getColGroup());
      assertEquals(TableCellBinding.DEFAULT_GROUP, event.getBinding().getMergeRowGroup());
      assertEquals(TableCellBinding.DEFAULT_GROUP, event.getBinding().getMergeColGroup());
   }

   /**
    * A <b>summary</b> cell is an aggregate, and an aggregate needs a formula — but a cell that
    * binds a field must use {@code content: "column"}, and that branch never set one. StyleBI then
    * threw {@code NullPointerException: Cannot read field "formula" because "finfo" is null} from
    * {@code TableLayoutHandler.createAggregateField}, which made a summary column cell impossible
    * to create through this tool at all.
    */
   @Test
   void carriesTheFormulaOnASummaryColumnCellBecauseAnAggregateNeedsOne() throws Exception {
      Harness h = harness(3, 3);

      h.service.setCellBinding("tok", principal(), "Calc1", 1, 1,
                               spec("content", "column", "grouping", "summary",
                                    "expand", "none", "formula", "Sum",
                                    "field", Map.of("column", "PAID", "type", "measure")));

      ArgumentCaptor<SetCellBindingEvent> captor =
         ArgumentCaptor.forClass(SetCellBindingEvent.class);
      verify(h.layoutService).setCellBinding(eq("rt1"), captor.capture(), any(Principal.class),
                                             any());
      assertEquals("Sum", captor.getValue().getBinding().getFormula());
      assertEquals("PAID", captor.getValue().getBinding().getValue());
   }

   /**
    * Without a formula the aggregate NPEs deep in StyleBI. Refusing here names the missing key
    * instead of surfacing a 500 the caller cannot act on.
    */
   @Test
   void refusesASummaryCellWithNoFormula() {
      Harness h = harness(3, 3);

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.setCellBinding("tok", principal(), "Calc1", 1, 1,
                                        spec("content", "column", "grouping", "summary",
                                             "expand", "none",
                                             "field", Map.of("column", "PAID",
                                                             "type", "measure"))));
      assertTrue(thrown.getMessage().contains("formula"));
   }

   /**
    * Regression test for the secondary finding: a {@code field.column} that is a real column --
    * just on a different table than the calc table's current {@code set_table_source} target --
    * used to be accepted without error and rendered a silently blank cell
    * (CLAUDE.md's tool-misuse-accepted-silently class). {@code BindableColumns.require} is the
    * same check {@code BindingAgentController.resolveSourceTable} already applies to
    * chart/table/crosstab writes; this is the first time a calc-table cell write applies it too.
    */
   @Test
   void refusesAColumnNotOnTheAssemblysCurrentSource() throws Exception {
      Harness h = harness(3, 3);
      when(h.fieldsService().list(anyString(), anyString(), any(Principal.class))).thenReturn(
         List.of(new BindableTable("OrderAmountTotal", true,
                                   List.of(new BindableField("TOTAL_ORDER_AMOUNT", "double",
                                                             "measure")))));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.setCellBinding(
            "tok", principal(), "Calc1", 1, 1,
            spec("content", "column", "grouping", "summary", "expand", "none", "formula", "Sum",
                 "field", Map.of("column", "TOTAL_RETURN_AMOUNT", "type", "measure"))));

      assertTrue(thrown.getMessage().contains("TOTAL_RETURN_AMOUNT"));
      assertTrue(thrown.getMessage().contains("OrderAmountTotal"));
      verify(h.layoutService, never()).setCellBinding(anyString(), any(), any(Principal.class),
                                                       any());
   }

   @Test
   void eachBindIsExactlyOneCheckpoint() throws Exception {
      Harness h = harness(3, 3);

      h.service.setCellBinding("tok", principal(), "Calc1", 0, 0, columnCell("Region"));

      verify(h.sessions, times(1)).mutate(anyString(), any(Principal.class), any());
   }

   /**
    * A coordinate outside the grid must be refused with the grid's real dimensions, since the
    * usual cause is a coordinate read before a layout change.
    */
   @Test
   void refusesACoordinateOutsideTheGridReportingItsSize() {
      Harness h = harness(2, 2);

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.setCellBinding("tok", principal(), "Calc1", 5, 0,
                                        columnCell("Region")));

      assertTrue(thrown.getMessage().contains("2 row"));
      assertTrue(thrown.getMessage().contains("stale"));
   }

   @Test
   void validatesTheBindingBeforeTouchingTheRuntime() {
      Harness h = harness(3, 3);

      assertThrows(Exception.class,
                   () -> h.service.setCellBinding("tok", principal(), "Calc1", 0, 0,
                                                  spec("type", "column")));

      verifyNoInteractions(h.sessions);
   }

   @Test
   void refusesAColumnCellWhoseFieldHasNoType() {
      Harness h = harness(3, 3);

      assertThrows(Exception.class,
                   () -> h.service.setCellBinding(
                      "tok", principal(), "Calc1", 0, 0,
                      spec("content", "column", "field", Map.of("column", "Region"))));
   }

   @Test
   void bindsATextCellFromItsLiteralValue() throws Exception {
      Harness h = harness(2, 2);

      h.service.setCellBinding("tok", principal(), "Calc1", 0, 0,
                               spec("content", "text", "value", "Total"));

      ArgumentCaptor<SetCellBindingEvent> captor =
         ArgumentCaptor.forClass(SetCellBindingEvent.class);
      verify(h.layoutService).setCellBinding(eq("rt1"), captor.capture(), any(Principal.class),
                                             any());
      assertEquals(CellBinding.BIND_TEXT, captor.getValue().getBinding().getType());
      assertEquals("Total", captor.getValue().getBinding().getValue());
   }

   /**
    * The regression test for the confirmed silent-drop defect: a formula cell's script is
    * CellBinding.value, the field LayoutTool/get_calc_cell_script actually reads -- not
    * .formula, which is exclusively the BIND_COLUMN summary-cell aggregate name (Sum/Count/...).
    * Before the fix this stored the script in .formula and left .value null, so
    * get_calc_cell_script silently read back no script at all.
    */
   @Test
   void bindsAFormulaCellStoringTheScriptInValueNotFormula() throws Exception {
      Harness h = harness(2, 2);

      h.service.setCellBinding("tok", principal(), "Calc1", 0, 0,
                               spec("content", "formula", "formula", "Sum(Sales)"));

      ArgumentCaptor<SetCellBindingEvent> captor =
         ArgumentCaptor.forClass(SetCellBindingEvent.class);
      verify(h.layoutService).setCellBinding(eq("rt1"), captor.capture(), any(Principal.class),
                                             any());
      assertEquals(CellBinding.BIND_FORMULA, captor.getValue().getBinding().getType());
      assertEquals("Sum(Sales)", captor.getValue().getBinding().getValue());
      assertNull(captor.getValue().getBinding().getFormula());
   }

   /**
    * get_cell_binding echoes a formula cell's script back under 'value', so a caller that
    * feeds a read straight back into set_cell_binding supplies 'value' with no 'formula' key.
    * That must still bind, and it must still land in .value.
    */
   @Test
   void acceptsValueAsAnAliasForFormulaContent() throws Exception {
      Harness h = harness(2, 2);

      h.service.setCellBinding("tok", principal(), "Calc1", 0, 0,
                               spec("content", "formula", "value", "Sum(Sales)"));

      ArgumentCaptor<SetCellBindingEvent> captor =
         ArgumentCaptor.forClass(SetCellBindingEvent.class);
      verify(h.layoutService).setCellBinding(eq("rt1"), captor.capture(), any(Principal.class),
                                             any());
      assertEquals("Sum(Sales)", captor.getValue().getBinding().getValue());
   }

   @Test
   void refusesANonCalcAssemblyPointingAtTheShelfTools() {
      Harness h = harnessFor(mock(CrosstabVSAssembly.class), 0, 0);

      Exception thrown = assertThrows(
         Exception.class, () -> h.service.readLayout("tok", principal(), "Crosstab1"));

      assertTrue(thrown.getMessage().contains("Crosstab1"));
      assertTrue(thrown.getMessage().contains("get_table_binding"));
   }

   @Test
   void vocabularyListsTheTokensRatherThanConstants() {
      Harness h = harness(1, 1);

      Map<String, Object> vocabulary = h.service.vocabulary();

      assertTrue(String.valueOf(vocabulary.get("content")).contains("formula"));
      assertTrue(String.valueOf(vocabulary.get("expand")).contains("vertical"));
   }

   // ── sort.rankBy / topn.rankBy resolution ─────────────────────────────────────

   private static AggregateRef aggregate(String column, String formulaName) {
      // A real AggregateFormula constant would work here too, but its static initializer reads
      // a property via SreeEnv, which throws outside a running Spring context -- exactly the
      // trap CalcTableService.vocabulary()'s own try/catch exists for. Mocking AggregateRef
      // directly avoids ever touching that class in this pure-Mockito suite.
      AggregateRef agg = mock(AggregateRef.class);
      when(agg.getDataRef()).thenReturn(new AttributeRef(null, column));
      when(agg.getFormulaName()).thenReturn(formulaName);
      return agg;
   }

   @Test
   void resolvesSortRankByToTheMatchingSummaryCellsIndex() throws Exception {
      Harness h = harness(3, 3);
      AggregateRef avg = aggregate("PAID", "Average");
      AggregateRef sum = aggregate("PAID", "Sum");
      when(h.layoutHandler().getCalcAggregateFields(any(), any()))
         .thenReturn(new inetsoft.uql.erm.CalcAggregate[]{ avg, sum });

      h.service.setCellBinding("tok", principal(), "Calc1", 0, 0,
         spec("content", "column", "grouping", "group", "expand", "vertical",
              "field", spec("column", "REGION", "type", "dimension"),
              "sort", spec("direction", "value_desc",
                           "rankBy", spec("column", "PAID", "formula", "Sum"))));

      ArgumentCaptor<SetCellBindingEvent> captor = ArgumentCaptor.forClass(SetCellBindingEvent.class);
      verify(h.layoutService).setCellBinding(eq("rt1"), captor.capture(), any(Principal.class), any());
      inetsoft.web.binding.model.table.OrderModel order = captor.getValue().getBinding().getOrder();
      assertEquals(XConstants.SORT_VALUE_DESC, order.getType());
      // Index 1 ("Sum"), not 0 ("Average") -- proves the match is by {column, formula}, not by
      // first-in-scope-aggregate.
      assertEquals(1, order.getSortCol());
   }

   @Test
   void refusesTopnWithNoRankByWhenMultipleSummaryCellsAreInScope() throws Exception {
      Harness h = harness(3, 3);
      AggregateRef avg = aggregate("PAID", "Average");
      AggregateRef sum = aggregate("PAID", "Sum");
      when(h.layoutHandler().getCalcAggregateFields(any(), any()))
         .thenReturn(new inetsoft.uql.erm.CalcAggregate[]{ avg, sum });

      Exception thrown = assertThrows(Exception.class, () -> h.service.setCellBinding(
         "tok", principal(), "Calc1", 0, 0,
         spec("content", "column", "grouping", "group", "expand", "vertical",
              "field", spec("column", "REGION", "type", "dimension"),
              "topn", spec("mode", "top", "n", 3))));

      assertTrue(thrown.getMessage().contains("ambiguous"));
      assertTrue(thrown.getMessage().contains("Sum"));
      assertTrue(thrown.getMessage().contains("Average"));
   }

   @Test
   void defaultsTopnRankByToTheSoleSummaryCellInScope() throws Exception {
      Harness h = harness(3, 3);
      AggregateRef sum = aggregate("PAID", "Sum");
      when(h.layoutHandler().getCalcAggregateFields(any(), any()))
         .thenReturn(new inetsoft.uql.erm.CalcAggregate[]{ sum });

      h.service.setCellBinding("tok", principal(), "Calc1", 0, 0,
         spec("content", "column", "grouping", "group", "expand", "vertical",
              "field", spec("column", "REGION", "type", "dimension"),
              "topn", spec("mode", "top", "n", 3)));

      ArgumentCaptor<SetCellBindingEvent> captor = ArgumentCaptor.forClass(SetCellBindingEvent.class);
      verify(h.layoutService).setCellBinding(eq("rt1"), captor.capture(), any(Principal.class), any());
      assertEquals(0, captor.getValue().getBinding().getTopn().getSumCol());
   }

   // ── field.namedGroup.others (boolean and string forms) ───────────────────────

   @Test
   void inlineNamedGroupOthersFalseMeansLeaveOthersInTheirOwnGroup() throws Exception {
      Harness h = harness(3, 3);

      h.service.setCellBinding("tok", principal(), "Calc1", 0, 0,
         spec("content", "column", "grouping", "group", "expand", "vertical",
              "field", spec("column", "REGION", "type", "dimension",
                            "namedGroup", spec(
                               "groups", List.of(spec("name", "West",
                                  "conditions", List.of(spec("operator", "one_of",
                                                             "values", List.of("CA"))))),
                               "others", false))));

      ArgumentCaptor<SetCellBindingEvent> captor = ArgumentCaptor.forClass(SetCellBindingEvent.class);
      verify(h.layoutService).setCellBinding(eq("rt1"), captor.capture(), any(Principal.class), any());
      // false must mean "leave" -- inverted, this would silently group instead (the exact
      // opposite of what the natural Boolean convention used elsewhere in inetsoft.web.wiz,
      // e.g. DimensionSortRanking.Ranking.others, means).
      assertFalse(captor.getValue().getBinding().getOrder().isOthers());
   }

   @Test
   void inlineNamedGroupOthersLeaveStringMeansLeaveOthersInTheirOwnGroup() throws Exception {
      Harness h = harness(3, 3);

      h.service.setCellBinding("tok", principal(), "Calc1", 0, 0,
         spec("content", "column", "grouping", "group", "expand", "vertical",
              "field", spec("column", "REGION", "type", "dimension",
                            "namedGroup", spec(
                               "groups", List.of(spec("name", "West",
                                  "conditions", List.of(spec("operator", "one_of",
                                                             "values", List.of("CA"))))),
                               "others", "leave"))));

      ArgumentCaptor<SetCellBindingEvent> captor = ArgumentCaptor.forClass(SetCellBindingEvent.class);
      verify(h.layoutService).setCellBinding(eq("rt1"), captor.capture(), any(Principal.class), any());
      assertFalse(captor.getValue().getBinding().getOrder().isOthers());
   }

   @Test
   void inlineNamedGroupOthersOmittedDefaultsToGroupingThemTogether() throws Exception {
      Harness h = harness(3, 3);

      h.service.setCellBinding("tok", principal(), "Calc1", 0, 0,
         spec("content", "column", "grouping", "group", "expand", "vertical",
              "field", spec("column", "REGION", "type", "dimension",
                            "namedGroup", spec(
                               "groups", List.of(spec("name", "West",
                                  "conditions", List.of(spec("operator", "one_of",
                                                             "values", List.of("CA")))))))));

      ArgumentCaptor<SetCellBindingEvent> captor = ArgumentCaptor.forClass(SetCellBindingEvent.class);
      verify(h.layoutService).setCellBinding(eq("rt1"), captor.capture(), any(Principal.class), any());
      assertTrue(captor.getValue().getBinding().getOrder().isOthers());
   }

   // ── mergeRowGroup / mergeColGroup / timeSeries wiring ─────────────────────────

   @Test
   void wiresMergeRowGroupMergeColGroupAndTimeSeriesOntoTheBinding() throws Exception {
      Harness h = harness(3, 3);

      h.service.setCellBinding("tok", principal(), "Calc1", 0, 0,
         spec("content", "column", "grouping", "group", "expand", "vertical",
              "field", spec("column", "REGION", "type", "dimension"),
              "mergeRowGroup", "TotalsRow", "mergeColGroup", null, "timeSeries", true));

      ArgumentCaptor<SetCellBindingEvent> captor = ArgumentCaptor.forClass(SetCellBindingEvent.class);
      verify(h.layoutService).setCellBinding(eq("rt1"), captor.capture(), any(Principal.class), any());
      CellBindingInfo bound = captor.getValue().getBinding();
      assertEquals("TotalsRow", bound.getMergeRowGroup());
      assertNull(bound.getMergeColGroup());
      assertTrue(bound.isTimeSeries());
   }

   // ── harness ───────────────────────────────────────────────────────────────

   private record Harness(CalcTableService service, ViewsheetSessionService sessions,
                          VSTableLayoutService layoutService, Viewsheet viewsheet,
                          CalcTableVSAssemblyInfo assemblyInfo,
                          DataRefModelFactoryService refModelService,
                          BindableFieldsService fieldsService,
                          VSColumnHandler columnsHandler, TableLayoutHandler layoutHandler) {}

   private static Harness harness(int rows, int cols) {
      CalcTableVSAssembly assembly = mock(CalcTableVSAssembly.class);
      CalcTableVSAssemblyInfo info = mock(CalcTableVSAssemblyInfo.class);
      TableLayout layout = mock(TableLayout.class);
      when(layout.getRowCount()).thenReturn(rows);
      when(layout.getColCount()).thenReturn(cols);
      when(info.getTableLayout()).thenReturn(layout);
      when(assembly.getInfo()).thenReturn(info);
      // Real DataVSAssembly#getSourceInfo() delegates to getInfo().getSourceInfo() -- keep this
      // mock consistent with that so stubbing info.getSourceInfo() (as the named-group tests do)
      // is reflected on the generalized assembly.getSourceInfo() path too.
      when(assembly.getSourceInfo()).thenAnswer(invocation -> info.getSourceInfo());
      return harnessFor(assembly, info, rows, cols);
   }

   private static Harness harnessFor(VSAssembly assembly, int rows, int cols) {
      return harnessFor(assembly, null, rows, cols);
   }

   private static Harness harnessFor(VSAssembly assembly, CalcTableVSAssemblyInfo info,
                                     int rows, int cols)
   {
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenReturn(assembly);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);

      try {
         // A real CapturingCommandDispatcher, not a bare null: resolving a namedGroup against
         // the repository-registered list dispatches GetPredefinedNamedGroupCommand the same
         // way a read does, and needs somewhere real to land.
         doAnswer(invocation -> {
            ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
            return CapturingCommandDispatcher.withCapturingDispatcher(
               principal(), dispatcher -> { mutation.run(rvs, "rt1", dispatcher); return null; });
         }).when(sessions).mutate(anyString(), any(Principal.class), any());
         when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);

         // The read entry point supplies a capturing dispatcher without a checkpoint. Composer
         // services that answer by dispatching a command need it; the test double runs the read
         // against a real CapturingCommandDispatcher so captured commands can be asserted.
         doAnswer(invocation -> {
            ViewsheetSessionService.Read<?> read = invocation.getArgument(2);
            return CapturingCommandDispatcher.withCapturingDispatcher(
               principal(), dispatcher -> read.run(rvs, "rt1", dispatcher));
         }).when(sessions).read(anyString(), any(Principal.class), any());
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      VSTableLayoutService layoutService = mock(VSTableLayoutService.class);
      DataRefModelFactoryService refModelService = mock(DataRefModelFactoryService.class);
      // ConditionVocabulary.toConditionList matches conditions to fields by name, so the
      // stub must echo the real DataRef's name rather than a bare, unstubbed mock (whose
      // getName() would default to null and make every field lookup fail).
      when(refModelService.createDataRefModel(any())).thenAnswer(invocation -> {
         DataRef ref = invocation.getArgument(0);
         DataRefModel model = mock(DataRefModel.class);
         when(model.getName()).thenReturn(ref.getName());
         return model;
      });

      // Default fixture: a single "current" source table carrying every column the existing
      // test suite binds. Individual tests that need to exercise the column-existence check
      // itself stub fieldsService.list(...) again with a narrower fixture.
      BindableFieldsService fieldsService = mock(BindableFieldsService.class);

      try {
         when(fieldsService.list(anyString(), anyString(), any(Principal.class))).thenReturn(
            List.of(new BindableTable("Query1", true, List.of(
               new BindableField("Region", "string", "dimension"),
               new BindableField("REGION", "string", "dimension"),
               new BindableField("PAID", "double", "measure")))));
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      VSColumnHandler columnsHandler = mock(VSColumnHandler.class);
      TableLayoutHandler layoutHandler = mock(TableLayoutHandler.class);

      // Default fixture: the same columns fieldsService.list() above already carries, so
      // CalcTableService.columnRef() (the inline named-group path's own column resolution,
      // separate from BindableFieldsService's column-existence check) also finds "REGION"/
      // "PAID" out of the box, the same way a fresh test doesn't have to stub fieldsService
      // again unless it needs a narrower fixture.
      try {
         ColumnSelection bindableCols = new ColumnSelection();
         bindableCols.addAttribute(new AttributeRef(null, "Region"));
         bindableCols.addAttribute(new AttributeRef(null, "REGION"));
         bindableCols.addAttribute(new AttributeRef(null, "PAID"));
         when(columnsHandler.getColumnSelection(any(), any(), any(), any(),
                                                anyBoolean(), anyBoolean(), anyBoolean(),
                                                anyBoolean(), anyBoolean(), anyBoolean()))
            .thenReturn(bindableCols);
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      // Default fixture: no summary cells in scope. Mockito's default answer for an array
      // return type is null, not empty -- aggregatesOf()'s "new AggregateRef[aggs.length]" NPEs
      // on that for every test that never bound a summary cell, not just the ones that care
      // about rankBy/sort-by-value.
      when(layoutHandler.getCalcAggregateFields(any(), any()))
         .thenReturn(new inetsoft.uql.erm.CalcAggregate[0]);

      return new Harness(
         new CalcTableService(sessions, layoutService, refModelService, fieldsService,
                              columnsHandler, layoutHandler),
         sessions, layoutService, vs, info, refModelService, fieldsService,
         columnsHandler, layoutHandler);
   }

   private static Principal principal() {
      return () -> "admin";
   }

   // ── cell scripts and named groups (2e Phase 3) ────────────────────────────
   //
   // Both composer services return their result by DISPATCHING a command rather than returning
   // it, which is why these need the read-only capturing entry point rather than resolve().

   @Test
   void readsACellScriptOutOfTheDispatchedCommand() throws Exception {
      Harness h = harness(3, 3);
      doAnswer(invocation -> {
         CommandDispatcher dispatcher = invocation.getArgument(3);
         dispatcher.sendCommand("Calc1", new GetCellScriptCommand("sum(PAID)"));
         return null;
      }).when(h.layoutService).getCellScript(anyString(), any(GetCellScriptEvent.class),
                                             any(Principal.class), any());

      Map<String, Object> read = h.service.cellScript("tok", principal(), "Calc1", 1, 1);

      assertEquals("sum(PAID)", read.get("script"));
      assertEquals(1, read.get("row"));
      assertEquals(1, read.get("col"));
   }

   @Test
   void reportsNoScriptRatherThanNullWhenACellHasNone() throws Exception {
      Harness h = harness(3, 3);

      Map<String, Object> read = h.service.cellScript("tok", principal(), "Calc1", 0, 0);

      assertNull(read.get("script"));
      assertNotNull(read.get("note"), "say that no script came back rather than looking empty");
   }

   /**
    * The command carries the group NAMES only: its constructor takes
    * {@code AssetNamedGroupInfo[]} but keeps just {@code getName()} from each, so the members
    * never reach the wire. This asserts what StyleBI actually sends.
    */
   @Test
   void readsNamedGroupNamesOutOfTheDispatchedCommand() throws Exception {
      Harness h = harness(3, 3);
      GetPredefinedNamedGroupCommand command = new GetPredefinedNamedGroupCommand(
         new AssetNamedGroupInfo[0]);
      command.setNamedGroups(new String[]{ "Regions", "Tiers" });
      doAnswer(invocation -> {
         CommandDispatcher dispatcher = invocation.getArgument(3);
         dispatcher.sendCommand("Calc1", command);
         return null;
      }).when(h.layoutService).getNamedGroup(anyString(),
                                             any(GetPredefinedNamedGroupEvent.class),
                                             any(Principal.class), any());

      Map<String, Object> read = h.service.namedGroups("tok", principal(), "Calc1", "REGION");

      assertEquals(List.of("Regions", "Tiers"), read.get("namedGroups"));
      assertEquals("REGION", read.get("column"));
   }

   /**
    * add_named_group creates a {@code DefaultNamedGroupAssembly} as an ordinary secondary
    * assembly inside the calc table's own bound worksheet -- a completely different code path
    * from the asset-repository "predefined named group" kind the block above already covers.
    * Without also scanning the worksheet, a group created via add_named_group would never be
    * listed here even though it genuinely exists.
    */
   @Test
   void includesWorksheetLocalNamedGroupsCreatedByAddNamedGroup() throws Exception {
      Harness h = harness(3, 3);
      when(h.assemblyInfo().getSourceInfo())
         .thenReturn(new SourceInfo(SourceInfo.ASSET, null, "Query1"));

      DefaultNamedGroupAssembly ngAssembly = mock(DefaultNamedGroupAssembly.class);
      when(ngAssembly.getName()).thenReturn("Regions");
      when(ngAssembly.getAttachedType()).thenReturn(AttachedAssembly.COLUMN_ATTACHED);
      when(ngAssembly.getAttachedSource())
         .thenReturn(new SourceInfo(SourceInfo.ASSET, null, "Query1"));
      when(ngAssembly.getAttachedAttribute()).thenReturn(new AttributeRef(null, "REGION"));

      Worksheet ws = mock(Worksheet.class);
      when(ws.getAssemblies()).thenReturn(new Assembly[]{ ngAssembly });
      when(h.viewsheet().getBaseWorksheet()).thenReturn(ws);

      Map<String, Object> read = h.service.namedGroups("tok", principal(), "Calc1", "REGION");

      @SuppressWarnings("unchecked")
      List<String> groups = (List<String>) read.get("namedGroups");
      assertTrue(groups.contains("Regions"), "expected worksheet-local group in: " + groups);
   }

   /**
    * Fix: {@code namedGroups()} previously required a {@code CalcTableVSAssembly}
    * ({@code requireCalcTable} rejected anything else, pointing the caller at
    * get_table_binding/get_binding -- which report current bindings, not the catalog of
    * available named groups). Named groups are a property of the assembly's bound
    * source/column, so a chart/crosstab/table dimension must be discoverable the same way.
    */
   @Test
   void namedGroupsWorksForANonCalcDataAssemblyLikeACrosstab() throws Exception {
      Harness h = harnessFor(mock(CrosstabVSAssembly.class), 0, 0);
      GetPredefinedNamedGroupCommand command = new GetPredefinedNamedGroupCommand(
         new AssetNamedGroupInfo[0]);
      command.setNamedGroups(new String[]{ "Regions" });
      doAnswer(invocation -> {
         CommandDispatcher dispatcher = invocation.getArgument(3);
         dispatcher.sendCommand("Crosstab1", command);
         return null;
      }).when(h.layoutService).getNamedGroup(anyString(),
                                             any(GetPredefinedNamedGroupEvent.class),
                                             any(Principal.class), any());

      Map<String, Object> read =
         h.service.namedGroups("tok", principal(), "Crosstab1", "REGION");

      assertEquals(List.of("Regions"), read.get("namedGroups"));
   }

   /**
    * Same generalization for the worksheet-local ({@code add_named_group}) discovery path,
    * which now resolves the assembly's {@code SourceInfo} directly via
    * {@code DataVSAssembly#getSourceInfo()} rather than casting to
    * {@code CalcTableVSAssemblyInfo}.
    */
   @Test
   void namedGroupsIncludesWorksheetLocalGroupsForANonCalcDataAssembly() throws Exception {
      CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
      when(assembly.getSourceInfo()).thenReturn(new SourceInfo(SourceInfo.ASSET, null, "Query1"));
      Harness h = harnessFor(assembly, 0, 0);

      DefaultNamedGroupAssembly ngAssembly = mock(DefaultNamedGroupAssembly.class);
      when(ngAssembly.getName()).thenReturn("Regions");
      when(ngAssembly.getAttachedType()).thenReturn(AttachedAssembly.COLUMN_ATTACHED);
      when(ngAssembly.getAttachedSource())
         .thenReturn(new SourceInfo(SourceInfo.ASSET, null, "Query1"));
      when(ngAssembly.getAttachedAttribute()).thenReturn(new AttributeRef(null, "REGION"));

      Worksheet ws = mock(Worksheet.class);
      when(ws.getAssemblies()).thenReturn(new Assembly[]{ ngAssembly });
      when(h.viewsheet().getBaseWorksheet()).thenReturn(ws);

      Map<String, Object> read =
         h.service.namedGroups("tok", principal(), "Crosstab1", "REGION");

      @SuppressWarnings("unchecked")
      List<String> groups = (List<String>) read.get("namedGroups");
      assertTrue(groups.contains("Regions"), "expected worksheet-local group in: " + groups);
   }

   /**
    * Bug 76311 / stylebi#4731 (reopened): a group created via {@code add_named_group}'s
    * datasource-scoped mode (datasource+logicalModel+sourceTable+attribute) attaches
    * {@code SourceInfo.MODEL}, source = the logical model name (e.g. "Order Model") -- never
    * equal to the crosstab's own bound {@code SourceInfo} (always {@code SourceInfo.ASSET},
    * source = the worksheet table's own assembly name). The match must fall back to resolving
    * the worksheet table assembly's own underlying {@code SourceInfo} and comparing against
    * that.
    */
   @Test
   void includesWorksheetLocalNamedGroupsForALogicalModelBoundDimension() throws Exception {
      CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
      when(assembly.getSourceInfo()).thenReturn(new SourceInfo(SourceInfo.ASSET, null, "Customer1"));
      Harness h = harnessFor(assembly, 0, 0);

      SourceInfo modelSource = new SourceInfo(SourceInfo.MODEL, "ds", "Order Model");
      DefaultNamedGroupAssembly ngAssembly = mock(DefaultNamedGroupAssembly.class);
      when(ngAssembly.getName()).thenReturn("WestStates");
      when(ngAssembly.getAttachedType()).thenReturn(AttachedAssembly.COLUMN_ATTACHED);
      when(ngAssembly.getAttachedSource()).thenReturn(modelSource);
      when(ngAssembly.getAttachedAttribute()).thenReturn(new AttributeRef(null, "State"));

      AbstractTableAssembly boundTable = mock(AbstractTableAssembly.class);
      when(boundTable.getSourceInfo()).thenReturn(modelSource);

      Worksheet ws = mock(Worksheet.class);
      when(ws.getAssemblies()).thenReturn(new Assembly[]{ ngAssembly });
      when(ws.getAssembly("Customer1")).thenReturn(boundTable);
      when(h.viewsheet().getBaseWorksheet()).thenReturn(ws);

      Map<String, Object> read =
         h.service.namedGroups("tok", principal(), "Crosstab1", "State");

      @SuppressWarnings("unchecked")
      List<String> groups = (List<String>) read.get("namedGroups");
      assertTrue(groups.contains("WestStates"),
                 "expected logical-model-bound group in: " + groups);
   }

   /**
    * The confirmed silent-drop defect: {@code field.namedGroup} naming a worksheet-local group
    * created via {@code add_named_group} must land on {@code CellBindingInfo.order} as an
    * {@code EXPERT_NAMEDGROUP_INFO} built from that assembly's own per-group conditions --
    * {@code order.info} only. {@code order.type} is untouched (stays at {@code OrderModel}'s
    * default) when the call gives no {@code sort}: a named group and a sort direction are
    * independent settings on a real cell (confirmed live -- a Composer cell can carry
    * {@code Sort: Manual} or even {@code Sort: By Value (Asc)} together with a named group), and
    * {@code CalcNamedGroupDialog.apply()}, the Composer's own commit path for a named group,
    * never assigns {@code order.type} either.
    */
   @Test
   void bindsAWorksheetLocalNamedGroupAsAnExpertOrder() throws Exception {
      Harness h = harness(3, 3);
      when(h.assemblyInfo().getSourceInfo())
         .thenReturn(new SourceInfo(SourceInfo.ASSET, null, "Query1"));

      Condition condition = mock(Condition.class);
      when(condition.getOperation()).thenReturn(Condition.EQUAL_TO);
      when(condition.getValues()).thenReturn(List.of("CA"));
      ConditionList conditionList = new ConditionList();
      conditionList.append(new ConditionItem(new AttributeRef(null, "REGION"), condition, 0));
      NamedGroupInfo namedGroupInfo = new NamedGroupInfo();
      namedGroupInfo.setGroupCondition("West", conditionList);

      DefaultNamedGroupAssembly ngAssembly = mock(DefaultNamedGroupAssembly.class);
      when(ngAssembly.getName()).thenReturn("Coastal");
      when(ngAssembly.getAttachedType()).thenReturn(AttachedAssembly.COLUMN_ATTACHED);
      when(ngAssembly.getAttachedSource())
         .thenReturn(new SourceInfo(SourceInfo.ASSET, null, "Query1"));
      when(ngAssembly.getAttachedAttribute()).thenReturn(new AttributeRef(null, "REGION"));
      when(ngAssembly.getNamedGroupInfo()).thenReturn(namedGroupInfo);

      Worksheet ws = mock(Worksheet.class);
      when(ws.getAssemblies()).thenReturn(new Assembly[]{ ngAssembly });
      when(h.viewsheet().getBaseWorksheet()).thenReturn(ws);

      h.service.setCellBinding("tok", principal(), "Calc1", 0, 0,
                               spec("content", "column", "grouping", "group", "expand", "vertical",
                                    "field", Map.of("column", "REGION", "type", "dimension",
                                                    "namedGroup", "Coastal")));

      ArgumentCaptor<SetCellBindingEvent> captor =
         ArgumentCaptor.forClass(SetCellBindingEvent.class);
      verify(h.layoutService).setCellBinding(eq("rt1"), captor.capture(), any(Principal.class),
                                             any());
      inetsoft.web.binding.model.table.OrderModel order = captor.getValue().getBinding().getOrder();
      // No 'sort' was given, so order.type stays at its OrderModel default (SORT_ASC) --
      // resolving a named group only ever sets order.info, never order.type (see the class
      // comment above).
      assertEquals(XConstants.SORT_ASC, order.getType());
      assertEquals(XNamedGroupInfo.EXPERT_NAMEDGROUP_INFO, order.getInfo().getType());
      List<inetsoft.web.composer.model.condition.ConditionExpression> conds =
         order.getInfo().getConditions();
      assertEquals(1, conds.size());
      assertEquals("West", conds.get(0).getName());
      assertEquals(1, conds.get(0).getList().length);
   }

   /**
    * A {@code namedGroup} that matches no worksheet-local assembly is treated as a genuine
    * repository-registered predefined named group, exactly the shape the real Composer UI
    * sends: {@code order.info = {name, type: ASSET_NAMEDGROUP_INFO}}, letting
    * {@code VSTableLayoutService.setNamedGroupInfo}'s existing branch resolve it.
    */
   @Test
   void bindsARegisteredPredefinedNamedGroupAsAnAssetReference() throws Exception {
      Harness h = harness(3, 3);
      GetPredefinedNamedGroupCommand command = new GetPredefinedNamedGroupCommand(
         new AssetNamedGroupInfo[0]);
      command.setNamedGroups(new String[]{ "Tiers" });
      doAnswer(invocation -> {
         CommandDispatcher dispatcher = invocation.getArgument(3);
         dispatcher.sendCommand("Calc1", command);
         return null;
      }).when(h.layoutService).getNamedGroup(anyString(),
                                             any(GetPredefinedNamedGroupEvent.class),
                                             any(Principal.class), any());

      h.service.setCellBinding("tok", principal(), "Calc1", 0, 0,
                               spec("content", "column", "grouping", "group", "expand", "vertical",
                                    "field", Map.of("column", "REGION", "type", "dimension",
                                                    "namedGroup", "Tiers")));

      ArgumentCaptor<SetCellBindingEvent> captor =
         ArgumentCaptor.forClass(SetCellBindingEvent.class);
      verify(h.layoutService).setCellBinding(eq("rt1"), captor.capture(), any(Principal.class),
                                             any());
      inetsoft.web.binding.model.table.OrderModel order = captor.getValue().getBinding().getOrder();
      // Same reasoning as bindsAWorksheetLocalNamedGroupAsAnExpertOrder above: no 'sort' was
      // given, so order.type stays at its default rather than being forced by the named group.
      assertEquals(XConstants.SORT_ASC, order.getType());
      assertEquals(XNamedGroupInfo.ASSET_NAMEDGROUP_INFO, order.getInfo().getType());
      assertEquals("Tiers", order.getInfo().getName());
   }

   /**
    * A {@code namedGroup} matching neither a worksheet-local assembly nor the repository-
    * registered list must fail loud, naming the field/column -- rather than silently building a
    * dangling {@code ASSET_NAMEDGROUP_INFO} reference that resolves to nothing at render time.
    */
   @Test
   void refusesAnUnrecognizedNamedGroup() {
      Harness h = harness(3, 3);

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.setCellBinding(
            "tok", principal(), "Calc1", 0, 0,
            spec("content", "column", "grouping", "group", "expand", "vertical",
                 "field", Map.of("column", "REGION", "type", "dimension",
                                 "namedGroup", "NoSuchGroup"))));

      assertTrue(thrown.getMessage().contains("NoSuchGroup"));
      assertTrue(thrown.getMessage().contains("REGION"));
   }

   @Test
   void namedGroupsNeedsAColumn() {
      Harness h = harness(3, 3);

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.namedGroups("tok", principal(), "Calc1", "  "));
      assertTrue(thrown.getMessage().contains("column"));
   }

   // ── layout operations (2e Phase 2) ────────────────────────────────────────

   @Test
   void insertsARowAndReturnsTheUpdatedLayout() throws Exception {
      Harness h = harness(3, 3);

      Map<String, Object> updated =
         h.service.modifyLayout("tok", principal(), "Calc1", "insertRow", 1, 0, null, null, 1);

      ArgumentCaptor<ModifyTableLayoutEvent> captor =
         ArgumentCaptor.forClass(ModifyTableLayoutEvent.class);
      verify(h.layoutService).modifyLayout(eq("rt1"), captor.capture(), any(Principal.class),
                                           any());
      assertEquals("insertRow", captor.getValue().getOp());
      assertEquals(1, captor.getValue().getNum());
      assertEquals(1, captor.getValue().getSelection().y);
      assertNotNull(updated.get("cells"), "the updated layout comes back with the response");
   }

   /**
    * Coordinates read before a layout op are stale afterwards, so the response says so rather
    * than leaving the caller to remember.
    */
   @Test
   void theResponseWarnsThatEarlierCoordinatesAreStale() throws Exception {
      Harness h = harness(3, 3);

      Map<String, Object> updated =
         h.service.modifyLayout("tok", principal(), "Calc1", "insertRow", 1, 0, null, null, 1);

      assertTrue(String.valueOf(updated.get("note")).contains("stale"));
   }

   @Test
   void acceptsTheOpNameSpellings() throws Exception {
      for(String op : List.of("insertRow", "insert_row", "insertrow")) {
         Harness h = harness(3, 3);
         h.service.modifyLayout("tok", principal(), "Calc1", op, 0, 0, null, null, 1);
         ArgumentCaptor<ModifyTableLayoutEvent> captor =
            ArgumentCaptor.forClass(ModifyTableLayoutEvent.class);
         verify(h.layoutService).modifyLayout(anyString(), captor.capture(), any(Principal.class),
                                              any());
         assertEquals("insertRow", captor.getValue().getOp(), "'" + op + "' should resolve");
      }
   }

   @Test
   void refusesAnUnknownLayoutOpListingTheValid() {
      Harness h = harness(3, 3);

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.modifyLayout("tok", principal(), "Calc1", "explode", 0, 0, null, null,
                                      1));

      assertTrue(thrown.getMessage().contains("explode"));
      assertTrue(thrown.getMessage().contains("mergeCells"));
   }

   /** Merging one cell is a handler no-op that would otherwise report success. */
   @Test
   void refusesMergingASingleCell() {
      Harness h = harness(3, 3);

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.modifyLayout("tok", principal(), "Calc1", "mergeCells", 0, 0, 1, 1, 1));

      assertTrue(thrown.getMessage().contains("report success"));
   }

   @Test
   void acceptsMergingASpanningSelection() throws Exception {
      Harness h = harness(3, 3);

      h.service.modifyLayout("tok", principal(), "Calc1", "mergeCells", 0, 0, 2, 2, 1);

      ArgumentCaptor<ModifyTableLayoutEvent> captor =
         ArgumentCaptor.forClass(ModifyTableLayoutEvent.class);
      verify(h.layoutService).modifyLayout(anyString(), captor.capture(), any(Principal.class),
                                           any());
      assertEquals(2, captor.getValue().getSelection().width);
      assertEquals(2, captor.getValue().getSelection().height);
   }

   /** Splitting an unmerged cell is the other handler no-op. */
   @Test
   void refusesSplittingAnUnmergedCell() {
      Harness h = harness(3, 3);

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.modifyLayout("tok", principal(), "Calc1", "splitCells", 0, 0, null,
                                      null, 1));

      assertTrue(thrown.getMessage().contains("not merged"));
   }

   @Test
   void refusesAnNBelowOne() {
      Harness h = harness(3, 3);

      assertThrows(Exception.class,
                   () -> h.service.modifyLayout("tok", principal(), "Calc1", "insertRow", 0, 0,
                                                null, null, 0));
   }

   @Test
   void refusesALayoutOpOutsideTheGrid() {
      Harness h = harness(2, 2);

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.modifyLayout("tok", principal(), "Calc1", "insertRow", 9, 0, null, null,
                                      1));

      assertTrue(thrown.getMessage().contains("stale"));
   }

   // ── copy / cut / remove (2e Phase 2) ──────────────────────────────────────

   @Test
   void copiesARangeToATarget() throws Exception {
      Harness h = harness(4, 4);

      h.service.copyCells("tok", principal(), "Calc1", "copy",
                          new java.awt.Rectangle(0, 0, 2, 1),
                          new java.awt.Rectangle(0, 2, 2, 1));

      ArgumentCaptor<CopyCutCalcCellEvent> captor =
         ArgumentCaptor.forClass(CopyCutCalcCellEvent.class);
      verify(h.layoutService).copyCut(eq("rt1"), captor.capture(), any(Principal.class), any());
      assertEquals("copy", captor.getValue().getOp());
      assertEquals(2, captor.getValue().getSelections().length);
   }

   @Test
   void removeNeedsNoTarget() throws Exception {
      Harness h = harness(4, 4);

      h.service.copyCells("tok", principal(), "Calc1", "remove",
                          new java.awt.Rectangle(0, 0, 1, 1), null);

      verify(h.layoutService).copyCut(anyString(), any(CopyCutCalcCellEvent.class),
                                      any(Principal.class), any());
   }

   /** Without a target there is nowhere to paste and the operation would do nothing. */
   @Test
   void refusesACopyWithNoTarget() {
      Harness h = harness(4, 4);

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.copyCells("tok", principal(), "Calc1", "copy",
                                   new java.awt.Rectangle(0, 0, 1, 1), null));

      assertTrue(thrown.getMessage().contains("nowhere to paste"));
   }

   @Test
   void refusesAnUnknownCopyOp() {
      Harness h = harness(4, 4);

      assertThrows(Exception.class,
                   () -> h.service.copyCells("tok", principal(), "Calc1", "duplicate",
                                             new java.awt.Rectangle(0, 0, 1, 1), null));
   }

   @Test
   void vocabularyListsTheLayoutAndCopyOps() {
      Harness h = harness(1, 1);

      Map<String, Object> vocabulary = h.service.vocabulary();

      assertTrue(String.valueOf(vocabulary.get("layoutOps")).contains("appendCol"));
      assertTrue(String.valueOf(vocabulary.get("copyOps")).contains("remove"));
   }
}
