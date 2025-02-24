/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.cluster.apt;

import com.github.mustachejava.util.DecoratedCollection;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ProxyMethod {
   public ProxyMethod(String name, String callableClassName, String returnType, String cacheName,
                      String keyParam, List<ProxyParameter> parameters)
   {
      this.name = name;
      this.callableClassName = callableClassName;
      this.returnType = returnType;
      this.cacheName = cacheName;
      this.keyParam = keyParam;
      this.parameters = new DecoratedCollection<>(parameters);
   }

   public String getName() {
      return name;
   }

   public String getCallableClassName() {
      return callableClassName;
   }

   public String getReturnType() {
      return returnType;
   }

   public String getCacheName() {
      return cacheName;
   }

   public String getKeyParam() {
      return keyParam;
   }

   public DecoratedCollection<ProxyParameter> getParameters() {
      return parameters;
   }

   @Override
   public boolean equals(Object o) {
      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      ProxyMethod that = (ProxyMethod) o;
      return Objects.equals(name, that.name) &&
         Objects.equals(callableClassName, that.callableClassName) &&
         Objects.equals(returnType, that.returnType) &&
         Objects.equals(cacheName, that.cacheName) &&
         Objects.equals(keyParam, that.keyParam) &&
         Objects.equals(parameters, that.parameters);
   }

   @Override
   public int hashCode() {
      return Objects.hash(name, callableClassName, returnType, cacheName, keyParam, parameters);
   }

   @Override
   public String toString() {
      return "ProxyMethod{" +
         "name='" + name + '\'' +
         ", callableClassName='" + callableClassName + '\'' +
         ", returnType='" + returnType + '\'' +
         ", cacheName='" + cacheName + '\'' +
         ", keyParam='" + keyParam + '\'' +
         ", parameters=" + parameters +
         '}';
   }

   private final String name;
   private final String callableClassName;
   private final String returnType;
   private final String cacheName;
   private final String keyParam;
   private final DecoratedCollection<ProxyParameter> parameters;
}
