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
package inetsoft.web.composer.vs.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.test.*;
import inetsoft.uql.XConstants;
import inetsoft.uql.XFormatInfo;
import inetsoft.uql.viewsheet.graph.CompositeTextFormat;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.composer.model.vs.VSObjectFormatInfoModel;
import inetsoft.web.graph.handler.ChartRegionHandler;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #82286: a chart text region's number/date format could be written but not read back, so
 * {@code get_chart_region_format} and {@code get_format} answered format:null for a region that
 * rendered with a format. The read must return the value format the user stored on the region,
 * in the shape the write takes, and must not report the default the axis reader derives from the
 * data as if the user had set it.
 */
@Tag("core")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
class FormatPainterServiceChartRegionFormatReadTest {
   @Test
   void unformattedRegionReadsNoValueFormat() {
      VSObjectFormatInfoModel model = read(new CompositeTextFormat());

      assertNull(model.getFormat());
      assertNull(model.getFormatSpec());
      assertNull(model.getDateSpec());
      assertTrue(model.isDurationPadZeros());
   }

   /** fixAxisDefaultFormat fills the default layer for a numeric axis the user never formatted. */
   @Test
   void defaultLayerFormatIsNotReportedAsUserDefined() {
      CompositeTextFormat format = new CompositeTextFormat();
      format.getDefaultFormat().setFormat(new XFormatInfo(XConstants.DECIMAL_FORMAT, "#,##0.00"));
      VSObjectFormatInfoModel model = read(format);

      assertNull(model.getFormat());
      assertNull(model.getFormatSpec());
      assertNull(model.getDateSpec());
   }

   @Test
   void decimalFormatIsRead() {
      VSObjectFormatInfoModel model = read(userFormat(XConstants.DECIMAL_FORMAT, "'#'0.00"));

      assertEquals(XConstants.DECIMAL_FORMAT, model.getFormat());
      assertEquals("'#'0.00", model.getFormatSpec());
      assertNull(model.getDateSpec());
   }

   @Test
   void customDatePatternIsReadWithItsPattern() {
      VSObjectFormatInfoModel model = read(userFormat(XConstants.DATE_FORMAT, "MM/yy"));

      assertEquals(XConstants.DATE_FORMAT, model.getFormat());
      assertEquals("Custom", model.getDateSpec());
      assertEquals("MM/yy", model.getFormatSpec());
   }

   @Test
   void namedDateStyleIsReadAsDateSpecOnly() {
      VSObjectFormatInfoModel model = read(userFormat(XConstants.DATE_FORMAT, "SHORT"));

      assertEquals(XConstants.DATE_FORMAT, model.getFormat());
      assertEquals("SHORT", model.getDateSpec());
      assertNull(model.getFormatSpec());
   }

   @Test
   void durationWithoutPaddingKeepsItsPaddingFlag() {
      VSObjectFormatInfoModel model =
         read(userFormat(XConstants.DURATION_FORMAT_PAD_NON, null));

      assertEquals(XConstants.DURATION_FORMAT, model.getFormat());
      assertFalse(model.isDurationPadZeros());
   }

   @Test
   void resetRegionReadsNoValueFormatAgain() throws Exception {
      CompositeTextFormat format = userFormat(XConstants.DECIMAL_FORMAT, "'~'#,##0");
      assertEquals("'~'#,##0", read(format).getFormatSpec());

      write(new VSObjectFormatInfoModel(), format, true);

      assertNull(read(format).getFormat());
      assertNull(read(format).getFormatSpec());
   }

   /** A read fed back into the write must store the same value format it came from. */
   @Test
   void readResultWrittenBackReadsTheSame() throws Exception {
      assertRoundTrips(XConstants.DECIMAL_FORMAT, "'#'0.00");
      assertRoundTrips(XConstants.DECIMAL_FORMAT, "#,##0");
      assertRoundTrips(XConstants.DATE_FORMAT, "MM/yy");
      assertRoundTrips(XConstants.DATE_FORMAT, "SHORT");
      assertRoundTrips(XConstants.DURATION_FORMAT, null);
      assertRoundTrips(XConstants.DURATION_FORMAT_PAD_NON, null);
   }

   private static void assertRoundTrips(String format, String spec) throws Exception {
      VSObjectFormatInfoModel first = read(userFormat(format, spec));
      assertNotNull(first.getFormat(), format + " " + spec);
      CompositeTextFormat target = new CompositeTextFormat();
      write(first, target, false);
      VSObjectFormatInfoModel second = read(target);

      assertEquals(first.getFormat(), second.getFormat(), format + " " + spec);
      assertEquals(first.getFormatSpec(), second.getFormatSpec(), format + " " + spec);
      assertEquals(first.getDateSpec(), second.getDateSpec(), format + " " + spec);
      assertEquals(first.isDurationPadZeros(), second.isDurationPadZeros(), format + " " + spec);
   }

   private static CompositeTextFormat userFormat(String format, String spec) {
      CompositeTextFormat result = new CompositeTextFormat();
      result.getUserDefinedFormat().setFormat(new XFormatInfo(format, spec));
      return result;
   }

   private static VSObjectFormatInfoModel read(CompositeTextFormat format) {
      VSObjectFormatInfoModel model = new VSObjectFormatInfoModel();
      FormatPainterService.applyUserDefinedValueFormat(model, format);
      return model;
   }

   /** What set_chart_region_format does with a model carrying the read's value format keys. */
   private static void write(VSObjectFormatInfoModel model, CompositeTextFormat format,
                             boolean reset)
      throws Exception
   {
      FormatPainterService service = new FormatPainterService(
         mock(CoreLifecycleService.class), mock(ChartRegionHandler.class),
         mock(ViewsheetService.class), mock(VSObjectModelFactoryService.class),
         mock(VSBindingService.class), mock(VSLayoutService.class));

      Method method = FormatPainterService.class.getDeclaredMethod(
         "changeChartRegionFormat", VSObjectFormatInfoModel.class, VSObjectFormatInfoModel.class,
         CompositeTextFormat.class, String.class, boolean.class,
         inetsoft.uql.viewsheet.graph.ChartInfo.class);
      method.setAccessible(true);
      method.invoke(service, model, new VSObjectFormatInfoModel(), format, "axis_labels", reset,
                    null);
   }
}
