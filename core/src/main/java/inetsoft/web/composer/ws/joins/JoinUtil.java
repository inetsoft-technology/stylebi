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
package inetsoft.web.composer.ws.joins;

import inetsoft.uql.asset.*;
import inetsoft.util.Catalog;
import inetsoft.web.composer.ws.event.WSRefreshAssemblyEvent;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;

import java.util.*;

public class JoinUtil {
   public static boolean tableContainsSubtable(CompositeTableAssembly table, String subtable) {
      return Arrays.stream(table.getTableNames()).anyMatch((name) -> name.equals(subtable));
   }

   /**
    * Check the table whether is a join member table.
    *
    * @param ws worksheet.
    * @param tableAssembly checking table.
    * @return
    * @throws Exception
    */
   public static boolean isJoinMember0(Worksheet ws, TableAssembly tableAssembly,
                                       boolean isJoinTableSub,
                                       Map<Assembly, Boolean> checked)
   {
      if(ws == null || tableAssembly == null) {
         return false;
      }

      if(checked.get(tableAssembly) != null) {
         return false;
      }

      checked.put(tableAssembly, true);

      if(isJoinTableSub && tableAssembly.isLiveData()) {
         return true;
      }

      AssemblyRef[] assemblyRefs = ws.getDependings(tableAssembly.getAssemblyEntry());

      if(assemblyRefs == null) {
         return false;
      }

      for(AssemblyRef assemblyRef : assemblyRefs) {
         if(assemblyRef == null || assemblyRef.getEntry() == null) {
            continue;
         }

         Assembly assembly = ws.getAssembly(assemblyRef.getEntry().getName());

         if(!(assembly instanceof TableAssembly)) {
            continue;
         }

         if(assembly instanceof CompositeTableAssembly) {
            TableAssemblyOperator operator = ((CompositeTableAssembly) assembly)
               .getOperator(tableAssembly.getName());

            if(operator != null) {
               TableAssemblyOperator.Operator[] operators = operator.getOperators();

               for(TableAssemblyOperator.Operator op : operators) {
                  if(!op.isJoin() ) {
                     continue;
                  }

                  isJoinTableSub = true;

                  if(((CompositeTableAssembly) assembly).isLiveData()) {
                     return true;
                  }
               }
            }
         }

         if(isJoinMember0(ws, (TableAssembly) assembly, isJoinTableSub, checked)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check the table whether is a join member table.
    *
    * @param ws worksheet.
    * @param tableAssembly checking table.
    * @return
    * @throws Exception
    */
   public static boolean hasLiveDataJoinMember(Worksheet ws, TableAssembly tableAssembly)
   {
      return isJoinMember0(ws, tableAssembly, false, new HashMap<>());
   }

   /**
    * Check the table whether is a join member table.
    *
    * @param assemblyName table name.
    * @param commandDispatcher command dispatcher.
    * @return
    * @throws Exception
    */
   public static void confirmJoinDependingsToMeta(String assemblyName,
                                                  CommandDispatcher commandDispatcher)
   {
      MessageCommand msgCmd = new MessageCommand();
      msgCmd.setMessage(Catalog.getCatalog().getString("composer.ws.joinTablesToMeta"));
      msgCmd.setType(MessageCommand.Type.CONFIRM);
      WSRefreshAssemblyEvent event = new WSRefreshAssemblyEvent();
      event.setAssemblyName(assemblyName);
      event.setRecursive(true);
      event.setReset(true);
      msgCmd.addNoEvent("/events/composer/worksheet/table/refresh-data", event);
      msgCmd.addEvent("/events/composer/worksheet/dependings-table-mode/default", event);
      commandDispatcher.sendCommand(msgCmd);
   }
}
