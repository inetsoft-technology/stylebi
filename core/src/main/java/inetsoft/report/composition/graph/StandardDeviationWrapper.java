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
package inetsoft.report.composition.graph;

import inetsoft.graph.guide.form.StandardDeviationStrategy;
import inetsoft.graph.guide.form.TargetStrategy;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.DynamicValue;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.util.*;

/**
 * Data transport class for Standard Deviation Target Strategy
 */
public class StandardDeviationWrapper extends TargetStrategyWrapper{
   /**
    * Convenience constructor.
    */
   public StandardDeviationWrapper(){
      // Accept defaults
   }

   /**
    * Build from an array of doubles.
    */
   public StandardDeviationWrapper(boolean isSample, String... factors) {
      for(String f : factors) {
         this.factors.add(new DynamicValue(f, XSchema.DOUBLE));
      }

      this.isSample = new DynamicValue(Boolean.toString(isSample), XSchema.BOOLEAN);
   }

   /**
    * Build from an array of Doubles.
    */
   public StandardDeviationWrapper(boolean isSample, Double... factors) {
      for(Double d : factors) {
         this.factors.add(new DynamicValue(d + "", XSchema.DOUBLE));
      }

      this.isSample = new DynamicValue(Boolean.toString(isSample), XSchema.BOOLEAN);
   }

   /**
    * Removes all percentages from the list.
    */
   public void clear() {
      this.factors.clear();
   }

   @Override
   public TargetStrategy unwrap() {
      return new StandardDeviationStrategy(unwrapList(factors),
         Tool.getBooleanData(isSample.getRuntimeValue(true)));
   }

   /**
    * Gets the name of the statistic.  Used for labels and in the GUI.
    * Override this method or the toString() method to change the name.
    *
    * @return the name of the statistic.
    */
   @Override
   public String getName() {
      return STDV;
   }

   /**
    * Gets the label(for description).
    */
   @Override
   public String getGenericLabel() {
      return genericLabelTemplate(null, factors) + " " +
         Catalog.getCatalog().getString(getName()) + "s";
   }

   /**
    * Gets the descrip label same like as.
    */
   public String getDescriptLabel() {
      return getFactors() + " " + Catalog.getCatalog().getString(getName()) + "s";
   }

   /**
    * Set strategy parameters from array of target parameters.
    */
   @Override
   public void setParameters(TargetParameterWrapper... parameters) {
      factors.clear();

      for(TargetParameterWrapper tpw : parameters) {
         factors.add(tpw.getConstantValue());
      }
   }


   /**
    * Build the Strategy from an XML element.
    */
   @Override
   public void parseXml(Element element) throws Exception {
      factors.addAll(readXmlList(element));
      isSample = new DynamicValue("true", XSchema.BOOLEAN);

      Element sampleNode = Tool.getChildNodeByTagName(element, "isSample");

      if(sampleNode != null) {
         String valueAttr = Tool.getAttribute(sampleNode, "value");

         if(!(valueAttr == null || valueAttr.isEmpty())) {
            isSample.setDValue(valueAttr);
         }
      }
   }

   /**
    * Returns an xml element which is a serialized version of the strategy.
    */
   @Override
   public String generateXmlContents() {
      return generateXmlList(factors) + "<isSample value=\"" +
         Tool.encodeHTMLAttribute(isSample.getDValue()) + "\" />";
   }

   /**
    * Returns references to all dynamic values in the strategy so they can
    * be executed appropriately for viewsheets.
    */
   @Override
   public Collection<DynamicValue> getDynamicValues() {
      List<DynamicValue> dValues = new ArrayList<>(factors);
      dValues.add(isSample);
      return dValues;
   }

   @Override
   public Map<String, Object> generateDTOContents() {
      Map<String, Object> dtoContents = new HashMap<>();

      dtoContents.put("factors", generateDTOList(factors));
      dtoContents.put("isSample", Tool.encodeHTMLAttribute(isSample.getDValue()));

      return dtoContents;
   }

   @Override
   public void readDTO(Map<String, Object> value) {
      factors.addAll(readDTOList((List) value.get("factors")));
      isSample = new DynamicValue("true", XSchema.BOOLEAN);

      if(value.get("isSample") != null) {
         String sampleValue = (String) value.get("isSample");

         if(!(sampleValue == null || sampleValue.isEmpty())) {
            isSample.setDValue(sampleValue);
         }
      }
   }

   /**
    * The toString method will display a label for the combobox
    */
   @Override
   public String toString() {
      return Catalog.getCatalog().getString("Standard Deviation") + " (" + getFactors() + ")";
   }

   /**
    * @return a comma separated list of factors.
    */
   public String getFactors() {
      return commaSeparatedList(factors);
   }

   /**
    * Get whether this is a sample or population standard deviation
    */
   public String isSample() {
      return isSample.getDValue();
   }

   /**
    * Set whether this is a sample or population standard deviation
    */
   public void setSample(boolean value) {
      isSample.setDValue(String.valueOf(value));
   }

   /**
    * Set whether this is a sample or population standard deviation
    */
   public void setSample(String value) {
      isSample.setDValue(value);
   }

   // The percentages we want to take.
   private List<DynamicValue> factors = new ArrayList<>();
   private DynamicValue isSample = new DynamicValue("true", XSchema.BOOLEAN);
}
