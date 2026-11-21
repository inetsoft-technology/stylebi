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
package inetsoft.web.wiz.controller;

import inetsoft.uql.erm.XDataModel;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * The name rules of data models, physical views, VPMs and data model folders. The native editors
 * enforce them client-side only ({@code FormValidators.invalidDataModelName},
 * {@code matchReservedModelName}, {@code notWhiteSpace}); no server rule exists, so the bridge
 * applies them and names the rule instead of failing deep in the registry.
 *
 * <p>Failures are {@code IllegalArgumentException}s, which the wiz advice maps to a 400. Folder
 * names delegate to {@link WizDatasourceNames#requireValidFolderName(String)}.</p>
 */
public final class WizDataModelNames {
   private WizDataModelNames() {
   }

   /**
    * Validates a model, view or VPM name: non-blank, not padded, no control character, none of
    * the forbidden characters, not a reserved name.
    */
   public static void requireValidModelName(String name) {
      if(name == null || name.isBlank()) {
         throw new IllegalArgumentException("Model name \"" + name + "\" is invalid: it is empty");
      }

      if(!name.equals(name.strip())) {
         throw new IllegalArgumentException(
            "Model name \"" + name + "\" is invalid: it must not start or end with a space");
      }

      if(CONTROL.matcher(name).find()) {
         throw new IllegalArgumentException(
            "Model name \"" + name + "\" is invalid: it must not contain control characters");
      }

      if(!ALLOWED.matcher(name).matches()) {
         throw new IllegalArgumentException(
            "Model name \"" + name + "\" is invalid: it must not contain any of " +
            FORBIDDEN_CHARS);
      }

      if(isReserved(name)) {
         throw new IllegalArgumentException(
            "Model name \"" + name + "\" is invalid: it is a reserved name");
      }
   }

   /** Validates a data model folder name. */
   public static void requireValidFolderName(String name) {
      WizDatasourceNames.requireValidFolderName(name);
   }

   /**
    * Fails with {@code DUPLICATE_NAME} (naming the existing kind) when the name is taken by a
    * physical view, logical model or VPM of the data source: the three share one namespace, but
    * only the Portal's advisory duplicate check enforces it, and each service checks one kind.
    */
   static void requireUniqueInSource(XDataModel dataModel, String name) {
      requireUniqueInSource(dataModel, name, (kind, existingName) -> true);
   }

   /**
    * As {@link #requireUniqueInSource(XDataModel, String)}, but the refusal names the existing
    * kind only when {@code mayName} says the caller may see that item; otherwise it is the same
    * refusal without a kind, so an invisible VPM or view is not revealed by its clash.
    */
   static void requireUniqueInSource(XDataModel dataModel, String name,
                                     java.util.function.BiPredicate<String, String> mayName)
   {
      String existing = WizDataModelSupport.kindOfName(dataModel, name);

      if(existing != null && !mayName.test(existing, name)) {
         throw new WizDataModelErrors.DmFailure(
            WizDataModelWire.DUPLICATE_NAME, "name", "the name \"" + name +
            "\" is already used in " + dataModel.getDataSource() + "; nothing changed", null,
            null);
      }

      if(existing != null) {
         throw new WizDataModelErrors.DmFailure(
            WizDataModelWire.DUPLICATE_NAME, "name",
            "a " + existing + " named \"" + name + "\" already exists in " +
               dataModel.getDataSource() + "; nothing changed", null, existing);
      }
   }

   /** Whether the name is reserved ({@code Domain}, {@code Data Model}). */
   public static boolean isReserved(String name) {
      return RESERVED.contains(name);
   }

   private static final String FORBIDDEN_CHARS = "\\ / ^ : * \" | < > ?";
   private static final Set<String> RESERVED = Set.of("Domain", "Data Model");
   private static final Pattern ALLOWED = Pattern.compile("[^\\\\/^:*\"|<>?]*");
   private static final Pattern CONTROL = Pattern.compile("\\p{Cntrl}");
}
