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
package inetsoft.web.security;

import jakarta.servlet.*;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.springframework.web.filter.DelegatingFilterProxy;

import java.io.IOException;

final class OptionalBeanFilter implements Filter {
   public OptionalBeanFilter(String beanName) {
      this.beanName = beanName;
   }

   @Override
   public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException
   {
      if(delegate == null) {
         chain.doFilter(request, response);
      }
      else {
         delegate.doFilter(request, response, chain);
      }
   }

   @Override
   public void init(FilterConfig filterConfig) throws ServletException {
      WebApplicationContext context =
         WebApplicationContextUtils.findWebApplicationContext(filterConfig.getServletContext());

      if(context != null && context.containsBean(beanName)) {
         delegate = DelegatingFilterChain.createFilterProxy(beanName);
         delegate.init(filterConfig);
      }
   }

   @Override
   public void destroy() {
      if(delegate != null) {
         delegate.destroy();
         delegate = null;
      }
   }

   private final String beanName;
   private DelegatingFilterProxy delegate;
}
