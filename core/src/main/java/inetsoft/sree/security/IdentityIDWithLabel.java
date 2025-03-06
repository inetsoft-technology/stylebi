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

package inetsoft.sree.security;

import java.util.Objects;

public class IdentityIDWithLabel implements Comparable<IdentityIDWithLabel> {
   public IdentityIDWithLabel() {
   }

   public IdentityIDWithLabel(IdentityID identityID, String label) {
      this.identityID = identityID;
      this.label = label;
   }

   public IdentityID getIdentityID() {
      return identityID;
   }

   public void setIdentityID(IdentityID identityID) {
      this.identityID = identityID;
   }

   public String getLabel() {
      return label;
   }

   public void setLabel(String label) {
      this.label = label;
   }

   public IdentityID identityID;
   public String label;

   @Override
   public int compareTo(IdentityIDWithLabel o) {
      return this.identityID.compareTo(o.identityID);
   }

   @Override
   public boolean equals(Object o) {
      if(!(o instanceof IdentityIDWithLabel that)) return false;
      return Objects.equals(identityID, that.identityID);
   }

   @Override
   public int hashCode() {
      return Objects.hashCode(identityID);
   }
}