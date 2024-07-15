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

import inetsoft.sree.internal.cluster.SingletonCallableTask;

import java.io.Serializable;

/**
 * {@code PutKeyValueTask} is a cluster singleton task that puts a value in a key-value store. The
 * previous value associated with the key is returned.
 *
 * @param <T> the value type.
 */
public class PutKeyValueTask<T extends Serializable>
   extends KeyValueTask<T> implements SingletonCallableTask<T>
{
   /**
    * Creates a new instance of {@code PutKeyValueTask}.
    *
    * @param id    the unique identifier of the key-value store.
    * @param key   the key.
    * @param value the value to store.
    */
   public PutKeyValueTask(String id, String key, T value) {
      super(id);
      this.key = key;
      this.data = serializeValue(value);
   }

   @Override
   public T call() throws Exception {
      T value = deserializeValue(data);
      T oldValue = getEngine().put(getId(), key, value);
      getMap().put(key, value);
      return oldValue;
   }

   private final String key;
   private final byte[] data;
}
