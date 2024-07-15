/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.mv;

/**
 * MV execution exception.
 *
 * @author InetSoft Technology
 * @version 11.1
 */
public final class MVExecutionException extends RuntimeException {
   public MVExecutionException(Throwable cause) {
      super(cause);
   }

   public MVExecutionException(String msg) {
      super(msg);
   }

   /**
    * Set need re-create mv.
    */
   public void setNeedRecreate(boolean recreate) {
      this.recreate = recreate;
   }

   /**
    * Is need recreate mv or not.
    */
   public boolean isNeedRecreateMV() {
      return recreate;
   }

   private boolean recreate = true;
}
