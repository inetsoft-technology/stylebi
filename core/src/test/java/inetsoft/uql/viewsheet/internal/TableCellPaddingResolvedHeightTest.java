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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Joins {@link TableCellPaddingSeedTest}'s seed half to VSTableLens.getRowPadding's render half:
 * a marked table's seeded padding, added to the density's stored row height, must equal the
 * shipped rendered height - "seeded, not resolved at render" is the feature's headline promise,
 * and nothing else in the branch proves the two halves are joined.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TableCellPaddingResolvedHeightTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void seededPaddingRendersTheShippedRowHeightAtEveryTier() {
      assertRenderedHeight("comfortable", 28);
      assertRenderedHeight("compact", 24);
      assertRenderedHeight("dense", 20);
   }

   private void assertRenderedHeight(String density, int expected) {
      SreeEnv.setProperty("viewsheet.density", density);
      VizContext ctx = VizContext.of(VizMark.MODERN_LIGHT);
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.seedChromeDefaults(ctx);
      VSTableLens lens = new VSTableLens(XTableUtil.getDefaultTableLens());

      int rendered = VSDensityDefaults.rowHeight(ctx) + lens.getRowPadding(1, info);

      assertEquals(expected, rendered, density);
   }
}
