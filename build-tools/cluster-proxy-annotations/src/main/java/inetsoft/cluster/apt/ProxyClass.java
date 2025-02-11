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

import java.util.*;

public final class ProxyClass {
   public ProxyClass(String targetClass, String packageName, String proxyClass,
                     String proxySimpleName)
   {
      this.targetClass = targetClass;
      this.packageName = packageName;
      this.proxyClass = proxyClass;
      this.proxySimpleName = proxySimpleName;
   }

   public String getTargetClass() {
      return targetClass;
   }

   public String getPackageName() {
      return packageName;
   }

   public String getProxyClass() {
      return proxyClass;
   }

   public String getProxySimpleName() {
      return proxySimpleName;
   }

   public List<ProxyMethod> getMethods() {
      return methods;
   }

   @Override
   public boolean equals(Object o) {
      if(o == null || getClass() != o.getClass()) {
         return false;
      }
      ProxyClass that = (ProxyClass) o;
      return Objects.equals(targetClass, that.targetClass) &&
         Objects.equals(packageName, that.packageName) &&
         Objects.equals(proxyClass, that.proxyClass) &&
         Objects.equals(proxySimpleName, that.proxySimpleName) &&
         Objects.equals(methods, that.methods);
   }

   @Override
   public int hashCode() {
      return Objects.hash(targetClass, packageName, proxyClass, proxySimpleName, methods);
   }

   @Override
   public String toString() {
      return "ProxyClass{" +
         "targetClass='" + targetClass + '\'' +
         ", packageName='" + packageName + '\'' +
         ", proxyClass='" + proxyClass + '\'' +
         ", proxySimpleName='" + proxySimpleName + '\'' +
         ", methods=" + methods +
         '}';
   }

   private final String targetClass;
   private final String packageName;
   private final String proxyClass;
   private final String proxySimpleName;
   private final List<ProxyMethod> methods = new ArrayList<>();
}
