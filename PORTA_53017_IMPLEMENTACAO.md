# Bloqueio seletivo da porta 53017

O botão fantasma foi alterado para instalar/remover regras de firewall via serviço Shizuku já presente no projeto.

- Bloqueia conexões TCP e UDP de saída destinadas à porta 53017.
- Bloqueia pacotes TCP e UDP de entrada cuja porta de origem seja 53017 (respostas dessas conexões).
- Aplica regras em IPv4 (`iptables`) e IPv6 (`ip6tables`).
- Não instala rotas padrão na VPN e não descarta o tráfego geral.
- Ao desligar o Fake Lag ou encerrar o overlay, tenta remover as regras.

Requer que o Shizuku esteja iniciado por Depuração sem fio, que este aplicativo tenha a permissão Shizuku e que a identidade `shell` do dispositivo tenha permissão `NET_ADMIN` para manipular as tabelas. Se o Android/OEM negar `iptables`/`ip6tables` ao shell, a interface exibirá a falha; parear o Shizuku, por si só, não concede `NET_ADMIN`.

As regras são voláteis e normalmente desaparecem após reinicialização do dispositivo.
