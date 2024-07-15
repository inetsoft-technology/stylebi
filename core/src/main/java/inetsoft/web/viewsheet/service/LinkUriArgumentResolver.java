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
package inetsoft.web.viewsheet.service;

import inetsoft.sree.SreeEnv;
import inetsoft.web.messaging.MessageAttributes;
import inetsoft.web.messaging.MessageContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Class that handles endpoint method arguments that are annotated with {@link LinkUri}.
 */
public class LinkUriArgumentResolver implements
   org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver,
   org.springframework.web.method.support.HandlerMethodArgumentResolver
{
   @Override
   public boolean supportsParameter(MethodParameter parameter) {
      return parameter.hasParameterAnnotation(LinkUri.class);
   }

   @Override
   public Object resolveArgument(MethodParameter parameter, Message<?> message) {
      MessageAttributes attributes = MessageContextHolder.currentMessageAttributes();
      StompHeaderAccessor headerAccessor = attributes.getHeaderAccessor();
      return headerAccessor.getSessionAttributes().get("viewsheetLinkUri");
   }

   @Override
   public Object resolveArgument(MethodParameter parameter,
                                 ModelAndViewContainer mavContainer,
                                 NativeWebRequest request,
                                 WebDataBinderFactory binderFactory)
   {
      return getLinkUri(request.getNativeRequest(HttpServletRequest.class));
   }

   /**
    * Constructs the link URI for a given request.
    *
    * @param request the HTTP request object.
    *
    * @return the link URI.
    */
   public static String getLinkUri(HttpServletRequest request) {
      String linkUri = null;
      String requestScheme = getRequestScheme(request);

      if(linkUri == null) {
         linkUri = getRemoteUri(request);
      }

      if(linkUri == null) {
         linkUri = SreeEnv.getProperty("replet.repository.servlet");
      }

      if(linkUri == null || linkUri.isEmpty()) {
         linkUri = buildLinkUri(request, requestScheme);
      }

      if(linkUri.startsWith("//")) {
         linkUri = requestScheme + ":" + linkUri;
      }

      if(linkUri.startsWith("/")) {
         StringBuilder prefix = new StringBuilder();
         prefix.append(requestScheme).append("://").append(request.getServerName());
         int port = request.getServerPort();

         if(port != 80 && port != 443) {
            prefix.append(':').append(port);
         }

         linkUri = prefix + linkUri;
      }

      if(!linkUri.endsWith("/")) {
         linkUri = linkUri + "/";
      }

      return linkUri;
   }

   /**
    * Transforms the requested URI to use the proper URI prefix.
    *
    * @param request the HTTP request object.
    *
    * @return the transformed URI.
    */
   public static String transformUri(HttpServletRequest request) {
      String baseUri = getLinkUri(request);
      StringBuilder remainingPath = new StringBuilder().append(request.getServletPath());

      if(request.getPathInfo() != null) {
         remainingPath.append(request.getPathInfo());
      }

      if(remainingPath.length() > 0 && remainingPath.charAt(0) == '/') {
         return baseUri + remainingPath.substring(1);
      }

      return baseUri + remainingPath;
   }

   private static String getRemoteUri(HttpServletRequest request) {
      String remoteUri = request.getHeader("X-Inetsoft-Remote-Uri");

      if(remoteUri != null) {
         StringBuilder uri = new StringBuilder();

         if(remoteUri.endsWith("/")) {
            uri.append(remoteUri, 0, remoteUri.length() - 1);
         }
         else {
            uri.append(remoteUri);
         }

         uri.append(request.getContextPath()).append('/');
         return uri.toString();
      }

      return null;
   }

   private static String getRequestScheme(HttpServletRequest request) {
      String requestScheme = request.getHeader("X-Forwarded-Proto");

      if(requestScheme == null) {
         requestScheme = request.getScheme();
      }

      return requestScheme;
   }

   public static String getRequestHost(HttpServletRequest request) {
      String host = request.getHeader("X-Forwarded-Host");

      if(host == null) {
         host = request.getServerName();
         int port = request.getServerPort();

         if(port != 80 && port != 443) {
            host = host + ":" + port;
         }
      }

      return host;
   }

   private static String buildLinkUri(HttpServletRequest request, String requestScheme) {
      return requestScheme + "://" + getRequestHost(request) + request.getContextPath() + "/";
   }
}
