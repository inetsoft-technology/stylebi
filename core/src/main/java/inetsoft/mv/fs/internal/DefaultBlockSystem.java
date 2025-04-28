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

import inetsoft.mv.comm.XReadBuffer;
import inetsoft.mv.fs.*;
import inetsoft.mv.util.TransactionChannel;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.security.Organization;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.util.*;
import inetsoft.util.log.LogUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;

/**
 * DefaultBlockSystem, the default implementation of XBlockSystem.
 *
 * @author InetSoft Technology
 * @version 10.2
 */
public final class DefaultBlockSystem implements XBlockSystem, XMLSerializable {
   /**
    * Constructor.
    */
   public DefaultBlockSystem(FSConfig config, String orgId) {
      this.config = config;
      this.orgId = orgId;

      Cluster cluster = Cluster.getInstance();
      String id =  getClass().getName() + (orgId == null ? "" : "." + orgId.toLowerCase());
      String mapId = id + ".blocks";
      blocks = new LocalClusterMap<>(mapId, cluster, cluster.getMap(mapId));
      lock = cluster.getLock(id + ".lock");
      lastLoad = cluster.getLong(id + ".lastLoad");

      init();
   }

   /**
    * Get the config of this file system.
    */
   @Override
   public FSConfig getConfig() {
      return config;
   }

   /**
    * Add one XBlock to this block system.
    * @param block the specified XBlock.
    * @param read the specified input.
    */
   @Override
   public NBlock add(XBlock block, XReadBuffer read) {
      lock.lock();
      boolean success = false;

      try {
         if(block == null) {
            return null;
         }

         BlockFile nfile = getFile(block.getID());
         NBlock nblock;

         try(TransactionChannel channel = nfile.openWriteChannel()) {
            long val = block.getLength();
            long len = 0;

            while(len < val) {
               ByteBuffer buf = read.read(null);
               len += buf.remaining();

               while(buf.hasRemaining()) {
                  channel.write(buf);
               }
               // may be a direct byte buffer
            }

            channel.commit();
            nblock = new NBlock(block.getParent(), block.getID());
            nblock.setPhysicalLen(val);
            nblock.setLength(block.getLength());
            nblock.setVersion(block.getVersion());
            blocks.put(nblock.getID(), nblock);
         }

         save();
         success = true;
         return nblock;
      }
      catch(IOException ex) {
         LOG.error("Failed to add block", ex);
         return null;
      }
      finally {
         // not success? remove this block to make sure the fs clean
         if(!success && block != null) {
            try {
               BlockFile nfile = getFile(block.getID());

               if(nfile != null) {
                  deleteFile(nfile);
               }
            }
            catch(Exception ex) {
               // ignore it
            }
         }

         lock.unlock();
      }
   }

   /**
    * Rename one XBlock.
    */
   @Override
   public NBlock rename(XBlock from, XBlock to) {
      String currentOrgID = OrganizationManager.getInstance().getCurrentOrgID();
      return copy0(from, currentOrgID, to, currentOrgID, true);
   }

   @Override
   public NBlock rename(XBlock from, XBlock to, String fromOrgId, String toOrgId) {
      return copy0(from, fromOrgId, to, toOrgId, true);
   }

   /**
    * Copy one XBlock.
    */
   @Override
   public NBlock copy(XBlock from, String fromOrgId, XBlock to, String toOrgId) {
      return copy0(from, fromOrgId, to, toOrgId, false);
   }

   private NBlock copy0(XBlock from, String fromOrgId, XBlock to, String toOrgId, boolean rename) {
      lock.lock();

      try {
         NBlock nblock = new NBlock(to.getParent(), to.getID());
         nblock.setLength(to.getLength());
         nblock.setVersion(to.getVersion());
         BlockFile oldFile = getFile(from);

         if(rename) {
            blocks.remove(from.getID());
         }

         if(oldFile != null) {
            BlockFile newFile = getFile(to.getID());
            // after renamed, the oldFile will not exist, so its length is 0
            nblock.setPhysicalLen(oldFile.length(fromOrgId));

            if(rename) {
               BlockFileStorage.getInstance().rename(orgId, oldFile, newFile);
            }
            else {
               BlockFileStorage.getInstance().copy(oldFile, fromOrgId, newFile, toOrgId);
            }

            blocks.put(nblock.getID(), nblock);
         }
         else {
            nblock = null;
         }

         save();
         return nblock;
      }
      catch(Exception e) {
         LOG.error("Failed to rename block file", e);
         return null;
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Check if contains the specified block for the given block id.
    */
   @Override
   public boolean contains(String id) {
      lock.lock();

      try {
         return blocks.containsKey(id);
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Check if contains the specified block for the given XBlock.
    */
   @Override
   public boolean contains(XBlock block) {
      return block != null && contains(block.getID());
   }

   /**
    * Get the NBlock for the given block id.
    */
   @Override
   public NBlock get(String id) {
      lock.lock();

      try {
         return blocks.get(id);
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Get the internal NBlock for the given XBlock.
    */
   @Override
   public NBlock get(XBlock block) {
      return block == null ? null : get(block.getID());
   }

   @Override
   public NBlock update(String id) {
      // Bug #61851, propagate the change for this key throughout the cluster
      return blocks.replace(id, blocks.get(id));
   }

   /**
    * Get the physical file for the given XBlock.
    * @return the physical file if any, null otherwise.
    */
   @Override
   public BlockFile getFile(XBlock block) {
      return block == null ? null : getFile(block.getID());
   }

   @Override
   public BlockFile getFile(String id) {
      // @by jasons, limit the file name to 64 characters (Macquarie). Use the
      // checksum of the name as the suffix to ensure uniqueness.
      String file = Tool.getUniqueName(id, 60);
      return new StorageBlockFile(file + ".blk");
   }

   /**
    * List all XBlocks in this block system.
    * @param clone true to clone the blocks.
    */
   @Override
   public NBlock[] list(boolean clone) {
      return list(null, clone);
   }

   /**
    * List the NBlocks match the specified XBlockFilter.
    * @param filter the specified XBlockFilter.
    * @param clone true to clone the blocks.
    */
   @Override
   public NBlock[] list(XBlockFilter filter, boolean clone) {
      lock.lock();

      try {
         List<NBlock> subs = new ArrayList<>();

         for(NBlock blk : blocks.values()) {
            if(filter == null || filter.accept(blk)) {
               subs.add(clone ? (NBlock) blk.clone() : blk);
            }
         }

         return subs.toArray(new NBlock[0]);
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Remove the XBlock for the given id.
    */
   @Override
   public boolean remove(String id) {
      lock.lock();

      try {
         BlockFile file = getFile(id);

         if(file != null && file.exists()) {
            deleteFile(file);
         }

         boolean changed = blocks.remove(id) != null;

         if(changed) {
            save();
         }

         file = getFile(id);
         return file == null || !file.exists();
      }
      finally {
         lock.unlock();
      }
   }

   private void deleteFile(BlockFile file) {
      try {
         BlockFileStorage.getInstance().delete(file.getName());
      }
      catch(Exception ex) {
         LOG.warn("Failed to delete block file: " + file, ex);
         LogUtil.saveStackTrace("mvDeleteFailed", "txt");
      }
   }

   /**
    * Remove the specified XBlock.
    */
   @Override
   public boolean remove(XBlock block) {
      return block == null || remove(block.getID());
   }

   /**
    * Remove the XBlocks match the specified XBlockFilter.
    */
   @Override
   public boolean remove(XBlockFilter filter) {
      lock.lock();
      boolean removed = true;

      try {
         for(NBlock blk : blocks.values()) {
            if(filter == null || filter.accept(blk)) {
               removed &= remove(blk);
            }
         }
      }
      finally {
         lock.unlock();
      }

      return removed;
   }

   /**
    * Update this block system with uptodate information.
    */
   @Override
   public void update() {
      lock.lock();

      try {
         NBlock[] nodes = list(false);

         for(NBlock node : nodes) {
            BlockFile file = getFile(node);
            long len = file == null || !file.exists() ? -1 : file.length();
            node.setPhysicalLen(len);
         }
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Updates the in-memory cache from the configuration file.
    */
   @Override
   public void refresh(boolean force) {
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      lock.lock();

      try {
         writer.println("<BlockSystem>");
         writer.print("<Blocks>");
         NBlock[] nodes = list(false);

         for(NBlock node : nodes) {
            node.writeXML(writer);
         }

         writer.println("</Blocks>");
         writer.println("</BlockSystem>");
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Method to parse an xml segment about parameter element information.
    */
   @Override
   public void parseXML(Element elem) throws Exception {
      lock.lock();

      try {
         Element valuesNode = Tool.getChildNodeByTagName(elem, "Blocks");
         NodeList valuesList =
            Tool.getChildNodesByTagName(valuesNode, "XBlock");
         blocks.clear();

         for(int i = 0; i < valuesList.getLength(); i++) {
            Element vnode = (Element) valuesList.item(i);
            NBlock nblock = new NBlock();
            nblock.parseXML(vnode);
            blocks.put(nblock.getID(), nblock);
         }
      }
      finally {
         lock.unlock();
      }

      update();
   }

   /**
    * Get the list of fs.bs.files.
    */
   private String[] getPaths() {
      String val = SreeEnv.getEarlyLoadedProperty("fs.bs.files");

      if(val == null) {
         val = "";
      }

      String[] files = val.split(",");

      if(orgId != null && !Tool.equals(Organization.getDefaultOrganizationID(), orgId)) {
         for(int i = 0; i < files.length; i++) {
            files[i] = files[i].trim();
            files[i] = getOrgFileName(files[i]);
         }
      }

      return files;
   }

   private String getOrgFileName(String oldName) {
      if(Tool.isEmptyString(oldName)) {
         return oldName;
      }

      int index = oldName.lastIndexOf("/");

      if(index == -1) {
         return orgId + "/" + oldName;
      }
      else {
         return oldName.substring(0, index) + "/" + orgId + oldName.substring(index);
      }
   }

   /**
    * Serializable the block map to file disk.
    */
   @Override
   public void save() {
      DataSpace space = DataSpace.getDataSpace();
      String[] paths = getPaths();

      for(String path : paths) {
         lock.lock();

         try(DataSpace.Transaction tx = space.beginTransaction();
             OutputStream out = tx.newStream(null, path))
         {
            PrintWriter writer =
               new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
            writeXML(writer);
            writer.flush();
            tx.commit();
         }
         catch(Throwable exc) {
            LOG.error("Failed to save block map: {}", path, exc);
         }
         finally {
            lock.unlock();
         }
      }
   }

   /**
    * Init blocks map from file disk.
    */
   private void init() {
      lock.lock();

      try {
         if(lastLoad.get() == 0L) {
            DataSpace space = DataSpace.getDataSpace();
            String[] paths = getPaths();

            for(String path : paths) {
               try(InputStream input = space.getInputStream(null, path)) {
                  if(input == null) {
                     continue;
                  }

                  lastLoad.set(space.getLastModified(null, path));
                  Document document = Tool.parseXML(input);
                  Element root = document.getDocumentElement();
                  parseXML(root);
                  LOG.debug("Initialized block system from: {}", path);
                  return;
               }
               catch(Exception ex) {
                  LOG.warn("Failed to initialize block system from: {}", path, ex);
               }
            }
         }
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
      try {
         blocks.close();
         lastLoad.set(0L);
      }
      catch(Exception e) {
         LOG.warn("Failed to close distributed map", e);
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(DefaultBlockSystem.class);
   private final Lock lock;
   private final LocalClusterMap<String, NBlock> blocks;
   private final DistributedLong lastLoad;
   private final FSConfig config;
   private final String orgId;
}
