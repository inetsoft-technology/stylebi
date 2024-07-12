/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.viewsheet.service;


import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.SetInputObjectValueEvent;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.*;
import inetsoft.report.internal.table.TableHighlightAttr;
import inetsoft.report.internal.table.TableHyperlinkAttr;
import inetsoft.report.script.viewsheet.ScriptEvent;
import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.table.XSwappableTable;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.*;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.composer.model.vs.DataInputPaneModel;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;

@Service
public class VSInputService {
   @Autowired
   public VSInputService(
      VSObjectService vsObjectService, PlaceholderService placeholderService,
      ViewsheetService viewsheetService, RuntimeViewsheetRef runtimeViewsheetRef)
   {
      this.vsObjectService = vsObjectService;
      this.placeholderService = placeholderService;
      this.viewsheetService = viewsheetService;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
   }

   /**
    * Apply selection.
    * {@link SetInputObjectValueEvent}
    *
    * @param assemblyName   the name of the selection assembly
    * @param selectedObject the selected object
    * @param principal      a principal identifying the current user.
    * @param dispatcher     the command dispatcher.
    * @param linkUri        the link URI
    *
    * @throws Exception if the selection could not be applied.
    */
   public void singleApplySelection(String assemblyName, Object selectedObject,
                                    Principal principal, CommandDispatcher dispatcher,
                                    @LinkUri String linkUri) throws Exception
   {
      final int hint = this.applySelection(assemblyName, selectedObject, principal, dispatcher);
      refreshVS(principal, dispatcher, getOldCrosstabInfo(principal),
                new String[]{ assemblyName }, new Object[]{ selectedObject }, new int[]{ hint },
                linkUri);
      // keep the 'event' object until onLoad is called. (62609)
      detachScriptEvent(principal);
   }

   private void detachScriptEvent(Principal principal) throws Exception {
      RuntimeViewsheet rvs =
         vsObjectService.getRuntimeViewsheet(runtimeViewsheetRef.getRuntimeId(), principal);
      final ViewsheetSandbox box = rvs.getViewsheetSandbox();
      box.detachScriptEvent();
   }

   public void multiApplySelection(String[] assemblyNames, Object[] selectedObjects, Principal principal,
                                   CommandDispatcher dispatcher, @LinkUri String linkUri)
      throws Exception
   {
      int[] hints = new int[assemblyNames.length];
      Map<String, VSAssemblyInfo> oldCrosstabInfo = getOldCrosstabInfo(principal);

      for(int i = 0; i < assemblyNames.length; i++) {
         hints[i] = applySelection(assemblyNames[i], selectedObjects[i],
                                   principal, dispatcher);
      }

      refreshVS(principal, dispatcher, oldCrosstabInfo, assemblyNames,
                selectedObjects, hints, linkUri);
      // keep the 'event' object until onLoad is called. (62609)
      detachScriptEvent(principal);
   }

   /**
    * Apply selection.
    * {@link SetInputObjectValueEvent}
    *
    * @param assemblyName   the name of the selection assembly
    * @param selectedObject the selected object
    * @param principal      a principal identifying the current user.
    * @param dispatcher     the command dispatcher.
    *
    * @throws Exception if the selection could not be applied.
    */
   private int applySelection(String assemblyName, Object selectedObject,
                              Principal principal, CommandDispatcher dispatcher)
      throws Exception
   {
      if(assemblyName == null) {
         return 0;
      }

      RuntimeViewsheet rvs =
         vsObjectService.getRuntimeViewsheet(runtimeViewsheetRef.getRuntimeId(), principal);
      final ViewsheetSandbox box = rvs.getViewsheetSandbox();
      int hint = 0;

      rvs.setSocketSessionId(dispatcher.getSessionId());
      rvs.setSocketUserName(dispatcher.getUserName());

      box.lockWrite();

      try {
         hint = applySelection0(rvs, assemblyName, selectedObject, dispatcher);
      }
      finally {
         box.unlockWrite();
      }

      return hint;
   }

   private int applySelection0(RuntimeViewsheet rvs, String assemblyName, Object selectedObject,
                                CommandDispatcher dispatcher)
      throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return 0;
      }

      // binding may be caused by variable, should retry mv if necessary
      rvs.resetMVOptions();
      VSAssembly vsAssembly = vs.getAssembly(assemblyName);

      if(vsAssembly == null || !(vsAssembly instanceof InputVSAssembly)) {
         return 0;
      }

      final InputVSAssembly assembly = (InputVSAssembly) vsAssembly;
      Object[] values;

      if(selectedObject == null) {
         values = null;
      }
      else if(selectedObject.getClass().isArray()) {
         values = (Object[]) selectedObject;
      }
      else if(selectedObject instanceof java.util.List) {
         values = ((java.util.List) selectedObject).toArray();
      }
      else {
         values = new Object[] { selectedObject };
      }

      InputVSAssemblyInfo info = (InputVSAssemblyInfo) assembly.getVSAssemblyInfo();
      String type = assembly.getDataType();
      Object obj = values == null || values.length == 0 ? null : Tool.getData(type, values[0]);

      if(values !=null && obj == null && type.equals(CoreTool.BOOLEAN) && values[0].equals("")) {
         obj = "";
      }

      int hint0;

      if(info instanceof SliderVSAssemblyInfo) {
         hint0 = info.setSelectedObject(obj);
         placeholderService.refreshVSAssembly(rvs, info.getAbsoluteName(), dispatcher);
      }
      else if(info instanceof SpinnerVSAssemblyInfo) {
         hint0 = info.setSelectedObject(obj);
      }
      else if(info instanceof CheckBoxVSAssemblyInfo) {
         Object[] objs = values == null ? null : new Object[values.length];

         if(values != null) {
            for(int i = 0; i < values.length; i++) {
               objs[i] = Tool.getData(type, values[i]);
            }
         }

         hint0 = info.setSelectedObjects(objs);
      }
      else if(info instanceof RadioButtonVSAssemblyInfo) {
         hint0 = info.setSelectedObject(obj);
      }
      else if(info instanceof ComboBoxVSAssemblyInfo) {
         hint0 = info.setSelectedObject(obj);
      }
      else if(info instanceof TextInputVSAssemblyInfo) {
         hint0 = info.setSelectedObject(obj);
      }
      else {
         return 0;
      }

      final int hint = hint0;

      final ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(box == null) {
         return 0;
      }

      final boolean form = info instanceof TextInputVSAssemblyInfo ||
         info instanceof ListInputVSAssemblyInfo;

      // @by stephenwebster, fix bug1386097203077
      // instead of dispatching the event, only attach the event to the
      // VSScope, otherwise, both the call to execute and the call to dispatchEvent
      // will execute the onLoad script.
      if(form) {
         ScriptEvent event0 = new SetInputObjectValueEvent.InputScriptEvent(assemblyName, assembly);
         box.attachScriptEvent(event0);
      }

      if(info.getWriteBackValue()) {
         ViewsheetSandbox baseBox = placeholderService.getSandbox(box, assemblyName);
         String tableName = VSUtil.stripOuter(info.getTableName());

         baseBox.writeBackFormDataDirectly(viewsheetService.getAssetRepository(),
                                           tableName, info.getColumnValue(),
                                           info.getRow(), info.getSelectedObject());
      }

      final AssetQuerySandbox wbox = box.getAssetQuerySandbox();
      Worksheet ws = assembly.getViewsheet().getBaseWorksheet();
      refreshVariable(assembly, wbox, ws, vs);

      // if assembly is a variable then check if any worksheet tables depend on it and
      // clear any metadata that selection lists/trees might be using
      resetTableMetadata(assembly, ws, box);

      return hint;
   }

   private void refreshVS(Principal principal, CommandDispatcher dispatcher,
                         Map<String, VSAssemblyInfo> oldCrosstabInfo,
                         String[] assemblyNames, Object[] selectedObjects, int[] hints,
                         @LinkUri String linkUri) throws Exception
   {
      RuntimeViewsheet rvs =
         vsObjectService.getRuntimeViewsheet(runtimeViewsheetRef.getRuntimeId(), principal);
      final ViewsheetSandbox box = rvs.getViewsheetSandbox();
      box.lockWrite();

      try {
         refreshVS0(rvs, principal, dispatcher, oldCrosstabInfo,
                    assemblyNames, selectedObjects, hints, linkUri);
      }
      finally {
         box.unlockWrite();
      }

   }

   private void refreshVS0(RuntimeViewsheet rvs, Principal principal, CommandDispatcher dispatcher,
                           Map<String, VSAssemblyInfo> oldCrosstabInfo,
                           String[] assemblyNames, Object[] selectedObjects, int[] hints,
                           @LinkUri String linkUri) throws Exception
   {
      ChangedAssemblyList clist = vsObjectService.createList(true, dispatcher, rvs, linkUri);
      final ViewsheetSandbox box = rvs.getViewsheetSandbox();
      Viewsheet vs = rvs.getViewsheet();
      InputVSAssembly[] assemblies = new InputVSAssembly[assemblyNames.length];
      Object[] objs = new Object[selectedObjects.length];

      if(vs == null) {
         return;
      }

      for(int i = 0; i < assemblyNames.length; i ++) {
         String assemblyName = assemblyNames[i];
         VSAssembly vsAssembly = vs.getAssembly(assemblyName);

         if(!(vsAssembly instanceof InputVSAssembly)) {
            return;
         }

         assemblies[i] = (InputVSAssembly) vsAssembly;
      }

      final AssetQuerySandbox wbox = box.getAssetQuerySandbox();
      boolean wsOutputDataChanged = false;
      // true to refresh all input assemblies.
      boolean refreshInput = true;

      for(int i = 0; i < assemblies.length; i ++) {
         InputVSAssembly assembly = assemblies[i];

         Worksheet ws = assembly.getViewsheet().getBaseWorksheet();
         String tname = assembly.getTableName();

         if(assembly.isVariable() && tname != null && tname.indexOf("$(") != -1) {
            tname = tname.substring(2, tname.length() - 1);
         }

         Assembly wsAssembly = ws == null ? null : ws.getAssembly(tname);
         InputVSAssemblyInfo info = (InputVSAssemblyInfo) assembly.getVSAssemblyInfo();
         // if not refreshing vs on input submit, refresh all input assemblies so
         // cascading parameters will work. (57856)
         refreshInput = refreshInput && !info.isRefresh();

         if(wbox != null && (wsAssembly instanceof VariableAssembly) &&
            hints[i] ==VSAssembly.OUTPUT_DATA_CHANGED)
         {
            wsOutputDataChanged = true;
         }
         else {
            // @by stephenwebster, For Bug #1726, remove the synchronize
            // on the VSAQueryLock.  getVGraphPair.init uses graphLock and
            // the VSAQueryLock is obtained after it to maintain correct
            // locking order.  Any calls to get data from a graph most likely
            // should be routed through the VGraphPair instead of getting it
            // direct.  I tested a similar asset related to bug1350539979627
            // and could not reproduce it.  This change is commented out below.

            //synchronized(box.getVSAQueryLock()) {
            // here may be cause fire command, AddVSObjectCommand for chart,
            // then GetChartAreaEvent will be fired in flex, VSEventUtil.execte
            // may cause ViewsheetSandbox.cancel, if current time, the chart
            // is in get data from GetChartAreaEvent, will cause no data returned
            // see bug1350539979627

            // @by larryl, should not be necessary to reset, optimization
            // Bug #24751, some dependency assemblies don't refresh after applying selection
            if(info.isSubmitOnChange() && info.isRefresh()) {
               box.reset(clist);
               vsObjectService.execute(
                  rvs, assemblyNames[i], linkUri, hints[i] | VSAssembly.VIEW_CHANGED, dispatcher);
            }
         }
      }

      if(wsOutputDataChanged) {
         wbox.setIgnoreFiltering(false);
         placeholderService.refreshEmbeddedViewsheet(rvs, linkUri, dispatcher);
         box.resetRuntime();
         // @by stephenwebster, For Bug #6575
         // refreshViewsheet() already calls reset() making this call redundant
         // It also has a side-effect of making the viewsheet load twice.
         // I double checked bug1391802567612 and it seems like it
         // is working as expected
         // box.reset(clist);
         placeholderService.refreshViewsheet(
            rvs, rvs.getID(), linkUri, dispatcher, false, true, true, clist);
      }

      for(int i = 0; i < assemblies.length; i ++) {
         InputVSAssembly assembly = assemblies[i];
         Object selectedObject = selectedObjects[i];
         Object[] values;

         if(selectedObject == null) {
            values = null;
         }
         else if(selectedObject.getClass().isArray()) {
            values = (Object[]) selectedObject;
         }
         else if(selectedObject instanceof java.util.List) {
            values = ((java.util.List) selectedObject).toArray();
         }
         else {
            values = new Object[] { selectedObject };
         }

         String type = assembly.getDataType();
         objs[i] = values == null || values.length == 0 ? null : Tool.getData(type, values[0]);
         InputVSAssemblyInfo info = (InputVSAssemblyInfo) assembly.getVSAssemblyInfo();

         // @davidd bug1364406849572, refactored processing of shared filters to
         // external and local.
         vsObjectService.processExtSharedFilters(assembly, hints[i], rvs, principal, dispatcher);
         box.processSharedFilters(assembly, clist, true);
      }

      // @by ankitmathur, Fix Bug #4211, Use the old VSCrosstabInfo's to sync
      // new TableDataPaths.
      // this should happen before table is refreshed
      for(Assembly cassembly : vs.getAssemblies()) {
         if(cassembly instanceof CrosstabVSAssembly) {
            try {
               box.updateAssembly(cassembly.getAbsoluteName());
               CrosstabVSAssembly cross = (CrosstabVSAssembly) cassembly;
               CrosstabVSAssemblyInfo ocinfo = (CrosstabVSAssemblyInfo) oldCrosstabInfo.get(
                  cassembly.getName());
               FormatInfo finfo = cross.getFormatInfo();
               CrosstabVSAssemblyInfo ncinfo = (CrosstabVSAssemblyInfo) cross.getVSAssemblyInfo();

               boolean allAggregateChanges = Arrays.stream(objs)
                  .filter(Objects::nonNull)
                  .map(Object::toString)
                  .allMatch(objValue -> isAggregateChange(ncinfo.getVSCrosstabInfo(), objValue));

               if(allAggregateChanges) {
                  continue;
               }

               TableHyperlinkAttr hyperlink = ncinfo.getHyperlinkAttr();
               TableHighlightAttr highlight = ncinfo.getHighlightAttr();

               if(finfo != null) {
                  synchronized(finfo.getFormatMap()) {
                     VSUtil.syncCrosstabPath(cross, ocinfo, false, finfo.getFormatMap(), true);
                  }
               }

               if(hyperlink != null) {
                  VSUtil.syncCrosstabPath(cross, ocinfo, false, hyperlink.getHyperlinkMap(), true);
               }

               if(highlight != null) {
                  VSUtil.syncCrosstabPath(cross, ocinfo, false, highlight.getHighlightMap(), true);
               }
            }
            catch(Exception ex) {
               LOG.warn("Failed to sync Crosstab paths", ex);
            }
         }
      }

      for(int i = 0; i < assemblies.length; i ++) {
         InputVSAssembly assembly = assemblies[i];
         InputVSAssemblyInfo info = (InputVSAssemblyInfo) assembly.getVSAssemblyInfo();

         // fix bug1368262989004, fix this bug same as bug1366884826731, now
         // no matter process share filter whether success or not, we should
         // also execute, or some dependency assembly will not refresh.
         if(info.isSubmitOnChange()) {
            vsObjectService.execute(rvs, assembly.getName(), linkUri, clist, dispatcher, true);
            vsObjectService.layoutViewsheet(rvs, linkUri, dispatcher);
         }
      }

      if(refreshInput) {
         for(Assembly assembly : vs.getAssemblies()) {
            if(assembly instanceof InputVSAssembly) {
               box.executeView(assembly.getAbsoluteName(), true);
               placeholderService.refreshVSAssembly(rvs, (VSAssembly) assembly, dispatcher);
            }
         }
      }
   }

   private Map<String, VSAssemblyInfo> getOldCrosstabInfo(Principal principal) throws Exception {
      // @by ankitmathur, Fix Bug #4211, Need to maintain the old instances of
      // all VSCrosstabInfo's which can be used to sync the new/updated
      // TableDataPaths after the assembly is updated.
      RuntimeViewsheet rvs =
         vsObjectService.getRuntimeViewsheet(runtimeViewsheetRef.getRuntimeId(), principal);
      Viewsheet vs = rvs.getViewsheet();
      Map<String, VSAssemblyInfo> oldCrosstabInfo = new HashMap<>();

      if(vs == null) {
         return oldCrosstabInfo;
      }

      Assembly[] assemblyList = vs.getAssemblies();

      for(Assembly casembly : assemblyList) {
         if(casembly instanceof CrosstabVSAssembly) {
            oldCrosstabInfo.put(casembly.getName(), (VSAssemblyInfo)
               ((CrosstabVSAssembly) casembly).getVSAssemblyInfo().clone());
         }
      }

      return oldCrosstabInfo;
   }

   /**
    * Get the rows of a column.
    * @param rvs        Runtime Viewsheet
    * @param table      the table
    * @param column     the column
    * @param principal  the principal user
    * @return the rows
    */
   public String[] getColumnRows(RuntimeViewsheet rvs, String table,
                                 String column, Principal principal)
   {
      Viewsheet vs = rvs.getViewsheet();
      String[] rows = new String[0];

      if(vs == null || column == null) {
         return rows;
      }

      Worksheet ws = vs.getBaseWorksheet();

      if(ws != null && VSEventUtil.checkBaseWSPermission(
         vs, principal, viewsheetService.getAssetRepository(), ResourceAction.READ))
      {
         TableAssembly tableAssembly = (TableAssembly) ws.getAssembly(table);

         if(tableAssembly instanceof SnapshotEmbeddedTableAssembly) {
            XSwappableTable stable = ((SnapshotEmbeddedTableAssembly)tableAssembly).getTable();
            ColumnRef colRef = new ColumnRef(new AttributeRef(null, column));
            int col = AssetUtil.findColumn(stable, colRef);

            if(col != -1) {
               rows = new String[stable.getRowCount()];

               for(int i = stable.getHeaderRowCount(); i < stable.getRowCount(); i++) {
                  rows[i] = Tool.toString(stable.getObject(i, col));
               }
            }
         }
         else if(tableAssembly instanceof EmbeddedTableAssembly) {
            XEmbeddedTable data =
               VSEventUtil.getVSEmbeddedData((EmbeddedTableAssembly) tableAssembly);
            ColumnRef colRef = new ColumnRef(new AttributeRef(null, column));
            int col = AssetUtil.findColumn(data, colRef);

            if(col != -1) {
               rows = new String[data.getRowCount()];

               for(int i = data.getHeaderRowCount(); i < data.getRowCount(); i++) {
                  rows[i] = Tool.toString(data.getObject(i, col));
               }
            }
         }
      }

      return rows;
   }

   /**
    * Get the Column Selection for a table, Mimic of GetColumnSelectionEvent
    * @param rvs                    Runtime Viewsheet instance
    * @param table                  the table
    * @param assembly               assembly name
    * @param vsName                 viewsheet name
    * @param dimensionOf            dimension name
    * @param embedded               get embedded tables
    * @param measureOnly            only get measures
    * @param includeCalc            include calc columns
    * @param ignoreToCheckPerm      ignore check ?
    * @param includeAggregate       include aggregate columns
    * @param excludeAggregateCalc   exclude aggregate calc columns
    * @param principal              principal user
    * @return the ColumnSelection
    * @throws Exception if can't retrieve the columns
    */
   public ColumnSelection getTableColumns(RuntimeViewsheet rvs, String table, String assembly,
                                          String vsName, String dimensionOf, boolean embedded,
                                          boolean measureOnly, boolean includeCalc,
                                          boolean ignoreToCheckPerm, boolean includeAggregate,
                                          boolean excludeAggregateCalc, Principal principal)
      throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return new ColumnSelection();
      }

      if(vsName == null && assembly != null && assembly.indexOf('.') > 0) {
         vsName = assembly.substring(0, assembly.lastIndexOf('.'));
      }

      // get the real viewsheet name, if this event is fired by an embedded vs
      if(vsName != null) {
         vs = (Viewsheet) vs.getAssembly(vsName);
      }

      if(vs == null) {
         return new ColumnSelection();
      }

      Worksheet ws = vs.getBaseWorksheet();
      ColumnSelection selection;

      if(ws != null && ignoreToCheckPerm ||
         VSEventUtil.checkBaseWSPermission(vs, principal, viewsheetService.getAssetRepository(), ResourceAction.READ))
      {
         Assembly wsobj = ws.getAssembly(table);

         if(!(wsobj instanceof TableAssembly)) {
            return new ColumnSelection();
         }

         TableAssembly tableAssembly = (TableAssembly) wsobj;
         TableAssembly tableAssembly0 = tableAssembly;

         while(tableAssembly0 instanceof MirrorTableAssembly) {
            tableAssembly0 =
               ((MirrorTableAssembly) tableAssembly0).getTableAssembly();
         }

         // still get the original column selection, otherwise the column selection will not match
         // the runtime column selection and things like TableVSAQuery.getQueryData() may fail
         selection = table == null ? new ColumnSelection() :
            (ColumnSelection) tableAssembly.getColumnSelection(true).clone();
         XUtil.addDescriptionsFromSource(tableAssembly0, selection);
         AggregateInfo dimonly = null;

         if(dimensionOf != null) {
            Assembly obj = vs.getAssembly(dimensionOf);

            if(obj instanceof CubeVSAssembly) {
               dimonly = ((CubeVSAssembly) obj).getAggregateInfo();
            }
         }

         for(int i = selection.getAttributeCount() - 1; i >= 0; i--) {
            ColumnRef ref = (ColumnRef) selection.getAttribute(i);
            boolean excludeCalc = !includeCalc && ref instanceof CalculateRef;
            boolean excludeAggCalc = excludeAggregateCalc && VSUtil.isAggregateCalc(ref);

            if(VSUtil.isPreparedCalcField(ref) || embedded && ref.isExpression() || measureOnly &&
               (ref.getRefType() & DataRef.CUBE) == DataRef.CUBE &&
               (ref.getRefType() & DataRef.MEASURE) != DataRef.MEASURE ||
               dimonly != null && !isDimensionRef(ref, dimonly) ||
               excludeCalc || excludeAggCalc ||
               ref instanceof CalculateRef && ((CalculateRef) ref).isDcRuntime())
            {
               selection.removeAttribute(i);
            }
         }

         if(includeCalc) {
            CalculateRef[] calcs = vs.getCalcFields(table);

            if(calcs != null && calcs.length > 0) {
               for(CalculateRef ref : calcs) {
                  if(VSUtil.isPreparedCalcField(ref)) {
                     continue;
                  }

                  if(!ref.isDcRuntime() && (!excludeAggregateCalc || ref.isBaseOnDetail()) &&
                     !(selection.containsAttribute(ref) ||
                        selection.getAttribute(ref.getName()) != null))
                  {
                     selection.addAttribute(ref);
                  }
               }
            }
         }

         selection = VSUtil.getVSColumnSelection(selection);
         selection = VSUtil.sortColumns(selection);

         if(includeAggregate) {
            ColumnSelection aggregate = VSEventUtil.getAggregateColumnSelection(vs, table);

            for(int i = 0; i < aggregate.getAttributeCount(); i++) {
               selection.addAttribute(aggregate.getAttribute(i), false);
            }
         }

         AssetQuery query = AssetUtil.handleMergeable(rvs.getRuntimeWorksheet(), tableAssembly);

         if(tableAssembly != null && query != null) {
            selection.setProperty("sqlMergeable", query.isQueryMergeable(false));
         }
      }
      else {
         selection = new ColumnSelection();
      }

      return selection;
   }

   /**
    * Check if ref is a group in the aggregate info.
    * @param ref     The column ref
    * @param ainfo   the aggregate info
    * @return if the ref is a dimension
    */
   private boolean isDimensionRef(ColumnRef ref, AggregateInfo ainfo) {
      // ignore table name in vs binding
      DataRef ref2 = new AttributeRef(ref.getAttribute());
      boolean dim = ainfo.containsGroup(new GroupRef(ref2));

      if(dim) {
         return true;
      }

      boolean agg = ainfo.containsAggregate(ref2);
      return !agg && !VSEventUtil.isMeasure(ref);
   }

   /**
    * Called by selection list editor
    * @param rvs        RuntimeViewsheet instance
    * @param table      table
    * @param principal  the principal user
    * @return the column selection
    * @throws Exception if can't retrieve columns
    */
   public ColumnSelection getTableColumns(RuntimeViewsheet rvs, String table,
                                          Principal principal)
      throws Exception
   {
      return getTableColumns(rvs, table, null, null, null, false,
                             false, false, false, false, false, principal);
   }

   /**
    * Called by data input pane
    * @param rvs        RuntimeViewsheet instance
    * @param table      table
    * @param embedded   get embedded tables
    * @param principal  the principal user
    * @return the column selection
    * @throws Exception if can't retrieve columns
    */
   public ColumnSelection getTableColumns(RuntimeViewsheet rvs, String table,
                                          boolean embedded, Principal principal)
      throws Exception
   {
      return getTableColumns(rvs, table, null, null, null, embedded,
                             false, false, false, false, false, principal);
   }

   /**
    * Get tables available in viewsheet as tree. Mimic of GetTablesEvent for data input
    * @param rvs        the runtime viewsheet
    * @param principal  the principal user
    * @return the tree model for the tables
    */
   public TreeNodeModel getInputTablesTree(RuntimeViewsheet rvs, boolean onlyVars,
                                           Principal principal)
   {
      List[] lists = getInputTables(rvs, true, onlyVars, principal);
      List<TreeNodeModel> tableChildren = new ArrayList<>();
      List<TreeNodeModel> variableChildren = new ArrayList<>();

      if(lists != null) {
         for(Assembly table : (List<Assembly>) lists[0]) {
            String name = table.getName();
            String normalizedName = name;
            String description = ((TableAssembly) table).getDescription() != null ?
               ((TableAssembly) table).getDescription() : "";

            if(name.endsWith("_VSO")) {
               normalizedName = name.substring(0, name.length() - 4);
            }

            normalizedName = VSUtil.stripOuter(name);

            tableChildren.add(TreeNodeModel.builder()
                                 .label(Tool.localize(normalizedName))
                                 .data(name)
                                 .leaf(true)
                                 .tooltip(description)
                                 .type("table")
                                 .build());
         }

         for(VariableEntry ve : (List<VariableEntry>) lists[1]) {
            variableChildren.add(TreeNodeModel.builder()
               .label(Tool.localize(ve.label != null && ve.label.length() != 0 ? ve.label :
                ve.value))
               .data("$(" + ve.value + ")")
               .leaf(true)
               .type("variable")
               .build());
         }
      }

      List<TreeNodeModel> treeChildren = new ArrayList<>();

      treeChildren.add(TreeNodeModel.builder()
                          .label(Catalog.getCatalog().getString("None"))
                          .data("")
                          .leaf(true)
                          .type("none")
                          .build());

      if(!onlyVars) {
         treeChildren.add(TreeNodeModel.builder()
                             .label(Catalog.getCatalog().getString("Tables"))
                             .data("Tables")
                             .leaf(false)
                             .type("folder")
                             .children(tableChildren)
                             .build());
      }

      treeChildren.add(TreeNodeModel.builder()
                          .label(Catalog.getCatalog().getString("Variables"))
                          .data("Variables")
                          .leaf(false)
                          .type("folder")
                          .children(variableChildren)
                          .build());

      return TreeNodeModel.builder().children(treeChildren).build();
   }

   /**
    * Get tables available in viewsheet arrays. Mimic of GetTablesEvent for selection list
    * @param rvs        the runtime viewsheet
    * @param principal  the principal user
    * @return the tree model for the tables
    */
   public List<String[]> getInputTablesArray(RuntimeViewsheet rvs, Principal principal) {
      List[] lists = getInputTables(rvs, false, false, principal);
      List<String> tables = new ArrayList<>();
      List<String> descriptions = new ArrayList<>();
      List<String> localizedTables = new ArrayList<>();
      List<String[]> result = new ArrayList<>();
      Viewsheet vs = rvs.getViewsheet();
      AssetEntry baseEntry = vs.getBaseEntry();

      if(lists != null) {
         for(Assembly table : (List<Assembly>) lists[0]) {
            String name = table.getName();
            String normalizedName = name;
            String description = baseEntry.isLogicModel() || baseEntry.isQuery() ?
               baseEntry.getProperty("Tooltip") : ((TableAssembly) table).getDescription();

            tables.add(name);
            descriptions.add(description);
            localizedTables.add(Tool.localize(normalizedName));
         }
      }

      result.add(tables.toArray(new String[0]));
      result.add(localizedTables.toArray(new String[0]));
      result.add(descriptions.toArray(new String[0]));
      return result;
   }

   /**
    * Get tables available in viewsheet. Mimic of GetTablesEvent
    * @param rvs        the runtime viewsheet
    * @param embedded   only get embedded tables
    * @param onlyVars   only get variables
    * @param principal  the principal user
    * @return the tables and variable tables lists
    */
   public List[] getInputTables(RuntimeViewsheet rvs, boolean embedded,
                                boolean onlyVars, Principal principal)
   {
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return null;
      }

      Worksheet ws = vs.getBaseWorksheet();
      List<Assembly> tableList =  new ArrayList<>();
      List<VariableEntry> variableList =  new ArrayList<>();
      List[] result = {tableList, variableList};

      if(ws != null && VSEventUtil.checkBaseWSPermission(vs, principal,
                                           viewsheetService.getAssetRepository(), ResourceAction.READ))
      {
         Assembly[] assemblies = ws.getAssemblies();

         for(Assembly assembly : assemblies) {
            if(!assembly.isVisible()) {
               continue;
            }

            if(embedded) {
               if(!onlyVars && assembly instanceof TableAssembly) {
                  TableAssembly tableAssembly = (TableAssembly) assembly;
                  TableAssembly embeddedAssembly = getBaseEmbedded(tableAssembly);

                  if(embeddedAssembly != null && tableAssembly.isVisibleTable()) {
                     tableList.add(embeddedAssembly);

                  }
               }

               if(assembly instanceof VariableAssembly) {
                  VariableAssembly va = (VariableAssembly) assembly;
                  VariableEntry ve = new VariableEntry(va.getVariable().getAlias(),
                                                       va.getVariable().getName());

                  if(!variableList.contains(ve)) {
                     variableList.add(ve);
                  }
               }
            }
            else if(assembly instanceof TableAssembly) {
               if(((TableAssembly) assembly).isVisibleTable()) {
                  tableList.add(assembly);
               }
            }
         }

         if(embedded) {
            UserVariable[] uvars = ws.getAllVariables();

            for(UserVariable uvar : uvars) {
               if(uvar != null) {
                  VariableEntry ve = new VariableEntry(uvar.getAlias(), uvar.getName());

                  if(!variableList.contains(ve)) {
                     variableList.add(ve);
                  }
               }
            }
         }

         tableList.sort(new VSEventUtil.WSAssemblyComparator(ws));
      }

      if(embedded) {
         Collections.sort(variableList);
      }

      return result;
   }

   /**
    * Check if the table is embedded.
    * @param table the table to check
    * @return the embedded table assembly
    */
   private TableAssembly getBaseEmbedded(TableAssembly table) {
      if(table == null) {
         return null;
      }

      if(VSEventUtil.isEmbeddedDataSource(table) &&
         !(table instanceof SnapshotEmbeddedTableAssembly))
      {
         return table;
      }

      TableAssembly embedded = null;
      String tname = table.getName();

      if(table instanceof EmbeddedTableAssembly) {
         embedded = table;
      }
      else if(table instanceof MirrorTableAssembly) {
         MirrorTableAssembly mtable = (MirrorTableAssembly) table;
         String bname = mtable.getAssemblyName();

         if((bname.equals(tname + "_VSO") || bname.equals(tname + "_O")) &&
            mtable.getTableAssembly() instanceof EmbeddedTableAssembly)
         {
            embedded = mtable.getTableAssembly();
         }
      }

      if(embedded != null && !"true".equals(embedded.getProperty("auto.generate")) &&
         !(embedded instanceof SnapshotEmbeddedTableAssembly))
      {
         return embedded;
      }

      return null;
   }

   public ListBindingInfo updateBindingInfo(ListBindingInfo info, String column, String value,
                                            RuntimeViewsheet rvs, Principal principal)
      throws Exception
   {
      String table = info.getTableName();

      if(table != null && !table.isEmpty() && column != null &&
         !column.isEmpty() && value != null && !value.isEmpty())
      {
         ColumnSelection selection = this.getTableColumns(rvs, table, principal);
         boolean colFound = false;
         boolean valFound = false;

         for(int i = 0; i < selection.getAttributeCount(); i++) {
            DataRef ref = selection.getAttribute(i);

            if(!colFound && column.equals(ref.getName())) {
               info.setLabelColumn(ref);
               colFound = true;
            }

            if(!valFound && value.equals(ref.getName())) {
               info.setValueColumn(ref);
               valFound = true;
            }

            if(colFound && valFound) {
               break;
            }
         }
      }

      return info;
   }

   public class VariableEntry implements Comparable{
      public VariableEntry(String label, String value) {
         this.label = label;
         this.value = value;
      }

      @Override
      public int compareTo(Object o) {
         VariableEntry that = (VariableEntry) o;

         if(this.label == null) {
            if(that.label == null) {
               return 0;
            }

            return -1;
         }
         else if(that.label == null) {
            return 1;
         }

         return this.label.compareTo(that.label);
      }

      @Override
      public boolean equals(Object o) {
         if(this == o) {
            return true;
         }

         if(o == null || getClass() != o.getClass()) {
            return false;
         }

         VariableEntry that = (VariableEntry) o;

         return this.value.equals(that.value);
      }

      @Override
      public int hashCode() {
         int result = label.hashCode();
         result = 31 * result + value.hashCode();
         return result;
      }

      private String label;
      private String value;
   }

   /**
    * Update the model with the data table related info
    * @param info      the assembly info
    * @param paneModel the model to update
    */
   public void getTableName(InputVSAssemblyInfo info, DataInputPaneModel paneModel) {
      String table = info.getTableName();

      if(table == null) {
         paneModel.setTableLabel(null);
         paneModel.setTable(null);
         paneModel.setVariable(info.isVariable());
         return;
      }

      if(table.startsWith("$(") && table.endsWith(")")) {
         table = table.substring(2, table.length() - 1);
      }

      Worksheet ws = info.getViewsheet().getBaseWorksheet();
      Assembly wsAssembly = ws == null ? null : ws.getAssembly(table);

      if(ws != null && wsAssembly == null && table.endsWith("_O")) {
         table = VSUtil.stripOuter(table);
         wsAssembly = ws.getAssembly(table);
      }

      String tableLabel = table; // for handle alias

      if(info.isVariable()) {
         VariableAssembly variableAssembly = (VariableAssembly) wsAssembly;

         //variable might not be in the baseworksheet
         if(variableAssembly != null) {
            String alias = variableAssembly.getVariable().getAlias();
            tableLabel = (alias == null || alias.isEmpty()) ?
               variableAssembly.getVariable().getName() : alias;
         }
      }
      else {
         tableLabel = VSUtil.stripOuter(tableLabel);
         tableLabel = Tool.localize(tableLabel);
      }

      paneModel.setTableLabel(tableLabel);
      paneModel.setTable(table);
      paneModel.setVariable(info.isVariable());
   }

   /**
    * Verify whether the input value is a part of the runtime Aggregates for the
    * Crosstab. If so, no need to sync the old TableDataPath.
    *
    * @param ncinfo VSCrosstabInfo which is generated after the assembly has
    *               been updated.
    * @param changedValue The new input value.
    *
    * @return <tt>true</tt> if the new input value is a runtime aggregate.
    */
   private boolean isAggregateChange(VSCrosstabInfo ncinfo, String changedValue) {
      if(ncinfo == null || ncinfo.getRuntimeAggregates() == null)
      {
         return false;
      }

      DataRef[] nAggRefs = ncinfo.getRuntimeAggregates();

      try {
         for(DataRef nagg : nAggRefs) {
            if(nagg.getName().contains(changedValue)) {
               return true;
            }
         }
      }
      catch(Exception ex) {
         //ignore the exception
      }

      return false;
   }

   private void refreshVariable(VSAssembly assembly, AssetQuerySandbox wbox, Worksheet ws,
                                Viewsheet vs) throws Exception
   {
      if(!(assembly instanceof InputVSAssembly) || wbox == null || ws == null) {
         return;
      }

      InputVSAssembly iassembly = (InputVSAssembly) assembly;
      Object cdata;
      Object mdata = null;

      if(iassembly instanceof SingleInputVSAssembly) {
         cdata = ((SingleInputVSAssembly) iassembly).getSelectedObject();
      }
      else if(iassembly instanceof CompositeInputVSAssembly) {
         Object[] objs =
            ((CompositeInputVSAssembly) iassembly).getSelectedObjects();
         cdata = objs == null || objs.length == 0 ? null : objs[0];
         mdata = objs == null || objs.length <= 1 ? null : objs;
      }
      else {
         throw new RuntimeException("Unsupported assembly found: " +
                                       assembly);
      }

      String tname = iassembly.getTableName();
      VariableTable vt = wbox.getVariableTable();

      if(iassembly.isVariable() && tname != null && tname.indexOf("$(") != -1) {
         tname = tname.substring(2, tname.length() - 1);
      }

      if(tname != null) {
         Assembly ass = ws.getAssembly(tname);

         if(ass instanceof VariableAssembly) {
            vt.put(tname, mdata == null ? cdata : mdata);
            wbox.refreshVariableTable(vt);
         }
         else {
            ArrayList<UserVariable> variableList = new ArrayList<>();

            Viewsheet.mergeVariables(variableList, ws.getAllVariables());
            Viewsheet.mergeVariables(variableList, vs.getAllVariables());

            for(UserVariable var : variableList) {
               if(var != null && tname.equals(var.getName())) {
                  vt.put(tname, mdata == null ? cdata : mdata);
                  break;
               }
            }

            wbox.refreshVariableTable(vt);
         }
      }
      else {
         vt.put(iassembly.getName(), mdata == null ? cdata : mdata);
         wbox.refreshVariableTable(vt);
      }
   }

   /**
    * Reset table metadata for any worksheet tables that are dependent on
    * the variable of the input assembly
    */
   private void resetTableMetadata(InputVSAssembly inputAssembly, Worksheet ws,
                                   ViewsheetSandbox box)
   {
      if(!inputAssembly.isVariable()) {
         return;
      }

      String varName = inputAssembly.getTableName();

      if(varName != null && varName.contains("$(")) {
         varName = varName.substring(2, varName.length() - 1);
      }

      for(Assembly wsAssembly : ws.getAssemblies()) {
         if(!(wsAssembly instanceof TableAssembly)) {
            continue;
         }

         TableAssembly tableAssembly = (TableAssembly) wsAssembly;

         // loop through the table's variables
         for(UserVariable var : tableAssembly.getAllVariables()) {

            // if it matches to the variable name of the input assembly then reset metadata
            if(Tool.equals(var.getName(), varName)) {
               box.resetTableMetaData(tableAssembly.getName());
            }
         }
      }
   }

   private final VSObjectService vsObjectService;
   private final PlaceholderService placeholderService;
   private final ViewsheetService viewsheetService;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private static final Logger LOG = LoggerFactory.getLogger(VSInputService.class);
}
