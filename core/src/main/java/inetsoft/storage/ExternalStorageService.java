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
package inetsoft.storage;

import inetsoft.sree.internal.SUtil;
import inetsoft.util.Catalog;
import inetsoft.util.SingletonManager;
import inetsoft.util.config.InetsoftConfig;

import java.io.IOException;
import java.nio.file.Path;
import java.security.Principal;
import java.util.List;
import java.util.ServiceLoader;

/**
 * {@code ExternalStorageService} provides the interface for classes that handle saving files to
 * some external storage.
 */
@SingletonManager.Singleton(ExternalStorageService.Reference.class)
public interface ExternalStorageService {
   /**
    * Writes a file to external storage.
    *
    * @param path the path to the file.
    * @param file the file to write.
    *
    * @throws IOException if an I/O error occurs.
    */
   void write(String path, Path file) throws IOException;

   default void write(String path, Path file, Principal principal) throws IOException {
      write(SUtil.addUserSpacePathPrefix(principal, path), file);
   }

   String getAvailableFile(String path, int start);

   /**
    * Gets the names of the files in the specified directory.
    *
    * @param directory the path to the directory to list.
    *
    * @return the list of file names.
    */
   List<String> listFiles(String directory);

   /**
    * Deletes a file from the external storage.
    *
    * @param path the path to the file to delete.
    *
    * @throws IOException if an I/O error occurs.
    */
   void delete(String path) throws IOException;

   void renameFolder(String ofolder, String nfolder) throws IOException;

   default String getStorageLocation() {
      return Catalog.getCatalog().getString("em.confirm.heapDump.storageLocation");
   }

   static ExternalStorageService getInstance() {
      return SingletonManager.getInstance(ExternalStorageService.class);
   }

   class Reference extends SingletonManager.Reference<ExternalStorageService> {
      @Override
      public ExternalStorageService get(Object... parameters) {
         if(service == null) {
            InetsoftConfig config = InetsoftConfig.getInstance();
            String type = config.getExternalStorage() == null ?
               "filesystem" : config.getExternalStorage().getType();

            for(ExternalStorageServiceFactory factory : ServiceLoader.load(ExternalStorageServiceFactory.class)) {
               if(factory.getType().equals(type)) {
                  service = factory.createExternalStorageService(config);
                  break;
               }
            }
         }

         return service;
      }

      @Override
      public void dispose() {
         if(service != null && service instanceof AutoCloseable closeable) {
            try {
               closeable.close();
            }
            catch(Exception ignore) {
            }
            finally {
               service = null;
            }
         }
      }

      private ExternalStorageService service;
   }
}
