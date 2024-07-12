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
package inetsoft.web.binding.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.web.binding.command.SetVSBindingModelCommand;
import inetsoft.web.binding.controller.ConvertTableRefController;
import inetsoft.web.binding.handler.ClearTableHeaderAliasHandler;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.model.AbstractBDRefModel;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.model.table.CrosstabBindingModel;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.List;

@Service
public class ConvertTableRefService {
   public ConvertTableRefService(VSBindingService bindingFactory,
                                 VSAssemblyInfoHandler assemblyInfoHandler,
                                 ViewsheetService viewsheetService)
   {
      this.bindingFactory = bindingFactory;
      this.assemblyInfoHandler = assemblyInfoHandler;
      this.viewsheetService = viewsheetService;
   }

   public void convertTableRef(String[] refNames, int convertType,
                               inetsoft.web.binding.model.SourceInfo source,
                               boolean sourceChange, String name, RuntimeViewsheet rvs,
                               Principal principal, CommandDispatcher dispatcher)
      throws Exception
   {
      convertTableRef0(refNames, convertType, source, sourceChange, name, rvs, principal,
         dispatcher, true);
   }

   public void convertTableRef0(String[] refNames, int convertType,
                                inetsoft.web.binding.model.SourceInfo source,
                                boolean sourceChange, String name, RuntimeViewsheet rvs,
                                Principal principal, CommandDispatcher dispatcher,
                                boolean refreshBinding)
      throws Exception
   {
      ViewsheetService engine = viewsheetService;
      Viewsheet vs = rvs.getViewsheet();
      TableDataVSAssembly assembly = (TableDataVSAssembly) vs.getAssembly(name);
      SourceInfo sinfo = source.toSourceAttr(assembly.getSourceInfo());
      AggregateInfo ainfo = null;

      if(assembly instanceof CrosstabVSAssembly) {
         VSCrosstabInfo cinfo = ((CrosstabVSAssembly) assembly).getVSCrosstabInfo();

         if(cinfo == null) {
            cinfo = new VSCrosstabInfo();
            ((CrosstabVSAssembly) assembly).setVSCrosstabInfo(cinfo);
         }

         if(cinfo.getAggregateInfo() == null) {
            ainfo = createAggregateInfo(engine.getAssetRepository(), vs, name, sinfo, principal);
            cinfo.setAggregateInfo(ainfo);
         }

         ainfo = cinfo.getAggregateInfo();
      }
      else if(assembly instanceof CalcTableVSAssembly) {
         CalcTableVSAssemblyInfo calcInfo = (CalcTableVSAssemblyInfo) assembly.getVSAssemblyInfo();

         if(calcInfo == null) {
            calcInfo = new CalcTableVSAssemblyInfo();
            assembly.setVSAssemblyInfo(calcInfo);
         }

         if(calcInfo.getAggregateInfo() == null) {
            ainfo = createAggregateInfo(engine.getAssetRepository(), vs, name, sinfo, principal);
            calcInfo.setAggregateInfo(ainfo);
         }

         ainfo = calcInfo.getAggregateInfo();
         assembly.removeBindingCol(refNames[0]);
      }

      ainfo = sourceChange ? null : ainfo;

      // no aggregate info? create a default aggregate info
      if(ainfo == null || ainfo.isEmpty()) {
         ainfo = createAggregateInfo(engine.getAssetRepository(), vs, name, sinfo, principal);
      }

      assembly.setSourceInfo(sinfo);

      for(String refName : refNames) {
         VSEventUtil.fixAggInfoByConvertRef(ainfo, convertType, refName);
      }

      FormatInfo formatInfo = null;

      if(assembly instanceof CrosstabVSAssembly) {
         VSCrosstabInfo cinfo = ((CrosstabVSAssembly) assembly).getVSCrosstabInfo();
         formatInfo = assembly.getFormatInfo();
         cinfo.setAggregateInfo(ainfo);
      }
      else if(assembly instanceof CalcTableVSAssembly) {
         CalcTableVSAssemblyInfo calcInfo = (CalcTableVSAssemblyInfo) assembly.getVSAssemblyInfo();
         calcInfo.setAggregateInfo(ainfo);
      }

      BindingModel binding = bindingFactory.createModel(assembly);

      for(String refName : refNames) {
         convertField(binding, refName, formatInfo);
      }

      if(!refreshBinding) {
         return;
      }

      VSAssembly clone = bindingFactory.updateAssembly(binding, assembly);
      VSAssemblyInfo ninfo = (VSAssemblyInfo) clone.getInfo();
      assemblyInfoHandler.apply(rvs, ninfo, engine, false, false, true, true, dispatcher);

      SetVSBindingModelCommand bcommand = new SetVSBindingModelCommand(binding);
      dispatcher.sendCommand(bcommand);
   }

   /**
    * remove binding field when convert field
    */
   private void convertField(BindingModel binding, String name, FormatInfo formatInfo) {
      if(binding instanceof CrosstabBindingModel) {
         CrosstabBindingModel bindingModel = (CrosstabBindingModel) binding;
         this.markOldField(bindingModel, name, formatInfo);
      }
   }

   private void markOldField(CrosstabBindingModel bindingModel, String name, FormatInfo formatInfo) {
      this.markOldField0(name, bindingModel.getRows(), formatInfo);
      this.markOldField0(name, bindingModel.getCols(), formatInfo);
      this.markOldField0(name, bindingModel.getAggregates(), formatInfo);
   }

   private void markOldField0(String name, List<? extends AbstractBDRefModel> fields,
                              FormatInfo formatInfo)
   {
      for(int i = 0; i < fields.size(); i++) {
         if(fields.get(i).getName().equals(name)) {
            AbstractBDRefModel refModel = fields.remove(i);

            if(refModel != null) {
               ClearTableHeaderAliasHandler.clearHeaderAlias(
                  refModel.createDataRef(), formatInfo, i);
            }

            break;
         }
      }
   }

   /**
    * CreateAggregateInfoEvent.
    **/
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

   private final VSBindingService bindingFactory;
   private final VSAssemblyInfoHandler assemblyInfoHandler;
   public final ViewsheetService viewsheetService;
   private static final Logger LOG =
      LoggerFactory.getLogger(ConvertTableRefController.class);
}
