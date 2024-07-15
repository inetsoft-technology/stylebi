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
package inetsoft.report.composition;

import inetsoft.mv.MVSession;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.internal.WSExecution;
import inetsoft.util.GroupedThread;
import inetsoft.util.Tool;
import inetsoft.util.log.LogContext;

/**
 * Worksheet event, the <tt>AssetEvent</tt> requires a worksheet as the context.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public abstract class WorksheetEvent extends GridEvent {
   /**
    * Constructor.
    */
   public WorksheetEvent() {
      super();
   }

   /**
    * Get the name of the sheet container.
    * @return the name of the sheet container.
    */
   @Override
   public String getSheetName() {
      String name = null;

      try {
         RuntimeSheet runtime =
            getWorksheetEngine().getWorksheet(getID(), getUser());
         AssetEntry entry = runtime == null ? null : runtime.getEntry();
         name = entry == null ? null : entry.getSheetName();
      }
      catch(Exception ex) {
      }

      return name;
   }

   /**
    * Check if is undoable/redoable.
    * @return <tt>true</tt> if undoable/redoable.
    */
   @Override
   public boolean isUndoable() {
      return true;
   }

   /**
    * Check if equals another object.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals, <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      String id = getID();
      WorksheetEvent event2 = (WorksheetEvent) obj;
      String id2 = event2.getID();

      return Tool.equals(id, id2);
   }

   /**
    * Get the hash code.
    * @return the hash code of the asset event.
    */
   public int hashCode() {
      int hash = super.hashCode();
      String id = getID();

      if(id != null) {
         hash = hash ^ id.hashCode();
      }

      return hash;
   }

   /**
    * Process this sheet event.
    * @param rs the specified runtime sheet as the context.
    * @param cmd the specified asset command.
    */
   @Override
   public void process(RuntimeSheet rs, AssetCommand cmd) throws Exception {
      AssetEntry entry = rs == null ? null : rs.getEntry();

      // if this event is triggered from a vs, such as a condition browse data,
      // get the base worksheet from the viewsheet
      if(rs instanceof RuntimeViewsheet) {
         AssetEntry wsEntry = ((RuntimeViewsheet) rs).getRuntimeWorksheet().getEntry();

         if(Thread.currentThread() instanceof GroupedThread) {
            GroupedThread thread = (GroupedThread) Thread.currentThread();

            if(entry != null) {
               thread.addRecord(LogContext.DASHBOARD, entry.getPath());
            }

            if(wsEntry != null) {
               thread.addRecord(LogContext.WORKSHEET,  wsEntry.getPath());
            }
         }

         process(((RuntimeViewsheet) rs).getRuntimeWorksheet(), cmd);
      }
      else {
         if(Thread.currentThread() instanceof GroupedThread) {
            GroupedThread thread = (GroupedThread) Thread.currentThread();

            if(entry != null) {
               thread.addRecord(LogContext.WORKSHEET,  entry.getPath());
            }
         }

         RuntimeWorksheet rws = (RuntimeWorksheet) rs;
         MVSession session = rws.getAssetQuerySandbox().getMVSession();

         WSExecution.setAssetQuerySandbox(rws.getAssetQuerySandbox());

         // if worksheet changed, re-init sql context so change in table
         // is reflected in spark sql
         if(isUndoable() && session != null) {
            session.clearInitialized();
         }

         WSExecution.setAssetQuerySandbox(rws.getAssetQuerySandbox());

         try {
            process(rws, cmd);
         }
         finally {
            WSExecution.setAssetQuerySandbox(null);
         }
      }
   }

   /**
    * Process this worksheet event.
    * @param ws the specified runtime worksheet as the context.
    * @param command the specified command container.
    */
   public abstract void process(RuntimeWorksheet ws, AssetCommand command)
      throws Exception;
}
