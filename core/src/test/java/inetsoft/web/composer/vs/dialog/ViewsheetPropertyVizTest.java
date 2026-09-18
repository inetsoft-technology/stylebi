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
package inetsoft.web.composer.vs.dialog;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.VizMark;
import inetsoft.uql.viewsheet.internal.VizModernizeUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ViewsheetPropertyVizTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aLightSheetReadsAsModernAndNotDark() {
      Viewsheet vs = modernSheet(VizMark.MODERN_LIGHT);

      assertTrue(ViewsheetPropertyDialogService.isVizModern(vs));
      assertFalse(ViewsheetPropertyDialogService.isVizDark(vs));
   }

   @Test
   void aDarkSheetReadsAsBoth() {
      Viewsheet vs = modernSheet(VizMark.MODERN_DARK);

      assertTrue(ViewsheetPropertyDialogService.isVizModern(vs));
      assertTrue(ViewsheetPropertyDialogService.isVizDark(vs));
   }

   @Test
   void aLegacySheetReadsAsNeither() {
      Viewsheet vs = new Viewsheet();
      vs.getVSAssemblyInfo().setVizMark(null);

      assertFalse(ViewsheetPropertyDialogService.isVizModern(vs));
      assertFalse(ViewsheetPropertyDialogService.isVizDark(vs));
   }

   @Test
   void theTargetMarkFollowsTheTwoSwitches() {
      assertNull(ViewsheetPropertyDialogService.targetMark(false, false));
      assertNull(ViewsheetPropertyDialogService.targetMark(false, true),
                 "dark without modern is not a state the mark can hold");
      assertEquals(VizMark.MODERN_LIGHT, ViewsheetPropertyDialogService.targetMark(true, false));
      assertEquals(VizMark.MODERN_DARK, ViewsheetPropertyDialogService.targetMark(true, true));
   }

   @Test
   void applyingTheTargetFlipsAWholeSheet() {
      Viewsheet vs = modernSheet(VizMark.MODERN_LIGHT);

      VizModernizeUtil.applyMark(vs, ViewsheetPropertyDialogService.targetMark(true, true));

      assertEquals(VizMark.MODERN_DARK, vs.getVSAssemblyInfo().getVizMark());

      TextVSAssembly text = (TextVSAssembly) vs.getAssembly("Text1");
      assertEquals(VizMark.MODERN_DARK, text.getVSAssemblyInfo().getVizMark());
   }

   @Test
   void inheritedDensityReceivingEmptyStringIsNotChanged() {
      assertFalse(ViewsheetPropertyDialogService.vizDensityChanged(null, ""),
                  "an inherited dashboard's null density must not read as changed when the " +
                  "model round-trips it as the empty-string sentinel");
   }

   @Test
   void aRealDensityChangeFromInheritedIsDetected() {
      assertTrue(ViewsheetPropertyDialogService.vizDensityChanged(null, "comfortable"));
   }

   @Test
   void aRealDensityChangeToInheritedIsDetected() {
      assertTrue(ViewsheetPropertyDialogService.vizDensityChanged("compact", ""));
   }

   private Viewsheet modernSheet(VizMark mark) {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new TextVSAssembly(vs, "Text1"));
      VizModernizeUtil.applyMark(vs, mark);
      return vs;
   }
}
