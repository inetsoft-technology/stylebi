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
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.ConditionList;
import inetsoft.uql.ConditionListWrapper;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssemblyInfo;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.*;
import inetsoft.web.viewsheet.DataTipDependencyCheckResult;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.event.OpenDataTipEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.web.bind.annotation.*;

import java.awt.*;
import java.security.Principal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.function.Predicate;

@RestController
public class VSDataTipController {
   @Autowired
   public VSDataTipController(
      RuntimeViewsheetRef runtimeViewsheetRef,
      CoreLifecycleService coreLifecycleService,
      ViewsheetService viewsheetService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.coreLifecycleService = coreLifecycleService;
      this.viewsheetService = viewsheetService;
   }

   /**
    * Handle displaying and applying conditions for a datatip.
    */
   @LoadingMask
   @MessageMapping("/datatip/open")
   public void applyDataTip(@Payload OpenDataTipEvent event,
                            Principal principal,
                            @LinkUri String linkUri,
                            CommandDispatcher dispatcher) throws Exception
   {
      String tipView = event.getName();
      String parent = event.getParent();
      String conds = event.getConds();

      RuntimeViewsheet rvs = viewsheetService.getViewsheet(
         runtimeViewsheetRef.getRuntimeId(), principal);
      Viewsheet vs = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      Worksheet ws = box.getWorksheet();
      VSAssembly comp = vs.getAssembly(parent);

      if(comp == null) {
         return;
      }

      AbstractTableAssembly tassembly = (AbstractTableAssembly)
         ws.getAssembly(comp.getTableName());
      ConditionList preList = null;

      if(tassembly != null) {
         preList = (ConditionList) tassembly.getPreRuntimeConditionList();
      }

      if(tipView == null) {
         return;
      }

      // data tip is self
      if(parent != null && parent.equals(tipView)) {
         return;
      }

      VSAssembly tip = vs.getAssembly(tipView);

      if(tip == null || ((comp.getVSAssemblyInfo() instanceof TipVSAssemblyInfo)
         && !VSEventUtil.checkTipViewValid(comp, comp.getViewsheet())))
      {
         return;
      }

      boolean applied = applyAll(tip, vsobj -> {
            try {
               ConditionList conditionList = conds == null ?
                  new ConditionList() : VSUtil.getConditionList(rvs, comp, conds, true);
               Object clist = VSUtil.fixCondition(rvs, vsobj, conditionList, parent, conds);

               if(Tool.equals(VSAssembly.NONE_CHANGED, clist)) {
                  return false;
               }
               else {
                  ConditionListWrapper clistWrapper = null;

                  if(clist instanceof ConditionList) {
                     clistWrapper = (ConditionList) clist;
                  }

                  vsobj.setTipConditionList(clistWrapper);
                  return true;
               }
            }
            catch(Exception ex) {
               throw new RuntimeException(ex);
            }
         });

      int hint = applied ? VSAssembly.INPUT_DATA_CHANGED : VSAssembly.NONE_CHANGED;
      VSAssemblyInfo info = (VSAssemblyInfo) tip.getInfo();

      // set visibility before executing script
      info.setVisible(true);

      // apply alpha on tipview
      VSAssemblyInfo parentInfo = comp.getVSAssemblyInfo();

      if(parentInfo instanceof TipVSAssemblyInfo) {
         setAlpha(((TipVSAssemblyInfo) parentInfo).getAlpha(), info.getFormatInfo());
      }
      else if(parentInfo instanceof PopVSAssemblyInfo) {
         setAlpha(((PopVSAssemblyInfo) parentInfo).getAlpha(), info.getFormatInfo());
      }

      if(info.getFormat().getBackground() == null && !(info instanceof GaugeVSAssemblyInfo)) {
         info.getFormat().getUserDefinedFormat().setBackground(new Color(245, 245, 249));
      }

      // the visible will be set to fix in execute() -> fixAssemblyInfo()
      // change it to true so data tip will show up
      applyAll(tip, vsobj -> {
            try {
               coreLifecycleService.execute(rvs, vsobj.getAbsoluteName(), linkUri, hint, dispatcher);
               vsobj.getInfo().setVisible(conds != null);
               coreLifecycleService.refreshVSAssembly(rvs, vsobj, dispatcher);
               return true;
            }
            catch(MessageException ex) {
               throw ex;
            }
            catch(Exception ex) {
               throw new RuntimeException(ex);
            }
         });

      // @by ankitmathur, Fix bug #5872, similar to bug1432218253134 and bug
      // #409, we need to maintain the original Pre-Runtime Condition List for
      // the assembly so that we can reset after the Tip Event.
      if(tassembly != null) {
         tassembly.setPreRuntimeConditionList(preList);
      }
   }

   // call assembly and children
   private static boolean applyAll(VSAssembly vsobj, Predicate<VSAssembly> func) {
      boolean rc = func.test(vsobj);

      long applied = 0;
      VSAssemblyInfo info = (VSAssemblyInfo) vsobj.getInfo();

      if(info instanceof ContainerVSAssemblyInfo) {
         final Viewsheet vs = info.getViewsheet();
         applied = Arrays.stream(((ContainerVSAssemblyInfo) info).getAssemblies()).filter(
            name -> {
               Assembly obj = vs.getAssembly(name);
               return obj != null && func.test((VSAssembly) obj);
            }).count();
      }

      return rc || applied > 0;
   }

   // Set alpha to all backgrounds of tipview
   private static void setAlpha(String alphaStr, FormatInfo finfo) {
      int alpha = (alphaStr != null) ? (int) Double.parseDouble(alphaStr) : 100;

      finfo.getFormats().forEach(fmt -> fmt.getUserDefinedFormat().setAlphaValue(alpha));
      finfo.getFormat(VSAssemblyInfo.OBJECTPATH).getUserDefinedFormat().setAlphaValue(alpha);
   }

   @RequestMapping(
      value = "/api/composer/vs/check-datatip-dependency",
      method = RequestMethod.GET
   )
   @ResponseBody
   public DataTipDependencyCheckResult checkDataTipDependency(
      @RequestParam("runtimeId") String runtimeId,
      @RequestParam("assemblyName") String assemblyName,
      @RequestParam("tipView") String tipView,
      Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      DataTipDependencyCheckResult result = new DataTipDependencyCheckResult();

      AbstractVSAssembly tipViewAssembly = (AbstractVSAssembly) viewsheet.getAssembly(tipView);

      result.setCycle(checkTipDependency(viewsheet, tipViewAssembly.getVSAssemblyInfo(),
                                         assemblyName, new HashMap<>()));

      if(result.getCycle()) {
         result.setMessage(Catalog.getCatalog(principal)
                           .getString("common.dependencyCycle"));
         return result;
      }

      return result;
   }

   /**
    * Checks for DataTip View cycle dependency. Mostly taken from
    * {@link inetsoft.analytic.composition.event.EditPropertyOverEvent#checkTipDependency}
    */
   private boolean checkTipDependency(Viewsheet vs, AssemblyInfo info, String mainAssembly,
                                      HashMap<String, String> map)
   {
      if(info instanceof TipVSAssemblyInfo) {
         TipVSAssemblyInfo tinfo = (TipVSAssemblyInfo) info;

         if(tinfo.getTipOptionValue() == TipVSAssemblyInfo.VIEWTIP_OPTION) {
            String view = tinfo.getTipViewValue();

            // if tip view is set to same as the current assembly, it's ignored at
            // runtime so we shouldn't check for the self-cycle
            if(view != null && !view.equals(info.getName())) {
               map.put(info.getName(), view);
               String name = view;

               if(vs.getAssembly(mainAssembly).getContainer() != null &&
                  vs.getAssembly(mainAssembly).getContainer().getName().equals(name)) {
                  return true;
               }

               if(mainAssembly.equals(name)) {
                  return true;
               }

               while(name != null) {
                  name = map.get(name);

                  if(mainAssembly.equals(name) || info.getName().equals(name)) {
                     return true;
                  }
               }

               Assembly assembly = vs.getAssembly(view);

               if(assembly != null) {
                  return checkTipDependency(vs, assembly.getInfo(), mainAssembly,  map);
               }
            }
         }
      }

      if(info instanceof GroupContainerVSAssemblyInfo) {
         String[] assemblies = ((GroupContainerVSAssemblyInfo) info).getAbsoluteAssemblies();

         for(int i = 0; i < assemblies.length; i++){
            AssemblyInfo aInfo = vs.getAssembly(assemblies[i]).getInfo();

            if(aInfo instanceof TipVSAssemblyInfo) {
               TipVSAssemblyInfo tInfo = (TipVSAssemblyInfo) aInfo;

               if(tInfo.getTipOptionValue() == TipVSAssemblyInfo.VIEWTIP_OPTION) {
                  String view = tInfo.getTipViewValue();

                  if(view != null && !view.equals(info.getName()) && mainAssembly.equals(view)) {
                     return true;
                  }
               }
            }
         }
      }

      return false;
   }

   private final CoreLifecycleService coreLifecycleService;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final ViewsheetService viewsheetService;
}
