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

import inetsoft.report.Hyperlink;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.VSDataRef;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.DateComparisonInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * MergedVSChartInfo maintains binding info of merged chart.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class MergedVSChartInfo extends VSChartInfo implements MergedChartInfo {
   /**
    * Constructor.
    */
   public MergedVSChartInfo() {
      super();
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
      super.update(vs, columns, sep, pdim, source, dcInfo);

      // maintain hyperlink from chart info to runtime aggregate ref
      if(link == null) {
         return;
      }

      DataRef[] refs = getRTFields();
      applyHyperlink(refs, link);
   }

   /**
    * Apply hyperlink to runtime VSChartAggregateRefs.
    */
   protected void applyHyperlink(DataRef[] refs, Hyperlink link) {
      for(int i = 0; i < refs.length; i++) {
         if(!(refs[i] instanceof VSChartAggregateRef)) {
            continue;
         }

         VSChartAggregateRef aggr = (VSChartAggregateRef) refs[i];
         aggr.setHyperlink(link);
      }
   }

   /**
    * Set hyperlink.
    */
   @Override
   public void setHyperlink(Hyperlink link) {
      this.link = link;
   }

   /**
    * Get hyperlink.
    */
   @Override
   public Hyperlink getHyperlink() {
      return link;
   }

   /**
    * Get the hyperlink dynamic property values.
    * @return the dynamic values.
    */
   @Override
   public List getHyperlinkDynamicValues() {
      List list = super.getHyperlinkDynamicValues();

      if(link != null && VSUtil.isScriptValue(link.getLinkValue())) {
         list.add(link.getDLink());
      }

      return list;
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(link != null) {
         writer.print("<hyperLink>");
         link.writeXML(writer);
         writer.print("</hyperLink>");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element lnode = Tool.getChildNodeByTagName(elem, "hyperLink");

      if(lnode != null) {
         link = new Hyperlink();
         link.parseXML((Element) lnode.getFirstChild());
      }
   }

   /**
    * Clone this object.
    * @return the cloned object.
    */
   @Override
   public VSChartInfo clone() {
       try {
         MergedVSChartInfo obj = (MergedVSChartInfo) super.clone();

         if(link != null) {
            obj.link = (Hyperlink) link.clone();
         }

         return obj;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone MergedVSChartInfo", ex);
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

      if(!(obj instanceof MergedVSChartInfo)) {
         return false;
      }

      MergedVSChartInfo chartInfo = (MergedVSChartInfo) obj;
      Hyperlink link2 = chartInfo.link;

      if(!Tool.equals(link2, link)) {
         return false;
      }

      return true;
   }

   /**
    * Get all runtime fields, including x, y and aesthetic fields.
    */
   @Override
   public VSDataRef[] getRTFields() {
      List list = new ArrayList();
      DataRef[] mergedRtflds = getMergedRTFields();
      DataRef[] xyflds = super.getRTFields(true, false, false, false);
      DataRef[] rtflds = super.getRTFields(false, true, true, true);

      if(this instanceof VSMapInfo) {
         ChartRef[] geoRefs = ((VSMapInfo) this).getGeoFields();

         for(int i = 0; i < geoRefs.length; i++) {
            if(geoRefs[i] instanceof VSChartGeoRef && getHyperlink() != null) {
               VSChartGeoRef ngeoRef = ((VSChartGeoRef) geoRefs[i]).clone();
               ngeoRef.setHyperlink(getHyperlink());
               list.add(ngeoRef);
            }
            else {
               list.add(geoRefs[i]);
            }
         }
      }

      for(int i = 0; i < xyflds.length; i++) {
         list.add(xyflds[i]);
      }

      for(int i = 0; i < mergedRtflds.length; i++) {
         list.add(mergedRtflds[i]);
      }

      for(int i = 0; i < rtflds.length; i++) {
         list.add(rtflds[i]);
      }


      VSDataRef[] arr = new VSDataRef[list.size()];
      list.toArray(arr);
      return arr;
   }

   /**
    * Get all runtime axis fields.
    */
   @Override
   public VSDataRef[] getRTAxisFields() {
      List list = new ArrayList();
      DataRef[] rtflds = super.getRTAxisFields();

      for(int i = 0; i < rtflds.length; i++) {
         list.add(rtflds[i]);
      }

      rtflds = getMergedRTAxisFields();

      for(int i = 0; i < rtflds.length; i++) {
         list.add(rtflds[i]);
      }

      VSDataRef[] arr = new VSDataRef[list.size()];
      list.toArray(arr);
      return arr;
   }

   /**
    * Get merge info special fields, not including x, y and aesthetic fields.
    */
   public VSDataRef[] getMergedRTFields() {
      return new VSDataRef[0];
   }

   /**
    * Get the fields (other than x/y) that should be treated as axis.
    */
   public VSDataRef[] getMergedRTAxisFields() {
      return getMergedRTFields();
   }

   /**
    * Check if the color frame is per measure.
    */
   @Override
   public boolean supportsColorFieldFrame() {
      return false;
   }

   /**
    * Check if the shape frame is per measure.
    */
   @Override
   public boolean supportsShapeFieldFrame() {
      return false;
   }

   private Hyperlink link;

   private static final Logger LOG =
      LoggerFactory.getLogger(MergedVSChartInfo.class);
}
