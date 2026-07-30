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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Catalogue of server properties the admin-chat feature understands, loaded from a JSON resource so
 * entries can be added without touching logic.
 *
 * <p>Uncatalogued properties remain settable — {@link AdminRiskClassifier} treats them as high risk
 * and unrecognized — so absence is a downgrade in guidance, not a hard block. What must never happen
 * is a catalogued name that does not exist in StyleBI: the server would snapshot {@code null},
 * apply, read back {@code null}, and report success for a property nothing reads. Verify every entry
 * against its {@code SreeEnv.getProperty}/{@code setProperty} call site before adding it.
 */
@Component
public class AdminPropertyCatalog {
   public AdminPropertyCatalog() {
      List<CatalogEntry> loaded;

      try(InputStream in = getClass().getResourceAsStream(RESOURCE)) {
         if(in == null) {
            throw new IllegalStateException("Admin property catalog resource not found: " + RESOURCE);
         }

         loaded = Arrays.asList(new ObjectMapper().readValue(in, CatalogEntry[].class));
      }
      catch(IOException e) {
         throw new IllegalStateException("Failed to load admin property catalog: " + RESOURCE, e);
      }

      Map<String, CatalogEntry> map = new HashMap<>();

      for(CatalogEntry entry : loaded) {
         map.put(entry.name().toLowerCase(), entry);

         if(entry.aliases() != null) {
            for(String alias : entry.aliases()) {
               map.put(alias.toLowerCase(), entry);
            }
         }
      }

      this.entries = Collections.unmodifiableList(loaded);
      this.byKey = Collections.unmodifiableMap(map);
   }

   /** All catalogued entries, in resource order. */
   public List<CatalogEntry> entries() {
      return entries;
   }

   /**
    * Parses {@code input} and rewrites a catalogued alias to its canonical name, preserving any
    * {@code inetsoft.org.{orgId}.} prefix.
    *
    * @throws IllegalArgumentException with a {@code property:}-prefixed message on blank input.
    */
   public AdminPropertyName resolve(String input) {
      AdminPropertyName parsed = AdminPropertyName.parse(input);
      CatalogEntry entry = byKey.get(parsed.baseName());

      if(entry == null || entry.name().equals(parsed.baseName())) {
         return parsed;
      }

      if(parsed.isOrgScoped()) {
         return new AdminPropertyName(
            "inetsoft.org." + parsed.orgId() + "." + entry.name(), entry.name(), parsed.orgId());
      }

      return new AdminPropertyName(entry.name(), entry.name(), null);
   }

   /**
    * Looks up the entry for a resolved name by its base name, so org-qualified properties keep
    * their validation, description and namespace risk rules.
    *
    * @return the entry, or {@code null} when the property is not catalogued.
    */
   public CatalogEntry getEntry(AdminPropertyName name) {
      return name == null ? null : byKey.get(name.baseName());
   }

   /**
    * Validates a proposed value and returns the spelling StyleBI stores.
    *
    * <p>Forgiving where the intent is unambiguous — an agent will naturally write {@code "INFO"} or
    * {@code "TRUE"} for values StyleBI keeps lowercase — and strict otherwise, so a bad value fails
    * loudly instead of being written through and silently misread. String values are returned
    * untouched, since leading/trailing whitespace may be significant.
    *
    * @param value the proposed value, or {@code null} to reset the property to its default.
    *
    * @throws IllegalArgumentException naming the property and the violation.
    */
   public String canonicalizeValue(CatalogEntry entry, String value) {
      if(value == null) {
         return null;
      }

      String trimmed = value.trim();

      switch(entry.type()) {
      case "int":
         if(!trimmed.matches("^-?\\d+$")) {
            throw new IllegalArgumentException(
               entry.name() + ": value must be an integer, got \"" + value + "\"");
         }

         long parsed = Long.parseLong(trimmed);

         if(entry.min() != null && parsed < entry.min()) {
            throw new IllegalArgumentException(
               entry.name() + ": value " + parsed + " is below minimum " + entry.min());
         }

         if(entry.max() != null && parsed > entry.max()) {
            throw new IllegalArgumentException(
               entry.name() + ": value " + parsed + " is above maximum " + entry.max());
         }

         return trimmed;
      case "boolean":
         if(!"true".equalsIgnoreCase(trimmed) && !"false".equalsIgnoreCase(trimmed)) {
            throw new IllegalArgumentException(
               entry.name() + ": value must be true or false, got \"" + value + "\"");
         }

         return trimmed.toLowerCase();
      case "enum":
         List<String> allowed = entry.allowedValues() == null ? List.of() : entry.allowedValues();

         for(String candidate : allowed) {
            if(candidate.equalsIgnoreCase(trimmed)) {
               return candidate;
            }
         }

         throw new IllegalArgumentException(entry.name() + ": value must be one of " +
            String.join(", ", allowed) + ", got \"" + value + "\"");
      default:
         return value;
      }
   }

   private static final String RESOURCE = "admin-property-catalog.json";
   private final List<CatalogEntry> entries;
   private final Map<String, CatalogEntry> byKey;
}
