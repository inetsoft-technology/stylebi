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
package inetsoft.web.portal.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.sree.*;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.IndexedStorage;
import inetsoft.util.ThreadContext;
import inetsoft.util.log.LogManager;
import inetsoft.web.portal.model.*;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Bug #78491: shared fixture for the favorites endpoint tests. The controller runs over
 * standalone MockMvc with the portal's real error handler, on the real asset engine, replet
 * engine and registry. Requests are JSON built from the entry the real portal tree returns;
 * forged requests are edited as JSON. The security engine is the real one, except that every
 * permission check is granted unless the test denies it.
 */
class FavoritesTestSupport implements AutoCloseable {
   FavoritesTestSupport() throws Exception {
      savedContextPrincipal = ThreadContext.getContextPrincipal();
      savedPrincipal = ThreadContext.getPrincipal();
      ThreadContext.setContextPrincipal(null);
      ThreadContext.setPrincipal(null);

      orgId = Organization.getDefaultOrganizationID();
      treeService = new RepositoryTreeService(
         SUtil.getRepletRepository(), null, null, RepletRegistryManager.getInstance());
      mvc = MockMvcBuilders.standaloneSetup(new FavoritesRepositoryEntryController(treeService))
         .setControllerAdvice(new ControllerErrorHandler(mock(LogManager.class)))
         .build();
      rep = AssetUtil.getAssetRepository(false);
      jane = user("jane78491", orgId);
      bob = user("bob78491", orgId);

      SecurityEngine real = SecurityEngine.getSecurity();
      SecurityEngine spy = mock(SecurityEngine.class, withSettings().spiedInstance(real)
         .defaultAnswer(inv -> {
            if(!inv.getMethod().getName().equals("checkPermission") ||
               inv.getArguments().length != 4)
            {
               return inv.callRealMethod();
            }

            Object[] args = inv.getArguments();
            Principal p = (Principal) args[0];
            Object resource = args[2] instanceof IdentityID id ? id.convertToKey() : args[2];
            Object action = args[3] instanceof EnumSet<?> set && set.size() == 1 ?
               set.iterator().next() : args[3];
            String name = p == null ? null : p.getName();
            return !denied.contains(Arrays.asList(name, args[1], resource, action)) &&
               !denied.contains(Arrays.asList(name, args[1], null, action));
         }));
      securityStatic = mockStatic(SecurityEngine.class, CALLS_REAL_METHODS);
      securityStatic.when(SecurityEngine::getSecurity).thenReturn(spy);
   }

   @Override
   public void close() {
      securityStatic.close();
      ThreadContext.setContextPrincipal(savedContextPrincipal);
      ThreadContext.setPrincipal(savedPrincipal);
   }

   static SRPrincipal user(String name, String org) {
      return new SRPrincipal(new IdentityID(name, org), new IdentityID[0], new String[0], org, 1L);
   }

   /** Refuses the permission (user, type, resource, action); a resource of null is any. */
   void deny(Principal user, ResourceType type, String resource, ResourceAction action) {
      denied.add(Arrays.asList(user.getName(), type, resource, action));
   }

   MockHttpServletResponse send(String action, Principal user, String json) throws Exception {
      return mvc.perform(put("/api/portal/tree/favorites/" + action + "?isArchive=false")
                            .principal(user).contentType("application/json").content(json))
         .andReturn().getResponse();
   }

   /** The event the portal sends for a sheet, serialized from the real tree's entry now. */
   String treeJson(String folderPath, AssetEntry vs, Principal viewer) throws Exception {
      RepositoryEntry[] entries = treeService.getAllEntries(
         new RepositoryEntry(folderPath, RepositoryEntry.FOLDER), ResourceAction.READ,
         RepositoryEntry.ALL, viewer);

      for(RepositoryEntry e : entries) {
         if(e instanceof ViewsheetEntry && e.getAssetEntry() != null &&
            vs.toIdentifier().equals(e.getAssetEntry().toIdentifier()))
         {
            return eventJson(new ViewsheetEntryModel((ViewsheetEntry) e));
         }
      }

      throw new AssertionError("not in the tree: " + vs + " " + Arrays.toString(entries));
   }

   String folderJson(String path) throws Exception {
      return eventJson(new RepletFolderEntryModel(new RepletFolderEntry(path)));
   }

   String missingJson(AssetEntry missing) throws Exception {
      ViewsheetEntry entry = new ViewsheetEntry(missing.getPath());
      entry.setAssetEntry(missing);
      return eventJson(new ViewsheetEntryModel(entry));
   }

   private String eventJson(RepositoryEntryModel<?> model) throws Exception {
      ObjectNode event = MAPPER.createObjectNode();
      event.set("entry", MAPPER.valueToTree(model));
      event.put("confirmed", false);
      return MAPPER.writeValueAsString(event);
   }

   /** Sets the stored favorites directly on the engine. */
   void seed(AssetEntry vs, String... users) throws Exception {
      AssetEntry n = rep.getAssetEntry(vs);
      n.clearFavoritesUser();

      for(String u : users) {
         n.addFavoritesUser(u);
      }

      rep.changeSheet(AssetEntry.createAssetEntry(vs.toIdentifier()), n, null, true, false, true);
   }

   AssetEntry folder(String path) throws Exception {
      AssetEntry f = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.REPOSITORY_FOLDER, path, null, orgId);
      rep.addFolder(f, null);
      RepletRegistry registry = RepletRegistryManager.getInstance().getRegistry(orgId);
      registry.addFolder(path);
      registry.save();
      return f;
   }

   AssetEntry viewsheet(String path, String alias) throws Exception {
      AssetEntry e = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, path, null, orgId);

      if(alias != null) {
         e.setAlias(alias);
      }

      rep.setSheet(e, new Viewsheet(), null, true);
      return e;
   }

   /** The sheet's entry in its parent folder, read straight from the indexed storage. */
   AssetEntry stored(AssetEntry vs) throws Exception {
      IndexedStorage storage = ((AbstractAssetEngine) rep).getStorage(vs);
      AssetFolder folder = (AssetFolder) storage.getXMLSerializable(
         vs.getParent().toIdentifier(), null, vs.getOrgID());
      AssetEntry entry = folder.getEntry(vs);
      assertNotNull(entry, "in storage: " + vs);
      return entry;
   }

   static Map<String, String> properties(AssetEntry entry) {
      Map<String, String> map = new TreeMap<>();

      for(String key : entry.getPropertyKeys()) {
         map.put(key, entry.getProperty(key));
      }

      return map;
   }

   static final ObjectMapper MAPPER = new ObjectMapper();
   final String orgId;
   final RepositoryTreeService treeService;
   final MockMvc mvc;
   final AssetRepository rep;
   final SRPrincipal jane;
   final SRPrincipal bob;
   private final Set<List<Object>> denied = new HashSet<>();
   private final MockedStatic<SecurityEngine> securityStatic;
   private final Principal savedContextPrincipal;
   private final Principal savedPrincipal;
}
