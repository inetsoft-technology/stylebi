/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.wiz.script;

import java.util.Objects;

/**
 * Value object that identifies a single scriptable location within a viewsheet.
 *
 * <p>Parse format:</p>
 * <ul>
 *   <li>{@code "vs-init"} → viewsheet init script</li>
 *   <li>{@code "vs-load"} → viewsheet load script</li>
 *   <li>{@code "assembly:<name>"} → per-assembly script</li>
 *   <li>{@code "assembly:<name>:onClick"} → per-assembly onClick handler</li>
 * </ul>
 */
public final class ScriptTarget {

   private final ScriptLocation location;
   private final String assemblyName;

   private ScriptTarget(ScriptLocation location, String assemblyName) {
      this.location = location;
      this.assemblyName = assemblyName;
   }

   /** The script location category. */
   public ScriptLocation location() {
      return location;
   }

   /**
    * The assembly name; non-null only for {@link ScriptLocation#ASSEMBLY} and
    * {@link ScriptLocation#ASSEMBLY_ONCLICK}.
    */
   public String assemblyName() {
      return assemblyName;
   }

   /**
    * Parse a target string into a {@code ScriptTarget}.
    *
    * @param target the string representation (see class-level Javadoc)
    * @return the parsed target
    * @throws IllegalArgumentException if the string is not a recognised format
    */
   public static ScriptTarget parse(String target) {
      if("vs-init".equals(target)) {
         return new ScriptTarget(ScriptLocation.VS_INIT, null);
      }

      if("vs-load".equals(target)) {
         return new ScriptTarget(ScriptLocation.VS_LOAD, null);
      }

      if(target != null && target.startsWith("assembly:")) {
         String rest = target.substring("assembly:".length());

         if(rest.endsWith(":onClick")) {
            String name = rest.substring(0, rest.length() - ":onClick".length());

            if(name.isEmpty()) {
               throw new IllegalArgumentException("Assembly name is empty in target: " + target);
            }

            return new ScriptTarget(ScriptLocation.ASSEMBLY_ONCLICK, name);
         }

         if(rest.isEmpty()) {
            throw new IllegalArgumentException("Assembly name is empty in target: " + target);
         }

         return new ScriptTarget(ScriptLocation.ASSEMBLY, rest);
      }

      throw new IllegalArgumentException("Unrecognised script target: " + target);
   }

   /**
    * Returns the canonical string form that {@link #parse} can round-trip.
    */
   @Override
   public String toString() {
      return switch(location) {
         case VS_INIT -> "vs-init";
         case VS_LOAD -> "vs-load";
         case ASSEMBLY -> "assembly:" + assemblyName;
         case ASSEMBLY_ONCLICK -> "assembly:" + assemblyName + ":onClick";
      };
   }

   @Override
   public boolean equals(Object obj) {
      if(this == obj) {
         return true;
      }

      if(!(obj instanceof ScriptTarget other)) {
         return false;
      }

      return location == other.location && Objects.equals(assemblyName, other.assemblyName);
   }

   @Override
   public int hashCode() {
      return Objects.hash(location, assemblyName);
   }
}
