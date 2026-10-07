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
import inetsoft.uql.viewsheet.VSAggregateRef;
import inetsoft.uql.viewsheet.VSCrosstabInfo;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.web.composer.vs.controller.FormatPainterService;
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

}
