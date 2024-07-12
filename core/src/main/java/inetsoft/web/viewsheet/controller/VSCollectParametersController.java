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
package inetsoft.web.viewsheet.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.util.XSessionService;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;
import inetsoft.util.audit.Audit;
import inetsoft.util.audit.ExecutionRecord;
import inetsoft.web.composer.ws.assembly.VariableAssemblyModelInfo;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.event.CollectParametersOverEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.awt.*;
import java.security.Principal;
import java.util.List;
import java.util.*;

@Controller
public class VSCollectParametersController {
   /**
    * Creates a new instance of <tt>VSCollectParametersController</tt>.
    *
    * @param runtimeViewsheetRef reference to the runtime viewsheet associated with the
    *                            WebSocket session.
    */
   @Autowired
   public VSCollectParametersController(RuntimeViewsheetRef runtimeViewsheetRef,
                                        PlaceholderService placeholderService,
                                        ViewsheetService viewsheetService,
                                        AssetRepository assetRepository)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.placeholderService = placeholderService;
      this.viewsheetService = viewsheetService;
      this.assetRepository = assetRepository;
   }

   @LoadingMask
   @MessageMapping("/vs/collectParameters")
   public void collectParameters(@Payload CollectParametersOverEvent event,
                                 Principal principal,
                                 @LinkUri String linkUri,
                                 CommandDispatcher dispatcher)
      throws Exception
   {
      RuntimeViewsheet rvs =
         viewsheetService.getViewsheet(runtimeViewsheetRef.getRuntimeId(), principal);
      VariableTable vtable = new VariableTable();
      Principal user = rvs.getUser();
      Viewsheet vs = rvs.getViewsheet();
      AssetEntry entry = rvs.getEntry();
      String vsName = entry.getSheetName();
      String userSessionID = principal == null ?
         XSessionService.createSessionID(XSessionService.USER, null) :
         ((XPrincipal) principal).getSessionID();
      String objectName = entry.getDescription();
      String execSessionID = event.disableAudit() ? null : rvs.getExecSessionID();
      String objectType = ExecutionRecord.OBJECT_TYPE_VIEW;
      String execType = ExecutionRecord.EXEC_TYPE_FINISH;
      java.sql.Date execTimestamp = new java.sql.Date(System.currentTimeMillis());

      ExecutionRecord executionRecord = event.disableAudit() ? null :
         new ExecutionRecord(execSessionID, userSessionID, objectName, objectType,
                             execType, execTimestamp, ExecutionRecord.EXEC_STATUS_SUCCESS,
                             null);
      try {
         fillVariableTable(event.variables(), vtable, user, vsName);
      }
      catch(Exception ex) {
         this.placeholderService.sendMessage(ex.toString(), MessageCommand.Type.ERROR,
                                             dispatcher);

         return;
      }

      if(vs == null) {
         return;
      }

      resetVariable(rvs.getViewsheetSandbox(), rvs.getViewsheet(), vtable);

      try {
         // @by stephenwebster, For Bug #6758
         // Handle the association between input assemblies and variable assemblies
         // Prompting for these parameters creates a reverse dependency on the
         // InputVSAssemblies which are tied to variables
         syncInputAssemblies(vtable, vs.getAssemblies());
      }
      catch(Exception syncException) {
         LOG.warn("Unable to synchronize input assemblies" +
            " with parameters");
      }

      if(principal instanceof SRPrincipal) {
         ThreadContext.setLocale(((SRPrincipal) principal).getLocale());
      }

      Object size = rvs.getProperty("viewsheet.appliedScale");
      int width = 0;
      int height = 0;

      if(size instanceof Dimension) {
         width = ((Dimension) size).width;
         height = ((Dimension) size).height;
      }

      try {
         VSUtil.OPEN_VIEWSHEET.set(event.openVS());
         ChangedAssemblyList clist =
            this.placeholderService.createList(false, dispatcher, rvs, linkUri);
         rvs.getViewsheetSandbox().clearInit();
         this.placeholderService.refreshViewsheet(rvs, rvs.getID(), linkUri, width, height, false,
            null, dispatcher, false, true, true, clist);
      }
      finally {
         VSUtil.OPEN_VIEWSHEET.remove();
      }

      if(!event.disableAudit() && executionRecord != null) {
         Audit.getInstance().auditExecution(executionRecord, principal);
      }
   }

   /**
    * Finds input assemblies on this viewsheet and attempts to synchronize the selected
    * values of these assemblies if the assemblies actually are driven by variables with the
    * same name as collected during the CollectParametersOverEvent.  This will ensure that
    * both the parameter and selected objects remain in sync.
    * @param vtable The variable table populated by this event
    * @param assemblies The viewsheet assemblies
    * @throws Exception
    */
   private void syncInputAssemblies(VariableTable vtable, Assembly[] assemblies)
      throws Exception
   {
      Enumeration variables = vtable.keys();

      while(variables.hasMoreElements()) {
         Object variable = variables.nextElement();

         for(Assembly assembly : assemblies) {
            if(assembly instanceof InputVSAssembly) {
               InputVSAssembly iassembly = (InputVSAssembly) assembly;
               String tableName = iassembly.getTableName();

               // The table name is the variable name inside the $() notation
               // when it is bound to a variable
               if(iassembly.isVariable() && tableName != null) {
                  tableName = tableName.substring(2, tableName.length() - 1);
               }
               else {
                  continue;
               }

               if(Tool.equals(tableName, variable)) {
                  if(assembly instanceof SingleInputVSAssembly) {
                     ((SingleInputVSAssembly) assembly).
                        setSelectedObject(vtable.get(tableName));
                  }
                  else if(assembly instanceof CompositeInputVSAssembly) {
                     Object[] variableValue =
                        (vtable.get(tableName) instanceof Object[]) ?
                           (Object[]) vtable.get(tableName) :
                           new Object[]{ vtable.get(tableName) };

                     ((CompositeInputVSAssembly) assembly).
                        setSelectedObjects(variableValue);
                  }
               }
            }
         }
      }
   }

   /**
    * Fill variables into variable table.
    * @param vtable store all the variables.
    * @param user the user name will used to set property.
    * @param vsName the name of the edited viewsheet.
    */
   private void fillVariableTable(List<VariableAssemblyModelInfo> variables,
                                    VariableTable vtable, Principal user,
                                    String vsName) throws Exception
   {
      Set dbs = new HashSet();

      variables.stream()
         .forEach((variable) -> {
            if(variable.getValue() != null && variable.getValue().length > 0) {
               Object[] values = new Object[variable.getValue().length];

               for(int k = 0; k < values.length; k++) {
                  Object tempVal = variable.getValue()[k];
                  values[k] = tempVal == null ? null :
                     Tool.getData(variable.getType(), tempVal + "", true);
               }

               String vname = variable.getName();

               // Comma separated lists of values should already be split by the VariableInputDialog in the frontend
               // Leaving it in an array ensures that it doesn't get split again in AssetCondition
               if(values.length == 1 && !(values[0] instanceof String && values[0].toString().contains(","))) {
                  vtable.put(vname, values[0]);
               }
               else {
                  vtable.put(vname, values);
                  vtable.setAsIs(vname, variable.getUsedInOneOf());
               }

               if(user == null || vsName == null) {
                  return;
               }

               Object value0 = values.length == 1 ? values[0] : values;
               viewsheetService.setCachedProperty(user, vsName + " variable : " + vname, value0);

               if(vname.startsWith(XUtil.DB_PASSWORD_PREFIX)) {
                  String db = vname.substring(XUtil.DB_PASSWORD_PREFIX.length());
                  dbs.add(db);
               }
            }
         });

      if(dbs.size() == 0) {
         return;
      }

      Object session = assetRepository.getSession();
      XRepository rep = XFactory.getRepository();
      Iterator iterator = dbs.iterator();

      while(iterator.hasNext()) {
         String db = (String) iterator.next();
         XDataSource ds = rep.getDataSource(db);

         if(db != null) {
            rep.testDataSource(session, ds, vtable);
            String name = (String) vtable.get(XUtil.DB_USER_PREFIX + db);
            String pass = (String) vtable.get(XUtil.DB_PASSWORD_PREFIX + db);

            // copy to user to avoid always prompt
            if(name != null && pass != null && user instanceof XPrincipal) {
               ((XPrincipal) user).setProperty(XUtil.DB_USER_PREFIX + db,
                                               name);
               ((XPrincipal) user).setProperty(XUtil.DB_PASSWORD_PREFIX+ db,
                                               pass);
            }

            rep.connect(session, ":" + db, vtable);
         }
      }
   }

   private void resetVariable(ViewsheetSandbox vbox, Viewsheet vs,
                                VariableTable vtable)
      throws Exception
   {
      if(vbox == null || vs == null) {
         return;
      }

      Assembly[] assemblies = vs.getAssemblies();

      for(int i = 0; i < assemblies.length; i++) {
         Assembly assembly = assemblies[i];

         if(assembly instanceof Viewsheet) {
            resetVariable(vbox.getSandbox(assembly.getAbsoluteName()),
                          (Viewsheet) assembly, vtable);
         }
      }

      AssetQuerySandbox box = vbox.getAssetQuerySandbox();

      if(box != null) {
         box.refreshVariableTable(vtable);
      }
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final PlaceholderService placeholderService;
   private final ViewsheetService viewsheetService;
   private final AssetRepository assetRepository;

   private static final Logger LOG =
      LoggerFactory.getLogger(CollectParametersOverEvent.class);
}
