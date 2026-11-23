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

import inetsoft.graph.LegendSpec;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.scale.LinearScale;
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link CompositeColorFrame}: the data path cascades through the sub frames (first
 * frame that answers wins), the value (legend) path is answered by the guide frame (first visible
 * frame), and the setters are delegated to the frames that own the setting.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class CompositeColorFrameTest {
   /** Configurable stub: answers a fixed colour (or null) for configured rows. */
   static class StubColor extends ColorFrame {
      Color result;
      boolean visible;
      int[] rows; // null: every row
      int initCount;
      int lastOption = -99;

      StubColor(Color result, boolean visible) {
         this.result = result;
         this.visible = visible;
      }

      @Override
      public boolean isVisible() {
         return visible;
      }

      @Override
      public Color getColor(DataSet data, String col, int row) {
         if(rows != null) {
            boolean found = false;

            for(int r : rows) {
               found |= r == row;
            }

            if(!found) {
               return null;
            }
         }

         return result;
      }

      @Override
      public Color getColor(Object val) {
         return result;
      }

      @Override
      public void init(DataSet data) {
         initCount++;
      }

      @Override
      public void setScaleOption(int option) {
         lastOption = option;
         super.setScaleOption(option);
      }
   }

   private static DataSet data() {
      return new DefaultDataSet(new Object[][] {
         { "c", "v" }, { "a", 0.0 }, { "b", 25.0 }, { "c", 50.0 }, { "d", 75.0 }
      });
   }

   private static LinearScale s100() {
      LinearScale s = new LinearScale(0, 100);
      assertEquals(0, s.getMin(), 0);
      assertEquals(100, s.getMax(), 0);
      return s;
   }

   /** Gradient from (0,0,0) to (200,100,40): at ratio r the colour is (200r,100r,40r). */
   private static GradientColorFrame grx(String field) {
      GradientColorFrame f = new GradientColorFrame(field);
      f.setFromColor(new Color(0, 0, 0));
      f.setToColor(new Color(200, 100, 40));
      return f;
   }

   private static GradientColorFrame grx100(String field) {
      GradientColorFrame f = grx(field);
      f.setScale(s100());
      return f;
   }

   private static void assertColor(int r, int g, int b, Color c) {
      assertNotNull(c);
      assertEquals(r, c.getRed(), "red");
      assertEquals(g, c.getGreen(), "green");
      assertEquals(b, c.getBlue(), "blue");
   }

   @Test
   void emptyCompositeHasNoFrameAndNoValues() {
      CompositeColorFrame c = new CompositeColorFrame();
      assertEquals(0, c.getFrameCount());
      assertNull(c.getGuideFrame());
      assertNull(c.getField());
      assertEquals(0, c.getValues().length);
      assertEquals(0, c.getLabels().length);
      assertNull(c.getColor("x"));
   }

   @Test
   @DisplayName("pinned: empty composite data path answers gray")
   void emptyCompositeDataPathAnswersGray() {
      assertEquals(Color.gray, new CompositeColorFrame().getColor(data(), "c", 0));
   }

   @Test
   void addGetAndRemoveFrames() {
      CompositeColorFrame c = new CompositeColorFrame();
      StubColor a = new StubColor(Color.RED, true);
      StubColor b = new StubColor(Color.GREEN, true);
      StubColor d = new StubColor(Color.BLUE, true);
      c.addFrame(a);
      c.addFrame(b);
      c.addFrame(d);
      assertSame(a, c.getFrame(0));
      assertSame(b, c.getFrame(1));
      assertSame(d, c.getFrame(2));
      c.removeFrame(1);
      assertEquals(2, c.getFrameCount());
      assertSame(a, c.getFrame(0));
      assertSame(d, c.getFrame(1));
   }

   @Test
   void cascadeFirstFrameThatAnswersWins() {
      CompositeColorFrame c = new CompositeColorFrame();
      c.addFrame(new StubColor(Color.GREEN, true));
      c.addFrame(new StubColor(Color.RED, true));
      assertEquals(Color.GREEN, c.getColor(data(), "c", 0));
   }

   @Test
   void cascadeSkipsFramesThatAnswerNull() {
      CompositeColorFrame c = new CompositeColorFrame();
      c.addFrame(new StubColor(null, true));
      c.addFrame(new StubColor(Color.RED, true));
      assertEquals(Color.RED, c.getColor(data(), "c", 0));
   }

   @Test
   @DisplayName("pinned: cascade where every frame answers null gives gray")
   void cascadeWithoutAnswerGivesGray() {
      CompositeColorFrame c = new CompositeColorFrame();
      c.addFrame(new StubColor(null, true));
      c.addFrame(new StubColor(null, true));
      assertEquals(Color.gray, c.getColor(data(), "c", 0));
   }

   @Test
   void brushingRecipeUsesHighlightFrameForItsRowsAndGradientForTheRest() {
      StubColor highlight = new StubColor(Color.MAGENTA, true);
      highlight.rows = new int[] { 0, 2 };
      CompositeColorFrame c = new CompositeColorFrame();
      c.addFrame(highlight);
      c.addFrame(grx100("v"));
      DataSet data = data();
      assertEquals(Color.MAGENTA, c.getColor(data, "c", 0));
      assertColor(50, 25, 10, c.getColor(data, "c", 1));
      assertEquals(Color.MAGENTA, c.getColor(data, "c", 2));
      assertColor(150, 75, 30, c.getColor(data, "c", 3));
   }

   @Test
   void valuePathUsesGuideFrameNotStaticFrame() {
      CompositeColorFrame c = new CompositeColorFrame();
      c.addFrame(new StaticColorFrame(Color.RED));
      c.addFrame(grx100("v"));
      assertColor(100, 50, 20, c.getColor(50.0));
   }

   @Test
   void valuePathWithoutGuideFrameUsesStaticFrame() {
      CompositeColorFrame c = new CompositeColorFrame();
      c.addFrame(new StubColor(null, false));
      c.addFrame(new StaticColorFrame(Color.BLUE));
      assertEquals(Color.BLUE, c.getColor("x"));
   }

   @Test
   void valuePathWithoutGuideFrameFallsBackToCategoricalFrame() {
      CategoricalColorFrame cat = new CategoricalColorFrame();
      cat.setColor("a", Color.MAGENTA);
      CompositeColorFrame c = new CompositeColorFrame();
      c.addFrame(new StubColor(Color.RED, false));
      c.addFrame(cat);
      assertEquals(Color.MAGENTA, c.getColor("a"));
   }

   @Test
   void valuePathWithoutAnyAnswerIsNull() {
      CompositeColorFrame c = new CompositeColorFrame();
      c.addFrame(new StubColor(Color.RED, false));
      assertNull(c.getColor("x"));
   }

   @Test
   void valuePathWhenGuideFrameAnswersNullIsNull() {
      CompositeColorFrame c = new CompositeColorFrame();
      c.addFrame(grx100("v"));
      c.addFrame(new StaticColorFrame(Color.BLUE));
      assertNull(c.getColor(Double.NaN));
   }

   @Test
   void guideFrameIsFirstVisibleFrame() {
      StubColor hidden = new StubColor(Color.RED, false);
      StubColor a = new StubColor(Color.RED, true);
      StubColor b = new StubColor(Color.RED, true);
      CompositeColorFrame c = new CompositeColorFrame();
      c.addFrame(hidden);
      c.addFrame(a);
      c.addFrame(b);
      assertSame(a, c.getGuideFrame());

      CompositeColorFrame none = new CompositeColorFrame();
      none.addFrame(new StubColor(Color.RED, false));
      assertNull(none.getGuideFrame());
   }

   @Test
   void fieldOfCompositeFollowsGuideFrameThenHiddenFrames() {
      StubColor visA = new StubColor(null, true);
      visA.setField("a");
      StubColor hidB = new StubColor(null, false);
      hidB.setField("b");

      CompositeColorFrame onlyVisible = new CompositeColorFrame();
      onlyVisible.addFrame(visA);
      assertEquals("a", onlyVisible.getField());

      CompositeColorFrame onlyHidden = new CompositeColorFrame();
      onlyHidden.addFrame(hidB);
      assertEquals("b", onlyHidden.getField());

      CompositeColorFrame both = new CompositeColorFrame();
      both.addFrame(visA);
      both.addFrame(hidB);
      assertEquals("a", both.getField());

      CompositeColorFrame none = new CompositeColorFrame();
      none.addFrame(new StubColor(null, false));
      assertNull(none.getField());
   }

   @Test
   void setFieldGoesToGuideFrameOnly() {
      StubColor a = new StubColor(null, true);
      StubColor b = new StubColor(null, false);
      CompositeColorFrame c = new CompositeColorFrame();
      c.addFrame(a);
      c.addFrame(b);
      c.setField("z");
      assertEquals("z", a.getField());
      assertNull(b.getField());
      assertDoesNotThrow(() -> new CompositeColorFrame().setField("z"));
   }

   @Test
   void scaleIsDelegatedToGuideFrame() {
      StaticColorFrame stat = new StaticColorFrame(Color.RED);
      GradientColorFrame grad = grx("v");
      CompositeColorFrame c = new CompositeColorFrame();
      c.addFrame(stat);
      c.addFrame(grad);
      LinearScale s = s100();
      c.setScale(s);
      assertSame(s, grad.getScale());
      assertSame(s, c.getScale());
      assertNull(stat.getScale());

      CompositeColorFrame empty = new CompositeColorFrame();
      assertNull(empty.getScale());
      assertDoesNotThrow(() -> empty.setScale(s100()));
   }

   @Test
   void brightnessReachesSubFrames() {
      StaticColorFrame stat = new StaticColorFrame(new Color(200, 100, 40));
      GradientColorFrame grad = new GradientColorFrame();
      CompositeColorFrame c = new CompositeColorFrame();
      c.addFrame(stat);
      c.addFrame(grad);
      c.setBrightness(0.5);
      assertEquals(0.5, c.getBrightness(), 0);
      assertEquals(0.5, stat.getBrightness(), 0);
      assertEquals(0.5, grad.getBrightness(), 0);
      assertColor(100, 50, 20, stat.getColor("x"));
   }

   // Known limitation, no test (a permanently disabled test gives no signal, so it was
   // removed). CompositeColorFrame does not override setGrayscale, so a grayscale flag set
   // on the composite never reaches the sub frames. Nothing in the product calls it: the
   // binding panels have no grayscale option and CompositeColorFrame is not a script class.
   // Only a graph script calling setGrayscale on the composite of a highlighted or brushed
   // chart (graph.getElement(0).getColorFrame()) reaches this. Add a test if that script
   // path is ever supported.
   @Test
   void scaleOptionAndInitReachEveryFrame() {
      StubColor hidden = new StubColor(null, false);
      StubColor shown = new StubColor(null, true);
      CompositeColorFrame c = new CompositeColorFrame();
      c.addFrame(hidden);
      c.addFrame(shown);
      c.setScaleOption(0);
      c.init(data());
      assertEquals(0, hidden.lastOption);
      assertEquals(0, shown.lastOption);
      assertEquals(1, hidden.initCount);
      assertEquals(1, shown.initCount);
   }

   @Test
   void visualFieldIsFirstFrameThatHasOne() {
      StubColor none = new StubColor(null, true);
      StubColor withField = new StubColor(null, true);
      withField.setField("v");
      CompositeColorFrame c = new CompositeColorFrame();
      c.addFrame(none);
      c.addFrame(withField);
      assertEquals("v", c.getVisualField());

      CompositeColorFrame noField = new CompositeColorFrame();
      noField.addFrame(new StubColor(null, true));
      assertNull(noField.getVisualField());
   }

   @Test
   void valuesAndLabelsComeFromGuideFrame() {
      GradientColorFrame hidden = grx("v");
      hidden.setScale(new LinearScale(0, 10));
      hidden.getLegendSpec().setVisible(false);
      GradientColorFrame shown = grx100("v");
      CompositeColorFrame c = new CompositeColorFrame();
      c.addFrame(hidden);
      c.addFrame(shown);
      assertSame(shown, c.getGuideFrame());
      assertTrue(shown.getValues().length > 0);
      assertArrayEquals(shown.getValues(), c.getValues());
      assertArrayEquals(shown.getLabels(), c.getLabels());
      assertFalse(java.util.Arrays.equals(hidden.getValues(), c.getValues()));
   }

   @Test
   void titleComesFromGuideFrameOrCatalog() {
      GradientColorFrame sales = grx100("sales");
      CompositeColorFrame c = new CompositeColorFrame();
      c.addFrame(sales);
      assertEquals("sales", c.getTitle());
      LegendSpec spec = new LegendSpec();
      spec.setTitle("My Title");
      sales.setLegendSpec(spec);
      assertEquals("My Title", c.getTitle());
      assertEquals("Color", new CompositeColorFrame().getTitle());
   }

   @Test
   void legendSpecIsDelegatedToGuideFrame() {
      GradientColorFrame a = new GradientColorFrame();
      CompositeColorFrame c = new CompositeColorFrame();
      c.addFrame(a);
      LegendSpec spec = new LegendSpec();
      c.setLegendSpec(spec);
      assertSame(spec, a.getLegendSpec());
      assertSame(spec, c.getLegendSpec());

      CompositeColorFrame empty = new CompositeColorFrame();
      LegendSpec own = new LegendSpec();
      empty.setLegendSpec(own);
      assertSame(own, empty.getLegendSpec());
   }

   @Test
   void cloneIsDeep() {
      CompositeColorFrame c = new CompositeColorFrame();
      GradientColorFrame grad = grx100("v");
      c.addFrame(grad);
      c.addFrame(new StaticColorFrame(Color.RED));
      CompositeColorFrame copy = (CompositeColorFrame) c.clone();
      assertEquals(2, copy.getFrameCount());
      assertNotSame(c.getFrame(0), copy.getFrame(0));
      assertNotSame(c.getFrame(1), copy.getFrame(1));
      assertColor(100, 50, 20, c.getColor(50.0));
      assertColor(100, 50, 20, copy.getColor(50.0));
      ((ColorFrame) copy.getFrame(0)).setBrightness(0.5);
      assertEquals(1, grad.getBrightness(), 0);
      assertColor(100, 50, 20, c.getColor(50.0));
   }

   @Test
   void isValidWhenAnySubFrameIsValid() {
      CompositeColorFrame c = new CompositeColorFrame();
      GradientColorFrame grad = grx("v");
      c.addFrame(new StubColor(null, true));
      c.addFrame(grad);
      assertFalse(c.isValid());
      c.init(data());
      assertTrue(grad.isValid());
      assertTrue(c.isValid());
   }

   @Test
   void legendFrameAndShareIdComeFromFrames() {
      GradientColorFrame a = grx100("v");
      GradientColorFrame legend = grx100("w");
      a.setLegendFrame(legend);
      CompositeColorFrame c = new CompositeColorFrame();
      c.addFrame(a);
      c.addFrame(new StaticColorFrame(Color.RED));
      assertSame(legend, c.getLegendFrame());
      assertEquals(a.getShareId(), c.getShareId());

      CompositeColorFrame empty = new CompositeColorFrame();
      assertNull(empty.getLegendFrame());
      assertDoesNotThrow(empty::getShareId);
   }

   @Test
   @DisplayName("composites with the same guide frame are equal with equal hash codes")
   void compositesWithSameFramesAreEqual() {
      CompositeColorFrame c1 = new CompositeColorFrame();
      c1.addFrame(new GradientColorFrame("v"));
      CompositeColorFrame c2 = new CompositeColorFrame();
      c2.addFrame(new GradientColorFrame("v"));
      assertEquals(c1, c2);
      assertEquals(c1.hashCode(), c2.hashCode());
   }

   @Test
   @DisplayName("pinned: equals ignores the sub frames, composites of Gradient(v) and Heat(v) are equal")
   void pinnedCompositeEqualsIgnoresSubFrameKind() {
      CompositeColorFrame c1 = new CompositeColorFrame();
      c1.addFrame(new GradientColorFrame("v"));
      CompositeColorFrame c2 = new CompositeColorFrame();
      c2.addFrame(new HeatColorFrame("v"));
      assertEquals(c1, c2);
      assertEquals(c1.hashCode(), c2.hashCode());
   }

}
