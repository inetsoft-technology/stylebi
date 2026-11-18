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
package inetsoft.web.vswizard.recommender.chart;

import inetsoft.graph.aesthetic.GShape;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.ShapeFrameWrapper;
import inetsoft.uql.viewsheet.graph.aesthetic.StaticShapeFrameWrapper;
import inetsoft.uql.viewsheet.internal.VizContext;
import inetsoft.uql.viewsheet.internal.VizMark;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The wizard point is filled on a marked chart and outlined on a classic one.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WizardFilledPointTest {
   @Test
   void aMarkedPointIsFilled() {
      PointChartFilter filter =
         new PointChartFilter(new AssetEntry[0], tempInfo(1, 1), List.of(), false);
      filter.setVizContext(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(GShape.FILLED_CIRCLE, pointShape(
         filter.createChartInfo(new ChartRefCombination(of(0), of(1), of()))));
   }

   @Test
   void aClassicPointIsOutlined() {
      PointChartFilter filter =
         new PointChartFilter(new AssetEntry[0], tempInfo(1, 1), List.of(), false);

      assertEquals(GShape.CIRCLE, pointShape(
         filter.createChartInfo(new ChartRefCombination(of(0), of(1), of()))));
   }

   @Test
   void aMarkedScatterIsFilled() {
      ScatterChartFilter filter =
         new ScatterChartFilter(new AssetEntry[0], tempInfo(0, 2), List.of(), false);
      filter.setVizContext(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(GShape.FILLED_CIRCLE, pointShape(
         filter.createChartInfo(new ChartRefCombination(of(0), of(1), of()))));
   }

   @Test
   void aClassicScatterIsOutlined() {
      ScatterChartFilter filter =
         new ScatterChartFilter(new AssetEntry[0], tempInfo(0, 2), List.of(), false);

      assertEquals(GShape.CIRCLE, pointShape(
         filter.createChartInfo(new ChartRefCombination(of(0), of(1), of()))));
   }

   private static GShape pointShape(VSChartInfo info) {
      assertNotNull(info, "the filter rejected the fixture");
      ChartAggregateRef aggr = (ChartAggregateRef) Arrays.stream(info.getYFields())
         .filter(r -> r instanceof ChartAggregateRef).findFirst().orElseThrow();
      ShapeFrameWrapper wrapper = aggr.getShapeFrameWrapper();
      assertInstanceOf(StaticShapeFrameWrapper.class, wrapper);
      return ShapeFrameWrapper.getGShape(((StaticShapeFrameWrapper) wrapper).getShape());
   }

   private static VSChartInfo tempInfo(int dimensions, int measures) {
      VSChartInfo temp = new VSChartInfo();

      for(int i = 0; i < dimensions; i++) {
         VSChartDimensionRef dim = new VSChartDimensionRef();
         dim.setGroupColumnValue("D" + i);
         dim.setDataType(XSchema.STRING);
         temp.addXField(dim);
      }

      for(int i = 0; i < measures; i++) {
         VSChartAggregateRef aggr = new VSChartAggregateRef();
         aggr.setColumnValue("M" + i);
         aggr.setFormula(AggregateFormula.SUM);
         temp.addYField(aggr);
      }

      return temp;
   }

   private static IntList of(int... indexes) {
      IntList list = new IntArrayList();

      for(int index : indexes) {
         list.add(index);
      }

      return list;
   }
}
