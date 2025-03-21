/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.web.binding.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.cluster.*;
import inetsoft.report.*;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.uql.xmla.XMLAHandler;
import inetsoft.uql.xmla.XMLAQuery2;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.util.script.ScriptEnv;
import inetsoft.web.binding.command.SetVSBindingModelCommand;
import inetsoft.web.binding.drm.CalculateRefModel;
import inetsoft.web.binding.event.ModifyCalculateFieldEvent;
import inetsoft.web.binding.event.RefreshBindingTreeEvent;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSChartHandler;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.controller.VSRefreshController;
import inetsoft.web.viewsheet.event.VSRefreshEvent;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.vswizard.command.*;
import inetsoft.web.vswizard.handler.VSWizardBindingHandler;
import inetsoft.web.vswizard.model.VSWizardEditModes;
import inetsoft.web.vswizard.recommender.WizardRecommenderUtil;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;

@Service
@ClusterProxy
public class ModifyCalculateFieldService {
   public ModifyCalculateFieldService(
      VSBindingService bindingFactory,
      VSBindingTreeController bindingTreeController,
      VSChartHandler chartHandler,
      XRepository xrepository,
      VSWizardBindingHandler wizardBindingHandler,
      VSRefreshController refreshController,
      ViewsheetService viewsheetService,
      VSAssemblyInfoHandler assemblyInfoHandler)
   {
      this.bindingFactory = bindingFactory;
      this.bindingTreeController = bindingTreeController;
      this.chartHandler = chartHandler;
      this.xrepository = xrepository;
      this.wizardBindingHandler = wizardBindingHandler;
      this.refreshController = refreshController;
      this.viewsheetService = viewsheetService;
      this.assemblyInfoHandler = assemblyInfoHandler;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void modifyCalculateField(@ClusterProxyKey String id, ModifyCalculateFieldEvent event,
                                    Principal principal, CommandDispatcher dispatcher,
                                    String linkUri)
      throws Exception
   {
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(id, principal);
      Viewsheet vs = rvs.getViewsheet();
      CalculateRef cref = event.calculateRef() == null ?
         null : (CalculateRef) event.calculateRef().createDataRef();
      String refname = event.refName();

      if(vs == null || (cref == null && refname == null)) {
         return null;
      }

      String tname = event.tableName();
      String dimtype = event.dimType();
      boolean create = event.create();
      boolean remove = event.remove();

      CalculateRef oldCalcRef = null;

      if(!create) {
         oldCalcRef = vs.getCalcField(tname, refname);

         if(oldCalcRef == null) {
            return null;
         }

         oldCalcRef = (CalculateRef) oldCalcRef.clone();
      }

      boolean rename = !create && refname != null && cref != null &&
         !refname.equals(cref.getName());
      boolean changeCalcType = !create && cref != null && oldCalcRef != null &&
         oldCalcRef.isBaseOnDetail() != cref.isBaseOnDetail();
      String oldPath = null;
      VSAssembly ass = vs.getAssembly(event.name());
      String wizardFixedTableName = tname;

      if(rename && event.wizard()) {
         wizardFixedTableName =
            wizardBindingHandler.fixTableName(vs.getBaseEntry(), ass, oldCalcRef, tname, principal);
         oldPath = wizardBindingHandler.getColumnPath(
            ((ChartVSAssembly) vs.getAssembly(event.name())).getAggregateInfo(),
            oldCalcRef, wizardFixedTableName);
      }

      // performance consider, if no change, return
      if(!remove && cref != null) {
         CalculateRef ocalc = vs.getCalcField(tname, refname == null ? cref.getName() : refname);

         if(ocalc != null) {
            if(isSameCalc(ocalc, cref)) {
               return null;
            }

            if(!Objects.equals(ocalc.getDataType(), cref.getDataType()) && event.wizard()) {
               wizardBindingHandler.calculatedRefTypeChanged(rvs, ass, ocalc, cref, dispatcher);
            }
         }
      }

      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(box == null) {
         return null;
      }

      AssetQuerySandbox wbox = box.getAssetQuerySandbox();
      ScriptEnv env = wbox == null ? null : wbox.getScriptEnv();
      CalculateRef oldCalc = null;

      try {
         if(remove) {
            syncAssemblies(tname, refname, vs, rename, changeCalcType, cref, dispatcher, rvs, linkUri);
            vs.removeCalcField(tname, refname);
         }
         else {
            if(changeCalcType) {
               vs.removeCalcField(tname, refname);
            }

            // rename and change calc type maybe affect in one time. So if change calc type is not
            // support, remove it. If support, rename it.
            if(rename) {
               oldCalc = vs.getCalcField(tname, refname);
               vs.removeCalcField(tname, refname);
               vs.addCalcField(tname, cref);
               syncAssemblies(tname, refname, vs, rename, changeCalcType, cref, dispatcher, rvs, linkUri);
               Assembly assembly = vs.getAssembly(event.name());

               if(assembly instanceof CalcTableVSAssembly) {
                  renameCol(((CalcTableVSAssembly) assembly).getTableLayout(),
                            refname, cref.getName());
               }
            }
            // If only change calc type, not change name. Check if support or not. If not, remove it
            else if(changeCalcType) {
               syncAssemblies(tname, refname, vs, rename, changeCalcType, cref, dispatcher, rvs, linkUri);
            }

            convertUsedAggregateRef(cref, tname, vs);
            vs.addCalcField(tname, cref);
         }
      }
      catch(Exception e) {
         MessageCommand command = new MessageCommand();
         command.setMessage(e.getMessage());
         dispatcher.sendCommand(new MessageCommand());
         return null;
      }

      /*if(ass == null) {
         return;
      }*/

      // refresh chart tree model event
      if(ass instanceof ChartVSAssembly) {
         ChartVSAssembly chart = (ChartVSAssembly) ass;
         VSChartInfo cinfo = chart.getVSChartInfo();
         SourceInfo srcInfo = chart.getSourceInfo();

         if(cinfo == null || srcInfo == null) {
            SourceInfo src = new SourceInfo(SourceInfo.ASSET, null, tname);
            cinfo = new DefaultVSChartInfo();
            chart.setSourceInfo(src);
            chart.setVSChartInfo(cinfo);
            chart.getVSChartInfo().setAggregateInfo(new AggregateInfo());
            VSUtil.setDefaultGeoColumns(cinfo, rvs, tname);

            if(changeCalcType && cinfo instanceof VSMapInfo) {
               ((ChartVSAssembly) ass).removeGeoColumns(refname, cinfo);
            }
         }
      }
      else if(ass instanceof CrosstabVSAssembly) {
         CrosstabVSAssembly cross = (CrosstabVSAssembly) ass;
         VSCrosstabInfo cinfo = cross.getVSCrosstabInfo();
         SourceInfo srcInfo = cross.getSourceInfo();

         if(cinfo == null || srcInfo == null) {
            cinfo = new VSCrosstabInfo();
            SourceInfo src = new SourceInfo(VSUtil.isVSAssemblyBinding(tname) ?
                                               SourceInfo.VS_ASSEMBLY : SourceInfo.ASSET, null, tname);
            cross.setVSCrosstabInfo(cinfo);
            cross.setSourceInfo(src);
            cross.getVSCrosstabInfo().setAggregateInfo(new AggregateInfo());
         }
      }
      else if(ass instanceof CalcTableVSAssembly) {
         CalcTableVSAssembly calc = (CalcTableVSAssembly) ass;
         CalcTableVSAssemblyInfo calcInfo = (CalcTableVSAssemblyInfo) calc.getVSAssemblyInfo();

         if(calcInfo == null) {
            calcInfo = new CalcTableVSAssemblyInfo();
            calc.setVSAssemblyInfo(calcInfo);
         }

         AggregateInfo ainfo = createAggregateInfo(engine.getAssetRepository(), vs,
                                                   calc.getAbsoluteName(), calc.getSourceInfo(), principal);
         calcInfo.setAggregateInfo(ainfo);
      }

      // remodified other chart/crosstab aggregate info
      boolean infoChanged = false;

      if(!remove) {
         Assembly[] assembies = vs.getAssemblies();

         for(int i = 0; i < assembies.length; i++) {
            if(!(assembies[i] instanceof CubeVSAssembly)) {
               continue;
            }

            CubeVSAssembly cube = (CubeVSAssembly) assembies[i];

            if(!Tool.equals(tname, cube.getTableName())) {
               continue;
            }

            AggregateInfo ainfo = cube.getAggregateInfo();

            if(ainfo != null) {
               if(create) {
                  boolean isdim = "true".equals(dimtype) ||
                     !"false".equals(dimtype) && !VSEventUtil.isMeasure(cref);

                  if(!cref.isBaseOnDetail()) {
                     isdim = false;
                  }

                  if(isdim) {
                     ainfo.addGroup(new GroupRef(cref));
                  }
                  else {
                     ainfo.addAggregate(new AggregateRef(cref, AggregateFormula.SUM));
                  }
               }
               else {
                  AggregateRef aref = ainfo.getAggregate(oldCalc);

                  if(aref != null) {
                     aref.setDataRef(cref);
                  }

                  GroupRef gref = ainfo.getGroup(oldCalc);

                  if(gref != null) {
                     gref.setDataRef(cref);
                  }
               }

               infoChanged = true;
            }
         }
      }

      // refresh chart tree model event
      if(ass instanceof ChartVSAssembly) {
         ChartVSAssembly chart = (ChartVSAssembly) ass;

         if(changeCalcType) {
            chart.changeBindingCalcType(refname, chart.getVSChartInfo(), cref, event.wizard());
            chart.getVSChartInfo().setAggregateInfo(new AggregateInfo());
            chartHandler.fixAggregateInfo(chart.getChartInfo(), vs, null);
         }
         else {
            chartHandler.fixAggregateInfo(chart.getChartInfo(), vs, chart.getAggregateInfo());
         }
      }
      else if(ass instanceof CrosstabVSAssembly) {
         CrosstabVSAssembly cross = (CrosstabVSAssembly) ass;
         TableAssembly tbl = VSEventUtil.getTableAssembly(vs, cross.getSourceInfo(),
                                                          engine.getAssetRepository(), principal);
         AggregateInfo nainfo = new AggregateInfo();

         if(changeCalcType) {
            VSEventUtil.createAggregateInfo(tbl, nainfo, null, vs, true);
         }
         else {
            VSEventUtil.createAggregateInfo(tbl, nainfo, cross.getAggregateInfo(), vs, true);
         }

         cross.getVSCrosstabInfo().setAggregateInfo(nainfo);
      }

      if(event.wizard()) {
         // refresh wizard binding.
         dispatcher.sendCommand(new RefreshWizardTreeTriggerCommand());

         if(rename) {
            String path = wizardBindingHandler.getColumnPath(
               ((ChartVSAssembly) ass).getAggregateInfo(), cref, wizardFixedTableName);
            ReplaceColumnCommand command =
               new ReplaceColumnCommand(Arrays.asList(path), Arrays.asList(oldPath));
            dispatcher.sendCommand(command);
         }
         // editing calc field needs to fire recommand command
         else if(!remove && !changeCalcType && !create) {
            dispatcher.sendCommand(new FireRecommandCommand());
         }
      }
      else {
         RefreshBindingTreeEvent refreshBindingTreeEvent = new RefreshBindingTreeEvent();
         refreshBindingTreeEvent.setName(event.name());
         bindingTreeController.getBinding(refreshBindingTreeEvent, principal, dispatcher);
      }

      boolean wizardNewVs =
         VSWizardEditModes.WIZARD_DASHBOARD.equalsIgnoreCase(event.wizardOriginalMode());

      if((!event.wizard() || event.remove() && wizardNewVs) && (infoChanged || !create)) {
         WizardRecommenderUtil.setIgnoreRefreshTempAssembly(event.wizard());
         VSRefreshEvent refresh = VSRefreshEvent.builder().confirmed(false).build();
         refreshController.refreshViewsheet(refresh, principal, dispatcher, linkUri);
         WizardRecommenderUtil.setIgnoreRefreshTempAssembly(null);
      }

      // not check confirm, if need confirm, will throws the exception,
      // not reach here
      /**
       if(infoChanged || !create) {
       VSRefreshEvent refresh = new VSRefreshEvent();
       refresh.setID(getID());
       refresh.setLinkURI(getLinkURI());

       try {
       AssetEvent.MAIN.set(this);
       refresh.process(rvs, command);
       }
       catch(ConfirmException e) {
       // mv on-demand
       command.addCommand(new MessageCommand(e.getMessage(),
       MessageCommand.PROGRESS));
       }
       }*/

      if(ass != null) {
         BindingModel binding = bindingFactory.createModel(ass);
         SetVSBindingModelCommand bcommand = new SetVSBindingModelCommand(binding);
         dispatcher.sendCommand(bcommand);
      }

      /*@temp by davezhang while ass is null,also can add CalcField.
      VSObjectModel model = ModelBuilder.builder(ass.getClass())
         .assembly(ass).runtimeViewsheet(rvs).build();
      RefreshVSObjectCommand command = new RefreshVSObjectCommand();
      command.setInfo(model);
      dispatcher.sendCommand(command);*/

      try {
         // check the expression valid or not
         if(!remove && cref != null &&
            !checkScriptValid(cref, tname, env, principal, dispatcher))
         {
            return null;
         }
      }
      catch(Exception e) {
         dispatcher.sendCommand(new MessageCommand());
      }

      return null;
   }

   private AggregateInfo createAggregateInfo(AssetRepository engine, Viewsheet vs,
                                             String name, SourceInfo sinfo, Principal principal)
   {
      TableAssembly tbl = VSEventUtil.getTableAssembly(vs, sinfo, engine, principal);

      if(tbl == null) {
         return null;
      }

      AggregateInfo nainfo = new AggregateInfo();
      // create default aggregte info, the old aggregate info is null
      VSEventUtil.createAggregateInfo(tbl, nainfo, null, vs, true);

      return nainfo;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public String getInUseAssemblies(@ClusterProxyKey String runtimeId,
                                    String tname, String refname, Principal principal)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null || tname == null || refname == null) {
         return "";
      }

      Assembly[] assembies = vs.getAssemblies();

      for(int i = 0; i < assembies.length; i++) {
         if(!(assembies[i] instanceof BindableVSAssembly) || assembies[i] instanceof InputVSAssembly) {
            continue;
         }

         BindableVSAssembly bind = ((BindableVSAssembly) assembies[i]);
         String vtable = bind.getTableName();

         if(!Tool.equals(tname, vtable)) {
            continue;
         }

         List<String> list = getUsingAssemblies(bind, refname);

         if(list.size() > 0) {
            return String.join(", ", list);
         }
      }

      return "";
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public String checkTrap(@ClusterProxyKey String runtimeId, String tname,
                           String refname, String create, CalculateRefModel calc,
                           Principal principal)
      throws Exception
   {
      boolean trap = false;

      if(runtimeId == null) {
         return "false";
      }

      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      CalculateRef cref = calc == null || calc.getDataRefModel() == null
         ? null : (CalculateRef) calc.createDataRef();

      // check modelTrap?
      String oname = cref != null ? cref.getDataRef().getName() : null;
      boolean rename = !"true".equals(create) && refname != null && cref != null &&
         !refname.equals(cref.getName());

      try {
         if(rename) {
            // first change the new ref's name to check model trap
            cref.setName(refname);
         }

         if(cref != null) {
            trap = containsTrap(tname, rvs, cref);
         }
      }
      catch(Exception ex) {
         // ignore it
      }
      finally {
         if(cref != null) {
            cref.setName(oname);
         }
      }

      return trap ? "true" : "false";
   }

   /**
    * Check the script validity.
    */
   private boolean checkScriptValid(CalculateRef cref, String tname, ScriptEnv env,
                                    Principal principal, CommandDispatcher dispatcher) throws Exception
   {
      // if create an expression measure, check if the expression is valid mdx string
      if((cref.getRefType() & DataRef.CUBE_MEASURE) == DataRef.CUBE_MEASURE) {
         String source = tname;
         String cubeName = source.substring(source.lastIndexOf("/") + 1);
         String sourceName = source.substring(Assembly.CUBE_VS.length(),
                                              source.lastIndexOf("/"));

         XDataSource ds = xrepository.getDataSource(sourceName);

         XMLAHandler handler = new XMLAHandler();
         XMLAQuery2 query = new XMLAQuery2();
         query.addMeasureRef(cref);
         query.setDataSource(ds);
         query.setCube(cubeName);

         AggregateInfo agginfo = new AggregateInfo();
         AggregateRef agg = new AggregateRef(cref, AggregateFormula.NONE);
         agginfo.addAggregate(agg);
         query.setAggregateInfo(agginfo);
         handler.connect(ds, new VariableTable());
         handler.execute(query, new VariableTable(), principal, null);
      }
      else {
         ExpressionRef exp = (ExpressionRef) cref.getDataRef();
         String estr = exp.getExpression();
         boolean issql = cref.isSQL();
         String str = ExpressionRef.getSQLExpression(issql, estr);

         if(issql) {
            if(!XUtil.isSQLExpressionValid(str)) {
               MessageCommand command = new MessageCommand();
               command.setMessage(
                  Catalog.getCatalog().getString("designer.qb.query.errFound"));
               dispatcher.sendCommand(command);
               return false;
            }
         }
         else if(env != null) {
            try {
               Object script = env.compile(str);
            }
            catch(Exception ex) {
               String suggestion = env.getSuggestion(ex, null);
               String msg = "Script error: " + ex.getMessage() +
                  (suggestion != null ? "\nTo fix: " + suggestion : "") +
                  "\nScript failed:\n" + XUtil.numbering(str);
               MessageCommand command = new MessageCommand();
               command.setMessage(msg);
               command.setType(MessageCommand.Type.ERROR);
               dispatcher.sendCommand(command);
               return false;
            }
         }
      }

      // write the ok to clinet to close the editor dialog
      //command.addCommand(new MessageCommand("", MessageCommand.OK));
      return true;
   }

   /**
    * Get the binding vsassembly which binding to the tname source.
    */
   private void syncAssemblies(String tname, String refname, Viewsheet vs, boolean rename,
                               boolean changeCalcType, CalculateRef cref,
                               CommandDispatcher dispatcher, RuntimeViewsheet rvs,
                               String linkUri)
      throws Exception
   {
      Assembly[] assembies = vs.getAssemblies();

      for(int i = 0; i < assembies.length; i++) {
         if(!(assembies[i] instanceof BindableVSAssembly) ||
            assembies[i] instanceof InputVSAssembly)
         {
            continue;
         }

         BindableVSAssembly bind = ((BindableVSAssembly) assembies[i]);
         String vtable = bind.getTableName();

         if(!Tool.equals(tname, vtable)) {
            continue;
         }

         // when rename and change calc type affect on one action, fix calc type before
         // rename. So the refname will same when change calc type.
         if(rename) {
            if(changeCalcType) {
               bind.changeCalcType(refname, cref);
            }

            bind.renameBindingCol(refname, cref.getName());
         }
         else if(changeCalcType) {
            bind.changeCalcType(refname, cref);
         }
         else {
            bind.removeBindingCol(refname);
         }

         assemblyInfoHandler.apply(rvs, bind.getVSAssemblyInfo().clone(), viewsheetService,
                                   false, false, false, false, dispatcher, null, null,
                                   linkUri, null);
      }
   }

   private List<String> getUsingAssemblies(BindableVSAssembly bind, String refname) {
      DataRef[] refs = bind.getAllBindingRefs();
      List<String> usingAssemblies = new ArrayList<>();

      for(int j = 0; j < refs.length; j++) {
         if(isUsed(refs[j], refname)) {
            if(bind instanceof AbstractVSAssembly) {
               String aname = bind.getName();

               if(!usingAssemblies.contains(aname)) {
                  usingAssemblies.add(aname);
               }
            }
         }
      }

      return usingAssemblies;
   }

   /**
    * Check the ref used the oldname represent dataref.
    */
   private boolean isUsed(DataRef ref, String oldname){
      if(Tool.equals(ref.getName(), oldname)) {
         return true;
      }

      if(ref instanceof VSAggregateRef) {
         VSAggregateRef aref = (VSAggregateRef) ref;
         return Tool.equals(aref.getSecondaryColumnValue(), oldname) ||
            Tool.equals(aref.getColumnValue(), oldname);
      }
      else if(ref instanceof VSDimensionRef) {
         VSDimensionRef dref = (VSDimensionRef) ref;
         return Tool.equals(dref.getGroupColumnValue(), oldname);
      }

      return false;
   }

   /**
    * Convert the cref used auto create aggregate ref to user create
    * aggregate ref, for example, gauge binding to sum(price),
    * agg calc use it, then delelte the gauge, the agg calc become invalid.
    */
   private void convertUsedAggregateRef(CalculateRef calc, String tname,
                                        Viewsheet vs)
   {
      if(!calc.isBaseOnDetail()) {
         AggregateRef[] all = VSUtil.getAggregates(vs, tname, true);
         ExpressionRef eref = (ExpressionRef) calc.getDataRef();
         String exstr = eref.getExpression();
         List<String> matchs = new ArrayList<>();
         List<AggregateRef> aggs = VSUtil.findAggregate(all, matchs, exstr);

         for(int i = 0; i < aggs.size(); i++) {
            AggregateRef aref = aggs.get(i);
            vs.addAggrField(tname, aref);
         }
      }
   }

   // rename column bound to cells
   private void renameCol(TableLayout layout, String oname, String nname) {
      List<TableCellBinding> cells = LayoutTool.getTableCellBindings(layout, 0);

      for(TableCellBinding cell : cells) {
         if(oname.equals(cell.getValue())) {
            cell.setValue(nname);
         }
      }
   }

   /**
    * Check the two calculateref is the same or not.
    */
   private boolean isSameCalc(CalculateRef ref1, CalculateRef ref2) {
      if(ref1 == null) {
         return ref2 == null;
      }

      if(!ref1.equals(ref2)) {
         return false;
      }

      ExpressionRef eref1 = (ExpressionRef) ref1.getDataRef();
      ExpressionRef eref2 = (ExpressionRef) ref2.getDataRef();

      return ref1.isSQL() == ref2.isSQL() && ref1.isBaseOnDetail() == ref2.isBaseOnDetail() &&
         Tool.equals(ref1.getDataType(), ref2.getDataType()) &&
         eref1.equalsContent(eref2);
   }

   /**
    * Get model trap command if needed.
    */
   private boolean containsTrap(String tname, RuntimeViewsheet rvs, CalculateRef cref) {
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return false;
      }

      VSModelTrapContext mtc = new VSModelTrapContext(rvs, true);
      AbstractModelTrapContext.TrapInfo trapInfo = mtc.checkCalcTrap(tname, cref);

      if(trapInfo.showWarning()) {
         return true;
      }

      return false;
   }

   private final VSBindingService bindingFactory;
   private final VSBindingTreeController bindingTreeController;
   private final VSRefreshController refreshController;
   private final VSChartHandler chartHandler;
   private final XRepository xrepository;
   private final ViewsheetService viewsheetService;
   private final VSWizardBindingHandler wizardBindingHandler;
   private final VSAssemblyInfoHandler assemblyInfoHandler;
}
