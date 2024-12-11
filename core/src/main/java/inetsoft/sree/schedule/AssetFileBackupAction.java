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
package inetsoft.sree.schedule;

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.web.admin.general.DataSpaceSettingsService;

import java.io.Serializable;
import java.security.Principal;
import java.util.concurrent.Callable;

/**
 * A schedule action that saves the storage contents to a backup file.
 *
 * @version 11.4
 * @author InetSoft Technology Corp
 */
public class AssetFileBackupAction implements ScheduleAction {

   public AssetFileBackupAction() {
   }

   /**
    * Execute the action.
    * @param principal represents an entity
    */
   @Override
   public void run(Principal principal) throws Throwable {
      Cluster.getInstance().submit(new AssetFileBackupActionCallable(principal), false).get();
   }

   private static class AssetFileBackupActionCallable implements Callable<String>, Serializable {
      public AssetFileBackupActionCallable() {
      }

      public AssetFileBackupActionCallable(Principal principal) {
         this.principal = principal;
      }

      @Override
      public String call() throws Exception {
         return DataSpaceSettingsService.backup(null);
      }

      private Principal principal;
   }
}
