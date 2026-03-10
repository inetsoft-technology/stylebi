/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import inetsoft.util.config.BlobConfig;
import inetsoft.util.config.InetsoftConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.ServiceLoader;

/**
 * Spring configuration that creates the storage engine beans, replacing the
 * {@code Reference} inner-class lazy-initialization pattern used by
 * {@link KeyValueEngine} and {@link BlobEngine}.
 */
@Configuration
public class StorageConfiguration {

   @Bean
   public InetsoftConfig inetsoftConfig() {
      return InetsoftConfig.BOOTSTRAP_INSTANCE;
   }

   @Bean
   public KeyValueEngine keyValueEngine(InetsoftConfig config) {
      String type = config.getKeyValue().getType();

      for(KeyValueEngineFactory factory : ServiceLoader.load(KeyValueEngineFactory.class)) {
         if(factory.getType().equals(type)) {
            return factory.createEngine(config);
         }
      }

      throw new RuntimeException("No KeyValueEngineFactory found for type: " + type);
   }

   @Bean
   public BlobEngine blobEngine(InetsoftConfig config) {
      String type = "local".equals(config.getBlob().getType())
         ? "filesystem" : config.getBlob().getType();

      for(BlobEngineFactory factory : ServiceLoader.load(BlobEngineFactory.class)) {
         if(factory.getType().equals(type)) {
            return factory.createEngine(config);
         }
      }

      throw new RuntimeException(
         "No BlobEngineFactory found for type: " + config.getBlob().getType());
   }

   /** External storage service — routes writes/reads to the configured storage backend. */
   @Bean
   @Lazy
   public ExternalStorageService externalStorageService(InetsoftConfig config) {
      String type = config.getExternalStorage() == null ?
         "filesystem" : config.getExternalStorage().getType();

      for(ExternalStorageServiceFactory factory : ServiceLoader.load(ExternalStorageServiceFactory.class)) {
         if(factory.getType().equals(type)) {
            return factory.createExternalStorageService(config);
         }
      }

      throw new RuntimeException("No ExternalStorageServiceFactory found for type: " + type);
   }

   @Bean
   public BlobCache blobCache(InetsoftConfig config, BlobEngine blobEngine) {
      BlobConfig blobConfig = config.getBlob();
      Path baseDir = Paths.get(Objects.requireNonNull(blobConfig.getCacheDirectory()));
      Long maxSize = blobConfig.getCacheMaxSize();

      if(maxSize != null && maxSize > 0) {
         return new BoundedBlobCache(baseDir, blobEngine, maxSize);
      }

      return new BlobCache(baseDir, blobEngine);
   }
}
