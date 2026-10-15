# ProCampo

Sistema desktop de orçamentos agropecuários (JavaFX + SQLite).

## Executar (Windows, sem instalar Java)

1. Baixe `dist/ProCampo.zip` e extraia.
2. Execute `ProCampo/ProCampo.exe`.

O banco `orcamentos.db` fica na mesma pasta do executável.

## Estrutura

```
ProCampo/
├── dist/                    # Aplicativo pronto (app-image Windows + .zip)
├── Documentaçao/            # Planilhas e requisitos (seed do banco)
├── src/main/java/com/procampo/
│   ├── controller/          # Controllers JavaFX
│   ├── dao/                 # Acesso a dados
│   ├── db/                  # Conexão, criação e seed do SQLite
│   ├── model/               # Entidades
│   └── util/                # Exportação PDF
├── src/main/resources/
│   ├── css/
│   └── fxml/
├── pom.xml
└── mvnw.cmd                 # Maven Wrapper
```

## Desenvolvimento

Requisitos: JDK 21.

```powershell
# Rodar
.\mvnw.cmd javafx:run

# Gerar fat jar (target/procampo-desktop-1.0.0.jar)
.\mvnw.cmd clean package
```

Na primeira execução sem banco, os dados iniciais são importados de
`Documentaçao/ORÇAMENTOS CENOP BNB_atualizado.xlsm`.

### Gerar o `dist`

```powershell
.\mvnw.cmd clean package
New-Item -ItemType Directory -Force build\input | Out-Null
Copy-Item target\procampo-desktop-1.0.0.jar build\input\

jlink --add-modules java.base,java.desktop,java.scripting,java.security.jgss,java.sql,java.xml,java.xml.crypto,java.logging,java.naming,java.management,jdk.jfr,jdk.unsupported,jdk.charsets,jdk.localedata,jdk.crypto.ec,jdk.zipfs `
      --strip-debug --no-header-files --no-man-pages --compress=zip-9 --output build\runtime

jpackage --type app-image --name ProCampo --input build\input `
         --main-jar procampo-desktop-1.0.0.jar --main-class com.procampo.Launcher `
         --runtime-image build\runtime --app-version 1.0.0 --dest dist
```
