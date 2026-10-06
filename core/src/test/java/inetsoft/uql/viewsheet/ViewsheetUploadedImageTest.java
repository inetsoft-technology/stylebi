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
package inetsoft.uql.viewsheet;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77865: the uploaded image map must never hold a null value, because writeXML()
 * encodes every entry and a null makes every later save fail.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ViewsheetUploadedImageTest {
   @Test
   void addingNullImageStoresNothing() {
      Viewsheet vs = new Viewsheet();
      vs.addUploadedImage("reg.png", null);

      assertDoesNotThrow(() -> write(vs));
      assertDoesNotThrow(() -> write(vs.clone()));
      assertArrayEquals(new String[0], vs.getUploadedImageNames());
      assertNull(vs.getUploadedImageBytes("reg.png"));
   }

   @Test
   void addingNullImageKeepsExistingImage() {
      Viewsheet vs = new Viewsheet();
      byte[] png = { 1, 2, 3 };
      vs.addUploadedImage("reg.png", png);
      vs.addUploadedImage("reg.png", null);

      assertArrayEquals(new String[] { "reg.png" }, vs.getUploadedImageNames());
      assertArrayEquals(png, vs.getUploadedImageBytes("reg.png"));
      assertTrue(write(vs).contains("reg.png"));
   }

   static String write(Viewsheet vs) {
      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      writer.println("<viewsheet>");
      vs.writeXML(writer);
      writer.println("</viewsheet>");
      writer.flush();
      return buffer.toString();
   }
}
