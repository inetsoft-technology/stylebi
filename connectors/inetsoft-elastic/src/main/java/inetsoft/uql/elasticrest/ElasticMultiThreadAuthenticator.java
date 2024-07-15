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
package inetsoft.uql.elasticrest;

import java.net.*;

public class ElasticMultiThreadAuthenticator extends Authenticator {
   public static void setThreadUser(String user) {
      ElasticMultiThreadAuthenticator.user.set(user);
   }

   public static void setThreadPassword(String password) {
      ElasticMultiThreadAuthenticator.password.set(password);
   }

   protected PasswordAuthentication getPasswordAuthentication() {
      return new PasswordAuthentication(user.get(), password.get().toCharArray());
   }

   private static ThreadLocal<String> user = new ThreadLocal<>();
   private static ThreadLocal<String> password = new ThreadLocal<>();
}
