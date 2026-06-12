export interface BookmarkConflict {
   viewsheetPath: string;
   user: string;
   bookmarkName: string;
   /** Epoch millis from VSBookmarkInfo.getCreateTime() for the existing (current) bookmark. -1 if unavailable. */
   existingCreated: number;
   /** Epoch millis from VSBookmarkInfo.getLastModified() for the existing (current) bookmark. */
   existingModified: number;
   /** Server-formatted display string for existingModified. */
   existingModifiedLabel: string;
   /** Epoch millis from VSBookmarkInfo.getCreateTime() for the imported bookmark. -1 if unavailable. */
   importedCreated: number;
   /** Epoch millis from VSBookmarkInfo.getLastModified() for the imported bookmark. -1 if unavailable. */
   importedModified: number;
   /** Server-formatted display string for importedModified. */
   importedModifiedLabel: string;
}
