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
package inetsoft.graph.visual;

import inetsoft.graph.aesthetic.GShape;
import inetsoft.graph.element.RelationElement;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.geom.Rectangle2D;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression guard for the inside-label inscribed-rect inset on rounded relation nodes
 * (ex.png overflow case): the text-layout box must shrink to fit the curved boundary,
 * not the outer rectangle.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class RelationVOInsideTextBoxTest {
   private static final double SQRT2_INSET = 1 - 1 / Math.sqrt(2);   // ~0.2929
   private static final double EPS = 1e-9;

   @Test
   void noRoundingReturnsBoxUnchanged() {
      Rectangle2D box = new Rectangle2D.Double(10, 20, 100, 40);
      RelationElement elem = new RelationElement("From", "To");
      // nodeCornerRadius defaults to 0 and nodeShape defaults to null

      Rectangle2D out = RelationVO.getInsideTextBox(box, elem);
      assertEquals(box.getX(), out.getX(), EPS);
      assertEquals(box.getY(), out.getY(), EPS);
      assertEquals(box.getWidth(), out.getWidth(), EPS);
      assertEquals(box.getHeight(), out.getHeight(), EPS);
   }

   @Test
   void cornerRadiusShrinksInscribedRectBy45DegInset() {
      Rectangle2D box = new Rectangle2D.Double(0, 0, 100, 40);
      RelationElement elem = new RelationElement("From", "To");
      elem.setNodeCornerRadius(0.3);

      Rectangle2D out = RelationVO.getInsideTextBox(box, elem);
      // aRadius = 0.3 * min(100,40) = 12; per-side inset = 12 * (1 - 1/sqrt2) ~ 3.515
      double expectedInset = 0.3 * 40 * SQRT2_INSET;
      assertEquals(100 - 2 * expectedInset, out.getWidth(), EPS);
      assertEquals(40 - 2 * expectedInset, out.getHeight(), EPS);
      // centred on the input box
      assertEquals(box.getCenterX(), out.getCenterX(), EPS);
      assertEquals(box.getCenterY(), out.getCenterY(), EPS);
   }

   @Test
   void inscribedRectStrictlySmallerThanBoxForAnyPositiveRadius() {
      Rectangle2D box = new Rectangle2D.Double(0, 0, 100, 40);
      RelationElement elem = new RelationElement("From", "To");
      elem.setNodeCornerRadius(0.05);   // small but non-zero

      Rectangle2D out = RelationVO.getInsideTextBox(box, elem);
      assertTrue(out.getWidth() < box.getWidth(),
                 "any positive corner radius must shrink the available text width");
      assertTrue(out.getHeight() < box.getHeight(),
                 "any positive corner radius must shrink the available text height");
   }

   @Test
   void halfRadiusStadiumShrinksToShortDimDriven() {
      // r=0.5 on a wide-short rect produces a stadium; inset is driven by min(w,h)
      Rectangle2D box = new Rectangle2D.Double(0, 0, 80, 30);
      RelationElement elem = new RelationElement("From", "To");
      elem.setNodeCornerRadius(0.5);

      Rectangle2D out = RelationVO.getInsideTextBox(box, elem);
      double expectedInset = 0.5 * 30 * SQRT2_INSET;   // aRadius = 15
      assertEquals(80 - 2 * expectedInset, out.getWidth(), EPS);
      assertEquals(30 - 2 * expectedInset, out.getHeight(), EPS);
   }

   @Test
   void customNodeShapeUsesEllipseInset() {
      Rectangle2D box = new Rectangle2D.Double(0, 0, 100, 60);
      RelationElement elem = new RelationElement("From", "To");
      elem.setNodeShape(GShape.CIRCLE);   // CIRCLE returns Ellipse2D -> ellipse branch

      Rectangle2D out = RelationVO.getInsideTextBox(box, elem);
      // inscribed rect for an ellipse with full dims (w,h): w/sqrt2 x h/sqrt2
      assertEquals(100 / Math.sqrt(2), out.getWidth(), EPS);
      assertEquals(60 / Math.sqrt(2), out.getHeight(), EPS);
   }

   @Test
   void customNodeShapeOverridesCornerRadius() {
      // matches paint() precedence: nodeShape wins over nodeCornerRadius
      Rectangle2D box = new Rectangle2D.Double(0, 0, 100, 60);
      RelationElement elem = new RelationElement("From", "To");
      elem.setNodeCornerRadius(0.1);
      elem.setNodeShape(GShape.CIRCLE);

      Rectangle2D out = RelationVO.getInsideTextBox(box, elem);
      assertEquals(100 / Math.sqrt(2), out.getWidth(), EPS,
                   "nodeShape inset must win over the smaller corner-radius inset");
   }

   @Test
   void nilNodeShapeReturnsBoxUnchanged() {
      // GShape.NIL renders as a plain rectangle, so no inset.
      Rectangle2D box = new Rectangle2D.Double(10, 20, 100, 40);
      RelationElement elem = new RelationElement("From", "To");
      elem.setNodeShape(GShape.NIL);

      Rectangle2D out = RelationVO.getInsideTextBox(box, elem);
      assertEquals(box.getX(), out.getX(), EPS);
      assertEquals(box.getY(), out.getY(), EPS);
      assertEquals(box.getWidth(), out.getWidth(), EPS);
      assertEquals(box.getHeight(), out.getHeight(), EPS);
   }

   @Test
   void squareNodeShapeReturnsBoxUnchanged() {
      // GShape.SQUARE returns a full-bounds Rectangle2D, so the painted node is just the bbox.
      Rectangle2D box = new Rectangle2D.Double(0, 0, 100, 40);
      RelationElement elem = new RelationElement("From", "To");
      elem.setNodeShape(GShape.SQUARE);

      Rectangle2D out = RelationVO.getInsideTextBox(box, elem);
      assertEquals(box.getWidth(), out.getWidth(), EPS);
      assertEquals(box.getHeight(), out.getHeight(), EPS);
   }

   @Test
   void diamondNodeShapeUsesConservativeInset() {
      // For a diamond, the largest inscribed axis-aligned rect is w/2 x h/2.
      Rectangle2D box = new Rectangle2D.Double(0, 0, 100, 40);
      RelationElement elem = new RelationElement("From", "To");
      elem.setNodeShape(GShape.DIAMOND);

      Rectangle2D out = RelationVO.getInsideTextBox(box, elem);
      assertEquals(50.0, out.getWidth(), EPS);
      assertEquals(20.0, out.getHeight(), EPS);
   }

   @Test
   void triangleNodeShapeUsesConservativeInset() {
      // Triangle's max inscribed axis-aligned rect is also w/2 x h/2.
      Rectangle2D box = new Rectangle2D.Double(0, 0, 100, 40);
      RelationElement elem = new RelationElement("From", "To");
      elem.setNodeShape(GShape.TRIANGLE);

      Rectangle2D out = RelationVO.getInsideTextBox(box, elem);
      assertEquals(50.0, out.getWidth(), EPS);
      assertEquals(20.0, out.getHeight(), EPS);
   }

   @Test
   void strokedNodeShapeUsesConservativeInset() {
      // Stroked paths (CROSS = two perpendicular lines) aren't closed regions; the
      // conservative branch still applies a w/2 x h/2 inset so text doesn't run past
      // the bbox of the strokes.
      Rectangle2D box = new Rectangle2D.Double(0, 0, 100, 40);
      RelationElement elem = new RelationElement("From", "To");
      elem.setNodeShape(GShape.CROSS);

      Rectangle2D out = RelationVO.getInsideTextBox(box, elem);
      assertEquals(50.0, out.getWidth(), EPS);
      assertEquals(20.0, out.getHeight(), EPS);
   }

   @Test
   void tinyNodeFloorsAtOnePixel() {
      // pathological tiny node should not produce zero/negative dims
      Rectangle2D box = new Rectangle2D.Double(0, 0, 2, 2);
      RelationElement elem = new RelationElement("From", "To");
      elem.setNodeShape(GShape.CIRCLE);   // ellipse inset on tiny box would shrink below 1px

      Rectangle2D out = RelationVO.getInsideTextBox(box, elem);
      assertTrue(out.getWidth() >= 1.0, "width must be floored at 1px");
      assertTrue(out.getHeight() >= 1.0, "height must be floored at 1px");
   }
}
