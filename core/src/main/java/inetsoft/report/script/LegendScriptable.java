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
package inetsoft.report.script;

import inetsoft.uql.XFormatInfo;
import inetsoft.uql.viewsheet.graph.LegendDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

/**
 * This class represents an LegendDescriptor in the Javascript environment.
 *
 * @version 11.4
 * @author InetSoft Technology Corp
 */
public class LegendScriptable extends PropertyScriptable {
   /**
    * Create a scriptable for a specific legend descriptor.
    */
   public LegendScriptable(LegendDescriptor legend) {
      this.legend = legend;
      init();
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "LegendDescriptor";
   }

   /**
    * Initialize the object.
    */
   private void init() {
      try {
         addProperty("title", "getTitle", "setTitle", String.class,
                     LegendDescriptor.class);
         addProperty("titleVisible", "isTitleVisible", "setTitleVisible",
                     boolean.class, LegendDescriptor.class);
         addProperty("color", "getColor", "setColor", Color.class,
                     LegendDescriptor.class);
         addProperty("font", "getFont", "setFont", Font.class,
                     LegendDescriptor.class);
         addProperty("format", "getFormat", "setFormat", XFormatInfo.class,
                     LegendDescriptor.class);
         addProperty("noNull", "isNotShowNull", "setNotShowNull", boolean.class,
                     LegendDescriptor.class);
      }
      catch(Exception ex) {
         LOG.error("Failed to register legend properties", ex);
      }
   }

   /**
    * Get the object for getting and setting properties.
    */
   @Override
   protected Object getObject() {
      return legend;
   }

   private LegendDescriptor legend;

   private static final Logger LOG =
      LoggerFactory.getLogger(AxisScriptable.class);
}
