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
package inetsoft.report.pdf;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/** Bug #80975: getFontNames must keep the first letter of FontName. */
@Tag("core")
class AFMFontInfoNamesTest {
   private static String[] names(String afm) throws Exception {
      return AFMFontInfo.getFontNames(
         new ByteArrayInputStream(afm.getBytes(StandardCharsets.ISO_8859_1)));
   }

   @Test
   void fontNameKeepsFirstLetter() throws Exception {
      String[] n = names("StartFontMetrics 4.1\nFontName SynthNameOnly\nFullName Synth Name Only\n");
      assertArrayEquals(new String[] {"SynthNameOnly", "Synth Name Only"}, n);
   }

   @Test
   void crlfAndFullNameFirst() throws Exception {
      String[] n = names("FullName Synth Full\r\nFontName SynthNameOnly\r\n");
      assertArrayEquals(new String[] {"SynthNameOnly", "Synth Full"}, n);
   }
}
