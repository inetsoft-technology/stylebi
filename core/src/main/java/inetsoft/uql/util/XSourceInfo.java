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
package inetsoft.uql.util;

import inetsoft.util.XMLSerializable;

import java.io.Serializable;

/**
 * XSourceInfo defines the source info interface.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public interface XSourceInfo extends XMLSerializable, Serializable, Cloneable {
   /**
    * A normal query source.
    */
   int QUERY = 0;
   /**
    * A data model source.
    */
   int MODEL = 1;
   /**
    * A source from other bindable report elements in the same report.
    */
   int REPORT = 2;
   /**
    * A normal source from report parameters.
    */
   int PARAMETER = 3;
   /**
    * An OLAP query source.
    */
   int CUBE = 5;
   /**
    * An embedded data source.
    */
   int EMBEDDED_DATA = 6;
   /**
    * An asset source.
    */
   int ASSET = 7;
   /**
    * A physical table source.
    */
   int PHYSICAL_TABLE = 8;
   /**
    * A data source.
    */
   int DATASOURCE = 16;
   /**
    * A source from other bindable assembly elements in the same viewsheet.
    */
   int VS_ASSEMBLY = 64;
   /**
    * Null source.
    */
   int NONE = -1;

   /**
    * Schema property.
    */
   String SCHEMA = "__schema__";
   /**
    * Catalog property.
    */
   String CATALOG = "__catalog__";
   /**
    * Table type property.
    */
   String TABLE_TYPE = "__table_type__";
   /**
    * Query folder property.
    */
   String QUERY_FOLDER = "__query_folder__";
   /**
    * Rest Prefix
    */
   String REST_PREFIX = "Rest.";

   /**
    * Get the type.
    * @return the type of the source info.
    */
   int getType();

   /**
    * Set the type.
    * @param type the specified type.
    */
   void setType(int type);

   /**
    * Get the prefix.
    * @return the prefix of the source info.
    */
   String getPrefix();

   /**
    * Set the prefix.
    * @param prefix the specified prefix.
    */
   void setPrefix(String prefix);

   /**
    * Get the source.
    * @return the source of the source info.
    */
   String getSource();

   /**
    * Set the source.
    * @param source the specified source.
    */
   void setSource(String source);

   /**
    * Get a property of the source info.
    * @param key the name of the property.
    * @return the value of the property.
    */
   String getProperty(String key);

   /**
    * Set a property of the source info.
    * @param key the name of the property.
    * @param value the value of the property, <tt>null</tt> to remove the
    * property.
    */
   void setProperty(String key, String value);

   /**
    * Check if the sort info is empty.
    * @return <tt>true</tt> if empty, <tt>false</tt> otherwise.
    */
   boolean isEmpty();
}
