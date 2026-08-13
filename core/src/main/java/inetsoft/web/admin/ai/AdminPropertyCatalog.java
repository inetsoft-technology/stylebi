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
 *
 * <p>{@code snapshotScope} follows one rule: {@code storage} means the property's side effect
 * <b>mutates the key-value or blob stores, or fires repository-wide change events whose listeners
 * may themselves reach storage</b>; {@code value} means the side effect, if any, only invalidates
 * an in-memory cache local to the process. Worked examples from {@code
 * inetsoft.web.admin.properties.PropertyChangeSideEffects}: {@code format.number.round} and {@code
 * format.percent.round} call {@code TableFormat.invalidateTableFormatCache()}, and {@code
 * string.compare.casesensitive} calls {@code Tool.invalidateCaseSensitive()} — all three are
 * cache-only, so {@code value} scope. {@code security.exposedefaultorgtoall} calls {@code
 * assetRepository.fireExposeDefaultOrgPropertyChange()}, which — see {@code
 * AbstractAssetEngine.fireExposeDefaultOrgPropertyChange} — constructs an {@code AssetEntry} and
 * fires a repository-wide {@code AssetChangeEvent} to every registered {@code
 * AssetChangeListener}; it performs no key-value or blob write itself, but a repository-wide event
 * whose listener set is open-ended is exactly the "may themselves reach storage" case, so it is
 * {@code storage} scope. A new entry's {@code snapshotScope} must be determined the same way — by
 * checking whether its side effect mutates storage directly, or fires an event broadcast widely
 * enough that some listener plausibly does — not by guessing from the property's apparent
 * importance.
 *
 * <p>{@code PropertyChangeSideEffects} is not the only side-effect channel a catalog author must
 * consider: {@code inetsoft.sree.PropertiesEngine.applyProperty} also reconfigures logging and
 * calls {@code SQLHelper.resetCache()} for a couple of specific property names, and listeners
 * registered through {@code PropertiesEngine.addPropertyChangeListener} fire on every property
 * change regardless of which of the three channels is involved.
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
      // baseName is case-PRESERVING for log.level.*, plugin.extra.classpath.* and the other two
      // families in AdminPropertyName.fixCase (that is the whole point of those families), but
      // byKey's keys are always lowercased when the catalog is built. Without lowercasing here
      // too, a catalogued entry in one of those families could never be found by lookup - see
      // Finding 4.
      CatalogEntry entry = byKey.get(parsed.baseName().toLowerCase());

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
      // See the comment in resolve(): baseName preserves case for the four case-preserving
      // families, so the lookup key must be lowercased to match byKey's construction.
      return name == null ? null : byKey.get(name.baseName().toLowerCase());
   }

   /**
    * Validates a proposed value and returns the spelling StyleBI stores.
    *
    * <p>Forgiving where the intent is unambiguous — an agent will naturally write {@code "INFO"} or
    * {@code "TRUE"} for values StyleBI keeps lowercase — and strict otherwise, so a bad value fails
    * loudly instead of being written through and silently misread. String values ARE trimmed: the
    * hash computed over the resolved plan must cover the exact value that will be written, and
    * {@code AdminChangeService} writes this method's return value verbatim (it no longer trims),
    * so trimming has to happen here, before the value is hashed, rather than at write time.
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
         return trimmed;
      }
   }

   /**
    * True when {@code baseName} names a property whose value must never be exposed through
    * admin-chat, e.g. {@code password.encryption.key} (StyleBI's password-encryption master key).
    *
    * <p>This is not a privilege boundary — the same operator role can already read and write these
    * properties, unmasked, through {@code PropertiesController} — but an egress and blast-radius
    * control: the caller here is an LLM that forwards responses to a model provider off-host, so a
    * secret value read through this path leaves the host in a way the ordinary EM properties page
    * never does.
    *
    * <p>Matches case-insensitively on the base name: contains {@code password}, {@code secret} or
    * {@code credential}; ends with {@code .key}; or starts with {@code license.}.
    */
   public static boolean isSecret(String baseName) {
      if(baseName == null) {
         return false;
      }

      String lower = baseName.toLowerCase();
      return lower.contains("password") || lower.contains("secret") ||
         lower.contains("credential") || lower.endsWith(".key") || lower.startsWith("license.");
   }

   /**
    * True when {@code baseName} names an application credential whose accessors encrypt on write
    * and decrypt on read, so admin-chat can set it through {@code SreeEnv.setPassword}.
    *
    * <p>An <b>allow-list, deliberately not a pattern.</b> {@link #isSecret} matches sixteen
    * properties and they do not behave alike: {@code log.fluentd.security.password},
    * {@code google.maps.key}, {@code sso.rsa.public.key} and {@code auth0.client.secret} are all
    * read with a plain {@code SreeEnv.getProperty}, so an encrypting write would hand each of them
    * ciphertext where they expect a literal - a broken deployment that still reports success.
    * {@code enable.changepassword} is not a secret at all; it matches on the substring
    * "password" and holds a boolean. And {@code password.encryption.key},
    * {@code password.hash.key}, {@code jwt.signing.key}, {@code sso.rsa.private.key} and the
    * {@code license.*} keys are generated or licensed material that must not be written here on
    * any path.
    *
    * <p>So membership is not inferable from the name, and every entry must be verified against its
    * accessor before it is added - the same discipline this class's javadoc requires of a catalog
    * entry, and for the same reason: the failure is silent. Both current entries were checked:
    *
    * <ul>
    *   <li>{@code openid.client.secret} - {@code inetsoft.web.admin.security.OpenIDConfig}, in this
    *       module. {@code setClientSecret} writes {@code encryptPassword(...)} and
    *       {@code getClientSecret} reverses it with {@code Tool.decryptPassword}.</li>
    *   <li>{@code stylebi.google.openid.client.secret} - {@code StyleBIGoogleOpenIDConfig}, at
    *       {@code enterprise/src/main/java/inetsoft/enterprise/sso/} in the <b>enterprise</b>
    *       superproject, which is NOT part of this repository. Same shape:
    *       {@code setClientSecret} writes
    *       {@code PasswordEncryption.newInstance().encryptPassword(...)} and
    *       {@code getClientSecret} reverses it. A reader who greps only this repository will not
    *       find that class and should not conclude the entry is unverified - note also that
    *       {@code SreeEnv.getPassword} carries a case-sensitive literal check for this property's
    *       cloud-secrets branch, which is the only trace of it visible from here.</li>
    * </ul>
    *
    * <p>An entry whose accessor lives in the enterprise superproject is still correct to list here:
    * on a Community build the property is simply never written, because nothing reads it.</p>
    *
    * <p>Reading these is still refused - see {@link #isSecret} for the egress rationale, which is
    * unaffected. This governs the write path alone.
    */
   public static boolean isEncryptedCredential(String baseName) {
      return baseName != null && ENCRYPTED_CREDENTIALS.contains(baseName.toLowerCase());
   }

   private static final Set<String> ENCRYPTED_CREDENTIALS = Set.of(
      "openid.client.secret", "stylebi.google.openid.client.secret");

   private static final String RESOURCE = "admin-property-catalog.json";
   private final List<CatalogEntry> entries;
   private final Map<String, CatalogEntry> byKey;
}
