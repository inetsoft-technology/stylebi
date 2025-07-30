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
package inetsoft.util;

import java.util.ServiceLoader;

public abstract class AbstractPasswordEncryption implements PasswordEncryption {
   @Override
   public synchronized SSLCertificateHelper getSSLCertificateHelper() {
      if(sslCertificateHelper == null) {
         for(SSLCertificateHelper.Factory factory : ServiceLoader.load(SSLCertificateHelper.Factory.class)) {
            if(factory.isFipsCompliant() == isFipsCompliant()) {
               sslCertificateHelper = factory.createSSLCertificateHelper();
               break;
            }
         }
      }

      return sslCertificateHelper;
   }

   protected abstract boolean isFipsCompliant();

   protected static final String OLD_PREFIX = "\\pwd";
   protected static final String NEW_PREFIX = "\\aes";
   public static final String MASTER_PREFIX = "\\master";

   private SSLCertificateHelper sslCertificateHelper;
}
