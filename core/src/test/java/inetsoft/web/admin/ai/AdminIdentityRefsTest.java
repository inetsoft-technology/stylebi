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

import inetsoft.sree.security.*;
import inetsoft.web.admin.ai.AdminIdentityRefs.Directory;
import inetsoft.web.admin.ai.AdminIdentityRefs.Kind;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bugs #78857 and #78866: the decision table of the shared admin-ai identity-reference parser.
 * Orgs {@code cur} (current) and {@code other} exist; identities are listed per test.
 */
@Tag("core")
class AdminIdentityRefsTest {
   private final Set<IdentityID> identities = new HashSet<>();
   private int lookups;

   private final Directory dir = new Directory() {
      @Override
      public boolean orgExists(String orgId) {
         lookups++;
         return Set.of("cur", "other").contains(orgId);
      }

      @Override
      public boolean exists(Kind kind, IdentityID id) {
         lookups++;
         return identities.contains(id);
      }
   };

   private IdentityID parse(String raw, boolean allowCrossOrg) {
      return AdminIdentityRefs.parse("f", raw, "cur", Kind.USER, allowCrossOrg, false, dir);
   }

   private String refusal(String raw, boolean allowCrossOrg) {
      return assertThrows(IllegalArgumentException.class, () -> parse(raw, allowCrossOrg))
         .getMessage();
   }

   @ParameterizedTest
   @ValueSource(strings = { "", "  " })
   void blankIsNull(String raw) {
      assertNull(parse(raw, true));
      assertNull(parse(null, true));
   }

   @ParameterizedTest
   @CsvSource({
      "a,             a,   cur",
      "' a ',         a,   cur",
      "a:cur,         a,   cur",
      "a~;~cur,       a,   cur",
   })
   void bareAndCurrentOrgFormsMakeNoLookup(String raw, String name, String org) {
      assertEquals(new IdentityID(name, org), parse(raw, false));
      assertEquals(0, lookups);
   }

   /** A doubled key (the pre-fix plan echo) splits at the first delimiter, naming no real org. */
   @Test void aDoubledKeySplitsAtTheFirstDelimiter() {
      assertTrue(refusal("a~;~cur~;~cur", true)
                    .contains("organization \"cur~;~cur\" does not exist"));
   }

   @Test void caretWithTheCurrentOrgSplitsWithOneLiteralProbe() {
      assertEquals(new IdentityID("a", "cur"), parse("a^cur", false));
      assertEquals(1, lookups);
   }

   @Test void caretWithAnotherKnownOrgSplitsAndFollowsTheOrgPolicy() {
      assertEquals(new IdentityID("a", "other"), parse("a^other", true));
      assertTrue(refusal("a^other", false).contains("targets org \"other\""));
   }

   @Test void theLastCaretSeparates() {
      assertEquals(new IdentityID("a^b", "cur"), parse("a^b^cur", false));
   }

   @Test void caretWithAnUnknownOrgShapedSuffixIsRefusedUnlessTheLiteralExists() {
      assertTrue(refusal("a^SELF", true).contains("no organization \"SELF\""));

      identities.add(new IdentityID("a^SELF", "cur"));

      assertEquals(new IdentityID("a^SELF", "cur"), parse("a^SELF", true));
   }

   @Test void caretWithASuffixThatCannotBeAnOrgIdIsTheLiteral() {
      assertEquals(new IdentityID("a^b c", "cur"), parse("a^b c", true));
      assertEquals(new IdentityID("a^b.c", "cur"), parse("a^b.c", true));
   }

   @Test void caretWhereOnlyTheLiteralExistsResolvesTheLiteral() {
      identities.add(new IdentityID("j^cur", "cur"));

      assertEquals(new IdentityID("j^cur", "cur"), parse("j^cur", false));
   }

   @Test void caretWhereBothReadingsExistIsAmbiguous() {
      identities.add(new IdentityID("j^cur", "cur"));
      identities.add(new IdentityID("j", "cur"));

      String message = refusal("j^cur", false);

      assertTrue(message.contains("ambiguous"), message);
      assertTrue(message.contains("both"), message);
   }

   @Test void caretWhereNeitherReadingExistsSplits() {
      assertEquals(new IdentityID("ghost", "cur"), parse("ghost^cur", false));
   }

   @ParameterizedTest
   @ValueSource(strings = { "a^__GLOBAL__", "a~;~__GLOBAL__", "a:__GLOBAL__", "a~;~null" })
   void globalIsRefusedUnlessAllowed(String raw) {
      assertTrue(refusal(raw, true).contains("global"));
      assertEquals(new IdentityID("a", null),
                   AdminIdentityRefs.parse("f", raw, "cur", Kind.ROLE, true, true, dir));
   }

   @ParameterizedTest
   @ValueSource(strings = { "a:", ":cur", "~;~cur", "a~;~", "^cur", "a^" })
   void blankPartsOfAnExplicitFormAreRefused(String raw) {
      assertTrue(refusal(raw, true).contains("non-blank"), raw);
   }

   @Test void anUnknownExplicitOrgIsRefusedWhereCrossOrgIsAllowed() {
      assertTrue(refusal("a:nope", true).contains("organization \"nope\" does not exist"));
      assertTrue(refusal("a~;~nope", true).contains("organization \"nope\" does not exist"));
      assertTrue(refusal("a:nope", false).contains("targets org \"nope\""));
   }

   @Test void theColonNameIsNotResplitOnACaret() {
      assertEquals(new IdentityID("a^other", "cur"), parse("a^other:cur", false));
   }

   @Test void directoryOverAProviderLooksUpLazilyAndFallsBackToAGlobalRole() {
      AuthenticationProvider provider = mock(AuthenticationProvider.class);
      when(provider.getRole(new IdentityID("Everyone", null))).thenReturn(mock(Role.class));
      when(provider.getOrganization("cur")).thenReturn(mock(Organization.class));
      when(provider.getUser(any())).thenReturn(null);
      int[] fetched = { 0 };
      Directory over = AdminIdentityRefs.of(() -> {
         fetched[0]++;
         return provider;
      });

      assertEquals(new IdentityID("a", "cur"),
                   AdminIdentityRefs.parse("f", "a", "cur", Kind.USER, true, false, over));
      assertEquals(0, fetched[0]);
      assertTrue(over.exists(Kind.ROLE, new IdentityID("Everyone", "cur")));
      assertFalse(over.exists(Kind.USER, new IdentityID("Everyone", "cur")));
      assertTrue(over.orgExists("cur"));
      assertFalse(over.orgExists(null));
   }

   @Test void withWarningPrefixesTheDescriptionOnly() {
      PlanChange change = new PlanChange("p", "o", "c", "n", "high", "value", true, "do it");

      PlanChange warned = AdminIdentityRefs.withWarning(change, "careful");

      assertEquals("WARNING: careful. do it", warned.description());
      assertEquals(new PlanChange("p", "o", "c", "n", "high", "value", true, warned.description()),
                   warned);
   }
}
