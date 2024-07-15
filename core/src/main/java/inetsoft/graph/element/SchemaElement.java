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

import com.inetsoft.build.tern.*;
import inetsoft.graph.GGraph;
import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.graph.aesthetic.VisualModel;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.geometry.SchemaGeometry;
import inetsoft.graph.scale.Scale;
import inetsoft.graph.schema.SchemaPainter;

import java.util.*;

/**
 * A schema element is used to add schema visualization to a graph. Schemas
 * are user defined drawing. New schemas can be created by implementing the
 * SchemaPainter.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
@TernClass(url = "#cshid=SchemaElement")
public class SchemaElement extends GraphElement {
   /**
    * Create an empty element. Dims and vars must be added explicitly.
    */
   public SchemaElement() {
      super();
   }

   /**
    * Create a schema element with painter. Dims and vars must be added
    * explicitly.
    * @param painter the specified schema painter.
    */
   @TernConstructor
   public SchemaElement(SchemaPainter painter) {
      this();

      setPainter(painter);
   }

   // set dodge so bars don't overlap
   {
      setCollisionModifier(DODGE_SYMMETRIC);
   }

   /**
    * Get the schema painter.
    */
   @TernMethod
   public SchemaPainter getPainter() {
      return painter;
   }

   /**
    * Set the schema painter. The individual visualization of data points are
    * painted by the schema painter.
    */
   @TernMethod
   public void setPainter(SchemaPainter painter) {
      this.painter = painter;
   }

   /**
    * Add a variable to be plotted using this element.
    * @param col the variable identifier.
    */
   @Override
   @TernMethod
   public void addVar(String col) {
      throw new RuntimeException("Use addSchema to add variables.");
   }

   /**
    * Remove the variable at the specified index.
    * @param idx the dim index.
    */
   @Override
   @TernMethod
   public void removeVar(int idx) {
      super.removeVar(idx);
      schemas.remove(idx);
   }

   /**
    * Add a schema columns binding to this element. Check the schema painter to
    * see what columns are required.
    * @param cols the columns to plot on a schema painter.
    */
   @TernMethod
   public void addSchema(String... cols) {
      super.addVar((cols.length > 0) ? cols[0] : null);
      schemas.add(cols);
   }

   /**
    * Remove all variables.
    */
   @Override
   @TernMethod
   public void clearVars() {
      super.clearVars();
      schemas.clear();
   }

   /**
    * Get all the variables.
    */
   @Override
   @TernMethod
   public String[] getVars() {
      List<String> list = new ArrayList<>();

      for(String[] schema : schemas) {
         list.addAll(Arrays.asList(schema));
      }

      return list.toArray(new String[0]);
   }

   /**
    * Check if the aesthetic frame is supported.
    */
   @Override
   public boolean supportsFrame(VisualFrame frame) {
      return super.supportsFrame(frame) && painter.supportsFrame(frame);
   }

   /**
    * Create the geometry objects for the chartLens.
    * @hidden
    * @param data the chartLens to plot using this element.
    * @param graph the containing graph.
    */
   @Override
   public void createGeometry(DataSet data, GGraph graph) {
      VisualModel vmodel = createVisualModel(data);
      int max = getEndRow(data);

      for(int i = getStartRow(data); i < max; i++) {
         if(!isAccepted(data, i)) {
            continue;
         }

         for(String[] vars : schemas) {
            double[] tuple = scale(data, i, vars, graph);

            if(tuple == null || vars.length == 0) {
               continue;
            }

            SchemaGeometry gobj = new SchemaGeometry(this, graph, vars, i, vmodel, tuple);
            gobj.setSubRowIndex(i);
            gobj.setRowIndex(getRootRowIndex(data, i));
            gobj.setColIndex(getRootColIndex(data, vars[0]));
            graph.addGeometry(gobj);
         }
      }
   }

   /**
    * Scale the specified tuple using scales defined in the graph.
    * @param data the specified data set.
    * @param r the specified row index.
    * @param graph the specified graph.
    * @return the new tuple as the result.
    */
   private double[] scale(DataSet data, int r, String[] vars, GGraph graph) {
      int dcnt = getDimCount();
      int vcnt = vars.length;
      double[] tuple = new double[dcnt + vcnt];

      for(int i = 0; i < dcnt; i++) {
         String col = getDim(i);
         Scale scale = getOrCreateScale(graph, col, data);
         tuple[i] = scale.map(data.getData(col, r));

         if(Double.isNaN(tuple[i])) {
            return null;
         }
      }

      for(int i = 0; i < vcnt; i++) {
         String col = vars[i];
         Scale scale = getOrCreateScale(graph, col, data);
         Object value = data.getData(col, r);

         if(value == null) {
            return null;
         }

         tuple[dcnt + i] = scale.map(value);

         if(Double.isNaN(tuple[dcnt + i])) {
            return null;
         }
      }

      return tuple;
   }

   @Override
   public boolean equalsContent(Object obj) {
      if(!super.equalsContent(obj)) {
         return false;
      }

      if(obj instanceof SchemaElement) {
         SchemaElement elem = (SchemaElement) obj;
         return schemas.equals(elem.schemas) && Objects.equals(painter, elem.painter);
      }

      return false;
   }

   private SchemaPainter painter;
   private List<String[]> schemas = new Vector<>(); // schema columns
   private static final long serialVersionUID = 1L;
}
