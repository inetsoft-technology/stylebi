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

import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.uql.erm.AbstractDataRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.aesthetic.VisualFrameWrapper;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.*;

/**
 * Base implementation of AestheticRef.
 *
 * @version 11.4
 * @author InetSoft Technology Corp.
 */
public abstract class AbstractAestheticRef extends AbstractDataRef implements AestheticRef {
   /**
    * Get the legend frame wrapper.
    * @return the legend frame wrapper.
    */
   @Override
   public VisualFrameWrapper getVisualFrameWrapper() {
      return frame;
   }

   /**
    * Set the legend frame wrapper.
    * @param wrapper legend frame wrapper.
    */
   @Override
   public void setVisualFrameWrapper(VisualFrameWrapper wrapper) {
      this.frame = wrapper;
   }

   /**
    * Get the legend frame.
    * @return the legend frame.
    */
   @Override
   public VisualFrame getVisualFrame() {
      if(frame == null) {
         return null;
      }

      return frame.getVisualFrame();
   }

   /**
    * Set the legend frame.
    * @param frame the legend frame.
    */
   @Override
   public void setVisualFrame(VisualFrame frame) {
      try {
         this.frame = VisualFrameWrapper.wrap(frame);
      }
      catch(Exception ex) {
         throw new RuntimeException("Wrapper class not found: " + frame.getClass());
      }
   }

   /**
    * Get legend descriptor for the corresponding legend.
    */
   @Override
   public LegendDescriptor getLegendDescriptor() {
      return legend;
   }

   /**
    * Set legend descriptor for the corresponding legend.
    */
   @Override
   public void setLegendDescriptor(LegendDescriptor desc) {
      this.legend = desc;
   }

   /**
    * Check if is measure.
    */
   @Override
   public boolean isMeasure() {
      return GraphUtil.isMeasure(dataRef);
   }

   /**
    * Get the runtime dataRef.
    * @return the runtime dataRef.
    */
   @Override
   public DataRef getRTDataRef() {
      return dataRef;
   }

   /**
    * Get the dataRef.
    * @return the dataRef.
    */
   @Override
   public DataRef getDataRef() {
      return this.dataRef;
   }

   /**
    * Set the dataRef.
    * @param dataRef the dataRef.
    */
   @Override
   public void setDataRef(DataRef dataRef) {
      this.dataRef = dataRef;
   }

   /**
    * Get the full name.
    */
   @Override
   public String getFullName() {
      DataRef dataRef = getRTDataRef();

      if(dataRef == null) {
         dataRef = getDataRef();
      }

      if(dataRef instanceof VSDataRef) {
         return ((VSDataRef) dataRef).getFullName();
      }

      return getName();
   }

   /**
    * Clone.
    */
   @Override
   public Object clone() {
      try {
         AbstractAestheticRef aestheticRef = (AbstractAestheticRef) super.clone();

         if(dataRef != null) {
            aestheticRef.dataRef = (DataRef) dataRef.clone();
         }

         if(frame != null) {
            aestheticRef.frame = (VisualFrameWrapper) frame.clone();
         }

         if(legend != null) {
            aestheticRef.legend = (LegendDescriptor) legend.clone();
         }

         return aestheticRef;
      }
      catch(Exception e) {
         LOG.error("Failed to clone AbstractAestheticRef", e);
         return null;
      }
   }

   /**
    * Get the type of the field.
    * @return the type of the field.
    */
   @Override
   public int getRefType() {
      return getRTDataRef() == null ? NONE : getRTDataRef().getRefType();
   }

   /**
    * Check if the attribute is an expression.
    * @return <tt>true</tt> if is an expression, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isExpression() {
      return getRTDataRef() != null && getRTDataRef().isExpression();
   }

   /**
    * Get the attribute's parent entity.
    * @return the name of the entity.
    */
   @Override
   public String getEntity() {
      return getRTDataRef() == null ? null : getRTDataRef().getEntity();
   }

   /**
    * Get the attribute's parent entity.
    * @return an Enumeration with the name of the entity.
    */
   @Override
   public Enumeration getEntities() {
      return getRTDataRef() == null ? Collections.emptyEnumeration() : getRTDataRef().getEntities();
   }

   /**
    * Get the referenced attribute.
    * @return the name of the attribute.
    */
   @Override
   public String getAttribute() {
      return getRTDataRef() == null ? "" : getRTDataRef().getAttribute();
   }

   /**
    * Get a list of all attributes that are referenced by this object.
    * @return an Enumeration containing AttributeRef objects.
    */
   @Override
   public Enumeration getAttributes() {
      return getRTDataRef() == null ? Collections.emptyEnumeration() :
         getRTDataRef().getAttributes();
   }

   /**
    * Determine if the entity is blank.
    * @return <code>true</code> if entity is <code>null</code> or blank.
    */
   @Override
   public boolean isEntityBlank() {
      return getRTDataRef() == null || getRTDataRef().isEntityBlank();
   }

   /**
    * Get the name of the field.
    * @return the name of the field.
    */
   @Override
   public String getName() {
      return getRTDataRef() == null ? "" : getRTDataRef().getName();
   }

   /**
    * Get the data type.
    * @return the data type defined in XSchema.
    */
   @Override
   public String getDataType() {
      return getRTDataRef() == null ? XSchema.STRING : getRTDataRef().getDataType();
   }

   /**
    * Get the view representation of this field.
    * @return the view representation of this field.
    */
   @Override
   public String toView() {
      return getRTDataRef() == null ? "" : getRTDataRef().toView();
   }

   /**
    * Check if equals another object by content.
    */
   public boolean equals(Object obj) {
      if(!equalsContent(obj)) {
         return false;
      }

      AbstractAestheticRef ref = (AbstractAestheticRef) obj;

      if(!(dataRef instanceof ChartAggregateRef) && !Tool.equals(dataRef, ref.dataRef)) {
         return false;
      }

      return true;
   }

   public boolean equalsContent(Object obj) {
      if(!(obj instanceof AbstractAestheticRef)) {
         return false;
      }

      AbstractAestheticRef ref = (AbstractAestheticRef) obj;

      if(dataRef instanceof ChartAggregateRef && ref.dataRef instanceof ChartAggregateRef) {
         ChartRef ref1 = (ChartRef) dataRef;
         ChartRef ref2 = (ChartRef) ref.dataRef;

         // the aesthetic bindings in aggregate is not used when it's an
         // aesthetic binding so we should ignore them when comparing
         if(!(ref1.getFullName().equals(ref2.getFullName()))) {
            return false;
         }

         if(!Tool.equals(ref1.getTextFormat(), ref2.getTextFormat())) {
            return false;
         }
      }
      else if(!Tool.equalsContent(dataRef, ref.dataRef) ||
              dataRef instanceof ChartDimensionRef &&
              ref.dataRef instanceof ChartDimensionRef &&
              !Tool.equals(((ChartRef) dataRef).getTextFormat(),
              ((ChartRef) ref.dataRef).getTextFormat()))
      {
         return false;
      }

      return Tool.equalsContent(frame, ref.frame) && Tool.equalsContent(legend, ref.legend);
   }

   /**
    * Parse xml.
    * @param tag the xml element.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      parseContents(tag);
   }

   /**
    * Read in the attribute of this object from an XML tag.
    * @param tag the xml element representing this object.
    */
   @Override
   protected void parseAttributes(Element tag) throws Exception {
      // do nothing
   }

   /**
    * Parse contents.
    * @param node the element tag.
    */
   @Override
   protected void parseContents(Element node) throws Exception {
      super.parseContents(node);

      Element snode = Tool.getChildNodeByTagName(node, "legendFrame");

      if(snode != null) {
         frame = VisualFrameWrapper.createVisualFrame(snode);
      }

      snode = Tool.getChildNodeByTagName(node, "legendDescriptor");

      if(snode != null) {
         legend = new LegendDescriptor();
         legend.parseXML(snode);
      }

      snode = Tool.getChildNodeByTagName(node, "dataRef");

      if(snode != null) {
         dataRef = AbstractDataRef.createDataRef(snode);
      }
   }

   /**
    * Write the attributes of this object.
    * @param writer the output stream to which to write the XML data.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      // do nothing
   }

   /**
    * Write contents to the writer.
    * @param writer the print writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(frame != null) {
         frame.writeXML(writer);
      }

      if(dataRef != null) {
         dataRef.writeXML(writer);
      }

      if(legend != null) {
         legend.writeXML(writer);
      }

      String fullName = getFullName();

      if(fullName != null && fullName.length() > 0) {
         writer.print("<fullName>");
         writer.print("<![CDATA[" + fullName + "]]>");
         writer.println("</fullName>");
      }
   }

   /**
    * Check if the aesthetic frame has been changed from default.
    */
   @Override
   public boolean isChanged() {
      return frame != null && frame.isChanged();
   }

   /**
    * Get the dynamic property values.
    */
   public List<DynamicValue> getDynamicValues() {
      return new ArrayList<>(legend.getDynamicValues());
   }

   /**
    * Get the hyperlink dynamic values.
    * @return the dynamic values.
    */
   public List<DynamicValue> getHyperlinkDynamicValues() {
      List<DynamicValue> list = new ArrayList<>();

      if(dataRef instanceof VSAggregateRef) {
         list.addAll(((VSAggregateRef) dataRef).getHyperlinkDynamicValues());
      }
      else if(dataRef instanceof VSDimensionRef) {
         list.addAll(((VSDimensionRef) dataRef).getHyperlinkDynamicValues());
      }

      return list;
   }

   private VisualFrameWrapper frame;
   private LegendDescriptor legend = new LegendDescriptor();
   private DataRef dataRef;

   private static final Logger LOG = LoggerFactory.getLogger(AbstractAestheticRef.class);
}
