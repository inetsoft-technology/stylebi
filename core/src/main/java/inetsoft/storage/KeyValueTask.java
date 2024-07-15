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

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.SingletonCallableTask;

import java.io.*;
import java.util.Map;

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
      return KeyValueEngine.getInstance();
   }

   /**
    * Gets the distributed map in which the values are cached.
    *
    * @return the map.
    */
   protected final Map<String, T> getMap() {
      Cluster cluster = Cluster.getInstance();
      return cluster.getReplicatedMap("inetsoft.storage.kv." + id);
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
}
