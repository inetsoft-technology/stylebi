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

import inetsoft.graph.guide.form.ConfidenceIntervalStrategy;
import inetsoft.graph.guide.form.TargetStrategy;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.DynamicValue;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.util.*;

/**
 * Data transport class for Confidence Interval Target Strategy
 */
public class ConfidenceIntervalWrapper extends TargetStrategyWrapper {

   /**
    * Convenience constructor
    */
   public ConfidenceIntervalWrapper() {
      // Accept defaults
   }

   /**
    * Convenience Constructor
    */
   public ConfidenceIntervalWrapper(int confidenceLevel) {
      setConfidenceLevel(confidenceLevel + "");
   }

   /**
    * Construct with the confidence level
    */
   public ConfidenceIntervalWrapper(String confidenceLevel) {
      setConfidenceLevel(confidenceLevel);
   }

   /**
    * Set the confidence level
    */
   public void setConfidenceLevel(String confidenceLevel) {
      this.confidenceLevel.setDValue(confidenceLevel);
   }


   /**
    * Set the confidence level
    */
   public void setConfidenceLevel(DynamicValue confidenceLevel) {
      this.confidenceLevel = confidenceLevel;
   }

   /**
    * @return the current confidence level
    */
   public String getConfidenceLevel() {
      return confidenceLevel.getDValue();
   }

   @Override
   public TargetStrategy unwrap() {
      ConfidenceIntervalStrategy cis = new ConfidenceIntervalStrategy();
      List<Double> rval = runtimeValueOf(confidenceLevel, true);

      if(rval.size() > 0) {
         cis.setConfidenceLevel(rval.get(0));
      }
      // empty value, ignore confidence level
      else {
         cis.setConfidenceLevel(0);
      }

      // Check if multiple parameters were actually passed in, print warning.
      Object testForMultiple = confidenceLevel.getRuntimeValue(false);

      if(testForMultiple instanceof Object[] &&
	      ((Object[]) testForMultiple).length > 1)
      {
         LOG.warn(
            "Multiple parameters detected for confidence level when " +
            "unwrapping the strategy. Using the first and ignoring the rest.");
      }

      return cis;
   }

   /**
    * Gets the name of the statistic.  Used for labels and in the GUI.
    * Override this method or the toString() method to change the name
    *
    * @return the name of the statistic
    */
   @Override
   public String getName() {
      return CONF;
   }

   /**
    * Gets the label for the specified line
    */
   @Override
   public String getGenericLabel() {
      return ConfidenceIntervalStrategy.
         getGenericLabel(confidenceLevel.toString());
   }

   /**
    * Gets the descript label same like as
    */
   public String getDescriptLabel() {
      return getConfidenceLevel() + Catalog.getCatalog().getString("%") + " "  +
         Catalog.getCatalog().getString(getName());
   }

   /**
    * Set strategy parameters from array of target parameters
    */
   @Override
   public void setParameters(TargetParameterWrapper... parameters) {
      if(parameters.length > 0) {
         setConfidenceLevel(parameters[0].getConstantValue());

         if(parameters.length > 1) {
            LOG.warn(
               "Multiple parameters detected for confidence level when " +
               "setting the parameters. Using the first and ignoring the " +
               "rest.");
         }
      }
   }

   /**
    * Build the Strategy from an XML element
    */
   @Override
   public void parseXml(Element element) throws Exception {
      Element cLevelNode =
         Tool.getChildNodeByTagName(element, "confidenceLevel");

      confidenceLevel.setDValue(cLevelNode.getAttribute("val"));
   }

   /**
    * Returns references to all dynamic values in the strategy so they can
    * be executed appropriately for viewsheets
    */
   @Override
   public Collection<DynamicValue> getDynamicValues() {
      List<DynamicValue> ret = new ArrayList<>();
      ret.add(confidenceLevel);

      return ret;
   }

   /**
    * The toString method will display a label for the combobox
    */
   @Override
   public String toString() {
      return Catalog.getCatalog().getString("Confidence Interval") +
         " (" + getConfidenceLevel() + ")";
   }

   /**
    * Returns an xml element which is a serialized version of the strategy
    */
   @Override
   public String generateXmlContents() {
      return "<confidenceLevel val=\"" + confidenceLevel + "\"/>";
   }

   @Override
   public Map<String, Object> generateDTOContents() {
      Map<String, Object> dtoContents = new HashMap<>();
      dtoContents.put("class", getClass().getName());
      dtoContents.put("confidenceLevel", confidenceLevel.toString());

      return dtoContents;
   }

   @Override
   public void readDTO(Map<String, Object> value) {
      confidenceLevel.setDValue((String) value.get("confidenceLevel"));
   }

   private DynamicValue confidenceLevel =
      new DynamicValue("95", XSchema.DOUBLE);
   private Logger LOG = LoggerFactory.getLogger(ConfidenceIntervalWrapper.class);
}
