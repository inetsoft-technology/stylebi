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

         CatalogEntry entry = catalog.getEntry(name);
         // An uncatalogued property cannot be validated or canonicalized, so its value passes
         // through verbatim; the classifier marks it high risk so review flags it.
         String proposed = entry == null
            ? requested.getValue() : catalog.canonicalizeValue(entry, requested.getValue());
         AdminRiskClassifier.RiskClassification risk = classifier.classify(name);

         changes.add(new PlanChange(name.key(), name.orgId(),
            SreeEnv.getProperty(name.key(), false, false), proposed,
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
            .append(String.valueOf(change.currentValue())).append(SEP)
            .append(String.valueOf(change.proposedValue())).append(SEP)
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

   /** Unit separator: cannot occur in a property name or value, so fields cannot run together. */
   private static final char SEP = '\u001f';
   private final AdminPropertyCatalog catalog;
   private final AdminRiskClassifier classifier;
}
