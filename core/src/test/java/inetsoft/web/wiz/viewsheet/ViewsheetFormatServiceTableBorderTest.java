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
import inetsoft.report.TableDataPath;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.FormatInfo;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo;
import inetsoft.web.composer.model.vs.VSObjectFormatInfoModel;
import inetsoft.web.composer.vs.controller.FormatPainterService;
import inetsoft.web.composer.vs.objects.event.FormatVSObjectEvent;
import inetsoft.web.wiz.binding.CalcTableService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Insets;
import java.util.List;
import java.util.Map;

import static inetsoft.web.wiz.viewsheet.ViewsheetFormatServiceTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #79096: table cell border seeding (-1 = use the table default) and the shared-edge colour
 * warning. Needs the Spring context because a table cell format read consults the CSS dictionary.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ViewsheetFormatServiceTableBorderTest {
   // ---- Bug #79096: -1 border seed on table cells and the shared-edge colour warning ----

   private static final TableDataPath CELL = new TableDataPath(-1, TableDataPath.DETAIL);

   private static CalcTableVSAssemblyInfo calcInfo() {
      CalcTableVSAssemblyInfo info = mock(CalcTableVSAssemblyInfo.class);
      when(info.getFormatInfo()).thenReturn(new FormatInfo());
      return info;
   }

   private static ViewsheetFormatService.CellFormatRequest cellRequest(String format,
                                                                       boolean reset)
      throws Exception
   {
      return new ObjectMapper().readValue(
         "{\"assembly\":\"C\",\"row\":2,\"col\":0,\"reset\":" + reset +
         (format == null ? "" : ",\"format\":" + format) + "}",
         ViewsheetFormatService.CellFormatRequest.class);
   }

   private static List<String> writeCell(FormatPainterService painter, CalcTableVSAssemblyInfo info,
                                         String format, boolean reset)
      throws Exception
   {
      CalcTableService calcService = mock(CalcTableService.class);
      when(calcService.cellFormatPath(any(), eq("C"), eq(2), eq(0))).thenReturn(CELL);
      return service(sessionsFor(rvsOf(Map.of("C", info))), painter, calcService)
         .setCellFormat("tok", principal(), cellRequest(format, reset), "");
   }

   @Test
   void aTopOnlyWriteToAnUnformattedTableCellSeedsTheOtherStyleSidesAsUseDefault()
      throws Exception
   {
      FormatPainterService painter = mock(FormatPainterService.class);

      writeCell(painter, calcInfo(),
                "{\"borderTopStyle\":\"thick\",\"borderTopColor\":\"#000000\"}", false);

      VSObjectFormatInfoModel model = paintedEvents(painter, 1).get(0).getFormat();
      assertNotEquals("-1", model.getBorderTopStyle(), "the sent side is not seeded");
      assertEquals("-1", model.getBorderLeftStyle());
      assertEquals("-1", model.getBorderBottomStyle());
      assertEquals("-1", model.getBorderRightStyle());
   }

   @Test
   void aColourOnlyWriteToAnUnformattedTableCellSeedsNoStyleSidesOnEveryWrite() throws Exception {
      FormatPainterService painter = mock(FormatPainterService.class);
      CalcTableVSAssemblyInfo info = calcInfo();

      for(int i = 0; i < 2; i++) {
         List<String> warnings = writeCell(painter, info, "{\"borderTopColor\":\"#eeeeee\"}",
                                           false);
         assertTrue(warnings.isEmpty(), "a colour at or above the grid colour gets no edge warning");
      }

      for(FormatVSObjectEvent event : paintedEvents(painter, 2)) {
         assertNull(event.getFormat().getBorderTopStyle());
         assertNull(event.getFormat().getBorderLeftStyle());
         assertNull(event.getFormat().getBorderBottomStyle());
         assertNull(event.getFormat().getBorderRightStyle());
      }
   }

   @Test
   void aTableCellWithAUserLayerStillSeedsFromWhatIsStored() throws Exception {
      FormatPainterService painter = mock(FormatPainterService.class);
      CalcTableVSAssemblyInfo info = calcInfo();
      VSCompositeFormat stored = new VSCompositeFormat();
      stored.getUserDefinedFormat().setBordersValue(new Insets(1, 2, 3, 4));
      info.getFormatInfo().setFormat(CELL, stored);

      writeCell(painter, info, "{\"borderTopColor\":\"#eeeeee\"}", false);

      VSObjectFormatInfoModel model = paintedEvents(painter, 1).get(0).getFormat();
      assertEquals("1", model.getBorderTopStyle());
      assertEquals("2", model.getBorderLeftStyle());
      assertEquals("3", model.getBorderBottomStyle());
      assertEquals("4", model.getBorderRightStyle());
   }

   @Test
   void aSecondSingleSideWriteKeepsTheUseDefaultSides() throws Exception {
      FormatPainterService painter = mock(FormatPainterService.class);
      CalcTableVSAssemblyInfo info = calcInfo();
      VSCompositeFormat stored = new VSCompositeFormat();
      stored.getUserDefinedFormat().setBordersValue(new Insets(4099, -1, -1, -1));
      info.getFormatInfo().setFormat(CELL, stored);

      writeCell(painter, info, "{\"borderBottomStyle\":\"solid\"}", false);

      VSObjectFormatInfoModel model = paintedEvents(painter, 1).get(0).getFormat();
      assertEquals("4099", model.getBorderTopStyle());
      assertEquals("-1", model.getBorderLeftStyle());
      assertEquals("-1", model.getBorderRightStyle());
      assertNotEquals("-1", model.getBorderBottomStyle());
   }

   @Test
   void aTableObjectBorderWriteIsNotSeededWithUseDefault() throws Exception {
      FormatPainterService painter = mock(FormatPainterService.class);

      serviceWith(painter, rvsOf(Map.of("C", calcInfo()))).setFormat(
         "tok", principal(),
         jsonRequest("[\"C\"]", "object", "{\"borderTopColor\":\"#00ff00\"}"), "");

      assertNull(paintedEvents(painter, 1).get(0).getFormat().getBorderLeftStyle());
   }

   @Test
   void aDarkBorderColourOnATableCellWarnsOnceThatTheSharedEdgeMayNotShow() throws Exception {
      FormatPainterService painter = mock(FormatPainterService.class);

      List<String> warnings = writeCell(
         painter, calcInfo(),
         "{\"borderTopStyle\":\"thick\",\"borderTopColor\":\"#000000\"," +
         "\"borderLeftStyle\":\"thick\",\"borderLeftColor\":\"#112233\"}", false);

      assertEquals(1, warnings.size());
      assertTrue(warnings.get(0).contains("top border colour #000000"), warnings.get(0));
      assertTrue(warnings.get(0).contains("left border colour #112233"), warnings.get(0));
      assertTrue(warnings.get(0).contains("adjacent cell"));
      assertTrue(warnings.get(0).contains("shared"));
   }

   @Test
   void aBorderColourNoDarkerThanTheDefaultGridDoesNotWarn() throws Exception {
      FormatPainterService painter = mock(FormatPainterService.class);

      assertTrue(writeCell(painter, calcInfo(),
                           "{\"borderTopStyle\":\"thick\",\"borderTopColor\":\"#eeeeee\"}",
                           false).isEmpty());
      assertTrue(writeCell(painter, calcInfo(),
                           "{\"borderTopStyle\":\"thick\",\"borderTopColor\":\"#dadada\"}",
                           false).isEmpty());
   }

   @Test
   void aResetAndAStyleOnlyWriteDoNotWarnAboutBorderColours() throws Exception {
      FormatPainterService painter = mock(FormatPainterService.class);

      assertTrue(writeCell(painter, calcInfo(), null, true).isEmpty());
      assertTrue(writeCell(painter, calcInfo(), "{\"borderTopStyle\":\"thick\"}", false)
                    .isEmpty());
   }

   @Test
   void aDarkBorderColourOnTheTableObjectOrAGaugeDoesNotWarnAboutSharedEdges() throws Exception {
      FormatPainterService painter = mock(FormatPainterService.class);
      ViewsheetFormatService service =
         serviceWith(painter, rvsOf(Map.of("C", calcInfo(), "G", gaugeInfo())));

      for(String name : new String[]{ "C", "G" }) {
         ViewsheetFormatService.FormatResult result = service.setFormat(
            "tok", principal(),
            jsonRequest("[\"" + name + "\"]", "object",
                        "{\"borderTopStyle\":\"thick\",\"borderTopColor\":\"#000000\"}"),
            "");
         assertTrue(result.warnings().stream().noneMatch(w -> w.contains("shared")), name);
      }
   }

}
