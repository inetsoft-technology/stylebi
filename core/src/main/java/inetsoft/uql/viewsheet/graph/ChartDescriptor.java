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

import inetsoft.report.composition.graph.GraphTarget;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.viewsheet.DynamicValue;
import inetsoft.uql.viewsheet.XDimensionRef;
import inetsoft.util.ContentObject;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.*;
import java.io.PrintWriter;
import java.text.Format;
import java.util.List;
import java.util.*;
import java.util.stream.Stream;

/**
 * ChartDescriptor is a bean that holds the top-level attributes of chart.
 *
 * @author InetSoft Technology Corp.
 * @since 10.0
 */
public class ChartDescriptor implements AssetObject, ContentObject {
   /**
    * Create a new instance of ChartDescriptor.
    */
   public ChartDescriptor() {
      legendsDesc = new LegendsDescriptor();
      plotDesc = new PlotDescriptor();
      titlesDesc = new TitlesDescriptor();
   }

   /**
    * Get the effect flag for the chart.
    * @return true if effect is to be applied.
    */
   public boolean isApplyEffect() {
      return effect;
   }

   /**
    * Set the effect flag for the chart.
    * @param effect true to apply effect on chart, false otherwise.
    */
   public void setApplyEffect(boolean effect) {
      this.effect = effect;
   }

   /**
    * Check if this chart should be set to sparkline mode.
    */
   public boolean isSparkline() {
      return sparkline;
   }

   /**
    * Set if this chart should be set to sparkline mode.
    */
   public void setSparkline(boolean sparkline) {
      this.sparkline = sparkline;
   }

    /**
     * Check if 'Others' group should always be sorted as the last item.
     */
   public boolean isSortOthersLast() {
      return sortOthersLast;
   }

   /**
    * Set if 'Others' group should always be sorted as the last item.
    */
   public void setSortOthersLast(boolean sortOthersLast) {
      this.sortOthersLast = sortOthersLast;
   }

   /**
    * Get the preferred size of chart.
    * @return preferred size.
    */
   public Dimension getPreferredSize() {
      return psize;
   }

   /**
    * Set the preferred size of chart.
    */
   public void setPreferredSize(Dimension size) {
      this.psize = size;
   }

   /**
    * Set legends descriptor of chart.
    * @param legendsDesc the specified LegendsDescriptor.
    */
   public void setLegendsDescriptor(LegendsDescriptor legendsDesc) {
      this.legendsDesc = legendsDesc;
   }

   /**
    * Get the legend descriptor of chart.
    * @return the legends descriptor.
    */
   public LegendsDescriptor getLegendsDescriptor() {
      return legendsDesc;
   }

   /**
    * Set titles descriptor of chart.
    * @param titlesDesc the specified TitlesDescriptor.
    */
   public void setTitlesDescriptor(TitlesDescriptor titlesDesc) {
      this.titlesDesc = titlesDesc;
   }

   /**
    * Get the title descriptor of chart.
    * @return the titles descriptor.
    */
   public TitlesDescriptor getTitlesDescriptor() {
      return titlesDesc;
   }

   /**
    * Set plot descriptor of chart.
    * @param plotDesc the specified PlotDescriptor.
    */
   public void setPlotDescriptor(PlotDescriptor plotDesc) {
      this.plotDesc = plotDesc;
   }

   /**
    * Get the plot descriptor of chart.
    * @return the plot descriptor.
    */
   public PlotDescriptor getPlotDescriptor() {
      return plotDesc;
   }

   /**
    * Add a target to the chart.
    * @param target the target to add.
    */
   public void addTarget(GraphTarget target) {
      targets.add(target);
   }

   /**
    * Remove a target from the chart.
    * @param target the target to remove.
    */
   public void removeTarget(GraphTarget target) {
      targets.remove(target);
   }

   /**
    *  Remove all targets from the chart.
    */
   public void clearTargets() {
      targets.clear();
   }

   /**
    * Get the number of targets on the chart.
    * @return the number of targets.
    */
   public int getTargetCount() {
      return targets.size();
   }

   /**
    * Get the target at the specified index.
    * @param idx the index of the target.
    * @return the target.
    */
   public GraphTarget getTarget(int idx) {
      return targets.get(idx);
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
    * @param obj the specified object.
    * @return <tt>true</tt> if equals the object in content, <tt>false</tt>
    * otherwise.
    */
   @Override
   public boolean equalsContent(Object obj) {
      if(!(obj instanceof ChartDescriptor)) {
         return false;
      }

      ChartDescriptor desc = (ChartDescriptor) obj;

      if(!Tool.equalsContent(legendsDesc, desc.legendsDesc)) {
         return false;
      }

      if(!Tool.equalsContent(plotDesc, desc.plotDesc)) {
         return false;
      }

      if(!Tool.equalsContent(titlesDesc, desc.titlesDesc)) {
         return false;
      }

      if(effect != desc.effect) {
         return false;
      }

      if(sparkline != desc.sparkline) {
         return false;
      }

      if(!Tool.equals(targets, desc.targets)) {
         return false;
      }

      if(!Tool.equals(psize, desc.psize)) {
         return false;
      }

      return sortOthersLast == desc.sortOthersLast && rankPerGroup == desc.rankPerGroup;
   }

   /**
    * Create a copy of this object.
    * @return a copy of this object.
    */
   @Override
   public Object clone() {
      try {
         ChartDescriptor dolly = (ChartDescriptor) super.clone();

         if(legendsDesc != null) {
            dolly.legendsDesc = (LegendsDescriptor) legendsDesc.clone();
         }

         if(plotDesc != null) {
            dolly.plotDesc = (PlotDescriptor) plotDesc.clone();
         }

         if(titlesDesc != null) {
            dolly.titlesDesc = (TitlesDescriptor) titlesDesc.clone();
         }

         if(targets != null) {
            dolly.targets = Tool.deepCloneCollection(targets);
         }

         return dolly;
      }
      catch(Exception exc) {
         LOG.error("Failed to clone ChartDescriptor", exc);
      }

      return null;
   }

   /**
    * Write xml representation.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<chartDescriptor");
      writeAttributes(writer);
      writer.println(">");
      writeContents(writer);
      writer.println("</chartDescriptor>");
   }

   /**
    * Write the content part(child node) of XML segment.
    * @param writer the destination print writer.
    */
   protected void writeContents(PrintWriter writer) {
      if(targets != null && targets.size() > 0) {
         writer.print("<targets>");

         for(GraphTarget target : targets) {
            target.writeXML(writer);
         }

         writer.println("</targets>");
      }

      if(legendsDesc != null) {
         legendsDesc.writeXML(writer);
      }

      if(plotDesc != null) {
         plotDesc.writeXML(writer);
      }

      if(titlesDesc != null) {
         titlesDesc.writeXML(writer);
      }
   }

   /**
    * Write attributes to a XML segment.
    * @param writer the destination print writer.
    */
   protected void writeAttributes(PrintWriter writer) {
      writer.print(" applyEffect=\"" + effect + "\" sparkline=\"" + sparkline + "\"");
      writer.print(" sortOthersLast=\"" + sortOthersLast + "\"");
      writer.print(" rankPerGroup=\"" + rankPerGroup + "\"");

      if(psize != null) {
         writer.print(" preferredWidth=\"" + psize.getWidth() + "\"");
         writer.print(" preferredHeight=\"" + psize.getHeight() + "\"");
      }
   }

   /**
    * Parse an xml segment about parameter element information.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      parseAttributes(tag);
      parseContents(tag);
   }

   /**
    * Parse attributes to an XML segment.
    */
   protected void parseAttributes(Element tag) throws Exception {
      String val, val1;

      if((val = Tool.getAttribute(tag, "applyEffect")) != null) {
         effect = Boolean.parseBoolean(val);
      }

      if((val = Tool.getAttribute(tag, "sparkline")) != null) {
         sparkline = Boolean.parseBoolean(val);
      }

      sortOthersLast = "true".equals(Tool.getAttribute(tag, "sortOthersLast"));
      rankPerGroup = !"false".equals(Tool.getAttribute(tag, "rankPerGroup"));

      if((val = Tool.getAttribute(tag, "preferredWidth")) != null &&
         (val1 = Tool.getAttribute(tag, "preferredHeight")) != null)
      {
         setPreferredSize(new Dimension(Double.valueOf(val).intValue(),
                                        Double.valueOf(val1).intValue()));
      }
   }

   /**
    * Parse the content part(child node) of XML segment.
    */
   protected void parseContents(Element tag) throws Exception {
      Element plotdNode = Tool.getChildNodeByTagName(tag, "plotDescriptor");

      if(plotdNode != null) {
         plotDesc = new PlotDescriptor();
         plotDesc.parseXML(plotdNode);
      }

      Element legendNode = Tool.getChildNodeByTagName(tag, "legendsDescriptor");

      if(legendNode != null) {
         legendsDesc = new LegendsDescriptor();
         legendsDesc.parseXML(legendNode);
      }

      Element titleNode = Tool.getChildNodeByTagName(tag, "titlesDescriptor");

      if(titleNode != null) {
         titlesDesc = new TitlesDescriptor();
         titlesDesc.parseXML(titleNode);
      }

      Element targetsNode = Tool.getChildNodeByTagName(tag, "targets");

      if(targetsNode != null) {
         NodeList list = Tool.getChildNodesByTagName(targetsNode,
                                                     "graphTarget");

         if(list != null && list.getLength() > 0) {
            targets = new ArrayList<>();

            for(int i = 0; i < list.getLength(); i++) {
               GraphTarget target = GraphTarget.instantiateFromXML((Element) list.item(i));
               targets.add(target);
            }
         }
      }
   }

   /**
    * Get the dynamic property values.
    */
   public List<DynamicValue> getDynamicValues() {
      List<DynamicValue> list = new ArrayList<>();

      for(int i = 0; i < getTargetCount(); i++) {
         list.addAll(getTarget(i).getDynamicValues());
      }

      list.addAll(titlesDesc.getDynamicValues());
      list.addAll(legendsDesc.getDynamicValues());

      return list;
   }

   /**
    * Set the default formats.
    */
   public void setFormats(Map<Class<?>, Format> formatmap) {
      this.formatmap = formatmap;
   }

   /**
    * Set the default formats.
    */
   public Map<Class<?>, Format> getFormats() {
      return formatmap;
   }

   /**
    * Set the sortOthersLast options for dimensions in binding.
    */
   public void setSortOthersLast(ChartInfo cinfo) {
      Stream.concat(GraphUtil.getAllDimensions(cinfo, true).stream(),
                    GraphUtil.getAllDimensions(cinfo, false).stream())
         .filter(a -> a != null)
         .forEach(a -> a.setSortOthersLast(isSortOthersLast()));

      if(GraphTypes.isFunnel(cinfo.getChartType())) {
         Stream.concat(Arrays.stream(cinfo.getBindingRefs(true)),
                       Arrays.stream(cinfo.getBindingRefs(false)))
            .filter(a -> a instanceof XDimensionRef)
            .forEach(a -> ((XDimensionRef) a).setSortOthersLast(false));
      }
   }

   /**
    * Check if ranking should be calculated across the entire dataset or per group.
    */
   public boolean isRankPerGroup() {
      return rankPerGroup;
   }

   /**
    * Set whether ranking should be calculated across the entire dataset instead of within
    * groups on X/Y dimensions.
    */
   public void setRankPerGroup(boolean rankPerGroup) {
      this.rankPerGroup = rankPerGroup;
   }

   private Dimension psize;
   private LegendsDescriptor legendsDesc;
   private PlotDescriptor plotDesc;
   private TitlesDescriptor titlesDesc;
   private ArrayList<GraphTarget> targets = new ArrayList<>();
   private boolean effect = false;
   private boolean sparkline = false;
   private boolean sortOthersLast = true;
   private boolean rankPerGroup = true;
   private transient Map<Class<?>, Format> formatmap = new HashMap<>();

   private static final Logger LOG = LoggerFactory.getLogger(ChartDescriptor.class);
}
