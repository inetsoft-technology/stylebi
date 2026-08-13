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
import inetsoft.util.Tool;
import inetsoft.util.audit.AdminChangeRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Resolves a requested change list into a {@link ResolvedPlan} and hashes it.
 *
 * <p>The hash is the review gate. {@code preview} returns it; {@code apply} must echo it, and the
 * server re-resolves and recomputes before executing. Because the hash covers <em>current</em>
 * values as well as proposed ones, a property that changed between preview and apply produces a
 * different hash — so the operator can never approve one plan and have a different one applied.
 *
 * <p>Reads use {@code orgScope=false} to match how {@link AdminChangeService} writes; see the
 * comment there.
 */
@Component
public class AdminChangePlanService {
   @Autowired
   public AdminChangePlanService(AdminPropertyCatalog catalog, AdminRiskClassifier classifier) {
      this.catalog = catalog;
      this.classifier = classifier;
   }

   /**
    * Resolves and hashes a plan. Performs no mutation.
    *
    * @throws IllegalArgumentException with a field-named message on a blank task, an empty change
    *                                 list, a duplicate property, or an invalid value.
    */
   public ResolvedPlan resolve(PlanRequest req) {
      if(req == null || req.getTask() == null || req.getTask().trim().isEmpty()) {
         throw new IllegalArgumentException("task: a non-empty description is required");
      }

      requireHashSafe("task", req.getTask());

      if(req.getChanges() == null || req.getChanges().isEmpty()) {
         throw new IllegalArgumentException("changes: at least one change is required");
      }

      List<PlanChange> changes = new ArrayList<>();
      Set<String> seen = new HashSet<>();

      for(PlanRequest.Change requested : req.getChanges()) {
         AdminPropertyName name = catalog.resolve(requested.getProperty());

         if(!seen.add(name.key())) {
            throw new IllegalArgumentException(
               "changes: duplicate entry for " + name.key() + "; list each property once");
         }

         boolean credential = AdminPropertyCatalog.isEncryptedCredential(name.baseName());

         // Unlike the read path in AdminPropertiesController (which withholds the value but still
         // shows the property exists), a change is refused outright: this service exists to WRITE
         // a value, and blanking a secret through it would make every stored encrypted credential
         // undecryptable. See AdminPropertyCatalog.isSecret for why this is an egress/blast-radius
         // control rather than a privilege boundary.
         //
         // The exception is the small allow-list of application credentials whose accessors
         // encrypt at rest: those are written through SreeEnv.setPassword, which encrypts exactly
         // as the Enterprise Manager field does. Reading them is still refused - the egress
         // rationale is about values LEAVING the host, and is untouched by letting one in.
         if(AdminPropertyCatalog.isSecret(name.baseName()) && !credential) {
            throw new IllegalArgumentException(
               name.key() + ": secret properties cannot be changed through admin-chat");
         }

         // With cloud secrets configured, these properties hold the NAME of a secret rather than a
         // secret - getPassword resolves it through Tool.loadCredentials and reads a client_secret
         // field out of the JSON. Enterprise Manager swaps its Client Secret field for a Secret ID
         // one in that mode for exactly this reason. An agent handed a literal secret would store
         // it where a reference belongs, and nothing downstream could resolve it, so refuse rather
         // than write a value that cannot work.
         if(credential && Tool.isCloudSecrets()) {
            throw new IllegalArgumentException(
               name.key() + ": this deployment uses cloud secrets, so this property holds the ID "
               + "of a secret rather than the secret itself. Set it from Enterprise Manager's "
               + "Settings > Security > SSO page, whose Secret ID field writes the reference "
               + "correctly.");
         }

         CatalogEntry entry = catalog.getEntry(name);
         // An uncatalogued property cannot be validated or canonicalized, so its value passes
         // through verbatim; the classifier marks it high risk so review flags it.
         String proposed = entry == null
            ? requested.getValue() : catalog.canonicalizeValue(entry, requested.getValue());
         AdminRiskClassifier.RiskClassification risk = classifier.classify(name);

         requireHashSafe("property", name.key());
         requireHashSafe("value", proposed);

         // A credential's stored form is ciphertext, and the plan is relayed to a model provider by
         // the caller. Ciphertext is not the secret, but there is no reason to ship it either, and
         // an operator reading the plan is served better by whether one is already set than by a
         // base64 blob. Note the consequence for the drift gate: replacing one secret with a
         // different one is not detected between preview and apply, only set <-> unset is. That is
         // tolerable here because the human approved "set this property to the value I supplied",
         // which is what executes either way.
         //
         // proposedValue is deliberately NOT masked, and the asymmetry with currentValue is the
         // point rather than an oversight. Masking a value only helps if withholding it keeps it
         // from somewhere it would otherwise reach. currentValue qualifies: it is read off the
         // server here and would reach the caller for the first time. proposedValue does not - it
         // arrived IN this request, and apply requires the caller to send the identical changes
         // array back, because the request body is the plan and the hash is recomputed from it. So
         // the caller necessarily holds the plaintext before and after this response, and blanking
         // it in between would remove nothing while destroying the operator's ability to see WHICH
         // secret a plan writes, which is what the review gate exists to show them.
         //
         // That reasoning depends on the value always arriving through the request. If an
         // out-of-band channel is ever added - a placeholder the server resolves from somewhere
         // the caller never sees - then echoing the resolved value here WOULD be a new disclosure,
         // and this must be revisited along with it.
         String currentValue = credential
            ? (SreeEnv.getProperty(name.key(), false, false) == null ? "(not set)" : "(set)")
            : SreeEnv.getProperty(name.key(), false, false);
         requireHashSafe("currentValue", currentValue);

         changes.add(new PlanChange(name.key(), name.orgId(),
            currentValue, proposed,
            risk.risk(), risk.snapshotScope(), risk.recognized(),
            entry == null ? null : entry.description()));
      }

      boolean backup = changes.stream()
         .anyMatch(c -> AdminChangeRecord.SCOPE_STORAGE.equals(c.snapshotScope()));
      boolean signoff = changes.stream()
         .anyMatch(c -> AdminChangeRecord.RISK_HIGH.equals(c.risk()));

      return new ResolvedPlan(req.getTask().trim(), Collections.unmodifiableList(changes),
                              backup, signoff, hash(req.getTask().trim(), changes));
   }

   /**
    * SHA-256 over the canonical plan. Field order and the record separators are part of the
    * contract: changing them invalidates every outstanding preview, which is safe (an apply is
    * refused with 409) but forces operators to re-review.
    */
   private static String hash(String task, List<PlanChange> changes) {
      StringBuilder canonical = new StringBuilder(task).append('\n');

      for(PlanChange change : changes) {
         canonical.append(change.property()).append(SEP)
            .append(canonical(change.currentValue())).append(SEP)
            .append(canonical(change.proposedValue())).append(SEP)
            .append(change.risk()).append(SEP)
            .append(change.snapshotScope()).append(SEP);
      }

      try {
         byte[] digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
         StringBuilder hex = new StringBuilder(digest.length * 2);

         for(byte b : digest) {
            hex.append(String.format("%02x", b));
         }

         return hex.toString();
      }
      catch(NoSuchAlgorithmException e) {
         throw new IllegalStateException("SHA-256 is required to hash an admin change plan", e);
      }
   }

   /**
    * Renders a nullable field for the canonical form.
    *
    * <p>{@code String.valueOf} would map a genuine {@code null} and the literal string
    * {@code "null"} onto identical bytes, so a plan whose property is unset would hash the same as
    * one whose property is literally set to {@code "null"} - and drift between the two would go
    * undetected. {@link #requireHashSafe} guarantees no real field contains a control character,
    * which makes one a marker that cannot collide with any legitimate value.
    */
   private static String canonical(String value) {
      return value == null ? NULL_MARKER : value;
   }

   /**
    * Rejects a field that could forge a record boundary in the canonical form.
    *
    * <p>{@link #hash} joins fields with {@link #SEP}. If a value could contain that separator it
    * could embed extra field boundaries and make two materially different plans hash identically,
    * which would defeat the review gate. Control characters are not legitimate in a StyleBI
    * property name or value, so rejecting them enforces what the canonical form assumes rather
    * than merely documenting it.
    *
    * <p>This is a blanket rule: if a future catalog entry legitimately needs a multiline or
    * otherwise control-bearing string value, that is a catalog-authoring decision to revisit then,
    * not a defect in this guard today.
    */
   private static void requireHashSafe(String fieldName, String value) {
      if(value == null) {
         return;
      }

      for(int i = 0; i < value.length(); i++) {
         if(Character.isISOControl(value.charAt(i))) {
            throw new IllegalArgumentException(
               fieldName + ": must not contain control characters");
         }
      }
   }

   /** Unit separator: cannot occur in a property name or value, so fields cannot run together. */
   private static final char SEP = '\u001f';
   /** Marks a null field in the canonical form; cannot collide with a real value, see {@link #canonical}. */
   private static final String NULL_MARKER = "\u0001";
   private final AdminPropertyCatalog catalog;
   private final AdminRiskClassifier classifier;
}
