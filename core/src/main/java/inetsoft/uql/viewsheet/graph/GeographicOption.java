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
package inetsoft.uql.viewsheet.graph;

import inetsoft.report.internal.graph.MapData;
import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.DynamicValue;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * GeographicOption, include the mapping and map layer.
 *
 * @version 10.2
 * @author InetSoft Technology Corp
 */
public class GeographicOption implements AssetObject {
   /**
    * Constructor.
    */
   public GeographicOption() {
      super();

      layerValue = new DynamicValue(
         null, XSchema.INTEGER, MapData.getLayerIds(), MapData.getLayerNames());
   }

   /**
    * Check if equals another objects.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals, <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof GeographicOption)) {
         return false;
      }

      GeographicOption option = (GeographicOption) obj;

      if(mapping != null && !mapping.equals(option.mapping)) {
         return false;
      }

      if(!layerValue.equals(option.layerValue)) {
         return false;
      }

      return true;
   }

   /**
    * Create a clone of this object.
    */
   @Override
   public Object clone() {
      try {
         GeographicOption obj = (GeographicOption) super.clone();

         if(mapping != null) {
            obj.mapping =  (FeatureMapping) mapping.clone();
         }

         obj.layerValue = (DynamicValue) layerValue.clone();

         return obj;
      }
      catch(Exception e) {
         LOG.error("Failed to clone GeographicOption", e);
         return null;
      }
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      return "GeographicOption[mapping: " + mapping + " layer: " +
             layerValue.getDValue() + "]";
   }

   /**
    * Get the mapping.
    */
   public FeatureMapping getMapping() {
      return mapping;
   }

   /**
    * Set the mapping.
    */
   public void setMapping(FeatureMapping mapping) {
      this.mapping = mapping;
   }

   /**
    * Get the layer value.
    * @return the layer option value.
    */
   public String getLayerValue() {
      return layerValue.getDValue();
   }

   /**
    * Get the layer.
    * @return the layer.
    */
   public int getLayer() {
      Number layer = (Number) layerValue.getRuntimeValue(true);
      return (layer == null || getLayerValue() == null) ? -1 : layer.intValue();
   }

   /**
    * Set the layer option value.
    * @param layer the layer option value.
    */
   public void setLayerValue(String layer) {
      this.layerValue.setDValue(layer);
   }

   /**
    * Generate the XML segment to represent this point.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<GeographicOption>");

      if(mapping != null) {
         mapping.writeXML(writer);
      }

      if(layerValue.getDValue() != null) {
         writer.print("<layerValue>");
         writer.print("<![CDATA[" + layerValue.getDValue() + "]]>");
         writer.println("</layerValue>");
      }

      writer.println("</GeographicOption>");
   }

   /**
    * Parse the XML element that contains information on this
    * point.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      Element mnode = Tool.getChildNodeByTagName(tag, "FeatureMapping");

      if(mnode != null) {
         mapping = new FeatureMapping();
         mapping.parseXML(mnode);
      }

      Element lnode = Tool.getChildNodeByTagName(tag, "layerValue");
      layerValue.setDValue(Tool.getValue(lnode));
   }

   /**
    * Rename the depended. This method should be called when an assembly or
    * other named variables are renamed. It updates of the dynamic references
    * to use the new name.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   public void renameDepended(String oname, String nname, Viewsheet vs) {
      VSUtil.renameDynamicValueDepended(oname, nname, layerValue, vs);
   }

   /**
    * Get the dynamic property values. Dynamic properties are properties that
    * can be a variable or a script.
    * @return the dynamic values.
    */
   public DynamicValue getDynamicValue() {
      return layerValue;
   }

   private FeatureMapping mapping = new FeatureMapping();
   private DynamicValue layerValue;
   private static final Logger LOG = LoggerFactory.getLogger(GeographicOption.class);
}
