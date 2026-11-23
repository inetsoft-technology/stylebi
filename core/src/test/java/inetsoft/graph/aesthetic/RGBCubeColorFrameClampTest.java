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

import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82070: RGBCubeColorFrame.getColor() must not throw for a one-colour path,
 * and must clamp the end colour's components like it clamps interpolated ones.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RGBCubeColorFrameClampTest {
   private static void assertRgb(int r, int g, int b, Color c) {
      assertEquals(r, c.getRed(), "red");
      assertEquals(g, c.getGreen(), "green");
      assertEquals(b, c.getBlue(), "blue");
   }

   @Test
   void singleColorPathGivesThatColorForEveryRatio() {
      RGBCubeColorFrame frame = new RGBCubeColorFrame(new double[][] { {1, 0, 0} });

      for(double ratio : new double[] { 0, 0.25, 0.5, 0.999, 1 }) {
         assertRgb(255, 0, 0, frame.getColor(ratio));
      }
   }

   @Test
   void endComponentAboveOneIsClampedTo255() {
      RGBCubeColorFrame frame =
         new RGBCubeColorFrame(new double[][] { {0, 0, 0}, {1.2, 0, 0} });

      assertRgb(255, 0, 0, frame.getColor(1));
   }

   @Test
   void endComponentBelowZeroIsClampedTo0() {
      RGBCubeColorFrame frame =
         new RGBCubeColorFrame(new double[][] { {1, 1, 1}, {-0.5, 0, 0} });

      assertRgb(0, 0, 0, frame.getColor(1));
   }

   @Test
   void singleColorPathIsClamped() {
      RGBCubeColorFrame frame = new RGBCubeColorFrame(new double[][] { {2, -1, 0.5} });

      assertRgb(255, 0, 127, frame.getColor(0.5));
      assertRgb(255, 0, 127, frame.getColor(1));
   }

   @Test
   void interiorInterpolationIsUnchanged() {
      RGBCubeColorFrame frame =
         new RGBCubeColorFrame(new double[][] { {0, 0, 0}, {1, 1, 1} });

      assertRgb(0, 0, 0, frame.getColor(0));
      assertRgb(127, 127, 127, frame.getColor(0.5));
      assertRgb(255, 255, 255, frame.getColor(1));

      RGBCubeColorFrame over =
         new RGBCubeColorFrame(new double[][] { {0, 0, 0}, {2, 0, 0} });
      assertRgb(255, 0, 0, over.getColor(0.75));
   }

   @Test
   void emptyPathIsBlack() {
      assertRgb(0, 0, 0, new RGBCubeColorFrame().getColor(0.5));
   }
}
