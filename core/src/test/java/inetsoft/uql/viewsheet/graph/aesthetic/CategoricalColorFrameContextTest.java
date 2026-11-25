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
package inetsoft.uql.viewsheet.graph.aesthetic;

import inetsoft.graph.aesthetic.*;
import inetsoft.test.*;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link CategoricalColorFrameContext} is a ThreadLocal that passes shared frames and the
 * viewsheet color mappings from the graph generator to the frame visitor. Frames are shared
 * by column, plus the date level for a date dimension; shape frames also by palette
 * (Bug 78462), and a chart's last shape frame can be looked up by chart name for a data tip.
 * The tests run on the context of the test thread, whose state they reset first.
 *
 * Callers: GraphGenerator and VSFrameVisitor (store and look up), VSChartDndService and
 * VSChartDrillHandler (hand in the viewsheet's map through setSharedFrames).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CategoricalColorFrameContextTest {
   private CategoricalColorFrameContext context;
   private CategoricalColorFrameContext.Scope scope;

   @BeforeEach
   void setUp() {
      // the context only holds state inside a bound scope (Bug #78989)
      scope = CategoricalColorFrameContext.bind(null);
      context = CategoricalColorFrameContext.getContext();
      context.setSharedFrames(new HashMap<>());
      context.setDimensionColors(new HashMap<>());
   }

   @AfterEach
   void tearDown() {
      scope.close();
   }

   private static VSChartDimensionRef dateDim(String column, int level) {
      ColumnRef col = new ColumnRef(new AttributeRef(null, column));
      col.setDataType(XSchema.DATE);
      VSChartDimensionRef ref = new VSChartDimensionRef(col);
      ref.setDateLevel(level);
      return ref;
   }

   private static VSChartDimensionRef stringDim(String column, int level) {
      ColumnRef col = new ColumnRef(new AttributeRef(null, column));
      col.setDataType(XSchema.STRING);
      VSChartDimensionRef ref = new VSChartDimensionRef(col);
      ref.setDateLevel(level);
      return ref;
   }

   private static CategoricalShapeFrame shapes(GShape... palette) {
      CategoricalShapeFrame frame = new CategoricalShapeFrame();

      for(int i = 0; i < palette.length; i++) {
         frame.setShape(i, palette[i]);
      }

      return frame;
   }

   @Test
   void contextIsKeptPerThread() throws Exception {
      AtomicReference<CategoricalColorFrameContext> other = new AtomicReference<>();
      Thread t = new Thread(() -> other.set(CategoricalColorFrameContext.getContext()));
      t.start();
      t.join();

      assertSame(context, CategoricalColorFrameContext.getContext());
      assertNotNull(other.get());
      assertNotSame(context, other.get());
   }

   @Test
   void aSharedFrameIsFoundByItsColumn() {
      CategoricalColorFrame frame = new CategoricalColorFrame();
      VSChartDimensionRef ref = stringDim("Region", 0);

      context.addSharedFrame("Region", frame, ref);

      assertSame(frame, context.getSharedFrame("Region", ref));
      assertNull(context.getSharedFrame("State", ref));
   }

   @Test
   void aLaterFrameForTheSameColumnReplacesTheEarlierOne() {
      CategoricalColorFrame first = new CategoricalColorFrame();
      CategoricalColorFrame second = new CategoricalColorFrame();
      VSChartDimensionRef ref = stringDim("Region", 0);

      context.addSharedFrame("Region", first, ref);
      context.addSharedFrame("Region", second, ref);

      assertSame(second, context.getSharedFrame("Region", ref));
   }

   @Test
   void aDateDimensionIsSharedPerDateLevel() {
      CategoricalColorFrame byYear = new CategoricalColorFrame();
      CategoricalColorFrame byMonth = new CategoricalColorFrame();

      context.addSharedFrame("Date", byYear, dateDim("Date", XConstants.YEAR_DATE_GROUP));
      context.addSharedFrame("Date", byMonth, dateDim("Date", XConstants.MONTH_DATE_GROUP));

      assertSame(byYear, context.getSharedFrame("Date", dateDim("Date", XConstants.YEAR_DATE_GROUP)));
      assertSame(byMonth, context.getSharedFrame("Date", dateDim("Date", XConstants.MONTH_DATE_GROUP)));
      assertNull(context.getSharedFrame("Date", dateDim("Date", XConstants.QUARTER_DATE_GROUP)));
   }

   @Test
   void aNonDateDimensionIgnoresItsDateLevel() {
      CategoricalColorFrame frame = new CategoricalColorFrame();

      context.addSharedFrame("Region", frame, stringDim("Region", XConstants.YEAR_DATE_GROUP));

      assertSame(frame,
                 context.getSharedFrame("Region", stringDim("Region", XConstants.MONTH_DATE_GROUP)));
   }

   @Test
   void aRefThatIsNotADimensionIsSharedByColumnOnly() {
      CategoricalColorFrame frame = new CategoricalColorFrame();
      VSChartAggregateRef measure = new VSChartAggregateRef();

      context.addSharedFrame("Sales", frame, measure);

      assertSame(frame, context.getSharedFrame("Sales", measure));
      assertSame(frame, context.getSharedFrame("Sales", null));
   }

   @Test
   void theViewsheetMapIsUsedAfterSetSharedFrames() {
      Map<SharedFrameParameters, VisualFrame> viewsheetFrames = new HashMap<>();
      CategoricalColorFrame old = new CategoricalColorFrame();
      VSChartDimensionRef ref = stringDim("Region", 0);
      context.addSharedFrame("Region", old, ref);

      context.setSharedFrames(viewsheetFrames);

      assertNull(context.getSharedFrame("Region", ref), "frames of the previous map are gone");

      CategoricalColorFrame frame = new CategoricalColorFrame();
      context.addSharedFrame("Region", frame, ref);
      assertEquals(1, viewsheetFrames.size(), "the frame is stored in the map that was handed in");
      assertTrue(viewsheetFrames.containsValue(frame));
   }

   @Test
   void dimensionColorsAreReadOnlyAndReplacedAsAWhole() {
      Map<String, Color> colors = new HashMap<>();
      colors.put("East", Color.RED);
      context.setDimensionColors(colors);

      assertEquals(Color.RED, context.getDimensionColors().get("East"));
      assertThrows(UnsupportedOperationException.class,
                   () -> context.getDimensionColors().put("West", Color.BLUE));

      Map<String, Color> next = new HashMap<>();
      next.put("West", Color.BLUE);
      context.setDimensionColors(next);

      assertNull(context.getDimensionColors().get("East"));
      assertEquals(Color.BLUE, context.getDimensionColors().get("West"));
   }

   // ---- shape frames (Bug 78462) ----

   @Test
   void aShapeFrameIsSharedOnlyWithTheSamePalette() {
      VSChartDimensionRef ref = stringDim("Region", 0);
      CategoricalShapeFrame circles = shapes(GShape.CIRCLE, GShape.SQUARE);
      context.addSharedShapeFrame("Region", circles, ref, "Chart1");

      assertSame(circles,
                 context.getSharedShapeFrame("Region", ref, shapes(GShape.CIRCLE, GShape.SQUARE)));
      assertNull(context.getSharedShapeFrame("Region", ref, shapes(GShape.STAR, GShape.CROSS)),
                 "another palette must not borrow this chart's shapes");
      assertNull(context.getSharedShapeFrame("Region", ref, shapes(GShape.CIRCLE)),
                 "a palette with fewer shapes is another palette");
   }

   @Test
   void anOutlineAndAFilledShapeAreDifferentPalettes() {
      VSChartDimensionRef ref = stringDim("Region", 0);
      context.addSharedShapeFrame("Region", shapes(GShape.CIRCLE), ref, null);

      assertNull(context.getSharedShapeFrame("Region", ref, shapes(GShape.FILLED_CIRCLE)));
      assertNotNull(context.getSharedShapeFrame("Region", ref, shapes(GShape.CIRCLE)));
   }

   @Test
   void aShapeFrameIsAlsoFoundByTheChartThatSharedIt() {
      VSChartDimensionRef ref = stringDim("Region", 0);
      CategoricalShapeFrame frame = shapes(GShape.CIRCLE, GShape.SQUARE);
      context.addSharedShapeFrame("Region", frame, ref, "Chart1");

      assertSame(frame, context.getChartSharedShapeFrame("Region", ref, "Chart1"));
      assertNull(context.getChartSharedShapeFrame("Region", ref, "Chart2"));
   }

   @Test
   void withoutAChartNameThereIsNoChartLookup() {
      Map<SharedFrameParameters, VisualFrame> frames = new HashMap<>();
      context.setSharedFrames(frames);
      VSChartDimensionRef ref = stringDim("Region", 0);
      CategoricalShapeFrame frame = shapes(GShape.CIRCLE);

      context.addSharedShapeFrame("Region", frame, ref, null);

      assertSame(frame, context.getSharedShapeFrame("Region", ref, shapes(GShape.CIRCLE)));
      assertNull(context.getChartSharedShapeFrame("Region", ref, null));
      assertEquals(1, frames.size(), "only the palette key is stored");
   }

   @Test
   void withAChartNameBothKeysAreStored() {
      Map<SharedFrameParameters, VisualFrame> frames = new HashMap<>();
      context.setSharedFrames(frames);

      context.addSharedShapeFrame("Region", shapes(GShape.CIRCLE), stringDim("Region", 0), "Chart1");

      assertEquals(2, frames.size(), "one key by palette and one by chart");
   }

   @Test
   void theChartLookupReturnsTheLastFrameTheChartSharedWhateverItsPalette() {
      VSChartDimensionRef ref = stringDim("Region", 0);
      CategoricalShapeFrame first = shapes(GShape.CIRCLE);
      CategoricalShapeFrame second = shapes(GShape.STAR, GShape.CROSS);

      context.addSharedShapeFrame("Region", first, ref, "Chart1");
      context.addSharedShapeFrame("Region", second, ref, "Chart1");

      assertSame(second, context.getChartSharedShapeFrame("Region", ref, "Chart1"));
      assertSame(first, context.getSharedShapeFrame("Region", ref, shapes(GShape.CIRCLE)));
   }

   @Test
   void shapeFramesDoNotMixWithColorFramesOfTheSameColumn() {
      VSChartDimensionRef ref = stringDim("Region", 0);
      CategoricalColorFrame color = new CategoricalColorFrame();
      CategoricalShapeFrame shape = shapes(GShape.CIRCLE);

      context.addSharedFrame("Region", color, ref);
      context.addSharedShapeFrame("Region", shape, ref, "Chart1");

      assertSame(color, context.getSharedFrame("Region", ref));
      assertSame(shape, context.getSharedShapeFrame("Region", ref, shapes(GShape.CIRCLE)));
   }

   @Test
   void aDateShapeDimensionIsSharedPerDateLevel() {
      CategoricalShapeFrame byYear = shapes(GShape.CIRCLE);
      context.addSharedShapeFrame("Date", byYear, dateDim("Date", XConstants.YEAR_DATE_GROUP), null);

      assertSame(byYear, context.getSharedShapeFrame(
         "Date", dateDim("Date", XConstants.YEAR_DATE_GROUP), shapes(GShape.CIRCLE)));
      assertNull(context.getSharedShapeFrame(
         "Date", dateDim("Date", XConstants.MONTH_DATE_GROUP), shapes(GShape.CIRCLE)));
   }
}
