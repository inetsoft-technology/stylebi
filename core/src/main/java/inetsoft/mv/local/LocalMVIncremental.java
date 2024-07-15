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
package inetsoft.mv.local;

import inetsoft.mv.*;
import inetsoft.mv.data.*;
import inetsoft.mv.fs.*;
import inetsoft.mv.fs.internal.CacheBlockFile;
import inetsoft.report.composition.WorksheetWrapper;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.ConditionUtil;
import inetsoft.uql.jdbc.UniformSQL;
import inetsoft.uql.jdbc.XFilterNode;
import inetsoft.uql.jdbc.util.ConditionListHandler;
import inetsoft.uql.util.XUtil;
import inetsoft.util.TimedQueue;
import inetsoft.util.swap.XIntList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.*;

/**
 * DefaultMVIncremental
 *
 * @version 12.2
 * @author InetSoft Technology Corp
 */
public class LocalMVIncremental extends MVIncremental {
   public LocalMVIncremental(MVDef def) {
      super(def);
   }

   /**
    * Cancel this task.
    */
   @Override
   public void cancel() {
      canceled = true;
   }

   /**
    * Update the mv data.
    */
   @Override
   public void update() throws Exception {
      if(update0()) {
         refresh();
      }
   }

   private boolean update0() throws Exception {
      XServerNode server = FSService.getServer();

      if(server == null) {
         throw new RuntimeException("This host is not server node!");
      }

      String table = mvdef.getMVTable();
      Worksheet ws = mvdef.getWorksheet();
      ws = new WorksheetWrapper(ws);
      TableAssembly assembly = (TableAssembly) ws.getAssembly(table);

      if(assembly == null || mv == null) {
         return false;
      }

      // 1. get mv delete condition list
      ConditionListWrapper wrapper = assembly.getMVDeleteConditionList();

      if(canceled) {
         return false;
      }

      VariableTable vars = new VariableTable();
      vars.put("MV.LastUpdateTime", new Timestamp(mvdef.getLastUpdateTime()));

      for(int i = 0; i < mvdef.getColumns().size(); i++) {
         MVColumn column = mvdef.getColumns().get(i);
         MVScriptable script = new MVScriptable(mvdef, column);
         String prefix =
            "MV." + column.getName().replaceAll("[^\\p{Alnum}]", "_");
         vars.put(prefix + ".Min", script.get("MinValue", null));
         vars.put(prefix + ".Max", script.get("MaxValue", null));
      }

      MVConditionListHandler handler = new MVConditionListHandler(assembly, mv, vars, mvdef);

      // 2. fix delete condition
      List<XNode> filter = fixCondition(wrapper, handler, vars);

      if(canceled) {
         return false;
      }

      boolean result = false;

      // there may be more than one scheduler updating fs (in memory),
      // lock to make sure only one copy is modified and saved before
      // the other is modified.
      Cluster.getInstance().lockKey("mv.fs.update");
      MVStorage storage = MVStorage.getInstance();

      try {
         // 3. dispatch the fixed condition
         boolean changed = dispatchCondition(filter);

         if(canceled) {
            throw new RuntimeException("MV incremental cancelled, " +
               "the data may be not in sync.");
         }

         if(!availableCondition(assembly)) {
            if(changed) {
               updateDictionaryRanges(assembly, vars);
            }

            return changed;
         }

         // 4. merge asset condition and mv append condition
         mergeAppendCondition(assembly, handler);

         if(canceled) {
            throw new RuntimeException("MV incremental cancelled, " +
               "the data may be not in sync.");
         }

         // 5. get data from db
         LOG.debug("Start creating incremental materialized view " + mvdef.getName());
         // ws/table may be changed (transformed) in getData(), make a copy so we don't
         // use the modified table later
         Worksheet ws2 = new WorksheetWrapper(ws);
         TableAssembly assembly2 = (TableAssembly) ws2.getAssembly(assembly.getName());
         long start = System.currentTimeMillis();
         XTable lens = getData(ws2, assembly2, vars);
         long end = System.currentTimeMillis();
         LOG.debug("Query executed for materialized view " + mvdef.getName() +
                  " in " + (end - start) + "ms");

         if(!lens.moreRows(lens.getHeaderRowCount())) {
            if(changed) {
               updateDictionaryRanges(assembly, vars);
            }

            return changed;
         }

         if(canceled) {
            throw new RuntimeException("MV incremental cancelled, " +
               "the data may be not in sync.");
         }

         String name = mvdef.getName();
         boolean aggregated = MVCreatorUtil.isAggregated(mvdef);
         start = System.currentTimeMillis();
         MVBuilder builder = new MVBuilder(
            lens, mvdef, aggregated, storage.get(MVStorage.getFile(name)));
         MV nMV = builder.getMV();

         // 8. dispatch the append record
         result = dispatchRecord(nMV, builder.getSubMVs());

         end = System.currentTimeMillis();

         if(changed) {
            updateDictionaryRanges(assembly, vars);
         }

         LOG.debug("Data file created for materialized view " +
                  mvdef.getName() + " in " + (end - start) + "ms");
      }
      catch(Exception ex) {
         mv.setSuccess(false);
         String name = mvdef.getName();
         String file = MVStorage.getFile(name);
         mv.save(file);
         throw ex;
      }
      finally {
         Cluster.getInstance().unlockKey("mv.fs.update");
	      FSService.refreshCluster(true);
      }

      // need to set the success here as refresh() will save the mvdef in MVManager
      // which may then be loaded by another process
      mvdef.setSuccess(result);
      return result;
   }

   /**
    * Update the mv data.
    */
   private void updateDictionaryRanges(TableAssembly assembly,
      VariableTable vars) throws Exception
   {
      // Get the table lens containing the data
      assembly = (TableAssembly) assembly.clone();
      assembly.getAggregateInfo().clear();
      LocalMVExecutor exec = new LocalMVExecutor(assembly, mvdef.getName(), vars, null);
      XTable lensMV = MVCreatorUtil.expand(mvdef, exec.getData());

      // Get a list of columns in MV-sorted order
      List<MVColumn> cols = mvdef.getColumns();
      XIntList colList = new XIntList();

      for(int i = 0; i < cols.size(); i++) {
         if(cols.get(i).isDimension()) {
            colList.add(i);
         }
      }

      for(int i = 0; i < cols.size(); i++) {
         if(!cols.get(i).isDimension()) {
            colList.add(i);
         }
      }

      int[] colArray = colList.toArray();

      if(lensMV.moreRows(1)) {
         // Reset the dictionary range for each dictionary (to maxint/minint)
         for(int b = 0; b < mv.getBlockSize(); b++) {
            for(int c = 0; c < mv.getHeaders().length; c++) {
               if(mv.getDictionary(c, b) != null) {
                  mv.getDictionary(c, b).resetRanges();
               }
            }
         }

         // Also reset the min/max for the numeric/date columns
         for(MVColumn col:mvdef.getColumns()) {
            if(col.isNumeric() || col.isDate()) {
               col.setRange(null, null);
            }
         }

         int b = 0;

         // Expand the range for each column by the data values
         for(int r = 1; lensMV.moreRows(r); r++) {
            // Adjust to next dictionary block
            if(mv.getBlockSize() - 1 > b) {
               if(mv.getDictionary(0, b + 1) != null) {
                  if(mv.getDictionary(0, b + 1).getBaseRow() >= r) {
                     b++;
                  }
               }
            }

            // Expand dictionary range for data values
            for(int c = 0; c < colArray.length; c++) {
               if(mv.getDictionary(c, b) != null) {
                  mv.getDictionary(c, b).expandRangeByObject(
                     lensMV.getObject(r, colArray[c]));
               }
               else {
                  // Also expand the range for the numeric/date columns
                  if(cols.get(colArray[c]).isNumeric()) {
                     cols.get(colArray[c]).expandRange(
                        (Number)lensMV.getObject(r, colArray[c]));
                  }

                  if(cols.get(colArray[c]).isDate()) {
                     cols.get(colArray[c]).expandRange(
                        (Date)lensMV.getObject(r, colArray[c]));
                  }
               }
            }
         }
      }
      else {
         // if there is no data in the table, then null the dictionary ranges
         for(int b = 0; b < mv.getBlockSize(); b++) {
            for(int c = 0; c < mv.getHeaders().length; c++) {
               if(mv.getDictionary(c, b) != null) {
                  mv.getDictionary(c, b).nullRanges();
               }
            }
         }
      }

      mv.save(MVStorage.getFile(mvdef.getName()));
   }

   /**
    * Check the mv condition is available condition.
    */
   private boolean availableCondition(TableAssembly table) {
      ConditionList conds = table.getMVUpdateConditionList();
      AggregateInfo ainfo = table.getAggregateInfo();

      if(conds == null || conds.isEmpty()) {
         return table.isMVForceAppendUpdates();
      }

      for(int i = 0; i < conds.getSize(); i +=2) {
         ConditionItem item = conds.getConditionItem(i);
         GroupRef group = ainfo.getGroup(item.getAttribute());
         XCondition cond = item.getXCondition();

         if(group == null || !(cond instanceof AssetCondition)) {
            continue;
         }

         AssetCondition acond = (AssetCondition) cond;
         ExpressionValue eval = null;

         if(acond.getValueCount() <= 0) {
            continue;
         }

         for(int j = 0; j < acond.getValueCount(); j++) {
            Object val = acond.getValue(j);

            if(val instanceof ExpressionValue) {
               eval = (ExpressionValue) val;
            }

            if(eval == null) {
               continue;
            }

            String exp = eval.getExpression();

            if(exp.contains("MV.") && group.getNamedGroupInfo() != null &&
               ainfo.isEmpty())
            {
               LOG.warn(
                  "Ignoring materialized view script condition defined on " +
                  "a named group column because it may lead to a data type " +
                  "mismatch");
               return false;
            }
         }
      }

      return true;
   }

   /**
    * Fix the mv condition.
    */
   private List<XNode> fixCondition(ConditionListWrapper wrapper,
      ConditionListHandler handler, VariableTable vars)
   {
      if(wrapper == null || wrapper.isEmpty()) {
         return null;
      }

      ConditionList cond = wrapper.getConditionList();

      if(cond != null) {
         cond = (ConditionList) cond.clone();
         cond = ConditionUtil.splitDateRangeCondition(cond);
         XUtil.convertDateCondition(cond, vars, true);
         cond.validate(false);
      }

      XFilterNode filter =
         handler.createXFilterNode(cond, new UniformSQL(), vars);
      return mv.fixFilter(filter);
   }

   /**
    * Dispatch the mv delete condition.
    */
   private boolean dispatchCondition(List<XNode> conds) throws Exception {
      if(conds == null) {
         return false;
      }

      XServerNode server = FSService.getServer();

      if(server == null) {
         throw new RuntimeException("This host is not server node!");
      }

      String name = mvdef.getName();
      XFileSystem sys = server.getFSystem();
      sys.deleteRecord(name, conds);

      return true;
   }

   /**
    * Dispatch mappings.
    */
   private boolean dispatchMapping(MV nmv, List<Integer> blockIndexes)
      throws Exception
   {
      // block id --> dict index mapping (old index to new index)
      Map<String, Map<Integer, Integer>[]> dictMaps = new HashMap<>();
      // block id --> sizes
      Map<String, int[]> sizes = new HashMap<>();
      // block id --> dimRanges
      Map<String, int[]> dimRanges = new HashMap<>();
      // block id --> intRanges
      Map<String, List<Number[]>> intRanges = new HashMap<>();

      initMappings(nmv, dictMaps, sizes, dimRanges, intRanges, blockIndexes);

      if(canceled || dictMaps.size() == 0) {
         return false;
      }

      XServerNode server = FSService.getServer();

      if(server == null) {
         throw new RuntimeException("This host is not server node!");
      }

      String name = mvdef.getName();
      XFileSystem sys = server.getFSystem();
      sys.updateRecord(name, dictMaps, sizes, dimRanges, intRanges);

      return true;
   }

   /**
    * Dispatch record.
    */
   private boolean dispatchRecord(MV nMV, Iterator<SubMV> subs) throws Exception {
      String name = mvdef.getName();
      String file = MVStorage.getFile(name);
      XServerNode server = FSService.getServer();

      if(server == null) {
         throw new RuntimeException("This host is not server node!");
      }

      if(canceled) {
         return false;
      }

      List<BlockFile> files = new ArrayList<>();
      List<Integer> blockIndexes = new ArrayList<>();
      int counter = 0;

      while(subs.hasNext()) {
         SubMV smv = subs.next();

         if(canceled) {
            return false;
         }

         BlockFile sfile = new CacheBlockFile("name-" + (counter++) + "-" +
                                                 UUID.randomUUID().toString().replace("-", ""), ".smv");
         TimedQueue.add(new TimedQueue.TimedRunnable(86400000) {
            @Override
            public void run() {
               sfile.delete();
            }
         });
         smv.write(sfile);
         files.add(sfile);
         blockIndexes.add(smv.getBlockIndex());
         smv.dispose();
      }

      boolean changed = dispatchMapping(nMV, blockIndexes);

      if(canceled) {
         return changed;
      }

      nMV.save(file);
       // replace mv, otherwise the refresh will save old mv
      mv = nMV;

      try {
         XFileSystem sys = server.getFSystem();
         sys.appendRecord(name, files, blockIndexes);
      }
      finally {
         // remove temp files if any
         for(BlockFile file1 : files) {
            if(file1 != null && file1.exists() && !file1.delete()) {
               LOG.warn("Failed to delete materialized view block file: {}", file1);
            }
         }

         // new mv saved? don't need to update again
         // return false;
      }

      return true;
   }

   /**
    * Init mappings.
    */
   @SuppressWarnings("unchecked")
   private void initMappings(MV nMV, Map dictIdxMap, Map size, Map dimRange,
      Map intRange, List<Integer> blockIndexes)
   {
      int ccnt = mv.getDimCount() + mv.getMeasureCount();
      XDimDictionary dict;
      XDimDictionary ndict;
      Map<Integer, String> i2b = getBlockIDs();

      for(Integer blockIndex : blockIndexes) {
         if(blockIndex < 0) {
            continue;
         }

         String bid = i2b.get(blockIndex);
         Map[] mappings = new HashMap[ccnt];
         int[] sizes = new int[ccnt];
         int[] dimRanges = new int[ccnt];
         List<Number[]> intRanges = new ArrayList<>();

         for(int i = 0; i < ccnt; i++) {
            dict = mv.getDictionary(i, blockIndex);
            ndict = nMV.getDictionary(i, blockIndex);

            if(dict == null || ndict == null) {
               continue;
            }

            int length = dict.size();

            if(dict.isOverflow() || ndict.isOverflow()) {
               continue;
            }

            for(int j = 0; j < length; j++) {
               Object obj = dict.getValue(j);
               int nidx = ndict.indexOf(obj, j);

               if(j == nidx) {
                  continue;
               }

               if(mappings[i] == null) {
                  mappings[i] = new HashMap<Integer, Integer>();
                  sizes[i] = ndict.size();
                  dimRanges[i] = ndict.getRangeMin();
               }

               mappings[i].put(j, nidx);
            }
         }

         initIntRanges(nMV, intRanges, blockIndex);

         if(isNull(mappings)) {
            continue;
         }

         dictIdxMap.put(bid, mappings);
         size.put(bid, sizes);
         dimRange.put(bid, dimRanges);
         intRange.put(bid, intRanges);
      }
   }

   /**
    * Check the maps is all null.
    */
   private boolean isNull(Map[] maps) {
      for(Map map : maps) {
         if(map != null && map.size() > 0) {
            return false;
         }
      }

      return true;
   }

   /**
    * Init mv int column ranges.
    */
   private void initIntRanges(MV mv, List<Number[]> ranges, int blockIndex) {
      MVBlockInfo binfo = mv.getBlockInfo(blockIndex);
      MVColumnInfo[] cinfos = binfo.getColumnInfos();

      for(MVColumnInfo cinfo : cinfos) {
         if(cinfo.isNumber()) {
            ranges.add(new Number[]{(Number) cinfo.getMin(),
                                    (Number) cinfo.getMax()});
         }
      }
   }

   /**
    * Get the mapping of the index --> blockID.
    */
   private Map<Integer, String> getBlockIDs() {
      XServerNode server = FSService.getServer();

      if(server == null) {
         throw new RuntimeException("This host is not server node!");
      }

      String name = mvdef.getName();
      XFileSystem sys = server.getFSystem();
      XFile file = sys.get(name);
      List<SBlock> sblocks = file.getBlocks();
      Map<Integer, String> i2b = new HashMap<>();

      for(int i = 0; i < sblocks.size(); i++) {
         SBlock sblock = sblocks.get(i);
         String bid = sblock.getID();
         i2b.put(i, bid);
      }

      return i2b;
   }

   private boolean canceled = false;

   private static final Logger LOG =
      LoggerFactory.getLogger(LocalMVIncremental.class);
}
