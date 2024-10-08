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

import inetsoft.web.admin.security.SSOSettingsService;
import inetsoft.web.admin.security.SSOType;
import jakarta.servlet.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;

@Component
public class StandardFilterChain extends DelegatingFilterChain {
   @Autowired
   public StandardFilterChain(SSOSettingsService settingsService) {
      super(Arrays.asList(
         new LogoutFilter(), new OptionalBeanFilter("styleBIGoogleSSOFilter"),
         new BasicAuthenticationFilter(), new DefaultAuthorizationFilter(),
         new AnonymousUserFilter()
      ));
      this.settingsService = settingsService;
   }

   @Override
   public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException
   {
      if(isEnabled()) {
         super.doFilter(request, response, chain);
      }
      else {
         chain.doFilter(request, response);
      }
   }

   private boolean isEnabled() {
      return settingsService.getActiveFilterType() == SSOType.NONE;
   }

   private final SSOSettingsService settingsService;
}
