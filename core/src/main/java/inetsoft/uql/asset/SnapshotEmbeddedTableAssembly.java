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
package inetsoft.uql.asset;

import inetsoft.sree.SreeEnv;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.table.*;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.util.*;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.nio.file.FileAlreadyExistsException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Embedded table assembly for snapshot, mainly deal with large data.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class SnapshotEmbeddedTableAssembly extends EmbeddedTableAssembly
   implements ActionListener, AssetChangeListener {

   /**
    * Constructor.
    */
   public SnapshotEmbeddedTableAssembly() {
      super();
      init();
   }

   private void init() {
      shareData = true;
      prefix = count.getAndIncrement();
      snapshots.add(new WeakReference<>(this));
   }

   /**
    * Constructor.
    */
   public SnapshotEmbeddedTableAssembly(Worksheet ws, String name) {
      super(ws, name);
      init();
      addAssetChangeListener(ws);
   }

   /**
    * Set the worksheet.
    *
    * @param ws the specified worksheet.
    */
   @Override
   public void setWorksheet(Worksheet ws) {
      super.setWorksheet(ws);

      addAssetChangeListener(ws);
   }

   /**
    * Print key to identify embedded data.
    */
   @Override
   protected boolean printEmbeddedDataKey(PrintWriter writer) throws Exception {
      if(dataPaths != null) {
         writer.print("dataPaths[");
         writer.print(Arrays.toString(dataPaths));
         writer.print("]");
      }
      else {
         writer.print("stable:" + stable);
      }

      writer.print(",rowCnt:" + rowCnt);

      return true;
   }

   /**
    * Add asset change listener. Delete external data file when thw whole
    * worksheet is removed.
    */
   private void addAssetChangeListener(Worksheet ws) {
      ws.removeActionListener(this);
      ws.addActionListener(this);

      AssetRepository rep = getAssetRepository();

      if(rep != null) {
         rep.removeAssetChangeListener(this);
         rep.addAssetChangeListener(this);
      }
   }

   @Override
   protected void finalize() throws Throwable {
      super.finalize();

      ws.removeActionListener(this);

      AssetRepository rep = getAssetRepository();

      if(rep != null) {
         rep.removeAssetChangeListener(this);
      }
   }

   /**
    * Get the embedded data.
    *
    * @return the embedded data.
    */
   public synchronized XEmbeddedTable getEmbeddedData() {
      return new XEmbeddedTable(getTable());
   }

   /**
    * Set the embedded data.
    *
    * @param data the specified embedded table.
    */
   @Override
   public void setEmbeddedData(XEmbeddedTable data) {
      synchronized(this) {
         super.setEmbeddedData(data);

         this.setTable(data.getDataTable());
         columns = getColumnSelection(false);
         this.creators = null;
         this.lflags = null;
         this.headers = null;
         this.stable = null;
         this.rowCnt = -1;
         this.fileDirty = true;
      }

      data.addDataChangeListener(this);
   }

   @Override
   public synchronized void pasted() {
      // make sure data files are saved to a new file instead of sharing with original assembly
      this.dataPaths = null;
      this.fileDirty = true;
      prefix = count.getAndIncrement();
   }

   /**
    * Set table.
    */
   public void setTable(XSwappableTable stable) {
      this.stable = stable;

      if(originalSTable == null) {
         originalSTable = stable;
      }
   }

   public void setOriginalSTable(XSwappableTable stable) {
      originalSTable = stable;
   }

   /**
    * Get table.
    */
   public XSwappableTable getTable() {
      if(stable == null) {
         synchronized(this) {
            if(stable == null) {
               initTable();
            }
         }
      }

      return stable;
   }

   /**
    * Get table.
    */
   public XSwappableTable getOriginalTable() {
      if(getTable() != null) {
         return originalSTable;
      }

      return null;
   }

   /**
    * Write out data content of this table.
    */
   @Override
   public void writeData(JarOutputStream out) {
      List<File> list = stable.getFilesList();
      String tprefix = getTablePrefix();

      for(File file : list) {
         try {
            String fileName = file.getName();
            String dataPath = containsTablePrefix(fileName) ? fileName : tprefix + fileName;
            ZipEntry zipEntry = new ZipEntry("__WS_EMBEDDED_TABLE_" + PDATA + "^_^" + dataPath);
            out.putNextEntry(zipEntry);
            // 1: should not use dataspace to release the input, because
            // the input is not opened by space
            // 2: should not release the output
            FileInputStream in = new FileInputStream(file);
            Tool.fileCopy(in, false, out, false);
            in.close();
         }
         catch(Exception exc) {
            LOG.error("Failed to serialize data", exc);
         }
      }
   }

   @Override
   public void actionPerformed(ActionEvent e) {
   }

   public void deleteDataFiles(String reason) {
      try {
         for(int i = 0; dataPaths != null && i < dataPaths.length; i++) {
            String path = dataPaths[i] + "_s.tdat";

            if(LOG.isDebugEnabled()) {
               LOG.debug("Deleting snapshot data file: {} [{}] " + this, path, reason);
            }

            try {
               EmbeddedTableStorage.getInstance().removeTable(path);
            }
            catch(IOException e) {
               if(LOG.isDebugEnabled()) {
                  LOG.warn("Failed to delete snapshot data file: " + path, e);

               }
               else {
                  LOG.warn("Failed to delete snapshot data file: {}", path);
               }
            }
         }

         dataPaths = null;
         fileDirty = false;
      }
      catch(Exception ex) {
         LOG.debug("Failed to delete data file", ex);
      }
   }

   @Override
   public void assetChanged(AssetChangeEvent event) {
      if(event == null) {
         return;
      }

      int changeType = event.getChangeType();
      AssetRepository rep = getAssetRepository();

      if(changeType != AssetChangeEvent.ASSET_DELETED || rep == null) {
         return;
      }

      try {
         AbstractSheet sheet = event.getSheet();

         if(sheet == null || sheet != getSheet()) {
            return;
         }

         deleteDataFiles("Worksheet deleted: " + getName() + " " + event.getReason());
      }
      catch(Exception ex) {
         LOG.debug("Failed to delete data file", ex);
      }
   }

   private AssetRepository getAssetRepository() {
      AssetRepository rep = null;

      try {
         rep = AssetUtil.getAssetRepository(false);
      }
      catch(Exception ex) {
         LOG.debug("Failed to get asset repository", ex);
      }

      return rep;
   }

   /**
    * Write embedded data.
    * @param writer the specified writer.
    */
   @Override
   protected synchronized void writeEmbeddedData(PrintWriter writer) {
      try {
         XSwappableTable stable = getTable();

         // make sure table is inited
         if(stable == null) {
            return;
         }

         if(!fileDirty && dataPaths != null) {
            // if data file has changed, don't reuse it otherwise the row count
            // and data may be out of sync. (56584)
            fileDirty = Arrays.stream(dataPaths)
               .anyMatch(p -> getLastModified(p, Long.MAX_VALUE) > dataTS);
         }

         XTableFragment[] stables = stable.getTables();

         //Temporary code for Test case, rewrite the snapshot with current version logic.
         if("true".equals(SreeEnv.getProperty("try.parse.old.version.snapshot")) && stables != null
            && Arrays.stream(stables).anyMatch(t -> t != null && t.isOldVersionSnapWrapFile()))
         {
            if(swapOldVersionSnapshotTables()) {
               fileDirty = true;
            }
         }

         if(dataPaths == null || fileDirty) {
            writeDataFiles(stable);
         }

         // init row count after load complete
         stable.moreRows(XTable.EOT);
         rowCnt = stable.getRowCount();

         writer.println("<sembeddedData row=\"" + rowCnt + "\">");

         if(columns != null) {
            columns.writeXML(writer);
         }

         writer.println("<paths>");

         if(dataPaths != null) {
            for(String dataPath : dataPaths) {
               if(dataPathsLoadVersion.get(dataPath) != null) {
                  writer.println("<path loadVersion=\"" + dataPathsLoadVersion.get(dataPath) +
                                    "\"><![CDATA[");
               }
               else {
                  writer.println("<path><![CDATA[");
               }

               writer.println(dataPath);
               writer.println("]]></path>");
            }
         }

         writer.println("</paths>");
         writer.println("<headers>");

         if(headers == null) {
            headers = new Object[stable.getColCount()];

            for(int c = 0; c < headers.length; c++) {
               headers[c] = stable.getObject(0, c);
            }
         }

         if(creators == null) {
            XTableColumnCreator[] xcreators = stable.getCreators();
            creators = new String[xcreators.length];

            for(int i = 0; i < xcreators.length; i++) {
               creators[i] = xcreators[i].getClass().getName();
               int idx = creators[i].lastIndexOf("$");

               if(idx > 0) {
                  creators[i] = creators[i].substring(0, idx);
               }
            }
         }

         if(lflags == null) {
            XTableFragment[] tables = stable.getTables();
            lflags = new boolean[headers.length];

            if(tables != null && tables.length > 0 && tables[0] != null) {
               XTableColumn[] columns = tables[0].getColumns();

               for(int i = 0; i < lflags.length; i++) {
                  if(columns[i] instanceof XBDDoubleColumn) {
                     lflags[i] = ((XBDDoubleColumn) columns[i]).isLong();
                  }
                  else {
                     lflags[i] = true;
                  }
               }
            }
         }

         for(int i = 0; i < headers.length; i++) {
            writer.print("<header ");

            if(headers[i] != null) {
               writer.print("name=\"" + Tool.escape(headers[i].toString()) + "\" ");
            }

            writer.println("creator=\"" + creators[i] + "\" lflag=\"" + lflags[i] + "\"/>");
         }

         writer.println("</headers>");
         writer.println("</sembeddedData>");
      }
      catch(Exception exc) {
         LOG.error("Failed to write data XML", exc);
      }
   }

   private static long getLastModified(String path, long def) {
      EmbeddedTableStorage storage = EmbeddedTableStorage.getInstance();

      try {
         return storage.getLastModified(path + "_s.tdat").toEpochMilli();
      }
      catch(FileNotFoundException e) {
         return def;
      }
   }

   /**
    * Write data parts to pdata.
    */
   private synchronized void writeDataFiles(XSwappableTable stable) throws Exception {
      String tprefix = getTablePrefix();
      String[] prefixes = stable.getPrefixes();
      XTableFragment[] tables = stable.getTables();
      fileDirty = false;
      dataPathsLoadVersion.clear();
      String[] dataPaths = new String[prefixes.length];

      if(dataPaths.length > 0) {
         List<File> files = stable.getFilesList();

         for(int i = 0; i < dataPaths.length; i++) {
            dataPaths[i] = tprefix + prefixes[i];

            if(tables != null && i < tables.length && tables[i].isOldVersionSnapWrapFile()) {
               if(tables[i].isParseWithKryo4()) {
                  dataPathsLoadVersion.put(dataPaths[i], "13.0");
               }
            }
         }

         if(files.size() != prefixes.length) {
            for(XTableFragment table : tables) {
               if(table.getFiles().isEmpty()) {
                  LOG.error("Table swap file is missing: " + table.getSwapFile());
                  return;
               }
            }
         }

         for(int i = 0; i < dataPaths.length; i++) {
            if(!dataFileExist(dataPaths[i])) {
               try(InputStream input = new FileInputStream(files.get(i))) {
                  EmbeddedTableStorage.getInstance().writeTable(dataPaths[i] + "_s.tdat", input);
                  dataTS = System.currentTimeMillis();
               }
            }
         }

         // don't change the original datafiles when save worksheet for autosave.
         if(!Worksheet.isTemp() && shouldDeleteOldFiles && this.dataPaths != null &&
            !Arrays.equals(dataPaths, this.dataPaths) &&
            dataPaths.length == this.dataPaths.length)
         {
            String[] odataPaths = this.dataPaths;
            // only delete file if different files are written. (50334)
            deleteDataFiles("Data file overwritten: " + getName() + " files: " +
                               Arrays.toString(this.dataPaths) + " replaced by: " +
                               Arrays.toString(dataPaths));

            /* replacing data file with newly updated file is problematic. if an existing
               column is swapped out, when it's swapped back in, it will read from the new
               file, which may be out of sync with the existing column (e.g. length).
               since the new storage based implementation copies the data into a local cache
               file, there is no need to keep the old data file.

               @by anton: The column types may also be out of sync. For example, the old column may
               be an XObjectColumn and the new one a XStringColumn, with incompatible serialization.

            if(LOG.isDebugEnabled()) {
               LOG.debug("Reuse data files: " + Arrays.toString(odataPaths));
            }

            // if new files are written, reuse the same file names if possible so open
            // ws won't find the data files missing. this is not fool-prove which would
            // require the old files not removed until all reference are gone, possibly
            // through some form of reference counting. (50334)
            for(int i = 0; i < dataPaths.length; i++) {
               try {
                  EmbeddedTableStorage.getInstance().renameTable(
                     dataPaths[i] + "_s.tdat", odataPaths[i] + "_s.tdat");
                  dataPaths[i] = odataPaths[i];
               }
               catch(IOException e) {
                  LOG.warn("Failed to rename table", e);
               }
            }
            */

            updateDataFiles(stable, dataPaths, odataPaths);
         }
      }

      this.dataPaths = dataPaths;
      shouldDeleteOldFiles = true;
   }

   // the snapshot assembly may be cloned in CompositeTableAssembly.getTableAssemblies(true)
   // when called in MirrorQuery, so the cloned copy may replace the dataPaths, which may
   // result in the base snapshot assembly containing the original dataPaths. that would
   // cause snapshot missing error when the base is used later. (58476)
   // this method makes sure all in-memory snapshot assemblies referencing the replaced
   // data files point to the new files instead of deleted files.
   private static void updateDataFiles(XSwappableTable stable, String[] dataPaths,
                                       String[] odataPaths)
   {
      synchronized(snapshots) {
         for(int i = snapshots.size() - 1; i >= 0; i--) {
            SnapshotEmbeddedTableAssembly base = snapshots.get(i).get();

            if(base != null) {
               if(Arrays.equals(odataPaths, base.dataPaths)) {
                  base.stable = stable;
                  base.dataPaths = dataPaths.clone();
                  base.dataPathsUpdated = true;
               }
            }
            else {
               snapshots.remove(i);
            }
         }
      }
   }

   /**
    * Get table prefix.
    */
   private String getTablePrefix() {
      return "t" + prefix + "_";
   }

   private boolean containsTablePrefix(String fileName) {
      return fileName != null && fileName.matches("^t[0-9]+_.*$");
   }

   /**
    * Parse embedded data.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseEmbeddedData(Element elem) throws Exception {
      Element delem = Tool.getChildNodeByTagName(elem, "sembeddedData");

      if(delem == null) {
         return;
      }

      rowCnt = Integer.parseInt(Tool.getAttribute(delem, "row"));

      Element cnode = Tool.getChildNodeByTagName(delem, "ColumnSelection");
      columns = new ColumnSelection();

      if(cnode != null) {
         columns.parseXML(cnode);
      }

      Element pnode = Tool.getChildNodeByTagName(delem, "paths");

      if(pnode == null) {
         return;
      }

      NodeList nodes = Tool.getChildNodesByTagName(pnode, "path");
      dataPaths = new String[nodes.getLength()];
      dataPathsLoadVersion = new HashMap<>();

      for(int i = 0; i < nodes.getLength(); i++) {
         Element node = (Element) nodes.item(i);
         String loadVersion = Tool.getAttribute(node, "loadVersion");
         dataPaths[i] = Tool.getValue(node);
         dataTS = Math.max(dataTS, getLastModified(dataPaths[i], 0));

         if(Tool.isEmptyString(loadVersion)) {
            continue;
         }

         dataPathsLoadVersion.put(dataPaths[i], loadVersion);
      }

      Element hnode = Tool.getChildNodeByTagName(delem, "headers");
      NodeList hnodes = Tool.getChildNodesByTagName(hnode, "header");
      int len = hnodes.getLength();
      headers = new String[len];
      creators = new String[len];
      lflags = new boolean[len];

      for(int i = 0; i < len; i++) {
         Element node = (Element) hnodes.item(i);
         headers[i] = Tool.getAttribute(node, "name");
         creators[i] = Tool.getAttribute(node, "creator");
         lflags[i] = "true".equals(Tool.getAttribute(node, "lflag"));

         String realType = getDataType(creators[i]);

         // data type may (for unknown circumstance) be incorrect. get the correct
         // type from creator, which is the most accurate. (50346)
         ColumnRef columnRef = ((ColumnRef) columns.getAttribute((String) headers[i]));

         if(realType != null && columnRef != null) {
            columnRef.setDataType(realType);
         }
      }
   }

   private static String getDataType(String creator) {
      if("inetsoft.uql.table.XBooleanColumn".equals(creator)) {
         return XSchema.BOOLEAN;
      }
      else if("inetsoft.uql.table.XFloatColumn".equals(creator)) {
         return XSchema.FLOAT;
      }
      else if("inetsoft.uql.table.XDoubleColumn".equals(creator)) {
         return XSchema.DOUBLE;
      }
      else if("inetsoft.uql.table.XShortColumn".equals(creator)) {
         return XSchema.SHORT;
      }
      else if("inetsoft.uql.table.XIntegerColumn".equals(creator)) {
         return XSchema.INTEGER;
      }
      else if("inetsoft.uql.table.XLongColumn".equals(creator)) {
         return XSchema.LONG;
      }
      else if("inetsoft.uql.table.XBDDoubleColumn".equals(creator)) {
         return XSchema.DOUBLE;
      }
      else if("inetsoft.uql.table.XBILongColumn".equals(creator)) {
         return XSchema.LONG;
      }
      else if("inetsoft.uql.table.XDateColumn".equals(creator)) {
         return XSchema.DATE;
      }
      else if("inetsoft.uql.table.XTimestampColumn".equals(creator)) {
         return XSchema.TIME_INSTANT;
      }
      else if("inetsoft.uql.table.XTimeColumn".equals(creator)) {
         return XSchema.TIME;
      }

      return null;
   }

   private static boolean dataFileExist(String fileName) {
      return EmbeddedTableStorage.getInstance().tableExists(fileName);
   }

   @Override
   public Object clone() {
      try {
         SnapshotEmbeddedTableAssembly table2 = (SnapshotEmbeddedTableAssembly) super.clone();
         table2.setEmbeddedData(super.getEmbeddedData(), false);
         table2.setOriginalEmbeddedData(getOriginalEmbeddedData());
         table2.stable = stable;
         table2.originalSTable = originalSTable;
         table2.columns = table2.getColumnSelection(false);
         snapshots.add(new WeakReference<>(table2));
         return table2;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   /**
    * Set default column selection.
    */
   public void setDefaultColumnSelection(ColumnSelection columns) {
      this.columns = columns;
   }

   /**
    * Get default column selection.
    */
   public ColumnSelection getDefaultColumnSelection() {
      return columns == null ? new ColumnSelection() : columns;
   }

   /**
    * Dispose the snap shot embedded table.
    */
   public final void dispose() {
      XSwappableTable stable = this.stable;

      if(stable != null) {
         XTableFragment[] fragments = stable.getTables();

         if(fragments != null) {
            for(XTableFragment table : fragments) {
               if(table != null) {
                  table.swap(true);
               }
            }
         }
      }
   }

   private boolean swapOldVersionSnapshotTables() {
      try {
         if(stable.moreRows(1)) {
            for(int i = 0; i < stable.getColCount(); i++) {
               stable.getObject(1, i);
            }

            XTableFragment[] fragments = stable.getTables();

            if(fragments != null) {
               for(XTableFragment table : fragments) {
                  if(table != null) {
                     table.complete();
                  }
               }
            }
         }
      }
      catch(Exception ex) {
         LOG.error(ex.getMessage(), ex);
         return false;
      }

      return true;
   }

   /**
    * Init table.
    */
   private void initTable() {
      if(dataPaths == null || fileDirty) {
         stable = super.getEmbeddedData().getDataTable();
         return;
      }

      if(dataPaths != null) {
         try {
            String[] paths = new String[dataPaths.length];
            Map<String, String> absolutePathsLoadVersion = new HashMap<>();
            List<File> tempFiles = new ArrayList<>();
            FileSystemService fileSystemService = FileSystemService.getInstance();

            // copy pdata to cache folder
            for(int i = 0; i < paths.length; i++) {
               String path = dataPaths[i] + "_s.tdat";
               File file = fileSystemService.getCacheFile(path);
               paths[i] = file.getAbsolutePath();
               paths[i] = paths[i].substring(0, paths[i].lastIndexOf("_s.tdat"));

               try(InputStream in = EmbeddedTableStorage.getInstance().readTable(path)) {
                  if(in != null) {
                     // if cache file doesn't exist then just copy
                     if(!file.exists()) {
                        try(FileOutputStream out = new FileOutputStream(file)) {
                           Tool.fileCopy(in, out);
                        }
                     }
                     // if cache file already exists then check if contents are different
                     else {
                        File tempCacheFile = fileSystemService.getCacheFile(
                           UUID.randomUUID() + path);

                        try(FileOutputStream out = new FileOutputStream(tempCacheFile)) {
                           Tool.fileCopy(in, out);
                        }

                        String oldDigest;
                        String newDigest;

                        try(InputStream input = new FileInputStream(file)) {
                           oldDigest = DigestUtils.md5Hex(input);
                        }

                        try(InputStream input = new FileInputStream(tempCacheFile)) {
                           newDigest = DigestUtils.md5Hex(input);
                        }

                        // if contents not equal then rename, otherwise delete the new file
                        if(!Tool.equals(oldDigest, newDigest)) {
                           fileSystemService.rename(tempCacheFile, file);
                        }
                        else {
                           fileSystemService.remove(tempCacheFile, 6000);
                        }
                     }

                     tempFiles.add(file);
                     absolutePathsLoadVersion.put(paths[i], dataPathsLoadVersion.get(dataPaths[i]));
                  }
                  else {
                     LOG.error("Snapshot data file missing: " + path +
                                  " updated: " + dataPathsUpdated + " (" + this + ")");
                  }
               }
               catch(FileAlreadyExistsException ignore) {
               }
            }

            XSwappableTable stable = new XSwappableTable();

            if(!tempFiles.isEmpty()) {
               synchronized(fileReferences) {
                  Cleaner.add(new EmbeddedTableReference(stable, tempFiles.toArray(new File[0])));
               }
            }

            XTableColumnCreator[] xcreators = new XTableColumnCreator[creators.length];
            XTableFragment[] tables = new XTableFragment[paths.length];

            for(int i = 0; i < creators.length; i++) {
               Class<?> clazz = Class.forName(creators[i]);
               Method method = clazz.getMethod("getCreator");
               xcreators[i] = (XTableColumnCreator) method.invoke(new Object[0]);
            }

            for(int i = 0; i < paths.length; i++) {
               tables[i] = createFragment(xcreators, paths[i], absolutePathsLoadVersion);
            }

            stable.init(xcreators);
            stable.initFragments(tables, headers, rowCnt, dataPaths);
            originalSTable = stable;
            this.stable = stable;
         }
         catch(Exception exc) {
            LOG.error("Failed to initialize table assembly", exc);
         }
      }
   }

   /**
    * Create table fragment.
    */
   private XTableFragment createFragment(XTableColumnCreator[] creators, String path,
                                         Map<String, String> versionMap)
   {
      XTableColumn[] columns = new XTableColumn[creators.length];

      for(int i = 0; i < columns.length; i++) {
         XTableColumn column = creators[i].createColumn((char) 128, (char) 0x2000);

         if(column instanceof XBDDoubleColumn) {
            ((XBDDoubleColumn) column).setLong(lflags[i]);
         }

         columns[i] = column;
      }

      XTableFragment table = new XTableFragment(columns, false);

      try {
         if(versionMap != null) {
            String loadVersion = versionMap.get(path);

            if(!Tool.isEmptyString(loadVersion)) {
               if(Float.parseFloat(loadVersion) <= 13.8F) {
                  table.setParseWithKryo4("true".equals(SreeEnv.getProperty("try.parse.old.version.snapshot")));
               }
            }
         }
      }
      catch(Exception ignore) {
      }

      table.setSnapshotPath(path);

      return table;
   }

   public String[] getDataPaths() {
      return this.dataPaths;
   }

   /**
    * Check if the column is used.
    */
   @Override
   protected boolean isColumnUsed(ColumnRef aref) {
      // snapshot has no aggregate in aggregate info,
      // force to include all columns
      // the above comments doesn't seem to be true. it's code merged in
      // revision 17306 from sr10_3. probably from old implementation
      // return true;
      return super.isColumnUsed(aref);
   }

   @Override
   public boolean isUndoable() {
      EmbeddedTableStorage embeddedTableStorage = EmbeddedTableStorage.getInstance();

      return dataPaths == null || Arrays.stream(dataPaths).map(p -> p + "_s.tdat")
         .allMatch((p -> embeddedTableStorage.tableExists(p)));
   }

   public void setShouldDeleteOldFiles(boolean shouldDeleteOldFiles) {
      this.shouldDeleteOldFiles = shouldDeleteOldFiles;
   }

   private static final AtomicInteger count = new AtomicInteger(0);
   private static final String PDATA = "pdata";
   private static final Logger LOG = LoggerFactory.getLogger(SnapshotEmbeddedTableAssembly.class);
   private int prefix = 0;
   private ColumnSelection columns;
   private XSwappableTable stable;
   private XSwappableTable originalSTable;
   private int rowCnt = -1;
   private Object[] headers;
   private String[] creators;
   private boolean[] lflags;

   // there are three states:
   // 1. memory only (no file): dataPaths == null
   // 2. memory and file in sync: dataPaths != null && !fileDirty
   // 3. memory and file out of sync: dataPaths != null && fileDirty
   // dirty file will be removed when new files are written or
   // sheet is saved and the delete flag is true
   private String[] dataPaths = null;
   private Map<String, String> dataPathsLoadVersion = new HashMap<>();
   private long dataTS = 0;
   private boolean fileDirty = false;
   private boolean deleted = false;
   private transient boolean shouldDeleteOldFiles = true;
   private transient boolean dataPathsUpdated;

   private static final Map<String, Integer> fileReferences = new HashMap<>();
   private static final List<Reference<SnapshotEmbeddedTableAssembly>> snapshots = new ArrayList<>();

   private static final class EmbeddedTableReference extends Cleaner.Reference<XSwappableTable> {
      EmbeddedTableReference(XSwappableTable referent, File[] files) {
         super(referent);
         this.files = Arrays.stream(files).map(File::getAbsolutePath).toArray(String[]::new);

         for(String file : this.files) {
            int count = fileReferences.getOrDefault(file, 0) + 1;
            fileReferences.put(file, count);
         }
      }

      @Override
      public void close() throws Exception {
         synchronized(fileReferences) {
            for(String file : files) {
               int count = fileReferences.getOrDefault(file, 1) - 1;

               if(count == 0) {
                  fileReferences.remove(file);
                  new File(file).delete();
               }
               else {
                  fileReferences.put(file, count);
               }
            }
         }
      }

      private final String[] files;
   }
}
