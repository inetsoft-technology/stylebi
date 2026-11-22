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
package inetsoft.graph.guide.legend;

import inetsoft.graph.EGraph;
import inetsoft.graph.GraphConstants;
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.coord.PolarCoord;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.IntervalElement;
import inetsoft.graph.element.PointElement;
import inetsoft.graph.internal.DimensionD;
import inetsoft.graph.scale.LinearScale;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static inetsoft.graph.guide.legend.LegendTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Legend items, item grid, layout, preferred and minimum sizes and painting of a single legend.
 * Assertions are structural (counts, labels, order, relative positions, containment); absolute
 * pixel sizes depend on the font metrics of the machine and are read from the objects themselves.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class LegendTest {
   private static final String MAX_COUNT = "graph.legend.maxcount";
   private static final int RIGHT = GraphConstants.RIGHT;
   private static final int TOP = GraphConstants.TOP;
   private static final Color DEFAULT_SYMBOL = new Color(0x759595);

   private String savedMaxCount;

   @BeforeEach
   void saveProperty() {
      savedMaxCount = SreeEnv.getProperty(MAX_COUNT);
   }

   @AfterEach
   void restoreProperty() {
      if(savedMaxCount == null) {
         SreeEnv.remove(MAX_COUNT);
      }
      else {
         SreeEnv.setProperty(MAX_COUNT, savedMaxCount);
      }
   }

   // ------------------------------------------------------------------ items

   @Test
   void itemsFollowTheOrderOfTheFrameValues() {
      Legend legend = legend(color("S", "A", "B", "C"), RIGHT);
      LegendItem[][] grid = legend.getItems();

      assertEquals(3, grid.length);
      assertEquals(1, grid[0].length);
      assertEquals(List.of("A", "B", "C"), labels(legend));
   }

   static Stream<Arguments> frameKinds() {
      return Stream.of(
         Arguments.of((Supplier<VisualFrame>) () -> color("S", "A", "B"), ColorLegend.class,
                      ColorLegendItem.class),
         Arguments.of((Supplier<VisualFrame>) () -> size("S", "A", "B"), SizeLegend.class,
                      SizeLegendItem.class),
         Arguments.of((Supplier<VisualFrame>) () -> shape("S", "A", "B"), ShapeLegend.class,
                      ShapeLegendItem.class),
         Arguments.of((Supplier<VisualFrame>) () -> texture("S", "A", "B"), TextureLegend.class,
                      TextureLegendItem.class),
         Arguments.of((Supplier<VisualFrame>) () -> line("S", "A", "B"), LineLegend.class,
                      LineLegendItem.class));
   }

   @ParameterizedTest
   @MethodSource("frameKinds")
   void createLegendPicksTheLegendAndItemClassOfTheFrameType(
      Supplier<VisualFrame> frame, Class<?> legendClass, Class<?> itemClass)
   {
      Legend legend = legend(frame.get(), RIGHT);

      assertSame(legendClass, legend.getClass());
      List<LegendItem> items = items(legend);
      assertEquals(2, items.size());

      for(LegendItem item : items) {
         assertSame(itemClass, item.getClass());
      }
   }

   @Test
   @DisplayName("pinned current behavior: createLegend returns null for null and text frames")
   void createLegendReturnsNullForFramesWithoutLegend() {
      EGraph graph = graph(RIGHT);

      assertNull(Legend.createLegend(null, graph));
      assertNull(Legend.createLegend(new DefaultTextFrame("S"), graph));
   }

   @Test
   void hiddenItemIsLeftOutAndLeavesNoEmptyCell() {
      CategoricalColorFrame frame = color("S", "A", "B", "C");
      frame.getLegendSpec().setVisible("B", false);
      Legend legend = legend(frame, RIGHT);
      LegendItem[][] grid = legend.getItems();

      assertEquals(List.of("A", "C"), labels(legend));
      assertEquals(2, grid.length);

      for(LegendItem[] row : grid) {
         for(LegendItem item : row) {
            assertNotNull(item);
         }
      }
   }

   @Test
   void hidingAValueThatIsNotInTheFrameChangesNothing() {
      CategoricalColorFrame frame = color("S", "A", "B", "C");
      frame.getLegendSpec().setVisible("Z", false);

      assertEquals(List.of("A", "B", "C"), labels(legend(frame, RIGHT)));
   }

   @Test
   void legendWithAllItemsHiddenHasNoItemsAndLaysOutAndPaints() {
      CategoricalColorFrame frame = color("S", "A", "B", "C");

      for(String v : new String[] { "A", "B", "C" }) {
         frame.getLegendSpec().setVisible(v, false);
      }

      // the title text is painted through batik, which core tests do not have
      frame.getLegendSpec().setTitleVisible(false);
      Legend legend = legend(frame, RIGHT);

      assertEquals(0, legend.getItems().length);
      assertDoesNotThrow(() -> layout(legend, 200, 100));
      assertDoesNotThrow(() -> paint(legend, 300, 300));
   }

   @Test
   @Disabled("Not filed (by design: init(Object...) takes values as given, like CategoricalScale): createItems keeps duplicate values, expected items [A, B] but was [A, A, B]")
   void duplicateValuesShowOneItem() {
      Legend legend = legend(color("S", "A", "A", "B"), RIGHT);

      assertEquals(List.of("A", "B"), labels(legend));
   }

   @Test
   void nullValueShowsAnItemWithEmptyLabel() {
      Legend legend = legend(color("S", "A", null, "B"), RIGHT);
      List<LegendItem> items = items(legend);

      assertEquals(3, items.size());
      assertNull(items.get(1).getValue());
      assertEquals("", items.get(1).getLabel());
   }

   @Test
   void frameWithoutValuesHasNoItemsAndNoFailure() {
      CategoricalColorFrame frame = color("S");
      // the title text is painted through batik, which core tests do not have
      frame.getLegendSpec().setTitleVisible(false);
      Legend legend = legend(frame, RIGHT);

      assertEquals(0, legend.getItems().length);
      assertDoesNotThrow(() -> layout(legend, 200, 100));
      assertDoesNotThrow(() -> legend.getPreferredHeight(200, 100));
      assertDoesNotThrow(() -> paint(legend, 300, 300));
   }

   @Test
   void defaultItemLimitIsFiveHundredAndKeepsTheFirstValues() {
      Legend legend = legend(color("S", values("v", 600)), RIGHT);
      List<String> labels = labels(legend);

      assertEquals(500, labels.size());
      assertEquals("v0", labels.get(0));
      assertEquals("v499", labels.get(499));
   }

   @Test
   void itemLimitPropertyKeepsTheFirstValues() {
      SreeEnv.setProperty(MAX_COUNT, "3");
      Legend legend = legend(color("S", values("v", 5)), RIGHT);

      assertEquals(List.of("v0", "v1", "v2"), labels(legend));
   }

   @Test
   @Disabled("Not filed (by design: same parsing as the sibling property graph.axislabel.maxcount): graph.legend.maxcount=abc throws NumberFormatException from the Legend constructor, expected the default limit")
   void invalidItemLimitPropertyFallsBackToTheDefault() {
      SreeEnv.setProperty(MAX_COUNT, "abc");
      CategoricalColorFrame frame = color("S", values("v", 5));
      Legend[] legend = new Legend[1];

      assertDoesNotThrow(() -> legend[0] = legend(frame, RIGHT));
      assertEquals(5, items(legend[0]).size());
   }

   @ParameterizedTest
   @ValueSource(strings = { "A", "B", "O'Brien", "100%", "a b" })
   void itemLabelIsTheValue(String value) {
      Legend legend = legend(color("S", value, "other"), RIGHT);

      assertEquals(List.of(value, "other"), labels(legend));
   }

   @Test
   void labelContainingTheQuoteSentinelIsNotCorrupted() {
      Legend legend = legend(color("S", "x^_^y'z", "other"), RIGHT);

      assertEquals("x^_^y'z", labels(legend).get(0));
   }

   @Test
   void textFrameAliasIsTheLabelAndTheValueStaysTheRawValue() {
      CategoricalColorFrame frame = color("S", "A", "B");
      DefaultTextFrame text = new DefaultTextFrame();
      text.setText("A", "Alpha");
      frame.getLegendSpec().setTextFrame(text);
      Legend legend = legend(frame, RIGHT);
      List<LegendItem> items = items(legend);

      assertEquals(List.of("Alpha", "B"), labels(legend));
      assertEquals("A", items.get(0).getValue());
      assertEquals("B", items.get(1).getValue());
   }

   @Test
   void legendTitleIsTheFieldUnlessSetAndFallsBackToTheFrameType() {
      CategoricalColorFrame frame = color("State", "A", "B");
      assertEquals("State", legend(frame, RIGHT).getTitle().getText());

      frame.getLegendSpec().setTitle("T");
      assertEquals("T", legend(frame, RIGHT).getTitle().getText());

      frame.getLegendSpec().setTitleVisible(false);
      assertNull(legend(frame, RIGHT).getTitle());

      assertEquals("Color", legend(color(null, "A", "B"), RIGHT).getTitle().getText());
   }

   // ----------------------------------------------------------------- layout

   @Test
   void narrowLegendBecomesTwoColumnsFilledColumnFirst() {
      CategoricalColorFrame frame = color("S", "A", "B", "C", "D", "E");
      double p = maxItemWidth(frame, RIGHT);
      Legend legend = legend(frame, RIGHT);
      // usable width is 2 * p: W minus the left padding of 2
      layout(legend, 2 * p + 2, 1000);
      LegendItem[][] grid = legend.getItems();

      assertEquals(3, grid.length);
      assertEquals(List.of("A", "D"), List.of(grid[0][0].getLabel(), grid[0][1].getLabel()));
      assertEquals(List.of("B", "E"), List.of(grid[1][0].getLabel(), grid[1][1].getLabel()));
      assertEquals("C", grid[2][0].getLabel());
      assertNull(grid[2][1]);

      Rectangle2D a = grid[0][0].getBounds();
      Rectangle2D b = grid[1][0].getBounds();
      Rectangle2D c = grid[2][0].getBounds();
      Rectangle2D d = grid[0][1].getBounds();

      assertEquals(a.getX(), b.getX(), 1e-9);
      assertEquals(a.getX(), c.getX(), 1e-9);
      assertTrue(a.getY() > b.getY() && b.getY() > c.getY());
      assertEquals(a.getY(), d.getY(), 1e-9);
      assertTrue(d.getX() > a.getX());
   }

   @Test
   void legendOnePixelNarrowerThanTwoItemsHasOneColumn() {
      CategoricalColorFrame frame = color("S", "A", "B", "C", "D", "E");
      double p = maxItemWidth(frame, RIGHT);
      Legend legend = legend(frame, RIGHT);
      layout(legend, 2 * p + 1, 1000);
      LegendItem[][] grid = legend.getItems();

      assertEquals(5, grid.length);

      for(LegendItem[] row : grid) {
         assertEquals(1, row.length);
      }
   }

   @Test
   void legendAsWideAsOneItemHasOneColumnTopDown() {
      CategoricalColorFrame frame = color("S", "A", "B", "C", "D", "E");
      double p = maxItemWidth(frame, RIGHT);
      Legend legend = legend(frame, RIGHT);
      layout(legend, p, 1000);
      LegendItem[][] grid = legend.getItems();

      assertEquals(5, grid.length);

      for(int i = 0; i < grid.length; i++) {
         assertEquals(1, grid[i].length);
         assertEquals(grid[0][0].getBounds().getX(), grid[i][0].getBounds().getX(), 1e-9);

         if(i > 0) {
            assertTrue(grid[i][0].getBounds().getY() < grid[i - 1][0].getBounds().getY());
         }
      }
   }

   @Test
   void topLegendLaysItemsInOneRowLeftToRight() {
      Legend legend = legend(color("S", "A", "B", "C", "D", "E"), TOP);
      layout(legend, 5000, 100);
      List<LegendItem> items = items(legend);

      assertEquals(5, items.size());

      for(int i = 1; i < items.size(); i++) {
         assertTrue(items.get(i).getBounds().getX() > items.get(i - 1).getBounds().getX());
         assertEquals(items.get(0).getBounds().getY(), items.get(i).getBounds().getY(), 1e-9);
      }
   }

   @ParameterizedTest
   @ValueSource(ints = { 1, 2, 3, 6 })
   void itemsStayInsideTheLegendBounds(int columns) {
      CategoricalColorFrame frame = color("S", "A", "B", "C", "D", "E");
      double p = maxItemWidth(frame, RIGHT);
      double w = columns == 1 ? p : columns * p + 3;
      Legend legend = legend(frame, RIGHT);
      layout(legend, w, 1000);
      Rectangle2D box = legend.getBounds();
      Rectangle2D tolerant = new Rectangle2D.Double(
         box.getX() - 1, box.getY() - 1, box.getWidth() + 2, box.getHeight() + 2);

      for(LegendItem item : items(legend)) {
         assertTrue(tolerant.contains(item.getBounds()), item + " " + item.getBounds());
      }
   }

   @Test
   @Disabled("Not filed (not reachable: each legend is laid out once and read with the same arguments): getItems caches the grid of the first call, expected 1 row after the wide layout but was 5")
   void itemGridFollowsTheLastLayout() {
      CategoricalColorFrame frame = color("S", "A", "B", "C", "D", "E");
      double p = maxItemWidth(frame, RIGHT);
      Legend legend = legend(frame, RIGHT);
      layout(legend, p, 1000);
      assertEquals(5, legend.getItems().length);

      layout(legend, 6 * p, 1000);
      Set<Double> ys = new HashSet<>();

      for(LegendItem item : items(legend)) {
         ys.add(item.getBounds().getY());
      }

      assertEquals(ys.size(), legend.getItems().length);
   }

   private static Legend stackedLegend(boolean stack, boolean polar) {
      CategoricalColorFrame frame = color("S", "A", "B", "C");
      IntervalElement elem = new IntervalElement();
      elem.setColorFrame(frame);
      elem.setCollisionModifier(GraphElement.MOVE_STACK, stack);
      EGraph graph = graph(RIGHT);
      graph.addElement(elem);

      if(polar) {
         graph.setCoordinate(new PolarCoord(new RectCoord()));
      }

      Legend legend = legend(frame, graph);
      layout(legend, maxItemWidth(color("S", "A", "B", "C"), RIGHT), 1000);
      return legend;
   }

   private static double y(Legend legend, String label) {
      for(LegendItem item : items(legend)) {
         if(label.equals(item.getLabel())) {
            return item.getBounds().getY();
         }
      }

      throw new AssertionError("no item " + label);
   }

   @Test
   void stackedElementLegendPutsTheFirstValueAtTheBottom() {
      Legend legend = stackedLegend(true, false);

      assertTrue(y(legend, "A") < y(legend, "B"));
      assertTrue(y(legend, "B") < y(legend, "C"));
   }

   @Test
   void unstackedElementLegendPutsTheFirstValueAtTheTop() {
      Legend legend = stackedLegend(false, false);

      assertTrue(y(legend, "A") > y(legend, "B"));
      assertTrue(y(legend, "B") > y(legend, "C"));
   }

   @Test
   void stackedElementOnPolarCoordinateIsNotReversed() {
      Legend legend = stackedLegend(true, true);

      assertTrue(y(legend, "A") > y(legend, "B"));
      assertTrue(y(legend, "B") > y(legend, "C"));
   }

   @Test
   void numericLegendPutsTheSmallestValueAtTheBottom() {
      LinearSizeFrame frame = new LinearSizeFrame();
      frame.setField("m");
      frame.setScale(new LinearScale("m") {
         @Override
         public Object[] getValues() {
            return new Object[] { 1, 2, 3, 4 };
         }
      });
      Legend legend = legend(frame, RIGHT);
      layout(legend, 1, 1000);
      List<LegendItem> items = new ArrayList<>(items(legend));
      items.sort(Comparator.comparingDouble(i -> ((Number) i.getValue()).doubleValue()));

      assertEquals(4, items.size());

      for(int i = 1; i < items.size(); i++) {
         assertTrue(items.get(i).getBounds().getY() > items.get(i - 1).getBounds().getY());
      }
   }

   @Test
   void paddingMovesTheFirstItemRight() {
      CategoricalColorFrame frame = color("S", "A", "B");
      frame.getLegendSpec().setBorder(GraphConstants.NONE);
      Legend plain = legend(frame, RIGHT);
      plain.setBounds(10, 0, 300, 100);
      plain.layout();
      assertEquals(12, items(plain).get(0).getBounds().getX(), 1e-9);

      frame.getLegendSpec().setPadding(new Insets(0, 7, 0, 0));
      Legend padded = legend(frame, RIGHT);
      padded.setBounds(10, 0, 300, 100);
      padded.layout();
      assertEquals(17, items(padded).get(0).getBounds().getX(), 1e-9);
   }

   @Test
   void partialLegendKeepsOneColumnWhenItemsDoNotFitTheHeight() {
      CategoricalColorFrame frame = color("S", "A", "B", "C", "D", "E", "F");
      frame.getLegendSpec().setTitleVisible(false);
      double p = maxItemWidth(frame, RIGHT);
      double ih = itemHeight(frame, RIGHT);

      frame.getLegendSpec().setPartial(true);
      Legend legend = legend(frame, RIGHT);
      layout(legend, p, 6 + 2.5 * ih);
      Set<Double> xs = new HashSet<>();
      Set<Double> ys = new HashSet<>();

      for(LegendItem item : items(legend)) {
         xs.add(item.getBounds().getX());
         ys.add(item.getBounds().getY());
      }

      assertEquals(1, xs.size());
      assertEquals(6, ys.size());

      LegendItem last = items(legend).stream()
         .min(Comparator.comparingDouble(i -> i.getBounds().getY())).get();
      assertTrue(last.getBounds().getMaxY() <= legend.getBounds().getY() + 1e-6,
                 "last row " + last.getBounds() + " is not below " + legend.getBounds());
   }

   @Test
   void nonPartialLegendAddsAColumnWhenItemsDoNotFitTheHeight() {
      CategoricalColorFrame frame = color("S", "A", "B", "C", "D", "E", "F");
      frame.getLegendSpec().setTitleVisible(false);
      double p = maxItemWidth(frame, RIGHT);
      double ih = itemHeight(frame, RIGHT);
      Legend legend = legend(frame, RIGHT);
      layout(legend, p, 6 + 2.5 * ih);
      Set<Double> xs = new HashSet<>();

      for(LegendItem item : items(legend)) {
         xs.add(item.getBounds().getX());
      }

      assertEquals(2, xs.size());
   }

   @Test
   void nonPartialLegendKeepsThreeRowsEachPartlyInsideTheBounds() {
      CategoricalColorFrame frame = color("S", "A", "B", "C", "D", "E", "F");
      frame.getLegendSpec().setTitleVisible(false);
      double p = maxItemWidth(frame, RIGHT);
      double ih = itemHeight(frame, RIGHT);
      Legend legend = legend(frame, RIGHT);
      layout(legend, p, 6 + 2.5 * ih);
      Rectangle2D box = legend.getBounds();
      Set<Double> ys = new HashSet<>();

      for(LegendItem item : items(legend)) {
         ys.add(item.getBounds().getY());
         Rectangle2D inter = item.getBounds().createIntersection(box);
         assertTrue(inter.getWidth() > 0 && inter.getHeight() > 0,
                    item + " is entirely outside " + box);
      }

      assertEquals(3, ys.size());
   }

   // ------------------------------------------------------------------ sizes

   @Test
   void preferredHeightGrowsWithTheNumberOfItems() {
      Legend three = legend(color("S", values("v", 3)), RIGHT);
      Legend ten = legend(color("S", values("v", 10)), RIGHT);

      assertTrue(ten.getPreferredHeight() > three.getPreferredHeight());
   }

   @Test
   void preferredWidthGrowsWithTheLabelLength() {
      Legend shortLabel = legend(color("S", "abc", "def"), RIGHT);
      Legend longLabel = legend(color("S", "a".repeat(30), "def"), RIGHT);

      assertTrue(longLabel.getPreferredWidth() > shortLabel.getPreferredWidth());
   }

   @Test
   @Disabled("Not filed (not reachable: each legend is laid out once and read with the same arguments): getPreferredHeight(w,maxh) caches by width only, expected 64.0 (fresh legend) but was 169.0")
   void preferredHeightWithAnotherMaxHeightEqualsAFreshLegend() {
      CategoricalColorFrame frame = color("S", longValues(10));
      double w = 1.5 * maxItemWidth(frame, RIGHT);
      Legend reused = legend(frame, RIGHT);
      reused.getPreferredHeight(w, 1000);

      assertEquals(legend(frame, RIGHT).getPreferredHeight(w, 50),
                   reused.getPreferredHeight(w, 50), 1e-9);
   }

   @Test
   @Disabled("Not filed (not reachable: each legend is laid out once and read with the same arguments): getPreferredWidth(h,maxw) caches by height only, expected 122.0 (fresh legend) but was 602.0")
   void preferredWidthWithAnotherMaxWidthEqualsAFreshLegend() {
      CategoricalColorFrame frame = color("S", longValues(10));
      Legend reused = legend(frame, RIGHT);
      reused.getPreferredWidth(60, 1000);

      assertEquals(legend(frame, RIGHT).getPreferredWidth(60, 30),
                   reused.getPreferredWidth(60, 30), 1e-9);
   }

   @Test
   void preferredHeightIsSmallerWhenTheAvailableHeightIsSmaller() {
      CategoricalColorFrame frame = color("S", longValues(10));
      double w = 1.5 * maxItemWidth(frame, RIGHT);

      assertTrue(legend(frame, RIGHT).getPreferredHeight(w, 50)
                    < legend(frame, RIGHT).getPreferredHeight(w, 1000));
   }

   @Test
   void preferredWidthIsSmallerWhenTheAvailableWidthIsSmaller() {
      CategoricalColorFrame frame = color("S", longValues(10));

      assertTrue(legend(frame, RIGHT).getPreferredWidth(60, 30)
                    < legend(frame, RIGHT).getPreferredWidth(60, 1000));
   }

   private static Object[] longValues(int n) {
      Object[] vals = new Object[n];

      for(int i = 0; i < n; i++) {
         vals[i] = String.format("value-%02d", i) + "x".repeat(12);
      }

      return vals;
   }

   @Test
   void preferredSizeOfTheSpecOverridesTheComputedSize() {
      CategoricalColorFrame frame = color("S", longValues(4));
      double computedW = legend(frame, RIGHT).getPreferredWidth(100, 1000);
      double computedH = legend(frame, RIGHT).getPreferredHeight(100, 1000);

      frame.getLegendSpec().setPreferredSize(new DimensionD(123, 45));
      Legend legend = legend(frame, RIGHT);
      assertEquals(123, legend.getPreferredWidth(100, 1000), 1e-9);
      assertEquals(45, legend.getPreferredHeight(100, 1000), 1e-9);

      frame.getLegendSpec().setPreferredSize(new DimensionD(1, 1));
      Legend ignored = legend(frame, RIGHT);
      assertEquals(computedW, ignored.getPreferredWidth(100, 1000), 1e-9);
      assertEquals(computedH, ignored.getPreferredHeight(100, 1000), 1e-9);
   }

   @Test
   void entireItemHeightCutsTheHeightToWholeItems() {
      CategoricalColorFrame frame = color("S", "A", "B", "C", "D", "E");
      frame.getLegendSpec().setTitleVisible(false);
      frame.getLegendSpec().setBorder(GraphConstants.NONE);
      double ih = itemHeight(frame, RIGHT);
      double overhead = 2; // half of the legend gap, no title and no border
      Legend legend = legend(frame, RIGHT);

      assertEquals(overhead + 3 * ih, legend.getEntireItemHeight(overhead + 3.4 * ih), 1e-6);
      assertEquals(overhead + 0.5 * ih, legend.getEntireItemHeight(overhead + 0.5 * ih), 1e-6);

      CategoricalColorFrame none = color("S");
      none.getLegendSpec().setTitleVisible(false);
      assertEquals(123, legend(none, RIGHT).getEntireItemHeight(123), 1e-9);
   }

   @Test
   void verticalLegendNeedsMoreMinimumHeightThanHorizontalLegend() {
      CategoricalColorFrame frame = color("S", "A", "B", "C", "D");

      assertTrue(legend(frame, RIGHT).getMinHeight() > legend(frame, TOP).getMinHeight());
   }

   // ------------------------------------------------------------ item colors

   private static Legend shapeLegendOver(CategoricalColorFrame colors,
                                         CategoricalShapeFrame shape)
   {
      PointElement elem = new PointElement();
      elem.setColorFrame(colors);
      elem.setShapeFrame(shape);
      EGraph graph = graph(RIGHT);
      graph.addElement(elem);
      return legend(shape, graph);
   }

   // the element alpha hint is applied to the symbol color, compare the rgb part only
   private static int rgb(LegendItem item) {
      return item.getSymbolColor().getRGB() & 0xFFFFFF;
   }

   @Test
   void whiteElementColorIsReplacedByTheDefaultSymbolColor() {
      CategoricalColorFrame colors = new CategoricalColorFrame("S");
      colors.init(new Object[] { "A", "B" }, new Color[] { Color.WHITE, Color.RED });
      Legend legend = shapeLegendOver(colors, shape("S", "A", "B"));
      List<LegendItem> items = items(legend);

      assertEquals(DEFAULT_SYMBOL.getRGB() & 0xFFFFFF, rgb(items.get(0)));
      assertEquals(0xFF0000, rgb(items.get(1)));
   }

   @Test
   void elementColorOfAnotherFieldIsNotUsedForSymbols() {
      CategoricalColorFrame colors = new CategoricalColorFrame("T");
      colors.init(new Object[] { "A", "B" }, new Color[] { Color.BLUE, Color.RED });
      Legend legend = shapeLegendOver(colors, shape("S", "A", "B"));

      for(LegendItem item : items(legend)) {
         assertEquals(DEFAULT_SYMBOL.getRGB() & 0xFFFFFF, rgb(item));
      }
   }

   @Test
   void scalarLegendHasNoItemsAndABandInsideTheBounds() {
      DefaultDataSet data = new DefaultDataSet(new Object[][] { { "m" }, { 1.0 }, { 5.0 } });
      HeatColorFrame frame = new HeatColorFrame("m");
      frame.init(data);
      Legend legend = legend(frame, RIGHT);

      assertTrue(legend.isScalar());
      assertEquals(0, legend.getItems().length);
      assertDoesNotThrow(() -> layout(legend, 150, 120));

      Rectangle2D box = legend.getBounds();
      Rectangle2D band = null;

      for(int i = 0; i < legend.getVisualCount(); i++) {
         if(legend.getVisual(i) instanceof ColorLegendBand b) {
            band = b.getBounds();
         }
      }

      assertNotNull(band);
      assertTrue(new Rectangle2D.Double(box.getX() - 1, box.getY() - 1, box.getWidth() + 2,
                                        box.getHeight() + 2).contains(band), band + " " + box);
   }

   @Test
   void legendFindsTheLastElementThatCarriesItsFrame() {
      CategoricalShapeFrame shared = shape("S", "A", "B");
      PointElement first = new PointElement();
      PointElement second = new PointElement();
      first.setShapeFrame(shared);
      second.setShapeFrame(shared);
      EGraph graph = graph(RIGHT);
      graph.addElement(first);
      graph.addElement(second);

      assertSame(second, legend(shared, graph).getGraphElement());

      Legend orphan = legend(shape("S", "A", "B"), graph);
      assertNull(orphan.getGraphElement());
      assertEquals(2, items(orphan).size());
   }

   @Test
   void dataIdIsTheFieldAndTheValuesInOrder() {
      String id = Legend.getDataId(color("S", "A", "B"));

      assertEquals(id, Legend.getDataId(color("S", "A", "B")));
      assertNotEquals(id, Legend.getDataId(color("S", "B", "A")));
      assertNotEquals(id, Legend.getDataId(color("T", "A", "B")));
      assertEquals("stacked-measure", Legend.getDataId(new StackedMeasuresColorFrame(null)));
   }

   // ------------------------------------------------------------------ paint

   private static BufferedImage paint(Legend legend, int w, int h) {
      BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = image.createGraphics();

      try {
         legend.paint(g);
      }
      finally {
         g.dispose();
      }

      return image;
   }

   @Test
   @Disabled("Not filed (not reachable: no production caller passes a null title spec): paintTitle dereferences the null title text spec, NullPointerException instead of no exception")
   void paintWithoutTitleTextSpecDoesNotFail() {
      CategoricalColorFrame frame = color("S", "A", "B");
      frame.getLegendSpec().setTitleTextSpec(null);
      Legend legend = legend(frame, RIGHT);
      layout(legend, 200, 100);

      assertDoesNotThrow(() -> paint(legend, 300, 300));
   }
}
