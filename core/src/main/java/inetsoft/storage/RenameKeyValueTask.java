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
import java.util.Map;

/**
 * {@code RenameKeyValueTask} is a cluster singleton task that renames a key in a key-value store.
 * Renaming a key removes the value with the old key and then puts the value with the new key. The
 * previous value associated with the new key is returned.
 *
 * @param <T> the value type.
 */
public class RenameKeyValueTask<T extends Serializable>
   extends KeyValueTask<T> implements SingletonCallableTask<T>
{
   /**
    * Creates a new instance of {@code RenameKeyValueTask}.
    *
    * @param id       the unique identifier of the key-value store.
    * @param oldKey   the old key.
    * @param newKey   the new key.
    * @param newValue the new value to use instead of the value associated with <i>oldKey</i>. If
    *                 {@code null}, the value associated with <i>oldKey</i> will be used.
    */
   public RenameKeyValueTask(String id, String oldKey, String newKey, T newValue) {
      super(id);
      this.oldKey = oldKey;
      this.newKey = newKey;
      this.data = serializeValue(newValue);
   }

   @Override
   public T call() throws Exception {
      T value = getEngine().remove(getId(), oldKey);

      if(value == null) {
         throw new Exception
            (oldKey + " does not exist in " + getId() + ", cannot rename to " + newKey);
      }

      T newValue = deserializeValue(data);

      if(newValue != null) {
         value = newValue;
      }

      T oldValue = getEngine().put(getId(), newKey, value);
      Map<String, T> map = getMap();
      map.remove(oldKey);
      map.put(newKey, value);
      return oldValue;
   }

   private final String oldKey;
   private final String newKey;
   private final byte[] data;
}
