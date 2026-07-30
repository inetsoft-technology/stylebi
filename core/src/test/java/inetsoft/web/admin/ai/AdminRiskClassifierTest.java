/*
 * This software is licensed under the AGPL license, see LICENSE.txt
 * and http://www.fsf.org/licensing/licenses/agpl-3.0.html for details.
 *
 * Copyright (c) 2024, InetSoft Technology Corp, All Rights Reserved.
 */

package inetsoft.web.admin.ai;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * risk and snapshotScope are independent axes. risk is blast radius (drives agent signoff);
 * snapshotScope is the reversibility MECHANISM. Deriving the backup requirement from risk would
 * force a full Tier-2 backup for changes that restoring a before-value fully undoes, and would make
 * the catalog's per-property scope meaningless for exactly the entries that most need a considered
 * answer.
 *
 * Namespace rules are applied to the BASE name, so an org-qualified mail.* property is still high
 * risk. Applying them to the raw input would let inetsoft.org.acme.mail.smtp.host - or the plain
 * alias smtp.host - escape the mail. rule, and review would display "low" for a high-risk change.
 */
@Tag("core")
class AdminRiskClassifierTest {
   private final AdminPropertyCatalog catalog = new AdminPropertyCatalog();
   private final AdminRiskClassifier classifier = new AdminRiskClassifier(catalog);

   private AdminRiskClassifier.RiskClassification classify(String input) {
      return classifier.classify(catalog.resolve(input));
   }

   @Test
   void usesCatalogRiskAndScopeForAKnownLowRiskProperty() {
      AdminRiskClassifier.RiskClassification result = classify("max.rows");
      assertEquals("low", result.risk());
      assertEquals("value", result.snapshotScope());
      assertTrue(result.recognized());
   }

   @Test
   void keepsAHighRiskPropertysOwnValueScope() {
      // High risk, but a single reversible value: no storage backup needed.
      AdminRiskClassifier.RiskClassification result = classify("mail.smtp.host");
      assertEquals("high", result.risk());
      assertEquals("value", result.snapshotScope());
      assertTrue(result.recognized());
   }

   @Test
   void honoursACataloguedStorageScope() {
      AdminRiskClassifier.RiskClassification result = classify("security.exposedefaultorgtoall");
      assertEquals("high", result.risk());
      assertEquals("storage", result.snapshotScope());
   }

   @Test
   void appliesAHighRiskNamespaceThroughAnAlias() {
      assertEquals("high", classify("smtp.host").risk());
   }

   @Test
   void appliesAHighRiskNamespaceThroughAnOrgPrefix() {
      AdminRiskClassifier.RiskClassification result = classify("inetsoft.org.acme.mail.smtp.host");
      assertEquals("high", result.risk());
      assertTrue(result.recognized());
   }

   @Test
   void treatsAnUnknownPropertyAsHighRiskStorageScopeUnrecognized() {
      AdminRiskClassifier.RiskClassification result = classify("totally.unknown.prop");
      assertEquals("high", result.risk());
      assertEquals("storage", result.snapshotScope());
      assertFalse(result.recognized());
   }

   @Test
   void forcesHighRiskForUnknownHighRiskNamespaces() {
      for(String property : new String[] { "security.some.future.flag", "cluster.node.address",
                                           "server.http.port", "mail.some.future.setting" })
      {
         assertEquals("high", classify(property).risk(), property);
      }
   }
}
