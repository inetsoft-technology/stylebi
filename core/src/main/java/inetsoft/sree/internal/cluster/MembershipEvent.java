/*
 * inetsoft-core - StyleBI is a business intelligence web application.
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
package inetsoft.sree.internal.cluster;

import java.util.EventObject;

public final class MembershipEvent extends EventObject {
   public MembershipEvent(Object source, String member) {
      super(source);
      this.member = member;
   }

   public String getMember() {
      return member;
   }

   @Override
   public String toString() {
      return "MembershipEvent{" +
         "member='" + member + '\'' +
         '}';
   }

   private final String member;
}