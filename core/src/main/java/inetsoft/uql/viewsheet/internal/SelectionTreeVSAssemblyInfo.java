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
package inetsoft.uql.viewsheet.internal;

import inetsoft.report.TableDataPath;
import inetsoft.report.internal.Common;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.AbstractDataRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.*;
import inetsoft.util.css.CSSAttr;
import inetsoft.util.css.CSSConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.*;
import java.util.*;

/**
 * SelectionTreeVSAssemblyInfo, the assembly info of a selection list assembly.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class SelectionTreeVSAssemblyInfo extends SelectionBaseVSAssemblyInfo {
   /**
    * Mode "Column".
    */
   public static final int COLUMN = 1;
   /**
    * Mode "ID".
    */
   public static final int ID = 2;

   /**
    * Constructor.
    */
   public SelectionTreeVSAssemblyInfo() {
      super();
      setTitleValue("SelectionTree");
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public DataRef[] getDataRefs() {
      return refs;
   }

   /**
    * Set the data refs.
    * @param nrefs the specified data nrefs.
    */
   public void setDataRefs(DataRef[] nrefs) {
      setDataRefs(nrefs, false);
   }

   public void setDataRefs(DataRef[] nrefs, boolean hasSyncLevels) {
      fixFormatInfo(nrefs);

      if(!hasSyncLevels) {
         syncSingleSelectionLevels(nrefs);
      }

      this.refs = nrefs;
   }

   @Override
   protected void copySingleLevelsInfo(SelectionVSAssemblyInfo sinfo) {
      if(Tool.equals(refs, sinfo.getDataRefs()) ||
         !Tool.equals(sinfo.getSingleSelectionLevels(), getSingleSelectionLevels()))
      {
         super.copySingleLevelsInfo(sinfo);
         return;
      }

      syncSingleSelectionLevels(sinfo.getDataRefs());
   }

   private void syncSingleSelectionLevels(DataRef[] nrefs) {
      List<Integer> singleSelectionLevels = originSingleSelectionLevels();

      if(CollectionUtils.isEmpty(singleSelectionLevels)) {
         return;
      }

      if(nrefs == null || nrefs.length < 1) {
         setSingleSelectionLevels(null);
         return;
      }

      List<DataRef> oldRefs = Arrays.asList(refs);
      List<DataRef> dataRefs = Arrays.asList(nrefs);
      List<Integer> willDeleteLevels = new ArrayList<>();
      Map<Integer, Integer> updateLevels = new HashMap<>(); // old level --> new level

      // check status
      for(Integer level : singleSelectionLevels) {
         DataRef currentRef = oldRefs.get(level);
         int newLevel = dataRefs.indexOf(currentRef);

         if(level >= refs.length || newLevel < 0) {
            willDeleteLevels.add(level);
         }

         if(level != newLevel && newLevel >= 0) {
            updateLevels.put(level, newLevel);
         }
      }

      // process removed level
      for(Integer level : willDeleteLevels) {
         removeSingleSelectionLevel(level);
      }

      // process update level
      for(Map.Entry<Integer, Integer> updateLevel : updateLevels.entrySet()) {
         if(removeSingleSelectionLevel(updateLevel.getKey())) {
            addSingleSelectionLevel(updateLevel.getValue());
         }
      }
   }

   /**
    * Fix the new format.
    */
   private void fixFormatInfo(DataRef[] nrefs) {
      DataRef[] orefs = this.refs;
      Map<String, TableDataPath> opaths = new HashMap<>();
      Map<TableDataPath, VSCompositeFormat> nfmts = new HashMap<>();

      // put the data path of existing refs into opaths
      for(int i = 0; i < orefs.length; i++) {
         int olevel = i < orefs.length - 1 ? i : -1;
         int otype = i < orefs.length - 1 ? TableDataPath.GROUP_HEADER : TableDataPath.DETAIL;
         TableDataPath opath = new TableDataPath(olevel, otype);
         String col = orefs[i].getName();
         opaths.put(col, opath);
      }

      // get the formats for the new refs and add to nfmts
      for(int i = 0; i < nrefs.length; i++) {
         String col = nrefs[i].getName();

         if(opaths.containsKey(col)) {
            TableDataPath opath = opaths.get(col);
            VSCompositeFormat ofmt = getFormatInfo().getFormat(opath, false);

            int nlevel = i < nrefs.length - 1 ? i : -1;
            int ntype = i < nrefs.length - 1 ? TableDataPath.GROUP_HEADER :
               TableDataPath.DETAIL;

            nfmts.put(new TableDataPath(nlevel, ntype), ofmt);
         }
      }

      FormatInfo ninfo = getFormatInfo().clone();

      // set the formats from nfmts
      for(Map.Entry<TableDataPath, VSCompositeFormat> entry : nfmts.entrySet()) {
         ninfo.setFormat(entry.getKey(), entry.getValue());
      }

      for(TableDataPath path : ninfo.getPaths()) {
         VSCompositeFormat cfmt = ninfo.getFormat(path);

         if(mode == COLUMN) {
            if(path.getType() == TableDataPath.GROUP_HEADER ||
               path.getType() == TableDataPath.DETAIL)
            {
               int level = path.getLevel() == -1 ? nrefs.length - 1 : path.getLevel();
               cfmt.getCSSFormat().setCSSAttributes(new CSSAttr("level", level + ""));
            }
         }
         else {
            cfmt.getCSSFormat().setCSSAttributes(new HashMap<>());
         }
      }

      ninfo.setFormat(OBJECTPATH, getFormatInfo().getFormat(OBJECTPATH));
      setFormatInfo(ninfo);
   }

   /**
    * Get the composite selection value.
    * @return the composite selection value.
    */
   public CompositeSelectionValue getCompositeSelectionValue() {
      return value;
   }

   /**
    * Set the composite selection value.
    * @param value composite selection value.
    */
   public void setCompositeSelectionValue(CompositeSelectionValue value) {
      this.value = value;
   }

   /**
    * Set the mode.
    */
   public void setMode(int mode) {
      this.mode = mode;
   }

   /**
    * Get the mode.
    */
   public int getMode() {
      return mode;
   }

   /**
    * Get the parent column.
    */
   public String getParentID() {
      Object obj = parentValue.getRuntimeValue(true);

      if(obj instanceof String) {
         return (String) obj;
      }

      return null;
   }

   /**
    * Set the parent column.
    */
   public void setParentID(String parent) {
      parentValue.setRValue(parent);
   }

   /**
    * Get the parent column.
    */
   public String getParentIDValue() {
      return parentValue.getDValue();
   }

   /**
    * Set the parent column.
    */
   public void setParentIDValue(String parent) {
      parentValue.setDValue(parent);
   }

   /**
    * Get the child column.
    */
   public String getID() {
      Object obj = childValue.getRuntimeValue(true);

      if(obj instanceof String) {
         return (String) obj;
      }

      return null;
   }

   /**
    * Set the child column.
    */
   public void setID(String child) {
      childValue.setRValue(child);
   }

   /**
    * Get the child column.
    */
   public String getIDValue() {
      return childValue.getDValue();
   }

   /**
    * Set the child column.
    */
   public void setIDValue(String child) {
      childValue.setDValue(child);
   }

   /**
    * Get the label column.
    */
   public String getLabel() {
      Object obj = labelValue.getRuntimeValue(true);

      if(obj instanceof String) {
         return (String) obj;
      }

      return null;
   }

   /**
    * Set the label column.
    */
   public void setLabel(String label) {
      labelValue.setRValue(label);
   }

   /**
    * Get the label column.
    */
   public String getLabelValue() {
      return labelValue.getDValue();
   }

   /**
    * Set the label column.
    */
   public void setLabelValue(String label) {
      labelValue.setDValue(label);
   }

   /**
    * Check if select children.
    */
   public boolean isSelectChildren() {
      return selectChildren;
   }

   /**
    * Set whether select children.
    */
   public void setSelectChildren(boolean selectChildren) {
      this.selectChildren = selectChildren;
   }

   /**
    * Check if all branches are expanded on initial display.
    */
   public boolean isExpandAll() {
      return expandAll;
   }

   /**
    * Set if all branches are expanded on initial display.
    */
   public void setExpandAll(boolean expandAll) {
      this.expandAll = expandAll;
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      writer.print(" mode=\"" + mode + "\"");
   }

   /**
    * Parse attributes.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseAttributes(Element elem) {
      super.parseAttributes(elem);

      String text = Tool.getAttribute(elem, "mode");
      mode = text == null ? COLUMN : Integer.parseInt(text);
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(refs != null && refs.length > 0) {
         writer.print("<dataRefs>");

         for(DataRef ref : refs) {
            ref.writeXML(writer);
         }

         writer.println("</dataRefs>");
      }

      if(value != null) {
         CompositeSelectionValue root = value;
         String search = getSearchString();

         if(search != null && search.length() > 0) {
            root = root.findAll(search, false);
         }

         // demand loading
         if(root.getValueCount() > 500) {
            root.writeXML(writer, 1, null);
         }
         else {
            root.writeXML(writer);
         }
      }

      writer.print("<selectChildren>");
      writer.print("<![CDATA[" + selectChildren + "]]>");
      writer.println("</selectChildren>");

      writer.print("<expandAll>");
      writer.print("<![CDATA[" + expandAll + "]]>");
      writer.println("</expandAll>");

      if(getParentID() != null && getParentID().length() > 0) {
         writer.print("<parentID>");
         writer.print("<![CDATA[" + getParentID() + "]]>");
         writer.println("</parentID>");
      }

      if(parentValue.getDValue() != null) {
         writer.print("<parentValue>");
         writer.print("<![CDATA[" + parentValue.getDValue() + "]]>");
         writer.println("</parentValue>");
      }

      if(getID() != null && getID().length() > 0) {
         writer.print("<id>");
         writer.print("<![CDATA[" + getID() + "]]>");
         writer.println("</id>");
      }

      if(childValue.getDValue() != null) {
         writer.print("<childValue>");
         writer.print("<![CDATA[" + childValue.getDValue() + "]]>");
         writer.println("</childValue>");
      }

      if(getLabel() != null && getLabel().length() > 0) {
         writer.print("<label>");
         writer.print("<![CDATA[" + getLabel() + "]]>");
         writer.println("</label>");
      }

      if(labelValue.getDValue() != null) {
         writer.print("<labelValue>");
         writer.print("<![CDATA[" + labelValue.getDValue() + "]]>");
         writer.println("</labelValue>");
      }
   }

   /**
    * Write selectionList as binary.
    */
   @Override
   public void writeData(DataOutputStream output) throws IOException {
      SelectionTreeVSAssemblyInfo info = (SelectionTreeVSAssemblyInfo) this.clone();
      info.value = null;
      XMLTool.writeXMLSerializableAsData(output, info);

      if(value != null) {
         CompositeSelectionValue root = value;
         String search = getSearchString();

         if(search != null && search.length() > 0) {
            root = root.findAll(search, false);
         }

         // demand loading
         if(root.getValueCount() > 500) {
            root.writeData(output, 1, null);
         }
         else {
            root.writeData(output);
         }
      }
      else {
         (new CompositeSelectionValue()).writeData(output);
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element refsNode = Tool.getChildNodeByTagName(elem, "dataRefs");

      if(refsNode != null) {
         NodeList refsList =
            Tool.getChildNodesByTagName(refsNode, "dataRef");

         if(refsList != null && refsList.getLength() > 0) {
            refs = new DataRef[refsList.getLength()];

            for(int i = 0; i < refsList.getLength(); i++) {
               refs[i] = AbstractDataRef.createDataRef(
                  (Element) refsList.item(i));
            }
         }
      }

      Element snode =
         Tool.getChildNodeByTagName(elem, "VSValue");

      if(snode != null) {
         value = new CompositeSelectionValue();
         value.parseXML(snode);
      }

      Element node = Tool.getChildNodeByTagName(elem, "parentValue");
      parentValue.setDValue(Tool.getValue(node));

      node = Tool.getChildNodeByTagName(elem, "childValue");
      childValue.setDValue(Tool.getValue(node));

      node = Tool.getChildNodeByTagName(elem, "labelValue");
      labelValue.setDValue(Tool.getValue(node));

      node = Tool.getChildNodeByTagName(elem, "selectChildren");
      selectChildren = "true".equals(Tool.getValue(node));

      node = Tool.getChildNodeByTagName(elem, "expandAll");
      expandAll = "true".equals(Tool.getValue(node));
   }

   /**
    * Clone this object.
    * @param shallow <tt>true</tt> to perform shallow clone,
    * <tt>false</tt> to perform deep clone.
    * @return the cloned object.
    */
   @Override
   public SelectionTreeVSAssemblyInfo clone(boolean shallow) {
      try {
         SelectionTreeVSAssemblyInfo info = (SelectionTreeVSAssemblyInfo) super.clone(shallow);

         if(!shallow) {
            if(refs != null && refs.length > 0) {
                  DataRef[] nrefs = new DataRef[refs.length];

               for(int i = 0; i < refs.length; i++) {
                  nrefs[i] = (DataRef) refs[i].clone();
               }

               info.refs = nrefs;
            }

            if(value != null) {
               info.value = (CompositeSelectionValue) value.clone();
            }

            if(parentValue != null) {
               info.parentValue = (DynamicValue) parentValue.clone();
            }

            if(childValue != null) {
               info.childValue = (DynamicValue) childValue.clone();
            }

            if(labelValue != null) {
               info.labelValue = (DynamicValue) labelValue.clone();
            }

            info.mode = mode;
            info.selectChildren = selectChildren;
            info.expandAll = expandAll;
         }

         return info;
      }
      catch(Exception ex) {
         LOG.error(
                     "Failed to clone SelectionTreeVSAssemblyInfo", ex);
      }

      return null;
   }

   /**
    * Copy the input data part assembly info.
    * @param info the specified viewsheet assembly info.
    * @return new hint.
    */
   @Override
   protected int copyInputDataInfo(VSAssemblyInfo info, int hint) {
      hint = super.copyInputDataInfo(info, hint);
      SelectionTreeVSAssemblyInfo sinfo = (SelectionTreeVSAssemblyInfo) info;

      if(!Tool.equals(refs, sinfo.refs)) {
         setDataRefs(sinfo.refs, true);
         hint |= VSAssembly.INPUT_DATA_CHANGED;
         hint |= VSAssembly.BINDING_CHANGED;
      }

      if(!Tool.equals(parentValue, sinfo.parentValue)) {
         parentValue = sinfo.parentValue;
         hint |= VSAssembly.INPUT_DATA_CHANGED;
         hint |= VSAssembly.BINDING_CHANGED;
      }

      if(!Tool.equals(childValue, sinfo.childValue)) {
         childValue = sinfo.childValue;
         hint |= VSAssembly.INPUT_DATA_CHANGED;
         hint |= VSAssembly.BINDING_CHANGED;
      }

      if(!Tool.equals(labelValue, sinfo.labelValue)) {
         labelValue = sinfo.labelValue;
         hint |= VSAssembly.INPUT_DATA_CHANGED;
      }

      if(!Tool.equals(mode, sinfo.mode)) {
         mode = sinfo.mode;
         hint |= VSAssembly.INPUT_DATA_CHANGED;
      }

      if(!Tool.equals(selectChildren, sinfo.selectChildren)) {
         selectChildren = sinfo.selectChildren;
         hint |= VSAssembly.INPUT_DATA_CHANGED;
      }

      if(!Tool.equals(expandAll, sinfo.expandAll)) {
         expandAll = sinfo.expandAll;
         hint |= VSAssembly.INPUT_DATA_CHANGED;
      }

      return hint;
   }

   /**
    * Get the dynamic property values. Dynamic properties are properties that
    * can be a variable or a script.
    */
   @Override
   public List<DynamicValue> getDynamicValues() {
      List<DynamicValue> list = super.getDynamicValues();
      list.add(parentValue);
      list.add(childValue);
      list.add(labelValue);

      return list;
   }

   /**
    * Reset runtime values.
    */
   @Override
   public void resetRuntimeValues() {
      super.resetRuntimeValues();

      parentValue.setRValue(null);
      childValue.setRValue(null);
      labelValue.setRValue(null);
   }

   /**
    * Gets selections data for SelectionList, SelectionTree, TimeSlider.
    * @return selections.
    */
   @Override
   public DataSerializable getSelections() {
      return value;
   }

   /**
    * Set the default vsobject format.
    */
   @Override
   protected void setDefaultFormat(boolean border) {
      super.setDefaultFormat(border);

      TableDataPath datapath = new TableDataPath(-1, TableDataPath.DETAIL);
      VSCompositeFormat format = getFormatInfo().getFormat(datapath);

      for(int i = 0; i < 5; i++) {
         datapath = new TableDataPath(i, TableDataPath.GROUP_HEADER);
         getFormatInfo().setFormat(datapath, format.clone());
      }
   }

   /**
    * Get the object css default type.
    */
   @Override
   public String getObjCSSType() {
      return CSSConstants.SELECTION_TREE;
   }

   /**
    * Recursively visit composite children.
    * @param csv the tree to be visited.
    * @param dispList the vector of the "lines" to be displayed .
    */
   public void visitCompositeChild(CompositeSelectionValue csv, List<SelectionValue> dispList) {
      visitCompositeChild(csv, dispList, false);
   }

   /**
    * Recursively visit composite children.
    * @param csv the tree to be visited.
    * @param dispList the vector of the "lines" to be displayed .
    */
   public void visitCompositeChild(CompositeSelectionValue csv, List<SelectionValue> dispList, boolean isExport) {
      if(csv == null) {
         return;
      }

      boolean parentVisible = isValueVisible(csv, isExport);

      if(mode == COLUMN || mode == ID && parentVisible) {
         dispList.add(csv);
      }

      int bufferedParentPosition = dispList.size(); // buffered index

      SelectionList sl = csv.getSelectionList();

      if(sl == null) {
         return;
      }

      sl.sort(VSUtil.getSortType(this));
      SelectionValue[] values = sl.getSelectionValues();

      for(SelectionValue selectionValue : values) {
         if(selectionValue instanceof CompositeSelectionValue) {
            // visit child if it is a tree
            visitCompositeChild((CompositeSelectionValue) selectionValue, dispList, isExport);
         }
         else {
            // a normal leaf
            if(selectionValue != null && isValueVisible(selectionValue, isExport)) {
               dispList.add(selectionValue);
            }
         }
      }

      int newSize = dispList.size();

      if((!parentVisible) && (bufferedParentPosition == newSize) && mode == COLUMN) {
         dispList.remove(bufferedParentPosition - 1);
      }
   }

   /**
    * Check if a value should be in the exported view.
    */
   public boolean isValueVisible(SelectionValue value) {
      return !value.isExcluded() && (value.isSelected() || value.isIncluded());
   }

   /**
    * Check if a value should be in the exported view.
    */
   public boolean isValueVisible(SelectionValue value, boolean isExport) {
      return isExport ? !value.isExcluded() : isValueVisible(value);
   }

   /**
    * Update the info to fill in runtime value.
    * @param vs the specified viewsheet.
    * @param columns the specified column selection.
    */
   @Override
   public void update(Viewsheet vs, ColumnSelection columns) throws Exception {
      super.update(vs, columns);

      if(mode == COLUMN || vs == null) {
         return;
      }

      Worksheet ws = vs.getBaseWorksheet();

      if(ws == null) {
         return;
      }

      TableAssembly table = (TableAssembly) ws.getAssembly(getTableName());
      ColumnSelection selection = table == null ? new ColumnSelection() :
         (ColumnSelection) table.getColumnSelection(true).clone();

      if(selection == null || selection.isEmpty()) {
         return;
      }

      selection = VSUtil.getVSColumnSelection(selection);
      // @by stephenwebster, For Bug #5922
      // I adjusted the code below to create a distinct list of data refs
      // including going through the old data ref list parsed in from storage.
      // I also fixed the existInRefs to compare against the list that is
      // being built here instead of just the old list since it would not take
      // into consideration the fact that the parent, id, label may be the same
      // ref.
      List<DataRef> list = new ArrayList<>();
      DataRef[] orefs = getDataRefs();

      for(DataRef oref : orefs) {
         if(!existInRefs(oref.getName(), list)) {
            list.add(oref);
         }
      }

      restoreRefs(selection, list, getParentID());
      restoreRefs(selection, list, getID());
      restoreRefs(selection, list, getLabel());

      DataRef[] nrefs = list.toArray(new DataRef[] {});
      setDataRefs(nrefs);
   }

   /**
    * Restore data refs.
    */
   private void restoreRefs(ColumnSelection columns, List<DataRef> list,
                            String name)
   {
      if(!existInRefs(name, list)) {
         DataRef ref = columns.getAttribute(name);

         if(ref != null) {
            list.add(ref);
         }
      }
   }

   /**
    * Check if the id exists in data refs.
    */
   private boolean existInRefs(String id, List<DataRef> list) {
      for(DataRef dataRef : list) {
         if(dataRef != null && Tool.equals(dataRef.getName(), id)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Get the height of each row.
    * @return list of row heights.
    */
   public List<Double> getRowHeights() {
      List<SelectionValue> dispList = new ArrayList<>();
      List<Double> rowHeights = new ArrayList<>();
      visitCompositeChild(getCompositeSelectionValue(), dispList, true);
      double cellWidth = getPixelSize().width;

      if(isShowBar() && getMeasure() != null) {
         cellWidth -= getBarSize();
      }

      if(isShowText() && getMeasure() != null) {
         cellWidth -= getMeasureSize();
      }

      for(int i = 1; i < dispList.size(); i++) {
         SelectionValue svalue = dispList.get(i);
         VSCompositeFormat format = svalue.getFormat();
         double cellHeight = format == null || !format.isWrapping() ? getCellHeight() :
            Common.getWrapTextHeight(svalue.getLabel(), cellWidth,
            format.getFont(), format.getAlignment());
         rowHeights.add(cellHeight);
      }

      return rowHeights;
   }

   // input data
   private DataRef[] refs = {};
   // runtime data
   private CompositeSelectionValue value;
   private DynamicValue parentValue = new DynamicValue(null);
   private DynamicValue childValue = new DynamicValue(null);
   private DynamicValue labelValue = new DynamicValue(null);
   private int mode = COLUMN;
   private boolean selectChildren = false;
   private boolean expandAll;

   private static final Logger LOG = LoggerFactory.getLogger(SelectionTreeVSAssemblyInfo.class);
}
