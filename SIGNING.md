# Assinatura do APK

O projeto valida o certificado do APK instalado pelo módulo `libintegrity`. O fingerprint público autorizado nesta branch é `5e7983afb37285390ab66a54a918990f6424bf6edef1116c10a0451d3025a622`. A nova chave foi gerada para substituir a chave anterior, cujo arquivo privado não estava disponível. Instalações assinadas pela chave antiga precisam ser removidas antes de instalar este APK.

O workflow `Build APK` publica `PrivBypass-APK-unsigned-debug`. Esse artefato do Actions usa a chave de depuração padrão e não deve ser instalado neste app protegido por verificação de assinatura. Para obter um APK válido, assine localmente com o arquivo privado `ghostpriv-release.jks` e o alias `ghostprivbypads`:

```sh
apksigner sign --ks ghostpriv-release.jks --ks-key-alias ghostprivbypads --out app-signed.apk app-debug.apk
apksigner verify --verbose --print-certs app-signed.apk
```

Confirme que o certificado exibido por `apksigner` tem o fingerprint indicado acima. A keystore e a senha devem ficar em backups privados e nunca ser adicionadas ao repositório ou a logs.
