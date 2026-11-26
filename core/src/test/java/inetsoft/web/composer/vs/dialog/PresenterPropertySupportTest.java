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

import inetsoft.report.internal.table.PresenterRef;
import inetsoft.report.painter.BarPresenter;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82311: presenter properties are written and read through {@link PresenterPropertySupport}
 * with the Presenter Properties dialog's type mapping.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PresenterPropertySupportTest {
   private static final String BAR = "inetsoft.report.painter.BarPresenter";
   private static final String LIGHT = "inetsoft.report.painter.TrafficLightPresenter";

   private static Map<String, Object> props(Object... pairs) {
      Map<String, Object> map = new LinkedHashMap<>();

      for(int i = 0; i < pairs.length; i += 2) {
         map.put((String) pairs[i], pairs[i + 1]);
      }

      return map;
   }

   @Test
   void barPropertiesAreStoredWithTheirTypes() {
      PresenterRef ref = new PresenterRef(BAR);
      PresenterPropertySupport.apply(
         ref, props("maximum", 100, "color", "#2E8B57", "autoMaximum", true));

      assertEquals(100.0, ref.getParameter("maximum"));
      assertEquals(Color.decode("#2E8B57"), ref.getParameter("color"));
      assertEquals(Boolean.TRUE, ref.getParameter("autoMaximum"));
   }

   @Test
   void trafficLightThresholdsAndFlagsAreStored() {
      PresenterRef ref = new PresenterRef(LIGHT);
      PresenterPropertySupport.apply(
         ref, props("high", 500.5, "low", "10", "highRed", "true", "size", 12.0));

      assertEquals(500.5, ref.getParameter("high"));
      assertEquals(10.0, ref.getParameter("low"));
      assertEquals(Boolean.TRUE, ref.getParameter("highRed"));
      assertEquals(12, ref.getParameter("size"));
   }

   @Test
   void storedPropertiesBuildThePresenter() throws Exception {
      PresenterRef ref = new PresenterRef(BAR);
      PresenterPropertySupport.apply(ref, props("maximum", 250, "color", "#112233"));

      BarPresenter bar = (BarPresenter) ref.createPresenter();

      assertEquals(250.0, bar.getMaximum());
      assertEquals(Color.decode("#112233"), bar.getColor());
   }

   @Test
   void aNullValueClearsTheProperty() {
      PresenterRef ref = new PresenterRef(BAR);
      PresenterPropertySupport.apply(ref, props("maximum", 100));
      PresenterPropertySupport.apply(ref, props("maximum", null));

      assertNull(ref.getParameter("maximum"));
   }

   @Test
   void aCaseOnlyDifferenceInTheNameIsAccepted() {
      PresenterRef ref = new PresenterRef(BAR);
      PresenterPropertySupport.apply(ref, props("MAXIMUM", 100));

      assertEquals(100.0, ref.getParameter("maximum"));
   }

   @Test
   void anUnknownPropertyIsRefusedWithTheSettableList() {
      PresenterRef ref = new PresenterRef(BAR);
      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class,
         () -> PresenterPropertySupport.apply(ref, props("maxium", 100)));

      assertTrue(e.getMessage().contains("'maxium'"), e.getMessage());
      assertTrue(e.getMessage().contains("maximum (a number)"), e.getMessage());
      assertTrue(e.getMessage().contains("color (a color string"), e.getMessage());
      assertEquals(0, ref.getParameterCount());
   }

   @Test
   void aValueOfTheWrongTypeIsRefusedNamingTheProperty() {
      PresenterRef ref = new PresenterRef(BAR);

      IllegalArgumentException number = assertThrows(
         IllegalArgumentException.class,
         () -> PresenterPropertySupport.apply(ref, props("maximum", "lots")));
      assertTrue(number.getMessage().contains("'maximum' must be a number"),
                 number.getMessage());

      IllegalArgumentException color = assertThrows(
         IllegalArgumentException.class,
         () -> PresenterPropertySupport.apply(ref, props("color", "green")));
      assertTrue(color.getMessage().contains("'color' must be a color"), color.getMessage());

      IllegalArgumentException flag = assertThrows(
         IllegalArgumentException.class,
         () -> PresenterPropertySupport.apply(ref, props("autoMaximum", 3)));
      assertTrue(flag.getMessage().contains("'autoMaximum' must be a boolean"),
                 flag.getMessage());
   }

   @Test
   void aFractionIsRefusedForAWholeNumberProperty() {
      PresenterRef ref = new PresenterRef(LIGHT);
      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class,
         () -> PresenterPropertySupport.apply(ref, props("size", 12.5)));

      assertTrue(e.getMessage().contains("'size' must be a whole number"), e.getMessage());
   }

   @Test
   void oneBadEntryStoresNothing() {
      PresenterRef ref = new PresenterRef(BAR);

      assertThrows(IllegalArgumentException.class,
                   () -> PresenterPropertySupport.apply(
                      ref, props("maximum", 100, "color", "not-a-color")));
      assertEquals(0, ref.getParameterCount());
   }

   @Test
   void anUnloadablePresenterIsRefused() {
      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class,
         () -> PresenterPropertySupport.validate("no.such.Presenter", props("maximum", 1)));

      assertTrue(e.getMessage().contains("no.such.Presenter"), e.getMessage());
   }

   @Test
   void theWireFormReadsEffectiveValuesOverDefaults() {
      PresenterRef ref = new PresenterRef(BAR);
      PresenterPropertySupport.apply(ref, props("maximum", 100, "color", "#2E8B57"));

      Map<String, Object> wire = PresenterPropertySupport.toWire(ref);

      assertEquals(100.0, wire.get("maximum"));
      assertEquals("#2E8B57", ((String) wire.get("color")).toUpperCase());
      assertEquals(Boolean.FALSE, wire.get("autoMaximum"));
      assertFalse(wire.containsKey("background"), "background is not a dialog property");
   }

   @Test
   void theWireFormRoundTripsThroughApply() {
      PresenterRef source = new PresenterRef(LIGHT);
      PresenterPropertySupport.apply(source, props("high", 900, "singleLight", true));

      PresenterRef copy = new PresenterRef(LIGHT);
      PresenterPropertySupport.apply(copy, PresenterPropertySupport.toWire(source));

      assertEquals(PresenterPropertySupport.toWire(source), PresenterPropertySupport.toWire(copy));
   }

   @Test
   void samePresenterIgnoresTheSpellingOfTheClassName() {
      assertTrue(PresenterPropertySupport.samePresenter(BAR, "BarPresenter"));
      assertTrue(PresenterPropertySupport.samePresenter(BAR, BAR));
      assertFalse(PresenterPropertySupport.samePresenter(BAR, LIGHT));
      assertFalse(PresenterPropertySupport.samePresenter(null, BAR));
   }
}
