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
 * Bug #82044: GradientColorFrame.getColor(ratio) must keep the alpha computed from
 * minAlpha/maxAlpha and from the alpha of the from/to colours.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class Bug82044GradientAlphaTest {
   @Test
   void alphaRangeIsApplied() {
      GradientColorFrame frame = new GradientColorFrame();
      frame.setMinAlpha(0.2);
      frame.setMaxAlpha(1);

      assertEquals(51, frame.getColor(0).getAlpha());
      assertEquals(153, frame.getColor(0.5).getAlpha());
      assertEquals(255, frame.getColor(1).getAlpha());
   }

   @Test
   void alphaOfTheTwoColorsIsInterpolated() {
      GradientColorFrame frame = new GradientColorFrame();
      frame.setFromColor(new Color(255, 0, 0, 0));
      frame.setToColor(new Color(0, 0, 255, 255));

      assertEquals(0, frame.getColor(0).getAlpha());
      assertEquals(127, frame.getColor(0.5).getAlpha());
      assertEquals(255, frame.getColor(1).getAlpha());
   }

   @Test
   void rgbChannelsAreUnchangedByTheAlpha() {
      GradientColorFrame frame = new GradientColorFrame();
      frame.setFromColor(new Color(200, 100, 0));
      frame.setToColor(new Color(0, 100, 200));
      frame.setMinAlpha(0.2);

      Color mid = frame.getColor(0.5);
      assertEquals(100, mid.getRed());
      assertEquals(100, mid.getGreen());
      assertEquals(100, mid.getBlue());
   }

   @Test
   void defaultAlphaStaysOpaque() {
      GradientColorFrame frame = new GradientColorFrame();

      assertEquals(255, frame.getColor(0).getAlpha());
      assertEquals(255, frame.getColor(0.5).getAlpha());
      assertEquals(255, frame.getColor(1).getAlpha());
      assertEquals(new Color(0xFF99CC), frame.getColor(0));
      assertEquals(new Color(0x008000), frame.getColor(1));
   }

   @Test
   void ratioOutsideZeroToOneDoesNotThrow() {
      GradientColorFrame frame = new GradientColorFrame();
      frame.setFromColor(new Color(255, 0, 0, 0));
      frame.setToColor(new Color(0, 0, 255, 255));
      frame.setMaxAlpha(1.5);

      assertDoesNotThrow(() -> frame.getColor(-0.5));
      assertDoesNotThrow(() -> frame.getColor(1.5));
      assertEquals(255, frame.getColor(1.5).getAlpha());
   }
}
