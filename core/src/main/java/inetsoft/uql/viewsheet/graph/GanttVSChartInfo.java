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
import inetsoft.uql.viewsheet.graph.aesthetic.SizeFrameWrapper;
import inetsoft.uql.viewsheet.graph.aesthetic.StaticSizeFrameWrapper;
import inetsoft.uql.viewsheet.internal.DateComparisonInfo;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.*;

/**
 * TreeVSChartInfo maintains binding info of tree chart.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class GanttVSChartInfo extends MergedVSChartInfo implements GanttChartInfo {
   /**
    * Constructor.
    */
   public GanttVSChartInfo() {
      super();
   }

   /**
    * Set the start field.
    */
   @Override
   public void setStartField(ChartRef field) {
      this.startField = (VSChartRef) field;
      updateChartTypes();
   }

   /**
    * Set the end field.
    */
   @Override
   public void setEndField(ChartRef field) {
      this.endField = (VSChartRef) field;
   }

   /**
    * Set the milestone field.
    */
   @Override
   public void setMilestoneField(ChartRef field) {
      this.milestoneField = (VSChartRef) field;
      updateChartTypes();
   }

   /**
    * Get the start field.
    */
   @Override
   public ChartRef getStartField() {
      return this.startField;
   }

   /**
    * Get the end field.
    */
   @Override
   public ChartRef getEndField() {
      return this.endField;
   }

   /**
    * Get the milestone field.
    */
   @Override
   public ChartRef getMilestoneField() {
      return this.milestoneField;
   }

   /**
    * Get all the binding refs.
    * @param runtime if viewsheet in run time mode.
    */
   @Override
   public ChartRef[] getBindingRefs(boolean runtime) {
      ChartRef[] refs = super.getBindingRefs(runtime);
      List<ChartRef> list = new ArrayList<>();
      list.addAll(Arrays.asList(refs));

      ChartRef start = runtime ? getRTStartField() : getStartField();

      if(start != null) {
         list.add(start);
      }

      ChartRef end = runtime ? getRTEndField() : getEndField();

      if(end != null) {
         list.add(end);
      }

      ChartRef milestone = runtime ? getRTMilestoneField() : getMilestoneField();

      if(milestone != null) {
         list.add(milestone);
      }

      return list.toArray(new ChartRef[0]);
   }

   /**
    * Get all fields, including x, y and aesthetic fields.
    */
   @Override
   public VSDataRef[] getFields() {
      VSDataRef[] refs = super.getFields();
      List<VSDataRef> list = new ArrayList(Arrays.asList(refs));

      if(startField != null) {
         list.add(startField);
      }

      if(endField != null) {
         list.add(endField);
      }

      if(milestoneField != null) {
         list.add(milestoneField);
      }

      return list.toArray(new VSDataRef[0]);
   }

   /**
    * Remove the chart binding fields.
    */
   @Override
   public void removeFields() {
      super.removeFields();

      startField = null;
      endField = null;
      milestoneField = null;
   }

   /**
    * Create size model map, for no size aesthetic ref.
    */
   public Map getSizeFrameMap() {
      HashMap sizeMap = new HashMap();

      if(getSizeFrameWrapper() == null) {
         setSizeFrameWrapper(new StaticSizeFrameWrapper());
      }

      sizeMap.put("key", getSizeFrameWrapper());

      return sizeMap;
   }

   /**
    * Set the size model map, for no size aesthetic ref.
    */
   public void setSizeFrameMap(Map map) {
      super.setSizeFrameWrapper((SizeFrameWrapper) map.values().toArray()[0]);
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(startField != null) {
         writer.println("<start>");
         startField.writeXML(writer);
         writer.println("</start>");
      }

      if(endField != null) {
         writer.println("<end>");
         endField.writeXML(writer);
         writer.println("</end>");
      }

      if(milestoneField != null) {
         writer.println("<milestone>");
         milestoneField.writeXML(writer);
         writer.println("</milestone>");
      }

      if(rstartField != null) {
         writer.println("<rstart>");
         rstartField.writeXML(writer);
         writer.println("</rstart>");
      }

      if(rendField != null) {
         writer.println("<rend>");
         rendField.writeXML(writer);
         writer.println("</rend>");
      }

      if(rmilestoneField != null) {
         writer.println("<rmilestone>");
         rmilestoneField.writeXML(writer);
         writer.println("</rmilestone>");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element startNode = Tool.getChildNodeByTagName(elem, "start");

      if(startNode != null) {
         setStartField((ChartRef) AbstractDataRef.createDataRef(Tool.getFirstChildNode(startNode)));
      }

      Element endNode = Tool.getChildNodeByTagName(elem, "end");

      if(endNode != null) {
         setEndField((ChartRef) AbstractDataRef.createDataRef(Tool.getFirstChildNode(endNode)));
      }

      Element milestoneNode = Tool.getChildNodeByTagName(elem, "milestone");

      if(milestoneNode != null) {
         setMilestoneField(
            (ChartRef) AbstractDataRef.createDataRef(Tool.getFirstChildNode(milestoneNode)));
      }
   }

   /**
    * Clone this object.
    * @return the cloned object.
    */
   @Override
   public VSChartInfo clone() {
       try {
         GanttVSChartInfo obj = (GanttVSChartInfo) super.clone();

         if(obj == null) {
            return null;
         }

         if(startField != null) {
            obj.startField = (VSChartRef) startField.clone();
         }

         if(endField != null) {
            obj.endField = (VSChartRef) endField.clone();
         }

         if(milestoneField != null) {
            obj.milestoneField = (VSChartRef) milestoneField.clone();
         }

         return obj;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone GanttVSChartInfo", ex);
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

      if(!(obj instanceof GanttVSChartInfo)) {
         return false;
      }

      GanttVSChartInfo chartInfo = (GanttVSChartInfo) obj;

      VSChartRef startField2 = chartInfo.startField;

      if(!Tool.equalsContent(startField, startField2)) {
         return false;
      }

      VSChartRef endField2 = chartInfo.endField;

      if(!Tool.equalsContent(endField, endField2)) {
         return false;
      }

      VSChartRef milestoneField2 = chartInfo.milestoneField;

      if(!Tool.equalsContent(milestoneField, milestoneField2)) {
         return false;
      }

      return true;
   }

   @Override
   public void renameDepended(String oname, String nname, Viewsheet vs) {
      super.renameDepended(oname, nname, vs);

      if(startField != null) {
         startField.renameDepended(oname, nname, vs);
      }

      if(endField != null) {
         endField.renameDepended(oname, nname, vs);
      }

      if(milestoneField != null) {
         milestoneField.renameDepended(oname, nname, vs);
      }
   }

   @Override
   public List getDynamicValues() {
      List list = super.getDynamicValues();

      if(startField != null) {
         list.addAll(startField.getDynamicValues());
      }

      if(endField != null) {
         list.addAll(endField.getDynamicValues());
      }

      if(milestoneField != null) {
         list.addAll(milestoneField.getDynamicValues());
      }

      return list;
   }

   @Override
   public synchronized void update(Viewsheet vs, ColumnSelection columns,
                                   boolean sep, VSChartDimensionRef pdim,
                                   String source, DateComparisonInfo dcInfo)
   {
      // first to update runtime fields, or statement getRTFields() in
      // MergedVSChartInfo.update() will get the wrong runtime fields
      rstartField = null;
      rendField = null;
      rmilestoneField = null;

      if(startField != null) {
         List list = startField.update(vs, columns);

         if(list.size() > 0) {
            rstartField = (VSChartRef) list.get(0);
         }
      }

      if(endField != null) {
         List list = endField.update(vs, columns);

         if(list.size() > 0) {
            rendField = (VSChartRef) list.get(0);
         }
      }

      if(milestoneField != null) {
         List list = milestoneField.update(vs, columns);

         if(list.size() > 0) {
            rmilestoneField = (VSChartRef) list.get(0);
         }
      }

      super.update(vs, columns, sep, pdim, source, dcInfo);
   }

   /**
    * Set the runtime end field.
    * @param field the specified runtime end field.
    */
   public void setRTEndField(VSChartRef field) {
      this.rendField = field;
   }

   /**
    * Set the runtime start field.
    * @param field the specified runtime start field.
    */
   public void setRTStartField(VSChartRef field) {
      this.rstartField = field;
   }

   /**
    * Set the runtime milestone field.
    * @param field the specified runtime milestone field.
    */
   public void setRTMilestoneField(VSChartRef field) {
      this.rmilestoneField = field;
   }

   /**
    * Get the runtime start field.
    * @return the runtime start field.
    */
   @Override
   public synchronized ChartRef getRTStartField() {
      return rstartField;
   }

   /**
    * Get the runtime end field.
    * @return the runtime end field.
    */
   @Override
   public synchronized ChartRef getRTEndField() {
      return rendField;
   }

   /**
    * Get the runtime milestone field.
    * @return the runtime milestone field.
    */
   @Override
   public synchronized ChartRef getRTMilestoneField() {
      return rmilestoneField;
   }

   /**
    * Get merge info special fields, not including x, y and aesthetic fields.
    */
   @Override
   public VSDataRef[] getMergedRTFields() {
      List list = new ArrayList();

      if(rstartField != null) {
         list.add(rstartField);
      }

      if(rendField != null) {
         list.add(rendField);
      }

      if(rmilestoneField != null) {
         list.add(rmilestoneField);
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
      ChartRef dm = startField != null ? startField : endField;
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
         new ChartRef[] {rstartField, rendField, rmilestoneField} :
         new ChartRef[] {startField, endField, milestoneField};

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
         new ChartRef[] {rstartField, rendField, rmilestoneField} :
         new ChartRef[] {startField, endField, milestoneField};

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

      if(startField != null && Tool.equals(startField.getFullName(), oldFiled.getFullName())) {
         startField = (VSChartRef) newFiled;

         return true;
      }

      if(endField != null && Tool.equals(endField.getFullName(), oldFiled.getFullName())) {
         endField = (VSChartRef) newFiled;

         return true;
      }

      if(milestoneField != null && Tool.equals(milestoneField.getFullName(), oldFiled.getFullName())) {
         milestoneField = (VSChartRef) newFiled;

         return true;
      }

      return super.replaceField(oldFiled, newFiled);
   }

   /**
    * Gantt is always inverted.
    */
   @Override
   public boolean isInvertedGraph() {
      return true;
   }

   @Override
   public boolean supportsColorFieldFrame() {
      return true;
   }

   @Override
   public boolean supportsShapeFieldFrame() {
      return true;
   }

   @Override
   public boolean supportsSizeFieldFrame() {
      return true;
   }

   @Override
   public List<ChartAggregateRef> getAestheticAggregateRefs(boolean runtime) {
      List<ChartAggregateRef> list = super.getAestheticAggregateRefs(runtime);
      list.addAll(getGanttFields(runtime, false));
      return list;
   }

   @Override
   public boolean isMultiAesthetic() {
      return true;
   }

   @Override
   public boolean supportUpdateChartType() {
      return rstartField != null || rendField != null || rmilestoneField != null;
   }

   private VSChartRef startField;
   private VSChartRef endField;
   private VSChartRef milestoneField;
   private VSChartRef rstartField;
   private VSChartRef rendField;
   private VSChartRef rmilestoneField;

   private static final Logger LOG = LoggerFactory.getLogger(GanttVSChartInfo.class);
}
