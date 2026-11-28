package net.augustus.changelog;

public class ChangelogEntry {
   private String[] lines;
   private final String rawText;
   private final ChangelogEntryType type;

   public ChangelogEntry(String[] strings, ChangelogEntryType changelogEntryType) {
      this.lines = strings != null ? strings : new String[0];
      this.rawText = (this.lines.length > 0 && this.lines[0] != null) ? this.lines[0] : "";
      this.type = changelogEntryType;
   }

   public String[] getLines() {
      return this.lines;
   }

   public void setLines(String[] strings) {
      this.lines = strings != null ? strings : new String[0];
   }

   public String getRawText() {
      return this.rawText;
   }

   public ChangelogEntryType getType() {
      return this.type;
   }
}
