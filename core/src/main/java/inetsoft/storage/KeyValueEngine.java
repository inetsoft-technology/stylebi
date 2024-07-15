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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.ContextAttributes;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import inetsoft.util.SingletonManager;
import inetsoft.util.config.InetsoftConfig;
import org.slf4j.LoggerFactory;

import java.util.ServiceLoader;
import java.util.stream.Stream;

/**
 * {@code KeyValueEngine} is an interface for classes that handle storing key-value pairs.
 */
@SingletonManager.Singleton(KeyValueEngine.Reference.class)
public interface KeyValueEngine extends AutoCloseable {
   /**
    * Determines if a store contains the specified key.
    *
    * @param id  the unique identifier of the key-value store.
    * @param key the key to find.
    *
    * @return {@code true} if the store contains the key or {@code false} if it doesn't.
    */
   boolean contains(String id, String key);

   /**
    * Gets the value associated with the specified key.
    *
    * @param id  the unique identifier of the key-value store.
    * @param key the key.
    *
    * @param <T> the value type.
    *
    * @return the value or {@code null} if none.
    */
   <T> T get(String id, String key);

   /**
    * Puts a value into a key-value store.
    *
    * @param <T> the value type.
    *
    * @param id         the unique identifier of the key-value store.
    * @param key        the key.
    * @param value      the value to store.
    * @return the old value associated with the key or {@code null} if none.
    */
   <T> T put(String id, String key, T value);

   /**
    * Removes the entry with the specified key.
    *
    * @param id  the unique identifier of the key-value store.
    * @param key the key.
    *
    * @param <T> the value type.
    *
    * @return the value that was associated with the key or {@code null} if none.
    */
   <T> T remove(String id, String key);

   /**
    * Deletes the store with the specified storeID and all associated data
    *
    * @param id  the unique identifier of the key-value store.
    */
   void deleteStorage(String id);

   /**
    * Gets a stream of the entries in a key-value store.
    *
    * @param id the unique identifier of the key-value store.
    *
    * @param <T> the value type.
    *
    * @return the entry stream.
    */
   <T> Stream<KeyValuePair<T>> stream(String id);

   /**
    * Gets a stream of the unique identifiers for all the key-value stores.
    *
    * <p>This operation is expensive in most implementations and should be used sparingly.</p>
    *
    * @return the identifier stream.
    */
   Stream<String> idStream();

   @Override
   default void close() throws Exception {
   }

   /**
    * Creates an object mapper suitable for the storage of values in the store.
    *
    * @return an object mapper.
    */
   static ObjectMapper createObjectMapper() {
      return createObjectMapper(false);
   }

   /**
    * Creates an object mapper suitable for the storage of values in the store.
    *
    * @param encodePropertyNames {@code true} to encode asset entry property names to avoid using
    *                            reserved names.
    *
    * @return an object mapper.
    */
   static ObjectMapper createObjectMapper(boolean encodePropertyNames) {
      ObjectMapper mapper = new ObjectMapper();
      mapper.registerModule(new Jdk8Module());
      mapper.registerModule(new JavaTimeModule());
      mapper.registerModule(new GuavaModule());
      mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

      if(encodePropertyNames) {
         ContextAttributes attrs = ContextAttributes.getEmpty()
            .withSharedAttribute(ENCODE_PROPERTY_NAMES, Boolean.TRUE);
         mapper = mapper.setDefaultAttributes(attrs);
      }

      return mapper;
   }

   /**
    * Gets the shared instance of the key value storage engine.
    *
    * @return the singleton instance.
    */
   static KeyValueEngine getInstance() {
      return SingletonManager.getInstance(KeyValueEngine.class);
   }

   String ENCODE_PROPERTY_NAMES = KeyValueEngine.class.getName() + ".encodePropertyNames";

   final class Reference extends SingletonManager.Reference<KeyValueEngine> {
      @Override
      public synchronized KeyValueEngine get(Object... parameters) {
         if(engine == null) {
            InetsoftConfig config = InetsoftConfig.getInstance();
            String type = config.getKeyValue().getType();

            for(KeyValueEngineFactory factory : ServiceLoader.load(KeyValueEngineFactory.class)) {
               if(factory.getType().equals(type)) {
                  engine = factory.createEngine(config);
                  break;
               }
            }

            if(engine == null) {
               throw new RuntimeException("Failed to get key value engine of type '" + type + "'");
            }
         }

         return engine;
      }

      @Override
      public synchronized void dispose() {
         if(engine != null) {
            try {
               engine.close();
            }
            catch(Exception e) {
               LoggerFactory.getLogger(KeyValueEngine.class)
                  .warn("Failed to close key value engine", e);
            }

            engine = null;
         }
      }

      private KeyValueEngine engine;
   }
}
