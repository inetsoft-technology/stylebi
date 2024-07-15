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

import inetsoft.sree.internal.cluster.SingletonCallableTask;

/**
 * {@code ListKeysTask} is a cluster singleton task that gets all the keys in a key-value store.
 */
public class ListKeysTask implements SingletonCallableTask<String[]> {
   /**
    * Creates a new instance of {@code ListKeysTask}.
    *
    * @param id the unique identifier of the key-value store.
    */
   public ListKeysTask(String id) {
      this.id = id;
   }

   @Override
   public String[] call() throws Exception {
      return KeyValueEngine.getInstance().stream(id)
         .map(KeyValuePair::getKey)
         .toArray(String[]::new);
   }

   private final String id;
}
