/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.util.audit;

import inetsoft.util.SingletonManager;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ExecutionRecordDispatcher {

   public static ExecutionRecordDispatcher getInstance() {
      return SingletonManager.getInstance(ExecutionRecordDispatcher.class);
   }

   public void dispatchRecord(ExecutionRecord record) {
      ExecutionRecordEvent event = null;

      for(ExecutionRecordListener l : listeners) {
         if(event == null) {
            event = new ExecutionRecordEvent(this, record);
         }

         l.recordInserted(event);
      }
   }

   public void addListener(ExecutionRecordListener l) {
      listeners.add(l);
   }

   public void removeListener(ExecutionRecordListener l) {
      listeners.remove(l);
   }

   private final List<ExecutionRecordListener> listeners = new CopyOnWriteArrayList<>();

   public static final class ExecutionRecordEvent extends EventObject {
      public ExecutionRecordEvent(Object source, ExecutionRecord record) {
         super(source);
         this.record = record;
      }

      public ExecutionRecord getRecord() {
         return record;
      }

      private final ExecutionRecord record;
   }

   public interface ExecutionRecordListener extends EventListener {
      void recordInserted(ExecutionRecordEvent event);
   }
}
