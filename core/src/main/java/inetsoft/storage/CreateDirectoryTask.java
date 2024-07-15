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

public class CreateDirectoryTask<T extends Serializable> extends BlobTask<T>
   implements SingletonRunnableTask
{
   public CreateDirectoryTask(String id, Blob<T> blob) {
      super(id);
      this.data = serializeValue(blob);
   }

   @Override
   public void run() {
      Blob<T> blob = deserializeValue(data);
      getEngine().put(getId(), blob.getPath(), blob);
      getMap().put(blob.getPath(), blob);
      getLastModified().set(blob.getLastModified().toEpochMilli());
   }

   private final byte[] data;
}
