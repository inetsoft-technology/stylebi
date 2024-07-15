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
package inetsoft.report.composition.graph;


import inetsoft.graph.guide.form.DynamicLineStrategy;
import inetsoft.graph.guide.form.TargetStrategy;
import inetsoft.uql.viewsheet.DynamicValue;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.*;

/**
 * Data transport class for Dynamic Line Target Strategy
 */
public class DynamicLineWrapper extends TargetStrategyWrapper {
   /**
    * Convenience empty constructor
    */
   public DynamicLineWrapper() {
      this(new TargetParameterWrapper[0]);
   }

   // This constructor generally used from script
   public DynamicLineWrapper(String... values) {
      addParameters(values);
   }

   /**
    * Add parameters as Strings
    */
   public void addParameters(String... values) {
      for(String s : values) {
         addParameter(s);
      }
   }

   /**
    * Add a parameter as a string
    */
   private void addParameter(String value) {
      lines.add(new TargetParameterWrapper(value));
   }

   /**
    * Constructor taking any number of doubles
    */
   public DynamicLineWrapper(TargetParameterWrapper... parameters) {
      setParameters(parameters);
   }

   /**
    * Set the parameters list
    */
   @Override
   public void setParameters(TargetParameterWrapper... parameters) {
      lines.clear();
      addParameters(parameters);
   }

   /**
    * Set the parameters list
    * @param parameters array of string parameters
    */
   public void setParameters(String... parameters) {
      lines.clear();
      addParameters(parameters);
   }

   /**
    * Add parameters to the list
    */
   public void addParameters(TargetParameterWrapper... parameters) {
      for(TargetParameterWrapper tpw : parameters) {
         if(tpw != null) {
            lines.add(tpw);
         }
      }
   }

   /**
    * Get the parameters
    */
   public TargetParameterWrapper[] getParameters() {
      return lines.toArray(new TargetParameterWrapper[0]);
   }

   @Override
   public TargetStrategy unwrap() {
      DynamicLineStrategy strat = new DynamicLineStrategy();

      for(TargetParameterWrapper tpw : lines) {
         strat.addParameters(tpw.unwrap());
      }

      return strat;
   }

   /**
    * Gets the name of the statistic.  Used for labels and in the GUI.
    * Override this method or the toString() method to change the name
    *
    * @return the name of the statistic
    */
   @Override
   public String getName() {
      return Catalog.getCatalog().getString("Dynamic Line");
   }

   @Override
   public String getGenericLabel() {
      TargetParameterWrapper[] dtps = new TargetParameterWrapper[lines.size()];
      lines.toArray(dtps);

      if(dtps.length < 1) {
         return null;
      }
      else if(dtps.length < 2) {
         String label0 = dtps[0].toString();
         label0 = label0.isEmpty() ? "0" : label0;
         return Catalog.getCatalog().getString("Line") + " " + label0;
      }
      else {
         String label0 = dtps[0].toString();
         String label1 = dtps[1].toString();
         label0 = label0.isEmpty() ? "0" : label0;
         label1 = label1.isEmpty() ? "0" : label1;

         return Catalog.getCatalog().getString("Band") + " " + label0 +
            " -> " + label1;
      }
   }

   /**
    * Build the Strategy from an XML element
    */
   @Override
   public void parseXml(Element element) throws Exception {
      this.lines.clear();

      Element parametersNode =
         Tool.getChildNodeByTagName(element, "parameters");

      if(parametersNode != null) {
         NodeList children =
            Tool.getChildNodesByTagName(parametersNode, "parameter");

         // Iterate through all children, each one is a parameter
         for(int i = 0; i < children.getLength(); i++) {
            Element current = (Element)children.item(i);
            TargetParameterWrapper tpw = new TargetParameterWrapper();

            if(tpw.parseXml(current)) {
               addParameters(tpw);
            }
         }
      }
   }

   /**
    * Returns an xml element which is a serialized version of the strategy
    */
   @Override
   public String generateXmlContents() {
      StringBuilder sb = new StringBuilder();

      sb.append("<parameters>");
      for(TargetParameterWrapper tpw : lines) {
         sb.append(tpw.getXml());
      }
      sb.append("</parameters>");

      return sb.toString();
   }

   @Override
   public Map<String, Object> generateDTOContents() {
      Map<String, Object> dtoContents = new HashMap<>();
      dtoContents.put("class", getClass().getName());
      List<Object> parameters = new ArrayList<>();

      for(TargetParameterWrapper tpw : lines) {
         parameters.add(tpw.getDTO());
      }

      dtoContents.put("parameters", parameters);

      return dtoContents;
   }

   @Override
   public void readDTO(Map<String, Object> value) {
      this.lines.clear();
      List<?> parameters = (List) value.get("parameters");

      if(parameters != null) {
         for(Object parameter : parameters) {
            TargetParameterWrapper tpw = new TargetParameterWrapper();

            if(tpw.readDTO((Map<String, Object>) parameter)) {
               addParameters(tpw);
            }
         }
      }
   }

   /**
    * Returns references to all dynamic values in the strategy so they can
    * be executed appropriately for viewsheets
    */
   @Override
   public Collection<DynamicValue> getDynamicValues() {
      List<DynamicValue> ret = new ArrayList<>();

      for(TargetParameterWrapper tpw : lines) {
         DynamicValue pt = tpw.getConstantValue();

         if(pt != null) {
            ret.add(pt);
         }
      }

      return ret;
   }

   private List<TargetParameterWrapper> lines = new ArrayList<>();
}
