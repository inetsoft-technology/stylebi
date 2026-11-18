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

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.WorksheetService;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.event.TouchAssetEvent;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Bug #81431: the lock owner now covers every node of the cluster, so looking it up asks the other
 * nodes. A touch comes from every open sheet on every heartbeat, and only a sheet that was locked
 * out needs to know whether the lock was released, so the lookup must not run for the others.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TouchAssetServiceLockOwnerTest {
   @Test
   void anEditableSheetDoesNotLookUpTheLockOwner() throws Exception {
      touch(true, "u1");

      verify(worksheetService, never()).getLockOwner(any());
   }

   @Test
   void aSheetThatWasNeverLockedOutDoesNotLookUpTheLockOwner() throws Exception {
      touch(false, null);

      verify(worksheetService, never()).getLockOwner(any());
   }

   @Test
   void aSheetThatWasLockedOutIsToldWhenTheLockIsReleased() throws Exception {
      when(worksheetService.getLockOwner(entry)).thenReturn(null);

      touch(false, "u1");

      verify(worksheetService).getLockOwner(entry);
      ArgumentCaptor<MessageCommand> command = ArgumentCaptor.forClass(MessageCommand.class);
      verify(dispatcher).sendCommand(command.capture());
      assertEquals(MessageCommand.Type.INFO, command.getValue().getType());
      verify(rws).setLockProcessed();
   }

   @Test
   void aSheetThatWasLockedOutIsToldWhenAnotherUserTakesTheLock() throws Exception {
      when(worksheetService.getLockOwner(entry)).thenReturn("u3");

      touch(false, "u1");

      verify(dispatcher).sendCommand(any(MessageCommand.class));
      verify(rws).setLockProcessed();
   }

   @Test
   void aSheetThatWasLockedOutIsNotToldWhileTheSameUserStillHoldsTheLock() throws Exception {
      when(worksheetService.getLockOwner(entry)).thenReturn("u1");

      touch(false, "u1");

      verify(worksheetService).getLockOwner(entry);
      verify(dispatcher, never()).sendCommand(any());
      verify(rws, never()).setLockProcessed();
   }

   private void touch(boolean editable, String lockedOutBy) throws Exception {
      String runtimeId = "rt-touch-1";

      TouchAssetEvent event = mock(TouchAssetEvent.class);
      when(event.design()).thenReturn(true);
      when(event.changed()).thenReturn(false);
      when(event.update()).thenReturn(false);

      when(rws.getID()).thenReturn(runtimeId);
      when(rws.getEntry()).thenReturn(entry);
      when(rws.getLockOwner()).thenReturn(lockedOutBy);
      when(rws.isEditable()).thenReturn(editable);
      when(rws.isRuntime()).thenReturn(false);
      when(rws.isPreview()).thenReturn(false);
      when(rws.isLockProcessed()).thenReturn(false);
      when(worksheetService.getSheet(eq(runtimeId), eq(principal))).thenReturn(rws);

      new TouchAssetService(worksheetService, mock(VSRefreshController.class))
         .touchAsset(runtimeId, event, principal, dispatcher, "");
   }

   private final Principal principal = mock(Principal.class);
   private final CommandDispatcher dispatcher = mock(CommandDispatcher.class);
   private final AssetEntry entry = mock(AssetEntry.class);
   private final RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
   private final WorksheetService worksheetService = mock(WorksheetService.class);
}
