/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.graph.schema;

import inetsoft.graph.aesthetic.StaticColorFrame;
import inetsoft.graph.element.SchemaElement;
import inetsoft.graph.geometry.SchemaGeometry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A scripted SchemaElement has no color frame; painting with a border color must not NPE.
 */
@Tag("core")
class BoxPainterTest {
   @Test
   void borderColorWithoutColorFrameDoesNotThrow() throws Exception {
      SchemaElement elem = new SchemaElement(new BoxPainter());
      elem.setBorderColor(Color.RED);
      assertNull(elem.getColorFrame());

      BufferedImage img = paint(elem);

      // the median line is drawn with the border color
      assertEquals(Color.RED.getRGB(), img.getRGB(10, 20));
   }

   @Test
   void borderColorWithColorFrameStillPaints() throws Exception {
      SchemaElement elem = new SchemaElement(new BoxPainter());
      elem.setBorderColor(Color.RED);
      elem.setColorFrame(new StaticColorFrame(Color.BLUE));

      BufferedImage img = paint(elem);

      assertNotEquals(0, img.getRGB(10, 20));
   }

   private BufferedImage paint(SchemaElement elem) throws Exception {
      BoxPainter painter = new BoxPainter();
      SchemaGeometry geometry = mock(SchemaGeometry.class);
      when(geometry.getElement()).thenReturn(elem);
      when(geometry.getColor(0)).thenReturn(Color.GREEN);

      Field gf = SchemaPainter.class.getDeclaredField("geometry");
      gf.setAccessible(true);
      gf.set(painter, geometry);

      Field sf = BoxPainter.class.getDeclaredField("shapes");
      sf.setAccessible(true);
      sf.set(painter, new java.awt.Shape[] {
         new Line2D.Double(10, 0, 10, 10), new Line2D.Double(5, 0, 15, 0),
         new Line2D.Double(5, 10, 15, 10), new Rectangle2D.Double(2, 2, 16, 6),
         new Line2D.Double(0, 20, 20, 20) });

      BufferedImage img = new BufferedImage(30, 30, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();
      painter.paint(g);
      g.dispose();
      return img;
   }
}
