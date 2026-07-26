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
package inetsoft.analytic.composition;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.Viewsheet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ViewsheetEngineWizLayoutTest {
   // ViewsheetLayout.parseAttributes() hardcodes scaleToScreen/fitToWidth back to true on
   // every reload of a persisted device-based layout (deliberate, documented product decision
   // -- see the comment in that method). ViewsheetEngine.createRuntimeSheet() applies the
   // matched tier's layout and then calls enforceWizFixedSizeLayout(), which must re-disable
   // both flags for a wiz-composed dashboard (Viewsheet.WizInfo#isWizSheet() == true), so the
   // dashboard's fixed-size, internally-scrollable chart tiles survive the reload.
   @Test
   void reDisablesScaleToScreenAndFitToWidthForAWizSheet() {
      Viewsheet viewsheet = new Viewsheet();
      viewsheet.setWizInfo(new Viewsheet.WizInfo(true));
      viewsheet.getViewsheetInfo().setScaleToScreen(true);
      viewsheet.getViewsheetInfo().setFitToWidth(true);

      ViewsheetEngine.enforceWizFixedSizeLayout(viewsheet);

      assertFalse(viewsheet.getViewsheetInfo().isScaleToScreen());
      assertFalse(viewsheet.getViewsheetInfo().isFitToWidth());
   }

   // Ordinary (non-wiz) device-based viewsheets must keep StyleBI's documented shared
   // behavior untouched -- the override is scoped to wiz-composed dashboards only.
   @Test
   void leavesScaleToScreenAndFitToWidthAloneForANonWizSheet() {
      Viewsheet viewsheet = new Viewsheet();
      viewsheet.getViewsheetInfo().setScaleToScreen(true);
      viewsheet.getViewsheetInfo().setFitToWidth(true);

      ViewsheetEngine.enforceWizFixedSizeLayout(viewsheet);

      assertTrue(viewsheet.getViewsheetInfo().isScaleToScreen());
      assertTrue(viewsheet.getViewsheetInfo().isFitToWidth());
   }
}
