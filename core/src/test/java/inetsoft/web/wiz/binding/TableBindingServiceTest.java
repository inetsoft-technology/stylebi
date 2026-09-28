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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.web.binding.controller.VSBindingModelService;
import inetsoft.web.binding.handler.SetTableHeaderAliasHandler;
import inetsoft.web.binding.event.ApplyVSAssemblyInfoEvent;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.model.table.BaseTableBindingModel;
import inetsoft.web.binding.model.table.CalcTableBindingModel;
import inetsoft.web.binding.model.table.CrosstabBindingModel;
import inetsoft.web.binding.model.table.CrosstabOptionInfo;
import inetsoft.web.binding.model.table.TableBindingModel;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.wiz.binding.model.FieldRef;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.w3c.dom.Element;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@code VSUtil}'s static initializer (touched by {@code TableBindingService.liveCrosstabRef}'s
 * {@code VSUtil.isFake} check on the aggregates shelf) needs a Spring/Catalog context, unlike the
 * rest of this file's mocked-{@code RuntimeViewsheet} tests -- so this class carries the same
 * Spring context bootstrap {@code CrossTabFilterTest}/{@code
 * CrosstabSortByValueAliasedAggregateNameTest} already use, additive to (not a replacement for)
 * every other test here.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TableBindingServiceTest {
   private static FieldRef dim(String column) {
      return new FieldRef(column, "dimension", null, null, null);
   }

   @Test
   void setShelfPostsTheModelItReadRatherThanAFreshOne() throws Exception {
      CrosstabBindingModel existing = new CrosstabBindingModel();
      existing.getName2Labels().put("Region", "Sales Region");
      existing.setSource(new inetsoft.web.binding.model.SourceInfo());
      existing.getSource().setSource("ORDERS");
      VSBindingModelService bindings = mock(VSBindingModelService.class);
      CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
      when(assembly.getSourceInfo()).thenReturn(new inetsoft.uql.asset.SourceInfo());

      harness(assembly, existing, bindings)
         .setShelf("tok", principal(), "Crosstab1", "rows", List.of(dim("Region")), null);

      ApplyVSAssemblyInfoEvent event = capture(bindings);
      CrosstabBindingModel posted = (CrosstabBindingModel) event.getBinding();
      assertEquals("Sales Region", posted.getName2Labels().get("Region"),
                   "column labels must survive a shelf write no tool here touches");
      assertEquals(1, posted.getRows().size());
      assertEquals("Crosstab1", event.getName());
   }

   /**
    * The trap flag reports a binding that would produce a cartesian result. Turning it off to
    * make a call succeed trades a reported problem for an unreported one.
    */
   @Test
   void leavesTrapCheckingOn() throws Exception {
      VSBindingModelService bindings = mock(VSBindingModelService.class);
      CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
      when(assembly.getSourceInfo()).thenReturn(new inetsoft.uql.asset.SourceInfo());
      CrosstabBindingModel existing = new CrosstabBindingModel();
      existing.setSource(new inetsoft.web.binding.model.SourceInfo());
      existing.getSource().setSource("ORDERS");

      harness(assembly, existing, bindings)
         .setShelf("tok", principal(), "Crosstab1", "rows", List.of(dim("Region")), null);

      assertTrue(capture(bindings).isCheckTrap());
   }

   @Test
   void aPivotIsOneCheckpointNotTwo() throws Exception {
      CrosstabBindingModel existing = new CrosstabBindingModel();
      TableBindingMutator.setShelf(existing, "rows", List.of(dim("Region"), dim("Year")));
      ViewsheetSessionService sessions = sessionsFor(mock(CrosstabVSAssembly.class));
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      serviceWith(sessions, existing, bindings)
         .moveField("tok", principal(), "Crosstab1", "rows", "cols", "Year", null);

      verify(sessions, times(1)).mutate(anyString(), any(Principal.class), any());
      CrosstabBindingModel posted = (CrosstabBindingModel) capture(bindings).getBinding();
      assertEquals(1, posted.getRows().size());
      assertEquals(1, posted.getCols().size());
   }

   @Test
   void addAndRemoveDelegate() throws Exception {
      CrosstabBindingModel existing = new CrosstabBindingModel();
      existing.setSource(new inetsoft.web.binding.model.SourceInfo());
      existing.getSource().setSource("ORDERS");
      VSBindingModelService bindings = mock(VSBindingModelService.class);
      CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
      when(assembly.getSourceInfo()).thenReturn(new inetsoft.uql.asset.SourceInfo());

      harness(assembly, existing, bindings)
         .addField("tok", principal(), "Crosstab1", "rows", dim("Region"), null, null);

      assertEquals(1, ((CrosstabBindingModel) capture(bindings).getBinding()).getRows().size());
   }

   // ── bug #76350, PCB-004: set_table_fields/add_table_field reported ok:true and never
   // established a source on a sourceless crosstab/table, so the assembly rendered empty with no
   // error -- the identical shape PCB-002 already fixed for charts (ChartBindingService.setShelf
   // + applySource). BindingAgentControllerTest drives the resolve-then-refuse half through the
   // controller endpoint; these assert the service actually applies a resolved source.
   @Test
   void setShelfEstablishesTheGivenSourceWhenTheModelHasNone() throws Exception {
      CrosstabBindingModel existing = withTables("ORDERS", "CUSTOMERS");
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      harness(mock(CrosstabVSAssembly.class), existing, bindings)
         .setShelf("tok", principal(), "Crosstab1", "rows", List.of(dim("Region")), "ORDERS");

      CrosstabBindingModel posted = (CrosstabBindingModel) capture(bindings).getBinding();
      assertNotNull(posted.getSource(), "the model must carry a source after the write");
      assertEquals("ORDERS", posted.getSource().getSource());
      assertEquals(1, posted.getRows().size(), "the shelf write itself must still land");
   }

   @Test
   void setShelfLeavesAnAlreadyBoundSourceAlone() throws Exception {
      CrosstabBindingModel existing = withTables("ORDERS", "CUSTOMERS");
      existing.setSource(new inetsoft.web.binding.model.SourceInfo());
      existing.getSource().setSource("ORDERS");
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      harness(mock(CrosstabVSAssembly.class), existing, bindings)
         .setShelf("tok", principal(), "Crosstab1", "rows", List.of(dim("Region")), "CUSTOMERS");

      CrosstabBindingModel posted = (CrosstabBindingModel) capture(bindings).getBinding();
      assertEquals("ORDERS", posted.getSource().getSource(),
                   "a bound source is never repointed as a side effect of a shelf write -- " +
                   "that is set_table_source with force, not this");
   }

   @Test
   void addFieldEstablishesTheGivenSourceWhenTheModelHasNone() throws Exception {
      CrosstabBindingModel existing = withTables("ORDERS", "CUSTOMERS");
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      harness(mock(CrosstabVSAssembly.class), existing, bindings)
         .addField("tok", principal(), "Crosstab1", "rows", dim("Region"), null, "ORDERS");

      CrosstabBindingModel posted = (CrosstabBindingModel) capture(bindings).getBinding();
      assertNotNull(posted.getSource(), "the model must carry a source after the write");
      assertEquals("ORDERS", posted.getSource().getSource());
      assertEquals(1, posted.getRows().size(), "the field write itself must still land");
   }

   @Test
   void readReportsTheObjectTypeAndShelvesWithoutMutating() throws Exception {
      TableBindingModel existing = new TableBindingModel();
      TableBindingMutator.setShelf(existing, "details", List.of(dim("Region")));
      TableVSAssembly table = mock(TableVSAssembly.class);
      when(table.getVSAssemblyInfo()).thenReturn(new TableVSAssemblyInfo());
      ViewsheetSessionService sessions = sessionsFor(table);

      Map<String, Object> read = serviceWith(sessions, existing,
                                             mock(VSBindingModelService.class))
         .read("tok", principal(), "Table1");

      assertEquals("table", read.get("objectType"));
      @SuppressWarnings("unchecked")
      Map<String, Object> shelves = (Map<String, Object>) read.get("shelves");
      assertTrue(shelves.containsKey("details"));
      assertFalse(shelves.containsKey("groups"),
                  "a table has no grouping — that is Crosstab's job — and this shelf can never " +
                  "be written, so it should not be advertised as readable either");
      assertFalse(shelves.containsKey("rows"), "a table has no rows shelf");
      verify(sessions, never()).mutate(anyString(), any(Principal.class), any());
   }

   @Test
   void refusesAChartNamingIt() {
      TableBindingService service = serviceWith(
         sessionsFor(mock(ChartVSAssembly.class)),
         new inetsoft.web.binding.model.ChartBindingModel(), mock(VSBindingModelService.class));

      Exception thrown = assertThrows(
         Exception.class,
         () -> service.setShelf("tok", principal(), "Chart1", "rows", List.of(dim("Region")),
                                null));
      assertTrue(thrown.getMessage().contains("Chart1"));
      assertTrue(thrown.getMessage().contains("chart"));
   }

   @Test
   void refusesACalcTablePointingAtItsCellLayout() {
      TableBindingService service = serviceWith(
         sessionsFor(mock(CalcTableVSAssembly.class)), new CrosstabBindingModel(),
         mock(VSBindingModelService.class));

      Exception thrown = assertThrows(
         Exception.class,
         () -> service.read("tok", principal(), "Calc1"));
      assertTrue(thrown.getMessage().contains("cell layout"));
   }

   // ── set_column_labels (VTB-011) ────────────────────────────────────────────

   /**
    * Table needs no rendered lens: the alias goes straight onto the model's own
    * {@code ColumnRefModel}, and this also rekeys a pre-existing per-column {@code FormatInfo}
    * entry from the old display name to the new one (ported from
    * {@code ComposerVSTableService.changeColumnTitle}'s own Table-branch rekey).
    */
   @Test
   void setColumnLabelsRenamesATableColumnAndRekeysItsFormat() throws Exception {
      TableBindingModel existing = new TableBindingModel();
      TableBindingMutator.setShelf(existing, "details", List.of(dim("REGION")));

      inetsoft.uql.viewsheet.FormatInfo formatInfo = new inetsoft.uql.viewsheet.FormatInfo();
      inetsoft.report.TableDataPath oldPath = new inetsoft.report.TableDataPath(
         -1, inetsoft.report.TableDataPath.HEADER, inetsoft.uql.schema.XSchema.STRING,
         new String[]{ "REGION" });
      inetsoft.uql.viewsheet.VSCompositeFormat oldFormat =
         new inetsoft.uql.viewsheet.VSCompositeFormat();
      oldFormat.getUserDefinedFormat().setBackgroundValue("16711680");
      formatInfo.setFormat(oldPath, oldFormat);

      TableVSAssembly assembly = mock(TableVSAssembly.class);
      when(assembly.getFormatInfo()).thenReturn(formatInfo);
      inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo mockedTableDataVSAssemblyInfo =
         mock(inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo.class);
      when(assembly.getTableDataVSAssemblyInfo()).thenReturn(mockedTableDataVSAssemblyInfo);
      ArgumentCaptor<inetsoft.uql.viewsheet.FormatInfo> rekeyed =
         ArgumentCaptor.forClass(inetsoft.uql.viewsheet.FormatInfo.class);

      VSBindingModelService bindings = mock(VSBindingModelService.class);
      List<String> applied = serviceWith(sessionsFor(assembly), existing, bindings)
         .setColumnLabels("tok", principal(), "Table1",
                          Map.of("REGION", "Sales Region"), null);

      verify(assembly).setFormatInfo(rekeyed.capture());
      inetsoft.report.TableDataPath newPath = new inetsoft.report.TableDataPath(
         -1, inetsoft.report.TableDataPath.HEADER, inetsoft.uql.schema.XSchema.STRING,
         new String[]{ "Sales Region" });
      assertEquals("16711680",
         rekeyed.getValue().getFormat(newPath).getUserDefinedFormat().getBackgroundValue(),
         "the pre-existing format must follow the rename to the new display name");
      assertNull(rekeyed.getValue().getFormat(oldPath),
         "the old display name's entry must not remain once rekeyed");
      verify(mockedTableDataVSAssemblyInfo).updateColumnWidthNames("REGION", "Sales Region");

      TableBindingModel posted = (TableBindingModel) capture(bindings).getBinding();
      assertEquals("Sales Region",
         ((inetsoft.web.binding.drm.ColumnRefModel) posted.getDetails().get(0)).getAlias());
      assertEquals(List.of("REGION -> Sales Region"), applied);
   }

   /**
    * The regression for the Java-review gap on VTB-011's own merged PR: {@code
    * rekeyTableFormatAndWidth} (as originally named) ported {@code FormatInfo} and column-width
    * but never the highlight map, so a pre-existing per-column {@code TableHighlightAttr} entry
    * silently orphaned under the old display name on rename -- mirrors {@link
    * #setColumnLabelsRenamesATableColumnAndRekeysItsFormat}'s {@code FormatInfo} coverage, for the
    * one rekey category that was missing (ported from {@code
    * ComposerVSTableService.syncHighlight}, which native {@code changeColumnTitle} calls
    * alongside the same {@code FormatInfo}/column-width rekey).
    */
   @Test
   void setColumnLabelsRenamesATableColumnAndRekeysItsHighlight() throws Exception {
      TableBindingModel existing = new TableBindingModel();
      TableBindingMutator.setShelf(existing, "details", List.of(dim("REGION")));

      inetsoft.report.internal.table.TableHighlightAttr hattr =
         new inetsoft.report.internal.table.TableHighlightAttr();
      inetsoft.report.TableDataPath oldPath = new inetsoft.report.TableDataPath(
         -1, inetsoft.report.TableDataPath.HEADER, inetsoft.uql.schema.XSchema.STRING,
         new String[]{ "REGION" });
      // The highlight rule must carry an actual value (a background color, here): both
      // HighlightGroup.validate() (called by setHighlight, including the rekey's own re-insert
      // at the new key) and Highlight.isEmpty() treat a rule with no format and no condition as
      // equivalent to no rule at all and discard it -- an empty rule here would trivially "pass"
      // by never actually being stored under either name.
      inetsoft.report.filter.HighlightGroup group = new inetsoft.report.filter.HighlightGroup();
      inetsoft.report.filter.TextHighlight highlight = new inetsoft.report.filter.TextHighlight();
      // Highlight.isEmpty() checks its own name field, not the map key it's stored under --
      // without this, validate() treats it as empty (no name, no condition) and drops it.
      highlight.setName("h1");
      highlight.setBackground(java.awt.Color.RED);
      group.addHighlight("h1", highlight);
      hattr.setHighlight(oldPath, group);

      TableVSAssembly assembly = mock(TableVSAssembly.class);
      when(assembly.getFormatInfo()).thenReturn(new inetsoft.uql.viewsheet.FormatInfo());
      inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo mockedTableDataVSAssemblyInfo =
         mock(inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo.class);
      when(mockedTableDataVSAssemblyInfo.getHighlightAttr()).thenReturn(hattr);
      when(assembly.getTableDataVSAssemblyInfo()).thenReturn(mockedTableDataVSAssemblyInfo);

      VSBindingModelService bindings = mock(VSBindingModelService.class);
      serviceWith(sessionsFor(assembly), existing, bindings)
         .setColumnLabels("tok", principal(), "Table1",
                          Map.of("REGION", "Sales Region"), null);

      inetsoft.report.TableDataPath newPath = new inetsoft.report.TableDataPath(
         -1, inetsoft.report.TableDataPath.HEADER, inetsoft.uql.schema.XSchema.STRING,
         new String[]{ "Sales Region" });
      assertSame(group, hattr.getHighlight(newPath),
         "the pre-existing highlight must follow the rename to the new display name");
      assertNull(hattr.getHighlight(oldPath),
         "the old display name's highlight entry must not remain once rekeyed");
   }

   /**
    * Structural only, per VTB-011's design doc S5: this proves the write landed in the right
    * field ({@code FormatInfo} at the resolved header {@code TableDataPath}) with a mocked lens —
    * it does not prove a real render shows it, which is what the manual {@code
    * get_viewsheet_image} check is for.
    */
   @Test
   void setColumnLabelsWritesAMessageFormatEntryForACrosstabColumn() throws Exception {
      CrosstabBindingModel existing = new CrosstabBindingModel();
      TableBindingMutator.setShelf(existing, "rows", List.of(dim("Region")));

      inetsoft.uql.viewsheet.VSDimensionRef liveRegion = new inetsoft.uql.viewsheet.VSDimensionRef();
      liveRegion.setGroupColumnValue("Region");
      inetsoft.uql.viewsheet.VSCrosstabInfo crossInfo = new inetsoft.uql.viewsheet.VSCrosstabInfo();
      crossInfo.setDesignRowHeaders(new inetsoft.uql.erm.DataRef[]{ liveRegion });

      CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
      when(assembly.getVSCrosstabInfo()).thenReturn(crossInfo);
      when(assembly.getAbsoluteName()).thenReturn("Crosstab1");
      inetsoft.uql.viewsheet.FormatInfo formatInfo = new inetsoft.uql.viewsheet.FormatInfo();
      when(assembly.getFormatInfo()).thenReturn(formatInfo);

      inetsoft.report.composition.VSTableLens lens =
         mock(inetsoft.report.composition.VSTableLens.class);
      when(lens.getHeaderRowCount()).thenReturn(1);
      when(lens.getHeaderColCount()).thenReturn(1);
      when(lens.getColCount()).thenReturn(1);
      when(lens.getRowCount()).thenReturn(1);
      inetsoft.report.TableDataPath headerPath = new inetsoft.report.TableDataPath(
         -1, inetsoft.report.TableDataPath.HEADER, inetsoft.uql.schema.XSchema.STRING,
         new String[]{ "Cell [0,0]" });
      when(lens.getTableDataPath(0, 0)).thenReturn(headerPath);

      inetsoft.report.composition.execution.ViewsheetSandbox sandbox =
         mock(inetsoft.report.composition.execution.ViewsheetSandbox.class);
      when(sandbox.getVSTableLens("Crosstab1", false)).thenReturn(lens);

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenReturn(assembly);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(java.util.Optional.of(sandbox));

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      doAnswer(invocation -> {
         ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
         mutation.run(rvs, "rt1", null);
         return null;
      }).when(sessions).mutate(anyString(), any(Principal.class), any());

      VSBindingModelService bindings = mock(VSBindingModelService.class);
      List<String> applied = serviceWith(sessions, existing, bindings)
         .setColumnLabels("tok", principal(), "Crosstab1",
                          Map.of("Region", "Sales Region"), null);

      inetsoft.uql.viewsheet.VSCompositeFormat written = formatInfo.getFormat(headerPath);
      assertNotNull(written, "a format must land at the resolved header TableDataPath");
      assertEquals(inetsoft.uql.viewsheet.VSFormat.MESSAGE_FORMAT,
         written.getUserDefinedFormat().getFormatValue());
      assertEquals("Sales Region", written.getUserDefinedFormat().getFormatExtentValue());
      assertEquals(List.of("rows[0] -> Sales Region"), applied);

      // The Crosstab branch has no ColumnRefModel.alias to write -- the model posted back must
      // be otherwise untouched by this call.
      CrosstabBindingModel posted = (CrosstabBindingModel) capture(bindings).getBinding();
      assertEquals(1, posted.getRows().size());
   }

   /**
    * Regression test for VTB-011 fix round 1, defect 1 -- live-reproduced on a crosstab with
    * exactly 1 row dimension, 0 column dimensions, 1 aggregate (a common, unremarkable shape):
    * {@code set_column_labels(CT1, {"Paid": "Revenue"})} failed with "Could not find
    * 'aggregates[0]' on the rendered header", even though the column-identity resolution had
    * already succeeded. Root cause: with no column-shelf dimension, the aggregate's header cell
    * renders side-by-side in the single header *row*, past the header *column* rectangle ({@code
    * col == getHeaderColCount()}, not {@code < getHeaderColCount()}) -- {@code findHeaderPath}'s
    * old scan bound never reached it. {@code SetTableHeaderAliasHandlerTest} covers the same fix
    * at the unit level with a mocked lens; this proves it through the actual
    * {@code TableBindingService.setColumnLabels} call path.
    */
   @Test
   void setColumnLabelsRenamesACrosstabAggregateHeaderRenderedPastTheHeaderColumnRectangle()
      throws Exception
   {
      CrosstabBindingModel existing = new CrosstabBindingModel();
      TableBindingMutator.setShelf(existing, "rows", List.of(dim("Date")));
      TableBindingMutator.setShelf(existing, "aggregates",
                                   List.of(new FieldRef("Paid", "measure", "Sum", null, null)));

      inetsoft.uql.viewsheet.VSDimensionRef liveDate = new inetsoft.uql.viewsheet.VSDimensionRef();
      liveDate.setGroupColumnValue("Date");
      // A mock, not a real VSAggregateRef -- constructing a real one and calling getFullName()
      // touches AggregateFormula's static initializer, which needs a Spring/Catalog context this
      // plain unit test doesn't have (see SetTableHeaderAliasHandlerTest's own comment for the
      // same constraint).
      inetsoft.uql.viewsheet.VSAggregateRef livePaid =
         mock(inetsoft.uql.viewsheet.VSAggregateRef.class);
      when(livePaid.getFullName()).thenReturn("Sum(Paid)");

      inetsoft.uql.viewsheet.VSCrosstabInfo crossInfo = new inetsoft.uql.viewsheet.VSCrosstabInfo();
      crossInfo.setDesignRowHeaders(new inetsoft.uql.erm.DataRef[]{ liveDate });
      crossInfo.setDesignAggregates(new inetsoft.uql.erm.DataRef[]{ livePaid });

      CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
      when(assembly.getVSCrosstabInfo()).thenReturn(crossInfo);
      when(assembly.getAbsoluteName()).thenReturn("CT1");
      inetsoft.uql.viewsheet.FormatInfo formatInfo = new inetsoft.uql.viewsheet.FormatInfo();
      when(assembly.getFormatInfo()).thenReturn(formatInfo);

      // Mirrors the live shape: headerRowCount=1, headerColCount=1 (just the Date dimension
      // column) -- the aggregate's GROUP_HEADER cell is at (0,1), past the header-column
      // rectangle, inside the single header row.
      inetsoft.report.composition.VSTableLens lens =
         mock(inetsoft.report.composition.VSTableLens.class);
      when(lens.getHeaderRowCount()).thenReturn(1);
      when(lens.getHeaderColCount()).thenReturn(1);
      when(lens.getColCount()).thenReturn(2);
      when(lens.getRowCount()).thenReturn(3);
      inetsoft.report.TableDataPath dimPath = new inetsoft.report.TableDataPath(
         -1, inetsoft.report.TableDataPath.HEADER, inetsoft.uql.schema.XSchema.STRING,
         new String[]{ "Cell [0,0]" });
      inetsoft.report.TableDataPath aggPath = new inetsoft.report.TableDataPath(
         -1, inetsoft.report.TableDataPath.GROUP_HEADER, inetsoft.uql.schema.XSchema.STRING,
         new String[]{ "Sum(Paid)" });
      when(lens.getTableDataPath(0, 0)).thenReturn(dimPath);
      when(lens.getTableDataPath(0, 1)).thenReturn(aggPath);

      inetsoft.report.composition.execution.ViewsheetSandbox sandbox =
         mock(inetsoft.report.composition.execution.ViewsheetSandbox.class);
      when(sandbox.getVSTableLens("CT1", false)).thenReturn(lens);

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenReturn(assembly);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(java.util.Optional.of(sandbox));

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      doAnswer(invocation -> {
         ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
         mutation.run(rvs, "rt1", null);
         return null;
      }).when(sessions).mutate(anyString(), any(Principal.class), any());

      VSBindingModelService bindings = mock(VSBindingModelService.class);
      List<String> applied = serviceWith(sessions, existing, bindings)
         .setColumnLabels("tok", principal(), "CT1", Map.of("Paid", "Revenue"), null);

      inetsoft.uql.viewsheet.VSCompositeFormat written = formatInfo.getFormat(aggPath);
      assertNotNull(written,
         "the aggregate's GROUP_HEADER cell (past the header-column rectangle) must be found");
      assertEquals(inetsoft.uql.viewsheet.VSFormat.MESSAGE_FORMAT,
         written.getUserDefinedFormat().getFormatValue());
      assertEquals("Revenue", written.getUserDefinedFormat().getFormatExtentValue());
      assertEquals(List.of("aggregates[0] -> Revenue"), applied);
   }

   // ── set_column_widths ────────────────────────────────────────────────────

   @Test
   void setColumnWidthsAppliesAPositiveWidthToTheMatchedColumn() throws Exception {
      TableBindingModel existing = new TableBindingModel();
      TableVSAssembly assembly = mock(TableVSAssembly.class);
      when(assembly.getAbsoluteName()).thenReturn("Table1");
      inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo info =
         mock(inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo.class);
      when(assembly.getInfo()).thenReturn(info);

      inetsoft.report.composition.VSTableLens lens =
         mock(inetsoft.report.composition.VSTableLens.class);
      when(lens.getHeaderRowCount()).thenReturn(1);
      when(lens.getRowCount()).thenReturn(1);
      when(lens.getColCount()).thenReturn(2);
      when(lens.getObject(0, 0)).thenReturn("Region");
      when(lens.getObject(0, 1)).thenReturn("Total");

      VSBindingModelService bindings = mock(VSBindingModelService.class);
      List<String> applied = serviceWith(sessionsWithLens(assembly, "Table1", lens),
                                         existing, bindings)
         .setColumnWidths("tok", principal(), "Table1", Map.of("Region", 120.0));

      verify(info).setColumnWidthValue2(0, 120.0, lens);
      verify(info).setExplicitTableWidthValue(true);
      assertEquals(List.of("Region -> 120px"), applied);
   }

   @Test
   void setColumnWidthsResetsANullWidthToAutoFit() throws Exception {
      TableBindingModel existing = new TableBindingModel();
      TableVSAssembly assembly = mock(TableVSAssembly.class);
      when(assembly.getAbsoluteName()).thenReturn("Table1");
      inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo info =
         mock(inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo.class);
      when(assembly.getInfo()).thenReturn(info);

      inetsoft.report.composition.VSTableLens lens =
         mock(inetsoft.report.composition.VSTableLens.class);
      when(lens.getHeaderRowCount()).thenReturn(1);
      when(lens.getRowCount()).thenReturn(1);
      when(lens.getColCount()).thenReturn(1);
      when(lens.getObject(0, 0)).thenReturn("Region");

      VSBindingModelService bindings = mock(VSBindingModelService.class);
      List<String> applied = serviceWith(sessionsWithLens(assembly, "Table1", lens),
                                         existing, bindings)
         .setColumnWidths("tok", principal(), "Table1",
                          java.util.Collections.singletonMap("Region", null));

      verify(info).setColumnWidthValue(0, Double.NaN);
      assertEquals(List.of("Region -> auto"), applied);
   }

   @Test
   void setColumnWidthsRejectsANonPositiveOrNonFiniteWidth() throws Exception {
      TableBindingModel existing = new TableBindingModel();
      TableVSAssembly assembly = mock(TableVSAssembly.class);
      when(assembly.getAbsoluteName()).thenReturn("Table1");
      inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo info =
         mock(inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo.class);
      when(assembly.getInfo()).thenReturn(info);

      inetsoft.report.composition.VSTableLens lens =
         mock(inetsoft.report.composition.VSTableLens.class);
      when(lens.getHeaderRowCount()).thenReturn(1);
      when(lens.getRowCount()).thenReturn(1);
      when(lens.getColCount()).thenReturn(1);
      when(lens.getObject(0, 0)).thenReturn("Region");

      for(double bad : new double[]{ 0.0, -5.0, Double.NaN, Double.POSITIVE_INFINITY }) {
         VSBindingModelService bindings = mock(VSBindingModelService.class);
         TableBindingService service = serviceWith(sessionsWithLens(assembly, "Table1", lens),
                                                    existing, bindings);

         assertThrows(IllegalArgumentException.class,
            () -> service.setColumnWidths("tok", principal(), "Table1",
                                          Map.of("Region", bad)),
            "width " + bad + " must be refused");
      }

      verify(info, never()).setColumnWidthValue2(anyInt(), anyDouble(), any());
   }

   @Test
   void setColumnWidthsRejectsAnUnknownColumnName() throws Exception {
      TableBindingModel existing = new TableBindingModel();
      TableVSAssembly assembly = mock(TableVSAssembly.class);
      when(assembly.getAbsoluteName()).thenReturn("Table1");
      inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo info =
         mock(inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo.class);
      when(assembly.getInfo()).thenReturn(info);

      inetsoft.report.composition.VSTableLens lens =
         mock(inetsoft.report.composition.VSTableLens.class);
      when(lens.getHeaderRowCount()).thenReturn(1);
      when(lens.getRowCount()).thenReturn(1);
      when(lens.getColCount()).thenReturn(1);
      when(lens.getObject(0, 0)).thenReturn("Region");

      TableBindingService service = serviceWith(sessionsWithLens(assembly, "Table1", lens),
                                                existing, mock(VSBindingModelService.class));

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> service.setColumnWidths("tok", principal(), "Table1", Map.of("Nope", 100.0)));
      assertTrue(thrown.getMessage().contains("Nope"));
      assertTrue(thrown.getMessage().contains("Table1"));
   }

   @Test
   void setColumnWidthsRejectsAnAmbiguousColumnName() throws Exception {
      TableBindingModel existing = new TableBindingModel();
      TableVSAssembly assembly = mock(TableVSAssembly.class);
      when(assembly.getAbsoluteName()).thenReturn("Table1");
      inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo info =
         mock(inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo.class);
      when(assembly.getInfo()).thenReturn(info);

      inetsoft.report.composition.VSTableLens lens =
         mock(inetsoft.report.composition.VSTableLens.class);
      when(lens.getHeaderRowCount()).thenReturn(1);
      when(lens.getRowCount()).thenReturn(1);
      when(lens.getColCount()).thenReturn(2);
      when(lens.getObject(0, 0)).thenReturn("Region");
      when(lens.getObject(0, 1)).thenReturn("Region");

      TableBindingService service = serviceWith(sessionsWithLens(assembly, "Table1", lens),
                                                existing, mock(VSBindingModelService.class));

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> service.setColumnWidths("tok", principal(), "Table1", Map.of("Region", 100.0)));
      assertTrue(thrown.getMessage().contains("ambiguous"));
      verify(info, never()).setColumnWidthValue2(anyInt(), anyDouble(), any());
   }

   /**
    * Regression test for the "wrong matching key" defect confirmed via a live
    * {@code set_column_widths} call against a running server: a crosstab dimension header
    * cell's {@code TableDataPath} last segment is an internal positional token ({@code
    * "Cell [0,0]"}, see {@code CrossFilterDataDescriptor.getCellDataPath}), not the rendered
    * text -- matching against the path (as the pre-fix implementation did) could never find a
    * dimension header by its rendered name, even though it is plainly visible. The fix matches
    * the lens's actual rendered cell value ({@code getObject}) instead.
    */
   @Test
   void setColumnWidthsMatchesACrosstabDimensionHeaderByItsRenderedCellValue() throws Exception {
      CrosstabBindingModel existing = new CrosstabBindingModel();
      CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
      when(assembly.getAbsoluteName()).thenReturn("CT1");
      inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo info =
         mock(inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo.class);
      when(assembly.getInfo()).thenReturn(info);

      inetsoft.report.composition.VSTableLens lens =
         mock(inetsoft.report.composition.VSTableLens.class);
      when(lens.getHeaderRowCount()).thenReturn(1);
      when(lens.getHeaderColCount()).thenReturn(1);
      when(lens.getColCount()).thenReturn(1);
      when(lens.getRowCount()).thenReturn(1);
      // The path's own last segment is an internal positional token, not the rendered text --
      // proves the fix isn't (still) matching against getTableDataPath.
      when(lens.getTableDataPath(0, 0)).thenReturn(new inetsoft.report.TableDataPath(
         -1, inetsoft.report.TableDataPath.HEADER, inetsoft.uql.schema.XSchema.STRING,
         new String[]{ "Cell [0,0]" }));
      when(lens.getObject(0, 0)).thenReturn("SALES_REP_NAME");

      VSBindingModelService bindings = mock(VSBindingModelService.class);
      List<String> applied = serviceWith(sessionsWithLens(assembly, "CT1", lens),
                                         existing, bindings)
         .setColumnWidths("tok", principal(), "CT1", Map.of("SALES_REP_NAME", 150.0));

      verify(info).setColumnWidthValue2(0, 150.0, lens);
      assertEquals(List.of("SALES_REP_NAME -> 150px"), applied);
   }

   /**
    * Regression test for the "L-shape scan-coverage gap" defect: on a non-side-by-side crosstab
    * (1 row dimension, 1 column dimension, 2+ aggregates), each aggregate's own label renders in
    * column 0 at a row past the header-row rectangle ({@code CrossTabFilter}'s "no label header
    * and side by side is false" branch writes {@code getMeasureHeader(i, false)} -- the
    * aggregate's own full name -- to {@code data[ccount + i][0]}), never in row 0 -- a
    * row-0-only scan can never find it. The fix scans the same L-shaped region {@code
    * SetTableHeaderAliasHandler.findHeaderPath} already scans for {@code set_column_labels}.
    */
   @Test
   void setColumnWidthsMatchesANonSideBySideAggregateHeaderPastTheHeaderRowRectangle()
      throws Exception
   {
      CrosstabBindingModel existing = new CrosstabBindingModel();
      CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
      when(assembly.getAbsoluteName()).thenReturn("CT1");
      inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo info =
         mock(inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo.class);
      when(assembly.getInfo()).thenReturn(info);

      // 1 row dim ("Date"), 1 col dim, 2 non-side-by-side aggregates: headerRowCount=1 (the
      // single column-dimension header row), headerColCount=1 (the row-dimension column); the
      // two aggregate labels render at (1,0) and (2,0), past the header-row rectangle.
      inetsoft.report.composition.VSTableLens lens =
         mock(inetsoft.report.composition.VSTableLens.class);
      when(lens.getHeaderRowCount()).thenReturn(1);
      when(lens.getHeaderColCount()).thenReturn(1);
      when(lens.getColCount()).thenReturn(2);
      when(lens.getRowCount()).thenReturn(3);
      when(lens.getObject(0, 0)).thenReturn("Date");
      when(lens.getObject(0, 1)).thenReturn("2024");
      when(lens.getObject(1, 0)).thenReturn("Sum(Paid)");
      when(lens.getObject(2, 0)).thenReturn("Sum(Cost)");
      stubAggregates(assembly, "Sum(Paid)", "Sum(Cost)");
      when(lens.getTableDataPath(1, 0)).thenReturn(groupHeaderPath("Sum(Paid)"));
      when(lens.getTableDataPath(2, 0)).thenReturn(groupHeaderPath("Sum(Cost)"));

      VSBindingModelService bindings = mock(VSBindingModelService.class);
      List<String> applied = serviceWith(sessionsWithLens(assembly, "CT1", lens),
                                         existing, bindings)
         .setColumnWidths("tok", principal(), "CT1", Map.of("Sum(Paid)", 90.0));

      verify(info).setColumnWidthValue2(0, 90.0, lens);
      assertEquals(List.of("Sum(Paid) -> 90px"), applied);
   }

   /**
    * Bug #77036: the left arm of the scan also covers the row-dimension value cells ("USA
    * East"), which matched by text and widened the whole row-header column. Only an aggregate
    * label cell -- its path's last segment is that aggregate's full name -- may match there.
    */
   @Test
   void setColumnWidthsRefusesARowDimensionValueAsAColumnName() throws Exception {
      CrosstabBindingModel existing = new CrosstabBindingModel();
      CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
      when(assembly.getAbsoluteName()).thenReturn("CT1");
      inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo info =
         mock(inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo.class);
      when(assembly.getInfo()).thenReturn(info);

      // rows=[Region], side-by-side Sum(Total): header row, then one row per Region value.
      inetsoft.report.composition.VSTableLens lens =
         mock(inetsoft.report.composition.VSTableLens.class);
      when(lens.getHeaderRowCount()).thenReturn(1);
      when(lens.getHeaderColCount()).thenReturn(1);
      when(lens.getColCount()).thenReturn(2);
      when(lens.getRowCount()).thenReturn(3);
      when(lens.getObject(0, 0)).thenReturn("Region");
      when(lens.getObject(0, 1)).thenReturn("Sum(Total)");
      when(lens.getObject(1, 0)).thenReturn("USA East");
      when(lens.getObject(2, 0)).thenReturn("USA West");
      when(lens.getTableDataPath(1, 0)).thenReturn(groupHeaderPath("Region"));
      when(lens.getTableDataPath(2, 0)).thenReturn(groupHeaderPath("Region"));
      stubAggregates(assembly, "Sum(Total)");

      TableBindingService service = serviceWith(sessionsWithLens(assembly, "CT1", lens),
                                                existing, mock(VSBindingModelService.class));

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.setColumnWidths("tok", principal(), "CT1", Map.of("USA East", 600.0)));

      assertTrue(thrown.getMessage().contains("not a visible column"), thrown.getMessage());
      verify(info, never()).setColumnWidthValue2(anyInt(), anyDouble(), any());
   }

   /**
    * The lens builds its paths from the runtime aggregates, whose full names can differ from
    * the stored ones -- a {@code $(var)} measure renders as {@code Sum(Sales)}, not {@code
    * Sum($(measure))}. A label known only to the runtime list must still match.
    */
   @Test
   void setColumnWidthsMatchesAnAggregateLabelKnownOnlyToTheRuntimeAggregates() throws Exception {
      CrosstabBindingModel existing = new CrosstabBindingModel();
      CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
      when(assembly.getAbsoluteName()).thenReturn("CT1");
      inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo info =
         mock(inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo.class);
      when(assembly.getInfo()).thenReturn(info);

      inetsoft.report.composition.VSTableLens lens =
         mock(inetsoft.report.composition.VSTableLens.class);
      when(lens.getHeaderRowCount()).thenReturn(1);
      when(lens.getHeaderColCount()).thenReturn(1);
      when(lens.getColCount()).thenReturn(2);
      when(lens.getRowCount()).thenReturn(2);
      when(lens.getObject(0, 0)).thenReturn("Date");
      when(lens.getObject(1, 0)).thenReturn("Sum(Sales)");
      when(lens.getTableDataPath(1, 0)).thenReturn(groupHeaderPath("Sum(Sales)"));

      stubAggregates(assembly, "Sum($(measure))");
      VSAggregateRef runtime = mock(VSAggregateRef.class);
      when(runtime.getFullName()).thenReturn("Sum(Sales)");
      when(assembly.getVSCrosstabInfo().getRuntimeAggregates())
         .thenReturn(new DataRef[]{ runtime });

      List<String> applied = serviceWith(sessionsWithLens(assembly, "CT1", lens),
                                         existing, mock(VSBindingModelService.class))
         .setColumnWidths("tok", principal(), "CT1", Map.of("Sum(Sales)", 90.0));

      verify(info).setColumnWidthValue2(0, 90.0, lens);
      assertEquals(List.of("Sum(Sales) -> 90px"), applied);
   }

   private static void stubAggregates(CrosstabVSAssembly assembly, String... fullNames) {
      DataRef[] aggregates = new DataRef[fullNames.length];

      for(int i = 0; i < fullNames.length; i++) {
         VSAggregateRef aggregate = mock(VSAggregateRef.class);
         when(aggregate.getFullName()).thenReturn(fullNames[i]);
         aggregates[i] = aggregate;
      }

      VSCrosstabInfo crossInfo = mock(VSCrosstabInfo.class);
      when(crossInfo.getAggregates()).thenReturn(aggregates);
      when(assembly.getVSCrosstabInfo()).thenReturn(crossInfo);
   }

   private static inetsoft.report.TableDataPath groupHeaderPath(String segment) {
      return new inetsoft.report.TableDataPath(
         -1, inetsoft.report.TableDataPath.GROUP_HEADER, inetsoft.uql.schema.XSchema.STRING,
         new String[]{ segment });
   }

   @Test
   void setColumnWidthsRefusesAChartNamingIt() {
      TableBindingService service = serviceWith(
         sessionsFor(mock(ChartVSAssembly.class)),
         new inetsoft.web.binding.model.ChartBindingModel(), mock(VSBindingModelService.class));

      Exception thrown = assertThrows(
         Exception.class,
         () -> service.setColumnWidths("tok", principal(), "Chart1", Map.of("Region", 100.0)));
      assertTrue(thrown.getMessage().contains("Chart1"));
      assertTrue(thrown.getMessage().contains("chart"));
   }

   @Test
   void setColumnWidthsRequiresAnActiveRenderSandbox() {
      TableBindingModel existing = new TableBindingModel();
      TableBindingService service = serviceWith(sessionsFor(mock(TableVSAssembly.class)),
                                                existing, mock(VSBindingModelService.class));

      Exception thrown = assertThrows(
         Exception.class,
         () -> service.setColumnWidths("tok", principal(), "Table1", Map.of("Region", 100.0)));
      assertTrue(thrown.getMessage().contains("sandbox"));
   }

   @Test
   void setColumnWidthsRejectsAnEmptyWidthsMap() {
      TableBindingService service = serviceWith(sessionsFor(mock(TableVSAssembly.class)),
                                                new TableBindingModel(),
                                                mock(VSBindingModelService.class));

      assertThrows(IllegalArgumentException.class,
         () -> service.setColumnWidths("tok", principal(), "Table1", Map.of()));
   }

   // ── harness ───────────────────────────────────────────────────────────────

   // ── set_table_source ──────────────────────────────────────────────────────
   //
   // A crosstab or table added in the Composer starts with no source. Its shelves can be
   // populated — set_table_fields reports success — and it renders nothing at all, because
   // shelves with no source have nothing to query. Nothing in the plugin could assign one.

   @Test
   void setSourceAssignsAnAssetSourceByName() throws Exception {
      CrosstabBindingModel existing = withTables("ORDERS", "CUSTOMERS");
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      harness(mock(CrosstabVSAssembly.class), existing, bindings)
         .setSource("tok", principal(), "Crosstab1", "ORDERS", false);

      CrosstabBindingModel posted = (CrosstabBindingModel) capture(bindings).getBinding();
      assertNotNull(posted.getSource(), "the model must carry a source after the write");
      assertEquals("ORDERS", posted.getSource().getSource());
      assertEquals(inetsoft.uql.asset.SourceInfo.ASSET, posted.getSource().getType(),
                   "worksheet tables bind as ASSET — the form used everywhere else");
   }

   @Test
   void setSourceRefusesATableTheAssemblyCannotSee() {
      CrosstabBindingModel existing = withTables("ORDERS", "CUSTOMERS");

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> harness(mock(CrosstabVSAssembly.class), existing, mock(VSBindingModelService.class))
            .setSource("tok", principal(), "Crosstab1", "NOPE", false));

      assertTrue(thrown.getMessage().contains("NOPE"));
      assertTrue(thrown.getMessage().contains("ORDERS"), "list what it can bind to");
   }

   /**
    * Repointing a bound assembly discards every field on its shelves, because the columns
    * belong to the old source. Doing that silently on one call is the failure mode this whole
    * plugin family exists to avoid.
    */
   @Test
   void setSourceRefusesToDiscardBoundFieldsUnlessForced() {
      CrosstabBindingModel existing = withTables("ORDERS", "CUSTOMERS");
      existing.setRows(List.of(new inetsoft.web.binding.model.BDimensionRefModel()));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> harness(mock(CrosstabVSAssembly.class), existing, mock(VSBindingModelService.class))
            .setSource("tok", principal(), "Crosstab1", "CUSTOMERS", false));

      assertTrue(thrown.getMessage().contains("force"), "name the way through");
   }

   @Test
   void setSourceProceedsWhenForced() throws Exception {
      CrosstabBindingModel existing = withTables("ORDERS", "CUSTOMERS");
      existing.setRows(List.of(new inetsoft.web.binding.model.BDimensionRefModel()));
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      harness(mock(CrosstabVSAssembly.class), existing, bindings)
         .setSource("tok", principal(), "Crosstab1", "CUSTOMERS", true);

      CrosstabBindingModel posted = (CrosstabBindingModel) capture(bindings).getBinding();
      assertEquals("CUSTOMERS", posted.getSource().getSource());
      assertTrue(posted.getRows().isEmpty(),
                 "force:true must discard the old source's fields, not just skip the refusal");
   }

   /**
    * The regression for the original bug where {@code force:true} skipped the refusal but never
    * actually discarded anything: setSource left every shelf's old-source field refs in place,
    * and those stale refs were written straight back onto the live assembly by the factory that
    * follows this mutation — read back later (e.g. via get_binding) as fields from a source the
    * assembly no longer has. This case uses a new source whose columns genuinely do not include
    * any of the old ones, so every field is correctly discarded either way; the sibling test
    * {@link #setSourceKeepsFieldsThatStillResolveInTheNewSourceWhenForced} covers the case this
    * one cannot distinguish -- a column that exists in both sources.
    */
   @Test
   void setSourceDiscardsFieldsThatDoNotResolveInTheNewSourceWhenForced() throws Exception {
      CrosstabBindingModel existing = withTablesAndColumns(
         Map.of("CUSTOMERS", List.of("STATE", "RESELLER", "CUSTOMER_ID"),
               "ORDERS1", List.of("ORDER_ID", "ORDER_DATE")));
      TableBindingMutator.setShelf(existing, "rows", List.of(dim("STATE")));
      TableBindingMutator.setShelf(existing, "cols", List.of(dim("RESELLER")));
      TableBindingMutator.setShelf(existing, "aggregates",
                                   List.of(new FieldRef("CUSTOMER_ID", "measure", "sum", null,
                                                        null)));
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      harness(mock(CrosstabVSAssembly.class), existing, bindings)
         .setSource("tok", principal(), "Crosstab1", "ORDERS1", true);

      CrosstabBindingModel posted = (CrosstabBindingModel) capture(bindings).getBinding();
      assertEquals("ORDERS1", posted.getSource().getSource());
      assertTrue(posted.getRows().isEmpty(),
                 "STATE does not exist in ORDERS1, so it must not survive");
      assertTrue(posted.getCols().isEmpty(),
                 "RESELLER does not exist in ORDERS1, so it must not survive");
      assertTrue(posted.getAggregates().isEmpty(),
                 "CUSTOMER_ID does not exist in ORDERS1, so it must not survive");
   }

   /**
    * The regression for the divergence the parity audit found relative to the UI's own repoint
    * path: {@code VSAssemblyInfoHandler}'s own comment states the intent plainly ("check the old
    * binding columns when source changed, if cannot found the columns in the source, just remove
    * them") -- implying a column that DOES still resolve must be kept, not blanket-discarded.
    * Repointing to a same-shaped source (e.g. a partitioned/monthly sibling table) must not lose
    * bindings a human doing the equivalent repoint would keep.
    */
   @Test
   void setSourceKeepsFieldsThatStillResolveInTheNewSourceWhenForced() throws Exception {
      CrosstabBindingModel existing = withTablesAndColumns(
         Map.of("ORDERS", List.of("ORDER_ID", "ORDER_DATE", "REGION"),
               "ORDERS_V2", List.of("ORDER_ID", "ORDER_DATE")));
      TableBindingMutator.setShelf(existing, "rows", List.of(dim("ORDER_DATE"), dim("REGION")));
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      harness(mock(CrosstabVSAssembly.class), existing, bindings)
         .setSource("tok", principal(), "Crosstab1", "ORDERS_V2", true);

      CrosstabBindingModel posted = (CrosstabBindingModel) capture(bindings).getBinding();
      assertEquals("ORDERS_V2", posted.getSource().getSource());
      assertEquals(1, posted.getRows().size(),
                   "ORDER_DATE still exists in ORDERS_V2 and must survive; REGION does not and " +
                   "must not");
      assertEquals("ORDER_DATE", posted.getRows().get(0).getColumnValue());
   }

   /**
    * The regression for the repair-review finding on the fix above: {@link #columnsOf} already
    * matches a qualified new-source column ({@code "table.attribute"}) against an unqualified
    * old field, but the reverse direction -- an old bound field whose own column name is
    * qualified (because ITS source was the joined/merged table) repointed at a new source whose
    * columns are unqualified -- went unhandled, so the field was discarded even though the same
    * repoint done by a human in the UI would keep it.
    */
   @Test
   void setSourceKeepsAQualifiedOldFieldThatResolvesUnqualifiedInTheNewSource() throws Exception {
      CrosstabBindingModel existing = withTablesAndColumns(
         Map.of("ORDERS", List.of("ORDERS.ORDER_DATE", "ORDERS.REGION"),
               "ORDERS_V2", List.of("ORDER_DATE")));
      TableBindingMutator.setShelf(existing, "rows",
                                   List.of(dim("ORDERS.ORDER_DATE"), dim("ORDERS.REGION")));
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      harness(mock(CrosstabVSAssembly.class), existing, bindings)
         .setSource("tok", principal(), "Crosstab1", "ORDERS_V2", true);

      CrosstabBindingModel posted = (CrosstabBindingModel) capture(bindings).getBinding();
      assertEquals(1, posted.getRows().size(),
                   "ORDERS.ORDER_DATE resolves against ORDERS_V2's unqualified ORDER_DATE and " +
                   "must survive; ORDERS.REGION does not and must not");
      assertEquals("ORDERS.ORDER_DATE", posted.getRows().get(0).getColumnValue());
   }

   /**
    * force:true against the source the assembly already has is a no-op repoint, not a real
    * source change — nothing to discard, and doing so anyway would destroy a binding the caller
    * never asked to touch.
    */
   @Test
   void setSourceForcedButUnchangedKeepsFields() throws Exception {
      CrosstabBindingModel existing = withTables("ORDERS", "CUSTOMERS");
      existing.setSource(new inetsoft.web.binding.model.SourceInfo());
      existing.getSource().setSource("ORDERS");
      TableBindingMutator.setShelf(existing, "rows", List.of(dim("STATE")));
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      harness(mock(CrosstabVSAssembly.class), existing, bindings)
         .setSource("tok", principal(), "Crosstab1", "ORDERS", true);

      CrosstabBindingModel posted = (CrosstabBindingModel) capture(bindings).getBinding();
      assertEquals(1, posted.getRows().size(),
                    "the source didn't actually change, so nothing should be discarded");
   }

   /**
    * A calc table has a source like any other data assembly — {@code CalcTableVSAssembly} extends
    * {@code TableDataVSAssembly} and {@code CalcTableBindingModel} extends
    * {@code BaseTableBindingModel}. The blanket calc-table refusal exists because *shelves* do not
    * apply to it, but assigning a source is not a shelf operation, and without this a freehand
    * table can never be pointed at data: its cells bind, and it renders empty forever.
    */
   @Test
   void setSourceAcceptsACalcTableBecauseSourceIsNotAShelfOperation() throws Exception {
      CalcTableBindingModel existing = new CalcTableBindingModel();
      List<BindingModel.SourceTable> tables = new ArrayList<>();
      BindingModel.SourceTable table = new BindingModel.SourceTable();
      table.setName("ORDERS");
      tables.add(table);
      existing.setTables(tables);
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      harness(mock(CalcTableVSAssembly.class), existing, bindings)
         .setSource("tok", principal(), "Calc1", "ORDERS", false);

      BaseTableBindingModel posted = (BaseTableBindingModel) capture(bindings).getBinding();
      assertEquals("ORDERS", posted.getSource().getSource());
   }

   @Test
   void shelfWritesStillRefuseACalcTable() {
      TableBindingService service = serviceWith(
         sessionsFor(mock(CalcTableVSAssembly.class)), new CalcTableBindingModel(),
         mock(VSBindingModelService.class));

      Exception thrown = assertThrows(
         Exception.class,
         () -> service.setShelf("tok", principal(), "Calc1", "rows", List.of(dim("Region")),
                                null));
      assertTrue(thrown.getMessage().contains("cell layout"));
   }

   // ── no-source refusal ─────────────────────────────────────────────────────
   //
   // set_table_fields/add_table_field must refuse a non-empty shelf write on an assembly with
   // no source, rather than silently applying it and rendering nothing — see setSource's javadoc
   // above for why nothing else here catches this.

   @Test
   void setShelfRefusesWhenAssemblyHasNoSource() {
      CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
      when(assembly.getSourceInfo()).thenReturn(null);
      TableBindingService service =
         serviceWith(sessionsFor(assembly), new CrosstabBindingModel(),
                     mock(VSBindingModelService.class));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> service.setShelf("tok", principal(), "Crosstab1", "rows", List.of(dim("Region")),
                                null));
      assertTrue(thrown.getMessage().contains("Crosstab1"));
      assertTrue(thrown.getMessage().contains("set_table_source"));
   }

   @Test
   void setShelfClearingAnEmptyShelfIsNotRefusedEvenWithNoSource() throws Exception {
      CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
      when(assembly.getSourceInfo()).thenReturn(null);
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      harness(assembly, new CrosstabBindingModel(), bindings)
         .setShelf("tok", principal(), "Crosstab1", "rows", List.of(), null);

      assertEquals(0,
         ((CrosstabBindingModel) capture(bindings).getBinding()).getRows().size());
   }

   @Test
   void setShelfProceedsWhenAssemblyHasASource() throws Exception {
      CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
      when(assembly.getSourceInfo()).thenReturn(new inetsoft.uql.asset.SourceInfo());
      CrosstabBindingModel existing = new CrosstabBindingModel();
      existing.setSource(new inetsoft.web.binding.model.SourceInfo());
      existing.getSource().setSource("ORDERS");
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      harness(assembly, existing, bindings)
         .setShelf("tok", principal(), "Crosstab1", "rows", List.of(dim("Region")), null);

      assertEquals(1,
         ((CrosstabBindingModel) capture(bindings).getBinding()).getRows().size());
   }

   @Test
   void addFieldRefusesWhenAssemblyHasNoSource() {
      CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
      when(assembly.getSourceInfo()).thenReturn(null);
      TableBindingService service =
         serviceWith(sessionsFor(assembly), new CrosstabBindingModel(),
                     mock(VSBindingModelService.class));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> service.addField("tok", principal(), "Crosstab1", "rows", dim("Region"), null,
                                null));
      assertTrue(thrown.getMessage().contains("Crosstab1"));
      assertTrue(thrown.getMessage().contains("set_table_source"));
   }

   @Test
   void addFieldProceedsWhenAssemblyHasASource() throws Exception {
      CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
      when(assembly.getSourceInfo()).thenReturn(new inetsoft.uql.asset.SourceInfo());
      CrosstabBindingModel existing = new CrosstabBindingModel();
      existing.setSource(new inetsoft.web.binding.model.SourceInfo());
      existing.getSource().setSource("ORDERS");
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      harness(assembly, existing, bindings)
         .addField("tok", principal(), "Crosstab1", "rows", dim("Region"), null, null);

      assertEquals(1,
         ((CrosstabBindingModel) capture(bindings).getBinding()).getRows().size());
   }

   /**
    * The guard is scoped to {@link TableBindingService#setShelf}/{@link
    * TableBindingService#addField}'s own call sites, not pushed into {@code applyWithContext}
    * itself — {@link TableBindingService#moveField} shares that plumbing and must not be refused
    * on a sourceless assembly, since by construction a sourceless assembly's shelves can never
    * have anything on them to move once setShelf/addField are guarded.
    */
   @Test
   void moveFieldIsNotRefusedEvenWithNoSource() throws Exception {
      CrosstabBindingModel existing = new CrosstabBindingModel();
      TableBindingMutator.setShelf(existing, "rows", List.of(dim("Region"), dim("Year")));
      CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
      when(assembly.getSourceInfo()).thenReturn(null);
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      harness(assembly, existing, bindings)
         .moveField("tok", principal(), "Crosstab1", "rows", "cols", "Year", null);

      CrosstabBindingModel posted = (CrosstabBindingModel) capture(bindings).getBinding();
      assertEquals(1, posted.getRows().size());
      assertEquals(1, posted.getCols().size());
   }

   // ── bug #76574, VTB-007: percentageBy silently, permanently defaulted to "col" by any wiz
   // write, not just set_table_options -- CrosstabOptionInfo(CrosstabVSAssembly)'s constructor
   // coalesces a never-set percentageByValue to "1" (col) purely for display, but
   // VSCrosstabBindingFactory.updateAssembly() persists whatever the model's option carries
   // onto the live assembly unconditionally on every write. The model handed to
   // TableBindingService here stands in for that already-coalesced-to-"1" snapshot
   // (binding.createModel(assembly) is mocked below, so it is not built via the real
   // constructor) -- what these assert is that a write that never asked to change
   // percentageBy resets that manufactured value back to null before it reaches
   // bindingModelService.setBinding, as long as the live assembly's own percentageByValue was
   // still null.

   @Test
   void setShelfDoesNotPersistTheManufacturedPercentageByDefault() throws Exception {
      CrosstabBindingModel existing = withTables("ORDERS");
      existing.setSource(new inetsoft.web.binding.model.SourceInfo());
      existing.getSource().setSource("ORDERS");
      CrosstabOptionInfo manufactured = new CrosstabOptionInfo();
      manufactured.setPercentageByValue("1"); // stand-in for the constructor's null-to-"1" coalesce
      existing.setOption(manufactured);
      VSBindingModelService bindings = mock(VSBindingModelService.class);
      CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
      when(assembly.getSourceInfo()).thenReturn(new inetsoft.uql.asset.SourceInfo());
      when(assembly.getVSCrosstabInfo()).thenReturn(new VSCrosstabInfo());

      harness(assembly, existing, bindings)
         .setShelf("tok", principal(), "Crosstab1", "aggregates",
                  List.of(new FieldRef("REVENUE", "measure", "Sum", null, null)), null);

      CrosstabBindingModel posted = (CrosstabBindingModel) capture(bindings).getBinding();
      assertNull(posted.getOption().getPercentageByValue(),
                "a shelf write that never mentioned percentageBy must not persist the " +
                "manufactured display default -- the live crosstab's own percentageBy was " +
                "never actually set");
   }

   @Test
   void setOptionsStillAppliesAnExplicitPercentageByChange() throws Exception {
      CrosstabBindingModel existing = withTables("ORDERS");
      existing.setSource(new inetsoft.web.binding.model.SourceInfo());
      existing.getSource().setSource("ORDERS");
      TableBindingMutator.setShelf(existing, "rows", List.of(dim("Region")));
      CrosstabOptionInfo manufactured = new CrosstabOptionInfo();
      manufactured.setPercentageByValue("1");
      existing.setOption(manufactured);
      VSBindingModelService bindings = mock(VSBindingModelService.class);
      CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
      when(assembly.getVSCrosstabInfo()).thenReturn(new VSCrosstabInfo());

      harness(assembly, existing, bindings)
         .setOptions("tok", principal(), "Crosstab1", Map.of("percentageBy", "row"));

      CrosstabBindingModel posted = (CrosstabBindingModel) capture(bindings).getBinding();
      assertEquals("2", posted.getOption().getPercentageByValue(),
                   "an explicit set_table_options(percentageBy: ...) call must still take " +
                   "effect -- this fix only stops an untouched default from being persisted, " +
                   "not a genuine, caller-requested change");
   }

   /**
    * The reset gate must key on whether <em>this call's</em> {@code options} map actually
    * contains {@code "percentageBy"}, not on which method was invoked -- {@code
    * TableBindingMutator#setCrosstabOptions} only calls {@code setPercentageByValue} when that
    * key is present, so a {@code set_table_options} call that touches only e.g. {@code
    * rowTotals} must still have the manufactured default reset, or it reopens bug #76574 through
    * a narrower, easily-reachable trigger.
    */
   @Test
   void setOptionsWithoutPercentageByStillResetsTheManufacturedDefault() throws Exception {
      CrosstabBindingModel existing = withTables("ORDERS");
      existing.setSource(new inetsoft.web.binding.model.SourceInfo());
      existing.getSource().setSource("ORDERS");
      TableBindingMutator.setShelf(existing, "rows", List.of(dim("Region")));
      CrosstabOptionInfo manufactured = new CrosstabOptionInfo();
      manufactured.setPercentageByValue("1"); // stand-in for the constructor's null-to-"1" coalesce
      existing.setOption(manufactured);
      VSBindingModelService bindings = mock(VSBindingModelService.class);
      CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
      when(assembly.getVSCrosstabInfo()).thenReturn(new VSCrosstabInfo());

      harness(assembly, existing, bindings)
         .setOptions("tok", principal(), "Crosstab1", Map.of("rowTotals", true));

      CrosstabBindingModel posted = (CrosstabBindingModel) capture(bindings).getBinding();
      assertNull(posted.getOption().getPercentageByValue(),
                "a set_table_options call that never mentioned percentageBy must not persist " +
                "the manufactured display default -- the live crosstab's own percentageBy was " +
                "never actually set");
   }

   // ── set_field_visibility (bug-76807) ─────────────────────────────────────

   /** A ColumnSelection holding one bare-named DataRef per given column name. */
   private static inetsoft.uql.ColumnSelection columnSelectionOf(String... names) {
      inetsoft.uql.ColumnSelection selection = new inetsoft.uql.ColumnSelection();

      for(String name : names) {
         selection.addAttribute(new inetsoft.uql.erm.AttributeRef(null, name));
      }

      return selection;
   }

   private static inetsoft.web.composer.vs.dialog.HideColumnsDialogService hideColumnsServiceReturning(
      String runtimeId, String assemblyName, List<String> available, List<String> hidden)
      throws Exception
   {
      inetsoft.web.composer.vs.dialog.HideColumnsDialogService hideColumnsService =
         mock(inetsoft.web.composer.vs.dialog.HideColumnsDialogService.class);
      when(hideColumnsService.getColumnOptionDialogModel(
         eq(runtimeId), eq(assemblyName), any(Principal.class)))
         .thenReturn(inetsoft.web.composer.model.vs.HideColumnsDialogModel.builder()
                        .availableColumns(available).hiddenColumns(hidden).build());
      return hideColumnsService;
   }

   private static inetsoft.web.composer.model.vs.HideColumnsDialogModel captureHideColumnsWrite(
      inetsoft.web.composer.vs.dialog.HideColumnsDialogService hideColumnsService,
      String runtimeId, String assemblyName)
      throws Exception
   {
      ArgumentCaptor<inetsoft.web.composer.model.vs.HideColumnsDialogModel> captor =
         ArgumentCaptor.forClass(inetsoft.web.composer.model.vs.HideColumnsDialogModel.class);
      verify(hideColumnsService).setColumnOptionDialogModel(
         eq(runtimeId), eq(assemblyName), captor.capture(), any(Principal.class), any(), any());
      return captor.getValue();
   }

   @Test
   void setFieldVisibilityHidesABoundColumn() throws Exception {
      inetsoft.web.composer.vs.dialog.HideColumnsDialogService hideColumnsService =
         hideColumnsServiceReturning("rt1", "TableView2",
                                     List.of("product_id", "product_name"), List.of());
      ViewsheetSessionService sessions = sessionsFor(mock(TableVSAssembly.class));

      serviceWith(sessions, new TableBindingModel(), mock(VSBindingModelService.class),
                 hideColumnsService)
         .setFieldVisibility("tok", principal(), "TableView2", "product_id", false, null);

      inetsoft.web.composer.model.vs.HideColumnsDialogModel posted =
         captureHideColumnsWrite(hideColumnsService, "rt1", "TableView2");
      assertEquals(List.of("product_id"), posted.hiddenColumns());
      assertEquals(List.of("product_name"), posted.availableColumns());
   }

   @Test
   void setFieldVisibilityShowsAPreviouslyHiddenColumn() throws Exception {
      inetsoft.web.composer.vs.dialog.HideColumnsDialogService hideColumnsService =
         hideColumnsServiceReturning("rt1", "TableView2",
                                     List.of("product_name"), List.of("product_id"));
      ViewsheetSessionService sessions = sessionsFor(mock(TableVSAssembly.class));

      serviceWith(sessions, new TableBindingModel(), mock(VSBindingModelService.class),
                 hideColumnsService)
         .setFieldVisibility("tok", principal(), "TableView2", "product_id", true, null);

      inetsoft.web.composer.model.vs.HideColumnsDialogModel posted =
         captureHideColumnsWrite(hideColumnsService, "rt1", "TableView2");
      assertEquals(List.of(), posted.hiddenColumns());
      assertEquals(List.of("product_name", "product_id"), posted.availableColumns());
   }

   @Test
   void setFieldVisibilityRefusesAnUnboundColumn() throws Exception {
      inetsoft.web.composer.vs.dialog.HideColumnsDialogService hideColumnsService =
         hideColumnsServiceReturning("rt1", "TableView2", List.of("product_name"), List.of());
      ViewsheetSessionService sessions = sessionsFor(mock(TableVSAssembly.class));
      TableBindingService service = serviceWith(
         sessions, new TableBindingModel(), mock(VSBindingModelService.class), hideColumnsService);

      Exception thrown = assertThrows(Exception.class,
         () -> service.setFieldVisibility("tok", principal(), "TableView2", "ghost_col", false,
                                          null));

      assertTrue(thrown.getMessage().contains("ghost_col"));
      assertTrue(thrown.getMessage().contains("not bound"));
      verify(hideColumnsService, never()).setColumnOptionDialogModel(
         anyString(), anyString(), any(), any(Principal.class), any(), any());
   }

   @Test
   void setFieldVisibilityRefusesACrosstabByName() {
      inetsoft.web.composer.vs.dialog.HideColumnsDialogService hideColumnsService =
         mock(inetsoft.web.composer.vs.dialog.HideColumnsDialogService.class);
      ViewsheetSessionService sessions = sessionsFor(mock(CrosstabVSAssembly.class));
      TableBindingService service = serviceWith(
         sessions, new CrosstabBindingModel(), mock(VSBindingModelService.class),
         hideColumnsService);

      Exception thrown = assertThrows(Exception.class,
         () -> service.setFieldVisibility("tok", principal(), "Crosstab1", "Year", false, null));

      assertTrue(thrown.getMessage().contains("Crosstab1"));
      assertTrue(thrown.getMessage().toLowerCase().contains("crosstab"));
   }

   // ── set_crosstab_column_visibility (bug #76869, VTB-030) ───────────────────

   /**
    * Hide resolves a bare column name -- the ordinary, unambiguous case -- all the way down to a
    * real rendered lens column index, and {@code CrosstabVSAssemblyInfo.addHiddenColumn} is
    * called with that real index, not a stand-in for the raw shelf position. "Region" (shelf
    * position 0) stays untouched; only "State" (shelf position 1, rendered at lens column 1) is
    * hidden.
    */
   @Test
   void setCrosstabColumnVisibilityHidesAColumnByNameAtItsRealLensIndex() throws Exception {
      CrosstabBindingModel existing = new CrosstabBindingModel();
      TableBindingMutator.setShelf(existing, "rows", List.of(dim("Region"), dim("State")));

      VSDimensionRef liveRegion = new VSDimensionRef();
      liveRegion.setGroupColumnValue("Region");
      VSDimensionRef liveState = new VSDimensionRef();
      liveState.setGroupColumnValue("State");
      VSCrosstabInfo crossInfo = new VSCrosstabInfo();
      crossInfo.setDesignRowHeaders(new inetsoft.uql.erm.DataRef[]{ liveRegion, liveState });

      inetsoft.uql.viewsheet.internal.CrosstabVSAssemblyInfo tableInfo =
         new inetsoft.uql.viewsheet.internal.CrosstabVSAssemblyInfo();
      CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
      when(assembly.getVSCrosstabInfo()).thenReturn(crossInfo);
      when(assembly.getCrosstabInfo()).thenReturn(tableInfo);
      when(assembly.getAbsoluteName()).thenReturn("Crosstab1");

      inetsoft.report.composition.VSTableLens lens =
         mock(inetsoft.report.composition.VSTableLens.class);
      when(lens.getHeaderRowCount()).thenReturn(1);
      when(lens.getHeaderColCount()).thenReturn(2);
      when(lens.getColCount()).thenReturn(2);
      when(lens.getRowCount()).thenReturn(1);
      inetsoft.report.TableDataPath col0 = new inetsoft.report.TableDataPath(
         -1, inetsoft.report.TableDataPath.HEADER, inetsoft.uql.schema.XSchema.STRING,
         new String[]{ "Cell [0,0]" });
      inetsoft.report.TableDataPath col1 = new inetsoft.report.TableDataPath(
         -1, inetsoft.report.TableDataPath.HEADER, inetsoft.uql.schema.XSchema.STRING,
         new String[]{ "Cell [0,1]" });
      when(lens.getTableDataPath(0, 0)).thenReturn(col0);
      when(lens.getTableDataPath(0, 1)).thenReturn(col1);

      // CrosstabVSAssemblyInfo.getColKey (addHiddenColumn/isColumnHidden's own key) reads the
      // lens's *descriptor* independently of getTableDataPath -- a real CrosstabVSAssemblyInfo is
      // used here (rather than mocking addHiddenColumn/isColumnHidden away) specifically so this
      // test proves the real hidden-column bookkeeping, so this needs its own stub too.
      inetsoft.report.TableDataDescriptor descriptor = mock(inetsoft.report.TableDataDescriptor.class);
      when(lens.getDescriptor()).thenReturn(descriptor);
      when(descriptor.getColDataPath(0)).thenReturn(col0);
      when(descriptor.getColDataPath(1)).thenReturn(col1);

      inetsoft.report.composition.execution.ViewsheetSandbox sandbox =
         mock(inetsoft.report.composition.execution.ViewsheetSandbox.class);
      when(sandbox.getVSTableLens("Crosstab1", false)).thenReturn(lens);

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenReturn(assembly);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(java.util.Optional.of(sandbox));

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      doAnswer(invocation -> {
         ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
         mutation.run(rvs, "rt1", null);
         return null;
      }).when(sessions).mutate(anyString(), any(Principal.class), any());

      VSBindingModelService bindings = mock(VSBindingModelService.class);
      List<String> applied = serviceWith(sessions, existing, bindings)
         .setCrosstabColumnVisibility(
            "tok", principal(), "Crosstab1", List.of("State"), null, false);

      assertTrue(tableInfo.isColumnHidden(1, lens), "the resolved column (lens index 1) must be hidden");
      assertFalse(tableInfo.isColumnHidden(0, lens), "an untouched sibling column must stay visible");
      assertEquals(List.of("State -> hidden"), applied);
   }

   /**
    * Show is unconditional and takes no column list -- matching the native Composer's own
    * Hide/Show action, which has no selective per-column show either.
    */
   @Test
   void setCrosstabColumnVisibilityShowClearsEveryHiddenColumn() throws Exception {
      inetsoft.uql.viewsheet.internal.CrosstabVSAssemblyInfo tableInfo =
         new inetsoft.uql.viewsheet.internal.CrosstabVSAssemblyInfo();
      inetsoft.report.composition.VSTableLens lens =
         mock(inetsoft.report.composition.VSTableLens.class);
      when(lens.getHeaderRowCount()).thenReturn(0);
      when(lens.getHeaderColCount()).thenReturn(1);
      inetsoft.report.TableDataDescriptor descriptor = mock(inetsoft.report.TableDataDescriptor.class);
      when(lens.getDescriptor()).thenReturn(descriptor);
      when(descriptor.getColDataPath(0)).thenReturn(new inetsoft.report.TableDataPath(
         -1, inetsoft.report.TableDataPath.HEADER, inetsoft.uql.schema.XSchema.STRING,
         new String[]{ "Cell [0,0]" }));
      tableInfo.addHiddenColumn(0, lens);
      assertTrue(tableInfo.hasHiddenColumn(), "test setup must actually start with a hidden column");

      CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
      when(assembly.getCrosstabInfo()).thenReturn(tableInfo);

      VSBindingModelService bindings = mock(VSBindingModelService.class);
      List<String> applied = harness(assembly, new CrosstabBindingModel(), bindings)
         .setCrosstabColumnVisibility("tok", principal(), "Crosstab1", null, null, true);

      assertFalse(tableInfo.hasHiddenColumn());
      assertEquals(List.of("shown every hidden column"), applied);
   }

   @Test
   void setCrosstabColumnVisibilityRefusesShowWithAColumnList() {
      TableBindingService service = serviceWith(
         mock(ViewsheetSessionService.class), new CrosstabBindingModel(),
         mock(VSBindingModelService.class));

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.setCrosstabColumnVisibility(
            "tok", principal(), "Crosstab1", List.of("Region"), null, true));

      assertTrue(thrown.getMessage().contains("no selective per-column show"));
   }

   @Test
   void setCrosstabColumnVisibilityRefusesHideWithNoColumns() {
      TableBindingService service = serviceWith(
         mock(ViewsheetSessionService.class), new CrosstabBindingModel(),
         mock(VSBindingModelService.class));

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.setCrosstabColumnVisibility(
            "tok", principal(), "Crosstab1", null, null, false));

      assertTrue(thrown.getMessage().contains("at least one column to hide"));
   }

   @Test
   void setCrosstabColumnVisibilityRefusesATableByName() {
      ViewsheetSessionService sessions = sessionsFor(mock(TableVSAssembly.class));
      TableBindingService service = serviceWith(
         sessions, new TableBindingModel(), mock(VSBindingModelService.class));

      Exception thrown = assertThrows(Exception.class,
         () -> service.setCrosstabColumnVisibility(
            "tok", principal(), "Table1", List.of("Region"), null, false));

      assertTrue(thrown.getMessage().contains("Table1"));
      assertTrue(thrown.getMessage().toLowerCase().contains("crosstab"));
   }

   /**
    * Read side of bug #76869 (VTB-030): {@code get_table_binding}'s {@code visible} was
    * unconditionally {@code null} for every Crosstab shelf entry before this, hidden or not (see
    * {@code enrichCrosstabLabelsAndVisibility}, formerly {@code enrichCrosstabLabels}, which
    * previously never touched {@code visible} at all). "Region" (lens column 0, not hidden) and
    * "State" (lens column 1, hidden) share the same live/lens fixture the write-side hide test
    * above uses, so both directions of this bug are proven against the same rendered shape.
    */
   @Test
   void readReportsCrosstabColumnVisibilityFromHiddenColumns() throws Exception {
      CrosstabBindingModel existing = new CrosstabBindingModel();
      TableBindingMutator.setShelf(existing, "rows", List.of(dim("Region"), dim("State")));

      VSDimensionRef liveRegion = new VSDimensionRef();
      liveRegion.setGroupColumnValue("Region");
      VSDimensionRef liveState = new VSDimensionRef();
      liveState.setGroupColumnValue("State");
      VSCrosstabInfo crossInfo = new VSCrosstabInfo();
      crossInfo.setDesignRowHeaders(new inetsoft.uql.erm.DataRef[]{ liveRegion, liveState });

      inetsoft.uql.viewsheet.internal.CrosstabVSAssemblyInfo tableInfo =
         new inetsoft.uql.viewsheet.internal.CrosstabVSAssemblyInfo();
      CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
      when(assembly.getVSCrosstabInfo()).thenReturn(crossInfo);
      when(assembly.getCrosstabInfo()).thenReturn(tableInfo);
      when(assembly.getAbsoluteName()).thenReturn("Crosstab1");
      when(assembly.getFormatInfo()).thenReturn(new inetsoft.uql.viewsheet.FormatInfo());

      inetsoft.report.composition.VSTableLens lens =
         mock(inetsoft.report.composition.VSTableLens.class);
      when(lens.getHeaderRowCount()).thenReturn(1);
      when(lens.getHeaderColCount()).thenReturn(2);
      when(lens.getColCount()).thenReturn(2);
      when(lens.getRowCount()).thenReturn(1);
      inetsoft.report.TableDataPath col0 = new inetsoft.report.TableDataPath(
         -1, inetsoft.report.TableDataPath.HEADER, inetsoft.uql.schema.XSchema.STRING,
         new String[]{ "Cell [0,0]" });
      inetsoft.report.TableDataPath col1 = new inetsoft.report.TableDataPath(
         -1, inetsoft.report.TableDataPath.HEADER, inetsoft.uql.schema.XSchema.STRING,
         new String[]{ "Cell [0,1]" });
      when(lens.getTableDataPath(0, 0)).thenReturn(col0);
      when(lens.getTableDataPath(0, 1)).thenReturn(col1);
      inetsoft.report.TableDataDescriptor descriptor = mock(inetsoft.report.TableDataDescriptor.class);
      when(lens.getDescriptor()).thenReturn(descriptor);
      when(descriptor.getColDataPath(0)).thenReturn(col0);
      when(descriptor.getColDataPath(1)).thenReturn(col1);

      tableInfo.addHiddenColumn(1, lens); // "State" (lens column 1) starts already hidden

      inetsoft.report.composition.execution.ViewsheetSandbox sandbox =
         mock(inetsoft.report.composition.execution.ViewsheetSandbox.class);
      when(sandbox.getVSTableLens("Crosstab1", false)).thenReturn(lens);

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenReturn(assembly);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(java.util.Optional.of(sandbox));

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);

      Map<String, Object> read =
         serviceWith(sessions, existing, mock(VSBindingModelService.class))
            .read("tok", principal(), "Crosstab1");

      @SuppressWarnings("unchecked")
      Map<String, List<FieldRef>> shelves =
         (Map<String, List<FieldRef>>) (Map<String, ?>) read.get("shelves");
      List<FieldRef> rows = shelves.get("rows");
      assertEquals(Boolean.TRUE, rows.get(0).visible(), "Region is not hidden");
      assertEquals(Boolean.FALSE, rows.get(1).visible(), "State is hidden");
   }

   // ── bug #76882: crosstab cols-shelf label/visibility resolution ────────────

   /**
    * Regression test for bug #76882 (VTB-024 incidental finding): with exactly one row
    * dimension and one column dimension, the corner box {@code findHeaderCell}'s {@code
    * "Cell [row,col]"} positional match resolves against collapses to a single cell ({@code
    * Cell [0,0]}) -- the row dimension's own title cell. Before the fix,
    * {@code enrichCrosstabLabelsAndVisibility} iterated {@code cols} through this same
    * mechanism and spuriously matched that row-dimension corner cell, leaking "Region"'s own
    * {@code MESSAGE_FORMAT} alias onto the untouched "Category" column-dimension shelf entry.
    * The fix removes {@code cols} from that shelf loop entirely; this proves the row dimension's
    * real label still reads correctly while the column dimension's stays {@code null} instead of
    * leaking.
    */
   @Test
   void readDoesNotLeakARowLabelOntoAColsShelfEntry() throws Exception {
      CrosstabBindingModel existing = new CrosstabBindingModel();
      TableBindingMutator.setShelf(existing, "rows", List.of(dim("Region")));
      TableBindingMutator.setShelf(existing, "cols", List.of(dim("Category")));

      VSDimensionRef liveRegion = new VSDimensionRef();
      liveRegion.setGroupColumnValue("Region");
      VSDimensionRef liveCategory = new VSDimensionRef();
      liveCategory.setGroupColumnValue("Category");
      VSCrosstabInfo crossInfo = new VSCrosstabInfo();
      crossInfo.setDesignRowHeaders(new inetsoft.uql.erm.DataRef[]{ liveRegion });
      crossInfo.setDesignColHeaders(new inetsoft.uql.erm.DataRef[]{ liveCategory });

      inetsoft.uql.viewsheet.FormatInfo formatInfo = new inetsoft.uql.viewsheet.FormatInfo();
      CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
      when(assembly.getVSCrosstabInfo()).thenReturn(crossInfo);
      when(assembly.getCrosstabInfo())
         .thenReturn(new inetsoft.uql.viewsheet.internal.CrosstabVSAssemblyInfo());
      when(assembly.getAbsoluteName()).thenReturn("Crosstab1");
      when(assembly.getFormatInfo()).thenReturn(formatInfo);

      // A crosstab with exactly one row dimension and one column dimension: the corner box is a
      // single cell, so the row dimension's own title cell is "Cell [0,0]" -- already relabeled,
      // simulating a prior set_column_labels(rows[0], "Region VTB024") call.
      inetsoft.report.composition.VSTableLens lens =
         mock(inetsoft.report.composition.VSTableLens.class);
      when(lens.getHeaderRowCount()).thenReturn(1);
      when(lens.getHeaderColCount()).thenReturn(1);
      when(lens.getColCount()).thenReturn(2);
      when(lens.getRowCount()).thenReturn(1);
      inetsoft.report.TableDataPath corner = new inetsoft.report.TableDataPath(
         -1, inetsoft.report.TableDataPath.HEADER, inetsoft.uql.schema.XSchema.STRING,
         new String[]{ "Cell [0,0]" });
      when(lens.getTableDataPath(0, 0)).thenReturn(corner);
      SetTableHeaderAliasHandler.setAlias(corner, formatInfo, "Region VTB024");

      inetsoft.report.composition.execution.ViewsheetSandbox sandbox =
         mock(inetsoft.report.composition.execution.ViewsheetSandbox.class);
      when(sandbox.getVSTableLens("Crosstab1", false)).thenReturn(lens);

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenReturn(assembly);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(java.util.Optional.of(sandbox));

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);

      Map<String, Object> read =
         serviceWith(sessions, existing, mock(VSBindingModelService.class))
            .read("tok", principal(), "Crosstab1");

      @SuppressWarnings("unchecked")
      Map<String, List<FieldRef>> shelves =
         (Map<String, List<FieldRef>>) (Map<String, ?>) read.get("shelves");
      assertEquals("Region VTB024", shelves.get("rows").get(0).label(),
                   "the row dimension's own relabel must still read back correctly");
      assertNull(shelves.get("cols").get(0).label(),
                 "the untouched column dimension must not inherit the row dimension's label");

      @SuppressWarnings("unchecked")
      Map<String, String> columnLabels = (Map<String, String>) read.get("columnLabels");
      assertEquals("Region VTB024", columnLabels.get("Region"));
      assertFalse(columnLabels.containsKey("Category"),
                  "'Category' must not appear in columnLabels with the leaked 'Region' label");
   }

   /**
    * Write-side counterpart of {@link #readDoesNotLeakARowLabelOntoAColsShelfEntry}: {@code
    * set_column_labels} must refuse a crosstab column-dimension target outright rather than
    * risk the identical collision on write (silently overwriting an unrelated row dimension's
    * real header alias).
    */
   @Test
   void setColumnLabelsRefusesACrosstabColsShelfTarget() throws Exception {
      CrosstabBindingModel existing = new CrosstabBindingModel();
      TableBindingMutator.setShelf(existing, "rows", List.of(dim("Region")));
      TableBindingMutator.setShelf(existing, "cols", List.of(dim("Category")));

      VSDimensionRef liveRegion = new VSDimensionRef();
      liveRegion.setGroupColumnValue("Region");
      VSDimensionRef liveCategory = new VSDimensionRef();
      liveCategory.setGroupColumnValue("Category");
      VSCrosstabInfo crossInfo = new VSCrosstabInfo();
      crossInfo.setDesignRowHeaders(new inetsoft.uql.erm.DataRef[]{ liveRegion });
      crossInfo.setDesignColHeaders(new inetsoft.uql.erm.DataRef[]{ liveCategory });

      inetsoft.uql.viewsheet.FormatInfo formatInfo = new inetsoft.uql.viewsheet.FormatInfo();
      CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
      when(assembly.getVSCrosstabInfo()).thenReturn(crossInfo);
      when(assembly.getAbsoluteName()).thenReturn("Crosstab1");
      when(assembly.getFormatInfo()).thenReturn(formatInfo);

      inetsoft.report.composition.VSTableLens lens =
         mock(inetsoft.report.composition.VSTableLens.class);
      when(lens.getHeaderRowCount()).thenReturn(1);
      when(lens.getHeaderColCount()).thenReturn(1);
      when(lens.getColCount()).thenReturn(2);
      when(lens.getRowCount()).thenReturn(1);
      inetsoft.report.TableDataPath corner = new inetsoft.report.TableDataPath(
         -1, inetsoft.report.TableDataPath.HEADER, inetsoft.uql.schema.XSchema.STRING,
         new String[]{ "Cell [0,0]" });
      when(lens.getTableDataPath(0, 0)).thenReturn(corner);

      inetsoft.report.composition.execution.ViewsheetSandbox sandbox =
         mock(inetsoft.report.composition.execution.ViewsheetSandbox.class);
      when(sandbox.getVSTableLens("Crosstab1", false)).thenReturn(lens);

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenReturn(assembly);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(java.util.Optional.of(sandbox));

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      doAnswer(invocation -> {
         ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
         mutation.run(rvs, "rt1", null);
         return null;
      }).when(sessions).mutate(anyString(), any(Principal.class), any());

      TableBindingService service =
         serviceWith(sessions, existing, mock(VSBindingModelService.class));

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.setColumnLabels(
            "tok", principal(), "Crosstab1", Map.of("Category", "Sales Category"), null));

      assertTrue(thrown.getMessage().contains("cols[0]"));
      assertNull(formatInfo.getFormat(corner),
                 "the refused write must not touch the row dimension's own corner cell");
   }

   /**
    * {@code set_crosstab_column_visibility}'s sibling of {@link
    * #setColumnLabelsRefusesACrosstabColsShelfTarget} -- same collision, same refusal.
    */
   @Test
   void setCrosstabColumnVisibilityRefusesAColsShelfTarget() throws Exception {
      CrosstabBindingModel existing = new CrosstabBindingModel();
      TableBindingMutator.setShelf(existing, "rows", List.of(dim("Region")));
      TableBindingMutator.setShelf(existing, "cols", List.of(dim("Category")));

      VSDimensionRef liveRegion = new VSDimensionRef();
      liveRegion.setGroupColumnValue("Region");
      VSDimensionRef liveCategory = new VSDimensionRef();
      liveCategory.setGroupColumnValue("Category");
      VSCrosstabInfo crossInfo = new VSCrosstabInfo();
      crossInfo.setDesignRowHeaders(new inetsoft.uql.erm.DataRef[]{ liveRegion });
      crossInfo.setDesignColHeaders(new inetsoft.uql.erm.DataRef[]{ liveCategory });

      inetsoft.uql.viewsheet.internal.CrosstabVSAssemblyInfo tableInfo =
         new inetsoft.uql.viewsheet.internal.CrosstabVSAssemblyInfo();
      CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
      when(assembly.getVSCrosstabInfo()).thenReturn(crossInfo);
      when(assembly.getCrosstabInfo()).thenReturn(tableInfo);
      when(assembly.getAbsoluteName()).thenReturn("Crosstab1");

      inetsoft.report.composition.VSTableLens lens =
         mock(inetsoft.report.composition.VSTableLens.class);
      when(lens.getHeaderRowCount()).thenReturn(1);
      when(lens.getHeaderColCount()).thenReturn(1);
      when(lens.getColCount()).thenReturn(2);
      when(lens.getRowCount()).thenReturn(1);
      when(lens.getTableDataPath(0, 0)).thenReturn(new inetsoft.report.TableDataPath(
         -1, inetsoft.report.TableDataPath.HEADER, inetsoft.uql.schema.XSchema.STRING,
         new String[]{ "Cell [0,0]" }));

      inetsoft.report.composition.execution.ViewsheetSandbox sandbox =
         mock(inetsoft.report.composition.execution.ViewsheetSandbox.class);
      when(sandbox.getVSTableLens("Crosstab1", false)).thenReturn(lens);

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenReturn(assembly);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(java.util.Optional.of(sandbox));

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      doAnswer(invocation -> {
         ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
         mutation.run(rvs, "rt1", null);
         return null;
      }).when(sessions).mutate(anyString(), any(Principal.class), any());

      TableBindingService service =
         serviceWith(sessions, existing, mock(VSBindingModelService.class));

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.setCrosstabColumnVisibility(
            "tok", principal(), "Crosstab1", List.of("Category"), null, false));

      assertTrue(thrown.getMessage().contains("cols[0]"));
      assertFalse(tableInfo.hasHiddenColumn(),
                  "the refused write must not hide the row dimension's own lens column");
   }

   // ── set_table_column_sort (bug-76806) ──────────────────────────────────────

   /**
    * Review round 1 (04-review-r1.md) found setColumnSort originally passed the assembly's
    * LIVE VSAssemblyInfo into VSAssemblyInfoHandler.apply() (mutated in place, same object),
    * which made AbstractVSAssembly.setVSAssemblyInfo()'s {@code this.info.copyInfo(info)} a
    * self-comparison -- TableDataVSAssemblyInfo.copyInputDataInfo()'s
    * {@code Tool.equalsContent(sinfo, tinfo.sinfo)} diff is always "equal" against itself, so
    * {@code VSAssembly.INPUT_DATA_CHANGED} was never set and the refresh a real column-header
    * click triggers silently didn't happen. The fix clones first (mirroring
    * HideColumnsDialogService.setColumnOptionDialogModel()'s already-correct pattern), which
    * means the mutation is staged on a clone until {@code apply()} commits it -- a genuinely
    * inert mocked handler (as every OTHER test in this file uses for
    * VSAssemblyInfoHandler) no longer observes any effect on {@code table.getSortInfo()} at
    * all, since nothing ever calls {@code assembly.setVSAssemblyInfo(...)}.
    *
    * <p>This stub performs {@code apply()}'s one essential commit step --
    * {@code assembly.setVSAssemblyInfo(info)}, exercising the REAL {@code copyInfo}/hint
    * mechanism through the real {@code TableVSAssembly} -- without any of {@code apply()}'s
    * refresh/dispatch side effects, so these tests can observe genuinely-committed state
    * across sequential calls the same way the real handler would, without needing
    * CoreLifecycleService/ParameterService/DataSourceRegistry wired up for every test.
    * {@link #setColumnSortProducesAGenuineInputDataChangedHint} below uses a fully real
    * {@link inetsoft.web.binding.handler.VSAssemblyInfoHandler} instead, since verifying the
    * hint value itself is the one thing this stub deliberately skips.
    */
   private static inetsoft.web.binding.handler.VSAssemblyInfoHandler committingAssemblyInfoHandler()
      throws Exception
   {
      inetsoft.web.binding.handler.VSAssemblyInfoHandler handler =
         mock(inetsoft.web.binding.handler.VSAssemblyInfoHandler.class);

      doAnswer(invocation -> {
         RuntimeViewsheet rvs = invocation.getArgument(0);
         inetsoft.uql.viewsheet.internal.VSAssemblyInfo info = invocation.getArgument(1);
         VSAssembly assembly = rvs.getViewsheet().getAssembly(info.getAbsoluteName2());

         if(assembly != null) {
            assembly.setVSAssemblyInfo(info);
         }

         return null;
      }).when(handler).apply(
         any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(),
         any(), any(), any(), any(), any());

      return handler;
   }

   private static TableBindingService serviceWithCommittingHandler(
      ViewsheetSessionService sessions, BindingModel model, VSBindingModelService bindings)
      throws Exception
   {
      VSBindingService binding = mock(VSBindingService.class);
      when(binding.createModel(any())).thenReturn(model);
      return new TableBindingService(
         sessions, binding, bindings,
         mock(inetsoft.web.binding.service.DataRefModelFactoryService.class),
         mock(inetsoft.web.composer.vs.dialog.HideColumnsDialogService.class),
         committingAssemblyInfoHandler());
   }

   @Test
   void setColumnSortSetsAnExplicitAscendingOrder() throws Exception {
      TableVSAssembly table = tableWithColumns("TableView2", "product_id", "product_name");
      ViewsheetSessionService sessions = sessionsFor(table);
      TableBindingService service = serviceWithCommittingHandler(
         sessions, new TableBindingModel(), mock(VSBindingModelService.class));

      service.setColumnSort("tok", principal(), "TableView2", "product_id", "asc", null);

      assertEquals(Integer.valueOf(inetsoft.report.StyleConstants.SORT_ASC),
                  orderOf(table.getSortInfo(), "product_id"));
   }

   @Test
   void setColumnSortAcceptsCaseInsensitiveDirectionAndColumnName() throws Exception {
      TableVSAssembly table = tableWithColumns("TableView2", "product_id");
      ViewsheetSessionService sessions = sessionsFor(table);
      TableBindingService service = serviceWithCommittingHandler(
         sessions, new TableBindingModel(), mock(VSBindingModelService.class));

      service.setColumnSort("tok", principal(), "TableView2", "PRODUCT_ID", "DESC", null);

      assertEquals(Integer.valueOf(inetsoft.report.StyleConstants.SORT_DESC),
                  orderOf(table.getSortInfo(), "product_id"));
   }

   @Test
   void setColumnSortReplacesAPreviousDirectionOnTheSameColumn() throws Exception {
      TableVSAssembly table = tableWithColumns("TableView2", "product_id");
      ViewsheetSessionService sessions = sessionsFor(table);
      TableBindingService service = serviceWithCommittingHandler(
         sessions, new TableBindingModel(), mock(VSBindingModelService.class));

      service.setColumnSort("tok", principal(), "TableView2", "product_id", "asc", null);
      service.setColumnSort("tok", principal(), "TableView2", "product_id", "desc", null);

      assertEquals(1, table.getSortInfo().getSortCount(),
                  "the second call replaces, rather than duplicates, the first call's entry");
      assertEquals(Integer.valueOf(inetsoft.report.StyleConstants.SORT_DESC),
                  orderOf(table.getSortInfo(), "product_id"));
   }

   @Test
   void setColumnSortNoneRemovesTheColumnsSort() throws Exception {
      TableVSAssembly table = tableWithColumns("TableView2", "product_id");
      ViewsheetSessionService sessions = sessionsFor(table);
      TableBindingService service = serviceWithCommittingHandler(
         sessions, new TableBindingModel(), mock(VSBindingModelService.class));

      service.setColumnSort("tok", principal(), "TableView2", "product_id", "asc", null);
      service.setColumnSort("tok", principal(), "TableView2", "product_id", "none", null);

      assertEquals(0, table.getSortInfo().getSortCount());
   }

   /**
    * The additive-vs-exclusive decision this fix makes explicit (see
    * {@link TableBindingService#setColumnSort}'s javadoc): sorting column B must not discard
    * column A's already-set sort, unlike a plain (non-shift) column-header click's own default
    * ({@code BaseTableSortColumnService.tableSortColumn}'s
    * {@code if(sinfo == null || !event.multi())} branch). This is the only way a sequence of
    * single-column tool calls can reproduce a Viewer shift-click's multi-column sort.
    */
   @Test
   void setColumnSortIsAdditiveAcrossDifferentColumns() throws Exception {
      TableVSAssembly table = tableWithColumns("TableView2", "product_id", "product_name");
      ViewsheetSessionService sessions = sessionsFor(table);
      TableBindingService service = serviceWithCommittingHandler(
         sessions, new TableBindingModel(), mock(VSBindingModelService.class));

      service.setColumnSort("tok", principal(), "TableView2", "product_id", "asc", null);
      service.setColumnSort("tok", principal(), "TableView2", "product_name", "desc", null);

      assertEquals(2, table.getSortInfo().getSortCount(),
                  "sorting product_name must not discard product_id's earlier sort");
      assertEquals(Integer.valueOf(inetsoft.report.StyleConstants.SORT_ASC),
                  orderOf(table.getSortInfo(), "product_id"));
      assertEquals(Integer.valueOf(inetsoft.report.StyleConstants.SORT_DESC),
                  orderOf(table.getSortInfo(), "product_name"));
   }

   /**
    * Directly proves review round 1's blocker is fixed: uses a fully REAL
    * {@link inetsoft.web.binding.handler.VSAssemblyInfoHandler} (its own external
    * collaborators mocked) instead of the inert/committing stubs the other tests in this
    * block use, so {@code copyInfo}'s hint computation genuinely runs end to end, and asserts
    * the hint reaching {@code CoreLifecycleService.execute(...)} actually carries
    * {@code VSAssembly.INPUT_DATA_CHANGED} -- the exact bit the review traced as silently
    * unset by the pre-fix (live-object, no-clone) code, where a self-comparison against the
    * assembly's own already-mutated field always computed {@code NONE_CHANGED} instead.
    */
   @Test
   void setColumnSortProducesAGenuineInputDataChangedHint() throws Exception {
      TableVSAssembly table = tableWithColumns("TableView2", "product_id", "product_name");

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenReturn(table);
      when(vs.getViewsheetInfo()).thenReturn(new inetsoft.uql.viewsheet.ViewsheetInfo());

      inetsoft.report.composition.execution.ViewsheetSandbox sandbox =
         mock(inetsoft.report.composition.execution.ViewsheetSandbox.class);
      // apply()'s removeVariable() step (info/oinfo both DataVSAssemblyInfo) unconditionally
      // dereferences box.getAssetQuerySandbox().getVariableTable() before it even checks
      // whether there is a precondition list to diff -- needs a non-null AssetQuerySandbox to
      // avoid an NPE unrelated to what this test is actually verifying.
      when(sandbox.getAssetQuerySandbox()).thenReturn(
         mock(inetsoft.report.composition.execution.AssetQuerySandbox.class));

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(java.util.Optional.of(sandbox));

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      doAnswer(invocation -> {
         ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
         mutation.run(rvs, "rt1", null);
         return null;
      }).when(sessions).mutate(anyString(), any(Principal.class), any());

      inetsoft.web.viewsheet.service.CoreLifecycleService coreLifecycleService =
         mock(inetsoft.web.viewsheet.service.CoreLifecycleService.class);
      inetsoft.web.binding.handler.VSAssemblyInfoHandler realHandler =
         new inetsoft.web.binding.handler.VSAssemblyInfoHandler(
            coreLifecycleService,
            mock(inetsoft.web.binding.service.DataRefModelFactoryService.class),
            mock(inetsoft.web.viewsheet.service.ParameterService.class),
            mock(inetsoft.uql.service.DataSourceRegistry.class));

      TableBindingService service = new TableBindingService(
         sessions, mock(VSBindingService.class), mock(VSBindingModelService.class),
         mock(inetsoft.web.binding.service.DataRefModelFactoryService.class),
         mock(inetsoft.web.composer.vs.dialog.HideColumnsDialogService.class), realHandler);

      service.setColumnSort("tok", principal(), "TableView2", "product_id", "asc", null);

      ArgumentCaptor<Integer> hintCaptor = ArgumentCaptor.forClass(Integer.class);
      verify(coreLifecycleService).execute(
         eq(rvs), anyString(), any(), hintCaptor.capture(), anyBoolean(), any());

      int hint = hintCaptor.getValue();
      assertTrue((hint & VSAssembly.INPUT_DATA_CHANGED) != 0,
                "the hint reaching CoreLifecycleService.execute() must include " +
                "INPUT_DATA_CHANGED -- passing the assembly's live (uncloned) VSAssemblyInfo " +
                "into apply() (the pre-fix bug) makes copyInfo's diff a self-comparison, " +
                "always NONE_CHANGED, regardless of what actually changed");
   }

   @Test
   void setColumnSortRefusesAnUnboundColumn() throws Exception {
      TableVSAssembly table = tableWithColumns("TableView2", "product_name");
      ViewsheetSessionService sessions = sessionsFor(table);
      TableBindingService service = serviceWith(
         sessions, new TableBindingModel(), mock(VSBindingModelService.class));

      Exception thrown = assertThrows(Exception.class,
         () -> service.setColumnSort("tok", principal(), "TableView2", "ghost_col", "asc", null));

      assertTrue(thrown.getMessage().contains("ghost_col"));
      assertTrue(thrown.getMessage().contains("not bound"));
      assertTrue(thrown.getMessage().contains("product_name"));
   }

   @Test
   void setColumnSortRefusesACrosstabByName() {
      ViewsheetSessionService sessions = sessionsFor(mock(CrosstabVSAssembly.class));
      TableBindingService service = serviceWith(
         sessions, new CrosstabBindingModel(), mock(VSBindingModelService.class));

      Exception thrown = assertThrows(Exception.class,
         () -> service.setColumnSort("tok", principal(), "Crosstab1", "Year", "asc", null));

      assertTrue(thrown.getMessage().contains("Crosstab1"));
      assertTrue(thrown.getMessage().toLowerCase().contains("crosstab"));
      assertTrue(thrown.getMessage().contains("set_field_sort"));
   }

   @Test
   void setColumnSortRefusesACrosstabOnlyDirection() throws Exception {
      TableVSAssembly table = tableWithColumns("TableView2", "product_id");
      ViewsheetSessionService sessions = sessionsFor(table);
      TableBindingService service = serviceWith(
         sessions, new TableBindingModel(), mock(VSBindingModelService.class));

      Exception thrown = assertThrows(Exception.class,
         () -> service.setColumnSort("tok", principal(), "TableView2", "product_id", "value_asc",
                                     null));

      assertTrue(thrown.getMessage().contains("set_field_sort"));
      assertEquals(0, table.getSortInfo().getSortCount(),
                  "a refused direction must not partially apply");
   }

   /**
    * B.2's persistence claim in the bug's diagnosis, which the refuter found had zero existing
    * test coverage anywhere in this codebase (no {@code *TableDataVSAssemblyInfoTest}/
    * {@code *TableVSAssemblyInfoTest} exists, and {@code BaseTableSortColumnServiceTest}
    * exercises its toggle purely in-memory, never through {@code writeXML}/{@code parseXML}).
    * Exercises the real, {@code final} {@code AssemblyInfo.writeXML}/{@code parseXML} pair --
    * the same pair {@code TableDataVSAssemblyInfo.writeContents}/{@code parseContents} plug
    * into for a genuine save/reload, not a hand-rolled substitute.
    */
   @Test
   void columnSortSurvivesAnXmlWriteAndParseRoundTrip() throws Exception {
      // Unlike tableWithColumns' usual mock(Viewsheet.class) (fine for every other test here,
      // which never serializes), writeXML's real TitleInfo.getTitle() dereferences
      // getViewsheet().getViewsheetInfo() -- a real Viewsheet is needed so that is non-null.
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.setColumnSelection(columnRefSelectionOf("product_id", "product_name"));
      TableVSAssembly table = new TableVSAssembly(new Viewsheet(), "TableView2");
      table.setVSAssemblyInfo(info);
      ViewsheetSessionService sessions = sessionsFor(table);
      TableBindingService service = serviceWithCommittingHandler(
         sessions, new TableBindingModel(), mock(VSBindingModelService.class));

      service.setColumnSort("tok", principal(), "TableView2", "product_id", "desc", null);

      java.io.StringWriter sw = new java.io.StringWriter();
      java.io.PrintWriter pw = new java.io.PrintWriter(sw);
      table.getVSAssemblyInfo().writeXML(pw);
      pw.flush();

      TableVSAssemblyInfo restored = new TableVSAssemblyInfo();
      restored.parseXML(parseXmlString(sw.toString()));

      assertEquals(1, restored.getSortInfo().getSortCount(),
                  "the column sort set by setColumnSort must survive a save (writeXML) / " +
                  "reload (parseXML) round trip, not just live in the runtime assembly");
      assertEquals(Integer.valueOf(inetsoft.report.StyleConstants.SORT_DESC),
                  orderOf(restored.getSortInfo(), "product_id"));
   }

   /**
    * Read side of bug-76806: {@code sorts} was structurally always empty for
    * {@code objectType:"table"} before this (see {@link #readReportsColumnVisibilityFromHiddenColumns}
    * for the parallel bug-76807 fix this mirrors). Uses the same real-{@code TableVSAssembly}/
    * real-{@code TableBindingModel} construction as that test, for the same reason: a hand-built
    * model bypasses whatever the live assembly actually reports.
    */
   @Test
   void readReportsAPlainTablesColumnSortAfterSetColumnSort() throws Exception {
      TableVSAssembly table = tableWithColumns("TableView2", "product_id", "product_name");
      ViewsheetSessionService sessions = sessionsFor(table);

      inetsoft.web.binding.service.DataRefModelFactoryService refModelService =
         mock(inetsoft.web.binding.service.DataRefModelFactoryService.class);
      when(refModelService.createDataRefModel(any())).thenAnswer(
         invocation -> new inetsoft.web.binding.drm.ColumnRefModel(invocation.getArgument(0)));

      VSBindingService binding = mock(VSBindingService.class);
      when(binding.createModel(any())).thenAnswer(invocation ->
         new TableBindingModel(refModelService, (TableVSAssembly) invocation.getArgument(0)));

      TableBindingService service = new TableBindingService(
         sessions, binding, mock(VSBindingModelService.class), refModelService,
         mock(inetsoft.web.composer.vs.dialog.HideColumnsDialogService.class),
         committingAssemblyInfoHandler());

      service.setColumnSort("tok", principal(), "TableView2", "product_id", "desc", null);

      Map<String, Object> read = service.read("tok", principal(), "TableView2");

      @SuppressWarnings("unchecked")
      Map<String, Object> sorts = (Map<String, Object>) read.get("sorts");
      assertEquals("desc", sorts.get("product_id"));
      assertFalse(sorts.containsKey("product_name"),
                  "an unsorted column is omitted, not reported as a default state");
   }

   private static TableVSAssembly tableWithColumns(String assemblyName, String... columns) {
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.setColumnSelection(columnRefSelectionOf(columns));
      TableVSAssembly table = new TableVSAssembly(mock(Viewsheet.class), assemblyName);
      table.setVSAssemblyInfo(info);
      return table;
   }

   private static Integer orderOf(inetsoft.uql.asset.SortInfo sinfo, String column) {
      for(inetsoft.uql.asset.SortRef ref : sinfo.getSorts()) {
         if(ref.getDataRef() != null && column.equals(ref.getDataRef().getName())) {
            return ref.getOrder();
         }
      }

      return null;
   }

   private static Element parseXmlString(String xml) throws Exception {
      javax.xml.parsers.DocumentBuilderFactory factory =
         javax.xml.parsers.DocumentBuilderFactory.newInstance();
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      org.w3c.dom.Document doc = factory.newDocumentBuilder()
         .parse(new java.io.ByteArrayInputStream(xml.getBytes()));
      return doc.getDocumentElement();
   }

   /**
    * Read side of bug-76807: {@code get_table_binding} was blind to hide/show state before this.
    *
    * <p>Deliberately does <em>not</em> hand-build the {@code TableBindingModel} via {@code
    * TableBindingMutator.setShelf} the way the write-side tests above do (they mock {@code
    * binding.createModel(any())} to return that hand-built model regardless of what the live
    * assembly contains). That would bypass the real {@code TableBindingModel(refModelService,
    * assembly)} constructor entirely and only prove {@code enrichTableVisibility} can mark an
    * entry the test itself pre-seeded into {@code details} -- it would never exercise whether a
    * genuinely-hidden column reaches {@code details} to begin with, which is exactly the gap a
    * prior version of this test missed (a real {@code TableVSAssembly}'s {@code details} is built
    * only from the shown-side {@code getColumnSelection()}, disjoint from {@code
    * getHiddenColumns()} -- see {@code TableBindingModel}'s own constructor). So this test uses a
    * real {@code TableVSAssembly}/{@code TableVSAssemblyInfo} and stubs {@code
    * binding.createModel} to call the real constructor, and stubs {@code refModelService} with a
    * real {@code ColumnRefModel(ColumnRef)} rather than a bare mock, so the column name actually
    * round-trips.
    */
   @Test
   void readReportsColumnVisibilityFromHiddenColumns() throws Exception {
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.setColumnSelection(columnRefSelectionOf("product_id"));
      info.setHiddenColumns(columnRefSelectionOf("product_name"));
      // The 2-arg constructor backs the assembly with a Viewsheet -- setVSAssemblyInfo below
      // calls getViewsheet().resetWS() -- a mock is enough since nothing under test reads it.
      TableVSAssembly table = new TableVSAssembly(mock(Viewsheet.class), "TableView2");
      table.setVSAssemblyInfo(info);
      ViewsheetSessionService sessions = sessionsFor(table);

      inetsoft.web.binding.service.DataRefModelFactoryService refModelService =
         mock(inetsoft.web.binding.service.DataRefModelFactoryService.class);
      when(refModelService.createDataRefModel(any())).thenAnswer(
         invocation -> new inetsoft.web.binding.drm.ColumnRefModel(invocation.getArgument(0)));

      VSBindingService binding = mock(VSBindingService.class);
      when(binding.createModel(any())).thenAnswer(invocation ->
         new TableBindingModel(refModelService, (TableVSAssembly) invocation.getArgument(0)));

      TableBindingService service = new TableBindingService(
         sessions, binding, mock(VSBindingModelService.class), refModelService,
         mock(inetsoft.web.composer.vs.dialog.HideColumnsDialogService.class),
         mock(inetsoft.web.binding.handler.VSAssemblyInfoHandler.class));

      Map<String, Object> read = service.read("tok", principal(), "TableView2");

      @SuppressWarnings("unchecked")
      Map<String, List<FieldRef>> shelves = (Map<String, List<FieldRef>>) (Map<String, ?>) read.get("shelves");
      List<FieldRef> details = shelves.get("details");
      List<String> columns = details.stream().map(FieldRef::column).toList();
      assertEquals(List.of("product_id", "product_name"), columns,
                   "the hidden column must still be reported by get_table_binding, not dropped " +
                   "from details entirely");
      assertEquals(Boolean.TRUE, details.get(columns.indexOf("product_id")).visible(),
                  "product_id is shown");
      assertEquals(Boolean.FALSE, details.get(columns.indexOf("product_name")).visible(),
                  "product_name is hidden");
   }

   /**
    * Write side of bug #76820: re-adding a hidden column via {@code add_table_field}/{@code
    * set_table_fields} left it present in both {@code getColumnSelection()} and {@code
    * getHiddenColumns()} at once, so {@code get_table_binding} kept reporting it hidden even
    * though it was live-bound and rendering. Calls {@link VSTableBindingFactory#updateTableAssembly}
    * directly -- it is {@code public static}, so no service-layer mocking is needed.
    */
   @Test
   void updateTableAssemblyClearsReaddedColumnFromHiddenColumns() {
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.setColumnSelection(columnRefSelectionOf("product_id"));
      info.setHiddenColumns(columnRefSelectionOf("product_name"));
      TableVSAssembly table = new TableVSAssembly(mock(Viewsheet.class), "TableView3");
      table.setVSAssemblyInfo(info);

      TableBindingModel model = new TableBindingModel();
      model.addDetail(new inetsoft.web.binding.drm.ColumnRefModel(
         new inetsoft.uql.asset.ColumnRef(new inetsoft.uql.erm.AttributeRef(null, "product_id"))));
      model.addDetail(new inetsoft.web.binding.drm.ColumnRefModel(
         new inetsoft.uql.asset.ColumnRef(new inetsoft.uql.erm.AttributeRef(null, "product_name"))));

      inetsoft.web.binding.service.VSTableBindingFactory.updateTableAssembly(model, table);

      assertNotNull(info.getColumnSelection().getAttribute("product_name"),
                    "the re-added column must still be present in the shown column selection");
      assertNull(info.getHiddenColumns().getAttribute("product_name"),
                "the re-added column must be cleared from hidden columns, not left dual-bound");
   }

   /** A ColumnSelection holding one real {@code ColumnRef} per given column name -- what a live
    *  {@code TableVSAssemblyInfo}'s {@code getColumnSelection()}/{@code getHiddenColumns()}
    *  actually hold, unlike {@link #columnSelectionOf}'s bare {@code AttributeRef}s (fine for the
    *  {@code HideColumnsDialogModel} tests above, which only compare column names by string, but
    *  not for a real {@code TableBindingModel} construction, which casts each attribute to
    *  {@code ColumnRef}). */
   private static inetsoft.uql.ColumnSelection columnRefSelectionOf(String... names) {
      inetsoft.uql.ColumnSelection selection = new inetsoft.uql.ColumnSelection();

      for(String name : names) {
         selection.addAttribute(
            new inetsoft.uql.asset.ColumnRef(new inetsoft.uql.erm.AttributeRef(null, name)));
      }

      return selection;
   }

   private static CrosstabBindingModel withTables(String... names) {
      CrosstabBindingModel model = new CrosstabBindingModel();
      List<BindingModel.SourceTable> tables = new ArrayList<>();

      for(String name : names) {
         BindingModel.SourceTable table = new BindingModel.SourceTable();
         table.setName(name);
         tables.add(table);
      }

      model.setTables(tables);
      return model;
   }

   /** Like {@link #withTables}, but each table carries the column names given for it. */
   private static CrosstabBindingModel withTablesAndColumns(Map<String, List<String>> byTable) {
      CrosstabBindingModel model = new CrosstabBindingModel();
      List<BindingModel.SourceTable> tables = new ArrayList<>();

      for(Map.Entry<String, List<String>> entry : byTable.entrySet()) {
         BindingModel.SourceTable table = new BindingModel.SourceTable();
         table.setName(entry.getKey());
         List<BindingModel.SourceTableColumn> columns = new ArrayList<>();

         for(String column : entry.getValue()) {
            columns.add(new BindingModel.SourceTableColumn(column, "string"));
         }

         table.setColumns(columns);
         tables.add(table);
      }

      model.setTables(tables);
      return model;
   }

   private static ApplyVSAssemblyInfoEvent capture(VSBindingModelService bindings)
      throws Exception
   {
      ArgumentCaptor<ApplyVSAssemblyInfoEvent> captor =
         ArgumentCaptor.forClass(ApplyVSAssemblyInfoEvent.class);
      verify(bindings).setBinding(eq("rt1"), captor.capture(), any(Principal.class), any());
      return captor.getValue();
   }

   private static TableBindingService harness(VSAssembly assembly, BindingModel model,
                                              VSBindingModelService bindings)
   {
      return serviceWith(sessionsFor(assembly), model, bindings);
   }

   /** Like {@link #sessionsFor}, but with a render sandbox that resolves {@code lens} for
    *  {@code assemblyName} -- needed by {@code setColumnWidths}, which resolves a column against
    *  a rendered {@code VSTableLens} rather than the wiz model. */
   private static ViewsheetSessionService sessionsWithLens(VSAssembly assembly,
                                                           String assemblyName,
                                                           inetsoft.report.composition.VSTableLens lens)
      throws Exception
   {
      inetsoft.report.composition.execution.ViewsheetSandbox sandbox =
         mock(inetsoft.report.composition.execution.ViewsheetSandbox.class);
      when(sandbox.getVSTableLens(assemblyName, false)).thenReturn(lens);

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenReturn(assembly);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(java.util.Optional.of(sandbox));

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      doAnswer(invocation -> {
         ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
         mutation.run(rvs, "rt1", null);
         return null;
      }).when(sessions).mutate(anyString(), any(Principal.class), any());

      return sessions;
   }

   private static ViewsheetSessionService sessionsFor(VSAssembly assembly) {
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenReturn(assembly);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);

      try {
         doAnswer(invocation -> {
            ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
            mutation.run(rvs, "rt1", null);
            return null;
         }).when(sessions).mutate(anyString(), any(Principal.class), any());
         when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      return sessions;
   }

   private static TableBindingService serviceWith(ViewsheetSessionService sessions,
                                                  BindingModel model,
                                                  VSBindingModelService bindings)
   {
      return serviceWith(sessions, model, bindings,
                         mock(inetsoft.web.composer.vs.dialog.HideColumnsDialogService.class));
   }

   private static TableBindingService serviceWith(
      ViewsheetSessionService sessions, BindingModel model, VSBindingModelService bindings,
      inetsoft.web.composer.vs.dialog.HideColumnsDialogService hideColumnsService)
   {
      VSBindingService binding = mock(VSBindingService.class);
      when(binding.createModel(any())).thenReturn(model);
      return new TableBindingService(sessions, binding, bindings,
                                     mock(inetsoft.web.binding.service.DataRefModelFactoryService.class),
                                     hideColumnsService,
                                     mock(inetsoft.web.binding.handler.VSAssemblyInfoHandler.class));
   }

   private static Principal principal() {
      return () -> "admin";
   }
}
