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
package inetsoft.mv.fs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * SBlock, server side block.
 *
 * @author InetSoft Technology
 * @version 10.2
 */
public final class SBlock extends XBlock {
   /**
    * Constructor.
    */
   public SBlock() {
      super();
   }

   /**
    * Constructor.
    */
   public SBlock(String parent, String id) {
      super(parent, id);
   }

   /**
    * Add one SNBlock.
    */
   public boolean add(SNBlock block) {
      // this is intentional, parent XBlock.equals() method is used
      //noinspection EqualsBetweenInconvertibleTypes
      if(!equals(block)) {
         return false;
      }

      try {
         nblock = block;
         return true;
      }
      finally {
         notifyReady();
      }
   }

   /**
    * Remove the SNBlock.
    */
   public void remove() {
      nblock = null;
   }

   private final void notifyReady() {
      if(!ready) {
         synchronized(this) {
            ready = true;
            notifyAll();
         }
      }
   }

   /**
    * Wait for block information to be loaded after initial creation.
    * This method should not be necessary if FileSystem and BlockSystem
    * are always in sync. But since we update the FileSystem and BlockSystem
    * (load from XML) separately, there should be a small time gap where
    * a XFile's SBlock may not have NBlocks populated.
    */
   public final boolean waitReady(XFile xfile) {
      if(!ready) {
         ReentrantReadWriteLock lock = xfile.getReadWriteLock();
         final int readcnt = lock.getReadHoldCount();
         final int writecnt = lock.getWriteHoldCount();

         try {
            // need to unload the read lock to allow AbstractFileSystem
            // to aquire the write lock to populate the SBlock
            for(int i = 0; i < readcnt; i++) {
               xfile.getReadLock().unlock();
            }

            for(int i = 0; i < writecnt; i++) {
               xfile.getWriteLock().unlock();
            }

            synchronized(this) {
               try {
                  wait(1000);
               }
               catch(Exception ex) {
               }
            }
         }
         finally {
            for(int i = 0; i < readcnt; i++) {
               xfile.getReadLock().lock();
            }

            for(int i = 0; i < writecnt; i++) {
               xfile.getWriteLock().lock();
            }
         }
      }

      return ready;
   }

   /**
    * Get the SNBlock for the given host.
    */
   public SNBlock get() {
      return nblock;
   }

   /**
    * Get all SNBlocks.
    */
   public SNBlock[] list() {
      return nblock != null ? new SNBlock[] { nblock } : new SNBlock[0];
   }

   /**
    * Clear the SBlock by removing all SNBlocks.
    */
   public void clear() {
      nblock = null;
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("SBlock");
      sb.append("-");
      sb.append(getID());
      sb.append("[");
      SNBlock[] blocks = list();

      for(int i = 0; i < blocks.length; i++) {
         if(i != 0) {
            sb.append(", ");
         }

         sb.append(blocks[i]);
      }

      sb.append("]");
      return sb.toString();
   }

   @Override
   public Object clone() {
      try {
         SBlock nblk = (SBlock) super.clone();

         if(nblock != null) {
            nblk.nblock = (SNBlock) nblock.clone();
         }

         return nblk;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone block", ex);
      }

      return null;
   }

   private static final Logger LOG = LoggerFactory.getLogger(SBlock.class);
   private SNBlock nblock = null;
   private boolean ready = false;
}
