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
package inetsoft.sree.internal.cluster;

import java.util.EventObject;

public final class MembershipEvent extends EventObject {
   public MembershipEvent(Object source, String member, boolean client) {
      super(source);
      this.member = member;
      this.client = client;
   }

   public String getMember() {
      return member;
   }

   public boolean isClient() {
      return client;
   }

   @Override
   public String toString() {
      return "MembershipEvent{" +
         "member='" + member + '\'' +
         ", client=" + client +
         '}';
   }

   private final String member;
   private final boolean client;
}
