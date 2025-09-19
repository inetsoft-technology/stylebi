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
package inetsoft.web.vswizard.model.recommender;

import com.fasterxml.jackson.annotation.JsonIgnore;
import inetsoft.uql.viewsheet.graph.ChartInfo;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.List;

public class ChartSubType extends VSSubType {
   public ChartSubType() {
   }

   public ChartSubType(String t) {
      setType(t);
   }

   public void setFacet(boolean f) {
      this.facet = f;
   }

   public boolean getFacet() {
      return facet;
   }

   public void setDualAxis(boolean da) {
      this.dualAxis = da;
   }

   public boolean getDualAxis() {
      return dualAxis;
   }

   public void setWordCloud(boolean wc) {
      this.wordCloud = wc;
   }

   public boolean isWordCloud() {
      return wordCloud;
   }

   public void setHeatMap(boolean hm) {
      this.heatMap = hm;
   }

   public boolean isHeatMap() {
      return heatMap;
   }

   public boolean isDotplot() {
      return dotplot;
   }

   public void setDotplot(boolean dotplot) {
      this.dotplot = dotplot;
   }

   public void setScatterMatrix(boolean scatterMatrix) {
      this.scatterMatrix = scatterMatrix;
   }

   public boolean isScatterMatrix() {
      return scatterMatrix;
   }

   public void setScatter(boolean scatter) {
      this.scatter = scatter;
   }

   public boolean isScatter() {
      return scatter;
   }

   public void setDonut(boolean donut) {
      this.donut = donut;
   }

   public boolean isDonut() {
      return donut;
   }

   public boolean isHistogram() {
      return histogram;
   }

   public void setHistogram(boolean histogram) {
      this.histogram = histogram;
   }

   public void setRotated(boolean rotated) {
      this.rotated = rotated;
   }

   public boolean isRotated() {
      return rotated;
   }

   public void setChartInfo(ChartInfo info) {
      this.chartInfo = info;
   }

   @JsonIgnore
   public ChartInfo getChartInfo() {
      return chartInfo;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      if(!super.equals(o)) {
         return false;
      }

      ChartSubType that = (ChartSubType) o;
      return chartInfo.getChartType() == that.chartInfo.getChartType() &&
         facet == that.facet && dualAxis == that.dualAxis && heatMap == that.heatMap &&
         wordCloud == that.wordCloud && scatterMatrix == that.scatterMatrix &&
         donut == that.donut && rotated == that.rotated && scatter == that.scatter &&
         dotplot == that.dotplot;
   }

   /**
    * Find the type that is closest to this sub-type.
    */
   public int findBestMatch(List<VSSubType> subTypes) {
      int score = 0;
      int idx = -1;

      for(int i = 0; i < subTypes.size(); i++) {
         if(!(subTypes.get(i) instanceof ChartSubType)) {
            continue;
         }

         ChartSubType that = (ChartSubType) subTypes.get(i);

         if(chartInfo.getChartType() != that.chartInfo.getChartType()) {
            continue;
         }

         if(heatMap != that.heatMap || wordCloud != that.wordCloud || dotplot != that.dotplot ||
            scatterMatrix != that.scatterMatrix || donut != that.donut && scatter != that.scatter)
         {
            continue;
         }

         if(that.equals(this)) {
            return i;
         }

         int score0 = 5;

         if(facet == that.facet) {
            score0 += 3;
         }

         if(dualAxis == that.dualAxis) {
            score0 += 2;
         }

         if(rotated == that.rotated) {
            score0 += 1;
         }

         if(score0 > score) {
            idx = i;
         }
      }

      return idx;
   }


   @Override
   public void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      writer.print(" facet=\"" + facet + "\"");
      writer.print(" dualAxis=\"" + dualAxis + "\"");
      writer.print(" heatMap=\"" + heatMap + "\"");
      writer.print(" wordCloud=\"" + wordCloud + "\"");
      writer.print(" scatter=\"" + scatter + "\"");
      writer.print(" scatterMatrix=\"" + scatterMatrix + "\"");
      writer.print(" donut=\"" + donut + "\"");
      writer.print(" rotated=\"" + rotated + "\"");
      writer.print(" histogram=\"" + histogram + "\"");
      writer.print(" dotplot=\"" + dotplot + "\"");
   }

   @Override
   public void writeContents(PrintWriter writer) {
      if(chartInfo != null) {
         chartInfo.writeXML(writer);
      }
   }

   @Override
   public void parseAttributes(Element elem) {
      super.parseAttributes(elem);

      setFacet("true".equalsIgnoreCase(Tool.getAttribute(elem, "facet")));
      setDualAxis("true".equalsIgnoreCase(Tool.getAttribute(elem, "dualAxis")));
      setHeatMap("true".equalsIgnoreCase(Tool.getAttribute(elem, "heatMap")));
      setWordCloud("true".equalsIgnoreCase(Tool.getAttribute(elem, "wordCloud")));
      setScatter("true".equalsIgnoreCase(Tool.getAttribute(elem, "scatter")));
      setScatterMatrix("true".equalsIgnoreCase(Tool.getAttribute(elem, "scatterMatrix")));
      setDonut("true".equalsIgnoreCase(Tool.getAttribute(elem, "donut")));
      setRotated("true".equalsIgnoreCase(Tool.getAttribute(elem, "rotated")));
      setHistogram("true".equalsIgnoreCase(Tool.getAttribute(elem, "histogram")));
      setDotplot("true".equalsIgnoreCase(Tool.getAttribute(elem, "dotplot")));
   }

   @Override
   public void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element enode = Tool.getChildNodeByTagName(elem, "VSChartInfo");

      if(enode != null) {
         chartInfo = VSChartInfo.createVSChartInfo(enode);
      }
   }

   private boolean facet = false;
   private boolean dualAxis = false;
   private boolean heatMap = false;
   private boolean wordCloud = false;
   private boolean scatter;
   private boolean scatterMatrix;
   private boolean donut;
   private boolean rotated;
   private boolean histogram;
   private boolean dotplot;
   private ChartInfo chartInfo;
}
