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
package inetsoft.web.factory;

import inetsoft.util.Tool;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerMapping;

import java.util.HashMap;
import java.util.Map;

/**
 * Resolves method arguments annotated with an @{@link DecodePathVariable}
 * will find the path variable value and return a byte deocded value
 * where the annotation does specify a path variable name.
 *
 * @since 13.5
 */
public class DecodePathVariableResolver implements HandlerMethodArgumentResolver {
   @Override
   public boolean supportsParameter(MethodParameter parameter) {
      return parameter.hasParameterAnnotations() &&
         parameter.getParameterAnnotation(DecodePathVariable.class) != null;
   }

   @Override
   public Object resolveArgument(MethodParameter parameter,
                                 ModelAndViewContainer mavContainer,
                                 NativeWebRequest webRequest,
                                 WebDataBinderFactory binderFactory)
   {
      DecodePathVariable pathVariable = parameter.getParameterAnnotation(DecodePathVariable.class);

      if(pathVariable != null) {
         String variableValue = null;

         Object result = webRequest.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
            RequestAttributes.SCOPE_REQUEST);

         if(result != null && result instanceof HashMap) {
            Map<String, String> pathVariables = (Map<String, String>) result;
            variableValue = pathVariables.get(pathVariable.value());
         }

         return variableValue == null ? null : Tool.byteDecode(variableValue);
      }

      return null;
   }
}
