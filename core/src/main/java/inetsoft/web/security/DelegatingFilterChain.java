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
import org.springframework.web.filter.DelegatingFilterProxy;

import java.io.IOException;
import java.util.*;

public class DelegatingFilterChain implements Filter {
   public DelegatingFilterChain(List<Filter> filters) {
      Objects.requireNonNull(filters);
      this.filters = filters;
   }

   @Override
   public void init(FilterConfig config) throws ServletException {
      for(Filter filter : filters) {
         filter.init(config);
      }
   }

   @Override
   public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException
   {
      if(filters.isEmpty()) {
         chain.doFilter(request, response);
      }
      else {
         new Chain(chain, filters).doFilter(request, response);
      }
   }

   @Override
   public void destroy() {
      for(Filter filter : filters) {
         filter.destroy();
      }
   }

   /**
    * Create a filter proxy for spring DI
    */
   protected static DelegatingFilterProxy createFilterProxy(String beanName) {
      DelegatingFilterProxy filter = new DelegatingFilterProxy(beanName, null);
      filter.setTargetFilterLifecycle(true);
      return filter;
   }

   private final List<Filter> filters;

   private static final class Chain implements FilterChain {
      Chain(FilterChain parent, Iterable<Filter> filters) {
         this.parent = parent;
         this.filters = filters.iterator();
      }

      @Override
      public void doFilter(ServletRequest request, ServletResponse response)
         throws IOException, ServletException
      {
         if(filters.hasNext()) {
            filters.next().doFilter(request, response, this);
         }
         else {
            parent.doFilter(request, response);
         }
      }

      private final FilterChain parent;
      private final Iterator<Filter> filters;
   }
}
