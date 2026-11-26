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
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.scale.Scale;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The stacked measures frames (shape, texture, line, size, text): which frame answers for a
 * measure, the default frame, and the legend rules shared through StackedMeasuresFrame.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class StackedMeasuresFramesTest {
   private static final double EPS = 1e-9;

   private enum Kind { SHAPE, TEXTURE, LINE, SIZE, TEXT }

   /** A stacked frame of one kind: m1 and m2 frames map value A to their own marker. */
   private record Scenario(Function<String, Object> stacked, Object m1, Object m2, Object def) {
   }

   private static DefaultDataSet column(String header, Object... values) {
      Object[][] rows = new Object[values.length + 1][];
      rows[0] = new Object[] { header };

      for(int i = 0; i < values.length; i++) {
         rows[i + 1] = new Object[] { values[i] };
      }

      return new DefaultDataSet(rows);
   }

   private static Scenario scenario(Kind kind, boolean withDefault) {
      DefaultDataSet data = column("g", "A", "B");

      switch(kind) {
      case SHAPE: {
         CategoricalShapeFrame f1 = new CategoricalShapeFrame("g");
         CategoricalShapeFrame f2 = new CategoricalShapeFrame("g");
         CategoricalShapeFrame d = new CategoricalShapeFrame("g");
         f1.setShape((Object) "A", GShape.STAR);
         f2.setShape((Object) "A", GShape.SQUARE);
         d.setShape((Object) "A", GShape.CROSS);
         StackedMeasuresShapeFrame s = new StackedMeasuresShapeFrame(withDefault ? d : null);
         s.setFrame("m1", f1);
         s.setFrame("m2", f2);
         return new Scenario(col -> s.getShape(data, col, 0), GShape.STAR, GShape.SQUARE,
                             GShape.CROSS);
      }
      case TEXTURE: {
         CategoricalTextureFrame f1 = new CategoricalTextureFrame("g");
         CategoricalTextureFrame f2 = new CategoricalTextureFrame("g");
         CategoricalTextureFrame d = new CategoricalTextureFrame("g");
         f1.setTexture((Object) "A", GTexture.PATTERN_1);
         f2.setTexture((Object) "A", GTexture.PATTERN_2);
         d.setTexture((Object) "A", GTexture.PATTERN_3);
         StackedMeasuresTextureFrame s = new StackedMeasuresTextureFrame(withDefault ? d : null);
         s.setFrame("m1", f1);
         s.setFrame("m2", f2);
         return new Scenario(col -> s.getTexture(data, col, 0), GTexture.PATTERN_1,
                             GTexture.PATTERN_2, GTexture.PATTERN_3);
      }
      case LINE: {
         CategoricalLineFrame f1 = new CategoricalLineFrame("g");
         CategoricalLineFrame f2 = new CategoricalLineFrame("g");
         CategoricalLineFrame d = new CategoricalLineFrame("g");
         f1.setLine((Object) "A", GLine.DOT_LINE);
         f2.setLine((Object) "A", GLine.DASH_LINE);
         d.setLine((Object) "A", GLine.MEDIUM_DASH);
         StackedMeasuresLineFrame s = new StackedMeasuresLineFrame(withDefault ? d : null);
         s.setFrame("m1", f1);
         s.setFrame("m2", f2);
         return new Scenario(col -> s.getLine(data, col, 0), GLine.DOT_LINE, GLine.DASH_LINE,
                             GLine.MEDIUM_DASH);
      }
      case SIZE: {
         CategoricalSizeFrame f1 = new CategoricalSizeFrame("g");
         CategoricalSizeFrame f2 = new CategoricalSizeFrame("g");
         CategoricalSizeFrame d = new CategoricalSizeFrame("g");
         f1.setSize("A", 7.0);
         f2.setSize("A", 8.0);
         d.setSize("A", 9.0);
         StackedMeasuresSizeFrame s = new StackedMeasuresSizeFrame(withDefault ? d : null);
         s.setFrame("m1", f1);
         s.setFrame("m2", f2);
         return new Scenario(col -> s.getSize(data, col, 0), 7.0, 8.0, 9.0);
      }
      default: {
         DefaultTextFrame f1 = new DefaultTextFrame("g");
         DefaultTextFrame f2 = new DefaultTextFrame("g");
         DefaultTextFrame d = new DefaultTextFrame("g");
         f1.setText("A", "t1");
         f2.setText("A", "t2");
         d.setText("A", "t3");
         StackedMeasuresTextFrame s = new StackedMeasuresTextFrame(withDefault ? d : null);
         s.setFrame("m1", f1);
         s.setFrame("m2", f2);
         return new Scenario(col -> s.getText(data, col, 0), "t1", "t2", "t3");
      }
      }
   }

   private static CategoricalShapeFrame shapeOn(String field, Object... values) {
      CategoricalShapeFrame frame = new CategoricalShapeFrame(field);
      frame.init(column(field, values));
      return frame;
   }

   private static Set<VisualFrame> identitySet(VisualFrame... frames) {
      Set<VisualFrame> set = Collections.newSetFromMap(new IdentityHashMap<>());
      set.addAll(Arrays.asList(frames));
      return set;
   }

   // ---- routing by measure ----

   @ParameterizedTest
   @EnumSource(Kind.class)
   void eachMeasureAnswersWithItsOwnFrame(Kind kind) {
      Scenario s = scenario(kind, true);

      assertEquals(s.m1(), s.stacked().apply("m1"));
      assertEquals(s.m2(), s.stacked().apply("m2"));
      assertNotEquals(s.stacked().apply("m1"), s.stacked().apply("m2"));
   }

   @ParameterizedTest
   @EnumSource(Kind.class)
   void unmappedMeasureUsesTheDefaultFrame(Kind kind) {
      Scenario s = scenario(kind, true);

      assertEquals(s.def(), s.stacked().apply("m3"));
   }

   @ParameterizedTest
   @EnumSource(value = Kind.class, names = { "SHAPE", "TEXTURE", "LINE", "TEXT" })
   void unmappedMeasureWithoutDefaultFrameIsNull(Kind kind) {
      Scenario s = scenario(kind, false);

      assertNull(s.stacked().apply("m3"));
      assertEquals(s.m1(), s.stacked().apply("m1"));
   }

   @Test
   void sizeFrameAnswersPerMeasureAndFromTheDefaultForOthers() {
      DefaultDataSet data = column("g", "A", "B");
      CategoricalSizeFrame m1 = new CategoricalSizeFrame("g");
      CategoricalSizeFrame m2 = new CategoricalSizeFrame("g");
      CategoricalSizeFrame def = new CategoricalSizeFrame("g");
      m2.setSmallest(10);
      m2.setLargest(20);
      def.setSmallest(5);
      def.setLargest(6);
      m1.init(data);
      m2.init(data);
      def.init(data);
      StackedMeasuresSizeFrame stacked = new StackedMeasuresSizeFrame(def);
      stacked.setFrame("m1", m1);
      stacked.setFrame("m2", m2);

      assertEquals(30, stacked.getSize(data, "m1", 1), EPS);
      assertEquals(20, stacked.getSize(data, "m2", 1), EPS);
      assertEquals(10, stacked.getSize(data, "m2", 0), EPS);
      assertEquals(6, stacked.getSize(data, "m3", 1), EPS);
   }

   @Test
   void valueLookupFindsTheFrameThatOwnsTheValue() {
      CategoricalShapeFrame m1 = shapeOn("g", "A", "B");
      CategoricalShapeFrame m2 = shapeOn("g", "C");
      m2.setShape((Object) "Z", GShape.CROSS);
      CategoricalShapeFrame def = new CategoricalShapeFrame("g");
      def.setDefaultShape(GShape.DIAMOND);
      StackedMeasuresShapeFrame stacked = new StackedMeasuresShapeFrame(def);
      stacked.setFrame("m1", m1);
      stacked.setFrame("m2", m2);

      assertEquals(GShape.CROSS, stacked.getShape("Z"));
      assertEquals(GShape.DIAMOND, stacked.getShape("unknown"));

      StackedMeasuresShapeFrame noDefault = new StackedMeasuresShapeFrame(null);
      noDefault.setFrame("m1", m1);
      noDefault.setFrame("m2", m2);

      assertEquals(GShape.CROSS, noDefault.getShape("Z"));
      assertNull(noDefault.getShape("unknown"));
   }

   @Test
   void sizeValueLookupFindsTheFrameThatOwnsTheValue() {
      CategoricalSizeFrame m1 = new CategoricalSizeFrame("g");
      m1.init(column("g", "A", "B"));
      CategoricalSizeFrame m2 = new CategoricalSizeFrame("g");
      m2.setLargest(12);
      m2.init(column("g", "C"));
      StackedMeasuresSizeFrame stacked = new StackedMeasuresSizeFrame(null);
      stacked.setFrame("m1", m1);
      stacked.setFrame("m2", m2);

      assertEquals(12, stacked.getSize("C"), EPS);
      assertEquals(30, stacked.getSize("B"), EPS);
   }

   @Test
   void setFrameReplacesTheFrameOfAMeasure() {
      CategoricalShapeFrame first = shapeOn("g", "A");
      CategoricalShapeFrame second = shapeOn("h", "B");
      StackedMeasuresShapeFrame stacked = new StackedMeasuresShapeFrame(null);
      stacked.setFrame("m1", first);
      stacked.setFrame("m1", second);

      assertEquals(1, stacked.getFrames().size());
      assertSame(second, stacked.getFrames().iterator().next());
   }

   @Test
   void fieldsAreTheDistinctNonNullFieldsOfTheFramesAndCannotBeSet() {
      StackedMeasuresShapeFrame stacked = new StackedMeasuresShapeFrame(null);
      stacked.setFrame("m1", shapeOn("g", "A"));
      stacked.setFrame("m2", shapeOn("h", "B"));
      stacked.setFrame("m3", new CategoricalShapeFrame());
      stacked.setFrame("m4", shapeOn("g", "C"));

      assertEquals(Set.of("g", "h"), new HashSet<>(Arrays.asList(stacked.getFields())));
      assertEquals(2, stacked.getFields().length);

      stacked.setFields("x");

      assertEquals(Set.of("g", "h"), new HashSet<>(Arrays.asList(stacked.getFields())));
   }

   @Test
   void scaleOptionReachesTheMeasureFrames() {
      CategoricalShapeFrame m1 = shapeOn("g", "A");
      CategoricalShapeFrame m2 = shapeOn("h", "B");
      StackedMeasuresShapeFrame stacked = new StackedMeasuresShapeFrame(new CategoricalShapeFrame());
      stacked.setFrame("m1", m1);
      stacked.setFrame("m2", m2);
      stacked.setScaleOption(Scale.ZERO);

      assertEquals(Scale.ZERO, m1.getScaleOption());
      assertEquals(Scale.ZERO, m2.getScaleOption());
   }

   @Test
   void initCreatesTheScalesOfTheMeasureFrames() {
      DefaultDataSet data = new DefaultDataSet(new Object[][] {
         { "g", "h" }, { "A", "X" }, { "B", "Y" }
      });
      CategoricalShapeFrame m1 = new CategoricalShapeFrame("g");
      CategoricalShapeFrame m2 = new CategoricalShapeFrame("h");
      StackedMeasuresShapeFrame stacked = new StackedMeasuresShapeFrame(null);
      stacked.setFrame("m1", m1);
      stacked.setFrame("m2", m2);

      assertNull(m1.getScale());

      stacked.init(data);

      assertArrayEquals(new Object[] { "A", "B" }, m1.getScale().getValues());
      assertArrayEquals(new Object[] { "X", "Y" }, m2.getScale().getValues());
   }

   // ---- legend rules ----

   @Test
   void valuesAreTheDistinctValuesOfTheFramesOrTheDefaultFrame() {
      StackedMeasuresShapeFrame stacked = new StackedMeasuresShapeFrame(shapeOn("d", "P", "Q"));

      assertArrayEquals(new Object[] { "P", "Q" }, stacked.getValues());

      stacked.setFrame("m1", shapeOn("g", "A", "B"));
      stacked.setFrame("m2", shapeOn("h", "B", "C"));

      Object[] values = stacked.getValues();

      assertEquals(3, values.length);
      assertEquals(Set.of("A", "B", "C"), new HashSet<>(Arrays.asList(values)));
   }

   @Test
   void titleIsTheLegendTitleThenTheJoinedFieldsThenTheDefaultFrameTitle() {
      StackedMeasuresShapeFrame stacked = new StackedMeasuresShapeFrame(shapeOn("d", "P"));

      assertEquals("d", stacked.getTitle());

      stacked.setFrame("m1", shapeOn("g", "A"));
      stacked.setFrame("m2", shapeOn("h", "B"));

      assertEquals(Set.of("g", "h"), new HashSet<>(Arrays.asList(stacked.getTitle().split("/"))));

      LegendSpec spec = new LegendSpec();
      spec.setTitle("Sales");
      stacked.setLegendSpec(spec);

      assertEquals("Sales", stacked.getTitle());
   }

   @ParameterizedTest
   @EnumSource(value = Kind.class, names = { "SHAPE", "TEXTURE", "LINE", "SIZE" })
   void shareIdIgnoresTheLegendOnlyWithoutADefaultFrame(Kind kind) {
      String withoutDefault = shareId(kind, false);
      String withDefault = shareId(kind, true);

      assertEquals("stacked-measure", withoutDefault);
      assertNotEquals("stacked-measure", withDefault);
      assertTrue(withDefault.contains("A") && withDefault.contains("B"), withDefault);
   }

   private static String shareId(Kind kind, boolean withDefault) {
      DefaultDataSet data = column("g", "A", "B");

      switch(kind) {
      case SHAPE: {
         CategoricalShapeFrame d = new CategoricalShapeFrame("g");
         d.init(data);
         return new StackedMeasuresShapeFrame(withDefault ? d : null).getShareId();
      }
      case TEXTURE: {
         CategoricalTextureFrame d = new CategoricalTextureFrame("g");
         d.init(data);
         return new StackedMeasuresTextureFrame(withDefault ? d : null).getShareId();
      }
      case LINE: {
         CategoricalLineFrame d = new CategoricalLineFrame("g");
         d.init(data);
         return new StackedMeasuresLineFrame(withDefault ? d : null).getShareId();
      }
      default: {
         CategoricalSizeFrame d = new CategoricalSizeFrame("g");
         d.init(data);
         return new StackedMeasuresSizeFrame(withDefault ? d : null).getShareId();
      }
      }
   }

   @Test
   void legendFramesCombineFramesOfOneCategoricalField() {
      StackedMeasuresShapeFrame stacked = new StackedMeasuresShapeFrame(null);
      stacked.setFrame("m1", shapeOn("g", "A", "B"));
      stacked.setFrame("m2", shapeOn("g", "A", "B"));

      VisualFrame[] legends = stacked.getLegendFrames();

      assertEquals(1, legends.length);
      assertSame(stacked, legends[0]);
   }

   @Test
   void legendFramesAreTheFramesThemselvesForDifferentFieldsOrMixedKinds() {
      CategoricalShapeFrame g = shapeOn("g", "A", "B");
      CategoricalShapeFrame h = shapeOn("h", "X", "Y");
      StackedMeasuresShapeFrame different = new StackedMeasuresShapeFrame(null);
      different.setFrame("m1", g);
      different.setFrame("m2", h);

      assertEquals(2, different.getLegendFrames().length);
      assertEquals(identitySet(g, h), identitySet(different.getLegendFrames()));

      OvalShapeFrame linear = new OvalShapeFrame("v");
      StackedMeasuresShapeFrame mixed = new StackedMeasuresShapeFrame(null);
      mixed.setFrame("m1", g);
      mixed.setFrame("m2", linear);

      assertEquals(2, mixed.getLegendFrames().length);
      assertEquals(identitySet(g, linear), identitySet(mixed.getLegendFrames()));
   }

   @Test
   void defaultFieldFollowsTheLegendFrames() {
      StackedMeasuresShapeFrame same = new StackedMeasuresShapeFrame(null);
      same.setFrame("m1", shapeOn("g", "A"));
      same.setFrame("m2", shapeOn("g", "B"));

      assertEquals("g", same.getDefaultField());

      StackedMeasuresShapeFrame different = new StackedMeasuresShapeFrame(null);
      different.setFrame("m1", shapeOn("g", "A"));
      different.setFrame("m2", shapeOn("h", "B"));

      assertNull(different.getDefaultField());
   }

   @Test
   void defaultFieldOfAFrameWithOnlyADefaultFrameIsNullBecauseTheFrameIsItsOwnLegend() {
      StackedMeasuresShapeFrame stacked = new StackedMeasuresShapeFrame(shapeOn("d", "P", "Q"));

      assertNull(stacked.getDefaultField());
   }
   @Test
   void visibilityFollowsTheLegendSpecAndTheDefaultFrame() {
      StackedMeasuresShapeFrame stacked = new StackedMeasuresShapeFrame(null);
      stacked.setFrame("m1", shapeOn("g", "A", "B"));

      assertTrue(stacked.isVisible());

      LegendSpec hidden = new LegendSpec();
      hidden.setVisible(false);
      stacked.setLegendSpec(hidden);

      assertFalse(stacked.isVisible());

      CategoricalShapeFrame invisibleDefault = shapeOn("d", "P", "Q");
      LegendSpec off = new LegendSpec();
      off.setVisible(false);
      invisibleDefault.setLegendSpec(off);

      assertFalse(new StackedMeasuresShapeFrame(invisibleDefault).isVisible());
      assertTrue(new StackedMeasuresShapeFrame(shapeOn("d", "P", "Q")).isVisible());
   }

   // ---- text frame ----

   @Test
   void textFrameAnswersWithTheMappedTextOfTheMeasureFrame() {
      DefaultDataSet data = new DefaultDataSet(new Object[][] {
         { "g", "h" }, { "A", "X" }
      });
      DefaultTextFrame m1 = new DefaultTextFrame("g");
      m1.setText("A", "Alpha");
      DefaultTextFrame m2 = new DefaultTextFrame("h");
      m2.setText("X", "Ex");
      StackedMeasuresTextFrame stacked = new StackedMeasuresTextFrame(new DefaultTextFrame("g"));
      stacked.setFrame("m1", m1);
      stacked.setFrame("m2", m2);

      assertEquals("Alpha", stacked.getText(data, "m1", 0));
      assertEquals("Ex", stacked.getText(data, "m2", 0));
      assertEquals("A", stacked.getText(data, "m3", 0));

      StackedMeasuresTextFrame noDefault = new StackedMeasuresTextFrame(null);
      noDefault.setFrame("m1", m1);

      assertNull(noDefault.getText(data, "m3", 0));
   }
}
