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
package inetsoft.web.viewsheet.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.report.script.viewsheet.VSPropertyDescriptor;
import inetsoft.report.script.viewsheet.ViewsheetScope;
import inetsoft.uql.asset.AssemblyEntry;
import inetsoft.uql.asset.AssemblyRef;
import inetsoft.uql.asset.internal.WSExecution;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import inetsoft.util.UserMessage;
import inetsoft.web.binding.event.VSOnClickEvent;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.event.InputValue;
import inetsoft.web.viewsheet.event.VSSubmitEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.*;

@Controller
public class OnClickController {
   @Autowired
   public OnClickController(RuntimeViewsheetRef runtimeViewsheetRef,
                            CoreLifecycleService coreLifecycleService,
                            VSInputService inputService,
                            ViewsheetService viewsheetService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.coreLifecycleService = coreLifecycleService;
      this.viewsheetService = viewsheetService;
      this.inputService = inputService;
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/onclick/{name}/{x}/{y}/{isConfirm}")
   public void onConfirm(@DestinationVariable("name") String name,
                       @DestinationVariable("x") String x,
                       @DestinationVariable("y") String y,
                       @DestinationVariable("isConfirm") boolean isConfirm,
                       @Payload VSOnClickEvent confirmEvent,
                       @LinkUri String linkUri, Principal principal,
                         CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(
         runtimeViewsheetRef.getRuntimeId(), principal);
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      ViewsheetScope scope = box.getScope();
      List<UserMessage> usrmsg = new ArrayList<>();

      //Bug #21607 on confirm event, first execute script to get the correct message
      if(confirmEvent.confirmed()) {
         try {
            scope.execute("confirmEvent.confirmed = true", ViewsheetScope.VIEWSHEET_SCRIPTABLE);
         }
         catch(Exception ignore) {
         }

         onClick(name, x, y, linkUri, isConfirm, usrmsg, principal, dispatcher);
      }

      //set confirmEvent.confirmed back to false
      if(isConfirm) {
         try {
            scope.execute("confirmEvent.confirmed = false", ViewsheetScope.VIEWSHEET_SCRIPTABLE);
         }
         catch(Exception e) {
         }
      }

      //pop up confirm dialog
      String cmsg = Tool.getConfirmMessage();
      Tool.clearConfirmMessage();

      if(cmsg != null) {
         MessageCommand cmd = new MessageCommand();
         cmd.setMessage(cmsg);
         cmd.setType(MessageCommand.Type.CONFIRM);
         VSOnClickEvent event = new VSOnClickEvent();
         event.setConfirmEvent(true);
         cmd.addEvent("/events/onclick/" + name + "/" + x +
                         "/" + y + "/" + true, event);
         dispatcher.sendCommand(cmd);
      }

      if(!usrmsg.isEmpty()) {
         final UserMessage userMessage = usrmsg.get(0);

         if(userMessage != null) {
            dispatcher.sendCommand(MessageCommand.fromUserMessage(userMessage));
         }
      }
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/onclick/{name}/{x}/{y}")
   public void onClick(@DestinationVariable("name") String name,
                       @DestinationVariable("x") String x,
                       @DestinationVariable("y") String y,
                       @Payload VSSubmitEvent submitEvent,
                       @LinkUri String linkUri, Principal principal,
                       CommandDispatcher dispatcher) throws Exception
   {
      if(submitEvent != null && submitEvent.values() != null) {
         final InputValue[] inputValues = submitEvent.values();

         if(inputValues != null) {
            final String[] assemblyNames = Arrays.stream(inputValues)
               .map(InputValue::assemblyName)
               .toArray(String[]::new);
            final Object[] selectedObjects = Arrays.stream(inputValues)
               .map(InputValue::value)
               .toArray();
            this.inputService.multiApplySelection(assemblyNames, selectedObjects, principal, dispatcher, linkUri);
         }
      }

      onClick(name, x, y, linkUri, false, null, principal, dispatcher);
   }

   public void onClick(String name, String x, String y, String linkUri,
                       boolean isConfirm, List<UserMessage> usrmsg, Principal principal,
                       CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeViewsheetRef.getRuntimeId(),
                                                           principal);

      WSExecution.setAssetQuerySandbox(rvs.getViewsheetSandbox().getAssetQuerySandbox());

      try {
         process0(rvs, name, x, y, linkUri, isConfirm, usrmsg, principal, dispatcher);
      }
      finally {
         WSExecution.setAssetQuerySandbox(null);
      }
   }

   private void process0(RuntimeViewsheet rvs, String name, String xstr, String ystr,
                         String linkUri, boolean isConfirm, List<UserMessage> usrmsg,
                         Principal principal, CommandDispatcher dispatcher)
      throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(vs == null || box == null) {
         return;
      }

      VSAssembly assembly = (VSAssembly) vs.getAssembly(name);

      if(assembly == null) {
         LOG.warn("Assembly is missing, failed to process on click event: " + name);
         return;
      }

      if(!(assembly.getInfo() instanceof ClickableOutputVSAssemblyInfo ||
         assembly.getInfo() instanceof  ClickableInputVSAssemblyInfo))
      {
         return;
      }

      if(!assembly.getVSAssemblyInfo().isScriptEnabled()) {
         return;
      }

      ViewsheetSandbox box0 = getVSBox(name, box);
      ViewsheetScope scope = box0.getScope();
      String script = null;

      if(assembly.getInfo() instanceof ClickableOutputVSAssemblyInfo) {
         script = ((ClickableOutputVSAssemblyInfo) assembly.getInfo()).getOnClick();
      }
      else {
         script = ((ClickableInputVSAssemblyInfo) assembly.getInfo()).getOnClick();
      }

      if(xstr != null && ystr != null) {
         scope.put("mouseX", scope, xstr);
         scope.put("mouseY", scope, ystr);
      }

      // after onClick event, the viewsheet will be refreshed, which reset
      // the runtime values. If the changes in onClick is applied to RValue,
      // they will be lost immediately.
      VSPropertyDescriptor.setUseDValue(true);

      try {
         scope.execute(script, assembly.getName());
      }
      finally {
         VSPropertyDescriptor.setUseDValue(false);
      }

      UserMessage msg = Tool.getUserMessage();

      if(usrmsg != null) {
         usrmsg.add(msg);
      }

      Set<AssemblyRef> set = new HashSet<>();
      ChangedAssemblyList clist = this.coreLifecycleService.createList(true, dispatcher,
                                                                       rvs, linkUri);

      VSUtil.getReferencedAssets(script, set,
                                 name.contains(".") ? box0.getViewsheet() : vs, assembly);

      for(AssemblyRef ref : set) {
         switch(ref.getType()) {
         case AssemblyRef.OUTPUT_DATA:
            clist.getDataList().add(ref.getEntry());
            break;
         case AssemblyRef.INPUT_DATA:
            clist.getDataList().add(ref.getEntry());
            break;
         case AssemblyRef.VIEW:
            clist.getViewList().add(ref.getEntry());
            break;
         }
      }

      ArrayList<VSAssembly> inputAssemblies = new ArrayList<>();

      try {
         // fix bug1269914683174, need to refresh chart when the chart data changed
         // by onclick script
         for(AssemblyEntry obj : clist.getDataList()) {
            String name0 = obj.getAbsoluteName();
            VSAssembly assembly0 = (VSAssembly) vs.getAssembly(name0);

            if(assembly0 instanceof TableVSAssembly) {
               box.resetDataMap(name0);

               // @by yanie: fix #691, refresh FormTableLens after commit
               TableVSAssembly ta = (TableVSAssembly) assembly0;
               TableVSAssemblyInfo tinfo = (TableVSAssemblyInfo) ta.getInfo();

               if(tinfo.isForm()) {
                  FormTableLens flens = box.getFormTableLens(name0);

                  if(hasFormScript(script, name0) && flens.isChanged()) {
                     box.addScriptChangedForm(name0);
                  }

                  box.syncFormData(name0);
               }
            }
            else if(assembly0 instanceof CrosstabVSAssembly) {
               box.resetDataMap(name0);
            }
            else if(assembly0 instanceof ChartVSAssembly) {
               processChart(rvs, name0, linkUri, principal, dispatcher);
            }
            else if(assembly0 instanceof InputVSAssembly) {
               inputAssemblies.add(assembly0);
            }
         }

         for(VSAssembly inputAssembly : inputAssemblies) {
            box.processChange(inputAssembly.getAbsoluteName(),
               VSAssembly.OUTPUT_DATA_CHANGED, clist);
         }

         //If property "refresh viewsheet after submit" of the submit button is checked,
         //we should update whole viewsheet after clicking the button.
         if(assembly instanceof SubmitVSAssembly &&
            ((SubmitVSAssemblyInfo) assembly.getInfo()).isRefresh())
         {
            this.coreLifecycleService.refreshViewsheet(rvs, rvs.getID(), linkUri, dispatcher,
                                                       false, true, true, clist, true);
         }
         else {
            box.processChange(name, VSAssembly.INPUT_DATA_CHANGED, clist);
            coreLifecycleService.execute(rvs, name, linkUri, clist, dispatcher, true);
         }
      }
      finally {
         box.clearScriptChangedFormSet();
      }

      if(!isConfirm) {
         String cmsg = Tool.getConfirmMessage();
         Tool.clearConfirmMessage();

         if(cmsg != null) {
            try {
               scope.execute("confirmEvent.confirmed = false", ViewsheetScope.VIEWSHEET_SCRIPTABLE);
            }
            catch(Exception ignore) {
            }

            MessageCommand cmd = new MessageCommand();
            cmd.setMessage(cmsg);
            cmd.setType(MessageCommand.Type.CONFIRM);
            VSOnClickEvent event = new VSOnClickEvent();
            event.setConfirmEvent(true);
            cmd.addEvent("/events/onclick/" + name + "/" + xstr + "/" + ystr + "/" + true, event);
            dispatcher.sendCommand(cmd);
         }

         if(msg != null) {
            dispatcher.sendCommand(MessageCommand.fromUserMessage(msg));
         }
      }
   }

   private boolean hasFormScript(String script, String name) {
      if(!LicenseManager.isComponentAvailable(LicenseManager.LicenseComponent.FORM)) {
         return false;
      }

      List<String> formFunc = new ArrayList<>();
      formFunc.add("setObject");
      formFunc.add("appendRow");
      formFunc.add("insertRow");
      formFunc.add("deleteRow");

      for(int i = 0; i < formFunc.size(); i++) {
         StringBuffer buffer = new StringBuffer();
         buffer.append(name);
         buffer.append(".");
         buffer.append(formFunc.get(i));

         if(script.indexOf(buffer.toString()) != -1) {
            return true;
         }
      }

      return false;
   }

   /**
    * Process chart when chart data changed.
    */
   private void processChart(RuntimeViewsheet rvs, String name, String linkUri,
                             Principal principal, CommandDispatcher dispatcher)
         throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(vs == null || box == null) {
         return;
      }

      VSAssembly assembly = (VSAssembly) vs.getAssembly(name);
      box.clearGraph(name);
      coreLifecycleService.refreshVSAssembly(rvs, assembly, dispatcher);
   }

   /**
    * If viewsheet is embeded, get matching sandbox.
    */
   private ViewsheetSandbox getVSBox(String name, ViewsheetSandbox box0) {
      if(name.indexOf(".") == -1) {
         return box0;
      }

      int index = name.lastIndexOf(".");
      String vsName = name.substring(0, index);
      box0 = box0.getSandbox(vsName);

      return getVSBox(name.substring(index + 1, name.length()), box0);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final CoreLifecycleService coreLifecycleService;
   private final ViewsheetService viewsheetService;
   private final VSInputService inputService;

   private static final Logger LOG = LoggerFactory.getLogger(OnClickController.class);
}
