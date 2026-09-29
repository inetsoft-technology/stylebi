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
package inetsoft.report.io.viewsheet;

import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.ShapeVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.geom.Rectangle2D;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77293: the PDF page / PNG canvas / PPT slide is sized from the
 * assemblies' own bounds, while the shape writers draw the drop shadow outside
 * them, so a shadowed shape at the right/bottom edge had its shadow cut off.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class AbstractVSExporterShapeShadowTest {
   /**
    * The reported asset (S22_shapes): the oval, SE distance 20 / blur 50
    * (radius 75), is the right- and bottom-most assembly.
    */
   @Test
   void shadowedShapeAtTheEdgeGrowsThePageToItsShadowInk() {
      Viewsheet vs = createViewsheet(true);
      Dimension pref = vs.getPreferredSize(false, true);
      assertEquals(new Dimension(560, 340), pref);

      Dimension size = AbstractVSExporter.adjustSizeForShapeShadows(vs, pref, true);

      // shape edge + offset + blur radius: the gaussian kernel ends at the radius
      assertEquals(new Dimension(560 + 20 + 75, 340 + 20 + 75), size);

      // never beyond the image the writers actually draw
      Rectangle2D drawn = ShapeShadowUtil.expandForShadow(
         new Rectangle2D.Double(360, 240, 200, 100),
         ((VSAssembly) vs.getAssembly("Oval1")).getVSAssemblyInfo());
      assertTrue(size.width <= drawn.getMaxX() && size.height <= drawn.getMaxY());
   }

   @Test
   void viewsheetWithoutShadowsKeepsItsSize() {
      Viewsheet vs = createViewsheet(false);
      Dimension pref = vs.getPreferredSize(false, true);

      assertSame(pref, AbstractVSExporter.adjustSizeForShapeShadows(vs, pref, true));
   }

   @Test
   void hiddenShadowedShapeIsIgnored() {
      Viewsheet vs = createViewsheet(true);
      ((VSAssembly) vs.getAssembly("Oval1")).getVSAssemblyInfo().setVisible("hide");
      Dimension pref = vs.getPreferredSize(false, true);

      // the rectangles still reach past it: Rect_sq 540/180 + 10 + 15
      assertEquals(new Dimension(565, 205),
                   AbstractVSExporter.adjustSizeForShapeShadows(vs, pref, true));
   }

   @Test
   void shadowCastAwayFromTheEdgeOnlyAddsTheBlurBleed() {
      Viewsheet vs = createViewsheet(true);
      shadow(vs, "Oval1").setDirection(ShapeShadow.NORTH_WEST);
      Dimension pref = vs.getPreferredSize(false, true);

      // radius - distance past the right/bottom edge
      assertEquals(new Dimension(560 + 75 - 20, 340 + 75 - 20),
                   AbstractVSExporter.adjustSizeForShapeShadows(vs, pref, true));
   }

   @Test
   void inkBoundsCoverEverySideOfTheShape() {
      Viewsheet vs = createViewsheet(true);
      VSAssemblyInfo info = ((VSAssembly) vs.getAssembly("Oval1")).getVSAssemblyInfo();
      Rectangle2D bounds = new Rectangle2D.Double(360, 240, 200, 100);

      // an SE shadow's blur still bleeds radius - distance up and left
      assertEquals(new Rectangle2D.Double(360 - 55, 240 - 55, 200 + 55 + 95, 100 + 55 + 95),
                   AbstractVSExporter.expandForShadowInk(bounds, info, 1));

      // no blur: only the offset, and nothing on the opposite side
      shadow(vs, "Oval1").setBlur(0);
      assertEquals(new Rectangle2D.Double(360, 240, 220, 120),
                   AbstractVSExporter.expandForShadowInk(bounds, info, 1));

      ((ShapeVSAssemblyInfo) info).setShadowValue(false);
      assertSame(bounds, AbstractVSExporter.expandForShadowInk(bounds, info, 1));
   }

   @Test
   void annotationsAreOnlyConsideredWhenRequested() {
      Viewsheet vs = createViewsheet(false);
      AnnotationRectangleVSAssembly note = new AnnotationRectangleVSAssembly(vs, "Note1");
      note.setPixelOffset(new Point(500, 300));
      note.setPixelSize(new Dimension(60, 40));
      setShadow(note, ShapeShadow.SOUTH_EAST, 10, 10);
      vs.addAssembly(note);
      Dimension pref = new Dimension(560, 340);

      assertSame(pref, AbstractVSExporter.adjustSizeForShapeShadows(vs, pref, false));
      assertEquals(new Dimension(585, 365),
                   AbstractVSExporter.adjustSizeForShapeShadows(vs, pref, true));
   }

   private static Viewsheet createViewsheet(boolean shadow) {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(shape(new RectangleVSAssembly(vs, "Rect_ns"), 60, 60, 300, 100,
                           shadow, 10, 10));
      vs.addAssembly(shape(new RectangleVSAssembly(vs, "Rect_sq"), 420, 60, 120, 120,
                           shadow, 10, 10));
      vs.addAssembly(shape(new OvalVSAssembly(vs, "Oval1"), 360, 240, 200, 100,
                           shadow, 20, 50));
      return vs;
   }

   private static ShapeVSAssembly shape(ShapeVSAssembly shape, int x, int y, int w, int h,
                                        boolean shadow, int distance, int blur)
   {
      shape.setPixelOffset(new Point(x, y));
      shape.setPixelSize(new Dimension(w, h));

      if(shadow) {
         setShadow(shape, ShapeShadow.SOUTH_EAST, distance, blur);
      }

      return shape;
   }

   private static void setShadow(VSAssembly shape, String dir, int distance, int blur) {
      ShapeVSAssemblyInfo info = (ShapeVSAssemblyInfo) shape.getVSAssemblyInfo();
      ShapeShadow shadow = new ShapeShadow();
      shadow.setDirection(dir);
      shadow.setDistance(distance);
      shadow.setBlur(blur);
      info.setShadowValue(true);
      info.setShadowInfo(shadow);
   }

   private static ShapeShadow shadow(Viewsheet vs, String name) {
      return ((ShapeVSAssemblyInfo) ((VSAssembly) vs.getAssembly(name))
         .getVSAssemblyInfo()).getShadowInfo();
   }
}
