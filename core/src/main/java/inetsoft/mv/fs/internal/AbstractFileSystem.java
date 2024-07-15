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
package inetsoft.mv.fs.internal;

import inetsoft.mv.fs.*;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.*;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;

/**
 * AbstractFileSystem, implements the common APIs for XFileSystem.
 *
 * @author InetSoft Technology
 * @version 10.2
 */
public abstract class AbstractFileSystem implements XFileSystem, XMLSerializable {
   /**
    * Create a proper file system.
    */
   public static AbstractFileSystem createFileSystem(XServerNode server) {
      AbstractFileSystem fs = new LocalFileSystem(server);
      fs.init();
      return fs;
   }

   /**
    * Constructor.
    */
   public AbstractFileSystem(XServerNode server) {
      super();

      this.server = server;
      config = server.getConfig();
      ts = System.currentTimeMillis();

      Cluster cluster = Cluster.getInstance();
      String mapId = getClass().getName() + ".map";
      xfilemap = new LocalClusterMap<>(mapId, cluster, cluster.getMap(mapId));
      lock = cluster.getLock(getClass().getName() + ".lock");
      lastLoad = cluster.getLong(getClass().getName() + ".lastLoad");
   }

   /**
    * Get the config of this file system.
    */
   @Override
   public final FSConfig getConfig() {
      return config;
   }

   /**
    * Check if the specified file is contained.
    */
   @Override
   public final boolean contains(String name) {
      return xfilemap.containsKey(name);
   }

   /**
    * Check if the specified file is contained.
    */
   @Override
   public final boolean contains(XFile file) {
      return file != null && contains(file.getName());
   }

   /**
    * Get the XFile for the given name.
    */
   @Override
   public final XFile get(String name) {
      return xfilemap.get(name);
   }

   /**
    * Get the internal XFile for the given XFile.
    */
   @Override
   public final XFile get(XFile file) {
      return file == null ? null : get(file.getName());
   }

   /**
    * List all XFiles in this file system.
    */
   @Override
   public final XFile[] list() {
      return list(null);
   }

   /**
    * List the XFiles match the specified XFileFilter.
    * @param filter the specified XFileFilter.
    */
   @Override
   public final XFile[] list(XFileFilter filter) {
      List<XFile> list = new ArrayList<>(xfilemap.values());
      List<XFile> subs = new ArrayList<>();

      for(XFile file : list) {
         if(filter == null || filter.accept(file)) {
            subs.add(file);
         }
      }

      return subs.toArray(new XFile[0]);
   }

   /**
    * Remove the XFile by its name.
    */
   @Override
   public final boolean remove(String name) {
      XFile file = get(name);
      return remove(file);
   }

   /**
    * Remove the XFiles match the specified XFileFilter.
    */
   @Override
   public final boolean remove(XFileFilter filter) {
      // not alive? no change is permitted
      if(!alive) {
         return false;
      }

      boolean removed = true;

      for(XFile file : xfilemap.values()) {
         if(filter == null || filter.accept(file)) {
            removed &= remove(file);
         }
      }

      return removed;
   }

   /**
    * Update the XFileSystem with one XNodeReport.
    */
   public final void update(XBlockSystem bsys) {
      Map<String, List<NBlock>> fileblocks = new HashMap<>();
      List<XFile> xfiles = new ArrayList<>();
      final NBlock[] nodes = bsys.list(true);

      for(NBlock node : nodes) {
         List<NBlock> data = fileblocks.get(node.getParent());

         if(data == null) {
            data = new ArrayList<>();
         }

         data.add(node);
         fileblocks.put(node.getParent(), data);
      }

      for(String name : fileblocks.keySet()) {
         XFile file = get(name);

         if(file == null) {
            continue;
         }

         xfiles.add(file);
      }

      for(XFile file : xfiles) {
         List<NBlock> fileBlocks = fileblocks.get(file.getName());

         // file write lock is required
         file.getWriteLock().lock();

         try {
            SBlock[] sblocks = file.list();

            for(SBlock sblock : sblocks) {
               sblock.remove();

               for(NBlock fileBlock : fileBlocks) {
                  sblock.add(new SNBlock(fileBlock));
               }
            }
         }
         finally {
            file.getWriteLock().unlock();
         }
      }

      XFile[] all = list();

      for(XFile xFile : all) {
         // remove the file blocks which is not contained in the data node
         if(!fileblocks.containsKey(xFile.getName())) {
            xFile.getWriteLock().lock();

            try {
               List<SBlock> sblocks = xFile.getBlocks();

               for(SBlock sblock : sblocks) {
                  sblock.remove();
               }
            }
            finally {
               xFile.getWriteLock().unlock();
            }
         }
      }
   }

   /**
    * Updates the in-memory cache from the configuration file.
    */
   @Override
   public final void refresh(XBlockSystem bsys, boolean force) {
      if(force) {
         lock.lock();

         try {
            update(bsys);
         }
         finally {
            lock.unlock();
         }
      }
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public final void writeXML(PrintWriter writer) {
      lock.lock();

      try {
         writer.println("<FileSystem>");
         writer.print("<Files>");
         XFile[] xfiles = list();

         for(XFile xfile : xfiles) {
            xfile.writeXML(writer);
         }

         writer.println("</Files>");
         writer.println("</FileSystem>");
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Method to parse an xml segment about parameter element information.
    */
   @Override
   public final void parseXML(Element elem) throws Exception {
      lock.lock();

      Map<String, XFile> xfilemap = new ConcurrentHashMap<>();

      try {
         Element valuesNode = Tool.getChildNodeByTagName(elem, "Files");
         NodeList valuesList = Tool.getChildNodesByTagName(valuesNode, "XFile");

         for(int i = 0; i < valuesList.getLength(); i++) {
            Element vnode = (Element) valuesList.item(i);
            XFile xfile = new XFile();
            xfile.parseXML(vnode);
            xfilemap.put(xfile.getName(), xfile);
         }
      }
      finally {
         this.xfilemap.clear();
         this.xfilemap.putAll(xfilemap);
         lock.unlock();
      }
   }

   /**
    * Get the block id path from the given name and index.
    * @return the new block id.
    */
   protected final String getBlockID(String name, int idx) {
      return name + "-" + ts + '-' + idx;
   }

   /**
    * Get the list of fs.files.
    */
   private String[] getPaths() {
      String val = SreeEnv.getEarlyLoadedProperty("fs.files");

      // SreeEnv.getProperty("fs.files","") always returns "",
      // overridding the correct value from default.properties,
      // which SreeEnv.getProperty("fs.files") returns.
      if(val == null) {
         val = "";
      }

      return val.split(",");
   }

   /**
    * Serializable the xfile map to data space.
    */
   protected final void save() {
      DataSpace space = DataSpace.getDataSpace();
      String[] paths = getPaths();

      for(String path : paths) {
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
            LOG.error("Failed to save file map: {}", path, exc);
         }
      }
   }

   /**
    * Initialize files map from file disk.
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
                  LOG.debug("Initialized distributed file system from: {}", path);
                  return;
               }
               catch(Exception ex) {
                  LOG.warn("Failed to initialize distributed file system from: {}", path, ex);
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
         xfilemap.close();
      }
      catch(Exception e) {
         LOG.warn("Failed to close distributed map", e);
      }
   }

   protected final FSConfig config;
   protected final XServerNode server;
   protected final LocalClusterMap<String, XFile> xfilemap;
   protected final Lock lock;
   private DistributedLong lastLoad;
   private final long ts;
   private boolean alive = true;
   private boolean idle = true;

   private static final Logger LOG = LoggerFactory.getLogger(AbstractFileSystem.class);
}
