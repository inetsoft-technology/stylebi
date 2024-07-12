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
package inetsoft.report.composition.execution;

import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.SelectionTreeVSAssemblyInfo;
import inetsoft.util.DefaultComparator;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * This processor use to refresh state CompositeSelectionValue for selection tree after
 * refreshSelectionValue in SelectionTreeVSAQuery or set selected object by script.
 */
public class SelectionTreeStateProcessor {
   public SelectionTreeStateProcessor(SelectionTreeVSAssembly assembly) {
      this.assembly = assembly;
   }

   /**
    * 1. force an item to be selected for single selection, like radio button.
    * 2. Since tree supports singleSelection levels, such as single-non single, so cannot only
    *    force to only select one path, that will missing selected values in non single level
    *    like the case in Bug #50505. So fix by remembering the first seleced parent values,
    *    for the leaf nodes with same parent(means parent values which in the selected single path)
    *    should be selected too (if leaf is singleselection level, then selected,
    *    if non singleselection, just included)
    *
    * @param selectionValue       the current CompositeSelectionValue.
    * @param stateSelectionValue  the state CompositeSelectionValue.
    * @param associationDefined   true if selections outside of the tree exist.
    * @param scriptable           true if refresh for set selected objects by scriptable.
    */
   public void refreshSingleSelectionState(CompositeSelectionValue selectionValue,
                                           CompositeSelectionValue stateSelectionValue,
                                           boolean associationDefined,
                                           boolean scriptable)
   {
      DataRef[] refs = assembly.getDataRefs();
      CompositeSelectionValue clone = (CompositeSelectionValue) selectionValue.clone();
      Map<String, List<SelectionValue>> selectedParentMap = new HashMap<>();
      SelectionList list = selectionValue.getSelectionList();
      boolean anySelected = getAnySelectedChild(list, true, refs.length - 1) != null;

      SelectionValueIterator iter = new SelectionValueIterator(selectionValue) {
         @Override
         protected void visit(SelectionValue val, List<SelectionValue> pvals) {
            if(val.getLevel() == refs.length - 1) {
               List<String> singlePaths = getSinglePaths(pvals);
               String lastSinglePath = singlePaths.size() > 0 ?
                  singlePaths.get(singlePaths.size() - 1) : null;

               if(!isAnySinglePathSelected(selectedParentMap, singlePaths) &&
                  shouldSelect(clone, val, pvals, scriptable, anySelected))
               {
                  if(addSelected(val, pvals, stateSelectionValue, associationDefined)) {
                     addSinglePaths(selectedParentMap, singlePaths, pvals);
                  }
               }
               else if(isAnySinglePathSelected(selectedParentMap, singlePaths) &&
                  shouldRemoveSelect(val, pvals))
               {
                  removeSelectedState(val, pvals, stateSelectionValue);
               }
               else if(!isSingleSelectionLevel(val.getLevel()) &&
                  shouldSelect(clone, val, pvals, scriptable, anySelected) &&
                  isSameSingleSelectionPath(pvals, selectedParentMap.get(lastSinglePath)))
               {
                  addSelected(val, pvals, stateSelectionValue, associationDefined);
               }
            }
         }
      };

      try {
         iter.iterate();
      }
      catch(Exception ex) {
         LOG.error("Refresh selection status failed caused by " + ex.getMessage());
      }

      fixStateSelection(stateSelectionValue);
   }

   /**
    * Remove leaf nodes which are included status.
    * @param selection the state composite selection.
    */
   private void fixStateSelection(CompositeSelectionValue selection) {
      SelectionList list = selection.getSelectionList();

      if(getAnySelectedChild(list, true) == null) {
         list.clear();
      }

      for(int i = 0; i < list.getSelectionValueCount(); i++) {
         SelectionValue value = list.getSelectionValue(i);

         if(value instanceof CompositeSelectionValue) {
            fixStateSelection((CompositeSelectionValue) value);
         }
      }
   }

   /**
    * Refresh value states when set selected objects by scriptable,
    * force each single selection exist and only exist on selected item.
    *
    * @param selectionValue the current CompositeSelectionValue.
    * @param stateSelectionValue  the state CompositeSelectionValue.
    * @param selectedValues the selected values setted by script.
    */
   public void refreshSingleSelectionState0(CompositeSelectionValue selectionValue,
                                            CompositeSelectionValue stateSelectionValue,
                                            Object[] selectedValues)
   {
      // level to selected values mape
      HashMap<Integer, List<SelectionValue>> levelValues = new HashMap<>();
      HashMap<SelectionValue, List<SelectionValue>> parents = new HashMap<>();
      Map<String, List<SelectionValue>> selectedParentMap = new HashMap<>();
      boolean isIDMode = assembly.isIDMode();

      // 1. select the target values.
      SelectionValueIterator iter = new SelectionValueIterator(selectionValue) {
         @Override
         protected void visit(SelectionValue val, List<SelectionValue> pvals) {
            int state = val.getState();
            val.setState(state - state & SelectionValue.STATE_SELECTED);
            int level = isIDMode ? 0 : val.getLevel();
            boolean singleSelection = assembly.getSelectionTreeInfo().isSingleSelection() &&
               assembly.getSelectionTreeInfo().isSingleSelectionLevel(level);

            for(int j = 0; j < selectedValues.length; j++) {
               String value0 = (String) Tool.getData(String.class, selectedValues[j]);

               if(Objects.equals(value0, val.getValue()) &&
                  (!singleSelection || !isIDMode || levelValues.isEmpty()))
               {
                  val.setState(state | SelectionValue.STATE_SELECTED);

                  if(levelValues.get(level) == null) {
                     levelValues.put(level, new ArrayList<>());
                  }

                  levelValues.get(level).add(val);
                  parents.put(val, pvals);
               }
            }
         }
      };

      try {
         iter.iterate();
      }
      catch(Exception ex) {
         LOG.error("Refresh selection status failed caused by " + ex.getMessage());
      }

      if(assembly.isIDMode()) {
         fixStateSelectionForIDTree(selectionValue, stateSelectionValue, levelValues);
         return;
      }

      fixStatesForScriptable(selectionValue, stateSelectionValue, levelValues, parents, selectedParentMap);

      // 4. refresh parent values state for single selection.
      if(selectedValues.length > 0) {
         refreshSingleSelectionState(selectionValue, stateSelectionValue, false, true);
      }
   }

   private void fixStateSelectionForIDTree(CompositeSelectionValue selectionValue,
                                           CompositeSelectionValue stateSelectionValue,
                                           HashMap<Integer, List<SelectionValue>> map)
   {
      SelectionTreeVSAssemblyInfo info = assembly.getSelectionTreeInfo();
      boolean singleSelection = info.isSingleSelection();
      List<SelectionValue> list = map.get(0);

      if(list == null || list.size() == 0) {
         if(singleSelection) {
            markSelectedForSingleIDTree(selectionValue, stateSelectionValue);
         }

         return;
      }

      boolean selectChildren = !singleSelection && info.isSelectChildren();

      for(int j = 0; j < list.size(); j++) {
         selectIDValue(list.get(j), stateSelectionValue, selectChildren);
      }
   }

   /**
    * Add target value to state composite selection value of pc tree.
    * @param value               the target value.
    * @param stateSelectionValue the state composite selection value.
    */
   private void selectIDValue(SelectionValue value,
                              CompositeSelectionValue stateSelectionValue,
                              boolean selectChildren)
   {
      value.setSelected(true);
      stateSelectionValue.getSelectionList().addSelectionValue(value);

      if(!(value instanceof CompositeSelectionValue) || !selectChildren) {
         return;
      }

      SelectionList list = ((CompositeSelectionValue) value).getSelectionList();

      for(int i = 0; i < list.getSelectionValueCount(); i++) {
         selectIDValue(list.getSelectionValue(i), stateSelectionValue, selectChildren);
      }
   }

   private void fixStatesForScriptable(CompositeSelectionValue selectionValue,
                                       CompositeSelectionValue stateSelectionValue,
                                       HashMap<Integer, List<SelectionValue>> selectedLevelVals,
                                       HashMap<SelectionValue, List<SelectionValue>> parentMap,
                                       Map<String, List<SelectionValue>> selectedParentMap)
   {
      DataRef[] refs = assembly.getDataRefs();

      // 2. force each single selection path only one selected item.
      for(int i = refs.length - 1; i >= 0; i--) {
         List<SelectionValue> list = selectedLevelVals.get(i);
         boolean singleSelection = assembly.getSelectionTreeInfo().isSingleSelectionLevel(i);

         if(list == null || list.size() == 0) {
            continue;
         }

         for(int j = 0; j < list.size(); j++) {
            SelectionValue value = list.get(j);
            List<SelectionValue> pvals = parentMap.get(value);

            if(!singleSelection) {
               SelectionValue clone = (SelectionValue) value.clone();

               if(clone instanceof CompositeSelectionValue) {
                  ((CompositeSelectionValue) clone).getSelectionList().clear();
               }

               addSelected(clone, pvals, stateSelectionValue, false);
            }

            if(!isSingleSelectionLevel(value.getLevel())) {
               continue;
            }

            List<String> singlePaths = getSinglePaths(pvals);

            // each single path should only contains on selected item.
            if(isAnySinglePathSelected(selectedParentMap, singlePaths)) {
               value.setSelected(false);
               continue;
            }

            addSinglePaths(selectedParentMap, singlePaths, pvals);
         }
      }

      boolean singleSelection = assembly.getSelectionTreeInfo().isSingleSelection();

      // 3. if any parent is selected, force the last level has one selected item
      // in each single selection path. We'll use the last level state to refresh
      // all the parent nodes state.
      SelectionValueIterator iter0 = new SelectionValueIterator(selectionValue) {
         @Override
         protected void visit(SelectionValue val, List<SelectionValue> pvals) {
            if(val.getLevel() != refs.length - 1 || !isAnySelected(pvals) || val.isSelected()) {
               return;
            }

            boolean singleLevel = isSingleSelectionLevel(val.getLevel());
            List<String> singlePaths = singleSelection ? getSinglePaths(pvals) : null;

            // only last level is single
            if(singleSelection && singlePaths.isEmpty()) {
               List<SelectionValue> paths = new ArrayList<>();
               paths.addAll(pvals);
               paths.add(val);
               singlePaths = getSinglePaths(paths);
            }

            if(!singleLevel) {
               CompositeSelectionValue parent = (CompositeSelectionValue) pvals.get(pvals.size() - 1);
               SelectionValue selectedChild = getAnySelectedChild(parent.getSelectionList());

               if(selectedChild == null && !isAnySinglePathSelected(selectedParentMap, singlePaths))
               {
                  val.setState(val.getState() | SelectionValue.STATE_INCLUDED);

                  if(singleSelection) {
                     addSinglePaths(selectedParentMap, singlePaths, pvals);
                  }
               }
            }
            else if(!isAnySinglePathSelected(selectedParentMap, singlePaths)) {
               val.setSelected(true);
               addSinglePaths(selectedParentMap, singlePaths, pvals);
            }
         }
      };

      try {
         iter0.iterate();
      }
      catch(Exception ex) {
         LOG.error("Refresh node status failed when set selected objects by script, " +
            "this is caused by " + ex.getMessage());
      }
   }

   /**
    * If has no selected node, then select the first included item, or the first item.
    */
   public void markSelectedForSingleIDTree(CompositeSelectionValue cselection,
                                           CompositeSelectionValue cselection2)
   {
      SelectionValue[] values = cselection.getSelectionList().getAllSelectionValues();
      SelectionList list = cselection2.getSelectionList();
      boolean hasIncluded = false;

      for(SelectionValue value : values) {
         if(value.isSelected()) {
            return;
         }

         if(value.isIncluded()) {
            hasIncluded = true;
            break;
         }
      }

      for(SelectionValue value : values) {
         if(!hasIncluded || hasIncluded && value.isIncluded()) {
            value.setSelected(true);
            list.addSelectionValue((SelectionValue) value.clone());
            break;
         }
      }
   }

   private boolean isAnySinglePathSelected(Map<String, List<SelectionValue>> selectedParentMap,
                                           List<String> paths)
   {
      if(paths == null) {
         return false;
      }

      return paths.stream().anyMatch(path -> selectedParentMap.containsKey(path));
   }

   private void addSinglePaths(Map<String, List<SelectionValue>> selectedParentMap,
                               List<String> paths, List<SelectionValue> pvals)
   {
      if(paths == null) {
         return;
      }

      List<Integer> levels = getSingleSelectionLevels();

      for(int i = pvals.size() - 1; i >= 0; i--) {
         if(levels.contains(pvals.get(i).getLevel())) {
            SelectionValue pval = i == 0 ? null : pvals.get(i - 1);
            boolean root = i - 1 == 0;
            paths.stream()
               .filter(path -> root || path.indexOf("/" +
                  (pval != null ? Tool.normalize(pval.getValue()) : "")) != -1)
               .forEach(path -> selectedParentMap.put(path, pvals));
         }
      }
   }

   private List<String> getSinglePaths(List<SelectionValue> pvals) {
      List<String> paths = new ArrayList<>();

      if(!assembly.getSelectionTreeInfo().isSingleSelection()) {
         return paths;
      }

      List<Integer> levels = getSingleSelectionLevels();

      if(levels.size() == 0) {
         return paths;
      }

      int lastSingeLevel = levels.get(levels.size() - 1);
      StringBuilder buffer = new StringBuilder();

      for(int i = 0; i < pvals.size() && i <= lastSingeLevel + 1; i++) {
         paths.add(buffer.toString());
         SelectionValue val = pvals.get(i);
         buffer.append(val != null ? Tool.normalize(val.getValue()) : "");
         buffer.append("/");
      }

      return paths;
   }

   private boolean shouldSelect(CompositeSelectionValue cloneRoot,
                                SelectionValue value,
                                List<SelectionValue> pvals,
                                boolean scriptable,
                                boolean anySelectedParent)
   {
      if(scriptable) {
         return value.isSelected() || (value.getState() & SelectionValue.STATE_INCLUDED) != 0;
      }

      if(value.isExcluded() && !value.isSelected() || anySelectedParent && !value.isSelected() && !isAnySelected(pvals)) {
         return false;
      }

      boolean singleLevel = isSingleSelectionLevel(value.getLevel());

      if(singleLevel) {
         CompositeSelectionValue parent = (CompositeSelectionValue) pvals.get(pvals.size() - 1);
         SelectionValue selectedChild = getAnySelectedChild(parent.getSelectionList());

         // since selected items may be iterate later than other items,
         // so if root level is single selection and exist selected item,
         // then only children of the selected one can be selected.
         if(selectedChild != null) {
            return Tool.equals(selectedChild, value);
         }
      }
      else {
         SelectionList list = getSelectionList(cloneRoot, pvals);
         SelectionValue selectedChild = getAnySelectedChild(list);

         // if any sibling is selected in original root then don't force this
         // unselected node to be marked as included. (see append issue in 52477)
         if(selectedChild != null && !value.isSelected()) {
            return false;
         }
      }

      return true;
   }

   private SelectionList getSelectionList(CompositeSelectionValue root,
                                          List<SelectionValue> pvals)
   {
      SelectionList list = root.getSelectionList();
      SelectionValue value = null;

      for(int i = 1; i < pvals.size(); i++) {
         value = list.findValue(pvals.get(i).getValue());

         if(value == null) {
            return null;
         }

         if(value instanceof CompositeSelectionValue) {
            list = ((CompositeSelectionValue) value).getSelectionList();
         }
      }

      return list;
   }


   private boolean isAnySelected(List<SelectionValue> pvals) {
      return pvals.stream().anyMatch(v -> v.isSelected());
   }

   private SelectionValue getAnySelectedChild(SelectionList list) {
      return getAnySelectedChild(list, false);
   }

   private SelectionValue getAnySelectedChild(SelectionList list, boolean recursive) {
      return getAnySelectedChild(list, recursive, -1);
   }

   private SelectionValue getAnySelectedChild(SelectionList list, boolean recursive, int endevel) {
      if(list == null) {
         return null;
      }

      for(int i = 0; i < list.getSelectionValueCount(); i++) {
         SelectionValue value = list.getSelectionValue(i);

         if(value.isSelected()) {
            return value;
         }

         if(recursive && value instanceof CompositeSelectionValue &&
            (endevel == -1 || value.getLevel() < endevel))
         {
            value = getAnySelectedChild(
               ((CompositeSelectionValue) value).getSelectionList(), recursive);

            if(value != null) {
               return value;
            }
         }

      }

      return null;
   }

   /**
    * Add selected node to state CompositeSelectionValue.
    *
    * @param val    the current selection value to set select state.
    * @param pvals  the parent selection values.
    * @param root   the root selection value of state selection value.
    */
   private boolean addSelected(SelectionValue val,
                               List<SelectionValue> pvals,
                               CompositeSelectionValue root,
                               boolean associationDefined)
   {
      boolean singleSelection = assembly.getSelectionTreeInfo().isSingleSelection();
      List<Integer> singleSelectionLevels = getSingleSelectionLevels();

      if(isSingleSelectionLevel(val.getLevel())) {
         int idx = getLastSingleSelectionLevel();

         if(pvals.size() > idx) {
            SelectionValue value = getSelectedValue(pvals.get(idx), val.getLevel());

            // in case, there are both compatible nodes and selected node,
            if(value != null && value != val) {
               return false;
            }
         }
      }

      for(int i = 1; pvals != null && i < pvals.size(); i++) {
         SelectionList list = root.getSelectionList();
         CompositeSelectionValue node = (CompositeSelectionValue) findValue(pvals.get(i), list);

         if(node == null) {
            int plevel = pvals.get(i).getLevel();
            int slevel = -1;

            if(list.getSelectionValueCount() > 0) {
               slevel = list.getSelectionValue(0).getLevel();
            }

            // the parent node is not a selected node.
            if(plevel == slevel) {
               return false;
            }

            node = (CompositeSelectionValue) pvals.get(i).clone();
            node.setSelectionList(new SelectionList());
            list.addSelectionValue(node);
         }

         int appendState = SelectionValue.STATE_INCLUDED;

         if(!singleSelection || singleSelectionLevels.contains(i - 1) ||
            node.isSelected() || !associationDefined)
         {
            appendState = SelectionValue.STATE_SELECTED;
         }

         pvals.get(i).setState(node.getState() | appendState);
         node.setState(node.getState() | appendState);
         root = node;
      }

      if(findValue(val, root.getSelectionList()) != null) {
         return true;
      }

      int appendState = !singleSelection || singleSelectionLevels.contains(val.getLevel()) ||
         val.isExcluded() ? SelectionValue.STATE_SELECTED : SelectionValue.STATE_INCLUDED;
      val.setState(val.getState() | appendState);

      if(val.isSelected() && root.getSelectionList().getSelectionValue(val) == null) {
         root.getSelectionList().addSelectionValue(val);
      }

      return true;
   }

   private boolean shouldRemoveSelect(SelectionValue value,
                                      List<SelectionValue> pvals)
   {

      boolean singleLevel = isSingleSelectionLevel(value.getLevel());

      if(!singleLevel) {
         return false;
      }

      CompositeSelectionValue parent = (CompositeSelectionValue) pvals.get(pvals.size() - 1);
      SelectionValue selectedChild = getAnySelectedChild(parent.getSelectionList());

      // if already exist selected path, should remove the current one.
      return selectedChild != null && !Tool.equals(selectedChild, value);
   }

   private void removeSelectedState(SelectionValue val,
                                    List<SelectionValue> pvals,
                                    CompositeSelectionValue root)
   {
      if(!isSingleSelectionLevel(val.getLevel()) || val.isExcluded()) {
         return;
      }

      val.setState(SelectionValue.STATE_COMPATIBLE);

      for(int i = 1; pvals != null && i < pvals.size(); i++) {
         SelectionList list = root.getSelectionList();
         CompositeSelectionValue node = (CompositeSelectionValue) findValue(pvals.get(i), list);

         if(node == null) {
            break;
         }
         root = node;
      }

      if(root == null) {
         return;
      }

      int idx = indexOfValue(val, root.getSelectionList());

      if(idx != -1) {
         root.getSelectionList().removeSelectionValue(idx);
      }
   }

   private SelectionValue getSelectedValue(SelectionValue value, int level) {
      if(!(value instanceof CompositeSelectionValue)) {
         return null;
      }

      CompositeSelectionValue parent = (CompositeSelectionValue) value;
      List<SelectionValue> list = parent.getSelectionValues(level,
         SelectionValue.STATE_SELECTED, 0);
      return list.size() > 0 ? list.get(0) : null;
   }

   /**
    * Find the selection value on the list.
    */
   private SelectionValue findValue(SelectionValue val, SelectionList list) {
      for(int i = 0; i < list.getSelectionValueCount(); i++) {
         SelectionValue value = list.getSelectionValue(i);

         if(Tool.equals(val.getValue(), value.getValue())) {
            return value;
         }
      }

      return null;
   }

   /**
    * Find the selection value on the list.
    */
   private int indexOfValue(SelectionValue val, SelectionList list) {
      for(int i = 0; i < list.getSelectionValueCount(); i++) {
         SelectionValue value = list.getSelectionValue(i);

         if(Tool.equals(val.getValue(), value.getValue())) {
            return i;
         }
      }

      return -1;
   }

   /**
    * Check if the two parent values are in same single selection path.
    */
   private boolean isSameSingleSelectionPath(List<SelectionValue> pvals1,
                                             List<SelectionValue> pvals2)
   {
      int idx = getLastSingleSelectionLevel();

      // here should start from index 1, because root "null" was included here.
      for(int i = 1; i <= idx + 1; i++) {
         if(!Tool.equals(pvals1.get(i), pvals2.get(i))) {
            return false;
         }
      }

      return true;
   }

   private boolean isSingleSelectionLevel(int level) {
      SelectionTreeVSAssemblyInfo sinfo = assembly.getSelectionTreeInfo();
      return sinfo.isSingleSelectionLevel(level);
   }

   /**
    * Get singleSelectionLevels of the selection tree.
    */
   private List<Integer> getSingleSelectionLevels() {
      SelectionTreeVSAssemblyInfo sinfo = assembly.getSelectionTreeInfo();
      return sinfo.getSingleSelectionLevels();
   }

   /**
    * Get the last single selection level.
    */
   private int getLastSingleSelectionLevel() {
      List<Integer> levels = getSingleSelectionLevels();

      if(levels == null || levels.size() == 0) {
         return -1;
      }

      levels.sort(new DefaultComparator());

      return levels.get(levels.size() - 1);
   }

   private SelectionTreeVSAssembly assembly;
   private static final Logger LOG = LoggerFactory.getLogger(SelectionTreeStateProcessor.class);
}
