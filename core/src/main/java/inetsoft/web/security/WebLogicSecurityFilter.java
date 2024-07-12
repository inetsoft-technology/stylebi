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
package inetsoft.web.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.lang.reflect.*;
import java.util.*;

/**
 * {@code WebLogicSecurityFilter} handles incompatibilities between WebLogic and Spring Session.
 */
public class WebLogicSecurityFilter implements Filter {
   @Override
   public void init(FilterConfig filterConfig) throws ServletException {
      // NO-OP
   }

   @Override
   public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException
   {
      chain.doFilter(new WebLogicRequestWrapper((HttpServletRequest) request), response);
   }

   @Override
   public void destroy() {
      // NO-OP
   }

   private static Class<?> getSessionSecurityDataClass() {
      try {
         return WebLogicSecurityFilter.class.getClassLoader().loadClass(SESSION_SECURITY_DATA);
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to load WebLogic SessionSecurityData class", e);
      }
   }

   public static final String SESSION_SECURITY_DATA =
      "weblogic.servlet.security.internal.SessionSecurityData";

   private static final class WebLogicRequestWrapper extends HttpServletRequestWrapper {
      public WebLogicRequestWrapper(HttpServletRequest request) {
         super(request);
      }

      @Override
      public HttpSession getSession(boolean create) {
         return wrapSession(super.getSession(create));
      }

      @Override
      public HttpSession getSession() {
         return wrapSession(super.getSession());
      }

      private HttpSession wrapSession(HttpSession session) {
         if(session == null) {
            cachedSession = null;
            sessionHandler = null;
            return null;
         }

         if(cachedSession != null && !sessionHandler.invalidated) {
            return cachedSession;
         }
         else {
            cachedSession = null;
            sessionHandler = null;
         }

         HttpSession baseSession = getBaseSession();

         ClassLoader loader = getClass().getClassLoader();
         Class<?> sessionSecurityData = getSessionSecurityDataClass();
         Class<?>[] ifaces = { HttpSession.class, sessionSecurityData };
         sessionHandler = new SessionHandler(session, baseSession);
         cachedSession = (HttpSession) Proxy.newProxyInstance(loader, ifaces, sessionHandler);
         return cachedSession;
      }

      private HttpSession getBaseSession() {
         Class<?> sessionSecurityData = getSessionSecurityDataClass();
         HttpServletRequest request = (HttpServletRequest) getRequest();
         HttpSession session = request.getSession();

         while(session != null && !sessionSecurityData.isAssignableFrom(session.getClass()) &&
            (request instanceof HttpServletRequestWrapper))
         {
            request = (HttpServletRequest) ((HttpServletRequestWrapper) request).getRequest();
            session = request.getSession();
         }

         if(session != null && sessionSecurityData.isAssignableFrom(session.getClass())) {
            return session;
         }

         return null;
      }

      private HttpSession cachedSession;
      private SessionHandler sessionHandler;
   }

   private static final class SessionHandler implements InvocationHandler {
      SessionHandler(HttpSession session, HttpSession baseSession) {
         this.session = session;
         this.baseSession = baseSession;
      }

      @Override
      public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
         if(baseMethods.contains(method.getName())) {
            return method.invoke(baseSession, args);
         }
         else {
            if("invalidate".equals(method.getName())) {
               invalidated = true;
            }

            return method.invoke(session, args);
         }
      }

      private final HttpSession session;
      private final HttpSession baseSession;
      private boolean invalidated = false;
      private static final Set<String> baseMethods = new HashSet<>(Arrays.asList(
         "getInternalId", "getIdWithServerInfo", "isValid", "getInternalAttribute",
         "setInternalAttribute", "removeInternalAttribute", "getConcurrentRequestCount"));
   }
}
