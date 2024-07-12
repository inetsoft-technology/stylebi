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
package inetsoft.web.cluster;

import inetsoft.sree.internal.SUtil;
import inetsoft.web.security.AbstractSecurityFilter;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.StringUtils;

import java.io.IOException;

public class PauseClusterFilter extends AbstractSecurityFilter {
   @Override
   public void init(FilterConfig filterConfig) {
   }

   @Override
   public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException
   {
      HttpServletRequest httpRequest = (HttpServletRequest) request;

      // don't pause the Enterprise Manager
      if(SUtil.isCluster() && !isPageRequested("/em/**", httpRequest) &&
            !isPageRequested("/api/em/**", httpRequest)
            && !isPageRequested("/vs-events/**", httpRequest)
            && !isPageRequested("/logout", httpRequest)
            && !isResource(httpRequest))
      {
         ServerClusterClient client = new ServerClusterClient();
         ServerClusterStatus status = client.getStatus();

         if(status.isPaused()) {
            ((HttpServletResponse) response).sendError(
               HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Server is paused");
            return;
         }
      }

      chain.doFilter(request, response);
   }

   private boolean isResource(HttpServletRequest httpRequest) {
      String path = httpRequest.getServletPath();

      return StringUtils.isEmpty(path) || "/".equals(path) ||
         path.endsWith(".js") || path.endsWith(".css") || path.endsWith(".html") ||
         isPageRequested("/app/assets/**", httpRequest) ||
         isPageRequested("/css/**", httpRequest) ||
         isPageRequested("/images/**", httpRequest);
   }

   @Override
   public void destroy() {
   }
}
