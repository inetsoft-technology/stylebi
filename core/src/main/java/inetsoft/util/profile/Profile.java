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
package inetsoft.util.profile;

import inetsoft.uql.XPrincipal;
import inetsoft.util.ThreadContext;
import inetsoft.util.audit.ExecutionBreakDownRecord;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

public class Profile {

   private static volatile Profile profile;

   private Profile() {
   }

   public static Profile getInstance() {
      if(null == profile) {
         synchronized(Profile.class) {
            if(null == profile) {
               profile = new Profile();
            }
         }
      }

      return profile;
   }

   public void addRecord(ExecutionBreakDownRecord record) {
      record.recordContext();
      String name = getSessionID();

      if(name == null) {
         return;
      }

      if(profilingData.get(name) == null) {
         ProfileInfo pInfo = new ProfileInfo();
         pInfo.addRecord(record);
         profilingData.put(name, pInfo);
         recordSize++;
      }
      else {
         profilingData.get(name).addRecord(record);
         recordSize++;
      }
   }

   public ProfileInfo getProfileInfo() {
      String user = getSessionID();

      return profilingData.get(user);
   }

   public ProfileInfo removeProfileInfo() {
      String user = getSessionID();

      return profilingData.remove(user);
   }

   public void removeProfileData(String name) {
      String user = getSessionID();

      if(profilingData.get(user) != null) {
         profilingData.get(user).removeProfileData(name);
      }
   }

   private String getSessionID() {
      Principal principal = ThreadContext.getContextPrincipal();

      if(principal instanceof XPrincipal) {
         return ((XPrincipal) principal).getSessionID();
      }

      if(principal == null) {
         return null;
      }

      return principal.getName();
   }

   public int getRecordSize() {
      return recordSize;
   }

   private boolean onlyTotal() {
      return recordSize >= 1000;
   }

   private final Map<String, ProfileInfo> profilingData = new HashMap<>();
   private int recordSize = 0;
}