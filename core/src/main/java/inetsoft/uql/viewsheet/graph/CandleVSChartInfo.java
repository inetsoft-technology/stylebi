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
package inetsoft.uql.viewsheet.graph;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.erm.AbstractDataRef;
import inetsoft.uql.viewsheet.VSDataRef;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.DateComparisonInfo;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.*;

/**
 * CandleVSChartInfo maintains binding info of candle chart.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class CandleVSChartInfo extends MergedVSChartInfo implements CandleChartInfo {
   /**
    * Constructor.
    */
   public CandleVSChartInfo() {
      super();
   }

   /**
    * Set the close field.
    * @param field the specified close field.
    */
   @Override
   public void setCloseField(ChartRef field) {
      this.closeField = (VSChartRef) field;
   }

   /**
    * Set the open field.
    * @param field the specified open field.
    */
   @Override
   public void setOpenField(ChartRef field) {
      this.openField = (VSChartRef) field;
   }

   /**
    * Set the high field.
    * @param field the specified high field.
    */
   @Override
   public void setHighField(ChartRef field) {
      this.highField = (VSChartRef) field;
   }

   /**
    * Set the low field.
    * @param field the specified low field.
    */
   @Override
   public void setLowField(ChartRef field) {
      this.lowField = (VSChartRef) field;
   }

   /**
    * Get the close field.
    * @return the close field.
    */
   @Override
   public ChartRef getCloseField() {
      return this.closeField;
   }

   /**
    * Get the open field.
    * @return the open field.
    */
   @Override
   public ChartRef getOpenField() {
      return this.openField;
   }

   /**
    * Get the high field.
    * @return the high field.
    */
   @Override
   public ChartRef getHighField() {
      return this.highField;
   }

   /**
    * Get the low field.
    * @return the low field.
    */
   @Override
   public ChartRef getLowField() {
      return this.lowField;
   }

   /**
    * Get all fields, including high, low, close and open fields.
    */
   @Override
   public VSDataRef[] getFields() {
      VSDataRef[] refs = super.getFields();
      List<VSDataRef> list = new ArrayList<>(Arrays.asList(refs));

      if(closeField != null) {
         list.add(closeField);
      }

      if(openField != null) {
         list.add(openField);
      }

      if(highField != null) {
         list.add(highField);
      }

      if(lowField != null) {
         list.add(lowField);
      }

      return list.toArray(new VSDataRef[0]);
   }

   /**
    * Remove the chart binding fields.
    */
   @Override
   public void removeFields() {
      super.removeFields();

      closeField = null;
      openField = null;
      highField = null;
      lowField = null;
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(closeField != null) {
         writer.println("<close>");
         closeField.writeXML(writer);
         writer.println("</close>");
      }

      if(openField != null) {
         writer.println("<open>");
         openField.writeXML(writer);
         writer.println("</open>");
      }

      if(highField != null) {
         writer.println("<high>");
         highField.writeXML(writer);
         writer.println("</high>");
      }

      if(lowField != null) {
         writer.println("<low>");
         lowField.writeXML(writer);
         writer.println("</low>");
      }

      if(rcloseField != null) {
         writer.println("<rclose>");
         rcloseField.writeXML(writer);
         writer.println("</rclose>");
      }

      if(ropenField != null) {
         writer.println("<ropen>");
         ropenField.writeXML(writer);
         writer.println("</ropen>");
      }

      if(rhighField != null) {
         writer.println("<rhigh>");
         rhighField.writeXML(writer);
         writer.println("</rhigh>");
      }

      if(rlowField != null) {
         writer.println("<rlow>");
         rlowField.writeXML(writer);
         writer.println("</rlow>");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);
      Element closeNode = Tool.getChildNodeByTagName(elem, "close");

      if(closeNode != null) {
         closeField = (VSChartRef) AbstractDataRef.createDataRef(
            Tool.getFirstChildNode(closeNode));
      }

      Element openNode = Tool.getChildNodeByTagName(elem, "open");

      if(openNode != null) {
         openField = (VSChartRef) AbstractDataRef.createDataRef(
            Tool.getFirstChildNode(openNode));
      }

      Element highNode = Tool.getChildNodeByTagName(elem, "high");

      if(highNode != null) {
         highField = (VSChartRef) AbstractDataRef.createDataRef(
            Tool.getFirstChildNode(highNode));
      }

      Element lowNode = Tool.getChildNodeByTagName(elem, "low");

      if(lowNode != null) {
         lowField = (VSChartRef) AbstractDataRef.createDataRef(
            Tool.getFirstChildNode(lowNode));
      }
   }

   /**
    * Clone this object.
    * @return the cloned object.
    */
   @Override
   public VSChartInfo clone() {
       try {
         CandleVSChartInfo obj = (CandleVSChartInfo) super.clone();

         if(obj == null) {
            return null;
         }

         if(closeField != null) {
            obj.closeField = (VSChartRef) closeField.clone();
         }

         if(openField != null) {
            obj.openField = (VSChartRef) openField.clone();
         }

         if(highField != null) {
            obj.highField = (VSChartRef) highField.clone();
         }

         if(lowField != null) {
            obj.lowField = (VSChartRef) lowField.clone();
         }

         return obj;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone CandleVSChartInfo", ex);
         return null;
      }
   }

   /**
    * Check if equals another object in content.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals the object in content, <tt>false</tt>
    * otherwise.
    */
   @Override
   public boolean equalsContent(Object obj) {
      if(!super.equalsContent(obj)) {
         return false;
      }

      if(!(obj instanceof CandleVSChartInfo)) {
         return false;
      }

      CandleVSChartInfo chartInfo = (CandleVSChartInfo) obj;
      VSChartRef closeField2 = chartInfo.closeField;

      if(!Tool.equalsContent(closeField, closeField2)) {
         return false;
      }

      VSChartRef openField2 = chartInfo.openField;

      if(!Tool.equalsContent(openField, openField2)) {
         return false;
      }

      VSChartRef highField2 = chartInfo.highField;

      if(!Tool.equalsContent(highField, highField2)) {
         return false;
      }

      VSChartRef lowField2 = chartInfo.lowField;

      if(!Tool.equalsContent(lowField, lowField2)) {
         return false;
      }

      return true;
   }

   /**
    * Rename the depended. This method should be called when an assembly or
    * other named variables are renamed. It updates of the dynamic references
    * to use the new name.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   @Override
   public void renameDepended(String oname, String nname, Viewsheet vs) {
      super.renameDepended(oname, nname, vs);

      if(closeField != null) {
         closeField.renameDepended(oname, nname, vs);
      }

      if(openField != null) {
         openField.renameDepended(oname, nname, vs);
      }

      if(highField != null) {
         highField.renameDepended(oname, nname, vs);
      }

      if(lowField != null) {
         lowField.renameDepended(oname, nname, vs);
      }
   }

   /**
    * Get the dynamic property values.
    * @return the dynamic values.
    */
   @Override
   public List getDynamicValues() {
      List list = super.getDynamicValues();

      if(openField != null) {
         list.addAll(openField.getDynamicValues());
      }

      if(closeField != null) {
         list.addAll(closeField.getDynamicValues());
      }

      if(highField != null) {
         list.addAll(highField.getDynamicValues());
      }

      if(lowField != null) {
         list.addAll(lowField.getDynamicValues());
      }

      return list;
   }

   /**
    * Update the info to fill in runtime value.
    * @param vs the specified viewsheet.
    * @param columns the specified column selection.
    * @param sep true if the chart is separated, false otherwise.
    * @param pdim the specified dimension for period comparison.
    */
   @Override
   public synchronized void update(Viewsheet vs, ColumnSelection columns,
                                   boolean sep, VSChartDimensionRef pdim,
                                   String source, DateComparisonInfo dcInfo)
   {
      // first to update runtime fields, or statement getRTFields() in
      // MergedVSChartInfo.update() will get the wrong runtime fields
      ropenField = null;
      rcloseField = null;
      rhighField = null;
      rlowField = null;

      if(openField != null) {
         List list = openField.update(vs, columns);

         if(list.size() > 0) {
            ropenField = (VSChartRef) list.get(0);
         }
      }

      if(closeField != null) {
         List list = closeField.update(vs, columns);

         if(list.size() > 0) {
            rcloseField = (VSChartRef) list.get(0);
         }
      }

      if(highField != null) {
         List list = highField.update(vs, columns);

         if(list.size() > 0) {
            rhighField = (VSChartRef) list.get(0);
         }
      }

      if(lowField != null) {
         List list = lowField.update(vs, columns);

         if(list.size() > 0) {
            rlowField = (VSChartRef) list.get(0);
         }
      }

      super.update(vs, columns, sep, pdim, source, dcInfo);
   }

   /**
    * Set the runtime open field.
    * @param field the specified runtime open field.
    */
   public void setRTOpenField(VSChartRef field) {
      this.ropenField = field;
   }

   /**
    * Set the runtime close field.
    * @param field the specified runtime close field.
    */
   public void setRTCloseField(VSChartRef field) {
      this.rcloseField = field;
   }

   /**
    * Set the runtime low field.
    * @param field the specified runtime low field.
    */
   public void setRTLowField(VSChartRef field) {
      this.rlowField = field;
   }

   /**
    * Set the runtime high field.
    * @param field the specified runtime high field.
    */
   public void setRTHighField(VSChartRef field) {
      this.rhighField = field;
   }

   /**
    * Get the runtime close field.
    * @return the runtime close field.
    */
   @Override
   public synchronized ChartRef getRTCloseField() {
      return rcloseField;
   }

   /**
    * Get the runtime open field.
    * @return the runtime open field.
    */
   @Override
   public synchronized ChartRef getRTOpenField() {
      return ropenField;
   }

   /**
    * Get the runtime high field.
    * @return the runtime high field.
    */
   @Override
   public synchronized ChartRef getRTHighField() {
      return rhighField;
   }

   /**
    * Get the runtime low field.
    * @return the runtime low field.
    */
   @Override
   public synchronized ChartRef getRTLowField() {
      return rlowField;
   }

   /**
    * Get merge info special fields, not including x, y and aesthetic fields.
    */
   @Override
   public VSDataRef[] getMergedRTFields() {
      List list = new ArrayList();

      if(ropenField != null) {
         list.add(ropenField);
      }

      if(rhighField != null) {
         list.add(rhighField);
      }

      if(rlowField != null) {
         list.add(rlowField);
      }

      if(rcloseField != null) {
         list.add(rcloseField);
      }

      VSDataRef[] arr = new VSDataRef[list.size()];
      list.toArray(arr);

      return arr;
   }

   /**
    * Get the default measure.
    */
   @Override
   public String getDefaultMeasure() {
      ChartRef dm = highField != null ? highField : (closeField != null ?
         closeField : (openField != null ? openField : lowField));

      return dm == null ? null : dm.getFullName();
   }

   /**
    * Get the field by a name.
    * @param name the specified field name.
    * @return the field.
    */
   @Override
   public ChartRef getFieldByName(String name, boolean rt) {
      ChartRef[] refs = rt ?
         new ChartRef[] {ropenField, rcloseField, rhighField, rlowField} :
         new ChartRef[] {openField, closeField, highField, lowField};

      for(int i = 0; i < refs.length; i++) {
         if(refs[i] != null && refs[i].getFullName().equals(name)) {
            return refs[i];
         }
      }

      return super.getFieldByName(name, rt);
   }

   @Override
   public ChartRef getFieldByName(String name, boolean rt, boolean ignoreDataGroup) {
      ChartRef[] refs = rt ?
         new ChartRef[] {ropenField, rcloseField, rhighField, rlowField} :
         new ChartRef[] {openField, closeField, highField, lowField};

      for(int i = 0; i < refs.length; i++) {
         if(isSameField(refs[i], name, ignoreDataGroup)) {
            return refs[i];
         }
      }

      return super.getFieldByName(name, rt, ignoreDataGroup);
   }

   @Override
   public boolean replaceField(ChartRef oldFiled, ChartRef newFiled) {
      if(!(oldFiled instanceof VSChartRef) || !(newFiled instanceof VSChartRef)) {
         return false;
      }

      if(openField != null && Tool.equals(openField.getFullName(), oldFiled.getFullName())) {
         openField = (VSChartRef) newFiled;

         return true;
      }

      if(closeField != null && Tool.equals(closeField.getFullName(), oldFiled.getFullName())) {
         closeField = (VSChartRef) newFiled;

         return true;
      }

      if(highField != null && Tool.equals(highField.getFullName(), oldFiled.getFullName())) {
         highField = (VSChartRef) newFiled;

         return true;
      }

      if(lowField != null && Tool.equals(lowField.getFullName(), oldFiled.getFullName())) {
         lowField = (VSChartRef) newFiled;

         return true;
      }

      return super.replaceField(oldFiled, newFiled);
   }

   /**
    * Candle is never inverted.
    */
   @Override
   public boolean isInvertedGraph() {
      return false;
   }

   /**
    * Check if the size frame is per measure.
    */
   @Override
   public boolean supportsSizeFieldFrame() {
      return false;
   }

   private VSChartRef openField;
   private VSChartRef closeField;
   private VSChartRef highField;
   private VSChartRef lowField;
   private VSChartRef ropenField;
   private VSChartRef rcloseField;
   private VSChartRef rhighField;
   private VSChartRef rlowField;

   private static final Logger LOG =
      LoggerFactory.getLogger(CandleVSChartInfo.class);
}
