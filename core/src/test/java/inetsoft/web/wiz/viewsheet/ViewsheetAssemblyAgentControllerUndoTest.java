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
package inetsoft.web.wiz.viewsheet;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.wiz.pairing.SheetAgentBroadcastService;
import inetsoft.web.wiz.pairing.SheetAgentFeature;
import inetsoft.web.wiz.pairing.SheetType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.lang.reflect.Constructor;
import java.security.Principal;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Bug #81188: the agent undo/redo restores the runtime but the Composer only drops an assembly on
 * RemoveVSObjectCommand, so the controller must hand the pre-undo viewsheet to the broadcast
 * service, and only when the undo/redo was actually applied.
 */
@Tag("core")
class ViewsheetAssemblyAgentControllerUndoTest {
   private final ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
   private final SheetAgentBroadcastService broadcast = mock(SheetAgentBroadcastService.class);
   private final ViewsheetAssemblyAgentController controller = controller();
   private final Principal user = () -> "admin";

   private final Viewsheet current = mock(Viewsheet.class);
   private final Viewsheet snapshot = mock(Viewsheet.class);
   private final RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);

   private void arrange(boolean applied, boolean redo) throws Exception {
      when(current.clone()).thenReturn(snapshot);
      when(rvs.getID()).thenReturn("vs-1");
      when(sessions.resolve("tok", user)).thenReturn(rvs);
      AtomicReference<Viewsheet> live = new AtomicReference<>(current);
      when(rvs.getViewsheet()).thenAnswer(inv -> live.get());
      org.mockito.stubbing.Answer<Boolean> swap = inv -> {
         live.set(mock(Viewsheet.class));
         return applied;
      };

      if(redo) {
         when(rvs.redo(null)).thenAnswer(swap);
      }
      else {
         when(rvs.undo(null)).thenAnswer(swap);
      }
   }

   @Test
   void undoSendsRemovalsFromThePreUndoCloneBeforeTheRefresh() throws Exception {
      arrange(true, false);

      Map<String, Object> result = controller.undo("tok", user);

      assertEquals(true, result.get("undone"));
      InOrder order = inOrder(broadcast);
      order.verify(broadcast).broadcastRemovedAssemblies(rvs, "vs-1", user, snapshot);
      order.verify(broadcast).broadcastRefresh(rvs, SheetType.VIEWSHEET, "vs-1", user);
   }

   @Test
   void redoSendsRemovalsFromThePreRedoClone() throws Exception {
      arrange(true, true);

      Map<String, Object> result = controller.redo("tok", user);

      assertEquals(true, result.get("redone"));
      InOrder order = inOrder(broadcast);
      order.verify(broadcast).broadcastRemovedAssemblies(rvs, "vs-1", user, snapshot);
      order.verify(broadcast).broadcastRefresh(rvs, SheetType.VIEWSHEET, "vs-1", user);
   }

   @Test
   void aNoOpUndoSendsNoRemovals() throws Exception {
      arrange(false, false);

      controller.undo("tok", user);

      verify(broadcast, never()).broadcastRemovedAssemblies(any(), any(), any(), any());
      verify(broadcast).broadcastRefresh(eq(rvs), eq(SheetType.VIEWSHEET), eq("vs-1"), eq(user));
   }

   @Test
   void aNoOpRedoSendsNoRemovals() throws Exception {
      arrange(false, true);

      controller.redo("tok", user);

      verify(broadcast, never()).broadcastRemovedAssemblies(any(), any(), any(), any());
   }

   private ViewsheetAssemblyAgentController controller() {
      try {
         Constructor<?> constructor =
            ViewsheetAssemblyAgentController.class.getConstructors()[0];
         Class<?>[] types = constructor.getParameterTypes();
         Object[] args = new Object[types.length];

         for(int i = 0; i < types.length; i++) {
            if(types[i] == ViewsheetSessionService.class) {
               args[i] = sessions;
            }
            else if(types[i] == SheetAgentBroadcastService.class) {
               args[i] = broadcast;
            }
            else {
               args[i] = mock(types[i]);
            }

            if(types[i] == SheetAgentFeature.class) {
               when(((SheetAgentFeature) args[i]).isEnabled()).thenReturn(true);
            }
         }

         return (ViewsheetAssemblyAgentController) constructor.newInstance(args);
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }
   }
}
