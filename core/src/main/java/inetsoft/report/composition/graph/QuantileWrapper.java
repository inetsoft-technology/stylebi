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

import inetsoft.graph.guide.form.QuantileStrategy;
import inetsoft.graph.guide.form.TargetStrategy;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.DynamicValue;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.util.*;

/**
 * Data transport class for Quantile Target Strategy
 */
public class QuantileWrapper extends TargetStrategyWrapper {
   @Override
   public TargetStrategy unwrap() {
      List<Double> rval = runtimeValueOf(numberOfQuantiles, true);
      double val = 0;

      if(rval.size() > 0) {
         val = rval.get(0);
      }

      return new QuantileStrategy((int) Math.round(val));
   }

   /**
    * Convenience constructor
    */
   public QuantileWrapper() {
      // Accept Defaults
   }

   /**
    * Initialize with a number of quantiles
    */
   public QuantileWrapper(String numberOfQuantiles) {
      this.numberOfQuantiles.setDValue(numberOfQuantiles);
   }

   /**
    * Gets the name of the statistic.  Used for labels and in the GUI.
    * Override this method or the toString() method to change the name
    *
    * @return the name of the statistic
    */
   @Override
   public String getName() {
      return QTLE;
   }

   /**
    * Gets the label(for description)
    */
   @Override
   public String getGenericLabel() {
      return QuantileStrategy.getGenericLabel();
   }

   /**
    * gets the descript label same like as.
    */
   public String getDescriptLabel() {
      return getNumberOfQuantiles() + " " + Catalog.getCatalog().getString(getName());
   }

   /**
    * Set strategy parameters from array of target parameters
    */
   @Override
   public void setParameters(TargetParameterWrapper... parameters) {
      if(parameters.length > 0) {
         numberOfQuantiles = parameters[0].getConstantValue();
      }
   }

   /**
    * Build the Strategy from an XML element
    */
   @Override
   public void parseXml(Element element) throws Exception {
      Element qCountNode = Tool.getChildNodeByTagName(element, "quantileCount");
      assert qCountNode != null;
      numberOfQuantiles.setDValue(qCountNode.getAttribute("val"));
   }

   /**
    * Returns an xml element which is a serialized version of the strategy
    */
   @Override
   public String generateXmlContents() {
      return "<quantileCount val=\"" + numberOfQuantiles + "\"/>";
   }

   @Override
   public Map<String, Object> generateDTOContents() {
      Map<String, Object> dtoContents = new HashMap<>();
      dtoContents.put("quantileCount", numberOfQuantiles.toString());

      return dtoContents;
   }

   @Override
   public void readDTO(Map<String, Object> value) {
      numberOfQuantiles.setDValue((String) value.get("quantileCount"));
   }

   /**
    * Returns references to all dynamic values in the strategy so they can
    * be executed appropriately for viewsheets
    */
   @Override
   public Collection<DynamicValue> getDynamicValues() {
      return Collections.singletonList(numberOfQuantiles);
   }

   public String getNumberOfQuantiles() {
      return numberOfQuantiles.getDValue();
   }

   /**
    * The toString method will display a label for the combobox
    */
   @Override
   public String toString() {
      return Catalog.getCatalog().getString("Quantiles") +
         " (" + getNumberOfQuantiles() + ")";
   }

   public void setNumberOfQuantiles(int numberOfQuantiles) {
      this.numberOfQuantiles.setDValue("" + numberOfQuantiles);
   }

   private DynamicValue numberOfQuantiles = new DynamicValue("4", XSchema.DOUBLE);
}
