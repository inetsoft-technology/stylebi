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
package inetsoft.web.wiz.viewsheet;

import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.viewsheet.model.ViewsheetModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.web.wiz.script.ScriptImageService;

import java.security.Principal;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST surface for agent-driven viewsheet layout editing.
 *
 * <p>Validation and delegation only — every mutation goes through the Composer's own services
 * via {@link ViewsheetSessionService}, which supplies the capturing dispatcher, the checkpoint,
 * and the browser broadcast.
 */
@RestController
public class ViewsheetAssemblyAgentController {
   @Autowired
   public ViewsheetAssemblyAgentController(SheetAgentFeature feature,
                                   SheetJoinService joinService,
                                   SheetSessionService sessionService,
                                   ViewsheetSessionService sessions,
                                   ViewsheetReadService readService,
                                   ViewsheetEditService editService,
                                   ViewsheetFormatService formatService,
                                   ScriptImageService imageService,
                                   AssemblyPropertyService propertyService,
                                   ViewsheetService viewsheetService,
                                   SheetAgentBroadcastService broadcast)
   {
      this.feature = feature;
      this.joinService = joinService;
      this.sessionService = sessionService;
      this.sessions = sessions;
      this.readService = readService;
      this.editService = editService;
      this.formatService = formatService;
      this.imageService = imageService;
      this.propertyService = propertyService;
      this.viewsheetService = viewsheetService;
      this.broadcast = broadcast;
   }

   public record JoinRequest(String code) {}
   public record JoinResponse(String sessionToken, String runtimeId, String ownerIdentity) {}

   @PostMapping("/api/wiz/v1/agent/viewsheet/join")
   public JoinResponse join(@RequestBody JoinRequest body, Principal user) throws PairingException {
      requireEnabled();
      JoinSession session = joinService.join(body.code(), user);
      return new JoinResponse(session.sessionToken(), session.runtimeId(), session.ownerIdentity());
   }

   @GetMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/model")
   public ViewsheetModel model(@PathVariable String sessionToken, Principal user)
      throws PairingException
   {
      requireEnabled();
      return readService.read(sessions.resolve(sessionToken, user));
   }

   @PostMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/edit")
   public void edit(@PathVariable String sessionToken,
                    @RequestBody EditRequest request,
                    @RequestParam(required = false, defaultValue = "") String linkUri,
                    Principal user)
      throws Exception
   {
      requireEnabled();
      editService.apply(sessionToken, user, request, linkUri);
   }

   @PostMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/format")
   public void format(@PathVariable String sessionToken,
                      @RequestBody ViewsheetFormatService.FormatRequest request,
                      @RequestParam(required = false, defaultValue = "") String linkUri,
                      Principal user)
      throws Exception
   {
      requireEnabled();
      formatService.setFormat(sessionToken, user, request, linkUri);
   }

   public record ImageResponse(String image, String format, int width, int height, String note) {}

   @GetMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/image")
   public ImageResponse image(@PathVariable String sessionToken,
                              @RequestParam(required = false) String target,
                              @RequestParam(required = false) Integer width,
                              @RequestParam(required = false) Integer height,
                              Principal user)
      throws Exception
   {
      requireEnabled();
      RuntimeViewsheet rvs = sessions.resolve(sessionToken, user);
      ScriptImageService.ChartImage image = target == null || target.isBlank()
         ? imageService.getViewsheetImage(rvs, width, height, user)
         : imageService.getAssemblyImage(rvs, target, width, height, user);

      return new ImageResponse(Base64.getEncoder().encodeToString(image.pngBytes()),
                               image.isPng() ? "png" : "svg",
                               image.width(), image.height(), image.note());
   }

   public record PropertyPatchRequest(String assembly, Map<String, Object> properties) {}

   @GetMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/properties/list")
   public Map<String, Object> listAssemblyProperties(@PathVariable String sessionToken,
                                                     @RequestParam String assembly,
                                                     Principal user)
      throws Exception
   {
      requireEnabled();
      return propertyService.list(sessionToken, user, assembly);
   }

   @GetMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/properties")
   public Object getAssemblyProperties(@PathVariable String sessionToken,
                                       @RequestParam String assembly,
                                       @RequestParam(required = false) boolean raw,
                                       Principal user)
      throws Exception
   {
      requireEnabled();
      return propertyService.get(sessionToken, user, assembly, raw);
   }

   @PostMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/properties")
   public void setAssemblyProperties(@PathVariable String sessionToken,
                                     @RequestBody PropertyPatchRequest request,
                                     @RequestParam(required = false, defaultValue = "") String linkUri,
                                     Principal user)
      throws Exception
   {
      requireEnabled();
      propertyService.set(sessionToken, user, request.assembly(), request.properties(), linkUri);
   }

   @PostMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/save")
   public void save(@PathVariable String sessionToken, Principal user) throws PairingException {
      requireEnabled();
      RuntimeViewsheet rvs = sessions.resolve(sessionToken, user);
      AssetEntry entry = rvs.getEntry();

      if(entry.getScope() == AssetRepository.TEMPORARY_SCOPE) {
         throw new PairingException(
            "Viewsheet is unsaved (\"" + entry.toView() + "\"). Save it with a name in the " +
            "StyleBI Composer first — there is no save-as through this tool — then call " +
            "save_viewsheet again.");
      }

      if(!(user instanceof XPrincipal xp)) {
         throw new PairingException("Cannot save: agent principal is not an XPrincipal (" +
                                    user.getClass().getName() + ")");
      }

      try {
         viewsheetService.setViewsheet(rvs.getViewsheet(), entry, xp, true, true);
         rvs.setSavePoint(rvs.getCurrent());
      }
      catch(Exception e) {
         throw new PairingException("Failed to save viewsheet: " + e.getMessage(), e);
      }

      broadcast.broadcastSave(rvs, rvs.getID(), user);
   }

   @PostMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/undo")
   public Map<String, Object> undo(@PathVariable String sessionToken, Principal user)
      throws Exception
   {
      requireEnabled();
      RuntimeViewsheet rvs = sessions.resolve(sessionToken, user);
      boolean undone = rvs.undo(null);
      broadcast.broadcastRefresh(rvs, SheetType.VIEWSHEET, rvs.getID(), user);
      return undoState("undone", undone, rvs);
   }

   @PostMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/redo")
   public Map<String, Object> redo(@PathVariable String sessionToken, Principal user)
      throws Exception
   {
      requireEnabled();
      RuntimeViewsheet rvs = sessions.resolve(sessionToken, user);
      boolean redone = rvs.redo(null);
      broadcast.broadcastRefresh(rvs, SheetType.VIEWSHEET, rvs.getID(), user);
      return undoState("redone", redone, rvs);
   }

   private static Map<String, Object> undoState(String key, boolean applied, RuntimeViewsheet rvs) {
      Map<String, Object> state = new LinkedHashMap<>();
      state.put(key, applied);
      state.put("checkpoint", rvs.getCurrent());
      state.put("total", rvs.size());
      state.put("savePoint", rvs.getSavePoint());
      return state;
   }

   @PostMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/detach")
   public void detach(@PathVariable String sessionToken, Principal user) {
      sessionService.close(sessionToken);
   }

   private void requireEnabled() {
      if(!feature.isEnabled()) {
         throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                           "Sheet agent pairing is disabled");
      }
   }

   @ExceptionHandler(PairingException.class)
   public ResponseEntity<Map<String, String>> handlePairingException(PairingException e) {
      HttpStatus status = switch(e.getKind()) {
         case SESSION_EXPIRED -> HttpStatus.NOT_FOUND;
         case USER_MISMATCH, FEATURE_DISABLED -> HttpStatus.FORBIDDEN;
         case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
         case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
         default -> HttpStatus.BAD_REQUEST;
      };

      Map<String, String> body = new LinkedHashMap<>();
      body.put("error", e.getMessage());
      body.put("errorCode", e.getKind().name());
      return ResponseEntity.status(status).body(body);
   }

   private final SheetAgentFeature feature;
   private final SheetJoinService joinService;
   private final SheetSessionService sessionService;
   private final ViewsheetSessionService sessions;
   private final ViewsheetReadService readService;
   private final ViewsheetEditService editService;
   private final ViewsheetFormatService formatService;
   private final ScriptImageService imageService;
   private final AssemblyPropertyService propertyService;
   private final ViewsheetService viewsheetService;
   private final SheetAgentBroadcastService broadcast;
}
