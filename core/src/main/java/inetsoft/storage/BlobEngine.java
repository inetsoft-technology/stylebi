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
package inetsoft.storage;

import inetsoft.util.SingletonManager;
import inetsoft.util.config.InetsoftConfig;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ServiceLoader;

/**
 * {@code BlobEngine} is an interface for classes that handle loading and saving blobs to some
 * persistent storage.
 */
@SingletonManager.Singleton(BlobEngine.Reference.class)
public interface BlobEngine extends AutoCloseable {
   /**
    * Determines if the blob with the specified digest exists in storage.
    *
    * @param id     the unique identifier of the storage.
    * @param digest the MD5 digest of the blob as a hexadecimal string.
    *
    * @return {@code true} if the blob exists or {@code false} if it doesn't.
    */
   boolean exists(String id, String digest);

   /**
    * Reads a blob from storage.
    *
    * @param id     the unique identifier of the storage.
    * @param digest the MD5 digest of the blob as a hexadecimal string.
    * @param target the path to the local file where the blob will be written.
    *
    * @throws IOException if an I/O error occurs.
    */
   void read(String id, String digest, Path target) throws IOException;

   /**
    * Writes a blob into storage.
    *
    * @param id     the unique identifier of the storage.
    * @param digest the MD5 digest of the blob as a hexadecimal string.
    * @param source the path to the local file from where the blob will be read.
    *
    * @throws IOException if an I/O error occurs.
    */
   void write(String id, String digest, Path source) throws IOException;

   /**
    * Deletes a blob from storage.
    *
    * @param id     the unique identifier of the storage.
    * @param digest the MD5 digest of the blob as a hexadecimal string.
    *
    * @throws IOException if an I/O error occurs.
    */
   void delete(String id, String digest) throws IOException;

   @Override
   default void close() throws Exception {
   }

   /**
    * Gets the shared instance of the blob storage engine.
    *
    * @return the singleton instance.
    */
   static BlobEngine getInstance() {
      return SingletonManager.getInstance(BlobEngine.class);
   }

   final class Reference extends SingletonManager.Reference<BlobEngine> {
      @Override
      public BlobEngine get(Object... parameters) {
         if(engine == null) {
            InetsoftConfig config = InetsoftConfig.getInstance();
            String type = "local".equals(config.getBlob().getType()) ? "filesystem" : config.getBlob().getType();

            for(BlobEngineFactory factory : ServiceLoader.load(BlobEngineFactory.class)) {
               if(factory.getType().equals(type)) {
                  engine = factory.createEngine(config);
                  break;
               }
            }

            if(engine == null) {
               throw new RuntimeException(
                  "Failed to get blob engine of type '" + config.getBlob().getType() + "'");
            }
         }

         return engine;
      }

      @Override
      public void dispose() {
         if(engine != null) {
            try {
               engine.close();
            }
            catch(Exception e) {
               LoggerFactory.getLogger(BlobEngine.class)
                  .warn("Failed to close blob storage engine", e);
            }
         }
      }

      private BlobEngine engine;
   }
}
