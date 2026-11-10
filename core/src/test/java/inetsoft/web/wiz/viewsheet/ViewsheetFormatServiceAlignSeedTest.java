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

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.report.StyleConstants;
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.adhoc.model.AlignmentInfo;
import inetsoft.web.composer.model.vs.VSObjectFormatInfoModel;
import inetsoft.web.composer.vs.controller.FormatPainterService;
import inetsoft.web.composer.vs.objects.event.FormatVSObjectEvent;
import inetsoft.web.wiz.binding.CalcTableService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;

import static inetsoft.web.wiz.viewsheet.ViewsheetFormatServiceTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #80935: an {@code align} naming one axis must keep the other axis instead of clearing it.
 * The painter stores alignment as one int, so the seed layer fills the unsent axis from the
 * path's current alignment; the chart text/region targets, which bypass that layer, refuse a
 * one-axis write instead. Needs the Spring context because a table cell format read consults the
 * CSS dictionary.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ViewsheetFormatServiceAlignSeedTest {
   private static final TableDataPath CELL = new TableDataPath(-1, TableDataPath.DETAIL);

   private static GaugeVSAssemblyInfo gaugeAligned(int align) {
      GaugeVSAssemblyInfo info = gaugeInfo();
      VSCompositeFormat format = new VSCompositeFormat();
      format.getUserDefinedFormat().setAlignmentValue(align);
      info.getFormatInfo().setFormat(VSAssemblyInfo.OBJECTPATH, format);
      return info;
   }

   private static AlignmentInfo paintedAlign(FormatVSObjectEvent event) {
      return event.getFormat().getAlign();
   }

   private static void assertAlign(String halign, String valign, AlignmentInfo align) {
      assertNotNull(align);
      assertEquals(halign, align.getHalign(), "halign");
      assertEquals(valign, align.getValign(), "valign");
   }

   private static List<FormatVSObjectEvent> writeObject(Map<String, VSAssemblyInfo> infos,
                                                        String assemblies, String format,
                                                        int events)
      throws Exception
   {
      FormatPainterService painter = mock(FormatPainterService.class);
      service(sessionsFor(rvsOf(infos)), painter, mock(CalcTableService.class))
         .setFormat("tok", principal(), jsonRequest(assemblies, "object", format), "");
      return paintedEvents(painter, events);
   }

   @Test
   void aVerticalOnlyWriteKeepsTheStoredHorizontalAxis() throws Exception {
      Map<String, VSAssemblyInfo> infos =
         Map.of("Gauge1", gaugeAligned(StyleConstants.H_RIGHT | StyleConstants.V_TOP));

      List<FormatVSObjectEvent> events =
         writeObject(infos, "[\"Gauge1\"]", "{\"align\":{\"valign\":\"Bottom\"}}", 1);

      assertAlign("Right", "Bottom", paintedAlign(events.get(0)));
   }

   @Test
   void aHorizontalOnlyWriteKeepsTheStoredVerticalAxis() throws Exception {
      Map<String, VSAssemblyInfo> infos =
         Map.of("Gauge1", gaugeAligned(StyleConstants.H_LEFT | StyleConstants.V_TOP));

      List<FormatVSObjectEvent> events =
         writeObject(infos, "[\"Gauge1\"]", "{\"align\":{\"halign\":\"Center\"}}", 1);

      assertAlign("Center", "Top", paintedAlign(events.get(0)));
   }

   @Test
   void bothAxesAreWrittenAsSent() throws Exception {
      Map<String, VSAssemblyInfo> infos =
         Map.of("Gauge1", gaugeAligned(StyleConstants.H_LEFT | StyleConstants.V_BOTTOM));

      List<FormatVSObjectEvent> events = writeObject(
         infos, "[\"Gauge1\"]", "{\"align\":{\"halign\":\"Right\",\"valign\":\"Top\"}}", 1);

      assertAlign("Right", "Top", paintedAlign(events.get(0)));
   }

   @Test
   void aOneAxisWriteWithNothingStoredKeepsTheEffectiveDefaultAxis() throws Exception {
      GaugeVSAssemblyInfo info = gaugeInfo();
      String defaultHalign = new AlignmentInfo(
         info.getFormatInfo().getFormat(VSAssemblyInfo.OBJECTPATH, false).getAlignment())
         .getHalign();

      List<FormatVSObjectEvent> events = writeObject(
         Map.of("Gauge1", info), "[\"Gauge1\"]", "{\"align\":{\"valign\":\"Bottom\"}}", 1);

      // What the Composer's pane would send back: the axis as currently displayed.
      assertAlign(defaultHalign, "Bottom", paintedAlign(events.get(0)));
   }

   @Test
   void eachAssemblyKeepsItsOwnStoredAxis() throws Exception {
      Map<String, VSAssemblyInfo> infos = new LinkedHashMap<>();
      infos.put("Gauge1", gaugeAligned(StyleConstants.H_RIGHT | StyleConstants.V_TOP));
      infos.put("Gauge2", gaugeAligned(StyleConstants.H_LEFT | StyleConstants.V_TOP));

      List<FormatVSObjectEvent> events = writeObject(
         infos, "[\"Gauge1\",\"Gauge2\"]", "{\"align\":{\"valign\":\"Bottom\"}}", 2);

      Map<String, AlignmentInfo> byName = new HashMap<>();

      for(FormatVSObjectEvent event : events) {
         assertEquals(1, event.getObjects().length);
         byName.put(event.getObjects()[0], paintedAlign(event));
      }

      assertAlign("Right", "Bottom", byName.get("Gauge1"));
      assertAlign("Left", "Bottom", byName.get("Gauge2"));
   }

   @Test
   void aCalcCellVerticalOnlyWriteKeepsTheCellsHorizontalAxis() throws Exception {
      CalcTableVSAssemblyInfo info = mock(CalcTableVSAssemblyInfo.class);
      when(info.getFormatInfo()).thenReturn(new FormatInfo());
      VSCompositeFormat stored = new VSCompositeFormat();
      stored.getUserDefinedFormat().setAlignmentValue(StyleConstants.H_RIGHT);
      info.getFormatInfo().setFormat(CELL, stored);

      CalcTableService calcService = mock(CalcTableService.class);
      when(calcService.cellFormatPath(any(), eq("C"), eq(0), eq(0))).thenReturn(CELL);
      FormatPainterService painter = mock(FormatPainterService.class);
      ViewsheetFormatService.CellFormatRequest request = new ObjectMapper().readValue(
         "{\"assembly\":\"C\",\"row\":0,\"col\":0,\"reset\":false," +
         "\"format\":{\"align\":{\"valign\":\"Bottom\"}}}",
         ViewsheetFormatService.CellFormatRequest.class);

      service(sessionsFor(rvsOf(Map.of("C", info))), painter, calcService)
         .setCellFormat("tok", principal(), request, "");

      assertAlign("Right", "Bottom", paintedAlign(paintedEvents(painter, 1).get(0)));
   }

   private static RuntimeViewsheet chartRvs() {
      ChartVSAssembly assembly = mock(ChartVSAssembly.class);
      when(assembly.getVSAssemblyInfo()).thenReturn(mock(ChartVSAssemblyInfo.class));
      Viewsheet viewsheet = mock(Viewsheet.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getAssembly("Chart1")).thenReturn(assembly);
      return rvs;
   }

   private static ViewsheetFormatService.FormatRequest chartRequest(String target, String align)
      throws Exception
   {
      String field = "text".equals(target) ? ",\"field\":\"Sales\"" : "";
      return new ObjectMapper().readValue(
         "{\"assemblies\":[\"Chart1\"],\"target\":\"" + target + "\"" + field +
         ",\"format\":{\"align\":" + align + "}}", ViewsheetFormatService.FormatRequest.class);
   }

   @Test
   void chartTextAndRegionTargetsRefuseAOneAxisAlignBeforeAnyWrite() throws Exception {
      for(String target : new String[]{ "text", "legendTitle" }) {
         ViewsheetSessionService sessions = sessionsFor(chartRvs());
         FormatPainterService painter = mock(FormatPainterService.class);

         IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> service(sessions, painter, mock(CalcTableService.class)).setFormat(
               "tok", principal(), chartRequest(target, "{\"valign\":\"Bottom\"}"), ""),
            target);

         assertTrue(e.getMessage().contains("'align'") && e.getMessage().contains("halign"),
                    e.getMessage());
         verify(sessions, never()).mutate(anyString(), any(Principal.class), any());
         verifyNoInteractions(painter);
      }
   }

   @Test
   void chartTextTargetStillTakesBothAxes() throws Exception {
      FormatPainterService painter = mock(FormatPainterService.class);

      service(sessionsFor(chartRvs()), painter, mock(CalcTableService.class)).setFormat(
         "tok", principal(), chartRequest("text", "{\"halign\":\"Right\",\"valign\":\"Top\"}"),
         "");

      assertAlign("Right", "Top", paintedAlign(paintedEvents(painter, 1).get(0)));
   }
}
