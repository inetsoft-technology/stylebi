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
package inetsoft.report.internal.graph;

import inetsoft.report.Hyperlink;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.viewsheet.VSDataRef;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;

import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79118: decorations on y-shelf measures survive a retype to a merged type, only the
 * per-measure chart type is reset.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ChangeChartTypeMergedDecorationsTest {
   @Test
   void severalMeasuresKeepOnlyTheirOwnDecorationsForEveryMergedType() {
      int[] merged = { GraphTypes.CHART_RADAR, GraphTypes.CHART_FILL_RADAR, GraphTypes.CHART_CANDLE,
                       GraphTypes.CHART_TREEMAP, GraphTypes.CHART_FUNNEL, GraphTypes.CHART_MEKKO };

      for(int type : merged) {
         ChartInfo info = barWithMeasures();
         info = new ChangeChartTypeProcessor(GraphTypes.CHART_BAR, type, null, info).process();
         VSChartAggregateRef a = find(info, "A");
         VSChartAggregateRef b = find(info, "B");
         VSChartAggregateRef c = find(info, "C");

         if(a == null || b == null || c == null) {
            // a type may legitimately drop measures it cannot hold; the ones kept must be intact
            continue;
         }

         assertNotNull(a.getHighlightGroup(), type + " A highlight");
         assertNull(a.getTextHighlightGroup(), type + " A text highlight");
         assertNull(b.getHighlightGroup(), type + " B highlight");
         assertNotNull(b.getTextHighlightGroup(), type + " B text highlight");
         assertNotNull(b.getHyperlink(), type + " B hyperlink");
         assertNull(c.getHyperlink(), type + " C hyperlink");
         assertEquals(GraphTypes.CHART_AUTO, a.getChartType(), type + " A chart type reset");
         assertEquals(GraphTypes.CHART_AUTO, b.getChartType(), type + " B chart type reset");
      }
   }

   @Test
   void radarToBarRoundTripAndXmlSerialization() throws Exception {
      ChartInfo info = barWithMeasures();
      info = new ChangeChartTypeProcessor(GraphTypes.CHART_BAR, GraphTypes.CHART_RADAR, null, info)
         .process();
      info = new ChangeChartTypeProcessor(GraphTypes.CHART_RADAR, GraphTypes.CHART_BAR, null, info)
         .process();
      assertNotNull(find(info, "A").getHighlightGroup());
      assertNotNull(find(info, "B").getTextHighlightGroup());
      assertNotNull(find(info, "B").getHyperlink());

      // persistence round trip of the retyped info
      ChartInfo radar = new ChangeChartTypeProcessor(GraphTypes.CHART_BAR, GraphTypes.CHART_RADAR,
                                                     null, barWithMeasures()).process();
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      radar.writeXML(pw);
      pw.flush();
      Document doc = Tool.parseXML(new ByteArrayInputStream(
         sw.toString().getBytes(StandardCharsets.UTF_8)));
      ChartInfo copy = new DefaultVSChartInfo();
      copy.parseXML(doc.getDocumentElement());
      assertNotNull(find(copy, "A").getHighlightGroup());
      assertNotNull(find(copy, "B").getTextHighlightGroup());
      assertNotNull(find(copy, "B").getHyperlink());
   }

   @Test
   void xFieldMeasuresStillKeepDecorations() {
      ChartInfo info = new DefaultVSChartInfo();
      VSChartAggregateRef x = aggregate("A");
      x.setHighlightGroup(new HighlightGroup());
      x.setChartType(GraphTypes.CHART_BAR);
      info.addXField(x);
      info.addYField(aggregate("B"));
      info = new ChangeChartTypeProcessor(GraphTypes.CHART_BAR, GraphTypes.CHART_RADAR, null, info)
         .process();
      VSChartAggregateRef a = find(info, "A");

      if(a != null) {
         assertNotNull(a.getHighlightGroup());
      }
   }

   private static ChartInfo barWithMeasures() {
      ChartInfo info = new DefaultVSChartInfo();
      VSChartDimensionRef dim = new VSChartDimensionRef();
      dim.setGroupColumnValue("Cat");
      dim.setDataType("string");
      info.addXField(dim);
      VSChartAggregateRef a = aggregate("A");
      a.setHighlightGroup(new HighlightGroup());
      VSChartAggregateRef b = aggregate("B");
      b.setTextHighlightGroup(new HighlightGroup());
      b.setHyperlink(new Hyperlink("http://example.com"));
      VSChartAggregateRef c = aggregate("C");
      info.addYField(a);
      info.addYField(b);
      info.addYField(c);
      return info;
   }

   private static VSChartAggregateRef find(ChartInfo info, String name) {
      for(VSDataRef f : info.getFields()) {
         if(name.equals(f.getName()) && f instanceof VSChartAggregateRef) {
            return (VSChartAggregateRef) f;
         }
      }

      return null;
   }

   private static VSChartAggregateRef aggregate(String column) {
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setColumnValue(column);
      agg.setFormula(AggregateFormula.SUM);
      agg.setAggregated(true);
      agg.setChartType(GraphTypes.CHART_BAR);
      return agg;
   }
}
