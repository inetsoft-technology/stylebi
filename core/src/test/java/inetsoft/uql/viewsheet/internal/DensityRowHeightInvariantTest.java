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
package inetsoft.uql.viewsheet.internal;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.internal.AssetUtil;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Insets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The stored row-height matrix absorbs the cell padding so that what a reader sees is unchanged
 * from what shipped: 28/24/20 for data rows and 30/26/22 for headers. The stored numbers are an
 * implementation detail of that sum and are not meaningful on their own - this test is the
 * contract, and VSDensityDefaultsTest's matrix assertions are its arithmetic.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DensityRowHeightInvariantTest {
   @Test
   void renderedDataRowHeightsAreUnchanged() {
      assertRenderedDataRow("comfortable", 28);
      assertRenderedDataRow("compact", 24);
      assertRenderedDataRow("dense", 20);
   }

   @Test
   void renderedHeaderRowHeightsAreUnchanged() {
      assertRenderedHeaderRow("comfortable", 30);
      assertRenderedHeaderRow("compact", 26);
      assertRenderedHeaderRow("dense", 22);
   }

   @Test
   void denseRenderedRowStillEqualsTheLegacyDefault() {
      // the anchoring promise: at dense, a marked table's row is the height it always was
      assertRenderedDataRow("dense", AssetUtil.defh);
   }

   @Test
   void selectionCellHeightIsNotDraggedDownByTheRebalance() {
      // the selection family is out of scope and has no additive padding path, so its cell
      // height keeps the pre-rebalance matrix rather than following rowHeightForMode
      assertEquals(28, VSDensityDefaults.selectionCellHeightForMode("comfortable"));
      assertEquals(24, VSDensityDefaults.selectionCellHeightForMode("compact"));
      assertEquals(20, VSDensityDefaults.selectionCellHeightForMode("dense"));
   }

   @Test
   void selectionCellHeightNoLongerTracksTheTableRow() {
      // if these ever converge again, someone has re-merged the two matrices D4 split
      assertNotEquals(VSDensityDefaults.rowHeightForMode("comfortable"),
                      VSDensityDefaults.selectionCellHeightForMode("comfortable"));
   }

   private void assertRenderedDataRow(String mode, int expected) {
      Insets pad = VSDensityDefaults.cellPaddingForMode(mode);
      assertEquals(expected, VSDensityDefaults.rowHeightForMode(mode) + pad.top + pad.bottom,
                   mode + " data row");
   }

   private void assertRenderedHeaderRow(String mode, int expected) {
      Insets pad = VSDensityDefaults.cellPaddingForMode(mode);
      assertEquals(expected, VSDensityDefaults.headerRowHeightForMode(mode) + pad.top + pad.bottom,
                   mode + " header row");
   }
}
