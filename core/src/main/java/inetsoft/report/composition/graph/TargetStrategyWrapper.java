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

import inetsoft.graph.guide.form.TargetStrategy;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.DynamicValue;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.*;

/**
 * Serves as data transport and interface to gui elements for
 * target strategies.
 */
public abstract class TargetStrategyWrapper implements Serializable {
   /**
    * Generate the Runtime Target Strategy.
    */
   public abstract TargetStrategy unwrap();

   /**
    * Gets the name of the statistic.  Used for labels and in the GUI.
    * Override this method or the toString() method to change the name
    * @return the name of the statistic
    */
   public abstract String getName();

   /**
    * Gets the label(for description in GUI)
    */
   public abstract String getGenericLabel();

   /**
    * Shared code used by several subclasses to generate a display label
    */
   protected static String genericLabelTemplate(String prepend,
                                                List<DynamicValue> vals)
   {
      StringBuilder sb = new StringBuilder();
      boolean first = true;
      boolean onlyOne = false;

      if(prepend != null) {
         sb.append(Catalog.getCatalog().getString(prepend)).append(" ");
      }

      // List percentages
      for(DynamicValue d : vals) {
         if(first) {
            first = false;
            onlyOne = true;
         }
         else {
            sb.append(", ");
            onlyOne = false;
         }

         sb.append(d);
      }

      if(onlyOne) {
         sb.append(" ");
      }

      return sb.toString();
   }

   /**
    * Set strategy parameters from array of target parameters.
    */
   public abstract void setParameters(TargetParameterWrapper... parameters);

   /**
    * Build the Strategy from an XML element.
    */
   public abstract void parseXml(Element element) throws Exception;

   /**
    * Returns an xml element which is a serialized version of the strategy.
    */
   public abstract String generateXmlContents();

   public abstract void readDTO(Map<String, Object> value);

   public abstract Map<String, Object> generateDTOContents();
   /**
    * Returns references to all dynamic values in the strategy so they can
    * be executed appropriately for viewsheets.
    */
   public abstract Collection<DynamicValue> getDynamicValues();

   /**
    * Shared code for several strategies which use lists of values.
    */
   protected static String generateXmlList(Collection<DynamicValue> values) {
      StringBuilder sb = new StringBuilder();
      sb.append("<values>");

      for(DynamicValue d : values) {
         sb.append("<value val=\"").append(d).append("\"/>");
      }

      sb.append("</values>");

      return sb.toString();
   }

   /**
    * Shared code for reading xml from generateXmlList().
    */
   protected static Collection<DynamicValue> readXmlList(Element element) {
      List<DynamicValue> ret = new ArrayList<>();
      Element parametersNode =
         Tool.getChildNodeByTagName(element, "values");

      if(parametersNode != null) {
         NodeList children =
            Tool.getChildNodesByTagName(parametersNode, "value");

         // For each child, extract the attribute
         for(int i = 0; i < children.getLength(); i++) {
            Element current = (Element)children.item(i);

            try {
               ret.add(new DynamicValue(
                  current.getAttribute("val"), XSchema.DOUBLE));
            }
            catch(NumberFormatException e) {
               // Ignore and skip
            }
         }
      }

      return ret;
   }

   protected static List<Object> generateDTOList(
      Collection<DynamicValue> values)
   {
      List<Object> dtoList = new ArrayList<>();

      for(DynamicValue d : values) {
         dtoList.add(d.toString());
      }

      return dtoList;
   }

   protected static Collection<DynamicValue> readDTOList(List<?> valueList) {
      List<DynamicValue> ret = new ArrayList<>();

      if(valueList != null) {
         for(Object item : valueList) {
            String d = (String) item;
            try {
               ret.add(new DynamicValue(d, XSchema.DOUBLE));
            }
            catch(NumberFormatException e) {
               // Ignore and skip
            }
         }
      }

      return ret;
   }

   /**
    * Shared code for use by target strategies to get
    * the appropriate runtime value for their dynamic values.
    */
   static List<Double> runtimeValueOf(DynamicValue d, boolean scalar) {
      List<Double> list = new ArrayList<>();

      if(d == null) {
         return list;
      }

      Object rval = d.getRuntimeValue(scalar);

      if(rval == null) {
         return list;
      }
      
      if(rval.getClass().isArray()) {
         for(int i = 0; i < Array.getLength(rval); i++) {
            list.add(Tool.getDoubleData(Array.get(rval, i)));
         }
      }
      else {
         list.add(Tool.getDoubleData(rval));
      }

      for(int i = 0; i < list.size(); i++) {
         if(list.get(i) == null) {
            list.remove(i--);
         }
      }

      return list;
   }

   /**
    * Shared code for use by target strategies to get
    * the appropriate runtime value for their dynamic values.
    */
   public static Double[] runtimeValuesOf(DynamicValue d) {
      if(d == null) {
         return new Double[0];
      }

      Object runtimeVal = d.getRuntimeValue(false);

      // If the script gave an array, separate into array
      if(runtimeVal instanceof Object[]) {
         Object[] arr = (Object[]) runtimeVal;
         Double[] ret = new Double[arr.length];

         for(int i = 0; i < arr.length; i++) {
            Double dVal = Tool.getDoubleData(arr[i]);
            ret[i] = dVal == null ? 0 : dVal;
         }

         return ret;
      }
      // Otherwise, make array of the single returned value
      else {
         Double dVal = Tool.getDoubleData(runtimeVal);
         return (dVal == null) ? new Double[0] : new Double[] {dVal};
      }
   }

   /**
    * Shared code to get the runtime values of an entire collection of DVs.
    */
   protected static Double[] runtimeValuesOf(Collection<DynamicValue> vals) {
      // Array of arrays, because each dynamic value may expand to multiple
      Double[][] runtimeVals = new Double[vals.size()][];
      int totalLength = 0;
      int i = 0;

      // Get runtime values of all the DynamicValues
      for(DynamicValue dv : vals){
         runtimeVals[i] = runtimeValuesOf(dv);
         totalLength += runtimeVals[i].length;
         i++;
      }

      // Finally, build one array containing all values from all arrays
      Double[] finalResult = new Double[totalLength];
      int totalIndex = 0;

      for(Double[] runtimeVal : runtimeVals) {
         // Iterate through j'th dynamic value
         for(Double value : runtimeVal) {
            finalResult[totalIndex++] = value;
         }
      }

      return finalResult;
   }

   /**
    * Shared code which returns a comma separated list of design time values.
    */
   protected static String commaSeparatedList(Collection<DynamicValue> vals) {
      StringBuilder sb = new StringBuilder();
      boolean first = true;

      for(DynamicValue dv : vals) {
         if(first) {
            first = false;
         }
         else {
            sb.append(",");
         }

         sb.append(dv.getDValue());
      }

      return sb.toString();
   }

   // Used by the unwrap methods of subclasses to convert their
   // dynamic value lists into runtime double lists.
   protected List<Double> unwrapList(List<DynamicValue> list) {
      List<Double> ret = new ArrayList<>();

      // Get all values from dynamic values
      Double[] vals = runtimeValuesOf(list);

      // Add them to the returning list
      Collections.addAll(ret, vals);
      return ret;
   }

   public static TargetStrategyWrapper fromClassName(String stratClassStr)
      throws Exception
   {
      Class stratClass = Class.forName(stratClassStr);
      return (TargetStrategyWrapper)stratClass.newInstance();
   }

   public static final Catalog catalog = Catalog.getCatalog();
   public static final String CONF = catalog.getString("Confidence Interval");
   public static final String PCGE = catalog.getString("Percentages");
   public static final String PCLE = catalog.getString("Percentiles");
   public static final String QTLE = catalog.getString("Quantiles");
   public static final String STDV = catalog.getString("Standard Deviation");
}
