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
package inetsoft.graph.element;

import inetsoft.graph.GGraph;
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.SortedDataSet;
import inetsoft.graph.geometry.ParaboxLineGeometry;
import inetsoft.graph.geometry.ParaboxPointGeometry;
import inetsoft.graph.scale.*;
import inetsoft.util.Tuple;

import java.util.*;

/**
 * This is a partial implementation of Parabox diagram. Currently only categorical (dimension)
 * columns are supported.
 *
 * @version 13.4
 * @author InetSoft Technology Corp
 */
public class ParaboxElement extends StackableElement {
   /**
    * Create an empty element. Dims and vars must be added explicitly.
    */
   public ParaboxElement() {
      LinearSizeFrame sizes = new LinearSizeFrame();
      sizes.getLegendSpec().setVisible(false);
      setSizeFrame(sizes);
      setTextFrame(new DefaultTextFrame());
   }

   public ParaboxElement(String ...paraFields) {
      this();

      for(String field : paraFields) {
         addParaboxField(field);
      }
   }

   /**
    * Add a parabox dimension (for the parallel coord axis). Currently only dimensions
    * are supported.
    */
   public void addParaboxField(String field) {
      paraboxFields.add(field);
   }

   /**
    * Get the number of parabox fields.
    */
   public int getParaboxFieldCount() {
      return paraboxFields.size();
   }

   /**
    * Get the specified parabox fields.
    */
   public String getParaboxField(int idx) {
      return paraboxFields.get(idx);
   }

   /**
    * Remove the specified parabox field.
    */
   public void removeParaboxField(int idx) {
      paraboxFields.remove(idx);
   }

   /**
    * Get parabox fields.
    */
   public String[] getParaboxFields() {
      return paraboxFields.toArray(new String[0]);
   }

   /**
    * Get the color frame for the connector lines.
    */
   public ColorFrame getLineColorFrame() {
      return lineColorFrame;
   }

   /**
    * Set the color frame for the connector lines.
    */
   public void setLineColorFrame(ColorFrame lineColorFrame) {
      this.lineColorFrame = lineColorFrame;
   }

   /**
    * Get the size frame for the connector lines.
    */
   public SizeFrame getLineSizeFrame() {
      return lineSizeFrame;
   }

   /**
    * Set the size frame for the connector lines.
    */
   public void setLineSizeFrame(SizeFrame lineSizeFrame) {
      this.lineSizeFrame = lineSizeFrame;
   }

   /**
    * Get the weight calculation formula.
    */
   public WeightScale getWeightScale() {
      return weightScale;
   }

   /**
    * Set the weight calculation formula. The default is count.
    */
   public void setWeightScale(WeightScale weightScale) {
      this.weightScale = weightScale;
   }

   @Override
   public void createGeometry(DataSet data, GGraph graph) {
      SortedDataSet sdata = sortData(data, graph);
      data = getSortedDataSetInRange(data, sdata);

      VisualModel vmodel = createVisualModel(data);
      VisualModel vmodel2 = createLineVisualModel(data);
      int max = getEndRow(data);
      Map<Integer, Map<Object, WeightScale>> weights = new HashMap<>();

      // calculate weight for all points on axes so they scales can be initialized before
      // the geometry objects are created.
      for(int i = getStartRow(data); i < max; i++) {
         for(int v = 0; v < getParaboxFieldCount(); v++) {
            Map<Object, WeightScale> varWeights = weights.computeIfAbsent(v, k -> new HashMap<>());
            String vname = getParaboxField(v);
            Object val = data.getData(vname, i);
            WeightScale counter = varWeights.computeIfAbsent(val, k -> weightScale.clone());
            counter.add(data, i);
         }
      }

      initWeight(graph, weights);

      // var-index -> <value -> point>
      Map<Integer, Map<Object, ParaboxPointGeometry>> points = new HashMap<>();
      // var-index -> <[from, to] -> line>
      Map<Integer, Map<Tuple, ParaboxLineGeometry>> lines = new HashMap<>();

      // create points and lines for the parabox graph.
      for(int i = getStartRow(data); i < max; i++) {
         for(int v = 0; v < getParaboxFieldCount(); v++) {
            String vname = getParaboxField(v);
            Map<Object, WeightScale> varWeights = weights.get(v);
            double tval = scale(vname, data, i, graph);
            Object val = data.getData(vname, i);

            if(Double.isNaN(tval)) {
               continue;
            }

            final int r = i;
            Map<Object, ParaboxPointGeometry> varPoints =
               points.computeIfAbsent(v, k -> new HashMap<>());
            Map<Tuple, ParaboxLineGeometry> varLines =
               lines.computeIfAbsent(v, k -> new HashMap<>());
            boolean existPoint = varPoints.containsKey(val);
            ParaboxPointGeometry point = varPoints.computeIfAbsent(
               val, k -> new ParaboxPointGeometry(this, graph, vname, r, vmodel, tval));

            int rootRow = getRootRowIndex(data, i);
            Object weight = varWeights.get(val).getWeight();

            // row index is used to select connected points and lines.
            point.addRowIndex(rootRow);
            point.setWeight(weight);

            // create a unique point for each axis value.
            if(!existPoint) {
               graph.addGeometry(point);
            }

            // create connector lines
            if(v < getParaboxFieldCount() - 1) {
               String vname2 = getParaboxField(v + 1);
               double tval2 = scale(vname2, data, i, graph);
               Object val2 = data.getData(vname2, i);
               Tuple fromTo = new Tuple(val, val2);

               boolean existLine = varLines.containsKey(fromTo);
               ParaboxPointGeometry line = varLines.computeIfAbsent(
                  fromTo, k -> new ParaboxLineGeometry(this, graph, vname, r, vmodel2, tval,
                                                       vname2, tval2));
               line.addRowIndex(rootRow);
               line.setWeight(weight);

               // create a unique connector for each [from, to].
               if(!existLine) {
                  graph.addGeometry(line);
               }
            }
         }
      }

      // create a default size frame to show the count as bubble size.
      if(getSizeFrame() != null && getSizeFrame().getField() == null &&
         getSizeFrame().getScale() == null)
      {
         LinearScale scale = new LinearScale();
         scale.setMax(data.getRowCount());
         getSizeFrame().setScale(scale);
      }
   }

   // Set scale value weights.
   private void initWeight(GGraph graph, Map<Integer, Map<Object, WeightScale>> ps) {
      for(int i = 0; i < getParaboxFieldCount(); i++) {
         Map<Object, WeightScale> pts = ps.get(i);
         Scale scale = graph.getScale(getParaboxField(i));

         if(!(scale instanceof CategoricalScale)) {
            continue;
         }

         CategoricalScale scale2 = (CategoricalScale) scale;

         for(Object val : pts.keySet()) {
            Object weight = pts.get(val).getWeight();

            if(weight instanceof Number) {
               scale2.setWeight(val, ((Number) weight).doubleValue());
            }
         }
      }
   }

   private VisualModel createLineVisualModel(DataSet data) {
      return new VisualModel(data, getLineColorFrame(), getLineSizeFrame(), getShapeFrame(),
                             getTextureFrame(), getLineFrame(), getTextFrame());
   }

   private double scale(String var, DataSet data, int r, GGraph graph) {
      Scale scale = graph.getScale(var);

      if(scale == null) {
         throw new RuntimeException("Scale missing: " + var);
      }

      return scale.map(data.getData(var, r));
   }

   @Override
   public SortedDataSet sortData(DataSet data, GGraph graph) {
      SortedDataSet sdata = super.sortData(data, graph);

      // sort by para fields
      for(String field : paraboxFields) {
         if(sdata == null) {
            sdata = createSortedDataSet(data, field);
         }
         else {
            sdata.addSortColumn(field, false);
         }
      }

      return sdata;
   }

   /**
    * Interface for calculating bubble weights.
    */
   public interface WeightScale extends Cloneable {
      void add(DataSet data, int row);
      Object getWeight();
      WeightScale clone();
   }

   // default to count
   private WeightScale weightScale = new WeightScale() {
      @Override
      public void add(DataSet data, int row) {
         cnt++;
      }

      @Override
      public Object getWeight() {
         return cnt;
      }

      @Override
      public WeightScale clone() {
         try {
            return (WeightScale) super.clone();
         }
         catch(CloneNotSupportedException e) {
            // impossible
            return null;
         }
      }

      private int cnt = 0;
   };

   @Override
   public boolean equalsContent(Object obj) {
      if(!super.equalsContent(obj)) {
         return false;
      }

      if(obj instanceof ParaboxElement) {
         ParaboxElement elem = (ParaboxElement) obj;
         return paraboxFields.equals(elem.paraboxFields) &&
            Objects.equals(lineColorFrame, elem.lineColorFrame) &&
            Objects.equals(lineSizeFrame, elem.lineSizeFrame);
      }

      return false;
   }

   private List<String> paraboxFields = new ArrayList<>();
   private ColorFrame lineColorFrame = new StaticColorFrame();
   private SizeFrame lineSizeFrame = new StaticSizeFrame(1);
}
