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

/**
 * {@code HashedPassword} contains a hashed password and the algorithm used to generated the
 * hash.
 */
public final class HashedPassword {
   /**
    * Creates a new instance of {@code HashedPassword}.
    *
    * @param hash      the hashed password.
    * @param algorithm the algorithm used to generate the hash.
    */
   public HashedPassword(String hash, String algorithm) {
      this.hash = hash;
      this.algorithm = algorithm;
   }

   /**
    * Gets the hashed password.
    *
    * @return the hashed password.
    */
   public String getHash() {
      return hash;
   }

   /**
    * Gets the algorithm used to generate the hash.
    *
    * @return the algorithm name.
    */
   public String getAlgorithm() {
      return algorithm;
   }

   private final String hash;
   private final String algorithm;
}
