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
package inetsoft.web.composer.vs.controller;

import inetsoft.analytic.composition.ViewsheetEngine;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VizMark;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Tag("core")
class ModernizeViewsheetServiceTest {
   @BeforeEach
   void setup() throws Exception {
      service = new ModernizeViewsheetService(viewsheetEngine, coreLifecycleService,
                                             assetRepository);
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      viewsheet = new Viewsheet();
      viewsheet.getVSAssemblyInfo().setVizMark(null);
      viewsheet.addAssembly(new TextVSAssembly(viewsheet, "Text1"));
      viewsheet.getAssembly("Text1").getVSAssemblyInfo().setVizMark(null);

      when(viewsheetEngine.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(rvs.getEntry()).thenReturn(entry);
      when(rvs.getID()).thenReturn("rid");
   }

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
   }

   @Test
   void modernizeStampsAndRefreshes() throws Exception {
      service.modernize("rid", principal, dispatcher, "uri");

      assertEquals(VizMark.MODERN_LIGHT, viewsheet.getVSAssemblyInfo().getVizMark());
      assertEquals(VizMark.MODERN_LIGHT,
                   viewsheet.getAssembly("Text1").getVSAssemblyInfo().getVizMark());
      verify(assetRepository).checkAssetPermission(eq(principal), eq(entry), any());
      verify(coreLifecycleService).setViewsheetInfo(eq(rvs), eq("uri"), eq(dispatcher));
      verify(coreLifecycleService).refreshViewsheet(eq(rvs), eq("rid"), eq("uri"), eq(dispatcher),
                                                   eq(false), eq(false), eq(true), any());
   }

   @Test
   void modernizeDoesNothingWithTheGateOff() throws Exception {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");

      service.modernize("rid", principal, dispatcher, "uri");

      assertNull(viewsheet.getVSAssemblyInfo().getVizMark());
      verify(coreLifecycleService, never())
         .refreshViewsheet(any(), anyString(), anyString(), any(), anyBoolean(), anyBoolean(),
                           anyBoolean(), any());
   }

   @Test
   void modernizeRefusesWithoutWritePermission() throws Exception {
      doThrow(new SecurityException("denied"))
         .when(assetRepository).checkAssetPermission(any(), any(), any());

      assertThrows(SecurityException.class,
                   () -> service.modernize("rid", principal, dispatcher, "uri"));
      assertNull(viewsheet.getVSAssemblyInfo().getVizMark(),
                 "the permission check precedes any write");
   }

   @Test
   void modernizeDispatchesViewsheetInfoOnPermissionDenial() throws Exception {
      doThrow(new SecurityException("denied"))
         .when(assetRepository).checkAssetPermission(any(), any(), any());

      assertThrows(SecurityException.class,
                   () -> service.modernize("rid", principal, dispatcher, "uri"));
      verify(coreLifecycleService).setViewsheetInfo(eq(rvs), eq("uri"), eq(dispatcher));
   }

   @Test
   void modernizeDispatchesViewsheetInfoOnGateOffNoOp() throws Exception {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");

      service.modernize("rid", principal, dispatcher, "uri");

      verify(coreLifecycleService).setViewsheetInfo(eq(rvs), eq("uri"), eq(dispatcher));
      verify(coreLifecycleService, never())
         .refreshViewsheet(any(), anyString(), anyString(), any(), anyBoolean(), anyBoolean(),
                           anyBoolean(), any());
   }

   private ModernizeViewsheetService service;
   private Viewsheet viewsheet;
   @Mock private ViewsheetEngine viewsheetEngine;
   @Mock private CoreLifecycleService coreLifecycleService;
   @Mock private AssetRepository assetRepository;
   @Mock private RuntimeViewsheet rvs;
   @Mock private AssetEntry entry;
   @Mock private Principal principal;
   @Mock private CommandDispatcher dispatcher;
}
