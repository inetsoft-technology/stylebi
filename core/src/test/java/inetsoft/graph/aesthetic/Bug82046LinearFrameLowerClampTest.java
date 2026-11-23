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

import inetsoft.graph.scale.LinearScale;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82046: the linear size/line/shape/texture frames must clamp the ratio of a
 * value below the scale minimum to 0, so it gets the minimum style.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class Bug82046LinearFrameLowerClampTest {
   @Test
   void linearSizeBelowTheMinimumNeverGoesBelowTheSmallest() {
      LinearSizeFrame frame = new LinearSizeFrame();
      frame.setScale(new LinearScale(0, 100));

      assertEquals(frame.getSmallest(), frame.getSize((Object) (-50)), 1e-9);
      assertEquals(frame.getSmallest(), frame.getSize((Object) 0), 1e-9);
      assertEquals((frame.getSmallest() + frame.getLargest()) / 2,
                   frame.getSize((Object) 50), 1e-9);
      assertEquals(frame.getLargest(), frame.getSize((Object) 150), 1e-9);
   }

   @Test
   void lineBelowMinIsClamped() {
      LinearLineFrame frame = new LinearLineFrame();
      frame.setScale(new LinearScale(0, 100));

      assertEquals(frame.getLine((Object) 0), frame.getLine((Object) (-50)));
      assertEquals(frame.getLine((Object) 100), frame.getLine((Object) 150));
      assertEquals(frame.getLine(0.5), frame.getLine((Object) 50));
   }

   @Test
   void textureBelowMinIsClamped() {
      GridTextureFrame frame = new GridTextureFrame();
      frame.setScale(new LinearScale(0, 100));

      assertEquals(frame.getTexture((Object) 0), frame.getTexture((Object) (-50)));
      assertEquals(frame.getTexture((Object) 100), frame.getTexture((Object) 150));
      assertEquals(frame.getTexture(0.5), frame.getTexture((Object) 50));
   }

   @Test
   void shapeBelowMinIsClamped() {
      OvalShapeFrame frame = new OvalShapeFrame();
      frame.setScale(new LinearScale(0, 100));

      assertEquals(pathOf(frame.getShape((Object) 0)), pathOf(frame.getShape((Object) (-50))));
      assertEquals(pathOf(frame.getShape((Object) 100)), pathOf(frame.getShape((Object) 150)));
      assertEquals(pathOf(frame.getShape(0.5)), pathOf(frame.getShape((Object) 50)));
   }

   @Test
   void polygonFarBelowMin() {
      PolygonShapeFrame frame = new PolygonShapeFrame();
      frame.setScale(new LinearScale(0, 100));

      GShape below = frame.getShape((Object) (-200));
      assertDoesNotThrow(() -> below.getShape(0, 0, 20, 20));
      assertEquals(pathOf(frame.getShape((Object) 0)), pathOf(below));
   }

   private static List<String> pathOf(GShape shape) {
      Shape s = shape.getShape(0, 0, 20, 20);
      List<String> segments = new ArrayList<>();
      for(PathIterator it = s.getPathIterator(null); !it.isDone(); it.next()) {
         double[] coords = new double[6];
         int type = it.currentSegment(coords);
         StringBuilder sb = new StringBuilder().append(type);

         for(double c : coords) {
            sb.append(',').append(Math.round(c * 1000) / 1000.0);
         }

         segments.add(sb.toString());
      }

      return segments;
   }
}
