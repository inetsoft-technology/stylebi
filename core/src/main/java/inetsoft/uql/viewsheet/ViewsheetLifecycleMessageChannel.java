/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.uql.viewsheet;

import java.util.*;

/**
 * Message channel for viewsheet lifecycle events
 */
public class ViewsheetLifecycleMessageChannel {
   public ViewsheetLifecycleMessageChannel() {
      listeners = new ArrayList<>();
   }

   /**
    * Subscribe to viewsheet lifecycle messages
    *
    * @param listener the event listener to be added
    *
    * @return true as specified by {@link Collection#add}
    */
   public boolean subscribe(ViewsheetLifecycleEventListener listener) {
      synchronized(listeners) {
         return listeners.add(listener);
      }
   }

   /**
    * Unsubscribe from viewsheet lifecycle messages
    *
    * @param listener the event listener to be removed
    *
    * @return true if the listener is in the list of subscribers
    */
   public boolean unsubscribe(ViewsheetLifecycleEventListener listener) {
      synchronized(listeners) {
         return listeners.remove(listener);
      }
   }

   public void executionStarted(String id) {
      sendMessage(id, ViewsheetLifecycleState.EXECUTION_STARTED);
   }

   public void executionCompleted(String id) {
      sendMessage(id, ViewsheetLifecycleState.EXECUTION_COMPLETED);
   }

   public void viewsheetOpened(String id) {
      sendMessage(id, ViewsheetLifecycleState.VIEWSHEET_OPENED);
   }

   public void viewsheetClosed(String id) {
      sendMessage(id, ViewsheetLifecycleState.VIEWSHEET_CLOSED);
   }

   private void sendMessage(String id, ViewsheetLifecycleState state) {
      List<ViewsheetLifecycleEventListener> listeners;

      synchronized(this.listeners){
         listeners = new ArrayList<>(this.listeners);
      }

      for(ViewsheetLifecycleEventListener l : listeners) {
         l.onLifecycleEvent(new ViewsheetLifecycleMessage(this, id, state));
      }
   }

   private final List<ViewsheetLifecycleEventListener> listeners;
}
