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

import inetsoft.sree.internal.cluster.*;
import inetsoft.util.ConfigurationContext;

import java.io.*;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * {@code KeyValueTask} is the base class for implementations of {@link SingletonCallableTask} that
 * access a key-value store.
 *
 * @param <T> the value type.
 */
public abstract class KeyValueTask<T extends Serializable> implements Serializable {
   /**
    * Creates a new instance of {@code KeyValueTask}.
    *
    * @param id the unique identifier of the key-value store.
    */
   public KeyValueTask(String id) {
      this.id = id;
   }

   /**
    * Gets the unique identifier of the key-value store.
    *
    * @return the store identifier.
    */
   protected final String getId() {
      return id;
   }

   /**
    * Gets the key-value engine instance.
    *
    * @return the engine.
    */
   protected final KeyValueEngine getEngine() {
      return getServiceBean(KeyValueEngine.class);
   }

   /**
    * Gets the blob storage engine instance.
    *
    * @return the engine.
    */
   protected final BlobEngine getBlobEngine() {
      return getServiceBean(BlobEngine.class);
   }

   /**
    * Gets the cluster instance.
    *
    * @return the cluster.
    */
   protected final Cluster getCluster() {
      return getServiceBean(Cluster.class);
   }

   /**
    * Gets the distributed map in which the values are cached.
    *
    * @return the map.
    */
   protected final DistributedMap<String, T> getMap() {
      return getCluster().getReplicatedMap("inetsoft.storage.kv." + id);
   }

   /**
    * Gets a Spring bean for a task running on a cluster singleton-service thread. The service can
    * start on a node before that node's main thread created the bean, while the main thread holds
    * the Spring singleton lock and goes on to wait for the service. A plain lookup would park on
    * that lock until the main thread gives up, so wait for the bean to be created instead
    * (Bug #76975).
    *
    * @param type the bean type.
    *
    * @return the bean.
    */
   static <B> B getServiceBean(Class<B> type) {
      return ConfigurationContext.getContext()
         .awaitSpringBean(type, SERVICE_BEAN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
   }

   /**
    * Serializes a value.
    *
    * @param value the value to serialize.
    *
    * @return the serialized data.
    */
   protected final byte[] serializeValue(Serializable value) {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();

      try(ObjectOutputStream output = new ObjectOutputStream(buffer)) {
         output.writeObject(value);
      }
      catch(IOException e) {
         throw new RuntimeException("Failed to serialize value", e);
      }

      return buffer.toByteArray();
   }

   /**
    * Deserializes a value.
    *
    * @param data the data to deserialize.
    *
    * @param <V> the data type.
    *
    * @return the deserialized value.
    */
   @SuppressWarnings("unchecked")
   protected final <V extends Serializable> V deserializeValue(byte[] data) {
      try(ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(data))) {
         return (V) input.readObject();
      }
      catch(IOException | ClassNotFoundException e) {
         throw new RuntimeException("Failed to deserialize value", e);
      }
   }

   private final String id;
   private static final long SERVICE_BEAN_TIMEOUT_SECONDS = 60L;
}
