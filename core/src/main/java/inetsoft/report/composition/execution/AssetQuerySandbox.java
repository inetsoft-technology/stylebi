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
package inetsoft.report.composition.execution;

import inetsoft.mv.*;
import inetsoft.report.TableFilter;
import inetsoft.report.TableLens;
import inetsoft.report.filter.ColumnMapFilter;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.XNodeMetaTable;
import inetsoft.report.internal.table.CancellableTableLens;
import inetsoft.report.lens.TextSizeLimitTableLens;
import inetsoft.report.script.formula.AssetQueryScope;
import inetsoft.report.script.viewsheet.ViewsheetScope;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.erm.vpm.VpmProcessor;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.util.SQLTypes;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.util.*;
import inetsoft.uql.viewsheet.SelectionVSAssembly;
import inetsoft.util.*;
import inetsoft.util.script.*;
import inetsoft.util.script.graal.pool.PoolConfig;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import inetsoft.web.messaging.MessageAttributes;
import inetsoft.web.messaging.MessageContextHolder;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.Serializable;
import java.security.Principal;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.function.BiFunction;

/**
 * Asset query sandbox, the box contains all data in a worksheet, and refreshes
 * data accordingly when the contained worksheet changes.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class AssetQuerySandbox implements Serializable, Cloneable, ActionListener {
   /**
    * Design mode.
    */
   public static final int DESIGN_MODE = 1;
   /**
    * Live data mode.
    */
   public static final int LIVE_MODE = 2;
   /**
    * Runtime mode.
    */
   public static final int RUNTIME_MODE = 4;
   /**
    * Embedded mode. This is used to attached to LIVE_MODE or DESIGN_MODE to make them behave
    * in a similar mannor as RUNTIME_MODE. Should consider refactoring since the meaning of
    * it is not well defined.
    */
   public static final int EMBEDDED_MODE = 8;
   /**
    * Browse mode.
    */
   public static final int BROWSE_MODE = LIVE_MODE | 16;
   /**
    * Edit mode.
    */
   public static final int EDIT_MODE = DESIGN_MODE | 32;

   /**
    * Check if is an embedded mode.
    */
   public static boolean isEmbeddedMode(int mode) {
      return (mode & EMBEDDED_MODE) == EMBEDDED_MODE;
   }

   /**
    * Check if is a design mode.
    */
   public static boolean isDesignMode(int mode) {
      return (mode & DESIGN_MODE) == DESIGN_MODE;
   }

   /**
    * Check if is a live mode.
    */
   public static boolean isLiveMode(int mode) {
      return (mode & LIVE_MODE) == LIVE_MODE;
   }

   /**
    * Check if is a runtime mode.
    */
   public static boolean isRuntimeMode(int mode) {
      return (mode & RUNTIME_MODE) == RUNTIME_MODE;
   }

   /**
    * Check if box is in edit mode.
    */
   public static boolean isEditMode(int mode) {
      return (mode & EDIT_MODE) == EDIT_MODE;
   }

   /**
    * Constructor.
    */
   public AssetQuerySandbox(Worksheet ws) {
      super();

      setVariables(new CachedVariableTable());
      this.falias = true;
      tmap = new ConcurrentHashMap<>();
      cmap = new ConcurrentHashMap<>();
      selections = new Hashtable<>();
      nolimit = new HashSet<>();
      mvsession = MVTool.newMVSession();
      setWorksheet(ws);
   }

   /**
    * Constructor.
    */
   public AssetQuerySandbox(Worksheet ws, XPrincipal user, VariableTable vars) {
      this(ws);

      setVariables(vars);
      setBaseUser(user);
   }

   /**
    * Set the worksheet.
    * @param ws the specified worksheet.
    */
   public void setWorksheet(Worksheet ws) {
      if(this.ws != null) {
         this.ws.removeActionListener(this);
      }

      this.ws = ws;

      if(ws != null) {
         this.ws.addActionListener(this);
      }

      reset();
      shrink();
   }

   /**
    * Get the worksheet.
    */
   public Worksheet getWorksheet() {
      return ws;
   }

   /**
    * Get the MV session for executing this worksheet.
    */
   public MVSession getMVSession() {
      return mvsession;
   }

   /**
    * Check if the box is active.
    */
   public boolean isActive() {
      return active;
   }

   /**
    * Set whether the box is active. It's active if this is for a runtime
    * worksheet (and not a viewsheet).
    */
   public void setActive(boolean active) {
      this.active = active;
   }

   /**
    * Set the max rows for live data.
    */
   public void setMaxRows(int max) {
      this.maxRows = max;
   }

   /**
    * Get the max rows for live data.
    */
   public int getMaxRows() {
      return maxRows;
   }

   /**
    * Check if the box is fixing alias.
    * @return <tt>true</tt> if yes, false otherwise.
    */
   public boolean isFixingAlias() {
      return falias;
   }

   /**
    * Set whether the box is fixing alias.
    * @param falias <tt>true</tt> if yes, false otherwise.
    */
   public void setFixingAlias(boolean falias) {
      this.falias = falias;
   }

   /**
    * Set the base user of the sandbox.
    * @param baseUser the specified principal.
    */
   public void setBaseUser(Principal baseUser) {
      this.baseUser = baseUser;

      if(baseUser instanceof XPrincipal) {
         VariableTable vars = getVariableTable();

         if(vars != null) {
            vars.copyParameters((XPrincipal) baseUser);
         }
      }
   }

   /**
    * Get the base user of the sandbox.
    *
    * @return the base user of the sandbox.
    */
   public Principal getBaseUser() {
      return baseUser;
   }

   /**
    * Get the user of the sandbox.
    *
    * @return the user of the sandbox.
    */
   public Principal getUser() {
      final Principal user;

      if(vpmUser != null) {
         user = vpmUser;
      }
      else {
         user = baseUser;
      }

      return user;
   }

   /**
    * Get the user of the sandbox for browse data.
    *
    * @return the user of the sandbox.
    */
   public Principal getBrowseUser() {
      final Principal user;

      if(vpmUser != null) {
         user = vpmUser;
      }
      else if(baseUser instanceof SRPrincipal) {
         final boolean virtual = "true".equals(((SRPrincipal) baseUser).getProperty("virtual"));
         user = virtual ? null : baseUser;
      }
      else {
         user = baseUser;
      }

      return user;
   }

   /**
    * Set the vpm user of the sandbox.
    * @param vpmUser the specified principal.
    */
   public void setVPMUser(XPrincipal vpmUser) {
      this.vpmUser = vpmUser;

      if(vpmUser != null) {
         vpmUser.setProperty(SUtil.VPM_USER, "true");
         ConnectionProcessor.getInstance().setAdditionalDatasource(vpmUser);
         VariableTable vars = getVariableTable();

         if(vars != null) {
            vars.copyParameters(vpmUser);
         }
      }
   }

   /**
    * Get the vpm user of the sandbox.
    * @return the vpm user of the sandbox.
    */
   public XPrincipal getVPMUser() {
      return vpmUser;
   }

   /**
    * Mark a table to ignore the time limit check.
    * @param tname the specified table assembly name.
    * @param limited <tt>true</tt> to check the time limit, <tt>false</tt>
    * otherwise.
    */
   public void setTimeLimited(String tname, boolean limited) {
      if(limited) {
         nolimit.remove(tname);
      }
      else {
         nolimit.add(tname);
      }
   }

   /**
    * Check if time limit should be applied to a table.
    * @param tname the specified table assembly name.
    * @return <tt>true</tt> to check the time limit, <tt>false</tt> otherwise.
    */
   public boolean isTimeLimited(String tname) {
      return !nolimit.contains(tname);
   }

   /**
    * Triggered when viewsheet action performed.
    * @param evt the specified action event.
    */
   @Override
   public void actionPerformed(ActionEvent evt) {
      int id = evt.getID();
      String cmd = evt.getActionCommand();

      if(id == Worksheet.REMOVE_ASSEMBLY) {
         shrink();
      }
      else if(id == Worksheet.RENAME_ASSEMBLY) {
         int index = cmd.indexOf("^");
         String oname = cmd.substring(0, index);
         String nname = cmd.substring(index + 1);
         List<TableEntry> entries = new ArrayList<>(tmap.keySet());

         for(TableEntry entry : entries) {
            if(entry.matches(oname)) {
               TableLens data = tmap.remove(entry);
               entry = new TableEntry(nname, entry.mode, entry.aggregate, entry.chash);
               tmap.put(entry, data);
            }
         }

         Map<String, ColumnSelection> cmap = this.cmap;

         if(cmap == null) {
            return;
         }

         ColumnSelection selection = cmap.remove(oname);

         if(selection != null) {
            cmap.put(nname, selection);
         }

         if(nolimit.contains(oname)) {
            nolimit.remove(oname);
            nolimit.add(nname);
         }
      }
   }

   /**
    * Reset the sandbox.
    */
   public void reset() {
      resetTableLens();
      resetDefaultColumnSelection();
      ScriptEnv old;

      synchronized(lock) {
         old = senv;
         senv = null;
      }

      retire(old);
      scope = null;
   }

   /**
    * Reset an assembly.
    * @param entry the entry to reset.
    */
   public void reset(AssemblyEntry entry) throws Exception {
      reset(entry, true);
   }

   /**
    * Reset an assembly.
    * @param entry the entry to reset.
    * @param recursive <tt>true</tt> to reset the dependency assemblies
    * recursively, <tt>false</tt> otherwise.
    */
   public void reset(AssemblyEntry entry, boolean recursive) {
      String name = entry.getName();
      resetTableLens(name);
      resetDefaultColumnSelection(name);

      if(recursive) {
         AssemblyRef[] refs = ws.getDependings(entry);

         for(AssemblyRef ref : refs) {
            reset(ref.getEntry(), recursive);
         }
      }
   }

   /**
    * Refresh the column selection of a table.
    * @param name the specified table name.
    * @param recursive refresh column selection from root table to the specified
    * table.
    */
   public void refreshColumnSelection(String name, boolean recursive)
      throws Exception
   {
      refreshColumnSelection(name, recursive, false);
   }

   /**
    * Refresh the column selection of a table.
    * @param name the specified table name.
    * @param recursive refresh column selection from root table to the specified
    * table.
    * @param metadata true if metadata should be used to refresh the column selection
    */
   public void refreshColumnSelection(String name, boolean recursive, boolean metadata)
      throws Exception
   {
      Assembly assembly = ws.getAssembly(name);

      // is a table? refresh column selection for this table
      if(assembly instanceof TableAssembly) {
         resetDefaultColumnSelection(name);

         TableAssembly table = (TableAssembly) assembly;
         TableAssembly table2 = (TableAssembly) table.clone();
         // incase the old saved columnselection was not lastest, so don't force to
         // set the columns which are not in the base columnselection to be invisbible
         // (when box active is false for vs) in the first time validate column selection
         // once the asset query was created, to avoid the columns never be visible even
         // the base columnselection was refreshed and match the default columnselection,
         // and leave hide the columns not exist in the default columnselection process to
         // the later validate columnselection (59407).
         boolean active = this.active;
         this.setActive(true);
         AssetQuery query = AssetQuery.createAssetQuery(
            table2, LIVE_MODE | EMBEDDED_MODE, this, false, -1L, true, true);
         this.setActive(active);

         // keep the column header columns of a crosstab, this refresh runs with row
         // limits so it only sees a sample of the data, see
         // AssetQuery.keepCrosstabHeaderColumns (77538)
         if(!table.getAggregateInfo().isCrosstab()) {
            table2.setColumnSelection(new ColumnSelection(), true);
         }

         // not plain? execute the query to refresh the column selection
         if(active && !table.isPlain() && !metadata) {
            query.getTableLens((VariableTable) vars.clone());

            // the table might go through the transformer so get that table
            if(WSMVTransformer.containsWSRuntimeMV(table2)) {
               table2 = query.getTable();
            }
         }
         // normal? refresh the column selection directly
         else {
            query.validateColumnSelection();
         }

         table.setColumnSelection(table2.getColumnSelection());

         if(query instanceof UnpivotQuery) {
            table2.setColumnSelection(new ColumnSelection(), true);
         }

         if((active && !metadata) || table.isPlain()) {
            if(MVTransformer.containsRuntimeMV(table, true)) {
               table.setColumnSelection(query.getTable().getColumnSelection(true), true);
            }
            else {
               table.setColumnSelection(table2.getColumnSelection(true), true);
            }
         }

         resetTableLens(table.getName(), DESIGN_MODE);
      }

      if(recursive) {
         ws.checkDependencies();
         AssemblyRef[] arr = ws.getDependings(assembly.getAssemblyEntry());
         Assembly[] assemblies = new Assembly[arr.length];

         for(int i = 0; i < arr.length; i++) {
            assemblies[i] = ws.getAssembly(arr[i].getEntry().getName());
         }

         // sort assemblies according to dependencies
         Tool.mergeSort(assemblies, new DependencyComparator(ws, true));

         for(int i = 0; i < assemblies.length; i++) {
            WSAssembly sub = (WSAssembly) assemblies[i];
            sub.dependencyChanged(name);
            refreshColumnSelection(sub.getName(), recursive, metadata);
         }
      }
   }

   /**
    * Reset the validated information.
    */
   public void resetDefaultColumnSelection(String name) {
      Optional.ofNullable(cmap).ifPresent(c -> c.remove(name));
   }

   /**
    * Reset the validated information.
    */
   public void resetDefaultColumnSelection(String name, Worksheet ws) {
      TableAssembly table = (TableAssembly) ws.getAssembly(name);

      if(table instanceof ComposedTableAssembly) {
         Map<String, ColumnSelection> cmap = this.cmap;

         if(cmap == null) {
            return;
         }

         cmap.remove(name);
         ComposedTableAssembly ctable = (ComposedTableAssembly) table;
         TableAssembly[] tables = ctable.getTableAssemblies(false);

         for(final TableAssembly tableAssembly : tables) {
            resetDefaultColumnSelection(tableAssembly.getName(), ws);
         }
      }
   }

   /**
    * Reset the validated information.
    */
   public void resetDefaultColumnSelection() {
      Optional.ofNullable(cmap).ifPresent(Map::clear);
   }

   /**
    * Get the column infos mapping for joined table.
    */
   public Map<String, String> getColumnInfoMapping(String name) {
      Map<String, String> map = new HashMap<>();

      int index = name.indexOf("/");
      name = index < 0 ? name : name.substring(index + 1);
      Assembly assembly = ws.getAssembly(name);

      if(!(assembly instanceof CompositeTableAssembly)) {
         return map;
      }

      List<ColumnInfo> cinfos = null;

      try {
         cinfos = getColumnInfos(name, DESIGN_MODE | EMBEDDED_MODE);
      }
      catch(Exception ex) {
         // ignore it
      }

      if(cinfos != null) {
         for(ColumnInfo cinfo : cinfos) {
            Map<String, Integer> uniqueNames = new HashMap<>();
            String cname = cinfo.getName();
            String cheader = cinfo.getHeader();

            if(cname != null && !cname.equals(cheader)) {
               // @by ankitmathur for bug1433840009090, There are edge-cases
               // where two or more Worksheet datablocks have the same name
               // and Column names (not case-sensitive). For this case we need
               // to ensure "cname" is unique so that the correct "cheader" can
               // be placed in the "aliasmap" without replacing the previous
               // value.
               if(map.get(cname) != null) {
                  int nameCount = 1;

                  if(uniqueNames.get(cname) != null) {
                     nameCount = uniqueNames.get(cname) + 1;
                  }

                  uniqueNames.put(cname, nameCount);
                  cname = cname + nameCount;
               }

               map.put(cname, cheader);
            }
         }
      }

      return map;
   }

   /**
    * Get the column infos.
    * @param name the specified table assembly name.
    * @param mode the specified mode.
    * @return the column infos.
    */
   public List<ColumnInfo> getColumnInfos(String name, int mode) throws Exception {
      int index = name.indexOf("/");
      name = index < 0 ? name : name.substring(index + 1);
      Assembly assembly = ws.getAssembly(name);

      if(!(assembly instanceof TableAssembly)) {
         return new ArrayList<>();
      }

      TableAssembly tassembly = (TableAssembly) assembly;
      TableLens table = getTableLens(name, mode);
      SortInfo sinfo = tassembly.getSortInfo();
      AggregateInfo ginfo = tassembly.getAggregateInfo();
      boolean pub = isRuntimeMode(mode) || isEmbeddedMode(mode) ||
         tassembly.isAggregate();
      ColumnSelection columns = tassembly.getColumnSelection(pub);
      ColumnSelection allCols = tassembly.getColumnSelection(false);
      List<ColumnInfo> list = new ArrayList<>();

      for(int i = 0; table != null && i < table.getColCount(); i++) {
         ColumnRef column = AssetUtil.findColumn(table, i, columns);

         if(column == null) {
            LOG.debug("Column not found: " + XUtil.getHeader(table, i),
               new Exception("Stack trace"));
            continue;
         }

         SortRef sort = (tassembly instanceof CubeTableAssembly) ?
            getSortRef(sinfo, column) : sinfo.getSort(column);
         sort = sort == null ? null : (SortRef) sort.clone();
         GroupRef group = ginfo.getGroup(column);
         group = group == null ? null : (GroupRef) group.clone();

         if(group == null && tassembly.isAggregate() &&
            tassembly instanceof CubeTableAssembly)
         {
            group = ginfo.getGroup(AssetUtil.findCubeColumn(allCols, column));
         }

         AggregateRef aggregate = ginfo.getAggregate(column);
         aggregate = aggregate == null ? null : (AggregateRef) aggregate.clone();

         if(aggregate == null && tassembly.isAggregate() &&
            tassembly instanceof CubeTableAssembly)
         {
            aggregate = ginfo.getAggregate(AssetUtil.findCubeColumn(allCols, column));
         }

         String header = AssetUtil.format(XUtil.getHeader(table, i));
         header = prefixLocalize(header);

         ColumnInfo info = new ColumnInfo(column.clone(), sort, group, aggregate, name,
                                          header, ginfo.isCrosstab());
         list.add(info);
      }

      return list;
   }

   /**
    * Get sort ref of the column ref. For cube column, find by alias else
    * attribute.
    */
   private SortRef getSortRef(SortInfo sinfo, ColumnRef col) {
      SortRef sref = sinfo.getSort(col);

      if(sref != null) {
         return sref;
      }

      SortRef[] sorts = sinfo.getSorts();
      String name = col.getAttribute();

      for(SortRef sort : sorts) {
         if(name.equals(((ColumnRef) sort.getDataRef()).getAlias())) {
            return sort;
         }
      }

      for(SortRef sort : sorts) {
         if(name.equals(sort.getAttribute())) {
            return sort;
         }
      }

      return null;
   }

   /**
    * Get all variables require to collect values.
    * @param vars ignore the variables that are already set in vars.
    * @return the variable list.
    */
   public UserVariable[] getAllVariables(VariableTable vars) {
      if(ws == null) {
         return new UserVariable[0];
      }

      ws.update();

      List<UserVariable> list = new ArrayList<>();
      UserVariable[] uvars = ws.getAllVariables();

      for(int i = 0; i < uvars.length; i++) {
         final Principal principal = getUser();

         if(principal instanceof XPrincipal) {
            XPrincipal xuser = (XPrincipal) principal;

            if(uvars[i].getName().startsWith("_Db_") &&
               xuser.getProperty(uvars[i].getName()) != null)
            {
               continue;
            }
         }

         if(!vars.contains(uvars[i].getName())) {
            list.add(uvars[i]);
         }
      }

      if(vprovider2 != null) {
         uvars = vprovider2.getAllVariables();

         for(int i = 0; i < uvars.length; i++) {
            if(!vars.contains(uvars[i].getName()) &&
               !AssetUtil.containsVariable(list, uvars[i]))
            {
               list.add(uvars[i]);
            }
         }
      }

      uvars = new UserVariable[list.size()];
      list.toArray(uvars);
      return uvars;
   }

   /**
    * Set the additional variable provider.
    */
   public void setAdditionalVariableProvider(VariableProvider provider) {
      this.vprovider2 = provider;
   }

   /**
    * Get the additional variable provider.
    */
   public VariableProvider getAdditionalVariableProvider() {
      return vprovider2;
   }

   /**
    * Get the variable table.
    * @return the variable table.
    */
   public VariableTable getVariableTable() {
      return vars;
   }

   /**
    * Get selections.
    * @return selection assembly map.
    */
   public Hashtable<String, SelectionVSAssembly> getSelections() {
      return selections;
   }

   /**
    * Refresh the selection map.
    * @param sel the selection map, name -> SelectionVSAssembly.
    */
   public void refreshSelections(Hashtable<String, SelectionVSAssembly> sel) {
      if(sel == null) {
         return;
      }

      selections.putAll(sel);
   }

   /**
    * Refresh the variable table.
    * @param vars the specified variable table.
    */
   public void refreshVariableTable(VariableTable vars) throws Exception {
      if(vars == null) {
         return;
      }

      this.vars.clearNullIgnored();
      Enumeration<String> keys = vars.keys();

      while(keys.hasMoreElements()) {
         String key = keys.nextElement();
         Object val = vars.get(key);
         this.vars.put(key, val);
         this.vars.setAsIs(key, vars.isAsIs(key));

         if(vars.isNotIgnoredNull(key)) {
            this.vars.setNotIgnoredNull(key);
         }
      }

      if(scope != null) {
         scope.setVariableTable(this.vars);
      }
   }

   /**
    * Reset the variable table.
    */
   public void resetVariableTable() {
      setVariables(new VariableTable());

      if(scope != null) {
         scope.setVariableTable(vars);
      }
   }

   /**
    * Get the table of a table assembly.
    * @param name the specified table name.
    * @param mode the specified mode.
    * @return the table of the query.
    */
   public TableLens getTableLens(String name, int mode) throws Exception {
      return getTableLens(name, mode, null);
   }

   /**
    * Get the table of a table assembly.
    * @param name the specified table name.
    * @param mode the specified mode.
    * @param vars the specified variable table.
    * @return the table of the query.
    */
   public TableLens getTableLens(String name, int mode, VariableTable vars) throws Exception {
      // may be disposed
      if(isDisposed()) {
         throw new RuntimeException("Asset query sandbox is disposed");
      }

      if(ws == null) {
         return null;
      }

      String cubeName = name;
      int index = name.indexOf("/");
      name = index < 0 ? name : name.substring(index + 1);
      Worksheet _ws = ws;
      Assembly assembly = _ws.getAssembly(name);

      // consider cube table
      if(assembly == null) {
         Assembly cube = ws.getAssembly(cubeName);

         if(cube instanceof CubeTableAssembly) {
            name = cubeName;
            assembly = cube;
         }
      }

      if(!(assembly instanceof TableAssembly)) {
         return null;
      }

      TableAssembly table = (TableAssembly) assembly;
      AggregateInfo ainfo = table.getAggregateInfo();
      boolean pub = isRuntimeMode(mode) ||
         (isLiveMode(mode) && (table.isAggregate() || ainfo.isEmpty()));
      TableLens data;

      // For EmbeddedTableAssembly, keep the entire setup+execution atomic under
      // synchronized(table) to prevent state mutation between setup and execution.
      // For cloned tables (non-EmbeddedTableAssembly), table2 is per-thread so query
      // execution runs outside synchronized(table), eliminating lock contention on
      // concurrent worksheet opens (Bug #73971).
      if(table instanceof EmbeddedTableAssembly) {
         data = getEmbeddedTableLens(table, name, mode, pub, vars);
      }
      else {
         // Clone the table so each thread gets its own copy. The synchronized(table) block
         // is kept short (cache check + clone only) so concurrent users are not blocked
         // during the expensive query execution / I/O phase. (Bug #73971)
         TableAssembly table2;

         synchronized(table) {
            ColumnSelection columns = table.getColumnSelection(pub);
            data = getTableLens0(name, mode, table.isAggregate(), columns.hashCode());
            int ncol = columns.getAttributeCount();

            // the columns of a crosstab don't match its data, the column header columns
            // missing from a row-limited run are kept (77538)
            // a result whose rows failed to load holds only the rows read before the failure,
            // run the query again instead of reusing it (Bug #77901)
            if(data != null && (data.getColCount() == ncol || pub && ainfo.isCrosstab()) &&
               AssetDataCache.getLoadException(data) == null)
            {
               return data;
            }

            table2 = (TableAssembly) table.clone();
         }

         data = executeQuery(table, table2, name, mode, pub, vars);
      }

      return data;
   }

   /**
    * Build (or return the cached) table lens for an {@code EmbeddedTableAssembly}, keeping
    * setup+execution atomic under {@code synchronized(table)} (Bug #73971) while avoiding the
    * AB-BA deadlock that atomicity can otherwise create against the worksheet's shared GraalJS
    * script-execution lock (Bug #77301).
    *
    * <p>Building an embedded table can force eager formula-row computation (the type probe in
    * {@code AssetQuery.getRuntimeTableLens}/{@code validateDataTypes}, which samples up to 1000
    * rows through {@code Util.getColType}), which blocks in {@code FormulaTableLens.lockForRow}
    * on the sandbox's shared script-execution lock whenever the context pool is off. A
    * concurrent script elsewhere in the same worksheet that reads this same table by name (e.g.
    * {@code A.length}) reaches {@code TableAssemblyScriptable.getElementTable()} while it
    * already holds that same lock (it is acquired for the whole duration of
    * {@code GraalJavaScriptEngine.exec()}), and blocks entering {@code synchronized(table)}
    * here. If this method took {@code synchronized(table)} before the script lock, the two
    * threads would acquire the monitor and the lock in opposite orders -- a classic AB-BA
    * deadlock, permanent in every {@code stall.watchdog.mode} (the default is {@code fail})
    * because entering a plain {@code synchronized} block can never time out or be interrupted.
    *
    * <p>The same cycle also forms one hop further out, with {@code table} having no expression
    * column of its own at all (review round 1 of this bug's fix): {@code
    * AssetQuery.getRuntimeTableLens} chains both {@link TableAssembly#getPreConditionList()} and
    * {@link TableAssembly#getPostConditionList()} onto the base lens (lines ~911-953) before its
    * <em>second</em> {@code validateDataTypes} call (line ~994, run against the fully-chained
    * lens -- unlike the first, earlier call against the unfiltered base). If either condition
    * list has a sub-query whose sub table can itself reach the script engine, the resulting
    * {@code PostProcessor$ConditionFilter2} sets its own {@code needsScriptLock} (bug #77158,
    * see {@code ConditionFilter2.subTableNeedsScriptLock}) and takes the engine lock the first
    * time {@code moreRows()} runs -- which that second {@code validateDataTypes} call forces,
    * still inside this method's {@code synchronized(table)}. A concurrent script that already
    * holds the lock and reads {@code table} by name blocks entering the monitor exactly as
    * above: the identical AB-BA cycle, just entered through a condition list instead of
    * {@code table}'s own columns.
    *
    * <p>Taking the script-execution lock first, before {@code synchronized(table)} -- the same
    * order {@code PostProcessor$ConditionFilter2} already uses for its own monitor (bug #76918)
    * -- keeps every path into this method ordered the same way: a thread already inside script
    * execution (already holding the lock) just re-acquires it (reentrant) and proceeds straight
    * to the monitor exactly as it does today, while a thread about to build the table for the
    * first time must wait for the lock before it ever touches the monitor, so it can never hold
    * the monitor while blocked on the lock.
    *
    * <p>The rule: the lock is taken first whenever anything the build can run under
    * {@code synchronized(table)} might reach the script engine -- not a list of shapes, since
    * every list so far turned out to miss one (bug #77873: condition expression values, a
    * JavaScript variable left in the variable table, the bases of a composed table under a
    * sub-query). {@code table}'s own script/JS expression column
    * ({@link #hasOwnScriptColumn(TableAssembly)}) is checked on its own, so this code can tell
    * when it is the reason (see below on why that distinction matters); everything else the
    * build reaches is one recursive walk ({@link #embeddedBuildMayRunScript}). A false positive
    * there only costs one extra,
    * uncontested lock acquisition (the same trade-off {@code PostProcessor$ConditionFilter2}
    * accepts for its own conservative checks), but forcing this path -- and the GraalJS
    * {@code Context} it requires, see below -- for every {@code EmbeddedTableAssembly} build
    * regardless of whether it can reach a script turned out not to be free: a first version of
    * this fix took it unconditionally, and it measurably slowed down unrelated concurrent
    * embedded-table builds enough to flake an unrelated timing-sensitive lock-cycle test
    * elsewhere in this same suite ({@code SubQueryConditionWorksheetCycleTest}'s
    * {@code WorkerGate}, which waits for a background worker to reach a specific row within a
    * fixed wall-clock budget).
    *
    * <p><strong>Round 1 of this fix shipped without the condition-list shape above</strong>,
    * reasoning it belonged to a separate, differently-shaped, already-tracked bug
    * ({@code SubQueryConditionWorksheetCycleTest.formulaReadsFilteredTableFirst}). Review round
    * 1 traced that this was a misattribution: {@code formulaReadsFilteredTableFirst}'s cycle is
    * entirely inside {@code ConditionFilter2}'s own monitor waiting on a background
    * {@code DistinctTableLens} worker holding the engine lock -- a worker-lending question
    * (bug #76938's family, needing a fundamentally different fix) -- and does not involve this
    * method's {@code synchronized(table)} at all: by the time that cycle's second thread starts,
    * this method has already returned for the table in question. The shape above is different:
    * it needs no worker, no {@code Distinct}, and no completed first build -- it is the exact
    * {@code synchronized(table)}-vs-engine-lock cycle this method exists to close, just entered
    * through a condition list instead of {@code table}'s own columns, and round 2 of this fix
    * closes it via {@link #needsEmbeddedTableScriptLock}.
    *
    * <p>Uses the creating {@link #getScriptEnv()}, not {@link #peekScriptEnv()}, and calls
    * {@code init()} on it before reading its lock: the reported scenario is two concurrent,
    * otherwise-ordinary requests, neither of them a script, racing to build {@code A} and
    * {@code X} for the first time in a sandbox that has run no script yet -- so no env, and no
    * initialized GraalJS engine, exists for either side yet when the race starts.
    * {@link ScriptEnv#getExecutionLock()} answers {@code null} until the engine backing it has
    * actually been built ({@code init()}, not merely constructing the env object) -- so reading
    * {@link #peekScriptEnv()}, or even the creating {@link #getScriptEnv()} without forcing
    * {@code init()}, would let both threads see no lock yet, take no lock here, and still
    * deadlock the moment each one's own {@code FormulaTableLens} lazily initializes and locks
    * the (shared, per-sandbox) env deeper in the call -- exactly as if this method did nothing,
    * which is what an even earlier version of this fix did, and a live run of
    * {@code EmbeddedTableScriptLockCycleTest} caught it.
    */
   private TableLens getEmbeddedTableLens(TableAssembly table, String name, int mode,
                                          boolean pub, VariableTable vars)
      throws Exception
   {
      Lock execLock = null;
      boolean ownColumn = false;

      if(!isScriptPoolMode()) {
         ownColumn = hasOwnScriptColumn(table);

         if(ownColumn || embeddedBuildMayRunScript(table, vars)) {
            ScriptEnv senv = getScriptEnv();
            senv.init();
            execLock = senv.getExecutionLock();
         }
      }

      if(execLock == null) {
         synchronized(table) {
            return getOrBuildEmbeddedTableLens(table, name, mode, pub, vars);
         }
      }

      execLock.lock();

      // Recorded (so a lens further down that would otherwise hand its own row
      // computation to a background worker and wait for it can lend this lock to that
      // worker instead, bug #76938) only when table's own column is the reason: that is
      // round 1's already-shipped, already-validated behavior, covering any worker A's
      // own pipeline directly waits for. When any other reason applies (a sub-query's
      // sub table or a composed base that reaches script, a condition expression value,
      // a variable expression -- all run inline on this thread, bug #77873), this
      // thread never itself waits on a worker before
      // PostProcessor$ConditionFilter2 exists (see getEmbeddedTableLens's javadoc) --
      // recording the lock here regardless would make a lens like DistinctTableLens see
      // JavaScriptEngine.holdsScriptLock() == true the first time it validates the
      // sub-query's own sub table (inside AssetConditionGroup's constructor, before any
      // filter exists), and it would compute inline rather than spawn its background
      // worker even though nothing here is yet waiting for one -- silently changing an
      // established, separately-tested concurrency behavior for a lens this fix has no
      // reason to touch (caught live: recording it here made
      // SubQueryConditionWorksheetCycleTest#formulaReadsFilteredTableByName's own
      // WorkerGate never see its worker at all). Any wait *this* thread later does for a
      // worker spawned that way is already covered once ConditionFilter2 exists, by its
      // own, narrower, already-shipped push (bug #77158) -- unaffected by this one.
      if(ownColumn) {
         JavaScriptEngine.pushHeldScriptLock(execLock);
      }

      try {
         synchronized(table) {
            return getOrBuildEmbeddedTableLens(table, name, mode, pub, vars);
         }
      }
      finally {
         if(ownColumn) {
            JavaScriptEngine.popHeldScriptLock();
         }

         execLock.unlock();
      }
   }

   /**
    * The cache-check + build formerly inlined in {@code getTableLens()}'s
    * {@code EmbeddedTableAssembly} branch; the caller holds {@code synchronized(table)}.
    */
   private TableLens getOrBuildEmbeddedTableLens(TableAssembly table, String name, int mode,
                                                 boolean pub, VariableTable vars)
      throws Exception
   {
      ColumnSelection columns = table.getColumnSelection(pub);
      TableLens data = getTableLens0(name, mode, table.isAggregate(), columns.hashCode());
      int ncol = columns.getAttributeCount();

      // the columns of a crosstab don't match its data, the column header columns
      // missing from a row-limited run are kept (77538)
      if(data != null &&
         (data.getColCount() == ncol || pub && table.getAggregateInfo().isCrosstab()))
      {
         return data;
      }

      return executeQuery(table, table, name, mode, pub, vars);
   }

   /**
    * @return {@code true} if building {@code table} under its own monitor might run anything
    * through the script engine besides {@code table}'s own expression columns (which
    * {@link #getEmbeddedTableLens} checks separately, see there): any table the build reaches
    * might ({@link #needsEmbeddedTableScriptLock}), or the variables the build runs with hold
    * an {@link ExpressionValue} ({@link #hasExpressionVariable}).
    *
    * <p>The variables are checked apart from the table walk because they are not part of any
    * table's definition: {@code AssetDataCache.getVariableTable} (from
    * {@code AssetQuery.doGetTableLens}'s cache key) evaluates a JavaScript
    * {@link ExpressionValue} left in them for every runtime/live build, even of a table with no
    * condition at all, and a {@code $(v)} condition value is replaced by the raw variable value
    * before {@code ConditionGroup.getExpressionVal} evaluates it, whatever its type. Both the
    * sandbox's own table and {@code vars} are checked: {@code executeQuery} layers them, and a
    * sub-query's sub table runs with a clone of the sandbox's own table (bug #77873).
    */
   private boolean embeddedBuildMayRunScript(TableAssembly table, VariableTable vars) {
      return hasExpressionVariable(getVariableTable()) || hasExpressionVariable(vars) ||
         needsEmbeddedTableScriptLock(table, Collections.newSetFromMap(new IdentityHashMap<>()));
   }

   /**
    * The rule: anything the build of {@code table} can run under the caller's monitor counts.
    * {@code AssetQuery} builds every table the build reaches inline, on the same thread -- a
    * sub-query's sub table ({@code AssetConditionGroup}) and the bases of a composed table
    * (mirror, join, concatenation, rotate, unpivot), both through
    * {@code AssetQuery.createAssetQuery} directly, never through {@link #getTableLens} -- so
    * none of them gets a lock order of its own, and they are all walked here, recursively, as
    * one. At every table reached, {@code table} itself included, this checks its own expression
    * columns ({@link #hasOwnScriptColumn}) and its pre-/post-conditions, runtime ones included
    * ({@link #conditionsMayRunScript}: expression values of any type, and sub-queries, whose
    * sub tables are walked in turn); then it walks the bases of a
    * {@link ComposedTableAssembly} ({@link #getBaseTables}).
    *
    * <p>Taking the lock this way also means the build must not hand work that may need it to
    * another thread and wait: {@code JoinQuery} and {@code ConcatenatedQuery} run their member
    * queries on the calling thread when it holds the lock
    * ({@link #holdsScriptExecutionLock}).
    *
    * <p>Each earlier version of this check covered one more shape (bug #77301: own columns,
    * then sub-queries), and the build then deadlocked through a shape it skipped (bug #77873:
    * condition expression values, composed bases). A script source added to the build later
    * belongs in this walk, not in a new, separate check.
    *
    * <p>{@code visited} (identity-based) is checked and grown for every table entered, so a
    * pathological cycle (a sub-query or base, however many hops away, that loops back to an
    * ancestor) terminates instead of recursing forever. Ordinary worksheets nest sub-queries
    * and composed tables only a few levels deep, so this is never meaningful extra work in
    * practice, and a false positive only costs one extra, uncontested lock acquisition.
    */
   private static boolean needsEmbeddedTableScriptLock(TableAssembly table,
                                                        Set<TableAssembly> visited)
   {
      if(table == null || !visited.add(table)) {
         return false;
      }

      if(hasOwnScriptColumn(table) ||
         conditionsMayRunScript(table.getPreConditionList(), visited) ||
         conditionsMayRunScript(table.getPostConditionList(), visited) ||
         conditionsMayRunScript(table.getPreRuntimeConditionList(), visited) ||
         conditionsMayRunScript(table.getPostRuntimeConditionList(), visited))
      {
         return true;
      }

      if(table instanceof ComposedTableAssembly) {
         TableAssembly[] bases = getBaseTables((ComposedTableAssembly) table);

         for(int i = 0; bases != null && i < bases.length; i++) {
            if(needsEmbeddedTableScriptLock(bases[i], visited)) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * The current definitions of {@code table}'s bases, read without filling or reading the
    * transient clone cache of {@link ComposedTableAssembly#getTableAssemblies(boolean)
    * getTableAssemblies(true)}: the query builds from a clone of {@code table} whose cache is
    * empty, so it resolves its bases from the worksheet again -- a cache left on the definition
    * could be stale. A mirror's base is read as {@code MirrorQuery} reads it; any other composed
    * table's bases by name from its worksheet, as {@code getTableAssemblies()} does, minus the
    * {@code update()} calls that method makes on them.
    */
   private static TableAssembly[] getBaseTables(ComposedTableAssembly table) {
      if(table instanceof MirrorTableAssembly) {
         TableAssembly base = ((MirrorTableAssembly) table).getTableAssembly();
         return base == null ? new TableAssembly[0] : new TableAssembly[] { base };
      }

      Worksheet ws = table.getWorksheet();
      String[] names = table.getTableNames();

      if(ws == null || names == null) {
         return table.getTableAssemblies(true);
      }

      List<TableAssembly> bases = new ArrayList<>();

      for(String name : names) {
         Assembly base = name == null ? null : ws.getAssembly(name);

         if(base instanceof TableAssembly) {
            bases.add((TableAssembly) base);
         }
      }

      return bases.toArray(new TableAssembly[0]);
   }

   /**
    * @return {@code true} if {@code table} has its own script/JS expression column -- i.e. a
    * {@link ColumnRef#isExpression()} column in {@link TableAssembly#getColumnSelection()}, the
    * same selection {@code AssetQuery.getFormulaTableLens} scans to decide which columns need a
    * {@code FormulaTableLens}. Deliberately conservative in one direction only: every
    * {@link ColumnRef#isExpression()} column counts, even one {@code getFormulaTableLens} would
    * itself skip (a grouped or already-processed expression, an SQL expression on a mergeable
    * source) -- those refinements only ever narrow whether a formula lens actually gets built,
    * never widen it, and an embedded source is never SQL-mergeable in the first place (see this
    * class's own {@code EmbeddedTableAssembly} handling), so replicating them here would only
    * risk a false negative for no real gain.
    */
   private static boolean hasOwnScriptColumn(TableAssembly table) {
      ColumnSelection columns = table.getColumnSelection();

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         DataRef ref = columns.getAttribute(i);

         if(ref instanceof ColumnRef && ((ColumnRef) ref).isExpression()) {
            return true;
         }
      }

      return false;
   }

   /**
    * @return {@code true} if a condition in {@code wrapper} has an {@link ExpressionValue} of
    * any type -- {@code AssetConditionGroup} (pre and detail post conditions),
    * {@code AssetQuery$AssetConditionGroup2} (aggregate post conditions) and
    * {@code PreAssetQuery.execScriptExpression} (an SQL-bound table's mergeable conditions) all
    * run it through the script engine, an SQL-type one included (bug #77873) -- or is a
    * sub-query whose sub table might reach the script engine, recursively (bug #77301), through
    * {@link #needsEmbeddedTableScriptLock}, which grows the same {@code visited} set.
    *
    * <p>This mirrors, rather than calls, {@code PostProcessor.ConditionFilter2
    * .subTableNeedsScriptLock}: that method inspects an already-built runtime
    * {@code ConditionGroup} and, through {@code AssetCondition.getSubTable()}, the sub query's
    * already-materialized {@code TableLens} -- neither of which exists yet at this call site.
    * This check runs *before* {@code table} is built at all, specifically to decide the lock
    * order for that build (by the time a {@code ConditionGroup} exists, {@code executeQuery} is
    * already inside {@code synchronized(table)} -- too late to change the order), so it has to
    * work from {@code table}'s static definition instead. It reads
    * {@link AssetCondition#getSubQueryValue()} directly: its {@code TableAssembly} is the
    * sub-query's definition (set by {@code SubQueryValue.update()} when the condition was
    * authored), available whether or not the condition has ever been evaluated -- unlike
    * {@link AssetCondition#getSubTable()}, which only answers non-{@code null} after
    * {@code initSubTable()} has actually run the sub-query once, deep inside a build already in
    * progress.
    */
   private static boolean conditionsMayRunScript(ConditionListWrapper wrapper,
                                                 Set<TableAssembly> visited)
   {
      ConditionList list = wrapper == null ? null : wrapper.getConditionList();

      if(list == null) {
         return false;
      }

      for(int i = 0; i < list.getConditionSize(); i++) {
         if(!list.isConditionItem(i)) {
            continue;
         }

         XCondition cond = list.getConditionItem(i).getXCondition();

         if(cond instanceof Condition) {
            Condition vcond = (Condition) cond;

            for(int j = 0; j < vcond.getValueCount(); j++) {
               if(vcond.getValue(j) instanceof ExpressionValue) {
                  return true;
               }
            }
         }

         if(!(cond instanceof AssetCondition)) {
            continue;
         }

         SubQueryValue sval = ((AssetCondition) cond).getSubQueryValue();
         TableAssembly stable = sval == null ? null : sval.getTable();

         if(stable != null && needsEmbeddedTableScriptLock(stable, visited)) {
            return true;
         }
      }

      return false;
   }

   /**
    * @return {@code true} if {@code vtable} (its base tables included) holds an
    * {@link ExpressionValue} of any type. See {@link #embeddedBuildMayRunScript}.
    *
    * <p>{@link VariableTable#get(String)} is the only read of a value it offers, and it can
    * write back: a {@code UserVariable} entry is replaced by its value node's value. That is the
    * same {@code get} the build itself makes on these tables ({@code AssetDataCache
    * .getVariableTable}, condition {@code replaceVariables}), so it changes nothing the build
    * would not, and it never runs a script.
    */
   private static boolean hasExpressionVariable(VariableTable vtable) {
      if(vtable == null) {
         return false;
      }

      Enumeration<String> keys = vtable.keys();

      while(keys.hasMoreElements()) {
         try {
            if(vtable.get(keys.nextElement()) instanceof ExpressionValue) {
               return true;
            }
         }
         catch(Exception ex) {
            // can't tell, so take the lock: a false positive only costs one lock acquisition
            return true;
         }
      }

      return false;
   }

   /**
    * Execute a table query: setup, run, write-back column selection, and cache the result.
    * For EmbeddedTableAssembly, table == table2 and the caller holds synchronized(table).
    * For cloned tables, table2 is a per-thread clone safe to use without locking.
    */
   private TableLens executeQuery(TableAssembly table, TableAssembly table2,
                                  String name, int mode, boolean pub,
                                  VariableTable vars)
      throws Exception
   {
      AssetQueryCacheNormalizer cacheNormalizer = new AssetQueryCacheNormalizer(table2, this, mode);
      long touchTime = vsbox != null ? vsbox.getTouchTimestamp() : -1L;
      AssetQuery query = AssetQuery.createAssetQuery(
         table2, mode, this, false, touchTime, true, false);

      TableLens base;

      try {
         VariableTable vars2;
         // set assemblyName for WS userMessages
         Tool.setUserMessageAssemblyName(name);

         // @by larryl, if variable table is passed in, use the variable table
         // so any changes make in the variable table is persistent. This cause
         // be used if the script in worksheet or VPM changes the parameters
         // and the changes should be carried to query generation. The regular
         // query generation and model binding all pass through the variables
         // to VPM and thus keep the changes from the VPM scripts.
         if(vars != null) {
            vars2 = vars;
            vars.removeBaseTable(CachedVariableTable.class);
            vars.addBaseTable(this.vars);
         }
         else if(this.vars != null) {
            vars2 = this.vars.clone();
         }
         else {
            vars2 = new VariableTable();
            addMessageAttributes(vars2);
         }

         XUtil.copyDBCredentials((XPrincipal) getUser(), vars2);
         Object omaxrows = vars2.get(XQuery.HINT_MAX_ROWS);
         vars2.put(XQuery.HINT_PREVIEW, isLiveMode(mode) + "");
         base = cacheNormalizer.transformTableLens(query.getTableLens(vars2));

         if(table.isLiveData()) {
            base = getColumnLimitTableLens(base, table instanceof UnpivotTableAssembly);
         }

         if(base != null) {
            base = new TextSizeLimitTableLens(base, Util.getOrganizationMaxCellSize());
         }

         if(vars != null) {
            vars.removeBaseTable(this.vars);
         }

         // validate column selection — brief re-lock for the write-back (for cloned tables;
         // for EmbeddedTableAssembly the caller already holds synchronized(table))
         synchronized(table) {
            table.setColumnSelection(table2.getColumnSelection());
            resetTableLens(table.getName(), DESIGN_MODE);

            // ignore design mode when sync outer column selection
            if(isRuntimeMode(mode) || (isLiveMode(mode) && (table.isAggregate() ||
               table.getAggregateInfo().isEmpty() || table.isPlain())))
            {
               table.setColumnSelection(table2.getColumnSelection(true), true);
            }
         }

         // restore in case changed by query
         vars2.put(XQuery.HINT_MAX_ROWS, omaxrows);
      }
      catch(ConfirmException | CancelledException ex) {
         throw ex;
      }
      catch(SQLException ex) {
         throw new ExpressionFailedException(-1, null, table.getName(), ex);
      }
      finally {
         Tool.clearUserMessageAssemblyName();
      }

      // cache the table for reuse
      int chash;

      synchronized(table) {
         chash = table.getColumnSelection(pub).hashCode();
      }

      putTableLens0(name, mode, table.isAggregate(), base, chash);

      return base;
   }

   private TableLens getColumnLimitTableLens(TableLens table, boolean unpivot) {
      if(table.getColCount() <= Util.getOrganizationMaxColumn()) {
         return table;
      }

      int max = Util.getOrganizationMaxColumn();
      int[] map = new int[max];
      int len = max;

      for(int i = 0; i < len; i++) {
         map[i] = i;
      }

      Tool.addUserMessage(Util.getColumnLimitMessage());

      return new ColumnMapFilter(table, map);
   }

   /**
    * Reset all the data.
    */
   public void resetTableLens() {
      ArrayList<TableLens> disposeList = new ArrayList<>();

      synchronized(this) {
         if(tmap != null) {
            for(TableLens data : tmap.values()) {
               if(data != null) {
                  disposeList.add(data);
               }
            }

            tmap.clear();
         }
      }

      // @by stephenwebster, For Bug #8579
      // Intentionally left outside synchronization block to avoid deadlock.
      for(TableLens lens : disposeList) {
         lens.dispose();
      }
   }

   /**
    * Cancel all the tables for this worksheet.
    */
   public void cancelTableLens() {
      ArrayList<CancellableTableLens> cancelList = new ArrayList<>();

      synchronized(this) {
         if(tmap != null) {
            for(TableLens obj : tmap.values()) {
               if(obj instanceof CancellableTableLens) {
                  CancellableTableLens data = (CancellableTableLens) obj;
                  cancelList.add(data);
               }
            }
         }
      }

      for(CancellableTableLens lens : cancelList) {
         lens.cancel();
      }
   }

   /**
    * Reset the data of a table.
    * @param table the specified table.
    */
   public void resetTableLens(String table) {
      Iterator<TableEntry> keys = tmap.keySet().iterator();
      List<TableEntry> removed = new ArrayList<>();

      while(keys.hasNext()) {
         TableEntry entry = keys.next();

         if(entry.matches(table)) {
            removed.add(entry);
         }
      }

      for(int i = 0; i < removed.size(); i++) {
         TableLens data = tmap.remove(removed.get(i));

         if(data != null) {
            data.dispose();
         }
      }
   }

   /**
    * Reset the data of a table in one mode.
    * @param table the specified table.
    * @param mode, the speicifed mode which should one of the predefined modes
    * namely <code>DESIGN_MODE</code>, <code>LIVE_MODE</code> and
    * <code>RUNTIME_MODE</code>.
    */
   public void resetTableLens(String table, int mode) {
      resetTableLens0(table, mode, true);
      resetTableLens0(table, mode, false);
   }

   /**
    * Clear out the table lens.
    */
   private void resetTableLens0(String table, int mode, boolean aggregate) {
      TableEntry entry = new TableEntry(table, mode, aggregate);

      if(tmap != null) {
         TableLens data = tmap.remove(entry);

         if(data != null) {
            data.dispose();
         }
      }
   }

   /**
    * Get the script execution environment.
    * @return the script execution environment.
    */
   public ScriptEnv getScriptEnv() {
      ScriptEnv senv = this.senv;

      if(senv == null) {
         synchronized(lock) {
            senv = this.senv;

            if(senv == null) {
               senv = this.senv = createScriptEnv();
            }
         }
      }

      return senv;
   }

   /**
    * Every env of this sandbox has the same type: the pool mode is fixed when the sandbox is
    * built, so a condition filter that captured one env never meets another kind (bug #76960).
    */
   private ScriptEnv createScriptEnv() {
      return scriptPoolMode ? new WorksheetScriptEnv(PoolConfig.read())
         : ScriptEnvRepository.getScriptEnv();
   }

   /**
    * @return whether this sandbox runs its worksheet scripts on pooled contexts.
    */
   public boolean isScriptPoolMode() {
      return scriptPoolMode;
   }

   /**
    * Release a dropped env's pooled contexts. A no-op for a plain env.
    */
   private static void retire(ScriptEnv env) {
      if(env instanceof WorksheetScriptEnv) {
         ((WorksheetScriptEnv) env).retire();
      }
   }

   /**
    * Peek at this sandbox's script environment without creating one if it does
    * not already exist. Used by condition filtering (bugs #76918, #76961) to
    * capture the env the lenses below the filter were built with, whose execution
    * lock it acquires *before* taking any of its own locks, so the two never get
    * acquired in opposite orders on different threads -- without forcing an
    * unused env (and its GraalJS Context) to be created for queries that never
    * touch scripts.
    *
    * @return the script environment, or {@code null} if none exists yet for this
    *         sandbox.
    */
   public ScriptEnv peekScriptEnv() {
      return senv;
   }

   /**
    * @return {@code true} if the calling thread holds this sandbox's script-execution lock,
    * for whatever reason -- inside {@code exec()}, or taken outside script evaluation, e.g. by
    * {@link #getEmbeddedTableLens} before an embedded table's monitor (bug #77873), recorded with
    * {@code JavaScriptEngine.pushHeldScriptLock} or not. A thread holding it must not hand work
    * that may need it to another thread and wait for that work (the fan-out in
    * {@code JoinQuery} and {@code ConcatenatedQuery}). Never creates an env or engine.
    */
   public boolean holdsScriptExecutionLock() {
      ScriptEnv env = senv;
      Lock lock = env == null ? null : env.getExecutionLock();

      return lock instanceof LendableReentrantLock &&
         ((LendableReentrantLock) lock).isHeldByCurrentThread();
   }

   /**
    * Get the scope for executing formulas. The scope should contain all
    * data tables.
    * @return the scope for executing formulas.
    */
   public AssetQueryScope getScope() {
      AssetQueryScope scope = this.scope;

      if(scope == null) {
         synchronized(lock) {
            scope = this.scope;

            if(scope == null) {
               ScriptEnv senv = getScriptEnv();
               scope = this.scope = createAssetQueryScope();

               senv.put("worksheet", scope);
            }
         }
      }

      return scope;
   }

   public AssetQueryScope createAssetQueryScope() {
      AssetQueryScope scope = new AssetQueryScope(AssetQuerySandbox.this);

      // chain viewsheet scope with worksheet scope
      if(vsbox != null) {
         ViewsheetScope vscope = new ViewsheetScope(vsbox, false);
         JavaScriptEngine.addToPrototype(scope, vscope);
      }

      return scope;
   }

   /**
    * Get the data of a table in one mode.
    * @param table the specified table.
    * @param mode the specified mode which should one of the predefined modes
    * namely <code>DESIGN_MODE</code>, <code>LIVE_MODE</code> and
    * <code>RUNTIME_MODE</code>.
    * @return the data of the table in the mode.
    */
   private synchronized TableLens getTableLens0(String table, int mode, boolean aggregate, int chash) {
      if(tmap == null) {
         return null;
      }

      TableEntry entry = new TableEntry(table, mode, aggregate, chash);
      TableLens obj = tmap.get(entry);

      // a table whose formula a script timeout stopped is computed again (bug #77949); only
      // that table is removed, not one put meanwhile
      if(obj != null && AssetDataCache.isStopped(obj)) {
         tmap.remove(entry, obj);
         obj = null;
      }

      // remove cached data
      if(obj == null && chash != 0) {
         tmap.remove(new TableEntry(table, mode, aggregate, 0));
      }

      return obj;
   }

   /**
    * Put the data of a table in one mode.
    * @param table the specified table.
    * @param mode the specified mode which should one of the predefined modes
    * namely <code>DESIGN_MODE</code>, <code>LIVE_MODE</code> and
    * <code>RUNTIME_MODE</code>.
    */
   private synchronized void putTableLens0(String table, int mode, boolean aggregate,
                                           TableLens data, int chash)
   {
      if(table.startsWith(Assembly.TABLE_VS) || tmap == null || data == null) {
         return;
      }

      if(isLiveMode(mode) && isDesignTable(data)) {
         // Bug #41691, don't cache a live mode table that is actually the design table, this
         // happens when XSessionManager returns null when the table is executed and typically
         // indicates that the query was cancelled.
         return;
      }

      TableEntry entry = new TableEntry(table, mode, aggregate, chash);
      tmap.put(entry, data);
   }

   /**
    * Determines if a table is the result of {@link AssetQuery#getDesignTableLens(VariableTable)}.
    *
    * @param table the table to test.
    *
    * @return {@code true} if a design table or {@code false} if not.
    */
   private boolean isDesignTable(TableLens table) {
      if((table instanceof XNodeMetaTable) ||
         Boolean.TRUE.equals(table.getProperty(AssetQuery.DESIGN_TABLE)))
      {
         return true;
      }

      if(table instanceof TableFilter) {
         return isDesignTable(((TableFilter) table).getTable());
      }

      return false;
   }

   /**
    * Get the default column selection of a table.
    * @param name the specified table name.
    * @return the default column selection of the table.
    */
   protected ColumnSelection getDefaultColumnSelection(String name) {
      final Map<String, ColumnSelection> cmap = this.cmap;

      if(cmap == null) {
         return new ColumnSelection();
      }

      return cmap.get(name);
   }

   /**
    * Set the default column selection of a table.
    * @param name the specified table name.
    * @param columns the default column selection of the table.
    */
   protected void setDefaultColumnSelection(String name, ColumnSelection columns) {
      Map<String, ColumnSelection> cmap = this.cmap;

      if(cmap == null) {
         return;
      }

      if(ws.containsAssembly(name) && !name.startsWith(Assembly.TABLE_VS) && !isDirectSource()) {
         cmap.put(name, columns);
      }
   }

   /**
    * Get the default column selection of a source info.
    * @param info the specified source info.
    * @param ocolumns the specified old column selection.
    * @return the default column selection of the source info.
    */
   protected ColumnSelection getDefaultColumnSelection(XSourceInfo info,
                                                       ColumnSelection ocolumns)
   {
      if(info == null || info.isEmpty()) {
         return new ColumnSelection();
      }

      if(info.getType() == XSourceInfo.MODEL) {
         return getModelDefaultColumnSelection(info, ocolumns);
      }
      else if(info.getType() == XSourceInfo.PHYSICAL_TABLE) {
         return getPhysicalDefaultColumnSelection(info, ocolumns);
      }

      return new NullColumnSelection();
   }

   /**
    * Get the session object.
    * @return the session object.
    */
   private Object getSession() throws Exception {
      XRepository rep = XRepository.getRepository();
      return rep.bind(System.getProperty("user.name"));
   }

   /**
    * Get physical table default column selection.
    * @param info the specified source info.
    * @param ocols the specified old columns.
    * @return the default column selection.
    */
   private ColumnSelection getPhysicalDefaultColumnSelection(XSourceInfo info,
      ColumnSelection ocols)
   {
      String table = info.getSource();
      String source = info.getPrefix();

      try {
         XRepository rep = XRepository.getRepository();
         XDataSource xds0 = rep.getDataSource(source);

         if(!(xds0 instanceof JDBCDataSource)) {
            return new NullColumnSelection();
         }

         final Principal principal = getUser();

         if(principal instanceof XPrincipal) {
            String dbuser = ((XPrincipal) principal).getProperty(
               XUtil.DB_USER_PREFIX + xds0.getFullName());
            String dbpassword = ((XPrincipal) principal).getProperty(
               XUtil.DB_PASSWORD_PREFIX + xds0.getFullName());

            if(dbuser != null && dbpassword != null) {
               xds0 = (XDataSource) xds0.clone();
               ((JDBCDataSource) xds0).setUser(dbuser);
               ((JDBCDataSource) xds0).setPassword(dbpassword);
            }
         }

         XDataSource xds = ConnectionProcessor.getInstance().getDatasource(principal, xds0);
         SQLTypes stypes = SQLTypes.getSQLTypes((JDBCDataSource) xds);
         Object session = getSession();
         XNode meta = new XNode();
         meta.setAttribute("type", "DBPROPERTIES");
         meta = rep.getMetaData(session, xds, meta, false, null);
         ColumnSelection columns = new ColumnSelection();

         XNode tableNode = stypes.getQualifiedTableNode(
            table, "true".equals(meta.getAttribute("hasCatalog")),
            "true".equals(meta.getAttribute("hasSchema")),
            (String) meta.getAttribute("catalogSep"),
            (JDBCDataSource) xds,
            info.getProperty(SourceInfo.CATALOG),
            info.getProperty(SourceInfo.SCHEMA));
         String type = info.getProperty(SourceInfo.TABLE_TYPE);

         if(type != null) {
            tableNode.setAttribute("type", type);
         }

         boolean catalog = "true".equals(meta.getAttribute("supportCatalog"));
         tableNode.setAttribute("supportCatalog", catalog + "");
         XNode node = rep.getMetaData(session, xds, tableNode, true, null);

         if(node == null) {
            return new NullColumnSelection();
         }

         node = node.getChild("Result");

         if(node == null) {
            return new NullColumnSelection();
         }

         String[] carr = new String[node.getChildCount()];

         for(int i = 0; i < node.getChildCount(); i++) {
            XTypeNode tnode = (XTypeNode) node.getChild(i);
            String name = tnode.getName();
            AttributeRef attributeRef = new AttributeRef(null, name);
            ColumnRef ref = new ColumnRef(attributeRef);
            ref.setDataType(tnode.getType());

            if(StringUtils.isNumeric(tnode.getSqlType())) {
               ref.setSqlType(Integer.parseInt(tnode.getSqlType()));
            }

            if(ocols == null || ocols.containsAttribute(ref)) {
               columns.addAttribute(ref);
            }

            carr[i] = table + "." + name;
         }

         // apply vpm for physical table
         if(isVPMEnabled()) {
            // vpm is associated with original data source (xds0) instead of
            // additional connections (xds)
            BiFunction<String, String, Boolean> hcolumns = VpmProcessor.getInstance()
               .getHiddenColumnsSelector(
                  new String[] { table}, carr, xds0.getFullName(), null, null, principal);
            int ccnt = columns.getAttributeCount();

            for(int i = ccnt - 1; i >= 0; i--) {
               ColumnRef col = (ColumnRef) columns.getAttribute(i);

               if(hcolumns.apply(table, col.getAttribute())) {
                  columns.removeAttribute(i);
               }
            }
         }

         return columns;
      }
      catch(Exception ex) {
         LOG.error("Failed to get default physical column selection: " +
            source + ", " + table, ex);
      }

      return new NullColumnSelection();
   }

   /**
    * Get model default column selection.
    * @param info the specified source info.
    * @param ocolumns the specified old column selection.
    * @return the default column selection.
    */
   private ColumnSelection getModelDefaultColumnSelection(XSourceInfo info,
      ColumnSelection ocolumns)
   {
      ColumnSelection columns = new ColumnSelection();

      try {
         XRepository repository = XRepository.getRepository();
         XDataModel model = repository.getDataModel(info.getPrefix());
         final Principal principal = getUser();
         XLogicalModel lmodel = model.getLogicalModel(info.getSource(), principal);

         if(lmodel == null) {
            throw new RuntimeException("Logical model doesn't exist: " +
               info.getSource() + " in " + info.getPrefix());
         }

         XAttribute[] attrs = XUtil.getAttributes(
            info.getPrefix(), info.getSource(), null, principal, false,
            isVPMEnabled(), vars);

         for(int i = 0; i < attrs.length; i++) {
            XAttribute attribute = attrs[i];
            AttributeRef attr =
               new AttributeRef(attribute.getEntity(), attribute.getName());
            ColumnRef column = new ColumnRef(attr);

            if(ocolumns == null || ocolumns.containsAttribute(attr)) {
               column.setDataType(attribute.getDataType());
               columns.addAttribute(column);
            }
         }

         AssetUtil.fixAlias(columns);

         return columns;
      }
      catch(Exception ex) {
         LOG.error("Failed to get default model column selection: " + info.getPrefix(),
            ex);
      }

      return new ColumnSelection();
   }

   /**
    * Dispose the asset query sandbox.
    */
   public void dispose() {
      // @by stephenwebster, For Bug #8579
      // Intentionally left outside synchronization block to avoid deadlock.
      resetTableLens();

      synchronized(this) {
         if(ws != null) {
            ws.dispose();
         }

         // @by larryl, bug1421859014604, a cached FormulaTableLens may hold a
         // reference to the AssetQuerySandbox, and will use the worksheet
         // (AssetQueryScope.get). We don't clear out the worksheet to avoid
         // the formula table becoming unusable.
         // ws = null;

         baseUser = null;
         vpmUser = null;
         vars = null;
         ovars = null;

         tmap = null;

         if(cmap != null) {
            cmap.clear();
            cmap = null;
         }

         scope = null;
         vprovider2 = null;
      }

      ScriptEnv old;

      synchronized(lock) {
         old = senv;
         senv = null;
      }

      retire(old);
   }

   /**
    * Check if this AssetQuerySandbox has been disposed.
    */
   public boolean isDisposed() {
      return tmap == null;
   }

   /**
    * Remove cached data that is no longer needed by the worksheet.
    */
   public void shrink() {
      Map<TableEntry, TableLens> tmap = this.tmap;

      if(tmap != null) {
         // copy to array first to avoid racing condition
         Arrays.stream(tmap.keySet().toArray(new TableEntry[0]))
            .filter(entry -> !ws.containsAssembly(entry.table))
            .forEach(entry -> nolimit.remove(entry.table));
      }

      Map<String, ColumnSelection> cmap = this.cmap;

      if(cmap != null) {
         Arrays.stream(cmap.keySet().toArray(new String[0]))
            .filter(name -> !ws.containsAssembly(name))
            .forEach(cmap::remove);
      }
   }

   /**
    * Check if the viewsheet which contains this worksheet is a direct source.
    * True shows that this table is from a virtual worksheet which is created
    * in the viewsheet for supporting query/physical table/logical model
    * data source.
    */
   private boolean isDirectSource() {
      if(ws == null) {
         return false;
      }

      Assembly[] assemblies = ws.getAssemblies();

      for(int i = 0; i < assemblies.length; i++) {
         Assembly assembly = assemblies[i];

         if(!(assembly instanceof BoundTableAssembly)) {
            continue;
         }

         SourceInfo info = ((BoundTableAssembly) assembly).getSourceInfo();

         if(info == null) {
            return false;
         }

         return "true".equals(info.getProperty("direct"));
      }

      return false;
   }

   /**
    * Get the query manager for this sheet.
    */
   public QueryManager getQueryManager() {
      return queryMgr;
   }

   /**
    * Set the query manager for tracking queries originated from this sandbox.
    */
   public void setQueryManager(QueryManager mgr) {
      this.queryMgr = mgr;
   }

   /**
    * Set whether to ignore filtering. If true, the filtering conditions on
    * runtime queries are ignored.
    */
   public void setIgnoreFiltering(boolean ifiltering) {
      if(this.ifiltering != ifiltering) {
         this.ifiltering = ifiltering;

         // discard table lens to regenerate them
         resetTableLens();
      }
   }

   /**
    * Check if is ignoring filtering.
    */
   public boolean isIgnoreFiltering() {
      return ifiltering;
   }

   /**
    * Get the name of the worksheet.
    */
   public String getWSName() {
      return wsname;
   }

   /**
    * Set the name of the worksheet.
    */
   public void setWSName(String name) {
      this.wsname = name;
   }

   /**
    * Check if VPM should be applied;
    */
   public boolean isVPMEnabled() {
      return vpmEnabled;
   }

   /**
    * Set if VPM should be applied;
    */
   public void setVPMEnabled(boolean flag) {
      this.vpmEnabled = flag;
   }

   /**
    * Table entry.
    */
   private static final class TableEntry implements Serializable, Cloneable {
      /**
       * Constructor.
       * @param table the specified table.
       * @param mode the specified mode, design/livedata.
       * @param aggregate true if showing aggregated data.
       */
      public TableEntry(String table, int mode, boolean aggregate) {
         this(table, mode, aggregate, 0);
      }

      /**
       * Constructor.
       * @param table the specified table.
       * @param mode the specified mode, design/livedata.
       * @param aggregate true if showing aggregated data.
       */
      public TableEntry(String table, int mode, boolean aggregate, int hash) {
         this.table = table;
         this.mode = mode;
         this.aggregate = aggregate;
         this.chash = hash;
      }

      /**
       * Check if the table entry matches a table.
       * @param table the specified table.
       * @return <tt>true</tt> if matches, <tt>false</tt> otherwise.
       */
      public boolean matches(String table) {
         return this.table.equals(table);
      }

      /**
       * Check if equals another object.
       * @param obj the specified object.
       * @return <tt>true</tt> if equals, <tt>false</tt> otherwise.
       */
      public boolean equals(Object obj) {
         if(!(obj instanceof TableEntry)) {
            return false;
         }

         TableEntry entry2 = (TableEntry) obj;
         return this.table.equals(entry2.table) && this.mode == entry2.mode &&
            this.aggregate == entry2.aggregate &&
            (chash == 0 || entry2.chash == 0 || chash == entry2.chash);
      }

      /**
       * Get the hash code.
       * @return the hash code of the table entry.
       */
      public int hashCode() {
        return table.hashCode() ^ mode;
      }

      /**
       * Get the string representation.
       * @return the string representation.
       */
      public String toString() {
         return "TableEntry[" + table + ", " + mode + ", " + aggregate + "]";
      }

      private final String table;
      private final int mode;
      private final boolean aggregate;
      private final int chash;
   }

   // marker class for clean up
   private static class CachedVariableTable extends VariableTable {
      public CachedVariableTable() {
         super();
      }
   }

   /**
    * Set the corresponding viewsheet sandbox if this is used in a viewsheet.
    */
   public void setViewsheetSandbox(ViewsheetSandbox vsbox) {
      this.vsbox = vsbox;
   }

   public ViewsheetSandbox getViewsheetSandbox() {
      return vsbox;
   }

   //---------------------------------mv logic--------------------------------
   /**
    * Get mv processor.
    */
   public MVProcessor getMVProcessor() {
      return mvprocessor;
   }

   /**
    * Set mv processor.
    */
   public void setMVProcessor(MVProcessor processor) {
      this.mvprocessor = processor;
   }

   /**
    * Cannot hit mv listener.
    */
   public interface MVProcessor {
      boolean needCheck();
      void notHitMV(String target);
   }

   /**
    * Check if this execution is for creating mv.
    */
   public boolean isCreatingMV() {
      return creatingMV;
   }

   /**
    * Set if this execution is for creating mv.
    */
   public void setCreatingMV(boolean mv) {
      this.creatingMV = mv;
   }

   /**
    * Set if this execution is for analyzing mv.
    */
   public void setAnalyzingMV(boolean analyzingMV) {
      this.analyzingMV = analyzingMV;
   }

   /*
    * @shield,for bug1408607426147,
    * add localize for header prefix, if header's prefix is like "Change from"
    */
   private String prefixLocalize(String header) {
      if(header.contains("Change from first") ||
         header.contains("Change from previous") ||
         header.contains("Change from next") ||
         header.contains("Change from last"))
      {
         int idx = header.indexOf(":");
         String name0 = header.substring(0, idx);
         String name1 = header.substring(idx + 1, header.length());
         String[] s = name0.split(" ");
         StringBuilder dataname = new StringBuilder();
         header = "";
         int cidx = 0;
         int chl;

         for(chl = 0; chl < s.length; chl++) {
            if(s[chl].equals("Change")) {
               cidx = chl;
               break;
            }

            header = s[chl] + " ";
         }

         if(chl < s.length) {
            header = header + Catalog.getCatalog().getString(s[cidx] +
               " " + s[cidx + 1] + " " + s[cidx + 2]);

            for(int a = cidx + 3; a < s.length; a++) {
               if(dataname.length() > 0) {
                  dataname.append(" ");
               }

               dataname.append(s[a]);
            }

            header = header + " " + Tool.localize(dataname.toString());
         }

         header = header + ":" + " " + Tool.localize(name1.trim());
      }

      return header;
   }

   public void restoreVariables() {
      this.vars = ovars != null ? ovars : new VariableTable();

      if(this.scope != null) {
         this.scope.setVariableTable(this.vars);
      }
   }

   private void setVariables(VariableTable vars) {
      this.ovars = this.vars;
      this.vars = vars != null ? vars : new VariableTable();
      addMessageAttributes(this.vars);
   }

   public AssetEntry getWSEntry() {
      return wsEntry;
   }

   public void setWSEntry(AssetEntry wsEntry) {
      this.wsEntry = wsEntry;
   }

   static void addMessageAttributes(VariableTable vars) {
      MessageAttributes attrs = MessageContextHolder.getMessageAttributes();

      if(attrs != null) {
         StompHeaderAccessor accessor = attrs.getHeaderAccessor();
         Map<String, Object> session = accessor.getSessionAttributes();

         if(session != null) {
            String prop = (String) session.get("viewsheetLinkHost");

            if(prop != null) {
               vars.put("__LINK_HOST__", prop);
            }

            prop = (String) session.get("viewsheetLinkUri");

            if(prop != null) {
               vars.put("__LINK_URI__", prop);
            }
         }
      }
   }

   private Worksheet ws; // box worksheet
   private int maxRows = 10000;
   private Principal baseUser; // base box user
   private XPrincipal vpmUser; // vpm box user
   private boolean active; // active flag
   private boolean falias; // fixing alias flag
   private boolean ifiltering; // ignoring filtering flag
   private VariableTable vars = new VariableTable(); // box variable table
   private transient VariableTable ovars; // old box variable table
   private Map<TableEntry, TableLens> tmap; // data map, TableEntry -> TableLens
   // default column selection map, name -> ColumnSelection
   private Map<String, ColumnSelection> cmap;
   private Hashtable<String, SelectionVSAssembly> selections; // selection assembly map,
   private ViewsheetSandbox vsbox;
   private AssetQueryScope scope; // scope for executing formulas
   private volatile ScriptEnv senv; // scripting env for executing scripts
   // read once: every env this sandbox creates, also after reset(), has the same type (#76960)
   private final boolean scriptPoolMode = PoolConfig.isEnabled();
   private final MVSession mvsession;
   private final Set<String> nolimit; // tables to ignore time limit
   private QueryManager queryMgr; // track pending queries
   private final Object lock = new Object(); // script lock
   private String wsname = null; // the name of the worksheet
   private AssetEntry wsEntry = null;
   private VariableProvider vprovider2; // additional variable provider
   private MVProcessor mvprocessor;
   private boolean vpmEnabled = true;
   private boolean creatingMV = false; // called during MV creation
   private boolean analyzingMV = false; // called during MV analysis

   private static final Logger LOG = LoggerFactory.getLogger(AssetQuerySandbox.class);
}
