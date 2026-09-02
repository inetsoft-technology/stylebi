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

import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.internal.VSObjectChromeDefaults;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The inverse of the modern seed, for formats that paint no surface behind their ink. A spreadsheet
 * has no page to paint, so its cells are unfilled white and a seeded light neutral is invisible on
 * them. Tested at the substitution rather than through an exporter, which needs a bootstrapped
 * server - so a mis-wiring at the call site is not caught here. Recorded, not hidden.
 */
@Tag("core")
class ExporterDarkOptOutTest {
   @Test
   void theOptOutPutsTheLegacyInkBackOnTheDefaultTier() {
      VSCompositeFormat format = new VSCompositeFormat();
      format.getDefaultFormat().setForegroundValue("0xcac4d0");

      AbstractVSExporter.applyDarkOptOutInPlace(format);

      assertEquals(VSObjectChromeDefaults.legacyCellForegroundValue(),
                   format.getDefaultFormat().getForegroundValue(),
                   "0xcac4d0 is invisible on an unfilled white cell");
   }

   @Test
   void theOptOutLeavesAUserColourAlone() {
      VSCompositeFormat format = new VSCompositeFormat();
      format.getDefaultFormat().setForegroundValue("0xcac4d0");
      format.getUserDefinedFormat().setForegroundValue("0xff0000");

      AbstractVSExporter.applyDarkOptOutInPlace(format);

      assertEquals("0xff0000", format.getForegroundValue(),
                   "the author's colour outranks both the seed and the opt-out");
   }

   @Test
   void aNullFormatIsTolerated() {
      assertDoesNotThrow(() -> AbstractVSExporter.applyDarkOptOutInPlace(null));
   }

   @Test
   void thePerItemArrayIsSubstitutedIntoCopies() {
      VSCompositeFormat item = new VSCompositeFormat();
      item.getDefaultFormat().setForegroundValue("0xe6e0e9");

      VSCompositeFormat[] copy = AbstractVSExporter.darkOptOutCopy(
         new VSCompositeFormat[]{ item });

      assertEquals(VSObjectChromeDefaults.legacyCellForegroundValue(),
                   copy[0].getDefaultFormat().getForegroundValue(),
                   "a checkbox item's seeded light ink is invisible on an unfilled white cell");
   }

   @Test
   void thePerItemArrayNeverWritesThroughToTheOriginal() {
      VSCompositeFormat item = new VSCompositeFormat();
      item.getDefaultFormat().setForegroundValue("0xe6e0e9");
      VSCompositeFormat[] original = { item };

      VSCompositeFormat[] copy = AbstractVSExporter.darkOptOutCopy(original);

      assertNotSame(original, copy, "a fresh array, not the shared one");
      assertNotSame(original[0], copy[0]);
      assertEquals("0xe6e0e9", item.getDefaultFormat().getForegroundValue(),
                   "an info's shallow clone shares this array, so writing through it would reach "
                      + "the live viewsheet's own formats");
   }

   @Test
   void anEmptyOrNullPerItemArrayIsTolerated() {
      assertDoesNotThrow(() -> AbstractVSExporter.darkOptOutCopy(null));
      assertDoesNotThrow(() -> AbstractVSExporter.darkOptOutCopy(new VSCompositeFormat[0]));
      assertDoesNotThrow(() -> AbstractVSExporter.darkOptOutCopy(new VSCompositeFormat[]{ null }));
   }
}
