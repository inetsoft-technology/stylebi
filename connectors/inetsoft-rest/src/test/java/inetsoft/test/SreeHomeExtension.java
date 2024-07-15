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
package inetsoft.test;

import inetsoft.util.*;
import org.junit.jupiter.api.extension.*;

import java.io.File;
import java.io.IOException;

public class SreeHomeExtension implements BeforeAllCallback, AfterAllCallback {
   @Override
   public void beforeAll(ExtensionContext context) throws IOException {
      SreeHome annotation = context.getRequiredTestClass().getAnnotation(SreeHome.class);
      String home = annotation.value();

      if(home.isEmpty()) {
         home = System.getProperty("sree.home", ".");
      }

      home = new File(home).getCanonicalPath();
      ConfigurationContext.getContext().setHome(home);
      Tool.setServer(true);
   }

   @Override
   public void afterAll(ExtensionContext context) {
      SingletonManager.reset();
   }
}
