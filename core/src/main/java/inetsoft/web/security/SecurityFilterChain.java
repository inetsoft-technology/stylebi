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

import inetsoft.sree.security.AuthenticationService;
import inetsoft.sree.web.SessionLicenseServiceProvider;
import inetsoft.web.ThreadLocalCleanupFilter;
import inetsoft.web.cluster.PauseClusterFilter;
import org.springframework.web.filter.ForwardedHeaderFilter;

import java.util.Arrays;

public class SecurityFilterChain extends DelegatingFilterChain {
   public SecurityFilterChain(SessionLicenseServiceProvider sessionLicenseServiceProvider,
                              AuthenticationService authenticationService)
   {
      super(Arrays.asList(
         new ForwardedHeaderFilter(),
         new ThreadLocalCleanupFilter(),
         new SessionRefreshDisabledFilter(sessionLicenseServiceProvider, authenticationService),
         new SecurityHeaderFilter(sessionLicenseServiceProvider, authenticationService),
         new PauseClusterFilter(sessionLicenseServiceProvider, authenticationService),
         createFilterProxy("invalidateSessionFilter"),
         new AuthenticationFilterChain(),
         new CSRFFilter(sessionLicenseServiceProvider, authenticationService),
         new RequestPrincipalFilter(sessionLicenseServiceProvider, authenticationService))
      );
   }
}
