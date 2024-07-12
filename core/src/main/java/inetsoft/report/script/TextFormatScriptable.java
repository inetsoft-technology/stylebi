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
import inetsoft.uql.viewsheet.graph.CompositeTextFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

/**
 * This class represents a TextFormatDescriptor in the Javascript environment.
 *
 * @version 11.4
 * @author InetSoft Technology Corp
 */
public class TextFormatScriptable extends PropertyScriptable {
   /**
    * Create a scriptable for a specific legend descriptor.
    */
   public TextFormatScriptable(CompositeTextFormat fmt) {
      this.fmt = fmt;
      init();
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "TextFormatDescriptor";
   }

   /**
    * Initialize the object.
    */
   private void init() {
      try {
         addProperty("format", "getFormat", "setFormat", XFormatInfo.class,
                     CompositeTextFormat.class, fmt);
         addProperty("font", "getFont", "setFont", Font.class,
                     CompositeTextFormat.class, fmt);
         addProperty("color", "getColor", "setColor", Color.class,
                     CompositeTextFormat.class, fmt);
         addProperty("rotation", "getRotation", "setRotation", Number.class,
                     CompositeTextFormat.class, fmt);
      }
      catch(Exception ex) {
         LOG.error("Failed to register text format properties", ex);
      }
   }

   /**
    * Get the object for getting and setting properties.
    */
   @Override
   protected Object getObject() {
      return fmt;
   }

   private CompositeTextFormat fmt;

   private static final Logger LOG =
      LoggerFactory.getLogger(TextFormatScriptable.class);
}
