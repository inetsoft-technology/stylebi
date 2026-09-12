/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.SingletonCallableTask;
import inetsoft.util.FileSystemService;

import java.io.*;

public class ListBlobsTask<T extends Serializable> extends BlobTask<T>
   implements SingletonCallableTask<String>
{
   /**
    * Creates a new instance of {@code ListBlobsTask}.
    */
   public ListBlobsTask(String id) {
      super(id);
   }

   @Override
   public String call() throws Exception {
      File tempFile = FileSystemService.getInstance()
         .getCacheTempFile("blob-list", ".dat");

      try(PrintWriter writer = new PrintWriter(new FileOutputStream(tempFile, true))) {
         writer.println(getId());
         BlobEngine.getInstance().list(getId(), writer);
         writer.println();
      }

      return Cluster.getInstance().addTransferFile(tempFile);
   }
}

