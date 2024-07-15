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
package inetsoft.web.adhoc;

import inetsoft.util.Tool;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Decode request parameter value when we send a httpRequest form web to server.
 */
public class DecodeArgumentResolver implements HandlerMethodArgumentResolver {
   @Override
   public boolean supportsParameter(MethodParameter parameter) {
      return parameter.hasParameterAnnotations() &&
         parameter.getParameterAnnotation(DecodeParam.class) != null;
   }

   @Override
   public Object resolveArgument(MethodParameter parameter,
      ModelAndViewContainer mavContainer, NativeWebRequest webRequest,
      WebDataBinderFactory binderFactory)
   {
      DecodeParam decodeParam = parameter.getParameterAnnotation(DecodeParam.class);

      if(decodeParam != null) {
         String paramValue = webRequest.getParameter(decodeParam.value());
         // DecodeParam shouldn't be necessary since value is uriEncodeComponent'ed
         // and the decoding should already be handled by the web server. decoding it
         // again would cause the value to be wrong (e.g. + changed to ' ').
         // we do a byteDecode here since some of the values are byteEncoded. we should
         // change to just use uriEncodeComponent in the future and eliminate the
         // byteEncode from url passing.
         return Tool.byteDecode(paramValue);
      }

      return null;
   }
}
