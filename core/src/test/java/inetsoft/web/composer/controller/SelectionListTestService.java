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

package inetsoft.web.composer.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.InputScriptEvent;
import inetsoft.cluster.*;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.script.viewsheet.ViewsheetScope;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.viewsheet.command.ViewsheetCommand;
import inetsoft.web.viewsheet.service.*;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;

@Service
@ClusterProxy
public class SelectionListTestService {
   public SelectionListTestService(
      CoreLifecycleService coreLifecycleService,
      ViewsheetService viewsheetService,
      SharedFilterService sharedFilterService)
   {
      this.coreLifecycleService = coreLifecycleService;
      this.viewsheetService = viewsheetService;
      this.sharedFilterService = sharedFilterService;
      this.dispatcher = new SelectionListTestService.MockCommandDispatcher(null, null, null);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public String applySelection(@ClusterProxyKey String vsId,
                                String assemblyName, String type, Integer state, Integer selectStart,
                                Integer selectEnd, String value, Principal principal, String linkUri
   ) throws Exception
   {
      try{
         RuntimeViewsheet rvs =
            viewsheetService.getViewsheet(vsId, principal);
         Viewsheet viewsheet = rvs.getViewsheet();

         SelectionVSAssembly assembly =
            (SelectionVSAssembly) viewsheet.getAssembly(assemblyName);
         applySelection0(assembly, type, new int[]{state}, new String[]{value}, selectStart, selectEnd,
                         principal, rvs, linkUri, viewsheet);

         coreLifecycleService.layoutViewsheet(rvs, rvs.getID(), linkUri, dispatcher);

         return "success";
      } catch(Exception e) {
         e.printStackTrace();
         return "fail";
      }
   }

   private void applySelection0(SelectionVSAssembly assembly, String type, int[] states,
                                String[] values, int selectStart, int selectEnd, Principal principal,
                                RuntimeViewsheet rvs, String linkUri, Viewsheet viewsheet) throws Exception
   {
      SelectionList selectionList = null;
      int hint = VSAssembly.NONE_CHANGED;

      if(assembly instanceof SelectionListVSAssembly ||
         assembly instanceof SelectionTreeVSAssembly)
      {
         if(assembly instanceof SelectionListVSAssembly) {
            SelectionListVSAssembly sassembly = (SelectionListVSAssembly) assembly;
            selectionList = sassembly.getSelectionList();
         }
         else { // SelectionTreeVSAssembly
            selectionList = ((SelectionTreeVSAssembly) assembly).getSelectionList();
         }

         boolean singleSelection = ((SelectionVSAssemblyInfo) assembly.getInfo())
            .isSingleSelection();

         if(selectionList != null) {
            selectionList = (SelectionList) selectionList.clone();

            // Unselect: zero-length list + "Apply"
            if(values == null || (values.length == 0 &&
               type == "APPLY"))
            {
               selectionList.setSelectionValues(new SelectionValue[0]);
            }
            else {
               for(int i = 0; i < values.length; i++) {
                  String value = values[i];

                  if(value != null) {
                     if(assembly instanceof SelectionTreeVSAssembly &&
                        ((SelectionTreeVSAssembly) assembly).isIDMode())
                     {
                        updateIDSelectionTree(new String[]{value}, selectionList, states[i],
                                              singleSelection);
                     }
                     else {
                        updateSelection(new String[]{value}, 0, selectionList, states[i],
                                        singleSelection);
                     }
                  }
               }

               // Remove selectionValues that have an unselected state
               SelectionValue[] selectionValues = selectionList.getSelectionValues();

               selectionValues = assembly instanceof SelectionTreeVSAssembly &&
                  ((SelectionTreeVSAssembly) assembly).getMode() == SelectionTreeVSAssemblyInfo.ID ?
                  shrinkSelectionTreeValues(selectionValues) :
                  shrinkSelectionValues(selectionValues);

               selectionList.setSelectionValues(selectionValues);
            }
         }
      }
      else if(assembly instanceof TimeSliderVSAssembly) {
         selectionList = ((TimeSliderVSAssembly) assembly).getSelectionList();

         if(selectionList != null) {
            for(int i = 0; i < selectionList.getSelectionValueCount(); i++) {
               SelectionValue sval = selectionList.getSelectionValue(i);

               if(selectStart != -1 && selectEnd != -1) {
                  sval.setSelected(i >= selectStart && i <= selectEnd);
               }
               else {
                  sval.setSelected(true);
               }
            }
         }
      }

      afterSelectionListUpdate(assembly, selectionList, type, principal, rvs,
                               linkUri, viewsheet, hint);
   }

   private void afterSelectionListUpdate(SelectionVSAssembly assembly, SelectionList selectionList,
                                         String type, Principal principal,
                                         RuntimeViewsheet rvs,
                                         String linkUri, Viewsheet viewsheet, int hint)
      throws Exception
   {
      String embedded = VSUtil.getEmbeddedTableWithSameSource(viewsheet, assembly);

      if(embedded != null) {
         String msg = Catalog.getCatalog().getString(
            "viewer.viewsheet.selection.notSupportEmbeddedTable", embedded);
         System.out.println(msg);
      }

      String table = assembly.getTableName();

      if(assembly instanceof SelectionListVSAssembly) {
         if(type == "REVERSE") {
            selectionList = reverseSelectionList((SelectionListVSAssembly)assembly,
                                                 selectionList);
         }
      }

      // 1. Apply the selection on the assembly
      hint |= applySelection(assembly, selectionList, true);

      List<SelectionVSAssembly> neighbors = getNeighborAssemblies(rvs, assembly);

      // 2. Apply the selection on all similarly bound assemblies
      for(SelectionVSAssembly neighbor : neighbors) {
         hint |= applySelection(neighbor, selectionList, false);
      }

      executeSelection(rvs, hint, assembly, linkUri, principal);

      // Hide adhoc filters if not filtering
      SelectionVSAssemblyInfo info = (SelectionVSAssemblyInfo) assembly.getInfo();

      if(!assembly.containsSelection() && info.isCreatedByAdhoc()) {
         // @by: ChrisSpagnoli bug1412261632374 #5 2014-10-16
         // As both VSLayoutEvent and ApplySelectionListEvent try to
         // delete the same assembly, depending on the timing of the
         // messages, it is possible for the assembly to already be
         // deleted.  So, check if the assembly referenced is still in
         // the Viewsheet before deleting it.
         if(rvs.getViewsheet().getAssembly(assembly.getAbsoluteName()) != null) {
            coreLifecycleService.removeVSAssembly(rvs, linkUri, assembly, dispatcher, false, false);
         }
      }

      // Iterate over all assemblies and for add to view list if they have
      // hyperlinks that "send selection parameters"
      coreLifecycleService.executeInfluencedHyperlinkAssemblies(
         viewsheet, dispatcher, rvs, linkUri, Collections.singletonList(table));
   }

   private void updateSelection(String[] path, int index, SelectionList selectionList, int state,
                                boolean isSingleListSelection)
   {
      SelectionValue value = null;

      for(SelectionValue v : selectionList.getSelectionValues()) {
         if(Objects.equals(path[index], v.getValue())) {
            value = v;

            if(!isSingleListSelection) {
               break;
            }
         }
         else if(isSingleListSelection) {
            v.setState(SelectionValue.STATE_COMPATIBLE);
         }
      }

      if((value instanceof CompositeSelectionValue) && index < path.length - 1) {
         updateSelection(path, index + 1, ((CompositeSelectionValue) value).getSelectionList(),
                         state, isSingleListSelection);

         if((state & SelectionValue.STATE_SELECTED) == SelectionValue.STATE_SELECTED) {
            // ensure that parent is selected (deselecting does not deselect parent)
            value.setState(state);
         }
      }
      else if(value != null && index == path.length - 1) {
         value.setState(state);
      }
   }

   /**
    * In parent/child ID Selection, we should not select parent nodes
    * when a node is selected and we have to iterate whole tree to
    * select and unselect all duplicate nodes under different parents/path.
    *
    * @param path The path referring to the selected node
    * @param selectionList The selectionList containing all SelectionValues in the current level
    * @param state The state to be applied to the selected node
    */
   private void updateIDSelectionTree(String[] path, SelectionList selectionList, int state,
                                      boolean isSingleListSelection) {
      if(selectionList == null){
         return;
      }

      for(SelectionValue value : selectionList.getSelectionValues()) {
         if(value != null && Tool.contains(path, value.getValue(), true, true)) {
            value.setState(state);
         }
         else if(isSingleListSelection) {
            value.setState(SelectionValue.STATE_COMPATIBLE);
         }

         if((value instanceof CompositeSelectionValue)) {
            updateIDSelectionTree(path, ((CompositeSelectionValue) value).getSelectionList(),
                                  state, isSingleListSelection);
         }
      }
   }

   private SelectionValue[] shrinkSelectionTreeValues(SelectionValue[] values) {
      List<SelectionValue> newList = new ArrayList<>();

      for(SelectionValue value: values) {
         if(value instanceof CompositeSelectionValue) {
            SelectionList slist = ((CompositeSelectionValue) value).getSelectionList();
            SelectionValue[] subValues = slist.getSelectionValues();
            subValues = shrinkSelectionTreeValues(subValues);
            slist.setSelectionValues(null);
            newList.addAll(Arrays.asList(subValues));
         }

         if(value.isSelected()) {
            newList.add(value);
         }
      }

      return newList.toArray(new SelectionValue[newList.size()]);
   }

   private SelectionValue[] shrinkSelectionValues(SelectionValue[] values) {
      return shrinkSelectionValues(values, true);
   }

   private SelectionValue[] shrinkSelectionValues(SelectionValue[] values, boolean shrinkUnselected) {
      List<SelectionValue> newList = new ArrayList<>();

      for(SelectionValue value: values) {
         if(value.isSelected() || !shrinkUnselected) {
            if(value instanceof CompositeSelectionValue) {
               SelectionList slist = ((CompositeSelectionValue) value).getSelectionList();
               SelectionValue[] subValues = slist.getSelectionValues();
               subValues = shrinkSelectionValues(subValues, shrinkUnselected);
               slist.setSelectionValues(subValues);
            }

            newList.add(value);
         }
      }

      return newList.toArray(new SelectionValue[newList.size()]);
   }

   /**
    * Apply a selection list to an assembly.
    *
    * @param assembly    the assembly to apply the selection to
    * @param slist       the selection list to apply
    * @param isInitiator <tt>true</tt> if the assembly initiated the change
    *
    * @return the assembly state change
    */
   private int applySelection(SelectionVSAssembly assembly, SelectionList slist,
                              boolean isInitiator) throws Exception
   {
      int hint = VSAssembly.NONE_CHANGED;

      if(assembly == null) {
         return hint;
      }

      if(slist == null) {
         slist = new SelectionList();
      }

      // NOTE: State changes made on a filtered selection list or tree ONLY SEND
      // visible state selection. Selected values that are being filtered out
      // must be added back to the "slist".

      if(assembly instanceof SelectionListVSAssembly) {
         SelectionListVSAssembly sassembly = (SelectionListVSAssembly) assembly;
         SelectionListVSAssemblyInfo sinfo = (SelectionListVSAssemblyInfo) sassembly.getInfo();
         String search = sinfo.getSearchString();
         boolean single = sinfo.isSingleSelection();

         // @by davidd bug1368322840659, When applying selection list values
         // to a selection list, ignore the search filter if the list is from
         // a different list.
         if(isInitiator && !single && search != null && !search.isEmpty()) {
            SelectionList olist = sassembly.getStateSelectionList();
            // Find Selected Values that do NOT match filter. See note above
            olist = olist.findAll(search, true);
            slist.mergeSelectionList(olist);
         }

         hint = sassembly.setStateSelectionList(slist);
      }
      else if(assembly instanceof SelectionTreeVSAssembly) {
         SelectionTreeVSAssembly sassembly =
            (SelectionTreeVSAssembly) assembly;
         CompositeSelectionValue cvalue = new CompositeSelectionValue();
         String search = ((SelectionTreeVSAssemblyInfo) sassembly.getInfo())
            .getSearchString();

         cvalue.setLevel(-1);
         cvalue.setSelectionList(slist);

         if(isInitiator && search != null && !search.isEmpty()) {
            CompositeSelectionValue olist =
               sassembly.getStateCompositeSelectionValue();
            // Find Selected Values that do NOT match filter. See note above
            olist = olist.findAll(search, true);

            // @by stephenwebster, Fix bug1398779532156
            // The child elements in a hierarchy may be visible even if they
            // do not match the search string (because their parent does).
            // Therefore, we should not merge these values without first
            // checking if they are already visible.
            CompositeSelectionValue visibleList =
               sassembly.getCompositeSelectionValue().findAll(search, false);
            SelectionList sl = olist.getSelectionList();

            for(int i = 0; i < sl.getSelectionValueCount(); i++) {
               SelectionValue sv = sl.getSelectionValue(i);

               if(visibleList.contains(sv.getValue(), true)) {
                  sl.removeSelectionValue(i);
               }
            }

            cvalue.mergeSelectionValue(olist);
         }

         hint = sassembly.setStateCompositeSelectionValue(cvalue);
      }
      else if(assembly instanceof TimeSliderVSAssembly) {
         TimeSliderVSAssembly timeSliderAssembly = (TimeSliderVSAssembly) assembly;
         // calculate the start and end, in order to calculate the length
         int start = -1;
         int end = slist.getSelectionValueCount() - 1;
         for(int i = 0; i < slist.getSelectionValueCount(); i++) {
            if(start == -1 && slist.getSelectionValue(i).isSelected()) {
               start = i;
            }

            if(end == (slist.getSelectionValueCount() - 1) && start != -1 &&
               !slist.getSelectionValue(i).isSelected())
            {
               end = i - 1;
               break;
            }
         }

         if(end - start > 0) {
            timeSliderAssembly.setLengthValue(end - start);
         }

         // create a list of just the selected for setStateSelectionList()
         SelectionList slist2 = new SelectionList();

         for(int i = 0; i < slist.getSelectionValueCount(); i++) {
            if(slist.getSelectionValue(i).isSelected()) {
               slist2.addSelectionValue(slist.getSelectionValue(i));
            }
         }

         hint = timeSliderAssembly.setStateSelectionList(slist2);
      }

      return hint;
   }

   /**
    * Execute selection value.
    */
   private void executeSelection(RuntimeViewsheet rvs, int hint,
                                 SelectionVSAssembly assembly, String linkUri,
                                 Principal principal)
      throws Exception
   {
      ChangedAssemblyList clist = coreLifecycleService.createList(true, dispatcher, rvs, linkUri);
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(box == null) {
         return;
      }

      ViewsheetScope scope = box.getScope();

      scope.addVariable("event", new InputScriptEvent(assembly.getAbsoluteName(), assembly));

      try {
         box.processChange(assembly.getAbsoluteName(), hint, clist);
         sharedFilterService.processExtSharedFilters(assembly, hint, rvs, principal, dispatcher);
      }
      finally {
         scope.removeVariable("event");
      }

      coreLifecycleService.execute(rvs, assembly.getName(), linkUri, clist, dispatcher, true);
   }

   /**
    * Get all SelectionListVSAssembly assemblies that are bound to the same
    * table and field (DataRef).
    *
    * @param rvs  the runtime viewsheet
    * @param target  the assembly to get related assemblies for
    * @return  list of SelectionList assemblies that are bound the same.
    */
   private List<SelectionVSAssembly> getNeighborAssemblies(RuntimeViewsheet rvs,
                                                           SelectionVSAssembly target)
   {
      Viewsheet vs = rvs.getViewsheet();
      List<SelectionVSAssembly> neighbor = new ArrayList<>();

      if(!(target instanceof SelectionListVSAssembly)) {
         return neighbor;
      }

      Assembly[] assemblies = getAssemblies(vs, target.getAbsoluteName());
      String tname = target.getTableName();

      if(tname == null || tname.equals("")) {
         return neighbor;
      }

      DataRef[] trefs = target.getDataRefs();

      if(trefs == null || trefs.length != 1) {
         return neighbor;
      }

      for(Assembly assembly : assemblies) {
         if(assembly == target) {
            continue;
         }

         if(!(assembly instanceof SelectionListVSAssembly)) {
            continue;
         }

         String name = ((SelectionVSAssembly) assembly).getTableName();

         if(name == null || name.equals("") || !name.equals(tname)) {
            continue;
         }

         DataRef[] refs = ((SelectionVSAssembly) assembly).getDataRefs();

         if(refs == null || refs.length != 1 || !refs[0].equals(trefs[0])) {
            continue;
         }

         neighbor.add((SelectionListVSAssembly) assembly);
      }

      return neighbor;
   }

   /**
    * Get all the assemblies from the embedded viewsheet.
    */
   private Assembly[] getAssemblies(Viewsheet vs, String name) {
      int index = name.indexOf(".");

      if(index >= 0) {
         Viewsheet vs0 = (Viewsheet) vs.getAssembly(name.substring(0, index));

         if(vs0 != null) {
            return getAssemblies(vs0, name.substring(index + 1));
         }
      }

      return vs.getAssemblies();
   }

   /**
    * Reverses a selection list.
    */
   private SelectionList reverseSelectionList(SelectionListVSAssembly assembly,
                                              SelectionList selectionList)
      throws Exception
   {
      if(assembly == null || selectionList == null) {
         return selectionList;
      }

      SelectionList assemblyList = assembly.getSelectionList();
      SelectionListVSAssemblyInfo sinfo = (SelectionListVSAssemblyInfo)
         assembly.getVSAssemblyInfo();

      if(sinfo.getSearchString() != null && sinfo.getSearchString().length() > 0) {
         assemblyList = assemblyList.findAll(sinfo.getSearchString(), false);
      }

      if(assemblyList == null) {
         return selectionList;
      }

      SelectionValue[] allValues = assemblyList.getSelectionValues();
      SelectionValue[] selectionValues = selectionList.getSelectionValues();
      ArrayList<SelectionValue> reversed = new ArrayList<>();
      int lastMatched = 0;
      Boolean match = false;

      for(SelectionValue value : allValues) {
         for(int j = lastMatched; j < selectionValues.length; j++) {
            if(value.getLabel().equals(selectionValues[j].getLabel()) &&
               selectionValues[j].isSelected())
            {
               match = true;
               lastMatched = j;
            }
         }

         if(!match) {
            for(int k = 0; k < lastMatched; k++) {
               if(value.getLabel().equals(selectionValues[k].getLabel()) &&
                  selectionValues[k].isSelected())
               {
                  match = true;
                  lastMatched = k;
               }
            }
         }

         if(!match) {
            value.setSelected(true);
            reversed.add(value);
         }

         match = false;
      }

      selectionList.setSelectionValues(reversed.toArray(new SelectionValue[0]));
      return selectionList;
   }

   /**
    * mock a commandDispather for coreLifecycleService
    */
   class MockCommandDispatcher extends CommandDispatcher {
      public MockCommandDispatcher(StompHeaderAccessor headerAccessor,
                                   CommandDispatcherService dispatcherService,
                                   FindByIndexNameSessionRepository sessionRepository)
      {
         super(headerAccessor, dispatcherService, sessionRepository);
      }

      @Override
      public void sendCommand(ViewsheetCommand command) {
         // ignore send command
      }

      @Override
      public void sendCommand(String assemblyName, ViewsheetCommand command) {
         // ignore send command
      }
   }

   private final CoreLifecycleService coreLifecycleService;
   private final ViewsheetService viewsheetService;
   private final SharedFilterService sharedFilterService;
   private final CommandDispatcher dispatcher;
}
