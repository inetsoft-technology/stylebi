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
import inetsoft.sree.SreeEnv;
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
import org.mockito.ArgumentCaptor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Server-side update (ViewsheetInfo updateEnabled + touchInterval). With assetMonitor.enabled
 * false (the default), every update tick must refresh the viewsheet. The refresh used to be
 * gated on a data-change time that nothing records (ViewsheetEngine.dataChanged() has no
 * callers), so getDataChangedTime() is always 0 in a running server and auto-refresh never
 * fired. With assetMonitor.enabled true, a tick refreshes only when a data change newer than
 * the last touch has been recorded.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TouchAssetServiceTest {
   @Test
   void updateTickRefreshesWhenNoDataChangeTimeIsRecorded() throws Exception {
      touch(true, true);

      verifyAutoRefresh();
   }

   @Test
   void plainTouchDoesNotRefresh() throws Exception {
      touch(false, true);

      verify(vsRefreshController, never()).refreshViewsheet(any(), any(), any(), any());
   }

   @Test
   void updateTickDoesNotRefreshWhenServerSideUpdateIsDisabled() throws Exception {
      touch(true, false);

      verify(vsRefreshController, never()).refreshViewsheet(any(), any(), any(), any());
   }

   @Test
   void monitorEnabledUpdateTickDoesNotRefreshWithoutDataChange() throws Exception {
      withAssetMonitorEnabled(() -> touch(true, true, 0L));

      verify(vsRefreshController, never()).refreshViewsheet(any(), any(), any(), any());
   }

   @Test
   void monitorEnabledUpdateTickDoesNotRefreshWhenDataChangeIsOlderThanTouch() throws Exception {
      withAssetMonitorEnabled(() -> touch(true, true, 500L));

      verify(vsRefreshController, never()).refreshViewsheet(any(), any(), any(), any());
   }

   @Test
   void monitorEnabledUpdateTickRefreshesWhenDataChangedSinceLastTouch() throws Exception {
      withAssetMonitorEnabled(() -> touch(true, true, 2_000L));

      verifyAutoRefresh();
   }

   private void verifyAutoRefresh() throws Exception {
      ArgumentCaptor<VSRefreshEvent> captor = ArgumentCaptor.forClass(VSRefreshEvent.class);
      verify(vsRefreshController).refreshViewsheet(
         captor.capture(), eq(principal), eq(dispatcher), eq(""));
      assertTrue(captor.getValue().autoRefresh());
   }

   private void withAssetMonitorEnabled(ThrowingRunnable action) throws Exception {
      SreeEnv.setProperty(ASSET_MONITOR_ENABLED, "true");

      try {
         action.run();
      }
      finally {
         SreeEnv.remove(ASSET_MONITOR_ENABLED);
      }
   }

   private void touch(boolean update, boolean updateEnabled) throws Exception {
      // what a running server reports: no data-change time is ever recorded
      touch(update, updateEnabled, 0L);
   }

   private void touch(boolean update, boolean updateEnabled, long changeTime) throws Exception {
      String runtimeId = "rt-touch-1";

      TouchAssetEvent event = mock(TouchAssetEvent.class);
      when(event.design()).thenReturn(false);
      when(event.changed()).thenReturn(false);
      when(event.update()).thenReturn(update);
      when(event.width()).thenReturn(1024);
      when(event.height()).thenReturn(768);

      ViewsheetInfo vinfo = mock(ViewsheetInfo.class);
      when(vinfo.isUpdateEnabled()).thenReturn(updateEnabled);

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
      when(rvs.getTouchTimestamp()).thenReturn(1_000L);

      WorksheetService worksheetService = mock(WorksheetService.class);
      when(worksheetService.getSheet(eq(runtimeId), eq(principal))).thenReturn(rvs);
      when(worksheetService.getDataChangedTime(any())).thenReturn(changeTime);

      new TouchAssetService(worksheetService, vsRefreshController)
         .touchAsset(runtimeId, event, principal, dispatcher, "");
   }

   @FunctionalInterface
   private interface ThrowingRunnable {
      void run() throws Exception;
   }

   private static final String ASSET_MONITOR_ENABLED = "assetMonitor.enabled";
   private final Principal principal = mock(Principal.class);
   private final CommandDispatcher dispatcher = mock(CommandDispatcher.class);
   private final VSRefreshController vsRefreshController = mock(VSRefreshController.class);
}
