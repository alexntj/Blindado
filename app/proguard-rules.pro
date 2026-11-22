# Proteção do APK: R8 reduz e ofusca o código do cliente.
# Componentes referenciados pelo manifesto e pela API refletiva do Shizuku.
-keep class com.passador.replay.MainActivity { *; }
-keep class com.passador.replay.PrivilegedService { *; }
-keep class com.passador.replay.IPrivilegedService { *; }
-keep class rikka.shizuku.** { *; }

# Contratos AIDL usados pelo Binder do Shizuku.
-keep class **.IPrivilegedService$Stub { *; }
-keep class **.IPrivilegedService$Stub$Proxy { *; }

# Não imprimir avisos sobre APIs opcionais durante o shrink.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
