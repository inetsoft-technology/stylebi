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
package inetsoft.report.composition.graph;

import inetsoft.graph.aesthetic.*;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.test.*;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.graph.aesthetic.CategoricalColorFrameContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.text.Format;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The static helpers of {@link VSFrameVisitor} that make a categorical visual frame show the
 * same assignment as the full data set: {@code syncCategoricalFrame} (color, shape and size
 * frames), the color collision rule (Bug 61424), the viewsheet-wide dimension colors taken
 * from {@link CategoricalColorFrameContext}, and {@code getDefaultFormat} /
 * {@code getVisualDataSet}.
 *
 * Callers: GraphGenerator.createFrame / GraphUtil (sync before render), VSFrameVisitor.createFrame
 * itself, and the shared-frame store, which keeps the synced frame for other charts.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSFrameVisitorTest {
   private static final String FIELD = "Region";

   private CategoricalColorFrameContext.Scope scope;

   @BeforeEach
   void setUp() {
      // the context only holds state inside a bound scope (Bug #78989)
      scope = CategoricalColorFrameContext.bind(null);
      CategoricalColorFrameContext context = CategoricalColorFrameContext.getContext();
      context.setDimensionColors(new HashMap<>());
      context.setSharedFrames(new HashMap<>());
   }

   @AfterEach
   void tearDown() {
      scope.close();
   }

   private static DataSet data(String... regions) {
      Object[][] rows = new Object[regions.length + 1][];
      rows[0] = new Object[] { FIELD, "Sales" };

      for(int i = 0; i < regions.length; i++) {
         rows[i + 1] = new Object[] { regions[i], i + 1 };
      }

      return new DefaultDataSet(rows);
   }

   private static CategoricalColorFrame colorFrame() {
      return new CategoricalColorFrame(FIELD);
   }

   private static void sync(VisualFrame frame, DataSet data) {
      VSFrameVisitor.syncCategoricalFrame(frame, data, null, null, false);
   }

   // ---- color frame ----

   /** Values are sorted before they are assigned, so the data order does not matter. */
   @Test
   void colorAssignmentDoesNotDependOnTheOrderOfTheData() {
      CategoricalColorFrame a = colorFrame();
      CategoricalColorFrame b = colorFrame();

      sync(a, data("West", "East", "North"));
      sync(b, data("East", "North", "West"));

      for(String region : new String[] { "East", "North", "West" }) {
         assertEquals(a.getColor(region), b.getColor(region), region);
      }
   }

   @Test
   void everyValueGetsADifferentColorWhileThePaletteLasts() {
      CategoricalColorFrame frame = colorFrame();

      sync(frame, data("East", "North", "West", "South"));

      assertEquals(4, java.util.stream.Stream.of("East", "North", "West", "South")
         .map(frame::getColor).distinct().count());
   }

   /** Two static colors that are the same: the second value loses its color (Bug 61424). */
   @Test
   void aDuplicateStaticColorIsClearedFromTheLaterValue() {
      CategoricalColorFrame frame = colorFrame();
      frame.setColor("East", Color.RED);
      frame.setColor("West", Color.RED);

      sync(frame, data("East", "North", "West"));

      assertEquals(Color.RED, frame.getColor("East"));
      assertNotEquals(Color.RED, frame.getColor("West"));
   }

   @Test
   void distinctStaticColorsAreKept() {
      CategoricalColorFrame frame = colorFrame();
      frame.setColor("East", Color.RED);
      frame.setColor("West", Color.BLUE);

      sync(frame, data("East", "North", "West"));

      assertEquals(Color.RED, frame.getColor("East"));
      assertEquals(Color.BLUE, frame.getColor("West"));
   }

   /** The colors the viewsheet fixed for a column are applied when the frame uses them. */
   @Test
   void viewsheetDimensionColorsAreAppliedWhenTheFrameUsesGlobalColors() {
      Map<String, Color> fixed = new HashMap<>();
      fixed.put("East", Color.MAGENTA);
      CategoricalColorFrameContext.getContext().setDimensionColors(fixed);
      CategoricalColorFrame frame = colorFrame();
      frame.setUseGlobal(true);

      sync(frame, data("East", "North", "West"));

      assertEquals(Color.MAGENTA, frame.getColor("East"));
   }

   @Test
   void viewsheetDimensionColorsAreIgnoredWhenTheFrameDoesNotUseThem() {
      Map<String, Color> fixed = new HashMap<>();
      fixed.put("East", Color.MAGENTA);
      CategoricalColorFrameContext.getContext().setDimensionColors(fixed);
      CategoricalColorFrame frame = colorFrame();
      frame.setUseGlobal(false);

      sync(frame, data("East", "North", "West"));

      assertNotEquals(Color.MAGENTA, frame.getColor("East"));
   }

   /** A value with a viewsheet-wide color keeps it even if another value has the same color. */
   @Test
   void aViewsheetColorIsNotClearedByTheCollisionRule() {
      Map<String, Color> fixed = new HashMap<>();
      fixed.put("West", Color.RED);
      CategoricalColorFrameContext.getContext().setDimensionColors(fixed);
      CategoricalColorFrame frame = colorFrame();
      frame.setUseGlobal(true);
      frame.setColor("East", Color.RED);

      sync(frame, data("East", "North", "West"));

      assertEquals(Color.RED, frame.getColor("East"));
      assertEquals(Color.RED, frame.getColor("West"));
   }

   @Test
   void aFrameWithoutAFieldIsLeftAlone() {
      CategoricalColorFrame frame = new CategoricalColorFrame();
      frame.setColor("East", Color.RED);

      sync(frame, data("East", "West"));

      assertEquals(Color.RED, frame.getColor("East"));
      assertNull(frame.getField());
      assertNull(frame.getScale(), "a frame without a field is not initialised from the data");
   }

   /** A frame that does not use the viewsheet colors gets no exemption from the collision rule. */
   @Test
   void theCollisionRuleStillAppliesToAFrameThatDoesNotUseViewsheetColors() {
      Map<String, Color> fixed = new HashMap<>();
      fixed.put("West", Color.RED);
      CategoricalColorFrameContext.getContext().setDimensionColors(fixed);
      CategoricalColorFrame frame = colorFrame();
      frame.setUseGlobal(false);
      frame.setColor("East", Color.RED);
      frame.setColor("West", Color.RED);

      sync(frame, data("East", "North", "West"));

      assertEquals(Color.RED, frame.getColor("East"));
      assertNotEquals(Color.RED, frame.getColor("West"));
   }

   /** User-set colors on the frame itself are not wiped by the sync; only the saved clone is. */
   @Test
   void syncKeepsTheUserColorsOfTheFrameItself() {
      CategoricalColorFrame frame = colorFrame();
      frame.setColor(0, Color.ORANGE);

      sync(frame, data("East", "West"));

      assertEquals(Color.ORANGE, frame.getUserColor(0));
   }

   // ---- other categorical frames ----

   @Test
   void shapesAreAssignedInSortedValueOrder() {
      CategoricalShapeFrame a = new CategoricalShapeFrame(FIELD);
      CategoricalShapeFrame b = new CategoricalShapeFrame(FIELD);

      sync(a, data("West", "East", "North"));
      sync(b, data("East", "North", "West"));

      for(String region : new String[] { "East", "North", "West" }) {
         assertNotNull(a.getShape(region), region);
         assertEquals(a.getShape(region).getLegendId(), b.getShape(region).getLegendId(), region);
      }
   }

   // ---- helpers ----

   @Test
   void defaultFormatOfADateDimensionDependsOnItsLevel() {
      ColumnRef col = new ColumnRef(new AttributeRef(null, "Date"));
      col.setDataType(XSchema.DATE);
      VSChartDimensionRef byMonth = new VSChartDimensionRef(col);
      byMonth.setDateLevel(XConstants.MONTH_DATE_GROUP);
      VSChartDimensionRef byYear = new VSChartDimensionRef(col);
      byYear.setDateLevel(XConstants.YEAR_DATE_GROUP);

      Format month = VSFrameVisitor.getDefaultFormat(byMonth);
      Format year = VSFrameVisitor.getDefaultFormat(byYear);

      assertNotNull(month);
      assertNotNull(year);
      assertNotEquals(month, year, "a month and a year are not formatted the same way");
   }

   @Test
   void defaultFormatOfAStringDimensionIsNullEvenWithADateLevel() {
      ColumnRef col = new ColumnRef(new AttributeRef(null, "Region"));
      col.setDataType(XSchema.STRING);
      VSChartDimensionRef ref = new VSChartDimensionRef(col);
      ref.setDateLevel(XConstants.MONTH_DATE_GROUP);

      assertNull(VSFrameVisitor.getDefaultFormat(ref));
   }

   @Test
   void visualDataSetOfAPlainDataSetIsTheDataSetItself() {
      DataSet data = data("East", "West");

      assertSame(data, VSFrameVisitor.getVisualDataSet(FIELD, data));
      assertSame(data, VSFrameVisitor.getVisualDataSet(null, data));
   }
}
