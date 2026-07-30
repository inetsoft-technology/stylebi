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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

   @Test
   void escalatesScopeToStorageWhenANamespaceRuleOverridesACatalogLowRisk() {
      // No seeded entry is BOTH low risk AND in a high-risk namespace, so without a stub this
      // branch is unreachable in tests: deleting the escalation logic would still pass everything
      // else here. The disagreement itself is the signal - a catalog entry that calls a mail.*
      // property low risk is probably under-specified, so back it up defensively.
      AdminPropertyCatalog stub = mock(AdminPropertyCatalog.class);
      AdminPropertyName name = AdminPropertyName.parse("mail.future.setting");
      CatalogEntry lowRiskInHighRiskNamespace = new CatalogEntry(
         "mail.future.setting", List.of(), "string", List.of(), null, null,
         "Synthetic entry: catalogued low risk inside the high-risk mail. namespace.",
         "low", "value");
      when(stub.getEntry(name)).thenReturn(lowRiskInHighRiskNamespace);

      AdminRiskClassifier.RiskClassification result = new AdminRiskClassifier(stub).classify(name);

      assertEquals("high", result.risk());
      assertEquals("storage", result.snapshotScope());
      assertTrue(result.recognized());
   }
}
