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

package inetsoft.web.viewsheet.command;

import java.util.List;
import java.util.Objects;

public class DelayVisibilityCommand implements ViewsheetCommand {
   public DelayVisibilityCommand() {
   }

   public DelayVisibilityCommand(int delay, List<String> assemblies) {
      this.delay = delay;
      this.assemblies = assemblies;
   }

   public int getDelay() {
      return delay;
   }

   public void setDelay(int delay) {
      this.delay = delay;
   }

   public List<String> getAssemblies() {
      return assemblies;
   }

   public void setAssemblies(List<String> assemblies) {
      this.assemblies = assemblies;
   }

   @Override
   public boolean equals(Object o) {
      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      DelayVisibilityCommand that = (DelayVisibilityCommand) o;
      return delay == that.delay && Objects.equals(assemblies, that.assemblies);
   }

   @Override
   public int hashCode() {
      return Objects.hash(delay, assemblies);
   }

   @Override
   public String toString() {
      return "DelayVisibilityCommand{" +
         "delay=" + delay +
         ", assemblies=" + assemblies +
         '}';
   }

   private int delay;
   private List<String> assemblies;
}
