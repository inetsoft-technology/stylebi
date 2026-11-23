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

import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82045: ColorFrame.process() must clamp the channels after applying the
 * brightness factor instead of throwing IllegalArgumentException.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class Bug82045BrightnessClampTest {
   private static final Color BASE = new Color(200, 100, 40, 128);

   @Test
   void brightnessAboveOneClampsChannels() {
      Color c = new StaticColorFrame().process(BASE, 1.5);

      assertEquals(new Color(255, 150, 60, 128), c);
   }

   @Test
   void negativeBrightnessClampsToZero() {
      Color c = new StaticColorFrame().process(BASE, -0.5);

      assertEquals(new Color(0, 0, 0, 128), c);
   }

   @Test
   void brightnessAboveOneThroughAFrame() {
      StaticColorFrame frame = new StaticColorFrame(new Color(200, 100, 40));
      frame.setBrightness(1.5);

      assertEquals(new Color(255, 150, 60), frame.getColor((Object) null));
   }

   @Test
   void grayscaleUsesTheClampedChannels() {
      StaticColorFrame frame = new StaticColorFrame();
      frame.setGrayscale(true);

      // clamped (255, 255, 255) -> y = 255; unclamped would be 0.3*450 + ... > 255
      Color c = frame.process(new Color(250, 250, 250), 1.8);
      assertEquals(new Color(255, 255, 255), c);

      // clamped (255, 150, 60) -> y = 0.3*255 + 0.59*150 + 0.11*60 = 171.6
      c = frame.process(new Color(200, 100, 40), 1.5);
      assertEquals(new Color(171, 171, 171), c);
   }

   @Test
   void brightnessInRangeIsUnchanged() {
      StaticColorFrame frame = new StaticColorFrame();

      assertSame(BASE, frame.process(BASE, 1));
      assertEquals(new Color(100, 50, 20, 128), frame.process(BASE, 0.5));
      assertEquals(new Color(0, 0, 0, 128), frame.process(BASE, 0));

      frame.setGrayscale(true);
      // y = 0.3*100 + 0.59*50 + 0.11*20 = 61.7
      assertEquals(new Color(61, 61, 61, 128), frame.process(BASE, 0.5));
   }
}
