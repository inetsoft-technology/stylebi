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
package inetsoft.web.binding;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.AggregateRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Tool;
import inetsoft.web.adhoc.DecodeParam;
import inetsoft.web.binding.drm.*;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSColumnHandler;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

@RestController
public class VSFormulaController {
   @RequestMapping(value = "/api/composer/vsformula/fields", method=RequestMethod.GET)
   public Map<String, Object> getFields(@DecodeParam("vsId") String vsId,
                              @RequestParam("assemblyName") String assemblyName,
                              @RequestParam(value="tableName", required=false) String tableName,
      Principal principal)
      throws Exception
   {
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(Tool.byteDecode(vsId), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      box.lockRead();

      try {
         VSAssembly assembly = (VSAssembly) viewsheet.getAssembly(assemblyName);
         tableName = tableName == null ? assembly.getTableName() : tableName;

         ColumnSelection selection = columnHandler.getColumnSelection(
            rvs, viewsheetService, assemblyName, tableName, null,
            false, true, false, false, false, true);

         List<DataRefModel> columnFields = new ArrayList<>();
         List<DataRefModel> aggregateFields = new ArrayList<>();
         List<DataRefModel> allcolumns = new ArrayList<>();
         List<String> calcFieldsGroup = new ArrayList<>();
         List<String> userAggNames = new ArrayList<>();

         populateSelection(selection, columnFields, aggregateFields,
                           allcolumns, calcFieldsGroup, tableName);

         AggregateRef[] userAggs = viewsheet.getAggrFields(tableName);

         if(userAggs != null) {
            for(int i = 0; i < userAggs.length; i++) {
               userAggNames.add(userAggs[i].toString());
            }
         }

         Map<String, Object> result = new HashMap<>();
         result.put("columnFields", columnFields);
         result.put("aggregateFields", aggregateFields);
         result.put("allcolumns", allcolumns);
         result.put("calcFieldsGroup", calcFieldsGroup);
         result.put("sqlMergeable", selection.getProperty("sqlMergeable"));
         result.put("userAggNames", userAggNames);
         boolean isWS = viewsheet.getBaseEntry() != null && viewsheet.getBaseEntry() .isWorksheet();
         DataRefModel[] grayedOutFields = assemblyInfoHandler.getGrayedOutFields(rvs);

         if(grayedOutFields == null) {
            return result;
         }

         if(isWS) {
            List<DataRefModel> grayedFields = new ArrayList<>();

            for(int i = 0; i < grayedOutFields.length; i++) {
               DataRefModel refModel = grayedOutFields[i];

               if(Tool.equals(tableName, refModel.getEntity())) {
                  refModel.setName(refModel.getAttribute());
                  grayedFields.add(refModel);
               }
            }

            result.put("grayedOutFields", grayedFields);
         }
         else {
            result.put("grayedOutFields", grayedOutFields);
         }

         return result;
      }
      finally {
         box.unlockRead();
      }
   }

   /**
    * ModifyAggregateFieldEvent.
    */
   @RequestMapping(value = "/api/composer/modifyAggregateField", method=RequestMethod.PUT)
   public void modifyAggregateField(@RequestParam("vsId") String vsId,
      @RequestParam("assemblyName") String assemblyName,
      @RequestParam("tname") String tname,
      @RequestBody Map<String, AggregateRefModel> model,
      Principal principal) throws Exception
   {
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(Tool.byteDecode(vsId), principal);
      Viewsheet vs = rvs.getViewsheet();
      AggregateRef nref = null;
      AggregateRef oref = null;
      AggregateRefModel nmodel = (AggregateRefModel) model.get("nref");
      AggregateRefModel omodel = (AggregateRefModel) model.get("oref");

      if(nmodel != null) {
         nref = (AggregateRef) nmodel.createDataRef();
      }

      if(omodel != null) {
         oref = (AggregateRef) omodel.createDataRef();
      }

      if(oref == null) {
         vs.addAggrField(tname, nref);
         //addAggregateModelCommand(command, vs, tname);
      }
      else {
         CalculateRef[] calcs = vs.getCalcFields(tname);

         if(calcs == null) {
            vs.removeAggrField(tname, oref);

            // edit? nref is null means remove
            if(nref != null) {
               vs.addAggrField(tname, nref);
            }

            //addAggregateModelCommand(command, vs, tname);
            return;
         }

         /**
         AggregateRef[] allagg = new AggregateRef[] {oref};
         List<String> usingCalcs = new ArrayList<String>();

         for(int i = 0; i < calcs.length; i++) {
            CalculateRef calc = calcs[i];

            if(!calc.isBaseOnDetail()) {
               List<String> matchNames = new ArrayList<String>();
               ExpressionRef eref = (ExpressionRef) calc.getDataRef();
               String expression = eref.getExpression();
               List<AggregateRef> aggs =
                  VSUtil.findAggregate(allagg, matchNames, expression);

               if(aggs.size() > 0) {
                  usingCalcs.add(calc.getName());
               }
            }
         }

         if(usingCalcs.size() > 0) {
            if(!isConfirmed()) {
               Catalog catalog = Catalog.getCatalog();
               ConfirmException cevent = new ConfirmException(
                  catalog.getString("aggregate.vsused.warning") + usingCalcs,
                  ConfirmException.CONFIRM);
               cevent.setEvent(this);
               throw cevent;
            }
         }**/

         vs.removeAggrField(tname, oref);

         // edit? nref is null means remove
         if(nref != null) {
            vs.addAggrField(tname, nref);
         }

         //addAggregateModelCommand(command, vs, tname);
      }

   }

   /**
    * Populate the local column selection from out data.
    */
   private void populateSelection(ColumnSelection all, List<DataRefModel> columns,
      List<DataRefModel> aggregates, List<DataRefModel> allcolumns,
      List<String> calcFieldsGroup, String tname)
   {
      for(int i = 0; i < all.getAttributeCount(); i++) {
         DataRef ref = all.getAttribute(i);

         if(ref instanceof XAggregateRef || ref instanceof AggregateRef) {
            // consider the isAggregate case
            if(ref instanceof XAggregateRef) {
               DataRef cref = ((XAggregateRef) ref).getDataRef();

               // not support aggregate calc used on other
               if(cref instanceof CalculateRef && !((CalculateRef) cref).isBaseOnDetail() ||
                  VSUtil.isPreparedCalcField(ref))
               {
                  continue;
               }
            }

            if(ref instanceof AggregateRef) {
               String name = ((AggregateRef) ref).getDataRef().getAttribute();

               if(isCalcAggregate(all, name)) {
                  continue;
               }
            }

            // aggregate ref not check contains it nor not
            DataRefModel agg = refModelFactoryService.createDataRefModel(ref);

            if(agg instanceof AggregateRefModel) {
               AggregateRefModel nagg = (AggregateRefModel) agg;

               if(nagg.getRef() != null) {
                  String attr = nagg.getRef().getAttribute();

                  for(int j = 0; j < columns.size(); j++) {
                     if(!(columns.get(j) instanceof CalculateRefModel) &&
                        Tool.equals(attr, columns.get(j).getAttribute()))
                     {
                        nagg.setRef(columns.get(j));
                        break;
                     }
                  }
               }

               if(nagg.getName().startsWith(tname + "_O.")) {
                  continue;
               }

               if(!containsAgg(aggregates, nagg)) {
                  aggregates.add(nagg);
               }
            }
            else {
               aggregates.add(agg);
            }
         }
         else {
            if(ref instanceof CalculateRef) {
               if(VSUtil.isPreparedCalcField(ref)) {
                  continue;
               }

               if(((CalculateRef) ref).isBaseOnDetail()) {
                  columns.add(refModelFactoryService.createDataRefModel(ref));
               }

               calcFieldsGroup.add(ref.getName());
            }
            else {
               columns.add(refModelFactoryService.createDataRefModel(ref));
            }

            allcolumns.add(refModelFactoryService.createDataRefModel(ref));
         }
      }
   }

   private boolean containsAgg(List<DataRefModel> aggregates, DataRefModel agg) {
      for(int i = 0; i < aggregates.size(); i++) {
         DataRefModel ref = aggregates.get(i);

         if(Tool.equals(ref.getView(), agg.getView())) {
            return true;
         }
      }

      return false;
   }

   private Boolean isCalcAggregate(ColumnSelection all, String name) {
      for(int i = 0; i < all.getAttributeCount(); i++) {
         DataRef ref = all.getAttribute(i);

         if(VSUtil.isPreparedCalcField(ref) && name.equals(ref.getName())) {
            return true;
         }
      }

      return false;
   }

   @Autowired
   public void setViewsheetService(ViewsheetService viewsheetService) {
      this.viewsheetService = viewsheetService;
   }

   @Autowired
   public void setAssemblyInfoHandler(VSAssemblyInfoHandler assemblyInfoHandler) {
      this.assemblyInfoHandler = assemblyInfoHandler;
   }

   @Autowired
   private VSColumnHandler columnHandler;
   @Autowired
   private DataRefModelFactoryService refModelFactoryService;
   private ViewsheetService viewsheetService;
   private VSAssemblyInfoHandler assemblyInfoHandler;
}
