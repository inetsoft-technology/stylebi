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
package inetsoft.report.io.viewsheet.excel;

import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.internal.VSObjectChromeDefaults;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FINDING 4 of the 2026-09-01 seeded-chrome-migration-group1 fix wave.
 *
 * Excel deliberately keeps the legacy near-black selection-cell foreground rather than the value
 * seeded onto the DEFAULT tier at creation (SelectionBaseVSAssemblyInfo.seedChromeDefaults): a
 * spreadsheet cell is unfilled white, and the seeded light neutral would be invisible on it.
 * Before the seed conversion this worked by declining to pass the assembly's real VizContext into
 * VSSelectionListHelper.getValueFormat; now the value already lives on the stored format a bound
 * SelectionValue carries, so ExcelSelectionListHelper (and ExcelSelectionTreeHelper, which shares
 * this method) substitutes the legacy colour back onto a clone of its own instead. This is the
 * one place seeding took a capability away rather than simply moving where a value is written,
 * and the only genuinely new production logic in the whole migration - everything else is a
 * deletion or a seed write.
 *
 * ExcelSelectionListHelper.write/writeList and ExcelSelectionTreeHelper.write/writeTree cannot be
 * driven end to end in this module, for two independent reasons hit while trying: writeList
 * unconditionally builds a real SelectionList (CompositeSelectionValue.setSelectionList falls back
 * to "new SelectionList()" even when handed null), and SelectionList extends XSwappable, whose
 * static initializer reads SreeEnv and throws ShutdownException without a bootstrapped Spring
 * context. writeTree avoids that one, but VSCompositeFormat.getBackground() - called
 * unconditionally on every format it touches, with no tier short-circuit - always consults
 * CSSDictionary, which reaches a live PortalThemesManager Spring bean and throws the same way. And
 * VizContext.of(..) itself, needed to decide "dark" at all, resolves the live density property
 * through the identical SreeEnv path. This module's own PoiExcelVSExporterTest already documents
 * this constraint for VSAssemblyInfo's constructor; every one of the above was hit by actually
 * running it, not assumed.
 *
 * The substitution logic itself needs none of that: given a resolved boolean, it is four lines -
 * clone if dark, overwrite the DEFAULT foreground, return. It was pulled out of both writeList and
 * writeTree into ExcelSelectionListHelper.applyDarkOptOut (package-visible, shared the same way
 * prepareParentBorders already is), taking the resolved flag rather than a VizContext or assembly
 * info so the method itself has no SreeEnv dependency left to route around. Both call sites still
 * resolve VizContext.of(info).dark themselves, exactly as before the extraction - nothing about
 * when or what is written changes.
 */
class ExcelSelectionDarkOptOutTest {
   @Test
   void aDarkContextSubstitutesTheLegacyNearBlackOntoAClone() {
      VSCompositeFormat seeded = new VSCompositeFormat();
      seeded.getDefaultFormat().setForegroundValue(VSObjectChromeDefaults.darkForegroundValue());

      VSCompositeFormat written = ExcelSelectionListHelper.applyDarkOptOut(seeded, true);

      assertEquals(VSObjectChromeDefaults.legacyCellForegroundValue(),
                   written.getDefaultFormat().getForegroundValue(),
                   "Excel must substitute the legacy near-black; its cells are unfilled white and "
                   + "the seeded light neutral would be invisible there");
      assertNotSame(seeded, written,
                    "the substitution clones the stored format rather than mutating the one " +
                    "reached from a FormatInfo, which other viewers still read");
      assertEquals(VSObjectChromeDefaults.darkForegroundValue(),
                   seeded.getDefaultFormat().getForegroundValue(),
                   "the original, uncloned format is untouched");
   }

   @Test
   void aNonDarkContextLeavesTheFormatAlone() {
      // covers both the light-modern and the unmarked/legacy caller: either way the value was
      // never seeded the light neutral to begin with, so there is nothing to opt out of
      VSCompositeFormat legacy = new VSCompositeFormat();
      legacy.getDefaultFormat().setForegroundValue(VSObjectChromeDefaults.legacyCellForegroundValue());

      VSCompositeFormat written = ExcelSelectionListHelper.applyDarkOptOut(legacy, false);

      assertSame(legacy, written, "a non-dark context must not clone or rewrite anything");
   }

   @Test
   void aNullFormatUnderADarkContextGetsTheLegacyForegroundInsteadOfNpe() {
      // writeList's own call site guards against a null format before this runs, but the method
      // has to be safe on its own since writeTree calls it directly against sv.getFormat(), which
      // can be null for a value nothing ever formatted
      VSCompositeFormat written = ExcelSelectionListHelper.applyDarkOptOut(null, true);

      assertNotNull(written);
      assertEquals(VSObjectChromeDefaults.legacyCellForegroundValue(),
                   written.getDefaultFormat().getForegroundValue());
   }

   @Test
   void aNullFormatUnderANonDarkContextStaysNull() {
      assertNull(ExcelSelectionListHelper.applyDarkOptOut(null, false));
   }
}
