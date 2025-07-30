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
package inetsoft.uql.viewsheet;

import inetsoft.report.TableDataPath;
import inetsoft.uql.ConditionList;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.PrintWriter;
import java.util.List;
import java.util.*;

/**
 * SelectionListVSAssembly represents one selection list assembly contained in a
 * <tt>Viewsheet</tt>.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class SelectionListVSAssembly extends AbstractSelectionVSAssembly
   implements CompositeVSAssembly, AssociatedSelectionVSAssembly, MaxModeSupportAssembly
{
   /**
    * Constructor.
    */
   public SelectionListVSAssembly() {
      super();
   }

   /**
    * Constructor.
    */
   public SelectionListVSAssembly(Viewsheet vs, String name) {
      super(vs, name);
   }

   /**
    * Create assembly info.
    * @return the associated assembly info.
    */
   @Override
   protected VSAssemblyInfo createInfo() {
      return new SelectionListVSAssemblyInfo();
   }

   /**
    * Get the type.
    * @return the type of the assembly.
    */
   @Override
   public int getAssemblyType() {
      return Viewsheet.SELECTION_LIST_ASSET;
   }

   /**
    * Get the name of the target table.
    * @return the name of the target table.
    */
   @Override
   public String getTableName() {
      return getSelectionListInfo().getTableName();
   }

   /**
    * Set the name of the target table.
    * @param table the specified name of the target table.
    */
   @Override
   public void setTableName(String table) {
      getSelectionListInfo().setTableName(table);
   }

   /**
    * Get the data ref.
    * @return the data ref.
    */
   public DataRef getDataRef() {
      return getSelectionListInfo().getDataRef();
   }

   /**
    * Set the data ref.
    * @param ref the specified data ref.
    */
   public void setDataRef(DataRef ref) {
      getSelectionListInfo().setDataRef(ref);
   }

   /**
    * Get the selection list.
    * @return the selection list.
    */
   @Override
   public SelectionList getSelectionList() {
      return getSelectionListInfo().getSelectionList();
   }

   /**
    * Set the selection list.
    * @param list the selection list.
    */
   @Override
   public void setSelectionList(SelectionList list) {
      if(isEnabled() || getSelectionListInfo().getSelectionList() == null) {
         getSelectionListInfo().setSelectionList(list);
      }
   }

   /**
    * Get the runtime show type.
    * @return the show type.
    */
   public int getShowType() {
      return getSelectionListInfo().getShowType();
   }

   /**
    * Get the design time show type.
    * @return the show type.
    */
   public int getShowTypeValue() {
      return getSelectionListInfo().getShowTypeValue();
   }

   /**
    * Set the design time show type, one of the options defined in
    * SelectionVSAssemblyInfo: LIST_SHOW_TYPE or DROPDOWN_SHOW_TYPE.
    * @param type the show type.
    */
   public void setShowTypeValue(int type) {
      getSelectionListInfo().setShowTypeValue(type);
   }

   /**
    * Get the runtime sort type.
    * @return the sort type.
    */
   public int getSortType() {
      return getSelectionListInfo().getSortType();
   }

   /**
    * Get the design time sort type.
    * @return the sort type.
    */
   public int getSortTypeValue() {
      return getSelectionListInfo().getSortTypeValue();
   }

   /**
    * Set the design time sort type, XConstants.SORT_ASC, XConstants.SORT_DESC,
    * or XConstants.SORT_SPECIFIC (for association sorting).
    * @param type the sort type.
    */
   public void setSortTypeValue (int type) {
      getSelectionListInfo().setSortTypeValue(type);
   }

   /**
    * Get the group title.
    * @return the title of the checkbox assembly.
    */
   @Override
   public String getTitle() {
      return getSelectionListInfo().getTitle();
   }

   /**
    * Get the group title value.
    * @return the title value of the checkbox assembly.
    */
   @Override
   public String getTitleValue() {
      return getSelectionListInfo().getTitleValue();
   }

   /**
    * Set the group title value.
    * @param value the specified group title.
    */
   @Override
   public void setTitleValue(String value) {
      getSelectionListInfo().setTitleValue(value);
   }

   /**
    * Get selection list assembly info.
    * @return the selection list assembly info.
    */
   public SelectionListVSAssemblyInfo getSelectionListInfo() {
      return (SelectionListVSAssemblyInfo) getInfo();
   }

   @Override
   public MaxModeSupportAssemblyInfo getMaxModeInfo() {
      return getSelectionListInfo();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean getSelection(Map<String, Map<String, Collection<Object>>> map, boolean applied) {
      if(!isEnabled()) {
         return false;
      }

      DataRef ref = getDataRef();

      if(ref == null) {
         return false;
      }

      // ignore addition usage, since binding timestamp + additional tables is not a common usage,
      // so no need to spend a lot of effort to figure it out which table the selection value data come from.
      Tool.useDatetimeWithMillisFormat.set(Tool.isDatabricks(this));
      List<Object> list = null;

      try {
         list = getSelectedObjects0(applied);
      }
      finally {
         Tool.useDatetimeWithMillisFormat.set(false);
      }

      if(list.size() > 0) {
         for(String tableName : getTableNames()) {
            final Map<String, Collection<Object>> tableSelections =
               map.computeIfAbsent(tableName, (k) -> new HashMap<>());
            tableSelections.computeIfAbsent(ref.getName(), (k) -> createSelectionSet(ref))
               .addAll(list);
         }
      }

      return false;
   }

   /**
    * Reset the selection.
    * @return <tt>true</tt> if changed, <tt>false</tt> otherwise.
    */
   @Override
   public boolean resetSelection() {
      boolean changed = slist != null && slist.getSelectionValueCount() > 0;
      setStateSelectionList(null);
      setSelectionList(null);
      return changed;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public ConditionList getConditionList() {
      return getConditionList(getDataRefs());
   }

   @Override
   public ConditionList getConditionList(DataRef[] dataRefs) {
      if(!isEnabled() || dataRefs == null || dataRefs.length == 0) {
         return null;
      }

      List<Object> list = getSelectedObjects();
      DataRef ref = dataRefs[0];

      if(ref == null) {
         return null;
      }

      return VSUtil.createConditionList(ref, list);
   }

   /**
    * Get the selected objects.
    * @return the selected objects.
    */
   public List<Object> getSelectedObjects() {
      return getSelectedObjects0(true);
   }

   /**
    * Get the selected objects.
    * @param applied true to include only selections that are not excluded
    * from the filtering.
    * @return the selected objects.
    */
   private List<Object> getSelectedObjects0(boolean applied) {
      //fix bug #3983
      checkScriptSelectedValues();

      DataRef ref = getDataRef();
      SelectionList slist = getStateSelectionList();
      List<Object> list = new ArrayList<>();

      if(slist == null || ref == null) {
         return list;
      }

      SelectionValue[] vals = slist.getSelectionValues();

      for(SelectionValue val : vals) {
         if(!val.isSelected()) {
            continue;
         }

         if(applied && ((val.getState() & SelectionValue.STATE_EXCLUDED) != 0 &&
            !getSelectionListInfo().isSingleSelection()))
         {
            continue;
         }

         String vstr = val.getValue();
         String dtype = ref.getDataType();
         Object obj = Tool.getData(dtype, vstr, false);
         list.add(obj);
      }

      return list;
   }

   /**
    * check if there is any selected values defined in javascript that
    * need to be processed.
    */
   private void checkScriptSelectedValues() {
      SelectionValue[] vals;
      SelectionListVSAssemblyInfo sinfo = (SelectionListVSAssemblyInfo) getInfo();

      if(scriptSelectedValues != null) {
         SelectionList newList = getSelectionList();

         if(newList != null) {
            newList = (SelectionList) newList.clone();

            vals = newList.getSelectionValues();

            for(SelectionValue val : vals) {
               int state = val.getState();
               val.setState(state - (state & SelectionValue.STATE_SELECTED));

               for(Object scriptSelectedValue : scriptSelectedValues) {
                  String value0 = Tool.toString(scriptSelectedValue);

                  if(value0.equals(val.getValue())) {
                     val.setState(state | SelectionValue.STATE_SELECTED);
                  }
               }
            }

            setStateSelectionList(newList);

            // @by stephenwebster, for Bug #25200
            // always check the script selected values, but only remove them once the
            // final query list has been settled
            if(!sinfo.isUsingMetaData()) {
               setScriptSelectedValues(null);
            }

            getSelectionList().setSelectionValues(vals);
         }
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public DataRef[] getDataRefs() {
      return getSelectionVSAssemblyInfo().getDataRefs();
   }

   /**
    * Get the state selection list.
    * @return the selection list.
    */
   @Override
   public SelectionList getStateSelectionList() {
      return slist;
   }

   /**
    * Check if contains excluded selection in this selection viewsheet assembly.
    * @return <tt>true</tt> if contains excluded selection, <tt>false</tt>
    * otherwise.
    */
   @Override
   public boolean containsExcludedSelection() {
      if(!isEnabled()) {
         return false;
      }

      return VSUtil.containsExcludedSelection(slist) ||
         // @by larryl, if this is a single selection, the a new selection could
         // be made when the old selection is cleared. We can't process the
         // condition until this assembly is processed.
         getSelectionListInfo().isSingleSelection();
   }

   /**
    * Check if contains selection in this selection viewsheet assembly.
    * @return <tt>true</tt> if contains excluded selection, <tt>false</tt>
    * otherwise.
    */
   @Override
   public boolean containsSelection() {
      return isEnabled() && VSUtil.containsSelection(slist);
   }

   /**
    * Set the state selection list.
    * @param list the selection list.
    * @return the change hint.
    */
   @Override
   public int setStateSelectionList(SelectionList list) {
      if(isEnabled()) {
         if(Tool.equals(slist, list)) {
            return NONE_CHANGED;
         }

         this.slist = list;
         return OUTPUT_DATA_CHANGED;
      }

      return NONE_CHANGED;
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
    * Write the state.
    * @param writer the specified print writer.
    */
   @Override
   protected void writeStateContent(PrintWriter writer, boolean runtime) {
      super.writeStateContent(writer, runtime);
      SelectionList slist = getStateSelectionList();

      if(slist != null) {
         writer.println("<state_selectionList>");
         slist.writeXML(writer);
         writer.println("</state_selectionList>");
      }

      writer.println("<state_order order=\"" + getSortTypeValue() + "\" />");

      writer.println("<state_selectionStyle dValue=\""
         + getSelectionVSAssemblyInfo().getSingleSelectionValue() + "\" />");
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
      Element snode = Tool.getChildNodeByTagName(elem, "state_selectionList");

      if(snode != null) {
         snode = Tool.getFirstChildNode(snode);
         SelectionList slist = new SelectionList();
         slist.parseXML(snode);
         this.slist = slist;
      }
      else {
         this.slist = null;
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
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public SelectionListVSAssembly clone() {
      try {
         SelectionListVSAssembly assembly2 = (SelectionListVSAssembly) super.clone();

         if(slist != null) {
            assembly2.slist = (SelectionList) slist.clone();
         }

         return assembly2;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone SelectionListVSAssembly", ex);
      }

      return null;
   }

   /**
    * Check if requires reset.
    * @return <tt>true</tt> if requires reset, <tt>false</tt> otherwise.
    */
   @Override
   public boolean requiresReset() {
      return slist != null && slist.requiresReset();
   }

   /**
    * Copy the state selection from a selection viewsheet assembly.
    * @param assembly the specified selection viewsheet assembly.
    * @return the changed hint.
    */
   @Override
   public int copyStateSelection(SelectionVSAssembly assembly) {
      SelectionListVSAssembly sassembly = (SelectionListVSAssembly) assembly;
      return setStateSelectionList(sassembly.getStateSelectionList());
   }

   /**
    * Get the cell width.
    * @return the cell width.
    */
   @Override
   public int getCellWidth() {
      TableDataPath path = new TableDataPath(-1, TableDataPath.DETAIL);
      VSCompositeFormat format = getFormatInfo().getFormat(path);
      Dimension span = format == null ? null : format.getSpan();
      return span == null ? 1 : span.width;
   }

   @Override
   public void removeBindingCol(String ref) {
      DataRef dref = getSelectionListInfo().getDataRef();

      if(dref != null && Tool.equals(ref, dref.getName())) {
         setTableName(null);
      }
   }

   @Override
   public void renameBindingCol(String oname, String nname) {
      DataRef dref = getSelectionListInfo().getDataRef();

      if(dref != null && Tool.equals(oname, dref.getName())) {
         VSUtil.renameDataRef(dref, nname);
      }
   }

   /**
    * Clear the layout state.
    */
   @Override
   public void clearLayoutState() {
      super.clearLayoutState();

      SelectionList list = getSelectionListInfo().getSelectionList();

      if(list != null) {
         SelectionValue[] svalues = list.getAllSelectionValues();

         for(SelectionValue svalue : svalues) {
            VSCompositeFormat format = svalue.getFormat();

            if(format != null) {
               format.setRScaleFont(1);
            }
         }
      }
   }

   @Override
   public String getMeasure() {
      return getSelectionListInfo().getMeasure();
   }

   @Override
   public String getMeasureValue() {
      return getSelectionListInfo().getMeasureValue();
   }

   @Override
   public String getFormula() {
      return getSelectionListInfo().getFormula();
   }

   @Override
   public String getFormulaValue() {
      return getSelectionListInfo().getFormulaValue();
   }

   // output data
   private SelectionList slist;

   private Object[] scriptSelectedValues;

   private static final Logger LOG =
      LoggerFactory.getLogger(SelectionListVSAssembly.class);
}
