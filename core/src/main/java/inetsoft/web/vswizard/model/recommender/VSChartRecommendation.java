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
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class VSChartRecommendation extends VSAbstractObjectRecommendation {
   public VSChartRecommendation() {
      setType(VSRecommendType.CHART);
   }

   /**
    * Set chartinfos for all the recommended chart types;
    */
   public void setChartInfos(List<ChartInfo> chartInfos) {
      this.chartInfos = chartInfos;
   }

   /**
    * Get chartinfos for all the recommended chart types;
    */
   public List<ChartInfo> getChartInfos() {
      return this.chartInfos;
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(chartInfos == null || chartInfos.isEmpty()) {
         return;
      }

      for(int i = 0; i < chartInfos.size(); i++) {
         ChartInfo info = chartInfos.get(i);
         info.writeXML(writer);
      }
   }

   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      NodeList list = Tool.getChildNodesByTagName(elem, "VSChartInfo");
      List<ChartInfo> chartInfos = new ArrayList<>();

      for(int i = 0; i < list.getLength(); i++) {
         Element item = (Element) list.item(i);

         if(item != null) {
            chartInfos.add(VSChartInfo.createVSChartInfo(item));
         }
      }
   }

   @JsonIgnore
   private transient List<ChartInfo> chartInfos;
}
