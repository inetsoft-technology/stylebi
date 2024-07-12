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

import inetsoft.web.admin.security.SSOSettingsService;
import inetsoft.web.admin.security.SSOType;
import jakarta.servlet.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class FallbackAuthenticationFilter implements Filter {
   @Autowired
   public FallbackAuthenticationFilter(SSOSettingsService settingsService) {
      this.settingsService = settingsService;
      this.filter = new BasicAuthenticationFilter();
   }

   @Override
   public void init(FilterConfig filterConfig) throws ServletException {
      filter.init(filterConfig);
   }

   @Override
   public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException
   {
      if(isEnabled()) {
         filter.doFilter(request, response, chain);
      }
      else {
         chain.doFilter(request, response);
      }
   }

   @Override
   public void destroy() {
      filter.destroy();
   }

   private boolean isEnabled() {
      return settingsService.getActiveFilterType() != SSOType.NONE &&
         settingsService.isFallbackLogin();
   }

   private final SSOSettingsService settingsService;
   private final BasicAuthenticationFilter filter;
}
