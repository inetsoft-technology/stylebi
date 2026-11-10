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
package inetsoft.web.wiz.viewsheet;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.FormatInfo;
import inetsoft.uql.viewsheet.GaugeVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.web.adhoc.model.FontInfo;
import inetsoft.web.adhoc.model.chart.ChartFormatConstants;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.composer.model.vs.VSObjectFormatInfoModel;
import inetsoft.web.composer.vs.controller.FormatPainterService;
import inetsoft.web.composer.vs.objects.command.SetCurrentFormatCommand;
import inetsoft.web.composer.vs.objects.event.GetVSObjectFormatEvent;
import inetsoft.web.graph.handler.ChartRegionHandler;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import inetsoft.web.wiz.binding.CalcTableService;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Font;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static inetsoft.web.wiz.viewsheet.ViewsheetFormatServiceTest.principal;
import static inetsoft.web.wiz.viewsheet.ViewsheetFormatServiceTest.sessionsFor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Bug #81012: {@code get_format} reads back what {@code set_format} wrote -- the stored format of
 * an assembly's own, title-bar and region targets, and a chart's data-label / text-field format --
 * and refuses by name a target it cannot read instead of answering a default.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ViewsheetFormatServiceReadTest {
   private static VSCompositeFormat userFormat(java.util.function.Consumer<
      inetsoft.uql.viewsheet.VSFormat> fill)
   {
      VSCompositeFormat format = new VSCompositeFormat();
      fill.accept(format.getUserDefinedFormat());
      return format;
   }

   /** The pane model the painter always answers with: every default is filled in. */
   private static VSObjectFormatInfoModel paneModel() {
      VSObjectFormatInfoModel model = new VSObjectFormatInfoModel();
      model.setColor("#ff0000");
      model.setFont(new FontInfo(new Font("Arial", Font.BOLD, 18)));
      model.setBackgroundAlpha(100);
      model.setRoundCorner(0);
      model.setWrapText(false);
      model.setBorderTopColor("#c0c0c0");
      model.setBorderLeftColor("#c0c0c0");
      model.setBorderBottomColor("#c0c0c0");
      model.setBorderRightColor("#c0c0c0");
      model.setAlign(new inetsoft.web.adhoc.model.AlignmentInfo(0));
      return model;
   }

   private static FormatPainterService painterAnswering(VSObjectFormatInfoModel model)
      throws Exception
   {
      FormatPainterService painter = mock(FormatPainterService.class);
      doAnswer(invocation -> {
         CapturingCommandDispatcher dispatcher = invocation.getArgument(3);
         dispatcher.sendCommand("Gauge1", new SetCurrentFormatCommand(model));
         return null;
      }).when(painter).getFormat(eq("rt1"), any(GetVSObjectFormatEvent.class),
                                 any(Principal.class), any());
      return painter;
   }

   private static ViewsheetFormatService service(FormatPainterService painter,
                                                 RuntimeViewsheet rvs)
   {
      return new ViewsheetFormatService(sessionsFor(rvs), FormatPainterProxies.local(painter),
                                        mock(CalcTableService.class), null);
   }

   private static Map<String, Object> read(ViewsheetFormatService service, String assembly,
                                           String target, String field) throws Exception
   {
      return service.getFormat("tok", principal(),
                               new ViewsheetFormatService.FormatReadRequest(assembly, target,
                                                                            field));
   }

   private static IllegalArgumentException refused(ViewsheetFormatService service,
                                                   String assembly, String target, String field)
   {
      return assertThrows(IllegalArgumentException.class,
                          () -> read(service, assembly, target, field));
   }

   private static VSAssemblyInfo titledInfo() {
      ChartVSAssemblyInfo info = mock(ChartVSAssemblyInfo.class);
      when(info.getFormatInfo()).thenReturn(new FormatInfo());
      return info;
   }

   private static RuntimeViewsheet gaugeRvs(VSAssemblyInfo info) {
      Viewsheet viewsheet = mock(Viewsheet.class);
      VSAssembly assembly = mock(GaugeVSAssembly.class);
      when(assembly.getVSAssemblyInfo()).thenReturn(info);
      when(viewsheet.getAssembly("Gauge1")).thenReturn(assembly);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(rvs.getID()).thenReturn("rt1");
      return rvs;
   }

   @Test
   void titleReadsOnlyWhatTheTitleEntryDefines() throws Exception {
      VSAssemblyInfo info = titledInfo();
      info.getFormatInfo().setFormat(
         VSAssemblyInfo.TITLEPATH,
         userFormat(f -> {
            f.setFontValue(new Font("Arial", Font.BOLD, 18), true);
            f.setForegroundValue("0xFF0000", true);
         }));
      FormatPainterService painter = painterAnswering(paneModel());

      Map<String, Object> out = read(service(painter, gaugeRvs(info)), "Gauge1", "title", null);

      assertEquals("title", out.get("target"));
      @SuppressWarnings("unchecked")
      Map<String, Object> format = (Map<String, Object>) out.get("format");
      assertEquals(java.util.Set.of("font", "color"), format.keySet(),
                   "pane defaults the entry never defined must not be reported: " + format);
      ArgumentCaptor<GetVSObjectFormatEvent> event =
         ArgumentCaptor.forClass(GetVSObjectFormatEvent.class);
      verify(painter).getFormat(eq("rt1"), event.capture(), any(Principal.class), any());
      assertEquals("Gauge1", event.getValue().getName());
      assertEquals(VSAssemblyInfo.TITLEPATH, event.getValue().getDataPath());
   }

   @Test
   void objectReadsBordersRoundCornerAndAlphaOnlyWhenWritten() throws Exception {
      VSAssemblyInfo info = titledInfo();
      info.getFormatInfo().setFormat(
         VSAssemblyInfo.OBJECTPATH,
         userFormat(f -> {
            f.setRoundCornerValue(8, true);
            f.setAlphaValue(40, true);
            f.setBordersValue(new java.awt.Insets(1, 1, 1, 1), true);
            f.setBorderColorsValue(new inetsoft.uql.viewsheet.BorderColors(
               java.awt.Color.RED, java.awt.Color.RED, java.awt.Color.RED, java.awt.Color.RED),
               true);
         }));
      VSObjectFormatInfoModel model = paneModel();
      model.setRoundCorner(8);
      model.setBackgroundAlpha(40);
      model.setBorderTopStyle("1");
      model.setBorderLeftStyle("1");
      model.setBorderBottomStyle("1");
      model.setBorderRightStyle("1");
      model.setBorderTopColor("#ff0000");
      model.setBorderLeftColor("#ff0000");
      model.setBorderBottomColor("#ff0000");
      model.setBorderRightColor("#ff0000");
      FormatPainterService painter = painterAnswering(model);

      @SuppressWarnings("unchecked")
      Map<String, Object> format = (Map<String, Object>) read(
         service(painter, gaugeRvs(info)), "Gauge1", null, null).get("format");

      assertEquals(8, format.get("roundCorner"));
      assertEquals(40, format.get("backgroundAlpha"));
      assertEquals("1", format.get("borderTopStyle"));
      assertEquals("#ff0000", format.get("borderRightColor"));
      assertFalse(format.containsKey("wrapText"), format.toString());
      assertFalse(format.containsKey("font"), format.toString());
      assertFalse(format.containsKey("align"), format.toString());

      ArgumentCaptor<GetVSObjectFormatEvent> event =
         ArgumentCaptor.forClass(GetVSObjectFormatEvent.class);
      verify(painter).getFormat(eq("rt1"), event.capture(), any(Principal.class), any());
      assertNull(event.getValue().getDataPath(), "object is the painter's no-path default");
   }

   @Test
   void aTargetWithNoFormatOfItsOwnReadsNullWithoutAskingThePainter() throws Exception {
      FormatPainterService painter = painterAnswering(paneModel());
      Map<String, Object> out = read(service(painter, gaugeRvs(titledInfo())), "Gauge1",
                                     "title", null);

      assertTrue(out.containsKey("format"));
      assertNull(out.get("format"));
      verify(painter, never()).getFormat(anyString(), any(), any(), any());
   }

   @Test
   void everyPlainPathTargetReadsThePathSetFormatWrites() throws Exception {
      Map<String, TableDataPath> paths = Map.of(
         "activeTab", inetsoft.uql.viewsheet.internal.TabVSAssemblyInfo.ACTIVE_TAB_PATH,
         "optionText", new TableDataPath(-1, TableDataPath.DETAIL));

      for(Map.Entry<String, TableDataPath> entry : paths.entrySet()) {
         VSAssemblyInfo info = titledInfo();
         info.getFormatInfo().setFormat(
            entry.getValue(), userFormat(f -> f.setForegroundValue("0x112233", true)));
         Viewsheet viewsheet = mock(Viewsheet.class);
         VSAssembly assembly = "activeTab".equals(entry.getKey()) ?
            mock(inetsoft.uql.viewsheet.TabVSAssembly.class) :
            mock(inetsoft.uql.viewsheet.CheckBoxVSAssembly.class);
         when(assembly.getVSAssemblyInfo()).thenReturn(info);
         when(viewsheet.getAssembly("A1")).thenReturn(assembly);
         RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
         when(rvs.getViewsheet()).thenReturn(viewsheet);
         when(rvs.getID()).thenReturn("rt1");
         FormatPainterService painter = painterAnswering(paneModel());

         read(service(painter, rvs), "A1", entry.getKey(), null);

         ArgumentCaptor<GetVSObjectFormatEvent> event =
            ArgumentCaptor.forClass(GetVSObjectFormatEvent.class);
         verify(painter).getFormat(eq("rt1"), event.capture(), any(Principal.class), any());
         assertEquals(entry.getValue(), event.getValue().getDataPath(), entry.getKey());
      }
   }

   @Test
   void viewsheetTargetTakesNoAssemblyAndReadsTheDashboardsOwnFormat() throws Exception {
      VSAssemblyInfo info = titledInfo();
      info.getFormatInfo().setFormat(
         VSAssemblyInfo.OBJECTPATH, userFormat(f -> f.setBackgroundValue("0xEEEEEE", true)));
      Viewsheet viewsheet = mock(Viewsheet.class);
      when(viewsheet.getVSAssemblyInfo()).thenReturn(info);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(rvs.getID()).thenReturn("rt1");
      VSObjectFormatInfoModel model = paneModel();
      model.setBackgroundColor("#eeeeee");
      FormatPainterService painter = painterAnswering(model);

      @SuppressWarnings("unchecked")
      Map<String, Object> format = (Map<String, Object>) read(
         service(painter, rvs), null, "viewsheet", null).get("format");

      assertEquals("#eeeeee", format.get("backgroundColor"));
      ArgumentCaptor<GetVSObjectFormatEvent> event =
         ArgumentCaptor.forClass(GetVSObjectFormatEvent.class);
      verify(painter).getFormat(eq("rt1"), event.capture(), any(Principal.class), any());
      assertNull(event.getValue().getName(), "no name is the painter's route to the dashboard");
      assertTrue(refused(service(painter, rvs), "Gauge1", "viewsheet", null).getMessage()
                    .contains("'assembly' must be omitted"));
   }

   // ── chart data labels / text field ──

   private static ChartRef measure(String name) {
      ChartRef ref = mock(ChartRef.class);
      when(ref.getFullName()).thenReturn(name);
      when(ref.isMeasure()).thenReturn(true);
      return ref;
   }

   private static RuntimeViewsheet chartRvs(String... measures) {
      ChartRef x = mock(ChartRef.class);
      when(x.getFullName()).thenReturn("Region");
      ChartRef[] y = new ChartRef[measures.length];

      for(int i = 0; i < y.length; i++) {
         y[i] = measure(measures[i]);
      }

      VSChartInfo info = mock(VSChartInfo.class);
      when(info.getXFields()).thenReturn(new ChartRef[]{ x });
      when(info.getYFields()).thenReturn(y);
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getVSChartInfo()).thenReturn(info);
      Viewsheet viewsheet = mock(Viewsheet.class);
      when(viewsheet.getAssembly("Chart1")).thenReturn(chart);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(rvs.getID()).thenReturn("rt1");
      return rvs;
   }

   private static FormatPainterService regionPainter() throws Exception {
      FormatPainterService painter = mock(FormatPainterService.class);
      VSObjectFormatInfoModel model = new VSObjectFormatInfoModel();
      model.setColor("#0000ff");
      model.setBackgroundColor("");
      model.setFont(new FontInfo(new Font("Arial", Font.PLAIN, 9)));
      model.setBackgroundAlpha(100);
      model.setAlign(new inetsoft.web.adhoc.model.AlignmentInfo(0));
      when(painter.getChartRegionFormatModel(anyString(), any(), anyString(), anyString(),
                                             anyInt(), anyString(), anyBoolean()))
         .thenReturn(model);
      return painter;
   }

   /**
    * A chart with both a text-aesthetic field and show-value measures: plotLabel must ask the
    * region reader for the measure's show-value format (ignoreTextField), text for the field's.
    * Reading plotLabel without the flag would answer the text field's format -- plausible, wrong.
    */
   @Test
   void plotLabelReadsTheShowValueFormatAndTextTheTextFieldFormat() throws Exception {
      FormatPainterService painter = regionPainter();
      ViewsheetFormatService service = service(painter, chartRvs("Sum(Total)"));

      Map<String, Object> label = read(service, "Chart1", "plotLabel", null);
      verify(painter).getChartRegionFormatModel(
         "rt1", principal(), "Chart1", ChartFormatConstants.PLOT_AREA, 0, "Sum(Total)", true);
      assertEquals("Sum(Total)", label.get("field"));
      @SuppressWarnings("unchecked")
      Map<String, Object> format = (Map<String, Object>) label.get("format");
      assertEquals("#0000ff", format.get("color"));
      assertFalse(format.containsKey("backgroundColor"), "empty values are left out");

      read(service, "Chart1", "text", "Region");
      verify(painter).getChartRegionFormatModel(
         "rt1", principal(), "Chart1", ChartFormatConstants.TEXT, 0, "Region", false);
   }

   @Test
   void plotLabelWithSeveralMeasuresNeedsAFieldAndNamesTheBoundOnes() throws Exception {
      ViewsheetFormatService service =
         service(regionPainter(), chartRvs("Sum(Total)", "Sum(Qty)"));

      String message = refused(service, "Chart1", "plotLabel", null).getMessage();
      assertTrue(message.contains("requires 'field'") && message.contains("Sum(Total)") &&
                 message.contains("Sum(Qty)"), message);

      message = refused(service, "Chart1", "plotLabel", "Region").getMessage();
      assertTrue(message.contains("not a measure bound") && message.contains("Sum(Qty)"), message);

      assertEquals("Sum(Qty)", read(service, "Chart1", "plotLabel", "Sum(Qty)").get("field"));
   }

   @Test
   void textNeedsAField() throws Exception {
      String message = refused(service(regionPainter(), chartRvs("Sum(Total)")), "Chart1",
                               "text", null).getMessage();
      assertTrue(message.startsWith("get_format: target 'text' requires 'field'"), message);
   }

   // ── refusals, all by name ──

   @Test
   void unreadableTargetsAreRefusedByNameWithTheToolThatReadsThem() throws Exception {
      ViewsheetFormatService service = service(regionPainter(), chartRvs("Sum(Total)"));

      for(String target : new String[]{ "axisLabel", "axisLabel2", "legendTitle",
                                        "legendContent" }) {
         String message = refused(service, "Chart1", target, null).getMessage();
         assertTrue(message.contains("'" + target + "'") &&
                    message.contains("get_chart_region_format"), message);
      }

      String message = refused(service, "Chart1", "field", "Sum(Total)").getMessage();
      assertTrue(message.contains("'field'") && message.contains("plotLabel"), message);

      for(String target : new String[]{ "data", "header", "cell", "measureText", "measureBar",
                                        "measureBarNegative" }) {
         message = refused(service, "Chart1", target, null).getMessage();
         assertTrue(message.contains("'" + target + "'") && message.contains("cannot be read"),
                    message);
      }

      message = refused(service, "Chart1", "bogus", null).getMessage();
      assertTrue(message.contains("'bogus'") && message.contains("plotLabel"), message);
   }

   @Test
   void aMissingAssemblyAndABadFieldAreNamedNotAnsweredAsNoFormat() throws Exception {
      ViewsheetFormatService service = service(regionPainter(), chartRvs("Sum(Total)"));

      assertTrue(refused(service, "Nope", "object", null).getMessage()
                    .contains("no assembly named 'Nope'"));
      assertTrue(refused(service, null, "object", null).getMessage()
                    .contains("requires 'assembly'"));
      assertTrue(refused(service, "Chart1", "object", "Sum(Total)").getMessage()
                    .contains("'field' only applies to targets 'plotLabel' and 'text'"));
   }

   @Test
   void assemblyKindMismatchesAreRefusedAsReadsNotAsWrites() throws Exception {
      VSAssemblyInfo plain = mock(VSAssemblyInfo.class);
      when(plain.getFormatInfo()).thenReturn(new FormatInfo());
      ViewsheetFormatService service = service(painterAnswering(paneModel()), gaugeRvs(plain));

      String message = refused(service, "Gauge1", "title", null).getMessage();
      assertTrue(message.startsWith("get_format: target 'title'"), message);
      assertFalse(message.contains("set_format") || message.contains("not applied"), message);
      assertTrue(refused(service, "Gauge1", "activeTab", null).getMessage()
                    .startsWith("get_format:"));
      assertTrue(refused(service, "Gauge1", "plotLabel", null).getMessage()
                    .startsWith("get_format: target 'plotLabel' only applies to a chart"));
   }

   // ── the read leaves the stored format alone ──

   /**
    * Through the real painter: reading twice answers the same and leaves the assembly's stored
    * format map exactly as it was, even though the painter's own lookup writes a default format
    * into the entry it reads.
    */
   @Test
   void readingIsRepeatableAndLeavesTheStoredFormatUnchanged() throws Exception {
      Viewsheet viewsheet = new Viewsheet();
      ChartVSAssembly gauge = new ChartVSAssembly(viewsheet, "Gauge1");
      viewsheet.addAssembly(gauge);
      FormatInfo formatInfo = gauge.getVSAssemblyInfo().getFormatInfo();
      formatInfo.setFormat(
         VSAssemblyInfo.OBJECTPATH, userFormat(f -> f.setBackgroundValue("0xEEEEEE", true)));
      formatInfo.setFormat(
         VSAssemblyInfo.TITLEPATH,
         userFormat(f -> f.setFontValue(new Font("Arial", Font.BOLD, 18), true)));
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(rvs.getID()).thenReturn("rt1");
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.empty());
      ViewsheetService viewsheets = mock(ViewsheetService.class);
      when(viewsheets.getViewsheet(any(), any())).thenReturn(rvs);
      FormatPainterService painter = new FormatPainterService(
         mock(CoreLifecycleService.class), mock(ChartRegionHandler.class), viewsheets,
         mock(VSObjectModelFactoryService.class), mock(VSBindingService.class),
         mock(inetsoft.web.composer.vs.controller.VSLayoutService.class));
      ViewsheetFormatService service = service(painter, rvs);
      String before = String.valueOf(
         formatInfo.getFormat(VSAssemblyInfo.TITLEPATH).getUserDefinedFormat());
      int entries = formatInfo.getPaths().length;

      Map<String, Object> first = read(service, "Gauge1", "title", null);
      Map<String, Object> second = read(service, "Gauge1", "title", null);

      assertEquals(first.toString(), second.toString());
      @SuppressWarnings("unchecked")
      Map<String, Object> format = (Map<String, Object>) first.get("format");
      assertNotNull(format.get("font"));
      assertFalse(format.containsKey("backgroundColor"),
                  "the object's background is inherited, not the title's own: " + format);
      assertEquals(entries, formatInfo.getPaths().length);
      assertEquals(
         before,
         String.valueOf(formatInfo.getFormat(VSAssemblyInfo.TITLEPATH).getUserDefinedFormat()));
   }

   @Test
   void aReadReportsTheRequestedTargetBack() throws Exception {
      VSAssemblyInfo info = titledInfo();
      info.getFormatInfo().setFormat(
         VSAssemblyInfo.OBJECTPATH, userFormat(f -> f.setForegroundValue("0x000000", true)));
      Map<String, Object> out = read(service(painterAnswering(paneModel()), gaugeRvs(info)),
                                     "Gauge1", "OBJECT", null);
      assertEquals("Gauge1", out.get("assembly"));
      assertEquals("object", out.get("target"));
      assertEquals(List.of("assembly", "target", "format"), List.copyOf(out.keySet()));
   }
}
