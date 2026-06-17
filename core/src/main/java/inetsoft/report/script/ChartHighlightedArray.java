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
package inetsoft.report.script;

import inetsoft.graph.EGraph;
import inetsoft.graph.aesthetic.ColorFrame;
import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.scale.Scale;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.composition.graph.HLColorFrame;
import inetsoft.report.filter.Highlight;
import inetsoft.report.script.viewsheet.ChartVSAScriptable;
import inetsoft.util.script.ArrayObject;
import inetsoft.util.script.graal.ScriptArrayScope;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * This array provides access to highlighted status.
 */
public class ChartHighlightedArray implements ArrayObject, ScriptArrayScope {
   /**
    */
   public ChartHighlightedArray(CommonChartScriptable chart) {
      this.chart = chart;
   }

   /**
    * Initialize highlight ids.
    */
   private void init() {
      if(data != null) {
         return;
      }

      this.data = chart.getDataSet();
      EGraph graph = chart.getEGraph();

      if(graph == null) {
         return;
      }

      for(int i = 0; i < graph.getElementCount(); i++) {
         GraphElement elem = graph.getElement(i);
         ColorFrame colors = elem.getColorFrame();

         for(VisualFrame frame : GraphUtil.getColorFrames(colors)) {
            addHLColorFrame(frame);
         }
      }

      for(Scale scale : GraphUtil.getAllScales(graph.getCoordinate())) {
         ColorFrame frame = scale.getAxisSpec().getColorFrame();
         addHLColorFrame(frame);
      }
   }

   private void addHLColorFrame(VisualFrame frame) {
      if(frame instanceof HLColorFrame) {
         HLColorFrame hlframe = (HLColorFrame) frame;

         if(chart instanceof ChartVSAScriptable) {
            hlframe.setQuerySandbox(((ChartVSAScriptable) chart).getViewsheetSandbox()
                                       .getAssetQuerySandbox());
         }

         hlframes.add(hlframe);

         for(Highlight highlight : hlframe.getHighlights()) {
            hlnames.add(highlight.getName());
         }
      }
   }

   /**
    * Get array element type.
    */
   @Override
   public Class getType() {
      return Boolean.class;
   }

   @Override
   public boolean hasMember(String id) {
      init();
      return hlnames.contains(id);
   }

   @Override
   public Object getMember(String id) {
      init();

      if(hlnames.contains(id)) {
         // making a copy to avoid concurrent modification exception
         List<HLColorFrame> hlframes = new ArrayList<>(this.hlframes);

         for(HLColorFrame hlframe : hlframes) {
            if(hlframe.isHighlighted(id, data)) {
               return true;
            }
         }

         return false;
      }

      return members.get(id);
   }

   @Override
   public void putMember(String id, Object value) {
      members.put(id, value);
   }

   @Override
   public Object getArrayElement(long index) {
      return null;
   }

   @Override
   public long getArraySize() {
      return 0;
   }

   @Override
   public Object[] getMemberKeys() {
      init();

      return hlnames.toArray();
   }

   /**
    * Get display suffix.
    */
   @Override
   public String getDisplaySuffix() {
      return "[highlightname]";
   }

   /**
    * Get suffix.
    */
   @Override
   public String getSuffix() {
      return "[]";
   }

   public String getClassName() {
      return "Highlighted";
   }

   private CommonChartScriptable chart;
   private List<String> hlnames = new ArrayList<>();
   private List<HLColorFrame> hlframes = new ArrayList<>();
   private DataSet data;
   private final Map<String, Object> members = new LinkedHashMap<>();
}
