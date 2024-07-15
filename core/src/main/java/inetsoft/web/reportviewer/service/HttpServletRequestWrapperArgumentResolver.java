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
package inetsoft.web.reportviewer.service;

import inetsoft.sree.web.HttpServiceRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;


/**
 * Class that handles endpoint method arguments that are annotated
 * with {@link HttpServletRequestWrapper}.
 */
public class HttpServletRequestWrapperArgumentResolver
   implements HandlerMethodArgumentResolver
{
   @Override
   public boolean supportsParameter(MethodParameter parameter) {
      return parameter.hasParameterAnnotation(HttpServletRequestWrapper.class);
   }

   @Override
   public Object resolveArgument(MethodParameter parameter,
                                 ModelAndViewContainer mavContainer,
                                 NativeWebRequest request,
                                 WebDataBinderFactory binderFactory) throws Exception
   {
      return getServiceRequest(request.getNativeRequest(HttpServletRequest.class));
   }

   /**
    * Constructs the HttpServiceRequest for a given request.
    *
    * @param request the HTTP request object.
    *
    * @return the instance of HttpServletRequest.
    */
   public static HttpServiceRequest getServiceRequest(HttpServletRequest request) {
      return new HttpServiceRequest(request);
   }
}
