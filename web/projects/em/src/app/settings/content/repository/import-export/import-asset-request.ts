import { BookmarkConflictResolution } from "./bookmark-conflict-resolution";

export interface ImportAssetRequest {
   /** Indices of dependent assets to exclude from import. */
   ignoreList: string[];
   /**
    * Admin resolutions for conflicting bookmarks. Only entries where keepImported=false
    * need to be included — absent entries default to import-wins.
    */
   bookmarkResolutions: BookmarkConflictResolution[];
}
