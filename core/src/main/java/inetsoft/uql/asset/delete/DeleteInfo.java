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
package inetsoft.uql.asset.delete;

import com.google.common.base.Objects;
import inetsoft.uql.asset.sync.RenameInfo;

public class DeleteInfo extends RenameInfo {
   public DeleteInfo(String name, int type, String source) {
      super(name, name, type, source);
   }

   public DeleteInfo(String name, int type, String source, String table) {
      super(name, name, type, source, table);
   }

   public String getName() {
      return getNewName();
   }

   @Override
   public int hashCode() {
      return Objects.hashCode(getName(), getType(), getSource());
   }

   @Override
   public boolean equals(Object obj) {
      if(obj == this) {
         return true;
      }

      if(obj == null || getClass() != obj.getClass()) {
         return false;
      }

      DeleteInfo other = (DeleteInfo) obj;

      return Objects.equal(getName(), other.getName())
         && Objects.equal(getType(), other.getType())
         && Objects.equal(getSource(), other.getSource())
         && Objects.equal(getTable(), other.getTable())
         && Objects.equal(isPrimary(), other.isPrimary());
   }

   @Override
   public String toString() {
      return getName();
   }

   public boolean isPrimary() {
      return primary;
   }

   public void setPrimary(boolean prim) {
      this.primary = prim;
   }

   protected boolean primary;
}
