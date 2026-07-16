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
import org.junit.jupiter.api.Test;

import java.awt.Shape;
import java.awt.geom.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards that RelationVO.resolveNodeShape() returns a shape matching the rendered node,
 * so the selection region matches what is painted (Bug #75650).
 */
class RelationVONodeShapeTest {
   private static final Rectangle2D BOX = new Rectangle2D.Double(0, 0, 100, 60);

   @Test
   void circleResolvesToEllipse() {
      RelationElement elem = new RelationElement("From", "To");
      elem.setNodeShape(GShape.CIRCLE);
      assertInstanceOf(Ellipse2D.class, RelationVO.resolveNodeShape(BOX, elem));
   }

   @Test
   void triangleResolvesToClosedPath() {
      RelationElement elem = new RelationElement("From", "To");
      elem.setNodeShape(GShape.TRIANGLE);
      Shape s = RelationVO.resolveNodeShape(BOX, elem);
      assertInstanceOf(Path2D.class, s);
      assertFalse(new Area(s).isEmpty(), "triangle is a closed, fillable contour");
   }

   @Test
   void diamondResolvesToClosedPath() {
      RelationElement elem = new RelationElement("From", "To");
      elem.setNodeShape(GShape.DIAMOND);
      Shape s = RelationVO.resolveNodeShape(BOX, elem);
      assertInstanceOf(Path2D.class, s);
      assertFalse(new Area(s).isEmpty(), "diamond is a closed, fillable contour");
   }

   @Test
   void squareResolvesToRectangle() {
      RelationElement elem = new RelationElement("From", "To");
      elem.setNodeShape(GShape.SQUARE);
      assertInstanceOf(Rectangle2D.class, RelationVO.resolveNodeShape(BOX, elem));
   }

   @Test
   void nilResolvesToNullForBoundingBoxFallback() {
      RelationElement elem = new RelationElement("From", "To");
      elem.setNodeShape(GShape.NIL);
      assertNull(RelationVO.resolveNodeShape(BOX, elem),
                 "GShape.NIL returns null from getShape() -> bounding-box fallback");
   }

   @Test
   void noShapeResolvesToNullForBoundingBoxFallback() {
      RelationElement elem = new RelationElement("From", "To");
      assertNull(RelationVO.resolveNodeShape(BOX, elem),
                 "no custom shape and no corner radius -> bounding-box fallback");
   }

   @Test
   void cornerRadiusResolvesToRoundRectangle() {
      RelationElement elem = new RelationElement("From", "To");
      elem.setNodeCornerRadius(0.2);
      assertInstanceOf(RoundRectangle2D.class, RelationVO.resolveNodeShape(BOX, elem));
   }

   @Test
   void nodeShapeWinsOverCornerRadius() {
      // matches paint() precedence: nodeShape wins over nodeCornerRadius
      RelationElement elem = new RelationElement("From", "To");
      elem.setNodeCornerRadius(0.2);
      elem.setNodeShape(GShape.CIRCLE);
      assertInstanceOf(Ellipse2D.class, RelationVO.resolveNodeShape(BOX, elem));
   }
}
