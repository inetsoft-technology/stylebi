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
import inetsoft.graph.data.DataSet;
import inetsoft.uql.viewsheet.graph.ChartDescriptor;

/**
 * Add property for chart scriptable.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public interface CommonChartScriptable extends BaseScriptable {
   /**
    * Add a property to a scriptable.
    * @name property name.
    * @param obj value as a String, Boolean, Number, or Scriptable.
    */
   public void addProperty(String name, Object obj);
   /**
    * Add a property to this scriptable.
    * @param name property name.
    * @param getter getter method for retrieving the property value.
    * @param setter setter method for changing  the property value.
    * @para type property type.
    * @param cls the class of the element object.
    */
   public void addProperty(String name, String getter, String setter,
                           Class type, Class cls);
   /**
    * Add a property to this scriptable.
    * @param name property name.
    * @param setter setter method for changing  the property value.
    * @para type property type.
    * @param cls the class of the element object.
    * @param value the value of the property.
    */
   public void addProperty(String name, String setter,
                           Class type, Class cls, Object value);
   /**
    * Add a property to this scriptable.
    * @param name property name.
    * @param getter getter method for retrieving the property value.
    * @param setter setter method for changing  the property value.
    * @para type property type.
    * @param cls the class of the element object.
    * @param object the target object to invoke setter/getter.
    */
   public void addProperty(String name, String getter, String setter,
                           Class type, Class cls, Object object);
   /**
    * Add a property to this scriptable.
    * @param name property name.
    * @param getter getter method for retrieving the property value.
    * @param setter setter method for changing  the property value.
    * @para type property type.
    * @param cls the class of the element object.
    * @param params the parameters to call setter and getter. For setter,
    * the set value is appended to parameters.
    */
   public void addProperty(String name, String getter, String setter,
                           Class type, Class cls, Object[] params);

   /**
    * Get the graph object in the chart.
    */
   public EGraph getEGraph();

   /**
    * Get the dataset plotted in graph.
    */
   public DataSet getDataSet();

   /**
    * Get runtime chart descriptor.
    */
   public ChartDescriptor getRTChartDescriptor();
}
