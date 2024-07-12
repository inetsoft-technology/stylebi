/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
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
package com.restfully.services;

import javax.naming.InitialContext;
import javax.ws.rs.core.*;
import java.util.*;

public class ShoppingApplication extends Application {
   private Set<Class<?>> classes = new HashSet<Class<?>>();

   public ShoppingApplication() {
   }

   @Override
   public Set<Class<?>> getClasses()
   {
      return classes;
   }

   @Override
   public Set<Object> getSingletons() {
      HashSet set = new HashSet();
      set.add(new RestService());
      return set;
   }

}
