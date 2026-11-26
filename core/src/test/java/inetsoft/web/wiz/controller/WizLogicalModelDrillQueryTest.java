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

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.DrillPath;
import inetsoft.uql.DrillSubQuery;
import inetsoft.uql.XDrillInfo;
import inetsoft.uql.XMetaInfo;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.erm.XAttribute;
import inetsoft.uql.erm.XEntity;
import inetsoft.uql.erm.XLogicalModel;
import inetsoft.web.wiz.controller.WizDataModelWire.Err;
import inetsoft.web.wiz.controller.WizLogicalModelWire.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A drill's worksheet query is stored by the asset identifier of the worksheet. The validator
 * resolves every accepted form of {@code query.entry} to it (so the write-back check compares
 * like with like), refuses what it cannot store with an error that names the field and the
 * accepted forms, and on an update leaves a stored worksheet that was deleted later alone.
 */
class WizLogicalModelDrillQueryTest extends WizLogicalModelTestBase {
   private static final IdentityID CALLER = new IdentityID("bob", "host-org");
   private static final String GLOBAL_ID = new AssetEntry(
      AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "Sales/orders", null)
      .toIdentifier();
   private static final String ORG = AssetEntry.createAssetEntry(GLOBAL_ID).getOrgID();
   private static final String USER_ID = new AssetEntry(
      AssetRepository.USER_SCOPE, AssetEntry.Type.WORKSHEET, "mine", CALLER).toIdentifier();

   /** The worksheets of the test repository. */
   private static final Set<String> EXISTING = Set.of(GLOBAL_ID, USER_ID);

   private final WizLogicalModelValidator validator = new WizLogicalModelValidator();
   private final ObjectMapper mapper = new ObjectMapper();

   private final WizDrillLinks.Worksheets worksheets = new WizDrillLinks.Worksheets() {
      @Override
      public IdentityID caller() {
         return CALLER;
      }

      @Override
      public boolean readable(AssetEntry entry) {
         return EXISTING.contains(entry.toIdentifier());
      }
   };

   private WizLogicalModelValidator.Result validate(DrillQuery query, XLogicalModel stored) {
      WizLogicalModelEnv env = new WizLogicalModelEnv(ds, dataModel, dataSourceService,
                                                      link -> link, worksheets);
      return validator.validate(env, withQuery(query), stored == null ?
                                   WizLogicalModelValidator.Mode.CREATE :
                                   WizLogicalModelValidator.Mode.UPDATE, stored);
   }

   private WizLogicalModelValidator.Result validate(String entry) {
      return validate(new DrillQuery(entry, null, List.of()), null);
   }

   private WizLogicalModelValidator.Result validate(String path, String scope) {
      return validate(new DrillQuery(null, new WorksheetAttach(path, scope), List.of()), null);
   }

   private static Definition withQuery(DrillQuery query) {
      Drill drill = new Drill(List.of(new WizLogicalModelWire.DrillPath(
         "Open", "web", "http://example.test", null, null, null, null, null, query, null)));
      return def("M", entity("E", new Attribute(
         "A", null, null, null, null, "T1", "ID", null, null, null, null, null, null, null, null,
         null, drill, null, null)));
   }

   private static DrillQuery stored(WizLogicalModelValidator.Result result) {
      return result.normalized().entities().get(0).attributes().get(0).drill().paths().get(0)
         .query();
   }

   private static XLogicalModel storedModel(String identifier) {
      XLogicalModel model = new XLogicalModel("M");
      XEntity entity = new XEntity("E");
      XAttribute attribute = new XAttribute("A", "T1", "ID", "string");
      DrillPath path = new DrillPath("Open");
      DrillSubQuery query = new DrillSubQuery();
      query.setWsEntry(AssetEntry.createAssetEntry(identifier));
      path.setQuery(query);
      XDrillInfo info = new XDrillInfo();
      info.addDrillPath(path);
      XMetaInfo meta = new XMetaInfo();
      meta.setXDrillInfo(info);
      attribute.setXMetaInfo(meta);
      entity.addAttribute(attribute);
      model.addEntity(entity);
      return model;
   }

   private static void assertRefused(WizLogicalModelValidator.Result result, String hint) {
      assertEquals(List.of("entities[E].attributes[A].drill.paths[Open].query.entry"),
                   result.errors().stream().map(Err::field).toList(), hint);
      assertEquals(List.of("DRILL_INVALID"),
                   result.errors().stream().map(Err::code).toList(), hint);
      String message = result.errors().get(0).message();
      assertTrue(message.contains(hint), message);
   }

   // -- accepted forms resolve to the identifier the Mapper stores --------------------------

   @Test
   void anIdentifierIsKept() {
      WizLogicalModelValidator.Result result = validate(GLOBAL_ID);

      assertTrue(result.valid(), result.errors().toString());
      assertEquals(GLOBAL_ID, stored(result).entry());
   }

   @Test
   void anIdentifierWithoutAnOrganizationGetsTheOrganization() {
      WizLogicalModelValidator.Result result = validate("1^2^__NULL__^Sales/orders");

      assertTrue(result.valid(), result.errors().toString());
      assertEquals(GLOBAL_ID, stored(result).entry());
   }

   @Test
   void aGlobalRunQueryStringIsResolved() {
      WizLogicalModelValidator.Result result = validate("ws:global:Sales/orders");

      assertTrue(result.valid(), result.errors().toString());
      assertEquals(GLOBAL_ID, stored(result).entry());
   }

   @Test
   void aQuotedRunQueryStringIsResolved() {
      WizLogicalModelValidator.Result result = validate("ws:global:\"Sales/orders\"");

      assertTrue(result.valid(), result.errors().toString());
      assertEquals(GLOBAL_ID, stored(result).entry());
   }

   @Test
   void aUserRunQueryStringIsResolved() {
      WizLogicalModelValidator.Result result = validate("ws:" + CALLER.convertToKey() + ":mine");

      assertTrue(result.valid(), result.errors().toString());
      assertEquals(USER_ID, stored(result).entry());
   }

   @Test
   void anAttachObjectIsResolvedInBothScopes() {
      WizLogicalModelValidator.Result global = validate("Sales/orders", "global");
      WizLogicalModelValidator.Result user = validate("mine", "user");

      assertTrue(global.valid(), global.errors().toString());
      assertEquals(GLOBAL_ID, stored(global).entry());
      assertNull(stored(global).attach(), "the stored query carries the identifier only");
      assertTrue(user.valid(), user.errors().toString());
      assertEquals(USER_ID, stored(user).entry());
   }

   @Test
   void theScopeOfAnAttachObjectIsCaseInsensitive() {
      WizLogicalModelValidator.Result result = validate("Sales/orders", " Global ");

      assertTrue(result.valid(), result.errors().toString());
      assertEquals(GLOBAL_ID, stored(result).entry());
   }

   @Test
   void anAttachPathWithQuoteAndCaretIsKept() {
      String odd = "Sales/\"q\"^x";
      Set<String> known = Set.of("1^2^__NULL__^" + odd + "^" + ORG);
      WizLogicalModelEnv env = new WizLogicalModelEnv(
         ds, dataModel, dataSourceService, link -> link, new WizDrillLinks.Worksheets() {
            @Override
            public IdentityID caller() {
               return CALLER;
            }

            @Override
            public boolean readable(AssetEntry entry) {
               return known.contains(entry.toIdentifier());
            }
         });
      WizLogicalModelValidator.Result result = validator.validate(
         env, withQuery(new DrillQuery(null, new WorksheetAttach(odd, "global"), List.of())),
         WizLogicalModelValidator.Mode.CREATE, null);

      assertTrue(result.valid(), result.errors().toString());
      assertEquals("1^2^__NULL__^" + odd + "^" + ORG, stored(result).entry());
   }

   @Test
   void theParametersSurviveTheResolution() {
      DrillQuery query = new DrillQuery("ws:global:Sales/orders", null,
                                        List.of(new DrillQueryParam("k", "ID")));
      WizLogicalModelValidator.Result result = validate(query, null);

      assertTrue(result.valid(), result.errors().toString());
      assertEquals(List.of(new DrillQueryParam("k", "ID")), stored(result).params());
   }

   @Test
   void whatIsWrittenIsWhatIsStoredSoTheReadBackMatches() {
      WizLogicalModelValidator.Result result = validate("ws:global:Sales/orders");

      // the saved model reads back as the identifier the mapper stores
      Definition readBack = withQuery(new DrillQuery(GLOBAL_ID, List.of()));

      assertNotNull(WizLogicalModelReadBack.diff(
         withQuery(new DrillQuery("ws:global:Sales/orders", List.of())), readBack),
                    "the raw runQuery string would have been reported as a failed write");
      assertNull(WizLogicalModelReadBack.diff(
         withQuery(new DrillQuery(stored(result).entry(), List.of())), readBack));
   }

   // -- refusals name the field and the accepted forms ---------------------------------------

   @Test
   void aPlainPathIsRefusedWithTheAcceptedForms() {
      assertRefused(validate("Sales/orders"), "ws:global:<path>");
      assertRefused(validate("para"), "asset identifier");
   }

   @Test
   void aBlankEntryIsRefused() {
      assertRefused(validate(new DrillQuery("  ", null, List.of()), null), "attach");
      assertRefused(validate(new DrillQuery(null, null, List.of()), null), "attach");
   }

   @Test
   void aViewsheetIdentifierIsNotAWorksheet() {
      assertRefused(validate("1^128^__NULL__^Reports/Sales^" + ORG), "not a worksheet");
   }

   @Test
   void aTableSegmentIsRefusedBecauseItCannotBeStored() {
      assertRefused(validate("ws:global:Sales/orders:Orders"), "table segment");
   }

   @Test
   void aMissingWorksheetIsRefused() {
      assertRefused(validate("ws:global:NoSuch/worksheet"), "does not exist");
      assertRefused(validate("NoSuch/worksheet", "global"), "does not exist");
   }

   @Test
   void anUnknownAttachScopeIsRefused() {
      assertRefused(validate("Sales/orders", "shared"), "\"global\" or \"user\"");
   }

   @Test
   void aUserScopeAttachNeedsTheCaller() {
      WizLogicalModelEnv env = new WizLogicalModelEnv(ds, dataModel, dataSourceService,
                                                      link -> link);
      WizLogicalModelValidator.Result result = validator.validate(
         env, withQuery(new DrillQuery(null, new WorksheetAttach("mine", "user"), List.of())),
         WizLogicalModelValidator.Mode.CREATE, null);

      assertRefused(result, "without the caller");
   }

   @Test
   void aReportScopeEntryIsRefused() {
      assertRefused(validate("ws:Sales/orders"), "global or the user scope");
   }

   // -- update: a stored worksheet is not looked at again ------------------------------------

   @Test
   void anUnchangedStoredWorksheetWhoseTargetIsGoneIsNotRefused() {
      String gone = "1^2^__NULL__^Deleted/later^" + ORG;
      WizLogicalModelValidator.Result result = validate(
         new DrillQuery(gone, null, List.of()), storedModel(gone));

      assertTrue(result.valid(), result.errors().toString());
      assertEquals(gone, stored(result).entry());
   }

   @Test
   void theUnchangedCheckComparesTheResolvedIdentifier() {
      String gone = "1^2^__NULL__^Deleted/later^" + ORG;
      WizLogicalModelValidator.Result byString = validate(
         new DrillQuery("ws:global:Deleted/later", null, List.of()), storedModel(gone));
      WizLogicalModelValidator.Result byAttach = validate(
         new DrillQuery(null, new WorksheetAttach("Deleted/later", "global"), List.of()),
         storedModel(gone));

      assertTrue(byString.valid(), byString.errors().toString());
      assertTrue(byAttach.valid(), byAttach.errors().toString());
   }

   @Test
   void aChangedWorksheetIsCheckedOnAnUpdate() {
      String gone = "1^2^__NULL__^Deleted/later^" + ORG;
      WizLogicalModelValidator.Result result = validate(
         new DrillQuery("ws:global:NoSuch/worksheet", null, List.of()), storedModel(gone));

      assertRefused(result, "does not exist");
   }

   @Test
   void aGlobalAndAUserWorksheetOfTheSamePathAreNotTheSameStoredWorksheet() {
      String globalGone = "1^2^__NULL__^same^" + ORG;
      WizLogicalModelValidator.Result result = validate(
         new DrillQuery(null, new WorksheetAttach("same", "user"), List.of()),
         storedModel(globalGone));

      assertRefused(result, "does not exist");
      assertEquals(AssetRepository.GLOBAL_SCOPE,
                   AssetEntry.createAssetEntry(globalGone).getScope());
   }

   // -- the wire: the object form of entry -----------------------------------------------------

   @Test
   void theWireReadsAStringAndTheObjectForm() throws Exception {
      DrillQuery text = mapper.readValue("{\"entry\":\"ws:global:x\",\"params\":[]}",
                                         DrillQuery.class);
      DrillQuery object = mapper.readValue(
         "{\"entry\":{\"path\":\"a b\",\"scope\":\"user\"},\"params\":[]}", DrillQuery.class);

      assertEquals("ws:global:x", text.entry());
      assertNull(text.attach());
      assertNull(object.entry());
      assertEquals(new WorksheetAttach("a b", "user"), object.attach());
   }

   @Test
   void theWireRefusesAMalformedEntryNamingTheField() {
      for(String bad : new String[] { "{\"entry\":5}", "{\"entry\":[]}",
                                      "{\"entry\":{\"path\":\"x\"}}",
                                      "{\"entry\":{\"path\":\"x\",\"scope\":\"global\",\"z\":1}}",
                                      "{\"entry\":{\"path\":\"\",\"scope\":\"global\"}}",
                                      "{\"attach\":{\"path\":\"x\",\"scope\":\"global\"}}" })
      {
         Exception e = assertThrows(Exception.class,
                                    () -> mapper.readValue(bad, DrillQuery.class), bad);
         assertTrue(String.valueOf(e.getMessage()).contains("query."), bad + ": " + e.getMessage());
      }
   }

   @Test
   void theWireWritesTheIdentifierOnly() throws Exception {
      String json = mapper.writeValueAsString(new DrillQuery(GLOBAL_ID, List.of()));

      assertFalse(json.contains("attach"), json);
      assertTrue(json.contains(GLOBAL_ID), json);
   }
}
