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
package inetsoft.uql.viewsheet.graph;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runtime/design split for snapTooltip and tooltipStyle: scripts mutate the
 * rvalue overlay, the composer dialog and XML use the dvalue, and a reset
 * between renders drops the overlay so the next render starts from dvalue.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ChartInfoTooltipPersistenceTest {
   @BeforeEach
   void gateOff() {
      // the subject here is the runtime/design split, not the org default; the two tests that
      // are about the gate set their own value
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
   }

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
   }

   @Test
   void scriptRuntimeWriteDoesNotMutateDesign() {
      VSChartInfo info = new VSChartInfo();
      info.setSnapTooltipValue(false);
      info.setTooltipStyleValue(ChartInfo.TooltipStyle.DEFAULT);

      info.setSnapTooltip(true);
      info.setTooltipStyle(ChartInfo.TooltipStyle.CARD);

      assertTrue(info.isSnapTooltip(), "runtime read should see the script value");
      assertEquals(ChartInfo.TooltipStyle.CARD, info.getTooltipStyle());

      assertFalse(info.getSnapTooltipValue(), "design read must ignore the runtime overlay");
      assertEquals(ChartInfo.TooltipStyle.DEFAULT, info.getTooltipStyleValue());
   }

   @Test
   void clearTooltipRuntimeValuesDropsOverlay() {
      VSChartInfo info = new VSChartInfo();
      info.setSnapTooltipValue(false);
      info.setTooltipStyleValue(ChartInfo.TooltipStyle.DEFAULT);
      info.setSnapTooltip(true);
      info.setTooltipStyle(ChartInfo.TooltipStyle.CARD);

      info.clearTooltipRuntimeValues();

      assertFalse(info.isSnapTooltip(), "after reset runtime falls back to dvalue");
      assertEquals(ChartInfo.TooltipStyle.DEFAULT, info.getTooltipStyle());
   }

   @Test
   void xmlRoundTripPreservesDesignValues() throws Exception {
      VSChartInfo source = new VSChartInfo();
      source.setSnapTooltipValue(true);
      source.setTooltipStyleValue(ChartInfo.TooltipStyle.CARD);

      VSChartInfo parsed = roundTrip(source);

      assertTrue(parsed.getSnapTooltipValue());
      assertEquals(ChartInfo.TooltipStyle.CARD, parsed.getTooltipStyleValue());
   }

   @Test
   void xmlRoundTripIgnoresRuntimeOverlay() throws Exception {
      VSChartInfo source = new VSChartInfo();
      source.setSnapTooltipValue(false);
      source.setTooltipStyleValue(ChartInfo.TooltipStyle.DEFAULT);
      // Simulate a script having run before the asset save.
      source.setSnapTooltip(true);
      source.setTooltipStyle(ChartInfo.TooltipStyle.CARD);

      VSChartInfo parsed = roundTrip(source);

      assertFalse(parsed.getSnapTooltipValue(),
                  "XML must serialize dvalue, not the script's rvalue");
      assertEquals(ChartInfo.TooltipStyle.DEFAULT, parsed.getTooltipStyleValue());
   }

   @Test
   void legacyXmlMissingTooltipStyleBecomesAuto() throws Exception {
      // Older charts predating the tooltipStyle attribute carry no explicit choice, so
      // the design value becomes AUTO and defers to the org modern-visualization default.
      // With the gate off the runtime style resolves to DEFAULT, so a legacy chart keeps
      // its original look in an org that has opted out.
      Document doc = Tool.parseXML(new StringReader("<VSChartInfo/>"));
      VSChartInfo parsed = new VSChartInfo();
      parsed.parseXML(doc.getDocumentElement());

      assertEquals(ChartInfo.TooltipStyle.AUTO, parsed.getTooltipStyleValue(),
                   "absent attribute defers to the org default via AUTO");
      assertEquals(ChartInfo.TooltipStyle.DEFAULT, parsed.getTooltipStyle(),
                   "AUTO resolves to legacy DEFAULT when the modern gate is off");
      assertFalse(parsed.getSnapTooltipValue());
   }

   @Test
   void newChartDefaultsToAutoAndResolvesToLegacyWhenGateOff() {
      // A brand-new chart carries no explicit choice (AUTO); with the gate off it
      // renders the legacy tooltip, so an org that has opted out sees no change.
      VSChartInfo info = new VSChartInfo();

      assertEquals(ChartInfo.TooltipStyle.AUTO, info.getTooltipStyleValue());
      assertEquals(ChartInfo.TooltipStyle.DEFAULT, info.getTooltipStyle());
   }

   @Test
   void autoResolvesToCardWhenModernGateOn() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      VSChartInfo info = new VSChartInfo();

      assertEquals(ChartInfo.TooltipStyle.AUTO, info.getTooltipStyleValue(),
                   "design value stays AUTO regardless of the gate");
      assertEquals(ChartInfo.TooltipStyle.CARD, info.getTooltipStyle(),
                   "AUTO resolves to CARD when the modern gate is on");
   }

   @Test
   void autoResolvesToCardWhenNothingIsSet() {
      // the shipped default resolves the gate on, so a chart carrying no explicit style -
      // including every chart saved before the attribute existed - takes the card tooltip
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      VSChartInfo info = new VSChartInfo();

      assertEquals(ChartInfo.TooltipStyle.AUTO, info.getTooltipStyleValue());
      assertEquals(ChartInfo.TooltipStyle.CARD, info.getTooltipStyle());
   }

   @Test
   void unknownTooltipStyleAttributeFallsBackToDefault() throws Exception {
      Document doc = Tool.parseXML(
         new StringReader("<VSChartInfo tooltipStyle=\"BOGUS\"/>"));
      VSChartInfo parsed = new VSChartInfo();
      parsed.parseXML(doc.getDocumentElement());

      assertEquals(ChartInfo.TooltipStyle.DEFAULT, parsed.getTooltipStyleValue());
   }

   @Test
   void cloneRetainsItsOwnRuntimeOverlay() {
      VSChartInfo original = new VSChartInfo();
      original.setSnapTooltipValue(false);
      original.setSnapTooltip(true);

      VSChartInfo copy = (VSChartInfo) original.clone();
      original.clearTooltipRuntimeValues();

      assertTrue(copy.isSnapTooltip(), "clone keeps its own rvalue snapshot");
      assertFalse(original.isSnapTooltip(), "original falls back to dvalue after clear");
   }

   @Test
   void scriptStyleNameAcceptsCaseInsensitiveAndUnknownValues() {
      VSChartInfo info = new VSChartInfo();
      info.setTooltipStyleValue(ChartInfo.TooltipStyle.DEFAULT);

      info.setTooltipStyleName("card");
      assertEquals(ChartInfo.TooltipStyle.CARD, info.getTooltipStyle());

      info.setTooltipStyleName("bogus");
      assertEquals(ChartInfo.TooltipStyle.DEFAULT, info.getTooltipStyle());
   }

   private static VSChartInfo roundTrip(VSChartInfo source) throws Exception {
      StringWriter sw = new StringWriter();

      try(PrintWriter pw = new PrintWriter(sw)) {
         source.writeXML(pw);
      }

      Document doc = Tool.parseXML(new StringReader(sw.toString()));
      VSChartInfo parsed = new VSChartInfo();
      parsed.parseXML(doc.getDocumentElement());
      return parsed;
   }
}
