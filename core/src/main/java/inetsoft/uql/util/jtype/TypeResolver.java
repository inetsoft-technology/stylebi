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
package inetsoft.uql.util.jtype;

/**
 * This interface defines the API for returning type information of fields
 * or properties in a class by name.
 *
 * @version 6.1, 9/20/2004
 * @author InetSoft Technology Corp
 */
public interface TypeResolver {
   /**
    * Get the type of a field or property.
    * @param name the fully qualified name of a property, including the
    * fully qualified class name, e.g. com.company.app.MyClass.fieldName.
    */
   public Class getType(String name);
}

