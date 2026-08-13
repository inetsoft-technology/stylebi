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

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.*;

/**
 * Read-only discovery of server properties for the admin-chat plugin: catalog metadata plus each
 * property's current value, so the agent can describe options and allowed values rather than guess.
 *
 * <p>Named for properties rather than changesets because the enterprise module already has an
 * {@code AdminChangesetController}.
 */
@RestController
public class AdminPropertiesController {
   @Autowired
   public AdminPropertiesController(AdminPropertyCatalog catalog, AdminRiskClassifier classifier) {
      this.catalog = catalog;
      this.classifier = classifier;
   }

   /**
    * Lists catalogued properties, optionally filtered by a case-insensitive substring of the name
    * or any alias.
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT,
      resource = "settings/properties",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/properties")
   public List<PropertyView> list(@RequestParam(value = "filter", required = false) String filter,
                                  Principal user)
   {
      requireSiteAdmin(user);
      String needle = filter == null ? null : filter.trim().toLowerCase();
      List<PropertyView> views = new ArrayList<>();

      for(CatalogEntry entry : catalog.entries()) {
         if(needle != null && !needle.isEmpty() && !matches(entry, needle)) {
            continue;
         }

         views.add(view(AdminPropertyName.parse(entry.name()), entry));
      }

      return views;
   }

   /**
    * Describes one property. Uncatalogued properties are still readable — the operator can inspect
    * anything — but come back {@code recognized=false} and high risk.
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT,
      resource = "settings/properties",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/properties/{name}")
   public PropertyView get(@PathVariable("name") String name, Principal user) {
      requireSiteAdmin(user);
      AdminPropertyName resolved = catalog.resolve(name);
      return view(resolved, catalog.getEntry(resolved));
   }

   private static boolean matches(CatalogEntry entry, String needle) {
      if(entry.name().toLowerCase().contains(needle)) {
         return true;
      }

      if(entry.aliases() != null) {
         for(String alias : entry.aliases()) {
            if(alias.toLowerCase().contains(needle)) {
               return true;
            }
         }
      }

      return false;
   }

   private PropertyView view(AdminPropertyName name, CatalogEntry entry) {
      AdminRiskClassifier.RiskClassification risk = classifier.classify(name);
      String description = entry == null ? null : entry.description();
      boolean secret = AdminPropertyCatalog.isSecret(name.baseName());
      // A secret's value is not read AT ALL, not merely omitted from the response - see the test
      // that asserts SreeEnv is never called for one. Determining existence by reading it would
      // trade that invariant for a signal the catalog supplies properly, so the secret path stays
      // blind and reports what it actually knows.
      //
      // orgScope=false so the value shown is the one an apply would actually change.
      String stored = secret ? null : SreeEnv.getProperty(name.key(), false, false);

      // Secret properties (e.g. password.encryption.key) are still LISTED - an operator
      // legitimately needs to know the property exists - but the value is withheld rather than
      // forwarded to the model provider that this endpoint's caller relays responses through. Not
      // a 403: the same operator role can already read this unmasked via PropertiesController, so
      // refusing to even acknowledge the property's existence would just be confusing.
      //
      // The old phrasing said "Value withheld", which asserts there is a value to withhold. This
      // branch is reached on nothing more than a name match, so an invented name ending .key came
      // back claiming a secret had been withheld - manufacturing evidence that the property exists
      // out of a string suffix.
      if(secret) {
         description = "Secret property: admin-chat can neither read nor change it. Its value is "
            + "not read here at all, so this says nothing about whether one is set.";
      }

      // A catalogued name is authoritative; a stored value is proof by demonstration, and covers
      // anything declared in defaults.properties, since the defaults chain resolves through
      // getProperty. Neither means the server cannot say whether the name is real - which is
      // always the case for a secret, since this path deliberately never looks.
      boolean confirmed = entry != null || stored != null;

      return new PropertyView(name.key(),
         entry == null ? List.of() : (entry.aliases() == null ? List.of() : entry.aliases()),
         entry == null ? null : entry.type(),
         entry == null ? List.of() : (entry.allowedValues() == null
            ? List.of() : entry.allowedValues()),
         entry == null ? null : entry.min(),
         entry == null ? null : entry.max(),
         description,
         risk.risk(), risk.snapshotScope(), stored, risk.recognized(),
         confirmed ? PropertyView.EXISTS_CONFIRMED : PropertyView.EXISTS_UNKNOWN,
         confirmed ? null : UNKNOWN_GUIDANCE);
   }

   private static final String UNKNOWN_GUIDANCE =
      "admin-chat cannot tell whether this property exists: it is not in the admin catalog and has "
      + "no stored value, and a real property that nobody has set yet looks exactly like a "
      + "misspelled name. Do NOT conclude from this that the setting is stored somewhere other "
      + "than server properties. Confirm the spelling against the property reference "
      + "(search_product_docs with corpus \"properties\") before including it in a change: an "
      + "unrecognised name is written verbatim, so a typo becomes an inert property that nothing "
      + "reads, while the change reports success.";

   /** See {@code AdminAiController.requireSiteAdmin} for why both checks are needed. */
   private void requireSiteAdmin(Principal user) {
      AdminAiCallerGuard.requireBearerAuthenticatedRequest();

      if(!OrganizationManager.getInstance().isSiteAdmin(user)) {
         throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Site Administrator role required");
      }
   }

   @ExceptionHandler(IllegalArgumentException.class)
   @ResponseStatus(HttpStatus.BAD_REQUEST)
   @ResponseBody
   public Map<String, String> handleIllegalArgument(IllegalArgumentException ex) {
      return Map.of("status", "failed", "error", String.valueOf(ex.getMessage()));
   }

   private final AdminPropertyCatalog catalog;
   private final AdminRiskClassifier classifier;
}
