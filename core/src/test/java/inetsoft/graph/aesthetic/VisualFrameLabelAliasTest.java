/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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

import inetsoft.graph.scale.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80629: a legend alias on a log/power scale tick threw ArrayStoreException because the
 * label array was cloned from the scale's typed Double[] values.
 */
@Tag("core")
class VisualFrameLabelAliasTest {
   private static LinearSizeFrame frame(Scale scale, Object key, String alias) {
      LinearSizeFrame frame = new LinearSizeFrame();
      frame.setScale(scale);
      DefaultTextFrame text = new DefaultTextFrame();
      text.setText(key, alias);
      frame.getLegendSpec().setTextFrame(text);
      return frame;
   }

   @Test
   void logScaleAliasOnTickIsReturned() {
      LinearSizeFrame frame = frame(new LogScale(1, 10000), "10000", "ten k");
      Object[] values = frame.getValues();
      assertInstanceOf(Double[].class, values);
      assertTrue(Arrays.asList(values).contains(10000.0));

      Object[] labels = assertDoesNotThrow(frame::getLabels);
      assertTrue(Arrays.asList(labels).contains("ten k"), Arrays.toString(labels));
   }

   @Test
   void logScaleAliasOnNonTickLeavesNumbers() {
      LinearSizeFrame frame = frame(new LogScale(1, 10000), "5", "five");
      Object[] labels = assertDoesNotThrow(frame::getLabels);
      assertFalse(Arrays.asList(labels).contains("five"));
      assertEquals(frame.getValues().length, labels.length);
   }

   @Test
   void powerScaleAliasOnTickIsReturned() {
      PowerScale scale = new PowerScale(0, 100);
      Object[] values = scale.getValues();
      assertInstanceOf(Double[].class, values);
      String key = values[values.length - 1].toString();
      LinearSizeFrame frame = frame(scale, key, "top");
      Object[] labels = assertDoesNotThrow(frame::getLabels);
      assertTrue(Arrays.asList(labels).contains("top"), Arrays.toString(labels));
   }

   @Test
   void linearScaleAliasControl() {
      LinearScale scale = new LinearScale(0, 100);
      Object[] values = scale.getValues();
      String key = values[values.length - 1].toString();
      LinearSizeFrame frame = frame(scale, key, "top");
      Object[] labels = assertDoesNotThrow(frame::getLabels);
      assertTrue(Arrays.asList(labels).contains("top"), Arrays.toString(labels));
   }

   @Test
   void logScaleWithManyTicksDoesNotThrow() {
      LogScale scale = new LogScale(1, 1e9);
      assertTrue(scale.getValues().length > 6);
      LinearSizeFrame frame = frame(scale, "10000", "ten k");
      assertDoesNotThrow(frame::getLabels);
   }
}
