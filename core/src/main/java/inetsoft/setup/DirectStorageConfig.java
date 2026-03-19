/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

package inetsoft.setup;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import inetsoft.storage.*;
import inetsoft.uql.util.XSessionService;
import inetsoft.util.config.InetsoftConfig;
import inetsoft.web.json.ThirdPartySupportModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ServiceLoader;

@Configuration
public class DirectStorageConfig {
   @Bean
   public ObjectMapper objectMapper() {
      StreamReadConstraints defaults = StreamReadConstraints.defaults();
      JsonFactory jsonFactory = new MappingJsonFactory();
      jsonFactory.setStreamReadConstraints(StreamReadConstraints.builder()
                                              .maxDocumentLength(defaults.getMaxDocumentLength())
                                              .maxNameLength(defaults.getMaxNameLength())
                                              .maxNumberLength(defaults.getMaxNumberLength())
                                              .maxNestingDepth(defaults.getMaxNestingDepth())
                                              .maxStringLength(1073741824)
                                              .build());
      ObjectMapper mapper = new ObjectMapper(jsonFactory);
      mapper.registerModule(new Jdk8Module());
      mapper.registerModule(new JavaTimeModule());
      mapper.registerModule(new ThirdPartySupportModule());
      mapper.registerModule(new GuavaModule());
      mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
      return mapper;
   }

   @Bean
   public InetsoftConfig inetsoftConfig() {
      return InetsoftConfig.BOOTSTRAP_INSTANCE;
   }

   @Bean
   public KeyValueEngine keyValueEngine(InetsoftConfig config) {
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

   @Bean
   public BlobEngine blobEngine(InetsoftConfig config) {
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

   @Bean
   public StorageService storageService(InetsoftConfig config, KeyValueEngine keyValueEngine, BlobEngine blobEngine) {
      return new StorageService(config, keyValueEngine, blobEngine);
   }

   @Bean
   public PropertiesService propertiesService(InetsoftConfig config, KeyValueEngine keyValueEngine) {
      return new PropertiesService(config, keyValueEngine);
   }

   @Bean
   public XSessionService xSessionService() {
      return new XSessionService();
   }
}
