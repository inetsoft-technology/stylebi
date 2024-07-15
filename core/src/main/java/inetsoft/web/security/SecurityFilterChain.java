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
package inetsoft.web.security;

import inetsoft.web.cluster.PauseClusterFilter;
import org.springframework.web.filter.ForwardedHeaderFilter;

import java.util.Arrays;

public class SecurityFilterChain extends DelegatingFilterChain {
   public SecurityFilterChain() {
      super(Arrays.asList(
         new ForwardedHeaderFilter(),
         new SecurityHeaderFilter(),
         new PauseClusterFilter(),
         createFilterProxy("invalidateSessionFilter"),
         new AuthenticationFilterChain(),
         new CSRFFilter(),
         new RequestPrincipalFilter())
      );
   }
}
