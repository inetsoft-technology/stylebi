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
package inetsoft.uql.asset;

/*
 * Bug #78075: the 3-arg checkAssetPermission (checkUserAsset=false) admits a caller on another
 * user's USER_SCOPE asset when the caller is the owner, or holds SECURITY_USER ADMIN on the owner
 * (the admin of the owning user: org admin, site admin, delegated EM user admin). That ADMIN
 * check went to AbstractAssetEngine.checkPermission(Principal, ResourceType, IdentityID, EnumSet),
 * a base stub that returns true and that the production engine (RepletEngine/AnalyticEngine)
 * does not override, so any same-org user (My Dashboards is granted by default) passed READ,
 * WRITE and DELETE, and could delete, rename or move another user's private viewsheet.
 *
 * [ordinary user]                    3-arg R/W/D refused; removeSheet/changeSheet refused and the
 *                                    owner's entry is intact
 * [not-logged-in non-owner]          refused with MessageException, no SecurityException escapes
 * [org admin -> site admin's VS]     refused (Bug #77347 rule)
 * [owner, logged in or not]          passes, nothing logged at ERROR             (control)
 * [site admin]                       passes, can remove                          (control)
 * [org admin]                        passes, can rename                          (control)
 * [delegated user admin, EM writer]  passes (explicit grant on the owner / Users root)
 * [org admin, 4-arg READ true]       refused (checkUserAsset=true is site-admin only) (control)
 *
 * The engine is the production one (AssetUtil.getAssetRepository(false)), and every permission
 * answer comes from the real SecurityEngine and DefaultCheckPermissionStrategy over the file
 * provider (SecurityTestDataBuilder). Delegated grants are written with the EM writer
 * (IdentityService.setIdentityPermissions). Single tenant, host org: SUtil.isMultiTenant() is
 * false in community, so grants are stored and read in the default org's bucket.
 */

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.util.Identity;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.MessageException;
import inetsoft.util.ThreadContext;
import inetsoft.web.admin.security.IdentityModel;
import inetsoft.web.admin.security.IdentityService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UserScopeAdminOfOwnerPermissionTest {
   private static final String ORG = Organization.getDefaultOrganizationID();
   private static final String OWNER = "owner78075";
   private static final String READER = "reader78075";
   private static final String STRANGER = "stranger78075";
   private static final String ORG_ADMIN = "orgAdmin78075";
   private static final String SITE_ADMIN = "siteAdmin78075";
   private static final String DELEGATE = "delegate78075";
   private static final String ROOT_DELEGATE = "rootDelegate78075";
   private static final ResourceAction[] ACTIONS =
      { ResourceAction.READ, ResourceAction.WRITE, ResourceAction.DELETE };

   private SecurityTestDataBuilder builder;
   private AssetRepository repository;
   private IdentityID ownerId;
   private SRPrincipal owner;
   private SRPrincipal reader;
   private SRPrincipal orgAdmin;
   private SRPrincipal siteAdmin;
   private SRPrincipal delegate;
   private SRPrincipal rootDelegate;
   private AssetEntry ownerEntry;
   private final List<AssetEntry> cleanup = new ArrayList<>();
   private final List<IdentityID> writtenGrants = new ArrayList<>();
   private String oldProvider;
   private Principal savedContextPrincipal;
   private Principal savedPrincipal;

   @BeforeEach
   void setUp() throws Exception {
      savedContextPrincipal = ThreadContext.getContextPrincipal();
      savedPrincipal = ThreadContext.getPrincipal();
      ThreadContext.setContextPrincipal(null);
      ThreadContext.setPrincipal(null);
      oldProvider = SreeEnv.getProperty("security.provider");
      SreeEnv.setProperty("security.provider", "file");

      builder = SecurityTestDataBuilder.create()
         .withMultiTenant("false")
         .addUser(OWNER, ORG, "password")
         .addUser(READER, ORG, "password")
         .addUser(STRANGER, ORG, "password")
         .addUser(DELEGATE, ORG, "password")
         .addUser(ROOT_DELEGATE, ORG, "password")
         .addUser(ORG_ADMIN, ORG, "password")
         .addOrgAdminRole("orgAdmins78075", ORG)
         .addUserToRole(ORG_ADMIN, "orgAdmins78075", ORG)
         .addUser(SITE_ADMIN, ORG, "password")
         .addSysAdminRole("siteAdmins78075", ORG)
         .addUserToRole(SITE_ADMIN, "siteAdmins78075", ORG)
         .setup();
      assertTrue(SecurityEngine.getSecurity().isSecurityEnabled(), "test requires security on");

      repository = AssetUtil.getAssetRepository(false);
      ownerId = new IdentityID(OWNER, ORG);
      owner = builder.principalOf(OWNER, ORG);
      reader = builder.principalOf(READER, ORG);
      orgAdmin = builder.principalOf(ORG_ADMIN, ORG);
      siteAdmin = builder.principalOf(SITE_ADMIN, ORG);
      delegate = builder.principalOf(DELEGATE, ORG);
      rootDelegate = builder.principalOf(ROOT_DELEGATE, ORG);
      ownerEntry = createPrivateViewsheet(ownerId, owner);
   }

   @AfterEach
   void tearDown() throws Exception {
      try {
         AssetRepository.IGNORE_PERM.set(true);

         try {
            for(AssetEntry entry : cleanup) {
               if(repository.containsEntry(entry)) {
                  repository.removeSheet(entry, null, true);
               }
            }
         }
         finally {
            AssetRepository.IGNORE_PERM.remove();
         }

         AuthorizationProvider authz =
            SecurityEngine.getSecurity().getSecurityProvider().getAuthorizationProvider();

         for(IdentityID id : writtenGrants) {
            authz.removePermission(ResourceType.SECURITY_USER, id);
         }
      }
      finally {
         cleanup.clear();
         writtenGrants.clear();

         if(builder != null) {
            builder.teardown();
         }

         SreeEnv.setProperty("security.provider", oldProvider == null ? "" : oldProvider);
         ThreadContext.setContextPrincipal(savedContextPrincipal);
         ThreadContext.setPrincipal(savedPrincipal);
      }
   }

   @Test
   void precondition_readerIsAnOrdinaryUserWithMyDashboards() {
      assertFalse(OrganizationManager.getInstance().isSiteAdmin(reader));
      as(reader, () -> {
         assertTrue(SecurityEngine.getSecurity().checkPermission(
            reader, ResourceType.MY_DASHBOARDS, "*", ResourceAction.READ),
                    "My Dashboards is granted by default, or the refusals prove nothing");
         assertFalse(SecurityEngine.getSecurity().checkPermission(
            reader, ResourceType.SECURITY_USER, ownerId, ResourceAction.ADMIN),
                    "the reader must not be an admin of the owner");
      });
   }

   // ---- ordinary same-org user: refused ---------------------------------------------------

   @Test
   void ordinaryUserRefusedOnOthersPrivateViewsheet() {
      assertRefusedAll(reader, ownerEntry);
   }

   @Test
   void ordinaryUserCannotRemoveOthersPrivateViewsheet() {
      as(reader, () -> assertThrows(MessageException.class,
                                    () -> repository.removeSheet(ownerEntry, reader, true),
                                    "an ordinary user must not delete another user's private " +
                                    "viewsheet"));
      assertTrue(exists(ownerEntry), "the owner's viewsheet must still exist");
   }

   @Test
   void ordinaryUserCannotRenameOthersPrivateViewsheet() {
      AssetEntry renamed = userEntry(ownerId, ownerEntry.getName() + "Renamed");
      cleanup.add(renamed);

      as(reader, () -> assertThrows(MessageException.class,
                                    () -> repository.changeSheet(ownerEntry, renamed, reader,
                                                                 true, true, false),
                                    "an ordinary user must not rename another user's private " +
                                    "viewsheet"));
      assertTrue(exists(ownerEntry), "the owner's viewsheet must still exist");
      assertFalse(exists(renamed), "no renamed copy may exist");
   }

   @Test
   void ordinaryUserCannotMoveOthersPrivateViewsheetIntoOwnScope() {
      AssetEntry stolen = userEntry(reader.getIdentityID(), ownerEntry.getName() + "Stolen");
      cleanup.add(stolen);

      as(reader, () -> assertThrows(MessageException.class,
                                    () -> repository.changeSheet(ownerEntry, stolen, reader,
                                                                 true, true, false),
                                    "an ordinary user must not move another user's private " +
                                    "viewsheet into their own scope"));
      assertTrue(exists(ownerEntry), "the owner's viewsheet must still exist");
      assertFalse(exists(stolen), "the viewsheet must not appear in the reader's scope");
   }

   @Test
   void notLoggedInNonOwnerRefusedWithoutSecurityException() {
      // a real user whose principal was built in code: not in the logged-in map, no ignoreLogin
      SRPrincipal stranger = new SRPrincipal(new IdentityID(STRANGER, ORG), new IdentityID[0],
                                             new String[0], ORG, 1L);

      assertRefusedAll(stranger, ownerEntry);
   }

   @Test
   void orgAdminRefusedOnSiteAdminsPrivateViewsheet() throws Exception {
      AssetEntry siteAdminEntry = createPrivateViewsheet(siteAdmin.getIdentityID(), siteAdmin);

      assertRefusedAll(orgAdmin, siteAdminEntry);
   }

   // ---- owner and admins of the owner: still pass -------------------------------------------

   @Test
   void ownerPasses() {
      SRPrincipal notLoggedInOwner = new SRPrincipal(ownerId, new IdentityID[0], new String[0],
                                                     ORG, 1L);
      ListAppender<ILoggingEvent> errors = captureErrors();

      try {
         for(SRPrincipal user : new SRPrincipal[] { owner, notLoggedInOwner }) {
            for(ResourceAction action : ACTIONS) {
               assertPasses(user, ownerEntry, action);
            }
         }
      }
      finally {
         detach(errors);
      }

      assertEquals(List.of(), errors.list.stream().map(ILoggingEvent::getFormattedMessage).toList(),
                   "the owner's check must not log an error");
   }

   @Test
   void siteAdminPassesAndCanRemove() {
      for(ResourceAction action : ACTIONS) {
         assertPasses(siteAdmin, ownerEntry, action);
      }

      as(siteAdmin, () -> assertDoesNotThrow(
         () -> repository.removeSheet(ownerEntry, siteAdmin, true)));
      assertFalse(exists(ownerEntry));
   }

   @Test
   void orgAdminPassesAndCanRename() {
      for(ResourceAction action : ACTIONS) {
         assertPasses(orgAdmin, ownerEntry, action);
      }

      AssetEntry renamed = userEntry(ownerId, ownerEntry.getName() + "ByAdmin");
      cleanup.add(renamed);
      as(orgAdmin, () -> assertDoesNotThrow(
         () -> repository.changeSheet(ownerEntry, renamed, orgAdmin, true, true, false)));
      assertFalse(exists(ownerEntry));
      assertTrue(exists(renamed));
   }

   @Test
   void orgAdminStillRefusedByStrictOwnerCheck() {
      as(orgAdmin, () -> assertThrows(MessageException.class, () -> repository
         .checkAssetPermission(orgAdmin, ownerEntry, ResourceAction.READ, true)));
   }

   @Test
   void delegatedAdminOfOwnerPasses() throws Exception {
      grantAdmin(ownerId, delegate);
      as(delegate, () -> assertTrue(SecurityEngine.getSecurity().checkPermission(
         delegate, ResourceType.SECURITY_USER, ownerId, ResourceAction.ADMIN),
                                    "the EM grant must make the delegate an admin of the owner"));

      for(ResourceAction action : ACTIONS) {
         assertPasses(delegate, ownerEntry, action);
      }
   }

   @Test
   void delegatedAdminOfUsersRootPasses() throws Exception {
      grantAdmin(new IdentityID("Users", ORG), rootDelegate);
      as(rootDelegate, () -> assertTrue(SecurityEngine.getSecurity().checkPermission(
         rootDelegate, ResourceType.SECURITY_USER, ownerId, ResourceAction.ADMIN),
                                    "the EM grant on the Users root must make the delegate an " +
                                    "admin of the owner"));

      for(ResourceAction action : ACTIONS) {
         assertPasses(rootDelegate, ownerEntry, action);
      }
   }

   // ---- helpers ---------------------------------------------------------------------------

   /** Checks every action, so a failure report lists each action that was let through. */
   private void assertRefusedAll(SRPrincipal user, AssetEntry entry) {
      assertAll(Arrays.stream(ACTIONS)
                   .map(action -> (Executable) () -> assertRefused(user, entry, action)));
   }

   private void assertRefused(SRPrincipal user, AssetEntry entry, ResourceAction action) {
      as(user, () -> assertThrows(
         MessageException.class, () -> repository.checkAssetPermission(user, entry, action),
         user.getName() + " must be refused " + action + " on " + entry.getUser() +
         "'s private viewsheet"));
   }

   private void assertPasses(SRPrincipal user, AssetEntry entry, ResourceAction action) {
      as(user, () -> assertDoesNotThrow(
         () -> repository.checkAssetPermission(user, entry, action),
         user.getName() + " must pass " + action + " on " + entry.getUser() +
         "'s private viewsheet"));
   }

   /** Writes an ADMIN grant on a SECURITY_USER resource the way the EM user page saves it. */
   private void grantAdmin(IdentityID resource, SRPrincipal grantee) {
      IdentityService identityService = new IdentityService(
         SecurityEngine.getSecurity(), SecurityEngine.getSecurity().getSecurityProvider(), null,
         null, null, null, null, null, null, null, null, null, null, null, Optional.empty(), null,
         null, null, null, null, null, null, null, null, null, null, null, null,
         Optional.empty());
      List<IdentityModel> grantees = List.of(IdentityModel.builder()
                                                .identityID(grantee.getIdentityID())
                                                .type(Identity.USER)
                                                .build());
      writtenGrants.add(resource);
      as(siteAdmin, () -> identityService.setIdentityPermissions(
         resource, resource, ResourceType.SECURITY_USER, siteAdmin, grantees, ORG));
   }

   private AssetEntry createPrivateViewsheet(IdentityID user, SRPrincipal principal)
      throws Exception
   {
      AssetEntry created = userEntry(user, "Bug78075VS" + System.nanoTime());
      AssetRepository.IGNORE_PERM.set(true);

      try {
         repository.setSheet(created, new Viewsheet(), principal, true);
      }
      finally {
         AssetRepository.IGNORE_PERM.remove();
      }

      // reopen from the stored identifier, as the public API and the portal tree do
      AssetEntry entry = AssetEntry.createAssetEntry(created.toIdentifier());
      cleanup.add(entry);
      assertTrue(exists(entry));
      return entry;
   }

   private static AssetEntry userEntry(IdentityID user, String name) {
      return new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET, name, user,
                            ORG);
   }

   private boolean exists(AssetEntry entry) {
      try {
         return repository.containsEntry(AssetEntry.createAssetEntry(entry.toIdentifier()));
      }
      catch(Exception ex) {
         throw new AssertionError(ex);
      }
   }

   /** Runs as the request thread of {@code user} does: with it as the thread principal. */
   private static void as(Principal user, ThrowingRunnable runnable) {
      Principal context = ThreadContext.getContextPrincipal();
      Principal principal = ThreadContext.getPrincipal();
      ThreadContext.setContextPrincipal(user);
      ThreadContext.setPrincipal(user);

      try {
         runnable.run();
      }
      catch(RuntimeException | Error ex) {
         throw ex;
      }
      catch(Throwable ex) {
         throw new AssertionError(ex);
      }
      finally {
         ThreadContext.setContextPrincipal(context);
         ThreadContext.setPrincipal(principal);
      }
   }

   private static ListAppender<ILoggingEvent> captureErrors() {
      ListAppender<ILoggingEvent> appender = new ListAppender<>();
      appender.addFilter(new ch.qos.logback.core.filter.Filter<>() {
         @Override
         public ch.qos.logback.core.spi.FilterReply decide(ILoggingEvent event) {
            return event.getLevel().isGreaterOrEqual(Level.ERROR) ?
               ch.qos.logback.core.spi.FilterReply.NEUTRAL :
               ch.qos.logback.core.spi.FilterReply.DENY;
         }
      });
      appender.start();
      ((Logger) LoggerFactory.getLogger("inetsoft")).addAppender(appender);
      return appender;
   }

   private static void detach(ListAppender<ILoggingEvent> appender) {
      ((Logger) LoggerFactory.getLogger("inetsoft")).detachAppender(appender);
      appender.stop();
   }

   @FunctionalInterface
   private interface ThrowingRunnable {
      void run() throws Throwable;
   }
}
