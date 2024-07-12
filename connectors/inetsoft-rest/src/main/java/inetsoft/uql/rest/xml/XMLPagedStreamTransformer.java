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

import inetsoft.uql.rest.pagination.PaginationSpec;
import org.w3c.dom.Node;

import javax.xml.transform.Transformer;
import java.io.InputStream;
import java.util.Map;

/**
 * Transformer which parses pagination parameters from an XML stream.
 */
public class XMLPagedStreamTransformer extends AbstractXMLStreamTransformer {
   XMLPagedStreamTransformer(RestXMLQuery query) throws Exception {
      super(query);
   }

   @Override
   protected void addXSLTStringParams(Map<String, String> params) {
      final PaginationSpec paginationSpec = query.getPaginationSpec();
      params.put("$pageCountXpath", paginationSpec.getPageCountXpath().getValue());
   }

   @Override
   protected void setXSLTObjectParameters(Transformer transformer) {
      transformer.setParameter("transformer", this);
   }

   @Override
   protected InputStream getXSLTInputStream() {
      return loader.getResourceAsStream("paginated-xpath.xslt");
   }

   public int getPageCount() {
      return pageCount;
   }

   public void setPageCount(Node node) {
      pageCount = nodeToInt(node);
   }

   private int nodeToInt(Node node) {
      return Integer.parseInt(node.getTextContent());
   }

   private int pageCount = -1;

   private static final ClassLoader loader = XMLPagedStreamTransformer.class.getClassLoader();
}
