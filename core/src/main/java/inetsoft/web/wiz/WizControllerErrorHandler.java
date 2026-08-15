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
package inetsoft.web.wiz;

import inetsoft.util.Catalog;
import inetsoft.util.InvalidUserException;
import inetsoft.web.wiz.service.UnsupportedDatasourceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps authorization denials from the wiz (AI composer agent) controllers to HTTP 403, mirroring
 * {@code ComposerControllerErrorHandler} for the standard composer. Without this the
 * {@code SecurityException}s thrown by the wiz permission gates would fall through unhandled to
 * {@code GlobalExceptionHandler} — which does not handle {@code SecurityException} — and surface as
 * a generic 500 instead of a 403.
 *
 * <p>Scoped to {@code inetsoft.web.wiz} so it covers every wiz controller. The two controllers in
 * this package that carry their own catch-all {@code @ExceptionHandler(Exception.class)}
 * ({@code WorksheetTableController}, {@code DatasourceMetaApiController}) each declare a more
 * specific local {@code SecurityException} handler, since a local handler takes precedence over a
 * {@code @ControllerAdvice} and their catch-all would otherwise intercept the denial first (as 400).
 */
@ControllerAdvice(basePackages = "inetsoft.web.wiz")
public class WizControllerErrorHandler {
   @ExceptionHandler({ inetsoft.sree.security.SecurityException.class, java.lang.SecurityException.class })
   public ResponseEntity<Map<String, String>> handleSecurityException(Exception e) {
      LOG.warn("Unauthorized wiz access: {}", e.getMessage());

      Map<String, String> payload = new HashMap<>();
      payload.put("error", "Forbidden");
      payload.put("message", Catalog.getCatalog().getString("http.error.unauthorized"));
      return new ResponseEntity<>(payload, null, HttpStatus.FORBIDDEN);
   }

   // getJDBCDatasource() (MetadataApiService) is called from several wiz controllers/services
   // beyond DatasourceMetaApiController (which has its own local override of this same handler,
   // since a local @ExceptionHandler always wins over this ControllerAdvice — see that class).
   // Catching it here benefits WorksheetAgentController.addBoundTable specifically: that method
   // declares throws Exception with no catch of its own, so an UnsupportedDatasourceException
   // genuinely propagates here and gets the friendly 422.
   //
   // NOT every other getJDBCDatasource caller benefits, though — verify the actual call chain
   // before assuming one does. WorksheetGenerateController.generateWs() and
   // WorksheetTableController.createTables() both wrap their whole service call in their own
   // catch(Exception e) (the latter one layer deeper, inside WorksheetTableService's per-table
   // try/catch) and always return 200 with errorMessage set — this exception never reaches
   // either controller, let alone this advice. Making those paths return a real 422 would mean
   // changing that per-item/per-request error-handling design, not just adding a handler.
   @ExceptionHandler(UnsupportedDatasourceException.class)
   public ResponseEntity<Map<String, String>> handleUnsupportedDatasource(
      UnsupportedDatasourceException e)
   {
      LOG.warn("Unsupported datasource: {} ({})", e.getDatasourceName(), e.getDatasourceType());

      Map<String, String> payload = new HashMap<>();
      payload.put("error", e.getMessage());
      payload.put("datasourceType", e.getDatasourceType());
      return new ResponseEntity<>(payload, null, HttpStatus.UNPROCESSABLE_ENTITY);
   }

   /**
    * Maps an input-validation failure to 400 <em>carrying its message</em>.
    *
    * <p>The wiz agent services express every bad-request condition as an
    * {@code IllegalArgumentException} with a caller-facing message — ~75 sites in
    * {@code inetsoft.web.wiz.viewsheet} alone ({@code ViewsheetEditService},
    * {@code PropertyPath}, {@code ConditionVocabulary}, {@code DateComparisonService}, …). Without
    * this handler every one of them fell through as a generic 500 with the message discarded, so a
    * caller saw "Request failed with status code 500" for a fixable bad request and had no way to
    * learn which field was wrong. That made correct validation indistinguishable from a server bug.
    *
    * <p>The message goes in the {@code error} key deliberately: that is the field wiz-services'
    * {@code handleError} extracts, so the text reaches the agent rather than being replaced by a
    * bare status code.
    */
   @ExceptionHandler(IllegalArgumentException.class)
   public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
      LOG.warn("Invalid wiz request: {}", e.getMessage());

      Map<String, String> payload = new HashMap<>();
      payload.put("error", e.getMessage());
      return new ResponseEntity<>(payload, null, HttpStatus.BAD_REQUEST);
   }

   /**
    * Maps a stale pairing session to 409 with an instruction the agent can act on.
    *
    * <p>When the principal behind a pairing session no longer owns the runtime sheet — the user
    * logged out and back in, or the container restarted and they re-opened the sheet in a new
    * browser session — {@code WorksheetEngine.getSheet} throws {@code InvalidUserException}. It
    * arrived here as a bare 500 HTML page with no message, so an agent saw an opaque server error
    * and its only sensible response was to retry, which fails identically every time.
    *
    * <p>The failure is especially misleading because <em>reads keep working</em>: those resolve the
    * sheet through the pairing lookup, which does not check ownership, while every mutation goes
    * through {@code getSheet} and is refused. So the session reports healthy and renders images
    * right up to the first write.
    *
    * <p>The raw message names two client session ids and nothing else, which tells the agent
    * nothing — hence a rewritten message rather than a passthrough. Kept as its own status so
    * "re-pair" is distinguishable from the 400 that means "fix your input".
    */
   @ExceptionHandler(InvalidUserException.class)
   public ResponseEntity<Map<String, String>> handleInvalidUser(InvalidUserException e) {
      LOG.warn("Pairing session no longer owns the sheet: {}", e.getMessage());

      Map<String, String> payload = new HashMap<>();
      payload.put("error",
                  "This pairing session no longer owns the sheet — the StyleBI login that opened " +
                  "it has been replaced. Ask the user for a fresh pairing code and connect again. " +
                  "Reads may still succeed on this session; writes will not.");
      return new ResponseEntity<>(payload, null, HttpStatus.CONFLICT);
   }

   /**
    * Maps a capability that is declared but not wired up to 501, carrying its message.
    *
    * <p>Some tools exist ahead of the mechanism they need — {@code set_column_labels} is the
    * first: renaming a header requires a {@code TableDataPath} cell override that is not built
    * yet, and writing the label anywhere else stores it where nothing reads. Such a tool must
    * refuse rather than report a success that did not happen, and the refusal must not arrive as
    * a 500, which reads as a bug and invites a retry that will fail identically.
    *
    * <p>501 says the request was fine and the feature is absent — which is exactly what an agent
    * needs in order to surface a gap instead of working around it.
    */
   @ExceptionHandler(UnsupportedOperationException.class)
   public ResponseEntity<Map<String, String>> handleUnsupported(UnsupportedOperationException e) {
      LOG.warn("Unimplemented wiz capability: {}", e.getMessage());

      Map<String, String> payload = new HashMap<>();
      payload.put("error", e.getMessage());
      return new ResponseEntity<>(payload, null, HttpStatus.NOT_IMPLEMENTED);
   }

   /**
    * Maps a body the converter could not read to 400 <em>saying so</em>.
    *
    * <p>Spring retries an unmatched exception against its cause, so a {@code @JsonCreator} that
    * throws {@code IllegalArgumentException} is already answered with its message by the handler
    * above. A failure raised by <em>Jackson</em> is not: a field given the wrong shape produced a
    * bodyless 400, and the caller saw "Request failed with status code 400" and nothing else —
    * which is indistinguishable from a broken tool, and sends an investigation looking at the
    * server rather than at the request.
    *
    * <p>Jackson's own text is verbose and names internal classes, so only its first line is
    * passed on: it is the line that names the offending field, which is the part a caller can act
    * on.
    */
   @ExceptionHandler(HttpMessageNotReadableException.class)
   public ResponseEntity<Map<String, String>> handleUnreadableBody(
      HttpMessageNotReadableException e)
   {
      LOG.warn("Unreadable wiz request body: {}", e.getMessage());

      Throwable cause = e.getMostSpecificCause();
      String detail = cause == null || cause.getMessage() == null ? "" : cause.getMessage();
      int newline = detail.indexOf('\n');

      Map<String, String> payload = new HashMap<>();
      payload.put("error",
                  "The request body could not be read: " +
                  (newline < 0 ? detail : detail.substring(0, newline)));
      return new ResponseEntity<>(payload, null, HttpStatus.BAD_REQUEST);
   }

   private static final Logger LOG = LoggerFactory.getLogger(WizControllerErrorHandler.class);
}
