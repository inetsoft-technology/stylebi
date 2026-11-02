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
package inetsoft.web.vswizard.service;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.vswizard.handler.VSWizardBindingHandler;
import inetsoft.web.vswizard.model.recommender.VSTemporaryInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Dimension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #79582: the wizard preview box (fixTempAssemblySize) and the untouched object sizes.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WizardSliderPreviewSizeTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void previewSliderKeepsDensityHeightWhenMarked() {
      assertEquals(new Dimension(350, 82), preview(true, "comfortable"));
      assertEquals(new Dimension(350, 74), preview(true, "compact"));
      assertEquals(new Dimension(350, 66), preview(true, "dense"));
   }

   @Test
   void previewSliderKeepsLegacyHeightWhenUnmarked() {
      assertEquals(new Dimension(350, 50), preview(false, "comfortable"));
   }

   @Test
   void otherWizardObjectSizesAreUnchanged() {
      WizardViewsheetService svc = new WizardViewsheetService(null, null);
      Viewsheet vs = new Viewsheet();
      assertEquals(new Dimension(100, 120),
                   svc.getDefaultAssemblyPixelSize(new SelectionListVSAssembly(vs, "l")));
      assertEquals(new Dimension(100, 120),
                   svc.getDefaultAssemblyPixelSize(new SelectionTreeVSAssembly(vs, "t")));
      assertEquals(new Dimension(300, 200),
                   svc.getDefaultAssemblyPixelSize(new CalendarVSAssembly(vs, "c")));
      assertEquals(new Dimension(100, 40),
                   svc.getDefaultAssemblyPixelSize(new TextVSAssembly(vs, "x")));
   }

   private Dimension preview(boolean marked, String density) {
      SreeEnv.setProperty("viewsheet.density", density);
      TimeSliderVSAssembly assembly = new TimeSliderVSAssembly(new Viewsheet(), "Slider1");
      TimeSliderVSAssemblyInfo info = (TimeSliderVSAssemblyInfo) assembly.getVSAssemblyInfo();
      info.setVizMark(marked ? VizMark.MODERN_LIGHT : null);
      VSWizardTemporaryInfoService tis = mock(VSWizardTemporaryInfoService.class);
      VSTemporaryInfo ti = mock(VSTemporaryInfo.class);
      when(ti.getPreviewPaneSize()).thenReturn(new Dimension(800, 600));
      when(tis.getVSTemporaryInfo(null)).thenReturn(ti);
      new VSWizardBindingHandler(null, null, null, null, null, null, null, null, null, null, null,
                                 tis, null, null).fixTempAssemblySize(info, null);
      return info.getPixelSize();
   }
}
