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
package inetsoft.web.composer.vs.objects.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.TableLens;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.*;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.composition.graph.VSDataSet;
import inetsoft.report.filter.Highlight;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.internal.graph.ChangeChartProcessor;
import inetsoft.report.internal.table.TableHighlightAttr;
import inetsoft.report.internal.table.TableHyperlinkAttr;
import inetsoft.report.lens.SubTableLens;
import inetsoft.report.script.viewsheet.ViewsheetScope;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssemblyInfo;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.XValueNode;
import inetsoft.uql.script.VariableScriptable;
import inetsoft.uql.util.XTableTableNode;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.*;
import inetsoft.util.script.ScriptEnv;
import inetsoft.util.script.ScriptException;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.VSObjectTreeNode;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.composer.vs.command.ExpandTreeNodesCommand;
import inetsoft.web.composer.vs.command.PopulateVSObjectTreeCommand;
import inetsoft.web.composer.vs.objects.command.RenameVSObjectCommand;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.controller.table.BaseTableController;
import inetsoft.web.viewsheet.service.*;
import inetsoft.web.vswizard.model.VSWizardConstants;
import inetsoft.web.vswizard.model.recommender.VSTemporaryInfo;
import inetsoft.web.vswizard.service.VSWizardTemporaryInfoService;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.security.Principal;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class VSObjectPropertyService {
   /**
    * Creates a new instance of <tt>VSObjectPropertyController</tt>.
    *
    * @param coreLifecycleService CoreLifecycleService instance
    * @param viewsheetService
    */
   @Autowired
   public VSObjectPropertyService(
      CoreLifecycleService coreLifecycleService,
      VSInputService vsInputService,
      VSObjectTreeService vsObjectTreeService,
      VSAssemblyInfoHandler infoHander,
      ViewsheetService viewsheetService,
      VSWizardTemporaryInfoService temporaryInfoService,
      VSCompositionService vsCompositionService,
      SharedFilterService sharedFilterService)
   {
      this.coreLifecycleService = coreLifecycleService;
      this.vsInputService = vsInputService;
      this.vsObjectTreeService = vsObjectTreeService;
      this.infoHander = infoHander;
      this.viewsheetService = viewsheetService;
      this.temporaryInfoService = temporaryInfoService;
      this.vsCompositionService = vsCompositionService;
      this.sharedFilterService = sharedFilterService;
   }

   public void editObjectProperty(RuntimeViewsheet rvs, VSAssemblyInfo info, String oldName,
                                  String newName, String linkUri, Principal user,
                                  CommandDispatcher commandDispatcher)
      throws Exception
   {
      editObjectProperty(rvs, info, oldName, newName, linkUri, user, commandDispatcher, true);
   }

   public void editObjectProperty(RuntimeViewsheet rvs, VSAssemblyInfo info, String oldName,
                                  String newName, String linkUri, Principal user,
                                  CommandDispatcher commandDispatcher, boolean propertyChanged)
      throws Exception
   {
      if(rvs == null || rvs.isDisposed()) {
         return;
      }

      Viewsheet vs = rvs.getViewsheet();
      Worksheet ws = vs.getBaseWorksheet();

      if(!Tool.equals(oldName, newName)) {
         String id = vs.getViewsheetInfo().getFilterID(oldName);
         vs.getViewsheetInfo().setFilterID(oldName, null);
         vs.getViewsheetInfo().setFilterID(newName, id);
      }

      //TODO pass confirmed/checktrap in
      boolean confirmed = false;
      boolean checkTrap = false;

      if(isTableModeChange(rvs, info, linkUri, commandDispatcher)) {
         LOG.warn("The table type is changed, its annotation will be lost");

         for(Assembly assembly : vs.getAssemblies()) {
            AssemblyInfo ainfo = assembly.getInfo();

            if(ainfo instanceof TipVSAssemblyInfo) {
               this.coreLifecycleService.refreshVSAssembly(rvs, ainfo.getAbsoluteName(),
                                                           commandDispatcher);
            }
         }

         return;
      }

      if(checkTipDependency(vs, info, new HashMap<>()) ||
         checkPopDependency(vs, info, new HashMap<>()))
      {
         this.coreLifecycleService.sendMessage(
            Catalog.getCatalog().getString("common.dependencyCycle"),
            MessageCommand.Type.ERROR, commandDispatcher);
         return;
      }

      if(info instanceof ListInputVSAssemblyInfo &&
         !checkInputList((ListInputVSAssemblyInfo) info, commandDispatcher))
      {
         return;
      }

      String name = newName == null ? oldName : newName;
      VSAssembly vsAssembly = vs.getAssembly(oldName);

      if(vsAssembly == null) {
         this.coreLifecycleService.sendMessage(
            Catalog.getCatalog().getString("viewer.viewsheet.editPropertyFailed"),
            MessageCommand.Type.ERROR, commandDispatcher);
         return;
      }

      VSAssemblyInfo oldInfo = vsAssembly.getVSAssemblyInfo();
      String oldPopComponent = oldInfo instanceof PopVSAssemblyInfo ?
         ((PopVSAssemblyInfo) oldInfo).getPopComponent() : null;

      boolean scriptChanged = false;

      // script has changed
      if(info != null && info.getScript() != null &&
         (oldInfo == null || !info.getScript().equals(oldInfo.getScript())) ||
         //Fixed bug #21516 that scriptEnableChanged should to be reset rt values.
         info.isScriptEnabled() != oldInfo.isScriptEnabled())
      {
         scriptChanged = true;

         try {
            // Bug #18613, #18826, #18870, #18868, #18867, #18866, #18865
            // when the script is change, reset the runtime values and clear the hidden actions
            info.resetRuntimeValues();
            info.getActionNames().clear();

            if(info instanceof ChartVSAssemblyInfo) {
               ((ChartVSAssemblyInfo) info).getVSChartInfo().clearRuntime();
            }
         }
         catch(Exception ex) {
            this.coreLifecycleService.sendMessage(
               Catalog.getCatalog().getString("viewer.viewsheet.scriptFailed",
               ex.getMessage()), MessageCommand.Type.CONFIRM, commandDispatcher);
            return;
         }
      }

      boolean renamed = false;

      if(!oldName.equals(newName)) {
         // rename all bind assemblies before rename assembly.
         renameAllBindSourceAssemblies(oldName, newName, vs, rvs, commandDispatcher);
         VSAssembly container = vsAssembly.getContainer();

         if(!renameAssembly(oldName, newName, container, rvs, commandDispatcher)) {
            this.coreLifecycleService.sendMessage(
               Catalog.getCatalog().getString("common.renameViewsheetFailed"),
               MessageCommand.Type.OK, commandDispatcher);
            return;
         }

         if(vsAssembly.getContainer() instanceof TabVSAssembly) {
            ((TabVSAssembly) vsAssembly.getContainer()).setSelectedValue(newName);
         }

         renamed = true;
         name = vsAssembly.getAbsoluteName();
         vsAssembly = vs.getAssembly(name);
         //refresh flyover/tips that use renamed object
         refreshFlyovers(newName, rvs, commandDispatcher);

         // set the start row as 0 after renaming.
         // because the web component will been rendered, all scroll info will been lost.
         if(vsAssembly instanceof TableDataVSAssembly) {
            ((TableDataVSAssembly) vsAssembly).setLastStartRow(0);
         }
      }

      //embedded viewsheet
      if(!(vsAssembly instanceof AbstractVSAssembly)) {
         vsAssembly.setVSAssemblyInfo(info);
         this.coreLifecycleService.refreshVSAssembly(rvs, vsAssembly.getAbsoluteName(),
                                                     commandDispatcher, true);
         return;
      }

      AbstractVSAssembly assembly = (AbstractVSAssembly) vsAssembly;
      VSAssemblyInfo oinfo = assembly.getVSAssemblyInfo().clone();
      VSModelTrapContext context = new VSModelTrapContext(rvs);
      AbstractModelTrapContext.TrapInfo tinfo = context.isCheckTrap() ?
         context.checkTrap(oinfo, info) : null;

      checkFlyoverChanges(rvs, oinfo, info, linkUri, commandDispatcher);

      if(!confirmed && checkTrap) {
         if(tinfo != null && tinfo.showWarning()) {
            //TODO show trap warning message
            return;
         }
      }

      List<Assembly> scriptObjs = new ArrayList<>();
      String oscript = oinfo.getScript();
      int hintScript = 0;

      if(assembly instanceof SelectionVSAssembly || assembly instanceof OutputVSAssembly) {
         String nscript = info.getScript();

         // if script contains binding change, re-process data
         if(nscript != null && !Objects.equals(nscript, oscript) &&
            (nscript.contains("query") || nscript.contains("fields")))
         {
            hintScript = VSAssembly.INPUT_DATA_CHANGED;
            hintScript |= VSAssembly.BINDING_CHANGED;
         }
      }

      if(oscript != null && !oscript.equals(info.getScript())) {
         for(Assembly aobj : vs.getAssemblies()) {
            String aname = aobj.getName();

            if(!oscript.contains(aname)) {
               continue;
            }

            scriptObjs.add(aobj);
            ((VSAssembly) aobj).getVSAssemblyInfo().resetRuntimeValues();
         }
      }

      //clear the values set from script so new settings can be applied
      for(Object dval: assembly.getDynamicValues()) {
         ((DynamicValue) dval).setRValue(null);
      }

      /* TODO get previous scroll index and set back to the scroll index
      String idxStr = (String) get("oindex");

      if(idxStr != null) {
         int oindex = Integer.parseInt(idxStr); // old scroll index

         if(oindex > 0) {
            command.addCommand(new ResetDataIndexCommand(name, oindex));
         }
      }
      */

      AssemblyRef[] irefs = vs.getDependings(assembly.getAssemblyEntry());
      AssemblyRef[] orefs = vs.getOutputDependings(assembly.getAssemblyEntry());
      AssemblyRef[] refs = new AssemblyRef[irefs.length + orefs.length];
      InputVSAssembly oassembly = null;

      System.arraycopy(irefs, 0, refs, 0, irefs.length);
      System.arraycopy(orefs, 0, refs, irefs.length, orefs.length);

      // reset embedded data for might be changed to unbound
      if(assembly instanceof InputVSAssembly) {
         oassembly = (InputVSAssembly) assembly.clone();
      }

      String target = null;

      if(assembly instanceof SelectionVSAssembly) {
         target = assembly.getTableName();
      }

      if(assembly instanceof ChartVSAssembly) {
         VSChartInfo oc = ((ChartVSAssemblyInfo) oinfo).getVSChartInfo();
         VSChartInfo nc = ((ChartVSAssemblyInfo) info).getVSChartInfo();
         new ChangeChartProcessor().fixNamedGroup(oc, nc);
      }

      int hint = 0;

      hint = assembly.setVSAssemblyInfo(info);
      // if script contains binding change, re-process data
      hint = assembly instanceof SelectionVSAssembly ? (hint | hintScript) : hint;

      /* TODO some logic relevant to hyperlink dialog  charts, decide if need to keep when implementing chart property change
      boolean viewOnly = "true".equals(get("viewOnly"));

      if(viewOnly) {
         restoreViewInfo(assembly.getVSAssemblyInfo(), oinfo);
      }
      */

      boolean infoChanged = (hint & VSAssembly.INPUT_DATA_CHANGED) ==
                            VSAssembly.INPUT_DATA_CHANGED ||
                            (hint & VSAssembly.OUTPUT_DATA_CHANGED) ==
                            VSAssembly.OUTPUT_DATA_CHANGED;

      if(infoChanged) {
         assembly.checkDependency();
      }

      // here we clear the runtime condition of the old target table.
      // There is a very special case that the selection bound to a new
      // table which is the mirror table of the old bound table
      if(target != null && infoChanged) {
         TableAssembly tassembly = ws == null ? null : (TableAssembly) ws.getAssembly(target);

         if(tassembly != null) {
            tassembly.setPreRuntimeConditionList(null);
            tassembly.setPostRuntimeConditionList(null);
         }
      }

      if(infoChanged && assembly instanceof TableDataVSAssembly) {
         ((TableDataVSAssemblyInfo) assembly.getVSAssemblyInfo()).resetSizeInfo();
      }

      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(box == null) {
         return;
      }

      //first process selection by script
      if(oscript == null && assembly instanceof SelectionVSAssembly) {
         box.executeScript(assembly);
      }

      // reset embedded data for the table/row/cell might be changed
      if(infoChanged && assembly instanceof InputVSAssembly) {
         new InputVSAQuery(box, oassembly.getName()).resetEmbeddedData(false, oassembly);
      }

      if(assembly instanceof ChartVSAssembly) {
         ChartDescriptor ode = ((ChartVSAssemblyInfo) oinfo).getChartDescriptor();
         ChartDescriptor nde = ((ChartVSAssemblyInfo) info).getChartDescriptor();

         VSDataSet lens = (VSDataSet) box.getData(assembly.getAbsoluteName());
         VSChartInfo cinfo = ((ChartVSAssemblyInfo) info).getVSChartInfo();
         VSDataRef[] drefs = cinfo.getFields();

         if(drefs != null && drefs.length > 0 && (drefs[0].getRefType() & DataRef.CUBE) != 0) {
            for(int i = 0; i < drefs.length; i++) {
               if(!(drefs[i] instanceof VSDimensionRef)) {
                  continue;
               }

               VSDimensionRef vref = (VSDimensionRef) drefs[i];

               if(vref.getGroupType() == null) {
                  continue;
               }

               String header = vref.getGroupColumnValue();

               if(lens != null && lens.indexOfHeader(header) < 0) {
                  header = NamedRangeRef.getName(header, Integer.parseInt(vref.getGroupType()));
               }

               processNamedGroup(lens, header, (SNamedGroupInfo) vref.getNamedGroupInfo(), cinfo);
            }
         }

         if(infoChanged || ode.getPlotDescriptor().isValuesVisible() !=
            nde.getPlotDescriptor().isValuesVisible())
         {
            box.updateAssembly(assembly.getAbsoluteName());
            this.coreLifecycleService.refreshVSAssembly(rvs, assembly, commandDispatcher);
         }
      }

      if(assembly instanceof CalendarVSAssembly) {
         CalendarVSAssembly cassembly = (CalendarVSAssembly) assembly;

         if(cassembly.getDataRef() == null) {
            cassembly.setRange(null);
         }
      }

      ChangedAssemblyList clist = this.coreLifecycleService.createList(true, commandDispatcher,
                                                                       rvs, linkUri);
      clist.setObjectPropertyChanged(true);

      try {
         /* TODO set/get clear cache attribute
         if("true".equals(get("clearCache"))) {
            box.resetDataMap(name);
         }
         */

         hint = assembly instanceof OutputVSAssembly ? (hint | hintScript) : hint;

         if(assembly instanceof OutputVSAssembly) {
            OutputVSAssemblyInfo outputInfo = (OutputVSAssemblyInfo) assembly.getInfo();
            BindingInfo binding = outputInfo.getBindingInfo();
            boolean noBinding = binding == null || binding.isEmpty();

            if(noBinding) {
               ((OutputVSAssembly) assembly).setValue(null);
            }
         }

         box.getVariableTable().remove(XQuery.HINT_MAX_ROWS);
         box.processChange(name, hint, clist);

         // Propagate Calendar Period change
         if(assembly instanceof CalendarVSAssembly) {
            this.sharedFilterService.processExtSharedFilters(
               assembly, hint, rvs, user, commandDispatcher);
         }

         // if just properties changed, can direct sync crosstab path,
         // and execute will return tablelens with right format after sync path.
         if(propertyChanged) {
            processCrosstab(assembly, oinfo, propertyChanged);
         }

         this.coreLifecycleService.execute(rvs, name, linkUri, clist, commandDispatcher, true);

         // if not property change, then need runtime fields to sync path, so need to do sync
         // logic after execute assembly, and reload tablelens to get right tablelens.
         if(!propertyChanged && assembly instanceof CrosstabVSAssembly) {
            processCrosstab(assembly, oinfo, propertyChanged);
            int mode = 0;
            int num = 100;
            int start = ((TableDataVSAssembly) assembly).getLastStartRow();
            rvs.getViewsheetSandbox().resetDataMap(name);
            BaseTableController.loadTableData(rvs, name, mode, start, num, linkUri, commandDispatcher, true);
         }

         if(assembly.getVSAssemblyInfo() instanceof PopVSAssemblyInfo) {
            PopVSAssemblyInfo popVSAssemblyInfo = (PopVSAssemblyInfo) assembly.getVSAssemblyInfo();
            String popComponent = popVSAssemblyInfo.getPopComponent();

            if(!StringUtils.equals(oldPopComponent, popComponent)) {
               refreshTable(rvs, popComponent, commandDispatcher);
               refreshTable(rvs, oldPopComponent, commandDispatcher);
            }
         }
      }
      catch(ConfirmException ex) {
         throw ex;
      }
      catch(RuntimeException ex) {
         LOG.warn("Failed to process property change: " + ex);

         // when the execution fails (e.g. script compilation), we should
         // just show the error and let the change be saved. otherwise it's
         // like an IDE not allow a program to be saved until it works,
         // which seems quite ridiculous

         // Don't pop up a blank message dialog client side for a null pointer exception or other
         // RuntimeException that has a null message
         if(ex.getMessage() != null) {
            this.coreLifecycleService.sendMessage(ex.getMessage(), MessageCommand.Type.WARNING,
                                                  commandDispatcher);
         }
      }

      List<Exception> exs = WorksheetService.ASSET_EXCEPTIONS.get();

      // show exception but don't prevent the property dialog from closing.
      // We used to restore the assemblyInfo when there is an error. But that
      // is problematic. For example, if the user enters a script that causes
      // the binding to contain an invalid column. If the user opens the
      // property dialog and clears out the script, there will still be an
      // error (which can only be corrected on the binding), so the user
      // is trapped in the property dialog.
      if(exs != null) {
         for(Exception ex : exs) {
            if(!(ex instanceof MessageException) && !(ex instanceof ConfirmException)) {
               String msg = ex.getMessage();

               if(msg != null && msg.length() > 0) {
                  if(ex instanceof ScriptException) {
                     msg = Catalog.getCatalog().getString("Script failed") + msg;
                  }

                  this.coreLifecycleService.sendMessage(msg, MessageCommand.Type.ERROR,
                                                        commandDispatcher);
               }
            }
         }

         WorksheetService.ASSET_EXCEPTIONS.remove();
      }

      List<Assembly> list = new ArrayList<>();

      for(int i = 0; infoChanged && i < refs.length; i++) {
         AssemblyEntry entry = refs[i].getEntry();

         if(clist.contains(entry)) {
            continue;
         }

         Assembly tassembly = null;

         if(entry.isWSAssembly()) {
            tassembly = ws != null ? ws.getAssembly(entry) : null;

            // reexecute runtime condition list
            if(tassembly instanceof TableAssembly &&
               !(assembly instanceof EmbeddedTableVSAssembly))
            {
               ((TableAssembly) tassembly).setPreRuntimeConditionList(null);
               ((TableAssembly) tassembly).setPostRuntimeConditionList(null);
               AssemblyRef[] refs2 = vs.getDependeds(entry);

               for(int j = 0; refs2 != null && j < refs2.length; j++) {
                  Assembly assembly2 = vs.getAssembly(refs2[j].getEntry());

                  if(assembly2 instanceof SelectionVSAssembly) {
                     list.add(assembly2);
                  }
               }
            }
         }
         else {
            tassembly = vs.getAssembly(entry);
         }

         if(tassembly != null) {
            list.add(tassembly);
         }
      }

      list.addAll(scriptObjs);

      if(list.size() > 0) {
         Assembly[] arr = new Assembly[list.size()];
         list.toArray(arr);
         box.reset(null, arr, clist, false, false, null);
         this.coreLifecycleService.execute(rvs, name, linkUri, clist, commandDispatcher, false);
      }

      //TODO re-create fixTipOrPopAssemblies
      //VSEventUtil.fixTipOrPopAssemblies(rvs, command);

      // if we do not refresh the depending assemblies, the infos
      // might be out-of-date, then the editing process might be false
      if(renamed) {
         refs = vs.getDependings(assembly.getAssemblyEntry());

         for(int i = 0; i < refs.length; i++) {
            AssemblyEntry entry = refs[i].getEntry();

            if(entry.isVSAssembly()) {
               this.coreLifecycleService.refreshVSAssembly(rvs, entry.getAbsoluteName(),
                                                           commandDispatcher);
            }
         }

         refs = vs.getViewDependings(assembly.getAssemblyEntry());

         for(int i = 0; i < refs.length; i++) {
            AssemblyEntry entry = refs[i].getEntry();

            if(entry.isVSAssembly()) {
               box.executeView(entry.getAbsoluteName(), true);
               this.coreLifecycleService.refreshVSAssembly(rvs, entry.getAbsoluteName(),
                                                           commandDispatcher);
            }
         }

         info.setName(newName);
      }
      else {
         // refresh assembly anyway, some types of modification will not cause
         // vspane refresh, in this case, assembly can't be updated
         this.coreLifecycleService.refreshVSAssembly(rvs, info.getAbsoluteName2(), commandDispatcher);
      }

      refreshTipAndPopAssembly(rvs, assembly, commandDispatcher);
      fixContainerProperties(rvs, oinfo, info, commandDispatcher);

      // handle when change VSTab visible property, should refresh the
      // embedded viewsheet
      if(assembly instanceof TabVSAssembly && (hint & VSAssembly.VIEW_CHANGED) != 0) {
         addDeleteEmbeddedViewsheet(rvs, commandDispatcher);
      }

      infoHander.getGrayedOutFields(rvs, commandDispatcher);

      if(info instanceof RangeOutputVSAssemblyInfo) {
         VSCompositeFormat format = oldInfo.getFormat();
         info.setFormat(format);
      }

      if(info instanceof SelectionTreeVSAssemblyInfo) {
         boolean expand = ((SelectionTreeVSAssemblyInfo) assembly.getInfo()).isExpandAll();

         ExpandTreeNodesCommand expandTreeNodesCommand = ExpandTreeNodesCommand.builder()
            .scriptChanged(scriptChanged)
            .expand(expand)
            .assembly(assembly.getAbsoluteName())
            .build();
         commandDispatcher.sendCommand(assembly.getAbsoluteName(), expandTreeNodesCommand);
      }

      this.coreLifecycleService.layoutViewsheet(rvs, rvs.getID(), linkUri, commandDispatcher);
      processChartHighlight(assembly);
      processListInput(rvs, assembly, oassembly, commandDispatcher);
      //TODO re-create processRefresh, optimization for charts
      //processRefresh(rvs, assembly, commandDispatcher);

      annotationEdited(rvs, assembly);
   }

   private void refreshTipAndPopAssembly(RuntimeViewsheet rvs, VSAssembly target, CommandDispatcher dispatcher)
      throws Exception
   {
      VSAssemblyInfo assemblyInfo = target.getVSAssemblyInfo();

      if(assemblyInfo instanceof TipVSAssemblyInfo) {
         TipVSAssemblyInfo tipAssemblyInfo = (TipVSAssemblyInfo) assemblyInfo;
         String tipView = tipAssemblyInfo.getTipView();
         String[] flyoverViews = tipAssemblyInfo.getFlyoverViews();

         if(tipView != null) {
            this.coreLifecycleService.refreshVSAssembly(rvs, tipView, dispatcher);
         }
         else if(flyoverViews != null && flyoverViews.length > 0) {
            for(String flyoverView : flyoverViews) {
               this.coreLifecycleService.refreshVSAssembly(rvs, flyoverView, dispatcher);
            }
         }
      }
      else if(assemblyInfo instanceof PopVSAssemblyInfo) {
         PopVSAssemblyInfo popVSAssemblyInfo = (PopVSAssemblyInfo) assemblyInfo;
         String popComponent = popVSAssemblyInfo.getPopComponent();

         if(popComponent != null) {
            this.coreLifecycleService.refreshVSAssembly(rvs, popComponent, dispatcher);
         }
      }
   }

   // check if script is grammatically correct.
   // @return error message if failed to compile.
   public String checkScript(RuntimeViewsheet rvs, String script) {
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      ScriptEnv env = box.getScope().getScriptEnv();

      try {
         env.compile(script);
      }
      catch(Exception ex) {
         return ex.getMessage();
      }

      return null;
   }

   /**
    * Refresh table assembly.
    */
   private void refreshTable(RuntimeViewsheet rvs, String tableName,
                             CommandDispatcher commandDispatcher)
      throws Exception
   {
      if(rvs == null) {
         return;
      }

      Viewsheet vs = rvs.getViewsheet();

      if(vs != null && !StringUtils.isEmpty(tableName) &&
         vs.getAssembly(tableName) instanceof TableVSAssembly)
      {
         this.coreLifecycleService.refreshVSAssembly(rvs, tableName, commandDispatcher);
      }
   }

   private void renameAllBindSourceAssemblies(String oname, String nname, Viewsheet vs,
                                             RuntimeViewsheet rvs,
                                             CommandDispatcher commandDispatcher)
      throws Exception
   {
      Assembly assembly = vs.getAssembly(oname);
      Assembly[] assemblies = vs.getAssemblies();

      if(assembly == null || assemblies.length == 1) {
         return;
      }

      for(Assembly assembly0 : assemblies) {
         if(assembly0.equals(assembly)) {
            continue;
         }

         Boolean boundTo = VSUtil.isBoundTo(assembly0, assembly, vs);
         boolean changeBindSource = false;

         if(boundTo) {
            if(assembly0 instanceof DataVSAssembly) {
               SourceInfo sinfo = ((DataVSAssembly) assembly0).getSourceInfo();

               if(sinfo.getSource() != null && sinfo.getSource().indexOf(oname) > -1) {
                  String source = sinfo.getSource().replace(oname, nname);
                  sinfo.setSource(source);
                  changeBindSource = true;
               }
            }
            else if(assembly0 instanceof TimeSliderVSAssembly) {
               TimeSliderVSAssembly tassembly = (TimeSliderVSAssembly) assembly0;
               tassembly.setTableName(nname);
            }
         }

         if(changeBindSource) {
            String bindAbsoluteName = ((VSAssembly) assembly0).getVSAssemblyInfo().getAbsoluteName();
            this.coreLifecycleService.refreshVSAssembly(rvs, bindAbsoluteName, commandDispatcher);
         }
      }
   }

   private boolean checkInputList(ListInputVSAssemblyInfo info, CommandDispatcher commandDispatcher)
   {
      ListData datas = info.getListData();
      Object[] values = datas == null ? new Object[0] : datas.getValues();

      try {
         for(Object value : values) {
            XValueNode vnode = XValueNode.createValueNode("node", datas.getDataType());

            if(value != null) {
               vnode.parse(Tool.getDataString(value, datas.getDataType()));
            }
         }
      }
      catch(Exception ex) {
         this.coreLifecycleService.sendMessage(Catalog.getCatalog().getString(
            "common.dataFormatErrorParam", ex.getMessage()),
                                               MessageCommand.Type.ERROR, commandDispatcher);
         return false;
      }

      return true;
   }

   private boolean renameAssembly(String oldName, String newName, VSAssembly container,
                                  RuntimeViewsheet rvs, CommandDispatcher commandDispatcher)
      throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();
      List<String> oldNames = collectChildAssemblyNames(oldName, vs);
      String containerName = container instanceof CurrentSelectionVSAssembly ?
         container.getAbsoluteName() : "";

      if(vs.renameAssembly(oldName, newName)) {
         rvs.getViewsheetSandbox().resetRuntime();
         commandDispatcher.sendCommand(containerName, new RenameVSObjectCommand(oldName, newName));
         renameChildAssemblies(oldNames, newName, vs);
         ViewsheetSandbox box = rvs.getViewsheetSandbox();
         ViewsheetScope scope = box.getScope();
         VariableScriptable vscriptable = scope.getVariableScriptable();
         VariableTable vtable = (VariableTable) vscriptable.unwrap();

         if(vtable != null && vtable.contains(oldName)) {
            Object value = vtable.get(oldName);
            vtable.remove(oldName);
            vtable.put(newName, value);
         }

         if(vs.getAssembly(newName) instanceof InputVSAssembly) {
            syncVariables(oldName, newName, vs);
            syncCalcFields(oldName, newName, vs);
         }

         VSObjectTreeNode tree = vsObjectTreeService.getObjectTree(rvs);
         PopulateVSObjectTreeCommand treeCommand = new PopulateVSObjectTreeCommand(tree);
         commandDispatcher.sendCommand(treeCommand);

         return true;
      }

      return false;
   }

   /**
    * Sync all the vs calc fields scripts after renaming input assembly.
    * @param oAssemblyName   the old name of the input assembly.
    * @param nAssemblyName   the new name of the input assembly.
    * @param vs              the target viewsheet.
    */
   private void syncCalcFields(String oAssemblyName, String nAssemblyName, Viewsheet vs) {
      Collection<String> calcSources = vs.getCalcFieldSources();

      for(String calcSource : calcSources) {
         CalculateRef[] sourceCalcs = vs.getCalcFields(calcSource);

         if(sourceCalcs == null || sourceCalcs.length == 0) {
            continue;
         }

         for(CalculateRef calc : sourceCalcs) {
            syncCalcField(oAssemblyName, nAssemblyName, calc);
         }
      }
   }

   private void syncCalcField(String oAssemblyName, String nAssemblyName, CalculateRef calc) {
      if(!(calc.getDataRef() instanceof ExpressionRef)) {
         return;
      }

      ExpressionRef exp = (ExpressionRef) calc.getDataRef();
      String expression = exp.getExpression();

      if(expression != null) {
         expression = Tool.replaceAll(expression, oAssemblyName + ".", nAssemblyName + ".");
         exp.setExpression(expression);
      }
   }

   /**
    * Sync all the vs variables after renaming input assembly.
    * @param oAssemblyName   the old name of the input assembly.
    * @param nAssemblyName   the new name of the input assembly.
    * @param vs              the target viewsheet.
    */
   private void syncVariables(String oAssemblyName, String nAssemblyName, Viewsheet vs) {
      syncAssemblyVariables(oAssemblyName, nAssemblyName, vs);
      Assembly[] assemblies = vs.getAssemblies();

      if(assemblies != null) {
         Arrays.stream(assemblies)
            .forEach(assembly ->
         syncAssemblyVariables(oAssemblyName, nAssemblyName, (VSAssembly) assembly));
      }
   }

   private void syncAssemblyVariables(String oAssemblyName, String nAssemblyName,
                                      VSAssembly assembly)
   {
      if(assembly == null || oAssemblyName == null || nAssemblyName == null ||
         Tool.equals(assembly.getAbsoluteName(), nAssemblyName))
      {
         return;
      }

      List<DynamicValue> dvalues = assembly.getDynamicValues();

      if(dvalues != null && dvalues.size() > 0) {
         dvalues.stream()
            .filter(v -> Tool.equals("$(" + oAssemblyName + ")", v.getDValue()))
            .forEach(v -> v.setDValue("$(" + nAssemblyName + ")"));
      }
   }

   private List<String> collectChildAssemblyNames(String name, Viewsheet vs) {
      List<String> names = new ArrayList<>();

      if(name == null || vs == null) {
         return names;
      }

      Assembly assembly = vs.getAssembly(name);

      if(!(assembly instanceof Viewsheet)) {
         return names;
      }

      Assembly[] assemblies = ((Viewsheet) assembly).getAssemblies();

      for(Assembly vsasembly: assemblies) {
         names.add(vsasembly.getAbsoluteName());
      }

      return names;
   }

   private void renameChildAssemblies(List<String> oldNames, String newName, Viewsheet vs) {
      if(newName == null || vs == null) {
         return;
      }

      Assembly assembly = vs.getAssembly(newName);

      if(!(assembly instanceof Viewsheet)) {
         return;
      }

      Assembly[] assemblies = ((Viewsheet) assembly).getAssemblies();

      if(oldNames.size() != assemblies.length) {
         return;
      }

      for(Assembly vsassembly : assemblies) {
         //TODO? rename vs object command
      }
   }

   private void refreshFlyovers(String newName, RuntimeViewsheet rvs, CommandDispatcher commandDispatcher)
      throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();

      for(Assembly assembly : vs.getAssemblies()) {
         AssemblyInfo info = assembly.getInfo();
         if(!(info instanceof TipVSAssemblyInfo)) {
            return;
         }

         TipVSAssemblyInfo tipInfo = (TipVSAssemblyInfo) info;
         String[] views = tipInfo.getFlyoverViews();

         if(newName.equals(tipInfo.getTipView()) || Tool.contains(views, newName)) {
            this.coreLifecycleService.refreshVSAssembly(rvs, info.getAbsoluteName(), commandDispatcher);
         }
      }
   }

   // Keep flyover views in sync.
   private void checkFlyoverChanges(RuntimeViewsheet rvs, VSAssemblyInfo oinfo,
                                    VSAssemblyInfo info, String linkUri,
                                    CommandDispatcher commandDispatcher)
      throws Exception
   {
      if(!(info instanceof TipVSAssemblyInfo)) {
         return;
      }

      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return;
      }

      TipVSAssemblyInfo oinfo0 = (TipVSAssemblyInfo) oinfo;
      TipVSAssemblyInfo info0 = (TipVSAssemblyInfo) info;
      String[] oviews = oinfo0.getFlyoverViews();
      String[] views = info0.getFlyoverViews();

      if(oviews != null && oviews.length > 0) {
         VSAssembly tip = (VSAssembly) vs.getAssembly(oviews[0]);
         ConditionListWrapper conds = null;

         if(tip != null) {
            conds = tip.getTipConditionList();
         }

         if(views == null) {
            views = new String[0];
         }

         // clear the views that's no longer on the list
         Set<String> oset = new HashSet(Arrays.asList(oviews));
         Set<String> nset = new HashSet(Arrays.asList(views));
         Set<String> all = new HashSet(oset);
         all.addAll(nset);

         for(String view : all) {
            if(oset.contains(view) && nset.contains(view)) {
               continue;
            }

            ConditionListWrapper nconds = oset.contains(view) ? null : conds;
            tip = (VSAssembly) vs.getAssembly(view);

            if(tip != null && !Tool.equals(nconds, tip.getTipConditionList())) {
               int hint = VSAssembly.INPUT_DATA_CHANGED;
               tip.setTipConditionList(nconds);
               this.coreLifecycleService.execute(rvs, tip.getAbsoluteName(),
                                                 linkUri, hint, commandDispatcher);
               this.coreLifecycleService.refreshVSAssembly(rvs, view, commandDispatcher);
            }
         }
      }
   }

   /**
    * Process named group info.
    */
   private void processNamedGroup(VSDataSet lens, String field,
                                  SNamedGroupInfo sginfo, VSChartInfo cinfo)
   {
      if(sginfo == null || lens == null) {
         return;
      }

      String[] grps = sginfo.getGroups();

      for(int j = 0; grps != null && j < grps.length; j++) {
         List values = sginfo.getGroupValue(grps[j]);

         if(values == null) {
            continue;
         }

         List<String> nvals = new ArrayList<>();
         Iterator<String> it = values.iterator();

         while(it.hasNext()) {
            String val = it.next();
            VSFieldValue[] pairs = lens.getFieldValues(field, val, true,
                                                       GraphTypes.isTreemap(cinfo.getChartType()));

            if(pairs.length == 0) {
               nvals.add(val);
               continue;
            }

            nvals.add(pairs[0].getFieldValue().getValue());
         }

         sginfo.setGroupValue(grps[j], nvals);
      }
   }
   /**
    * Sync chart highlight.
    */
   private void processChartHighlight(VSAssembly assembly) {
      if(!(assembly instanceof ChartVSAssembly)) {
         return;
      }

      ChartVSAssembly chart = (ChartVSAssembly) assembly;
      ChartVSAssemblyInfo ncinfo = (ChartVSAssemblyInfo)
         chart.getVSAssemblyInfo();
      VSChartInfo chartinfo = ncinfo.getVSChartInfo();
      List<XAggregateRef> aggs = getMeasures(chartinfo);
      List<XDimensionRef> dims = GraphUtil.getAllDimensions(chartinfo, true);

      for(int i = 0; i < aggs.size(); i++) {
         ((ChartAggregateRef) aggs.get(i)).highlights()
            .forEach(hg -> syncChartHighlightCondition(hg.clone(), dims));
      }
   }

   /**
    * Get all the measures of target chart.
    */
   public List<XAggregateRef> getMeasures(VSChartInfo info) {
      ChartRef[] rxrefs = info.getRTXFields();
      ChartRef[] ryrefs = info.getRTYFields();

      if(containsMeasure(ryrefs)) {
         return GraphUtil.getMeasures(ryrefs);
      }
      else {
         return GraphUtil.getMeasures(rxrefs);
      }
   }

   /**
    * Check if contains measure.
    */
   private boolean containsMeasure(ChartRef[] refs) {
      if(refs == null || refs.length == 0) {
         return false;
      }

      return refs[refs.length - 1].isMeasure();
   }

   /**
    * Sync chart highlight condition.
    */
   private void syncChartHighlightCondition(HighlightGroup hg, List<XDimensionRef> dims) {
      String[] levels = hg.getLevels();

      for(String level : levels) {
         String[] names = hg.getNames(level);

         for(String name : names) {
            Highlight h = hg.getHighlight(level, name);
            ConditionList cond = h.getConditionGroup();

            for(int j = 0; j < cond.getSize(); j += 2) {
               ConditionItem citem = cond.getConditionItem(j);
               DataRef dataRef = citem.getAttribute();

               for(int m = 0; m < dims.size(); m++) {
                  boolean needRefresh = dims.get(m).getName().equals(dataRef.getName()) &&
                        dims.get(m) instanceof VSDimensionRef;

                  if(!needRefresh) {
                     continue;
                  }

                  if(!(dataRef instanceof ColumnRef)) {
                     continue;
                  }

                  DataRef attr = ((ColumnRef) dataRef).getDataRef();

                  if(!(attr instanceof AttributeRef)) {
                     continue;
                  }

                  String caption = getCaption(dims.get(m));
                  ((AttributeRef) attr).setCaption(caption);
               }
            }
         }
      }
   }

   /**
    * Modify the crosstab data path to match the new change.
    */
   private void processCrosstab(VSAssembly assembly, VSAssemblyInfo oinfo, boolean propertyChanged) {
      if(assembly instanceof CrosstabVSAssembly) {
         CrosstabVSAssembly cross = (CrosstabVSAssembly) assembly;
         CrosstabVSAssemblyInfo ncinfo = (CrosstabVSAssemblyInfo)
            cross.getVSAssemblyInfo();
         CrosstabVSAssemblyInfo ocinfo = (CrosstabVSAssemblyInfo) oinfo;
         FormatInfo finfo = cross.getFormatInfo();
         TableHyperlinkAttr hyperlink = ncinfo.getHyperlinkAttr();
         TableHighlightAttr highlight = ncinfo.getHighlightAttr();

         if(finfo != null) {
            VSUtil.syncCrosstabPath(cross, ocinfo, propertyChanged, finfo.getFormatMap());
         }

         if(hyperlink != null) {
            VSUtil.syncCrosstabPath(cross, ocinfo, propertyChanged, hyperlink.getHyperlinkMap());
         }

         if(highlight != null) {
            VSUtil.syncCrosstabPath(cross, ocinfo, propertyChanged, highlight.getHighlightMap());
            syncCrosstabHighlightCondition(highlight, ncinfo);
            syncHighlightConditionOrder(highlight, ncinfo);
         }
      }
   }

   /**
    * Sync the highlight conditions of crosstab.
    */
   private void syncCrosstabHighlightCondition(TableHighlightAttr highlight,
                                               CrosstabVSAssemblyInfo cinfo)
   {
      VSCrosstabInfo vscrosstabinfo = cinfo.getVSCrosstabInfo();
      Enumeration hgs = highlight.getAllHighlights();

      while(hgs.hasMoreElements()) {
         HighlightGroup hg = (HighlightGroup) hgs.nextElement();
         String[] levels = hg.getLevels();

         for(int i = 0; i < levels.length; i++) {
            String[] names = hg.getNames(levels[i]);

            for(int j = 0; j < names.length; j++) {
               Highlight hl = hg.getHighlight(levels[i], names[j]);
               ConditionList clist = hl.getConditionGroup();

               for(int k = 0; k < clist.getSize(); k += 2) {
                  ConditionItem citem = clist.getConditionItem(k);
                  DataRef dataRef = citem.getAttribute();

                  if(!(dataRef instanceof VSDimensionRef)) {
                     continue;
                  }

                  VSDimensionRef dim = (VSDimensionRef) dataRef;
                  DataRef[] headers = vscrosstabinfo.getRuntimeRowHeaders();

                  for(int m = 0; m < headers.length; m++) {
                     if(headers[i].getName().equals(dim.getName()) &&
                        headers[i] instanceof VSDimensionRef)
                     {
                        DataRef ref = dim.getDataRef();

                        if(ref instanceof ColumnRef) {
                           String caption =
                              getCaption((VSDimensionRef) headers[i]);
                           ((ColumnRef) ref).setCaption(caption);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   /**
    * Sync highlight condition item sort order.
    */
   private void syncHighlightConditionOrder(TableHighlightAttr highlight,
                                            CrosstabVSAssemblyInfo cinfo)
   {
      VSCrosstabInfo vscrosstabinfo = cinfo.getVSCrosstabInfo();
      Enumeration highlightGroups = highlight.getAllHighlights();

      while(highlightGroups.hasMoreElements()) {
         HighlightGroup highlightGroup =
            (HighlightGroup) highlightGroups.nextElement();
         String[] levels = highlightGroup.getLevels();

         for(int i = 0; i < levels.length; i++) {
            String[] names = highlightGroup.getNames(levels[i]);

            for(int j = 0; j < names.length; j++) {
               Highlight texthighlight =
                  highlightGroup.getHighlight(levels[i], names[j]);
               ConditionList conditionlist = texthighlight.getConditionGroup();

               for(int k = 0; k < conditionlist.getSize(); k += 2) {
                  ConditionItem conditionItem =
                     conditionlist.getConditionItem(k);
                  DataRef dataRef = conditionItem.getAttribute();

                  if(!(dataRef instanceof VSDimensionRef)) {
                     return;
                  }

                  VSDimensionRef dim = (VSDimensionRef) dataRef;
                  DataRef[] headers = vscrosstabinfo.getRuntimeRowHeaders();

                  for(int m = 0; m < headers.length; m++) {
                     if(headers[i].getName().equals(dim.getName()) &&
                        headers[i] instanceof VSDimensionRef)
                     {
                        dim.setOrder(((VSDimensionRef) headers[i]).getOrder());

                        return;
                     }
                  }

                  headers = vscrosstabinfo.getRuntimeColHeaders();

                  for(int m = 0; m < headers.length; m++) {
                     if(headers[i].getName().equals(dim.getName()) &&
                        headers[i] instanceof VSDimensionRef)
                     {
                        dim.setOrder(((VSDimensionRef) headers[i]).getOrder());

                        return;
                     }
                  }
               }
            }
         }
      }
   }

   /**
    * Maintain selected objects.
    */
   private void processListInput(RuntimeViewsheet rvs, VSAssembly assembly,
                                 VSAssembly oassembly, CommandDispatcher commandDispatcher)
      throws Exception
   {
      if(assembly instanceof ListInputVSAssembly) {
         ListInputVSAssemblyInfo listInfo =
            (ListInputVSAssemblyInfo) assembly.getInfo();
         ListInputVSAssemblyInfo olistInfo =
            (ListInputVSAssemblyInfo) oassembly.getInfo();

         if(!Tool.equals(listInfo.getSelectedObjects(),
                         olistInfo.getSelectedObjects()))
         {
            List<String> selecteds = new ArrayList<>();

            for(Object selected : listInfo.getSelectedObjects()) {
               if(selected != null) {
                  selecteds.add(selected.toString());
               }
            }

            String[] values = selecteds.toArray(new String[0]);
            //TODO recreate SetInputObjectValueEvent.process0
//            SetInputObjectValueEvent.process0(rvs, command, this,
//                                              listInfo.getName(), values);
         }
      }
   }

   /**
    * Get the caption of a data ref.
    */
   private String getCaption(DataRef ref) {
      return GraphUtil.getCaption(ref);
   }

   private void annotationEdited(RuntimeViewsheet rvs, Assembly assembly) {
      if(assembly instanceof AnnotationRectangleVSAssembly) {
         Viewsheet vs = rvs.getViewsheet();
         ViewsheetSandbox box = rvs.getViewsheetSandbox();

         if (vs == null || box == null) {
            return;
         }

         Assembly aasembly =
            AnnotationVSUtil.getAnnotationAssembly(vs, assembly.getName());

         Assembly base =
            AnnotationVSUtil.getBaseAssembly(vs, aasembly.getName());

         String baseAssembly = base != null ? base.getName() : "";
         AnnotationRectangleVSAssemblyInfo rinfo =
            (AnnotationRectangleVSAssemblyInfo) assembly.getInfo();

         String txt = rinfo.getContent();
         String column = "";
         XNode annotationData = null;
         AnnotationVSAssemblyInfo ainfo =
            (AnnotationVSAssemblyInfo) aasembly.getInfo();

         // only deal with the lens when the annotation is related to data
         if(ainfo.getType() == AnnotationVSAssemblyInfo.DATA) {
            int row = ainfo.getRow();
            int col = ainfo.getCol();
            TableLens lens = null;
            String measureName = null;

            try {
               if(base instanceof TableDataVSAssembly) {
                  lens = box.getTableData(base.getAbsoluteName());
               }
               else if(base instanceof ChartVSAssembly) {
                  lens = ((VSDataSet)box.getData(base.getAbsoluteName())).getTable();
                  row += 1; //chart's row seems to be off by one
                  measureName = ((ChartDataValue) ainfo.getValue()).getMeasureName();
               }

               if (lens != null) {
                  int [] rows = new int[lens.getHeaderRowCount() + 1];
                  int [] cols = new int[lens.getColCount()];
                  int i;

                  for(i = 0; i < lens.getHeaderRowCount(); i++) {
                     rows[i] = i;
                  }

                  rows[i] = row;

                  for(i = 0; i < lens.getColCount(); i++) {
                     cols[i] = i;
                  }

                  lens = new SubTableLens(lens, rows, cols);
                  annotationData = new XTableTableNode(lens);
               }
            }
            catch(Exception ex) {
               //do nothing
            }

            if(col == -1 && measureName != null){
               column = measureName;
            }
            else if(lens != null) {
               column = XUtil.getHeader(lens, col).toString();
            }
         }

         txt = txt.replaceAll("(?i)</P>","\n");
         txt = txt.substring(0, txt.lastIndexOf("\n"));
         txt = txt.replaceAll("\\<.*?>","");

         ServiceLoader<AnnotationListener> loader =
            ServiceLoader.load(AnnotationListener.class);
         Iterator<AnnotationListener> iter = loader.iterator();

         while(iter.hasNext()) {
            AnnotationListener service = iter.next();
            service.annotationEdited(rvs.getUser(), box.getAssetEntry(), baseAssembly,
                                     column, txt, annotationData);
         }
      }
   }

   public String[] getObjectNames(Viewsheet vs, String name) {
      List<String> result;
      Assembly[] assemblies = vs.getAssemblies(true);
      //for those embedded viewsheets, provide the top name for avoiding duplicate name
      Assembly[] assemblies2 = vs.getAssemblies(false);

      result = Stream.concat(
         Arrays.stream(assemblies).map(Assembly::getAbsoluteName),
         Arrays.stream(assemblies2).map(Assembly::getAbsoluteName))
         .distinct()
         .filter(assemblyName -> !assemblyName.equals(name))
         .collect(Collectors.toList());

      return result.isEmpty() ? new String[0] : result.toArray(new String[0]);
   }

   public String[] getSupportedPopComponents(Viewsheet vs, String name) {
      List<String> result = new ArrayList<>();
      Assembly[] assemblies = vs.getAssemblies(true);
      VSAssembly currentAssembly = (VSAssembly) vs.getAssembly(name);

      for(Assembly assembly : assemblies) {
         if(!assembly.getAbsoluteName().equals(name)) {
            VSAssemblyInfo vsInfo = (VSAssemblyInfo) assembly.getInfo();
            VSAssembly container = ((VSAssembly) assembly).getContainer();
            String aname = vsInfo.getAbsoluteName();

            if(vsInfo instanceof GroupContainerVSAssemblyInfo) {
               String[] groupAssemblies = ((GroupContainerVSAssemblyInfo) vsInfo).getAssemblies();

               if(containsViewsheet(vs, groupAssemblies)) {
                  continue;
               }
            }

            if(vsInfo.isEmbedded()) {
               int lastDot = aname.lastIndexOf(".");
               String containerName = aname.substring(0, lastDot);

               if(vs.getAssembly(containerName) instanceof Viewsheet) {
                  continue;
               }
            }

            if(vsInfo instanceof TabVSAssemblyInfo ||
               vsInfo instanceof ViewsheetVSAssemblyInfo ||
               vsInfo instanceof ShapeVSAssemblyInfo ||
               vsInfo instanceof CurrentSelectionVSAssemblyInfo ||
               vsInfo instanceof AnnotationLineVSAssemblyInfo ||
               vsInfo instanceof AnnotationRectangleVSAssemblyInfo ||
               vsInfo instanceof AnnotationVSAssemblyInfo)
            {
               continue;
            }

            if(vsInfo instanceof SelectionBaseVSAssemblyInfo &&
               (((SelectionBaseVSAssemblyInfo) vsInfo).getShowType() !=
               SelectionVSAssemblyInfo.LIST_SHOW_TYPE) ||
               container instanceof CurrentSelectionVSAssembly)
            {
               continue;
            }

            if(currentAssembly.getContainer() != null &&
               currentAssembly.getContainer().getAbsoluteName().equals(aname))
            {
               continue;
            }

            if(container instanceof GroupContainerVSAssembly ||
               container instanceof TabVSAssembly) {
               continue;
            }

            result.add(aname);
         }
      }

      Collections.sort(result);
      return result.isEmpty() ? new String[0] : result.toArray(new String[0]);
   }

   private boolean containsViewsheet(Viewsheet vs, String[] assemblyIds) {
      for(String assemblyId : assemblyIds) {
         VSAssembly assembly = vs.getAssembly(assemblyId);

         if(assembly instanceof Viewsheet) {
            return true;
         }
         else if(assembly instanceof TabVSAssembly) {
            String[] tabAssemblies = ((TabVSAssembly) assembly).getAssemblies();

            if(containsViewsheet(vs, tabAssemblies)) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Get datatip/flyover candidates.
    */
   public String[] getSupportedTablePopComponents(RuntimeViewsheet rvs, String name, boolean flyover) {
      List<String> result = new ArrayList<>();
      Viewsheet vs = rvs.getViewsheet();
      Assembly[] assemblies = vs.getAssemblies(true);

      VSTemporaryInfo tempInfo = temporaryInfoService.getVSTemporaryInfo(rvs);
      String originalName = tempInfo == null || tempInfo.getOriginalModel() == null ? null :
         tempInfo.getOriginalModel().getOriginalName();

      for(Assembly assembly : assemblies) {
         if(supportAsDataTip(vs, assembly, name, originalName, flyover)) {
            VSAssemblyInfo vsInfo = (VSAssemblyInfo) assembly.getInfo();
            String aname = vsInfo.getAbsoluteName();
            result.add(aname);
         }
      }

      return result.isEmpty() ? new String[0] : result.toArray(new String[0]);
   }

   /**
    * @param inContainer true if include assemblies inside container. If yes, group containers
    * are not included.
    */
   private boolean supportAsDataTip(Viewsheet vs, Assembly assembly, String name,
                                    String originalName, boolean inContainer)
   {
      VSAssemblyInfo vsInfo = (VSAssemblyInfo) assembly.getInfo();
      String aname = vsInfo.getAbsoluteName();

      if(aname.equals(name) || aname.equals(originalName) ||
         aname.startsWith(CalcTableVSAQuery.TEMP_ASSEMBLY_PREFIX) ||
         aname.equals(VSWizardConstants.TEMP_CHART_NAME))
      {
         return false;
      }

      if(!(vsInfo instanceof OutputVSAssemblyInfo || vsInfo instanceof DataVSAssemblyInfo ||
           vsInfo instanceof GroupContainerVSAssemblyInfo) ||
         vsInfo instanceof EmbeddedTableVSAssemblyInfo || vsInfo instanceof SubmitVSAssemblyInfo)
      {
         return false;
      }

      if(vsInfo.isEmbedded() || (((VSAssembly) assembly).getContainer() != null && !inContainer)) {
         return false;
      }

      if(vs.getAssembly(name).getContainer() != null && vs.getAssembly(name).getContainer().equals(assembly)){
         return false;
      }

      if(vsInfo instanceof GroupContainerVSAssemblyInfo) {
         if(inContainer) {
            return false;
         }

         String[] assemblies = ((GroupContainerVSAssemblyInfo) vsInfo).getAbsoluteAssemblies();

         for(int i = 0; i < assemblies.length; i++) {
            VSAssembly vsAssembly = vs.getAssembly(assemblies[i]);
            
            if(vsAssembly != null && !supportAsDataTip(vs, vsAssembly, name, originalName,  true)) {
               return false;
            }
         }

         return false;
      }

      return true;
   }

   public static String getColorHexString(String color) {
      if(color != null && !color.isEmpty()) {
         color = String.format("#%06x", Integer.decode(color));
      }

      return color;
   }

   public boolean isEmbeddedEnabled(RuntimeViewsheet rvs, TableVSAssemblyInfo tinfo) {
      SourceInfo sinfo = tinfo.getSourceInfo();

      if(sinfo == null || sinfo.isEmpty()) {
         return false;
      }

      Viewsheet vs = rvs.getViewsheet();
      Worksheet ws = vs.getBaseWorksheet();
      String source = sinfo.getSource();
      boolean found = false;
      Assembly wsobj = ws.getAssembly(source);

      if(isEmbeddedTable(wsobj)) {
         found = true;
      }

      if(!found && isEmbeddedTable(ws.getAssembly(source + "_O"))) {
         EmbeddedTableAssembly embed = (EmbeddedTableAssembly) ws.getAssembly(source + "_O");
         AggregateInfo ainfo = embed.getAggregateInfo();
         found = ainfo == null || ainfo.isEmpty();
      }

      return found && !containsCalcFields(tinfo);
   }

   private boolean containsCalcFields(TableVSAssemblyInfo info) {
      if(info.getSourceInfo() == null || info.getColumnSelection() == null) {
         return false;
      }

      Viewsheet vs = info.getViewsheet();
      String source = info.getSourceInfo().getSource();
      CalculateRef[] detailCalcs = vs.getCalcFields(source);

      if(detailCalcs == null || detailCalcs.length == 0) {
         return false;
      }

      ColumnSelection columns = info.getColumnSelection();

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         DataRef ref = columns.getAttribute(i);

         if(ref != null && vs.getCalcField(source, ref.getName()) != null) {
            return true;
         }
      }

      return false;
   }

   private static boolean isEmbeddedTable(Object assembly) {
      return assembly instanceof EmbeddedTableAssembly &&
         !(assembly instanceof SnapshotEmbeddedTableAssembly);
   }

   public OutputColumnRefModel[] getHierarchyColumnList(String dimensionOf, String table,
                                                        RuntimeViewsheet rvs, Principal principal)
      throws Exception
   {
      ColumnSelection selection = vsInputService.getTableColumns(
         rvs, table, null, null, dimensionOf, false, false, true, false, false, true, principal);
      List<OutputColumnRefModel> columnList = new ArrayList<>();

      for(int i = 0; i < selection.getAttributeCount(); i++) {
         ColumnRef columnRef = (ColumnRef) selection.getAttribute(i);

         if(VSUtil.isPreparedCalcField(columnRef)) {
            continue;
         }

         OutputColumnRefModel columnModel = new OutputColumnRefModel();
         columnModel.setName(columnRef.getName());
         columnModel.setDescription(columnRef.getDescription());
         columnModel.setEntity(columnRef.getEntity());
         columnModel.setAttribute(columnRef.getAttribute());
         columnModel.setDataType(columnRef.getDataType());
         columnModel.setRefType(columnRef.getRefType());
         columnList.add(columnModel);
      }

      return columnList.toArray(new OutputColumnRefModel[0]);
   }

   public VSDimensionModel convertVSDimensionToModel(VSDimension vsDimension) {
      VSDimensionModel result = new VSDimensionModel();
      List<VSDimensionMemberModel> vsDimensionMemberModelList = new ArrayList<>();

      for(int i = 0; i < vsDimension.getLevelCount(); i++) {
         VSDimensionMember member = (VSDimensionMember) vsDimension.getLevelAt(i);
         ColumnRef columnRef = (ColumnRef) member.getDataRef();
         OutputColumnRefModel columnModel = new OutputColumnRefModel();
         columnModel.setName(columnRef.getName());
         columnModel.setDescription(columnRef.getDescription());
         columnModel.setEntity(columnRef.getEntity());
         columnModel.setAttribute(columnRef.getAttribute());
         columnModel.setDataType(columnRef.getDataType());
         columnModel.setRefType(columnRef.getRefType());
         VSDimensionMemberModel vsDimensionMemberModel = new VSDimensionMemberModel();
         vsDimensionMemberModel.setOption(member.getDateOption());
         vsDimensionMemberModel.setDataRef(columnModel);
         vsDimensionMemberModelList.add(vsDimensionMemberModel);
      }

      result.setMembers(vsDimensionMemberModelList.toArray(new VSDimensionMemberModel[0]));
      return result;
   }

   public VSDimension convertModelToVSDimension(VSDimensionModel vsDimensionModel) {
      VSDimension result = new VSDimension();
      VSDimensionMemberModel[] memberModels = vsDimensionModel.getMembers();

      for(VSDimensionMemberModel vsDimensionMemberModel : memberModels) {
         VSDimensionMember member = new VSDimensionMember();
         member.setDateOption(vsDimensionMemberModel.getOption());
         OutputColumnRefModel refModel = vsDimensionMemberModel.getDataRef();
         AttributeRef aRef = new AttributeRef(refModel.getEntity(), refModel.getAttribute());
         aRef.setRefType(refModel.getRefType());
         ColumnRef cRef = new ColumnRef(aRef);
         cRef.setDataType(refModel.getDataType());
         cRef.setDescription(refModel.getDescription());
         member.setDataRef(cRef);
         result.addLevel(member);
      }

      return result;
   }

   /**
    * Check whether the table mode has changed, and change it if necessary.
    */
   public boolean isTableModeChange(RuntimeViewsheet rvs, VSAssemblyInfo info,
                                    String linkUri,
                                    CommandDispatcher dispatcher)
      throws Exception
   {
      if(info == null || !(info instanceof TableVSAssemblyInfo)) {
         return false;
      }

      Viewsheet vs = rvs.getViewsheet();
      TableVSAssemblyInfo oinfo = (TableVSAssemblyInfo) info.clone();
      Point position = oinfo.getPixelOffset();
      String oname = oinfo.getAbsoluteName();
      VSAssembly assembly = (VSAssembly) vs.getAssembly(oname);
      VSAssembly newAssembly = null;

      if(assembly == null) {
         return false;
      }

      boolean changed = false;
      boolean toEmbedded = false;

      // convert embedded table to simple table
      if(oinfo instanceof EmbeddedTableVSAssemblyInfo &&
         !oinfo.isEmbeddedTable())
      {
         newAssembly = VSEventUtil.createVSAssembly(rvs, Viewsheet.TABLE_VIEW_ASSET);

         if(newAssembly == null) {
            this.coreLifecycleService.sendMessage(
               Catalog.getCatalog().getString(
                  "viewer.viewsheet.convertTableFailed", "embedded table",
                  "simple table"), MessageCommand.Type.ERROR, dispatcher);
            return true;
         }

         changed = true;

         LOG.warn("The table data might be changed by applying max row property to table.");
      }
      // convert simple table to embedded table
      else if(!(oinfo instanceof EmbeddedTableVSAssemblyInfo) && oinfo.isEmbeddedTable()) {
         newAssembly = VSEventUtil.createVSAssembly(rvs, Viewsheet.EMBEDDEDTABLE_VIEW_ASSET);

         if(newAssembly == null) {
            this.coreLifecycleService.sendMessage(
               Catalog.getCatalog().getString(
                  "viewer.viewsheet.convertTableFailed", "simple table",
                  "embedded table"), MessageCommand.Type.ERROR, dispatcher);
            return true;
         }

         changed = true;
         toEmbedded = true;
         LOG.warn("The table data might be changed by ignoring max row property and condition.");
      }

      if(!changed || newAssembly == null) {
         return false;
      }

      newAssembly.setPixelOffset(position);
      rvs.getViewsheet().addAssembly(newAssembly);

      int hint = keepGrouping(rvs, assembly, oname, newAssembly.getAbsoluteName(), dispatcher);

      TableVSAssemblyInfo tinfo = (TableVSAssemblyInfo) newAssembly.getInfo();
      tinfo.setEmbeddedTable(oinfo.isEmbeddedTable());

      // copy info
      hint |= tinfo.copyInfo(oinfo, false);

      // clear the data tip/fly over view
      if(toEmbedded) {
         tinfo.setTipOption(TipVSAssemblyInfo.TOOLTIP_OPTION);
         tinfo.setTipOptionValue(TipVSAssemblyInfo.TOOLTIP_OPTION);
         tinfo.setTipView(null);
         tinfo.setTipViewValue(null);
         tinfo.setFlyoverViews(null);
         tinfo.setFlyoverViewsValue(null);
         tinfo.setFlyOnClick(false);
         tinfo.setFlyOnClickValue("false");
         tinfo.setPreConditionList(null);
         tinfo.setHyperlinkAttr(new TableHyperlinkAttr());
         tinfo.setRowHyperlink(null);

         if(assembly instanceof TableVSAssembly) {
            TableVSAssembly table = (TableVSAssembly) assembly;
            hint = table.setColumnSelection(table.getColumnSelection()) | hint;
         }
      }

      rvs.getViewsheet().removeAssembly(oname);
      // keep the assembly name
      renameAssembly(newAssembly.getName(), oname, assembly, rvs, dispatcher);

      VSAssembly container = assembly.getContainer();

      if(container instanceof TabVSAssembly) {
         ((TabVSAssembly) container).setSelectedValue(oname);
      }

      this.coreLifecycleService.refreshVSAssembly(rvs, oname, dispatcher, true);
      this.coreLifecycleService.execute(rvs, oname, linkUri, hint, dispatcher);
      this.coreLifecycleService.loadTableLens(rvs, oname, linkUri, dispatcher);
      this.coreLifecycleService.layoutViewsheet(rvs, rvs.getID(), linkUri, dispatcher);

      return true;
   }

   /**
    * Make sure converted table stays in propert container.
    */
   private int keepGrouping(RuntimeViewsheet rvs, VSAssembly assembly, String oname,
                            String nname, CommandDispatcher dispatcher)
      throws Exception
   {
      int hint = 0;
      Viewsheet vs = rvs.getViewsheet();
      VSAssembly cassembly = assembly.getContainer();

      // keep the convert assembly in group
      if(cassembly instanceof GroupContainerVSAssembly) {
         GroupContainerVSAssembly ogroup = (GroupContainerVSAssembly) cassembly;
         String ogname = ogroup.getAbsoluteName();
         String[] assemblies = ogroup.getAssemblies();
         TabVSAssembly tab = null;

         for(int i = 0; i < assemblies.length; i++) {
            if(assemblies[i].equals(oname)) {
               assemblies[i] = nname;
            }
         }

         ogroup.setAssemblies(assemblies);

         if(ogroup.getContainer() instanceof TabVSAssembly) {
            tab = (TabVSAssembly) ogroup.getContainer();
         }

         if(tab != null) {
            if(vs.getAssembly(tab.getAbsoluteName()) == null) {
               vs.addAssembly(tab);
            }

            coreLifecycleService.execute(rvs, tab.getAbsoluteName(), null,
                                         VSAssembly.VIEW_CHANGED, dispatcher);
         }
      }
      // keep the convert assembly in tab
      else if(cassembly instanceof TabVSAssembly) {
         TabVSAssembly tab = (TabVSAssembly) cassembly;
         String tname = tab.getAbsoluteName();
         String[] assemblies = tab.getAssemblies();

         for(int i = 0; i < assemblies.length; i++) {
            if(assemblies[i].equals(oname)) {
               assemblies[i] = nname;
            }
         }

         tab.setAssemblies(assemblies);

         coreLifecycleService.execute(rvs, tname, null, VSAssembly.VIEW_CHANGED, dispatcher);
         // remove assembly
         removeVSAssembly(rvs, assembly, dispatcher);
      }
      else {
         // remove assembly
         removeVSAssembly(rvs, assembly, dispatcher);
      }

      return hint;
   }

   /**
    * Remove vs assembly from container and vs.
    */
   private void removeVSAssembly(RuntimeViewsheet rvs, VSAssembly assembly,
                                        CommandDispatcher dispatcher)
      throws Exception
   {
      final String name = assembly.getAbsoluteName();
      removeObjectFromContainer(rvs, null, name,
                                dispatcher, new String[]{ name },
                                new ArrayList<>(), new ArrayList<>());
      coreLifecycleService.removeVSAssembly(rvs, null, assembly, dispatcher, false, true);
   }

   /**
    * Check tip view dependency, if cycle found, return true.
    */
   private boolean checkTipDependency(Viewsheet vs, AssemblyInfo info,
                                      HashMap<String, String> map) {
      if(info instanceof TipVSAssemblyInfo) {
         TipVSAssemblyInfo tinfo = (TipVSAssemblyInfo) info;

         if(tinfo.getTipOptionValue() == TipVSAssemblyInfo.VIEWTIP_OPTION) {
            String view = tinfo.getTipViewValue();

            if(view != null) {
               map.put(info.getName(), view);
               String name = view;

               while(name != null) {
                  name = map.get(name);

                  if(info.getName().equals(name)) {
                     return true;
                  }
               }

               Assembly assembly = vs.getAssembly(view);

               if(assembly != null) {
                  return checkTipDependency(vs, assembly.getInfo(), map);
               }
            }
         }
      }

      return false;
   }

   /**
    * Check pop component dependency, if cycle found, return true.
    */
   private boolean checkPopDependency(Viewsheet vs, AssemblyInfo info,
                                      HashMap<String, String> map) {
      if(info instanceof PopVSAssemblyInfo) {
         PopVSAssemblyInfo pinfo = (PopVSAssemblyInfo) info;

         if(pinfo.getPopOptionValue() == PopVSAssemblyInfo.POP_OPTION) {
            String view = pinfo.getPopComponentValue();

            if(view != null) {
               map.put(info.getName(), view);
               String name = view;

               while(name != null) {
                  name = map.get(name);

                  if(info.getName().equals(name)) {
                     return true;
                  }
               }

               Assembly assembly = vs.getAssembly(view);

               if(assembly != null) {
                  return checkPopDependency(vs, assembly.getInfo(), map);
               }
            }
         }
      }

      return false;
   }

   private void fixContainerProperties(RuntimeViewsheet rvs, VSAssemblyInfo oldInfo,
                                      VSAssemblyInfo newInfo, CommandDispatcher commandDispatcher)
      throws Exception
   {
      if(oldInfo.isPrimary() != newInfo.isPrimary()) {
         setAssemblyPrimary(rvs, newInfo.getName(), newInfo.isPrimary(),
                                               commandDispatcher);
      }
   }

   private void setAssemblyPrimary(RuntimeViewsheet rvs, String name, boolean primary,
                                  CommandDispatcher commandDispatcher) throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return;
      }

      VSAssembly as = (VSAssembly) vs.getAssembly(name);
      VSAssembly cass = as.getContainer();

      as.setPrimary(primary);

      // force components in tab/current selection to have
      // same primary setting as the tab/current selection
      // all container display same, see bug1255069279490
      if(cass instanceof ContainerVSAssembly) {
         cass.setPrimary(as.isPrimary());
         as = cass;
         coreLifecycleService.refreshVSAssembly(rvs, cass.getName(), commandDispatcher);
      }

      if(as instanceof ContainerVSAssembly) {
         Viewsheet vs2 = as.getViewsheet();
         String[] children = ((ContainerVSAssembly) as).getAssemblies();

         for(String childName : children) {
            VSAssembly as2 = (VSAssembly) vs2.getAssembly(childName);
            as2.setPrimary(as.isPrimary());
            coreLifecycleService.refreshVSAssembly(rvs, childName, commandDispatcher);
         }
      }

      coreLifecycleService.refreshVSAssembly(rvs, name, commandDispatcher);
   }


   private void addDeleteEmbeddedViewsheet(RuntimeViewsheet rvs,
                                          CommandDispatcher commandDispatcher)
      throws Exception
   {
      List<Assembly> assemblies = new ArrayList<>();
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return;
      }

      VSEventUtil.listEmbeddedAssemblies(vs, assemblies);

      for(Assembly assembly: assemblies) {
         coreLifecycleService.addDeleteVSObject(rvs, (VSAssembly) assembly, commandDispatcher);
      }
   }

   /**
    * Remove object from container.
    */
   private void removeObjectFromContainer(RuntimeViewsheet rvs, String uri,
                                         String name, CommandDispatcher dispatcher,
                                         String[] toBeRemovedObjs, List<String> processedObjs,
                                         List<String> needRefreshObjs)
      throws Exception
   {
      if(processedObjs.indexOf(name) >= 0) {
         return;
      }

      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return;
      }

      VSAssembly assembly = (VSAssembly) vs.getAssembly(name);

      if(assembly == null) {
         return;
      }

      VSAssembly cassembly = assembly.getContainer();

      if(!(cassembly instanceof GroupContainerVSAssembly)) {
         return;
      }

      GroupContainerVSAssembly gassembly = (GroupContainerVSAssembly) cassembly;
      boolean changed = false;

      for(String toBeRemovedObj : toBeRemovedObjs) {
         name = toBeRemovedObj;

         if(processedObjs.indexOf(name) >= 0) {
            continue;
         }

         if(!gassembly.containsAssembly(name)) {
            continue;
         }

         gassembly.removeAssembly(name);

         if(processedObjs.indexOf(name) < 0) {
            processedObjs.add(name);
            changed = true;
         }
      }

      if(!changed) {
         return;
      }

      String[] children = gassembly.getAssemblies();

      if(children == null || children.length <= 1) {
         ContainerVSAssembly container = (ContainerVSAssembly)
            gassembly.getContainer();

         // if a group container is in a tab, and it's removed when
         // only one child is left, replace the group container in
         // the tab with the only child
         if(container instanceof TabVSAssembly &&
            children != null && children.length == 1)
         {
            TabVSAssembly tab = (TabVSAssembly) container;
            String[] tabchildren = container.getAssemblies();

            for(int i = 0; i < tabchildren.length; i++) {
               if(gassembly.getName().equals(tabchildren[i])) {
                  if(tabchildren[i].equals(tab.getSelected())) {
                     tab.setSelectedValue(children[0]);
                  }

                  tabchildren[i] = children[0];
               }
            }

            tab.setAssemblies(tabchildren);
         }

         vsCompositionService.updateZIndex(vs, gassembly);
         coreLifecycleService.removeVSAssembly(rvs, uri, gassembly, dispatcher, false, true);
      }
      else {
         // should not update child position
         Point p = gassembly.getPixelOffset();
         p.move(0, gassembly.getPixelSize().height);
         gassembly.setAssemblies(new String[]{});
         gassembly.setPixelOffset(p);
         gassembly.setAssemblies(children);

         for(String childName : children) {
            if(needRefreshObjs.indexOf(childName) < 0) {
               needRefreshObjs.add(childName);
            }
         }
      }
   }


   private final CoreLifecycleService coreLifecycleService;
   private final VSInputService vsInputService;
   private final VSObjectTreeService vsObjectTreeService;
   private final VSAssemblyInfoHandler infoHander;
   private final ViewsheetService viewsheetService;
   private final VSWizardTemporaryInfoService temporaryInfoService;
   private final VSCompositionService vsCompositionService;
   private final SharedFilterService sharedFilterService;
   private final static String VIEWSHEET_FLAG = Catalog.getCatalog().getString("Current viewsheet");

   private final Logger LOG = LoggerFactory.getLogger(VSObjectPropertyService.class);
}
