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
package inetsoft.uql.util;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Guards against reintroducing a Spring-dependent static initializer in {@link XUtil}.
 * The database key-value engine bootstrap loads XUtil (via DBConnectionPool.applyProperties)
 * before any Spring context exists, so XUtil's class initializer must not require a live
 * context. Loading the class in an isolated, context-free classloader reproduces that path.
 */
@Tag("core")
public class XUtilClassLoadTest {
   @Test
   void initializesWithoutSpringContext() throws Exception {
      List<URL> urls = new ArrayList<>();

      for(String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
         urls.add(new File(entry).toURI().toURL());
      }

      // Child-first for inetsoft.* so XUtil and ConfigurationContext initialize fresh with
      // no application context, mirroring the offline storage bootstrap.
      try(URLClassLoader loader = new IsolatedLoader(urls.toArray(new URL[0]),
                                                     getClass().getClassLoader()))
      {
         assertDoesNotThrow(() -> Class.forName("inetsoft.uql.util.XUtil", true, loader));
      }
   }

   private static final class IsolatedLoader extends URLClassLoader {
      IsolatedLoader(URL[] urls, ClassLoader parent) {
         super(urls, parent);
      }

      @Override
      protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
         synchronized(getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);

            if(c == null && name.startsWith("inetsoft.")) {
               c = findClass(name);
            }

            if(c == null) {
               return super.loadClass(name, resolve);
            }

            if(resolve) {
               resolveClass(c);
            }

            return c;
         }
      }
   }
}
