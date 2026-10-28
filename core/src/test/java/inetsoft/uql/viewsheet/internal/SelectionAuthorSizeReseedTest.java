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

import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Dimension;
import java.awt.Insets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79565: re-seed, Revert, user resize and dropdown behaviour of the author-size inset rule.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SelectionAuthorSizeReseedTest {
   @AfterEach void reset() { SreeEnv.setProperty("viewsheet.density", null); }

   private SelectionListVSAssemblyInfo census(String d) {
      SreeEnv.setProperty("viewsheet.density", d);
      SelectionListVSAssemblyInfo i = new SelectionListVSAssemblyInfo();
      i.setPixelSize(new Dimension(344, 88)); i.setCellHeight(31); i.setTitleHeight(18);
      i.setVizMark(VizMark.MODERN_LIGHT);
      i.seedChromeDefaults(VizContext.of(i));
      return i;
   }

   @Test void idempotentReseed() {
      SelectionListVSAssemblyInfo i = census("compact");
      Insets p = i.getPadding(); Dimension s = i.getPixelSize();
      for(int k = 0; k < 3; k++) i.seedChromeDefaults(VizContext.of(i));
      assertEquals(p, i.getPadding()); assertEquals(s, i.getPixelSize());
      assertEquals(new Insets(0, 12, 0, 12), p);
   }

   @Test void defaultSizeIdempotentAndDensityChange() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      SelectionListVSAssemblyInfo i = new SelectionListVSAssemblyInfo();
      i.setVizMark(VizMark.MODERN_LIGHT);
      i.seedChromeDefaults(VizContext.of(i));
      Dimension s = i.getPixelSize(); Insets p = i.getPadding();
      i.seedChromeDefaults(VizContext.of(i));
      assertEquals(s, i.getPixelSize()); assertEquals(p, i.getPadding());
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      i.seedChromeDefaults(VizContext.of(i));
      assertEquals(new Insets(16, 16, 16, 16), i.getPadding());
      assertEquals(VSDensityDefaults.selectionSize(VizContext.of(i)), i.getPixelSize());
   }

   @Test void defaultSizeRevertRestoresExactly() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      SelectionListVSAssemblyInfo i = new SelectionListVSAssemblyInfo();
      Dimension s0 = i.getPixelSize(); Insets p0 = (Insets) i.getPadding().clone();
      i.setVizMark(VizMark.MODERN_LIGHT);
      i.seedChromeDefaults(VizContext.of(i));
      assertNotEquals(s0, i.getPixelSize());
      i.setVizMark(null);
      i.seedChromeDefaults(VizContext.ofTransition(null, null));
      assertEquals(s0, i.getPixelSize()); assertEquals(p0, i.getPadding());
   }

   @Test void authorSizedModernWithOldVerticalInsetIsRewritten() {
      SelectionListVSAssemblyInfo i = census("compact");
      i.setPadding(new Insets(12, 12, 12, 12)); // as saved by the pre-fix build
      i.seedChromeDefaults(VizContext.of(i));
      assertEquals(new Insets(0, 12, 0, 12), i.getPadding());
   }

   @Test void userResizedDefaultListLosesVerticalInset() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      SelectionListVSAssemblyInfo i = new SelectionListVSAssemblyInfo();
      i.setVizMark(VizMark.MODERN_LIGHT);
      i.seedChromeDefaults(VizContext.of(i));
      i.setPixelSize(new Dimension(300, 200)); i.setUserSize(true);
      i.seedChromeDefaults(VizContext.of(i));
      assertEquals(new Insets(0, 12, 0, 12), i.getPadding());
      assertEquals(new Dimension(300, 200), i.getPixelSize());
   }

   @Test void dropdownAuthorSized() {
      SelectionListVSAssemblyInfo i = new SelectionListVSAssemblyInfo();
      i.setShowType(SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
      i.setPixelSize(new Dimension(344, 30));
      SreeEnv.setProperty("viewsheet.density", "compact");
      i.setVizMark(VizMark.MODERN_LIGHT);
      i.seedChromeDefaults(VizContext.of(i));
      assertEquals(new Insets(0, 12, 0, 12), i.getPadding());
      assertEquals(new Dimension(344, 30), i.getPixelSize());
   }

   @Test void unmarkedOpenKeepsLegacy() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      SelectionListVSAssemblyInfo i = new SelectionListVSAssemblyInfo();
      i.setPixelSize(new Dimension(344, 88));
      i.seedChromeDefaults(VizContext.of(i));
      assertEquals(new Insets(0, 0, 0, 0), i.getPadding());
   }
}
