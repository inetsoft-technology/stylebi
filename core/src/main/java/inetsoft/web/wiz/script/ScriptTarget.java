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

import inetsoft.web.wiz.pairing.PairingException;

/**
 * Parses/formats the {@code target} string the wiz-services proxy (and the MCP script tools)
 * send for every read/write/execute call.
 *
 * <p>Format: {@code "vs-init"}, {@code "vs-load"}, {@code "assembly:<name>"}, or
 * {@code "assembly:<name>:onClick"}.</p>
 */
public final class ScriptTarget {
   public enum Location { VS_INIT, VS_LOAD, ASSEMBLY, ASSEMBLY_ONCLICK }

   private ScriptTarget(Location location, String assemblyName) {
      this.location = location;
      this.assemblyName = assemblyName;
   }

   public Location location() {
      return location;
   }

   /** The assembly name, or {@code null} for {@code VS_INIT}/{@code VS_LOAD}. */
   public String assemblyName() {
      return assemblyName;
   }

   public static ScriptTarget parse(String target) throws PairingException {
      if(target == null || target.isBlank()) {
         throw new PairingException("target is required");
      }

      if("vs-init".equals(target)) {
         return new ScriptTarget(Location.VS_INIT, null);
      }

      if("vs-load".equals(target)) {
         return new ScriptTarget(Location.VS_LOAD, null);
      }

      if(target.startsWith("assembly:")) {
         String rest = target.substring("assembly:".length());

         if(rest.endsWith(":onClick")) {
            String name = rest.substring(0, rest.length() - ":onClick".length());

            if(name.isBlank()) {
               throw new PairingException("Invalid target: " + target);
            }

            return new ScriptTarget(Location.ASSEMBLY_ONCLICK, name);
         }

         if(rest.isBlank()) {
            throw new PairingException("Invalid target: " + target);
         }

         return new ScriptTarget(Location.ASSEMBLY, rest);
      }

      throw new PairingException("Invalid target: \"" + target +
         "\". Expected \"vs-init\", \"vs-load\", \"assembly:<name>\", or \"assembly:<name>:onClick\".");
   }

   @Override
   public String toString() {
      return switch(location) {
         case VS_INIT -> "vs-init";
         case VS_LOAD -> "vs-load";
         case ASSEMBLY -> "assembly:" + assemblyName;
         case ASSEMBLY_ONCLICK -> "assembly:" + assemblyName + ":onClick";
      };
   }

   private final Location location;
   private final String assemblyName;
}
