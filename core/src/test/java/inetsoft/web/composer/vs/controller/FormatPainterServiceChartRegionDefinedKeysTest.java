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
import inetsoft.graph.internal.GDefaults;
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

import java.awt.Color;
import java.awt.Font;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #82419: a chart text region's effective read reports defaults for a region nobody
 * formatted, so a format copy could not tell "set" from "default". {@code definedKeys} names the
 * keys the user layer actually holds.
 */
@Tag("core")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
class FormatPainterServiceChartRegionDefinedKeysTest {
   @Test
   void aRegionNobodyFormattedHasNoDefinedKeysEvenThoughTheEffectiveReadHasValues() {
      CompositeTextFormat format = new CompositeTextFormat();

      // the effective getters answer with the built-in defaults: this is why the old read could
      // not tell an unformatted region from a formatted one
      assertNotNull(format.getColor());
      assertNotNull(format.getFont());
      assertEquals(List.of(), FormatPainterService.definedKeys(format));
   }

   @Test
   void everyUserStoredKeyIsReportedByTheNameTheModelUses() {
      CompositeTextFormat format = new CompositeTextFormat();
      format.setColor(Color.RED);
      format.setBackground(Color.WHITE);
      format.setAlpha(50);
      format.setFont(new Font("Arial", Font.BOLD, 14));
      format.setAlignment(2);
      format.setFormat(new XFormatInfo(XConstants.DECIMAL_FORMAT, "#,##0"));

      assertEquals(List.of("color", "backgroundColor", "backgroundAlpha", "font", "align",
                           "format"),
                   FormatPainterService.definedKeys(format));
   }

   /** The case the effective-value heuristic got wrong: a stored value equal to the default. */
   @Test
   void aValueTheUserStoredThatEqualsTheDefaultIsStillDefined() {
      CompositeTextFormat format = new CompositeTextFormat();
      format.setColor(GDefaults.DEFAULT_TEXT_COLOR);

      assertEquals(format.getDefaultFormat().getColor(), format.getColor());
      assertEquals(List.of("color"), FormatPainterService.definedKeys(format));
   }

   @Test
   void theDefaultLayerIsNeverReportedAsStored() {
      CompositeTextFormat format = new CompositeTextFormat();
      format.getDefaultFormat().setFormat(new XFormatInfo(XConstants.DECIMAL_FORMAT, "#,##0.00"));
      format.getDefaultFormat().setColor(Color.BLUE);

      assertEquals(List.of(), FormatPainterService.definedKeys(format));
   }

   /** Through the product's own write: only the keys it changed become stored; reset clears. */
   @Test
   void theRegionWriteStoresOnlyTheKeysItChangedAndResetClearsThem() throws Exception {
      CompositeTextFormat format = new CompositeTextFormat();
      VSObjectFormatInfoModel model = new VSObjectFormatInfoModel();
      model.setColor("#336699");

      write(model, format, false);
      assertEquals(List.of("color"), FormatPainterService.definedKeys(format));

      write(new VSObjectFormatInfoModel(), format, true);
      assertEquals(List.of(), FormatPainterService.definedKeys(format));
   }

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
