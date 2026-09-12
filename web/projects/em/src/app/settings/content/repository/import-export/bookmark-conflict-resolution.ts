export interface BookmarkConflictResolution {
   viewsheetPath: string;
   user: string;
   bookmarkName: string;
   /** true = imported (JAR) version wins; false = existing (current) version is kept. */
   keepImported: boolean;
}
