/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
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
package inetsoft.uql.rest.xml.parse;

import inetsoft.uql.rest.xml.RestXMLQuery;
import inetsoft.uql.rest.xml.schema.*;
import inetsoft.uql.schema.XTypeNode;

import java.io.InputStream;
import java.net.URL;

public class DocumentParserFactory {
   private DocumentParserFactory() {
   }

   /**
    * Create the appropriate document parser for the query.
    */
   public static DocumentParser createDocumentParser(RestXMLQuery query) throws Exception {
      final DocumentParser docParser;
      final String schemaUrl = query.getSchemaUrl();
      final XMLSchema schema = query.getSchema();

      if(schema != XMLSchema.NONE && schemaUrl != null && !schemaUrl.isEmpty()) {
         final SchemaParser schemaParser = SchemaParserFactory.createSchemaParser(schema);
         final InputStream schemaInput = new URL(schemaUrl).openStream();
         final XTypeNode type = schemaParser.parse(schemaInput);

         docParser = new SchemaDocumentParser(type);
      }
      else {
         docParser = new NoSchemaDocumentParser();
      }

      return docParser;
   }
}
