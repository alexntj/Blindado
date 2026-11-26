# Ghost Priv — atualização VPN e Bypass SS

## Alterações desta versão

- O controle VPN foi redesenhado como botão horizontal arredondado, com ícone de escudo à esquerda e estados visuais distintos para conectar e desconectar.
- Um atalho **BYPASS SS** está visível na tela inicial da VPN e abre a tela correspondente; o acesso pelo menu também foi mantido.
- Um novo toque no botão VPN solicita a ação `ACTION_STOP` ao `NetworkPauseVpnService`.
- A parada fecha o descritor TUN existente sem criar outro túnel, remove a notificação foreground, emite o estado desligado e encerra o serviço sem reinício sticky.
- Os controles de desligar a VPN e desinstalar o próprio aplicativo na tela Bypass SS foram mantidos.
- A validação de chave permanece no endpoint dedicado `https://ghostcheats.de.deplexo.com/api/fake-lag-holograma`, usando POST JSON e as regras de autorização documentadas no módulo `libauth`.

## Ajuste no overlay

- O botão Bypass SS foi removido da tela principal da VPN e adicionado dentro da aba **BYPASS SS** do painel flutuante. Ele encaminha para a ação já existente de encerrar a VPN e abrir a confirmação de desinstalação.
- A aba **TEXTURAS** continua chamando, via `ACTION_HOLOGRAM_INJECT`, o mesmo método `MainActivity.injectMaxHologram()` da tela original; o método valida Key, permissões, Shizuku e arquivos antes de copiar, e os logs/status são retransmitidos ao overlay.

## Build e validação

- `assembleRelease` concluído com sucesso usando Gradle e JDK 17.
- APK alinhado e assinatura verificada pelas ferramentas Android; certificado SHA-256: `bd68b288664443f4482ac295bf67dc774339d8f334697b51087e49d8383458a8`.
- O ZIP de código-fonte não inclui keystore, caches do Gradle, saídas de build ou APKs.

## Observação técnica

O serviço VPN é um controlador local: no modo normal, o TUN não instala rotas padrão e o tráfego segue pela rede normal; quando pausado, instala rotas IPv4/IPv6 no TUN para bloquear o tráfego localmente. Não é um provedor VPN remoto nem encaminha pacotes para um servidor.
