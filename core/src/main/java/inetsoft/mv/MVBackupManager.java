/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.mv;

import inetsoft.mv.data.MVStorage;
import inetsoft.mv.fs.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MVBackupManager, a manager to handle mv backup processing.
 * !!!! To be removed in 12.3 !!!!
 *
 * @deprecated 12.2
 * @version 11.5
 * @author InetSoft Technology Corp
 */
public class MVBackupManager {
   /**
    * Remove mv from file system, if need backup, backup it.
    */
   public static boolean remove(String name) {
      XServerNode server = FSService.getServer();

      if(server == null) {
         throw new RuntimeException("Host \"" + Tool.getIP() +
            "\" is not server node!");
      }

      XFileSystem fsys = server.getFSystem();
      remove0(name, fsys);
      return true;
   }

   /**
    * Remove mv from file system.
    */
   private static void remove0(String name, XFileSystem fsys) {
      fsys.remove(name);
      MVStorage storage = MVStorage.getInstance();
      String mfile = MVStorage.getFile(name);

      if(storage.exists(mfile)) {
         try {
            storage.remove(mfile);
         }
         catch(Exception ignore) {
         }
      }
   }
}
