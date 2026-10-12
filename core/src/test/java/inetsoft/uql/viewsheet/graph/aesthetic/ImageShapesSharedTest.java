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
package inetsoft.uql.viewsheet.graph.aesthetic;

import inetsoft.graph.aesthetic.GShape;
import inetsoft.graph.aesthetic.SVGShape;
import inetsoft.test.*;
import inetsoft.util.DataSpace;
import inetsoft.util.script.graal.GraalJavaScriptEngine;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77497: the image shape cache hands the same shape objects to every chart of an
 * organization (and, through the default-organization fallback, of others), so the cached
 * shapes are read-only, like the shape constants. Covers the built-in shapes and an image file
 * loaded from the shapes folder, read in Java, through the frame wrappers and from a script.
 * Failure messages report counts only.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ImageShapesSharedTest {
   @BeforeEach
   void setup() throws Exception {
      BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = image.createGraphics();
      g.setColor(Color.BLUE);
      g.fillRect(0, 0, 8, 8);
      g.dispose();
      ByteArrayOutputStream png = new ByteArrayOutputStream();
      ImageIO.write(image, "png", png);
      DataSpace.getDataSpace().withOutputStream(
         ImageShapes.getGlobalShapesDirectory(), IMAGE_FILE, out -> out.write(png.toByteArray()));
      ImageShapes.clearAllShapes();
   }

   @AfterEach
   void teardown() {
      DataSpace.getDataSpace().delete(ImageShapes.getGlobalShapesDirectory(), IMAGE_FILE);
      // drop any cached shape a write changed, so other tests are not affected
      ImageShapes.clearAllShapes();
   }

   @Test
   void cachedShapesRejectChanges() {
      List<GShape> shapes = cachedShapes();
      int images = 0;
      int open = 0;

      for(GShape shape : shapes) {
         open += refused(() -> shape.setLineColor(Color.RED));
         open += refused(() -> shape.setFillColor(Color.RED));
         open += refused(() -> shape.setLineStyle(2));

         if(shape instanceof SVGShape svg) {
            open += refused(() -> svg.setSVG("images/x.svg"));
         }
         else if(shape instanceof GShape.ImageShape image) {
            images++;
            open += refused(() -> image.setApplyColor(true));
            open += refused(() -> image.setApplySize(false));
            open += refused(() -> image.setTile(true));
            open += refused(() -> image.setIgnoredColor(Color.RED));
            open += refused(() -> image.setAlignment(GShape.ImageShape.Alignment.TOP));
            open += refused(() -> image.setImage(
               new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)));
         }
      }

      assertTrue(images > 0, "no image file shape loaded");
      assertEquals(0, open, open + " setter call(s) on a cached shape were not refused");
   }

   /** The frame wrappers hand out the cached shape itself; a copy of it can be changed. */
   @Test
   void frameShapesAreTheCachedShapesAndCopiesCanBeChanged() {
      int different = 0;

      for(String name : ImageShapes.getShapeNames()) {
         GShape shape = ImageShapes.getShape(name);
         different += ShapeFrameWrapper.getGShape(name) == shape ? 0 : 1;

         GShape copy = shape.clone();
         copy.setLineColor(Color.RED);
         assertEquals(Color.RED, copy.getLineColor());

         if(copy instanceof GShape.ImageShape image) {
            image.setApplyColor(true);
            assertTrue(image.isApplyColor());
            assertFalse(((GShape.ImageShape) shape).isApplyColor());
         }

         assertNull(shape.getLineColor());
      }

      assertEquals(0, different, different + " frame shape(s) differ from the cached shape");
   }

   /** A script that reads a cached shape cannot change it for other charts. */
   @Test
   void scriptCannotChangeCachedShapes() throws Exception {
      GraalJavaScriptEngine engine = new GraalJavaScriptEngine();
      engine.init(new HashMap<>());
      int reached = 0;
      int changed = 0;

      try {
         for(String name : ImageShapes.getShapeNames()) {
            GShape shape = ImageShapes.getShape(name);
            String read = "Java.type('" + ImageShapes.class.getName() + "').getShape('" +
               name + "')";
            reached += engine.exec(engine.compile(read), null, null) == shape ? 1 : 0;
            engine.exec(engine.compile(
               "var s = " + read + ";" +
               "try { s.setLineColor(java.awt.Color.RED); } catch(e) {}" +
               "try { s.setApplyColor(true); } catch(e) {}" +
               "try { s.setApplySize(false); } catch(e) {}" +
               "try { s.setSVG('images/x.svg'); } catch(e) {}" +
               "try { s.setImage(null); } catch(e) {} 1"), null, null);

            boolean same = shape.getLineColor() == null;

            if(shape instanceof GShape.ImageShape image) {
               same &= !image.isApplyColor() && image.isApplySize() && image.getImage() != null;
            }
            else if(shape instanceof SVGShape svg) {
               same &= !"images/x.svg".equals(svg.getSVG());
            }

            changed += same ? 0 : 1;
         }
      }
      finally {
         engine.close();
      }

      assertTrue(reached > 0, "no cached shape reached from a script");
      assertEquals(0, changed, changed + " cached shape(s) changed by a script");
   }

   private static List<GShape> cachedShapes() {
      List<GShape> shapes = new ArrayList<>();

      for(String name : ImageShapes.getShapeNames()) {
         shapes.add(ImageShapes.getShape(name));
      }

      assertTrue(shapes.size() > ImageShapes.getBuiltins().size(), "image file shape not loaded");
      return shapes;
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

   private static final String IMAGE_FILE = "shared-shape-test.png";
}
