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
package inetsoft.uql.viewsheet.graph;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.erm.AbstractDataRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.aesthetic.*;
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
public class RelationVSChartInfo extends MergedVSChartInfo implements RelationChartInfo {
   /**
    * Constructor.
    */
   public RelationVSChartInfo() {
      super();
      nodeColorFrame = new StaticColorFrameWrapper();
      nodeSizeFrame = new StaticSizeFrameWrapper();
   }

   /**
    * Set the source field.
    */
   @Override
   public void setSourceField(ChartRef field) {
      this.sourceField = (VSChartRef) field;
   }

   /**
    * Set the target field.
    */
   @Override
   public void setTargetField(ChartRef field) {
      this.targetField = (VSChartRef) field;
   }

   /**
    * Get the source field.
    */
   @Override
   public ChartRef getSourceField() {
      return this.sourceField;
   }

   /**
    * Get the target field.
    */
   @Override
   public ChartRef getTargetField() {
      return this.targetField;
   }

   /**
    * Get all the binding refs.
    * @param runtime if viewsheet in run time mode.
    */
   @Override
   public ChartRef[] getBindingRefs(boolean runtime) {
      ChartRef[] refs = super.getBindingRefs(runtime);
      List<ChartRef> list = new ArrayList<>(Arrays.asList(refs));

      ChartRef sourceField = runtime ? getRTSourceField() : getSourceField();

      if(sourceField != null) {
         list.add(sourceField);
      }

      ChartRef targetField = runtime ? getRTTargetField() : getTargetField();

      if(targetField != null) {
         list.add(targetField);
      }

      return list.toArray(new ChartRef[0]);
   }

   /**
    * Get all fields, including x, y and aesthetic fields.
    */
   @Override
   public VSDataRef[] getFields() {
      VSDataRef[] refs = super.getFields();
      List<VSDataRef> list = new ArrayList();

      if(sourceField != null) {
         list.add(sourceField);
      }

      if(targetField != null) {
         list.add(targetField);
      }

      DataRef ref = (nodeColorField != null) ? nodeColorField.getDataRef() : null;

      if(ref != null) {
         list.add((VSDataRef) ref);
      }

      ref = (nodeSizeField != null) ? nodeSizeField.getDataRef() : null;

      if(ref != null) {
         list.add((VSDataRef) ref);
      }

      VSDataRef[] nrefs = new VSDataRef[refs.length + list.size()];
      System.arraycopy(refs, 0, nrefs, 0, refs.length);

      for(int i = 0; i < list.size(); i++) {
         nrefs[refs.length + i] = list.get(i);
      }

      return nrefs;
   }

   /**
    * Remove the chart binding fields.
    */
   @Override
   public void removeFields() {
      super.removeFields();

      sourceField = null;
      targetField = null;
      nodeColorField = null;
      nodeSizeField = null;
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

   @Override
   public AestheticRef getNodeColorField() {
      return nodeColorField;
   }

   @Override
   public void setNodeColorField(AestheticRef field) {
      this.nodeColorField = field;
   }

   @Override
   public AestheticRef getNodeSizeField() {
      return nodeSizeField;
   }

   @Override
   public void setNodeSizeField(AestheticRef field) {
      this.nodeSizeField = field;
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(sourceField != null) {
         writer.println("<source>");
         sourceField.writeXML(writer);
         writer.println("</source>");
      }

      if(targetField != null) {
         writer.println("<target>");
         targetField.writeXML(writer);
         writer.println("</target>");
      }

      if(rsourceField != null) {
         writer.println("<rsource>");
         rsourceField.writeXML(writer);
         writer.println("</rsource>");
      }

      if(rtargetField != null) {
         writer.println("<rtarget>");
         rtargetField.writeXML(writer);
         writer.println("</rtarget>");
      }

      if(getNodeColorField() != null && !getNodeColorField().isRuntime()) {
         writer.println("<nodeColor>");
         getNodeColorField().writeXML(writer);
         writer.println("</nodeColor>");
      }

      if(getNodeSizeField() != null && !getNodeSizeField().isRuntime()) {
         writer.println("<nodeSize>");
         getNodeSizeField().writeXML(writer);
         writer.println("</nodeSize>");
      }

      if(nodeColorFrame != null) {
         writer.print("<nodeColorVisualFrame>");
         nodeColorFrame.writeXML(writer);
         writer.print("</nodeColorVisualFrame>");
      }

      if(nodeSizeFrame != null) {
         writer.print("<nodeSizeVisualFrame>");
         nodeSizeFrame.writeXML(writer);
         writer.print("</nodeSizeVisualFrame>");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element sourceNode = Tool.getChildNodeByTagName(elem, "source");

      if(sourceNode != null) {
         sourceField = (VSChartRef) AbstractDataRef.createDataRef(
            Tool.getFirstChildNode(sourceNode));
      }

      Element targetNode = Tool.getChildNodeByTagName(elem, "target");

      if(targetNode != null) {
         targetField = (VSChartRef) AbstractDataRef.createDataRef(
            Tool.getFirstChildNode(targetNode));
      }

      Element nodeColorNode = Tool.getChildNodeByTagName(elem, "nodeColor");

      if(nodeColorNode != null) {
         nodeColorField = createAestheticRef();
         nodeColorField.parseXML(Tool.getFirstChildNode(nodeColorNode));
      }

      Element nodeSizeNode = Tool.getChildNodeByTagName(elem, "nodeSize");

      if(nodeSizeNode != null) {
         nodeSizeField = createAestheticRef();
         nodeSizeField.parseXML(Tool.getFirstChildNode(nodeSizeNode));
      }

      Element node = Tool.getChildNodeByTagName(elem, "nodeColorVisualFrame");

      if(node != null) {
         nodeColorFrame = (ColorFrameWrapper) VisualFrameWrapper.createVisualFrame(
            Tool.getFirstChildNode(node));
      }

      node = Tool.getChildNodeByTagName(elem, "nodeSizeVisualFrame");

      if(node != null) {
         nodeSizeFrame = (SizeFrameWrapper) VisualFrameWrapper.createVisualFrame(
            Tool.getFirstChildNode(node));
      }
   }

   @Override
   public VSChartInfo clone() {
       try {
         RelationVSChartInfo obj = (RelationVSChartInfo) super.clone();

         if(obj == null) {
            return null;
         }

         if(sourceField != null) {
            obj.sourceField = (VSChartRef) sourceField.clone();
         }

         if(targetField != null) {
            obj.targetField = (VSChartRef) targetField.clone();
         }

         if(nodeColorField != null) {
            obj.nodeColorField = (AestheticRef) nodeColorField.clone();
         }

         if(nodeSizeField != null) {
            obj.nodeSizeField = (AestheticRef) nodeSizeField.clone();
         }

         if(nodeColorFrame != null) {
            obj.nodeColorFrame = (ColorFrameWrapper) nodeColorFrame.clone();
         }

         if(nodeSizeFrame != null) {
            obj.nodeSizeFrame = (SizeFrameWrapper) nodeSizeFrame.clone();
         }

        return obj;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone TreeVSChartInfo", ex);
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

      if(!(obj instanceof RelationVSChartInfo)) {
         return false;
      }

      RelationVSChartInfo chartInfo = (RelationVSChartInfo) obj;

      VSChartRef sourceField2 = chartInfo.sourceField;

      if(!Tool.equalsContent(sourceField, sourceField2)) {
         return false;
      }

      VSChartRef targetField2 = chartInfo.targetField;

      if(!Tool.equalsContent(targetField, targetField2)) {
         return false;
      }

      if(!Tool.equalsContent(nodeColorField, chartInfo.nodeColorField)) {
         return false;
      }

      if(!Tool.equalsContent(nodeSizeField, chartInfo.nodeSizeField)) {
         return false;
      }

      ColorFrameWrapper nodeColorFrame2 = chartInfo.nodeColorFrame;

      if(!Tool.equals(nodeColorFrame, nodeColorFrame2)) {
         return false;
      }

      SizeFrameWrapper nodeSizeFrame2 = chartInfo.nodeSizeFrame;

      if(!Tool.equals(nodeSizeFrame, nodeSizeFrame2)) {
         return false;
      }

      return true;
   }

   @Override
   public void renameDepended(String oname, String nname, Viewsheet vs) {
      super.renameDepended(oname, nname, vs);

      if(sourceField != null) {
         sourceField.renameDepended(oname, nname, vs);
      }

      if(targetField != null) {
         targetField.renameDepended(oname, nname, vs);
      }
   }

   @Override
   public List getDynamicValues() {
      List list = super.getDynamicValues();

      if(sourceField != null) {
         list.addAll(sourceField.getDynamicValues());
      }

      if(targetField != null) {
         list.addAll(targetField.getDynamicValues());
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
      rsourceField = null;
      rtargetField = null;

      if(sourceField != null) {
         if(sourceField instanceof VSDimensionRef) {
            ((VSDimensionRef) sourceField).setGroupOthersValue("false");
         }

         List list = sourceField.update(vs, columns);

         if(list.size() > 0) {
            rsourceField = (VSChartRef) list.get(0);
         }
      }

      if(targetField != null) {
         if(targetField instanceof VSDimensionRef) {
            ((VSDimensionRef) targetField).setGroupOthersValue("false");
         }

         List list = targetField.update(vs, columns);

         if(list.size() > 0) {
            rtargetField = (VSChartRef) list.get(0);
         }
      }

      super.update(vs, columns, sep, pdim, source, dcInfo);
   }

   /**
    * Set the runtime target field.
    * @param field the specified runtime target field.
    */
   public void setRTTargetField(VSChartRef field) {
      this.rtargetField = field;
   }

   /**
    * Set the runtime source field.
    * @param field the specified runtime source field.
    */
   public void setRTSourceField(VSChartRef field) {
      this.rsourceField = field;
   }

   /**
    * Get the runtime source field.
    * @return the runtime source field.
    */
   @Override
   public synchronized ChartRef getRTSourceField() {
      return rsourceField;
   }

   /**
    * Get the runtime target field.
    * @return the runtime target field.
    */
   @Override
   public synchronized ChartRef getRTTargetField() {
      return rtargetField;
   }

   @Override
   public ColorFrameWrapper getNodeColorFrameWrapper() {
      return nodeColorFrame;
   }

   @Override
   public void setNodeColorFrameWrapper(ColorFrameWrapper color) {
      this.nodeColorFrame = color;
   }

   @Override
   public SizeFrameWrapper getNodeSizeFrameWrapper() {
      return nodeSizeFrame;
   }

   @Override
   public void setNodeSizeFrameWrapper(SizeFrameWrapper size) {
      this.nodeSizeFrame = size;
   }

   /**
    * Get merge info special fields, not including x, y and aesthetic fields.
    */
   @Override
   public VSDataRef[] getMergedRTFields() {
      List list = new ArrayList();

      if(rsourceField != null) {
         list.add(rsourceField);
      }

      if(rtargetField != null) {
         list.add(rtargetField);
      }

      VSDataRef[] arr = new VSDataRef[list.size()];
      list.toArray(arr);

      return arr;
   }

   @Override
   public VSDataRef[] getMergedRTAxisFields() {
      return new VSDataRef[0];
   }

   /**
    * Get the default measure.
    */
   @Override
   public String getDefaultMeasure() {
      ChartRef dm = sourceField != null ? sourceField : targetField;
      return dm == null ? null : dm.getFullName();
   }

   /**
    * Get the field by a name.
    * @param name the specified field name.
    */
   @Override
   public ChartRef getFieldByName(String name, boolean rt) {
      ChartRef[] refs = rt ?
         new ChartRef[] {rsourceField, rtargetField} :
         new ChartRef[] {sourceField, targetField};

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
         new ChartRef[] {rsourceField, rtargetField} :
         new ChartRef[] {sourceField, targetField};

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

      if(sourceField != null && Tool.equals(sourceField.getFullName(), oldFiled.getFullName())) {
         sourceField = (VSChartRef) newFiled;

         return true;
      }

      if(targetField != null && Tool.equals(targetField.getFullName(), oldFiled.getFullName())) {
         targetField = (VSChartRef) newFiled;

         return true;
      }

      return super.replaceField(oldFiled, newFiled);
   }

   /**
    * Tree is never inverted.
    */
   @Override
   public boolean isInvertedGraph() {
      return false;
   }

   @Override
   public List<DynamicValue> getHyperlinkDynamicValues() {
      List<DynamicValue> dvalues = super.getHyperlinkDynamicValues();

      if(sourceField != null) {
         dvalues.addAll(sourceField.getHyperlinkDynamicValues());
      }

      if(targetField != null) {
         dvalues.addAll(targetField.getHyperlinkDynamicValues());
      }

      return dvalues;
   }

   @Override
   public boolean supportUpdateChartType() {
      return rsourceField != null && rtargetField != null;
   }

   private VSChartRef sourceField;
   private VSChartRef targetField;
   private VSChartRef rsourceField;
   private VSChartRef rtargetField;
   private ColorFrameWrapper nodeColorFrame;
   private SizeFrameWrapper nodeSizeFrame;
   private AestheticRef nodeColorField;
   private AestheticRef nodeSizeField;

   private static final Logger LOG = LoggerFactory.getLogger(RelationVSChartInfo.class);
}
