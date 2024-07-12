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
package inetsoft.report.script.viewsheet;

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.SelectionListVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.util.Tool;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Undefined;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * The selection list viewsheet assembly scriptable in viewsheet scope.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class SelectionListVSAScriptable extends SelectionVSAScriptable
   implements CompositeVSAScriptable
{
   /**
    * Create a selection list viewsheet assembly scriptable.
    * @param box the specified viewsheet sandbox.
    */
   public SelectionListVSAScriptable(ViewsheetSandbox box) {
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
      return "SelectionListVSA";
   }

   /**
    * Get a named property from the object.
    */
   @Override
   public Object get(String name, Scriptable start) {
      Viewsheet vs = box.getViewsheet();
      VSAssembly vassembly = assembly == null ? null :
         (VSAssembly) vs.getAssembly(assembly);

      if(!(vassembly instanceof SelectionListVSAssembly)) {
         return Undefined.instance;
      }

      if(cellValue != NULL && name.equals("value")) {
         return cellValue;
      }

      return super.get(name, start);
   }

   /**
    * Add the assembly properties.
    */
   @Override
   protected void addProperties() {
      super.addProperties();

      SelectionListVSAssemblyInfo info = getInfo();

      addProperty("dropdown", "getShowType", "setShowType",
                  boolean.class, getClass(), this);
      addProperty("singleSelection", "isSingleSelection", "setSingleSelection",
                  boolean.class, getClass(), this);
      addProperty("selectFirstItemOnLoad", "isSelectFirstItem", "setSelectFirstItem",
                  boolean.class, getClass(), this);
      addProperty("selectedObjects", "getSelectedObjectsArray", "setSelectedObjects",
                  Object[].class, getClass(), this);

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
      addProperty("value", null);
   }

   /**
    * Indicate whether or not a named property is defined in an object.
    */
   @Override
   public boolean has(String name, Scriptable start) {
      if(!(getVSAssemblyInfo() instanceof SelectionListVSAssemblyInfo)) {
         return false;
      }

      if(cellValue != NULL && name.equals("value")) {
         return true;
      }

      return super.has(name, start);
   }

   @Override
   public void setSingleSelection(boolean single) {
      boolean refreshSelectedObjs = single && !isSingleSelection();
      getInfo().setSingleSelection(single);

      if(refreshSelectedObjs) {
         setSelectedObjects(getSelectedObjects());
      }
   }

   /**
    * Set selectied objects.
    * @param values the values will be set to the selection objects.
    */
   @Override
   public void setSelectedObjects(Object[] values) {
      // null should clear the list
      if(values == null) {
         values = new Object[0];
      }

      Viewsheet vs = box.getViewsheet();
      ViewsheetInfo vInfo = vs.getViewsheetInfo();
      SelectionListVSAssembly vsAssembly =
         (SelectionListVSAssembly) getVSAssembly();
      SelectionList list = getInfo().getSelectionList();

      //fix bug #3983, if viewsheet uses metadata mode and the selection
      //list's data has not been populated yet. we do not set selected objects
      //here. Instead, we save the values into the assembly object and process
      //them later.
      // @by stephenwebster, For Bug #16411
      // Save the selections for processing when list is ready.
      if(list == null || vInfo.isMetadata() || getInfo().isUsingMetaData()) {
         if(vsAssembly.getSelectionListInfo().isSingleSelection() &&
            (values == null || values.length == 0) && list.getSelectionValueCount() > 0)
         {
            SelectionValue selectionValue = list.getSelectionValue(0);

            if(selectionValue != null) {
               selectionValue.setState(selectionValue.getState() | SelectionValue.STATE_SELECTED);
               values = new Object[] { list.getSelectionValue(0) };
            }
         }

         vsAssembly.setScriptSelectedValues(values);
         return;
      }

      list = (SelectionList) list.clone();
      SelectionValue[] vals = list.getSelectionValues();
      boolean hasSelected = false;

      for(int i = 0; i < vals.length; i++) {
         int state = vals[i].getState();
         vals[i].setState(state - state & SelectionValue.STATE_SELECTED);

         if(values.length == 0 && isSingleSelection()  && !hasSelected) {
            vals[i].setState(state | SelectionValue.STATE_SELECTED);
            hasSelected = true;
         }

         for(int j = 0; j < values.length; j++) {
            String value0 = (String) Tool.getData(String.class, values[j]);

            if(hasSelected) {
               break;
            }

            if(value0.equals(vals[i].getValue())) {
               vals[i].setState(state | SelectionValue.STATE_SELECTED);

               if(isSingleSelection()) {
                  hasSelected = true;
               }
            }
         }
      }

      vsAssembly.setStateSelectionList(list);
      vsAssembly.setSelectionList((SelectionList) list.clone());
   }

   /**
    * Get selectied objects.
    * @return the selected objects in the selection list.
    */
   @Override
   public Object[] getSelectedObjects() {
      List list = ((SelectionListVSAssembly) getVSAssembly()).getSelectedObjects();
      return list.toArray(new Object[0]);
   }

   public boolean getShowType() {
      return getInfo().getShowType() == 1;
   }

   public void setShowType(boolean type) {
      Dimension dim = getFixSize(getInfo(), getInfo().getListHeight());
      getInfo().setPixelSize(new Dimension(dim.width, type ? AssetUtil.defh : dim.height));
      getInfo().setShowType(type ? 1 : 0);
   }

   public void setShowTypeValue(boolean type) {
      Dimension dim = getFixSize(getInfo(), getInfo().getListHeight());
      getInfo().setPixelSize(new Dimension(dim.width, type ? AssetUtil.defh : dim.height));
      getInfo().setShowTypeValue(type ? 1 : 0);
   }

   /**
    * Get Fields.
    */
   @Override
   public Object[] getFields() {
      if(getInfo() instanceof SelectionListVSAssemblyInfo) {
         ColumnRef dataref = (ColumnRef) getInfo().getDataRef();

         if(dataref instanceof ColumnRef) {
            List<String> refName = new ArrayList<>();
            refName.add(dataref.getAttribute());

            return refName.toArray(new String[0]);
         }

         return null;
      }

      return null;
   }

   /**
    * Set Fields.
    */
   @Override
   public void setFields(Object[] fields) {
      DataRef dataref = getInfo().getDataRef();

      if(fields.length > 0) {
	 ColumnRef colref = (ColumnRef) dataref;

	 if(dataref != null) {
	    if(colref.getAttribute() != null &&
	       (colref.getAttribute()).equals((String) fields[0]))
	    {
	       return;
	    }
	 }
	 else {
	    colref = new ColumnRef();
         }

         colref.setDataRef(new AttributeRef((String) fields[0]));
         getInfo().setDataRef((DataRef) colref);
      }
   }

   /**
    * Get the assembly info of current selection list.
    */
   private SelectionListVSAssemblyInfo getInfo() {
      if(getVSAssemblyInfo() instanceof SelectionListVSAssemblyInfo) {
         return (SelectionListVSAssemblyInfo) getVSAssemblyInfo();
      }

      return new SelectionListVSAssemblyInfo();
   }

   /**
    * Set the size.
    * @param dim the dimension of size.
    */
   @Override
   public void setSize(Dimension dim) {
      VSAssemblyInfo info = getVSAssemblyInfo();

      if(info == null) {
         LOG.warn(
            "Could not set the selection list size, the assembly info is null");
         return;
      }

      if(dim.height <= 0 || dim.width <= 0) {
         LOG.warn(
            "Could not set the selection list size, invalid dimension: " + dim);
         return;
      }

      Viewsheet vs = box.getViewsheet();
      Dimension opsize = vs.getPixelSize(info);
      super.setSize(dim);
      Dimension psize = vs.getPixelSize(info);
      SelectionListVSAssemblyInfo sinfo = getInfo();
      int ocol = sinfo.getColumnCount();
      int ncol = ocol;
      int colw = opsize.width / ncol;

      if(psize.width > opsize.width) {
         ncol = Math.max(1, psize.width / colw);
      }
      else {
         ncol = Math.min(ncol, sinfo.getPixelSize().width / colw);
      }

      // when enlarging a selection list, the more likely intent is to
      // make it wider rather than changing it to multi-column, so we
      // only change the column count if it's already multi-column or
      // if the width is drastically wider than before
      if(ocol > 1 || ncol > 3) {
         sinfo.setColumnCount(ncol);
      }
   }

   private Object cellValue;

   private static final Logger LOG =
      LoggerFactory.getLogger(SelectionListVSAScriptable.class);
}
