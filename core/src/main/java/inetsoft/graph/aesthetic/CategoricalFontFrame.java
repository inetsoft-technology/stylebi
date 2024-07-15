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
package inetsoft.graph.aesthetic;

import inetsoft.graph.internal.GTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.*;

/**
 * This class defines a font frame for categorical values. Only statically set font
 * is supported. Dataset based value mapping is not supported.
 *
 * @version 12.3
 * @author InetSoft Technology
 */
public class CategoricalFontFrame extends FontFrame implements CategoricalFrame {
   /**
    * Create a font frame for categorical values.
    */
   public CategoricalFontFrame() {
      super();
   }

   /**
    * Get the font for the specified value.
    */
   @Override
   public Font getFont(Object val) {
      Font font = null;

      if(cmap.size() > 0) {
         Object formatted = formatValue(val);
         font = cmap.get(GTool.toString(val));

         if(font == null && formatted != val) {
            font = cmap.get(formatted);
         }
      }

      return font;
   }

   /**
    * Set the font for the specified value.
    */
   public void setFont(Object val, Font font) {
      if(font != null) {
         cmap.put(GTool.toString(val), font);
      }
      else {
         cmap.remove(GTool.toString(val));
      }
   }

   /**
    * Check if the value is assigned a static aesthetic value.
    */
   @Override
   public boolean isStatic(Object val) {
      return cmap.get(GTool.toString(val)) != null || cmap.get(formatValue(val)) != null;
   }

   @Override
   public Set<Object> getStaticValues() {
      return cmap.keySet();
   }

   @Override
   public void clearStatic() {
      cmap.clear();
   }

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      CategoricalFontFrame frame2 = (CategoricalFontFrame) obj;
      return cmap.equals(frame2.cmap);
   }

   /**
    * Create a copy of this object.
    */
   @Override
   public Object clone() {
      try {
         CategoricalFontFrame frame = (CategoricalFontFrame) super.clone();
         frame.cmap = new LinkedHashMap<>(cmap);
         return frame;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone font frame", ex);
         return null;
      }
   }

   private Map<Object, Font> cmap = new LinkedHashMap<>();

   private static final long serialVersionUID = 1L;
   private static final Logger LOG = LoggerFactory.getLogger(CategoricalFontFrame.class);
}
