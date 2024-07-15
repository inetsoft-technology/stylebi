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
package inetsoft.web.composer.ws.joins;

import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.PlaceholderService;
import inetsoft.web.vswizard.command.FireRecommandCommand;
import inetsoft.web.vswizard.recommender.WizardRecommenderUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;

/**
 * Handle re-executing the query after prompting for a cross join
 */
@Service
public class CrossJoinService {
   @Autowired
   public CrossJoinService(PlaceholderService service) {
      this.service = service;
   }

   void executeCrossjoinAssemblies(Principal principal,
                                   CommandDispatcher dispatcher, String linkUri,
                                   RuntimeSheet sheet, String tableName) throws Exception
   {
      if(sheet instanceof RuntimeWorksheet) {
         refreshWorksheet(principal, dispatcher, (RuntimeWorksheet) sheet, tableName);
      }
      else {
         refreshViewsheet(dispatcher, linkUri, (RuntimeViewsheet) sheet, tableName);
      }
   }

   private void refreshViewsheet(CommandDispatcher dispatcher, String linkUri,
                                 RuntimeViewsheet sheet, String tableName) throws Exception
   {
      RuntimeWorksheet rws;
      rws = sheet.getRuntimeWorksheet();
      final Viewsheet viewsheet = sheet.getViewsheet();
      final Worksheet worksheet = rws.getWorksheet();
      final Assembly joinTable = worksheet.getAssembly(tableName);
      ((TableAssembly) joinTable).setProperty(CONFIRM_CROSSJOIN, "true");

      tableName = VSUtil.stripOuter(tableName);

      final Assembly table = worksheet.getAssembly(tableName);
      final AssemblyEntry assemblyEntry = table.getAssemblyEntry();
      final AssemblyRef[] dependings = viewsheet.getDependings(assemblyEntry);
      final ViewsheetSandbox box = sheet.getViewsheetSandbox();
      final ChangedAssemblyList clist = service.createList(true, dispatcher, sheet, linkUri);
      box.lockWrite();

      try {
         if(sheet.isWizardViewsheet()) {
            final VSAssembly tempAssembly = WizardRecommenderUtil.getTempAssembly(viewsheet);

            if(tempAssembly != null) {
               service.execute(sheet, tempAssembly.getName(), linkUri,
                               VSAssembly.INPUT_DATA_CHANGED, dispatcher);
            }
            else {
               dispatcher.sendCommand(new FireRecommandCommand());
            }
         }
         else {
            for(AssemblyRef depending : dependings) {
               final Assembly assembly = viewsheet.getAssembly(depending.getEntry());

               if(assembly instanceof VSAssembly) {
                  service.execute(sheet, assembly.getName(), linkUri,
                                  VSAssembly.INPUT_DATA_CHANGED, dispatcher);
                  service.refreshVSAssembly(sheet, ((VSAssembly) assembly), dispatcher);
               }
            }
         }

         service.refreshViewsheet(sheet, sheet.getID(), linkUri, dispatcher,
                                  true, true, true, clist);
      }
      finally {
         box.unlockWrite();
      }
   }

   private void refreshWorksheet(Principal principal, CommandDispatcher commandDispatcher,
                                 RuntimeWorksheet sheet, String tableName) throws Exception
   {
      final Worksheet worksheet = sheet.getWorksheet();
      final Assembly assembly = worksheet.getAssembly(tableName);
      ((TableAssembly) assembly).setProperty(CONFIRM_CROSSJOIN, "true");
      WorksheetEventUtil.refreshAssembly(sheet, tableName, true, commandDispatcher, principal);
   }


   public static final String CONFIRM_CROSSJOIN = "confirm.crossjoin";
   private final PlaceholderService service;
}