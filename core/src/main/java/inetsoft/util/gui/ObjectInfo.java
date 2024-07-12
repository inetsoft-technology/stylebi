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
package inetsoft.util.gui;

import inetsoft.uql.DataSourceListing;
import inetsoft.uql.XDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.io.InputStream;
import java.util.HashMap;

/**
 * ObjectInfo, the infomations for a data object, such as icon, label used for
 * query, report, worksheet and so on.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class ObjectInfo {
   /**
    * Report.
    */
   public static final String REPORT = "report";
   /**
    * Report Component.
    */
   public static final String COMPONENT = "component";
   /**
    * Data Source.
    */
   public static final String DATA_SOURCE = "data source";
   /**
    * Worksheet.
    */
   public static final String WORKSHEET = "worksheet";

   /**
    * Table Style.
    */
   public static final String TABLE_STYLE = "table style";
   /**
    * Script Function.
    */
   public static final String SCRIPT_FUNCTION = "script function";
   /**
    * Physical View.
    */
   public static final String PHYSICAL_VIEW = "physical view";
   /**
    * Logical Model.
    */
   public static final String LOGICAL_MODEL = "logical model";
   /**
    * Query.
    */
   public static final String QUERY = "query";

   /**
    * VPM.
    */
   public static final String VPM = "vpm";

   /**
    * Check current type is a createable object type.
    */
   public static boolean isCreateable(String type) {
      return type != null &&!COMPONENT.equals(type) &&
             !DATA_SOURCE.equals(type) && !PHYSICAL_VIEW.equals(type) &&
             !LOGICAL_MODEL.equals(type) && !VPM.equals(type);
   }

   /**
    * Check current type is a createable object type.
    */
   public static boolean isCreateable(String type, String subtype) {
      boolean model = PHYSICAL_VIEW.equals(type) || LOGICAL_MODEL.equals(type)
         || VPM.equals(type);

      if(model) {
         return XDataSource.JDBC.equals(subtype);
      }

      return (!(QUERY.equals(type) && XDataSource.XMLA.equals(subtype)));
   }

   /**
    * Check current type is ignore subtype or not.
    */
   public static boolean ignoreSubType(String type) {
      return PHYSICAL_VIEW.equals(type) || LOGICAL_MODEL.equals(type) || VPM.equals(type);
   }

   /**
    * Default constructor.
    */
   public ObjectInfo() {
      super();
   }

   /**
    * Constructor.
    * @param type the type for this object.
    */
   public ObjectInfo(String type) {
      this(type, null);
   }

   /**
    * Constructor.
    * @param type the object type.
    * @param label the label for this object.
    */
   public ObjectInfo(String type, String label) {
      this(type, label, label);
   }

   /**
    * Constructor.
    * @param type the object type.
    * @param label the label for this object.
    * @param displayLabel the display label for this object.
    */
   public ObjectInfo(String type, String label, String displayLabel) {
      this(type, label, displayLabel, null);
   }

   /**
    * Constructor.
    * @param type the object type.
    * @param label the label for this object.
    * @param displayLabel the display label for this object.
    * @param desc the descriptor this this object.
    */
   public ObjectInfo(String type, String label, String displayLabel,
                     String desc) {
      this(type, label, displayLabel, desc, (String) null);
   }

   /**
    * Constructor.
    * @param type the object type.
    * @param label the label for this object.
    * @param displayLabel the display label for this object.
    * @param desc the descriptor this this object.
    * @param iconURL the icon url for this object.
    */
   public ObjectInfo(String type, String label, String displayLabel,
                     String desc, String iconURL) {
      this.type = type;
      this.label = label;
      this.displayLabel = displayLabel;
      this.desc = desc;
      setIcon(iconURL);
   }

   /**
    * Set icon by url.
    */
   public void setIcon(String url) {
      try {
         if(url != null) {
            setIcon(getClass().getResourceAsStream(url));
         }
      }
      catch(Exception ex) {
         LOG.warn(ex.getMessage(), ex);
      }
   }

   /**
    * Sets the icon to an SVGIcon when input stream is supplied
    *
    * @param inputStream the InputStream of the icon.
    */
   public void setIcon(InputStream inputStream) {
      this.icon = new SVGIcon(inputStream);
   }

   /**
    * Set the object icon.
    */
   public void setIcon(Icon icon) {
      this.icon = icon;
   }

   /**
    * Get the object icon.
    * @return Icon.
    */
   public Icon getIcon() {
      return this.icon;
   }

   /**
    * Set the object label.
    */
   public void setLabel(String label) {
      this.label = label;
   }

   /**
    * Get the object label.
    * @return Label.
    */
   public String getLabel() {
      return this.label;
   }

   /**
    * Set the object display(localized) label.
    */
   public void setDisplayLabel(String displayLabel) {
      this.displayLabel = displayLabel;
   }

   /**
    * Get the object display(localized) label.
    * @return Display(localized) Label.
    */
   public String getDisplayLabel() {
      return this.displayLabel;
   }

   /**
    * Set the object description.
    */
   public void setDesc(String desc) {
      this.desc = desc;
   }

   /**
    * Get the object description.
    * @return Description.
    */
   public String getDesc() {
      return this.desc;
   }

   /**
    * Set type, when this function is called, all properties will be auto setted
    * to default, so function should be called before set other properties.
    */
   public void setType(String type) {
      this.type = type;
   }

   /**
    * Get type.
    */
   public String getType() {
      return type;
   }

   /**
    * Get the value by the given key.
    */
   public Object get(String name) {
      return (map == null) ? null : map.get(name);
   }

   public String getCategory() {
      return category;
   }

   public void setCategory(String category) {
      this.category = category;
   }

   public DataSourceListing getListing() {
      return listing;
   }

   public void setListing(DataSourceListing listing) {
      this.listing = listing;
   }

   /**
    * Set a key-value pair.
    * @param name property name.
    * @param val property value.
    */
   public void set(String name, Object val) {
      if(val == null) {
         if(map != null) {
            map.remove(name);
         }
      }
      else {
         if(map == null) {
            map = new HashMap();
         }

         map.put(name, val);
      }
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      return "ObjectInfo-" + type + "[" + map + "]";
   }

   private String type;
   private Icon icon;
   private String label;
   private String displayLabel;
   private String desc;
   private String category;
   private DataSourceListing listing;
   // contain info to create asset object
   private HashMap<String, Object> map;

   private static final Logger LOG = LoggerFactory.getLogger(ObjectInfo.class);
}
