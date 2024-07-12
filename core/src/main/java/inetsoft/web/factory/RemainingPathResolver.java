/*
 * inetsoft-core - StyleBI is a business intelligence web application.
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
package inetsoft.web.factory;

import org.springframework.core.MethodParameter;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Class that resolves handler method arguments for the remaining path for a ** pattern.
 *
 * @since 12.3
 */
public class RemainingPathResolver implements HandlerMethodArgumentResolver {
   @Override
   public boolean supportsParameter(MethodParameter parameter) {
      return parameter.getParameterType().isAssignableFrom(String.class) &&
         parameter.hasParameterAnnotation(RemainingPath.class);
   }

   @Override
   public Object resolveArgument(MethodParameter parameter,
                                 ModelAndViewContainer mavContainer,
                                 NativeWebRequest request,
                                 WebDataBinderFactory binderFactory) throws Exception
   {
      String path = null;
      String fullPath = (String) request.getAttribute(
         HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE,
         NativeWebRequest.SCOPE_REQUEST);
      String pattern = (String) request.getAttribute(
         HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
         NativeWebRequest.SCOPE_REQUEST);

      if(fullPath != null && pattern != null) {
         AntPathMatcher matcher = new AntPathMatcher();
         path = matcher.extractPathWithinPattern(pattern, fullPath);

         if(path.isEmpty()) {
            path = "/";
         }
         else if(fullPath.endsWith("/") && !path.endsWith("/")) {
            path = path + "/";
         }
      }

      if(path == null) {
         path = "/";
      }

      // value already decoded by web server, shouldn't need to decode again
      //return URLDecoder.decode(path, "UTF-8");
      return path;
   }
}
