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
package inetsoft.uql.viewsheet;

import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.report.StyleConstants;
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.Util;
import inetsoft.report.io.viewsheet.CoordinateHelper;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XValueNode;
import inetsoft.uql.viewsheet.graph.aesthetic.SharedFrameParameters;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.uql.viewsheet.vslayout.*;
import inetsoft.util.*;
import inetsoft.util.css.CSSAttr;
import inetsoft.util.css.CSSConstants;
import inetsoft.util.log.LogLevel;
import inetsoft.util.log.logback.LogbackTraceAppender;
import inetsoft.util.xml.VersionControlComparators;
import inetsoft.web.vswizard.recommender.WizardRecommenderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.sql.Timestamp;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static inetsoft.util.Tool.byteEncode;

/**
 * Viewsheet is a visualization of a worksheet. It contains view assemblies
 * and provides additional properties on selection and binding definitions.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class Viewsheet extends AbstractSheet implements VSAssembly, VariableProvider {
   /**
    * A zindex gap for container.
    */
   public static final int CONTAINER_ZINDEX_GAP = 50;
   /**
    * A zindex gap for viewsheet.
    */
   public static final int VIEWSHEET_ZINDEX_GAP = 1000;
   /**
    * Normal zindex gap.
    */
   public static final int NORMAL_ZINDEX_GAP = 1;

   public static final String OUTER_TABLE_SUFFIX = "_O";

   public static final String VS_MIRROR_TABLE = "vs_mirror_table";

   /**
    * Merge a variable array to list.
    * @param list the specified list.
    * @param vars the specified variable array.
    */
   public static void mergeVariables(List<UserVariable> list, UserVariable[] vars) {
      for(UserVariable var : vars) {
         if(!AssetUtil.containsVariable(list, var)) {
            list.add(var);
         }
      }
   }

   /**
    * Get the parameters defined by variable assembly in the worksheet.
    */
   public static VariableTable getVariableTable(Worksheet ws) {
      VariableTable vtable = new VariableTable();

      if(ws == null) {
         return vtable;
      }

      for(Assembly wsAssembly : ws.getAssemblies()) {
         if(!(wsAssembly instanceof VariableAssembly)) {
            continue;
         }

         VariableAssembly assembly = (VariableAssembly) wsAssembly;
         AssetVariable var = assembly.getVariable();

         if(!var.isPromptWithDValue()) {
            XValueNode vnode = var.getValueNode();
            Object value = vnode == null ? null : vnode.getValue();

            if(value != null) {
               vtable.put(var.getName(), value);
            }
         }
      }

      return vtable;
   }

   /**
    * Constructor.
    */
   public Viewsheet() {
      super();

      info = new ViewsheetVSAssemblyInfo();
      vinfo = new ViewsheetInfo();
      layoutInfo = new LayoutInfo();
      this.assemblies = new Vector<>();
      this.dependencies = new HashSet<>();
      this.imgmap = new HashMap<>();
      this.calcmap = new ConcurrentHashMap<>();
      this.aggregatemap = new ConcurrentHashMap<>();
      this.wnames = new HashSet<>();
      this.bmap = new HashMap<>();
      this.childrenIds = new ArrayList<>();
      this.addActionListener(listener);
      dimensionColors = new HashMap<>();
      sharedFrames = new HashMap<>();
   }

   /**
    * Constructor.
    * @param wentry the specified base worksheet entry.
    */
   public Viewsheet(AssetEntry wentry) {
      this();
      this.wentry = wentry;
   }

   /**
    * Get the base worksheet entry.
    * @return the base worksheet entry.
    */
   public AssetEntry getBaseEntry() {
      return wentry;
   }

   /**
    * Change the base worksheet. The caller is responsible to call update on
    * the viewsheet so the bindings are kept in sync.
    */
   public void setBaseEntry(AssetEntry wentry) {
      this.wentry = wentry;
   }

   /**
    * Get the base worksheet.
    * @return the base worksheet.
    */
   public Worksheet getBaseWorksheet() {
      return ws;
   }

   /**
    * Set the base worksheet to this viewsheet.
    * @param ws the specified base worksheet.
    */
   private void setBaseWorksheet(Worksheet ws) {
      if(ws == null) {
         ws = new Worksheet();
      }

      this.ws = ws;
      this.originalWs = (Worksheet) ws.clone();
      wnames = new HashSet<>();

      for(Assembly assembly : ws.getAssemblies()) {
         wnames.add(assembly.getName());
      }

      resetWS();
   }

   /**
    * Get original base ws without added table assemblies for vs
    * @return the base worksheet.
    */
   public Worksheet getOriginalWorksheet() {
      return originalWs;
   }

   /**
    * Get the variable table of this viewsheet.
    * @return the variable table of this viewsheet.
    */
   public VariableTable getVariableTable() {
      return getVariableTable(this.ws);
   }

   /**
    * Check if the viewsheet is valid.
    */
   @Override
   public void checkValidity(boolean checkCrossJoins) throws Exception {
      // do nothing at present
   }

   /**
    * Check if the dependencies are valid.
    */
   @Override
   public synchronized void checkDependencies() throws InvalidDependencyException {
      for(Assembly assembly : getAssemblies()) {
         assembly.checkDependency();
      }
   }

   /**
    * Set the print mode.
    * @param print <tt>true</tt> if is print mode, <tt>false</tt> otherwise.
    */
   public void setPrintMode(boolean print) {
      this.print = print;
   }

   /**
    * Check if is print mode.
    * @return <tt>true</tt> if is print mode, <tt>false</tt> otherwise.
    */
   public boolean isPrintMode() {
      return getViewsheet() != null ? getViewsheet().isPrintMode() : print;
   }

   /**
    * Reset the viewsheet.
    */
   @Override
   public void reset() {
      if(ws != null) {
         ws.reset();
      }
   }

   /**
    * Get the type of the sheet.
    * @return the type of the sheet.
    */
   @Override
   public int getType() {
      return VIEWSHEET_ASSET;
   }

   /**
    * Init the worksheet in the QTL mode.
    */
   private Worksheet getWorksheet(AssetRepository rep,
                                  AssetEntry entry,
                                  Principal user) throws Exception
   {
      String prefix = entry.getProperty("prefix");
      String source = entry.getProperty("source");
      String type = entry.getProperty("type");
      String folderDesc = entry.getProperty("folder_description");
      SourceInfo sinfo = new SourceInfo(Integer.parseInt(type), prefix, source);

      if(folderDesc != null) {
         sinfo.setProperty(SourceInfo.QUERY_FOLDER, folderDesc);
      }

      ws = new Worksheet();
      sinfo.setProperty("direct", "true");

      // fix bug1313184971713, if is direct source,
      // set maximum rows of detail table for design mode
      ws.getWorksheetInfo().setDesignMaxRows(vinfo.getDesignMaxRows());
      TableAssembly assembly = createTableAssembly(rep, entry, user, sinfo);
      ws.addAssembly(assembly);

//      if(assembly instanceof QueryBoundTableAssembly) {
         // why we need this logic? seems unreasonable, fix bug1333250038912
         // createVariableAssembly(assembly);
//      }

      return ws;
   }

   /**
    * Create a table assembly.
    */
   private TableAssembly createTableAssembly(AssetRepository rep,
                                             AssetEntry entry, Principal user,
                                             SourceInfo sinfo)
         throws Exception
   {
      AssetEntry[] entries;
      boolean restQuery = false;
      user = checkVPM(user);

      if(entry.isQuery() || entry.isPhysicalTable()) {
         entries = rep.getEntries(entry, user, ResourceAction.READ);
         String subType = entry.getProperty("subType");
         restQuery = subType != null && subType.startsWith(SourceInfo.REST_PREFIX);
      }
      else if(entry.isLogicModel()) {
         List<AssetEntry> lentries = new ArrayList<>();
         entries = rep.getEntries(entry, user, ResourceAction.READ);

         for(AssetEntry childEntry : entries) {
            lentries.addAll(Arrays.asList(rep.getEntries(childEntry, user, ResourceAction.READ)));
         }

         entries = lentries.toArray(new AssetEntry[] {});
      }
      else {
         throw new RuntimeException(entry.toString() + " is not supported");
      }

      ColumnSelection columns = new ColumnSelection();

      for(AssetEntry col : entries) {
         String entity = col.getProperty("entity");
         String attr = col.getProperty("attribute");
         String alias = null;

         if(attr.startsWith(entity + ":")) {
            alias = attr;
            attr = attr.substring(entity.length() + 1);
         }

         AttributeRef attributeRef = new AttributeRef(entity, attr);
         ColumnRef ref = new ColumnRef(attributeRef);

         if(attr.contains(".") && alias == null && !restQuery) {
            alias = AssetUtil.findAlias(columns, ref);
         }

         ref.setDataType(col.getProperty("dtype"));

         if(alias != null) {
            ref.setAlias(alias);
         }

         if(col.getProperty("refType") != null) {
            attributeRef.setRefType(
               Integer.parseInt(col.getProperty("refType")));
         }

         if(col.getProperty("formula") != null) {
            attributeRef.setDefaultFormula(col.getProperty("formula"));
         }

         if(col.getProperty("sqltype") != null) {
            int sqlType = Integer.parseInt(col.getProperty("sqltype"));
            ref.setSqlType(sqlType);
            attributeRef.setSqlType(sqlType);
         }

         if(col.getProperty("dtype") != null) {
            attributeRef.setDataType(col.getProperty("dtype"));
         }

         columns.addAttribute(ref);
      }

      if(sinfo.getType() == SourceInfo.PHYSICAL_TABLE) {
         sinfo.setProperty(SourceInfo.SCHEMA,
            getBaseEntry().getProperty(SourceInfo.SCHEMA));
         sinfo.setProperty(SourceInfo.CATALOG,
            getBaseEntry().getProperty(SourceInfo.CATALOG));
         sinfo.setProperty(SourceInfo.TABLE_TYPE,
            getBaseEntry().getProperty(SourceInfo.TABLE_TYPE));
      }

      BoundTableAssembly table;
      String tableName = VSUtil.getTableName(entry.getName());

      if(sinfo.getType() == SourceInfo.MODEL) {
         table = new BoundTableAssembly(ws, tableName);
      }
      else if(sinfo.getType() == SourceInfo.PHYSICAL_TABLE) {
         table = new PhysicalBoundTableAssembly(ws, tableName);
      }
      else {
         table = new QueryBoundTableAssembly(ws, tableName);
      }

      table.setSourceInfo(sinfo);
      table.setColumnSelection(columns, false);
      table.setColumnSelection(columns, true);

      return table;
   }

   /**
    * Set the bypassVPM flag in the asset.
    */
   private Principal checkVPM(Principal user) {
      if(vinfo.isBypassVPM() && user instanceof XPrincipal) {
         XPrincipal xuser = (XPrincipal) user;
         xuser = (XPrincipal) xuser.clone();
         xuser.setProperty("bypassVPM", "true");
         return xuser;
      }

      return user;
   }

   /**
    * Reset the worksheet assemblies.
    */
   public void resetWS() {
      if(ws == null) {
         return;
      }

      ws.getWorksheetInfo().setDesignMaxRows(getViewsheetInfo().getDesignMaxRows());

      // if any table name changed, we should keep the viewsheet table
      // in sync, fix bug1328194963858
      for(Assembly assembly : ws.getAssemblies()) {
         if(!(assembly instanceof TableAssembly)) {
            continue;
         }

         TableAssembly table = (TableAssembly) assembly;
         ColumnSelection cols = table.getColumnSelection();

         if(cols == null || cols.isEmpty()) {
            continue;
         }

         List<DataRef> newCols = cols.stream()
            // @by stephenwebster, For Bug #9172
            // Avoid removing attributes from dynamically created assemblies
            // as this may cause the assembly to fail since it cannot find the
            // calculated field.
            .filter(col -> !(col instanceof CalculateRef) ||
               "true".equals(table.getProperty("output.temp.table")))
            .collect(Collectors.toList());

         if(newCols.size() != cols.getAttributeCount()) {
            table.setColumnSelection(new ColumnSelection(newCols));
         }
      }

      boolean resetVS = createMirrorTables();

      if(resetVS) {
         for(Assembly wassembly : ws.getAssemblies()) {
            if(wassembly instanceof MirrorTableAssembly &&
               wassembly.getName().startsWith(Assembly.TABLE_VS))
            {
               ((MirrorTableAssembly) wassembly).clearCache();
            }
         }
      }

      Set<String> used = createSelectionTables(resetVS);

      // remove useless assemblies and initialize variable map
      Map<String, Set<Assembly>> varmap = new HashMap<>();

      for(Assembly wsAssembly : ws.getAssemblies()) {
         String name = wsAssembly.getName();

         if(name.startsWith(SELECTION) && !used.contains(name) && !wnames.contains(name) &&
            ws.getDependings(wsAssembly.getAssemblyEntry()).length == 0)
         {
            ws.removeAssembly(name);
         }

         if(wsAssembly instanceof TableAssembly) {
            UserVariable[] vars = wsAssembly instanceof ComposedTableAssembly ?
               ((ComposedTableAssembly) wsAssembly).getAllVariables(false) :
               ((TableAssembly) wsAssembly).getAllVariables();

            if(vars != null && vars.length > 0) {
               for(UserVariable var : vars) {
                  String vname = var.getName();
                  Set<Assembly> vset = varmap.computeIfAbsent(vname, k -> new HashSet<>());
                  vset.add(wsAssembly);
               }
            }
         }
      }

      this.varmap = varmap;
   }

   private void addCalcRefs(TableAssembly table) {
      addCalcRefs(table, table.getName());
   }

   private void addCalcRefs(TableAssembly table, String name) {
      final List<CalculateRef> calculateRefs = calcmap.get(name);

      if(calculateRefs != null && calculateRefs.size() > 0) {
         final ColumnSelection columnSelection = table.getColumnSelection(false);

         synchronized(calculateRefs) {
            calculateRefs.forEach((c) -> columnSelection.addAttribute(c.clone()));
         }

         table.resetColumnSelection();
      }
   }

   /**
    * Get the tables which used the specified variable.
    */
   public Collection<Assembly> getTables(String var) {
      return varmap == null ? null : varmap.get(var);
   }

   /**
    * If the specific tableAssembly name in worksheet is a MirrorTableAssembly,
    * convert it to EmbeddedTableAssembly.
    */
   public boolean convertToEmbeddedTable(Worksheet ws, String tableName) {
      boolean changed = false;
      WSAssembly mtable = (WSAssembly) ws.getAssembly(tableName);
      WSAssembly otable = (WSAssembly) ws.getAssembly(tableName + OUTER_TABLE_SUFFIX);

      if(mtable instanceof MirrorTableAssembly && otable instanceof EmbeddedTableAssembly) {
         ws.removeAssembly(tableName);
         otable.getInfo().setName(tableName);
         otable.setVisible(true);

         for(Assembly obj : ws.getAssemblies()) {
            WSAssembly assembly = (WSAssembly) obj;
            assembly.renameDepended(tableName + OUTER_TABLE_SUFFIX, tableName);
         }

         changed = true;
      }

      return changed;
   }

   /**
    * Update this viewsheet.
    * @param rep the specified asset repository.
    * @param entry the specified entry stored in.
    * @param user the specified principal.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   @Override
   public synchronized boolean update(AssetRepository rep, AssetEntry entry, Principal user) {
      try {
         Worksheet ws = null;

         if(wentry != null && wentry.isWorksheet()) {
            ws = wentry == null ? null :
               (Worksheet) rep.getSheet(wentry, null, false, AssetContent.ALL);
            // if binding source is worksheet, use worksheet max rows.

            if(ws != null) {
               ws.getWorksheetInfo().setDesignMaxRows(vinfo.getDesignMaxRows());
            }
         }
         else if(isDirectSource()) {
            ws = getWorksheet(rep, wentry, user);
         }

         amapLock.lock();

         try {
            clearCache(); // clear cached mapping

            // update base worksheet
            setBaseWorksheet(ws);
            List<Assembly> assemblies = new ArrayList<>(Arrays.asList(getAssemblies()));

            // update contained viewsheets
            for(int i = assemblies.size() - 1; i >= 0; i--) {
               VSAssembly assembly = (VSAssembly) assemblies.get(i);

               if(assembly.getAssemblyType() == VIEWSHEET_ASSET) {
                  Viewsheet tvs = (Viewsheet) assembly;
                  ByteArrayOutputStream out = new ByteArrayOutputStream();
                  PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
                  tvs.writeState(writer, true);
                  writer.close();
                  tvs = tvs.refreshViewsheet(rep, entry, user);

                  if(tvs != null) {
                     InputStream in = new ByteArrayInputStream(out.toByteArray());
                     Document doc = Tool.parseXML(in);
                     Element element = doc.getDocumentElement();
                     tvs.parseState(element);
                     updateVSAssembly((VSAssembly) assemblies.get(i), tvs);
                     clearCache();
                     tvs.setLastModified(System.currentTimeMillis());
                     in.close();

                     // clear cached absolute name since the base viewsheet doesn't
                     // have the parent name in the absolute name
                     for(Assembly aobj : tvs.getAssemblies()) {
                        ((VSAssemblyInfo) aobj.getInfo()).setAbsoluteName2(null);
                        ((VSAssemblyInfo) aobj.getInfo()).updateCSSValues();
                     }
                  }
                  else {
                     assemblies.remove(i);
                  }
               }
            }
         }
         finally {
            amapLock.unlock();
         }

         layout();
      }
      catch(Exception ex) {
         LOG.error("Failed to update viewsheet", ex);

         return false;
      }

      return true;
   }

   private void updateVSAssembly(VSAssembly vsAssembly, VSAssembly newAssembly) {
      for(int i = 0; i < assemblies.size(); i++) {
         if(assemblies.get(i).equals(vsAssembly)) {
            assemblies.set(i, newAssembly);
            return;
         }
      }
   }

   /**
    * Copy layout information from current viewsheet.
    */
   private void copyLayout(Viewsheet vs) {
      vs.setRScaleFont(rscaleFont);

      for(Assembly assembly : vs.getAssemblies()) {
         VSAssembly vsassembly = (VSAssembly) assembly;
         VSAssemblyInfo info = vsassembly.getVSAssemblyInfo();
         VSAssembly lassembly = getAssembly(vsassembly.getName());

         if(lassembly != null) {
            VSAssemblyInfo linfo = lassembly.getVSAssemblyInfo();
            Point layoutPosition = linfo.getLayoutPosition(false);
            Point scaledPosition = linfo.getLayoutPosition(true);

            if(layoutPosition != null) {
               info.setLayoutPosition(layoutPosition);
            }

            if(scaledPosition != layoutPosition) { // identity is correct in this context
               info.setScaledPosition(scaledPosition);
            }

            Dimension layoutSize = linfo.getLayoutSize(false);
            Dimension scaledSize = linfo.getLayoutSize(true);

            if(layoutSize != null) {
               info.setLayoutSize(layoutSize);
            }

            if(scaledSize != layoutSize) { // identity is correct in this context
               info.setScaledSize(scaledSize);
            }

            copyFontScale(vsassembly, lassembly);
         }
      }
   }

   /**
    * Clone font scale.
    */
   private void copyFontScale(VSAssembly nassembly, VSAssembly oassembly) {
      FormatInfo formatInfo = nassembly.getFormatInfo();
      FormatInfo oformatInfo = oassembly.getFormatInfo();
      TableDataPath[] paths = formatInfo.getPaths();

      for(TableDataPath path : paths) {
         if(!(nassembly instanceof TableDataVSAssembly) ||
            path.getType() == TableDataPath.TITLE) {
            VSCompositeFormat format = formatInfo.getFormat(path);
            VSCompositeFormat oformat = oformatInfo.getFormat(path);

            if(oformat != null) {
               format.setRScaleFont(oformat.getRScaleFont());
            }
         }
      }
   }

   /**
    * Refresh this viewsheet.
    * @param rep the specified viewsheet repository.
    * @param entry the specified entry stored in.
    * @param user the specified principal.
    * @return the newly created viewsheet.
    */
   private Viewsheet refreshViewsheet(AssetRepository rep, AssetEntry entry,
                                      Principal user)
      throws Exception
   {
      if(ventry != null && ventry.equals(entry)) {
         throw new MessageException("Self contained viewsheet found: " + entry,
                                    LogLevel.WARN, false);
      }

      Viewsheet tvs = ventry == null ? null :
         (Viewsheet) rep.getSheet(ventry, null, false, AssetContent.ALL);

      if(tvs == null) {
         return null;
      }

      tvs.update(rep, ventry, user);

      // copy the assembly associated properties
      tvs.info = info;
      tvs.setViewsheet(this.getViewsheet());
      tvs.ventry = this.ventry;
      copyLayout(tvs);

      return tvs;
   }

   /**
    * Get the outer dependents.
    * @return the outer dependents.
    */
   @Override
   public AssetEntry[] getOuterDependents() {
      Set<AssetEntry> list = new HashSet<>();

      if(wentry != null && wentry.isWorksheet()) {
         list.add(wentry);
      }

      for(Assembly assembly : getAssemblies()) {
         if(!(assembly instanceof Viewsheet)) {
            continue;
         }

         Viewsheet vassembly = (Viewsheet) assembly;
         AssetEntry entry = vassembly.getEntry();

         if(entry != null) {
            list.add(entry);
         }

         Collections.addAll(list, vassembly.getOuterDependents());
      }

      return list.toArray(new AssetEntry[0]);
   }

   /**
    * Rename an outer dependent.
    * @param oentry the specified old entry.
    * @param nentry the specified new entry.
    */
   @Override
   public synchronized void renameOuterDependent(AssetEntry oentry, AssetEntry nentry) {
      if(wentry != null && wentry.isWorksheet() && oentry.equals(wentry)) {
         wentry = nentry;
      }

      for(Assembly assembly : getAssemblies()) {
         if(!(assembly instanceof Viewsheet)) {
            continue;
         }

         Viewsheet vassembly = (Viewsheet) assembly;
         AssetEntry entry = vassembly.getEntry();

         if(Tool.equals(entry, oentry)) {
            vassembly.setEntry(nentry);
         }
      }
   }

   /**
    * Get the outer dependencies.
    * @return the outer dependencies.
    */
   @Override
   public AssetEntry[] getOuterDependencies(boolean sort) {
      AssetEntry[] arr = new AssetEntry[dependencies.size()];
      dependencies.toArray(arr);

      if(sort) {
         Arrays.sort(arr, VersionControlComparators.assetEntry);
      }

      return arr;
   }

   /**
    * Add an outer dependency.
    * @param entry the specified entry.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   @Override
   public boolean addOuterDependency(AssetEntry entry) {
      return dependencies.add(entry);
   }

   /**
    * Remove an outer dependency.
    * @param entry the specified entry.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   @Override
   public boolean removeOuterDependency(AssetEntry entry) {
      return dependencies.remove(entry);
   }

   /**
    * Remove all the outer dependencies.
    */
   @Override
   public void removeOuterDependencies() {
      dependencies.clear();
  }

   /**
    * Get the assemblies depended on of an assembly in a viewsheet.
    * @param entry the specified assembly entry.
    */
   @Override
   public AssemblyRef[] getDependeds(AssemblyEntry entry) {
      return getDependeds(entry, false, false);
   }

   /**
    * Get the assemblies depended on of an assembly in a viewsheet.
    * @param entry the specified assembly entry.
    * @param view <tt>true</tt> to include view, <tt>false</tt> otherwise.
    * @param out <tt>out</tt> to include out, <tt>false</tt> otherwise.
    */
   @Override
   public synchronized AssemblyRef[] getDependeds(AssemblyEntry entry, boolean view, boolean out) {
      Set<AssemblyRef> set = new HashSet<>();
      Assembly assembly = getAssembly(entry);

      if(assembly == null) {
         return new AssemblyRef[0];
      }

      // for worksheet assembly, append the viewsheet assemblies depends on
      if(entry.isWSAssembly()) {
         assembly.getDependeds(set);

         if(out) {
            for(Assembly assemblyItem : getAssemblies(false, false, false, false)) {
               VSAssembly vassembly = (VSAssembly) assemblyItem;

               for(AssemblyRef ref : vassembly.getDependingWSAssemblies()) {
                  if(entry.equals(ref.getEntry())) {
                     set.add(new AssemblyRef(ref.getType(), vassembly.getAssemblyEntry()));
                     break;
                  }
               }
            }
         }
      }
      // for viewsheet assembly, both worksheet and viewsheet assembly depends
      // on are included
      else {
         assembly.getDependeds(set);

         if(view) {
            ((VSAssembly) assembly).getViewDependeds(set, false);
         }
      }

      AssemblyRef[] arr = new AssemblyRef[set.size()];
      set.toArray(arr);
      return arr;
   }

   /**
    * Get the depending assemblies in a viewsheet.
    * @param entry the specified assembly entry.
    */
   public AssemblyRef[] getDependings(AssemblyEntry entry) {
      return getDependings(entry, false);
   }

   /**
    * Get the assemblies that uses the specified assembly. For example, if vstable uses
    * a wstable, and wstable entry is passed in, the vstable is returned.
    * @param entry the specified assembly entry.
    * @param delete is delete for get dependings.
    */
   public synchronized AssemblyRef[] getDependings(AssemblyEntry entry, boolean delete) {
      Set<AssemblyRef> set = new HashSet<>();
      Set<AssemblyRef> set2 = new HashSet<>();
      Assembly assembly = getAssembly(entry);

      if(assembly == null) {
         return new AssemblyRef[0];
      }

      List<Assembly> assemblies = Arrays.asList(getAssemblies());

      if(entry.isWSAssembly()) {
         if(ws != null) {
            Collections.addAll(set, ws.getDependings(entry));
         }

         // for worksheet assembly, append the dependency viewsheet assemblies
         for(Assembly assemblyItem : assemblies) {
            VSAssembly vassembly = (VSAssembly) assemblyItem;

            for(AssemblyRef ref : vassembly.getDependedWSAssemblies()) {
               if(entry.equals(ref.getEntry())) {
                  set.add(new AssemblyRef(ref.getType(), vassembly.getAssemblyEntry()));
                  break;
               }
            }
         }
      }
      else {
         for(Assembly assemblyItem : assemblies) {
            VSAssembly vassembly = (VSAssembly) assemblyItem;
            set2.clear();
            vassembly.getDependeds(set2);
            Iterator<AssemblyRef> iterator = set2.iterator();

            while(iterator.hasNext()) {
               AssemblyRef ref = iterator.next();

               if((delete || ref.getType() == AssemblyRef.INPUT_DATA) && ref.getEntry().equals(entry)) {
                  set.add(new AssemblyRef(AssemblyRef.INPUT_DATA, vassembly.getAssemblyEntry()));
               }
            }

            set2.clear();

            if(delete && vassembly instanceof AbstractContainerVSAssembly) {
               ((AbstractContainerVSAssembly) vassembly).getViewDependeds(
                  set2, true, false);
            }
            else {
               vassembly.getViewDependeds(set2, true);
            }

            if(vassembly instanceof DateCompareAbleAssembly) {
               String shareFromAssembly = ((DateCompareAbleAssembly) vassembly).getComparisonShareFromAssembly();

               if(Tool.equals(entry.getName(), shareFromAssembly)) {
                  set2.add(new AssemblyRef(AssemblyRef.INPUT_DATA, entry));
               }
            }

            iterator = set2.iterator();

            while(iterator.hasNext()) {
               AssemblyRef ref = iterator.next();

               if((delete || ref.getType() == AssemblyRef.INPUT_DATA) &&
                  ref.getEntry().equals(entry))
               {
                  set.add(new AssemblyRef(AssemblyRef.VIEW, vassembly.getAssemblyEntry()));
               }
            }
         }

         // for viewsheet assembly, append the dependency worksheet assemblies
         Collections.addAll(set, ((VSAssembly) assembly).getDependingWSAssemblies());
      }

      return set.toArray(new AssemblyRef[0]);
   }

   /**
    * Get the view dependency assemblies in a viewsheet.
    * @param entry the specified assembly entry.
    */
   public AssemblyRef[] getViewDependings(AssemblyEntry entry) {
      return getViewDependings(entry, false);
   }

   /**
    * Get the view dependency assemblies in a viewsheet.
    * @param entry the specified assembly entry.
    */
   public synchronized AssemblyRef[] getViewDependings(AssemblyEntry entry, boolean delete) {
      Set<AssemblyRef> set = new HashSet<>();
      Set<AssemblyRef> set2 = new HashSet<>();

      List<Assembly> assemblies = Arrays.asList(getAssemblies());

      for(Assembly assemblyItem : assemblies) {
         VSAssembly assembly = (VSAssembly) assemblyItem;
         set2.clear();
         assembly.getDependeds(set2);

         for(AssemblyRef ref : set2) {
            if((delete || ref.getType() == AssemblyRef.VIEW) && ref.getEntry().equals(entry)) {
               set.add(new AssemblyRef(AssemblyRef.INPUT_DATA, assembly.getAssemblyEntry()));
            }
         }

         set2.clear();

         if(!(assembly instanceof CurrentSelectionVSAssembly) &&
            assembly instanceof AbstractContainerVSAssembly)
         {
            ((AbstractContainerVSAssembly) assembly).getViewDependeds(set2, true, false);
         }
         else {
            assembly.getViewDependeds(set2, true);
         }

         for(AssemblyRef ref : set2) {
            if((delete || ref.getType() == AssemblyRef.VIEW) && ref.getEntry().equals(entry)) {
               set.add(new AssemblyRef(AssemblyRef.VIEW, assembly.getAssemblyEntry()));
            }
         }
      }

      return set.toArray(new AssemblyRef[0]);
   }

   /**
    * Get the output dependency assemblies in a viewsheet.
    * @param entry the specified assembly entry.
    */
   public synchronized AssemblyRef[] getOutputDependings(AssemblyEntry entry) {
      Set<AssemblyRef> set = new HashSet<>();
      Set<AssemblyRef> set2 = new HashSet<>();

      for(Assembly assemblyItem : getAssemblies()) {
         VSAssembly assembly = (VSAssembly) assemblyItem;
         set2.clear();
         assembly.getOutputDependeds(set2);
         Iterator<AssemblyRef> iterator = set2.iterator();

         while(iterator.hasNext()) {
            AssemblyRef ref = iterator.next();

            if(ref.getType() == AssemblyRef.OUTPUT_DATA && ref.getEntry().equals(entry) &&
               !WizardRecommenderUtil.isWizardTempAssembly(assembly.getName()))
            {
               set.add(new AssemblyRef(AssemblyRef.OUTPUT_DATA, assembly.getAssemblyEntry()));
            }
         }

         set2.clear();
         assembly.getDependeds(set2);
         iterator = set2.iterator();

         while(iterator.hasNext()) {
            AssemblyRef ref = iterator.next();

            if(ref.getType() == AssemblyRef.OUTPUT_DATA && ref.getEntry().equals(entry) &&
               !WizardRecommenderUtil.isWizardTempAssembly(assembly.getName()))
            {
               set.add(new AssemblyRef(AssemblyRef.INPUT_DATA, assembly.getAssemblyEntry()));
            }
         }

         set2.clear();
         assembly.getViewDependeds(set2, true);
         iterator = set2.iterator();

         while(iterator.hasNext()) {
            AssemblyRef ref = iterator.next();

            if(ref.getType() == AssemblyRef.OUTPUT_DATA && ref.getEntry().equals(entry)) {
               set.add(new AssemblyRef(AssemblyRef.VIEW, assembly.getAssemblyEntry()));
            }
         }
      }

      Assembly assembly = getAssembly(entry);

      if(assembly instanceof VSAssembly) {
         VSAssembly vassembly = (VSAssembly) assembly;

         for(AssemblyRef ref : vassembly.getDependingWSAssemblies()) {
            set.add(new AssemblyRef(AssemblyRef.INPUT_DATA, ref.getEntry()));
         }
      }

      return set.toArray(new AssemblyRef[0]);
   }

   /**
    * Check if contains script.
    */
   @Override
   public boolean containsScript() {
      return false;
   }

   /**
    * Get the dependency assemblies by attached script.
    * @param entry the specified assembly entry.
    * @return an array of AssemblyRef.
    */
   public synchronized AssemblyRef[] getScriptDependings(AssemblyEntry entry) {
      Set<AssemblyRef> set = new HashSet<>();
      Set<AssemblyRef> set2 = new HashSet<>();

      for(Assembly assemblyItem : getAssemblies()) {
         if(!(assemblyItem instanceof AbstractVSAssembly)) {
            continue;
         }

         AbstractVSAssembly assembly = (AbstractVSAssembly) assemblyItem;
         set2.clear();
         assembly.getScriptReferencedAssets(set2);

         for(AssemblyRef ref : set2) {
            if(ref.getEntry().equals(entry)) {
               set.add(new AssemblyRef(ref.getType(), assembly.getAssemblyEntry()));
            }
         }
      }

      return set.toArray(new AssemblyRef[0]);
   }

   /**
    * Get the runtime entry.
    * @return the runtime entry of this viewsheet.
    */
   public AssetEntry getRuntimeEntry() {
      return rentry;
   }

   /**
    * Set the runtime entry.
    * @param rentry the specified runtime entry.
    */
   public void setRuntimeEntry(AssetEntry rentry) {
      this.rentry = rentry;
   }

   /**
    * Validate the viewsheet.
    */
   public synchronized void validate() {
      for(Assembly assemblyItem : getAssemblies()) {
         VSAssembly assembly = (VSAssembly) assemblyItem;
         ContainerVSAssembly tassembly = (ContainerVSAssembly) assembly.getContainer();

         if(tassembly != null) {
            if(tassembly instanceof GroupContainerVSAssembly && tassembly.isPrimary()) {
               continue;
            }

            assembly.setPrimary(tassembly.isPrimary());
         }
      }
   }

   // start of assembly apis

   /**
    * Create a viewsheet assembly.
    * @param name the specified name.
    * @return the created viewsheet assembly.
    */
   public Viewsheet createVSAssembly(String name) {
      this.setName(name);
      return this;
   }

   /**
    * Get the mirrored viewsheet entry.
    */
   public AssetEntry getEntry() {
      return ventry;
   }

   /**
    * Set the mirrored viewsheet entry.
    */
   public void setEntry(AssetEntry ventry) {
      this.ventry = ventry;
   }

   /**
    * Get the name.
    * @return the name of the assembly.
    */
   @Override
   public String getName() {
      return info.getName();
   }

   /**
    * Set the name.
    * @param name the specified name of this assembly.
    */
   protected void setName(String name) {
      info.setName(name);
   }

   /**
    * Get the assembly entry.
    * @return the assembly entry.
    */
   @Override
   public AssemblyEntry getAssemblyEntry() {
      return new AssemblyEntry(getName(), getAbsoluteName(), getAssemblyType());
   }

   /**
    * Get the assembly info.
    * @return the associated assembly info.
    */
   @Override
   public AssemblyInfo getInfo() {
      return info;
   }

   /**
    * Set the position.
    */
   @Override
   public void setPixelOffset(Point pos) {
      info.setPixelOffset(pos);
   }

   /**
    * Get the position.
    * @return the position of the assembly.
    */
   @Override
   public Point getPixelOffset() {
      if(!isEmbedded()) {
         return new Point(0, 0);
      }

      return info.getPixelOffset();
   }

   /**
    * Set the size.
    * @param size the specified size.
    */
   @Override
   public void setPixelSize(Dimension size) {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Set the bounds.
    * @param bounds the specified bounds.
    */
   @Override
   public void setBounds(Rectangle bounds) {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Get the bounds.
    * @return the bounds of the assembly.
    */
   @Override
   public Rectangle getBounds() {
      return new Rectangle(getPixelOffset(), getPixelSize());
   }

   /**
    * Get the minimum size.
    * @return the minimum size of the assembly.
    */
   @Override
   public Dimension getMinimumSize() {
      return getPixelSize();
   }

   /**
    * Get the type.
    * @return the type of the assembly.
    */
   @Override
   public int getAssemblyType() {
      return VIEWSHEET_ASSET;
   }

   /**
    * Check if is visible.
    * @return <tt>true</tt> if visible, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isVisible() {
      Viewsheet vs = getViewsheet();

      if(vs != null && vs.isEmbedded() && !isPrimary()) {
         return false;
      }

      boolean cprint = vs == null ? isPrintMode() : vs.isPrintMode();
      VSAssembly container = getContainer();
      boolean cvis = container == null || container.getVSAssemblyInfo().isVisible(cprint);
      boolean pvis = vs == null || vs.isVisible();
      boolean svis = info.isVisible(isPrintMode());
      return svis && cvis && pvis;
   }

   /**
    * Check if is editable.
    * @return <tt>true</tt> if editable, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isEditable() {
      return info.isEditable();
   }

   /**
    * Check if the VSAssembly is resizable.
    * @return <tt>true</tt> of resizable, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isResizable() {
      return info.isResizable();
   }

   /**
    * Check if the binding source is Query/Physical Table/LogicalModel(QTL).
    * @return <tt>true</tt> if QTL, <tt>false</tt> otherwise.
    */
   public boolean isDirectSource() {
      return wentry != null && (wentry.isQuery() || wentry.isPhysicalTable() ||
         wentry.isLogicModel());
   }

   /**
    * Check if is logical model source.
    */
   public boolean isLMSource() {
      return wentry != null && wentry.isLogicModel();
   }

   /**
    * Get the viewsheet assembly info.
    * @return the viewsheet assembly info.
    */
   @Override
   public VSAssemblyInfo getVSAssemblyInfo() {
      return (VSAssemblyInfo) getInfo();
   }

   /**
    * Set the parent viewsheet.
    * @param vs the specified viewsheet.
    */
   @Override
   public void setViewsheet(Viewsheet vs) {
      info.setViewsheet(vs);
   }

   /**
    * Get the parent viewsheet.
    * @return the parent viewsheet.
    */
   @Override
   public Viewsheet getViewsheet() {
      return info.getViewsheet();
   }

   /**
    * Check if is a primary assembly.
    * @return <tt>true</tt> if primary, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isPrimary() {
      return info.isPrimary();
   }

   /**
    * Set whether is a primary assembly.
    * @param primary <tt>true</tt> if primary, <tt>false</tt> otherwise.
    */
   @Override
   public void setPrimary(boolean primary) {
      info.setPrimary(primary);
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public synchronized Viewsheet clone() {
      return clone0(false);
   }

   public synchronized Viewsheet cloneForBindingEditor() {
      return clone0(false, false);
   }

   private synchronized Viewsheet clone0(boolean checkpoint) {
      return clone0(checkpoint, true);
   }

   private synchronized Viewsheet clone0(boolean checkpoint, boolean ignoreShareFrames) {
      try {
         Viewsheet vs = (Viewsheet) super.clone();
         vs.info = (ViewsheetVSAssemblyInfo) info.clone();
         vs.vinfo = (ViewsheetInfo) vinfo.clone();
         vs.layoutInfo = (LayoutInfo) layoutInfo.clone();

         try {
            amapLock.lock();
            cloneAssemblyList(vs, assemblies);
         }
         finally {
            amapLock.unlock();
         }

         vs.dependencies = new HashSet<>(dependencies);
         vs.ws = ws == null || checkpoint ? null: (Worksheet) ws.clone();
         vs.wentry = wentry == null ? null : (AssetEntry) wentry.clone();
         vs.clearCache();
         vs.varmap = null;
         vs.annotationsVisible = annotationsVisible;

         if(imgmap != null) {
            vs.imgmap = new HashMap<>(imgmap);
         }

         if(dimensionColors != null) {
            vs.dimensionColors = new HashMap<>(dimensionColors);
         }

         if(calcmap != null) {
            vs.calcmap = Tool.deepCloneMap(calcmap);
         }

         if(aggregatemap != null) {
            vs.aggregatemap = Tool.deepCloneMap(aggregatemap);
         }

         // set proper viewsheet
         for(int i = 0; i < vs.assemblies.size(); i++) {
            VSAssembly assembly = vs.assemblies.get(i);
            assembly.setViewsheet(vs);
         }

         if(ignoreShareFrames) {
            // deep clone sharedFrames
            Field field = Viewsheet.class.getDeclaredField("sharedFrames");
            field.setAccessible(true);
            Map<SharedFrameParameters, VisualFrame> clonedSharedFrames = new HashMap<>();

            for(Map.Entry<SharedFrameParameters, VisualFrame> entry : this.sharedFrames.entrySet()) {
               SharedFrameParameters key = entry.getKey();
               VisualFrame value = entry.getValue();
               VisualFrame clonedVisualFrame = (VisualFrame) value.clone();
               clonedSharedFrames.put(key, clonedVisualFrame);
            }

            field.set(vs, clonedSharedFrames);
         }

         vs.listener = new VSChangeListener(vs);
         vs.addActionListener(vs.listener);
         return vs;
      }
      catch(Exception ex) {
         LOG.error("failed to clone viewsheet", ex);
         return null;
      }
   }

   /**
    * Clone assembly list.
    */
   private synchronized void cloneAssemblyList(Viewsheet vs, List<VSAssembly> list) {
      int size = list.size();
      List<VSAssembly> list2 = new Vector<>(size);

      for(Assembly assembly : list) {
         if(latestTemp == assembly) {
            vs.latestTemp = (VSAssembly) assembly.clone();
            list2.add(vs.latestTemp);
         }
         else {
            list2.add((VSAssembly) assembly.clone());
         }
      }

      vs.assemblies = list2;
   }

   /**
    * Get the offset.
    * @return the offset.
    */
   public Point getOffset() {
      if(isEmbedded()) {
         if(upperLeft == null) {
            layout();
         }

         return upperLeft;
      }

      return new Point(0, 0);
   }

   /**
    * Get the preferred size.
    * @return the preferred size.
    */
   public Dimension getPreferredSize() {
      return getPreferredSize(false, false);
   }

   public Dimension getPreferredSize(boolean includeInvisible, boolean includeAnnotation) {
      Rectangle bounds = getPreferredBounds(includeInvisible, includeAnnotation);
      return new Dimension((int) Math.floor(bounds.getMaxX()), (int) Math.floor(bounds.getMaxY()));
   }

   public Rectangle getPreferredBounds() {
      return getPreferredBounds(false, false, false);
   }

   public Rectangle getPreferredBounds(boolean includeInvisible, boolean includeAnnotation) {
      return getPreferredBounds(includeInvisible, includeAnnotation, false);
   }

   public Rectangle getPreferredBounds(boolean includeInvisible,
                                       boolean includeAnnotation,
                                       boolean forcePixelSizeIfLayoutZero)
   {

      Rectangle bounds = null;
      final boolean ovis = getInfo().isVisible();

      if(!ovis && forcePixelSizeIfLayoutZero) {
         // if vs is not visible, all children will be invisible so the preferred size
         // is meaningless. set to true and then restore later
         //info.setVisible(true);
      }

      try {
         Viewsheet rootVS = getRootViewsheet(this);

         for(Assembly assemblyItem : getAssemblies(false, false, true, false, true)) {
            VSAssembly assembly = (VSAssembly) assemblyItem;
            String assemblyName = assembly.getAbsoluteName();
            boolean isFloat = VSUtil.isPopComponent(assemblyName, rootVS) ||
               VSUtil.isTipView(assemblyName, rootVS);

            if(!includeAnnotation) {
               if(assembly instanceof AnnotationVSAssembly ||
                  assembly instanceof AnnotationLineVSAssembly ||
                  assembly instanceof AnnotationRectangleVSAssembly)
               {
                  continue;
               }
            }

            // Don't factor invisible or pop-type components into scaling
            if(!includeInvisible && !assembly.isVisible() || isFloat ||
               (isEmbedded() && !isPrimary(assembly)))
            {
               continue;
            }

            // Skip assemblies which are inside of a selection container.
            if(assembly.getContainer() instanceof CurrentSelectionVSAssembly) {
               continue;
            }

            AssemblyInfo info = assembly.getInfo();
            Dimension size;
            Point pos;

            size = ((VSAssemblyInfo) info).getLayoutSize();
            pos = ((VSAssemblyInfo) info).getLayoutPosition();

            if(size == null || forcePixelSizeIfLayoutZero && (size.width == 0 || size.height == 0))
            {
               size = getPixelSize(info);
            }

            if(pos == null) {
               pos = getPixelPosition(info);
            }

            if(info instanceof TextVSAssemblyInfo) {
               CoordinateHelper.fixTextSize(size, pos, (TextVSAssemblyInfo) info);
            }

            // data tip is hidden by moving it off-screen. this should be treated
            // as invisible
            if(!includeInvisible && (pos.y < 0 && -pos.y > size.height ||
               pos.x < 0 && -pos.x > size.width)) {
               continue;
            }

            Rectangle assemblyBounds =
               new Rectangle(pos.x, pos.y, size.width, size.height);

            if(bounds == null) {
               bounds = assemblyBounds;
            }
            else {
               bounds.add(assemblyBounds);
            }
         }
      }
      finally {
      }

      if(bounds == null) {
         bounds = new Rectangle(0, 0, AssetUtil.defw, AssetUtil.defh);
      }

      return bounds;
   }

   private Viewsheet getRootViewsheet(Viewsheet vs) {
      if(vs.getViewsheet() == null) {
         return vs;
      }

      return getRootViewsheet(vs.getViewsheet());
   }

   private boolean isPrimary(VSAssembly assembly) {
      if(!assembly.isPrimary()) {
         return false;
      }

      VSAssembly container = assembly.getContainer();

      if(container != null) {
         return isPrimary(container);
      }

      return true;
   }

   /**
    * Get the size of this sheet. For embedded viewsheet, this is the size
    * in the parent grid calculated from the absolute size.
    */
   @Override
   public Dimension getPixelSize() {
      return (info.getPixelSize() == null) ? getSize0() : info.getPixelSize();
   }

   /**
    * Get the size of this sheet. This is the rows and columns occupied by the
    * assemblies in this viewsheet.
    */
   private Dimension getSize0() {
      if(isEmbedded()) {
         if(upperLeft == null) {
            layout();
         }

         return new Dimension(bottomRight.x - upperLeft.x,
                              bottomRight.y - upperLeft.y);
      }

      Dimension size = getPreferredSize();
      int maxw = Math.max(MIN_SIZE.width, size.width + (10 * AssetUtil.defw));
      int maxh = Math.max(MIN_SIZE.height, size.height + (10 * AssetUtil.defh));

      return new Dimension(maxw, maxh);
   }

   /**
    * Get the absolute name of this assembly.
    * @return the absolute name of this assembly.
    */
   @Override
   public String getAbsoluteName() {
      return info.getAbsoluteName();
   }

   /**
    * Check if this viewsheet is an embedded one.
    * @return <tt>true</tt> if is embedded, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isEmbedded() {
      return info.isEmbedded();
   }

   /**
    * Check if the assembly is enabled.
    * @return <tt>true</tt> if enabled, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isEnabled() {
      return info.isEnabled();
   }

   /**
    * Copy the assembly.
    * @param name the specified new assembly name.
    * @return the copied assembly.
    */
   @Override
   public VSAssembly copyAssembly(String name) {
      try {
         Viewsheet assembly = clone();
         assembly.setName(name);
         return assembly;
      }
      catch(Exception ex) {
         return null;
      }
   }

   /**
    * Get the container.
    * @return the container if existing & visible, <tt>null</tt> otherwise.
    */
   @Override
   public VSAssembly getContainer() {
      Viewsheet vs = getViewsheet();

      if(vs == null) {
         return null;
      }

      for(Assembly tassembly : vs.getAssemblies()) {
         if(tassembly.getAssemblyType() != Viewsheet.TAB_ASSET &&
            tassembly.getAssemblyType() != Viewsheet.GROUPCONTAINER_ASSET &&
            tassembly.getAssemblyType() != Viewsheet.CURRENTSELECTION_ASSET)
         {
            continue;
         }

         ContainerVSAssembly container = (ContainerVSAssembly) tassembly;

         if(container.containsAssembly(this.getName())) {
            return container;
         }
      }

      return null;
   }

   /**
    * Check if the dependency is valid.
    */
   @Override
   public void checkDependency() {
      // do nothing
   }

   /**
    * Get the worksheet.
    * @return the worksheet if any.
    */
   @Override
   public Worksheet getWorksheet() {
      Viewsheet vs = getViewsheet();
      return (vs == null) ? null : vs.getBaseWorksheet();
   }

   /**
    * Get the worksheet assemblies depended on.
    * @return the assemblies depended on.
    */
   @Override
   public AssemblyRef[] getDependedWSAssemblies() {
      return new AssemblyRef[0];
   }

   /**
    * Get the depending worksheet assemblies to modify.
    * @return the depending assemblies to modify.
    */
   @Override
   public AssemblyRef[] getDependingWSAssemblies() {
      return new AssemblyRef[0];
   }

   /**
    * Get the assemblies depended on.
    * @param set the set stores the assemblies depended on.
    */
   @Override
   public void getDependeds(Set<AssemblyRef> set) {
      List<DynamicValue> list = getDynamicValues();
      VSUtil.getDynamicValueDependeds(list, set, getViewsheet(), null);
      AssemblyRef[] entries = getDependedWSAssemblies();
      Collections.addAll(set, entries);
   }

   /**
    * Get the assemblies depended on by its output values.
    * @param set the set stores the assemblies depended on.
    */
   @Override
   public void getOutputDependeds(Set<AssemblyRef> set) {
      List<DynamicValue> list = getOutputDynamicValues();
      VSUtil.getDynamicValueDependeds(list, set, getViewsheet(), null);
   }

   /**
    * Get the view assemblies depended on.
    * @param set the set stores the presentation assemblies depended on.
    * @param self <tt>true</tt> to include self, <tt>false</tt> otherwise.
    */
   @Override
   public void getViewDependeds(Set<AssemblyRef> set, boolean self) {
      List<DynamicValue> list = getViewDynamicValues(true);

      for(Object item : list) {
         DynamicValue val = (DynamicValue) item;
         VSUtil.getDynamicValueDependeds(val, set, getViewsheet(), null);
      }

      // self view dependes on self data
      if(self) {
         set.add(new AssemblyRef(AssemblyRef.INPUT_DATA, getAssemblyEntry()));
      }
   }

   /**
    * Get the dynamic property values. Dynamic properties are properties that
    * can be a variable or a script.
    * @return the dynamic values.
    */
   @Override
   public List<DynamicValue> getDynamicValues() {
      VSAssemblyInfo vinfo = getVSAssemblyInfo();
      return vinfo.getDynamicValues();
   }

   /**
    * Get the output dynamic property values.
    * @return the dynamic values.
    */
   @Override
   public List<DynamicValue> getOutputDynamicValues() {
      VSAssemblyInfo vinfo = getVSAssemblyInfo();
      return vinfo.getOutputDynamicValues();
   }

   /**
    * Get the view dynamic values.
    * @param all true to include all view dynamic values. Otherwise only the
    * dynamic values need to be executed are returned.
    * @return the view dynamic values.
    */
   @Override
   public List<DynamicValue> getViewDynamicValues(boolean all) {
      VSAssemblyInfo vinfo = getVSAssemblyInfo();
      return vinfo.getViewDynamicValues(all);
   }

   /**
    * Get th hyperlink dynamic property values.
    * @return the dynamic values.
    */
   @Override
   public List<DynamicValue> getHyperlinkDynamicValues() {
      return new ArrayList<>();
   }

   /**
    * Rename the depended. This method should be called when an assembly or
    * other named variables are renamed. It updates of the dynamic references
    * to use the new name.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   @Override
   public void renameDepended(String oname, String nname) {
      if(isEmbedded()) {
         return;
      }

      VSAssemblyInfo info = getVSAssemblyInfo();
      info.renameDepended(oname, nname, getViewsheet());

      if(vinfo.getOnInit() != null) {
         vinfo.setOnInit(Util.renameScriptDepended(oname, nname, vinfo.getOnInit()));
      }

      if(vinfo.getOnLoad() != null) {
         vinfo.setOnLoad(Util.renameScriptDepended(oname, nname, vinfo.getOnLoad()));
      }
   }

   /**
    * Set the assembly info.
    * @param info the specified viewsheet assembly info.
    * @return the hint to reset view, input data or output data.
    */
   @Override
   public int setVSAssemblyInfo(VSAssemblyInfo info) {
      VSAssemblyInfo vinfo = getVSAssemblyInfo();
      return vinfo.copyInfo(info);
   }

   /**
    * Update the assembly to fill in runtime value.
    * @param columns the specified column selection.
    */
   @Override
   public void update(ColumnSelection columns) throws Exception {
      VSAssemblyInfo vinfo = getVSAssemblyInfo();
      vinfo.update(getViewsheet(), columns);
   }

   /**
    * Write the state.
    * @param writer the specified print writer.
    */
   @Override
   public synchronized void writeState(PrintWriter writer, boolean runtime) {
      writer.print("<assembly modified=\"" + getLastModified() + "\" class=\"" +
                   getClass().getName() + "\">");

      if(getViewsheet() != null) {
         writer.print("<name>");
         writer.print("<![CDATA[" + getName() + "]]>");
         writer.println("</name>");
      }

      // write current selection first
      for(Assembly assemblyItem : getAssemblies()) {
         VSAssembly assembly = (VSAssembly) assemblyItem;

         if(assembly.getAssemblyType() == CURRENTSELECTION_ASSET) {
            assembly.writeState(writer, runtime);
         }
      }

      // writer other second
      for(Assembly assemblyItem : getAssemblies()) {
         VSAssembly assembly = (VSAssembly) assemblyItem;

         if(needWriteState(assembly)) {
            assembly.writeState(writer, runtime);
         }
      }

      writer.println("<state_viewsheet_calc>");

      for(Map.Entry<String, List<CalculateRef>> entry : calcmap.entrySet()) {
         final String key = entry.getKey();
         final List<CalculateRef> calcs = entry.getValue();
         writer.println("<allcalc>");
         writer.print("<tname>");
         writer.print("<![CDATA[" + key + "]]>");
         writer.println("</tname>");
         writer.print("<values>");

         for(CalculateRef calc : calcs) {
            if(calc.isDcRuntime()) {
               continue;
            }

            writer.print("<value>");
            calc.writeXML(writer);
            writer.println("</value>");
         }

         writer.print("</values>");
         writer.println("</allcalc>");
      }

      writer.println("</state_viewsheet_calc>");

      writer.print("</assembly>");
   }

   /**
    * Check this assembly if need write state.
    */
   private boolean needWriteState(VSAssembly assembly) {
      return assembly.getAssemblyType() != CURRENTSELECTION_ASSET &&
         (!AnnotationVSUtil.isAnnotation(assembly) ||
          AnnotationVSUtil.getAnnoType(this, assembly) ==
          AnnotationVSAssemblyInfo.VIEWSHEET);
   }

   /**
    * Parse the state.
    * @param elem the specified xml element.
    */
   @Override
   public void parseState(Element elem) throws Exception {
      parseState(elem, false);
   }

   /**
    * Get all table names which is binding to selection.
    */
   private synchronized List<String> getSelectionTables() {
      List<String> tables = new ArrayList<>();

      for(Object obj : getAssemblies()) {
         if(obj instanceof SelectionVSAssembly) {
            String table = ((SelectionVSAssembly) obj).getTableName();

            if(table != null && !tables.contains(table)) {
               tables.add(table);
            }
         }
      }

      return tables;
   }

   /**
    * Reset the table which is original binding to a selection, but now not.
    */
   private void resetSelectionTable(String table) {
      if(table != null || ws != null) {
         Object obj = ws.getAssembly(table);

         if(obj instanceof TableAssembly) {
            TableAssembly tass = (TableAssembly) obj;

            if(tass.isPlain()) {
               tass.setPreRuntimeConditionList(new ConditionList());
            }
            else {
               tass.setPostRuntimeConditionList(new ConditionList());
            }
         }
      }
   }

   /**
    * Parse the state.
    * @param elem the specified xml element.
    * @param ignoreTS the specified ignore time stamp or not.
    */
   public void parseState(Element elem, boolean ignoreTS) throws Exception {
      List<String> oselections = getSelectionTables();
      reset();
      String str = Tool.getAttribute(elem, "modified");

      if(str != null && !ignoreTS) {
         if(getLastModified() - Long.parseLong(str) > 0) {
            return;
         }
      }

      NodeList anodes = Tool.getChildNodesByTagName(elem, "assembly");
      // current selection name and the its element node, for current selection
      // we should first remove all children in all current selection, and then
      // parse to add each current selection's children
      // @by skyf, for annotation feature, here we should also remove the
      // annotation assemblies firstly for DataVSAssembly and OutputVSAssembly
      Map<String, Element> clist = new HashMap<>();
      List<Element> vsAnno = new ArrayList<>();
      // @by skyf, remove all old annotations firstly.
      Viewsheet vs = VSUtil.getTopViewsheet(this);

      for(Assembly assemblyItem : getAssemblies()) {
         VSAssemblyInfo vinfo = (VSAssemblyInfo) assemblyItem.getInfo();

         if(vinfo instanceof AnnotationVSAssemblyInfo) {
            AnnotationVSAssemblyInfo ainfo = (AnnotationVSAssemblyInfo) vinfo;

            if(ainfo.getType() == AnnotationVSAssemblyInfo.VIEWSHEET) {
               String rect = ainfo.getRectangle();

               if(rect != null) {
                  removeAssembly(rect);
               }

               removeAssembly(ainfo.getAbsoluteName());
            }
         }
         else if(vinfo instanceof BaseAnnotationVSAssemblyInfo) {
            boolean noAnno = getRuntimeEntry() != null &&
               "true".equals(getRuntimeEntry().getProperty("noAnnotation"));

            if(!noAnno) {
               BaseAnnotationVSAssemblyInfo binfo =
                  (BaseAnnotationVSAssemblyInfo) vinfo;
               List<String> list = binfo.getAnnotations();

               for(String annotation : list) {
                  Assembly sub = vs.getAssembly(annotation);

                  if(sub instanceof AnnotationVSAssembly) {
                     AnnotationVSAssemblyInfo ainfo =
                        (AnnotationVSAssemblyInfo) sub.getInfo();

                     if(ainfo.getType() != AnnotationVSAssemblyInfo.VIEWSHEET) {
                        String line = ainfo.getLine();
                        String rect = ainfo.getRectangle();

                        if(line != null) {
                           vs.removeAssembly(line);
                        }

                        if(rect != null) {
                           vs.removeAssembly(rect);
                        }

                        vs.removeAssembly(annotation);
                     }
                  }
               }

               binfo.clearAnnotations();
            }
         }
      }

      for(int i = 0; i < anodes.getLength(); i++) {
         Element anode = (Element) anodes.item(i);
         String cls = Tool.getAttribute(anode, "class");

         // @by skyf, parse viewsheet annotation
         if("inetsoft.uql.viewsheet.AnnotationVSAssembly".equals(cls) ||
            "inetsoft.uql.viewsheet.AnnotationRectangleVSAssembly".equals(cls))
         {
            Element nnode = Tool.getChildNodeByTagName(anode, "assemblyInfo");

            if(nnode != null) {
               vsAnno.add(anode);
               continue;
            }
         }

         Element nnode = Tool.getChildNodeByTagName(anode, "name");
         String name = Tool.getValue(nnode);
         VSAssembly assembly = getAssembly(name);

         if(assembly != null) {
            if(assembly instanceof CurrentSelectionVSAssembly ||
               assembly.getInfo() instanceof BaseAnnotationVSAssemblyInfo)
            {
               clist.put(name, anode);
               continue;
            }

            if(assembly instanceof Viewsheet) {
               ((Viewsheet) assembly).parseState(anode, ignoreTS);
            }
            else {
               assembly.parseState(anode);
            }
         }
      }

      Iterator<String> keys = clist.keySet().iterator();

      while(keys.hasNext()) {
         String name = keys.next();
         Assembly ass = getAssembly(name);

         if(ass instanceof CurrentSelectionVSAssembly) {
            // only remove all children in current selection
            CurrentSelectionVSAssembly cass = (CurrentSelectionVSAssembly) ass;
            String[] children = cass.getAssemblies();

            for(String child : children) {
               removeAssembly(child);
            }
         }
      }

      // @by skyf, create the viewsheet annotation after remove old annotations
      for(Element vsAnnoElement : vsAnno) {
         VSAssembly assembly = AbstractVSAssembly.createVSAssembly(vsAnnoElement, vs);

         if(assembly != null) {
            vs.addAssembly(assembly);
         }
      }

      keys = clist.keySet().iterator();

      while(keys.hasNext()) {
         String name = keys.next();
         VSAssembly vass = getAssembly(name);

         String className = clist.get(name).getAttribute("class");

         if(vass.getClass().getName().equals(className)) {
            vass.parseState(clist.get(name));
         }
         else {
            VSAssembly nvass = (VSAssembly) Class.forName(className).getConstructor().newInstance();

            if(vass instanceof DataVSAssembly && nvass instanceof DataVSAssembly) {
               ((DataVSAssembly) nvass).setSourceInfo(((DataVSAssembly) vass).getSourceInfo());
            }

            clearCache();
            nvass.setViewsheet(this);
            nvass.getInfo().setName(vass.getInfo().getName());
            nvass.parseState(clist.get(name));
            syncBookmarkAssembly(nvass, vass);
            updateVSAssembly(vass, nvass);
         }
      }

      List<String> nselections = getSelectionTables();

      for(String otable : oselections) {
         if(!nselections.contains(otable)) {
            resetSelectionTable(otable);
         }
      }

      // layout the top viewsheet for size might change
      if(!isEmbedded()) {
         layout();
      }

      Element calcNode = Tool.getChildNodeByTagName(elem, "state_viewsheet_calc");

      if(calcNode != null) {
         calcmap.clear();
         NodeList inodes = Tool.getChildNodesByTagName(calcNode, "allcalc");

         for(int i = 0; i < inodes.getLength(); i++) {
            Element inode = (Element) inodes.item(i);
            Element nnode = Tool.getChildNodeByTagName(inode, "tname");
            String tname = Tool.getValue(nnode);
            Element vnode = Tool.getChildNodeByTagName(inode, "values");

            if(vnode != null) {
               NodeList cnode = Tool.getChildNodesByTagName(vnode, "value");
               List<CalculateRef> calcs = new ArrayList<>();

               for(int j = 0; j < cnode.getLength(); j++) {
                  Element calcnode = (Element) cnode.item(j);
                  CalculateRef cref =
                     (CalculateRef) AbstractDataRef.createDataRef(
                        Tool.getChildNodeByTagName(calcnode, "dataRef"));
                  cref.setVisible(true);
                  calcs.add(cref);
               }

               calcmap.put(tname, calcs);
            }
         }
      }
   }

   /**
    * Get the sheet container.
    * @return the sheet container.
    */
   @Override
   public AbstractSheet getSheet() {
      return getViewsheet();
   }

   /**
    * Get the format info.
    * @return the format info of this assembly info.
    */
   @Override
   public FormatInfo getFormatInfo() {
      return info.getFormatInfo();
   }

   /**
    * Set the format info to this assembly info.
    * @param finfo the specified format info.
    */
   @Override
   public void setFormatInfo(FormatInfo finfo) {
      info.setFormatInfo(finfo);
   }

   /**
    * Check if equals another object.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals, <tt>false</tt> otherwise.
    */
   @Override
   public boolean equals(Object obj) {
      if(!(obj instanceof Viewsheet)) {
         return false;
      }

      Viewsheet assembly = (Viewsheet) obj;
      return getAbsoluteName().equals(assembly.getAbsoluteName());
   }

   /**
    * Get the hash code.
    * @return the hash code.
    */
   public int hashCode() {
      return getAssemblyEntry().hashCode();
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      return getAbsoluteName() + addr();
   }

   /**
    * Check if supports tab.
    * @return <tt>true</tt> if this assembly may be laid as one sub component
    * of a tab, <tt>false</tt> otherwise.
    */
   @Override
   public boolean supportsTab() {
      return true;
   }

   /**
    * Check if supports container.
    * @return <tt>true</tt> if this assembly may be laid as one sub component
    * of a container, <tt>false</tt> otherwise.
    */
   @Override
   public boolean supportsContainer() {
      return false;
   }

   /**
    * Initialize the default format.
    */
   @Override
   public void initDefaultFormat() {
      info.initDefaultFormat();
   }

   /**
    * Check if this data assembly only depends on selection assembly.
    * @return <tt>true</tt> if it is only changed by the selection assembly,
    * <tt>false</tt> otherwise.
    */
   @Override
   public boolean isStandalone() {
      return false;
   }

   // end of assembly apis

   /**
    * Layout the sheet. Any overlapping assemblies are moved.
    * @return the names of the assemblies relocated.
    */
   @Override
   public Assembly[] layout() {
      return layout(new String[0]);
   }

   /**
    * Layout the sheet. Any overlapping assemblies are moved.
    * @param moved the manual moved assemblies.
    * @return the names of the assemblies relocated.
    */
   public synchronized Assembly[] layout(String[] moved) {
      List<Assembly> list = new ArrayList<>();

      for(Assembly assembly : getAssemblies()) {
         if(assembly.getAssemblyType() != VIEWSHEET_ASSET &&
            !(assembly instanceof ContainerVSAssembly))
         {
            continue;
         }

         Assembly[] arr;

         if(assembly instanceof Viewsheet) {
            Viewsheet tvs = (Viewsheet) assembly;
            arr = tvs.layout();
         }
         else {
            ContainerVSAssembly cassembly = (ContainerVSAssembly) assembly;
            arr = cassembly.layout();
         }

         if(arr != null) {
            Collections.addAll(list, arr);
         }

         list.add(assembly);
      }

      Assembly[] arr = super.layout(true);

      // fix line anchored positions
      for(Assembly assembly : getAssemblies()) {
         if(assembly instanceof LineVSAssembly) {
            if(((LineVSAssembly) assembly).updateAnchor(this)) {
               list.add(assembly);
            }
         }
      }

      for(Assembly assembly : arr) {
         list.add(assembly);

         // move contained assemblies
         if(assembly instanceof ContainerVSAssembly) {
            ContainerVSAssembly container = (ContainerVSAssembly) assembly;
            addChildren(container, list);
         }
         // move auto-moved viewsheet contained assemblies
         else if(assembly.getAssemblyType() == VIEWSHEET_ASSET) {
            Viewsheet vs = (Viewsheet) assembly;
            addRepositionedAssemblies(vs, list);
         }
      }

      // move manually moved viewsheet contained assemblies
      for(String movedName : moved) {
         Assembly assembly = getAssembly(movedName);

         if(assembly == null) {
            continue;
         }

         // move contained assemblies
         if(assembly instanceof ContainerVSAssembly) {
            ContainerVSAssembly container = (ContainerVSAssembly) assembly;
            addChildren(container, list);
         }
         else if(assembly.getAssemblyType() == VIEWSHEET_ASSET) {
            Viewsheet svs = (Viewsheet) assembly;
            addRepositionedAssemblies(svs, list);
         }
      }

      arr = new Assembly[list.size()];
      list.toArray(arr);

      resetCorners();

      if(isEmbedded()) {
         info.setPixelSize(getSize0());
      }

      return arr;
   }

   /**
    * Add the container's children to list.
    */
   private void addChildren(ContainerVSAssembly container, List<Assembly> list) {
      String[] children = container.getAssemblies();

      for(String childName : children) {
         Assembly child = getAssembly(childName);

         if(child instanceof ContainerVSAssembly) {
            addChildren((ContainerVSAssembly) child, list);
         }

         if(child.isVisible() || !isEmbedded()) {
            list.add(child);
         }
      }
   }

   /**
    * Add repositioned assemblies when laying out a viewsheet.
    * @param vs the specified viewsheet.
    * @param list the specified list.
    */
   private void addRepositionedAssemblies(Viewsheet vs, List<Assembly> list) {
      for(Assembly assembly : vs.getAssemblies()) {
         if(!list.contains(assembly)) {
            list.add(assembly);
         }

         if(assembly.getAssemblyType() == VIEWSHEET_ASSET) {
            Viewsheet svs = (Viewsheet) assembly;
            addRepositionedAssemblies(svs, list);
         }
      }
   }

   /**
    * Calculate the upper-left and bottom-right corner of the viewsheet.
    */
   private synchronized void resetCorners() {
      upperLeft = null;
      bottomRight = null;
      upperLeftPixel = null;
      bottomRightPixel = null;

      if(!isEmbedded()) {
         return;
      }

      Point nupperLeft = null;
      Point nupperLeftPixel = new Point(0, 0);
      List<String> tips = null;
      List<String> pops = null;

      // we need to calculate upperLeft before calling getGridSize since the
      // float assemblies may need to know the upperLeft to calculate the
      // grid size from pixel size
      for(Assembly assemblyItem : getAssemblies()) {
         VSAssembly assembly = (VSAssembly) assemblyItem;

         // here using primary visible seems more reasonable
         if(!assembly.isPrimary() ||
            assembly.getContainer() instanceof CurrentSelectionVSAssembly ||
            assembly.getContainer() != null && !assembly.getContainer().isPrimary())
         {
            continue;
         }

         if(isEmbedded()) {
            if(tips == null) {
               tips = getDataTips();
            }

            if(pops == null) {
               pops = getPopComponents();
            }

            if(tips.contains(assembly.getName()) || pops.contains(assembly.getName())) {
               continue;
            }
         }

         AssemblyInfo ainfo = assembly.getInfo();

         if(ainfo instanceof SelectionVSAssemblyInfo &&
            ((SelectionVSAssemblyInfo) ainfo).isCreatedByAdhoc())
         {
            continue;
         }

         Point offset = ainfo.getPixelOffset();
         offset = offset == null ? new Point(0, 0) : offset;

         if(nupperLeft == null) {
            nupperLeft = (Point) offset.clone();
            nupperLeftPixel = (Point) offset.clone();
         }
         else {
            nupperLeft.x = Math.min(nupperLeft.x, offset.x);
            nupperLeft.y = Math.min(nupperLeft.y, offset.y);
            nupperLeftPixel.x = Math.min(nupperLeftPixel.x, offset.x);
            nupperLeftPixel.y = Math.min(nupperLeftPixel.y, offset.y);
         }
      }

      if(nupperLeft == null) {
         nupperLeft = new Point(0, 0);
      }

      Point nbottomRight = null;
      Point nbottomRightPixel= new Point(0, 0);

      for(Assembly assemblyItem : getAssemblies()) {
         VSAssembly assembly = (VSAssembly) assemblyItem;

         // here using primary visible seems more reasonable
         if(!assembly.isPrimary() ||
            assembly.getContainer() instanceof CurrentSelectionVSAssembly)
         {
            continue;
         }

         if(isEmbedded()) {
            if(tips == null) {
               tips = getDataTips();
            }

            if(pops == null) {
               pops = getPopComponents();
            }

            if(tips.contains(assembly.getName()) || pops.contains(assembly.getName())) {
               continue;
            }
         }

         AssemblyInfo ainfo = assembly.getInfo();

         if(ainfo instanceof SelectionVSAssemblyInfo &&
            ((SelectionVSAssemblyInfo) ainfo).isCreatedByAdhoc())
         {
            continue;
         }

         // @by davyc, the upperLeft are relative to current viewsheet,
         // so bottomRight should also relative to current viewsheet, and
         // in updateGridSize, the viewsheet's pixel size caculation are
         // also used by the viewsheet self grid to caculate, FloatableHelper
         // changed too, see bug1244455900824
         Dimension size = ainfo.getPixelSize();
         Point pos = ainfo.getPixelOffset();
         Point pixelPos = new Point(pos.x + size.width, pos.y + size.height);

         if(nbottomRight == null) {
            nbottomRight = new Point(pos.x + size.width, pos.y + size.height);
            nbottomRightPixel = pixelPos;
         }
         else {
            nbottomRight.x = Math.max(nbottomRight.x, pos.x + size.width);
            nbottomRight.y = Math.max(nbottomRight.y, pos.y + size.height);
            nbottomRightPixel.x = Math.max(nbottomRightPixel.x, pixelPos.x);
            nbottomRightPixel.y = Math.max(nbottomRightPixel.y, pixelPos.y);
         }
      }

      if(nbottomRight == null) {
         nbottomRight = new Point(AssetUtil.defw, AssetUtil.defh);
      }

      // @by larryl, this could be reset in getGridSize
      upperLeft = nupperLeft;
      bottomRight = nbottomRight;
      upperLeftPixel = nupperLeftPixel;
      bottomRightPixel = nbottomRightPixel;

      info.setAssemblyBounds(new Rectangle(upperLeft.x, upperLeft.y,
                                           bottomRight.x - upperLeft.x,
                                           bottomRight.y - upperLeft.y));
   }

   /**
    * Get all assemblies used as data tips.
    */
   public synchronized List<String> getDataTips() {
      return Arrays.stream(getAssemblies())
         .map(Assembly::getInfo)
         .filter(obj -> obj instanceof TipVSAssemblyInfo)
         .map(obj -> ((TipVSAssemblyInfo) obj).getTipView())
         .filter(Objects::nonNull)
         .collect(Collectors.toList());
   }

   /**
    * Get all assemblies used as flyover views.
    */
   public synchronized List<String> getFlyoverViews() {
      List<String> allFlyoverViews = new ArrayList<>();

      for(Assembly assembly : getAssemblies()) {
         if(!(assembly.getInfo() instanceof TipVSAssemblyInfo)) {
            continue;
         }

         TipVSAssemblyInfo info = (TipVSAssemblyInfo) assembly.getInfo();
         String[] flyoverViews = info.getFlyoverViews();

         if(flyoverViews != null && flyoverViews.length > 0) {
            Arrays.stream(flyoverViews).forEach((flyoverView) -> allFlyoverViews.add(flyoverView));
         }
      }

      return allFlyoverViews;
   }

   /**
    * Get all assemblies used as pop components.
    */
   public synchronized List<String> getPopComponents() {
      return Arrays.stream(getAssemblies())
         .map(Assembly::getInfo)
         .filter(obj -> obj instanceof PopVSAssemblyInfo)
         .map(obj -> ((PopVSAssemblyInfo) obj).getPopComponent())
         .filter(Objects::nonNull)
         .collect(Collectors.toList());
   }

   /**
    * Get the gap between two assemblies.
    * @return the gap between two assemblies.
    */
   @Override
   protected int getGap() {
      return 0;
   }

   /**
    * Get the pixel bounds of an assembly.
    * @param assembly the specified assembly.
    * @return the layout bounds of the assembly.
    */
   @Override
   protected Rectangle getActualBounds(Assembly assembly) {
      Rectangle rect = getLayoutBounds(assembly);

      if(assembly instanceof Viewsheet) {
         Viewsheet avs = (Viewsheet) assembly;
         return avs.getChildrenBounds(rect.x, rect.y) == null ?
            rect : avs.getChildrenBounds(rect.x, rect.y);
      }

      return rect;
   }

   /**
    * Get the layout bounds of an assembly.
    * @param assembly the specified assembly.
    * @return the layout bounds of the assembly.
    */
   @Override
   protected Rectangle getLayoutBounds(Assembly assembly) {
      if(assembly.getAssemblyType() != TAB_ASSET) {
         return super.getLayoutBounds(assembly);
      }

      TabVSAssembly tab = (TabVSAssembly) assembly;
      Rectangle obounds = tab.getBounds();
      Rectangle bounds = (Rectangle) obounds.clone();
      String[] children = tab.getAssemblies();

      for(String childName : children) {
         Assembly child = getAssembly(childName);
         Rectangle cbounds = child.getBounds();

         bounds.width = Math.max(bounds.width, cbounds.width);
         bounds.height = Math.max(bounds.height, cbounds.height + obounds.height);
      }

      return bounds;
   }

   /**
    * Move one assembly in y dimension.
    * @param a the specified assembly a.
    * @param b the specified assembly b to move.
    * @param vonly true if vertical movement only, false both directions are ok.
    * @return the move result.
    */
   @Override
   protected int moveY(Assembly a, Assembly b, boolean vonly) {
      Rectangle abounds = getLayoutBounds(a);
      Rectangle aabounds = getActualBounds(a);
      abounds.height += getGap() * AssetUtil.defh; // at least one gap

      Rectangle bbounds = getLayoutBounds(b);
      Rectangle babounds = getActualBounds(b);
      bbounds.x -= getGap() * AssetUtil.defw; // at least one gap
      bbounds.width += 2 * getGap() * AssetUtil.defw; // at least one gap
      Rectangle ibounds = aabounds.intersection(babounds);
      int moved = NO_MOVEMENT;

      if(!ibounds.isEmpty()) {
         // For viewsheet now vertical only is true.
         if(vonly) {
            int x0 = bbounds.x + (getGap() * AssetUtil.defw);
            int y0 = abounds.y + abounds.height;
            b.setPixelOffset(new Point(x0, y0));
            moved = Y_MOVEMENT;
         }
      }

      return moved;
   }

   /**
    * Check if the assembly is visible.
    * @param name the specified assembly name.
    * @param mode the specified mode.
    * @return <tt>true</tt> if visible, <tt>false</tt> otherwise.
    */
   public boolean isVisible(String name, int mode) {
      if(name == null) {
         return false;
      }

      int index = name.indexOf('.');

      if(index >= 0) {
         Viewsheet vs = (Viewsheet) getAssembly(name.substring(0, index));
         return vs != null && vs.isVisible(name.substring(index + 1), mode);
      }

      Assembly assembly = getAssembly(name);
      return isVisible(assembly, mode);
   }

   /**
    * Check if is visible.
    * @param assembly the specified assembly.
    * @param mode the specified mode.
    * @return <tt>true</tt> if visible when layout, <tt>false</tt> otherwise.
    */
   public boolean isVisible(Assembly assembly, int mode) {
      VSAssembly vassembly = (VSAssembly) assembly;
      Viewsheet pvs = vassembly.getViewsheet();

      if(pvs != this) {
         return pvs.isVisible(assembly, mode);
      }

      // if parent viewsheet is not visible, whole is not visible
      // fix bug1255498865782
      if(getViewsheet() != null && !getViewsheet().isVisible(this, mode)) {
         return false;
      }

      // self visible
      boolean svis = (mode == SHEET_DESIGN_MODE &&
                      (assembly instanceof Viewsheet || !vassembly.isEmbedded() ||
                       vassembly.isPrimary() && vassembly.getInfo().isVisible()))
         || assembly.isVisible();
      ContainerVSAssembly tassembly =
         (ContainerVSAssembly) vassembly.getContainer();

      if(tassembly instanceof TabVSAssembly &&
         (mode == SHEET_RUNTIME_MODE || tassembly.isEmbedded()))
      {
         if(!isVisible(tassembly,mode)) {
            return false;
         }

         TabVSAssembly tab = (TabVSAssembly) tassembly;
         TabVSAssemblyInfo tinfo = (TabVSAssemblyInfo) tab.getInfo().clone();

         VSUtil.fixSelected(tinfo, this);

         int idx0 = -1;
         int selectedIdx = -1;
         String[] arr = tab.getAssemblies();
         int visChildren = 0;

         for(int i = 0; assembly.getInfo().isVisible() && i < arr.length; i++) {
            Assembly ass = getAssembly(arr[i]);

            if(ass != null) {
               if(ass == assembly) {
                  idx0 = i;
               }

               if(ass.isVisible()) {
                  visChildren++;
               }
               else if(ass.getAbsoluteName().equals(tinfo.getSelected())) {
                  selectedIdx = i;
               }
            }
         }

         return svis && (assembly.getName().equals(tinfo.getSelected()) ||
                         visChildren == 1 ||
                         selectedIdx != -1 && (selectedIdx + 1 == idx0 ||
                                               idx0 + 1 == selectedIdx &&
                                               selectedIdx + 1 == arr.length));
      }
      else if(tassembly != null) {
         return isVisible(tassembly, mode) && svis;
      }

      return svis;
   }

   /**
    * Check if is visible when layout.
    * @param assembly the specified assembly.
    * @return <tt>true</tt> if visible when layout, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean isLayoutVisible(Assembly assembly) {
      VSAssembly vassembly = (VSAssembly) assembly;

      if(assembly instanceof FloatableVSAssembly || assembly instanceof Viewsheet) {
         return false;
      }

      VSAssemblyInfo info = vassembly.getVSAssemblyInfo();

      if((info instanceof SelectionListVSAssemblyInfo &&
         ((SelectionListVSAssemblyInfo) info).isAdhocFilter()) ||
         (info instanceof TimeSliderVSAssemblyInfo &&
         ((TimeSliderVSAssemblyInfo) info).isAdhocFilter()))
      {
         return false;
      }

      VSAssembly container = vassembly.getContainer();
      return container == null && super.isLayoutVisible(assembly);
   }

   /**
    * Check if contains an assembly.
    * @param name the specified assembly name.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   @Override
   public boolean containsAssembly(String name) {
      return name != null && getAssembly(name) != null;
   }

   /**
    * Check if contains an assembly.
    * @param assembly the specified assembly.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean containsAssembly(VSAssembly assembly) {
      return assembly != null && containsAssembly(assembly.getAbsoluteName());
   }

   /**
    * Get an assembly by its entry.
    * @param entry the specified assembly entry.
    * @return the assembly, <tt>null</tt> if not found.
    */
   @Override
   public Assembly getAssembly(AssemblyEntry entry) {
      if(entry.isWSAssembly()) {
         return ws == null ? null : ws.getAssembly(entry.getName());
      }

      return getAssembly(entry.getName());
   }

   /**
    * Get an assembly by its name.
    * @param name the specified assembly name.
    * @return the assembly, <tt>null</tt> if not found.
    */
   @Override
   public VSAssembly getAssembly(String name) {
      if(name == null) {
         return null;
      }

      int index = name.indexOf('.');

      if(index >= 0) {
         Viewsheet vs = null;
         boolean currentVs;
         String vsName = name.substring(0, index);
         String childName = name.substring(index + 1);
         int index2 = childName.indexOf('.');

         if(vsName.equals(this.getAbsoluteName())) {
            vs = this;
            currentVs = true;
         }
         else {
            vs = (Viewsheet) getAssembly(vsName);
            currentVs = false;
         }

         if(vs == null) {
            return null;
         }

         if(index2 >= 0 && childName.substring(0, index2).equals(vs.getAbsoluteName())) {
            // If the next direct vs child has the same name as the current vs
            // Get the next vs by using the direct child's absolute name
            String childAbsoluteName = name.substring(0, index + index2 + 1);
            vs = (Viewsheet) vs.getAssembly(childAbsoluteName);
            childName = name.substring(index + index2 + 2);
            currentVs = false;
         }

         VSAssembly vsAssembly = vs.getAssembly(childName);

         if(vsAssembly != null) {
            return vsAssembly;
         }

         // try to get assembly in current vs, if do not find,
         // then to search in sub vs when the current viewsheet name is equals to sub vs name.
         if(currentVs) {
            vs = (Viewsheet) getAssembly(vsName);

            if(vs != null) {
               return vs.getAssembly(name.substring(index + 1));
            }
         }

         return null;
      }

      // @by larryl, optimization, getAssembly() may be called many times
      // from script iterator

      Map<String, VSAssembly> amap = this.amap;

      if(amap == null) {
         synchronized(this) {
            amapLock.lock();

            try {
               amap = this.amap;

               if(amap == null) {
                  amap = new ConcurrentHashMap<>();

                  for(VSAssembly assembly : assemblies) {
                     if(amap.containsKey(assembly.getName())) {
                        LOG.error("Duplicate assembly detected [" + assembly.getName() + "]");
                     }

                     amap.put(assembly.getName(), assembly);
                  }

                  this.amap = amap;
               }
            }
            finally {
               amapLock.unlock();
            }
         }
      }

      VSAssembly vsobj = amap.get(name);

      // since assembly name is mutable, the amap could be out of sync if the assembly is
      // renamed without calling the Viewsheet.rename...
      // this is a safety check to make sure if amap is out of sync, we can find it on the
      // list. in most case getAssembly() is not expected to find a miss.
      // ScriptIterator may trigger this on every token so miss hit is expected
      if(vsobj == null && !ScriptIterator.isProcessing()) {
         List<VSAssembly> assemblies;
         amapLock.lock();

         // make a copy since assemblies modifications are protected in amapLock. this
         // makes sure the list isn't changed during iteration
         try {
            assemblies = Arrays.asList(this.assemblies.toArray(new VSAssembly[0]));
         }
         finally {
            amapLock.unlock();
         }

         for(VSAssembly assembly : assemblies) {
            if(name.equals(assembly.getName())) {
               vsobj = assembly;
               break;
            }
         }

         if(vsobj != null) {
            clearCache();
         }
         else if("true".equals(SreeEnv.getProperty("debug.viewsheet"))) {
            String msg = "Assembly: " + name + " in " +
               assemblies.stream().map(Assembly::getName).collect(Collectors.joining(","))
               + " at: " + new Timestamp(System.currentTimeMillis());
            LogbackTraceAppender.captureStackTraces(msg, rmStacks.get(name));
         }
      }

      return vsobj;
   }

   /**
    * Get an assembly by its position. If the assembly occupies the cell,
    * return itself.
    * @param r the specified row.
    * @param c the specified col.
    * @return the assembly, <tt>null</tt> if not found.
    */
   public synchronized Assembly getAssembly(int r, int c) {
      for(Assembly assembly : getAssemblies()) {
         Rectangle bounds = assembly.getBounds();

         if(bounds.contains(r, c)) {
            return assembly;
         }
      }

      return null;
   }

   /**
    * Get all the assemblies.
    * @return all the assemblies.
    */
   @Override
   public Assembly[] getAssemblies() {
      return getAssemblies(false);
   }

   /**
    * Get all the assemblies.
    * @return all the assemblies.
    */
   public Assembly[] getAssemblies(boolean recursive) {
      return getAssemblies(recursive, false);
   }

   /**
    * Get all the assemblies.
    * @return all the assemblies.
    */
   public Assembly[] getAssemblies(boolean recursive, boolean sort) {
      return getAssemblies(recursive, sort, false);
   }

   public Assembly[] getAssemblies(boolean recursive, boolean sort,
                                   boolean includeWizardExpiredTemp)
   {
      return getAssemblies(recursive, sort, true, includeWizardExpiredTemp);
   }

   /**
    *
    * @param recursive
    * @param sort
    * @param includeWizardTemp if to include temp assemblies which temporarily created in wizard.
    * @param includeWizardExpiredTemp if to include the expired recommended assemblies in wizard.
    * @return
    */
   public Assembly[] getAssemblies(boolean recursive, boolean sort,
                                   boolean includeWizardTemp,
                                   boolean includeWizardExpiredTemp)
   {
      return getAssemblies(recursive, sort, includeWizardTemp, includeWizardExpiredTemp, false);
   }

   /**
    *
    * @param recursive
    * @param sort
    * @param includeWizardTemp if to include temp assemblies which temporarily created in wizard.
    * @param includeWizardExpiredTemp if to include the expired recommended assemblies in wizard.
    * @param includeWarningText if to include the warning text.
    * @return
    */
   public Assembly[] getAssemblies(boolean recursive, boolean sort,
                                   boolean includeWizardTemp,
                                   boolean includeWizardExpiredTemp,
                                   boolean includeWarningText)
   {
      List<VSAssembly> list;

      synchronized(this) {
         if(!recursive) {
            if(includeWizardExpiredTemp) {
               list = assemblies;
            }
            else {
               VSAssembly lastedTemp = getLatestTempAssembly();

               list = assemblies.stream().filter(assembly -> {
                  String name = assembly.getAbsoluteName();

                  if(!includeWizardTemp && WizardRecommenderUtil.isWizardTempAssembly(name)) {
                     return false;
                  }

                  if(!includeWarningText && VS_WARNING_TEXT.equals(name)) {
                     return false;
                  }

                  boolean temp = WizardRecommenderUtil.isTempAssembly(name);

                  if(temp && assembly != lastedTemp) {
                     return false;
                  }

                  return true;
               }).collect(Collectors.toList());
            }
         }
         else {
            list = new ArrayList<>();
            getAssemblies(list, includeWizardTemp, includeWizardExpiredTemp);
         }
      }

      if(sort) {
         list.sort(new ZIndexComparator(recursive, this));
      }

      return list.toArray(new Assembly[0]);
   }

   /**
    * Get the assemblies recursively.
    * @param list the specified assembly container.
    */
   private synchronized void getAssemblies(List<VSAssembly> list,
                                           boolean includeWizardTemp,
                                           boolean includeWizardExpiredTemp)
   {
      VSAssembly lastedTemp = getLatestTempAssembly();

      for(VSAssembly assembly : assemblies) {
         if(!(assembly instanceof Viewsheet)) {
            String name = assembly.getAbsoluteName();

            if(!includeWizardTemp && WizardRecommenderUtil.isWizardTempAssembly(name)) {
               continue;
            }

            boolean temp = WizardRecommenderUtil.isTempAssembly(name);

            if(!includeWizardExpiredTemp && temp && assembly != lastedTemp) {
               continue;
            }

            list.add(assembly);
         }
         else {
            ((Viewsheet) assembly).getAssemblies(list, includeWizardTemp, includeWizardExpiredTemp);
         }
      }
   }

   public synchronized VSAssembly getLatestTempAssembly() {
      return latestTemp;
   }

   public Stream<SelectionVSAssembly> getSelectionAssemblyStream() {
      return Arrays.stream(getAssemblies())
         .filter(SelectionVSAssembly.class::isInstance)
         .map(SelectionVSAssembly.class::cast);
   }

   /**
    * Add an assembly.
    * @param assembly the specified assembly.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   public boolean addAssembly(VSAssembly assembly) {
      return addAssembly(assembly, true);
   }

   /**
    * Add an assembly.
    * @param assembly the specified assembly.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   public boolean addAssembly(VSAssembly assembly, boolean adjust) {
      return addAssembly(assembly, adjust, true);
   }

   public boolean addAssembly(VSAssembly assembly, boolean adjust, boolean fireEvent) {
      return addAssembly(assembly, adjust, fireEvent, true);
   }

   public boolean addAssembly(VSAssembly assembly, boolean adjust, boolean fireEvent,
                              boolean refreshLatestTemp)
   {
      String name = assembly.getName();
      boolean contained = false;

      synchronized(this) {
         if(WizardRecommenderUtil.isTempAssembly(name) && refreshLatestTemp) {
            latestTemp = assembly;
         }

         amapLock.lock();

         try {
            for(int i = 0; i < assemblies.size(); i++) {
               if(name.equals(assemblies.get(i).getName())) {
                  contained = true;
                  assemblies.remove(i);
                  break;
               }
            }

            clearCache(); // clear cached mapping
            assembly.setViewsheet(this);

            if(adjust) {
               adjustAssemblyZIndex(assembly);
            }

            updateCSSFormat(null, assembly);
            this.assemblies.add(assembly);
         }
         finally {
            amapLock.unlock();
         }
      }

      if(fireEvent && !contained && isFireEvent()) {
         fireEvent(ADD_ASSEMBLY, name);
      }

      return true;
   }

   /**
    * Adjust vs assembly's z-index.
    */
   private void adjustAssemblyZIndex(VSAssembly assembly) {
      // max zindx + gap as default
      Assembly[] assemblies = getAssemblies(false, true, false);
      VSAssembly massembly = assemblies.length <= 0 ? null :
         (VSAssembly) assemblies[assemblies.length - 1];
      int zindex = massembly == null ? 0 :
         massembly.getZIndex() + VSUtil.getZIndexGap(massembly);
      assembly.setZIndex(zindex);
   }

   /**
    * Calculate the sub component z index.
    */
   public void calcChildZIndex() {
      Assembly[] assemblies = getAssemblies(false, false, false);
      List<Assembly> firstLevel = new ArrayList<>();

      for(Assembly assembly : assemblies) {
         if(assembly instanceof VSAssembly && ((VSAssembly) assembly).getContainer() == null) {
            firstLevel.add(assembly);
         }
      }

      assemblies = new Assembly[firstLevel.size()];
      firstLevel.toArray(assemblies);
      VSUtil.calcChildZIndex(assemblies, getZIndex());

      for(Assembly assembly : assemblies) {
         if(assembly instanceof ContainerVSAssembly) {
            ((ContainerVSAssembly) assembly).calcChildZIndex();
         }
      }
   }

   /**
    * Remove an assembly.
    * @param name the specified assembly to be removed.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   public boolean removeAssembly(String name) {
      return removeAssembly(name, true);
   }

   public boolean removeAssembly(String name, boolean fireEvent) {
      return removeAssembly(name, fireEvent, false);
   }

   /**
    * emove an assembly.
    * @param name     the specified assembly to be removed.
    * @param fireEvent if need to fire event.
    * @param ignoreLayout don't refresh layout if true (for vs wizard), else refresh layout.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   public boolean removeAssembly(String name, boolean fireEvent, boolean ignoreLayout) {
      int index = name.indexOf('.');

      if(index >= 0) {
         Viewsheet vs = (Viewsheet) getAssembly(name.substring(0, index));
         return vs != null && vs.removeAssembly(name.substring(index + 1));
      }

      String removed = null;

      synchronized(this) {
         for(Iterator<VSAssembly> i = assemblies.iterator(); i.hasNext();) {
            VSAssembly assembly = i.next();

            if(assembly.getName().equals(name)) {
               if(latestTemp == assembly) {
                  latestTemp = null;
               }

               amapLock.lock();

               try {
                  clearCache(); // clear cached mapping
                  i.remove();
               }
               finally {
                  amapLock.unlock();
               }

               removed = name;
               break;
            }
         }
      }

      if("true".equals(SreeEnv.getProperty("debug.viewsheet"))) {
         rmStacks.put(name, new LogbackTraceAppender.StackRecord());
      }

      // fire outside of synchronized to avoid deadlock
      if(fireEvent && removed != null) {
         fireEvent(REMOVE_ASSEMBLY, removed);
      }

      if(!ignoreLayout) {
         removeLayout(layoutInfo.getPrintLayout(), name);
         layoutInfo.getViewsheetLayouts().forEach(v -> removeLayout(v, name));
      }

      if(removed != null) {
         DateCompareAbleAssemblyInfo.cleanShareDependencies(removed, this);
      }

      return removed != null;
   }

   private void removeLayout(AbstractLayout layout, String name) {
      if(layout != null && layout.getVSAssemblyLayouts() != null) {
         layout.setVSAssemblyLayouts(layout.getVSAssemblyLayouts().stream()
                                     .filter(v -> !name.equals(v.getName()))
                                     .collect(Collectors.toList()));
      }

      if(layout instanceof PrintLayout) {
         PrintLayout printLayout = (PrintLayout) layout;

         if(printLayout.getHeaderLayouts() != null) {
            printLayout.setHeaderLayouts(printLayout.getHeaderLayouts().stream()
                                            .filter(v -> !name.equals(v.getName()) ||
                                               v instanceof VSEditableAssemblyLayout)
                                            .collect(Collectors.toList()));
         }

         if(printLayout.getFooterLayouts() != null) {
            printLayout.setFooterLayouts(printLayout.getFooterLayouts().stream()
                                            .filter(v -> !name.equals(v.getName()) ||
                                               v instanceof VSEditableAssemblyLayout)
                                            .collect(Collectors.toList()));
         }
      }
   }

   /**
    * Remove an assembly.
    * @param assembly the specified assembly to be removed.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   public boolean removeAssembly(VSAssembly assembly) {
      return removeAssembly(assembly.getAbsoluteName());
   }

   /**
    * Rename an assembly.
    * @param oname the specified assembly to be renamed.
    * @param nname the specified new assembly name.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   public boolean renameAssembly(String oname, String nname) {
      if(!containsAssembly(oname) || containsAssembly(nname)) {
         return false;
      }

      synchronized(this) {
         VSAssembly oassembly = getAssembly(oname);

         amapLock.lock();

         try {
            if(oassembly instanceof AbstractVSAssembly) {
               ((AbstractVSAssembly) oassembly).setName(nname);
            }
            else {
               ((Viewsheet) oassembly).setName(nname);
            }

            clearCache(); // clear cached mapping
         }
         finally {
            amapLock.unlock();
         }

         renameDepended(oname, nname);

         for(Assembly assemblyItem : assemblies) {
            VSAssembly assembly = (VSAssembly) assemblyItem;

            if(assembly.isEmbedded()) {
               continue;
            }

            assembly.renameDepended(oname, nname);
         }

         renameLayout(layoutInfo.getPrintLayout(), oname, nname);
         layoutInfo.getViewsheetLayouts().forEach(v -> renameLayout(v, oname, nname));
      }

      vinfo.renameLocalID(oname, nname);
      fireEvent(RENAME_ASSEMBLY, oname + "^" + nname);
      return true;
   }

   private void renameLayout(AbstractLayout layout, String oname, String nname) {
      if(layout != null && layout.getVSAssemblyLayouts() != null) {
         layout.getVSAssemblyLayouts().forEach(v -> {
               if(oname.equals(v.getName())) {
                  v.setName(nname);
               }
            });
      }

      if(layout instanceof PrintLayout) {
         renameHeaderOrFooterLayout(((PrintLayout) layout).getHeaderLayouts(), oname, nname);
         renameHeaderOrFooterLayout(((PrintLayout) layout).getFooterLayouts(), oname, nname);
      }
   }

   private void renameHeaderOrFooterLayout(List<VSAssemblyLayout> assemblyLayouts,
                                           String oname, String nname)
   {
      if(assemblyLayouts != null) {
         assemblyLayouts.forEach(v -> {
            if(!(v instanceof VSEditableAssemblyLayout) && oname.equals(v.getName())) {
               v.setName(nname);
            }
         });
      }
   }

   /**
    * Get the viewsheet info.
    * @return the viewsheet info of this viewsheet.
    */
   public ViewsheetInfo getViewsheetInfo() {
      return vinfo;
   }

   /**
    * Set the viewsheet info.
    * @param vinfo the specified viewsheet info.
    */
   public void setViewsheetInfo(ViewsheetInfo vinfo) {
      this.vinfo = vinfo;
   }

   /**
    * Get the layout info.
    * @return the layout info of this viewsheet.
    */
   public LayoutInfo getLayoutInfo() {
      return layoutInfo;
   }

   /**
    * Set the layout info.
    * @param layoutInfo the specified layout info.
    */
   public void setLayoutInfo(LayoutInfo layoutInfo) {
      this.layoutInfo = layoutInfo;
   }

   /**
    * Get original viewsheet of the viewsheet which applied vslayout.
    */
   public Viewsheet getOriginalVs() {
      return originalVs;
   }

   /**
    * Set original viewsheet of the viewsheet which applied vslayout.
    */
   public void setOriginalVs(Viewsheet originalVs) {
      this.originalVs = originalVs;
   }

   /**
    * Get the uploaded image names.
    */
   public String[] getUploadedImageNames() {
      Set<String> keys = imgmap.keySet();
      String[] names = new String[keys.size()];
      keys.toArray(names);
      Arrays.sort(names);

      return names;
   }

   /**
    * Get the uploaded image bytes.
    * @param name the image name.
    */
   public byte[] getUploadedImageBytes(String name) {
      return imgmap.get(name);
   }

   /**
    * Add the uploaded images.
    */
   public void addUploadedImage(String name, byte[] image) {
      imgmap.put(name, image);
   }

   /**
    * Remove the uploaded images.
    * @param name the image name.
    */
   public boolean removeUploadedImage(String name) {
      Object result = imgmap.remove(name);
      return result != null;
   }

   /**
    * Get a map of dimension value -> color for a column
    *
    * @param column the name of the color column
    * @return a new map that contains the fixed mappings for the specified column
    */
   public Map<String, Color> getDimensionColors(String column) {
      // add delimiter at the end so that we don't match the wrong column
      final String columnNameKey = getAttribute(column, false) + COLUMN_DELIMITER;

      final Function<Map.Entry<String, Color>, String> keyParser = entry -> {
         final String key = entry.getKey();
         return key.substring(key.indexOf(COLUMN_DELIMITER) + COLUMN_DELIMITER.length());
      };

      Map<String, Color> map = dimensionColors.entrySet()
         .stream()
         .filter(entry -> entry.getKey().startsWith(columnNameKey))
         .collect(Collectors.toMap(keyParser, Map.Entry::getValue));

      // try the old column name which may have the date part missing if the name contains ':'
      if(map.isEmpty()) {
         final String oldColumnNameKey = getAttribute(column, true) + COLUMN_DELIMITER;
         map = dimensionColors.entrySet()
            .stream()
            .filter(entry -> entry.getKey().startsWith(oldColumnNameKey))
            .collect(Collectors.toMap(keyParser, Map.Entry::getValue));
      }

      return map;
   }

   /**
    * Store the dimension value color mapping with the column name encoded
    *
    * @param column the name of the column that contains the dimension values
    * @param dimensionColors the dimension value -> color map
    */
   public void setDimensionColors(String column, Map<String, Color> dimensionColors) {
      final String columnNameKey = getAttribute(column, false) + COLUMN_DELIMITER;

      final Function<Map.Entry<String, Color>, String> mapKey =
         entry -> columnNameKey + entry.getKey();

      // remove the old mappings for this column
      this.dimensionColors.entrySet().removeIf((entry) -> entry.getKey().startsWith(columnNameKey));

      // add the mappings from the passed in map
      this.dimensionColors.putAll(dimensionColors.entrySet().stream()
                                     .filter(entry -> entry.getValue() != null)
                                     .collect(Collectors.toMap(mapKey, Map.Entry::getValue)));
   }

   /**
    * Copy the dimension color mapping from specified viewsheeet.
    */
   public void copyDimensionColors(Viewsheet vs) {
      this.dimensionColors.putAll(vs.dimensionColors);
   }

   /**
    * Strip the entity name from the column name e.g Product:Category -> Category
    *
    * @param column the name of the column that may contain a ':'
    * @param oldAttr the old attr logic will strip the date part when the column contains ':'
    *
    * @return the attribute name without the preceding entity and ':'
    */
   private String getAttribute(String column, boolean oldAttr) {
      int index = column.indexOf(":");

      if(index >= 0) {
         int indexP = column.indexOf("(");

         // The Year part in Year(Order:Date) gets cut off with a simple ':' check. This will
         // cause Year(Order:Date) and Month(Order:Date) to be treated as the same.
         // If there is no '(' then doing it like this is probably fine.
         if(oldAttr || indexP < 0 || indexP > index) {
            return column.substring(index + 1);
         }
         else {
            return column.substring(0, indexP + 1) + column.substring(index + 1);
         }
      }
      else {
         return column;
      }
   }

   public Map<SharedFrameParameters, VisualFrame> getSharedFrames() {
      return sharedFrames;
   }

   /**
    * Clear the shared color frames. This method should be called if the binding for color
    * changed or if the value of the color field will change on next refresh (e.g. changing
    * interval on date comparison).
    */
   public void clearSharedFrames() {
      sharedFrames.clear();
   }

   /**
    * Get the calculate field of one table.
    * @param name the table name.
    */
   public CalculateRef[] getCalcFields(String name) {
      List<CalculateRef> list = name != null ? calcmap.get(name) : null;

      if(list != null) {
         synchronized(list) {
            return list.toArray(new CalculateRef[0]);
         }
      }

      return null;
   }

   /**
    * Get the aggregate field of one table.
    * @param name the table name.
    */
   public AggregateRef[] getAggrFields(String name) {
      List<AggregateRef> list = aggregatemap.get(name);
      return list == null ? null : list.toArray(new AggregateRef[0]);
   }

   /**
    * Get the aggregate field of one table.
    */
   public AggregateRef[] getAOAAggrFields() {
      return aggrs == null ? null : aggrs.toArray(new AggregateRef[0]);
   }

   /**
    * Add the calculate ref.
    */
   public void addCalcField(String table, CalculateRef ref) {
      List<CalculateRef> calcs = calcmap.computeIfAbsent(table, k -> new ArrayList<>());

      synchronized(calcs) {
         // remove the old ref if exist
         while(calcs.contains(ref)) {
            calcs.remove(ref);
         }

         calcs.add(ref);
      }

      fireEvent(BINDING_CHANGED, getName());
   }

   /**
    * Add the aggregate ref.
    */
   public void addAggrField(String table, AggregateRef ref) {
      List<AggregateRef> aggrs = aggregatemap.computeIfAbsent(table, k -> new ArrayList<>());

      // remove the old ref if exist
      for(int i = aggrs.size() - 1; i >= 0 ; i--) {
         if(ref.equalsContent(aggrs.get(i))) {
            aggrs.remove(i);
            break;
         }
      }

      aggrs.add(ref);
   }

   /**
    * Check if the aoa aggregate ref is contained.
    */
   public boolean isAOARef(AggregateRef ref) {
      if(aggrs != null) {
         for(int i = aggrs.size() - 1; i >= 0 ; i--) {
            if(ref.equalsContent(aggrs.get(i))) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Add the aoa aggregate ref.
    */
   public void addAOARef(AggregateRef ref) {
      if(aggrs == null) {
         aggrs = new ArrayList<>();
      }

      // remove the old ref if exist
      for(int i = aggrs.size() - 1; i >= 0 ; i--) {
         if(ref.equalsContent(aggrs.get(i))) {
            aggrs.remove(i);
            break;
         }
      }

      aggrs.add(ref);
   }

   /**
    * Remove the user created calc field.
    * @param calc the calcfield name.
    */
   public boolean removeCalcField(String table, String calc) {
      List<CalculateRef> calcs = calcmap.get(table);
      boolean result = false;

      if(calcs != null) {
         synchronized(calcs) {
            CalculateRef target = new CalculateRef();
            target.setDataRef(new AttributeRef(calc));
            result = calcs.remove(target);
         }

         if(calcs.size() == 0) {
            calcmap.remove(table);
         }
      }

      if(result) {
         fireEvent(BINDING_CHANGED, getName());
      }

      return result;
   }

   /**
    * Remove the user create calc fields for the target source table.
    */
   public void removeCalcField(String table) {
      calcmap.remove(table);
   }

   /**
    * Get the user created calc field if exists.
    * @param table the table name from which to find the calcfield.
    * @param calc the calcfield name.
    */
   public CalculateRef getCalcField(String table, String calc) {
      List<CalculateRef> calcs = table != null ? calcmap.get(table) : null;

      if(calcs != null) {
         synchronized(calcs) {
            CalculateRef target = new CalculateRef();
            target.setDataRef(new AttributeRef(calc));
            int idx = calcs.indexOf(target);

            if(idx >= 0) {
               return calcs.get(idx);
            }
         }
      }

      return null;
   }

   /**
    * Get the source with calc fields defined.
    */
   public Collection<String> getCalcFieldSources() {
      return calcmap.keySet();
   }

   /**
    * Remove the user created aggregate field.
    * @param table the table name from which to find the aggrfield.
    * @param ref the aggrfield ref object to remove.
    */
   public boolean removeAggrField(String table, AggregateRef ref) {
      List<AggregateRef> aggrs = aggregatemap.get(table);
      boolean result = false;

      if(aggrs != null) {
         for(int i = aggrs.size() - 1; i >= 0 ; i--) {
            // remove ref if exist
            if(ref.equalsContent(aggrs.get(i))) {
               if(aggrs.remove(i) != null) {
                  result = true;
                  break;
               }
            }
         }

         if(aggrs.size() == 0) {
            aggregatemap.remove(table);
         }
      }

      return result;
   }

   /**
    * Remove the user created aggregate field.
    * @param table the table name from which to find the aggrfield.
    */
   public void removeAggrField(String table) {
      aggregatemap.remove(table);
   }

   /**
    * Get the aggr field sources
    */
   public Collection<String> getAggrFieldSources() {
      return aggregatemap.keySet();
   }

   /**
    * Get last modified.
    * @return last modified time.
    */
   @Override
   public synchronized long getLastModified(boolean recursive) {
      long modified = getLastModified();

      if(!recursive) {
         return modified;
      }

      for(Assembly assembly : assemblies) {
         if(!(assembly instanceof Viewsheet)) {
            continue;
         }

         modified = Math.max(modified, ((Viewsheet) assembly).getLastModified(true));
      }

      return modified;
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public synchronized void writeXML(PrintWriter writer) {
      if(!isEmbedded()) {
         shrink();
      }

      // avoid concurrent modification from auto-save
      // synchronization has a risk of deadlock. copying should be safe since no change is
      // made to assemblies in this method
      List<Assembly> assemblies = new Vector<>(Arrays.asList(getAssemblies()));

      writer.print("<assembly class=\"" + getClass().getName() + "\"" +
         " modified=\"" + getLastModified() + "\"");

      if(getLastModifiedBy() != null) {
         writer.print(" modifiedBy=\"" + byteEncode(getLastModifiedBy()) + "\"");
      }

      if(getCreatedBy() != null) {
         writer.print(" createdBy=\"" + byteEncode(getCreatedBy()) + "\"");
         writer.print(" created=\"" + getCreated() + "\"");
      }

      writer.print(">");
      writer.println("<Version>" + getVersion() + "</Version>");

      writer.println("<assemblies>");

      for(Assembly assembly : assemblies) {
         writer.println("<oneAssembly>");
         assembly.writeXML(writer);
         writer.println("</oneAssembly>");
      }

      writer.println("</assemblies>");

      writer.println("<dependencies>");

      for(AssetEntry entry : getOuterDependencies(true)) {
         entry.writeXML(writer);
      }

      writer.println("</dependencies>");

      if(ventry != null) {
         writer.print("<viewsheetEntry>");
         ventry.writeXML(writer);
         writer.println("</viewsheetEntry>");
      }

      if(wentry != null) {
         writer.print("<worksheetEntry>");
         wentry.writeXML(writer);
         writer.print("</worksheetEntry>");
      }

      writer.println("<assemblyInfo>");
      info.writeXML(writer);
      writer.println("</assemblyInfo>");

      writer.println("<viewsheetInfo>");
      vinfo.writeXML(writer);
      writer.println("</viewsheetInfo>");

      writer.println("<layoutInfo>");
      layoutInfo.writeXML(writer);
      writer.println("</layoutInfo>");

      // the root viewsheet, save bookmarks and images
      if(getViewsheet() == null) {
         List<Map.Entry<String, byte[]>> entries
            = VersionControlComparators.sortStringKeyMap(imgmap);

         for(Map.Entry<String, byte []> e : entries) {
            String key = e.getKey();
            byte[] bytes = e.getValue();
            String val = Encoder.encodeAsciiHex(bytes);

            writer.println("<embeddedImage>");
            writer.print("<name>");
            writer.print("<![CDATA[" + key + "]]>");
            writer.println("</name>");
            writer.print("<value>");
            writer.print("<![CDATA[" + val + "]]>");
            writer.println("</value>");
            writer.println("</embeddedImage>");
         }
      }

      List<Map.Entry<String, List<CalculateRef>>> calcEntryList
         = VersionControlComparators.sortStringKeyMap(calcmap);

      for(Map.Entry<String, List<CalculateRef>> entry : calcEntryList) {
         String key = entry.getKey();
         List<CalculateRef> calcs = entry.getValue();

         writer.println("<allcalc>");

         writer.print("<tname>");
         writer.print("<![CDATA[" + key + "]]>");
         writer.println("</tname>");
         writer.print("<values>");

         for(CalculateRef calc : calcs) {
            if(calc.isDcRuntime()) {
               continue;
            }

            writer.print("<value>");
            calc.writeXML(writer);
            //writer.print("<![CDATA[" +  + "]]>");
            writer.println("</value>");
         }

         writer.print("</values>");
         writer.println("</allcalc>");
      }

      writer.println("<dimensionColors>");

      List<Map.Entry<String, Color>> entries
         = VersionControlComparators.sortStringKeyMap(dimensionColors);

      for(Map.Entry<String, Color> dimensionColor : entries) {
         final String dimension = dimensionColor.getKey();
         final Color color = dimensionColor.getValue();
         writer.println("<dimensionColor>");
         writer.format("<dimension><![CDATA[%s]]></dimension>%n", dimension);
         writer.format("<color><![CDATA[%s]]></color>%n", Tool.colorToHTMLString(color));
         writer.println("</dimensionColor>");
      }

      writer.println("</dimensionColors>");


      List<Map.Entry<String, List<AggregateRef>>> aggEntries
         = VersionControlComparators.sortStringKeyMap(aggregatemap);

      for(Map.Entry<String, List<AggregateRef>> aggEntry : aggEntries) {
         String key = aggEntry.getKey();
         List<AggregateRef> calcs = aggEntry.getValue();

         writer.println("<allaggr>");

         writer.print("<tname>");
         writer.print("<![CDATA[" + key + "]]>");
         writer.println("</tname>");
         writer.print("<values>");

         for(AggregateRef calc : calcs) {
            writer.print("<value>");
            calc.writeXML(writer);
            writer.println("</value>");
         }

         writer.print("</values>");
         writer.println("</allaggr>");
      }

      writer.println("</assembly>");
   }

   /**
    * Method to parse an xml segment.
    * @param elem the specified xml element.
    */
   @Override
   public void parseXML(Element elem) throws Exception {
      // form 10.3 ViewsheetAsset begin to write "viewsheet" for root, so
      // VS10_2Transformer need to append "viewsheet" node, but when open
      // an old version viewsheet directly, we should not contain the viewsheet
      // node, so here to igonre it
      if("viewsheet".equals(elem.getNodeName())) {
         elem = Tool.getChildNodeByTagName(elem, "assembly");
      }

      Element asnode = Tool.getChildNodeByTagName(elem, "assemblies");
      NodeList list = Tool.getChildNodesByTagName(asnode, "oneAssembly");
      ScriptIterator.setProcessing(true);

      for(int i = 0; i < list.getLength(); i++) {
         Element onenode = (Element) list.item(i);
         Element anode = Tool.getChildNodeByTagName(onenode, "assembly");
         String cls = Tool.getAttribute(anode, "class");

         if("inetsoft.uql.viewsheet.MapVSAssembly".equals(cls)) {
            continue;
         }

         VSAssembly assembly =
            AbstractVSAssembly.createVSAssembly(anode, this);

         if(assembly == null) {
            continue;
         }

         // parse state content may have been added the assembly
         if(getAssembly(assembly.getName()) == null) {
            this.assemblies.add(assembly);
            // @by larryl, this shouldn't be necessary but I have seen cases
            // where the visibility (of tip view assemblies) being set to
            // false and saved to the xml so it's never visible again.
            // this is a safty and shouldn't cause problem since we never set
            // a vs assembly permanently invisible
            assembly.getInfo().setVisible(true);
            clearCache(); // clear cached mapping
         }
      }

      ScriptIterator.setProcessing(false);
      Element anode = Tool.getChildNodeByTagName(elem, "assemblyInfo");

      if(anode != null) {
         // for bc, pre 12.3 assemblyInfo node was wrapped in another assemblyInfo tag
         if(Tool.getChildNodeByTagName(anode, "assemblyInfo") != null) {
            anode = Tool.getChildNodeByTagName(anode, "assemblyInfo");
         }

         info.parseXML(anode);
      }

      if(!isEmbedded()) {
         shrink();
         NodeList inodes =
            Tool.getChildNodesByTagName(elem, "embeddedImage");

         for(int i = 0; i < inodes.getLength(); i++) {
            Element inode = (Element) inodes.item(i);
            Element nnode = Tool.getChildNodeByTagName(inode, "name");
            Element vnode = Tool.getChildNodeByTagName(inode, "value");
            String name = Tool.getValue(nnode);
            String val = Tool.getValue(vnode);
            byte[] bytes = Encoder.decodeAsciiHex(val);
            imgmap.put(name, bytes);
         }
      }

      NodeList inodes = Tool.getChildNodesByTagName(elem, "allcalc");

      for(int i = 0; i < inodes.getLength(); i++) {
         Element inode = (Element) inodes.item(i);
         Element nnode = Tool.getChildNodeByTagName(inode, "tname");
         String tname = Tool.getValue(nnode);
         Element vnode = Tool.getChildNodeByTagName(inode, "values");

         if(vnode != null) {
            NodeList cnode = Tool.getChildNodesByTagName(vnode, "value");
            List<CalculateRef> calcs = new ArrayList<>();

            for(int j = 0; j < cnode.getLength(); j++) {
               Element calcnode = (Element) cnode.item(j);
               CalculateRef cref =
                  (CalculateRef) AbstractDataRef.createDataRef(
                     Tool.getChildNodeByTagName(calcnode, "dataRef"));
               cref.setVisible(true);
               calcs.add(cref);
            }

            calcmap.put(tname, calcs);
         }
      }

      final Element dimensionColors = Tool.getChildNodeByTagName(elem, "dimensionColors");

      if(dimensionColors != null) {
         final NodeList dimensionColor =
            Tool.getChildNodesByTagName(dimensionColors, "dimensionColor");

         for(int i = 0; i < dimensionColor.getLength(); i++) {
            final Node entry = dimensionColor.item(i);
            final Element dimensionNode = Tool.getChildNodeByTagName(entry, "dimension");
            final Element colorNode = Tool.getChildNodeByTagName(entry, "color");
            final String dimension = Tool.getValue(dimensionNode);
            final String colorValue = Tool.getValue(colorNode);
            final Color color = Tool.getColorFromHexString(colorValue);
            this.dimensionColors.put(dimension, color);
         }
      }

      inodes = Tool.getChildNodesByTagName(elem, "allaggr");

      for(int i = 0; i < inodes.getLength(); i++) {
         Element inode = (Element) inodes.item(i);
         Element nnode = Tool.getChildNodeByTagName(inode, "tname");
         String tname = Tool.getValue(nnode);
         Element vnode = Tool.getChildNodeByTagName(inode, "values");

         if(vnode != null) {
            NodeList cnode = Tool.getChildNodesByTagName(vnode, "value");
            List<AggregateRef> aggrs = new ArrayList<>();

            for(int j = 0; j < cnode.getLength(); j++) {
               Element aggrnode = (Element) cnode.item(j);
               AggregateRef aref =
                  (AggregateRef) AbstractDataRef.createDataRef(
                     Tool.getChildNodeByTagName(aggrnode, "dataRef"));
               aggrs.add(aref);
            }

            aggregatemap.put(tname, aggrs);
         }
      }

      Element dsnode = Tool.getChildNodeByTagName(elem, "dependencies");
      list = Tool.getChildNodesByTagName(dsnode, "assetEntry");

      for(int i = 0; i < list.getLength(); i++) {
         anode = (Element) list.item(i);
         AssetEntry entry = AssetEntry.createAssetEntry(anode);
         this.dependencies.add(entry);
      }

      // parse context nodes
      String val = Tool.getAttribute(elem, "modified");

      if(val != null) {
         setLastModified(Long.parseLong(val));
      }

      val = Tool.getAttribute(elem, "modifiedBy");

      if(val != null) {
         setLastModifiedBy(val);
      }

      val = Tool.getAttribute(elem, "created");

      if(val != null) {
         setCreated(Long.parseLong(val));
      }

      val = Tool.getAttribute(elem, "createdBy");

      if(val != null) {
         setCreatedBy(val);
      }

      setVersion(Tool.getChildValueByTagName(elem, "Version"));
      Element vsnode = Tool.getChildNodeByTagName(elem, "viewsheetEntry");

      if(vsnode != null) {
         vsnode = Tool.getFirstChildNode(vsnode);
         ventry = AssetEntry.createAssetEntry(vsnode);
         ventry.setOrgID(OrganizationManager.getInstance().getCurrentOrgID());
      }

      Element wnode = Tool.getChildNodeByTagName(elem, "worksheetEntry");

      if(wnode != null) {
         wnode = Tool.getFirstChildNode(wnode);
         wentry = AssetEntry.createAssetEntry(wnode);
      }

      Element vinode = Tool.getChildNodeByTagName(elem, "viewsheetInfo");

      if(vinode != null) {
         vinfo.parseXML(Tool.getFirstChildNode(vinode));
      }

      Element linode = Tool.getChildNodeByTagName(elem, "layoutInfo");

      if(linode != null) {
         layoutInfo.parseXML(Tool.getFirstChildNode(linode));
      }

      fixZIndex();
   }

   private synchronized void fixZIndex() {
      for(Assembly assembly : assemblies) {
         if(assembly instanceof GroupContainerVSAssembly) {
            GroupContainerVSAssembly group = (GroupContainerVSAssembly) assembly;
            GroupContainerVSAssemblyInfo groupInfo =
               (GroupContainerVSAssemblyInfo) assembly.getInfo();
            String[] names = group.getAssemblies();

            for(String name : names) {
               Assembly ass = getAssembly(name);

               if(ass instanceof TableVSAssembly) {
                  VSAssemblyInfo tinfo = (VSAssemblyInfo) ass.getInfo();

                  if(tinfo.getZIndex() <= groupInfo.getZIndex()) {
                     tinfo.setZIndex(groupInfo.getZIndex() + 1);
                  }
               }
            }
         }
      }
   }

   /**
    * Get the description of the viewsheet.
    * @return the description of the viewsheet.
    */
   @Override
   public String getDescription() {
      return vinfo == null ? null : vinfo.getDescription();
   }

   /**
    * Get pixel position on this viewsheet.
    */
   public Point getPixelPositionInViewsheet(AssemblyInfo info) {
      return (Point) info.getPixelOffset().clone();
   }

   /**
    * Get pixel position on top viewsheet.
    */
   public Point getPixelPosition(Point position) {
      Point res = !isEmbedded() ? new Point(0, 0) :
         getViewsheet().getPixelPosition(getInfo());
      res.x += position.x;
      res.y += position.y;

      return res;
   }

    /**
    * Get the row height of a row from info.
    * @param isHead the whether or not head.
    * @param assemblyName the specified assemblyName
    * @param headerRowIndex the index of header row.
    * @return the row height of the row.
    */
   public int getDisplayRowHeight(boolean isHead, String assemblyName, int headerRowIndex) {
      if(assemblyName != null) {
         Assembly[] assemblies = getAssemblies();

         for(Assembly assemblyItem : assemblies) {
            VSAssembly assembly = (VSAssembly) assemblyItem;

            if(assemblyName.equals(assembly.getName()) &&
               assembly instanceof TableDataVSAssembly)
            {
               TableDataVSAssemblyInfo tinfo =
                  ((TableDataVSAssembly) assembly).getTableDataVSAssemblyInfo();
               double height;

               if(isHead) {
                  int[] headerHeights;

                  if(assembly instanceof CrosstabVSAssembly) {
                     VSCrosstabInfo vsinfo =
                        ((CrosstabVSAssembly) assembly).getVSCrosstabInfo();
                     int hrow = vsinfo != null ?
                        Math.max(1, vsinfo.getRuntimeColHeaders().length) : 0;
                     headerHeights = ((CrossBaseVSAssemblyInfo) tinfo).getHeaderRowHeights(false,
                        hrow);
                  }
                  else {
                     headerHeights = tinfo.getHeaderRowHeights();
                  }

                  height = headerHeights.length > 0 ?
                     headerHeights[headerRowIndex] : AssetUtil.defh;
               }
               else {
                  height = tinfo.getDataRowHeight();
               }

               if(!Double.isNaN(height)) {
                  return (int) height;
               }
            }
         }
      }

      return AssetUtil.defh;
   }

    /**
    * Get the row height of a row from info.
    * @param isHead the whether or not head.
    * @param assemblyName the specified assemblyName
    * @return the row height of the row.
    */
   public int getDisplayRowHeight(boolean isHead, String assemblyName) {
      return this.getDisplayRowHeight(isHead, assemblyName, 0);
   }

   /**
    * Get pixel position.
    */
   public Point getPixelPosition(AssemblyInfo info) {
      Point pos;

      // if embedded, get the position in parent viewsheet
      pos = !isEmbedded() ? new Point(0, 0) :
         getViewsheet().getPixelPosition(getInfo());

      Point offset = info.getPixelOffset();

      if(offset != null) {
         pos.x += offset.x;
         pos.y += offset.y;
      }

      if(upperLeft != null) {
         pos.x -= upperLeft.x;
         pos.y -= upperLeft.y;
      }

      return pos;
   }

   /**
    * Get pixel size.
    */
   public Dimension getPixelSize(AssemblyInfo info) {
      Dimension size = info.getPixelSize();

      if(size != null) {
         return size;
      }
      else {
         return new Dimension(AssetUtil.defw, AssetUtil.defh);
      }
   }

   /**
    * Set the brush source.
    */
   public synchronized void setBrush(String table, VSAssembly assembly) {
      if(table == null) {
         return;
      }

      if(assembly == null) {
         bmap.remove(table);
      }
      else {
         bmap.put(table, assembly);
      }

      if(assembly == null) {
         return;
      }

      // clear the other brush selection if any
      for(Assembly assembly2 : getAssemblies()) {
         if(assembly2 instanceof ChartVSAssembly &&
            !WizardRecommenderUtil.isTempDataAssembly(assembly2.getName()))
         {
            ChartVSAssembly chart = (ChartVSAssembly) assembly2;
            String table2 = chart.getTableName();

            if(Tool.equals(table, table2) && !Tool.equals(chart, assembly)) {
               chart.setBrushSelection(null);
            }
         }
      }
   }

   /**
    * Get the brush source.
    */
   public VSAssembly getBrush(String table) {
      return bmap.get(table);
   }

   @Override
   public Viewsheet prepareCheckpoint() {
      Viewsheet vs = clone0(true);

      for(Assembly assembly : vs.getAssemblies()) {
         AssemblyInfo info = assembly.getInfo();

         if(info instanceof SelectionListVSAssemblyInfo) {
            ((SelectionListVSAssemblyInfo) info).setSelectionList(null);
         }
         else if(info instanceof SelectionTreeVSAssemblyInfo) {
            ((SelectionTreeVSAssemblyInfo) info).setCompositeSelectionValue(null);
         }
      }

      return vs;
   }

   /**
    * Viewsheet change listener.
    */
   private static class VSChangeListener implements ActionListener {
      public VSChangeListener(Viewsheet vs) {
         this.vs = vs;
      }

      @Override
      public void actionPerformed(ActionEvent event) {
         vs.resetWS();
      }

      private final Viewsheet vs;
   }

   /**
    * Return stack order.
    */
   @Override
   public int getZIndex() {
      return info.getZIndex();
   }

   /**
    * Set stack order.
    */
   @Override
   public void setZIndex(int zIndex) {
      this.info.setZIndex(zIndex);
   }

   /**
    * Add embeded viewsheet child id.
    */
   public void addChildId(String id) {
      childrenIds.add(id);
   }

   /**
    * Remove embeded viewsheet child id.
    */
   public void removeChildId(String id) {
      childrenIds.remove(id);
   }

   /**
    * Get embeded viewsheet children ids.
    */
   public List<String> getChildrenIds() {
      return childrenIds;
   }

   /**
    * Get the bound table.
    */
   @Override
   public String getTableName() {
      return null;
   }

   /**
    * Set the tip condition list.
    */
   @Override
   public void setTipConditionList(ConditionListWrapper wrapper) {
      // do nothing
   }

   /**
    * Get the tip condition list.
    */
   @Override
   public ConditionListWrapper getTipConditionList() {
      return null;
   }

   @Override
   public DataRef[] getBindingRefs() {
      return new DataRef[0];
   }

   /**
    * Get all the binding refs of vsasseblies except the embedded viewsheet.
    */
   @Override
   public synchronized DataRef[] getAllBindingRefs() {
      List<DataRef> refs = new ArrayList<>();

      for(Assembly assemblyItem : getAssemblies()) {
         VSAssembly assembly = (VSAssembly) assemblyItem;

         if(assembly instanceof Viewsheet) {
            continue;
         }

         refs.addAll(Arrays.asList(assembly.getBindingRefs()));
      }

      return refs.toArray(new DataRef[0]);
   }

   /**
    * Get all variables in the condition value list.
    * @return the variable list.
    */
   @Override
   public synchronized UserVariable[] getAllVariables() {
      List<UserVariable> list = new ArrayList<>();

      for(Assembly assemblyItem : getAssemblies()) {
         VSAssembly assembly = (VSAssembly) assemblyItem;
         UserVariable[] vars = assembly.getAllVariables();
         mergeVariables(list, vars);
      }

      UserVariable[] vars = new UserVariable[list.size()];
      list.toArray(vars);
      return vars;
   }

   public TextVSAssembly getWarningTextAssembly() {
      return getWarningTextAssembly(true);
   }

   public TextVSAssembly getWarningTextAssembly(boolean createIfNotExist) {
      VSAssembly assembly = getAssembly(VS_WARNING_TEXT);

      if(assembly == null && createIfNotExist) {
         TextVSAssembly textVSAssembly = new TextVSAssembly(this, VS_WARNING_TEXT);
         textVSAssembly.getTextInfo().setAutoSize(true);
         textVSAssembly.initDefaultFormat();
         VSCompositeFormat format = textVSAssembly.getVSAssemblyInfo().getFormat();

         if(format != null) {
            format.getUserDefinedFormat().setForegroundValue("#ff0000");
            format.getUserDefinedFormat()
               .setAlignmentValue(StyleConstants.H_LEFT | StyleConstants.V_TOP);
         }

         Dimension size = textVSAssembly.getPixelSize();

         if(size != null) {
            size.width = 800;
         }

         textVSAssembly.setPixelSize(size);
         textVSAssembly.setTextValue("");
         addAssembly(textVSAssembly);
         assembly = textVSAssembly;
         adjustWarningTextPosition();
      }

      return (TextVSAssembly) assembly;
   }

   private int getWarningTextPositionY() {
      Assembly[] assemblies = getAssemblies();

      if(assemblies == null) {
         return 0;
      }

      int maxY = 0;

      for(Assembly assembly : assemblies) {
         if(assembly == null || Tool.equals(assembly.getName(), VS_WARNING_TEXT)) {
            continue;
         }

         VSAssemblyInfo info = ((VSAssembly) assembly).getVSAssemblyInfo();

         Point point = info.isScaled() ? info.getLayoutPosition() : assembly.getPixelOffset();
         Dimension size = info.isScaled() ? info.getLayoutSize() : assembly.getPixelSize();

         if(point == null || size == null) {
            continue;
         }

         maxY = Math.max(maxY, point.y + size.height);
      }

      return maxY + 10;
   }

   public void adjustWarningTextPosition() {
      int y = getWarningTextPositionY();
      TextVSAssembly textVSAssembly = getWarningTextAssembly(false);

      if(textVSAssembly != null) {
         Point point = textVSAssembly.getPixelOffset();
         point.y = y;
         textVSAssembly.setPixelOffset(point);
      }
   }

   /**
    * Shrink the sheet width/height settings if possible.
    */
   protected void shrink() {
      if(ws == null) {
         return;
      }

      Set<String> tables = new HashSet<>();

      if(wentry != null) {
         if(wentry.isWorksheet()) {
            Worksheet wss = getBaseWorksheet();

            if(wss != null) {
               for(Assembly assembly : wss.getAssemblies()) {
                  if(assembly instanceof TableAssembly) {
                     tables.add(assembly.getName());
                  }
               }
            }
         }
         else {
            tables.add(wentry.getName());
         }
      }

      Iterator<String> keys = calcmap.keySet().iterator();
      List<String> idles = getIdleTables(keys, tables);

      for(String idle : idles) {
         List<CalculateRef> crefs = calcmap.remove(idle);

         if(crefs != null) {
            LOG.debug("Remove the calc fields: " + crefs + " for " + idle);
         }
      }

      keys = aggregatemap.keySet().iterator();
      idles = getIdleTables(keys, tables);

      for(String idle : idles) {
         List<AggregateRef> aggs = aggregatemap.remove(idle);

         if(aggs != null) {
            LOG.debug("Remove the aggregate fields: " + aggs + " for " + idle);
         }
      }
   }

   /**
    * Get table names not belong to source tables from calcmap or aggregatemap.
    */
   private List<String> getIdleTables(Iterator<String> keys, Set<String> tables) {
      List<String> idleList = new ArrayList<>();

      while(keys.hasNext()) {
         String tname = keys.next();

         if(!tables.contains(tname) && !tname.startsWith(Assembly.CUBE_VS)) {
            idleList.add(tname);
         }
      }

      return idleList;
   }

   /**
    * Get the actual size of the embedded vs
    */
   private Rectangle getChildrenBounds(int x0, int y0) {
      if(upperLeft == null || bottomRight == null || upperLeftPixel == null ||
         bottomRightPixel == null || upperLeft.x == bottomRight.x ||
         upperLeft.y == bottomRight.y)
      {
         return null;
      }

      int x = Math.max(x0, x0 + upperLeftPixel.x - upperLeft.x);
      int y = Math.max(y0, y0 + upperLeftPixel.y - upperLeft.y);
      int w = bottomRightPixel.x - upperLeftPixel.x;
      int h = bottomRightPixel.y - upperLeftPixel.y;

      return new Rectangle(x, y, w, h);
   }

   /**
    * Inspect all assemblies for adhoc filtering
    *
    * @return  <tt>true</tt> if at least one assembly has adhoce filtering
    */
   public boolean hasAdhocFilters() {
      for(Assembly assemblyItem : getAssemblies()) {
         if(assemblyItem instanceof AbstractSelectionVSAssembly) {
            AbstractSelectionVSAssembly assembly =
               (AbstractSelectionVSAssembly) assemblyItem;

            VSAssemblyInfo info = assembly.getVSAssemblyInfo();

            if(info instanceof SelectionVSAssemblyInfo &&
               ((SelectionVSAssemblyInfo) info).isAdhocFilter() ||
               info instanceof TimeSliderVSAssemblyInfo &&
               ((TimeSliderVSAssemblyInfo) info).isAdhocFilter())
            {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Clear assemblys layout position and size.
    */
   @Override
   public synchronized void clearLayoutState() {
      rscaleFont = 1;
      VSAssemblyInfo info = getVSAssemblyInfo();
      info.setLayoutPosition(null);
      info.setLayoutSize(null);

      if(!info.isControlByScript()) {
         info.setLayoutVisible(VSAssembly.NONE_CHANGED);
      }

      FormatInfo formatInfo = getFormatInfo();
      TableDataPath[] paths = formatInfo.getPaths();

      for(TableDataPath path : paths) {
         VSCompositeFormat format = formatInfo.getFormat(path);
         format.setRScaleFont(1);
      }

      for(Object assembly : assemblies) {
         VSAssembly vsAssembly = (VSAssembly) assembly;
         vsAssembly.clearLayoutState();
      }
   }

   /**
    * Set the runtime scale font.
    */
   public void setRScaleFont(float scale) {
      rscaleFont = scale;
   }

   /**
    * Get the runtime scale font.
    */
   public float getRScaleFont() {
      return rscaleFont;
   }

   /**
    * Get whether annotations should be shown on this viewsheet
    *
    * @return true if annotations should be shown
    */
   public boolean getAnnotationsVisible() {
      return annotationsVisible;
   }

   /**
    * Set whether to show annotations. Should be set from the runtime viewsheet so the
    * UserEnv property can be checked
    *
    * @param visible true to show, false to hide
    */
   public void setAnnotationsVisible(boolean visible) {
      annotationsVisible = visible;
   }

   /**
    * Set whether there is a table/crosstab/freehand/chart was displayed in max mode.
    */
   public void setMaxMode(boolean maxMode) {
      this.maxMode = maxMode;
   }

   /**
    * Get whether there is a table/crosstab/freehand/chart was displayed in max mode.
    */
   public boolean isMaxMode() {
      return this.maxMode;
   }

   /**
    * Create tables for the selection assemblies in the viewsheet.
    *
    * @param resetVS whether or not viewsheet will be reset
    *
    * @return a set containing used table names
    */
   private synchronized Set<String> createSelectionTables(boolean resetVS) {
      final Set<String> used = new HashSet<>();

      for(Assembly assembly : getAssemblies()) {
         if(assembly instanceof SelectionVSAssembly) {
            final SelectionVSAssembly sassembly = (SelectionVSAssembly) assembly;
            DataRef[] dataRefs = sassembly.getDataRefs();

            if(sassembly.isSelectionUnion()) {
               final List<String> tableNames = sassembly.getTableNames();
               final List<String> selectionTableNames = sassembly.getSelectionTableNames();
               final List<String> subtableNames = createSubtableNames(sassembly);
               final List<TableAssembly> subtables = new ArrayList<>();

               for(int i = 0; i < tableNames.size(); i++) {
                  final String tableName = tableNames.get(i);
                  final String selectionTableName = selectionTableNames.get(i);
                  final TableAssembly selectionTable =
                     createSelectionTable(tableName, selectionTableName, resetVS, dataRefs);

                  if(selectionTable != null) {
                     used.add(selectionTable.getName());
                  }

                  final String subtableName = subtableNames.get(i);
                  final TableAssembly subtable =
                     createSelectionTable(tableName, subtableName, resetVS, dataRefs);

                  if(subtable != null) {
                     subtables.add(subtable);
                  }
               }

               // Some subtable might not exist anymore
               if(subtables.size() != tableNames.size()) {
                  continue;
               }
               else {
                  subtables.stream()
                     .map(Assembly::getName)
                     .forEach(used::add);
               }

               populateColumnSelections(subtables);

               final ConcatenatedTableAssembly concatTable = createConcatTable(sassembly);
               concatTable.setVisible(false);
               ws.addAssembly(concatTable);
               used.add(concatTable.getName());
            }

            final String table = sassembly.getTableName();
            final String stable = sassembly.getSelectionTableName();
            final TableAssembly tassembly = createSelectionTable(table, stable, resetVS, dataRefs);

            if(tassembly != null) {
               used.add(tassembly.getName());
            }
         }
      }

      return used;
   }

   /**
    * Populate the column selections of each table in {@code subtables}.
    *
    * @param subtables the tables to populate the column selections of
    */
   private void populateColumnSelections(List<TableAssembly> subtables) {
      if(subtables.size() <= 1) {
         return;
      }

      final List<ColumnRef> refs = findMutualColumnRefs(subtables);

      for(TableAssembly subtable : subtables) {
         final ColumnRef[] visibleColumns = new ColumnRef[refs.size()];
         final ArrayList<ColumnRef> invisibleColumns = new ArrayList<>();
         final ColumnSelection oldColumnSelection = subtable.getColumnSelection(false);

         for(int i = 0; i < oldColumnSelection.getAttributeCount(); i++) {
            final ColumnRef col = (ColumnRef) oldColumnSelection.getAttribute(i);
            final String colName = col.getDisplayName();
            int matchingIndex = -1;

            for(int j = 0; j < refs.size(); j++) {
               if(refs.get(j).getDisplayName().equals(colName)) {
                  matchingIndex = j;
                  break;
               }
            }

            if(matchingIndex == -1) {
               col.setVisible(false);
               invisibleColumns.add(col);
            }
            else {
               visibleColumns[matchingIndex] = col;
            }
         }

         final ColumnSelection newColumnSelection = new ColumnSelection();
         Arrays.stream(visibleColumns).forEach(newColumnSelection::addAttribute);
         invisibleColumns.forEach(newColumnSelection::addAttribute);

         subtable.setColumnSelection(newColumnSelection);
      }
   }

   /**
    * @param subtables the subtables to find the mutual column refs of
    *
    * @return a {@code List} of mutual column refs in the order matching the column refs in the
    * first subtable
    */
   private List<ColumnRef> findMutualColumnRefs(List<TableAssembly> subtables) {
      final List<ColumnRef> refs = new ArrayList<>();
      final TableAssembly firstTable = subtables.get(0);
      final List<TableAssembly> subsequentTables = subtables.subList(1, subtables.size());
      final ColumnSelection firstColumnSelection = firstTable.getColumnSelection(true);

      for(int i = 0; i < firstColumnSelection.getAttributeCount(); i++) {
         refs.add((ColumnRef) firstColumnSelection.getAttribute(i));
      }

      final Iterator<ColumnRef> iterator = refs.iterator();

      while(iterator.hasNext()) {
         final ColumnRef ref = iterator.next();

         final boolean allTablesContainRef = subsequentTables.stream()
            .map((t) -> t.getColumnSelection(true).stream())
            .allMatch((stream) -> stream
               .map(ColumnRef.class::cast)
               .anyMatch((c) -> SelectionVSUtil.areColumnsCompatible(c, ref)));

         if(!allTablesContainRef) {
            iterator.remove();
         }
      }

      return refs;
   }

   /**
    * Create a selection table if one doesn't exist and add it to the worksheet.
    *
    * @param boundTableName     the name the table the selection is bound to
    * @param selectionTableName the name of the selection table
    * @param resetVS            whether other not the viewsheet will be reset
    *
    * @return the selection table assembly if one exists or is created, otherwise null
    */
   private TableAssembly createSelectionTable(String boundTableName,
                                              String selectionTableName,
                                              boolean resetVS,
                                              DataRef[] dataRefs)
   {
      TableAssembly selectionTable = null;

      if(boundTableName != null && !boundTableName.isEmpty()) {
         selectionTable = (TableAssembly) ws.getAssembly(selectionTableName);

         // we need regenerate the selection table
         // because the table structure may be changed
         // see bug1357442251751
         TableAssembly boundTable = (TableAssembly) ws.getAssembly(boundTableName);

         if(boundTable != null) {
            boolean boundToCalcRef = isBoundToCalcRef(boundTable, dataRefs);

            if(boundToCalcRef) {
               addCalcRefs(boundTable);
            }

            if(selectionTable == null || resetVS) {
               if(!boundToCalcRef) {
                  boundTable = (TableAssembly) boundTable.clone();
                  addCalcRefs(boundTable);
               }

               selectionTable = (TableAssembly) boundTable.copyAssembly(selectionTableName);

               // do not change selection for consistency
               if(selectionTable instanceof EmbeddedTableAssembly) {
                  EmbeddedTableAssembly eassembly = (EmbeddedTableAssembly) selectionTable;
                  eassembly.setEmbeddedData(eassembly.getOriginalEmbeddedData());
               }

               selectionTable.setPreRuntimeConditionList(null);
               selectionTable.setPostRuntimeConditionList(null);
               selectionTable.setVisible(false);
               ws.addAssembly(selectionTable);
            }
            else {
               addCalcRefs(selectionTable, boundTableName);
            }
         }
      }

      return selectionTable;
   }

   private boolean isBoundToCalcRef(TableAssembly table, DataRef[] dataRefs) {
      final List<CalculateRef> calculateRefs = calcmap.get(table.getName());

      if(calculateRefs == null) {
         return false;
      }

      for(DataRef ref : dataRefs) {
         boolean found = calculateRefs.stream()
            .anyMatch((calcRef) -> Tool.equals(calcRef.getName(), ref.getName()));

         if(found) {
            return true;
         }
      }

      return false;
   }

   /**
    * Create mirror tables for the tables bound by viewsheet assemblies.
    *
    * @return true if the viewsheet should be reset
    */
   private synchronized boolean createMirrorTables() {
      boolean resetVS = false;

      for(Assembly assemblyItem : getAssemblies()) {
         VSAssembly assembly = (VSAssembly) assemblyItem;

         if(assembly instanceof SelectionVSAssembly &&
            ((SelectionVSAssembly) assembly).isSelectionUnion())
         {
            SelectionVSAssembly sassembly = (SelectionVSAssembly) assembly;

            for(String tname : sassembly.getTableNames()) {
               resetVS = createMirrorTable(tname) || resetVS;
            }
         }
         else {
            String tname = assembly.getTableName();
            resetVS = createMirrorTable(tname) || resetVS;
         }
      }

      return resetVS;
   }

   /**
    * @param tname the table to mirror
    *
    * @return true if the underlying worksheet was changed, false otherwise
    */
   private boolean createMirrorTable(String tname) {
      if(tname == null) {
         return false;
      }

      Assembly wsobj = ws.getAssembly(tname);

      if(!(wsobj instanceof TableAssembly)) {
         return false;
      }

      TableAssembly table = (TableAssembly) wsobj;
      String nname = tname + OUTER_TABLE_SUFFIX;

      // do not create mirror for embedded table, for viewsheet might
      // change the values contained in this embedded table (special case)
      if(VSUtil.isDynamicTable(this, table)) {
         return convertToEmbeddedTable(ws, tname);
      }

      // If the cube table is from worksheet, do not continue and change it
      // to mirror table as normal table.
      if(table instanceof CubeTableAssembly && table.getName().contains(Assembly.CUBE_VS)) {
         return false;
      }

      // already created? ignore it
      if(ws.containsAssembly(nname)) {
         return false;
      }

      // break dependency cycle
      if(table instanceof MirrorTableAssembly) {
         if(nname.equals(((MirrorTableAssembly) table).getAssemblyName())) {
            return false;
         }
      }

      table.setVisible(false);
      table.getInfo().setName(nname);
      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, tname, table);
      mirror.setVisible(!VSUtil.isVSAssemblyBinding(tname));
      mirror.setVisibleTable(table.isVisibleTable());
      mirror.setProperty(VS_MIRROR_TABLE, "true");
      ws.addAssembly(mirror);

      return true;
   }

   /**
    * @param unionSelectionAssembly the union selection assembly to create the concatenated table
    *                               from.
    *
    * @return the new concatenated table assembly.
    */
   private ConcatenatedTableAssembly createConcatTable(
      SelectionVSAssembly unionSelectionAssembly)
   {
      final String concatTableName = unionSelectionAssembly.getTableName();
      final String[] tableNames =
         createSubtableNames(unionSelectionAssembly).toArray(new String[0]);
      final TableAssemblyOperator[] ops = new TableAssemblyOperator[tableNames.length - 1];

      for(int i = 0; i < tableNames.length - 1; i++) {
         ops[i] = new TableAssemblyOperator(null, null, null, null, TableAssemblyOperator.UNION);
         ops[i].getOperator(0).setDistinct(true);
      }

      final TableAssembly[] subtables = Arrays.stream(tableNames)
         .map(ws::getAssembly)
         .filter(TableAssembly.class::isInstance)
         .map(TableAssembly.class::cast)
         .toArray(TableAssembly[]::new);

      final ConcatenatedTableAssembly concatTable =
         new ConcatenatedTableAssembly(ws, concatTableName, subtables, ops);
      concatTable.initDefaultColumnSelection();

      return concatTable;
   }

   /**
    * Create the subtable names for the concatenated table of a union selection assembly.
    *
    * @param sassembly the union selection assembly to create the subtable names from.
    *
    * @return the subtable names.
    */
   private List<String> createSubtableNames(SelectionVSAssembly sassembly) {
      if(!sassembly.isSelectionUnion()) {
         throw new IllegalArgumentException("Selection assembly must be a union selection.");
      }

      final DataRef[] refs = sassembly.getDataRefs();

      // The data refs are used to differentiate between selection tables with different
      // column selections.
      final StringJoiner prefixJoiner = new StringJoiner("_", CONCATENATED_SELECTION_SUBTABLE, "_");
      Arrays.stream(refs)
         .map(DataRef::getName)
         .forEach(prefixJoiner::add);
      final String prefix = prefixJoiner.toString();

      return sassembly.getTableNames().stream()
         .map((tname) -> prefix + tname)
         .collect(Collectors.toList());
   }

   public boolean isWizardTemporary() {
      return false;
   }

   public void setWizardTemporary(boolean temp) {
      // do noting.
   }

   public void updateCSSFormat(String exportFormat, Assembly newAssembly) {
      updateCSSFormat(exportFormat, newAssembly, null);
   }

   public void updateCSSFormat(String exportFormat, Assembly newAssembly, ViewsheetSandbox box) {
      FormatInfo vsFormatInfo = getFormatInfo();
      VSCompositeFormat vsCompositeFormat = null;

      if(vsFormatInfo != null) {
         vsCompositeFormat = vsFormatInfo.getFormat(VSAssemblyInfo.OBJECTPATH);

         if(vsCompositeFormat != null) {
            VSCSSFormat sheetCSSFormat = vsCompositeFormat.getCSSFormat();

            if(sheetCSSFormat != null && newAssembly == null) {
               sheetCSSFormat.setCSSType(CSSConstants.VIEWSHEET);
               sheetCSSFormat.setCSSAttributes(new CSSAttr("print", (exportFormat != null) + "",
                                                           "format", exportFormat));
            }

            // clone after updating the css so that it can be set in the assemblies formatInfo
            vsCompositeFormat = vsCompositeFormat.clone();
         }
      }

      if(vsCompositeFormat == null) {
         return;
      }

      Assembly[] assemblies = newAssembly != null ? new Assembly[]{ newAssembly } :
         getAssemblies(true);

      // update the sheet format in the children
      for(Assembly assembly : assemblies) {
         if(assembly instanceof VSAssembly) {
            VSAssembly vsAssembly = (VSAssembly) assembly;
            fixSheetCssFormat(vsAssembly, vsCompositeFormat);
            String name = vsAssembly.getAbsoluteName();
            int index = name.lastIndexOf('.');

            if(index >= 0 && box != null) {
              ViewsheetSandbox nbox = box.getSandbox(name.substring(0, index));
              name = name.substring(index + 1);
              VSAssembly embedAss = nbox.getViewsheet().getAssembly(name);
              fixSheetCssFormat(embedAss, vsCompositeFormat);
              nbox.resetDataMap(name);
            }

            // only true if newAssembly is Viewsheet
            if(assembly instanceof Viewsheet) {
               ((Viewsheet) assembly).updateCSSFormat(exportFormat, null, null);
            }
         }
      }

      if(newAssembly == null && layoutInfo != null && layoutInfo.getPrintLayout() != null) {
         List<VSAssemblyLayout> layoutAssemblies = new ArrayList<>();
         layoutAssemblies.addAll(layoutInfo.getPrintLayout().getHeaderLayouts());
         layoutAssemblies.addAll(layoutInfo.getPrintLayout().getFooterLayouts());

         for(VSAssemblyLayout assembly : layoutAssemblies) {
            if(assembly instanceof VSEditableAssemblyLayout) {
               FormatInfo formatInfo = ((VSEditableAssemblyLayout) assembly).getInfo().getFormatInfo();

               if(formatInfo != null) {
                  formatInfo.setFormat(VSAssemblyInfo.SHEETPATH, vsCompositeFormat);
               }
            }
         }
      }
   }

   private void fixSheetCssFormat(VSAssembly assembly, VSCompositeFormat vsCompositeFormat) {
      FormatInfo formatInfo = assembly.getFormatInfo();

      if(formatInfo != null) {
         formatInfo.setFormat(VSAssemblyInfo.SHEETPATH, vsCompositeFormat);

         if(assembly instanceof StateSelectionListVSAssembly) {
            SelectionList selectionList = ((StateSelectionListVSAssembly) assembly).getSelectionList();
            VSCompositeFormat objFormat = formatInfo.getFormat(VSAssemblyInfo.OBJECTPATH);

            if(selectionList != null) {
               for(SelectionValue selectionValue : selectionList.getAllSelectionValues()) {
                  VSCompositeFormat svalueFmt = selectionValue.getFormat();

                  if(svalueFmt != null) {
                     svalueFmt.setFormatInfo(formatInfo);
                     formatInfo.copyDefaultFormat(svalueFmt.getDefaultFormat(), objFormat);
                  }
               }
            }
         }
         else if(assembly instanceof ListInputVSAssembly) {
            VSCompositeFormat[] formats = ((ListInputVSAssembly) assembly).getFormats();
            VSCompositeFormat objFormat = formatInfo.getFormat(VSAssemblyInfo.OBJECTPATH);

            if(formats != null) {
               for(VSCompositeFormat format : formats) {
                  if(format != null) {
                     format.setFormatInfo(formatInfo);
                     formatInfo.copyDefaultFormat(format.getDefaultFormat(), objFormat);
                  }
               }
            }
         }
      }

      assembly.getVSAssemblyInfo().updateCSSValues();
   }

   private void clearCache() {
      amap = null;
   }

   public void clearCalcMap() {
      calcmap.clear();
   }

   private void syncBookmarkAssembly(VSAssembly newAss, VSAssembly oldAss) {
      if(newAss == null || oldAss == null) {
         return;
      }

      newAss.setBounds(oldAss.getBounds());
      newAss.getVSAssemblyInfo().setScript(oldAss.getVSAssemblyInfo().getScript());
   }

   /**
    * Get all disabled variable names.
    */
   public Set<String> getDisabledVariables() {
      Set<String> list = new LinkedHashSet<>();
      list.addAll(Arrays.asList(vinfo.getDisabledVariables()));

      for(Assembly assembly : getAssemblies()) {
         if(assembly instanceof Viewsheet) {
            list.addAll(((Viewsheet) assembly).getDisabledVariables());
         }
      }

      return list;
   }

   private ActionListener listener = new VSChangeListener(this);
   private ViewsheetVSAssemblyInfo info; // assembly info
   private ViewsheetInfo vinfo; // viewsheet info
   private Viewsheet originalVs; // original viewsheet of viewsheet applied vslayout.
   private LayoutInfo layoutInfo; // layout info
   private AssetEntry ventry; // mirrored viewsheet entry
   private AssetEntry wentry; // base data source entry
   private List<VSAssembly> assemblies; // assembly list
   private Set<AssetEntry> dependencies; // outer dependencies
   private Map<String, byte[]> imgmap; // embedded image map
   private Map<String, Color> dimensionColors;
   private final Map<SharedFrameParameters, VisualFrame> sharedFrames;
   // calc field map
   private Map<String, List<CalculateRef>> calcmap;
   // user create aggregate field map
   private Map<String, List<AggregateRef>> aggregatemap;
   // runtime
   private final transient Map<String, VSAssembly> bmap; // brush map: tablename->brush source
   private transient Worksheet ws; // base worksheet
   private transient Worksheet originalWs; // original base ws without added table assemblies for vs
   private transient volatile Map<String, VSAssembly> amap; // assembly name -> assembly cache
   private VSAssembly latestTemp;
   private final transient Lock amapLock = new ReentrantLock();
   // user create aggregate aoa's aggregate
   private transient List<AggregateRef> aggrs;
   private transient Map<String, Set<Assembly>> varmap;
   private Point upperLeft; // upper left corner
   private Point bottomRight; // bottom right corner
   private Point upperLeftPixel;
   private Point bottomRightPixel;
   private boolean print; // print mode
   private AssetEntry rentry; // runtime asset entry
   private Set<String> wnames; // worksheet assembly names
   private final ArrayList<String> childrenIds; // embeded viewsheet children ids
   private float rscaleFont = 1;
   private boolean annotationsVisible = true;
   private boolean maxMode = false;
   private Map<String, LogbackTraceAppender.StackRecord> rmStacks = new ConcurrentHashMap<>();

   private static final String COLUMN_DELIMITER = "^^";
   private static final String VS_WARNING_TEXT = "VS^WARNING^TEXT";
   private static final Logger LOG = LoggerFactory.getLogger(Viewsheet.class);
}
