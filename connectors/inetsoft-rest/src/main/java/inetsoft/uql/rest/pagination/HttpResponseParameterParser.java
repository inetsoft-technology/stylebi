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
package inetsoft.uql.rest.pagination;

import inetsoft.uql.rest.HttpResponse;
import inetsoft.uql.rest.InputTransformer;

import java.io.IOException;
import java.net.URL;

/**
 * Class which handles the parsing of parameters from an HTTP response.
 */
public class HttpResponseParameterParser {
   public HttpResponseParameterParser(InputTransformer transformer) {
      this.transformer = transformer;
   }

   public int parseInt(PaginationParameter param, Object obj, HttpResponse response) {
      final ParameterParser parser = getParser(param, obj, response);
      return parser.parseInt(param.getValue());
   }

   public String parseString(PaginationParameter param, Object obj, HttpResponse response) {
      final ParameterParser parser = getParser(param, obj, response);
      return parser.parseString(param.getValue());
   }

   public boolean parseBoolean(PaginationParameter param, Object obj, HttpResponse response) {
      final ParameterParser parser = getParser(param, obj, response);
      return parser.parseBoolean(param.getValue());
   }

   public URL parseURL(PaginationParameter param, Object json, HttpResponse response)
      throws IOException
   {
      final ParameterParser parser = getParser(param, json, response);
      return parser.parseURL(param);
   }

   private ParameterParser getParser(
      PaginationParameter param,
      Object obj,
      HttpResponse response)
   {
      final ParameterParser parser;

      switch(param.getType()) {
         case JSON_PATH:
            parser = new PathParameterParser(transformer, obj, response);
            break;
         case HEADER:
            parser = new HeaderParameterParser(response);
            break;
         case LINK_HEADER:
            parser = new LinkHeaderParameterParser(response);
            break;
         case XPATH:
            parser = new PathParameterParser(transformer, obj, response);
            break;
         case QUERY:
         case URL_VARIABLE:
         default:
            throw new IllegalStateException("Unexpected value: " + param.getType());
      }

      return parser;
   }

   private final InputTransformer transformer;
}
