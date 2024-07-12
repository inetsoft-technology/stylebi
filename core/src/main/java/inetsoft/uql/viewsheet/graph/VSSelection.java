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

import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.viewsheet.CalculateRef;
import inetsoft.uql.viewsheet.VSDataRef;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * VSSelection stores multiple VSPoints, so it defines a selection in chart.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class VSSelection implements AssetObject {
   /**
    * Not range selection.
    */
   public static final int NONE_RANGE = 0;
   /**
    * Physical range selection.
    */
   public static final int PHYSICAL_RANGE = 1;
   /**
    * Logical range selection.
    */
   public static final int LOGICAL_RANGE = 2;

   /**
    * Constructor.
    */
   public VSSelection() {
      super();

      points = new CopyOnWriteArrayList<>();
   }

   /**
    * Check if equals another objects.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals, <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof VSSelection)) {
         return false;
      }

      VSSelection sel = (VSSelection) obj;

      return range == sel.range && points.equals(sel.points);
   }

   /**
    * Check if is a logical range.
    */
   public boolean isLogicalRange() {
      return range == LOGICAL_RANGE;
   }

   /**
    * Check if is a physical range.
    */
   public boolean isPhysicalRange() {
      return range == PHYSICAL_RANGE;
   }

   /**
    * Get the range option.
    */
   public int getRange() {
      return range;
   }

   /**
    * Set whether range selection.
    */
   public void setRange(int range) {
      this.range = range;
   }

   /**
    * Add a point to point set.
    * @param vsp add a VSPoint to this selection.
    */
   public void addPoint(VSPoint vsp) {
      points.add(vsp);
   }

   /**
    * Remove the point at the specified index.
    */
   public void removePoint(int idx) {
      points.remove(idx);
   }

   /**
    * Get the point count.
    */
   public int getPointCount() {
      return points.size();
   }

   /**
    * Get the point at the specified index.
    */
   public VSPoint getPoint(int idx) {
      return points.get(idx);
   }

   /**
    * Clear all points from point set.
    */
   public void clearPoints() {
      points.clear();
   }

   /**
    * Test if the selection is empty.
    */
   public boolean isEmpty() {
      return points.size() == 0;
   }

   /**
    * Generate the XML segment to represent this selection.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<VSSelection rangeSelection=\"" + range + "\" class=\"" +
         getClass().getName() + "\">");

      for(VSPoint point : points) {
         point.writeXML(writer);
      }

      if(orig != null) {
         writer.println("<orig>");
         orig.writeXML(writer);
         writer.println("</orig>");
      }

      writer.println("</VSSelection>");
   }

   /**
    * Parse the XML element that contains information on this
    * selection.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      String text = Tool.getAttribute(tag, "rangeSelection");
      NodeList xnodes = Tool.getChildNodesByTagName(tag, "VSPoint");

      try {
         range = Integer.parseInt(text);
      }
      catch(Exception ex) {
         LOG.error("Invalid range selection: " + text, ex);
      }

      for(int i = 0; i < xnodes.getLength(); i++) {
         Element xnode = (Element) xnodes.item(i);
         VSPoint point = new VSPoint();
         point.parseXML(xnode);
         points.add(point);
      }

      Element origNode = Tool.getChildNodeByTagName(tag, "orig");
      Element subNode = origNode == null ?
         null : Tool.getChildNodeByTagName(origNode, "VSSelection");

      if(subNode != null) {
         orig = new VSSelection();
         orig.parseXML(subNode);
      }
   }

   /**
    * Create a clone of this object.
    */
   @Override
   public VSSelection clone() {
      try {
         VSSelection obj = (VSSelection) super.clone();
         obj.points = new CopyOnWriteArrayList<>(points);

         return obj;
      }
      catch(Exception e) {
         LOG.error("Failed to clone VSSelection", e);
         return null;
      }
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      String rstr = "";

      switch(range) {
      case NONE_RANGE:
         break;
      case PHYSICAL_RANGE:
         rstr = "range:";
         break;
      case LOGICAL_RANGE:
         rstr = "prange:";
         break;
      }

      return "VSSelection[" + rstr + points + "]";
   }

   /**
    * Sort the selection list.
    */
   public void sort(Comparator<VSPoint> comp) {
      points.sort(comp);
   }

   /**
    * Set the selection without the 'Others' expanded.
    */
   public void setOrigSelection(VSSelection orig) {
      this.orig = orig;
   }

   /**
    * Get the selection without the 'Others' expanded.
    */
   public VSSelection getOrigSelection() {
      return orig;
   }

   /**
    * Check if all selection values exist on columns.
    */
   public boolean isValid(ChartInfo cinfo) {
      return points.stream().allMatch(p -> {
         for(int i = 0; i < p.getValueCount(); i++) {
            if(cinfo.getRTFieldByFullName(p.getValue(i).getFieldName()) == null &&
               !calcContains(cinfo, p.getValue(i).getFieldName()))
            {
               return false;
            }
         }

         return true;
      });
   }

   private boolean calcContains(ChartInfo cinfo, String name) {
      if(!(cinfo instanceof VSChartInfo)) {
         return false;
      }

      String newName = Tool.replaceAll(name, "(", "([");
      newName = Tool.replaceAll(newName, ")", "])");

      VSChartInfo vinfo = (VSChartInfo) cinfo;
      VSDataRef[] arr = vinfo.getRTFields();

      for(int i = 0; i < arr.length; i++) {
         if(!(arr[i] instanceof VSChartAggregateRef)) {
            continue;
         }

         VSChartAggregateRef agg = (VSChartAggregateRef) arr[i];

         if(agg.getDataRef() instanceof CalculateRef) {
            CalculateRef calc = (CalculateRef) agg.getDataRef();

            if(calc.getDataRef() instanceof ExpressionRef) {
               String exp = ((ExpressionRef) calc.getDataRef()).getExpression();

               if(exp != null && exp.contains(newName)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private int range = NONE_RANGE;
   private List<VSPoint> points;
   private VSSelection orig;

   private static final Logger LOG = LoggerFactory.getLogger(VSSelection.class);
}
