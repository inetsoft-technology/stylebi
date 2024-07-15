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
package inetsoft.web.binding.dnd.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.DataMap;
import inetsoft.report.composition.execution.VSAQuery;
import inetsoft.uql.asset.ConfirmException;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.binding.command.SetVSBindingModelCommand;
import inetsoft.web.binding.event.VSDndEvent;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.PlaceholderService;
import org.springframework.beans.factory.annotation.Autowired;

import java.security.Principal;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * This class handles get vsobjectmodel from the server.
 */
public class VSAssemblyDndController {
   /**
    * Creates a new instance of <tt>VSViewController</tt>.
    *
    * @param runtimeViewsheetRef reference to the runtime viewsheet associated with the
    *                            WebSocket session.
    */
   @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
   @Autowired
   public VSAssemblyDndController(
      RuntimeViewsheetRef runtimeViewsheetRef,
      VSBindingService bfactory,
      VSAssemblyInfoHandler assemblyInfoHandler,
      VSObjectModelFactoryService objectModelService,
      ViewsheetService viewsheetService,
      PlaceholderService placeholderService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.bfactory = bfactory;
      this.assemblyInfoHandler = assemblyInfoHandler;
      this.viewsheetService = viewsheetService;
      this.placeholderService = placeholderService;
   }

   protected void changeSource(VSAssembly assembly, String table, int sourceType) {
      assemblyInfoHandler.changeSource(assembly, table, sourceType);
   }

   protected void validateBinding(VSAssembly nassembly) {
      assemblyInfoHandler.validateBinding(nassembly);
   }

   protected boolean sourceChanged(VSAssembly assembly, String table) {
      SourceInfo sinfo = ((DataVSAssemblyInfo) assembly.getInfo()).getSourceInfo();
      return sinfo == null || assemblyInfoHandler.sourceChanged(table, assembly);
   }

   protected RuntimeViewsheet getRuntimeVS(Principal principal) throws Exception {
      String id = runtimeViewsheetRef.getRuntimeId();

      if(id == null) {
         return null;
      }

      return viewsheetService.getViewsheet(id, principal);
   }

   protected VSAssembly getVSAssembly(RuntimeViewsheet rvs, String name) {
      Viewsheet viewsheet = rvs.getViewsheet();
      return viewsheet.getAssembly(name);
   }

   protected void applyAssemblyInfo(RuntimeViewsheet rvs, VSAssembly oassembly,
                                    VSAssembly nassembly, CommandDispatcher dispatcher,
                                    VSDndEvent event, String url, String linkUri)
      throws Exception
   {
      applyAssemblyInfo(rvs, oassembly, nassembly, dispatcher,
         event, url, linkUri, null, null);
   }

   protected void applyAssemblyInfo(RuntimeViewsheet rvs, VSAssembly oassembly,
                                    VSAssembly nassembly, CommandDispatcher dispatcher,
                                    VSDndEvent event, String url, String linkUri,
                                    Consumer<VSCrosstabInfo> updateCalculate,
                                    BiConsumer<Map<TableDataPath, VSCompositeFormat>, TableDataPath> clearAliasFormatProcessor)
      throws Exception
   {
      // validate current binding when source changed.
      if(oassembly instanceof DataVSAssembly) {
         SourceInfo osource = ((DataVSAssemblyInfo) oassembly.getInfo()).getSourceInfo();
         SourceInfo nsource = ((DataVSAssemblyInfo) nassembly.getInfo()).getSourceInfo();

         if(osource != null && osource.getSource() != null && nsource != null) {
            if(!osource.getSource().equals(nsource.getSource()) &&
               nsource.getType() == SourceInfo.VS_ASSEMBLY)
            {
               VSAQuery query = VSAQuery.createVSAQuery(rvs.getViewsheetSandbox(),
                                                        nassembly, DataMap.DETAIL);
               query.createAssemblyTable(nassembly.getTableName());
            }

            if(!osource.getSource().equals(nsource.getSource()) ||
               VSUtil.isVSAssemblyBinding(event.getTable()))
            {
               validateBinding(nassembly);
            }
         }
      }

      applyAssemblyInfo(rvs, oassembly, (VSAssemblyInfo) nassembly.getInfo(), dispatcher,
         event, url, linkUri, updateCalculate, clearAliasFormatProcessor);
   }

   protected void applyAssemblyInfo(RuntimeViewsheet rvs, VSAssembly assembly,
                                    VSAssemblyInfo clone, CommandDispatcher dispatcher,
                                    VSDndEvent event, String linkUri,
                                    Consumer<VSCrosstabInfo> updateCalculate)
      throws Exception
   {
      applyAssemblyInfo(rvs, assembly, clone, dispatcher, event, null, linkUri, updateCalculate);
   }

   protected void applyAssemblyInfo(RuntimeViewsheet rvs, VSAssembly assembly,
                                    VSAssemblyInfo clone, CommandDispatcher dispatcher,
                                    VSDndEvent event, String url, String linkUri,
                                    Consumer<VSCrosstabInfo> updateCalculate)
      throws Exception
   {
      applyAssemblyInfo(rvs, assembly, clone, dispatcher, event, url,
         linkUri, updateCalculate, null);
   }

   protected void applyAssemblyInfo(RuntimeViewsheet rvs, VSAssembly assembly,
      VSAssemblyInfo clone, CommandDispatcher dispatcher, VSDndEvent event,
      String url, String linkUri, Consumer<VSCrosstabInfo> updateCalculate,
      BiConsumer<Map<TableDataPath, VSCompositeFormat>, TableDataPath> clearAliasFormatProcessor)
      throws Exception
   {
      assemblyInfoHandler.apply(rvs, clone, viewsheetService, event.confirmed(),
         event.checkTrap(), false, false, dispatcher, url,
         event, linkUri, updateCalculate, clearAliasFormatProcessor);

      try {
         createDndCommands(rvs, assembly, dispatcher, event, linkUri);
      }
      catch(ConfirmException ex) {
         if(!placeholderService.waitForMV(ex, rvs, dispatcher)) {
            throw ex;
         }
      }
   }

   protected void createDndCommands(RuntimeViewsheet rvs, VSAssembly assembly,
                                    CommandDispatcher dispatcher, VSDndEvent event,
                                    String linkUri) throws Exception
   {
      final BindingModel binding = bfactory.createModel(assembly);
      final SetVSBindingModelCommand bcommand = new SetVSBindingModelCommand(binding);
      dispatcher.sendCommand(bcommand);
      placeholderService.refreshVSAssembly(rvs, assembly, dispatcher);
   }

   private final VSBindingService bfactory;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final VSAssemblyInfoHandler assemblyInfoHandler;
   private final ViewsheetService viewsheetService;
   private final PlaceholderService placeholderService;
}
