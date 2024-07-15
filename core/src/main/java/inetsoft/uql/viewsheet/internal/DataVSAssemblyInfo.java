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

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.ConditionList;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.DynamicValue;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.*;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * DataVSAssemblyInfo, the assembly info of a data assembly.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public abstract class DataVSAssemblyInfo extends VSAssemblyInfo
   implements BaseAnnotationVSAssemblyInfo
{
   /**
    * Constructor.
    */
   public DataVSAssemblyInfo() {
      super();

      annotations = new ArrayList<>();
   }

   /**
    * Get the source info of the target.
    * @return the source info of the target.
    */
   public SourceInfo getSourceInfo() {
      return src;
   }

   /**
    * Set the source info of the target.
    * @param src the specified source info of the target.
    */
   public void setSourceInfo(SourceInfo src) {
      this.src = src;
   }

   /**
    * Get the target table.
    * @return the target table.
    */
   public String getTableName() {
      SourceInfo source = getSourceInfo();
      boolean hasSource = source != null && !source.isEmpty() &&
         (source.getType() == SourceInfo.ASSET ||
            source.getType() == SourceInfo.VS_ASSEMBLY);
      return !hasSource ? null : source.getSource();
   }

   /**
    * Check whether drill up/down is enabled.
    */
   public boolean isDrillEnabled() {
      return Boolean.valueOf(drillValue.getRuntimeValue(true) + "");
   }

   /**
    * Set whether drill up/down is enabled.
    */
   public void setDrillEnabled(boolean drill) {
      drillValue.setRValue(drill);
   }

   /**
    * Get whether drill up/down is enabled.
    */
   public String getDrillEnabledValue() {
      return drillValue.getDValue();
   }

   /**
    * Set whether drill up/down is enabled.
    */
   public void setDrillEnabledValue(String drill) {
      drillValue.setDValue(drill);
   }

   /**
    * Return max mode size if it's in max mode.
    */
   public Dimension getMaxSize() {
      return null;
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);
      writer.print(" drillValue=\"" + getDrillEnabledValue() + "\"");

      if(detailStyle != null) {
         writer.print(" detailStyle=\"" + detailStyle + "\"");
      }

      writer.print(" editedByWizard=\"" + editedByWizard + "\"");
      writer.print(" dateComparisonEnabled=\"" + getDateComparisonEnabledValue() + "\"");
   }

   /**
    * Parse attributes.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseAttributes(Element elem) {
      super.parseAttributes(elem);

      String prop;

      if((prop = Tool.getAttribute(elem, "drillValue")) != null) {
         setDrillEnabledValue(prop);
      }

      prop = Tool.getAttribute(elem, "detailStyle");

      if(prop != null && !prop.equals("null")) {
         setDetailStyle(prop);
      }

      if(Tool.getAttribute(elem, "editedByWizard") != null) {
         editedByWizard = Tool.getAttribute(elem, "editedByWizard").equals("true");
      }

      if((prop = Tool.getAttribute(elem, "dateComparisonEnabled")) != null) {
         setDateComparisonEnabledValue(prop);
      }
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(src != null) {
         src.writeXML(writer);
      }

      if(preconds != null && !preconds.isEmpty()) {
         preconds.writeXML(writer);
      }

      if(infos != null) {
         writer.println("<columnsVisibility>");
         infos.writeXML(writer);
         writer.println("</columnsVisibility>");
      }

      if(annotations != null && !annotations.isEmpty()) {
         writer.print("<annotations>");

         for(int j = 0; j < annotations.size(); j++) {
            writer.print("<annotation>");
            writer.print("<![CDATA[" + annotations.get(j) + "]]>");
            writer.print("</annotation>");
         }

         writer.println("</annotations>");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element node = Tool.getChildNodeByTagName(elem, "sourceInfo");

      if(node != null) {
         src = new SourceInfo();
         src.parseXML(node);
      }

      node = Tool.getChildNodeByTagName(elem, "conditions");

      if(node != null) {
         preconds = new ConditionList();
         preconds.parseXML(node);
      }

      node = Tool.getChildNodeByTagName(elem, "columnsVisibility");

      if(node != null) {
         infos = new ColumnSelection();
         node = Tool.getChildNodeByTagName(node, "ColumnSelection");

         if(node != null) {
            infos.parseXML(node);
         }
      }

      Element anode = Tool.getChildNodeByTagName(elem, "annotations");

      if(anode != null) {
         NodeList alist = Tool.getChildNodesByTagName(anode, "annotation");

         if(alist != null && alist.getLength() > 0) {
            annotations = new ArrayList<>();

            for(int i = 0; i < alist.getLength(); i++) {
               annotations.add(Tool.getValue(alist.item(i)));
            }
         }
      }
   }

   /**
    * Get show details table columns visibility property.
    */
   public ColumnSelection getDetailColumns() {
      return infos;
   }

   /**
    * Set show details table columns visibility property.
    */
   public void setDetailColumns(ColumnSelection infos) {
      this.infos = infos;
   }

   /**
    * Get table style for show detail table.
    */
   public String getDetailStyle() {
      return detailStyle;
   }

   /**
    * Get table style for show detail table.
    */
   public void setDetailStyle(String style) {
      detailStyle = style;
   }

   /**
    * Clone this object.
    * @param shallow <tt>true</tt> to perform shallow clone,
    * <tt>false</tt> to perform deep clone.
    * @return the cloned object.
    */
   @Override
   public DataVSAssemblyInfo clone(boolean shallow) {
      try {
         DataVSAssemblyInfo info = (DataVSAssemblyInfo) super.clone(shallow);

         if(!shallow) {
            if(src != null) {
               info.src = (SourceInfo) src.clone();
            }

            if(annotations != null) {
               info.annotations = Tool.deepCloneCollection(annotations);
            }

            if(infos != null) {
               info.infos = (ColumnSelection) infos.clone();
            }

            if(preconds != null) {
               info.preconds = preconds.clone();
            }

            if(annotations != null) {
               info.annotations = new ArrayList<>(annotations);
            }

            if(drillValue != null) {
               info.drillValue = (DynamicValue) drillValue.clone();
            }
         }

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone DataVSAssemblyInfo", ex);
      }

      return null;
   }

   @Override
   protected boolean copyViewInfo(VSAssemblyInfo info, boolean deep) {
      boolean result = super.copyViewInfo(info, deep);
      DataVSAssemblyInfo ninfo = (DataVSAssemblyInfo) info;

      if(!Tool.equals(drillValue, ninfo.drillValue)) {
         drillValue = ninfo.drillValue;
         result = true;
      }

      if(!Tool.equals(detailStyle, ninfo.detailStyle)) {
         detailStyle = ninfo.detailStyle;
         result = true;
      }

      if(!Tool.equals(dateComparisonEnabled, ninfo.dateComparisonEnabled)) {
         this.dateComparisonEnabled = ninfo.dateComparisonEnabled;
         result = true;
      }

      return result;
   }

   /**
    * Copy the input data part assembly info.
    * @param info the specified viewsheet assembly info.
    * @return new hint.
    */
   @Override
   protected int copyInputDataInfo(VSAssemblyInfo info, int hint) {
      hint = super.copyInputDataInfo(info, hint);
      DataVSAssemblyInfo cinfo = (DataVSAssemblyInfo) info;

      if(!Tool.equals(src, cinfo.src)) {
         src = cinfo.src;
         fireBindingEvent();
         hint |= VSAssembly.INPUT_DATA_CHANGED;
         hint |= VSAssembly.BINDING_CHANGED;
      }

      hint = setPreConditionList0(cinfo.preconds, hint);
      return hint;
   }

   /**
    * Copy condition list defined in this data viewsheet assembly.
    */
   private int setPreConditionList0(ConditionList npreconds, int hint) {
      if(npreconds != null && npreconds.isEmpty()) {
         npreconds = null;
      }

      if(!Tool.equals(preconds, npreconds)) {
         preconds = npreconds;
         hint |= VSAssembly.INPUT_DATA_CHANGED;
      }

      return hint;
   }

   /**
    * Set the pre-condition list defined in this data viewsheet assembly.
    */
   public int setPreConditionList(ConditionList preconds) {
      return setPreConditionList0(preconds, 0);
   }

   /**
    * Get the pre-condition list.
    */
   public ConditionList getPreConditionList() {
      return preconds;
   }

   public boolean equalsContent(Object obj) {
      if(!(obj instanceof DataVSAssemblyInfo)) {
         return false;
      }

      DataVSAssemblyInfo info = (DataVSAssemblyInfo) obj;

      if(!Tool.equals(drillValue, info.drillValue)) {
         return false;
      }

      if(!Tool.equals(detailStyle, info.detailStyle)) {
         return false;
      }

      if(!Tool.equals(dateComparisonEnabled, info.dateComparisonEnabled)) {
         return false;
      }

      return super.equals(obj);
   }

   /**
    * Clear runtime values.
    */
   @Override
   public void resetRuntimeValues() {
      super.resetRuntimeValues();
      drillValue.setRValue(null);
      dateComparisonEnabled.setRValue(null);
   }

   @Override
   public List<DynamicValue> getViewDynamicValues(boolean all) {
      List<DynamicValue> list = super.getViewDynamicValues(all);
      list.add(drillValue);
      return list;
   }

   /**
    * Get annotations.
    */
   @Override
   public List<String> getAnnotations() {
      return annotations;
   }

   /**
    * Add a annotation.
    * @param name the specified annotation name.
    */
   @Override
   public void addAnnotation(String name) {
      if(!annotations.contains(name)) {
         annotations.add(name);
      }
   }

   /**
    * Remove the annotation.
    * @param name the specified annotation name.
    */
   @Override
   public void removeAnnotation(String name) {
      annotations.remove(name);
   }

   /**
    * Remove all annotations.
    */
   @Override
   public void clearAnnotations() {
      annotations.clear();
   }

   /**
    * Edited only through wizard
    */
   public boolean isEditedByWizard() {
      return editedByWizard;
   }

   /**
    * Edited only through wizard
    */
   public void setEditedByWizard(boolean editedByWizard) {
      this.editedByWizard = editedByWizard;
   }

   /**
    * Clear the binding fields.
    */
   public void clearBinding() {
   }

   public boolean isDateComparisonEnabled() {
      return Boolean.valueOf(dateComparisonEnabled.getRuntimeValue(true) + "");
   }

   public void setDateComparisonEnabled(boolean dateComparisonEnabled) {
      this.dateComparisonEnabled.setRValue(dateComparisonEnabled);
   }

   public String getDateComparisonEnabledValue() {
      return dateComparisonEnabled.getDValue();
   }

   public void setDateComparisonEnabledValue(String drill) {
      dateComparisonEnabled.setDValue(drill);
   }

   private SourceInfo src; // input data
   private ColumnSelection infos; // show details table columns visibility
   private ConditionList preconds; // pre filter
   private List<String> annotations;
   private String detailStyle;
   private DynamicValue drillValue = new DynamicValue("true", XSchema.BOOLEAN);
   private boolean editedByWizard = false;
   private DynamicValue dateComparisonEnabled = new DynamicValue("true", XSchema.BOOLEAN);

   private static final Logger LOG = LoggerFactory.getLogger(DataVSAssemblyInfo.class);
}
