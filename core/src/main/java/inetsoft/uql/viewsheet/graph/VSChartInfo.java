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

import inetsoft.util.data.CommonKVModel;
import inetsoft.graph.aesthetic.ColorFrame;
import inetsoft.graph.aesthetic.SizeFrame;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.erm.AbstractDataRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.aesthetic.SizeFrameWrapper;
import inetsoft.uql.viewsheet.graph.aesthetic.StaticColorFrameWrapper;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.ContentObject;
import inetsoft.util.Tool;
import inetsoft.web.binding.handler.VSChartHandler;

import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * VSChartInfo maintains binding info of chart.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class VSChartInfo extends AbstractChartInfo implements ContentObject, DateComparisonBinding {
   /**
    * Create the VSChartInfo according to class name.
    */
   public static VSChartInfo createVSChartInfo(Element elem) throws Exception {
      String cls = Tool.getAttribute(elem, "class");
      VSChartInfo info = (VSChartInfo) VSChartInfo.class.getClassLoader()
         .loadClass(cls).getConstructor().newInstance();
      info.parseXML(elem);

      return info;
   }

   /**
    * Constructor.
    */
   public VSChartInfo() {
      super();

      runtime = false;
      raesRefs = new VSDataRef[0];
   }

   /**
    * Write the xml segment to print writer.
    *
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<VSChartInfo");

      writeAttributes(writer);
      writer.print(">");
      writeContents(writer);

      writer.println("</VSChartInfo>");
   }

   /**
    * Write contents.
    *
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(raesRefs != null && raesRefs.length > 0) {
         writer.println("<raesRefs>");
         VSDataRef fld = (VSDataRef) getRTColorField();

         if(fld != null && !(fld instanceof AestheticRef && ((AestheticRef) fld).isRuntime())) {
            writer.println("<color>");
            fld.writeXML(writer);
            writer.println("</color>");
         }

         fld = (VSDataRef) getRTShapeField();

         if(fld != null) {
            writer.println("<shape>");
            fld.writeXML(writer);
            writer.println("</shape>");
         }

         fld = (VSDataRef) getRTSizeField();

         if(fld != null) {
            writer.println("<size>");
            fld.writeXML(writer);
            writer.println("</size>");
         }

         fld = (VSDataRef) getRTTextField();

         if(fld != null) {
            writer.println("<text>");
            fld.writeXML(writer);
            writer.println("</text>");
         }

         writer.println("</raesRefs>");
      }

      if(periodRef != null) {
         writer.println("<periodRef>");
         periodRef.writeXML(writer);
         writer.println("</periodRef>");
      }

      if(geoCols != null) {
         writer.println("<geoCols>");
         geoCols.writeXML(writer);
         writer.println("</geoCols>");
      }

      if(toolTipValue != null && getToolTipValue() != null) {
         writer.println("<toolTipValue><![CDATA[");
         writer.println(getToolTipValue());
         writer.println("]]></toolTipValue>");
      }

      if(toolTipValue != null && getToolTip() != null) {
         writer.println("<toolTip><![CDATA[");
         writer.println(getToolTip());
         writer.println("]]></toolTip>");
      }

      if(combinedTooltip != null && combinedTooltip.getDValue() != null) {
         writer.println("<combinedToolTipValue><![CDATA[");
         writer.println(combinedTooltip.getDValue());
         writer.println("]]></combinedToolTipValue>");
      }

      if(combinedTooltip != null && combinedTooltip.getRValue() != null) {
         writer.println("<combinedTooltip><![CDATA[");
         writer.println(combinedTooltip.getRValue());
         writer.println("]]></combinedTooltip>");
      }

      if(rPathRef != null) {
         writer.println("<rPathRef>");
         rPathRef.writeXML(writer);
         writer.println("</rPathRef>");
      }

      if(rDateComparisonRefs != null) {
         writer.println("<rDateComparisonRefs>");

         for(ChartRef ref : rDateComparisonRefs) {
            if(ref == null) {
               continue;
            }

            ref.writeXML(writer);
         }

         writer.println("</rDateComparisonRefs>");
      }
   }

   /**
    * Parse contents.
    *
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element rxsnode = Tool.getChildNodeByTagName(elem, "rxrefs");
      List<ChartRef> rxlist = new ArrayList<>();

      if(rxsnode != null) {
         NodeList rxnodes = Tool.getChildNodesByTagName(rxsnode, "dataRef");

         for(int i = 0; i < rxnodes.getLength(); i++) {
            Element rxnode = (Element) rxnodes.item(i);
            ChartRef ref = (ChartRef) AbstractDataRef.createDataRef(rxnode);
            rxlist.add(ref);
         }
      }

      Element rysnode = Tool.getChildNodeByTagName(elem, "ryrefs");
      List<ChartRef> rylist = new ArrayList<>();

      if(rysnode != null) {
         NodeList rynodes = Tool.getChildNodesByTagName(rysnode, "dataRef");

         for(int i = 0; i < rynodes.getLength(); i++) {
            Element rynode = (Element) rynodes.item(i);
            ChartRef ref = (ChartRef) AbstractDataRef.createDataRef(rynode);
            rylist.add(ref);
         }
      }

      // find the period ref so format changes on period axis won't be lost
      initPeriodRef(rxlist, rylist);

      if(periodRef == null) {
         Element periodNode = Tool.getChildNodeByTagName(elem, "periodRef");

         if(periodNode != null) {
            periodNode = Tool.getChildNodeByTagName(periodNode, "dataRef");
            periodRef = (ChartRef) AbstractDataRef.createDataRef(periodNode);
         }
      }

      // @by larryl, runtime refs are re-generated in update(), don't remember
      // otherwise the aeshtetic/descriptor change will not take effect
      // setRTXFields((ChartRef[]) list.toArray(new VSChartRef[list.size()]));
      // setRTYFields((ChartRef[]) list.toArray(new VSChartRef[list.size()]));

      Element rgsnode = Tool.getChildNodeByTagName(elem, "rgrefs");
      List<ChartRef> list = new ArrayList<>();

      if(rgsnode != null) {
         NodeList rgnodes = Tool.getChildNodesByTagName(rgsnode, "dataRef");

         for(int i = 0; i < rgnodes.getLength(); i++) {
            Element rgnode = (Element) rgnodes.item(i);
            ChartRef ref = (ChartRef) AbstractDataRef.createDataRef(rgnode);
            list.add(ref);
         }
      }

      setRTGroupFields(list.toArray(new ChartRef[0]));

      Element node = Tool.getChildNodeByTagName(elem, "geoCols");

      if(node != null) {
         node = Tool.getChildNodeByTagName(node, "ColumnSelection");

         if(node != null) {
            geoCols = new ColumnSelection();
            geoCols.parseXML(node);
         }
      }

      node = Tool.getChildNodeByTagName(elem, "toolTipValue");
      node = node == null ? Tool.getChildNodeByTagName(elem, "toolTip") : node;

      if(Tool.getValue(node) != null) {
         this.setToolTipValue(Tool.getValue(node));
      }

      node = Tool.getChildNodeByTagName(elem, "combinedToolTipValue");
      node = node == null ? Tool.getChildNodeByTagName(elem, "combinedToolTip") : node;

      if(Tool.getValue(node) != null) {
         this.combinedTooltip.setDValue(Tool.getValue(node));
      }

      node = Tool.getChildNodeByTagName(elem, "rDateComparisonRefs");
      rDateComparisonRefs = new ChartRef[0];

      if(node != null) {
         NodeList nodes = Tool.getChildNodesByTagName(node, "dataRef");

         if(nodes != null) {
            rDateComparisonRefs = new ChartRef[nodes.getLength()];
         }

         for(int i = 0; i < nodes.getLength(); i++) {
            rDateComparisonRefs[i] =
               (ChartRef) AbstractDataRef.createDataRef((Element) nodes.item(i));
         }
      }
   }

   /**
    * Find the period ref from runtime x/y axis refs.
    */
   private void initPeriodRef(List<ChartRef> rx, List<ChartRef> ry) {
      periodRef = null;

      if(!initPeriodRef(rx, true)) {
         initPeriodRef(ry, false);
      }
   }

   /**
    * Find the period ref from runtime refs.
    */
   private boolean initPeriodRef(List<ChartRef> rtRefs, boolean xAxis) {
      int cnt = xAxis ? getXFieldCount() : getYFieldCount();

      if(rtRefs.size() == 0) {
         return false;
      }

      ChartRef ref = rtRefs.get(0);

      if(!isDateDim(ref)) {
         return false;
      }

      if(cnt == 0) {
         periodRef = ref;

         return true;
      }

      ChartRef axisRef = xAxis ? getXField(0) : getYField(0);

      if(!Tool.equals(ref.getFullName(), axisRef.getFullName())) {
         periodRef = ref;

         return true;
      }

      return false;
   }

   /**
    * Check whether current object is a XDimension date ref.
    */
   private boolean isDateDim(Object ref) {
      if(ref instanceof XDimensionRef) {
         DataRef dref = ((XDimensionRef) ref).getDataRef();

         if(dref != null) {
            return XSchema.isDateType(
               ((XDimensionRef) ref).getDataRef().getDataType());
         }
      }

      return false;
   }

   /**
    * Clone this object.
    *
    * @return the cloned object.
    */
   @Override
   public VSChartInfo clone() {
      try {
         VSChartInfo obj = (VSChartInfo) super.clone();

         obj.geoCols = (ColumnSelection) geoCols.clone();
         obj.setRTYFields(deepCloneArray(getRTYFields()));
         obj.setRTXFields(deepCloneArray(getRTXFields()));
         obj.setRTGroupFields(deepCloneArray(getRTGroupFields()));

         if(toolTipValue != null) {
            obj.toolTipValue = (DynamicValue) toolTipValue.clone();
         }

         if(combinedTooltip != null) {
            obj.combinedTooltip = (DynamicValue) combinedTooltip.clone();
         }

         if(periodRef != null) {
            obj.periodRef = (ChartRef) periodRef.clone();
         }

         if(rDateComparisonRefs != null) {
            obj.rDateComparisonRefs = deepCloneArray(rDateComparisonRefs);
         }

         return obj;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone VSChartInfo", ex);
         return null;
      }
   }

   /**
    * Deep clone array.
    */
   private static ChartRef[] deepCloneArray(ChartRef[] refs) {
      ChartRef[] arr = new ChartRef[refs.length];

      for(int i = 0; i < arr.length; i++) {
         arr[i] = (ChartRef) refs[i].clone();
      }

      return arr;
   }

   /**
    * Check if equals another object in content.
    *
    * @param obj the specified object.
    *
    * @return <tt>true</tt> if equals the object in content, <tt>false</tt>
    * otherwise.
    */
   public boolean equals(Object obj) {
      return equalsContent(obj);
   }

   /**
    * Print the key to identify this content object. If the keys of two content
    * objects are equal, the content objects are equal too.
    */
   @Override
   public boolean printKey(PrintWriter writer) throws Exception {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Check if equals another object in content.
    *
    * @param obj the specified object.
    *
    * @return <tt>true</tt> if equals the object in content, <tt>false</tt>
    * otherwise.
    */
   @Override
   public boolean equalsContent(Object obj) {
      if(!super.equalsContent(obj)) {
         return false;
      }

      VSChartInfo chartInfo = (VSChartInfo) obj;

      // fix bug1352359465621
      // since the runtime fields are not generate when parseContents() now,
      // so it shoud not to compare runtime info when compare content
      // if it is not generated
      ChartRef[] rxrefs2 = chartInfo.getRTXFields();
      ChartRef[] rxrefs = getRTXFields();

      if(rxrefs.length != rxrefs2.length) {
         return false;
      }

      for(int i = 0; i < rxrefs.length; i++) {
         if(!Tool.equalsContent(rxrefs[i], rxrefs2[i])) {
            return false;
         }
      }

      ChartRef[] ryrefs2 = chartInfo.getRTYFields();
      ChartRef[] ryrefs = getRTYFields();

      if(ryrefs.length != ryrefs2.length) {
         return false;
      }

      for(int i = 0; i < ryrefs.length; i++) {
         if(!Tool.equalsContent(ryrefs[i], ryrefs2[i])) {
            return false;
         }
      }

      ChartRef[] rgrefs2 = chartInfo.getRTGroupFields();
      ChartRef[] rgrefs = getRTGroupFields();

      if(rgrefs2.length != rgrefs.length) {
         return false;
      }

      for(int i = 0; i < rgrefs.length; i++) {
         if(!Tool.equalsContent(rgrefs[i], rgrefs2[i])) {
            return false;
         }
      }

      if(!geoCols.equals(chartInfo.geoCols)) {
         return false;
      }

      if(!Tool.equals(toolTipValue, chartInfo.toolTipValue) ||
         !Tool.equals(getToolTip(), chartInfo.getToolTip()))
      {
         return false;
      }

      if(!Tool.equals(combinedTooltip, chartInfo.combinedTooltip) ||
         !Tool.equals(isCombinedToolTip(), chartInfo.isCombinedToolTip()))
      {
         return false;
      }

      if(!Tool.equals(chartInfo.rmapType, rmapType)) {
         return false;
      }

      ChartRef[] dcRef = chartInfo.getRuntimeDateComparisonRefs();
      ChartRef[] dcRef2 = getRuntimeDateComparisonRefs();

      if(!Tool.equalsContent(dcRef, dcRef2)) {
         return false;
      }

      return true;
   }

   /**
    * Rename the depended. This method should be called when an assembly or
    * other named variables are renamed. It updates of the dynamic references
    * to use the new name.
    *
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   public void renameDepended(String oname, String nname, Viewsheet vs) {
      for(AestheticRef aref : getAestheticRefs(false)) {
         ((VSAestheticRef) aref).renameDepended(oname, nname, vs);
      }

      for(int i = 0; i < getXFieldCount(); i++) {
         VSChartRef cref = (VSChartRef) getXField(i);
         cref.renameDepended(oname, nname, vs);
      }

      for(int i = 0; i < getYFieldCount(); i++) {
         VSChartRef cref = (VSChartRef) getYField(i);
         cref.renameDepended(oname, nname, vs);
      }

      for(int i = 0; i < getGroupFieldCount(); i++) {
         VSChartRef cref = (VSChartRef) getGroupField(i);
         cref.renameDepended(oname, nname, vs);
      }

      for(int i = 0; i < geoCols.getAttributeCount(); i++) {
         DataRef ref = geoCols.getAttribute(i);

         if(!(ref instanceof VSChartRef)) {
            continue;
         }

         VSChartRef cref = (VSChartRef) ref;
         cref.renameDepended(oname, nname, vs);
      }

      VSChartRef pathRef = (VSChartRef) getPathField();

      if(pathRef != null) {
         pathRef.renameDepended(oname, nname, vs);
      }
   }

   /**
    * Get the dynamic property values.
    *
    * @return the dynamic values.
    */
   public List<DynamicValue> getDynamicValues() {
      List<DynamicValue> list = new ArrayList<>();

      AestheticRef[][] arefs = { getAestheticRefs(true), getAestheticRefs(false) };

      for(AestheticRef[] arefs2 : arefs) {
         for(AestheticRef aref : arefs2) {
            list.addAll(((VSAestheticRef) aref).getDynamicValues());
         }
      }

      for(int i = 0; i < getXFieldCount(); i++) {
         VSChartRef cref = (VSChartRef) getXField(i);
         list.addAll(cref.getDynamicValues());
      }

      for(int i = 0; i < getYFieldCount(); i++) {
         VSChartRef cref = (VSChartRef) getYField(i);
         list.addAll(cref.getDynamicValues());
      }

      for(int i = 0; i < getGroupFieldCount(); i++) {
         VSChartRef cref = (VSChartRef) getGroupField(i);
         list.addAll(cref.getDynamicValues());
      }

      for(int i = 0; i < geoCols.getAttributeCount(); i++) {
         DataRef ref = geoCols.getAttribute(i);

         if(ref instanceof VSChartGeoRef) {
            list.addAll(((VSChartGeoRef) ref).getDynamicValues());
         }
      }

      VSChartRef pathRef = (VSChartRef) getPathField();

      if(pathRef != null) {
         list.addAll(pathRef.getDynamicValues());
      }

      return list;
   }

   /**
    * Get the hyperlink dynamic property values.
    *
    * @return the dynamic values.
    */
   public List<DynamicValue> getHyperlinkDynamicValues() {
      List<DynamicValue> list = new ArrayList<>();

      AestheticRef[][] arefs = { getAestheticRefs(true), getAestheticRefs(false) };

      for(AestheticRef[] arefs2 : arefs) {
         for(AestheticRef aref : arefs2) {
            list.addAll(((VSAestheticRef) aref).getHyperlinkDynamicValues());
         }
      }

      for(int i = 0; i < getXFieldCount(); i++) {
         VSChartRef cref = (VSChartRef) getXField(i);
         list.addAll(cref.getHyperlinkDynamicValues());
      }

      for(int i = 0; i < getYFieldCount(); i++) {
         VSChartRef cref = (VSChartRef) getYField(i);
         list.addAll(cref.getHyperlinkDynamicValues());
      }

      for(int i = 0; i < getGroupFieldCount(); i++) {
         VSChartRef cref = (VSChartRef) getGroupField(i);
         list.addAll(cref.getHyperlinkDynamicValues());
      }

      for(int i = 0; i < geoCols.getAttributeCount(); i++) {
         DataRef ref = geoCols.getAttribute(i);

         if(ref instanceof VSChartGeoRef) {
            list.addAll(((VSChartGeoRef) ref).getHyperlinkDynamicValues());
         }
      }

      VSChartRef pathRef = (VSChartRef) getPathField();

      if(pathRef != null) {
         list.addAll(pathRef.getHyperlinkDynamicValues());
      }

      ChartRef periodRef = getPeriodField();

      if(periodRef instanceof VSChartRef) {
         list.addAll(((VSChartRef) periodRef).getHyperlinkDynamicValues());
      }

      ChartRef[] dcRefs = getRuntimeDateComparisonRefs();

      if(dcRefs != null) {
         for(ChartRef chartRef : dcRefs) {
            if(!(chartRef instanceof VSChartRef)) {
               continue;
            }

            list.addAll(((VSChartRef) chartRef).getHyperlinkDynamicValues());
         }
      }

      return list;
   }

   /**
    * Check if is runtime.
    *
    * @return <tt>true</tt> if runtime, <tt>false</tt> otherwise.
    */
   public boolean isRuntime() {
      return runtime;
   }

   /**
    * Set whether is runtime.
    *
    * @param runtime <tt>true</tt> if runtime, <tt>false</tt> otherwise.
    */
   public void setRuntime(boolean runtime) {
      this.runtime = runtime;
   }

   /**
    * Check if need reset shape frame.
    *
    * @return <tt>true</tt> if need reset shape, <tt>false</tt> otherwise.
    */
   public boolean isNeedResetShape() {
      return resetShape;
   }

   /**
    * Set whether need reset shape frame.
    *
    * @param resetShape <tt>true</tt> if need reset shape,
    *                   <tt>false</tt> otherwise.
    */
   public void setNeedResetShape(boolean resetShape) {
      this.resetShape = resetShape;
   }

   /**
    * Get the runtime map type.
    *
    * @return the map type.
    */
   public String getRTMapType() {
      return rmapType;
   }

   /**
    * Set the runtime map type.
    *
    * @param type the map type.
    */
   public void setRTMapType(String type) {
      this.rmapType = type;
   }

   /**
    * Get runtime axis descriptor from this ref.
    *
    * @return the axis descriptor.
    */
   public AxisDescriptor getRTAxisDescriptor() {
      return rdesc;
   }

   /**
    * Set the runtime axis descriptor into this ref.
    *
    * @param desc the axis descriptor.
    */
   public void setRTAxisDescriptor(AxisDescriptor desc) {
      this.rdesc = desc;
   }

   /**
    * Get runtime secondary axis descriptor.
    */
   public AxisDescriptor getRTAxisDescriptor2() {
      return rdesc2;
   }

   /**
    * Set the runtime axis descriptor.
    */
   public void setRTAxisDescriptor2(AxisDescriptor desc) {
      this.rdesc2 = desc;
   }

   /**
    * Get the runtime aesthetic fields.
    *
    * @return all runtime aesthetic fields.
    */
   public VSDataRef[] getRTAestheticFields() {
      List<VSDataRef> list = new ArrayList<>();
      VSDataRef fld = (VSDataRef) getRTColorField();

      // add color
      if(fld != null) {
         // clearRanking(fld);
         list.add(fld);
      }

      fld = (VSDataRef) getRTShapeField();

      // add shape
      if(fld != null) {
         // clearRanking(fld);
         list.add(fld);
      }

      // add size
      fld = (VSDataRef) getRTSizeField();

      if(fld != null) {
         // clearRanking(fld);
         list.add(fld);
      }

      // add text
      fld = (VSDataRef) getRTTextField();

      if(fld != null) {
         // clearRanking(fld);
         list.add(fld);
      }

      VSDataRef[] arr = new VSDataRef[list.size()];
      list.toArray(arr);
      return arr;
   }

   /**
    * Update the info to fill in runtime value.
    *
    * @param vs      the specified viewsheet.
    * @param columns the specified column selection.
    * @param sep     true if the chart is separated, false otherwise.
    * @param pdim    the specified dimension for period comparison.
    */
   public void update(Viewsheet vs, ColumnSelection columns,
                                   boolean sep, VSChartDimensionRef pdim,
                                   String source, DateComparisonInfo dcInfo)
   {
      synchronized(this) {
         runtime = true;
         clearDCRuntime();
         String oname = getColorField() != null && getColorField().getDataRef() != null ?
            getColorField().getDataRef().getName() : null;

         // update both design time and runtime aesthetic refs to make sure
         // the dataRef is set in both (for composer and viewer/runtime)
         for(AestheticRef aref : getAestheticRefs(false)) {
            try {
               ((VSAestheticRef) aref).update(vs, columns);
            }
            catch(ColumnNotFoundException ex) {
               if(((VSChartRef) aref.getDataRef()).isScript()) {
                  throw ex;
               }
            }
         }

         String nname = getColorField() != null && getColorField().getDataRef() != null ?
            getColorField().getDataRef().getName() : null;

         if(!Tool.equals(nname, oname)) {
            vs.clearSharedFrames();
            VSChartHandler.clearColorFrame(this, false, null);
         }

         for(AestheticRef aref : getAestheticRefs(true)) {
            try {
               ((VSAestheticRef) aref).update(vs, columns);
            }
            catch(ColumnNotFoundException ex) {
               if(((VSChartRef) aref.getDataRef()).isScript()) {
                  throw ex;
               }
            }
         }

         List<VSChartRef> list = new ArrayList<>();

         for(int i = 0; i < getXFieldCount(); i++) {
            VSChartRef cref = (VSChartRef) getXField(i);

            try {
               // @by stephenwebster, For Bug #1156
               // Support drilling on charts without removing the ref from the list
               // of xFields.  Instead, just rely on visibility.  Default will
               // always be true unless the field is drilled in DrillEvent
               if(cref.isDrillVisible()) {
                  List<VSChartRef> refs = toVSChartRefList(cref.update(vs, columns));
                  fixRuntimeDimensionAxis(cref, refs);

                  // mekko chart doesn't support facet, so just use the last level for X.
                  if(GraphTypes.isMekko(getRTChartType()) && refs.size() > 1) {
                     VSChartRef lastXRefs = refs.get(refs.size() - 1);
                     refs.clear();
                     refs.add(lastXRefs);
                  }

                  list.addAll(getRTRefsWithDescriptors(refs, getRTXFields()));
               }
            }
            catch(ColumnNotFoundException ex) {
               if(cref.isScript() || cref.isVariable()) {
                  list.add(cref);

                  if(cref.isScript()) {
                     throw ex;
                  }
               }
            }
         }

         ChartRef[] temp = new ChartRef[list.size()];
         list.toArray(temp);
         setRTXFields(temp);

         list = new ArrayList<>();

         for(int i = 0; i < getYFieldCount(); i++) {
            VSChartRef cref = (VSChartRef) getYField(i);

            try {
               // @by stephenwebster, For Bug #1156
               // Support drilling on charts without removing the ref from the list
               // of yFields.  Instead, just rely on visibility.  Default will
               // always be true unless the field is drilled in DrillEvent
               if(cref.isDrillVisible()) {
                  list.addAll(getRTRefsWithDescriptors(toVSChartRefList(cref.update(vs, columns)),
                     getRTYFields()));
               }
            }
            catch(ColumnNotFoundException ex) {
               if(cref.isScript() || cref.isVariable()) {
                  list.add(cref);

                  if(cref.isScript()) {
                     throw ex;
                  }
               }
            }
         }

         temp = new ChartRef[list.size()];
         list.toArray(temp);
         setRTYFields(temp);

         raesRefs = getRTAestheticFields();
         list = new ArrayList<>();

         for(int i = 0; i < getGroupFieldCount(); i++) {
            VSChartRef cref = (VSChartRef) getGroupField(i);

            try {
               list.addAll(getRTRefsWithDescriptors(toVSChartRefList(cref.update(vs, columns)),
                  getRTGroupFields()));
            }
            catch(ColumnNotFoundException ex) {
               if(cref.isScript() || cref.isVariable()) {
                  list.add(cref);

                  if(cref.isScript()) {
                     throw ex;
                  }
               }
            }
         }

         temp = new ChartRef[list.size()];
         list.toArray(temp);
         setRTGroupFields(temp);

         // only not found the period dimension, period is true
         period = false;
         periodRef = pdim;

         if(pdim != null) {
            boolean inverted = !containYMeasure();

            if(getChartType() == GraphTypes.CHART_CANDLE ||
               getChartType() == GraphTypes.CHART_STOCK)
            {
               inverted = getDefaultMeasure() == null;
            }

            ChartRef[] ryrefs = inverted ? getRTXFields() : getRTYFields();
            ChartRef[] rxrefs = inverted ? getRTYFields() : getRTXFields();
            boolean found = false;

            for(ChartRef ref : ryrefs) {
               if(ref instanceof VSDimensionRef) {
                  VSDimensionRef tdim = (VSDimensionRef) ref;

                  if(pdim.equals(tdim) && tdim.getDateLevel() == pdim.getDateLevel()) {
                     found = true;
                     break;
                  }
               }
            }

            // add period dimension to y
            if(!found) {
               for(int i = rxrefs.length - 1; i >= 0; i--) {
                  ChartRef ref = rxrefs[i];

                  if(ref instanceof VSDimensionRef) {
                     VSDimensionRef tdim = (VSDimensionRef) ref;

                     if(pdim.equals(tdim)) {
                        tdim.setDateLevel(getPartLevel(tdim.getDateLevel(), pdim));
                        periodPartRef = tdim;
                        break;
                     }
                  }
               }

               ChartRef[] refs2 = new ChartRef[ryrefs.length + 1];

               System.arraycopy(ryrefs, 0, refs2, 1, ryrefs.length);
               refs2[0] = pdim;
               period = true;

               if(inverted) {
                  setRTXFields(refs2);
               }
               else {
                  setRTYFields(refs2);
               }
            }
         }

         // update type after the date grouping level is adjusted
         updateChartType(sep);

         VSChartRef pathRef = (VSChartRef) getPathField();
         rPathRef = null;

         if(pathRef != null) {
            try {
               List<VSChartRef> grefs = toVSChartRefList(pathRef.update(vs, columns));

               if(grefs.size() > 0) {
                  rPathRef = grefs.get(0);
               }
            }
            catch(ColumnNotFoundException ex) {
               if(pathRef.isScript() || pathRef.isVariable()) {
                  rPathRef = pathRef;

                  if(pathRef.isScript()) {
                     throw ex;
                  }
               }
            }
         }

         updateAggregateStatus();

         if(dcInfo != null && DateComparisonUtil.supportDateComparison(this, true)) {
            ChartDcProcessor dcProcessor = new ChartDcProcessor(this, dcInfo);
            dcProcessor.process(source, vs);
         }
         else {
            setRuntimeDateComparisonRefs(null);
         }

         DateComparisonUtil.syncWeekGroupingLevels(this, dcInfo);
      }
   }

   /**
    * Remove the run time color filed for date comparison.
    */
   private void clearDateComparisonRuntimeRef() {
      clearDateComparisonRuntimeRef(this);
      setRuntimeSizeFrame(null);
      dcTempGroups = null;

      VSDataRef[] aggs = getAggregateRefs();

      if(aggs != null) {
         Arrays.stream(aggs)
            .filter(VSChartAggregateRef.class::isInstance)
            .map(VSChartAggregateRef.class::cast)
            .forEach(agg -> {
               agg.setRuntimeSizeframe(null);
               clearDateComparisonRuntimeRef(agg);
            });
      }
   }

   public void setRuntimeSizeFrame(SizeFrameWrapper runtimeSFrame) {
      this.runtimeSFrame = runtimeSFrame;
   }

   public SizeFrameWrapper getRuntimeSizeFrame() {
      return this.runtimeSFrame;
   }

   /**
    * Remove the run time color filed for date comparison.
    */
   private void clearDateComparisonRuntimeRef(ChartBindable chartBindable) {
      if(chartBindable == null) {
         return;
      }

      if(chartBindable.getColorField() != null && chartBindable.getColorField().isRuntime()) {
         chartBindable.setColorField(null);
      }

      if(chartBindable.getShapeField() != null && chartBindable.getShapeField().isRuntime()) {
         chartBindable.setShapeField(null);
      }

      if(chartBindable.getColorFrameWrapper() instanceof StaticColorFrameWrapper) {
         ((StaticColorFrameWrapper) chartBindable.getColorFrameWrapper()).setDcRuntimeColor(null);
      }
   }

   private static List<VSChartRef> toVSChartRefList(List<? super VSChartRef> refs) {
      return refs.stream()
                 .map(VSChartRef.class::cast)
                 .collect(Collectors.toCollection(ArrayList::new));
   }

   /**
    * for dimension expanded by dynamic value, the axis descriptor is stored in
    * the original dimension. we make sure the shared descriptor is in rt dim
    * so setting it (hide/show) would work. (42200)
    * @param dimRef define dimension ref.
    * @param runtimeRefs runtime dimension refs.
    */
   private void fixRuntimeDimensionAxis(VSChartRef dimRef, List<VSChartRef> runtimeRefs) {
      if(dimRef instanceof VSChartDimensionRef) {
         VSChartDimensionRef chartDimensionRef = (VSChartDimensionRef) dimRef;

         if(VSUtil.isDynamicValue(chartDimensionRef.getGroupColumnValue()) ) {
            for(int i = 1; i < runtimeRefs.size(); i++) {
               VSChartRef ref = runtimeRefs.get(i);

               if(!(ref instanceof VSChartDimensionRef)) {
                  continue;
               }

               ref.setAxisDescriptor(dimRef.getAxisDescriptor());
            }
         }
      }
   }

   @Override
   public int getDimensionDateLevel(XDimensionRef dref) {
      if(dref instanceof VSDimensionRef) {
         VSDimensionRef vdref = (VSDimensionRef) dref;

         try {
            return Integer.parseInt(vdref.getDateLevelValue());
         }
         catch(Exception ex) {
            // ignore it
         }
      }

      return super.getDimensionDateLevel(dref);
   }

   @Override
   protected boolean isDateDimension(XDimensionRef dref, int level) {
      if(dref instanceof VSDimensionRef) {
         VSDimensionRef vdref = (VSDimensionRef) dref;
         String type = dref.getDataType();
         return (vdref.isDynamic() ||
            (XSchema.isDateType(type) && DateRangeRef.isDateTime(level))) &&
            (vdref.getRefType() & DataRef.CUBE) == 0;
      }
      else {
         return super.isDateDimension(dref, level);
      }
   }

   @Override
   public boolean isSizeChanged(String... vars) {
      if(super.isSizeChanged(vars)) {
         return true;
      }

      if(getRuntimeDateComparisonRefs() == null || getRuntimeDateComparisonRefs().length == 0) {
         return false;
      }

      Set vset = new HashSet(Arrays.asList(vars));

      for(ChartAggregateRef aggr : getAestheticAggregateRefs(true)) {
         if((vset.isEmpty() || vset.contains(aggr.getFullName())) && aggr.isSizeChanged()) {
            return true;
         }
      }

      return false;
   }

   @Override
   public SizeFrame getSizeFrame() {
      return runtimeSFrame == null ? super.getSizeFrame() :
         (SizeFrame) runtimeSFrame.getVisualFrame();
   }

   @Override
   public SizeFrameWrapper getSizeFrameWrapper() {
      return runtimeSFrame == null ? super.getSizeFrameWrapper() : runtimeSFrame;
   }

   /**
    * Get runtime refs with the runtime axis/legend descriptors so the
    * changes made in the descriptor by script will not be lost after
    * the update.
    */
   private List<VSChartRef> getRTRefsWithDescriptors(List<VSChartRef> list, ChartRef[] rtRefs) {
      // before the script changes the runtime refs, if the update() is
      // callled after script is executed, the setting from the script will
      // be lost. here we copy the descriptors and other data structures
      // that could be changed from script to the new refs. if the runtime
      // ref infos shouldn't be kept, the clearRuntime() should be called
      boolean[] fixed = new boolean[list.size()];
      ChartRef oref = null;

      for(int i = 0; i < list.size(); i++) {
         VSChartRef ref = list.get(i);

         if(ref == null) {
            continue;
         }

         boolean found = false;

         for(ChartRef rtRef : rtRefs) {
            if(!(rtRef instanceof VSChartRef)) {
               continue;
            }

            // @by billh, we should compare the design time content instead of
            // column name only, so that we could differentiate between
            // sum(measure) and max(measure)
            // @by larryl, we shouldn't use equalsContent because we want to
            // ignore chart style differences. Use fullname should be sufficient
            // to distinguish measure and its formula
            if(Tool.equals(ref.getFullName(), rtRef.getFullName())) {
               found = true;

               if(oref == null) {
                  oref = rtRef;
               }

               copyDescriptors(ref, rtRef, false);
               break;
            }
         }

         if(found) {
            fixed[i] = true;
         }
      }

      if(oref != null) {
         for(int i = 0; i < list.size(); i++) {
            VSChartRef ref = list.get(i);

            if(ref != null & fixed[i]) {
               copyDescriptors(ref, oref, true);
            }

            // if the list.size > 1, the (runtime) refs are all generated from one
            // dynamic ref (e.g. drillMembers). if axis size is set, applying it to all
            // generated members (state -> state, city) may result in unexpected result.
            // since the axis size is set on the original (undrilled) member, we only
            // apply it at the 1st axis and clears it for the subsequent ones. (56529)
            if(i > 0 && oref instanceof VSDimensionRef && ref != null) {
               ref.getAxisDescriptor().setAxisWidth(0);
               ref.getAxisDescriptor().setAxisHeight(0);
               ref.setRTAxisDescriptor(null);
            }
         }
      }

      return list;
   }

   /**
    * Copy the descriptors from the runtime ref to the new ref.
    */
   private void copyDescriptors(VSChartRef ref, ChartRef rtRef, boolean clone) {
      if(clone) {
         rtRef = (ChartRef) rtRef.clone();
      }

      ref.setAxisDescriptor(rtRef.getAxisDescriptor());
      // RTAxisDescriptor may not be generated yet. copy regular descriptor so setting
      // from script is not lost. (62163)
      ref.setRTAxisDescriptor(((VSChartRef) rtRef).getRTAxisDescriptor());

      if(ref instanceof VSChartAggregateRef && rtRef instanceof VSChartAggregateRef) {
         VSChartAggregateRef aref = (VSChartAggregateRef) rtRef;
         VSChartAggregateRef nref = (VSChartAggregateRef) ref;

         // chart type set in the script
         if(aref.getChartType() != 0) {
            nref.setChartType(aref.getChartType());
         }

         if(aref.getColorField() != null && nref.getColorField() != null) {
            nref.getColorField().setLegendDescriptor(
               aref.getColorField().getLegendDescriptor());
         }

         if(aref.getShapeField() != null && nref.getShapeField() != null) {
            nref.getShapeField().setLegendDescriptor(
            aref.getShapeField().getLegendDescriptor());
         }

         if(aref.getSizeField() != null && nref.getSizeField() != null) {
            nref.getSizeField().setLegendDescriptor(
            aref.getSizeField().getLegendDescriptor());
         }

         nref.setTextFormat(aref.getTextFormat());
         nref.setColorFrameWrapper(aref.getColorFrameWrapper());
         nref.setShapeFrameWrapper(aref.getShapeFrameWrapper());
         nref.setLineFrameWrapper(aref.getLineFrameWrapper());
         nref.setTextureFrameWrapper(aref.getTextureFrameWrapper());
         nref.setSizeFrameWrapper(aref.getSizeFrameWrapper());
         nref.setColorField(aref.getColorField());
         nref.setShapeField(aref.getShapeField());
         nref.setSizeField(aref.getSizeField());
         nref.setTextField(aref.getTextField());
      }
   }

   /**
    * Get the date part level for a date level for period comparison.
    * @param pdim the period comparison date dimension.
    */
   private int getPartLevel(int level, VSDimensionRef pdim) {
      switch(level) {
      case DateRangeRef.QUARTER_INTERVAL:
         level = DateRangeRef.QUARTER_OF_YEAR_PART;
         break;
      case DateRangeRef.MONTH_INTERVAL:
         level = DateRangeRef.MONTH_OF_YEAR_PART;
         break;
      case DateRangeRef.WEEK_INTERVAL:
         level = DateRangeRef.WEEK_OF_YEAR_PART;
         break;
      case DateRangeRef.DAY_INTERVAL:
         level = DateRangeRef.DAY_OF_MONTH_PART;
         break;
      case DateRangeRef.HOUR_INTERVAL:
      case DateRangeRef.MINUTE_INTERVAL:
      case DateRangeRef.SECOND_INTERVAL:
      case DateRangeRef.NONE_INTERVAL:
         level = DateRangeRef.HOUR_OF_DAY_PART;
         break;
      }

      // the max date level for meaningful comparison
      int maxlevel = DateRangeRef.YEAR_INTERVAL;
      // if more than one items (e.g. month) is selected on each calendar
      boolean multi = pdim.getDates() != null && pdim.getDates().length > 2;

      switch(pdim.getDateLevel()) {
      case DateRangeRef.YEAR_INTERVAL:
         maxlevel = multi ? DateRangeRef.YEAR_INTERVAL
            : DateRangeRef.MONTH_OF_YEAR_PART;
         break;
      case DateRangeRef.MONTH_INTERVAL:
         maxlevel = multi ? DateRangeRef.MONTH_OF_YEAR_PART
            : DateRangeRef.DAY_OF_MONTH_PART;
         break;
      case DateRangeRef.WEEK_INTERVAL:
         maxlevel = multi ? DateRangeRef.DAY_OF_MONTH_PART
            : DateRangeRef.DAY_OF_WEEK_PART;
         break;
      case DateRangeRef.DAY_INTERVAL:
         maxlevel = multi ? DateRangeRef.DAY_OF_MONTH_PART
            : DateRangeRef.HOUR_OF_DAY_PART;
         break;
      }

      final int[] parts = {
         DateRangeRef.HOUR_OF_DAY_PART,
         DateRangeRef.DAY_OF_WEEK_PART,
         DateRangeRef.DAY_OF_MONTH_PART,
         DateRangeRef.WEEK_OF_YEAR_PART,
         DateRangeRef.MONTH_OF_YEAR_PART,
         DateRangeRef.QUARTER_OF_YEAR_PART,
         DateRangeRef.YEAR_INTERVAL
      };

      int idx = Math.min(indexOfPart(level, parts),
                         indexOfPart(maxlevel, parts));

      return parts[idx];
   }

   /**
    * Return ranking of parts.
    */
   private int indexOfPart(int level, int[] parts) {
      for(int i = 0; i < parts.length; i++) {
         if(parts[i] == level) {
            return i;
         }
      }

      return parts.length - 1;
   }

   /**
    * Update chart type by generating runtime chart type.
    * @param separated true if the chart types are maintained in aggregate,
    * false otherwise.
    */
   @Override
   public void updateChartType(boolean separated) {
      updateChartType(separated, getRTXFields(), getRTYFields());
   }

   /**
    * Create a new aesthetic ref.
    */
   @Override
   protected AestheticRef createAestheticRef() {
      return new VSAestheticRef();
   }

   /**
    * Get geographic column selection.
    */
   public ColumnSelection getGeoColumns() {
      return geoCols;
   }

   /**
    * Set geographic column selection.
    */
   public void setGeoColumns(ColumnSelection cols) {
      this.geoCols = cols;
   }

   /**
    * Update geo column selection.
    */
   public void updateGeoColumns(Viewsheet vs, ColumnSelection columns) {
      rGeoCols = new ColumnSelection();

      for(int i = 0; i < geoCols.getAttributeCount(); i++) {
         DataRef ref = geoCols.getAttribute(i);

         if(ref instanceof VSChartGeoRef) {
            VSChartGeoRef col = (VSChartGeoRef) ref;

            try {
               List<DataRef> rcols = col.update(vs, columns);

               for(DataRef rcol : rcols) {
                  rGeoCols.addAttribute(rcol);
               }
            }
            catch(Exception ex) {
               LOG.debug("Failed to update geo columns", ex);
            }
         }
         else {
            rGeoCols.addAttribute(ref);
         }
      }
   }

   /**
    * Get runtime geographic column selection.
    */
   public ColumnSelection getRTGeoColumns() {
      return rGeoCols;
   }

   /**
    * Check if the specified column is geographic.
    */
   public boolean isGeoColumn(String name) {
      return rGeoCols.getAttribute(name) != null;
   }

   /**
    * Set the tool tip.
    * @param toolTip the specified tool tip.
    */
   @Override
   public void setToolTip(String toolTip) {
      super.setToolTip(toolTip);
      toolTipValue.setRValue(toolTip);
   }

   /**
    * Get the tool tip.
    * @return the specified tool tip.
    */
   @Override
   public String getToolTip() {
      return toolTipValue.getRValue() != null ? toolTipValue.getRValue() + "" : null;
   }

   /**
    * Set the tool tip.
    * @param toolTip the specified tool tip.
    */
   public void setToolTipValue(String toolTip) {
      super.setToolTip(toolTip);
      toolTipValue.setDValue(toolTip);
   }

   /**
    * Get the tool tip.
    * @return the specified tool tip.
    */
   public String getToolTipValue() {
      return toolTipValue.getDValue();
   }

   /**
    * Set if the tool tip should display data from other lines in a line/area chart.
    */
   @Override
   public void setCombinedToolTip(boolean combinedTooltip) {
      this.combinedTooltip.setRValue(combinedTooltip);
   }

   public void clearCombinedTooltipValue() {
      this.combinedTooltip.setRValue(null);
   }

   /**
    * Get if the tool tip should display data from other lines in a line/area chart.
    */
   @Override
   public boolean isCombinedToolTip() {
      return combinedTooltip.getRValue() != null ?
         Boolean.valueOf(combinedTooltip.getRuntimeValue(true) + "") : false;
   }

   /**
    * Set if the tool tip should display data from other lines in a line/area chart.
    */
   public void setCombinedToolTipValue(boolean combinedTooltip) {
      this.combinedTooltip.setDValue(combinedTooltip + "");
   }

   /**
    * Get if the tool tip should display data from other lines in a line/area chart.
    */
   public boolean getCombinedToolTipValue() {
      return Boolean.valueOf(combinedTooltip.getDValue());
   }

   /**
    * Get the chart style.
    */
   @Override
   public int getChartStyle() {
      if(!isMultiStyles()) {
         return getRTChartType();
      }
      else {
         ChartRef[] xrefs = getRTXFields();
         ChartRef[] yrefs = getRTYFields();

         for(ChartRef[] refs : new ChartRef[][] {yrefs, xrefs}) {
            for(ChartRef ref : refs) {
               if(!GraphUtil.isMeasure(ref)) {
                  continue;
               }

               ChartAggregateRef mref = (ChartAggregateRef) ref;
               return mref.getRTChartType();
            }
         }

         return GraphTypes.CHART_AUTO;
      }
   }

   /**
    * Create color model map, for no color aesthetic ref.
    */
   @Override
   public Map<String, ColorFrame> getColorFrameMap() {
      Map<String, ColorFrame> map = new LinkedHashMap<>();
      ChartRef[] refs = getModelRefs(false);

      for(ChartRef chartRef : refs) {
         ChartAggregateRef ref = (ChartAggregateRef) chartRef;
         ColorFrame frame = ref.getColorFrame();

         if(frame != null) {
            map.put(ref.getFullName(), frame);
         }
      }

      return map;
   }

   /**
    * Get hyperlink variable.
    * @return the variable.
    */
   public VariableTable getLinkVarTable() {
      return linkVarTable;
   }

   /**
    * Set hyperlink variable.
    * @param table the variable table object.
    */
   public void setLinkVarTable(VariableTable table) {
      this.linkVarTable = table;
   }

   /**
    * Set selections.
    */
   public void setLinkSelections(Hashtable<String, SelectionVSAssembly> sel) {
      selections = sel;
   }

   /**
    * Get selections.
    * @return selection assembly map.
    */
   public Hashtable<String, SelectionVSAssembly> getLinkSelections() {
      return selections;
   }

   /**
    * Get Aggregate refs names.
    * @return the aggregaterefs
    */
   @Override
   public VSDataRef[] getAggregateRefs() {
      Set<VSDataRef> set = new HashSet<>();
      VSDataRef[] refs = getBindingRefs(true);

      for(VSDataRef ref : refs) {
         if(ref instanceof VSChartAggregateRef) {
            set.add(ref);
         }
      }

      AestheticRef[] refs2 = getAestheticRefs(false);

      for(AestheticRef aestheticRef : refs2) {
         VSDataRef ref = (VSDataRef) aestheticRef.getDataRef();

         if(ref instanceof VSAggregateRef) {
            set.add(ref);
         }
      }

      VSDataRef[] arr = new VSDataRef[set.size()];
      return set.toArray(arr);
   }

   @Override
   public ChartRef[] getBindingRefs(boolean runtime) {
      ChartRef[][] arrs = runtime ?
         new ChartRef[][] {getRTXFields(), getRTYFields(), getRTGroupFields()} :
         new ChartRef[][] {getXFields(), getYFields(), getGroupFields()};
      List<ChartRef> refs = new ArrayList<>();

      for(ChartRef[] arr : arrs) {
         refs.addAll(Arrays.asList(arr));
      }

      return refs.toArray(new ChartRef[0]);
   }

   /**
    * Get all the aggregaterefs which contains the specified dataref.
    * @param aesthetic if contains aesthetic refs.
    */
   @Override
   public VSAggregateRef[] getAggregates(DataRef fld, boolean aesthetic) {
      if(fld == null) {
         return null;
      }

      ArrayList<VSAggregateRef> refs = new ArrayList<>();

      for(int i = 0; i < getXFieldCount(); i++) {
         if(getXField(i) instanceof VSAggregateRef) {
            VSAggregateRef aggregate = (VSAggregateRef) getXField(i);

            if(Tool.equals(aggregate.getDataRef(), fld)) {
               refs.add(aggregate);
            }
         }
      }

      for(int i = 0; i < getYFieldCount(); i++) {
         if(getYField(i) instanceof VSAggregateRef) {
            VSAggregateRef aggregate = (VSAggregateRef) getYField(i);

            if(Tool.equals(aggregate.getDataRef(), fld) ||
               // if runtime is not set, check the design ref name
               aggregate.getDataRef() == null &&
               Tool.equals(aggregate.getName(), fld.getName()))
            {
               refs.add(aggregate);
            }
         }
      }

      if(aesthetic) {
         AestheticRef[] arefs = getAestheticRefs(false);

         for(AestheticRef aref : arefs) {
            if(aref.getDataRef() instanceof VSAggregateRef) {
               VSAggregateRef aggregate = (VSAggregateRef) aref.getDataRef();

               if(Tool.equals(aggregate.getDataRef(), fld)) {
                  refs.add(aggregate);
               }
            }
         }
      }

      Object[] objs = refs.toArray();
      VSAggregateRef[] arefs = new VSAggregateRef[objs.length];

      for(int i = 0; i < objs.length; i++) {
         arefs[i] = (VSAggregateRef) objs[i];
      }

      return arefs;
   }

   /**
    * Get all the dimensionrefs which contains the specified dataref.
    * @param aesthetic if contains aesthetic refs.
    */
   @Override
   public VSDimensionRef[] getDimensions(DataRef fld, boolean aesthetic) {
      if(fld == null) {
         return null;
      }

      ArrayList<VSDimensionRef> refs = new ArrayList<>();

      for(int i = 0; i < getXFieldCount(); i++) {
         if(getXField(i) instanceof VSDimensionRef) {
            VSDimensionRef dimension = (VSDimensionRef) getXField(i);

            if(Tool.equals(dimension.getDataRef(), fld)) {
               refs.add(dimension);
            }
         }
      }

      for(int i = 0; i < getYFieldCount(); i++) {
         if(getYField(i) instanceof VSDimensionRef) {
            VSDimensionRef dimension = (VSDimensionRef) getYField(i);

            if(Tool.equals(dimension.getDataRef(), fld)) {
               refs.add(dimension);
            }
         }
      }

      if(aesthetic) {
         AestheticRef[] arefs = getAestheticRefs(false);

         for(AestheticRef aref : arefs) {
            if(aref.getDataRef() instanceof VSDimensionRef) {
               VSDimensionRef dimension = (VSDimensionRef) aref.getDataRef();

               if(Tool.equals(dimension.getDataRef(), fld)) {
                  refs.add(dimension);
               }
            }
         }
      }

      Object[] objs = refs.toArray();
      VSDimensionRef[] arefs = new VSDimensionRef[objs.length];

      for(int i = 0; i < objs.length; i++) {
         arefs[i] = (VSDimensionRef) objs[i];
      }

      return arefs;
   }

   /**
    * Copy of same method in VsChartInfo.as
    * Get all field by a full name or name.
    * @param name the specified field full name or name.
    * @param applyDC check if the assembly applies date comparison.
    * @return the fields array.
    */
   public ChartRef[] getFields(String name, boolean applyDC) {
      List<ChartRef> refs = new ArrayList<>();
      boolean pref = isPeriodRef(name);
      ChartRef[] yrefs = getYFields();
      ChartRef[] ryrefs = getRTYFields();
      ChartRef[] xrefs = getXFields();
      ChartRef[] rxrefs = getRTXFields();

      if(!pref && yrefs.length != ryrefs.length) {
         ChartRef ref = getFieldByName(name, applyDC);

         if(ref != null) {
            refs.add(ref);
            return refs.toArray(new ChartRef[0]);
         }
      }

      int fixedYIndex = period && yrefs.length != ryrefs.length ? 1 : 0;

      for(int i = 0; i < yrefs.length; i++) {
         if(yrefs[i] != null && yrefs[i].getName().equals(name) ||
            ryrefs[i + fixedYIndex] != null && ryrefs[i + fixedYIndex].getFullName().equals(name))
         {
            refs.add(yrefs[i]);
         }
      }

      int fixedXIndex = period && xrefs.length != rxrefs.length ? 1 : 0;

      for(int i = 0; i < xrefs.length; i++) {
         if(xrefs[i] != null && xrefs[i].getName().equals(name) ||
            i + fixedXIndex < rxrefs.length && rxrefs[i + fixedXIndex] != null &&
            rxrefs[i + fixedXIndex].getFullName().equals(name))
         {
            refs.add(xrefs[i]);
         }
      }

      return refs.toArray(new ChartRef[0]);
   }

   /**
    * Check the specified column name is the period ref.
    */
   public boolean isPeriodRef(String name0) {
      if(!period) {
         return false;
      }

      if(name0 == null) {
         return false;
      }

      if(Arrays.stream(getYFields()).anyMatch(r -> Tool.equals(r.getName(), name0))) {
         return false;
      }

      if(Arrays.stream(getXFields()).anyMatch(r -> Tool.equals(r.getName(), name0))) {
         return false;
      }

      ChartRef[] ryrefs = getRTYFields();
      ChartRef[] rxrefs = getRTXFields();

      if(ryrefs.length > 0 && Tool.equals(ryrefs[0].getFullName(), name0) &&
         XSchema.isDateType(ryrefs[0].getDataType()) ||
         rxrefs.length > 0 && Tool.equals(rxrefs[0].getFullName(), name0) &&
         XSchema.isDateType(rxrefs[0].getDataType()))
      {
         return true;
      }

      return false;
   }

   /**
    * Get the runtime path field.
    */
   @Override
   public ChartRef getRTPathField() {
      return rPathRef;
   }

   /**
    * Get the period comparison field.
    */
   public ChartRef getPeriodField() {
      return periodRef;
   }

   /**
    * Set the period comparison field.
    */
   public void setPeriodField(ChartRef periodRef) {
      this.periodRef = periodRef;
   }

   /**
    * Get all the aggregaterefs which contains the specifed dataref.
    * @param fld The target ref to search for
    * @param aesthetic if true, will include aesthetic refs, false will not.
    */
   @Override
   public XAggregateRef[] getAllAggregates(DataRef fld, boolean aesthetic) {
      if(fld == null) {
         return null;
      }

      ArrayList<XAggregateRef> refs = new ArrayList<>();
      refs.addAll(getFields(getXFields(), fld.getName(), false));
      refs.addAll(getFields(getRTXFields(), fld.getName(), false));

      refs.addAll(getFields(getYFields(), fld.getName(), false));
      refs.addAll(getFields(getRTYFields(), fld.getName(), false));

      refs.addAll(getFields(getGroupFields(), fld.getName(), false));
      refs.addAll(getFields(getRTGroupFields(), fld.getName(), false));

      refs.addAll(getFields(new DataRef[] {getPathField()}, fld.getName(),
                            false));
      refs.addAll(getFields(new DataRef[] {getRTPathField()}, fld.getName(),
                            false));

      if(this instanceof CandleChartInfo) {
         refs.addAll(getFields(getStockOrCandleFields(), fld.getName(),
            false));
         refs.addAll(getFields(getRTStockOrCandleFields(), fld.getName(),
            false));
      }

      if(aesthetic) {
         refs.addAll(getFields(getAestheticRefs(false), fld.getName(), false));
         refs.addAll(getFields(getAestheticRefs(true), fld.getName(), false));
      }

      XAggregateRef[] arefs = new XAggregateRef[refs.size()];
      refs.toArray(arefs);
      return arefs;
   }

   /**
    * Get all the stock and candle binding fields.
    */
   public ChartRef[] getStockOrCandleFields() {
      if(!(this instanceof CandleChartInfo)) {
         return new ChartRef[0];
      }

      List<ChartRef> list = new ArrayList<>();
      CandleChartInfo cinfo = (CandleChartInfo) this;

      if(cinfo.getHighField() != null) {
         list.add(cinfo.getHighField());
      }

      if(cinfo.getCloseField() != null) {
         list.add(cinfo.getCloseField());
      }

      if(cinfo.getLowField() != null) {
         list.add(cinfo.getLowField());
      }

      if(cinfo.getOpenField() != null) {
         list.add(cinfo.getOpenField());
      }

      ChartRef[] ref = new ChartRef[list.size()];
      list.toArray(new Object[0]);
      return ref;
   }

   /**
    * Get all the stock and candle binding fields.
    */
   private ChartRef[] getRTStockOrCandleFields() {
      if(!(this instanceof CandleChartInfo)) {
         return new ChartRef[0];
      }

      List<ChartRef> list = new ArrayList<>();
      CandleChartInfo cinfo = (CandleChartInfo) this;

      if(cinfo.getHighField() != null) {
         list.add(cinfo.getRTHighField());
      }

      if(cinfo.getCloseField() != null) {
         list.add(cinfo.getRTCloseField());
      }

      if(cinfo.getLowField() != null) {
         list.add(cinfo.getRTLowField());
      }

      if(cinfo.getOpenField() != null) {
         list.add(cinfo.getRTOpenField());
      }

      ChartRef[] ref = new ChartRef[list.size()];
      list.toArray(new Object[0]);
      return ref;
   }

   /**
    * Get all the dimensionrefs which contains the specifed dataref.
    * @param others if contains other refs as group, aesthetic fields.
    */
   @Override
   public XDimensionRef[] getAllDimensions(DataRef fld, boolean others) {
      if(fld == null) {
         return null;
      }

      ArrayList<XDimensionRef> refs = new ArrayList<>();

      refs.addAll(getFields(getXFields(), fld.getName(), true));
      refs.addAll(getFields(getYFields(), fld.getName(), true));

      if(others) {
         refs.addAll(getFields(getGroupFields(), fld.getName(), true));
         refs.addAll(getFields(new DataRef[] {getPathField()}, fld.getName(), true));
         refs.addAll(getFields(getAestheticRefs(false), fld.getName(), true));
      }

      XDimensionRef[] arefs = new XDimensionRef[refs.size()];
      refs.toArray(arefs);

      return arefs;
   }

   /**
    * Get the field by a name.
    */
   private List getFields(DataRef[] refs, String name, boolean dim) {
      List<DataRef> list = new ArrayList<>();

      for(DataRef ref : refs) {
         if(ref instanceof AestheticRef) {
            ref = ((AestheticRef) ref).getDataRef();
         }

         if(dim) {
            if(ref instanceof VSDimensionRef) {
               VSDimensionRef dimension = (VSDimensionRef) ref;

               if(Tool.equals(dimension.getName(), name)) {
                  list.add(dimension);
               }
            }
         }
         else if(ref instanceof VSAggregateRef) {
            VSAggregateRef aggregate = (VSAggregateRef) ref;
            String aname = aggregate.getName();
            aname = aname == null || "".equals(aname) ?
               aggregate.getVSName() : aname;

            if(Tool.equals(aname, name)) {
               list.add(aggregate);
            }
         }
      }

      return list;
   }

   public void removeFormulaField(Set<String> calcFieldsRefs) {
      for(int i = 0; i < getXFieldCount(); i++) {
         ChartRef xField = getXField(i);

         if(xField != null && calcFieldsRefs.contains(xField.getName())) {
            removeXField(i);
         }
      }

      for(int i = 0; i < getYFieldCount(); i++) {
         ChartRef yField = getYField(i);

         if(yField != null && calcFieldsRefs.contains(yField.getName())) {
            removeYField(i);
         }
      }

      for(int i = 0; i < getGroupFieldCount(); i++) {
         ChartRef gField = getGroupField(i);

         if(gField != null && calcFieldsRefs.contains(gField.getName())) {
            removeGroupField(i);
         }
      }

      AestheticRef aesField = getColorField();

      if(aesField != null && calcFieldsRefs.contains(aesField.getName())) {
         setColorField(null);
      }

      aesField = getShapeField();

      if(aesField != null && calcFieldsRefs.contains(aesField.getName())) {
         setShapeField(null);
      }

      aesField = getSizeField();

      if(aesField != null && calcFieldsRefs.contains(aesField.getName())) {
         setSizeField(null);
      }

      aesField = getTextField();

      if(aesField != null && calcFieldsRefs.contains(aesField.getName())) {
         setTextField(null);
      }

      ChartRef pathField = getPathField();

      if(pathField != null && calcFieldsRefs.contains(pathField.getName())) {
         setPathField(null);
      }
   }

   /**
    * Get the runtime date comparison fields.
    * @return date comparison fields
    */
   public ChartRef[] getRuntimeDateComparisonRefs() {
      return rDateComparisonRefs;
   }

   /**
    * Set the runtime date comparison fields.
    */
   public void setRuntimeDateComparisonRefs(ChartRef[] refs) {
      this.rDateComparisonRefs = refs != null ? refs : new ChartRef[0];
   }

   /**
    * Check if applied custom periods date comparison.
    * @return
    */
   public boolean isAppliedCustomPeriodsDc() {
      if(rDateComparisonRefs == null || rDateComparisonRefs.length == 0) {
         appliedCustomPeriodsDc = false;
      }

      return appliedCustomPeriodsDc;
   }

   public void setAppliedCustomPeriodsDc(boolean appliedCustomPeriodsDc) {
      this.appliedCustomPeriodsDc = appliedCustomPeriodsDc;
   }

   @Override
   public void clearRuntime() {
      super.clearRuntime();
      this.rdesc = null;
      this.rdesc2 = null;

      clearDateComparisonRuntimeRef();

      for(AestheticRef ref : getAestheticRefs(false)) {
         ((VSAestheticRef) ref).setRTDataRef(null);
      }

      for(ChartRef ref : getBindingRefs(false)) {
         if(ref instanceof VSChartRef) {
            ((VSChartRef) ref).setRTAxisDescriptor(null);
         }
      }

      runtimeMulti = null;
      runtimeSeparated = null;
   }

   @Override
   public boolean isMultiStyles() {
      return rDateComparisonRefs != null && rDateComparisonRefs.length > 0 && runtimeMulti != null
         ? runtimeMulti : super.isMultiStyles();
   }

   public boolean isMultiStyles(boolean ignoreDc) {
      if(ignoreDc) {
         return super.isMultiStyles();
      }

      return rDateComparisonRefs != null && rDateComparisonRefs.length > 0 && runtimeMulti != null
         ? runtimeMulti : super.isMultiStyles();
   }

   @Override
   public boolean isSeparatedGraph() {
      return rDateComparisonRefs != null && rDateComparisonRefs.length > 0 && runtimeSeparated != null
         ? runtimeSeparated : super.isSeparatedGraph();
   }

   @Override
   public boolean supportsPathField() {
      if(isAppliedDateComparison()) {
         if(!isMultiStyles()) {
            return supportsPathField0(getRTChartType());
         }
         else {
            List refs = new ArrayList();

            refs.addAll(Arrays.asList(getRTXFields()));
            refs.addAll(Arrays.asList(getRTYFields()));

            for(int i = 0; i < refs.size(); i++) {
               Object ref = refs.get(i);

               if(!(ref instanceof ChartAggregateRef)) {
                  continue;
               }

               int type0 = ((ChartAggregateRef) ref).getRTChartType();

               if(!supportsPathField0(type0)) {
                  return false;
               }
            }
         }

         return true;
      }

      return super.supportsPathField();
   }

   /**
    * Clear the runtime changed by date comparison.
    */
   public void clearDCRuntime() {
      clearDateComparisonRuntimeRef();
      runtimeMulti = null;
      runtimeSeparated = null;
   }

   public boolean isDesignSeparated() {
      return super.isSeparatedGraph();
   }

   /**
    * Set multi style by dc.
    */
   public void setRuntimeMulti(Boolean runtimeMulti) {
      this.runtimeMulti = runtimeMulti;
   }

   public boolean isDesignMultiStyles() {
      return super.isMultiStyles();
   }

   /**
    * Set separated by dc.
    */
   public void setRuntimeSeparated(Boolean dcRtSeparated) {
      this.runtimeSeparated = dcRtSeparated;
   }

   public List<CommonKVModel<String, String>> getClearedFormula() {
      return clearedFormula;
   }

   public void setClearedFormula(List<CommonKVModel<String, String>> clearedFormula) {
      this.clearedFormula = clearedFormula;
   }

   public void setDateComparisonRef(VSDataRef ref) {
      dateComparisonRef = ref;
   }

   public VSDataRef getDateComparisonRef() {
      return dateComparisonRef;
   }

   /**
    * @return true if the date comparison base date field is on x, else on y.
    */
   public boolean isDcBaseDateOnX() {
      return dcBaseDateOnX;
   }

   public void setDcBaseDateOnX(boolean dcBaseDateOnX) {
      this.dcBaseDateOnX = dcBaseDateOnX;
   }

   @Override
   public XDimensionRef[] getDcTempGroups() {
      return dcTempGroups == null ? new XDimensionRef[0] : dcTempGroups;
   }

   @Override
   public void setDcTempGroups(XDimensionRef[] dcTempGroups) {
      this.dcTempGroups = dcTempGroups;
   }

   @Override
   public boolean isAppliedDateComparison() {
      return rDateComparisonRefs != null && rDateComparisonRefs.length > 0;
   }

   public boolean isPeriodPartRef(String name) {
      return periodPartRef != null && Tool.equals(name, periodPartRef.getFullName());
   }

   private List<CommonKVModel<String, String>> clearedFormula;

   protected ColumnSelection geoCols = new ColumnSelection();
   private ColumnSelection rGeoCols = new ColumnSelection();

   private boolean runtime; // flag indicates runtime or not
   private VSDataRef[] raesRefs; // runtime aestheic data refs
   private DynamicValue toolTipValue = new DynamicValue();
   private DynamicValue combinedTooltip = new DynamicValue();
   private boolean period = false; // flag add period field or not
   private boolean resetShape; // flag indicates reset shape frame or not
   private VariableTable linkVarTable; // the variable for hyperlink
   private AxisDescriptor rdesc; // runtime axis desc
   private AxisDescriptor rdesc2; // runtime 2nd axis desc
   private String rmapType = null; // runtime map type
   private VSChartRef rPathRef;
   private ChartRef periodRef; // period comparison field
   private Hashtable<String, SelectionVSAssembly> selections;
   private VSDataRef dateComparisonRef; // the date field to do date comparison
   private ChartRef[] rDateComparisonRefs = {}; // runtime date comparison field.
   private boolean appliedCustomPeriodsDc = false;
   private Boolean runtimeMulti; // multi style by date comparison.
   private Boolean runtimeSeparated; // separated by date comparison.
   private boolean dcBaseDateOnX;
   private SizeFrameWrapper runtimeSFrame;
   private XDimensionRef[] dcTempGroups;
   private VSDimensionRef periodPartRef;
   private final Object RUNTIME_REFS_LOCK = new Object();

   private static final Logger LOG = LoggerFactory.getLogger(VSChartInfo.class);
}
