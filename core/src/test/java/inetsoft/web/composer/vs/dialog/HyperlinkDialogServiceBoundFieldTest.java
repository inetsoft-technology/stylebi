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
package inetsoft.web.composer.vs.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.Hyperlink;
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.model.vs.HyperlinkDialogModel;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.service.HighlightService;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import inetsoft.report.composition.VSTableLens;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78758 items 3 and 4: a chart colName that matches no bound field stored the link nowhere
 * with ok:true, and a crosstab header that only exists under a date comparison accepted a link the
 * next binding apply lost or moved. Both are refused on the wiz path (rejectDerivedMeasure).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class HyperlinkDialogServiceBoundFieldTest {
   private static final Principal USER = () -> "admin";

   private static VSChartDimensionRef dim(String column) {
      VSChartDimensionRef dim = new VSChartDimensionRef(new AttributeRef(column));
      dim.setGroupColumnValue(column);
      return dim;
   }

   private static VSChartAggregateRef sum(String column) {
      VSChartAggregateRef measure = new VSChartAggregateRef();
      measure.setDataRef(new AttributeRef(column));
      measure.setColumnValue(column);
      measure.setFormula(AggregateFormula.SUM);
      return measure;
   }

   private static class ChartFixture {
      HyperlinkDialogService service;
      VSObjectPropertyService props;
      VSChartAggregateRef measure;
   }

   /** A chart with x = {@code xs} and y = Sum(v); runtime refs mirror the design refs. */
   private static ChartFixture chart(VSChartDimensionRef... xs) throws Exception {
      ChartFixture f = new ChartFixture();
      f.measure = sum("v");
      VSChartInfo chartInfo = new DefaultVSChartInfo();
      chartInfo.setChartDescriptor(new ChartDescriptor());

      for(VSChartDimensionRef x : xs) {
         chartInfo.addXField(x);
      }

      chartInfo.addYField(f.measure);
      chartInfo.setRTXFields(chartInfo.getXFields());
      chartInfo.setRTYFields(chartInfo.getYFields());

      Viewsheet vs = new Viewsheet();
      ChartVSAssembly assembly = new ChartVSAssembly(vs, "Chart1");
      assembly.setVSChartInfo(chartInfo);
      vs.addAssembly(assembly);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.empty());
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      when(viewsheetService.getViewsheet(anyString(), any())).thenReturn(rvs);
      f.props = mock(VSObjectPropertyService.class);
      f.service = new HyperlinkDialogService(null, f.props, viewsheetService,
                                             mock(DataRefModelFactoryService.class), null, null,
                                             mock(HighlightService.class), null);
      return f;
   }

   private static HyperlinkDialogModel model(String colName, boolean reject) {
      HyperlinkDialogModel model = new HyperlinkDialogModel();
      model.setColName(colName);
      model.setLinkType(Hyperlink.WEB_LINK);
      model.setWebLink("https://example.com/x");
      model.setRejectDerivedMeasure(reject);
      return model;
   }

   private static ChartVSAssemblyInfo written(ChartFixture f) throws Exception {
      ArgumentCaptor<VSAssemblyInfo> captor = ArgumentCaptor.forClass(VSAssemblyInfo.class);
      verify(f.props).editObjectProperty(any(), captor.capture(), anyString(), anyString(),
                                         any(), any(), any(), anyBoolean(), any());
      return (ChartVSAssemblyInfo) captor.getValue();
   }

   private static void assertNothingWritten(ChartFixture f) throws Exception {
      verify(f.props, never()).editObjectProperty(any(), any(), anyString(), anyString(), any(),
                                                  any(), any(), anyBoolean(), any());
   }

   @Test
   void wrongCaseMeasureNameResolvesToTheBoundField() throws Exception {
      ChartFixture f = chart(dim("region"));
      String exact = f.measure.getFullName();
      assertNotEquals(exact, exact.toUpperCase());

      f.service.setHyperlinkDialogModel("rt", "Chart1", model(exact.toUpperCase(), true), "",
                                        USER, null);
      VSChartAggregateRef out = (VSChartAggregateRef) written(f).getVSChartInfo().getYFields()[0];

      assertNotNull(out.getHyperlink(), "the link must land on the measure");
      assertEquals("https://example.com/x", out.getHyperlink().getLink());
   }

   @Test
   void wrongCaseNameReadsBackThroughGet() throws Exception {
      ChartFixture f = chart(dim("region"));
      Hyperlink link = new Hyperlink();
      link.setLinkType(Hyperlink.WEB_LINK);
      link.setLink("https://example.com/x");
      f.measure.setHyperlink(link);
      String exact = f.measure.getFullName();

      HyperlinkDialogModel read = f.service.getHyperlinkDialogModel(
         "rt", "Chart1", 0, 0, exact.toLowerCase(), false, false, false, false, USER);

      assertEquals(Hyperlink.WEB_LINK, read.getLinkType());
      assertEquals(exact, read.getColName());
   }

   @Test
   void nameMatchingNoBoundFieldIsRefusedNamingColNameAndTheFields() throws Exception {
      ChartFixture f = chart(dim("region"));

      Exception e = assertThrows(IllegalArgumentException.class, () ->
         f.service.setHyperlinkDialogModel("rt", "Chart1", model("nonsense", true), "", USER, null));
      assertTrue(e.getMessage().contains("colName") && e.getMessage().contains("nonsense") &&
                    e.getMessage().contains(f.measure.getFullName()), e.getMessage());
      assertNothingWritten(f);
   }

   @Test
   void namesDifferingOnlyByCaseAreRefusedAsAmbiguous() throws Exception {
      ChartFixture f = chart(dim("Region"), dim("region"));

      Exception e = assertThrows(IllegalArgumentException.class, () ->
         f.service.setHyperlinkDialogModel("rt", "Chart1", model("REGION", true), "", USER, null));
      assertTrue(e.getMessage().contains("differ only by case"), e.getMessage());
      assertNothingWritten(f);
   }

   @Test
   void exactAndBaseNamesStillWork() throws Exception {
      ChartFixture byFull = chart(dim("region"));
      byFull.service.setHyperlinkDialogModel(
         "rt", "Chart1", model(byFull.measure.getFullName(), true), "", USER, null);
      assertNotNull(((VSChartAggregateRef) written(byFull).getVSChartInfo().getYFields()[0])
                       .getHyperlink());

      ChartFixture byBase = chart(dim("region"));
      String base = byBase.measure.getName();
      assertNotEquals(byBase.measure.getFullName(), base,
                      "fixture must separate name from full name");
      byBase.service.setHyperlinkDialogModel("rt", "Chart1", model(base, true), "", USER, null);
      assertNotNull(((VSChartAggregateRef) written(byBase).getVSChartInfo().getYFields()[0])
                       .getHyperlink());
   }

   @Test
   void titleLinkIgnoresTheColNameAndGuiPathKeepsItsOldBehaviour() throws Exception {
      ChartFixture title = chart(dim("region"));
      HyperlinkDialogModel m = model("nonsense", true);
      m.setTitleLink(true);
      assertDoesNotThrow(() -> title.service.setHyperlinkDialogModel(
         "rt", "Chart1", m, "", USER, null));

      ChartFixture gui = chart(dim("region"));
      assertDoesNotThrow(() -> gui.service.setHyperlinkDialogModel(
         "rt", "Chart1", model("nonsense", false), "", USER, null));
   }

   // ---- crosstab header under a date comparison

   private static TableDataPath header(String... names) {
      return new TableDataPath(-1, TableDataPath.GROUP_HEADER, "string", names);
   }

   private static HighlightService.DateComparisonFields derived(String... names) {
      return new HighlightService.DateComparisonFields(List.of(names), List.of());
   }

   /** A crosstab whose cell (1, 0) sits at {@code path}; the comparison classifies {@code fields}. */
   private static HyperlinkDialogService crosstab(TableDataPath path,
                                                  HighlightService.DateComparisonFields fields)
      throws Exception
   {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new CrosstabVSAssembly(vs, "Crosstab1"));

      VSTableLens lens = mock(VSTableLens.class);
      when(lens.getTableDataPath(anyInt(), anyInt())).thenReturn(path);
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getVSTableLens(anyString(), anyBoolean())).thenReturn(lens);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      when(viewsheetService.getViewsheet(anyString(), any())).thenReturn(rvs);
      HighlightService highlights = mock(HighlightService.class);
      when(highlights.getDateComparisonFields(any(), any(), any())).thenReturn(fields);
      return new HyperlinkDialogService(
         mock(CoreLifecycleService.class), null, viewsheetService, null, null, null, highlights,
         null);
   }

   private static void setLink(HyperlinkDialogService service, boolean reject, boolean clear)
      throws Exception
   {
      HyperlinkDialogModel model = model(null, reject);
      model.setRow(1);
      model.setCol(0);
      model.setLinkType(clear ? HyperlinkDialogService.NONE : Hyperlink.WEB_LINK);
      service.setHyperlinkDialogModel("rt", "Crosstab1", model, "", USER, null);
   }

   @Test
   void crosstabHeaderThatExistsOnlyUnderTheComparisonIsRefused() {
      for(String name : new String[] { "Year(ORDER_DATE)", "MonthOfYear(ORDER_DATE)" }) {
         Exception e = assertThrows(IllegalArgumentException.class, () -> setLink(
            crosstab(header(name), derived("Year(ORDER_DATE)", "MonthOfYear(ORDER_DATE)")),
            true, false));
         assertTrue(e.getMessage().contains("'" + name + "'") &&
                       e.getMessage().toLowerCase().contains("date comparison"), e.getMessage());
      }
   }

   @Test
   void nestedHeaderIsJudgedByItsOwnNameAndAmbiguousIsRefused() {
      assertDoesNotThrow(() -> setLink(
         crosstab(header("Year(ORDER_DATE)", "Month(ORDER_DATE)"), derived("Year(ORDER_DATE)")),
         true, false));
      assertThrows(IllegalArgumentException.class, () -> setLink(
         crosstab(header("Year(ORDER_DATE)"),
                  new HighlightService.DateComparisonFields(List.of(), List.of("Year(ORDER_DATE)"))),
         true, false));
   }

   @Test
   void plainHeaderClearingTheGuiPathAndOtherCellsAreStillAccepted() {
      assertDoesNotThrow(() -> setLink(
         crosstab(header("Month(ORDER_DATE)"), derived("Year(ORDER_DATE)")), true, false));
      assertDoesNotThrow(() -> setLink(
         crosstab(header("Year(ORDER_DATE)"), HighlightService.DateComparisonFields.NONE),
         true, false));
      assertDoesNotThrow(() -> setLink(
         crosstab(header("Year(ORDER_DATE)"), derived("Year(ORDER_DATE)")), true, true));
      assertDoesNotThrow(() -> setLink(
         crosstab(header("Year(ORDER_DATE)"), derived("Year(ORDER_DATE)")), false, false));
      TableDataPath summary = new TableDataPath(-1, TableDataPath.SUMMARY, "double",
                                                new String[] { "Year(ORDER_DATE)" });
      assertDoesNotThrow(() -> setLink(
         crosstab(summary, derived("Year(ORDER_DATE)")), true, false));
   }
}
