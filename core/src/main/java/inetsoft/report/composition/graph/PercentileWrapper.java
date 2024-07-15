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
package inetsoft.report.composition.graph;

import inetsoft.graph.guide.form.PercentileStrategy;
import inetsoft.graph.guide.form.TargetStrategy;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.DynamicValue;
import inetsoft.util.Catalog;
import org.w3c.dom.Element;

import java.util.*;

/**
 * Data transport class for Percentile Target Strategy.
 */
public class PercentileWrapper extends TargetStrategyWrapper {
   public PercentileWrapper() {
      // Accept defaults
   }

   /**
    * Convenience Constructor
    */
   public PercentileWrapper(String percentile) {
      this(new String[]{percentile});
   }

   /**
    * Build from an array of Strings.
    */
   public PercentileWrapper(String[] percentiles) {
      for(String s : percentiles) {
         this.percentiles.add(new DynamicValue(s, XSchema.DOUBLE));
      }
   }

   @Override
   public TargetStrategy unwrap() {
      return new PercentileStrategy(unwrapList(percentiles));
   }

   /**
    * Gets the label(for description).
    */
   @Override
   public String getGenericLabel() {
      return genericLabelTemplate(getName(), percentiles);
   }

   /**
    * Gets the descript label same like as
    */
   public String getDescriptLabel() {
      return getPercentiles() + " " + Catalog.getCatalog().getString(getName());
   }

   /**
    * Set strategy parameters from array of target parameters.
    */
   @Override
   public void setParameters(TargetParameterWrapper... parameters) {
      percentiles.clear();

      for(TargetParameterWrapper tpw : parameters) {
         percentiles.add(tpw.getConstantValue());
      }
   }

   /**
    * Returns an xml element which is a serialized version of the strategy.
    */
   @Override
   public String generateXmlContents() {
      return generateXmlList(percentiles);
   }

   /**
    * Returns references to all dynamic values in the strategy so they can
    * be executed appropriately for viewsheets.
    */
   @Override
   public Collection<DynamicValue> getDynamicValues() {
      return percentiles;
   }

   /**
    * Build the Strategy from an XML element.
    */
   @Override
   public void parseXml(Element element) throws Exception {
      percentiles.addAll(readXmlList(element));
   }

   @Override
   public Map<String, Object> generateDTOContents() {
      Map<String, Object> dtoContents = new HashMap<>();
      dtoContents.put("percentiles", generateDTOList(percentiles));

      return dtoContents;
   }

   @Override
   public void readDTO(Map<String, Object> value) {
      percentiles.addAll(readDTOList((List) value.get("percentiles")));
   }

   /**
    * Gets the name of the statistic.  Used for labels and in the GUI.
    * Override this method or the toString() method to change the name.
    *
    * @return the name of the statistic.
    */
   @Override
   public String getName() {
      return PCLE;
   }

   /**
    * removes all percentiles from the list.
    */
   public void clear() {
      this.percentiles.clear();
   }

   /**
    * The toString method will display a label for the combobox
    */
   @Override
   public String toString() {
      return Catalog.getCatalog().getString("Percentiles") +
         " (" + getPercentiles() + ")";
   }

   /**
    * Get a list of the percentiles.
    */
   public String getPercentiles() {
      return commaSeparatedList(percentiles);
   }

   private List<DynamicValue> percentiles = new ArrayList<>();
}
