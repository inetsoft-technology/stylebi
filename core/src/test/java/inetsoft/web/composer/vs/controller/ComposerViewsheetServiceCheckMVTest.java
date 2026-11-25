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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.mv.MVManager;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.Organization;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.Catalog;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.event.CheckMVEvent;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Cancelling the MV that a viewsheet waits for ({@link ComposerViewsheetService#checkMV} with a
 * confirmed event): a viewer of a viewsheet whose MV is required gets the missing-MV error and
 * the viewsheet is marked cancelled; without {@code mv.required} the viewer goes on with live
 * data. A designer with {@code mv.metadata} goes on with the metadata only, without it with live
 * data.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ComposerViewsheetServiceCheckMVTest {
   private static final String[] PROPERTIES = { "mv.required", "mv.metadata" };

   @BeforeEach
   void setUp() throws Exception {
      for(String name : PROPERTIES) {
         saved.put(name, SreeEnv.getProperty(name));
         SreeEnv.remove(name);
      }

      user = new XPrincipal(new IdentityID("admin", Organization.getDefaultOrganizationID()));
      entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                             "mvtest/Orders", null);
      vs = new Viewsheet();
      box = mock(ViewsheetSandbox.class);
      when(box.getAssetEntry()).thenReturn(entry);
      rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
      ViewsheetService viewsheets = mock(ViewsheetService.class);
      when(viewsheets.getViewsheet("rid", user)).thenReturn(rvs);
      manager = mock(MVManager.class);
      service = new ComposerViewsheetService(null, null, viewsheets, null, null, null, null,
                                             null, manager, null);
      dispatcher = mock(CommandDispatcher.class);
   }

   @AfterEach
   void tearDown() {
      for(Map.Entry<String, String> prop : saved.entrySet()) {
         if(prop.getValue() == null) {
            SreeEnv.remove(prop.getKey());
         }
         else {
            SreeEnv.setProperty(prop.getKey(), prop.getValue());
         }
      }
   }

   @Test
   void aRequiredMVCancelsTheViewsheetOfAViewer() throws Exception {
      SreeEnv.setProperty("mv.required", "true");
      pending(true);
      when(rvs.isRuntime()).thenReturn(true);

      checkMV();

      verify(manager).cancelMV(entry, user);
      verify(rvs).setProperty("cancelled", "true");
      ArgumentCaptor<MessageCommand> command = ArgumentCaptor.forClass(MessageCommand.class);
      verify(dispatcher).sendCommand(command.capture());
      assertEquals(MessageCommand.Type.ERROR, command.getValue().getType());
      assertEquals(Catalog.getCatalog().getString("vs.mv.missing"),
                   command.getValue().getMessage());
      verify(box, never()).setMVDisabled(anyBoolean());
   }

   @Test
   void withoutRequiredAViewerGoesOnWithLiveData() throws Exception {
      SreeEnv.setProperty("mv.required", "false");
      pending(true);
      when(rvs.isRuntime()).thenReturn(true);

      checkMV();

      verify(manager).cancelMV(entry, user);
      verify(box).setMVDisabled(true);
      verify(rvs, never()).setProperty(eq("cancelled"), any());
      verifyNoInteractions(dispatcher);
   }

   @Test
   void metadataLetsTheDesignerGoOnWithMetadataOnly() throws Exception {
      SreeEnv.setProperty("mv.metadata", "true");
      // mv.required is for viewers only
      SreeEnv.setProperty("mv.required", "true");
      pending(true);
      when(rvs.isRuntime()).thenReturn(false);

      checkMV();

      verify(manager).cancelMV(entry, user);
      assertTrue(vs.getViewsheetInfo().isMetadata());
      verify(box, never()).setMVDisabled(anyBoolean());
      verify(rvs, never()).setProperty(eq("cancelled"), any());
      verifyNoInteractions(dispatcher);
   }

   @Test
   void withoutMetadataTheDesignerGoesOnWithLiveData() throws Exception {
      pending(true);
      when(rvs.isRuntime()).thenReturn(false);

      checkMV();

      verify(manager).cancelMV(entry, user);
      assertFalse(vs.getViewsheetInfo().isMetadata());
      verify(box).setMVDisabled(true);
      verifyNoInteractions(dispatcher);
   }

   @Test
   void nothingIsCancelledWhenNoMVIsPending() throws Exception {
      SreeEnv.setProperty("mv.required", "true");
      pending(false);
      when(rvs.isRuntime()).thenReturn(true);

      checkMV();

      verify(manager, never()).cancelMV(any(), any());
      verify(rvs, never()).setProperty(eq("cancelled"), any());
      verify(box, never()).setMVDisabled(anyBoolean());
      verifyNoInteractions(dispatcher);
   }

   private void pending(boolean pending) {
      when(manager.isPending(entry, user)).thenReturn(pending);
   }

   private void checkMV() throws Exception {
      CheckMVEvent event = new CheckMVEvent();
      event.setEntryId(entry.toIdentifier());
      event.setConfirmed(true);
      service.checkMV("rid", event, user, dispatcher, null);
   }

   private final Map<String, String> saved = new HashMap<>();
   private XPrincipal user;
   private AssetEntry entry;
   private Viewsheet vs;
   private ViewsheetSandbox box;
   private RuntimeViewsheet rvs;
   private MVManager manager;
   private ComposerViewsheetService service;
   private CommandDispatcher dispatcher;
}
