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

import inetsoft.graph.LegendSpec;
import inetsoft.graph.scale.LinearScale;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.text.DecimalFormat;
import java.text.Format;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82073: when the legend text spec has no format, the legend of a frame takes the
 * format of the frame scale's axis text spec, on the first getLegendSpec() call and on
 * every clone, without changing the frame's own legend spec.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class Bug82073LegendScaleFormatTest {
   @Test
   void firstCallHasScaleFormat() {
      GradientColorFrame frame = createFrame();

      assertFormatted(frame.getLegendSpec().getTextSpec().getFormat());
   }

   @Test
   void ownLegendSpecIsNotChanged() {
      GradientColorFrame frame = createFrame();
      LegendSpec own = new LegendSpec();
      frame.setLegendSpec(own);

      assertFormatted(frame.getLegendSpec().getTextSpec().getFormat());
      assertNull(own.getTextSpec().getFormat(),
                 "the frame's own legend spec must not get the scale format");
   }

   @Test
   void cloneDoesNotDependOnCallHistory() {
      GradientColorFrame frame = createFrame();
      VisualFrame before = (VisualFrame) frame.clone();

      frame.getLegendSpec();
      VisualFrame after = (VisualFrame) frame.clone();

      assertFormatted(before.getLegendSpec().getTextSpec().getFormat());
      assertFormatted(after.getLegendSpec().getTextSpec().getFormat());
      assertEquals(before.getLegendSpec(), after.getLegendSpec());
   }

   @Test
   void textFrameMatchesLabelFormattedWithScaleFormat() {
      GradientColorFrame frame = createFrame();
      Object[] values = frame.getValues();
      assertNotNull(values);
      assertTrue(values.length > 0);

      DefaultTextFrame textFrame = new DefaultTextFrame();
      DecimalFormat fmt = new DecimalFormat("0.0");

      for(Object value : values) {
         textFrame.setText(fmt.format(value), "label-" + fmt.format(value));
      }

      LegendSpec own = new LegendSpec();
      own.setTextFrame(textFrame);
      frame.setLegendSpec(own);

      Object[] labels = frame.getLabels();

      for(int i = 0; i < values.length; i++) {
         assertEquals("label-" + fmt.format(values[i]), labels[i]);
      }
   }

   private static GradientColorFrame createFrame() {
      GradientColorFrame frame = new GradientColorFrame("m");
      LinearScale scale = new LinearScale(0, 100);
      scale.getAxisSpec().getTextSpec().setFormat(new DecimalFormat("0.0"));
      frame.setScale(scale);
      return frame;
   }

   private static void assertFormatted(Format format) {
      assertNotNull(format, "legend text format should come from the scale");
      assertEquals("12.3", format.format(12.34));
   }
}
