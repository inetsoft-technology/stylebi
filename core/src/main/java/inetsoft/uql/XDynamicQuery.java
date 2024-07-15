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
package inetsoft.uql;

/**
 * A query definition that is generated dynamically at runtime.
 * 
 * @author  InetSoft Technology
 * @since   6.0
 */
public interface XDynamicQuery {
   /**
    * Flag indicating that a selection is based on a query.
    */
   public static final String QUERY_TYPE = "Query";

   /**
    * Flag indicating that a selection is based on a data model.
    */
   public static final String MODEL_TYPE = "Model";

   /**
    * Get the name of the source of this query.
    * 
    * @return the name of the source of the query.
    */
   public String getSource();
   
   /**
    * Get the type of this query.
    * 
    * @return the type flag of this query.
    */
   public String getType();

   /**
    * Set a property value. Property is generic interface for attaching
    * additional information to a query object. Properties are transient and
    * is not saved as part of the query definition.
    */
   public void setProperty(String name, Object val);

   /**
    * Get a property value.
    */
   public Object getProperty(String name);
}
