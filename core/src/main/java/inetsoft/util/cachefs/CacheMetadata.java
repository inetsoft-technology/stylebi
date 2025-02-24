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

package inetsoft.util.cachefs;

import java.io.Serializable;
import java.util.Arrays;

public final class CacheMetadata implements Serializable {
   public CacheMetadata(long creationTime) {
      this.creationTime = creationTime;
   }

   public long getCreationTime() {
      return creationTime;
   }

   public String[] getChildren() {
      return children;
   }

   public void setChildren(String[] children) {
      this.children = children;
   }

   @Override
   public String toString() {
      return "CacheMetadata{" +
         "creationTime=" + creationTime +
         ", children=" + Arrays.toString(children) +
         '}';
   }

   private final long creationTime;
   private String[] children;
}
