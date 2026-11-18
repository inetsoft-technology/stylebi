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

package inetsoft.report.script;

import inetsoft.test.*;
import inetsoft.uql.viewsheet.graph.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class AxisScriptableTest {
   private AxisScriptable axisScriptable;
   private ChartInfo mockChartInfo;
   private ChartRef chartRef;
   private VSChartRef mockVSChartRef;
   private  AxisDescriptor mockAxisDescriptor;

   @BeforeEach
   void setUp() {
      mockChartInfo = mock(ChartInfo.class);
      mockAxisDescriptor = mock(AxisDescriptor.class);
      mockVSChartRef = mock(VSChartRef.class);
   }

   /**
    * check with RadarChartInfo and field is Parallel_Label
    */
   @Test
   void testInitWithRadarChartInfo() {
      chartRef = mock(ChartRef.class);
      when(mockChartInfo.getFieldByName("state", true)).thenReturn(chartRef);
      axisScriptable = new AxisScriptable(mockChartInfo, "state");

      assertNull(axisScriptable.getObject());

      //check is radarParallel
      when(chartRef.getName()).thenReturn("Parallel_Label");
      RadarChartInfo mockRadarChartInfo = mock(RadarChartInfo.class);
      when(mockRadarChartInfo.getRTXFields()).thenReturn(new ChartRef[] {chartRef});
      when(mockRadarChartInfo.getFieldByName("Parallel_Label", true)).thenReturn(chartRef);
      when(mockRadarChartInfo.getLabelAxisDescriptor()).thenReturn(mockAxisDescriptor);
      when(mockRadarChartInfo.getAxisDescriptor()).thenReturn(mock(AxisDescriptor.class));

      axisScriptable = new AxisScriptable(mockRadarChartInfo, "Parallel_Label");
      assertEquals(mockAxisDescriptor,  axisScriptable.getObject());
   }

   /**
    * cjeck initWith ChartDimensionRef
    */
   @Test
   void testInitWithChartDimensionRef() {
      when(mockChartInfo.isSeparatedGraph()).thenReturn(true);
      when(mockChartInfo.getFieldByName("state", true)).thenReturn(mockVSChartRef);

      //check  VSChartRef and axis is null
      axisScriptable = new AxisScriptable(mockChartInfo, "state");
      assertNull(axisScriptable.getObject());

      //check ChartDimensionRef
      when(mockChartInfo.isSeparatedGraph()).thenReturn(false);
      ChartDimensionRef mockChartDimensionRef = mock(ChartDimensionRef.class);
      when(mockChartDimensionRef.getAxisDescriptor()).thenReturn(mockAxisDescriptor);
      when(mockChartInfo.getFieldByName("state", true)).thenReturn(mockChartDimensionRef);

      axisScriptable = new AxisScriptable(mockChartInfo, "state");
      assertEquals(mockAxisDescriptor,  axisScriptable.getObject());
   }

   /**
    * check VSChartAggregateRef and isSecondaryY is true
    */
   @Test
   void testInitWithVSChartAggregateRef() {
      VSChartAggregateRef mockVSChartAggregateRef = mock(VSChartAggregateRef.class);
      when(mockVSChartAggregateRef.isSecondaryY()).thenReturn(true);

      VSChartInfo mockVSChartInfo = mock(VSChartInfo.class);
      when(mockVSChartInfo.getFieldByName("state", true)).thenReturn(mockVSChartAggregateRef);
      when(mockVSChartInfo.getRTAxisDescriptor2()).thenReturn(mockAxisDescriptor);
      axisScriptable = new AxisScriptable(mockVSChartInfo, "state");

      assertEquals(mockAxisDescriptor,  axisScriptable.getObject());

      //check addDimensionProperties
      assertTrue(axisScriptable.hasMember("noNull"));
      assertTrue(axisScriptable.hasMember("truncate"));

      //check else and isMeasure is false
      when(mockVSChartAggregateRef.isSecondaryY()).thenReturn(false);
      when(mockVSChartAggregateRef.isMeasure()).thenReturn(true);
      when(mockVSChartInfo.getRTAxisDescriptor()).thenReturn(mockAxisDescriptor);
      axisScriptable = new AxisScriptable(mockVSChartInfo, "state");

      assertEquals(mockAxisDescriptor,  axisScriptable.getObject());
      assertTrue(axisScriptable.hasMember("minimum"));
   }

   /**
    * test setLabelAlias,setFieldAxis,putMember and getMember
    */
   @Test
   void testOtherFunctions() {
      when(mockChartInfo.isSeparatedGraph()).thenReturn(true);
      when(mockChartInfo.getFieldByName("state", true)).thenReturn(mockVSChartRef);

      axisScriptable = new AxisScriptable(mockChartInfo, "state");
      axisScriptable.setLabelAlias("label", "alias");
      axisScriptable.setFieldAxis(true);

      //labelColor,font,format,rotation: property not registered (axis is null),
      //so an unknown member resolves to null (was Rhino UniqueTag.NOT_FOUND).
      axisScriptable.putMember("font", mock(PropertyDescriptor.class));
      assertNull(axisScriptable.getMember("font"));
   }

   @Test
   void scriptLineVisibleStampsTheAuthorFlag() {
      AxisDescriptor axis = new AxisDescriptor();
      axisScriptable = new AxisScriptable(chartWithDimensionAxis(axis), "state");

      axisScriptable.putMember("lineVisible", false);

      assertFalse(axis.isLineVisible());
      assertTrue(axis.isUserLineVisible());
   }

   @Test
   void scriptAssigningTheCurrentLineStampsNothing() {
      AxisDescriptor shownOn = new AxisDescriptor();
      AxisDescriptor before = shownOn.clone();
      new AxisScriptable(chartWithDimensionAxis(shownOn), "state").putMember("lineVisible", true);

      assertFalse(shownOn.isUserLineVisible(), "a line already on");
      // the render compares runtime and design descriptors by content
      assertTrue(before.equalsContent(shownOn), "a line already on is unchanged");

      AxisDescriptor seededOff = new AxisDescriptor();
      seededOff.setLineVisible(false);
      new AxisScriptable(chartWithDimensionAxis(seededOff), "state").putMember("lineVisible", false);

      assertFalse(seededOff.isLineVisible());
      assertFalse(seededOff.isUserLineVisible(), "a seeded line already off");
   }

   // a chart whose "state" dimension owns the given axis descriptor
   private ChartInfo chartWithDimensionAxis(AxisDescriptor axis) {
      ChartInfo info = mock(ChartInfo.class);
      ChartDimensionRef ref = mock(ChartDimensionRef.class);
      when(info.isSeparatedGraph()).thenReturn(false);
      when(ref.getAxisDescriptor()).thenReturn(axis);
      when(info.getFieldByName("state", true)).thenReturn(ref);
      return info;
   }

   private VSChartDimensionRef dimRef;
   private VSChartAggregateRef primaryRef;
   private VSChartAggregateRef secondaryRef;
   private DefaultVSChartInfo realInfo;

   private void setUpRealInfo(boolean separated) {
      realInfo = new DefaultVSChartInfo();
      realInfo.setSeparatedGraph(separated);
      dimRef = new VSChartDimensionRef();
      dimRef.setGroupColumnValue("D");
      primaryRef = new VSChartAggregateRef();
      primaryRef.setColumnValue("A");
      secondaryRef = new VSChartAggregateRef();
      secondaryRef.setColumnValue("B");
      secondaryRef.setSecondaryY(true);

      for(VSChartRef ref : new VSChartRef[] { dimRef, primaryRef, secondaryRef }) {
         ref.setRTAxisDescriptor(new AxisDescriptor());
      }

      realInfo.setRTYFields(new ChartRef[] { dimRef, primaryRef, secondaryRef });
      realInfo.setRTAxisDescriptor(new AxisDescriptor());
      realInfo.setRTAxisDescriptor2(new AxisDescriptor());
   }

   @Test
   void y2AxisSeparatedOnlyWritesSecondaryAndOwnAxis() {
      setUpRealInfo(true);
      AxisScriptable y2 = new AxisScriptable(realInfo, realInfo.getRTAxisDescriptor2(), false, true);
      y2.putMember("lineVisible", false);

      assertTrue(dimRef.getRTAxisDescriptor().isLineVisible());
      assertTrue(primaryRef.getRTAxisDescriptor().isLineVisible());
      assertFalse(secondaryRef.getRTAxisDescriptor().isLineVisible());
      assertFalse(realInfo.getRTAxisDescriptor2().isLineVisible());
      assertTrue(realInfo.getRTAxisDescriptor().isLineVisible());
      assertEquals(false, y2.getMember("lineVisible"));
   }

   @Test
   void y2AxisNonSeparatedSkipsDimension() {
      setUpRealInfo(false);
      AxisScriptable y2 = new AxisScriptable(realInfo, realInfo.getRTAxisDescriptor2(), false, true);
      y2.putMember("lineVisible", false);

      assertTrue(dimRef.getRTAxisDescriptor().isLineVisible());
      assertFalse(realInfo.getRTAxisDescriptor2().isLineVisible());
      assertTrue(realInfo.getRTAxisDescriptor().isLineVisible());
   }

   @Test
   void yAxisSeparatedBehaviorUnchanged() {
      setUpRealInfo(true);
      AxisScriptable y = new AxisScriptable(realInfo, realInfo.getRTAxisDescriptor(), false, false);
      y.putMember("lineVisible", false);

      assertFalse(dimRef.getRTAxisDescriptor().isLineVisible());
      assertFalse(primaryRef.getRTAxisDescriptor().isLineVisible());
      assertFalse(secondaryRef.getRTAxisDescriptor().isLineVisible());
      assertTrue(realInfo.getRTAxisDescriptor2().isLineVisible());
   }

   private VSChartDimensionRef xDim;
   private VSChartAggregateRef yMeasure1;
   private VSChartAggregateRef yMeasure2;

   private void setUpDimOnX(boolean separated) {
      realInfo = new DefaultVSChartInfo();
      realInfo.setSeparatedGraph(separated);
      xDim = new VSChartDimensionRef();
      xDim.setGroupColumnValue("Q");
      yMeasure1 = new VSChartAggregateRef();
      yMeasure1.setColumnValue("M1");
      yMeasure2 = new VSChartAggregateRef();
      yMeasure2.setColumnValue("M2");

      for(VSChartRef ref : new VSChartRef[] { xDim, yMeasure1, yMeasure2 }) {
         ref.setRTAxisDescriptor(new AxisDescriptor());
      }

      realInfo.setRTXFields(new ChartRef[] { xDim });
      realInfo.setRTYFields(new ChartRef[] { yMeasure1, yMeasure2 });
      realInfo.setRTAxisDescriptor(new AxisDescriptor());
      realInfo.setRTAxisDescriptor2(new AxisDescriptor());
   }

   private AxisScriptable xAxis() {
      return new AxisScriptable(realInfo, realInfo.getRTAxisDescriptor(), true, false);
   }

   private AxisScriptable yAxis() {
      return new AxisScriptable(realInfo, realInfo.getRTAxisDescriptor(), false, false);
   }

   @Test
   void xAxisReadIgnoresYAxisLineColorWrite() {
      setUpDimOnX(false);
      AxisScriptable x = xAxis();
      Object def = x.getMember("lineColor");
      AxisScriptable y = yAxis();
      y.putMember("lineColor", java.awt.Color.MAGENTA);

      assertEquals(java.awt.Color.MAGENTA, y.getMember("lineColor"));
      assertEquals(def, xAxis().getMember("lineColor"));
      assertEquals(def, x.getMember("lineColor"));
      assertNotEquals(java.awt.Color.MAGENTA, xAxis().getMember("lineColor"));
   }

   @Test
   void xAxisLineVisibleUnaffectedByYAxisWrite() {
      setUpDimOnX(false);
      yAxis().putMember("lineVisible", false);

      assertEquals(false, yAxis().getMember("lineVisible"));
      assertEquals(true, xAxis().getMember("lineVisible"));
   }

   @Test
   void xAxisWriteReadsBack() {
      setUpDimOnX(false);
      AxisScriptable x = xAxis();
      x.putMember("lineColor", java.awt.Color.BLUE);

      assertEquals(java.awt.Color.BLUE, x.getMember("lineColor"));
      assertEquals(java.awt.Color.BLUE, xDim.getRTAxisDescriptor().getLineColor());
      assertNotEquals(java.awt.Color.BLUE, realInfo.getRTAxisDescriptor().getLineColor());
   }

   @Test
   void xAxisFormatPropertyReadsBack() {
      setUpDimOnX(false);
      AxisScriptable x = xAxis();
      x.putMember("labelColor", java.awt.Color.RED);

      assertEquals(java.awt.Color.RED, x.getMember("labelColor"));
      assertNotEquals(java.awt.Color.RED, yAxis().getMember("labelColor"));
   }

   @Test
   void yAxisAndFieldAxisStillReadBack() {
      setUpDimOnX(false);
      AxisScriptable y = yAxis();
      y.putMember("lineColor", java.awt.Color.GREEN);
      assertEquals(java.awt.Color.GREEN, y.getMember("lineColor"));

      xDim.getRTAxisDescriptor().setLineColor(java.awt.Color.ORANGE);
      AxisScriptable field = new AxisScriptable(realInfo, "Q");
      assertEquals(java.awt.Color.ORANGE, field.getMember("lineColor"));
   }

   @Test
   void xAxisReadsFirstDimensionWhenMeasureComesFirst() {
      setUpDimOnX(false);
      realInfo.setRTXFields(new ChartRef[] { yMeasure1, xDim });
      xDim.getRTAxisDescriptor().setLineColor(java.awt.Color.CYAN);

      assertEquals(java.awt.Color.CYAN, xAxis().getMember("lineColor"));
   }

   @Test
   void xAxisReadFollowsReboundFields() {
      setUpDimOnX(false);
      AxisScriptable x = xAxis();
      xDim.getRTAxisDescriptor().setLineColor(java.awt.Color.CYAN);
      assertEquals(java.awt.Color.CYAN, x.getMember("lineColor"));

      VSChartDimensionRef other = new VSChartDimensionRef();
      other.setGroupColumnValue("R");
      other.setRTAxisDescriptor(new AxisDescriptor());
      other.getRTAxisDescriptor().setLineColor(java.awt.Color.PINK);
      realInfo.setRTXFields(new ChartRef[] { other });

      assertEquals(java.awt.Color.PINK, x.getMember("lineColor"));
   }

   @Test
   void xAxisWithoutFieldsFallsBackToSharedDescriptor() {
      setUpDimOnX(false);
      realInfo.setRTXFields(new ChartRef[0]);
      AxisScriptable x = xAxis();
      x.putMember("lineColor", java.awt.Color.BLUE);
      realInfo.getRTAxisDescriptor().setLineColor(java.awt.Color.YELLOW);

      assertEquals(java.awt.Color.YELLOW, x.getMember("lineColor"));
   }
}
