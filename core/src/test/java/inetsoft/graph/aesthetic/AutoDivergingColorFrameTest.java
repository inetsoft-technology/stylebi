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
package inetsoft.graph.aesthetic;

import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.scale.LogScale;
import inetsoft.graph.scale.Scale;
import inetsoft.graph.visual.ElementVO;
import inetsoft.report.composition.graph.BrushDataSet;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A Teal that draws the diverging ramp, centred on zero, when its data crosses zero.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AutoDivergingColorFrameTest {
   private static final String FIELD = "Sum(V)";

   private static DataSet data(double... values) {
      Object[][] rows = new Object[values.length + 1][];
      rows[0] = new Object[] { FIELD };

      for(int i = 0; i < values.length; i++) {
         rows[i + 1] = new Object[] { values[i] };
      }

      return new DefaultDataSet(rows);
   }

   private static AutoDivergingColorFrame frame(DataSet data) {
      TealColorFrame teal = new TealColorFrame();
      teal.setField(FIELD);
      AutoDivergingColorFrame frame = AutoDivergingColorFrame.from(teal);
      frame.init(data);
      return frame;
   }

   @Test
   void staysTealWhenTheRangeIsPositive() {
      AutoDivergingColorFrame frame = frame(data(1, 2, 3, 4, 5));
      assertFalse(frame.isDiverging());
      assertEquals(new TealColorFrame().getColor(0.5), frame.getColor(0.5));
   }

   @Test
   void touchingZeroStaysTeal() {
      assertFalse(frame(data(0, 1, 3, 5)).isDiverging());
   }

   @Test
   void crossingZeroDrawsVarianceCentredOnZero() {
      AutoDivergingColorFrame frame = frame(data(-2, 6));
      VarianceColorFrame variance = new VarianceColorFrame();
      assertTrue(frame.isDiverging());
      assertEquals(-frame.getScale().getMax(), frame.getScale().getMin());
      assertEquals(variance.getColor(0.5), frame.getColor((Object) 0.0));
      assertEquals(variance.getColor(1.0), frame.getColor((Object) frame.getScale().getMax()));
   }

   @Test
   void aRebuiltScaleIsCentredAgain() {
      DataSet data = data(-2, 6);
      AutoDivergingColorFrame frame = frame(data);
      frame.setScaleOption(frame.getScaleOption() | Scale.ZERO);
      frame.setScale(null);
      frame.init(data);
      assertTrue(frame.isDiverging());
      assertEquals(-frame.getScale().getMax(), frame.getScale().getMin());
   }

   @Test
   void aReversedScaleIsCentred() {
      DataSet data = data(-2, 6);
      TealColorFrame teal = new TealColorFrame();
      teal.setField(FIELD);
      LinearScale scale = new LinearScale();
      scale.setReversed(true);
      scale.setFields(FIELD);
      teal.setScale(scale);
      AutoDivergingColorFrame frame = AutoDivergingColorFrame.from(teal);
      frame.init(data);
      assertTrue(frame.isDiverging());
      assertEquals(-frame.getScale().getMax(), frame.getScale().getMin());

      // reversed: the largest value takes the ramp's low end
      double m = Math.abs(frame.getScale().getMax());
      VarianceColorFrame variance = new VarianceColorFrame();
      assertEquals(variance.getColor(0.0), frame.getColor((Object) m));
      assertEquals(variance.getColor(0.5), frame.getColor((Object) 0.0));
      assertEquals(variance.getColor(1.0), frame.getColor((Object) (-m)));
      assertTrue(frame.getScale().getMin() > frame.getScale().getMax(),
                 "a reversed scale runs from +M down to -M");
   }

   @Test
   void aLogScaleNeverDiverges() {
      DataSet data = data(-2, 6);
      TealColorFrame teal = new TealColorFrame();
      teal.setField(FIELD);
      LogScale scale = new LogScale();
      scale.setFields(FIELD);
      teal.setScale(scale);
      AutoDivergingColorFrame frame = AutoDivergingColorFrame.from(teal);
      frame.init(data);
      assertFalse(frame.isDiverging());
   }

   @Test
   void theAllDataColumnDecides() {
      BrushDataSet data = brush(false);
      assertTrue(data.indexOfHeader(ElementVO.ALL_PREFIX + FIELD) >= 0,
                 "premise: the brushed data carries the all-data column");

      AutoDivergingColorFrame frame = frame(data);
      assertTrue(frame.isDiverging());
      assertEquals(-3, frame.getScale().getMin());
      assertEquals(3, frame.getScale().getMax());
   }

   @Test
   void aBrushedOnlySetDecidesOnItsSignData() {
      BrushDataSet data = brush(true);
      assertNull(data.getData(ElementVO.ALL_PREFIX + FIELD, 0),
                 "premise: brushed rows only, so the all-data column holds no value");

      TealColorFrame teal = new TealColorFrame();
      teal.setField(FIELD);
      AutoDivergingColorFrame frame = AutoDivergingColorFrame.from(teal);
      frame.setSignData(states(-2, 1, 3));
      frame.init(data);

      // decided on all data, centred on the brushed range
      assertTrue(frame.isDiverging());
      assertEquals(-3, frame.getScale().getMin());
      assertEquals(3, frame.getScale().getMax());
   }

   @Test
   void signDataDecidesOverThePlottedData() {
      TealColorFrame teal = new TealColorFrame();
      teal.setField(FIELD);
      AutoDivergingColorFrame frame = AutoDivergingColorFrame.from(teal);
      frame.setSignData(data(-2, 6));
      frame.init(data(1, 5));

      assertTrue(frame.isDiverging());
      assertEquals(-5, frame.getScale().getMin());
      assertEquals(5, frame.getScale().getMax());
   }

   @Test
   void signDataWithoutTheFieldIsIgnored() {
      DataSet other = new DefaultDataSet(new Object[][] { { "Other" }, { -2.0 }, { 6.0 } });

      TealColorFrame teal = new TealColorFrame();
      teal.setField(FIELD);
      AutoDivergingColorFrame positive = AutoDivergingColorFrame.from(teal);
      positive.setSignData(other);
      positive.init(data(1, 5));
      assertFalse(positive.isDiverging());

      AutoDivergingColorFrame crossing = AutoDivergingColorFrame.from(teal);
      crossing.setSignData(other);
      crossing.init(data(-2, 6));
      assertTrue(crossing.isDiverging());
   }

   @Test
   void signDataIsNotPartOfEquality() {
      AutoDivergingColorFrame a = frame(data(1, 5));
      AutoDivergingColorFrame b = frame(data(1, 5));
      b.setSignData(data(1, 5));
      assertEquals(a, b);
      assertEquals(a.hashCode(), b.hashCode());
   }

   // all data CA -2, NY 1, TX 3; brushed NY 1, TX 3
   private static BrushDataSet brush(boolean brushedOnly) {
      BrushDataSet data = new BrushDataSet(states(-2, 1, 3), states(1, 3));
      data.setBrushedDataOnly(brushedOnly);
      return data;
   }

   private static DataSet states(double... values) {
      String[] all = { "CA", "NY", "TX" };
      String[] names = values.length == all.length ? all : new String[] { "NY", "TX" };
      Object[][] rows = new Object[values.length + 1][];
      rows[0] = new Object[] { "State", FIELD };

      for(int i = 0; i < values.length; i++) {
         rows[i + 1] = new Object[] { names[i], values[i] };
      }

      return new DefaultDataSet(rows);
   }

   @Test
   void aLaterPositiveRangeReturnsToTeal() {
      AutoDivergingColorFrame frame = frame(data(-2, 6));
      assertTrue(frame.isDiverging());

      frame.setScale(null);
      frame.init(data(1, 5));
      assertFalse(frame.isDiverging());
      assertEquals(new TealColorFrame().getColor(0.3), frame.getColor(0.3));
   }

   @Test
   void lowStopRemapsOnlyTeal() {
      AutoDivergingColorFrame positive = frame(data(1, 5));
      positive.setLowStop(0.25);
      assertEquals(new TealColorFrame().getColor(0.25), positive.getColor(0.0));

      AutoDivergingColorFrame crossing = frame(data(-2, 6));
      crossing.setLowStop(0.25);
      assertEquals(new VarianceColorFrame().getColor(0.5), crossing.getColor(0.5));
   }

   @Test
   void fromCopiesTheTealsSettings() {
      TealColorFrame teal = new TealColorFrame();
      teal.setField(FIELD);
      teal.setScaleOption(Scale.ZERO);
      teal.getLegendSpec().setVisible(false);
      LinearScale scale = new LinearScale(FIELD);
      teal.setScale(scale);

      AutoDivergingColorFrame copy = AutoDivergingColorFrame.from(teal);
      assertEquals(FIELD, copy.getField());
      assertEquals(Scale.ZERO, copy.getScaleOption());
      assertFalse(copy.getLegendSpec().isVisible());
      assertNotNull(copy.getScale());
      assertEquals(0, copy.getLowStop());
   }

   @Test
   void equalsSeesDivergingAndLowStop() {
      AutoDivergingColorFrame a = frame(data(1, 5));
      AutoDivergingColorFrame b = frame(data(1, 5));
      assertEquals(a, b);
      assertEquals(a.hashCode(), b.hashCode());

      b.setLowStop(0.25);
      assertNotEquals(a, b);

      assertNotEquals(a, frame(data(-2, 6)));
   }

   @Test
   void aCloneKeepsItsState() {
      AutoDivergingColorFrame frame = frame(data(-2, 6));
      frame.setLowStop(0.25);
      DataSet sign = data(-2, 6);
      frame.setSignData(sign);
      AutoDivergingColorFrame copy = (AutoDivergingColorFrame) frame.clone();
      assertTrue(copy.isDiverging());
      assertEquals(0.25, copy.getLowStop());
      assertSame(sign, copy.getSignData());
      assertEquals(frame, copy);
      assertEquals(frame.getColor((Object) 1.0), copy.getColor((Object) 1.0));
   }
}
