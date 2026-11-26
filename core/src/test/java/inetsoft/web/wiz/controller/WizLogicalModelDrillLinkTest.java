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

import inetsoft.uql.DrillPath;
import inetsoft.uql.XDrillInfo;
import inetsoft.uql.XMetaInfo;
import inetsoft.uql.erm.XAttribute;
import inetsoft.uql.erm.XEntity;
import inetsoft.uql.erm.XLogicalModel;
import inetsoft.web.wiz.controller.WizDataModelWire.Err;
import inetsoft.web.wiz.controller.WizLogicalModelWire.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A viewsheet auto drill is stored by the asset identifier of its target, as the Portal stores
 * the one picked from the repository tree. The validator resolves a path to it and refuses a
 * viewsheet that does not exist; on an update only a new or changed link is looked at.
 */
class WizLogicalModelDrillLinkTest extends WizLogicalModelTestBase {
   private static final String ID = "1^128^__NULL__^Reports/Sales^host-org";
   private final WizLogicalModelValidator validator = new WizLogicalModelValidator();

   /** The repository of the test: one global viewsheet, {@code Reports/Sales}. */
   private static String lookup(String link) {
      if(link.equals("Reports/Sales") || link.equals(ID)) {
         return ID;
      }

      return null;
   }

   private WizLogicalModelValidator.Result validate(Definition definition, XLogicalModel stored) {
      WizLogicalModelEnv env = new WizLogicalModelEnv(ds, dataModel, dataSourceService,
                                                      WizLogicalModelDrillLinkTest::lookup);
      return validator.validate(env, definition, stored == null ?
                                   WizLogicalModelValidator.Mode.CREATE :
                                   WizLogicalModelValidator.Mode.UPDATE, stored);
   }

   private static Definition withDrill(String linkType, String link) {
      return def("M", entity("E", drillAttribute(linkType, link)));
   }

   private static Attribute drillAttribute(String linkType, String link) {
      Drill drill = new Drill(List.of(new WizLogicalModelWire.DrillPath(
         "Open", linkType, link, null, null, null, null, null, null, null)));
      return new Attribute("A", null, null, null, null, "T1", "ID", null, null, null, null,
                           null, null, null, null, null, drill, null, null);
   }

   private static String link(WizLogicalModelValidator.Result result) {
      return result.normalized().entities().get(0).attributes().get(0).drill().paths().get(0)
         .link();
   }

   private static XLogicalModel stored(String link) {
      XLogicalModel model = new XLogicalModel("M");
      XEntity entity = new XEntity("E");
      XAttribute attribute = new XAttribute("A", "T1", "ID", "string");
      DrillPath path = new DrillPath("Open");
      path.setLinkType(DrillPath.VIEWSHEET_LINK);
      path.setLink(link);
      XDrillInfo info = new XDrillInfo();
      info.addDrillPath(path);
      XMetaInfo meta = new XMetaInfo();
      meta.setXDrillInfo(info);
      attribute.setXMetaInfo(meta);
      entity.addAttribute(attribute);
      model.addEntity(entity);
      return model;
   }

   @Test
   void theRoutesRefuseAMissingViewsheetOnACreateAndOnADryRun() throws Exception {
      for(Boolean dryRun : new Boolean[] { null, Boolean.TRUE }) {
         Object result = controller.create(new CreateRequest(
            ds, withDrill("viewsheet", "NoSuchFolder/missing"), dryRun), bob);

         assertEquals(List.of("DRILL_INVALID"), codes(result));
         assertEquals("entities[E].attributes[A].drill.paths[Open].link",
                      ((WizDataModelWire.Failure) result).errors().get(0).field());
      }

      assertNull(fresh().getLogicalModel("M"), "nothing was written");
   }

   @Test
   void aPathIsResolvedToTheAssetIdentifier() {
      WizLogicalModelValidator.Result result =
         validate(withDrill("viewsheet", "Reports/Sales"), null);

      assertTrue(result.valid(), result.errors().toString());
      assertEquals(ID, link(result));
   }

   @Test
   void anExistingIdentifierIsKept() {
      WizLogicalModelValidator.Result result = validate(withDrill("viewsheet", ID), null);

      assertTrue(result.valid(), result.errors().toString());
      assertEquals(ID, link(result));
   }

   @Test
   void aMissingViewsheetIsRefusedOnACreate() {
      for(String missing : new String[] { "NoSuchFolder/missing",
                                          "1^128^__NULL__^NoSuchFolder/missing^host-org" })
      {
         WizLogicalModelValidator.Result result =
            validate(withDrill("viewsheet", missing), null);

         assertEquals(List.of("entities[E].attributes[A].drill.paths[Open].link"),
                      result.errors().stream().map(Err::field).toList(), missing);
         assertEquals(List.of("DRILL_INVALID"),
                      result.errors().stream().map(Err::code).toList(), missing);
         assertTrue(result.errors().get(0).message().contains(missing),
                    result.errors().get(0).message());
      }
   }

   @Test
   void aWebLinkIsNotLookedUp() {
      WizLogicalModelValidator.Result result =
         validate(withDrill("web", "http://example.test/x"), null);

      assertTrue(result.valid(), result.errors().toString());
      assertEquals("http://example.test/x", link(result));
   }

   @Test
   void aChangedLinkIsCheckedOnAnUpdate() {
      WizLogicalModelValidator.Result result = validate(
         withDrill("viewsheet", "NoSuchFolder/missing"), stored(ID));

      assertEquals(List.of("DRILL_INVALID"),
                   result.errors().stream().map(Err::code).toList());
   }

   @Test
   void anUnchangedStoredLinkWhoseTargetIsGoneIsNotRefused() {
      String gone = "1^128^__NULL__^Deleted/later^host-org";
      WizLogicalModelValidator.Result result =
         validate(withDrill("viewsheet", gone), stored(gone));

      assertTrue(result.valid(), result.errors().toString());
      assertEquals(gone, link(result));
   }

   @Test
   void aPathIsResolvedOnAnUpdateToo() {
      WizLogicalModelValidator.Result result =
         validate(withDrill("viewsheet", "Reports/Sales"), stored("Other"));

      assertTrue(result.valid(), result.errors().toString());
      assertEquals(ID, link(result));
   }
}
