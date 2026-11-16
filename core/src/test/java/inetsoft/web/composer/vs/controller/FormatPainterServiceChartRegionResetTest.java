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
import inetsoft.uql.XConstants;
import inetsoft.uql.XFormatInfo;
import inetsoft.uql.viewsheet.graph.CompositeTextFormat;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.composer.model.vs.VSObjectFormatInfoModel;
import inetsoft.web.graph.handler.ChartRegionHandler;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Bug #81448: resetting a chart text region (axis labels, target label, data labels, ...) wrote
 * a copy of the region's default value format into the region's own user-defined format. A
 * non-empty user-defined format overrides the field/column format, so one reset swapped the
 * column's own number format for the default's frozen copy. A reset must leave the region's own
 * value format empty so the field/column format applies again.
 *
 * <p>{@code changeChartRegionFormat} only reads its arguments, so a plain {@code mock()}-built
 * service is enough.
 */
@Tag("core")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
class FormatPainterServiceChartRegionResetTest {
   @Test
   void resetClearsTheRegionsOwnValueFormat() throws Exception {
      CompositeTextFormat format = formatWithUserDefinedFormat("'EUR '#,##0");

      // the reset model carries the default layer's format, as setChartFormat builds it
      VSObjectFormatInfoModel model = model(XConstants.DECIMAL_FORMAT, "$#,##0");
      VSObjectFormatInfoModel orig = model(null, null);

      changeChartRegionFormat(model, orig, format, true);

      XFormatInfo userFormat = format.getUserDefinedFormat().getFormat();
      assertTrue(userFormat.isEmpty(), "reset must not write a value format into the region");
      assertEquals(null, userFormat.getFormatSpec());
   }

   @Test
   void resetDoesNotWriteTheCommaFormatSpec() throws Exception {
      CompositeTextFormat format = new CompositeTextFormat();
      VSObjectFormatInfoModel model = model(XConstants.COMMA_FORMAT, null);

      changeChartRegionFormat(model, model(null, null), format, true);

      assertTrue(format.getUserDefinedFormat().getFormat().isEmpty());
   }

   @Test
   void explicitFormatChangeIsStillWritten() throws Exception {
      CompositeTextFormat format = new CompositeTextFormat();
      VSObjectFormatInfoModel model = model(XConstants.DECIMAL_FORMAT, "#,##0.00");

      changeChartRegionFormat(model, model(null, null), format, false);

      XFormatInfo userFormat = format.getUserDefinedFormat().getFormat();
      assertEquals(XConstants.DECIMAL_FORMAT, userFormat.getFormat());
      assertEquals("#,##0.00", userFormat.getFormatSpec());
   }

   private static CompositeTextFormat formatWithUserDefinedFormat(String spec) {
      CompositeTextFormat format = new CompositeTextFormat();
      format.getUserDefinedFormat().setFormat(new XFormatInfo(XConstants.DECIMAL_FORMAT, spec));
      return format;
   }

   private static VSObjectFormatInfoModel model(String format, String formatSpec) {
      VSObjectFormatInfoModel model = new VSObjectFormatInfoModel();
      model.setFormat(format);
      model.setFormatSpec(formatSpec);
      return model;
   }

   private static void changeChartRegionFormat(VSObjectFormatInfoModel model,
                                               VSObjectFormatInfoModel orig,
                                               CompositeTextFormat format, boolean reset)
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
      method.invoke(service, model, orig, format, "axis_labels", reset, null);
   }
}
