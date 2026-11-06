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
package inetsoft.web.wiz.binding;

import inetsoft.test.*;
import inetsoft.uql.viewsheet.graph.aesthetic.ImageShapes;
import inetsoft.util.DataSpace;
import inetsoft.web.binding.model.graph.aesthetic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80341: an unknown image shape name used to be stored verbatim and then resolved to a
 * null shape, so the category's marks vanished. The name must be refused before a frame is built.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VisualFrameAliasesShapeNameTest {
   private static final String UPLOADED = "bug80341-uploaded.png";
   private static final String MISSING = "zz-no-such-shape.jpg";

   @BeforeEach
   void setup() throws Exception {
      BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
      ByteArrayOutputStream png = new ByteArrayOutputStream();
      ImageIO.write(image, "png", png);
      DataSpace.getDataSpace().withOutputStream(
         ImageShapes.getGlobalShapesDirectory(), UPLOADED, out -> out.write(png.toByteArray()));
      ImageShapes.clearAllShapes();
   }

   @AfterEach
   void teardown() {
      DataSpace.getDataSpace().delete(ImageShapes.getGlobalShapesDirectory(), UPLOADED);
      ImageShapes.clearAllShapes();
   }

   private static Map<String, Object> spec(Object... pairs) {
      Map<String, Object> spec = new LinkedHashMap<>();

      for(int i = 0; i < pairs.length; i += 2) {
         spec.put((String) pairs[i], pairs[i + 1]);
      }

      return spec;
   }

   @Test
   void rejectsUnknownStaticShapeNamingTheFile() {
      IllegalArgumentException ex = assertThrows(
         IllegalArgumentException.class,
         () -> VisualFrameAliases.create("shape", spec("type", "static", "shape", MISSING)));

      assertTrue(ex.getMessage().contains(MISSING), ex.getMessage());
      assertTrue(ex.getMessage().contains("105Flag.svg"), "lists the available shapes");
   }

   @Test
   void rejectsUnknownNameInAnySlotOfACategoricalFrame() {
      IllegalArgumentException ex = assertThrows(
         IllegalArgumentException.class,
         () -> VisualFrameAliases.create("shape", spec("type", "categorical", "shapes",
                                                       List.of("900", MISSING))));

      assertTrue(ex.getMessage().contains(MISSING), ex.getMessage());
   }

   @Test
   void acceptsBuiltInImageShapeAndGeometricCodes() {
      StaticShapeModel flag = assertInstanceOf(
         StaticShapeModel.class,
         VisualFrameAliases.create("shape", spec("type", "static", "shape", "105Flag.svg")));
      assertEquals("105Flag.svg", flag.getShape());

      CategoricalShapeModel mixed = assertInstanceOf(
         CategoricalShapeModel.class,
         VisualFrameAliases.create("shape", spec("type", "categorical", "shapes",
                                                 List.of("900", "105Flag.svg"))));
      assertArrayEquals(new String[]{ "900", "105Flag.svg" }, mixed.getShapes());
   }

   @Test
   void acceptsAnUploadedShape() {
      StaticShapeModel uploaded = assertInstanceOf(
         StaticShapeModel.class,
         VisualFrameAliases.create("shape", spec("type", "static", "shape", UPLOADED)));
      assertEquals(UPLOADED, uploaded.getShape());
   }
}
