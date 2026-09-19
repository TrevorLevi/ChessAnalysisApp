# Le pont JNI vers Stockfish est appele depuis le code natif : on garde les noms.
-keep class com.chessforge.engine.sf.NativeBridge { *; }
-keepclasseswithmembernames class * { native <methods>; }

# Les classes du moteur integre sont fortement sollicitees : pas d'obfuscation agressive.
-keep class com.chessforge.chess.** { *; }
