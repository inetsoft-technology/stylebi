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

import inetsoft.sree.internal.cluster.SingletonRunnableTask;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * {@code ReplaceAllKeyValueTask} is a cluster singleton task that clears a key-value store and bulk
 * inserts a set of key-value pairs.
 *
 * @param <T> the value type.
 */
public class ReplaceAllKeyValueTask<T extends Serializable>
   extends KeyValueTask<T> implements SingletonRunnableTask
{
   /**
    * Creates a new instance of {@code ReplaceAllKeyValueTask}.
    *
    * @param id     the unique identifier of the key-value store.
    * @param values the new values.
    */
   public ReplaceAllKeyValueTask(String id, SortedMap<String, T> values) {
      super(id);
      this.data = serializeValue((Serializable) values);
   }

   @Override
   public void run() {
      try {
         // first deserialize before removing anything
         Map<String, T> values = deserializeValue(data);

         // get keys that aren't present in the new set of keys
         Set<String> keysToRemove = getEngine().stream(getId())
            .map(KeyValuePair::getKey)
            .filter(key -> !values.containsKey(key))
            .collect(Collectors.toSet());

         // insert new keys
         getEngine().putAll(getId(), values);

         // remove old keys not present in new keys
         getEngine().removeAll(getId(), keysToRemove);

         Map<String, T> map = getMap();

         if(!keysToRemove.isEmpty()) {
            if(keysToRemove.size() == 1) {
               map.remove(keysToRemove.iterator().next());
            }
            else {
               map.clear();
            }
         }

         map.putAll(values);
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to replace values", e);
      }
   }

   private final byte[] data;
}
