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
package inetsoft.web.viewsheet.controller;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetService;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.ViewsheetInfo;
import inetsoft.web.viewsheet.event.TouchAssetEvent;
import inetsoft.web.viewsheet.event.VSRefreshEvent;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #76674, the shape PR #5252 fixed for {@code ModifyCalculateFieldService}.
 *
 * <p>{@code touchAsset} is a {@code @ClusterProxyKey}-routed method, so it is handed the runtime
 * id it is executing for. Its auto-update branch used to drop that id and call the four-argument
 * {@code VSRefreshController.refreshViewsheet}, which re-derives one from
 * {@code RuntimeViewsheetRef} -- a STOMP-message-scoped bean populated only from a native header
 * on a live browser WebSocket session. A caller reached without one gets null, and the null
 * reaches Ignite's {@code AffinityKey} constructor. The id was in scope the whole time; the
 * private helper simply did not take it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TouchAssetServiceTest {
   /**
    * Both verifies carry weight. The first pins that the id reaching the refresh is this call's
    * own; the second pins that the session-deriving overload is not used at all, which is the
    * only assertion that would fail against the unfixed code -- a test that merely checked "a
    * refresh happened" passes either way, since the session-scoped overload would have been
    * invoked happily on a mock and its null id never examined.
    */
   @Test
   void autoUpdateRefreshesWithTheCallsOwnRuntimeIdNotANullSessionScopedOne() throws Exception {
      String runtimeId = "rt-touch-1";
      Principal principal = mock(Principal.class);
      CommandDispatcher dispatcher = mock(CommandDispatcher.class);

      TouchAssetEvent event = mock(TouchAssetEvent.class);
      when(event.design()).thenReturn(false);
      when(event.changed()).thenReturn(false);
      when(event.update()).thenReturn(true);
      when(event.width()).thenReturn(1024);
      when(event.height()).thenReturn(768);

      ViewsheetInfo vinfo = mock(ViewsheetInfo.class);
      when(vinfo.isUpdateEnabled()).thenReturn(true);

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getViewsheetInfo()).thenReturn(vinfo);

      AssetEntry entry = mock(AssetEntry.class);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getID()).thenReturn(runtimeId);
      when(rvs.getLockOwner()).thenReturn(null);
      when(rvs.getEntry()).thenReturn(entry);
      when(rvs.getOriginalID()).thenReturn(null);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.empty());
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.isRuntime()).thenReturn(true);
      // data changed after the last touch, which is what arms the auto-refresh
      when(rvs.getTouchTimestamp()).thenReturn(1_000L);

      WorksheetService worksheetService = mock(WorksheetService.class);
      when(worksheetService.getSheet(eq(runtimeId), eq(principal))).thenReturn(rvs);
      when(worksheetService.getDataChangedTime(eq(entry))).thenReturn(2_000L);

      VSRefreshController vsRefreshController = mock(VSRefreshController.class);

      TouchAssetService service = new TouchAssetService(worksheetService, vsRefreshController);

      service.touchAsset(runtimeId, event, principal, dispatcher, "");

      verify(vsRefreshController).refreshViewsheet(
         eq(runtimeId), any(VSRefreshEvent.class), eq(principal), eq(dispatcher), eq(""));
      verify(vsRefreshController, never()).refreshViewsheet(
         any(VSRefreshEvent.class), any(), any(), any());
   }
}
