# Correção do painel flutuante

- Removidos do APK os assets antigos de `texture_packs` (12 pacotes) e a tela/botão antigo de injeção de textura.
- A aba `TEXTURAS` do painel flutuante agora contém a ação `APLICAR HOLOGRAMA` para o Free Fire MAX.
- O painel envia uma ação interna para a MainActivity; a MainActivity reutiliza a validação da Key, verificação do FF MAX, extração dos assets `max_hologram` e cópia via Shizuku já existente.
- O status/log da cópia é enviado de volta ao painel.
- O painel é executado como foreground service e a permissão `SYSTEM_ALERT_WINDOW` foi declarada.
- A aba `FAKE LAG` mantém o botão de fantasma para pausar/retomar a internet via `VpnService`.
- A aba `BYPASS SS` neste build é somente uma área de diagnóstico/fechamento; não implementa evasão de captura de tela ou anti-cheat.

## Validação

- XMLs parseados com sucesso.
- IDs de recursos referenciados por `MainActivity` conferidos contra `activity_main.xml`.
- Busca global confirmou ausência dos antigos assets e controles `texture_packs`, `INJETAR TEXTURA`, `btnInjectTexture` e `screenTextures`.
- O Gradle não pôde compilar neste ambiente: o wrapper tentou baixar Gradle 8.2.1 e a conexão DNS com `services.gradle.org` falhou. A compilação final precisa ser confirmada no GitHub Actions.
