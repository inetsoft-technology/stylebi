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
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The default methods of CompositeVisualFrame and the frame list handling, checked through its
 * implementation CompositeColorFrame.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class CompositeFramesTest {
   private static CategoricalColorFrame visible(String field) {
      return new CategoricalColorFrame(field);
   }

   private static CategoricalColorFrame hidden(String field) {
      CategoricalColorFrame frame = new CategoricalColorFrame(field);
      LegendSpec spec = new LegendSpec();
      spec.setVisible(false);
      frame.setLegendSpec(spec);
      return frame;
   }

   @Test
   void visualFieldsAreTheNonNullFieldsInOrder() {
      CompositeColorFrame composite = new CompositeColorFrame();
      composite.addFrame(visible("a"));
      composite.addFrame(new StaticColorFrame());
      composite.addFrame(visible("c"));

      assertArrayEquals(new String[] { "a", "c" }, composite.getVisualFields());
   }

   @Test
   void framesCanBeAddedAndRemovedByIndex() {
      CategoricalColorFrame first = visible("a");
      CategoricalColorFrame second = visible("b");
      CategoricalColorFrame third = visible("c");
      CompositeColorFrame composite = new CompositeColorFrame();
      composite.addFrame(first);
      composite.addFrame(second);
      composite.addFrame(third);

      assertEquals(3, composite.getFrameCount());

      composite.removeFrame(1);

      assertEquals(2, composite.getFrameCount());
      assertSame(first, composite.getFrame(0));
      assertSame(third, composite.getFrame(1));
   }

   @Test
   void framesOfATypeIncludeSubclassesAndKeepTheOrder() {
      CategoricalColorFrame plain = visible("a");
      CategoricalColorFrame subclass = new CategoricalColorFrame("b") {
      };
      CompositeColorFrame composite = new CompositeColorFrame();
      composite.addFrame(plain);
      composite.addFrame(new StaticColorFrame());
      composite.addFrame(subclass);

      List<VisualFrame> found =
         composite.getFrames(CategoricalColorFrame.class).collect(Collectors.toList());

      assertEquals(2, found.size());
      assertSame(plain, found.get(0));
      assertSame(subclass, found.get(1));
   }

   @Test
   void guideFrameIsTheFirstVisibleFrame() {
      CategoricalColorFrame shown = visible("b");
      CompositeColorFrame composite = new CompositeColorFrame();
      composite.addFrame(hidden("a"));
      composite.addFrame(shown);

      assertSame(shown, composite.getGuideFrame());

      CompositeColorFrame none = new CompositeColorFrame();
      none.addFrame(hidden("a"));

      assertNull(none.getGuideFrame());
   }
}
