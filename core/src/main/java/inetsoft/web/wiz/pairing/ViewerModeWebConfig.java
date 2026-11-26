/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.wiz.pairing;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers {@link ViewerModeInterceptor} on {@code /api/wiz/v1/agent/**}. A wiz-package
 * {@link WebMvcConfigurer} of its own so the shared {@code inetsoft.web.WebConfig} is not touched
 * (Spring composes every configurer bean).
 */
@Configuration
public class ViewerModeWebConfig implements WebMvcConfigurer {
   @Autowired
   public ViewerModeWebConfig(ViewerModeInterceptor interceptor) {
      this.interceptor = interceptor;
   }

   @Override
   public void addInterceptors(InterceptorRegistry registry) {
      registry.addInterceptor(interceptor).addPathPatterns(ViewerModePolicy.AGENT_PREFIX + "**");
   }

   private final ViewerModeInterceptor interceptor;
}
