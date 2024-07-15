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
package inetsoft.uql.viewsheet.graph;

import inetsoft.report.Hyperlink;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.internal.binding.OrderInfo;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.GroupRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.Tool;

import java.io.PrintWriter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

/**
 * VSChartDimensionRef, rather than just dimension information, it holds the
 * attributes required to render itself, axis, label, etc.
 *
 * @version 10.0
 * @author InetSoft Technology Corp.
 */
public class VSChartDimensionRef extends VSDimensionRef
   implements VSChartRef, HyperlinkRef, ChartDimensionRef, HighlightRef
{
   /**
    * Create a VSChartDimensionRef.
    */
   public VSChartDimensionRef() {
      super();
      this.refImpl = new ChartRefImpl();
   }

   /**
    * Create a VSChartDimensionRef.
    */
   public VSChartDimensionRef(DataRef group) {
      super(group);
      refImpl = new ChartRefImpl();
   }

   public void initDefaultFormat() {
      initDefaultFormat(false);
   }

   public void initDefaultFormat(boolean vs) {
      this.refImpl.initDefaultFormat(vs);
   }

   @Override
   public void setAxisCSS(String css) {
      this.refImpl.setAxisCSS(css);
   }

   /**
    * Get axis descriptor from this ref.
    * @return the axis descriptor.
    */
   @Override
   public AxisDescriptor getAxisDescriptor() {
      return refImpl.getAxisDescriptor();
   }

   /**
    * Set the axis descriptor into this ref.
    * @param desc the axis descriptor.
    */
   public void setAxisDescriptor(AxisDescriptor desc) {
      refImpl.setAxisDescriptor(desc);
   }

   /**
    * Get runtime axis descriptor from this ref.
    * @return the axis descriptor.
    */
   @Override
   public AxisDescriptor getRTAxisDescriptor() {
      return rdesc;
   }

   /**
    * Set the runtime axis descriptor into this ref.
    * @param desc the axis descriptor.
    */
   @Override
   public void setRTAxisDescriptor(AxisDescriptor desc) {
      this.rdesc = desc;
   }

   @Override
   public List<DataRef> update(Viewsheet vs, ColumnSelection columns) {
      List<DataRef> list = super.update0(vs, columns, false);

      for(DataRef dim : list) {
         VSChartDimensionRef chartDim = (VSChartDimensionRef) dim;

         if(this.equals(dim)) {
            // need to share axis descriptor so axis property would work. (58003)
            chartDim.setAxisDescriptor(refImpl.getAxisDescriptor());
            continue;
         }

         AxisDescriptor desc = refImpl.getAxisDescriptor(chartDim.getFullName());

         if(desc == null) {
            desc = getAxisDescriptor().clone();
            refImpl.setAxisDescriptor(chartDim.getFullName(), desc);
         }

         chartDim.setAxisDescriptor(desc);
      }

      return list;
   }

   /**
    * Get the data format for this measure.
    */
   @Override
   public CompositeTextFormat getTextFormat() {
      return refImpl.getTextFormat();
   }

   /**
    * Set the data format for this measure.
    */
   @Override
   public void setTextFormat(CompositeTextFormat fmt) {
      refImpl.setTextFormat(fmt);
   }

   /**
    * Create group ref.
    */
   @Override
   public GroupRef createGroupRef(ColumnSelection cols) {
      GroupRef group = super.createGroupRef(cols);

      if(group == null) {
         return group;
      }

      int order = getOrder();

      if(order != XConstants.SORT_VALUE_ASC &&
         order != XConstants.SORT_VALUE_DESC)
      {
         return group;
      }

      String scol = getSortByCol();

      if(scol == null || scol.trim().length() == 0) {
         return group;
      }

      OrderInfo info = group.getOrderInfo();

      if(info == null) {
         info = new OrderInfo();
      }

      info.setOrder(order);
      // we use name instead of index
      info.setSortByCol(-1);

      // time series always sorted by time so ignore sort by value (49425).
      if(isTimeSeries()) {
         info.setSortByColValue(null);
      }
      else {
         info.setSortByColValue(scol);
      }

      group.setOrderInfo(info);
      return group;
   }

   /**
    * Parse contents.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);
      Element node;

      refImpl.parseXML(elem);

      node = Tool.getChildNodeByTagName(elem, "Hyperlink");

      if(node != null) {
         link = new Hyperlink();
         link.parseXML(node);
      }

      parseHighlightGroup(elem);
   }

   /**
    * Write contents.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(refImpl != null) {
         refImpl.writeXML(writer);
      }

      if(link != null) {
         link.writeXML(writer);
      }

      writeHighlightGroup(writer);
   }

   @Override
   public VSChartDimensionRef clone() {
      try {
         VSChartDimensionRef cdimRef = (VSChartDimensionRef) super.clone();

         if(refImpl != null) {
            cdimRef.refImpl = (ChartRefImpl) refImpl.clone();
         }

         /*
         move to VSChartInfo
         if(!Objects.equals(getName(), getGroupColumnValue()) &&
            VSUtil.isDynamicValue(getGroupColumnValue()))
         {
            // for dimension expanded by dynamic value, the axis descriptor is stored in
            // the original dimension. we make sure the shared descriptor is in rt dim
            // so setting it (hide/show) would work. (42200)
            cdimRef.setAxisDescriptor(getAxisDescriptor());
         }
          */

         if(link != null) {
            cdimRef.link = (Hyperlink) link.clone();
         }

         if(hlGroup != null) {
            cdimRef.hlGroup = hlGroup.clone();
         }

         if(textHL != null) {
            cdimRef.textHL = textHL.clone();
         }

         return cdimRef;
      }
      catch(Exception e) {
         LOG.error("Failed to clone VSChartDimensionRef", e);
         return null;
      }
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
    * Check if equals another object by content.
    */
   @Override
   public boolean equalsContent(Object obj) {
      return equalsContent0(obj, false);
   }

   /**
    * If group column value is dynamic, the format in refImpl will maybe changed in runtime,
    * (since it depending the runtime field). But sometimes we don't need to consider the format
    * when check if content equals, so add this overload function to support ingore refImpl.
    *
    * @param ignoreRefImpl ignore the refImpl if true, else not.
    */
   public boolean equalsContent0(Object obj, boolean ignoreRefImpl) {
      if(!super.equalsContent(obj)) {
         return false;
      }

      if(!(obj instanceof VSChartDimensionRef)) {
         return false;
      }

      VSChartDimensionRef ref = (VSChartDimensionRef) obj;

      if(!ignoreRefImpl && !Tool.equals(refImpl, ref.refImpl)) {
         return false;
      }

      return Tool.equals(link, ref.link) && Tool.equals(hlGroup, ref.hlGroup) &&
         Tool.equals(textHL, ref.textHL);
   }

   /**
    * Get the hyperlink of the ref.
    * @return the hyperlink.
    */
   @Override
   public Hyperlink getHyperlink() {
      return this.link;
   }

   /**
    * Set the hyperlink of the ref.
    * @param link the hyperlink.
    */
   @Override
   public void setHyperlink(Hyperlink link) {
      this.link = link;
   }

   /**
    * Check this ref is treat as dimension or measure.
    */
   @Override
   public boolean isMeasure() {
      return false;
   }

   /**
    * Get the highlight group of this ref.
    * @return the highlight group.
    */
   @Override
   public HighlightGroup getHighlightGroup() {
      return this.hlGroup;
   }

   /**
    * Set the highlight group of this ref.
    * @param hlGroup the highlight group.
    */
   @Override
   public void setHighlightGroup(HighlightGroup hlGroup) {
      this.hlGroup = hlGroup;
   }

   @Override
   public HighlightGroup getTextHighlightGroup() {
      return textHL;
   }

   @Override
   public void setTextHighlightGroup(HighlightGroup group) {
      this.textHL = group;
   }

   @Override
   public String getFullName() {
      String name = super.getFullName();

      if(name == null || name.isEmpty()) {
         name = getGroupColumnValue();
      }

      return name;
   }

   private ChartRefImpl refImpl; // impl contains axis, format, etc.
   private AxisDescriptor rdesc; // runtime axis desc
   private Hyperlink link;
   private HighlightGroup hlGroup;
   private HighlightGroup textHL;
   private static final Logger LOG = LoggerFactory.getLogger(VSChartDimensionRef.class);
}
