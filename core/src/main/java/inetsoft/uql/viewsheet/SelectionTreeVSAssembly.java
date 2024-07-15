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
package inetsoft.uql.viewsheet;

import inetsoft.uql.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.CoreTool;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.*;

/**
 * SelectionTreeVSAssembly represents one selection tree assembly contained in a
 * <tt>Viewsheet</tt>.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class SelectionTreeVSAssembly extends AbstractSelectionVSAssembly
   implements CompositeVSAssembly, AssociatedSelectionVSAssembly, MaxModeSupportAssembly
{
   /**
    * Constructor.
    */
   public SelectionTreeVSAssembly() {
      super();
   }

   /**
    * Constructor.
    */
   public SelectionTreeVSAssembly(Viewsheet vs, String name) {
      super(vs, name);
   }

   /**
    * Create assembly info.
    * @return the associated assembly info.
    */
   @Override
   protected VSAssemblyInfo createInfo() {
      return new SelectionTreeVSAssemblyInfo();
   }

   /**
    * Get the type.
    * @return the type of the assembly.
    */
   @Override
   public int getAssemblyType() {
      return Viewsheet.SELECTION_TREE_ASSET;
   }

   /**
    * Get the name of the target table.
    * @return the name of the target table.
    */
   @Override
   public String getTableName() {
      return getSelectionTreeInfo().getTableName();
   }

   /**
    * Set the name of the target table.
    * @param table the specified name of the target table.
    */
   @Override
   public void setTableName(String table) {
      getSelectionTreeInfo().setTableName(table);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public DataRef[] getDataRefs() {
      return getSelectionTreeInfo().getDataRefs();
   }

   /**
    * Set the data refs.
    * @param refs the specified data refs.
    */
   public void setDataRefs(DataRef[] refs) {
      getSelectionTreeInfo().setDataRefs(refs);
   }

   /**
    * Get the composite selection value.
    * @return the composite selection value.
    */
   public CompositeSelectionValue getCompositeSelectionValue() {
      return getSelectionTreeInfo().getCompositeSelectionValue();
   }

   /**
    * Set the composite selection value.
    * @param value composite selection value.
    */
   public void setCompositeSelectionValue(CompositeSelectionValue value) {
      if(isEnabled()) {
         getSelectionTreeInfo().setCompositeSelectionValue(value);
      }
   }

   /**
    * Get the runtime show type.
    * @return the show type.
    */
   public int getShowType() {
      return getSelectionTreeInfo().getShowType();
   }

   /**
    * Get the design time show type.
    * @return the show type.
    */
   public int getShowTypeValue() {
      return getSelectionTreeInfo().getShowTypeValue();
   }

   /**
    * Set the design time show type, one of the options defined in
    * SelectionVSAssemblyInfo: LIST_SHOW_TYPE or DROPDOWN_SHOW_TYPE.
    * @param type the show type.
    */
   public void setShowTypeValue(int type) {
      getSelectionTreeInfo().setShowTypeValue(type);
   }

   /**
    * Get the runtime sort type.
    * @return the sort type.
    */
   public int getSortType() {
      return getSelectionTreeInfo().getSortType();
   }

   /**
    * Get the design time sort type.
    * @return the sort type.
    */
   public int getSortTypeValue() {
      return getSelectionTreeInfo().getSortTypeValue();
   }

   /**
    * Set the design time sort type, XConstants.SORT_ASC, XConstants.SORT_DESC,
    * or XConstants.SORT_SPECIFIC (for association sorting).
    * @param type the sort type.
    */
   public void setSortTypeValue(int type) {
      getSelectionTreeInfo().setSortTypeValue(type);
   }

   /**
    * Get the group title.
    * @return the title of the checkbox assembly.
    */
   @Override
   public String getTitle() {
      return getSelectionTreeInfo().getTitle();
   }

   /**
    * Get the group title value.
    * @return the title value of the checkbox assembly.
    */
   @Override
   public String getTitleValue() {
      return getSelectionTreeInfo().getTitleValue();
   }

   /**
    * Set the group title value.
    * @param value the specified group title.
    */
   @Override
   public void setTitleValue(String value) {
      getSelectionTreeInfo().setTitleValue(value);
   }

   /**
    * Get the parent column.
    */
   public String getParentID() {
      return getSelectionTreeInfo().getParentID();
   }

   /**
    * Get the parent column.
    */
   public String getParentIDValue() {
      return getSelectionTreeInfo().getParentIDValue();
   }

   /**
    * Set the parent column.
    * @param parent the specified parent column.
    */
   public void setParentIDValue(String parent) {
      getSelectionTreeInfo().setParentIDValue(parent);
   }

   /**
    * Get the child column.
    */
   public String getID() {
      return getSelectionTreeInfo().getID();
   }

   /**
    * Get the child column.
    */
   public String getIDValue() {
      return getSelectionTreeInfo().getIDValue();
   }

   /**
    * Set the child column.
    * @param child the specified child column.
    */
   public void setIDValue(String child) {
      getSelectionTreeInfo().setIDValue(child);
   }

   /**
    * Get the label column.
    */
   public String getLabel() {
      return getSelectionTreeInfo().getLabel();
   }

   /**
    * Get the label column.
    */
   public String getLabelValue() {
      return getSelectionTreeInfo().getLabelValue();
   }

   /**
    * Set the label column.
    * @param label the specified label column.
    */
   public void setLabelValue(String label) {
      getSelectionTreeInfo().setLabelValue(label);
   }

   /**
    * Set the mode.
    */
   public void setMode(int mode) {
      getSelectionTreeInfo().setMode(mode);
   }

   /**
    * Get the mode.
    */
   public int getMode() {
      return getSelectionTreeInfo().getMode();
   }

   /**
    * get the array of selected values defined in javascript
    */
   @Override
   public Object[] getScriptSelectedValues() {
      return scriptSelectedValues;
   }

   /**
    * set the array of selected values defined in javascript
    * @param values the array of selected values defined in javascript
    */
   public void setScriptSelectedValues(Object[] values) {
      this.scriptSelectedValues = values;
   }

   /**
    * Get selection list assembly info.
    * @return the selection list assembly info.
    */
   public SelectionTreeVSAssemblyInfo getSelectionTreeInfo() {
      return (SelectionTreeVSAssemblyInfo) getInfo();
   }

   @Override
   public MaxModeSupportAssemblyInfo getMaxModeInfo() {
      return getSelectionTreeInfo();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean getSelection(Map<String, Map<String, Collection<Object>>> map, boolean applied) {
      if(isIDMode()) {
         return getIDModeSelection(map, applied);
      }
      else {
         return getColumnModeSelection(map, applied);
      }
   }

   /**
    * Get the selection. An entry is added for the first level containing the selected values.
    * A single SELECTION_PATH entry is added and contains all selected paths.
    *
    * @param map     the container contains the selection of this selection
    *                viewsheet assembly.
    * @param applied true to include only selections that are not excluded
    *                from the filtering.
    *
    * @return <tt>true</tt> if duplicated, <tt>false</tt> otherwise.
    */
   private boolean getColumnModeSelection(Map<String, Map<String, Collection<Object>>> map,
                                          boolean applied)
   {
      // if state selection are all come from assocations, don't merge
      // to selection list to effect the selection. 52591
      if(getSelectionTreeInfo().isSingleSelection() &&
         !hasAnySelectedValue(getStateCompositeSelectionValue()))
      {
         return false;
      }

      DataRef[] refs = getDataRefs();
      final List<String> tableNames = getTableNames();

      if(refs.length >= 1) {
         final DataRef ref = refs[0];
         List<Object> list = getColumnModeSelectedObjects0(ref, applied);

         if(list.size() > 0) {
            for(String tableName : tableNames) {
               final Map<String, Collection<Object>> tableSelections =
                  map.computeIfAbsent(tableName, (k) -> new HashMap<>());

               tableSelections.computeIfAbsent(ref.getName(), (k) -> createSelectionSet(ref))
                  .addAll(list);
            }
         }
      }

      Set<Object> selpaths = getSelectedPaths(applied);

      if(selpaths.size() > 0 && refs.length > 0) {
         final String selectionPathKey = SELECTION_PATH + VSUtil.getSelectionKey(refs);

         for(String tableName : tableNames) {
            final Map<String, Collection<Object>> tableSelections =
               map.computeIfAbsent(tableName, (k) -> new HashMap<>());

            if(tableSelections != null) {
               tableSelections.computeIfAbsent(selectionPathKey, (k) -> createSelectionSet(refs[0]))
                  .addAll(selpaths);
            }
         }
      }

      return false;
   }

   /**
    * Check if root level values of the state selection are all included state
    * which means the selections are all caused by associations.
    */
   private boolean hasAnySelectedValue(CompositeSelectionValue stateSelection) {
      if(stateSelection == null) {
         return false;
      }

      SelectionList list = stateSelection.getSelectionList();

      if(list == null) {
         return false;
      }

      for(int i = 0; i < list.getSelectionValueCount(); i++) {
         if(list.getSelectionValue(i).isSelected()) {
            return true;
         }
      }

      return false;
   }

   /**
    * get the selected path based on selection value.
    */
   public Set<Object> getSelectedPaths(CompositeSelectionValue selection, DataRef[] refs, Set<Object> oselPaths,
                                       String selectionPathKey, boolean applied)
   {
      return refs.length > 0 && Tool.equals(selectionPathKey, SELECTION_PATH + VSUtil.getSelectionKey(refs)) ?
         getSelectedPaths(selection, applied) : oselPaths;
   }

   /**
    * Get the selection.
    * @param map the container contains the selection of this selection
    * viewsheet assembly.
    * @param applied true to include only selections that are not excluded
    * from the filtering.
    * @return <tt>true</tt> if duplicated, <tt>false</tt> otherwise.
    */
   private boolean getIDModeSelection(Map<String, Map<String, Collection<Object>>> map,
                                      boolean applied)
   {
      DataRef ref = getDataRef(getID());

      if(ref == null) {
         return false;
      }

      List<Object> list = getIDModeSelectedObjects0(ref, applied);

      if(list.size() > 0) {
         final SelectionSet selectionValues = getSelectionSet(ref, list);

         for(final String tableName : getTableNames()) {
            final Map<String, Collection<Object>> tableSelections =
               map.computeIfAbsent(tableName, (k) -> new HashMap<>());
            tableSelections.computeIfAbsent(ref.getName(), (k) -> createSelectionSet(ref))
               .addAll(selectionValues);
         }
      }

      return false;
   }

   /**
    * Get the specified data ref.
    * @return the specified data ref.
    */
   public DataRef getDataRef(String name) {
      final DataRef[] refs = getDataRefs();

      if(refs == null) {
         return null;
      }

      for(DataRef ref : refs) {
         if(Tool.equals(name, ref.getName())) {
            return ref;
         }
      }

      return null;
   }

   /**
    * Get all the selected path. Each path is / delimited node.
    */
   private Set<Object> getSelectedPaths(boolean applied) {
      return getSelectedPaths(getStateCompositeSelectionValue(), applied);
   }

   /**
    * Get all the selected path. Each path is / delimited node.
    */
   private Set<Object> getSelectedPaths(CompositeSelectionValue root, boolean applied) {
      DataRef[] refs = getDataRefs();
      int refType = refs == null || refs.length == 0 ? DataRef.NONE : refs[0].getRefType();
      Set<Object> paths = (refType & DataRef.CUBE) == DataRef.CUBE ?
         createSelectionSet(refs[0]) : new HashSet<>();

      if(root != null) {
         addSelectedPaths(root.getSelectionList(), paths, "", applied);
      }

      return paths;
   }

   /**
    * Add node to the path set.
    */
   private void addSelectedPaths(SelectionList slist, Set<Object> paths,
                                 String prefix, boolean applied)
   {
      boolean marked = false;

      for(int i = 0; i < slist.getSelectionValueCount(); i++) {
         SelectionValue val = slist.getSelectionValue(i);
         boolean excluded = (val.getState() & SelectionValue.STATE_EXCLUDED) != 0;

         if(applied && excluded) {
            continue;
         }

         if(prefix == null) {
            prefix = CoreTool.FAKE_NULL;
         }

         String value = val.getValue();

         if(value == null) {
            value = CoreTool.FAKE_NULL;
         }

         String p = (prefix == null || prefix.length() == 0) ? value : prefix + "/" + value;
         paths.add(p);

         // this is a fake path to mark that child of the prefix is selected
         if(!excluded && !marked) {
            paths.add(prefix + "/CHILD_SELECTION_EXISTS");
            marked = true;
         }

         if(val instanceof CompositeSelectionValue) {
            addSelectedPaths(((CompositeSelectionValue) val).getSelectionList(),
                             paths, p, applied);
         }
      }
   }

   /**
    * Get the condition list.
    * @return the condition list.
    */
   @Override
   public ConditionList getConditionList() {
      return isIDMode() ? getIDModeConditionList() : getConditionList(getDataRefs());
   }

   /**
    * Gets the condition list.
    *
    * @param dataRefs the columns to use in the conditions.
    *
    * @return the condition list.
    */
   @Override
   public ConditionList getConditionList(DataRef[] dataRefs) {
      if(!isEnabled()) {
         return null;
      }

      return isIDMode() ? getIDModeConditionList(dataRefs[0]) :
         getColumnModeConditionList(dataRefs);
   }

   /**
    * Get the column mode condition list.
    * @return the condition list.
    */
   private ConditionList getColumnModeConditionList(DataRef[] refs) {
      CompositeSelectionValue cval = getStateCompositeSelectionValue();

      if(cval == null) {
         return null;
      }

      SelectionList slist = cval.getSelectionList();
      return getConditionList(0, slist, refs);
   }

   /**
    * Get the ID mode condition list.
    * @return the condition list.
    */
   private ConditionList getIDModeConditionList() {
      return getIDModeConditionList(getDataRef(getID()));
   }

   /**
    * Get the ID mode condition list.
    *
    * @param ref the ID column.
    *
    * @return the condition list.
    */
   private ConditionList getIDModeConditionList(DataRef ref) {
      if(ref == null) {
         return null;
      }

      List<Object> list = getSelectedObjects();
      return VSUtil.createConditionList(ref, list);
   }

   /**
    * Get the condition list of a selection list.
    * @param level the specified selection list level.
    * @param slist the specified selection list.
    * @param refs  the condition columns.
    * @return the created condition list.
    */
   private ConditionList getConditionList(int level, SelectionList slist, DataRef[] refs) {
      ConditionList result = null;
      int length = refs.length;

      if(level >= 0 && level < length) {
         if(level == length - 1) {
            if(refs[level] != null) {
               List<Object> objs = getSelectedObjects(refs[level], slist, level);
               result = VSUtil.createConditionList(refs[level], objs);
            }
         }
         else {
            List<ConditionList> list = new ArrayList<>();

            for(int i = 0; i < slist.getSelectionValueCount(); i++) {
               final SelectionValue sval = slist.getSelectionValue(i);
               ConditionList conds = getConditionList(level, sval, refs);
               list.add(conds);
            }

            result = VSUtil.mergeConditionList(list, JunctionOperator.OR);
         }
      }

      return result;
   }

   private boolean shouldMergeToCondition(SelectionValue sval) {
      int state = sval.getState();

      if((state & SelectionValue.STATE_EXCLUDED) != 0) {
         return false;
      }

      List<Integer> slevels = getSelectionTreeInfo().getSingleSelectionLevels();
      int lastSingleLevel =
         slevels.size() > 0 ? slevels.get(slevels.size() - 1) : -1;

      return sval.isSelected() || lastSingleLevel != -1 &&
         (state & SelectionValue.STATE_INCLUDED) != 0 && sval.getLevel() < lastSingleLevel;
   }

   /**
    * Get the condition list of a selection value.
    *
    * @param sval the specified selection value.
    *
    * @return the created condition list.
    */
   private ConditionList getConditionList(int level, SelectionValue sval, DataRef[] refs) {
      if(!shouldMergeToCondition(sval)) {
         return null;
      }

      DataRef ref = refs[level];
      ConditionList conds = new ConditionList();

      if(ref != null) {
         String vstr = sval.getValue();
         String dtype = ref.getDataType();
         Object obj = Tool.getData(dtype, vstr, true);
         Condition cond = new Condition();
         cond.setOperation(obj == null ? Condition.NULL : Condition.EQUAL_TO);
         cond.setType(ref.getDataType());
         cond.addValue(obj);
         ConditionItem citem = new ConditionItem(ref, cond, 0);
         conds.append(citem);
      }

      if(sval instanceof CompositeSelectionValue) {
         final CompositeSelectionValue cval = (CompositeSelectionValue) sval;
         SelectionList slist = cval.getSelectionList();
         ConditionList sconds = getConditionList(level + 1, slist, refs);

         if(sconds != null && sconds.getSize() > 0) {
            if(conds.getSize() > 0) {
               conds.append(new JunctionOperator(JunctionOperator.AND, 0));
            }

            for(int i = 0; i < sconds.getSize(); i++) {
               HierarchyItem item = sconds.getItem(i);

               if(sconds.getSize() > 1) {
                  item.setLevel(item.getLevel() + 1);
               }

               conds.append(item);
            }
         }
      }

      return conds;
   }

   /**
    * Get the selected objects.
    * @param ref the specified data ref.
    * @param slist the specified selection list.
    * @return the selected objects.
    */
   private List<Object> getSelectedObjects(DataRef ref, SelectionList slist, int level) {
      List<Object> list = new ArrayList<>();

      if(slist == null) {
         return list;
      }

      SelectionValue[] vals = slist.getSelectionValues();

      for(SelectionValue val : vals) {
         if(!val.isSelected()) {
            continue;
         }

         if((val.getState() & SelectionValue.STATE_EXCLUDED) != 0 &&
            !getSelectionTreeInfo().isSingleSelectionLevel(level))
         {
            continue;
         }

         String vstr = val.getValue();
         String dtype = ref.getDataType();
         Object obj = Tool.getData(dtype, vstr, true);
         list.add(obj);
      }

      return list;
   }

   /**
    * Get the selected objects.
    * @return the selected objects.
    */
   public List<Object> getSelectedObjects() {
      if(isIDMode()) {
         return getSelectedObjects(getDataRef(getID()));
      }
      else {
         DataRef[] refs = getDataRefs();
         List<Object> all = new ArrayList<>();

         for(DataRef ref : refs) {
            List<Object> list = getSelectedObjects(ref);
            all.addAll(list);
         }

         return all;
      }
   }

   /**
    * Get the selected objects.
    * @return the selected objects.
    */
   public List<Object> getSelectedObjects(DataRef ref) {
      return isIDMode() ? getIDModeSelectedObjects0(ref, true) :
         getColumnModeSelectedObjects0(ref, true);
   }

   /**
    * Get the column mode selected objects.
    * @param applied true to include only selections that are not excluded
    * from the filtering.
    * @return the selected objects.
    */
   private List<Object> getColumnModeSelectedObjects0(DataRef ref, final boolean applied) {
      int level = -1;
      final DataRef[] refs = getDataRefs();
      final List<Object> list = new ArrayList<>();

      for(int i = 0; i < refs.length; i++) {
         if(refs[i].equals(ref)) {
            level = i;
            break;
         }
      }

      if(level == -1) {
         return list;
      }

      CompositeSelectionValue cval = getStateCompositeSelectionValue();

      if(cval == null) {
         return list;
      }

      //fix bug #3983
      checkScriptSelectedValues();

      String dtype = ref.getDataType();
      int excluded = applied ? SelectionValue.STATE_EXCLUDED : 0;
      List<SelectionValue> vals = cval.getSelectionValues(
         level, SelectionValue.STATE_SELECTED, excluded);

      for(SelectionValue val : vals) {
         String vstr = val.getValue();
         Object obj = Tool.getData(dtype, vstr, true);

         list.add(obj);
      }

      return list;
   }

   /**
    * Get the ID mode selected objects.
    * @param applied true to include only selections that are not excluded
    * from the filtering.
    * @return the selected objects.
    */
   private List<Object> getIDModeSelectedObjects0(DataRef ref, final boolean applied) {
      //fix bug #3983
      checkScriptSelectedValues();

      SelectionList slist = getStateSelectionList();
      List<Object> list = new ArrayList<>();

      if(slist == null || ref == null) {
         return list;
      }

      SelectionValue[] vals = slist.getAllSelectionValues();

      for(SelectionValue val : vals) {
         if(!val.isSelected()) {
            continue;
         }

         if(applied && (val.getState() & SelectionValue.STATE_EXCLUDED) != 0) {
            continue;
         }

         String vstr = val.getValue();
         String dtype = ref.getDataType();
         Object obj = Tool.getData(dtype, vstr, true);
         list.add(obj);
      }

      return list;
   }

   /**
    * check if there is any selected values defined in javascript that
    * need to be processed.
    */
   private void checkScriptSelectedValues() {
      SelectionTreeVSAssemblyInfo sinfo = (SelectionTreeVSAssemblyInfo) getInfo();

      if(scriptSelectedValues != null && !sinfo.isUsingMetaData()) {
         CompositeSelectionValue cvalue = sinfo.getCompositeSelectionValue();
         cvalue = (CompositeSelectionValue) cvalue.clone();

         SelectionValueIterator iter = new SelectionValueIterator(cvalue) {
            @Override
            protected void visit(SelectionValue val,
                                 List<SelectionValue> parents) throws Exception
            {
               int state = val.getState();
               val.setState(state - state & SelectionValue.STATE_SELECTED);

               for(int j = 0; j < scriptSelectedValues.length; j++) {
                  String value0 =
                     (String) Tool.getData(String.class, scriptSelectedValues[j]);

                  if(value0.equals(val.getValue())) {
                     val.setState(state | SelectionValue.STATE_SELECTED);
                  }
               }
            }
         };

         try {
            iter.iterate();
         }
         catch(Exception ex) {
            LOG.error("Failed to set the selected objects: " +
                  Arrays.toString(scriptSelectedValues), ex);
         }

         setStateCompositeSelectionValue(cvalue);
         setScriptSelectedValues(null);
      }
   }

   /**
    * Reset the selection.
    * @return <tt>true</tt> if changed, <tt>false</tt> otherwise.
    */
   @Override
   public boolean resetSelection() {
      SelectionList slist = cval == null ? null : cval.getSelectionList();
      boolean changed = slist != null && slist.getSelectionValueCount() > 0;
      setStateCompositeSelectionValue(null);
      setCompositeSelectionValue(null);
      return changed;
   }

   /**
    * Get the selection list.
    * @return the selection list.
    */
   @Override
   public SelectionList getSelectionList() {
      CompositeSelectionValue cvalue = getCompositeSelectionValue();
      return cvalue == null ? null : cvalue.getSelectionList();
   }

   /**
    * Set the selection list.
    * @param list the selection list.
    */
   @Override
   public void setSelectionList(SelectionList list) {
      CompositeSelectionValue cvalue = list == null ?  null : new CompositeSelectionValue();

      if(cvalue != null) {
         cvalue.setLevel(-1);
         cvalue.setSelectionList(list);
      }

      setCompositeSelectionValue(cvalue);
   }

   /**
    * Get the state selection list.
    * @return the selection list.
    */
   @Override
   public SelectionList getStateSelectionList() {
      CompositeSelectionValue cvalue = getStateCompositeSelectionValue();
      return cvalue == null ? null : cvalue.getSelectionList();
   }

   /**
    * Check if contains excluded selection in this selection viewsheet assembly.
    * @return <tt>true</tt> if contains excluded selection, <tt>false</tt>
    * otherwise.
    */
   @Override
   public boolean containsExcludedSelection() {
      if(!isEnabled() || cval == null) {
         return false;
      }

      SelectionList slist = cval.getSelectionList();
      return VSUtil.containsExcludedSelection(slist);
   }

   /**
    * Check if contains selection in this selection viewsheet assembly.
    * @return <tt>true</tt> if contains excluded selection, <tt>false</tt>
    * otherwise.
    */
   @Override
   public boolean containsSelection() {
      if(!isEnabled() || cval == null) {
         return false;
      }

      SelectionList slist = cval.getSelectionList();
      return VSUtil.containsSelection(slist);
   }

   /**
    * Set the state selection list.
    * @param list the selection list.
    * @return the change hint.
    */
   @Override
   public int setStateSelectionList(SelectionList list) {
      CompositeSelectionValue cvalue = list == null ?  null :
         new CompositeSelectionValue();

      if(cvalue != null) {
         cvalue.setLevel(-1);
         cvalue.setSelectionList(list);
      }

      return setStateCompositeSelectionValue(cvalue);
   }

   /**
    * Get the state composite selection value.
    * @return the composite selection value.
    */
   public CompositeSelectionValue getStateCompositeSelectionValue() {
      return cval;
   }

   /**
    * Set the state composite selection value.
    * @param cval the composte selection value.
    * @return the change hint.
    */
   public int setStateCompositeSelectionValue(CompositeSelectionValue cval) {
      // shouldn't ignore the value if it's disabled. the PlaceholderService.refreshViewsheet:1011
      // sets the enabled to false during addDeletedVSObject(), so if we ignore the value here,
      // value set throught script would not be applied. (63150)
      //if(isEnabled()) {
      if(Tool.equals(this.cval, cval)) {
         return NONE_CHANGED;
      }

      this.cval = cval;
      return OUTPUT_DATA_CHANGED;
   }

   /**
    * Write the state.
    * @param writer the specified print writer.
    */
   @Override
   protected void writeStateContent(PrintWriter writer, boolean runtime) {
      super.writeStateContent(writer, runtime);

      CompositeSelectionValue cval = getStateCompositeSelectionValue();

      if(cval != null) {
         writer.println("<state_selectionValue>");
         cval.writeXML(writer);
         writer.println("</state_selectionValue>");
      }

      writer.println("<state_order order=\"" + getSortTypeValue() + "\" />");
      writer.println("<state_selectionStyle dValue=\""
         + getSelectionVSAssemblyInfo().getSingleSelectionValue() + "\" />");

      if(runtime) {
         writer.println("<state_info>");
         getSelectionVSAssemblyInfo().writeStateContent(writer, runtime);
         writer.println("</state_info>");
      }
   }

   /**
    * Parse the state.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseStateContent(Element elem, boolean runtime)
      throws Exception
   {
      super.parseStateContent(elem, runtime);

      Element snode = Tool.getChildNodeByTagName(elem, "state_selectionValue");

      if(snode != null) {
         snode = Tool.getFirstChildNode(snode);
         CompositeSelectionValue cval = new CompositeSelectionValue();
         cval.parseXML(snode);
         this.cval = cval;
      }
      else {
         this.cval = null;
      }

      Element onode = Tool.getChildNodeByTagName(elem, "state_order");

      if(onode != null) {
         int order = Integer.parseInt(Tool.getAttribute(onode, "order"));
         setSortTypeValue(order);
      }

      onode = Tool.getChildNodeByTagName(elem, "state_selectionStyle");

      if(onode != null) {
         boolean singleValue = Boolean.parseBoolean(Tool.getAttribute(onode, "dValue"));
         getSelectionVSAssemblyInfo().setSingleSelectionValue(singleValue);
      }

      onode = Tool.getChildNodeByTagName(elem, "state_info");

      if(onode != null) {
         getSelectionVSAssemblyInfo().parseStateContent(onode, runtime);
      }
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public SelectionTreeVSAssembly clone() {
      try {
         SelectionTreeVSAssembly assembly2 = (SelectionTreeVSAssembly) super.clone();

         if(cval != null) {
            assembly2.cval = (CompositeSelectionValue) cval.clone();
         }

         return assembly2;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone SelectionTreeVSAssembly", ex);
      }

      return null;
   }

   /**
    * Check if requires reset.
    * @return <tt>true</tt> if requires reset, <tt>false</tt> otherwise.
    */
   @Override
   public boolean requiresReset() {
      return cval != null && cval.requiresReset();
   }

   /**
    * Copy the state selection from a selection viewsheet assembly.
    * @param assembly the specified selection viewsheet assembly.
    * @return the changed hint.
    */
   @Override
   public int copyStateSelection(SelectionVSAssembly assembly) {
      SelectionTreeVSAssembly sassembly = (SelectionTreeVSAssembly) assembly;
      CompositeSelectionValue val = sassembly.getStateCompositeSelectionValue();
      return setStateCompositeSelectionValue(val);
   }

   /**
    * Get the cell width.
    * @return the cell width.
    */
   @Override
   public int getCellWidth() {
      return 1;
   }

   /**
    * Get display value.
    * @param onlyList only get the selected values, not include title,
    * and not restrict by visible properties.
    * @return the string to represent the selected value.
    */
   @Override
   public String getDisplayValue(boolean onlyList) {
      return getDisplayValue(true, "; ");
   }

   /**
    * Get display value.
    * @param onlyList only get the selected values, not include title,
    * and not restrict by visible properties.
    * @param separator the specified separator.
    * @return the string to represent the selected value.
    */
   @Override
   public String getDisplayValue(boolean onlyList, String separator) {
      if(getCompositeSelectionValue() == null || !isEnabled()) {
         return null;
      }

      SelectionTreeVSAssemblyInfo tinfo = getSelectionTreeInfo();
      Vector dispList = new Vector();
      StringBuilder values = new StringBuilder();
      List<String> prefix = new ArrayList<>();
      int lastLevel = -1;
      boolean hasValue = false;

      tinfo.visitCompositeChild(getCompositeSelectionValue(), dispList);

      for(int i = 0; i < dispList.size(); i++) {
         SelectionValue sv = (SelectionValue) dispList.get(i);

         if(sv.getLabel() == null) {
            continue;
         }

         hasValue = true;

         if(isIDMode()) {
            List<String> list = new ArrayList<>();
            list.add(sv.getLabel());
            addValueText(list, values, separator);
         }
         else {
            if(sv.getLevel() > lastLevel) {
               prefix.add(sv.getLabel());
            }
            else {
               List<String> nprefix = new ArrayList<>();
               addValueText(prefix, values, separator);

               for(int j = 0; j < sv.getLevel(); j++) {
                  nprefix.add(prefix.get(j));
               }

               nprefix.add(sv.getLabel());
               prefix = nprefix;
            }

            if(i == dispList.size() - 1) {
               addValueText(prefix, values, separator);
            }

            lastLevel = sv.getLevel();
         }
      }

      return hasValue ? values.toString() : null;
   }

   /**
    * Get the value text from the ArrayList.
    */
   private void addValueText(List<String> prefix, StringBuilder values, String separator) {
      StringBuilder valueBuilder = new StringBuilder();

      for(String s : prefix) {
         if(valueBuilder.length() > 0) {
            valueBuilder.append(":");
         }

         valueBuilder.append(s);
      }

      if(values.length() > 0) {
         values.append(separator);
      }

      values.append(valueBuilder.toString());
   }

   @Override
   public void removeBindingCol(String ref) {
      DataRef[] refs = getDataRefs();

      if(refs != null) {
         for(int i = refs.length - 1; i >= 0; i--) {
            if(Tool.equals(refs[i].getName(), ref)) {
               refs = VSUtil.removeRow(refs, i);
               getSelectionTreeInfo().setDataRefs(refs);
            }
         }
      }

      if(getMeasure() != null) {
         if(Tool.equals(getMeasure(), ref)) {
            getSelectionTreeInfo().setMeasure(null);
            getSelectionTreeInfo().setMeasureValue(null);
         }
      }
   }

   @Override
   public void renameBindingCol(String oname, String nname) {
      DataRef[] refs = getDataRefs();

      if(refs != null) {
         for(int i = refs.length - 1; i >= 0; i--) {
            DataRef cref = refs[i];

            if(Tool.equals(cref.getName(), oname)) {
               VSUtil.renameDataRef(cref, nname);
            }
         }
      }
   }

   public boolean isIDMode() {
      return getMode() == SelectionTreeVSAssemblyInfo.ID;
   }

   @Override
   public String getMeasure() {
      return getSelectionTreeInfo().getMeasure();
   }

   @Override
   public String getMeasureValue() {
      return getSelectionTreeInfo().getMeasureValue();
   }

   @Override
   public String getFormula() {
      return getSelectionTreeInfo().getFormula();
   }

   @Override
   public String getFormulaValue() {
      return getSelectionTreeInfo().getFormulaValue();
   }

   /**
    * Deselect all nodes with level at or above the given level.
    *
    * @param level the level at which to start deselecting nodes.
    *
    * @return true if any nodes were deselected, false otherwise.
    */
   public boolean deselect(int level) {
      if(getDataRefs().length <= level || isIDMode()) {
         return false;
      }

      boolean deselected = false;
      final CompositeSelectionValue rootStateValue = getStateCompositeSelectionValue();

      if(rootStateValue != null) {
         final SelectionList slist = rootStateValue.getSelectionList();
         deselected = deselectStateSelectionList(slist, level);
      }

      final CompositeSelectionValue rootValue = getCompositeSelectionValue();

      if(rootValue != null && deselected) {
         final SelectionList slist = rootValue.getSelectionList();
         deselectSelectionList(slist, level);
      }

      return deselected;
   }

   /**
    * @param slist the selection list to deselect.
    * @param level the level at which to start deselecting nodes.
    */
   private void deselectSelectionList(SelectionList slist, int level) {
      for(int i = 0; i < slist.getSelectionValueCount(); i++) {
         final SelectionValue selectionValue = slist.getSelectionValue(i);

         if(selectionValue instanceof CompositeSelectionValue) {
            final SelectionList childSelectionList =
               ((CompositeSelectionValue) selectionValue).getSelectionList();

            deselectSelectionList(childSelectionList, level);
         }

         if(selectionValue.getLevel() >= level) {
            selectionValue.setSelected(false);
         }
      }
   }

   /**
    * @param slist the state selection list to deselect.
    * @param level the level at which to start deselecting nodes.
    *
    * @return true if any nodes were deselected, false otherwise.
    */
   private boolean deselectStateSelectionList(SelectionList slist, int level) {
      boolean deselected = false;

      for(int i = 0; i < slist.getSelectionValueCount(); i++) {
         final SelectionValue selectionValue = slist.getSelectionValue(i);

         if(selectionValue instanceof CompositeSelectionValue) {
            final SelectionList childSelectionList =
               ((CompositeSelectionValue) selectionValue).getSelectionList();

            if(level - 1 > selectionValue.getLevel()) {
               deselected = deselectStateSelectionList(childSelectionList, level) || deselected;
            }
            else if(level - 1 == selectionValue.getLevel()) {
               deselected = deselected || childSelectionList.getSelectionValueCount() > 0;
               childSelectionList.clear();
            }
         }
      }

      return deselected;
   }

   // output data
   private CompositeSelectionValue cval;
   private Object[] scriptSelectedValues;

   private static final Logger LOG =
      LoggerFactory.getLogger(SelectionTreeVSAssembly.class);
}
