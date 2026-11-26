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
import inetsoft.report.internal.table.PresenterRef;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.VSFormat;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.composer.model.vs.VSObjectFormatInfoModel;
import inetsoft.web.graph.handler.ChartRegionHandler;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #82311: {@code presenterProperties} on a format model are stored on the presenter
 * reference the way the Presenter Properties dialog stores them.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class FormatPainterServicePresenterPropertiesTest {
   private static final String BAR = "inetsoft.report.painter.BarPresenter";
   private static final String LIGHT = "inetsoft.report.painter.TrafficLightPresenter";

   @BeforeEach
   void setup() throws Exception {
      service = new FormatPainterService(
         mock(CoreLifecycleService.class), mock(ChartRegionHandler.class),
         mock(ViewsheetService.class), mock(VSObjectModelFactoryService.class),
         mock(VSBindingService.class), mock(VSLayoutService.class));
      setUserFormat = FormatPainterService.class.getDeclaredMethod(
         "setUserFormat", VSCompositeFormat.class, VSObjectFormatInfoModel.class,
         VSObjectFormatInfoModel.class, boolean.class, boolean.class);
      setUserFormat.setAccessible(true);
   }

   private static Map<String, Object> props(Object... pairs) {
      Map<String, Object> map = new LinkedHashMap<>();

      for(int i = 0; i < pairs.length; i += 2) {
         map.put((String) pairs[i], pairs[i + 1]);
      }

      return map;
   }

   private static VSObjectFormatInfoModel model(String presenter, Map<String, Object> props) {
      VSObjectFormatInfoModel model = new VSObjectFormatInfoModel();
      model.setPresenter(presenter);
      model.setPresenterProperties(props);
      return model;
   }

   private void write(VSCompositeFormat format, VSObjectFormatInfoModel model, boolean reset)
      throws Exception
   {
      try {
         // the original is blank, as the wiz set_format seeds it
         setUserFormat.invoke(service, format, model, new VSObjectFormatInfoModel(), reset, false);
      }
      catch(InvocationTargetException e) {
         if(e.getCause() instanceof Exception) {
            throw (Exception) e.getCause();
         }

         throw e;
      }
   }

   private static PresenterRef stored(VSCompositeFormat format) {
      return format.getUserDefinedFormat().getPresenterValue();
   }

   @Test
   void presenterAndPropertiesAreStoredTogether() throws Exception {
      VSCompositeFormat format = new VSCompositeFormat();
      write(format, model(BAR, props("maximum", 100, "color", "#2E8B57")), false);

      PresenterRef ref = stored(format);
      assertEquals(BAR, ref.getName());
      assertEquals(100.0, ref.getParameter("maximum"));
      assertEquals(Color.decode("#2E8B57"), ref.getParameter("color"));
      assertTrue(format.getUserDefinedFormat().isPresenterValueDefined());
   }

   @Test
   void propertiesWithoutAPresenterGoOnTheStoredOne() throws Exception {
      VSCompositeFormat format = new VSCompositeFormat();
      write(format, model(BAR, props("color", "#2E8B57")), false);
      write(format, model(null, props("maximum", 100)), false);

      PresenterRef ref = stored(format);
      assertEquals(BAR, ref.getName());
      assertEquals(100.0, ref.getParameter("maximum"));
      assertEquals(Color.decode("#2E8B57"), ref.getParameter("color"), "earlier property kept");
   }

   @Test
   void theSamePresenterClassKeepsTheStoredProperties() throws Exception {
      VSCompositeFormat format = new VSCompositeFormat();
      write(format, model(BAR, props("color", "#2E8B57")), false);
      write(format, model("BarPresenter", props("maximum", 100)), false);

      PresenterRef ref = stored(format);
      assertEquals(100.0, ref.getParameter("maximum"));
      assertEquals(Color.decode("#2E8B57"), ref.getParameter("color"));
   }

   @Test
   void aDifferentPresenterClassStartsAFreshReference() throws Exception {
      VSCompositeFormat format = new VSCompositeFormat();
      write(format, model(BAR, props("color", "#2E8B57", "maximum", 100)), false);
      write(format, model(LIGHT, props("high", 900)), false);

      PresenterRef ref = stored(format);
      assertEquals(LIGHT, ref.getName());
      assertEquals(900.0, ref.getParameter("high"));
      assertEquals(1, ref.getParameterCount(), "the old presenter's properties are gone");
   }

   @Test
   void propertiesAreNotStoredOnTheReferenceTheFormatHeldBefore() throws Exception {
      VSCompositeFormat format = new VSCompositeFormat();
      PresenterRef before = new PresenterRef(BAR);
      format.getUserDefinedFormat().setPresenterValue(before);

      write(format, model(null, props("maximum", 100)), false);

      assertEquals(0, before.getParameterCount(), "the held reference is cloned, not edited");
      assertEquals(100.0, stored(format).getParameter("maximum"));
   }

   @Test
   void resetClearsThePresenterAndItsProperties() throws Exception {
      VSCompositeFormat format = new VSCompositeFormat();
      write(format, model(BAR, props("maximum", 100)), false);
      write(format, model(null, props("maximum", 100)), true);

      VSFormat user = format.getUserDefinedFormat();
      assertNull(user.getPresenterValue());
      assertFalse(user.isPresenterValueDefined());
   }

   @Test
   void aPresenterWithoutPropertiesStaysAFreshReference() throws Exception {
      VSCompositeFormat format = new VSCompositeFormat();
      write(format, model(BAR, props("maximum", 100)), false);
      write(format, model(BAR, null), false);

      PresenterRef ref = stored(format);
      assertEquals(BAR, ref.getName());
      assertEquals(0, ref.getParameterCount(), "a presenter-only write is unchanged");
   }

   @Test
   void propertiesWithNoPresenterAnywhereAreRefused() {
      VSCompositeFormat format = new VSCompositeFormat();
      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class,
         () -> write(format, model(null, props("maximum", 100)), false));

      assertTrue(e.getMessage().contains("needs a presenter class"), e.getMessage());
      assertNull(stored(format));
   }

   @Test
   void propertiesWithAnEmptyPresenterAreRefused() {
      VSCompositeFormat format = new VSCompositeFormat();
      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class,
         () -> write(format, model("", props("maximum", 100)), false));

      assertTrue(e.getMessage().contains("empty presenter"), e.getMessage());
   }

   @Test
   void anUnknownPropertyIsRefusedAndTheStoredReferenceIsKept() throws Exception {
      VSCompositeFormat format = new VSCompositeFormat();
      write(format, model(BAR, props("maximum", 100)), false);

      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class,
         () -> write(format, model(null, props("maxium", 5)), false));

      assertTrue(e.getMessage().contains("'maxium'"), e.getMessage());
      assertTrue(e.getMessage().contains("maximum (a number)"), e.getMessage());
      assertEquals(100.0, stored(format).getParameter("maximum"));
   }

   private FormatPainterService service;
   private Method setUserFormat;
}
