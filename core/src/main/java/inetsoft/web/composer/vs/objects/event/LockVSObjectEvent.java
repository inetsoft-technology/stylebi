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
package inetsoft.web.composer.vs.objects.event;

import java.io.Serializable;

/**
 * Class that encapsulates the event to change object lock state.
 *
 * @since 12.3
 */
public class LockVSObjectEvent extends VSObjectEvent implements Serializable {
   /**
    * Gets the lock state of object.
    *
    * @return whether object is locked.
    */
   public boolean isLocked() {
      return locked;
   }

   /**
    * Sets the lock state of object.
    *
    * @param locked whether object is locked.
    */
   public void setLocked(boolean locked) {
      this.locked = locked;
   }

   @Override
   public String toString() {
      return "LockVSObjectEvent{" +
         "name='" + this.getName() + '\'' +
         ", isLocked=" + locked +
         '}';
   }

   private boolean locked;
}
