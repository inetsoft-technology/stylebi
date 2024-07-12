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
package inetsoft.web.viewsheet.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.SetInputObjectValueEvent;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.script.viewsheet.ViewsheetScope;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.event.ApplySelectionListEvent;
import inetsoft.web.viewsheet.event.SortSelectionListEvent;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.awt.*;
import java.security.Principal;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service that handles selection events for selection assemblies in a viewsheet.
 */
@Service
public class VSSelectionService {
   @Autowired
   public VSSelectionService(PlaceholderService placeholderService,
                             ViewsheetService viewsheetService,
                             MaxModeAssemblyService maxModeAssemblyService)
   {
      this.placeholderService = placeholderService;
      this.viewsheetService = viewsheetService;
      this.maxModeAssemblyService = maxModeAssemblyService;
   }

   public Context createContext(String runtimeId, Principal principal,
                                 CommandDispatcher dispatcher,
                                 String linkUri)
      throws Exception
   {
      final RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);

      return Context.builder()
         .rvs(rvs)
         .principal(principal)
         .dispatcher(dispatcher)
         .linkUri(linkUri)
         .build();
   }

   /**
    * Apply a selection event to a selection assembly.
    *
    * @param assemblyName the name of the selection assembly.
    * @param event        the event to apply.
    * @param context      the execution context.
    */
   public void applySelection(String assemblyName, ApplySelectionListEvent event,
                              Context context)
      throws Exception
   {
      ViewsheetSandbox box = context.rvs().getViewsheetSandbox();
      box.lockRead();

      try {
         SelectionVSAssembly assembly = (SelectionVSAssembly) context.getAssembly(assemblyName);

         if(assembly != null) {
            applySelection(assembly, event, context);
         }
      }
      finally {
         box.unlockRead();
      }
   }

   /**
    * Apply a selection event to a selection assembly.
    *
    * @param assembly the selection assembly.
    * @param event    the event to apply.
    * @param context  the execution context.
    */
   public void applySelection(SelectionVSAssembly assembly,
                              ApplySelectionListEvent event,
                              Context context)
      throws Exception
   {
      final ViewsheetSandbox box = context.rvs().getViewsheetSandbox();
      box.lockWrite();

      try {
         doApplySelection(assembly, event, context);
      }
      finally {
         box.unlockWrite();
      }
   }

   private void doApplySelection(SelectionVSAssembly assembly,
                                 ApplySelectionListEvent event,
                                 Context context)
      throws Exception
   {
      SelectionList selectionList = null;
      int hint = VSAssembly.NONE_CHANGED;
      Map<List<String>, Set<List<String>>> oldConditions = Collections.emptyMap();

      if(assembly instanceof SelectionListVSAssembly ||
         assembly instanceof SelectionTreeVSAssembly)
      {
         final boolean toggle = event != null && event.isToggle();
         final boolean toggleAll = event != null && event.isToggleAll();
         boolean isIDMode = false;
         //boolean notContainsCurrentLevel = false;
         //Integer currentLevel = null;
         SelectionVSAssemblyInfo info = (SelectionVSAssemblyInfo) assembly.getInfo();

//         if(event != null && !CollectionUtils.isEmpty(event.getValues())) {
//            String[] indexs = event.getValues().get(0).getValue();
//            currentLevel = indexs.length - 1;
//            notContainsCurrentLevel = !info.isSingleSelectionLevel(currentLevel);
//         }

         if(assembly instanceof SelectionListVSAssembly) {
            SelectionListVSAssembly sassembly = (SelectionListVSAssembly) assembly;
            selectionList = sassembly.getSelectionList();
         }
         else { // SelectionTreeVSAssembly
            selectionList = ((SelectionTreeVSAssembly) assembly).getSelectionList();
            isIDMode = ((SelectionTreeVSAssembly) assembly).isIDMode();
         }

         if(toggleAll || toggle && assembly instanceof SelectionListVSAssembly) {
            info.setSingleSelectionValue(!info.isSingleSelection());
            info.setSingleSelectionLevels(null);
         }
         else if(event != null && event.getToggleLevels() != null) {
//            if(info.isSingleSelectionLevel(currentLevel)) {
//               info.removeSingleSelectionLevel(currentLevel);
//            }
//            else {
//               info.addSingleSelectionLevel(currentLevel);
//            }

            for(int level : event.getToggleLevels()) {
               if(!info.isSingleSelectionLevel(level)) {
                  info.addSingleSelectionLevel(level);
               }
               else {
                  info.removeSingleSelectionLevel(level);
               }
            }

            List<Integer> singleSelectionLevels = info.getSingleSelectionLevels();
            info.setSingleSelectionValue(singleSelectionLevels != null &&
               !singleSelectionLevels.isEmpty());
         }

         oldConditions = findConditionPaths((AssociatedSelectionVSAssembly) assembly);

         final boolean singleSelection = info.isSingleSelection();

         if(singleSelection) {
            if(assembly instanceof SelectionTreeVSAssembly) {
               List<Integer> singleLevels = info.getSingleSelectionLevels();

               if(!isIDMode && !CollectionUtils.isEmpty(singleLevels)) {
                  if(event != null) {
                     for(ApplySelectionListEvent.Value value : event.getValues()) {
                        String[] selectionValues = value.getValue();

                        if(value.isSelected() || toggle) {
                           selectionValues = fixSingleSelectValue(
                              selectionList, singleLevels, value.getValue());
                           value.setValue(selectionValues);
                        }

                        clearSingleSelectionValues(selectionList, singleLevels,
                           selectionValues, value.isSelected() || toggle);
                     }
                  }
               }
               else {
                  unselectChildren(selectionList);
               }
            }
            else {
               unselectChildren(selectionList);
            }
         }

         boolean requireReset = toggle || toggleAll;

         if(selectionList != null) {
            selectionList = (SelectionList) selectionList.clone();

            // Unselect: zero-length list + "Apply"
            if(event == null || (event.getValues().isEmpty() &&
               event.getType() == ApplySelectionListEvent.Type.APPLY))
            {
               selectionList.setSelectionValues(new SelectionValue[0]);
            }
            else {
               for(ApplySelectionListEvent.Value value : event.getValues()) {
                     if(assembly instanceof SelectionTreeVSAssembly &&
                        ((SelectionTreeVSAssembly) assembly).isIDMode())
                     {
                        updateIDSelectionTree(value.getValue(), selectionList,
                           value.isSelected() || toggle, singleSelection, requireReset);
                     }
                     else {
                        updateSelectionOfChangedAssembly(value.getValue(), 0, selectionList,
                           value.isSelected() || toggle, requireReset);
                     }
               }

               // Remove selectionValues that have an unselected state
               SelectionValue[] selectionValues =
                  SelectionVSUtil.shrinkSelectionValues(assembly, selectionList.getSelectionValues());
               selectionList.setSelectionValues(selectionValues);
            }
         }
      }
      else if(assembly instanceof TimeSliderVSAssembly) {
         selectionList = ((TimeSliderVSAssembly) assembly).getSelectionList();

         if(selectionList != null) {
            final int start = event == null || event.getSelectStart() == -1 ?
               0 : event.getSelectStart();
            int end = event == null || event.getSelectEnd() == -1 ?
               selectionList.getSelectionValueCount() - 1 : event.getSelectEnd();

            if(!((TimeSliderVSAssembly) assembly).isUpperInclusive()) {
               end--;
            }

            for(int i = 0; i < selectionList.getSelectionValueCount(); i++) {
               SelectionValue sval = selectionList.getSelectionValue(i);

               sval.setSelected(i >= start && i <= end);
            }
         }
      }
      else if(assembly instanceof CalendarVSAssembly) {
         //can only un-select calendar current-selections
         if(event == null) {
            CalendarVSAssemblyInfo calendarInfo =
               (CalendarVSAssemblyInfo) (Tool.clone(assembly.getVSAssemblyInfo()));
            calendarInfo.setDates(new String[0]);
            hint = assembly.setVSAssemblyInfo(calendarInfo);
         }
      }

      afterSelectionListUpdate(assembly, selectionList, event, oldConditions, hint, context);
   }

   /**
    * Select a subtree of a selection tree assembly.
    *
    * @param assemblyName the name of the selection tree assembly.
    * @param event        the event to apply
    * @param context      the execution context
    */
   public void selectSubtree(String assemblyName,
                             ApplySelectionListEvent event,
                             Context context)
      throws Exception
   {
      final SelectionTreeVSAssembly assembly =
         (SelectionTreeVSAssembly) context.getAssembly(assemblyName);
      final SelectionList selectionList = (SelectionList) assembly.getSelectionList().clone();
      final ApplySelectionListEvent.Value eventValue = event.getValues().get(0);
      SelectionTreeVSAssemblyInfo treeInfo = assembly.getSelectionTreeInfo();
      final boolean isIDTree = assembly.isIDMode();
      final boolean requireReset = event.isToggle() || event.isToggleAll();
      final SelectionValue subtreeRoot = !isIDTree ?
         findSubtreeRoot(eventValue.getValue(), eventValue.isSelected(), 0, selectionList, requireReset) :
         findIDSubtreeRoot(eventValue.getValue(), eventValue.isSelected(), 0, selectionList);

      if(subtreeRoot == null) {
         return;
      }

      final Map<List<String>, Set<List<String>>> oldConditions = findConditionPaths(assembly);
      List<Integer> singleLevels = treeInfo.getSingleSelectionLevels();

      if(!isIDTree && !CollectionUtils.isEmpty(singleLevels)) {
         for(ApplySelectionListEvent.Value value : event.getValues()) {
            String[] selectionValues = value.getValue();

            if(value.isSelected()) {
               selectionValues = fixSingleSelectValue(
                  selectionList, singleLevels, value.getValue());
               value.setValue(selectionValues);
            }

            clearSingleSelectionValues(selectionList, singleLevels,
               selectionValues, value.isSelected());
         }
      }

      // Apply the state to the subtree
      final String[] selectedArray = setSubtree(treeInfo, subtreeRoot, eventValue.isSelected(),
         isIDTree, true, new ArrayList<>(), requireReset);
      event.setValues(null);

      if(selectedArray.length > 0 && isIDTree) {
         updateIDSelectionTree(selectedArray, selectionList, eventValue.isSelected(), false,
            requireReset);
      }

      SelectionValue[] selectionValues = selectionList.getSelectionValues();
      selectionValues = shrinkSelectionValues(selectionValues, !isIDTree);
      selectionList.setSelectionValues(selectionValues);
      afterSelectionListUpdate(assembly, selectionList, event, oldConditions,
                               VSAssembly.NONE_CHANGED, context);
   }

   /**
    * Unselect a selection assembly.
    *
    * @param assemblyName the name of the selection assembly.
    * @param context      the execution context.
    */
   public void unselectAll(String assemblyName, Context context) throws Exception {
      final CurrentSelectionVSAssembly assembly =
         (CurrentSelectionVSAssembly) context.getAssembly(assemblyName);
      final Viewsheet vs2 = assembly.getViewsheet();

      for(String subAssemblyName : assembly.getAssemblies()) {
         SelectionVSAssembly subAssembly = (SelectionVSAssembly) vs2.getAssembly(subAssemblyName);
         applySelection(subAssembly, null, context);
      }

      CurrentSelectionVSAssemblyInfo assemblyInfo =
         (CurrentSelectionVSAssemblyInfo) assembly.getVSAssemblyInfo();

      if(assemblyInfo.isShowCurrentSelection()) {
         for(String outName : assemblyInfo.getOutSelectionNames()) {
            SelectionVSAssembly outAssembly = (SelectionVSAssembly) vs2.getAssembly(outName);
            applySelection(outAssembly, null, context);
         }
      }

      // if a selection container is hidden by clear, the parent tab and its children
      // visibility may change. (62311)
      if(!assembly.isVisible() && assembly.getContainer() instanceof TabVSAssembly) {
         Viewsheet viewsheet = context.rvs().getViewsheet();
         placeholderService.refreshVSAssembly(context.rvs(), assembly.getContainer(),
                                              context.dispatcher());

         for(String child : ((TabVSAssembly) assembly.getContainer()).getAbsoluteAssemblies()) {
            VSAssembly childAssembly = viewsheet.getAssembly(child);

            if(assembly.isEmbedded() && childAssembly != null) {
               placeholderService.addDeleteVSObject(context.rvs(), childAssembly, context.dispatcher());
            }
            else {
               placeholderService.refreshVSAssembly(context.rvs(), child, context.dispatcher());
            }
         }
      }
   }

   public void toggleSelectionStyle(String assemblyName, Context context)
      throws Exception
   {
      final RuntimeViewsheet rvs = context.rvs();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      box.lockRead();

      try {
         SelectionVSAssembly assembly = (SelectionVSAssembly) context.getAssembly(assemblyName);
         SelectionVSAssemblyInfo selectionVSAssemblyInfo = (SelectionVSAssemblyInfo) assembly.getInfo();

         boolean single = !selectionVSAssemblyInfo.isSingleSelection();
         selectionVSAssemblyInfo.setSingleSelectionValue(single);

         final CommandDispatcher dispatcher = context.dispatcher();
         final String linkUri = context.linkUri();
         final ChangedAssemblyList clist =
            placeholderService.createList(true, dispatcher, rvs, linkUri);
         box.processChange(assemblyName, VSAssembly.NONE_CHANGED, clist);
      }
      finally {
         box.unlockRead();
      }
   }

   /**
    * Sort a selection assembly.
    *
    * @param assemblyName the name of the selection assembly.
    * @param event        the sort event.
    * @param context      the execution context.
    */
   public void sortSelection(String assemblyName,
                             SortSelectionListEvent event,
                             Context context)
      throws Exception
   {
      final RuntimeViewsheet rvs = context.rvs();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      box.lockRead();

      try {
         SelectionVSAssembly assembly = (SelectionVSAssembly) context.getAssembly(assemblyName);

         if(assembly instanceof SelectionListVSAssembly) {
            sortList((SelectionListVSAssembly) assembly, event);
         }
         else if(assembly instanceof SelectionTreeVSAssembly) {
            sortTree((SelectionTreeVSAssembly) assembly, event);
         }

         final CommandDispatcher dispatcher = context.dispatcher();
         final String linkUri = context.linkUri();
         final ChangedAssemblyList clist =
            placeholderService.createList(true, dispatcher, rvs, linkUri);
         box.processChange(assemblyName, VSAssembly.NONE_CHANGED, clist);
      }
      finally {
         box.unlockRead();
      }
   }

   private void sortList(SelectionListVSAssembly listAssembly, SortSelectionListEvent event) {
      SelectionListVSAssemblyInfo sinfo = (SelectionListVSAssemblyInfo) listAssembly.getInfo();

      if(event.getSearch() == null) {
         sinfo.setSortTypeValue(nextSortType(sinfo.getSortTypeValue()));
         listAssembly.setSortTypeValue(sinfo.getSortTypeValue());
      }
      else {
         sinfo.setSearchString(event.getSearch());
      }

      if(sinfo.getSelectionList() != null) {
         sinfo.getSelectionList().sort(VSUtil.getSortType(sinfo));
      }
   }

   private int nextSortType(int curSortType) {
      switch(curSortType) {
         case XConstants.SORT_ASC:
            return XConstants.SORT_DESC;
         case XConstants.SORT_DESC:
            return XConstants.SORT_SPECIFIC;
         default:
            return XConstants.SORT_ASC;
      }
   }

   private void sortTree(SelectionTreeVSAssembly treeAssembly, SortSelectionListEvent event) {
      final SelectionTreeVSAssemblyInfo sinfo =
         (SelectionTreeVSAssemblyInfo) treeAssembly.getInfo();

      if(event.getSearch() == null) {
         sinfo.setSortTypeValue(nextSortType(sinfo.getSortTypeValue()));
         treeAssembly.setSortTypeValue(sinfo.getSortTypeValue());
      }
      else {
         sinfo.setSearchString(event.getSearch());
      }

      if(sinfo.getCompositeSelectionValue() != null &&
         sinfo.getCompositeSelectionValue().getSelectionList() != null)
      {
         sinfo.getCompositeSelectionValue().getSelectionList().sort(VSUtil.getSortType(sinfo));
      }
   }

   private void afterSelectionListUpdate(SelectionVSAssembly assembly,
                                         SelectionList selectionList,
                                         ApplySelectionListEvent event,
                                         Map<List<String>, Set<List<String>>> oldConditions,
                                         int hint,
                                         Context context)
      throws Exception
   {
      final RuntimeViewsheet rvs = context.rvs();
      final CommandDispatcher dispatcher = context.dispatcher();
      final String linkUri = context.linkUri();
      String embedded = VSUtil.getEmbeddedTableWithSameSource(rvs.getViewsheet(), assembly);

      if(embedded != null) {
         String msg = Catalog.getCatalog().getString(
            "viewer.viewsheet.selection.notSupportEmbeddedTable", embedded);
         MessageCommand command = new MessageCommand();
         command.setMessage(msg);
         command.setType(MessageCommand.Type.WARNING);
         dispatcher.sendCommand(command);
      }

      String table = assembly.getTableName();

      if(assembly instanceof SelectionListVSAssembly && event != null &&
         event.getType() == ApplySelectionListEvent.Type.REVERSE)
      {
         reverseSelectionList((SelectionListVSAssembly) assembly, selectionList);
      }
      else if(assembly instanceof SelectionTreeVSAssembly && event != null &&
         event.getType() == ApplySelectionListEvent.Type.REVERSE)
      {
         reverseSelectionTree((SelectionTreeVSAssembly) assembly, selectionList);
      }

      // 1. Apply the selection on the assembly
      hint |= applySelection(assembly, selectionList, true);
      refreshNeighborSelections(assembly, oldConditions);

      executeSelection(assembly, hint, context, event != null ? event.getEventSource() : null);

      // Hide adhoc filters if not filtering
      SelectionVSAssemblyInfo info = (SelectionVSAssemblyInfo) assembly.getInfo();

      if(!assembly.containsSelection() && info.isCreatedByAdhoc()) {
         VSAssembly selectionAssembly = rvs.getViewsheet().getAssembly(assembly.getAbsoluteName());

         // @by: ChrisSpagnoli bug1412261632374 #5 2014-10-16
         // As both VSLayoutEvent and ApplySelectionListEvent try to
         // delete the same assembly, depending on the timing of the
         // messages, it is possible for the assembly to already be
         // deleted.  So, check if the assembly referenced is still in
         // the Viewsheet before deleting it.
         if(selectionAssembly != null) {
            if(selectionAssembly instanceof MaxModeSupportAssembly &&
               rvs.getViewsheet().isMaxMode() &&
               ((MaxModeSupportAssembly) selectionAssembly).getMaxModeInfo().getMaxSize() != null)
            {
               this.maxModeAssemblyService.toggleMaxMode(rvs, assembly.getAbsoluteName(),
                  null, dispatcher, linkUri);
            }

            placeholderService.removeVSAssembly(rvs, linkUri, assembly, dispatcher, false, false);
         }
      }

      // Iterate over all assemblies and for add to view list if they have
      // hyperlinks that "send selection parameters"
      placeholderService.executeInfluencedHyperlinkAssemblies(
         rvs.getViewsheet(), dispatcher, rvs, linkUri, Collections.singletonList(table));
   }

   /**
    * In parent/child ID Selection, we should not select parent nodes
    * when a node is selected and we have to iterate whole tree to
    * select and unselect all duplicate nodes under different parents/path.
    *
    * @param path          The path referring to the selected node
    * @param selectionList The selectionList containing all SelectionValues in the current level
    * @param selected      The selected state to be applied to the selected node
    * @param requireReset  true if toggle or toggle all was done, use to set proper state
    *     *                to decide if the non neighbor assemblies should be resetted.
    */
   private void updateIDSelectionTree(String[] path,
                                      SelectionList selectionList,
                                      boolean selected,
                                      boolean isSingleListSelection, boolean requireReset)
   {
      if(selectionList == null) {
         return;
      }

      for(SelectionValue value : selectionList.getSelectionValues()) {
         if(Tool.contains(path, value.getValue(), true, true, true)) {
            setSelectedAndUnexclude(value, selected, requireReset);
         }
         else if(isSingleListSelection) {
            value.setState(SelectionValue.STATE_COMPATIBLE);
         }

         if((value instanceof CompositeSelectionValue)) {
            updateIDSelectionTree(path, ((CompositeSelectionValue) value).getSelectionList(),
                                  selected, isSingleListSelection, requireReset);
         }
      }
   }

   private SelectionValue[] shrinkSelectionTreeValues(SelectionValue[] values) {
      return SelectionVSUtil.shrinkSelectionTreeValues(values);
   }

   private SelectionValue[] shrinkSelectionValues(SelectionValue[] values) {
      return SelectionVSUtil.shrinkSelectionValues(values, true);
   }

   private SelectionValue[] shrinkSelectionValues(SelectionValue[] values, boolean shrinkUnselected)
   {
      return SelectionVSUtil.shrinkSelectionValues(values, shrinkUnselected);
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
   private int applySelection(SelectionVSAssembly assembly,
                              SelectionList slist,
                              boolean isInitiator)
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

            if(olist != null) {
               // Find Selected Values that do NOT match filter. See note above
               olist = olist.findAll(search, true);
               slist.mergeSelectionList(olist);
            }
         }

         hint = sassembly.setStateSelectionList(slist);
      }
      else if(assembly instanceof SelectionTreeVSAssembly) {
         SelectionTreeVSAssembly sassembly = (SelectionTreeVSAssembly) assembly;
         SelectionTreeVSAssemblyInfo sassemblyInfo = (SelectionTreeVSAssemblyInfo) sassembly.getVSAssemblyInfo();
         CompositeSelectionValue cvalue = new CompositeSelectionValue();
         String search = ((SelectionTreeVSAssemblyInfo) sassembly.getInfo()).getSearchString();

         cvalue.setLevel(-1);
         cvalue.setSelectionList(slist);

         CompositeSelectionValue olist = sassembly.getStateCompositeSelectionValue();

         if(isInitiator && search != null && !search.isEmpty() && olist != null) {
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

               if(visibleList.contains(sv.getValue(), true)
                  || sassemblyInfo.containsLevel(sv.getLevel()))
               {
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

            if(end == slist.getSelectionValueCount() - 1 && start != -1 &&
               !slist.getSelectionValue(i).isSelected())
            {
               end = i - 1;
               break;
            }
         }

         final int length = end - start + (timeSliderAssembly.isUpperInclusive() ? 0 : 1);

         if(length > 0) {
            timeSliderAssembly.setLengthValue(length);
         }

         // create a list of just the selected for setStateSelectionList()
         SelectionList slist2 = new SelectionList();

         for(int i = 0; i < slist.getSelectionValueCount(); i++) {
            if(slist.getSelectionValue(i).isSelected()) {
               slist2.addSelectionValue(slist.getSelectionValue(i));
            }
         }

         hint = timeSliderAssembly.setStateSelectionList(slist2);
         timeSliderAssembly.updateSharedBounds();
      }

      return hint;
   }

   /**
    * Execute selection value.
    */
   private void executeSelection(SelectionVSAssembly assembly, int hint, Context context,
                                 String eventSource)
      throws Exception
   {
      final RuntimeViewsheet rvs = context.rvs();
      final Principal principal = context.principal();
      final CommandDispatcher dispatcher = context.dispatcher();
      final String linkUri = context.linkUri();
      ChangedAssemblyList clist = placeholderService.createList(true, dispatcher, rvs, linkUri);
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(box == null) {
         return;
      }

      ViewsheetScope scope = box.getScope();

      scope.addVariable("event", new SetInputObjectValueEvent.
         InputScriptEvent(assembly.getAbsoluteName(), assembly));

      try {
         box.processChange(assembly.getAbsoluteName(), hint, clist);
         placeholderService.processExtSharedFilters(assembly, hint, rvs, principal, dispatcher);
      }
      finally {
         scope.removeVariable("event");
      }

      Assembly eventSourceAssembly = null;

      if(!StringUtils.isEmpty(eventSource)) {
         eventSourceAssembly = rvs.getViewsheet().getAssembly(eventSource);
      }

      placeholderService.execute(rvs, assembly.getName(), linkUri, clist, dispatcher,
         true);

      // Bug #59654, reapply scale to vs assemblies after changing a selection value
      if(rvs.getViewsheet().getViewsheetInfo().isScaleToScreen() &&
         (rvs.isPreview() || rvs.isViewer()))
      {
         Object scaleSize = rvs.getProperty("viewsheet.appliedScale");

         if(scaleSize instanceof Dimension && ((Dimension) scaleSize).width > 0 &&
            ((Dimension) scaleSize).height > 0)
         {
            this.placeholderService.refreshViewsheet(rvs, rvs.getID(), linkUri,
                                                     ((Dimension) scaleSize).width,
                                                     ((Dimension) scaleSize).height,
                                                     false, null, dispatcher,
                                                     false, false, false, clist);
         }
      }

      List<VSAssembly> tassemblies = VSUtil.getSharedVSAssemblies(rvs.getViewsheet(), assembly);

      for(VSAssembly tassembly : tassemblies) {
         placeholderService.refreshVSObject(tassembly, rvs, null, box, dispatcher);
      }
   }

   /**
    * Get all SelectionListVSAssembly assemblies that are bound to the same
    * table and field (DataRef).
    *
    * @param target the assembly to get related assemblies for
    *
    * @return list of SelectionList assemblies that are bound the same.
    */
   private Set<AssociatedSelectionVSAssembly> getNeighborAssemblies(SelectionVSAssembly target) {
      if(!(target instanceof AssociatedSelectionVSAssembly)) {
         return new HashSet<>();
      }

      final List<String> tableNames = target.getTableNames();

      if(tableNames.isEmpty() || tableNames.contains("")) {
         return new HashSet<>();
      }

      final DataRef[] trefs = target.getDataRefs();

      if(trefs == null || trefs.length == 0) {
         return new HashSet<>();
      }

      return SelectionVSUtil.getAssociatedSelectionCluster(target)
         .stream()
         .filter(s -> !Tool.equals(target, s))
         .collect(Collectors.toSet());
   }

   /**
    * Reverses a selection list.
    */
   private void reverseSelectionList(SelectionListVSAssembly assembly, SelectionList selectionList)
   {
      if(assembly == null || selectionList == null) {
         return;
      }

      SelectionList assemblyList = assembly.getSelectionList();
      SelectionListVSAssemblyInfo sinfo = (SelectionListVSAssemblyInfo)
         assembly.getVSAssemblyInfo();

      if(sinfo.getSearchString() != null && sinfo.getSearchString().length() > 0) {
         assemblyList = assemblyList.findAll(sinfo.getSearchString(), false);
      }

      if(assemblyList == null) {
         return;
      }

      SelectionValue[] allValues = assemblyList.getSelectionValues();
      SelectionValue[] selectionValues = selectionList.getSelectionValues();
      ArrayList<SelectionValue> reversed = new ArrayList<>();
      int lastMatched = 0;
      boolean match = false;

      for(SelectionValue value : allValues) {
         for(int j = lastMatched; j < selectionValues.length; j++) {
            if(Tool.equals(value.getLabel(), selectionValues[j].getLabel()) &&
               selectionValues[j].isSelected())
            {
               match = true;
               lastMatched = j;
            }
         }

         if(!match) {
            for(int k = 0; k < lastMatched; k++) {
               if(Tool.equals(value.getLabel(), selectionValues[k].getLabel()) &&
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
   }

   /**
    * Reverses a selection tree.
    */
   private void reverseSelectionTree(SelectionTreeVSAssembly assembly, SelectionList selectionList) {
      SelectionList list = assembly.getSelectionList();

      if(list != null) {
         SelectionValue[] allValues = list.getSelectionValues();
         SelectionValue[] selectionValues = selectionList.getSelectionValues();
         final SelectionValue[] reversedValues;

         if(assembly.isIDMode()) {
            reversedValues = reverseTreeChildrenID(allValues, selectionValues);
         }
         else {
            reversedValues = reverseTreeChildren(allValues, selectionValues);
         }

         selectionList.setSelectionValues(reversedValues);
      }
   }

   /**
    * Reverses a selection tree node's children.
    */
   private SelectionValue[] reverseTreeChildren(SelectionValue[] allValues, SelectionValue[] selectionValues) {
      ArrayList<SelectionValue> reversed = new ArrayList<>();
      int lastMatched = 0;
      boolean match = false;

      for(SelectionValue value : allValues) {
         for(int j = lastMatched; j < selectionValues.length; j++) {
            if(Tool.equals(value.getLabel(), selectionValues[j].getLabel()) &&
               selectionValues[j].isSelected())
            {
               match = true;
               lastMatched = j;
            }
         }

         if(!match) {
            for(int k = 0; k < lastMatched; k++) {
               if(Tool.equals(value.getLabel(), selectionValues[k].getLabel()) &&
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
         else if(value instanceof CompositeSelectionValue &&
                 selectionValues[lastMatched] instanceof CompositeSelectionValue)
         {
            SelectionValue selectedParent = selectionValues[lastMatched];
            SelectionList childList = ((CompositeSelectionValue) selectedParent).getSelectionList();
            SelectionValue[] selectedChildren = childList.getSelectionValues();

            if(selectedChildren.length > 0) {
               SelectionValue[] allChildren = ((CompositeSelectionValue)value)
                  .getSelectionList().getSelectionValues();

               SelectionValue[] newChildren = reverseTreeChildren(allChildren, selectedChildren);

               if(newChildren.length > 0) {
                  reversed.add(selectedParent);
                  childList.setSelectionValues(newChildren);
               }
            }
         }

         match = false;
      }

      return reversed.toArray(new SelectionValue[0]);
   }

   private SelectionValue[] reverseTreeChildrenID(SelectionValue[] allValues, SelectionValue[] selectionValues) {
      ArrayList<SelectionValue> reversed = new ArrayList<>();
      boolean match = false;

      for(SelectionValue value : allValues) {
         for(int i = 0; i < selectionValues.length; i++) {
            if(Tool.equals(value.getLabel(), selectionValues[i].getLabel()) && selectionValues[i].isSelected()) {
               match = true;
               break;
            }
         }

         if(value instanceof CompositeSelectionValue) {
            final SelectionValue[] selectedChildren =
               Arrays.stream(((CompositeSelectionValue) value).getSelectionList().getSelectionValues())
                     .filter(SelectionValue::isSelected)
                     .toArray(SelectionValue[]::new);

            SelectionValue[] allChildren = ((CompositeSelectionValue) value).getSelectionList().getSelectionValues();
            SelectionValue[] newChildren = reverseTreeChildrenID(allChildren, selectedChildren);

            reversed.addAll(Arrays.asList(newChildren));
            ((CompositeSelectionValue) value).getSelectionList().setSelectionValues(newChildren);
         }

         if(!match) {
            value.setSelected(true);
            reversed.add(value);
         }

         match = false;
      }

      return reversed.toArray(new SelectionValue[0]);
   }

   /*
    * Recursively set the state of the selected node and its children.
    */
   private String[] setSubtree(SelectionTreeVSAssemblyInfo treeInfo,
                               SelectionValue value,
                               boolean selected,
                               boolean isIDTree,
                               boolean isRoot,
                               List<String> selectedList,
                               boolean requireReset)
   {
      int level = value.getLevel();

      if(selected && treeInfo.isSingleSelection()
         && treeInfo.containsLevel(level) && !value.isSelected())
      {
         return selectedList.toArray(new String[0]);
      }

      if(!isIDTree || !isRoot) {
         setSelectedAndUnexclude(value, selected, requireReset);
         selectedList.add(value.getValue());
      }

      if(value instanceof CompositeSelectionValue) {
         SelectionList childrenList = ((CompositeSelectionValue) value).getSelectionList();
         SelectionValue[] childValues = childrenList.getSelectionValues();

         final boolean hasSelected = Arrays.stream(childValues)
            .anyMatch(SelectionValue::isSelected);

         if(!hasSelected && selected && childValues.length > 0) {
            // no selected, select first child
            setSelectedAndUnexclude(childValues[0], true, requireReset);
         }

         for(SelectionValue childValue : childValues) {
            setSubtree(treeInfo, childValue, selected, isIDTree, false, selectedList, requireReset);
         }
      }

      return selectedList.toArray(new String[0]);
   }

   private SelectionValue findSubtreeRoot(String[] path,
                                          boolean selected,
                                          int index,
                                          SelectionList selectionList,
                                          boolean requireReset)
   {
      // Find the corresponding SelectionValue
      for(SelectionValue svalue : selectionList.getSelectionValues()) {
         if(Tool.equals(svalue.getValue(), path[index])) {
            index++;
            setSelectedAndUnexclude(svalue, selected, requireReset);

            if(svalue instanceof CompositeSelectionValue && index < path.length) {
               return findSubtreeRoot(path, selected, index,
                  ((CompositeSelectionValue) svalue).getSelectionList(), requireReset);
            }

            return svalue;
         }

      }

      return null;
   }

   private SelectionValue findIDSubtreeRoot(String[] path,
                                            boolean selected,
                                            int index,
                                            SelectionList selectionList)
   {
      for(SelectionValue svalue : selectionList.getSelectionValues()) {
         if(Tool.equals(svalue.getValue(), path[index])) {
            index++;

            if(svalue instanceof CompositeSelectionValue && index < path.length) {
               return findIDSubtreeRoot(path, selected, index,
                                      ((CompositeSelectionValue) svalue).getSelectionList());
            }

            return svalue;
         }

      }

      return null;
   }

   /**
    * Find the condition paths of the {@code associatedAssembly}.
    * <p>
    * The keys in the resultant map are sublists of dataRef names.
    * The values in the resultant map are sets of lists of values.
    * The resultant map describes the conditions and the paths to those conditions that the
    * assembly is currently applying according to its dataRefs and selected values.
    *
    * @param assembly the assembly to find the condition paths of
    *
    * @return the map of condition paths of this assembly
    */
   private Map<List<String>, Set<List<String>>> findConditionPaths(
      AssociatedSelectionVSAssembly assembly)
   {
      final Map<List<String>, Set<List<String>>> conditionPaths;

      if(assembly instanceof SelectionListVSAssembly) {
         conditionPaths = findSelectionListConditionPaths((SelectionListVSAssembly) assembly);
      }
      else { // Selection tree
         conditionPaths = findSelectionTreeConditionPaths((SelectionTreeVSAssembly) assembly);
      }

      return conditionPaths;
   }

   /**
    * Find the condition paths of a selection list.
    *
    * @param assembly the selection list to find the condition paths of.
    *
    * @return the condition paths
    */
   private Map<List<String>, Set<List<String>>> findSelectionListConditionPaths(
      SelectionListVSAssembly assembly)
   {
      final Map<List<String>, Set<List<String>>> conditionPaths = new HashMap<>();
      final Set<List<String>> paths = new HashSet<>();
      final SelectionList selectionList = assembly.getStateSelectionList();

      if(selectionList != null) {
         for(int i = 0; i < selectionList.getSelectionValueCount(); i++) {
            final SelectionValue selectionValue = selectionList.getSelectionValue(i);

            if(selectionValue.isSelected()) {
               final List<String> path = new ArrayList<>();
               path.add(selectionValue.getValue());
               paths.add(path);
            }
         }
      }

      if(!paths.isEmpty()) {
         final List<String> dataRefs = Arrays.stream(assembly.getDataRefs())
            .map(DataRef::getName)
            .collect(Collectors.toList());

         conditionPaths.put(dataRefs, paths);
      }

      return conditionPaths;
   }

   /**
    * Find the condition paths of a selection tree.
    *
    * @param assembly the selection tree to find the condition paths of
    *
    * @return the condition paths
    */
   private Map<List<String>, Set<List<String>>> findSelectionTreeConditionPaths(
      SelectionTreeVSAssembly assembly)
   {
      final Map<List<String>, Set<List<String>>> conditionPaths = new HashMap<>();
      final CompositeSelectionValue root = assembly.getStateCompositeSelectionValue();

      if(assembly.isIDMode() && assembly.getID() == null) {
         return conditionPaths;
      }

      if(root != null) {
         final List<List<String>> selectedPaths = findSelectedPaths(root, assembly.isIDMode());
         final List<List<String>> dataRefSubsets = SelectionVSUtil.createDataRefSubLists(assembly);

         if(dataRefSubsets.size() == 0) {
            return conditionPaths;
         }

         for(List<String> selectedPath : selectedPaths) {
            final int level = selectedPath.size() - 1;

            if(level > dataRefSubsets.size() - 1 || !assembly.isIDMode()) {
               continue;
            }

            final List<String> dataRefSublist = assembly.isIDMode() ?
               dataRefSubsets.get(0) : dataRefSubsets.get(level);
            conditionPaths.computeIfAbsent(dataRefSublist, k -> new HashSet<>())
               .add(selectedPath);
         }
      }

      return conditionPaths;
   }

   /**
    * Find the selected paths of {@code cValue}.
    *
    * @param cValue the selection value to find the selected paths in
    *
    * @return a {@code List} of selected paths.
    */
   private List<List<String>> findSelectedPaths(CompositeSelectionValue cValue, boolean idMode) {
      final List<List<String>> selectedPaths = new ArrayList<>();
      final SelectionList selectionList = cValue.getSelectionList();

      for(int i = 0; i < selectionList.getSelectionValueCount(); i++) {
         final SelectionValue selectionValue = selectionList.getSelectionValue(i);

         if(selectionValue.isSelected()) {
            final int level = selectionValue.getLevel();
            final String value = selectionValue.getValue();

            if(selectionValue instanceof CompositeSelectionValue) {
               final List<List<String>> paths =
                  findSelectedPaths((CompositeSelectionValue) selectionValue, idMode);
               selectedPaths.addAll(paths);
            }
            else {
               int pathSize = idMode ? 1 : level + 1;
               final List<String> path = Arrays.stream(new String[pathSize])
                  .collect(Collectors.toList());
               path.set(idMode ? 0 : level, value);
               selectedPaths.add(path);
            }
         }
      }

      final int level = cValue.getLevel();

      if(level >= 0) {
         if(selectedPaths.isEmpty()) {
            int pathSize = idMode ? 1 : level + 1;
            final List<String> path = Arrays.stream(new String[pathSize])
               .collect(Collectors.toList());
            selectedPaths.add(path);
         }

         int targetLevel = idMode ? 0 : cValue.getLevel();
         selectedPaths.forEach(path -> path.set(targetLevel, cValue.getValue()));
      }

      return selectedPaths;
   }

   /**
    * Refresh the selections of the neighbors of {@code assembly}.
    * This is done by comparing the old condition paths of {@code assembly} with its new
    * condition paths, and then applying the difference to the assembly's neighbors.
    *
    * @param assembly      the assembly to refresh the neighbors of
    * @param oldConditions the old condition paths of {@code assembly}
    */
   private void refreshNeighborSelections(
      SelectionVSAssembly assembly,
      Map<List<String>, Set<List<String>>> oldConditions)
   {
      if(!(assembly instanceof AssociatedSelectionVSAssembly)) {
         return;
      }

      final Set<AssociatedSelectionVSAssembly> neighbors = getNeighborAssemblies(assembly);

      if(!neighbors.isEmpty()) {
         final Map<List<String>, Set<List<String>>> newConditions =
            findConditionPaths((AssociatedSelectionVSAssembly) assembly);

         final Map<List<String>, Set<List<String>>> removedConditions =
            findRemovedConditions(oldConditions, newConditions);

         for(AssociatedSelectionVSAssembly neighbor : neighbors) {
            boolean removed = applySelectionConditions(neighbor, removedConditions, false);
            boolean added = applySelectionConditions(neighbor, newConditions, true);

            if(removed || added) {
               final SelectionList selectionList = neighbor.getSelectionList();
               final SelectionValue[] selectionValues = selectionList.getSelectionValues();

               final SelectionValue[] shrunkenSelectionValues =
                  shrinkSelectionValues(assembly, selectionValues);

               final SelectionList stateSelectionList = new SelectionList();
               stateSelectionList.setSelectionValues(shrunkenSelectionValues);
               applySelection(neighbor, stateSelectionList, false);
            }
         }
      }
   }

   /**
    * Find the removed condition paths by comparing {@code oldConditions} and {@code newConditions}.
    *
    * @param oldConditions the old condition paths
    * @param newConditions the new condition paths
    *
    * @return the removed condition paths
    */
   private Map<List<String>, Set<List<String>>> findRemovedConditions(
      Map<List<String>, Set<List<String>>> oldConditions,
      Map<List<String>, Set<List<String>>> newConditions)
   {
      final Map<List<String>, Set<List<String>>> removedConditions = new HashMap<>();

      for(Map.Entry<List<String>, Set<List<String>>> oldCondition : oldConditions.entrySet()) {
         final List<String> dataRefSublist = oldCondition.getKey();
         final Set<List<String>> newPaths =
            newConditions.getOrDefault(dataRefSublist, Collections.emptySet());

         for(List<String> oldPath : oldCondition.getValue()) {
            if(!newPaths.contains(oldPath)) {
               removedConditions.computeIfAbsent(dataRefSublist, k -> new HashSet<>())
                  .add(oldPath);
            }
         }
      }

      return removedConditions;
   }

   private SelectionValue[] shrinkSelectionValues(SelectionVSAssembly assembly,
                                                  SelectionValue[] selectionValues)
   {
      return assembly instanceof SelectionTreeVSAssembly &&
         ((SelectionTreeVSAssembly) assembly).getMode() == SelectionTreeVSAssemblyInfo.ID ?
         shrinkSelectionTreeValues(selectionValues) :
         shrinkSelectionValues(selectionValues);
   }

   /**
    * Apply the condition paths in {@code conditions} to {@code assembly}.
    * If {@code select} is true, then select the matching paths in {@code conditions}.
    * Otherwise, deselect those paths.
    *
    * @param assembly   the assembly to apply the conditions to
    * @param conditions the conditions to
    * @param select     if true, then conditions should be selected, otherwise deselected
    *
    * @return true if the assembly selections were changed, otherwise false
    */
   private boolean applySelectionConditions(
      AssociatedSelectionVSAssembly assembly,
      Map<List<String>, Set<List<String>>> conditions,
      boolean select)
   {
      final List<List<String>> dataRefSublists = SelectionVSUtil.createDataRefSubLists(assembly);
      final Map<String, Integer> refNameIndices = new HashMap<>();
      final SelectionList selectionList = assembly.getSelectionList();
      boolean changed = false;

      for(Map.Entry<List<String>, Set<List<String>>> condition : conditions.entrySet()) {
         final List<String> conditionDataRefSublist = condition.getKey();
         List<String> dataRefSublist = null;

         for(List<String> sublist : dataRefSublists) {
            if(sublist.size() > conditionDataRefSublist.size()) {
               break;
            }

            if(Tool.equals(sublist, conditionDataRefSublist.subList(0, sublist.size()))) {
               dataRefSublist = sublist;
            }
         }

         if(dataRefSublist != null) {
            final Set<List<String>> paths = condition.getValue();

            for(List<String> path : paths) {
               final String[] pathArr = new String[dataRefSublist.size()];

               for(int i = 0; i < dataRefSublist.size(); i++) {
                  final String refName = conditionDataRefSublist.get(i);
                  final int index = refNameIndices.computeIfAbsent(
                     refName, dataRefSublist::indexOf);
                  pathArr[index] = path.get(i);
               }

               updateSelectionPath(pathArr, 0, selectionList, select);
               changed = true;
            }
         }
      }

      return changed;
   }

   /**
    * Update the selectionList values according to the selection path.
    *
    * @param path          the path of values
    * @param index         the index of the path to update
    * @param selectionList the selection list to update
    * @param selected      if true, the selection values will be selected, otherwise deselected
    */
   private void updateSelectionPath(String[] path,
                                    int index,
                                    SelectionList selectionList,
                                    boolean selected)
   {
      final SelectionValue value = selectionList.findValue(path[index]);

      if(value == null) {
         return;
      }

      if((value instanceof CompositeSelectionValue) && index < path.length - 1) {
         updateSelectionPath(path, index + 1,
                             ((CompositeSelectionValue) value).getSelectionList(), selected);
      }
      else if(value instanceof CompositeSelectionValue && !selected) {
         unselectChildren(((CompositeSelectionValue) value).getSelectionList());
      }

      value.setSelected(selected);
   }

   /**
    * Update the selection list of the assembly which was changed.
    *
    * @param path            the path of values
    * @param index           the index of the path to update
    * @param selectionList   the selection list to update
    * @param selected        if true, the selection values will be selected, otherwise deselected
    * @param requireReset    true if toggle or toggle all was done, use to set proper state
    *                        to decide if the non neighbor assemblies should be resetted.
    */
   private void updateSelectionOfChangedAssembly(String[] path,
                                                 int index,
                                                 SelectionList selectionList,
                                                 boolean selected,
                                                 boolean requireReset)
   {
      final SelectionValue value = selectionList.findValue(path[index], false);

      if(value == null) {
         return;
      }

      final boolean isParent = index < path.length - 1;
      final boolean isTarget = index == path.length - 1;

      if((value instanceof CompositeSelectionValue)) {
         final SelectionList valueList = ((CompositeSelectionValue) value).getSelectionList();

         if(isParent) {
            updateSelectionOfChangedAssembly(path, index + 1, valueList, selected, requireReset);
         }
         else if(!selected) {
            unselectChildren(valueList);
         }
      }

      if(isTarget || (isParent && selected)) {
         setSelectedAndUnexclude(value, selected, requireReset);
      }
   }

   /**
    * Unselect the children values of the selection list.
    *
    * @param selectionList the selection list to unselect the children values of
    */
   private void unselectChildren(SelectionList selectionList) {
      if(selectionList == null) {
         return;
      }

      for(int i = 0; i < selectionList.getSelectionValueCount(); i++) {
         final SelectionValue value = selectionList.getSelectionValue(i);

         if(value instanceof CompositeSelectionValue) {
            unselectChildren(((CompositeSelectionValue) value).getSelectionList());
         }

         value.setSelected(false);
      }
   }

   /**
    * Default to selecting the first child value for Selection Tree levels with single selection enabled
    */
   private String[] fixSingleSelectValue(SelectionList selectionList,
                                         List<Integer> singleLevels, String[] valueNames)
   {
      int minimumLevel = singleLevels.size() == 0 ? 0 : singleLevels.get(singleLevels.size() -1);

      if(valueNames.length > minimumLevel) {
         return valueNames;
      }
      else {
         String[] newValue = new String[minimumLevel + 1];

         for(int i = 0; i < valueNames.length; i ++) { //Navigate to the current selection in the tree
            newValue[i] = valueNames[i];

            for(int j = 0; j < selectionList.getSelectionValueCount(); j++) {
               final SelectionValue value = selectionList.getSelectionValue(j);

               if(Tool.equals(valueNames[i], value.getValue())) {
                  if(value instanceof CompositeSelectionValue) {
                     selectionList = ((CompositeSelectionValue) value).getSelectionList();
                     break;
                  }
                  else {
                     return newValue;
                  }
               }
            }
         }

         for(int i = valueNames.length; i <= minimumLevel; i++) {
            if(selectionList.getSelectionValueCount() > 0) {
               final SelectionValue value = selectionList.getSelectionValue(0);

               if(value instanceof CompositeSelectionValue) {
                  selectionList = ((CompositeSelectionValue) value).getSelectionList();
               }

               newValue[i] = value.getValue();
            }
            else {
               break;
            }
         }

         return newValue;
      }
   }

   /**
    * Unselect values in SelectionTree level that are indicated to be Single Selection
    *
    * @param selectionList the selection list to unselect the children values of
    * @param singleLevels the levels of the selection tree that have single selection applied
    * @param valueNames the value selected in the tree, lists all the levels in order
    */
   private void clearSingleSelectionValues(SelectionList selectionList, List<Integer> singleLevels,
                                           String[] valueNames, boolean selected)
   {
      for(int i = 0; i < selectionList.getSelectionValueCount(); i++) {
         final SelectionValue value = selectionList.getSelectionValue(i);
         boolean singleSelection = singleLevels.contains(value.getLevel());

         if(value.getLevel() >= valueNames.length) {
            if(!selected) {
               if(value instanceof CompositeSelectionValue) {
                  SelectionList childList = ((CompositeSelectionValue) value).getSelectionList();
                  unselectChildren(childList);
               }

               value.setSelected(false);
            }

            return;
         }

         if(Objects.equals(valueNames[value.getLevel()], value.getValue()) &&
            value instanceof CompositeSelectionValue)
         {
            SelectionList childList = ((CompositeSelectionValue) value).getSelectionList();
            clearSingleSelectionValues(childList, singleLevels, valueNames, selected);
         }
         else if(singleSelection) {
            if(value instanceof CompositeSelectionValue) { //If singleSelection value does not match, clear children
               SelectionList childList = ((CompositeSelectionValue) value).getSelectionList();
               unselectChildren(childList);
            }

            value.setSelected(false);
         }
         else if(value instanceof CompositeSelectionValue) {
            fixSingleSelectionValues(
               ((CompositeSelectionValue) value).getSelectionList(), singleLevels);
         }
      }
   }

   /**
    * Make sure nodes in single selection level only select one in each subtree.
    * @param selectionList  the selection list need to fix single selection values.
    * @param singleLevels   the current single levels.
    */
   private void fixSingleSelectionValues(SelectionList selectionList, List<Integer> singleLevels) {
      if(selectionList == null || selectionList.getSelectionValueCount() == 0 ||
         singleLevels == null || singleLevels.size() == 0)
      {
         return;
      }

      boolean hasSelected = false;

      for(int i = 0; i < selectionList.getSelectionValueCount(); i++) {
         SelectionValue value = selectionList.getSelectionValue(i);

         if(value instanceof CompositeSelectionValue) {
            fixSingleSelectionValues(
               ((CompositeSelectionValue) value).getSelectionList(), singleLevels);
         }

         boolean singleSelection = singleLevels.contains(value.getLevel());

         if(!singleSelection) {
            continue;
         }

         if(!hasSelected && value.isSelected()) {
            hasSelected = true;
            continue;
         }

         if(value.isSelected()) {
            value.setState(SelectionValue.STATE_COMPATIBLE);
         }
      }
   }

   /**
    *
    * @param value         the current selection value.
    * @param selected      true if the target selection value should be setted.
    * @param requireReset  true if toggle or toggle all was done, use to set proper state
    *                      to decide if the non neighbor assemblies should be resetted.
    */
   private void setSelectedAndUnexclude(SelectionValue value, boolean selected,
                                        boolean requireReset)
   {
      if(requireReset && selected) {
         value.setState(SelectionValue.STATE_SELECTED);
      }
      else {
         value.setSelected(selected);
      }

      if(selected) {
         value.setExcluded(false);
      }
   }

   private final PlaceholderService placeholderService;
   private final ViewsheetService viewsheetService;
   private final MaxModeAssemblyService maxModeAssemblyService;
}
