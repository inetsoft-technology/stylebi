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
package inetsoft.report.script;

import inetsoft.uql.viewsheet.graph.CompositeTextFormat;
import inetsoft.uql.viewsheet.graph.TitleDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

/**
 * This class represents an TitleDescriptor in the Javascript environment.
 *
 * @version 11.4
 * @author InetSoft Technology Corp
 */
public class TitleScriptable extends PropertyScriptable {
   /**
    * Create a scriptable for a specific legend descriptor.
    */
   public TitleScriptable(TitleDescriptor legend) {
      this.legend = legend;
      init();
   }

   public TitleScriptable(TitleGetter getter) {
      this.getter = getter;
      init2();
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "TitleDescriptor";
   }

   /**
    * Initialize the object.
    */
   private void init() {
      try {
         addProperty("text", "getTitle", "setTitle", String.class,
                     TitleDescriptor.class);
         addProperty("visible", "isVisible", "setVisible", boolean.class,
                     TitleDescriptor.class);
         addProperty("font", "getFont", "setFont", Font.class,
                     CompositeTextFormat.class, legend.getTextFormat());
         addProperty("color", "getColor", "setColor", Color.class,
                     CompositeTextFormat.class, legend.getTextFormat());
         addProperty("rotation", "getRotation", "setRotation", Number.class,
                     CompositeTextFormat.class, legend.getTextFormat());
      }
      catch(Exception ex) {
         LOG.error("Failed to register title descriptor properties", ex);
      }
   }

   /**
    * Initialize the object.
    */
   private void init2() {
      try {
         addProperty("text", "getTitle", "setTitle", String.class,
                     getClass(), this);
         addProperty("visible", "isVisible", "setVisible", boolean.class,
                     getClass(), this);
         addProperty("font", "getFont", "setFont", Font.class,
                     getClass(), this);// legend.getTextFormat());
         addProperty("color", "getColor", "setColor", Color.class,
                     getClass(), this);// legend.getTextFormat());
         addProperty("rotation", "getRotation", "setRotation", Number.class,
                     getClass(), this);//legend.getTextFormat());
      }
      catch(Exception ex) {
         LOG.error("Failed to register title properties", ex);
      }
   }

   public void setTitle(String title) {
      getter.getTitle().setTitle(title);
   }

   public String getTitle() {
      return getter.getTitle().getTitle();
   }

   public void setVisible(boolean visible) {
      getter.getTitle().setVisible(visible);
   }

   public boolean isVisible() {
      return getter.getTitle().isVisible();
   }

   public void setFont(Font font) {
      getter.getTitle().getTextFormat().setFont(font);
   }

   public Font getFont() {
      return getter.getTitle().getTextFormat().getFont();
   }

   public void setColor(Color color) {
      getter.getTitle().getTextFormat().setColor(color);
   }

   public Color getColor() {
      return getter.getTitle().getTextFormat().getColor();
   }

   public void setRotation(Number rotation) {
      getter.getTitle().getTextFormat().setRotation(rotation);
   }

   public Number getRotation() {
      return getter.getTitle().getTextFormat().getRotation();
   }

   /**
    * Get the object for getting and setting properties.
    */
   @Override
   protected Object getObject() {
      return legend;
   }

   public static interface TitleGetter {
      public TitleDescriptor getTitle();
   }

   private TitleDescriptor legend;
   private TitleGetter getter;

   private static final Logger LOG =
      LoggerFactory.getLogger(TitleScriptable.class);
}
