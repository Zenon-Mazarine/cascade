# Cascade uses no reflection on its own classes. The status-bar helper reflects on a
# framework class (android.app.StatusBarManager), which R8 never renames.
# Play Billing ships its own consumer rules.
-keepattributes SourceFile,LineNumberTable
