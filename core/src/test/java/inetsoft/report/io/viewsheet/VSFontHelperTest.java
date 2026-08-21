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
package inetsoft.report.io.viewsheet;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/*
 * Bug #75992. The ppt/excel exporters write this family name into the document as the run's
 * typeface. Office expects a family, not a face -- a face name such as "Roboto Bold" (what
 * Font.getFontName() returns) cannot be resolved even when the family is installed, and the
 * viewer then substitutes a font whose metrics differ from the ones the server measured with,
 * re-wrapping text that fit. Java logical families are equally unresolvable and are mapped to
 * families office actually has.
 */
@Tag("core")
class VSFontHelperTest {
   @Test
   void nullFontYieldsNull() {
      assertNull(VSFontHelper.getExportFontFamily(null));
   }

   @Test
   void logicalFamiliesAreMappedToOfficeFamilies() {
      assertEquals("Arial", VSFontHelper.getExportFontFamily(font("Dialog")));
      assertEquals("Arial", VSFontHelper.getExportFontFamily(font("SansSerif")));
      assertEquals("Times New Roman", VSFontHelper.getExportFontFamily(font("Serif")));
      assertEquals("Courier New", VSFontHelper.getExportFontFamily(font("Monospaced")));
      assertEquals("Courier New", VSFontHelper.getExportFontFamily(font("DialogInput")));
   }

   @Test
   void mappingIsCaseInsensitive() {
      assertEquals("Arial", VSFontHelper.getExportFontFamily(font("dialog")));
      assertEquals("Times New Roman", VSFontHelper.getExportFontFamily(font("SERIF")));
   }

   /*
    * The regression this guards: a styled font's face name carries the style suffix, so writing
    * it out produced an unresolvable typeface. The style is written separately by the exporters.
    */
   @Test
   void styledFontYieldsFamilyNotFaceName() {
      Font bold = new Font("Dialog", Font.BOLD, 10);

      assertEquals("Dialog.bold", bold.getFontName());
      assertEquals("Arial", VSFontHelper.getExportFontFamily(bold));
   }

   /*
    * A font the server cannot resolve has already collapsed to a logical family, but the name
    * the user picked survives on the font. Keep it, so a client that does have the font renders
    * like the browser does rather than being pinned to the logical-family fallback.
    */
   @Test
   void unresolvedFontKeepsRequestedName() {
      Font unresolved = new Font("No Such Font " + UNRESOLVED_SUFFIX, Font.PLAIN, 10);

      assumeTrue(isLogicalFamily(unresolved),
                 "font unexpectedly resolved on this platform");
      assertEquals("No Such Font " + UNRESOLVED_SUFFIX,
                   VSFontHelper.getExportFontFamily(unresolved));
   }

   private static boolean isLogicalFamily(Font font) {
      return "Dialog".equalsIgnoreCase(font.getFamily())
         || "DialogInput".equalsIgnoreCase(font.getFamily());
   }

   private static final String UNRESOLVED_SUFFIX = "Zzq";

   private static Font font(String name) {
      return new Font(name, Font.PLAIN, 10);
   }
}
