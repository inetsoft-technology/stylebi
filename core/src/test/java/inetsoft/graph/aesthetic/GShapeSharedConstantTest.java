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

import inetsoft.graph.EGraph;
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.PointElement;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77497: the public shape constants are shared by every chart, script and thread in the
 * JVM, so they are read-only; copies made from them are not.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class GShapeSharedConstantTest {
   @Test
   void sharedConstantsRejectChanges() {
      int open = 0;
      List<GShape> constants = constants();
      assertFalse(constants.isEmpty());

      for(GShape shape : constants) {
         open += changes(shape, () -> shape.setLineColor(Color.RED));
         open += changes(shape, () -> shape.setFillColor(Color.RED));
         open += changes(shape, () -> shape.setLineStyle(2));
         open += changes(shape, () -> shape.setFill(!shape.isFill()));
         open += changes(shape, () -> shape.setOutline(true));

         if(shape instanceof SVGShape svg) {
            open += changes(shape, () -> svg.setSVG("images/x.svg"));
         }
      }

      assertEquals(0, open, open + " setter call(s) on a shared constant were not refused");
   }

   @Test
   void copiesOfSharedConstantsCanBeChanged() throws Exception {
      for(GShape shape : constants()) {
         GShape clone = shape.clone();
         clone.setLineColor(Color.RED);
         assertEquals(Color.RED, clone.getLineColor());
         assertEquals(shape.getClass(), clone.getClass());

         GShape variation = shape.create(true, true);
         variation.setFillColor(Color.BLUE);
         assertEquals(Color.BLUE, variation.getFillColor());

         GShape copy = deserialize(serialize(shape));
         copy.setLineStyle(2);
         assertEquals(2, copy.getLineStyle());
         assertNull(shape.getLineColor());
      }

      SVGShape svg = SVGShape.STAR.clone() instanceof SVGShape s ? s : null;
      assertNotNull(svg);
      svg.setSVG("images/x.svg");
      assertEquals("images/x.svg", svg.getSVG());
   }

   /** Painting a point with a border and a line style does not change the shape constant. */
   @Test
   void pointWithBorderPaintsWithoutChangingTheConstant() {
      DefaultDataSet data = new DefaultDataSet(new Object[][] {
         { "x", "y" }, { "a", 1 }, { "b", 2 }, { "c", 3 } });
      PointElement elem = new PointElement("x", "y");
      elem.setShapeFrame(new StaticShapeFrame(GShape.CIRCLE));
      elem.setSizeFrame(new StaticSizeFrame(20));
      elem.setBorderColor(Color.RED);
      elem.setLineFrame(new StaticLineFrame(GLine.DASH_LINE));
      EGraph graph = new EGraph();
      graph.addElement(elem);

      VGraph vgraph = Plotter.getPlotter(graph).plotAndLayout(data, 0, 0, 200, 200);
      BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = image.createGraphics();
      int painted = paintPoints(vgraph, g);
      g.dispose();

      assertTrue(painted > 0, "no point painted");
      assertNull(GShape.CIRCLE.getLineColor());
      assertEquals(GShape.CIRCLE.clone().getLineStyle(), GShape.CIRCLE.getLineStyle());
   }

   /**
    * An image shape that is shared (as the shape cache hands out to every chart) refuses its
    * setters, like the shape constants; its copies can be changed.
    */
   @Test
   void sharedImageShapeRejectsChanges() {
      GShape.ImageShape shape = GShape.sharedConstant(new GShape.ImageShape(image(Color.BLUE)));
      Image other = image(Color.GREEN);
      int open = 0;

      open += refused(() -> shape.setImage(other));
      open += refused(() -> shape.setTile(true));
      open += refused(() -> shape.setApplyColor(true));
      open += refused(() -> shape.setApplySize(false));
      open += refused(() -> shape.setIgnoredColor(Color.RED));
      open += refused(() -> shape.setAlignment(GShape.ImageShape.Alignment.TOP));
      open += refused(() -> shape.setLineColor(Color.RED));
      open += refused(() -> shape.setFillColor(Color.RED));
      open += refused(() -> shape.setLineStyle(2));
      open += refused(() -> shape.setFill(false));
      open += refused(() -> shape.setOutline(true));

      assertEquals(0, open, open + " setter call(s) on a shared image shape were not refused");

      GShape.ImageShape clone = (GShape.ImageShape) shape.clone();
      clone.setApplySize(false);
      clone.setApplyColor(true);
      clone.setAlignment(GShape.ImageShape.Alignment.RIGHT);
      clone.setImage(other);
      assertFalse(clone.isApplySize());
      assertSame(other, clone.getImage());

      GShape.ImageShape variation = (GShape.ImageShape) shape.create(true, true);
      variation.setTile(true);
      assertTrue(variation.isTile());

      assertTrue(shape.isApplySize());
      assertFalse(shape.isApplyColor());
      assertFalse(shape.isTile());
      assertEquals(GShape.ImageShape.Alignment.CENTER, shape.getAlignment());
      assertNotSame(other, shape.getImage());
   }

   /**
    * A point without a size frame draws an image shape without applying the shape size; the
    * shape is shared, so that is done on a copy and the shape is unchanged.
    */
   @Test
   void pointWithSharedImageShapePaintsWithoutChangingIt() {
      for(boolean sized : new boolean[] { false, true }) {
         GShape.ImageShape shape = GShape.sharedConstant(new GShape.ImageShape(image(Color.BLUE)));
         DefaultDataSet data = new DefaultDataSet(new Object[][] {
            { "x", "y" }, { "a", 1 }, { "b", 2 }, { "c", 3 } });
         PointElement elem = new PointElement("x", "y");
         elem.setShapeFrame(new StaticShapeFrame(shape));

         if(sized) {
            elem.setSizeFrame(new StaticSizeFrame(20));
         }

         EGraph graph = new EGraph();
         graph.addElement(elem);

         VGraph vgraph = Plotter.getPlotter(graph).plotAndLayout(data, 0, 0, 200, 200);
         BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
         Graphics2D g = image.createGraphics();
         int painted = paintPoints(vgraph, g);
         g.dispose();

         assertTrue(painted > 0, "no point painted");
         assertTrue(shape.isApplySize(), "the shared image shape was changed");
      }
   }

   /** A small image filled with one color. */
   private static Image image(Color color) {
      BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = image.createGraphics();
      g.setColor(color);
      g.fillRect(0, 0, 8, 8);
      g.dispose();
      return image;
   }

   /** 1 if the change went through, 0 if it was refused. */
   private static int refused(Runnable change) {
      try {
         change.run();
      }
      catch(UnsupportedOperationException expected) {
         return 0;
      }

      return 1;
   }

   private static int paintPoints(inetsoft.graph.VContainer container, Graphics2D g) {
      int painted = 0;

      for(int i = 0; i < container.getVisualCount(); i++) {
         Object visual = container.getVisual(i);

         if(visual instanceof inetsoft.graph.visual.PointVO point) {
            try {
               point.paint(g);
            }
            catch(UnsupportedOperationException ex) {
               fail("painting a point changed a shared shape constant");
            }
            catch(RuntimeException ex) {
               // the shape drawing needs the SVG support of a module that is not on this
               // module's test class path; it runs after the border and line are applied
               assertInstanceOf(ClassNotFoundException.class, rootCause(ex));
            }

            painted++;
         }
         else if(visual instanceof inetsoft.graph.VContainer child) {
            painted += paintPoints(child, g);
         }
      }

      return painted;
   }

   private static Throwable rootCause(Throwable ex) {
      while(ex.getCause() != null) {
         ex = ex.getCause();
      }

      return ex;
   }

   /** 1 if the change went through (and is undone), 0 if it was refused. */
   private static int changes(GShape shape, Runnable change) {
      GShape before = shape.clone();

      try {
         change.run();
      }
      catch(UnsupportedOperationException expected) {
         return 0;
      }

      // undo, so other tests are not affected
      shape.setLineColor(before.getLineColor());
      shape.setFillColor(before.getFillColor());
      shape.setLineStyle(before.getLineStyle());
      shape.setFill(before.isFill());

      if(shape instanceof SVGShape svg) {
         svg.setSVG(((SVGShape) before).getSVG());
      }

      return 1;
   }

   private static List<GShape> constants() {
      List<GShape> list = new ArrayList<>();

      for(Class<?> cls : new Class<?>[] { GShape.class, SVGShape.class }) {
         for(Field field : cls.getDeclaredFields()) {
            int mod = field.getModifiers();

            if(Modifier.isPublic(mod) && Modifier.isStatic(mod) &&
               GShape.class.isAssignableFrom(field.getType()))
            {
               try {
                  list.add((GShape) field.get(null));
               }
               catch(IllegalAccessException ex) {
                  throw new AssertionError(ex);
               }
            }
         }
      }

      return list;
   }

   private static byte[] serialize(Object obj) throws IOException {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();

      try(ObjectOutputStream out = new ObjectOutputStream(bytes)) {
         out.writeObject(obj);
      }

      return bytes.toByteArray();
   }

   private static GShape deserialize(byte[] bytes) throws Exception {
      try(ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
         return (GShape) in.readObject();
      }
   }
}
