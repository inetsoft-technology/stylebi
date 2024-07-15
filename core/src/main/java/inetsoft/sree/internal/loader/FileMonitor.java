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
package inetsoft.sree.internal.loader;

import inetsoft.sree.SreeEnv;
import inetsoft.util.FileSystemService;
import inetsoft.util.GroupedThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

/**
 * File monitor monitors replet class file changes.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class FileMonitor extends GroupedThread {
   /**
    * Default constructor. Creates a new FileMonitor
    */
   public FileMonitor() {
      setDaemon(true);
      listenerMap = new HashMap<>();
      fileModificationMap = new HashMap<>();
   }

   /**
    * Registers a listener for a file
    * @param fileName the file to monitor for changes
    * @param listener the FileChangedListener to notify if changes
    * are dectected in fileName
    */
   public void register(String fileName, FileChangedListener listener) {
      File file = FileSystemService.getInstance().getFile(fileName);

      if(!file.exists()) {
         throw new IllegalArgumentException("file does not exist");
      }

      List<FileChangedListener> v = listenerMap.get(fileName);

      if(v == null) {
         v = new ArrayList<>();
         listenerMap.put(fileName, v);
      }

      v.add(listener);
      fileModificationMap.put(fileName, Long.valueOf(file.lastModified()));
   }

   /**
    * Notifies the listeners for fileName
    * @param fileName the file name of the file that has changed
    */
   private void fireEvent(String fileName) {
      List<FileChangedListener> v = listenerMap.get(fileName);

      for(FileChangedListener listener : v) {
         listener.fileChanged(fileName);
      }
   }

   /**
    * Monitors for file changes.
    */
   @Override
   protected void doRun() {
      while(true) {
         for(String fileName : listenerMap.keySet()) {
            File file = FileSystemService.getInstance().getFile(fileName);

            if(file.exists()) {
               Long lmodified = (Long) fileModificationMap.get(fileName);
               long modificationTime = file.lastModified();
               long lastModificationTime = lmodified == null ?
                  Long.MAX_VALUE : lmodified.longValue();

               if(modificationTime > lastModificationTime) {
                  fireEvent(fileName);
                  Long newModificationTime = Long.valueOf(modificationTime);
                  fileModificationMap.put(fileName, newModificationTime);
               }
            }
         }

         try {
            long sleepTime = Long.parseLong(SreeEnv.getProperty
               ("replet.auto.reload.interval"));

            Thread.sleep(sleepTime);
         }
         catch(Exception ex) {
            LOG.error("Invalid numeric value for replet reload " +
               "interval (replet.auto.reload.interval): " +
               SreeEnv.getProperty("replet.auto.reload.interval"), ex);
         }

         String reload = SreeEnv.getProperty("replet.auto.reload");

         if(reload.equalsIgnoreCase("false")) {
            break;
         }
      }
   }

   /**
    * Clears the list of files to monitor
    */
   public void clear() {
      listenerMap = new HashMap<>();
      fileModificationMap = new HashMap<>();
   }

   // String -> Vector
   private Map<String, List<FileChangedListener>> listenerMap;
   private Map<String, Long> fileModificationMap; // String -> Long

   private static final Logger LOG =
      LoggerFactory.getLogger(FileMonitor.class);
}

