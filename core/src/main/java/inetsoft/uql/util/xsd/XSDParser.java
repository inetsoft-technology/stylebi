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
package inetsoft.uql.util.xsd;

import inetsoft.uql.schema.XTypeNode;

import java.io.InputStream;

/**
 * Interface for classes that parse XML schemas.
 */
public interface XSDParser {
   /**
    * Parses a schema.
    *
    * @param input the input stream from which to read the schema definition file.
    *
    * @return the parsed type hierarchy.
    *
    * @throws Exception if the schema could not be parsed.
    */
   XTypeNode parse(InputStream input) throws Exception;

   /**
    * Gets an instance of {@code XSDParser}.
    *
    * @return a parser instance.
    */
   static XSDParser getInstance() {
      try {
         Class<?> clazz = XSDParser.class.getClassLoader()
            .loadClass("inetsoft.uql.util.xsd.XercesXSDParser");
         return (XSDParser) clazz.getConstructor().newInstance();
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to create parser instance", e);
      }
   }
}
