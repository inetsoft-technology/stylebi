/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
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
package inetsoft.uql.rest.xml;

import inetsoft.uql.rest.pagination.PaginationSpec;

import javax.xml.transform.Transformer;
import java.io.InputStream;
import java.util.Map;

/**
 * XML transformer for {@link inetsoft.uql.rest.pagination.PaginationType#ITERATION}
 */
public class XMLIterationStreamTransformer extends AbstractXMLStreamTransformer {
   XMLIterationStreamTransformer(RestXMLQuery query) throws Exception {
      super(query);
   }

   @Override
   protected void addXSLTStringParams(Map<String, String> params) {
      final PaginationSpec paginationSpec = query.getPaginationSpec();
      params.put("$pageOffsetXpath", paginationSpec.getPageOffsetParamToRead().getValue());
      params.put("$hasNextXpath", paginationSpec.getHasNextParam().getValue());
   }

   @Override
   protected void setXSLTObjectParameters(Transformer transformer) {
      transformer.setParameter("transformer", this);
   }

   @Override
   protected InputStream getXSLTInputStream() {
      return loader.getResourceAsStream("iteration-xpath.xslt");
   }

   public String getPageOffsetParam() {
      return pageOffsetParam;
   }

   public void setPageOffsetParam(String iterationParam) {
      this.pageOffsetParam = iterationParam;
   }

   public boolean hasNext() {
      return hasNext;
   }

   public void setHasNext(boolean hasNext) {
      this.hasNext = hasNext;
   }

   private String pageOffsetParam;
   private boolean hasNext = true;
   private static final ClassLoader loader = XMLIterationStreamTransformer.class.getClassLoader();
}
