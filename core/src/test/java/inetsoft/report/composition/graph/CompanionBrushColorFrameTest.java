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
package inetsoft.report.composition.graph;

import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.graph.aesthetic.StaticColorFrame;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.report.filter.Highlight;
import inetsoft.report.filter.TextHighlight;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CompanionBrushColorFrameTest {
   private static final Color AZURE = new Color(0x0490FF);
   private static final Color AZURE_SOFT = new Color(0x97BEEB);
   private static final Color CORAL = new Color(0xFF5A35);
   private static final Color CORAL_SOFT = new Color(0xF6B2A2);

   @Test
   void aUniformSelectedLayerKeepsTheBaseColour() {
      CompanionBrushColorFrame frame =
         new CompanionBrushColorFrame(null, true, new StaticColorFrame(AZURE), false);

      assertEquals(AZURE, frame.getColor(data(), "Sales", 0));
   }

   @Test
   void aUniformUnselectedLayerTakesTheCompanion() {
      CompanionBrushColorFrame frame =
         new CompanionBrushColorFrame(null, false, new StaticColorFrame(AZURE), false);

      assertEquals(AZURE_SOFT, frame.getColor(data(), "Sales", 0));
   }

   @Test
   void eachSeriesTakesItsOwnCompanion() {
      assertEquals(AZURE_SOFT,
                   new CompanionBrushColorFrame(null, false, new StaticColorFrame(AZURE), false)
                      .getColor(data(), "Sales", 0));
      assertEquals(CORAL_SOFT,
                   new CompanionBrushColorFrame(null, false, new StaticColorFrame(CORAL), false)
                      .getColor(data(), "Sales", 0));
   }

   @Test
   void darkResolvesAgainstTheDarkCompanionPalette() {
      CompanionBrushColorFrame frame = new CompanionBrushColorFrame(
         null, false, new StaticColorFrame(new Color(0x4FA5FF)), true);

      assertEquals(new Color(0x00569C), frame.getColor(data(), "Sales", 0));
   }

   @Test
   void aNullBaseColourFallsThrough() {
      // null constructor alone still falls back to DEFAULT_COLOR; clear it too for a true null
      StaticColorFrame base = new StaticColorFrame((Color) null);
      base.setDefaultColor(null);
      CompanionBrushColorFrame frame = new CompanionBrushColorFrame(null, false, base, false);

      assertNull(frame.getColor(data(), "Sales", 0));
   }

   @Test
   void aUniformLayerReportsItsOwnSelectionState() {
      DataSet data = data();

      assertTrue(new CompanionBrushColorFrame(null, true, new StaticColorFrame(AZURE), false)
                    .isSelected(data, 0));
      assertFalse(new CompanionBrushColorFrame(null, false, new StaticColorFrame(AZURE), false)
                     .isSelected(data, 0));
   }

   @Test
   void aPerRowLayerSplitsSelectedFromUnselected() {
      DataSet data = data();
      CompanionBrushColorFrame frame = new CompanionBrushColorFrame(
         brushedAtRow(0), false, new StaticColorFrame(AZURE), false);

      assertTrue(frame.isSelected(data, 0));
      assertEquals(AZURE, frame.getColor(data, "Sales", 0));

      assertFalse(frame.isSelected(data, 1));
      assertEquals(AZURE_SOFT, frame.getColor(data, "Sales", 1));
   }

   @Test
   void aUniformSelectedLayerKeepsTheBaseColourByValue() {
      CompanionBrushColorFrame frame =
         new CompanionBrushColorFrame(null, true, new StaticColorFrame(AZURE), false);

      assertEquals(AZURE, frame.getColor("Sales"));
   }

   @Test
   void aUniformUnselectedLayerTakesTheCompanionByValue() {
      CompanionBrushColorFrame frame =
         new CompanionBrushColorFrame(null, false, new StaticColorFrame(AZURE), false);

      assertEquals(AZURE_SOFT, frame.getColor("Sales"));
   }

   @Test
   void aPerRowLayerKeepsTheBaseColourByValueRegardlessOfSelected() {
      HLColorFrame predicate = brushedAtRow(0);

      assertEquals(AZURE,
                   new CompanionBrushColorFrame(predicate, false, new StaticColorFrame(AZURE), false)
                      .getColor("Sales"));
      assertEquals(AZURE,
                   new CompanionBrushColorFrame(predicate, true, new StaticColorFrame(AZURE), false)
                      .getColor("Sales"));
   }

   @Test
   void applicabilityFollowsTheWrappedFrame() {
      // a relation geometry asks with the source dimension for a root node. answering yes there
      // makes the wrapped frame resolve the target row's colour, because a categorical frame reads
      // its own field and ignores the column it is handed - so the root node takes its child's hue
      CategoricalColorFrame palette = new CategoricalColorFrame();
      palette.setField("Target");

      CompanionBrushColorFrame frame =
         new CompanionBrushColorFrame(null, false, palette, false);

      assertTrue(frame.isApplicable("Target"));
      assertFalse(frame.isApplicable("Source"));
   }

   @Test
   void aScopedPredicateSelectsOnlyItsOwnLevel() {
      // a relation brush condition that lands on one level scopes its highlight to that level.
      // the row still matches the condition, so an unscoped check would report every column of
      // that row as selected - on a tree, brushing a parent would leave its children undimmed
      DataSet data = data();
      CompanionBrushColorFrame frame = new CompanionBrushColorFrame(
         brushedAtRow(0, "Target"), false, new StaticColorFrame(AZURE), false);

      assertTrue(frame.isSelected(data, "Target", 0));
      assertFalse(frame.isSelected(data, "Source", 0));

      assertEquals(AZURE, frame.getColor(data, "Target", 0));
      assertEquals(AZURE_SOFT, frame.getColor(data, "Source", 0));
   }

   @Test
   void anUnscopedPredicateSelectsEveryLevel() {
      DataSet data = data();
      CompanionBrushColorFrame frame = new CompanionBrushColorFrame(
         brushedAtRow(0), false, new StaticColorFrame(AZURE), false);

      assertTrue(frame.isSelected(data, "Target", 0));
      assertTrue(frame.isSelected(data, "Source", 0));
   }

   @Test
   void applicabilityIsFalseWithoutABase() {
      assertFalse(new CompanionBrushColorFrame(null, false, null, false).isApplicable("Target"));
   }

   @Test
   void brightnessDarkensTheCompanion() {
      CompanionBrushColorFrame frame =
         new CompanionBrushColorFrame(null, false, new StaticColorFrame(AZURE), false);
      frame.setBrightness(0.75);

      assertEquals(new Color(0x718EB0), frame.getColor(data(), "Sales", 0));
      assertEquals(new Color(0x718EB0), frame.getColor("Sales"));
   }

   @Test
   void brightnessDarkensASelectedMarkToo() {
      CompanionBrushColorFrame frame =
         new CompanionBrushColorFrame(null, true, new StaticColorFrame(AZURE), false);
      frame.setBrightness(0.75);

      assertEquals(new Color(0x036CBF), frame.getColor(data(), "Sales", 0));
   }

   @Test
   void aCloneDoesNotShareItsBase() {
      StaticColorFrame base = new StaticColorFrame(AZURE);
      CompanionBrushColorFrame frame = new CompanionBrushColorFrame(null, false, base, false);
      CompanionBrushColorFrame copy = (CompanionBrushColorFrame) frame.clone();

      assertNotSame(base, copy.getBase());
      assertEquals(AZURE_SOFT, copy.getColor(data(), "Sales", 0));

      // brightness pushed onto the clone's base must not reach the original's
      copy.getBase().setBrightness(0.5);
      assertEquals(AZURE_SOFT, frame.getColor(data(), "Sales", 0));
   }

   @Test
   void repeatedResolutionsOfTheSameBaseAgree() {
      CompanionBrushColorFrame frame =
         new CompanionBrushColorFrame(null, false, new StaticColorFrame(AZURE), false);
      Color first = frame.getColor(data(), "Sales", 0);

      assertEquals(AZURE_SOFT, first);
      assertEquals(first, frame.getColor(data(), "Sales", 0));
      assertEquals(first, frame.getColor(data(), "Sales", 0));
   }

   @Test
   void aColourOutsideThePaletteStillResolves() {
      CompanionBrushColorFrame frame = new CompanionBrushColorFrame(
         null, false, new StaticColorFrame(new Color(0x123456)), false);
      Color companion = frame.getColor(data(), "Sales", 0);

      assertNotNull(companion);
      assertEquals(companion, frame.getColor(data(), "Sales", 0));
   }

   @Test
   void withBaseKeepsTheSelectionRuleAndSwapsTheColour() {
      CompanionBrushColorFrame frame =
         new CompanionBrushColorFrame(brushedAtRow(0), false, new StaticColorFrame(AZURE), false);
      CompanionBrushColorFrame rebased = frame.withBase(new StaticColorFrame(CORAL));

      assertTrue(rebased.isSelected(data(), 0));
      assertEquals(CORAL, rebased.getColor(data(), "Sales", 0));
      assertEquals(CORAL_SOFT, rebased.getColor(data(), "Sales", 1));
   }

   private DataSet data() {
      return new DefaultDataSet(new Object[][] {
         { "Region", "Sales" },
         { "East", 100.0 },
         { "West", 5.0 }
      });
   }

   private HLColorFrame brushedAtRow(int brushedRow) {
      return brushedAtRow(brushedRow, null);
   }

   private HLColorFrame brushedAtRow(int brushedRow, String level) {
      return new HLColorFrame(level, null, null) {
         @Override
         public Highlight getHighlight(DataSet data, int row) {
            return row == brushedRow ? new TextHighlight() : null;
         }
      };
   }
}
