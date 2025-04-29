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

package inetsoft.report.script.viewsheet;

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.viewsheet.ImageVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.ImageVSAssemblyInfo;

import inetsoft.uql.viewsheet.internal.PopVSAssemblyInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import java.awt.*;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;

public class ImageVSAScriptableTest {
   private ViewsheetSandbox viewsheetSandbox ;
   private ImageVSAScriptable imageVSAScriptable;
   private ImageVSAssemblyInfo imageVSAssemblyInfo;
   private ImageVSAssembly imageVSAssembly;
   private VSAScriptable vsaScriptable;

   @BeforeEach
   void setUp() {
      openMocks(this);
      Viewsheet viewsheet = new Viewsheet();
      viewsheet.getVSAssemblyInfo().setName("vs1");

      imageVSAssembly = new ImageVSAssembly();
      imageVSAssemblyInfo = (ImageVSAssemblyInfo) imageVSAssembly.getVSAssemblyInfo();
      imageVSAssemblyInfo.setName("Image1");
      viewsheet.addAssembly(imageVSAssembly);

      viewsheetSandbox = mock(ViewsheetSandbox.class);
      when(viewsheetSandbox.getID()).thenReturn("vs1");
      when(viewsheetSandbox.getViewsheet()).thenReturn(viewsheet);

      imageVSAScriptable = new ImageVSAScriptable(viewsheetSandbox);
      vsaScriptable = new VSAScriptable(viewsheetSandbox);
      imageVSAScriptable.setAssembly("Image1");
      vsaScriptable.setAssembly("Image1");
   }

   @Test
   void testGetClassName() {
      assertEquals("ImageVSA", imageVSAScriptable.getClassName());
   }

   @ParameterizedTest
   @ValueSource(strings = {"maintainAspectRatio", "scaleImage", "animate", "tile"})
   void testAddProperties(String propertyName) {
      imageVSAScriptable.addProperties();
      assert imageVSAScriptable.get(propertyName, imageVSAScriptable) instanceof Boolean;
   }

   @ParameterizedTest
   @CsvSource({
      "maintainAspectRatio, false, false",
      "scaleImage, true, true",
      "animate, true, true",
      "tile, true, true"
   })
   void testSetProperty(String propertyName, Object propertyValue, Object expectedValue) {
      imageVSAScriptable.setProperty(propertyName, propertyValue);
      assertEquals(expectedValue, imageVSAScriptable.get(propertyName, imageVSAScriptable));
   }

   @Test
   void testGetSuffix() {
      assertEquals("[]", imageVSAScriptable.getSuffix("scale9"));
      assertEquals("", imageVSAScriptable.getSuffix("highlighted"));
   }

   @Test
   void testGetSetPopComponent() {
      imageVSAScriptable.setPopComponent("testComponent");
      assertEquals("testComponent", imageVSAScriptable.getPopComponent());

      imageVSAScriptable.setPopComponentValue("testValue");
      assertEquals("testValue", imageVSAScriptable.getPopComponent());
   }

   @Test
   void testGetSetPopLocation() {
      imageVSAScriptable.setPopLocation("CENTER");
      assertEquals(PopVSAssemblyInfo.PopLocation.CENTER.value, imageVSAScriptable.getPopLocation());
      imageVSAScriptable.setPopLocation("MOUSE");
      assertEquals(PopVSAssemblyInfo.PopLocation.MOUSE.value, imageVSAScriptable.getPopLocation());

      //invalid position
      RuntimeException runtimeException = assertThrows(RuntimeException.class, () -> {
         imageVSAScriptable.setPopLocation("test");
      });
      assertEquals("Invalid PopLocation", runtimeException.getMessage());
   }

   @Test
   void testGetSetImage() throws Exception {
      // Test with null image
      imageVSAScriptable.setImage(null);
      assertNull(imageVSAScriptable.getImage());
      imageVSAScriptable.setImageValue(null);
      assertNull(imageVSAScriptable.getImage());

      imageVSAScriptable.setImage("testImage");
      assertEquals("testImage", imageVSAScriptable.getImage());

      imageVSAScriptable.setImageValue("testValue");
      assertEquals("testValue", imageVSAScriptable.getImage());

      // Test with an actual image
      URL url = new URL("https://www.inetsoft.com/images/website/homepage/dataPipeline-1.png");
      Image image = ImageIO.read(url);

      // Ensure the image is not null
      assertNotNull(image, "ImageIO.read(url) returned null. Ensure the URL is valid and accessible.");

      imageVSAScriptable.setImage(image);
      assertEquals(image, imageVSAScriptable.getImage());
   }
}