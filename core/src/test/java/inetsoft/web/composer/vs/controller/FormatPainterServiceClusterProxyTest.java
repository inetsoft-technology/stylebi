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
package inetsoft.web.composer.vs.controller;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bug #80336: FormatPainterService is a {@code @ClusterProxy} bean. A public method without
 * {@code @ClusterProxyMethod} and a {@code @ClusterProxyKey} parameter is not generated into the
 * proxy, so it ran on the proxy instance whose fields are null (NPE on {@code viewsheetService}).
 * The cluster-proxy annotations have SOURCE retention, so the invariant is asserted on the
 * generated proxy class: every service entry point must exist there. Mocking the painter in the
 * wiz service tests cannot see this.
 */
@Tag("core")
class FormatPainterServiceClusterProxyTest {
   @Test
   void everyServiceEntryPointIsGeneratedIntoTheProxy() throws Exception {
      Class<?> proxy = Class.forName(
         "inetsoft.web.composer.vs.controller.FormatPainterServiceProxy");
      Set<String> nested = new HashSet<>();

      for(Class<?> c : proxy.getDeclaredClasses()) {
         nested.add(c.getSimpleName());
      }

      List<String> missing = new ArrayList<>();

      for(Method method : FormatPainterService.class.getDeclaredMethods()) {
         if(!Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers())) {
            continue;
         }

         boolean entryPoint = method.getParameterCount() > 1 &&
            method.getParameterTypes()[0] == String.class &&
            Arrays.asList(method.getParameterTypes()).contains(Principal.class);

         if(!entryPoint) {
            continue;
         }

         String name = Character.toUpperCase(method.getName().charAt(0)) +
            method.getName().substring(1);

         if(!nested.contains(name)) {
            missing.add(method.getName());
         }
      }

      assertTrue(missing.isEmpty(), "Not generated into the cluster proxy: " + missing);
   }

   @Test
   void chartRegionReadBackIsGeneratedIntoTheProxy() throws Exception {
      Class<?> proxy = Class.forName(
         "inetsoft.web.composer.vs.controller.FormatPainterServiceProxy");
      boolean found = false;

      for(Class<?> c : proxy.getDeclaredClasses()) {
         found |= "GetChartRegionFormatModel".equals(c.getSimpleName());
      }

      assertTrue(found, "getChartRegionFormatModel must be a proxied method");
   }
}
