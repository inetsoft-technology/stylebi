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
package inetsoft.web;

import inetsoft.util.GroupedThread;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Interceptor that clears logging context at the end of each request.
 * @since 13.3
 */
public class LogContextInterceptor implements HandlerInterceptor {
   @Override
   public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                               Object handler, @Nullable Exception ex) throws Exception
   {
      if(Thread.currentThread() instanceof GroupedThread) {
         GroupedThread groupedThread = (GroupedThread) Thread.currentThread();
         groupedThread.setPrincipal(null);
         groupedThread.removeRecords();
      }
   }
}
