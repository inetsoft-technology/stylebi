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
package inetsoft.uql.viewsheet.internal;

import inetsoft.graph.aesthetic.CategoricalShapeFrame;
import inetsoft.graph.aesthetic.GShape;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.composition.region.ChartConstants;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.schema.XSchema;
import inetsoft.report.StyleConstants;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.CategoricalShapeFrameWrapper;
import inetsoft.uql.viewsheet.graph.aesthetic.ShapeFrameWrapper;
import inetsoft.uql.viewsheet.graph.aesthetic.VisualFrameWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The categorical shape list follows the mark: the default list swaps, an author's list stays.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ShapeListSeedTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   private static final VizContext MODERN = VizContext.of(VizMark.MODERN_LIGHT);
   private static final VizContext CLASSIC = VizContext.of((VizMark) null);

   @Test
   void modernizeFillsTheDefaultList() {
      CategoricalShapeFrame frame = new CategoricalShapeFrame();
      ChartVSAssemblyInfo info = chartWithShape(frame);

      info.seedChromeDefaults(MODERN);

      assertArrayEquals(ModernShapes.modernDefault(), list(frame));
      assertEquals(GShape.FILLED_CIRCLE, frame.getShape(0));
      assertEquals(30, frame.getShapeCount());
   }

   @Test
   void revertRestoresTheClassicList() {
      CategoricalShapeFrame frame = new CategoricalShapeFrame();
      ChartVSAssemblyInfo info = chartWithShape(frame);

      info.seedChromeDefaults(MODERN);
      info.seedChromeDefaults(CLASSIC);

      assertArrayEquals(ModernShapes.classicDefault(), list(frame));
      assertArrayEquals(list(new CategoricalShapeFrame()), list(frame));
   }

   @Test
   void aClassicChartSeededClassicIsUnchanged() {
      CategoricalShapeFrame frame = new CategoricalShapeFrame();
      chartWithShape(frame).seedChromeDefaults(CLASSIC);

      assertArrayEquals(list(new CategoricalShapeFrame()), list(frame));
   }

   @Test
   void anAuthorsListIsKept() {
      CategoricalShapeFrame frame = new CategoricalShapeFrame();
      frame.setShape(0, GShape.STAR);
      GShape[] authored = list(frame);
      ChartVSAssemblyInfo info = chartWithShape(frame);

      info.seedChromeDefaults(MODERN);
      assertArrayEquals(authored, list(frame));

      info.seedChromeDefaults(CLASSIC);
      assertArrayEquals(authored, list(frame));
   }

   @Test
   void aPerValueShapeIsKept() {
      CategoricalShapeFrame frame = new CategoricalShapeFrame();
      frame.setShape("East", GShape.STAR);
      ChartVSAssemblyInfo info = chartWithShape(frame);

      info.seedChromeDefaults(MODERN);

      assertEquals(GShape.STAR, frame.getShape("East"));
      assertTrue(frame.isStatic("East"));
      assertArrayEquals(ModernShapes.modernDefault(), list(frame));
   }

   @Test
   void thePanesResetListSwapsBothWays() {
      CategoricalShapeFrame frame = new CategoricalShapeFrame();
      setList(frame, ModernShapes.classicReset());
      ChartVSAssemblyInfo info = chartWithShape(frame);

      info.seedChromeDefaults(MODERN);
      assertArrayEquals(ModernShapes.modernReset(), list(frame));
      assertEquals(GShape.FILLED_CIRCLE, frame.getShape(0));
      assertEquals(GShape.XSHAPE, frame.getShape(10));
      assertEquals(GShape.VSHAPE, frame.getShape(11));

      info.seedChromeDefaults(CLASSIC);
      assertArrayEquals(ModernShapes.classicReset(), list(frame));
   }

   @Test
   void theResetListIsWhatThePaneWritesThroughTheWrapper() {
      CategoricalShapeFrameWrapper wrapper = new CategoricalShapeFrameWrapper();
      GShape[] reset = ModernShapes.classicReset();

      for(int i = 0; i < reset.length; i++) {
         wrapper.setShape(i, ShapeFrameWrapper.getID(reset[i]));
      }

      assertArrayEquals(reset, list((CategoricalShapeFrame) wrapper.getVisualFrame()));

      for(GShape shape : reset) {
         assertNotNull(shape);
      }
   }

   @Test
   void theModernResetListIsWhatThePaneWritesThroughTheWrapper() {
      assertArrayEquals(ModernShapes.modernReset(),
                        throughWrapper(paneIds(new String[] {
                           StyleConstants.FILLED_CIRCLE + "", StyleConstants.FILLED_TRIANGLE + "",
                           StyleConstants.FILLED_SQUARE + "", StyleConstants.FILLED_DIAMOND + "",
                           StyleConstants.CIRCLE + "", StyleConstants.TRIANGLE + "",
                           StyleConstants.SQUARE + "", StyleConstants.CROSS + "",
                           StyleConstants.STAR + "", StyleConstants.DIAMOND + "",
                           StyleConstants.X + ""
                        })));
   }

   @Test
   void theClassicResetListIsWhatThePaneWritesThroughTheWrapper() {
      assertArrayEquals(ModernShapes.classicReset(),
                        throughWrapper(paneIds(new String[] {
                           StyleConstants.CIRCLE + "", StyleConstants.TRIANGLE + "",
                           StyleConstants.SQUARE + "", StyleConstants.CROSS + "",
                           StyleConstants.STAR + "", StyleConstants.DIAMOND + "",
                           StyleConstants.X + "", StyleConstants.FILLED_CIRCLE + "",
                           StyleConstants.FILLED_TRIANGLE + "", StyleConstants.FILLED_SQUARE + "",
                           StyleConstants.FILLED_DIAMOND + ""
                        })));
   }

   // the pane's ids: the given leading shapes, the other classic shapes, then the images
   private static String[] paneIds(String[] leading) {
      String[] rest = {
         StyleConstants.V_ANGLE + "", StyleConstants.RIGHT_ANGLE + "",
         StyleConstants.LT_ANGLE + "", StyleConstants.V_LINE + "", StyleConstants.H_LINE + "",
         "100ArrowDown.svg", "101ArrowUp.svg", "102Check.svg", "103Cancel.svg",
         "104Exclamation.svg", "105Flag.svg", "106Light.svg", "107Star.svg", "108No.svg",
         "109Man.svg", "110Woman.svg", "111FaceHappy.svg", "112FaceSad.svg", "113Face.svg",
         "114ArrowUperRight.svg", "115ArrowLowerRight.svg"
      };
      String[] ids = new String[leading.length + rest.length];
      System.arraycopy(leading, 0, ids, 0, leading.length);
      System.arraycopy(rest, 0, ids, leading.length, rest.length);
      return ids;
   }

   private static GShape[] throughWrapper(String[] ids) {
      CategoricalShapeFrameWrapper wrapper = new CategoricalShapeFrameWrapper();

      for(int i = 0; i < ids.length; i++) {
         wrapper.setShape(i, ids[i]);
      }

      return list((CategoricalShapeFrame) wrapper.getVisualFrame());
   }

   @Test
   void everyFrameIsReached() {
      CategoricalShapeFrame multi = new CategoricalShapeFrame();
      CategoricalShapeFrame second = new CategoricalShapeFrame();
      CategoricalShapeFrame lines = new CategoricalShapeFrame();
      CategoricalShapeFrame radar = new CategoricalShapeFrame();
      CategoricalShapeFrame map = new CategoricalShapeFrame();

      VSChartInfo multiInfo = new VSChartInfo();
      multiInfo.setMultiStyles(true);
      multiInfo.addYField(aggregate("A", multi));
      multiInfo.addYField(aggregate("B", second));

      VSChartInfo lineInfo = new VSChartInfo();
      lineInfo.addYField(aggregate("A", lines));

      RadarVSChartInfo radarInfo = new RadarVSChartInfo();
      radarInfo.addYField(aggregate("A", radar));

      VSMapInfo mapInfo = new VSMapInfo();
      mapInfo.addYField(aggregate("A", map));

      for(VSChartInfo cinfo : new VSChartInfo[] { multiInfo, lineInfo, radarInfo, mapInfo }) {
         ChartVSAssemblyInfo info = newChart();
         info.setVSChartInfo(cinfo);
         info.seedChromeDefaults(MODERN);
      }

      CategoricalShapeFrame[] frames = { multi, second, lines, radar, map };

      for(int i = 0; i < frames.length; i++) {
         assertArrayEquals(ModernShapes.modernDefault(), list(frames[i]), "frame " + i);
      }
   }

   @Test
   void aNewFrameOnAMarkedChartIsFilled() {
      assertArrayEquals(ModernShapes.modernDefault(), list(newShapeFrame(MODERN)));
      assertArrayEquals(ModernShapes.classicDefault(), list(newShapeFrame(CLASSIC)));
      assertArrayEquals(ModernShapes.classicDefault(), list(newShapeFrame(VizContext.LEGACY)));
   }

   @Test
   void theSeedNeverMarksTheFrameChanged() {
      CategoricalShapeFrame frame = new CategoricalShapeFrame();
      ChartVSAssemblyInfo info = chartWithShape(frame);
      VisualFrameWrapper wrapper = info.getVSChartInfo().getShapeField().getVisualFrameWrapper();
      assertFalse(wrapper.isChanged(), "premise");

      info.seedChromeDefaults(MODERN);
      assertFalse(wrapper.isChanged());

      info.seedChromeDefaults(CLASSIC);
      assertFalse(wrapper.isChanged());
   }

   private static CategoricalShapeFrame newShapeFrame(VizContext ctx) {
      VSAestheticRef ref = new VSAestheticRef();
      ref.setDataRef(stateDimension());
      VSChartInfo cinfo = new VSChartInfo();
      cinfo.setChartType(GraphTypes.CHART_POINT);
      cinfo.setRTChartType(GraphTypes.CHART_POINT);
      GraphUtil.fixVisualFrame(ref, ChartConstants.AESTHETIC_SHAPE, GraphTypes.CHART_POINT, cinfo,
                               ctx);
      return (CategoricalShapeFrame) ref.getVisualFrame();
   }

   private static VSChartDimensionRef stateDimension() {
      VSChartDimensionRef dim = new VSChartDimensionRef(new BaseField("State"));
      dim.setGroupColumnValue("State");
      dim.setDataType(XSchema.STRING);
      return dim;
   }

   private static VSChartAggregateRef aggregate(String name, CategoricalShapeFrame frame) {
      VSChartAggregateRef agg = new VSChartAggregateRef();
      BaseField field = new BaseField(name);
      field.setDataType(XSchema.DOUBLE);
      agg.setDataRef(field);
      agg.setShapeField(shapeRef(frame));
      return agg;
   }

   private static VSAestheticRef shapeRef(CategoricalShapeFrame frame) {
      VSAestheticRef ref = new VSAestheticRef();
      ref.setDataRef(stateDimension());
      ref.setVisualFrame(frame);
      return ref;
   }

   private static ChartVSAssemblyInfo chartWithShape(CategoricalShapeFrame frame) {
      ChartVSAssemblyInfo info = newChart();
      info.getVSChartInfo().setShapeField(shapeRef(frame));
      return info;
   }

   private static ChartVSAssemblyInfo newChart() {
      ChartVSAssembly chart = new ChartVSAssembly(new Viewsheet(), "Chart1");
      chart.getVSAssemblyInfo().initDefaultFormat();
      return (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
   }

   private static GShape[] list(CategoricalShapeFrame frame) {
      GShape[] list = new GShape[frame.getShapeCount()];

      for(int i = 0; i < list.length; i++) {
         list[i] = frame.getShape(i);
      }

      return list;
   }

   private static void setList(CategoricalShapeFrame frame, GShape[] list) {
      for(int i = 0; i < list.length; i++) {
         frame.setShape(i, list[i]);
      }
   }
}
