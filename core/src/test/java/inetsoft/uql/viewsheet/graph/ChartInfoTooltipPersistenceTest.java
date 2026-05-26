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

import inetsoft.util.Tool;
import org.junit.jupiter.api.Test;
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
class ChartInfoTooltipPersistenceTest {
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
   void legacyXmlMissingTooltipStyleDefaultsToDefault() throws Exception {
      // Older charts predating the tooltipStyle attribute should keep their
      // original look rather than picking up the new CARD constructor default.
      Document doc = Tool.parseXML(new StringReader("<VSChartInfo/>"));
      VSChartInfo parsed = new VSChartInfo();
      parsed.parseXML(doc.getDocumentElement());

      assertEquals(ChartInfo.TooltipStyle.DEFAULT, parsed.getTooltipStyleValue());
      assertFalse(parsed.getSnapTooltipValue());
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
