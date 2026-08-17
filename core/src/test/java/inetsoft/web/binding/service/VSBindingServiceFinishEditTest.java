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
package inetsoft.web.binding.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.analytic.AnalyticAssistant;
import inetsoft.util.MessageException;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.composer.vs.objects.controller.GroupingService;
import inetsoft.web.composer.vs.objects.controller.VSTableService;
import inetsoft.web.composer.vs.objects.controller.VSTrapService;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import inetsoft.web.viewsheet.service.VSSelectionContainerService;
import inetsoft.web.vswizard.service.VSWizardTemporaryInfoService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Write coordination (2026-08-17-write-coordination-design.md / -implementation.md): the
 * binding editor's {@code finishEdit} replaces the ENTIRE live viewsheet on the base runtime,
 * not just the assembly being bound -- the largest blast radius of any write path in the
 * Composer. This is the highest-value regression test in the whole write-coordination effort:
 * it is the one case no document had identified as whole-viewsheet before the design spec.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Tag("core")
class VSBindingServiceFinishEditTest {
   @BeforeEach
   void setup() {
      service = new VSBindingService(trapService, vsTableService, groupingService,
         viewsheetService, Collections.emptyList(), dataRefService, objectModelService,
         wizardTemporaryInfoService, vsSelectionContainerService, analyticAssistant,
         assemblyHandler, vsObjectTreeService, coreLifecycleService);
   }

   @Test
   void refusesToReplaceTheBaseViewsheetWhenItChangedDuringEditing() throws Exception {
      when(viewsheetService.getViewsheet("nid1", null)).thenReturn(nrvs);
      when(nrvs.getOriginalID()).thenReturn("oid1");
      when(viewsheetService.getViewsheet("oid1", null)).thenReturn(orvs);
      when(orvs.getEntry()).thenReturn(entry);

      // The base viewsheet accepted a concurrent write (any write tool, any assembly) after
      // the binding editor opened -- its revision moved from what nrvs captured at open time.
      when(nrvs.getBaseWriteRevisionAtOpen()).thenReturn(4);
      when(orvs.getWriteRevision()).thenReturn(5);

      Assertions.assertThrows(MessageException.class, () ->
         service.finishEdit(viewsheetService, "nid1", "Chart1", null, null, null));

      verify(orvs, never()).setViewsheet(any());
   }

   @Test
   void proceedsWhenTheBaseRevisionIsUnchanged() throws Exception {
      when(viewsheetService.getViewsheet("nid1", null)).thenReturn(nrvs);
      when(nrvs.getOriginalID()).thenReturn("oid1");
      when(viewsheetService.getViewsheet("oid1", null)).thenReturn(orvs);
      when(orvs.getEntry()).thenReturn(entry);
      when(nrvs.getBaseWriteRevisionAtOpen()).thenReturn(5);
      when(orvs.getWriteRevision()).thenReturn(5);

      // Bail out cleanly right after the revision check, the same way an empty sandbox does
      // elsewhere in this codebase -- proves reachability without mocking the rest of a
      // whole-viewsheet commit.
      when(orvs.getViewsheetSandbox()).thenThrow(new RuntimeException("reached past the check"));
      when(nrvs.getViewsheetSandbox()).thenReturn(java.util.Optional.empty());

      RuntimeException thrown = Assertions.assertThrows(RuntimeException.class, () ->
         service.finishEdit(viewsheetService, "nid1", "Chart1", null, null, null));

      Assertions.assertEquals("reached past the check", thrown.getMessage());
   }

   @Mock VSTrapService trapService;
   @Mock VSTableService vsTableService;
   @Mock GroupingService groupingService;
   @Mock ViewsheetService viewsheetService;
   @Mock DataRefModelFactoryService dataRefService;
   @Mock VSObjectModelFactoryService objectModelService;
   @Mock VSWizardTemporaryInfoService wizardTemporaryInfoService;
   @Mock VSSelectionContainerService vsSelectionContainerService;
   @Mock AnalyticAssistant analyticAssistant;
   @Mock VSAssemblyInfoHandler assemblyHandler;
   @Mock VSObjectTreeService vsObjectTreeService;
   @Mock CoreLifecycleService coreLifecycleService;
   @Mock RuntimeViewsheet nrvs;
   @Mock RuntimeViewsheet orvs;
   @Mock AssetEntry entry;
   @Mock Viewsheet viewsheet;

   private VSBindingService service;
}
