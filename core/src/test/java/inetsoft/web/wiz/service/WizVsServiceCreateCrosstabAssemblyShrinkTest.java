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
package inetsoft.web.wiz.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CrosstabVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Bug #76316 (symptom UD-003): a chat-generated crosstab rendered with StyleBI's "Shrink to Fit"
 * property off, because createCrosstabAssembly never set it and the server default
 * (TableDataVSAssemblyInfo) is false. Pins the fix: a freshly created crosstab must default to
 * Shrink to Fit = true.
 *
 * <p>Deliberately does not cover the sync path ({@code SyncCrosstabHandler.syncProperties}), which
 * copies {@code isShrink()} from a pre-existing source assembly on later chat-driven modification
 * turns — an existing crosstab created before this fix (shrink=false) keeps that value through
 * sync. That is an accepted, disclosed limitation, not a defect this fix addresses.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WizVsServiceCreateCrosstabAssemblyShrinkTest {
   private static WizVsService newService() throws Exception {
      ViewsheetService vsService = mock(ViewsheetService.class);
      AssetRepository engine = mock(AssetRepository.class);
      SecurityEngine sec = mock(SecurityEngine.class);
      when(sec.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      return new WizVsService(vsService, engine, sec, null, null, null);
   }

   @Test
   void newlyCreatedCrosstabDefaultsToShrinkToFit() throws Exception {
      Viewsheet vs = new Viewsheet();
      WizVsService service = newService();

      Method createCrosstabAssembly = WizVsService.class.getDeclaredMethod(
         "createCrosstabAssembly", Viewsheet.class, String.class,
         inetsoft.web.wiz.model.VisualizationConfig.class);
      createCrosstabAssembly.setAccessible(true);

      CrosstabVSAssembly crosstab =
         (CrosstabVSAssembly) createCrosstabAssembly.invoke(service, vs, "Crosstab1", null);

      assertTrue(((CrosstabVSAssemblyInfo) crosstab.getVSAssemblyInfo()).isShrink(),
                 "chat-generated crosstabs must default to Shrink to Fit = true");
   }
}
