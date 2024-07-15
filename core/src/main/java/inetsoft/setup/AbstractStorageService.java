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
package inetsoft.setup;

import inetsoft.storage.*;
import inetsoft.util.config.InetsoftConfig;

import java.io.File;
import java.util.ServiceLoader;

/**
 * {@code AbstractConfigService} is the base class for services access a configured storage backend.
 */
public abstract class AbstractStorageService implements AutoCloseable {
   AbstractStorageService(String dir) {
      File file = new File(dir, "inetsoft.yaml");

      if(!file.exists() && new File(dir, "inetsoft.yml").exists()) {
         file = new File(dir, "inetsoft.yml");
      }

      config = InetsoftConfig.load(file.toPath());
   }

   final KeyValueEngine createKeyValueEngine() {
      String type = config.getKeyValue().getType();
      KeyValueEngine engine = null;

      for(KeyValueEngineFactory factory : ServiceLoader.load(KeyValueEngineFactory.class)) {
         if(factory.getType().equals(type)) {
            engine = factory.createEngine(config);
            break;
         }
      }

      if(engine == null) {
         throw new RuntimeException("Failed to get key value engine of type " + type);
      }

      return engine;
   }

   final BlobEngine createBlobEngine() {
      String type = "local".equals(config.getBlob().getType()) ? "filesystem" : config.getBlob().getType();
      BlobEngine engine = null;

      for(BlobEngineFactory factory : ServiceLoader.load(BlobEngineFactory.class)) {
         if(factory.getType().equals(type)) {
            engine = factory.createEngine(config);
            break;
         }
      }

      if(engine == null) {
         throw new RuntimeException("Failed to get blob engine of type " + type);
      }

      return engine;
   }

   private final InetsoftConfig config;
}
