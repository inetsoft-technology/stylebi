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
package inetsoft.web.composer.ws.assembly;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.uql.asset.*;
import inetsoft.util.GroupedThread;
import inetsoft.util.log.LogContext;

import java.security.Principal;

public class WSAssemblyModelFactory {
   private WSAssemblyModelFactory() {
   }

   public static WSAssemblyModel createModelFrom(
      WSAssembly assembly, RuntimeWorksheet rws, Principal principal) throws Exception
   {
      WSAssemblyModel model = null;

      GroupedThread.withGroupedThread(groupedThread -> {
         AssetEntry wsEntry = rws.getEntry();

         if(wsEntry != null) {
            groupedThread.addRecord(LogContext.WORKSHEET, wsEntry.getPath());
         }

         if(assembly != null) {
            groupedThread.addRecord(LogContext.ASSEMBLY, assembly.getName());
         }
      });

      if(assembly instanceof AbstractTableAssembly) {
         model = createTableModelFrom((AbstractTableAssembly) assembly, rws, principal);
      }
      else if(assembly instanceof VariableAssembly) {
         model = createVariableModelFrom((VariableAssembly) assembly, rws);
      }
      else if(assembly instanceof NamedGroupAssembly) {
         model = createGroupingModelFrom((NamedGroupAssembly) assembly, rws);
      }

      GroupedThread.withGroupedThread(groupedThread -> {
         AssetEntry wsEntry = rws.getEntry();

         if(wsEntry != null) {
            groupedThread.removeRecord(LogContext.WORKSHEET.getRecord(wsEntry.getPath()));
         }

         if(assembly != null) {
            groupedThread.removeRecord(LogContext.ASSEMBLY.getRecord(assembly.getName()));
         }
      });

      return model;
   }

   public static TableAssemblyModel createTableModelFrom(
      AbstractTableAssembly assembly, RuntimeWorksheet rws, Principal principal)
   {
      return TableAssemblyModelFactory.createModelFrom(assembly, rws, principal);
   }

   public static VariableAssemblyModel createVariableModelFrom(
      VariableAssembly assembly, RuntimeWorksheet rws) throws Exception
   {
      return new VariableAssemblyModel(assembly, rws);
   }

   public static GroupingAssemblyModel createGroupingModelFrom(
      NamedGroupAssembly assembly, RuntimeWorksheet rws)
   {
      return new GroupingAssemblyModel(assembly, rws);
   }
}
