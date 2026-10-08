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

import inetsoft.report.TableDataPath;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.Organization;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.table.*;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.util.*;
import inetsoft.util.log.LogLevel;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.groovy.io.StringBuilderWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.*;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.nio.file.FileAlreadyExistsException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;

/**
 * Embedded table assembly for snapshot, mainly deal with large data.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class SnapshotEmbeddedTableAssembly extends EmbeddedTableAssembly {

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
   }

   /**
    * Set the worksheet.
    *
    * @param ws the specified worksheet.
    */
   @Override
   public void setWorksheet(Worksheet ws) {
      super.setWorksheet(ws);
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
   }

   /**
    * Set the column selection. Overridden to keep the default column selection
    * in sync with the private column selection.
    */
   @Override
   public void setColumnSelection(ColumnSelection selection, boolean pub) {
      super.setColumnSelection(selection, pub);

      if(!pub) {
         this.columns = selection;
      }
   }

   @Override
   public synchronized void pasted() {
      // an outer copy keeps naming the files of the worksheet it was copied from, they are not
      // its to replace. a copy that isn't copied again on load gets its own files when its
      // worksheet is saved (bug #78022, #78023)
      if(!ownsDataFiles()) {
         super.pasted();
         return;
      }

      // load the rows before the paths are dropped, or an empty table is written in their place.
      // if they can't be loaded, fail and keep the paths. the copy must not share them, it would
      // own files the original still names (bug #78029)
      checkDataLoaded();
      super.pasted();

      // make sure data files are saved to a new file instead of sharing with original assembly
      this.dataPaths = null;
      this.fileDirty = true;
      prefix = count.getAndIncrement();

      // don't delete data files of the original assembly, the stored worksheet it came from
      // still names them
      committedDataPaths = null;
   }

   /**
    * Load the data of this table if its rows are only in the data files it names, so that new
    * files can be written for it. Call it before a copy of the table is added anywhere, so a
    * table whose data is gone fails before anything is changed (bug #78029).
    *
    * @throws MessageException if the data of the table could not be loaded.
    */
   public synchronized void checkDataLoaded() {
      // a table read from a stored worksheet has its rows only in the files it names
      if(ownsDataFiles() && dataPaths != null && !fileDirty) {
         XSwappableTable table = getTable();

         if(table == null || table == incompleteTable) {
            throw new MessageException(
               Catalog.getCatalog().getString("common.worksheetSnapshotDataNotLoaded", getName()),
               LogLevel.ERROR, false, ConfirmException.ERROR);
         }
      }
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

            try {
               out.putNextEntry(zipEntry);
            }
            catch(ZipException e) {
               if (e.getMessage() != null && e.getMessage().contains("duplicate entry")) {
                  // Ignore error for duplicate entries and move on
                  LOG.debug("Duplicate embedded table", e);
                  continue;
               }
               else {
                  // Log error for other errors
                  throw e;
               }
            }

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

   /**
    * Check if this table owns the data files it names, so it may delete or replace them. An
    * outer copy of a table of another worksheet owns them only if it wrote them itself, otherwise
    * they belong to that worksheet (bug #78022, #78023).
    */
   public boolean ownsDataFiles() {
      return !isOuter() || dataOwner;
   }

   /**
    * Set whether this outer table wrote the data files it names.
    */
   public void setDataOwner(boolean dataOwner) {
      this.dataOwner = dataOwner;
   }

   public void deleteDataFiles(String reason) {
      deleteDataFiles(dataPaths, reason);
      dataPaths = null;
      fileDirty = false;
   }

   public void deleteDataFiles(String[] dataPaths, String reason) {
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
      }
      catch(Exception ex) {
         LOG.debug("Failed to delete data file", ex);
      }
   }

   /**
    * Write embedded data.
    * @param writer the specified writer.
    */
   @Override
   protected synchronized void writeEmbeddedData(PrintWriter writer) {
      dataWriteFailed = false;

      try {
         XSwappableTable stable = prepareWrite();

         // make sure table is inited
         if(stable == null) {
            return;
         }

         boolean writeDateFile = dataPaths == null || fileDirty;
         boolean dataSaved = true;

         if(writeDateFile) {
            dataSaved = writeDataFiles(stable);
         }

         // the data is not stored, a worksheet save must not report success (bug #77986)
         if(!dataSaved) {
            dataWriteFailed = true;
         }

         // init row count after load complete
         stable.moreRows(XTable.EOT);
         rowCnt = stable.getRowCount();

         writer.print(Tool.buildString("<sembeddedData row=\"", rowCnt, "\""));

         if(isOuter() && dataOwner) {
            writer.print(" dataOwner=\"true\"");
         }

         writer.println(">");

         if(columns != null) {
            columns.writeXML(writer);
         }

         writer.println("<paths>");

         // the previous files don't hold the current data, don't pair them with it (bug #77963)
         if(dataPaths != null && dataSaved) {
            for(String dataPath : dataPaths) {
               if(dataPathsLoadVersion.get(dataPath) != null) {
                  writer.println(Tool.buildString("<path loadVersion=\"", dataPathsLoadVersion.get(dataPath), "\"><![CDATA["));
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

         if(creators == null || writeDateFile) {
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
               writer.print(Tool.buildString("name=\"", Tool.escape(headers[i].toString()), "\" ") );
            }

            writer.println(Tool.buildString("creator=\"", creators[i],  "\" lflag=\"", lflags[i], "\"/>"));
         }

         writer.println("</headers>");

         Map<TableDataPath, XMetaInfo> metaMap = stable.getXMetaInfoMap();

         if(!metaMap.isEmpty()) {
            writer.println("<metaInfos>");

            for(Map.Entry<TableDataPath, XMetaInfo> entry : metaMap.entrySet()) {
               XMetaInfo minfo = entry.getValue();

               if(minfo != null && !minfo.isXFormatInfoEmpty()) {
                  TableDataPath path = entry.getKey();
                  String[] pathArr = path.getPath();

                  if(pathArr != null && pathArr.length > 0) {
                     writer.print("<metaInfo header=\"");
                     writer.print(Tool.escape(pathArr[0]));
                     writer.println("\">");
                     minfo.writeXML(writer);
                     writer.println("</metaInfo>");
                  }
               }
            }

            writer.println("</metaInfos>");
         }

         writer.println("</sembeddedData>");
      }
      catch(Exception exc) {
         dataWriteFailed = true;
         LOG.error("Failed to write data XML", exc);
      }
   }

   /**
    * Get the table to write, after checking whether its data files must be written again.
    */
   private XSwappableTable prepareWrite() {
      // If undo was done, especially after a "save as" action then
      // there is a chance that two tables from different worksheets could share
      // the same data files. To prevent that, rename the data files when writing to xml
      // to be safe.
      if(undo && !Worksheet.isTemp()) {
         pasted();
         undo = false;
      }

      XSwappableTable stable = getTable();

      // a newer file of the worksheet this outer copy was copied from is that worksheet's
      // business, the copy must not replace it (bug #78022)
      if(stable != null && !fileDirty && dataPaths != null && ownsDataFiles()) {
         // if data file has changed, don't reuse it otherwise the row count
         // and data may be out of sync. (56584)
         fileDirty = Arrays.stream(dataPaths)
            .anyMatch(p -> getLastModified(p, Long.MAX_VALUE) > dataTS);
      }

      return stable;
   }

   /**
    * Write the data files of the snapshot tables of a worksheet before the worksheet is saved,
    * so that a failed write stops the save before anything is stored, and the stored worksheet
    * keeps its previous data. The files named by the stored worksheet are kept until
    * {@link #finishSave(Worksheet, boolean)} is called after the worksheet is stored, so a
    * failed save never leaves the stored worksheet pointing at deleted files (bug #77986).
    * The files the saved worksheet names are marked as not temporary before it is stored, so
    * they are not removed as expired temp files later (bug #78012).
    *
    * @param ws the worksheet to save.
    *
    * @throws MessageException if the data of a table could not be written.
    */
   public static void writeDataFilesForSave(Worksheet ws) {
      try {
         for(SnapshotEmbeddedTableAssembly table : getSnapshotTables(ws)) {
            table.writeDataFilesForSave(ws.isFrozenOuterAssembly(table));
         }
      }
      catch(RuntimeException ex) {
         finishSave(ws, false);
         throw ex;
      }
   }

   /**
    * Finish a save started by {@link #writeDataFilesForSave(Worksheet)}. If the worksheet was
    * stored, the data files that the previously stored version named and the saved one no
    * longer names are deleted. Nothing else ever deletes them (bug #78012).
    *
    * @param ws    the saved worksheet.
    * @param saved {@code true} if the worksheet was stored.
    *
    * @throws MessageException if the worksheet was stored without the data of a table.
    */
   public static void finishSave(Worksheet ws, boolean saved) {
      List<String> failed = new ArrayList<>();

      for(SnapshotEmbeddedTableAssembly table : getSnapshotTables(ws)) {
         if(table.finishSave(saved)) {
            failed.add(table.getName());
         }
      }

      if(!failed.isEmpty()) {
         throw new MessageException(
            Catalog.getCatalog().getString("common.worksheetSnapshotDataNotStored",
                                           String.join(", ", failed)),
            LogLevel.ERROR, false, ConfirmException.ERROR);
      }
   }

   private static List<SnapshotEmbeddedTableAssembly> getSnapshotTables(Worksheet ws) {
      List<SnapshotEmbeddedTableAssembly> tables = new ArrayList<>();

      if(ws != null) {
         for(Assembly assembly : ws.getAssemblies()) {
            if(assembly instanceof SnapshotEmbeddedTableAssembly) {
               tables.add((SnapshotEmbeddedTableAssembly) assembly);
            }
         }
      }

      return tables;
   }

   /**
    * @param frozen {@code true} if this is an outer copy that is not copied again from its
    *               worksheet when this worksheet is opened.
    */
   private synchronized void writeDataFilesForSave(boolean frozen) {
      dataWriteFailed = false;

      try {
         XSwappableTable stable = prepareWrite();

         if(stable != null && frozen && !ownsDataFiles() && !Worksheet.isTemp()) {
            detachDataFiles(stable);
         }

         if(stable != null && (dataPaths == null || fileDirty) && !writeDataFiles(stable)) {
            throw new IOException("Table swap file is missing");
         }

         // files written by a temp write (undo checkpoint, cache flush) that the saved worksheet
         // names must not expire as temp files. the table is not dirty after such a write, so
         // writeDataFiles did not run (bug #78012). the files of an outer copy that doesn't own
         // them are left to the worksheet it was copied from (bug #78022)
         if(stable != null && dataPaths != null && !Worksheet.isTemp() && ownsDataFiles()) {
            EmbeddedTableStorage storage = EmbeddedTableStorage.getInstance();

            for(String dataPath : dataPaths) {
               if(storage.clearTempFlag(dataPath + "_s.tdat")) {
                  dataTS = System.currentTimeMillis();
               }
            }
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to write the data files of snapshot table: {}", getName(), ex);
         MessageException mex = new MessageException(
            Catalog.getCatalog().getString("common.worksheetSnapshotDataNotSaved", getName()),
            LogLevel.ERROR, false, ConfirmException.ERROR);
         mex.initCause(ex);
         throw mex;
      }
   }

   /**
    * Give a frozen outer copy its own data files. It keeps its data when the worksheet it was
    * copied from replaces or deletes the shared files, and the files are deleted with its own
    * worksheet (bug #78023). The table must be loaded before the paths are reset, or its data
    * is lost.
    */
   private void detachDataFiles(XSwappableTable stable) {
      String[] oldDataPaths = dataPaths;
      Map<String, String> oldLoadVersion = new HashMap<>(dataPathsLoadVersion);
      int oldPrefix = prefix;
      long oldDataTS = dataTS;
      boolean oldFileDirty = fileDirty;

      // write new files, the shared ones are left to the worksheet that owns them
      dataPaths = null;
      prefix = count.getAndIncrement();
      fileDirty = true;
      boolean written = false;
      Exception error = null;

      try {
         written = writeDataFiles(stable);
      }
      catch(Exception ex) {
         error = ex;
      }

      if(written) {
         dataOwner = true;
         // the shared files the stored worksheet named are not this table's to delete when the
         // save finishes, they belong to the worksheet it was copied from
         committedDataPaths = null;
         return;
      }

      // the shared files can't be copied, e.g. they are already gone. keep naming them so the
      // worksheet is still saved, the user can update the mirror to get the current data
      dataPaths = oldDataPaths;
      dataPathsLoadVersion = oldLoadVersion;
      prefix = oldPrefix;
      dataTS = oldDataTS;
      fileDirty = oldFileDirty;
      LOG.warn("Failed to copy the data files of outer table {}, it keeps sharing them: {}",
               getName(), Arrays.toString(oldDataPaths), error);
   }

   /**
    * @return {@code true} if the worksheet was stored without the data of this table.
    */
   private synchronized boolean finishSave(boolean saved) {
      if(!saved) {
         return false;
      }

      if(dataWriteFailed) {
         return true;
      }

      if(Worksheet.isTemp() || dataPaths == null) {
         return false;
      }

      // the stored worksheet now names dataPaths. delete the files the previously stored version
      // named and this one does not. the files a temp write (cache flush, undo checkpoint) or
      // another copy of the worksheet replaced are not deleted, since the stored worksheet may
      // still name them (bug #78012). an outer copy that doesn't own its files never deletes
      // the files of the worksheet it was copied from (bug #78022)
      if(ownsDataFiles() && committedDataPaths != null &&
         !Arrays.equals(committedDataPaths, dataPaths))
      {
         deleteOldFiles(committedDataPaths, dataPaths);
      }

      committedDataPaths = dataPaths.clone();
      return false;
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
    * @return false if a data file could not be written. The data is then still out of sync with
    * the files (fileDirty), and the next save writes it again (bug #77963).
    */
   private synchronized boolean writeDataFiles(XSwappableTable stable) throws Exception {
      String tprefix = getTablePrefix();
      String[] prefixes = stable.getPrefixes();
      XTableFragment[] tables = stable.getTables();
      dataPathsLoadVersion.clear();
      String[] dataPaths = new String[prefixes.length];

      if(dataPaths.length > 0) {
         List<File> files = stable.getFilesList();

         for(int i = 0; i < dataPaths.length; i++) {
            dataPaths[i] = tprefix + prefixes[i];
         }

         if(files.size() != prefixes.length) {
            for(XTableFragment table : tables) {
               if(table.getFiles().isEmpty()) {
                  LOG.error("Table swap file is missing, the snapshot data is not saved: " +
                            table.getSwapFile());
                  return false;
               }
            }
         }

         EmbeddedTableStorage storage = EmbeddedTableStorage.getInstance();

         for(int i = 0; i < dataPaths.length; i++) {
            String fileName = dataPaths[i] + "_s.tdat";

            // save the table regardless if it's temp or not
            if(!dataFileExist(fileName)) {
               try(InputStream input = new FileInputStream(files.get(i))) {
                  storage.writeTable(fileName, input, Worksheet.isTemp());
                  dataTS = System.currentTimeMillis();
               }
            }
            // when worksheet is saved, remove the temp flag on the table files
            else if(!Worksheet.isTemp() && storage.isTempTable(fileName)) {
               try(InputStream input = new FileInputStream(files.get(i))) {
                  storage.writeTable(fileName, input, false);
                  dataTS = System.currentTimeMillis();
               }
            }
         }
      }

      // the replaced files are not deleted here. a stored worksheet may still name them, and
      // only finishSave() after a successful save may delete them (bug #78012)
      this.dataPaths = dataPaths;
      fileDirty = false;
      return true;
   }

   /**
    * Delete the files that the previously stored worksheet named and the saved one does not.
    */
   private void deleteOldFiles(String[] oldDataPaths, String[] newDataPaths) {
      Set<String> kept = new HashSet<>(Arrays.asList(newDataPaths));
      List<String> replaced = new ArrayList<>();

      for(String oldDataPath : oldDataPaths) {
         if(!kept.contains(oldDataPath)) {
            replaced.add(oldDataPath);
         }
      }

      if(replaced.isEmpty()) {
         return;
      }

      // only delete file if different files are written. (50334) a file may already be deleted
      // by the save of another copy of the worksheet
      String[] existing = replaced.stream()
         .filter(path -> dataFileExist(path + "_s.tdat"))
         .toArray(String[]::new);
      deleteDataFiles(existing, "Data file overwritten: " + getName() + " files: " +
                                Arrays.toString(oldDataPaths) + " replaced by: " +
                                Arrays.toString(newDataPaths));
      // keep other copies (e.g. another session) from saving the deleted files again
      updateDataFiles(stable, newDataPaths, oldDataPaths);
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
      // an outer copy stored without the flag names the files of the worksheet it was copied
      // from (bug #78022)
      dataOwner = "true".equals(Tool.getAttribute(delem, "dataOwner"));

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

      committedDataPaths = dataPaths.clone();

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

      Element mnode = Tool.getChildNodeByTagName(delem, "metaInfos");

      if(mnode != null) {
         NodeList mnodes = Tool.getChildNodesByTagName(mnode, "metaInfo");
         metaInfoMap = new HashMap<>();

         for(int i = 0; i < mnodes.getLength(); i++) {
            Element node = (Element) mnodes.item(i);
            String header = Tool.getAttribute(node, "header");
            Element minfoElem = Tool.getChildNodeByTagName(node, "XMetaInfo");

            if(header != null && minfoElem != null) {
               XMetaInfo minfo = new XMetaInfo();
               minfo.parseXML(minfoElem);
               metaInfoMap.put(header, minfo);
            }
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
         table2.dataWriteFailed = false;
         // each copy keeps its own state, so one copy never deletes the files another copy
         // (or the stored worksheet) still names (bug #78012). writeDataFiles of a copy, e.g. an
         // outer copy in another worksheet, must not clear the load versions of this table
         // (bug #78022)
         table2.committedDataPaths = committedDataPaths == null ? null : committedDataPaths.clone();
         table2.dataPathsLoadVersion = new HashMap<>(dataPathsLoadVersion);
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
            StringBuilderWriter cacheKeyBuilderWriter = new StringBuilderWriter();
            printEmbeddedDataKey(new PrintWriter(cacheKeyBuilderWriter));
            String cacheKey = cacheKeyBuilderWriter.toString();
            XSwappableTable cacheTable = SnapshotEmbeddedTableDataCache.getInstance().get(cacheKey);

            if(cacheTable != null) {
               stable = cacheTable;
               return;
            }

            ReentrantLock lock = SnapshotEmbeddedTableDataCache.getInstance().getLock(cacheKey);
            lock.lock();

            try {
               cacheTable = SnapshotEmbeddedTableDataCache.getInstance().get(cacheKey);

               if(cacheTable != null) {
                  stable = cacheTable;
                  return;
               }

               String[] paths = new String[dataPaths.length];
               Map<String, String> absolutePathsLoadVersion = new HashMap<>();
               List<File> tempFiles = new ArrayList<>();
               FileSystemService fileSystemService = FileSystemService.getInstance();
               boolean complete = true;

               // copy pdata to cache folder
               for(int i = 0; i < paths.length; i++) {
                  String path = dataPaths[i] + "_s.tdat";
                  File file = fileSystemService.getCacheFile(path);
                  paths[i] = file.getAbsolutePath();
                  paths[i] = paths[i].substring(0, paths[i].lastIndexOf("_s.tdat"));

                  String orgId = OrganizationManager.getInstance().getCurrentOrgID();

                  if(!EmbeddedTableStorage.getInstance().tableExists(path) &&
                     SUtil.isDefaultVSGloballyVisible())
                  {
                     // Filenames are unique. Checking across organizations should be fine.
                     orgId = Organization.getDefaultOrganizationID();
                  }

                  try(InputStream in = EmbeddedTableStorage.getInstance().readTable(path, orgId)) {
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
                        complete = false;
                        LOG.error("Snapshot data file missing: " + path +
                                     " updated: " + dataPathsUpdated + " (" + this + ")");
                     }
                  }
                  catch(FileAlreadyExistsException ignore) {
                  }
               }

               XSwappableTable stable = new XSwappableTable();

               if(!tempFiles.isEmpty()) {
                  Cleaner.add(new EmbeddedTableReference(stable, tempFiles.toArray(new File[0])));
               }

               XTableColumnCreator[] xcreators = new XTableColumnCreator[creators.length];
               XTableFragment[] tables = new XTableFragment[paths.length];

               for(int i = 0; i < creators.length; i++) {
                  Class<?> clazz = Tool.loadSubclass(creators[i], XTableColumn.class);
                  Method method = clazz.getMethod("getCreator");
                  xcreators[i] = (XTableColumnCreator) method.invoke(new Object[0]);
               }

               for(int i = 0; i < paths.length; i++) {
                  tables[i] = createFragment(xcreators, paths[i], absolutePathsLoadVersion);
               }

               stable.init(xcreators);
               stable.initFragments(tables, headers, rowCnt, dataPaths);

               if(metaInfoMap != null) {
                  for(Map.Entry<String, XMetaInfo> entry : metaInfoMap.entrySet()) {
                     stable.setXMetaInfo(entry.getKey(), entry.getValue());
                  }
               }

               originalSTable = stable;
               this.stable = stable;

               // a table with missing files has null rows in their place, don't hand it to
               // other tables as their data (bug #78029)
               if(complete) {
                  SnapshotEmbeddedTableDataCache.getInstance().set(cacheKey, stable);
               }
               else {
                  incompleteTable = stable;
               }
            }
            finally {
               lock.unlock();
            }
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

      boolean undoable = dataPaths == null || Arrays.stream(dataPaths).map(p -> p + "_s.tdat")
         .allMatch((p -> embeddedTableStorage.tableExists(p)));

      if(undoable) {
         undo = true;
      }

      return undoable;
   }

   private static final AtomicInteger count = new AtomicInteger(0);
   private static final String PDATA = "pdata";
   private static final Logger LOG = LoggerFactory.getLogger(SnapshotEmbeddedTableAssembly.class);
   private int prefix = 0;
   private ColumnSelection columns;
   private XSwappableTable stable;
   private XSwappableTable originalSTable;
   // a table loaded while some of its data files were missing (bug #78029)
   private transient XSwappableTable incompleteTable;
   private int rowCnt = -1;
   private Object[] headers;
   private String[] creators;
   private boolean[] lflags;

   // there are three states:
   // 1. memory only (no file): dataPaths == null
   // 2. memory and file in sync: dataPaths != null && !fileDirty
   // 3. memory and file out of sync: dataPaths != null && fileDirty
   // replaced files are removed only after the worksheet is saved (finishSave)
   private String[] dataPaths = null;
   // the data paths named by the stored worksheet this table was loaded from or last saved to.
   // only these may be deleted, by a save that no longer names them (bug #78012)
   private String[] committedDataPaths = null;
   private Map<String, String> dataPathsLoadVersion = new HashMap<>();
   private Map<String, XMetaInfo> metaInfoMap = null;
   private long dataTS = 0;
   private boolean fileDirty = false;
   private boolean deleted = false;
   private boolean undo = false;
   // an outer copy wrote the data files it names, see ownsDataFiles() (bug #78022, #78023)
   private boolean dataOwner = false;
   private transient boolean dataPathsUpdated;
   // the last write of the data did not store it (bug #77986)
   private transient boolean dataWriteFailed;

   public static final String FILE_REFERENCES_MAP = "inetsoft.snapshot.file.map";
   public static final String FILE_REFERENCES_MAP_LOCK = "inetsoft.snapshot.file.map.lock";
   private static final List<Reference<SnapshotEmbeddedTableAssembly>> snapshots = new ArrayList<>();

   private static final class EmbeddedTableReference extends Cleaner.Reference<XSwappableTable> {
      EmbeddedTableReference(XSwappableTable referent, File[] files) {
         super(referent);
         this.files = Arrays.stream(files).map(File::getAbsolutePath).toArray(String[]::new);
         Cluster cluster = Cluster.getInstance();
         Lock lock = cluster.getLock(FILE_REFERENCES_MAP_LOCK);
         lock.lock();

         try {
            Map<String, Integer> map = cluster.getMap(FILE_REFERENCES_MAP);

            for(String file : this.files) {
               int count = map.getOrDefault(file, 0) + 1;
               map.put(file, count);
            }
         }
         finally {
            lock.unlock();
         }
      }

      @Override
      public void close() throws Exception {
         Cluster cluster = Cluster.getInstance();
         Lock lock = cluster.getLock(FILE_REFERENCES_MAP_LOCK);
         lock.lock();

         try {
            Map<String, Integer> map = cluster.getMap(FILE_REFERENCES_MAP);

            for(String file : files) {
               int count = map.getOrDefault(file, 1) - 1;

               if(count == 0) {
                  map.remove(file);
                  new File(file).delete();
               }
               else {
                  map.put(file, count);
               }
            }
         }
         finally {
            lock.unlock();
         }
      }

      private final String[] files;
   }
}
