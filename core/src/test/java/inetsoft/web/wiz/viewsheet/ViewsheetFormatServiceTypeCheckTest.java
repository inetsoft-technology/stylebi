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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.CalendarVSAssembly;
import inetsoft.uql.viewsheet.CheckBoxVSAssembly;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.GaugeVSAssembly;
import inetsoft.uql.viewsheet.ImageVSAssembly;
import inetsoft.uql.viewsheet.SliderVSAssembly;
import inetsoft.uql.viewsheet.TabVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.internal.CalendarVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.CheckBoxVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.CrosstabVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.GaugeVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.ImageVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.SliderVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TabVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TextVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.VSAggregateRef;
import inetsoft.uql.viewsheet.VSCrosstabInfo;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.web.composer.model.vs.VSObjectFormatInfoModel;
import inetsoft.web.composer.vs.controller.FormatPainterService;
import inetsoft.web.wiz.binding.CalcTableService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static inetsoft.web.wiz.viewsheet.ViewsheetFormatServiceTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77597: the {@code set_format} refusals that judge a value by its effective type. A date
 * dimension's effective type comes from {@code DateRangeRef}, whose static initializer reads the
 * server configuration, so these need the SREE context that {@link ViewsheetFormatServiceTest}
 * deliberately does without.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ViewsheetFormatServiceTypeCheckTest {
   @Test
   void aPartLevelDateDimensionIsNumericAndAFullLevelIsADate() {
      assertEquals(XSchema.INTEGER, WizFormatChecks.effectiveType(
         dimension("MonthOfYear(Order Date)", XSchema.DATE, DateRangeRef.MONTH_OF_YEAR_PART)));
      assertEquals(XSchema.TIME_INSTANT, WizFormatChecks.effectiveType(
         dimension("Month(Order Date)", XSchema.DATE, DateRangeRef.MONTH_INTERVAL)));
      assertEquals(XSchema.DATE, WizFormatChecks.effectiveType(
         dimension("Order Date", XSchema.DATE, DateRangeRef.NONE_INTERVAL)));
      assertEquals(XSchema.STRING, WizFormatChecks.effectiveType(
         dimension("Region", XSchema.STRING, 0)));
      assertNull(WizFormatChecks.effectiveType(null));
   }

   private static RuntimeViewsheet crosstabBindingRvs(DataRef[] rows, DataRef[] aggregates) {
      CrosstabVSAssembly crosstab = mock(CrosstabVSAssembly.class);
      VSCrosstabInfo cinfo = mock(VSCrosstabInfo.class);
      when(crosstab.getVSCrosstabInfo()).thenReturn(cinfo);
      when(cinfo.getRuntimeRowHeaders()).thenReturn(rows);
      when(cinfo.getRuntimeColHeaders()).thenReturn(new DataRef[0]);
      when(cinfo.getColHeaders()).thenReturn(new DataRef[0]);
      when(cinfo.getRuntimeAggregates()).thenReturn(new DataRef[0]);
      when(cinfo.getAggregates()).thenReturn(aggregates);
      return rvsWith("Crosstab1", crosstab);
   }

   private static VSDimensionRef dimension(String fullName, String type, int level) {
      VSDimensionRef dim = mock(VSDimensionRef.class);
      when(dim.getFullName()).thenReturn(fullName);
      when(dim.getDataType()).thenReturn(type);
      when(dim.getDateLevel()).thenReturn(level);
      return dim;
   }

   private static VSAggregateRef aggregate(String fullName, String type) {
      VSAggregateRef aggr = mock(VSAggregateRef.class);
      when(aggr.getFullName()).thenReturn(fullName);
      when(aggr.getDataType()).thenReturn(type);
      return aggr;
   }

   @Test
   void refusesAWholeCrosstabDateFormatOverANumericAggregate() {
      RuntimeViewsheet rvs = crosstabBindingRvs(
         new DataRef[]{ dimension("Month(Order Date)", XSchema.DATE,
                                  DateRangeRef.MONTH_INTERVAL) },
         new DataRef[]{ aggregate("Sum(Revenue)", XSchema.DOUBLE) });

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> serviceWith(mock(FormatPainterService.class), rvs).setFormat(
            "tok", principal(),
            new ViewsheetFormatService.FormatRequest(List.of("Crosstab1"), dateFormat(),
                                                     false), ""));

      assertTrue(thrown.getMessage().contains("Sum(Revenue)"), thrown.getMessage());
      assertFalse(thrown.getMessage().contains("Month(Order Date)"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("target:\"header\""), thrown.getMessage());
   }

   /** A part-level date dimension (MonthOfYear) renders integers, so the header band refuses. */
   @Test
   void refusesACrosstabHeaderDateFormatOverAPartLevelDimension() {
      RuntimeViewsheet rvs = crosstabBindingRvs(
         new DataRef[]{ dimension("MonthOfYear(Order Date)", XSchema.DATE,
                                  DateRangeRef.MONTH_OF_YEAR_PART) },
         new DataRef[]{ aggregate("Sum(Revenue)", XSchema.DOUBLE) });

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> serviceWith(mock(FormatPainterService.class), rvs).setFormat(
            "tok", principal(),
            new ViewsheetFormatService.FormatRequest(List.of("Crosstab1"), dateFormat(),
                                                     false, "header"), ""));

      assertTrue(thrown.getMessage().contains("MonthOfYear(Order Date)"), thrown.getMessage());
      assertFalse(thrown.getMessage().contains("Sum(Revenue)"), thrown.getMessage());
   }

   /** Only full-level date dimensions in the header band: the binding check lets it through. */
   @Test
   void allowsACrosstabHeaderDateFormatOverFullLevelDateDimensions() throws Exception {
      FormatPainterService painter = mock(FormatPainterService.class);
      RuntimeViewsheet rvs = crosstabBindingRvs(
         new DataRef[]{ dimension("Month(Order Date)", XSchema.DATE,
                                  DateRangeRef.MONTH_INTERVAL) },
         new DataRef[]{ aggregate("Sum(Revenue)", XSchema.DOUBLE) });

      // This mock has no sandbox, so any later refusal comes from the path computation; the
      // binding check itself must not refuse.
      try {
         serviceWith(painter, rvs).setFormat(
            "tok", principal(),
            new ViewsheetFormatService.FormatRequest(List.of("Crosstab1"), dateFormat(),
                                                     false, "header"), "");
      }
      catch(IllegalArgumentException e) {
         assertFalse(e.getMessage().contains("numeric"), e.getMessage());
      }
   }

   private static <A extends VSAssembly, I extends VSAssemblyInfo> A assemblyOf(
      Class<A> type, Class<I> infoType)
   {
      A assembly = mock(type);
      when(assembly.getVSAssemblyInfo()).thenReturn(mock(infoType));
      return assembly;
   }

   private static RuntimeViewsheet rvsOfAssemblies(String name, VSAssembly assembly) {
      return rvsWith(name, assembly);
   }

   /** Runs set_format and returns the refusal message, or null when it was not refused here. */
   private static String refusal(RuntimeViewsheet rvs, String target, boolean reset,
                                 String field, String name)
      throws Exception
   {
      ViewsheetSessionService sessions = sessionsFor(rvs);
      FormatPainterService painter = mock(FormatPainterService.class);
      VSObjectFormatInfoModel format = new VSObjectFormatInfoModel();
      format.setColor("#FF0000");

      try {
         service(sessions, painter, mock(CalcTableService.class)).setFormat(
            "tok", principal(),
            new ViewsheetFormatService.FormatRequest(List.of(name), reset ? null : format, reset,
                                                     target, field, null), "");
      }
      catch(IllegalArgumentException e) {
         verify(sessions, never()).mutate(anyString(), any(java.security.Principal.class), any());
         verifyNoInteractions(painter);
         return e.getMessage();
      }
      catch(Exception e) {
         // a later failure on the bare mocks is not a refusal by the new checks
      }

      return null;
   }

   private static String titleRefusal(VSAssembly assembly, boolean reset)
      throws Exception
   {
      return refusal(rvsOfAssemblies("A1", assembly), "title", reset, null, "A1");
   }

   @Test
   void refusesTitleOnAssembliesWithoutATitleBar() throws Exception {
      List<VSAssembly> untitled = List.of(
         assemblyOf(GaugeVSAssembly.class, GaugeVSAssemblyInfo.class),
         assemblyOf(SliderVSAssembly.class, SliderVSAssemblyInfo.class),
         assemblyOf(TextVSAssembly.class, TextVSAssemblyInfo.class),
         assemblyOf(TabVSAssembly.class, TabVSAssemblyInfo.class),
         assemblyOf(ImageVSAssembly.class, ImageVSAssemblyInfo.class));

      for(VSAssembly assembly : untitled) {
         String message = titleRefusal(assembly, false);
         assertNotNull(message, assembly.getClass().getName());
         assertTrue(message.contains("'A1'") && message.contains("has no title bar"), message);
         assertTrue(message.contains(assembly.getClass().getSimpleName()), message);
         assertTrue(message.contains("target 'object'"), message);
      }
   }

   @Test
   void refusesTitleResetOnAnAssemblyWithoutATitleBar() throws Exception {
      assertNotNull(titleRefusal(
         assemblyOf(GaugeVSAssembly.class, GaugeVSAssemblyInfo.class), true));
   }

   @Test
   void allowsTitleOnAssembliesWithATitleBar() throws Exception {
      List<VSAssembly> titled = List.of(
         assemblyOf(TableVSAssembly.class, TableVSAssemblyInfo.class),
         assemblyOf(CrosstabVSAssembly.class, CrosstabVSAssemblyInfo.class),
         assemblyOf(ChartVSAssembly.class, ChartVSAssemblyInfo.class),
         assemblyOf(CheckBoxVSAssembly.class, CheckBoxVSAssemblyInfo.class),
         assemblyOf(CalendarVSAssembly.class, CalendarVSAssemblyInfo.class));

      for(VSAssembly assembly : titled) {
         assertNull(titleRefusal(assembly, false), assembly.getClass().getName());
      }
   }

   @Test
   void allowsTitleOnATitledAssemblyWhoseTitleIsHidden() throws Exception {
      TableVSAssembly table = assemblyOf(TableVSAssembly.class, TableVSAssemblyInfo.class);
      when(((TableVSAssemblyInfo) table.getVSAssemblyInfo()).isTitleVisible()).thenReturn(false);

      assertNull(titleRefusal(table, false));
   }

   @Test
   void titleCheckFailsOpenOnANullInfoAndANullViewsheetButRefusesAMissingAssembly()
      throws Exception
   {
      String missing = refusal(rvsOfAssemblies("A1", null), "title", false, null, "A1");
      assertNotNull(missing);
      assertTrue(missing.contains("no assembly named 'A1'"), missing);

      VSAssembly noInfo = mock(GaugeVSAssembly.class);
      assertNull(titleRefusal(noInfo, false));

      RuntimeViewsheet noViewsheet = mock(RuntimeViewsheet.class);
      assertNull(refusal(noViewsheet, "title", false, null, "A1"));
      assertNull(refusal(null, "title", false, null, "A1"));
   }

   @Test
   void refusesTextOnANonChartAndOnAMissingAssembly() throws Exception {
      String gauge = refusal(
         rvsOfAssemblies("A1", assemblyOf(GaugeVSAssembly.class, GaugeVSAssemblyInfo.class)),
         "text", false, "x", "A1");
      assertNotNull(gauge);
      assertTrue(gauge.contains("only applies to a chart") && gauge.contains("'A1'") &&
                 gauge.contains("GaugeVSAssembly"), gauge);

      String missing = refusal(rvsOfAssemblies("A1", null), "text", false, "x", "A1");
      assertNotNull(missing);
      assertTrue(missing.contains("only applies to a chart") && missing.contains("not found"),
                 missing);
   }

   @Test
   void textOnAChartIsNotRefusedByTheNewCheck() throws Exception {
      assertNull(refusal(
         rvsOfAssemblies("A1", assemblyOf(ChartVSAssembly.class, ChartVSAssemblyInfo.class)),
         "text", false, "x", "A1"));
   }

}
