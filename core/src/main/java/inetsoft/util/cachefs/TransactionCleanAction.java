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

package inetsoft.util.cachefs;

import inetsoft.storage.BlobTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class TransactionCleanAction implements Runnable {
   public TransactionCleanAction(BlobTransaction<CacheMetadata> transaction) {
      this.transaction = transaction;
   }

   @Override
   public void run() {
      try {
         transaction.close();
      }
      catch(IOException e) {
         if(LOG.isDebugEnabled()) {
            LOG.warn("Failed to close transaction", e);
         }
         else {
            LOG.warn("Failed to clean up blob transaction");
         }
      }
   }

   private final BlobTransaction<CacheMetadata> transaction;
   private static final Logger LOG = LoggerFactory.getLogger(TransactionCleanAction.class);
}
