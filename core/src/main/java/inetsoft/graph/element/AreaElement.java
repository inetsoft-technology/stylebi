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
package inetsoft.graph.element;

import com.inetsoft.build.tern.*;
import inetsoft.graph.EGraph;
import inetsoft.graph.GGraph;
import inetsoft.graph.aesthetic.VisualModel;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.geometry.AreaGeometry;
import inetsoft.graph.geometry.LineGeometry;
import inetsoft.graph.scale.Scale;

import java.util.*;

/**
 * An area element visualizes data as filled areas from data points to the X
 * axis. The bottom of the area can also be defined by another column, so the
 * space between the base line and data points are filled.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
@TernClass(url = "#cshid=AreaElement")
public class AreaElement extends LineElement {
   /**
    * Create an empty element. Dims and vars must be added explicitly.
    */
   public AreaElement() {
      initBases();
   }

   /**
    * Create a area element for a single column (1d).
    */
   public AreaElement(String field1) {
      super(field1);
      initBases();
   }

   /**
    * Create a area element for two columns (2d).
    */
   public AreaElement(String field1, String field2) {
      super(field1, field2);
      initBases();
   }

   /**
    * Create a area element for three columns (3d).
    */
   @TernConstructor
   public AreaElement(String field1, String field2, String field3) {
      super(field1, field2, field3);
      initBases();
   }

   // default transparency
   {
      setHint(HINT_ALPHA, 0.8);
      setFillLineStyle(0);
   }

   /**
    * Create the bases list.
    */
   private void initBases() {
      if(bases == null) {
         bases = new Vector();
      }
   }

   /**
    * Add a variable to be plotted using this element.
    * @param col the variable identifier.
    */
   @Override
   @TernMethod
   public void addVar(String col) {
      addInterval(null, col);
   }

   /**
    * Remove the variable at the specified index.
    * @param idx the dim index.
    */
   @Override
   @TernMethod
   public void removeVar(int idx) {
      super.removeVar(idx);
      bases.remove(idx);
   }

   /**
    * Add an interval to this element.
    * @param col1 the lower bound of the interval.
    * @param col2 the upper bound of the interval.
    */
   @TernMethod
   public void addInterval(String col1, String col2) {
      super.addVar(col2);
      initBases();
      bases.add(col1);
   }

   /**
    * Remove all variables.
    */
   @Override
   @TernMethod
   public void clearVars() {
      super.clearVars();
      bases.clear();
   }

   /**
    * Get all the variables.
    */
   @Override
   @TernMethod
   public String[] getVars() {
      List<String> list = new ArrayList<>();

      for(int i = 0; i < getVarCount(); i++) {
         list.add(getVar(i));
      }

      for(String obj : bases) {
         if(obj != null) {
            list.add(obj);
         }
      }

      return list.toArray(new String[list.size()]);
   }

   /**
    * Create the geometry object.
    */
   @Override
   LineGeometry createGeometry(LineElement elem, GGraph graph, String vname,
                               VisualModel vmodel)
   {
      return new AreaGeometry(elem, graph, vname, vmodel);
   }

   /**
    * Add a tuple to geometry.
    */
   @Override
   void addTuple(LineGeometry gobj, double[] tuple, int vidx,
                 DataSet data, int row, EGraph egraph)
   {
      gobj.addTuple(tuple);

      // add the base point tuple
      String basevar = null;
      Scale bscale = null;

      if(vidx >= 0 && vidx < bases.size() && bases.get(vidx) != null) {
         basevar = bases.get(vidx);
         bscale = getOrCreateScale(egraph, basevar, data);
      }
      else if(vidx >= 0 && getVarCount() > 0) {
         bscale = getOrCreateScale(egraph, getVar(vidx), data);
      }
      else {
         bscale = getOrCreateScale(egraph, getDim(vidx), data);
      }

      double base = 0;

      if(basevar != null) {
         base = bscale.map(data.getData(basevar, row));
      }
      else if(bscale != null) {
         // parallel, the bottom of each axis is the base
         if(getVarCount() == 0) {
            base = bscale.getMin();
         }
         else {
            // base should be at 0, also the case max<min (reversed scale)
            base = Math.max(0, Math.min(bscale.getMin(), bscale.getMax()));
         }
      }

      double[] basetuple = tuple.clone();

      // parallel
      if(getVarCount() == 0) {
         basetuple[vidx] = base;
      }
      else {
         basetuple[tuple.length - 1] = base;
      }

      ((AreaGeometry) gobj).addBaseTuple(basetuple);
   }

   /**
    * Optionally sort the geometries.
    */
   @Override
   Collection<String> sortGeometry(Map<String, LineGeometry> map) {
      List<String> keys = new ArrayList<>(map.keySet());

      final Comparator comp = (k1, k2) -> {
         AreaGeometry geometry1 = (AreaGeometry) map.get(k1);
         AreaGeometry geometry2 = (AreaGeometry) map.get(k2);
         return geometry1.compareTo(geometry2);
      };

      Collections.sort(keys, comp);
      return keys;
   }

   @Override
   public boolean equalsContent(Object obj) {
      if(!super.equalsContent(obj)) {
         return false;
      }

      if(obj instanceof AreaElement) {
         AreaElement elem = (AreaElement) obj;
         return bases.equals(elem.bases);
      }

      return false;
   }

   private List<String> bases; // interval base columns
   private static final long serialVersionUID = 1L;
}
