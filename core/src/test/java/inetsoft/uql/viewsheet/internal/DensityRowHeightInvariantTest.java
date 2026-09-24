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

import inetsoft.report.composition.VSTableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.XTableUtil;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.asset.internal.AssetUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Insets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a reader sees is the density matrix - 28/24/20 for data rows and 30/26/22 for headers -
 * whatever padding the table carries. A table stores the rendered height less its seeded cell
 * padding and render adds the padding back, so the sum holds even when the padding was seeded
 * under a tier other than the one in force: an org density change reseeds nothing.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DensityRowHeightInvariantTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void renderedRowHeightsMatchTheMatrixAtEveryTier() {
      assertRendered("comfortable", "comfortable", 28, 30);
      assertRendered("compact", "compact", 24, 26);
      assertRendered("dense", "dense", 20, 22);
   }

   @Test
   void denseRenderedRowStillEqualsTheLegacyDefault() {
      // the anchoring promise: at dense, a marked table's row is the height it always was
      assertRendered("dense", "dense", AssetUtil.defh, 22);
   }

   @Test
   void paddingSeededUnderAnotherTierRendersTheTierInForce() {
      assertRendered("compact", "dense", 20, 22);
      assertRendered("compact", "comfortable", 28, 30);
      assertRendered("dense", "comfortable", 28, 30);
      assertRendered("comfortable", "dense", 20, 22);
   }

   @Test
   void aTableWithNoSeededPaddingRendersTheMatrix() {
      // an asset saved before the field existed carries no padding at all
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.setCellPadding(null, CompositeValue.Type.DEFAULT);

      assertEquals(24, renderedDataRow(info, "compact"));
   }

   @Test
   void anAuthorPaddingAddsToTheRenderedHeight() {
      // additive, as a stylesheet's padding is: 24 - 8 seeded + 20 authored
      TableVSAssemblyInfo info = seededAt("compact");
      info.setCellPadding(new Insets(10, 6, 10, 6), CompositeValue.Type.USER);

      assertEquals(36, renderedDataRow(info, "compact"));
   }

   private void assertRendered(String seedMode, String renderMode, int data, int header) {
      TableVSAssemblyInfo info = seededAt(seedMode);
      VizContext ctx = contextAt(renderMode);
      VSTableLens lens = new VSTableLens(XTableUtil.getDefaultTableLens());
      String label = seedMode + " seeded, " + renderMode + " rendered";

      assertEquals(data, VSDensityDefaults.rowHeight(ctx, info) + lens.getRowPadding(1, info),
                   label + " data row");
      assertEquals(header,
                   VSDensityDefaults.headerRowHeight(ctx, info) + lens.getRowPadding(0, info),
                   label + " header row");
   }

   private int renderedDataRow(TableVSAssemblyInfo info, String renderMode) {
      VizContext ctx = contextAt(renderMode);
      VSTableLens lens = new VSTableLens(XTableUtil.getDefaultTableLens());
      return VSDensityDefaults.rowHeight(ctx, info) + lens.getRowPadding(1, info);
   }

   private TableVSAssemblyInfo seededAt(String mode) {
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.seedChromeDefaults(contextAt(mode));
      return info;
   }

   private VizContext contextAt(String mode) {
      SreeEnv.setProperty("viewsheet.density", mode);
      return VizContext.of(VizMark.MODERN_LIGHT);
   }
}
