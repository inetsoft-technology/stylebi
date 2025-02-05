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
package inetsoft.mv.fs.internal;

import inetsoft.mv.MVExecutionException;
import inetsoft.mv.comm.*;
import inetsoft.mv.data.SubMV;
import inetsoft.mv.fs.*;
import inetsoft.mv.util.SeekableInputStream;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.XNode;
import inetsoft.util.Tool;
import inetsoft.util.swap.XSwapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ReadableByteChannel;
import java.util.*;

/**
 * LocalFileSystem, the local implementation of XFileSystem.
 *
 * @author InetSoft Technology
 * @version 10.2
 */
public final class LocalFileSystem extends AbstractFileSystem {
   /**
    * Constructor.
    */
   public LocalFileSystem(XServerNode server, String orgId) {
      super(server, orgId);

      // get block system from data node. For local system, server node is
      // also data node, so here we just get block system from data node
      XDataNode node = FSService.getDataNode(orgId);
      bsys = node.getBSystem();
   }

   /**
    * Constructor.
    */
   public LocalFileSystem(XServerNode server) {
      super(server);

      // get block system from data node. For local system, server node is
      // also data node, so here we just get block system from data node
      FSService service = FSService.getService();
      XDataNode node = FSService.getDataNode();
      bsys = node.getBSystem();
   }

   /**
    * Append record.
    */
   public static boolean appendRecord(NBlock block, BlockFile nfile, int nversion) throws Exception {
      XBlockSystem sys = FSService.getDataNode().getBSystem();
      String bid = block.getID();
      BlockFile file = sys.getFile(bid);
      SubMV record = SubMV.get(nfile);

      if(file == null) {
         throw new Exception("The file of the block not found: " + bid);
      }

      SubMV sub = SubMV.get(file);

      if(sub == null) {
         throw new Exception("The sub mv of the block not found: " + bid);
      }

      try {
         sub.appendBlock(record);

         NBlock nblock = sys.get(bid);
         nblock.setVersion(nversion);
         nblock.setPhysicalLen(file.length());
         nblock.setLength(file.length());

         if(block != nblock) {
            // server node block
            block.setVersion(nversion);
            block.setPhysicalLen(file.length());
            block.setLength(file.length());
         }

         SubMV.removeMap(file);
         sys.update(bid);
         sys.save();
      }
      catch(Exception e) {
         LOG.error("Failed to append record, block file=" +
            file + ", sub-mv file=" + nfile, e);
         return false;
      }

      return true;
   }

   /**
    * Delete the specified rows in the table block.
    */
   public static boolean deleteRecord(NBlock block, XNode cond, int nversion)
      throws Exception
   {
      XBlockSystem sys = FSService.getDataNode().getBSystem();
      String bid = block.getID();
      BlockFile file = sys.getFile(bid);

      if(file == null) {
         throw new Exception("The file of the block not found: " + bid);
      }

      SubMV sub = SubMV.get(file);

      if(sub == null) {
         throw new Exception("The sub mv of the block not found: " + bid);
      }

      try {
         boolean deleted = sub.deleteRecord(cond);
         NBlock nblock = sys.get(bid);
         nblock.setVersion(nversion);
         nblock.setPhysicalLen(file.length());
         nblock.setLength(file.length());

         if(block != nblock) {
            // server node block
            block.setVersion(nversion);
            block.setPhysicalLen(file.length());
            block.setLength(file.length());
         }

         SubMV.removeMap(file);
         sys.update(bid);
         sys.save();
      }
      catch(Exception e) {
         LOG.error("Failed to delete record from block " + bid + " in file " + file, e);
         return false;
      }

      return true;
   }

   /**
    * Update block.
    */
   public static boolean updateBlock(NBlock block, Map<Integer, Integer>[] maps,
      int[] sizes, int[] dimRanges, List<Number[]> intRanges, int nversion)
      throws Exception
   {
      XBlockSystem sys = FSService.getDataNode().getBSystem();
      String bid = block.getID();
      BlockFile file = sys.getFile(bid);

      if(file == null) {
         throw new Exception("The file of the block not found: " + bid);
      }

      SubMV sub = SubMV.get(file);

      if(sub == null) {
         throw new Exception("The sub mv of the block not found: " + bid);
      }

      try {
         sub.updateData(maps, sizes, dimRanges, intRanges);
         NBlock nblock = sys.get(bid);
         nblock.setVersion(nversion);
         nblock.setPhysicalLen(file.length());
         nblock.setLength(file.length());

         if(block != nblock) {
            // server node block
            block.setVersion(nversion);
            block.setPhysicalLen(file.length());
            block.setLength(file.length());
         }

         SubMV.removeMap(file);
         sys.update(bid);
         sys.save();
      }
      catch(Exception e) {
         LOG.error("Failed to update block " + block.getID() + " in file " + file, e);
         return false;
      }

      return true;
   }

   // @TEST
   /**
    * Append file to file system, if the xfile not exist, create it.
    */
   @Override
   public XFile append(String name, BlockFile[] files) {
      return add(name, files, -1, true);
   }

   /**
    * Add one XFile to this file system.
    * @param name the specified name of this XFile.
    * @param files the specified physical files as blocks.
    * @return the created XFile if succeeded.
    */
   @Override
   public XFile add(String name, BlockFile[] files) {
      return add(name, files, -1, false);
   }

   /**
    * Add one XFile to this file system.
    * @param name the specified name of this XFile.
    * @param files the specified physical files as blocks.
    * @param nversion the file new version.
    * @param fromAppend identical this is from append function.
    * @return the created XFile if succeeded.
    */
   private XFile add(String name, BlockFile[] files, int nversion, boolean fromAppend) {
      if(bsys == null) {
         return null;
      }

      lock.lock();

      try {
         XFile file = get(name);
         boolean append = nversion != -1;

         if(fromAppend && file != null) {
            append = true;
         }

         if(!append && file != null) {
            throw new RuntimeException("File " + name +
               " already exists in this distributed file system!");
         }
         else if(append && file == null) {
            throw new RuntimeException("File " + name +
               " doesn't exist in this distributed file system!");
         }

         List<SBlock> blocks = new ArrayList<>(files.length);

         if(!append) {
            file = new XFile(name, blocks);
         }

         file.getWriteLock().lock();

         try {
            for(int i = 0; i < files.length; i++) {
               String blockid = getBlockID(name, i);
               int idx = i;

               while(append && file.getBlock(blockid) != null) {
                  blockid = getBlockID(name, ++idx);
               }

               SBlock sblock = new SBlock(name, blockid);

               if(append) {
                  file.getBlocks().add(sblock);
               }

               sblock.setLength(files[i].length());
               blocks.add(sblock);
               SNBlock snblock = new SNBlock(name, blockid);
               snblock.setLength(files[i].length());

               if(append && !fromAppend) {
                  snblock.setVersion(nversion);
               }

               sblock.add(snblock);

               try(SeekableInputStream channel = files[i].openInputStream()) {
                  ByteBufferWrapper wrapper = new ByteBufferWrapper(channel);
                  NBlock nblock = bsys.add(snblock, wrapper);

                  if(nblock == null) {
                     LOG.warn(Tool.convertUserLogInfo(
                        "Failed to add block " + snblock.getID() +
                        " to file " + files[i]));
                  }
               }
               catch(Exception ex) {
                  LOG.error("An error prevented block " +
                     snblock.getID() + " from being added to file " + files[i],
                     ex);
               }
            }
         }
         finally {
            file.getWriteLock().unlock();
         }

         xfilemap.put(file.getName(), file);
         save();
         return file;
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Rename one XFile.
    */
   @Override
   public boolean rename(String from, String to) {
      String currentOrgID = OrganizationManager.getInstance().getCurrentOrgID();
      return rename(from, currentOrgID, to, currentOrgID, false);
   }

   /**
    * Copy one XFile.
    */
   @Override
   public boolean rename(String from, String fromOrgId, String to, String toOrgId, boolean keepGlobalFile) {
      if(bsys == null) {
         return false;
      }

      lock.lock();

      try {
         XFile file = get(from);

         if(file == null) {
            throw new RuntimeException("File " + from +
                                          " does not exist in this distributed file system!");
         }

         XFile newfile = get(to);

         if(newfile != null) {
            throw new RuntimeException("File " + to +
                                          " already exists in this distributed file system!");
         }

         file.getWriteLock().lock();

         try {
            List<SBlock> sblocks = file.getBlocks();
            List<SBlock> nsblocks = new ArrayList<>(sblocks.size());

            for(int i = 0; i < sblocks.size(); i++) {
               String blkid = getBlockID(to, i);

               // unlock to allow other thread to proceed to update SBlock
               lock.unlock();

               try {
                  sblocks.get(i).waitReady(file);
               }
               finally {
                  lock.lock();
               }

               SNBlock[] snblocks = sblocks.get(i).list();
               SBlock nsnblk = new SBlock(to, blkid);
               nsnblk.setLength(sblocks.get(i).getLength());

               for(SNBlock snblock : snblocks) {
                  SNBlock nsn = new SNBlock(to, blkid);
                  nsn.setLength(snblock.getLength());

                  NBlock block = !keepGlobalFile ? bsys.rename(snblock, nsn) : bsys.copy(snblock, fromOrgId, nsn, toOrgId);

                  if(block == null) {
                     LOG.warn(Tool.convertUserLogInfo(
                        "Failed to rename block " + snblock.getID() +
                           " from " + from + " to " + to));
                  }
               }

               nsblocks.add(nsnblk);
            }

            newfile = new XFile(to, nsblocks);

            xfilemap.remove(file.getName());
            xfilemap.put(newfile.getName(), newfile);
            save();
            return true;
         }
         catch(Exception e) {
            return false;
         }
         finally {
            file.getWriteLock().unlock();
         }
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Remove the specified XFile.
    */
   @Override
   public boolean remove(XFile file) {
      if(bsys == null) {
         return false;
      }

      lock.lock();

      try {
         if(file == null || !contains(file)) {
            return true;
         }

         file.getWriteLock().lock();

         try {
            List<SBlock> sblocks = file.getBlocks();

            for(SBlock sblock : sblocks) {
               lock.unlock();

               try {
                  sblock.waitReady(file);
               }
               finally {
                  lock.lock();
               }

               SNBlock[] snblocks = sblock.list();

               for(SNBlock snblock : snblocks) {
                  boolean success = bsys.remove(snblock);

                  if(!success) {
                     LOG.warn(Tool.convertUserLogInfo(
                        "Failed to remove block " + snblock.getID() +
                           " from file " + file.getName()));
                  }
               }
            }
         }
         finally {
            file.getWriteLock().unlock();
         }

         boolean success = xfilemap.remove(file.getName()) != null;

         if(success) {
            save();
         }

         return success;
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Delete the block record for the given name.
    */
   @Override
   public boolean deleteRecord(String name, List conds) throws Exception {
      if(bsys == null) {
         return false;
      }

      lock.lock();

      try {
         XFile file = get(name);

         if(file == null) {
            throw new RuntimeException("File not found: " + name);
         }

         file.getWriteLock().lock();
         int nversion = file.getNextVersion();

         try {
            List<SBlock> sblocks = file.getBlocks();

            for(int i = 0; i < sblocks.size(); i++) {
               SBlock sblock = sblocks.get(i);
               XNode cond = (XNode) conds.get(i);

               lock.unlock();

               try {
                  sblock.waitReady(file);
               }
               finally {
                  lock.lock();
               }

               SNBlock[] snblocks = sblock.list();
               boolean success = true;

               for(SNBlock snblock : snblocks) {
                  success = deleteRecord(
                     snblock, cond, nversion);

                  if(success) {
                     sblock.setLength(snblock.getPhysicalLen());
                  }
               }

               if(!success) {
                  throw new MVExecutionException("Blocks " + sblock.getID() +
                     " delete records failed, a newly mv will be created.");
               }
            }

            file.setVersion(nversion);
            xfilemap.replace(file.getName(), file);
         }
         finally {
            file.getWriteLock().unlock();
         }

         save();
         return true;
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Update the block record for the given name.
    */
   @Override
   public boolean updateRecord(String name, Map dictMaps, Map sizes,
                               Map dimRanges, Map intRanges) throws Exception
   {
      if(bsys == null) {
         return false;
      }

      lock.lock();

      try {
         XFile file = get(name);

         if(file == null) {
            throw new RuntimeException("File not found: " + name);
         }

         file.getWriteLock().lock();
         int nversion = file.getNextVersion();

         try {
            List<SBlock> sblocks = file.getBlocks();

            for(SBlock sblock : sblocks) {
               lock.unlock();

               try {
                  sblock.waitReady(file);
               }
               finally {
                  lock.lock();
               }

               SNBlock[] snblocks = sblock.list();
               boolean success = true;

               for(SNBlock snblock : snblocks) {
                  String bid = snblock.getID();

                  if(dictMaps.get(bid) == null) {
                     continue;
                  }

                  success = updateBlock(
                     snblock, (Map[]) dictMaps.get(bid),
		     (int[]) sizes.get(bid), (int[]) dimRanges.get(bid),
                     (List<Number[]>) intRanges.get(bid), nversion);

                  if(success) {
                     sblock.setLength(snblock.getPhysicalLen());
                  }
               }

               if(!success) {
                  throw new MVExecutionException("Blocks " + sblock.getID() +
                     " update records failed, a newly mv will be created.");
               }
            }

            file.setVersion(nversion);
            xfilemap.replace(file.getName(), file);
         }
         finally {
            file.getWriteLock().unlock();
         }

         save();
         return true;
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Append the block record for the given name.
    */
   @Override
   public boolean appendRecord(String name, List<BlockFile> fileList,
                               List<Integer> blockIndexes) throws Exception
   {
      if(bsys == null) {
         return false;
      }

      lock.lock();

      try {
         XFile file = get(name);
         file.getWriteLock().lock();

         try {
            int preferred = Integer.parseInt(
               SreeEnv.getProperty("mv.preferred.block"));
            int nversion = file.getNextVersion();
            List<SBlock> sblocks = file.getBlocks();

            if(sblocks.size() <= 0) {
               remove(name);
               return add(name, fileList.toArray(new BlockFile[0]), -1, false) != null;
            }

            boolean desktop = FSService.getServer().getConfig().isDesktop();

            // 1: append files to the specified block index
            for(int i = blockIndexes.size() - 1; i >= 0; i--) {
               int blockIndex = blockIndexes.get(i);

               if(blockIndex < 0 || blockIndex >= sblocks.size()) {
                  continue;
               }

               BlockFile ifile = fileList.get(i);
               SBlock block = sblocks.get(blockIndex);

               lock.unlock();

               try {
                  block.waitReady(file);
               }
               finally {
                  lock.lock();
               }

               SNBlock[] snblocks = block.list();
               String bid = block.getID();
               boolean success = false;

               for(SNBlock snblock : snblocks) {
                  boolean result = appendRecord(snblock, ifile, nversion);

                  if(result) {
                     block.setLength(snblock.getPhysicalLen());
                     success = true;
                  }
               }

               if(!success) {
                  throw new MVExecutionException("Blocks " + bid +
                     " append records failed, a newly mv need to be recreated.");
               }

               blockIndexes.remove(i);
               fileList.remove(i);
            }

            // we has checked if one block could append to the last block
            // when create the subMV, if can we specified it append to
            // the last block, so it can append in the #1.

            for(SBlock isblock : sblocks) {
               lock.unlock();

               try {
                  isblock.waitReady(file);
               }
               finally {
                  lock.lock();
               }

               SNBlock[] snblocks = isblock.list();

               for(SNBlock snblock : snblocks) {
                  NBlock nblock = bsys.get(snblock.getID());
                  nblock.setVersion(nversion);
               }
            }

            if(fileList.size() > 0) {
               BlockFile[] remainds = fileList.toArray(new BlockFile[0]);
               add(name, remainds, nversion, false);
            }

            bsys.save();
            file.setVersion(nversion);
            xfilemap.replace(file.getName(), file);
         }
         finally {
            file.getWriteLock().unlock();
         }

         save();
         return true;
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Dispose this file system.
    */
   @Override
   public void dispose() {
      super.dispose();

      if(bsys != null) {
         bsys.dispose();
         bsys = null;
      }
   }

   /**
    * ByteBufferWrapper wraps channel, and performs like XReadBuffer.
    */
   static final class ByteBufferWrapper implements XReadBuffer {
      private static ByteBuffer readData(ReadableByteChannel channel, int len)
         throws IOException
      {
         ByteBuffer buffer = ByteBuffer.allocate(len);
         buffer.order(ByteOrder.BIG_ENDIAN);
         channel.read(buffer);
         XSwapUtil.flip(buffer);
         return buffer;
      }

      public ByteBufferWrapper(SeekableInputStream channel) throws IOException {
         this.channel = channel;
         remaining = channel.size();
      }

      @Override
      public boolean hasRemaining() {
         return remaining != 0;
      }

      @Override
      public int getRemaining() {
         return (int) remaining;
      }

      @Override
      public void add(ByteBuffer buf) {
         throw new RuntimeException("Unsupported operation is called!");
      }

      @Override
      public ByteBuffer read(ByteBuffer buf) throws IOException {
         if(buf == null) {
            buf = CommUtil.createBlockBuffer();
         }

         int length = channel.read(buf);
         XSwapUtil.flip(buf);

         if(length > 0) {
            remaining -= length;
         }

         return buf;
      }

      @Override
      public byte readByte() throws IOException {
         remaining -= 1;
         ByteBuffer buf = readData(channel, 1);
         byte n = buf.get();
         // may be a direct byte buffer
         return n;
      }

      @Override
      public double readDouble() throws IOException {
         remaining -= 8;
         ByteBuffer buf = readData(channel, 8);
         //TODO is this right? should it be buf.getDouble() instead?
         double n = buf.getLong();
         // may be a direct byte buffer
         return n;
      }

      @Override
      public float readFloat() throws IOException {
         remaining -= 4;
         ByteBuffer buf = readData(channel, 4);
         float n = buf.getFloat();
         // may be a direct byte buffer
         return n;
      }

      @Override
      public int readInt() throws IOException {
         remaining -= 4;
         ByteBuffer buf = readData(channel, 4);
         int n = buf.getInt();
         // may be a direct byte buffer
         return n;
      }

      @Override
      public long readLong() throws IOException {
         remaining -= 8;
         ByteBuffer buf = readData(channel, 8);
         long n = buf.getLong();
         // may be a direct byte buffer
         return n;
      }

      @Override
      public short readShort() throws IOException {
         remaining -= 2;
         ByteBuffer buf = readData(channel, 2);
         short n = buf.getShort();
         // may be a direct byte buffer
         return n;
      }

      @Override
      public String readString() throws IOException {
         throw new RuntimeException("Unsupported operation is called!");
      }

      private SeekableInputStream channel;
      private long remaining;
   }

   private static final Logger LOG = LoggerFactory.getLogger(LocalFileSystem.class);
   private XBlockSystem bsys;
}
