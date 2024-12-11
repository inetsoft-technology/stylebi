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
import java.util.Set;

/**
 * {@code DeleteAllKeyValueTask} is cluster singleton task that bulk deletes a set of keys.
 *
 * @param <T> the value type.
 */
public class DeleteAllKeyValueTask<T extends Serializable>
   extends KeyValueTask<T> implements SingletonRunnableTask
{
   /**
    * Creates a new instance of {@code DeleteKeyValueTask}.
    *
    * @param id   the unique identifier of the key-value store.
    * @param keys the keys.
    */
   public DeleteAllKeyValueTask(String id, Set<String> keys) {
      super(id);
      this.keys = keys;
   }

   @Override
   public void run() {
      getEngine().removeAll(getId(), keys);
      getMap().removeAll(keys);
   }

   private final Set<String> keys;
}
