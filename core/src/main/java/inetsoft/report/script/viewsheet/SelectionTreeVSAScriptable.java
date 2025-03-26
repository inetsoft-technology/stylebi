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
package inetsoft.report.script.viewsheet;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.SelectionTreeVSAssemblyInfo;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Undefined;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * The selection tree viewsheet assembly scriptable in viewsheet scope.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class SelectionTreeVSAScriptable extends SelectionVSAScriptable
   implements CompositeVSAScriptable
{
   /**
    * Create a selection tree viewsheet assembly scriptable.
    * @param box the specified viewsheet sandbox.
    */
   public SelectionTreeVSAScriptable(ViewsheetSandbox box) {
      super(box);

      cellValue = NULL;
   }

   /**
    * Set the cell value.
    * @param val the specified cell value, <tt>NULL</tt> clear cell value.
    */
   @Override
   public void setCellValue(Object val) {
      this.cellValue = val;
   }

   /**
    * Get the cell value.
    * @return the cell value, <tt>NULL</tt> no cell value.
    */
   @Override
   public Object getCellValue() {
      return this.cellValue;
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "SelectionTreeVSA";
   }

   /**
    * Get a named property from the object.
    */
   @Override
   public Object get(String name, Scriptable start) {
      Viewsheet vs = box.getViewsheet();
      VSAssembly vassembly = assembly == null ? null :
         (VSAssembly) vs.getAssembly(assembly);

      if(!(vassembly instanceof SelectionTreeVSAssembly)) {
         return Undefined.instance;
      }

      if(cellValue != NULL && name.equals("value")) {
         return cellValue;
      }

      SelectionTreeVSAssembly tree = (SelectionTreeVSAssembly) vassembly;

      if((name.equals("drillMember") || name.equals("drillMembers")) &&
         tree.getStateCompositeSelectionValue() != null)
      {
         return getDrillData(tree, name);

      }

      return super.get(name, start);
   }

   /**
    * get the DrillData from scripted DrillMember(s) or for ViewSheetSandBox AutoDrill
    */
   public static Object getDrillData(SelectionTreeVSAssembly tree, String name) {
      boolean members = name.equals("drillMembers");
      boolean autoDrill = name.equals("autodrill");

      // drillMember
      if(tree.isIDMode() && !members) {
         return tree.getID();
      }

      DataRef[] refs = tree.getDataRefs();

      if (refs.length > 0) {
         int level = tree.isIDMode() ? refs.length - 1
            : getSelectedLevel(tree.getStateCompositeSelectionValue());
         int lastlevel = Math.min(level + 1, refs.length - 1);

         // drillMember
         if(!members) {

            if (autoDrill) {
               return refs[lastlevel];
            }

            return refs[lastlevel].getName();
         }

         // drillMembers
         String[] fields = new String[lastlevel + 1];

         for(int i = 0; i < fields.length; i++) {
            fields[i] = refs[i].getName();
         }

         return fields;
      }

      //empty tree
      return null;
   }

   /**
    * Get the (max) selected level.
    */
   public static int getSelectedLevel(SelectionValue value) {
      final int[] level = {-1};

      SelectionValueIterator iter = new SelectionValueIterator(value) {
         @Override
         protected void visit(SelectionValue val,
                              List<SelectionValue> parents) throws Exception
         {
            level[0] = Math.max(level[0], val.getLevel());
         }
      };

      try {
         iter.iterate();
      }
      catch(Exception ex) {
         LOG.error("Failed to set the selected level: " + value, ex);
      }

      return level[0];
   }

   /**
    * Add the assembly properties.
    */
   @Override
   protected void addProperties() {
      super.addProperties();

      SelectionTreeVSAssemblyInfo info = getInfo();

      addProperty("dropdown", "getShowType", "setShowType",
                  boolean.class, getClass(), this);
      addProperty("singleSelection", "isSingleSelection", "setSingleSelection",
                  boolean.class, getClass(), this);
      addProperty("singleSelectionLevels","getSingleSelectionLevels", "setSingleSelectionLevels",
                  String[].class, getClass(), this);
      addProperty("selectFirstItemOnLoad", "isSelectFirstItem", "setSelectFirstItem",
                  boolean.class, getClass(), this);
      addProperty("selectedObjects", "getSelectedObjectsArray", "setSelectedObjects",
                  Object[].class, getClass(), this);
      addProperty("drillMember", null);
      addProperty("drillMembers", null);

      addProperty("title", "getTitle", "setTitle",
                  String.class, info.getClass(), info);
      addProperty("sortType", "getSortType", "setSortType",
                  int.class, info.getClass(), info);
      addProperty("submitOnChange", "isSubmitOnChange", "setSubmitOnChange",
                  boolean.class, info.getClass(), info);
      addProperty("wrapping", "isWrapping", "setWrapping", boolean.class,
                  getClass(), this);
      addProperty("suppressBlank", "isSuppressBlankValue",
                  "setSuppressBlankValue", boolean.class, info.getClass(), info);
      addProperty("expandAll", "isExpandAll",
                  "setExpandAll", boolean.class, info.getClass(), info);
      addProperty("value", null);
   }

   /**
    * Indicate whether or not a named property is defined in an object.
    */
   @Override
   public boolean has(String name, Scriptable start) {
      if(!(getVSAssemblyInfo() instanceof SelectionTreeVSAssemblyInfo)) {
         return false;
      }

      if(cellValue != NULL && name.equals("value")) {
         return true;
      }

      return super.has(name, start);
   }

   /**
    * Check if single-selection is enabled.
    * @return true if single-selection is enabled.
    */
   @Override
   public boolean isSingleSelection() {
      return getInfo().isSingleSelection();
   }

   /**
    * Set if single-selection is enabled.
    */
   @Override
   public void setSingleSelection(boolean single) {
      boolean refreshSelectedObjs = single && !getInfo().isSingleSelection();
      getInfo().setSingleSelection(single);

      if(refreshSelectedObjs) {
         Object[] objs = getSelectedObjects();

         if(objs != null && objs.length > 0) {
            setSelectedObjects(objs);
         }
      }
   }

   public String[] getSingleSelectionLevels() {
      if(getInfo().getSingleSelectionLevelNames() == null) {
         return null;
      }

      return getInfo().getSingleSelectionLevelNames().toArray(new String[0]);
   }

   public void setSingleSelectionLevels(String[] levels) {
      if(levels == null) {
         return;
      }

      getInfo().setSingleSelectionLevelNames(Arrays.asList(levels));
   }

   /**
    * Set selected objects.
    * @param values0 the values will be set to the selection objects.
    */
   @Override
   public void setSelectedObjects(Object[] values0) {
      // null should clear selected objects
      if(values0 == null) {
         values0 = new Object[0];
      }

      final Object[] values = new HashSet(Arrays.asList(values0)).toArray();
      Viewsheet vs = box .getViewsheet();
      ViewsheetInfo vInfo = vs.getViewsheetInfo();

      CompositeSelectionValue cvalue = getInfo().getCompositeSelectionValue();
      cvalue = (CompositeSelectionValue) cvalue.clone();
      SelectionTreeVSAssembly vsAssembly = (SelectionTreeVSAssembly) getVSAssembly();

      //fix bug #3983, if viewsheet uses metadata mode and the selection
      //list's data has not been populated yet. we do not set selected objects
      //here. Instead, we save the values into the assembly object and process
      //them later.
      if(!box.isRuntime() && vInfo.isMetadata() && getInfo().isUsingMetaData()) {
         vsAssembly.setScriptSelectedValues(values);
         return;
      }

      final DataRef[] refs = vsAssembly.getDataRefs();
      CompositeSelectionValue cselection = SelectionTreeVSAQuery.createNode(refs[0].getDataType());
      SelectionTreeStateProcessor processor = new SelectionTreeStateProcessor(vsAssembly);
      processor.refreshSingleSelectionState0(cvalue, cselection, values);
      vsAssembly.setCompositeSelectionValue(cvalue);
      vsAssembly.setStateCompositeSelectionValue(cselection);
   }

   /**
    * Get selectied objects.
    * @return the selected objects in the selection list.
    */
   @Override
   public Object[] getSelectedObjects() {
      List list = ((SelectionTreeVSAssembly) getVSAssembly()).getSelectedObjects();
      return list.toArray(new Object[0]);
   }

   /**
    * Get the show type.
    * @return the show type.
    */
   public boolean getShowType() {
      return getInfo().getShowType() == 1;
   }

   /**
    * Set the show type.
    * @param type the show type.
    */
   public void setShowType(boolean type) {
      Dimension dim = getFixSize(getInfo(), getInfo().getListHeight());
      getInfo().setPixelSize(new Dimension(dim.width, type ? AssetUtil.defh : dim.height));
      getInfo().setShowType(type ? 1 : 0);
   }

   /**
    * Get Fields.
    */
   @Override
   public Object[] getFields() {
      DataRef[] datarefs = getInfo().getDataRefs();
      List<String> refName = new ArrayList<>();

      for(DataRef dataref : datarefs) {
         if(dataref instanceof ColumnRef) {
            refName.add(dataref.getAttribute());
         }
      }

      return refName.toArray(new String[0]);
   }

   /**
    * Set Fields.
    */
   @Override
   public void setFields(Object[] fields) {
      if(fields.length > 0) {
         DataRef[] dataRefs = getInfo().getDataRefs();
         boolean refreshFlag = false;

         if(dataRefs.length == fields.length) {
            for(int i = 0; i < dataRefs.length; i++) {
               ColumnRef colref = (ColumnRef) dataRefs[i];

               if(colref.getAttribute() == null ||
                  !(colref.getAttribute()).equals((String) fields[i]))
               {
                  refreshFlag = true;
                  break;
               }
            }
         }
         else {
            refreshFlag = true;
         }

         if(refreshFlag) {
            ArrayList<DataRef> secList = new ArrayList<>();

            for(Object field:fields) {
               ColumnRef colref = new ColumnRef();
               colref.setDataRef(new AttributeRef((String) field));
               secList.add(colref);
            }

            DataRef[] selection = secList.toArray(new DataRef[0]);
            getInfo().setDataRefs(selection);
         }
      }
   }

   /**
    * Get the assembly info of current selection tree.
    */
   private SelectionTreeVSAssemblyInfo getInfo() {
      if(getVSAssemblyInfo() instanceof SelectionTreeVSAssemblyInfo) {
         return (SelectionTreeVSAssemblyInfo) getVSAssemblyInfo();
      }

      return new SelectionTreeVSAssemblyInfo();
   }

   private Object cellValue;
   private static final Logger LOG =
      LoggerFactory.getLogger(SelectionTreeVSAScriptable.class);
}
