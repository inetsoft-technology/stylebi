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

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.DefaultVSChartInfo;
import inetsoft.uql.viewsheet.graph.VSAestheticRef;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the index-based text-layout field key path (Redmine #75474): the Format panel addresses a
 * Layout Designer field by its INDEX ("_layoutfield:&lt;i&gt;[:&lt;aggrName&gt;]") rather than its
 * aggregated name, which the client cannot know on first open. Covers key recognition and
 * FormatPainterService.resolveLayoutFieldByIndex for valid, out-of-bounds, malformed, and
 * missing-aggregate keys.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class FormatPainterServiceLayoutFieldTest {
   private static VSChartInfo infoWithTextLayoutFields(String... names) {
      VSChartInfo info = new DefaultVSChartInfo();

      for(String name : names) {
         VSAestheticRef ref = new VSAestheticRef();
         ref.setDataRef(new VSChartDimensionRef(new AttributeRef(null, name)));
         info.addTextLayoutField(ref);
      }

      return info;
   }

   @Test
   void isLayoutFieldKeyRecognizesPrefix() {
      assertTrue(FormatPainterService.isLayoutFieldKey("_layoutfield:0"));
      assertTrue(FormatPainterService.isLayoutFieldKey("_layoutfield:2:Sum(Sales)"));
      assertFalse(FormatPainterService.isLayoutFieldKey("_static:Total:"));
      assertFalse(FormatPainterService.isLayoutFieldKey("Sum(Sales)"));
      assertFalse(FormatPainterService.isLayoutFieldKey(null));
   }

   @Test
   void resolvesEachFieldByItsOwnIndex() {
      VSChartInfo info = infoWithTextLayoutFields("state", "city", "zip");

      ChartRef r0 = FormatPainterService.resolveLayoutFieldByIndex(info, "_layoutfield:0");
      ChartRef r1 = FormatPainterService.resolveLayoutFieldByIndex(info, "_layoutfield:1");
      ChartRef r2 = FormatPainterService.resolveLayoutFieldByIndex(info, "_layoutfield:2");

      assertNotNull(r0);
      assertNotNull(r1);
      assertNotNull(r2);
      // Each index must resolve to a DISTINCT ref — this is the crux of #75474 (every field gets its
      // own unique format target, not all collapsing onto the first).
      assertNotSame(r0, r1);
      assertNotSame(r1, r2);
      assertSame(info.getTextLayoutFields().get(1).getDataRef(), r1);
   }

   @Test
   void returnsNullForOutOfRangeIndex() {
      VSChartInfo info = infoWithTextLayoutFields("state");

      assertNull(FormatPainterService.resolveLayoutFieldByIndex(info, "_layoutfield:1"));
      assertNull(FormatPainterService.resolveLayoutFieldByIndex(info, "_layoutfield:-1"));
   }

   @Test
   void returnsNullForMalformedIndex() {
      VSChartInfo info = infoWithTextLayoutFields("state");

      assertNull(FormatPainterService.resolveLayoutFieldByIndex(info, "_layoutfield:abc"));
      assertNull(FormatPainterService.resolveLayoutFieldByIndex(info, "_layoutfield:"));
   }

   @Test
   void returnsNullForNonLayoutFieldKey() {
      VSChartInfo info = infoWithTextLayoutFields("state");

      assertNull(FormatPainterService.resolveLayoutFieldByIndex(info, "Sum(Sales)"));
      assertNull(FormatPainterService.resolveLayoutFieldByIndex(info, "_static:Total:"));
   }

   @Test
   void returnsNullWhenAggregateNotFound() {
      VSChartInfo info = infoWithTextLayoutFields("state");

      // A key naming an aggregate that does not exist resolves against that aggregate's (absent)
      // textLayoutFields, not the chart-level list, so it must return null rather than mis-resolving
      // to the chart-level field at the same index.
      assertNull(FormatPainterService.resolveLayoutFieldByIndex(info, "_layoutfield:0:NoSuchAggr"));
   }
}
