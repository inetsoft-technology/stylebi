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
package inetsoft.web.admin.ai;

/**
 * A property name resolved into the exact key {@code SreeEnv} will use, plus the base name the
 * catalog and side-effect hooks need.
 *
 * <p>Mirrors {@code PropertiesEngine.computePropertyNameCase}: names are lowercased except for four
 * case-preserving families. Lowercasing one of those would address a <em>different</em> property,
 * which StyleBI then creates -- the change would apply, verify and audit while the property the
 * operator meant stayed untouched. Keep these patterns in sync with that method.
 *
 * <p>Org-scoped properties are addressed by their fully-qualified name,
 * {@code inetsoft.org.{orgId}.{base}}. Note that {@code computePropertyNameCase} fully lowercases
 * any such name -- its own {@code inetsoft.org.} branch is unreachable, because all four patterns are
 * anchored on {@code log.}/{@code plugin.}/{@code inetsoft.uql.} prefixes. Org-scoped overrides of
 * the case-preserving families are therefore not addressable; this class matches the server rather
 * than trying to improve on it.
 *
 * @param key      the key to pass to {@code SreeEnv}.
 * @param baseName the property name without any org prefix; used for catalog lookup and for
 *                 {@code PropertyChangeSideEffects}, which matches exact literals.
 * @param orgId    the organization id when org-scoped, otherwise {@code null}.
 */
public record AdminPropertyName(String key, String baseName, String orgId) {
   /**
    * Parses operator-supplied input into a resolved name.
    *
    * @throws IllegalArgumentException with a {@code property:}-prefixed message when the input is
    *                                 null or blank.
    */
   public static AdminPropertyName parse(String input) {
      if(input == null || input.trim().isEmpty()) {
         throw new IllegalArgumentException("property: must not be blank");
      }

      String key = fixCase(input.trim());

      if(key.startsWith(ORG_PREFIX)) {
         int index = key.indexOf('.', ORG_PREFIX.length());

         if(index > ORG_PREFIX.length() && index < key.length() - 1) {
            return new AdminPropertyName(
               key, key.substring(index + 1), key.substring(ORG_PREFIX.length(), index));
         }
      }

      return new AdminPropertyName(key, key, null);
   }

   /** True when this name addresses a per-organization override. */
   public boolean isOrgScoped() {
      return orgId != null;
   }

   /** Mirrors {@code PropertiesEngine.computePropertyNameCase}. */
   private static String fixCase(String name) {
      if(name.startsWith("log.level.") ||
         name.startsWith("plugin.extra.classpath.") ||
         name.matches("^log\\.[A-Z_]+\\.level\\..+$") ||
         name.matches("^inetsoft\\.uql\\.jdbc\\.pool\\..+\\.connectionTestQuery$"))
      {
         return name;
      }

      return name.toLowerCase();
   }

   private static final String ORG_PREFIX = "inetsoft.org.";
}
