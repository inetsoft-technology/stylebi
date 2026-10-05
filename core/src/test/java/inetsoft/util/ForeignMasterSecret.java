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
package inetsoft.util;

/**
 * Test helper that encrypts a value the way an export from another server does, i.e. with the
 * {@code \master} prefix under a different master password.
 */
public final class ForeignMasterSecret {
   private ForeignMasterSecret() {
   }

   /**
    * Encrypts a value with the master key derived from the given master password.
    *
    * @param masterPassword the master password of the exporting server.
    * @param value          the clear text value.
    *
    * @return the {@code \master}-prefixed encrypted value.
    */
   public static String encrypt(String masterPassword, String value) {
      char[] old = LocalPasswordEncryption.masterPassword.get();
      LocalPasswordEncryption.masterPassword.set(masterPassword.toCharArray());

      try {
         String encrypted = new JcePasswordEncryption().encryptMasterPassword(value);

         if(!encrypted.startsWith(AbstractPasswordEncryption.MASTER_PREFIX)) {
            throw new IllegalStateException("Not master-encrypted: " + encrypted);
         }

         return encrypted;
      }
      finally {
         LocalPasswordEncryption.masterPassword.set(old);
      }
   }
}
