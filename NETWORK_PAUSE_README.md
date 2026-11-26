# Ghost Priv — pausa local de internet

Esta atualização acrescenta um serviço `VpnService` local e um pequeno overlay flutuante.

- Depois da validação da Key, o app solicita a permissão de sobreposição e o consentimento VPN do Android.
- O overlay mostra um botão redondo com fantasma.
- Ao tocar, o serviço cria uma interface TUN local com rotas IPv4 e IPv6 padrão e não encaminha os pacotes: a internet fica bloqueada enquanto pausada.
- Ao tocar novamente, a interface TUN é fechada e o roteamento normal do Android é restaurado.
- A ação de limpeza/desinstalação também fecha o overlay e encerra o serviço de pausa.

**Limitação importante:** isto implementa um bloqueador local controlado pelo usuário usando a API `VpnService`; não é uma VPN comercial nem um túnel para servidor remoto. No estado liberado, o tráfego usa a conexão normal do Android. No estado pausado, o tráfego é descartado localmente. A aplicação não encaminha, inspeciona nem registra o conteúdo do tráfego.

## Validação

Os arquivos XML foram analisados e a estrutura do projeto foi preservada. A compilação Gradle não pôde ser executada neste ambiente porque o wrapper precisa baixar Gradle 8.2.1 de `services.gradle.org`, e o ambiente está sem acesso à internet. Portanto, compile no GitHub Actions antes de instalar no aparelho.
