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
package inetsoft.graph.element;

import inetsoft.graph.EGraph;
import inetsoft.graph.GGraph;
import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.graph.aesthetic.ColorFrame;
import inetsoft.graph.aesthetic.StaticColorFrame;
import inetsoft.graph.aesthetic.StaticSizeFrame;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.geometry.RelationGeometry;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for Bug #76417: a tree/relation chart root node's "representative
 * row" (the row whose value feeds the node's color lookup, see
 * {@link RelationGeometry#getSubRowIndex()}) must be chosen deterministically, not by
 * incidental query/row order.
 * <p>
 * {@link RelationElement#createGeometry} picks, for each node, the first row encountered
 * after {@link RelationElement#sortData} sorts the data by {@code [sourceDim, targetDim]}.
 * When multiple rows tie on both of those, the field that actually paints the node --
 * the element's general Color frame and/or its Node Color frame -- is now added as an
 * additional **descending** sort key so the tie is broken in favor of the most recent
 * value of that field, instead of by incidental row order. This test builds the real
 * {@link RelationElement}/{@link EGraph}/{@link GGraph}
 * pipeline (no mocking) and reads back the actual {@code getSubRowIndex()} chosen by the
 * production code, following the same technique used to originally prove the defect.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class RelationElementColorTieBreakTest {
   /**
    * Two rows tie on [Group=B, Year=2017] but differ on Quarter ("2017Q1" vs "2017Q3").
    * With Quarter bound as the element's general Color, the tie must always be broken in
    * favor of the same row -- the one with the largest (descending / most-recent) Quarter
    * value -- no matter which of the tied rows the query happened to return first.
    */
   @Test
   void colorFieldBreaksTie_orderIndependent_generalColor() {
      // "2017Q3" listed before "2017Q1" for group B.
      Object[][] originalOrder = {
         { "Group", "Year", "Quarter" },
         { "B", 2019, "2019Q2" },
         { "B", 2017, "2017Q3" },
         { "B", 2017, "2017Q1" },
         { "B", 2018, "2018Q1" },
         { "A", 2020, "2020Q4" },
         { "A", 2017, "2017Q2" },
      };
      // Same rows, only the two tied 2017/group-B rows swapped.
      Object[][] swappedOrder = {
         { "Group", "Year", "Quarter" },
         { "B", 2019, "2019Q2" },
         { "B", 2017, "2017Q1" },
         { "B", 2017, "2017Q3" },
         { "B", 2018, "2018Q1" },
         { "A", 2020, "2020Q4" },
         { "A", 2017, "2017Q2" },
      };

      String quarterOriginal = winningQuarterForRoot("B", originalOrder, new CategoricalColorFrame("Quarter"), null);
      String quarterSwapped = winningQuarterForRoot("B", swappedOrder, new CategoricalColorFrame("Quarter"), null);

      assertEquals(quarterOriginal, quarterSwapped,
                   "Row order must not affect which row is chosen as group B's representative row");
      // Descending tie-break convention: most recent (largest) Quarter value among the tied rows wins.
      assertEquals("2017Q3", quarterOriginal,
                   "The tie between 2017Q1 and 2017Q3 must be broken by descending (most-recent) Quarter value");

      // Group A has only one 2017 row (no tie) -- still resolves correctly.
      String quarterA = winningQuarterForRoot("A", originalOrder, new CategoricalColorFrame("Quarter"), null);
      assertEquals("2017Q2", quarterA, "Group A's single 2017 row must still be its representative row");
   }

   /**
    * Same scenario as above, but the tie-breaking field is bound only as the element's
    * Node Color (not the general Color) -- the fix must consult both frames, each for its
    * own node type.
    * <p>
    * This checks the <b>leaf/target</b> ("Year") node, not the root -- per
    * {@code RelationGeometry.getColor()} (read directly: lines 60-95), a leaf node
    * <i>always</i> resolves its color via {@code getNodeColorFrame()} directly, while a
    * root node only falls back to it when Node Color's field equals the root's own
    * dimension; here Node Color is bound to "Quarter" (not "Group"), so a root node
    * in this exact configuration would actually fall through to {@code super.getColor(0)}
    * -- i.e. the *general* Color frame (unset/null here), not Node Color at all. Checking
    * the leaf node is therefore the structurally-correct way to prove this fix's Node
    * Color tie-break, independent of the general Color tie-break already proven above.
    */
   @Test
   void colorFieldBreaksTie_orderIndependent_nodeColor() {
      Object[][] originalOrder = {
         { "Group", "Year", "Quarter" },
         { "B", 2017, "2017Q3" },
         { "B", 2017, "2017Q1" },
      };
      Object[][] swappedOrder = {
         { "Group", "Year", "Quarter" },
         { "B", 2017, "2017Q1" },
         { "B", 2017, "2017Q3" },
      };

      String quarterOriginal = winningQuarterForNode("Year", "2017", originalOrder, null, new CategoricalColorFrame("Quarter"));
      String quarterSwapped = winningQuarterForNode("Year", "2017", swappedOrder, null, new CategoricalColorFrame("Quarter"));

      assertEquals(quarterOriginal, quarterSwapped,
                   "Row order must not affect which row is chosen when only Node Color is bound to the field");
      assertEquals("2017Q3", quarterOriginal);
   }

   /**
    * Reviewer-flagged scenario (the reason this test class's helper was generalized to
    * check either node type): general Color and Node Color bound to two <b>different</b>
    * fields, with rows tied on [Group, Year] where the "most recent" row differs between
    * the two fields. The root's representative row must maximize the general Color field
    * and the leaf's representative row must maximize the Node Color field --
    * <i>independently</i>, not whichever field a single shared sort-priority order would
    * have favored for both node types at once.
    */
   @Test
   void colorAndNodeColorOnDifferentFields_eachNodeTypeUsesItsOwnField() {
      // Row 0 has the larger ColorA (general Color field) but the smaller ColorB
      // (Node Color field); row 1 is the reverse. If a single shared sort/tie-break
      // (as in the original, reviewer-flagged implementation) picked one winning row for
      // both node types, one of the two assertions below would fail.
      Object[][] rows = {
         { "Group", "Year", "ColorA", "ColorB" },
         { "X", 2020, "A2", "B1" },
         { "X", 2020, "A1", "B2" },
      };

      DefaultDataSet data = new DefaultDataSet(rows);
      CategoricalScale groupScale = new CategoricalScale("Group");
      groupScale.init(data);
      CategoricalScale yearScale = new CategoricalScale("Year");
      yearScale.init(data);

      RelationElement element = new RelationElement("Group", "Year");
      element.setSizeFrame(new StaticSizeFrame());
      element.setNodeSizeFrame(new StaticSizeFrame());
      element.setColorFrame(new CategoricalColorFrame("ColorA"));
      element.setNodeColorFrame(new CategoricalColorFrame("ColorB"));

      EGraph egraph = new EGraph();
      egraph.addElement(element);
      RectCoord coord = new RectCoord(groupScale, yearScale);
      egraph.setCoordinate(coord);
      GGraph ggraph = egraph.createGGraph(coord, data);

      Integer rootSubRowIndex = null;
      Integer leafSubRowIndex = null;

      for(int i = 0; i < ggraph.getGeometryCount(); i++) {
         Object geom = ggraph.getGeometry(i);

         if(!(geom instanceof RelationGeometry)) {
            continue;
         }

         RelationGeometry rgeom = (RelationGeometry) geom;

         if("Group".equals(rgeom.getVar()) && "X".equals(rgeom.getMxCell().getValue())) {
            rootSubRowIndex = rgeom.getSubRowIndex();
         }
         else if("Year".equals(rgeom.getVar()) && "2020".equals(String.valueOf(rgeom.getMxCell().getValue()))) {
            leafSubRowIndex = rgeom.getSubRowIndex();
         }
      }

      assertNotNull(rootSubRowIndex, "Root node for Group=X must exist");
      assertNotNull(leafSubRowIndex, "Leaf node for Year=2020 must exist");

      // Root's representative row must be the one that maximizes ColorA ("A2", row 0) --
      // driven by the general Color frame, independent of ColorB.
      assertEquals("A2", data.getData("ColorA", rootSubRowIndex),
                   "Root node's representative row must maximize the general Color field");
      // Leaf's representative row must be the one that maximizes ColorB ("B2", row 1) --
      // driven by the Node Color frame, independent of ColorA.
      assertEquals("B2", data.getData("ColorB", leafSubRowIndex),
                   "Leaf node's representative row must maximize the Node Color field");
   }

   /**
    * No-op case: when no color field is bound at all (a plain static color), there is
    * nothing to tie-break on, so the pre-fix behavior -- first row in original order wins
    * -- must be preserved exactly. This documents that the fix does not invent a new
    * "most recent wins" semantic; it only adds a tie-break when a color field is present.
    */
   @Test
   void noColorFieldBound_tieStillFollowsOriginalRowOrder() {
      Object[][] q3First = {
         { "Group", "Year", "Quarter" },
         { "B", 2017, "2017Q3" },
         { "B", 2017, "2017Q1" },
      };
      Object[][] q1First = {
         { "Group", "Year", "Quarter" },
         { "B", 2017, "2017Q1" },
         { "B", 2017, "2017Q3" },
      };

      StaticColorFrame staticColor = new StaticColorFrame();
      assertNull(staticColor.getField(), "Static color frame has no bound field");

      String winnerWhenQ3First = winningQuarterForRoot("B", q3First, staticColor, null);
      String winnerWhenQ1First = winningQuarterForRoot("B", q1First, staticColor, null);

      assertEquals("2017Q3", winnerWhenQ3First, "With no color field, first-in-order row wins (pre-fix behavior)");
      assertEquals("2017Q1", winnerWhenQ1First, "With no color field, first-in-order row wins (pre-fix behavior)");
      assertNotEquals(winnerWhenQ3First, winnerWhenQ1First,
                       "Without a color field to tie-break on, row order still determines the outcome -- unchanged from before the fix");
   }

   /**
    * No-tie case: when there is no ambiguity (each source/target pair appears once),
    * binding a color field must not change which row is picked -- the fix is a no-op
    * when there is nothing to break a tie on.
    */
   @Test
   void noTies_colorFieldDoesNotChangeSelection() {
      Object[][] data = {
         { "Group", "Year", "Quarter" },
         { "A", 2017, "2017Q2" },
         { "A", 2020, "2020Q4" },
         { "B", 2018, "2018Q1" },
      };

      String withColor = winningQuarterForRoot("A", data, new CategoricalColorFrame("Quarter"), null);
      String withoutColor = winningQuarterForRoot("A", data, new StaticColorFrame(), null);

      assertEquals("2017Q2", withColor);
      assertEquals(withoutColor, withColor,
                   "With no ties, binding a color field must not change the chosen representative row");
   }

   /**
    * Edge case: the color field is the same field as the target dimension itself. The
    * fix must recognize the field is already part of the sort key and not attempt to add
    * a redundant/conflicting sort column (which would otherwise risk an exception or
    * double-application of the same column).
    */
   @Test
   void colorFieldSameAsTargetDim_noRedundantSortColumnAdded() {
      Object[][] data = {
         { "Group", "Year", "Quarter" },
         { "B", 2017, "2017Q3" },
         { "B", 2017, "2017Q1" },
      };

      // Color bound to the target dimension itself ("Year") -- already the second sort key.
      assertDoesNotThrow(() -> winningQuarterForRoot("B", data, new CategoricalColorFrame("Year"), null),
                          "Binding color to a field already in the sort key must not throw");
   }

   /**
    * Builds a real RelationElement -> EGraph -> GGraph pipeline (source dim "Group",
    * target dim "Year") over the given data, with the given color/node-color frames
    * (either may be null), and returns the "Quarter" value at the representative row
    * chosen for the given root ("Group") value.
    */
   private String winningQuarterForRoot(String groupValue, Object[][] rows,
                                        ColorFrame colorFrame, ColorFrame nodeColorFrame)
   {
      return winningQuarterForNode("Group", groupValue, rows, colorFrame, nodeColorFrame);
   }

   /**
    * Same as {@link #winningQuarterForRoot}, but for an arbitrary node dimension/value --
    * i.e. either the root ("Group") or the leaf ("Year") node. Returns the "Quarter" value
    * at the representative row chosen for the node identified by {@code varName}/
    * {@code value} (e.g. {@code ("Year", "2017", ...)} for the leaf node whose Year is
    * 2017).
    */
   private String winningQuarterForNode(String varName, Object value, Object[][] rows,
                                        ColorFrame colorFrame, ColorFrame nodeColorFrame)
   {
      DefaultDataSet data = new DefaultDataSet(rows);

      CategoricalScale groupScale = new CategoricalScale("Group");
      groupScale.init(data);
      CategoricalScale yearScale = new CategoricalScale("Year");
      yearScale.init(data);

      RelationElement element = new RelationElement("Group", "Year");
      // size frames are required by RelationElement.createGeometry()/RelationGeometry.getSize()
      // regardless of what this test is exercising; a plain static size is irrelevant to the
      // tie-break logic.
      element.setSizeFrame(new StaticSizeFrame());
      element.setNodeSizeFrame(new StaticSizeFrame());

      if(colorFrame != null) {
         element.setColorFrame(colorFrame);
      }

      if(nodeColorFrame != null) {
         element.setNodeColorFrame(nodeColorFrame);
      }

      EGraph egraph = new EGraph();
      egraph.addElement(element);

      RectCoord coord = new RectCoord(groupScale, yearScale);
      egraph.setCoordinate(coord);

      GGraph ggraph = egraph.createGGraph(coord, data);

      for(int i = 0; i < ggraph.getGeometryCount(); i++) {
         Object geom = ggraph.getGeometry(i);

         if(!(geom instanceof RelationGeometry)) {
            continue;
         }

         RelationGeometry rgeom = (RelationGeometry) geom;

         if(!varName.equals(rgeom.getVar())) {
            continue;
         }

         if(!String.valueOf(value).equals(String.valueOf(rgeom.getMxCell().getValue()))) {
            continue;
         }

         int subRowIndex = rgeom.getSubRowIndex();
         return (String) data.getData("Quarter", subRowIndex);
      }

      throw new AssertionError("No " + varName + " node found for value=" + value);
   }
}
