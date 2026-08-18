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
package inetsoft.uql.viewsheet.vslayout;

import inetsoft.graph.internal.DimensionD;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The custom page size survives a read/modify/write round trip in every unit.
 *
 * <p>Written to settle a review finding that read the other way: that
 * {@code ViewsheetPropertyDialogService}'s getter takes the custom size in inches while its setter
 * multiplies by {@code getUnitRatio(units)}, so one unrelated property write would divide a custom
 * page size by 25.4 and repeat writes would compound.
 *
 * <p>The asymmetry is not there, and the reason is easy to miss: {@link PrintInfo#getSize()} does
 * the inches-to-current-unit conversion <em>itself</em>, which is why the dialog getter passes a
 * ratio of 1 for the size while passing a real ratio for the margins two lines above. That
 * difference looks exactly like the bug described. These tests pin the round trip so the next
 * reader does not have to re-derive it — and so that "fixing" the getter to match the margins,
 * which would genuinely introduce the reported bug, fails loudly.
 */
@Tag("core")
class PrintInfoSizeUnitsTest {
   /** What the dialog setter does: a value entered in {@code unit} becomes stored inches. */
   private static DimensionD asStored(double width, double height, String unit) {
      double ratio = PrintInfo.getUnitRatio(unit);
      return new DimensionD(width * ratio, height * ratio);
   }

   private static PrintInfo infoWith(DimensionD stored, String unit) {
      PrintInfo info = new PrintInfo();
      info.setUnit(unit);
      info.setSize(stored);
      return info;
   }

   @Test
   void customSizeRoundTripsInMillimetres() {
      PrintInfo info = infoWith(asStored(210, 297, "mm"), "mm");

      // getSize() converts back to the current unit, which is why the dialog displays it as-is.
      assertEquals(210, info.getSize().getWidth(), 1e-9);
      assertEquals(297, info.getSize().getHeight(), 1e-9);
   }

   @Test
   void customSizeRoundTripsInPoints() {
      PrintInfo info = infoWith(asStored(612, 792, "points"), "points");

      assertEquals(612, info.getSize().getWidth(), 1e-9);
      assertEquals(792, info.getSize().getHeight(), 1e-9);
   }

   @Test
   void customSizeRoundTripsInInches() {
      PrintInfo info = infoWith(asStored(8.5, 11, "inches"), "inches");

      assertEquals(8.5, info.getSize().getWidth(), 1e-9);
      assertEquals(11, info.getSize().getHeight(), 1e-9);
   }

   /**
    * The claim underlying the review finding: that repeated writes shrink the page.
    *
    * <p>Ten read/modify/write cycles that touch nothing about the size must leave it identical. If
    * the conversion were one-sided this would be off by 25.4^10.
    */
   @Test
   void repeatedWritesThatDoNotTouchTheSizeLeaveItUnchanged() {
      String unit = "mm";
      PrintInfo info = infoWith(asStored(210, 297, unit), unit);

      for(int i = 0; i < 10; i++) {
         DimensionD displayed = info.getSize();
         info.setSize(asStored(displayed.getWidth(), displayed.getHeight(), unit));
      }

      assertEquals(210, info.getSize().getWidth(), 1e-9);
      assertEquals(297, info.getSize().getHeight(), 1e-9);
   }

   /** Margins convert through the same ratio, in the opposite direction, on the dialog's side. */
   @Test
   void theUnitRatioIsTheSameConstantBothWays() {
      assertEquals(1 / 25.4, PrintInfo.getUnitRatio("mm"), 1e-12);
      assertEquals(1 / 72.0, PrintInfo.getUnitRatio("points"), 1e-12);
      assertEquals(1, PrintInfo.getUnitRatio("inches"), 1e-12);
      assertEquals(1, PrintInfo.getUnitRatio(null), 1e-12, "an unknown unit must not scale");
   }
}
