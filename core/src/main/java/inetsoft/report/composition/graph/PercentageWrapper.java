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

import inetsoft.graph.guide.form.*;
import inetsoft.report.filter.Formula;
import inetsoft.report.filter.MaxFormula;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.DynamicValue;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.util.*;

/**
 * Data transport class for Percentage Target Strategy
 */
public class PercentageWrapper extends TargetStrategyWrapper {
   /**
    * Convenience Constructor
    */
   public PercentageWrapper() {
      this.aggregate.setFormula(new MaxFormula());
   }

   /**
    * Convenience Constructor
    */
   public PercentageWrapper(String percentage, Formula aggregateFormula) {
      this(new String[]{percentage}, aggregateFormula);
   }

   /**
    * Array constructor
    */
   public PercentageWrapper(String[] percentages,
                             Formula aggregateFormula)
   {
      this(percentages, new TargetParameterWrapper(aggregateFormula));
   }

   /**
    * Array Constructor
    */
   public PercentageWrapper(String[] percentages, TargetParameterWrapper agg) {
      this.aggregate = agg;

      for(String s : percentages) {
         this.percentages.add(new DynamicValue(s, XSchema.STRING));
      }
   }

   /**
    * Set the Aggregate
    */
   public void setAggregate(TargetParameterWrapper agg) {
      this.aggregate = agg;
   }

   /**
    * Get the Aggregate
    */
   public TargetParameterWrapper getAggregate() {
      return aggregate;
   }

   /**
    * Get the pass through value of the aggregate
    */
   public String getAggregateConstantValue() {
      return aggregate.getConstantValue().getDValue();
   }

   /**
    * Set the pass through value of the aggregate
    * also sets the formula to null so it will have an effect
    */
   public void setAggregateConstantValue(String newVal) {
      aggregate.setFormula(null);
      aggregate.setConstantValue(new DynamicValue(newVal, XSchema.DOUBLE));
   }

   /**
    * set the Aggregate formula
    */
   public void setAggregateFormula(Formula newFormula) {
      this.aggregate.setFormula(newFormula);
   }

   /**
    * @return the Aggregate formula
    */
   public Formula getAggregateFormula() {
      return this.aggregate.getFormula();
   }

   /**
    * removes all percentages from the list
    */
   public void clear() {
      this.percentages.clear();
   }

   @Override
   public TargetStrategy unwrap() {
      TargetParameter[] aggrs = aggregate.unwrap();
      return new PercentageStrategy(unwrapList(percentages),
                                    aggrs.length > 0 ? aggrs[0] : null);
   }

   /**
    * Gets the name of the statistic.  Used for labels and in the GUI.
    * Override this method or the toString() method to change the name
    *
    * @return the name of the statistic
    */
   @Override
   public String getName() {
      return PCGE;
   }

   /**
    * Gets the label(for description)
    */
   @Override
   public String getGenericLabel() {
      return genericLabelTemplate(getName(), percentages);
   }

   /**
    * Gets the label for descript(like as)
    */
   public String getDescriptLabel() {
      return getPercentages() + " " +
         Catalog.getCatalog().getString(getName()) + " " + aggregate.toString();
   }

   /**
    * Set strategy parameters from array of target parameters
    */
   @Override
   public void setParameters(TargetParameterWrapper... parameters) {
      percentages.clear();

      for(TargetParameterWrapper tpw : parameters) {
         percentages.add(tpw.getConstantValue());
      }
   }

   /**
    * Returns an xml element which is a serialized version of the strategy
    */
   @Override
   public String generateXmlContents() {
      return generateXmlList(percentages) + aggregate.getXml();
   }

   /**
    * Returns references to all dynamic values in the strategy so they can
    * be executed appropriately for viewsheets
    */
   @Override
   public Collection<DynamicValue> getDynamicValues() {
      List<DynamicValue> values = new ArrayList<>(percentages);

      // Add the aggregate value to the percentages, if applicable.
      DynamicValue ptv = aggregate.getConstantValue();

      if(ptv != null) {
         values.add(ptv);
      }

      return values;
   }

   /**
    * Build the Strategy from an XML element
    */
   @Override
   public void parseXml(Element element) throws Exception {
      percentages.addAll(readXmlList(element));

      // Get the aggregate value DTP
      Element aggregateNode = Tool.getChildNodeByTagName(element, "parameter");
      if(aggregateNode != null) {
         aggregate.parseXml(aggregateNode);
      }
   }

   @Override
   public Map<String, Object> generateDTOContents() {
      Map<String, Object> dtoContents = new HashMap<>();

      dtoContents.put("percentages", generateDTOList(percentages));
      dtoContents.put("aggregate", aggregate.getDTO());

      return dtoContents;
   }

   @Override
   public void readDTO(Map<String, Object> value) {
      percentages.addAll(readDTOList((List) value.get("percentages")));
      aggregate.readDTO((Map<String, Object>) value.get("aggregate"));
   }

   /**
    * The toString method will display a label for the combobox
    */
   @Override
   public String toString() {
      return Catalog.getCatalog().getString("Percentages") +
         " (" + getPercentages() + ")";
   }

   /**
    * Return a comma separated list of percentages
    */
   public String getPercentages() {
      return commaSeparatedList(percentages);
   }

   // The percentages we want to take.
   private List<DynamicValue> percentages = new ArrayList<>();

   // Used to determine the aggregate of which we are taking percentages.
   private TargetParameterWrapper aggregate = new TargetParameterWrapper();
}
