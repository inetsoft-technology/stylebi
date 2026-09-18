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
package inetsoft.web.admin.ai.autosave;

import inetsoft.sree.security.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.util.Tool;
import inetsoft.web.AutoSaveUtils;
import inetsoft.web.security.auth.MissingResourceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;
import java.security.Principal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read/collision-check wrapper over the product's own {@code AutoSaveUtils} Auto Save Recycle Bin
 * primitive -- the EM "Auto Saved Asset Info" pane (crash/disconnect recovery copies of
 * in-progress viewsheet/worksheet edits, NOT deleted assets; a distinct feature from the ordinary
 * Recycle Bin area). Talks to {@code AutoSaveUtils} directly, never to {@code AutoSaveController}
 * (session-cookie/CSRF EM SPA endpoint under {@code /api/em/content/repository/autosave/*}) -- the
 * same "reuse the real service, not the internal controller" rule every other wiz admin-chat area
 * follows.
 *
 * <p>{@link #listEntries} enumerates {@code AutoSaveUtils.getAutoSavedFiles(user, true)} directly
 * (the {@code recycle/}-prefixed bucket {@code AutoSaveController}'s own delete/gettime/restore
 * endpoints already operate on) and parses each storage key's {@code scope^TYPE^owner^path^ip~}
 * fields itself, rather than re-deriving {@code AutoSaveController.getRepositoryTree}'s own
 * folder-tree walk -- there is no existing "give me the full flat list" primitive to reuse, so this
 * parsing is genuinely new code, not a wrapper over an existing method the way every other read
 * path in this plugin is.
 */
@Component
public class AutoSaveRecycleBinService {
   @Autowired
   public AutoSaveRecycleBinService(AssetRepository assetRepository, SecurityProvider securityProvider) {
      this.assetRepository = assetRepository;
      this.securityProvider = securityProvider;
   }

   /** Every Auto Save Recycle Bin entry the caller holds {@code ADMIN} on -- an owner-less entry
    * (owner {@code "_NULL_"} or absent, an anonymous/system draft) is always visible once the
    * caller has cleared the controller-level site-administrator gate, since there is no owner
    * identity to check {@code ADMIN} against. */
   public List<AutoSaveRecycleBinEntryProjection> listEntries(Principal user) {
      List<AutoSaveRecycleBinEntryProjection> result = new ArrayList<>();

      for(String file : AutoSaveUtils.getAutoSavedFiles(user, true)) {
         String id = AutoSaveUtils.getName(file);
         AutoSaveRecycleBinEntryProjection entry = parse(id, user);

         if(entry == null || !isVisible(entry, user)) {
            continue;
         }

         result.add(entry);
      }

      result.sort(Comparator.comparing(AutoSaveRecycleBinEntryProjection::id));
      return result;
   }

   /** {@code Optional.empty()} both when {@code id} does not name an entry AND when it exists but
    * is not visible to {@code user} -- same posture {@code RecycleBinService.getEntry}/
    * {@code ScriptLibraryService.getEntry} document for their own areas. */
   public Optional<AutoSaveRecycleBinEntryProjection> getEntry(String id, Principal user) {
      AutoSaveRecycleBinEntryProjection entry = parse(id, user);

      if(entry == null || !exists(id, user) || !isVisible(entry, user)) {
         return Optional.empty();
      }

      return Optional.of(entry);
   }

   /**
    * @throws MissingResourceException if {@code id} does not name an entry, or exists but is not
    *         visible to {@code user}.
    */
   public AutoSaveRecycleBinEntryProjection requireEntry(String id, Principal user)
      throws MissingResourceException
   {
      return getEntry(id, user).orElseThrow(() -> new MissingResourceException(
         "id: no auto save recycle bin entry found for \"" + id + "\" -- it may not exist, may " +
         "already have been restored/deleted, or you may not have permission to see it"));
   }

   /** Whether the underlying blob actually still exists in storage -- {@link #parse} only reads
    * the id's own encoded fields and never touches storage, so a caller must combine this with it
    * to know an entry is real (mirrors {@code AutoSaveUtils.exists}). */
   public boolean exists(String id, Principal user) {
      return AutoSaveUtils.exists(AutoSaveUtils.getAutoSavedByName(id, true), user);
   }

   /**
    * Whether restoring {@code entry} to {@code assetName} would collide with something already
    * occupying that destination -- mirrors the exact duplicate check
    * {@code inetsoft.web.AutoSaveService.restoreAutoSaveAssets} runs internally via
    * {@code viewsheetService.isDuplicatedEntry} (the same underlying {@code AssetUtil} concept
    * {@code RecycleBinService.wouldCollide} already uses for its own area), exposed here so
    * {@link AutoSaveRecycleBinChangePlanService} can classify risk -- and refuse loud -- BEFORE
    * ever calling the real, mutating restore.
    */
   public boolean wouldCollide(AutoSaveRecycleBinEntryProjection entry, String assetName, Principal user)
      throws Exception
   {
      AssetEntry.Type type = AutoSaveRecycleBinEntryProjection.TYPE_WORKSHEET.equals(entry.type()) ?
         AssetEntry.Type.WORKSHEET : AssetEntry.Type.VIEWSHEET;
      IdentityID actingUser = IdentityID.getIdentityIDFromKey(user.getName());
      AssetEntry candidate = new AssetEntry(AssetRepository.GLOBAL_SCOPE, type, assetName, actingUser);
      return AssetUtil.isDuplicatedEntry(assetRepository, candidate);
   }

   /**
    * Parses a bare (recycle-prefix-stripped) Auto Save storage key's {@code scope^TYPE^owner^
    * path^ip~} fields, mirroring {@code AutoSaveController}'s own inline parsing
    * ({@code path.contains("^WORKSHEET^")}) and {@code AutoSaveUtils.getUserAutoSaveFiles}'s own
    * {@code Tool.split(asset, '^')} convention. Returns {@code null} for a malformed key (fewer
    * than 4 fields) rather than throwing -- a caller enumerating a whole bucket must not let one
    * unparseable entry abort the entire listing.
    */
   AutoSaveRecycleBinEntryProjection parse(String id, Principal user) {
      String[] attrs = Tool.split(id, '^');

      if(attrs.length <= 3) {
         return null;
      }

      String scope = (AssetRepository.USER_SCOPE + "").equals(attrs[0]) ?
         AutoSaveRecycleBinEntryProjection.SCOPE_USER : AutoSaveRecycleBinEntryProjection.SCOPE_GLOBAL;
      String type = "VIEWSHEET".equals(attrs[1]) ? AutoSaveRecycleBinEntryProjection.TYPE_DASHBOARD :
         AutoSaveRecycleBinEntryProjection.TYPE_WORKSHEET;
      String owner = "_NULL_".equals(attrs[2]) || attrs[2].isEmpty() ? null : attrs[2];
      String path = denormalizeAssetName(attrs[3]);
      String timestamp = timestampOf(id, user);
      return new AutoSaveRecycleBinEntryProjection(id, type, path, scope, owner, timestamp);
   }

   private String timestampOf(String id, Principal user) {
      try {
         Instant modified = AutoSaveUtils.getStorage(user)
            .getLastModified(AutoSaveUtils.getAutoSavedByName(id, true));
         return DateTimeFormatter.ISO_INSTANT.format(modified);
      }
      catch(FileNotFoundException e) {
         return null;
      }
   }

   /**
    * Reverses the escaping {@code Tool.normalizeFileName} applies when an asset path is folded
    * into an Auto Save storage key, so the projection shows the real, original path -- the
    * identical regex/replay logic {@code ContentRepositoryTreeService.denormalizeAssetName}
    * already uses for the SAME auto-save-filename use case (its own javadoc names this exact
    * scenario), replicated here rather than shared since that method is private to its own class
    * and this area calls {@code AutoSaveUtils} directly rather than through
    * {@code ContentRepositoryTreeService}.
    */
   static String denormalizeAssetName(String name) {
      if(name == null || name.indexOf('_') < 0) {
         return name;
      }

      Matcher matcher = NORMALIZED_CHAR.matcher(name);
      StringBuilder buffer = new StringBuilder();
      int index = 0;

      while(matcher.find()) {
         buffer.append(name, index, matcher.start()).append((char) Integer.parseInt(matcher.group(1)));
         index = matcher.end();
      }

      return buffer.append(name.substring(index)).toString();
   }

   /**
    * Reproduces {@code RecycleBinService.isVisible}'s own per-entry security check for the
    * user-owned case: {@code ADMIN} on {@code SECURITY_USER} for the entry's owner. An owner-less
    * entry has nothing further to check beyond the controller-level site-administrator gate every
    * tool in this area already requires, so it is always visible once reached.
    */
   boolean isVisible(AutoSaveRecycleBinEntryProjection entry, Principal user) {
      if(entry.owner() == null) {
         return true;
      }

      return securityProvider.checkPermission(user, ResourceType.SECURITY_USER, entry.owner(),
         ResourceAction.ADMIN);
   }

   private static final Pattern NORMALIZED_CHAR =
      Pattern.compile("_(34|42|44|47|58|59|60|62|63|92|124)_");
   private final AssetRepository assetRepository;
   private final SecurityProvider securityProvider;
}
