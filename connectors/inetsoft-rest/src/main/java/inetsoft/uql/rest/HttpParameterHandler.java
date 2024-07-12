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
package inetsoft.uql.rest;

import inetsoft.uql.tabular.HttpParameter;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;

/**
 * Handles adding HTTP parameters to an HTTP request.
 */
public class HttpParameterHandler {
   public void addHttpParameters(HttpRequestBase request, Collection<HttpParameter> params)
      throws URISyntaxException
   {
      final URIBuilder builder = new URIBuilder(request.getURI().toString());

      for(final HttpParameter parameter : params) {
         if(parameter == null || parameter.getName() == null || parameter.getName().isEmpty()) {
            continue;
         }

         switch(parameter.getType()) {
            case QUERY:
               builder.addParameter(parameter.getName(), parameter.getValue());
               break;
            case HEADER:
               request.addHeader(parameter.getName(), parameter.getValue());
               break;
            default:
               throw new IllegalStateException("Unexpected value: " + parameter.getType());
         }
      }

      request.setURI(URI.create(builder.toString()));
   }
}
