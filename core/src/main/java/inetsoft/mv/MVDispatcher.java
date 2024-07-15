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
package inetsoft.mv;

import inetsoft.mv.data.*;
import inetsoft.mv.fs.BlockFile;
import inetsoft.mv.fs.FSService;
import inetsoft.mv.fs.internal.CacheBlockFile;
import inetsoft.report.TableLens;
import inetsoft.report.composition.WorksheetWrapper;
import inetsoft.report.composition.execution.*;
import inetsoft.report.lens.xnode.XNodeTableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.Identity;
import inetsoft.uql.util.XUtil;
import inetsoft.util.*;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * MVDispatcher, builds data from MVDef and dispatches it to
 * distributed file system.
 *
 * @author InetSoft Technology
 * @since  10.2
 */
public class MVDispatcher {
   /**
    * Create an instanceof MVDispatcher.
    */
   public MVDispatcher(MVDef def) {
      this.def = def;
      this.name = def.getName();
   }

   public boolean isCanceled() {
      return canceled || (data instanceof XNodeTableLens &&
         ((XNodeTableLens) data).isCancelled());
   }

   public void cancel() {
      AssetQuerySandbox box0 = box;

      if(box0 != null && box0.getQueryManager() != null) {
         box0.getQueryManager().cancel();
      }

      canceled = true;

      if(dispatchers != null) {
         for(MVDispatcher dispatcher : dispatchers) {
            if(dispatcher != null) {
               dispatcher.cancel();
            }
         }
      }
   }

   /**
    * Dispatch the MV.
    */
   public final void dispatch() throws Exception {
      MVStorage storage = MVStorage.getInstance();
      Worksheet ws = def.getWorksheet();
      String table = def.getMVTable();
      TableAssembly oassembly = (TableAssembly) ws.getAssembly(table);

      // try to split the table into multiple tables using the incremental
      // update column, so each sub-table can be processed in parallel.
      // this implies that only tables defined with incremental update
      // could be generated in parallel.
      TableAssembly assembly = split(oassembly);

      try {
         if(oassembly != assembly) {
            TableAssembly nassembly = assembly;
            ws.removeAssembly(oassembly.getName());
            ws.addAssembly(assembly);

            try {
               AssetQuerySandbox box = new AssetQuerySandbox(ws);
               box.setWSName(def.getWsId());
               AssetQuery query = AssetQuery.createAssetQuery(
                  assembly, AssetQuerySandbox.RUNTIME_MODE, box, false,
                  -1L, true, false);

               if(!query.isQueryMergeable(false)) {
                  nassembly = oassembly;
               }
            }
            catch(Throwable t) {
               nassembly = oassembly;
            }

            // not mergeable
            if(nassembly == oassembly) {
               LOG.info("use single dispatcher for query is not mergeable");
               assembly = oassembly;
               ws.removeAssembly(assembly.getName());
               ws.addAssembly(oassembly);
            }
         }

         processDispatch(oassembly != assembly);
         String file = MVStorage.getFile(name);

         if(storage.exists(file)) {
            storage.get(file).setSuccess(true);
         }
      }
      catch(Exception t) {
         String file = MVStorage.getFile(name);

         if(storage.exists(file)) {
            storage.get(file).setSuccess(false);
         }

         throw t;
      }
      finally {
         if(oassembly != assembly) {
            ws.removeAssembly(assembly.getName());
            ws.addAssembly(oassembly);
         }
      }
   }

   /**
    * Dispatch the MV creation job(s).
    * @param multi true to process in parallel.
    */
   private void processDispatch(boolean multi) throws Exception {
      if(multi) {
         int dispatcherCnt = getDispatcherCount();
         int threads = dispatcherCnt;
         ThreadPool pool = new ThreadPool(threads, threads);

         try {
            dispatchers = new MVDispatcher[threads];
            Calendar calendar = Calendar.getInstance();
            // assuming 12 months data
            int dinterval = Math.max(1, 12 / threads);
            int nrange = 1000000;

            // number of month in each interval, don't document it for now
            // just in case for tuning
            String str = SreeEnv.getProperty("mv.dispatcher.interval");

            if(str != null) {
               dinterval = Integer.parseInt(str);
            }

            str = SreeEnv.getProperty("mv.dispatcher.number.maximum");

            if(str != null) {
               nrange = Integer.parseInt(str);
            }

            int ninterval = nrange / threads;

            for(int i = 0; i < dispatcherCnt; i++) {
               VariableTable vars = new VariableTable();

               if(i == 0) {
                  vars.put("__FIRST__", Boolean.TRUE);
               }

               // all the way to the future
               if(i == 0) {
                  if(isDate) {
                     Calendar calendar0 = Calendar.getInstance();
                     // add 100 years instead of using MAX_VALUE, which
                     // causes year out of range error in oracle
                     calendar0.add(Calendar.YEAR, 100);
                     vars.put("__END__", calendar0.getTime());
                  }
                  // first block condition is >=, no <
               }
               else {
                  if(isDate) {
                     vars.put("__END__", calendar.getTime());
                  }
                  else {
                     vars.put("__END__", nrange);
                  }
               }

               // last dispatcher?
               if(i == dispatcherCnt - 1) {
                  if(isDate) {
                     calendar.set(1970, 0, 0, 0, 0, 0);
                  }
               }
               else {
                  if(isDate) {
                     calendar.add(Calendar.MONTH, -dinterval);
                  }
                  else {
                     nrange -= ninterval;
                  }
               }

               if(isDate) {
                  vars.put("__START__", calendar.getTime());
               }
               // last condition is <, no >=
               else if(i < dispatcherCnt - 1) {
                  vars.put("__START__", nrange);
               }

               MVCompositeDispatcher dispatcher =
                  new MVCompositeDispatcher(def, vars);
               dispatcher.number = this.number;
               dispatcher.isDate = this.isDate;
               dispatchers[i] = dispatcher;
               pool.add(dispatcher);
            }

            for(int i = 0; i < dispatchers.length; i++) {
               // wait until finished
               if(dispatchers[i] != null && dispatchers[i].isCompleted()) {
                  if(dispatchers[i].getException() != null) {
                     throw dispatchers[i].getException();
                  }
               }
            }
         }
         finally {
            try {
               pool.clear();
               pool.interrupt();
               pool.finalize();
            }
            catch(Throwable t) {
               // ignore it
            }
         }
      }
      // if not multi threads, only create for the first scheduler task
      else {
         dispatchers = new MVDispatcher[1];
         dispatchers[0] = new MVSingleDispatcher(def);
         dispatchers[0].dispatch0();
      }
   }

   protected boolean isCompleted() {
      return true;
   }

   public Exception getException() {
      return null;
   }

   protected void dispatch0() throws Exception {
      // do nothing, override by sub class
   }

   /**
    * Split the table assembly to multi assemblies to support multi threading
    * create mv.
    */
   private TableAssembly split(TableAssembly table) {
      int dispatcherCnt = getDispatcherCount();

      if(dispatcherCnt <= 1) {
         return table;
      }

      boolean desktop = FSService.getConfig().isDesktop();

      if(desktop) {
         return table;
      }

      ConditionListWrapper pre = table.getMVUpdatePreConditionList();

      // no update condition? cannot find which date column as split column
      if(pre == null || pre.isEmpty()) {
         return table;
      }

      ConditionList conds = pre.getConditionList();
      DataRef date = null;

      for(int i = 0; i < conds.getSize(); i++) {
         HierarchyItem item = conds.getItem(i);

         if(item instanceof ConditionItem) {
            DataRef ref = ((ConditionItem) item).getAttribute();
            String type = ref == null ? XSchema.STRING : ref.getDataType();

            if(XSchema.DATE.equals(type) || XSchema.TIME_INSTANT.equals(type)) {
               date = ref;
               break;
            }

            // if int/long, we assume it's an id and break it by in range
            if(number == null && (XSchema.INTEGER.equals(type) || XSchema.LONG.equals(type))) {
               number = ref;
            }
         }
      }

      if(date == null && number == null) {
         LOG.info("mv append condition without date/number column, " +
            "cannot use multi threading");
         return table;
      }

      pre = table.getPreConditionList();
      ConditionListWrapper rpre = table.getPreRuntimeConditionList();

      if(pre != null && !pre.isEmpty() && rpre != null && !rpre.isEmpty()) {
         LOG.info("pre condition and pre runtime condition both existed, " +
            "cannot use multi threading");
         return table;
      }

      boolean runtime = rpre != null && !rpre.isEmpty();

      if(runtime) {
         pre = rpre;
      }

      conds = pre.getConditionList();

      for(int i = 0; i < conds.getSize(); i++) {
         HierarchyItem item = conds.getItem(i);

         // level 0 operator only support AND
         if(item instanceof JunctionOperator) {
            JunctionOperator op = (JunctionOperator) item;

            if(op.getLevel() == 0 && op.getJunction() != JunctionOperator.AND) {
               LOG.info("mv append condition operator is not AND, " +
                  "it is too complex to use multi threading");
               return table;
            }
         }
      }

      table = (TableAssembly) table.clone();
      ConditionList tconds = (ConditionList) conds.clone();
      int level = 0;

      if(tconds.getSize() > 0) {
         tconds.append(new JunctionOperator(JunctionOperator.AND, 0));
         level = 1;
      }

      isDate = date != null;
      String dataType = date != null ? XSchema.DATE : number.getDataType();

      if(isDate) {
         AssetCondition acond = new AssetCondition(dataType);
         acond.setConvertingType(false);
         acond.setOperation(XCondition.BETWEEN);
         acond.addValue("$(__START__)");
         acond.addValue("$(__END__)");
         tconds.append(new ConditionItem(date, acond, level));
         tconds.append(new JunctionOperator(JunctionOperator.OR, level));

         acond = new AssetCondition(dataType);
         acond.setConvertingType(false);
         acond.setOperation(XCondition.NULL);
         acond.addValue("$(__FIRST__)");
         tconds.append(new ConditionItem(date, acond, level));
      }
      else {
         AssetCondition acond = new AssetCondition(dataType);
         acond.setConvertingType(false);
         acond.setOperation(XCondition.GREATER_THAN);
         acond.setEqual(true);
         acond.addValue("$(__START__)");
         tconds.append(new ConditionItem(number, acond, level));

         tconds.append(new JunctionOperator(JunctionOperator.AND, level));

         acond = new AssetCondition(dataType);
         acond.setConvertingType(false);
         acond.setOperation(XCondition.LESS_THAN);
         acond.addValue("$(__END__)");
         tconds.append(new ConditionItem(number, acond, level));

         tconds.append(new JunctionOperator(JunctionOperator.OR, level));

         acond = new AssetCondition(dataType);
         acond.setConvertingType(false);
         acond.setOperation(XCondition.NULL);
         acond.addValue("$(__FIRST__)");
         tconds.append(new ConditionItem(number, acond, level));
      }

      if(runtime) {
         table.setPreRuntimeConditionList(tconds);
      }
      else {
         table.setPreConditionList(tconds);
      }

      return table;
   }

   /**
    * Get the data.
    */
   protected XTable getData(boolean desktop, VariableTable bvar) throws Exception {
      if(data == null && def != null) {
         Worksheet ws = def.getWorksheet();
         String table = def.getMVTable();
         Identity[] users = def.getUsers();
         XPrincipal user = users == null || users.length == 0 ? null : users[0].create();
         TableAssembly assembly = (TableAssembly) ws.getAssembly(table);
         String breakcol = def.getBreakColumn();
         TableAssembly oassembly = assembly;
         String mname = null;
         boolean refreshIdentifier = true;

         if(breakcol != null && !desktop) {
            ws = new WorksheetWrapper(ws);
            assembly = (TableAssembly) ws.getAssembly(table);
            oassembly = assembly;
            mname = AssetUtil.getNextName(ws, "blk");
            MirrorTableAssembly mirror = new MirrorTableAssembly(ws, mname, assembly);
            SortInfo sortinfo = new SortInfo();
            SortRef sref = new SortRef(new AttributeRef(table, breakcol));

            sref.setOrder(XConstants.SORT_ASC);
            sortinfo.addSort(sref);
            mirror.setSortInfo(sortinfo);
            ws.addAssembly(mirror);
            assembly = mirror;
            refreshIdentifier = false;
         }
         else {
            oassembly = (TableAssembly) oassembly.clone();
            assembly = oassembly;
         }

         fixBlockCondition(oassembly, bvar);
         MVCreatorUtil.mergeConditionList(oassembly);

         box = MVCreatorUtil.createAssetQuerySandbox(def, bvar, user);

         try {
            box.setCreatingMV(true);
            MVCreatorUtil.setupTable(def, assembly, box);

            // set time as now, not to use cached data when create mv
            data = AssetDataCache.getData(null, assembly, box, null,
                                          AssetQuerySandbox.RUNTIME_MODE,
                                          false, System.currentTimeMillis(),
                                          box.getQueryManager());


            if(refreshIdentifier) {
               refreshColumnIdentifiers((TableLens) data, assembly);
            }

            MVBenchmark.addMV(file -> {
               data.moreRows(XTable.EOT);
               int dimensionCount =
                  (int) def.getColumns().stream().filter(MVColumn::isDimension).count();
               int measureCount = def.getColumns().size() - dimensionCount;
               file.put(name, data.getRowCount(), data.getColCount(), dimensionCount, measureCount);
            });
         }
         catch(Throwable ex) {
            if(assembly.getRuntimeMV() != null && def.isAssociationMV()) {
               throw new CancelledException(
                  "Association MV canceled due to problem generating data, " +
                  "possibly caused by hidden columns defined in VPM");
            }
            else {
               throw new RuntimeException("Error executing query for MV data", ex);
            }
         }
         finally {
            box.setCreatingMV(false);
         }

         // an empty table is returned if connection to db failed
         if(data == null || data.getColCount() == 0) {
            throw new RuntimeException("Error executing query for MV data");
         }

         box = null;
      }

      return data;
   }

   private void refreshColumnIdentifiers(TableLens data, TableAssembly assembly) {
      if(data == null) {
         return;
      }

      data = AssetQuery.shuckOffFormat(data);
      String tablePrefix = assembly.getName() + ".";

      for(int i = 0; i < data.getColCount(); i++) {
         String header = AssetUtil.format(XUtil.getHeader(data, i));
         String originalIdentifier = data.getColumnIdentifier(i);

         if(Tool.equals(XUtil.getDefaultColumnName(i), header) &&
            (StringUtils.isEmpty(originalIdentifier) || originalIdentifier.endsWith(".")))
         {
            header = tablePrefix;
         }
         else if(header != null && !header.startsWith(tablePrefix)) {
            header = tablePrefix + header;
         }

         data.setColumnIdentifier(i, header);
      }
   }

   private void fixBlockCondition(TableAssembly table, VariableTable vars) {
      if(vars == null) {
         return;
      }

      ConditionListWrapper conds = table.getPreRuntimeConditionList();
      removeNullConds(conds, vars);
      conds = table.getPreConditionList();
      removeNullConds(conds, vars);

      if(number == null) {
         return;
      }

      conds = table.getPreRuntimeConditionList();
      fixConds(conds, vars);
      conds = table.getPreConditionList();
      fixConds(conds, vars);
   }

   private void removeNullConds(ConditionListWrapper conds, VariableTable vars)
   {
      if(vars.contains("__FIRST__")) {
         return;
      }

      if(conds == null) {
         return;
      }

      ConditionList list = conds.getConditionList();

      for(int i = 0; i < list.getConditionSize(); i += 2) {
         ConditionItem cond = list.getConditionItem(i);
         Condition xcond = cond.getCondition();
         String value = xcond.getValue(0) + "";

         if("$(__FIRST__)".equals(value)) {
            list.remove(i);
            list.remove(i - 1);
            return;
         }
      }
   }

   private void fixConds(ConditionListWrapper conds, VariableTable vars) {
      if(conds == null) {
         return;
      }

      boolean hasStart = vars.contains("__START__");
      boolean hasEnd = vars.contains("__END__");

      if(hasStart && hasEnd) {
         return;
      }

      ConditionList list = conds.getConditionList();

      for(int i = 0; i < list.getConditionSize(); i += 2) {
         ConditionItem cond = list.getConditionItem(i);
         DataRef attr = cond.getAttribute();

         if(number.equals(attr)) {
            Condition xcond = cond.getCondition();
            String value = xcond.getValue(0) + "";

            // replace variable value to real value, otherwise some
            // database will cause value out of range exception, like db2
            if("$(__START__)".equals(value) && !hasStart) {
               list.remove(i + 1);
               list.remove(i);
               i = i - 2;
            }
            else if("$(__END__)".equals(value) && !hasEnd) {
               list.remove(i);
               list.remove(i - 1);
               i = i - 2;
            }
         }
      }
   }

   /**
    * Get mv builder.
    */
   protected MVBuilder getMVBuilder() {
      boolean aggregated = MVCreatorUtil.isAggregated(def);

      if(def != null) {
         return new MVBuilder(data, def, aggregated);
      }

      return new MVBuilder(data, dims, measures, aggregated);
   }

   /**
    * Save to temp file.
    */
   protected List<BlockFile> saveTempFile(MVBuilder builder) throws Exception {
      // save to temp files
      Iterator<SubMV> it = builder.getSubMVs();
      List<BlockFile> list = new ArrayList<>();
      int counter = 0;

      while(it.hasNext()) {
         SubMV smv = it.next();

         if(canceled) {
            return list;
         }

         BlockFile file = createFile(counter++);
         TimedQueue.add(new TimedQueue.TimedRunnable(86400000) {
            @Override
            public void run() {
               file.delete();
            }
         });
         smv.write(file);
         list.add(file);
         smv.dispose();
      }

      return list;
   }

   /**
    * Create file to save sub mv block.
    */
   protected BlockFile createFile(int counter) throws Exception {
      return new CacheBlockFile("name-" + counter + "-" +
                                   UUID.randomUUID().toString().replace("-", "") + ".smv");
   }

   private int getDispatcherCount() {
      String str = SreeEnv.getProperty("mv.dispatcher.count");
      int dispatcherCnt = Tool.getAvailableCPUCores();

      if(str != null) {
         try {
            dispatcherCnt = Integer.parseInt(str);
         }
         catch(Exception ex) {
            // ignore it
         }
      }

      return dispatcherCnt;
   }

   /**
    * Merge mv.
    */
   public static MV mergeMV(MV mv, MV pmv) {
      // empty block? some status may be wrong, so use another mv
      // fix bug1404124708101
      if(pmv == null || pmv.getBlockSize() <= 0) {
         return mv;
      }

      if(mv == null || mv.getBlockSize() <= 0) {
         return pmv;
      }

      long start = System.currentTimeMillis();

      // 1: add block from mv to pmv, so the block sequence will be kept
      for(int i = 0; i < mv.getBlockSize(); i++) {
         MVBlockInfo binfo = mv.getBlockInfo(i);
         MVColumnInfo[] cinfos = binfo.getColumnInfos();

         // update dictionary index
         for(int j = 0; j < cinfos.length; j++) {
            XDimDictionary dict = mv.getDictionary(j, i);

            if(dict != null) {
               XDimDictionaryIndex dictIndex = pmv.getDictionaryIndex(j, dict);
               cinfos[j].setDictionary(dictIndex);
            }
         }

         pmv.addBlockInfo(binfo);
      }

      // 2: break value will have problem, here not implement, TO DO

      // 3: update def
      MVDef def = mv.getDef(true);
      List<MVColumn> cols = def.getColumns();
      MVDef pdef = pmv.getDef(true);
      List<MVColumn> pcols = pdef.getColumns();

      for(int i = 0; i < cols.size(); i++) {
         MVColumn col = cols.get(i);
         Number max = col.getOriginalMax();
         Number min = col.getOriginalMin();
         MVColumn pcol = pcols.get(i);
         Number pmax = pcol.getOriginalMax();
         Number pmin = pcol.getOriginalMin();
         max = getMax(max, pmax);
         min = getMin(min, pmin);
         pcol.setRange(min, max);

         /*
         if(col instanceof RangeMVColumn) {
            RangeMVColumn rcol = (RangeMVColumn) col;
            max = rcol.getMax();
            min = rcol.getMin();
            RangeMVColumn rpcol = (RangeMVColumn) pcol;
            pmax = prcol.getMax();
            pmin = prcol.getMin();
            max = getMax(max, pmax);
            min = getMin(min, pmin);
            pcol.setMin(min);
            pcol.setMax(max);
         }
         */

         if(col instanceof DateMVColumn) {
            DateMVColumn dcol = (DateMVColumn) col;
            Date dmax = dcol.getMax();
            Date dmin = dcol.getMin();
            DateMVColumn dpcol = (DateMVColumn) pcol;
            Date dpmax = dcol.getMax();
            Date dpmin = dcol.getMin();
            dpcol.convert(dpmin);
            dpcol.convert(dpmax);
         }
      }

      long end = System.currentTimeMillis();
      LOG.debug("MV Block Job - MV merged [" + def.getName() +
              "]: " + (end - start) + " ms, node " + Tool.getHost());
      return pmv;
   }

   private static Number getMax(Number max1, Number max2) {
      if(max1 == null) {
         return max2;
      }

      if(max2 == null) {
         return max1;
      }

      return max1.doubleValue() > max2.doubleValue() ? max1 : max2;
   }

   private static Number getMin(Number min1, Number min2) {
      if(min1 == null) {
         return min1;
      }

      if(min2 == null) {
         return min2;
      }

      return min1.doubleValue() < min2.doubleValue() ? min1 : min2;
   }

   private static final Logger LOG = LoggerFactory.getLogger(MVDispatcher.class);
   protected MVDef def;
   protected String name;
   private boolean canceled = false;
   protected AssetQuerySandbox box;
   protected boolean isDate = false;
   protected DataRef number = null;
   private transient XTable data;
   private transient int[] dims;
   private transient int[] measures;

   private MVDispatcher[] dispatchers;
}
