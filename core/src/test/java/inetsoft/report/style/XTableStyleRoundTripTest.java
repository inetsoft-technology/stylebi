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
package inetsoft.report.style;

import inetsoft.report.lens.DefaultTableLens;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #81209: id, name, createdBy and modifiedBy must survive export and re-read unchanged.
 */
@Tag("core")
class XTableStyleRoundTripTest {
   @Test
   void specialCharactersRoundTrip() throws Exception {
      String special = "R&D <Blue> \"x\" 'y'";
      XTableStyle style = new XTableStyle(new DefaultTableLens(1, 1));
      style.setID(special + " id");
      style.setName(special);
      style.setCreatedBy("O'Brien & <Co>");
      style.setLastModifiedBy("\"Mod\" & é");

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      style.export(out);

      XTableStyle read = XTableStyle.getXTableStyle(
         new DefaultTableLens(1, 1), new ByteArrayInputStream(out.toByteArray()));

      assertEquals(style.getID(), read.getID());
      assertEquals(style.getName(), read.getName());
      assertEquals(style.getCreatedBy(), read.getCreatedBy());
      assertEquals(style.getLastModifiedBy(), read.getLastModifiedBy());
   }
}
