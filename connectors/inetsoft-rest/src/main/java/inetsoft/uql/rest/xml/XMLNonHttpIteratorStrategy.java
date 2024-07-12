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
package inetsoft.uql.rest.xml;

import inetsoft.uql.rest.AbstractRestDataIteratorStrategy;
import inetsoft.uql.rest.URLCreator;
import inetsoft.uql.rest.xml.parse.ParsedNode;
import inetsoft.util.CoreTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * Strategy for resolving non-http XML URLs, e.g. an XML file.
 */
public class XMLNonHttpIteratorStrategy
   extends AbstractRestDataIteratorStrategy<RestXMLQuery, XMLBasicStreamTransformer>
{
   protected XMLNonHttpIteratorStrategy(RestXMLQuery query, XMLBasicStreamTransformer transformer) {
      super(query, transformer);
   }

   @Override
   public boolean hasNext() {
      return next;
   }

   @Override
   public ParsedNode next() {
      next = false;
      final URL url;

      try {
         url = URLCreator.fromQuery(query);
      }
      catch(URISyntaxException | MalformedURLException ex) {
         LOG.error("URL is malformed", ex);
         return null;
      }

      try(InputStream input = url.openStream()) {
         return transformer.transform(input);
      }
      catch(Exception ex) {
         LOG.error("Error executing Rest query: {}", url, ex);
         CoreTool.addUserMessage("Error executing Rest query: " +
            url + "(" + ex.getMessage() + ")");
      }

      return null;
   }

   @Override
   public void close() {
      // no-op
   }

   private boolean next = true;

   private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
}
