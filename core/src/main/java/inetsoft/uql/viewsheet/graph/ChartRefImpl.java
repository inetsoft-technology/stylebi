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

import inetsoft.graph.internal.GDefaults;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Tool;
import inetsoft.util.css.CSSConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.*;

/**
 * The implementation of the ChartRef.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class ChartRefImpl implements Cloneable, Serializable {
   /**
    * Create a ChartRefImpl object.
    */
   public ChartRefImpl() {
      desc = new AxisDescriptor();
      fmt = new CompositeTextFormat();
      initDefaultFormat();
      fmt.getCSSFormat().setCSSType(CSSConstants.CHART_PLOTLABELS);
   }

   public void initDefaultFormat() {
      initDefaultFormat(false);
   }

   public void initDefaultFormat(boolean vs) {
      TextFormat deffmt = fmt.getDefaultFormat();
      deffmt.setColor(GDefaults.DEFAULT_TEXT_COLOR);
      deffmt.setFont(vs ? VSAssemblyInfo.getDefaultFont(VSUtil.getDefaultFont()) :
                        VSUtil.getDefaultFont());
   }

   /**
    * Get axis descriptor from this ref.
    */
   public AxisDescriptor getAxisDescriptor() {
      return this.desc;
   }

   /**
    * Set the axis descriptor into this ref.
    */
   public void setAxisDescriptor(AxisDescriptor desc) {
      this.desc = desc;
   }

  /**
    * Get the axis descriptor for the named chart ref.
    */
   public AxisDescriptor getAxisDescriptor(String fullname) {
      return descMap != null ? descMap.get(fullname) : null;
   }

   /**
    * Set the axis descriptor for the named chart ref.
    */
   public void setAxisDescriptor(String fullname, AxisDescriptor desc) {
      if(descMap == null) {
         descMap = new HashMap<>();
      }

      descMap.put(fullname, desc);
   }

   public void setAxisCSS(String css) {
      if(this.desc != null) {
         this.desc.setAxisCSS(css);
      }
   }

   /**
    * Get the data format for this measure.
    */
   public CompositeTextFormat getTextFormat() {
      return fmt;
   }

   /**
    * Set the data format for this measure.
    */
   public void setTextFormat(CompositeTextFormat fmt) {
      this.fmt = fmt;
   }

   /**
    * Check if equqls another object by content.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof ChartRefImpl)) {
         return false;
      }

      ChartRefImpl impl = (ChartRefImpl) obj;
      return Tool.equalsContent(desc, impl.desc) && Tool.equals(fmt, impl.fmt) &&
         Objects.equals(descMap, impl.descMap);
   }

   /**
    * Make a copy of this object.
    */
   @Override
   public Object clone() {
      try {
         ChartRefImpl refImpl = (ChartRefImpl) super.clone();

         if(desc != null) {
            refImpl.desc = (AxisDescriptor) desc.clone();
         }

         if(fmt != null) {
            refImpl.fmt = (CompositeTextFormat) fmt.clone();
         }

         if(descMap != null) {
            refImpl.descMap = new HashMap<>(descMap);
         }

         return refImpl;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone ChartRefImpl", ex);
      }

      return null;
   }

   /**
    * Write contents.
    */
   public void writeXML(PrintWriter writer) {
      if(getAxisDescriptor() != null) {
         getAxisDescriptor().writeXML(writer);
      }

      if(getTextFormat() != null) {
         getTextFormat().writeXML(writer);
      }

      if(descMap != null) {
         for(String fullname : descMap.keySet()) {
            writer.println("<axisDescriptors2 name=\"" + Tool.byteEncode(fullname) + "\">");
            descMap.get(fullname).writeXML(writer);
            writer.println("</axisDescriptors2>");
         }
      }
   }

   /**
    * Parse contents.
    */
   public void parseXML(Element elem) throws Exception {
      Element node = Tool.getChildNodeByTagName(elem, "axisDescriptor");

      if(node != null) {
         getAxisDescriptor().parseXML(node);
      }

      node = Tool.getChildNodeByTagName(elem, "compositeTextFormat");

      if(node != null) {
         getTextFormat().parseXML(node);
      }

      NodeList list = Tool.getChildNodesByTagName(elem, "axisDescriptors2");

      if(list.getLength() > 0) {
         descMap = new HashMap<>();

         for(int i = 0; i < list.getLength(); i++) {
            Element child = (Element) list.item(i);
            String fullname = Tool.byteDecode(Tool.getAttribute(child, "name"));
            AxisDescriptor desc2 = new AxisDescriptor();
            desc2.parseXML(Tool.getChildNodeByTagName(child, "axisDescriptor"));
            descMap.put(fullname, desc2);
         }
      }
   }

   private AxisDescriptor desc;
   private Map<String, AxisDescriptor> descMap = null;
   private CompositeTextFormat fmt;
   private static final Logger LOG = LoggerFactory.getLogger(ChartRefImpl.class);
}
