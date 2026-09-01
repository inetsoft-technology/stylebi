/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.uql.viewsheet.graph;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import inetsoft.sree.SreeEnv;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests GraphTypes.getGeomMaxCount(), which resolves the graph.*.maxcount limit that
 * GraphGenerator.createElement stores on each element to cap how many rows a chart draws
 * shapes for.
 */
@Tag("core")
class GraphTypesTest {
   @Test
   void getGeomMaxCountReturnsConfiguredValue() {
      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
         sreeEnv.when(() -> SreeEnv.getProperty("graph.bar.maxcount")).thenReturn("1500");

         assertEquals(1500, GraphTypes.getGeomMaxCount(GraphTypes.CHART_BAR),
                      "A numeric graph.bar.maxcount must be used as-is");
      }
   }

   @Test
   void getGeomMaxCountReturnsNegativeValueAsConfigured() {
      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
         sreeEnv.when(() -> SreeEnv.getProperty("graph.bar.maxcount")).thenReturn("-1");

         assertEquals(-1, GraphTypes.getGeomMaxCount(GraphTypes.CHART_BAR),
                      "A negative graph.bar.maxcount is a deliberate no-limit setting and " +
                      "must be returned unchanged, not treated as a bad value");
      }
   }

   @ParameterizedTest(name = "graph.bar.maxcount [{0}] falls back to the shipped default")
   @NullSource
   @ValueSource(strings = { "abc", "", " ", "100,000", "1e5", "100000px" })
   void getGeomMaxCountFallsBackWhenValueIsNotANumber(String propertyValue) {
      // the parse used to be a bare Integer.parseInt outside any try, so an admin typo threw
      // NumberFormatException out of GraphGenerator.createElement -- at chart generation
      // time, not when the value was stored -- and the chart failed to render
      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
         sreeEnv.when(() -> SreeEnv.getProperty("graph.bar.maxcount")).thenReturn(propertyValue);
         sreeEnv.when(SreeEnv::getDefaultProperties)
            .thenReturn(defaults("graph.bar.maxcount", "100000"));

         assertEquals(100000, GraphTypes.getGeomMaxCount(GraphTypes.CHART_BAR),
                      "A missing or non-numeric graph.bar.maxcount must fall back to the " +
                      "value shipped in defaults.properties instead of throwing");
      }
   }

   @Test
   void getGeomMaxCountFallsBackToNoLimitWhenTheDefaultIsAlsoUnusable() {
      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
         sreeEnv.when(() -> SreeEnv.getProperty("graph.bar.maxcount")).thenReturn("abc");
         sreeEnv.when(SreeEnv::getDefaultProperties).thenReturn(new Properties());

         assertEquals(-1, GraphTypes.getGeomMaxCount(GraphTypes.CHART_BAR),
                      "With no usable default the limit must degrade to -1 (no limit), the " +
                      "same value returned for a chart type that matches no property");
      }
   }

   @Test
   void getGeomMaxCountWarnsNamingThePropertyAndTheOffendingValue() {
      // the warning is half the fix: without it the fallback is silent and an admin has no
      // way to attribute a chart drawing on the default limit to the value they mistyped
      Logger logger = (Logger) LoggerFactory.getLogger(GraphTypes.class);
      ListAppender<ILoggingEvent> appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);

      try {
         try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
            sreeEnv.when(() -> SreeEnv.getProperty("graph.bar.maxcount")).thenReturn("100,000");
            sreeEnv.when(SreeEnv::getDefaultProperties)
               .thenReturn(defaults("graph.bar.maxcount", "100000"));

            GraphTypes.getGeomMaxCount(GraphTypes.CHART_BAR);
         }

         assertEquals(1, appender.list.size(), "the fallback must report itself exactly once");

         ILoggingEvent event = appender.list.get(0);
         assertEquals(Level.WARN, event.getLevel(), "the fallback must be reported at WARN");

         String message = event.getFormattedMessage();
         assertTrue(message.contains("graph.bar.maxcount"),
                    "the warning must name the property so it can be corrected, but was: " +
                    message);
         assertTrue(message.contains("100,000"),
                    "the warning must quote the offending value, but was: " + message);
      }
      finally {
         logger.detachAppender(appender);
      }
   }

   /**
    * isBar() returns true for the 3D bar types as well as the flat ones, so getGeomMaxCount
    * relies on testing is3DBar first. Reordering those two branches -- or inserting a new one
    * between them -- would silently retire graph.3dbar.maxcount and move 3D bars onto
    * graph.bar.maxcount. This pins the property to the type.
    */
   @ParameterizedTest(name = "3D bar type {0} resolves graph.3dbar.maxcount")
   @ValueSource(ints = { GraphTypes.CHART_3D_BAR, GraphTypes.CHART_3D_BAR_STACK })
   void getGeomMaxCountPrefers3dBarPropertyOverBarProperty(int type) {
      assertTrue(GraphTypes.isBar(type),
                 "precondition: isBar() is a superset of is3DBar(), which is why the branch " +
                 "order in getGeomMaxCount is load-bearing");

      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
         sreeEnv.when(() -> SreeEnv.getProperty("graph.3dbar.maxcount")).thenReturn("111");
         sreeEnv.when(() -> SreeEnv.getProperty("graph.bar.maxcount")).thenReturn("222");

         assertEquals(111, GraphTypes.getGeomMaxCount(type),
                      "a 3D bar must draw its limit from graph.3dbar.maxcount, not from " +
                      "graph.bar.maxcount");
      }
   }

   @Test
   void getGeomMaxCountUsesBarPropertyForFlatBars() {
      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
         sreeEnv.when(() -> SreeEnv.getProperty("graph.3dbar.maxcount")).thenReturn("111");
         sreeEnv.when(() -> SreeEnv.getProperty("graph.bar.maxcount")).thenReturn("222");

         assertEquals(222, GraphTypes.getGeomMaxCount(GraphTypes.CHART_BAR),
                      "a flat bar must draw its limit from graph.bar.maxcount");
      }
   }

   private static Properties defaults(String name, String value) {
      Properties properties = new Properties();
      properties.setProperty(name, value);
      return properties;
   }
}
