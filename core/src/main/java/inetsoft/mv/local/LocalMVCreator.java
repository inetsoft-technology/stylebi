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
package inetsoft.mv.local;

import inetsoft.mv.*;
import inetsoft.mv.fs.*;
import inetsoft.uql.ConditionListWrapper;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.asset.Worksheet;

/**
 * DefaultMVCreator, determine to use incremental create mv or newly create mv.
 *
 * @version 11.2
 * @author InetSoft Technology Corp
 */
public class LocalMVCreator extends AbstractMVCreator {
   /**
    * Creates a new instance of <tt>LocalMVCreator</tt>.
    *
    * @param def the view definition.
    */
   public LocalMVCreator(MVDef def) {
      super(def);
   }

   /**
    * Cancel this task.
    */
   @Override
   public void cancel() {
      canceled = true;

      if(dispatcher != null) {
         dispatcher.cancel();
      }

      if(incremental != null) {
         incremental.cancel();
      }
   }

   @Override
   protected boolean create0() throws Exception {
      XServerNode server = FSService.getServer();

      if(server == null) {
         throw new RuntimeException("This host is not server node!");
      }

      XFileSystem sys = server.getFSystem();
      String name = def.getName();
      XFile file = sys.get(name);

      Worksheet ws = def.getWorksheet();
      String table = def.getMVTable();
      TableAssembly assembly = (TableAssembly) ws.getAssembly(table);
      ConditionListWrapper conds = assembly.getMVConditionList();

      // association MV is created from the base MV so it doesn't need to go
      // through the incremental creation (which may need special logic)
      if(def.isAssociationMV() || file == null ||
         (conds == null || conds.isEmpty()) && !assembly.isMVForceAppendUpdates())
      {
         if(canceled || Thread.currentThread().isInterrupted()) {
            return false;
         }

         // here should not remove the def, this will cause run from
         // schedule, the def lost, because def will not be saved when
         // run schedule

         dispatcher = new MVDispatcher(def);

         if(canceled || Thread.currentThread().isInterrupted()) {
            return false;
         }

         dispatcher.dispatch();
         return true;
      }

      incremental = new LocalMVIncremental(def);

      if(canceled || Thread.currentThread().isInterrupted()) {
         return false;
      }

      incremental.update();
      return true;
   }

   private MVDispatcher dispatcher;
   private LocalMVIncremental incremental;
   private volatile boolean canceled = false;
}
