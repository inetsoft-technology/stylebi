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
import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.viewsheet.CalculateRef;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Catalog;
import inetsoft.web.binding.event.ModifyAggregateFieldEvent;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

@Service
@ClusterProxy
public class ModifyAggregateFieldService {
   public ModifyAggregateFieldService(ViewsheetService viewsheetService) {
      this.viewsheetService = viewsheetService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void modifyAggregateField(@ClusterProxyKey String id, ModifyAggregateFieldEvent event,
                                    Principal principal, CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(id, principal);
      Viewsheet vs = rvs.getViewsheet();
      String tname = event.getTableName();
      AggregateRef nref = event.getNewRef() == null ?
         null : (AggregateRef) event.getNewRef().createDataRef();
      AggregateRef oref = event.getOldRef() == null ?
         null : (AggregateRef) event.getOldRef().createDataRef();

      if(vs == null || (nref == null && oref == null)) {
         return null;
      }

      fixAggregateDataType(vs, tname, nref);

      try {
         // create?
         if(oref == null) {
            vs.addAggrField(tname, nref);
         }
         else {
            CalculateRef[] calcs = vs.getCalcFields(tname);

            if(calcs == null) {
               vs.removeAggrField(tname, oref);

               // edit? nref is null means remove
               if(nref != null) {
                  vs.addAggrField(tname, nref);
               }

               return null;
            }

            AggregateRef[] allagg = new AggregateRef[] {oref};
            List<String> usingCalcs = new ArrayList<>();

            for(int i = 0; i < calcs.length; i++) {
               CalculateRef calc = calcs[i];

               if(!calc.isBaseOnDetail()) {
                  List<String> matchNames = new ArrayList<>();
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
               if(!event.isConfirmed()) {
                  Catalog catalog = Catalog.getCatalog();
                  ConfirmException cevent = new ConfirmException(
                     catalog.getString("aggregate.vsused.warning") + usingCalcs,
                     ConfirmException.CONFIRM);
                  //cevent.setEvent(this);
                  throw cevent;
               }
            }

            vs.removeAggrField(tname, oref);

            // edit? nref is null means remove
            if(nref != null) {
               vs.addAggrField(tname, nref);
            }
         }
      }
      catch(ConfirmException ex) {
         throw ex;
      }
      catch(Exception e) {
         MessageCommand command = new MessageCommand();
         command.setMessage(e.getMessage());
         dispatcher.sendCommand(command);
         return null;
      }

      return null;
   }

   /**
    * fix data type for the viewsheet aggregate field.
    */
   private void fixAggregateDataType(Viewsheet vs, String tableName, AggregateRef aref) {
      if(aref == null || aref.getDataRef() instanceof ColumnRef) {
         return;
      }

      Worksheet ws = vs.getBaseWorksheet();
      Assembly assembly = ws == null ? null : ws.getAssembly(tableName);

      if(!(assembly instanceof TableAssembly)) {
         return;
      }

      TableAssembly table = (TableAssembly) assembly;
      ColumnSelection cols = table.getColumnSelection(true);
      DataRef ref = cols.getAttribute(aref.getName());

      if(ref == null) {
         return;
      }

      ColumnRef column = new ColumnRef(aref.getDataRef());
      column.setDataType(ref.getDataType());

      if(ref instanceof ColumnRef) {
         column.setSqlType(((ColumnRef) ref).getSqlType());
      }

      aref.setDataRef(column);
   }

   private final ViewsheetService viewsheetService;
}
