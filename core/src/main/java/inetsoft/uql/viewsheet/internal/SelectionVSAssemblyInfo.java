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
package inetsoft.uql.viewsheet.internal;

import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.DataSerializable;
import inetsoft.util.Tool;
import org.springframework.util.CollectionUtils;
import org.w3c.dom.*;

import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

import static inetsoft.uql.asset.Assembly.CONCATENATED_SELECTION;

/**
 * SelectionVSAssemblyInfo, the assembly info of a selection assembly.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public abstract class SelectionVSAssemblyInfo extends VSAssemblyInfo {
   /**
    * Title type name.
    */
   public static final int TITLE_TYPE_NAME = 0;

   /**
    * Title type value.
    */
   public static final int TITLE_TYPE_VALUE = 1;

   /**
    * Show type drop list.
    */
   public static final int LIST_SHOW_TYPE = 0;

   /**
    * Show type not drop list, title only.
    */
   public static final int DROPDOWN_SHOW_TYPE = 1;

   /**
    * Constructor.
    */
   public SelectionVSAssemblyInfo() {
      super();
      additionalTables = Collections.emptyList();
   }

   /**
    * Get the dynamic property values for output properties.
    * @return the dynamic values.
    */
   @Override
   public List<DynamicValue> getOutputDynamicValues() {
      List<DynamicValue> list = super.getOutputDynamicValues();
      list.add(getEnabledDynamicValue());
      return list;
   }

   /**
    * Get the name of the target table.
    * @return the name of the target table.
    */
   public String getTableName() {
      final String tableName;

      if(isSelectionUnion()) {
         final StringJoiner stringJoiner = new StringJoiner("_", CONCATENATED_SELECTION, "");
         stringJoiner.add(firstTable);
         additionalTables.forEach(stringJoiner::add);
         tableName = stringJoiner.toString();
      }
      else {
         tableName = getFirstTableName();
      }

      return tableName;
   }

   /**
    * Set the name of the target table.
    * @param table the specified name of the target table.
    */
   public void setTableName(String table) {
      this.firstTable = table;
      this.setAdditionalTableNames(Collections.emptyList());
   }

   /**
    * Get the name of the first table.
    * @return the name of the first table
    */
   public String getFirstTableName() {
      return firstTable;
   }

   /**
    * Set the name of the first table
    * @param firstTable the specified name of the first table
    */
   public void setFirstTableName(String firstTable) {
      this.firstTable = firstTable;
   }

   /**
    * Set the names of the additional tables.
    *
    * @param additionalTables the names of the additional tables
    */
   public void setAdditionalTableNames(List<String> additionalTables) {
      this.additionalTables = additionalTables;
   }

   /**
    * @return the names of the additional tables
    */
   public List<String> getAdditionalTableNames() {
      return additionalTables;
   }

   /**
    * Check binding source type.
    */
   public int getSourceType() {
      return sourceType;
   }

   /**
    * Set binding source type.
    * @param sourceType types defined in SourceInfo.
    */
   public void setSourceType(int sourceType) {
      this.sourceType = sourceType;
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      writer.print(" hitsMV=\"" + hitsMV + "\"");
      writer.print(" adhoc=\"" + createdAdhoc + "\"");
      writer.print(" submitOnChange=\"" + isSubmitOnChange() + "\"");
      writer.print(" submitOnChangeValue=\"" + submitValue.getDValue() + "\"");
      writer.print(" singleSelection=\"" + isSingleSelection() + "\"");
      writer.print(" singleSelectionValue=\"" + singleValue.getDValue() + "\"");
      writer.print(" sourceType=\"" + sourceType + "\"");
      writer.print(" mixedSingleSelection=\"" + mixedSingleSelection + "\"");
      writer.print(" selectFirstItem=\"" + selectFirstItem.getDValue() + "\"");
   }

   /**
    * Parse attributes.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseAttributes(Element elem) {
      super.parseAttributes(elem);

      createdAdhoc = "true".equals(Tool.getAttribute(elem, "adhoc"));
      submitValue.setDValue(getAttributeStr(elem, "submitOnChange", "true"));
      selectFirstItem.setDValue(getAttributeStr(elem, "selectFirstItem", "false"));
      String prop = Tool.getAttribute(elem, "singleSelection");

      if(prop != null) {
         singleValue.setRValue(Boolean.valueOf(prop));
      }

      singleValue.setDValue(getAttributeStr(elem, "singleSelection", "false"));
      String sstr = Tool.getAttribute(elem, "sourceType");

      if(sstr != null) {
         sourceType = Integer.parseInt(sstr);
      }

      mixedSingleSelection = "true".equals(Tool.getAttribute(elem, "mixedSingleSelection"));
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(firstTable != null) {
         writer.print("<firstTable>");
         writer.print("<![CDATA[" + firstTable + "]]>");
         writer.println("</firstTable>");
      }

      if(additionalTables != null) {
         writer.print("<additionalTables>");

         for(String table : additionalTables) {
            writer.print("<table>");
            writer.print("<![CDATA[" + table + "]]>");
            writer.println("</table>");
         }

         writer.print("</additionalTables>");
      }

      if(singleSelectionLevels != null) {
         writer.print("<singleSelectionLevels>");

         for(Integer level : singleSelectionLevels) {
            writer.print("<level>");
            writer.print("<![CDATA[" + level + "]]>");
            writer.println("</level>");
         }

         writer.print("</singleSelectionLevels>");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element firstTableNode = Tool.getChildNodeByTagName(elem, "firstTable");

      // TODO: this is only for dev compatibility for viewsheets created/exported in 13.1
      // prior to Feature #10000. Can be removed without breaking anything.
      if(firstTableNode == null) {
         firstTableNode = Tool.getChildNodeByTagName(elem, "table");
      }

      if(firstTableNode != null) {
         firstTable = Tool.getValue(firstTableNode);
      }

      Element additionalTablesNode = Tool.getChildNodeByTagName(elem, "additionalTables");

      if(additionalTablesNode != null) {
         final NodeList additionalTables =
            Tool.getChildNodesByTagName(additionalTablesNode,"table");
         this.additionalTables = new ArrayList<>();

         for(int i = 0; i < additionalTables.getLength(); i++) {
            final Node tableNode = additionalTables.item(i);
            this.additionalTables.add(Tool.getValue(tableNode));
         }
      }

      Element singleSelectionLevelsNode = Tool.getChildNodeByTagName(elem, "singleSelectionLevels");

      if(singleSelectionLevelsNode != null) {
         final NodeList singleSelectionLevels =
            Tool.getChildNodesByTagName(singleSelectionLevelsNode,"level");
         this.singleSelectionLevels = new ArrayList<>();

         for(int i = 0; i < singleSelectionLevels.getLength(); i++) {
            final Node levelNode = singleSelectionLevels.item(i);
            this.singleSelectionLevels.add(Integer.parseInt(Tool.getValue(levelNode)));
         }
      }
   }

   /**
    * Write the state.
    * @param writer the specified print writer.
    */
   public void writeStateContent(PrintWriter writer, boolean runtime) {
      writer.println("<state_selectionStyle dValue=\"" + getSingleSelectionValue() + "\" />");
      writer.println("<state_mixedSingleSelection dValue=\"" + mixedSingleSelection + "\" />");

      if(singleSelectionLevels != null) {
         writer.print("<state_singleSelectionLevels>");

         for(Integer level : singleSelectionLevels) {
            writer.print("<level>");
            writer.print("<![CDATA[" + level + "]]>");
            writer.println("</level>");
         }

         writer.print("</state_singleSelectionLevels>");
      }
   }

   /**
    * Parse the state.
    * @param elem the specified xml element.
    * @param runtime if is runtime mode, default is true.
    */
   public void parseStateContent(Element elem, boolean runtime) throws Exception {
      Element node = Tool.getChildNodeByTagName(elem, "state_selectionStyle");

      if(node != null) {
         boolean singleValue = Boolean.parseBoolean(Tool.getAttribute(node, "dValue"));
         setSingleSelectionValue(singleValue);
      }

      node = Tool.getChildNodeByTagName(elem, "state_mixedSingleSelection");

      if(node != null) {
         mixedSingleSelection = Boolean.parseBoolean(Tool.getAttribute(node, "dValue"));
      }

      node = Tool.getChildNodeByTagName(elem, "state_singleSelectionLevels");
      this.singleSelectionLevels = new ArrayList<>();

      if(node != null) {
         final NodeList singleSelectionLevels = Tool.getChildNodesByTagName(node,"level");

         for(int i = 0; i < singleSelectionLevels.getLength(); i++) {
            final Node levelNode = singleSelectionLevels.item(i);
            this.singleSelectionLevels.add(Integer.parseInt(Tool.getValue(levelNode)));
         }
      }
   }

   /**
    * Copy the input data part assembly info.
    * @param info the specified viewsheet assembly info.
    * @return new hint.
    */
   @Override
   protected int copyInputDataInfo(VSAssemblyInfo info, int hint) {
      hint = super.copyInputDataInfo(info, hint);
      SelectionVSAssemblyInfo sinfo = (SelectionVSAssemblyInfo) info;

      if(!Tool.equals(firstTable, sinfo.firstTable) ||
         !Tool.equals(additionalTables, sinfo.additionalTables))
      {
         firstTable = sinfo.firstTable;
         additionalTables = new ArrayList<>(sinfo.additionalTables);
         sourceType = sinfo.sourceType;
         fireBindingEvent();
         hint |= VSAssembly.INPUT_DATA_CHANGED;
         hint |= VSAssembly.BINDING_CHANGED;
      }

      sourceType = sinfo.sourceType;

      // need to refresh list since we may need to select/deselect some items
      if(!Tool.equals(singleValue, sinfo.singleValue) ||
         !Tool.equals(isSingleSelection(), sinfo.isSingleSelection()))
      {
         singleValue = sinfo.singleValue;
         hint |= VSAssembly.INPUT_DATA_CHANGED;
      }

      return hint;
   }

   /**
    * Copy the view part assembly info.
    * @param info the specified viewsheet assembly info.
    * @return <tt>true</tt> if changed, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean copyViewInfo(VSAssemblyInfo info, boolean deep) {
      boolean result = super.copyViewInfo(info, deep);
      SelectionVSAssemblyInfo cinfo = (SelectionVSAssemblyInfo) info;

      if(hitsMV != cinfo.hitsMV) {
         hitsMV = cinfo.hitsMV;
         result = true;
      }

      if(!Tool.equals(submitValue, cinfo.submitValue) ||
         !Tool.equals(isSubmitOnChange(), cinfo.isSubmitOnChange()))
      {
         submitValue = cinfo.submitValue;
         result = true;
      }

      if(!Tool.equals(mixedSingleSelection, cinfo.mixedSingleSelection)) {
         mixedSingleSelection = cinfo.mixedSingleSelection;
         result = true;
      }

      if(!Tool.equals(singleSelectionLevels, cinfo.singleSelectionLevels)) {
         copySingleLevelsInfo(cinfo);
         result = true;
      }

      if(!Tool.equals(selectFirstItem, cinfo.selectFirstItem) ||
         !Tool.equals(isSelectFirstItem(), cinfo.isSelectFirstItem()))
      {
         selectFirstItem = cinfo.selectFirstItem;
         result = true;
      }

      return result;
   }

   protected void copySingleLevelsInfo(SelectionVSAssemblyInfo sinfo) {
      singleSelectionLevels = sinfo.singleSelectionLevels;
   }

   /**
    * Clone this object.
    * @param shallow <tt>true</tt> to perform shallow clone,
    * <tt>false</tt> to perform deep clone.
    * @return the cloned object.
    */
   @Override
   public SelectionVSAssemblyInfo clone(boolean shallow) {
      SelectionVSAssemblyInfo sinfo = (SelectionVSAssemblyInfo) super.clone(shallow);

      if(!shallow) {
         if(submitValue != null) {
            sinfo.submitValue = (DynamicValue) submitValue.clone();
         }

         if(singleValue != null) {
            sinfo.singleValue = (DynamicValue) singleValue.clone();
            sinfo.mixedSingleSelection = mixedSingleSelection;
         }

         if(singleSelectionLevels != null) {
            sinfo.singleSelectionLevels = new ArrayList(singleSelectionLevels);
         }

         if(selectFirstItem != null) {
            sinfo.selectFirstItem = (DynamicValue) selectFirstItem.clone();
         }
      }

      return sinfo;
   }

   /**
    * Copy the assembly info.
    * @param info the specified viewsheet assembly info.
    * @return the hint to reset view, data or worksheet data.
    */
   @Override
   public int copyInfo(VSAssemblyInfo info) {
      int hint = VSAssembly.NONE_CHANGED;

      if(isEnabled() != info.isEnabled()) {
         hint = hint | VSAssembly.OUTPUT_DATA_CHANGED;
      }

      return hint | super.copyInfo(info);
   }

   /**
    * Gets selections data for SelectionList, SelectionTree, TimeSlider.
    * @return selections.
    */
   public DataSerializable getSelections() {
      return null;
   }

   /**
    * Check if the selection hits mv.
    */
   public boolean isHitsMV() {
      return hitsMV;
   }

   /**
    * Set if the selection hits mv.
    */
   public void setHitsMV(boolean hitsMV) {
      this.hitsMV = hitsMV;
   }

   /**
    * Set if the this assembly is used as adhoc filter.
    */
   public void setAdhocFilter(boolean adFilter) {
      // nothing, need to be override
   }

   /**
    * Check if this assembly is used as adhoc filtering.
    */
   public boolean isAdhocFilter() {
      return false;
   }

   /**
    * Set if the this assembly is created by adhoc filter.
    */
   public void setCreatedByAdhoc(boolean adhoc) {
      this.createdAdhoc = adhoc;
   }

   /**
    * Check if this assembly is created by adhoc filter.
    */
   public boolean isCreatedByAdhoc() {
      return createdAdhoc;
   }

   /**
    * Check if the selection should be submitted on change at runtime.
    */
   public boolean isSubmitOnChange() {
      return Boolean.parseBoolean(submitValue.getRuntimeValue(true) + "");
   }

   /**
    * Set if the selection should be submitted on change at runtime.
    */
   public void setSubmitOnChange(boolean submit) {
      submitValue.setRValue(submit);
   }

   /**
    * Set if the selection should be submitted on change design time.
    */
   public void setSubmitOnChangeValue(boolean submit) {
      submitValue.setDValue(submit + "");
   }

   /**
    * Check if the selection should be submitted on change at design time.
    */
   public boolean getSubmitOnChangeValue() {
      return Boolean.parseBoolean(submitValue.getDValue());
   }

   /**
    * Check if runtime single-selection is enabled.
    */
   public boolean isSingleSelection() {
      return Boolean.parseBoolean(singleValue.getRuntimeValue(true) + "");
   }

   /**
    * Set if runtime single-selection is enabled.
    */
   public void setSingleSelection(boolean single) {
      singleValue.setRValue(single);
   }

   /**
    * Set if design time single-selection is enabled.
    */
   public void setSingleSelectionValue(boolean single) {
      singleValue.setDValue(single + "");
   }

   /**
    * Check if design time single-selection is allowed.
    */
   public boolean getSingleSelectionValue() {
      return Boolean.parseBoolean(singleValue.getDValue());
   }

   public void setSelectFirstItem(boolean value) {
      this.selectFirstItem.setRValue(value);
   }

   public boolean isSelectFirstItem() {
      return Boolean.parseBoolean(selectFirstItem.getRuntimeValue(true) + "");
   }

   public void setSelectFirstItemValue(boolean value) {
      selectFirstItem.setDValue(value + "");
   }

   public boolean getSelectFirstItemValue() {
      return Boolean.parseBoolean(selectFirstItem.getDValue());
   }

   public boolean removeSingleSelectionLevel(Integer level) {
      mixedSingleSelection = true;
      List<Integer> levels = singleSelectionLevels;

      if(levels != null) {
         return levels.remove(level);
      }

      return false;
   }

   public void addSingleSelectionLevel(Integer level) {
      mixedSingleSelection = true;
      List<Integer> levels = singleSelectionLevels == null
         ? singleSelectionLevels = new ArrayList<>() : singleSelectionLevels;

      levels.add(level);
      levels.sort(Comparator.comparingInt(c -> c));
   }

   public boolean isSingleSelectionLevel(Integer level) {
      return isSingleSelection() &&
         singleSelectionLevels != null && singleSelectionLevels.contains(level);
   }

   protected List<Integer> originSingleSelectionLevels() {
      return this.singleSelectionLevels;
   }

   public boolean containsLevel(Integer level) {
      return getSingleSelectionLevels().contains(level);
   }

   public List<Integer> getSingleSelectionLevels() {
      if(!isSingleSelection() && !mixedSingleSelection) {
         return new ArrayList<>();
      }

      if(singleSelectionLevels == null || singleSelectionLevels.size() == 0) {
         fixSingleSelectionLevels();
      }

      return singleSelectionLevels == null ? new ArrayList<>() : singleSelectionLevels;
   }

   /**
    * If single selection mode, and not mixed single selection mode,
    * then all levels should be single selection mode.
    */
   private void fixSingleSelectionLevels() {
      if(isSingleSelection() && !mixedSingleSelection &&
         (singleSelectionLevels == null || singleSelectionLevels.size() == 0))
      {
         singleSelectionLevels = new ArrayList<>();

         for(int i = 0; i < getDataRefs().length; i++) {
            singleSelectionLevels.add(i);
         }
      }
   }

   public List<String> getSingleSelectionLevelNames() {
      List<Integer> levels = getSingleSelectionLevels();

      if(levels.size() == 0) {
         return null;
      }

      List<String> levelNames = Arrays.stream(this.getDataRefs())
         .map((ref) -> getLevelName(ref))
         .collect(Collectors.toList());

      if(CollectionUtils.isEmpty(levelNames)) {
         return null;
      }

      List<String> names = levels.stream()
         .filter(level -> level < levelNames.size())
         .map(levelNames::get)
         .collect(Collectors.toList());

      return names;
   }

   public void setSingleSelectionLevels(List<Integer> levels) {
      this.singleSelectionLevels = levels;
   }

   public void setSingleSelectionLevelNames(List<String> levels) {
      if(!CollectionUtils.isEmpty(levels)) {
         List<String> levelNames = Arrays.stream(this.getDataRefs())
            .map((ref) -> getLevelName(ref))
            .collect(Collectors.toList());

         this.singleSelectionLevels = levels.stream()
            .map(levelNames::indexOf)
            .filter((level) -> level != -1)
            .collect(Collectors.toList());
      }
      else {
         this.singleSelectionLevels = null;
      }
   }

   private String getLevelName(DataRef ref) {
      int refType = ref.getRefType();
      String name = null;

      if((refType & DataRef.CUBE_DIMENSION) == DataRef.CUBE_DIMENSION) {
         name = ref.getAttribute();
      }
      else {
         name = ref.getName();
      }

      int idx = name.indexOf(":");

      if(idx != -1) {
         name = name.substring(idx + 1);
      }

      return name;
   }

   /**
    * Reset runtime values.
    */
   @Override
   public void resetRuntimeValues() {
      super.resetRuntimeValues();
      submitValue.setRValue(null);
      singleValue.setRValue(null);
   }

   /**
    * Get the data refs.
    *
    * @return the data refs.
    */
   public abstract DataRef[] getDataRefs();

   /**
    * Get the list of tables this selection applies to.
    */
   public List<String> getTableNames() {
      final String firstTableName = getFirstTableName();
      final List<String> additionalTables = getAdditionalTableNames();
      final List<String> tableNames = new ArrayList<>(additionalTables.size() + 1);

      if(firstTableName != null && !firstTableName.isEmpty()) {
         tableNames.add(firstTableName);
      }

      tableNames.addAll(additionalTables);
      return tableNames;
   }

   /**
    * Set the list of tables this selection applies to.
    */
   public void setTableNames(List<String> tableNames) {
      if(tableNames.size() == 0) {
         setFirstTableName(null);
         setAdditionalTableNames(Collections.emptyList());
      }
      else {
         setFirstTableName(tableNames.get(0));
         final List<String> additionalTableNames = tableNames.stream()
            .skip(1) // Skip first
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
         setAdditionalTableNames(additionalTableNames);
      }
   }

   /**
    * @return <tt>true</tt> if this selection applies to multiple tables, <tt>false</tt> otherwise.
    */
   public boolean isSelectionUnion() {
      return !getAdditionalTableNames().isEmpty();
   }

   /**
    * @return if the current selection binded any calcfield.
    */
   public boolean bindedCalcFields() {
      if(firstTable == null) {
         return false;
      }

      DataRef[] refs = getDataRefs();

      if(refs == null || refs.length == 0) {
         return false;
      }

      Viewsheet vs = getViewsheet();

      for(int i = 0; i < refs.length; i++) {
         if(vs.getCalcField(firstTable, refs[i].getName()) != null) {
            return true;
         }
      }

      return false;
   }

   private DynamicValue submitValue = new DynamicValue("true", XSchema.BOOLEAN);
   // view
   private DynamicValue singleValue = new DynamicValue("false", XSchema.BOOLEAN);
   private DynamicValue selectFirstItem = new DynamicValue("false", XSchema.BOOLEAN);
   private List<Integer> singleSelectionLevels;
   private boolean mixedSingleSelection = false;
   // input data
   private String firstTable;
   private List<String> additionalTables;
   // binding source type, only maintained binding vs assembly source
   private int sourceType = XSourceInfo.NONE;
   private boolean hitsMV;

   private boolean createdAdhoc = false;
}
