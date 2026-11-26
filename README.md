# Augustus Redesing

Rediseño visual del cliente **Augustus** para **Minecraft 1.8.9**.

Esta build mantiene **Java 8, Minecraft 1.8.9 y LWJGL 2** exactamente como el cliente original, pero
reemplaza toda la capa de presentación por una interfaz de cristal sólido: paneles oscuros, superficies
con desenfoque real, tipografía MSDF e iconos vectoriales.

<p align="center">
  <img src="assets/ss.png" alt="Augustus Redesing" width="640" />
</p>

---

## Créditos

El cliente **Augustus** no es mío: viene de **https://github.com/Skid-Technology/OpenAugustus**, que es la
base directa de este rediseño. Todo el mérito del cliente original (módulos, sistema de configuración,
gestión de eventos, hacks) es de su autor y colaboradores.

- **Origen directo de esta build:** [Skid-Technology/OpenAugustus](https://github.com/Skid-Technology/OpenAugustus)
- **Origen original del cliente Augustus:** [yxzeroo/openaugustus](https://github.com/yxzeroo/openaugustus) (así lo acredita el README de OpenAugustus)
- Comunidad del autor original: https://discord.gg/skidsolutions

Este repositorio es un **derivado** de OpenAugustus. El rediseño es **exclusivamente visual**: no se ha
alterado la lógica de ningún módulo, ni los ajustes, ni el formato de configuración.

Minecraft es una marca de Mojang Studios. Este proyecto no está afiliado ni respaldado por Mojang.

---

## Requisitos

| Uso | Necesitas |
| --- | --- |
| **Lanzador del release** (recomendado) | Windows + **Java 8** (1.8.0_xxx). Nada más. |
| **Ejecutar desde el código** | **JDK 21 o 17** para el wrapper de Gradle (8.14.3) + un **JDK 8** para la toolchain que compila y ejecuta el cliente + internet la primera vez |
| **Compilar** | Lo mismo que arriba |

Gradle usa una *toolchain* de **Java 8** para compilar y **ejecutar** el cliente: Minecraft 1.8.9 no
funciona en JVM modernas. El JDK 21 que uses para Gradle es solo el motor del build.

---

## Cómo ejecutar Augustus Redesing

### Opción 1 — Lanzador incluido (un clic)

El release trae un ZIP con el lanzador, los nativos de LWJGL y el cliente ya compilado:

1. Descarga el ZIP del release y descomprímelo (por ejemplo en `C:\AugustusRedesing`).
2. Doble clic en **`Iniciar-Augustus-Redesing.bat`**.
3. El lanzador busca Java 8, avisa qué modo va a usar y arranca el cliente.

El lanzador funciona en dos modos y elige solo:

- **Modo jar**: si encuentra `openaugustus-*-all.jar` junto a él (o en `build/libs/`), lo ejecuta con los
  nativos en `-Djava.library.path`.
- **Modo código fuente**: si encuentra `gradlew.bat` cerca, ejecuta `gradlew runClient`, que extrae los
  nativos y descarga los assets de Minecraft 1.8 automáticamente.

### Opción 2 — Desde el código fuente (lo que se usa en desarrollo)

```powershell
cd OpenAugustus
.\gradlew.bat runClient
```

Eso crea `run/`, extrae los nativos de LWJGL/OpenAL y descarga los assets de Minecraft 1.8 la primera vez.

¿Prefieres usar tu propio `.minecraft` (tus opciones, tus assets, tu cuenta ya configurada)?

```powershell
.\gradlew.bat runClientMinecraftDir
```

### Opción 3 — Con el jar ya compilado

```powershell
# El fat jar trae todas las dependencias; necesita los nativos y una carpeta con assets de 1.8
java "-Djava.library.path=natives" "-Dorg.lwjgl.librarypath=natives" ^
     "-Dnet.java.games.input.librarypath=natives" -Xms512m -Xmx2g ^
     -jar openaugustus-b4.23.3-all.jar
```

La clase `Start` del fat jar resuelve los assets en este orden: `assets/`, luego `run/assets/`, luego el
`.minecraft` del sistema.

### Opción 4 — Desde un IDE

Ejecuta directamente `src/main/java/Start.java` (o `StartMinecraftDirectory.java` para usar tu
`.minecraft`). Recuerda añadir `build/natives` a los *VM options* como `java.library.path`.

---

## Abrir la ClickGUI en el juego

- **RSHIFT** abre la ClickGUI (es el keybind por defecto del módulo `ClickGui`).
- **RSHIFT otra vez**, **ESC** o la **X** de la barra de título la cierran, con su animación de salida.
- El conmutador de escala de la interfaz está dentro de la propia GUI: `ClickGui → General → GUI Scale`.

---

## Cómo compilar

```powershell
cd OpenAugustus
.\gradlew.bat clean build      # build/libs/openaugustus-b4.23.3.jar
.\gradlew.bat fatJar           # build/libs/openaugustus-b4.23.3-all.jar  (todo incluido, es el del release)
.\gradlew.bat compatibilityJar # variante para JRE 8 con políticas JCE restringidas
```

---

## Qué cambia respecto al cliente original

Solo la capa de presentación. En concreto:

- **Cristal sólido**: superficies oscuras con desenfoque **gaussiano multipaso real** (pirámide Kawase)
  sobre *framebuffers*, no una transparencia falsa.
- **Composición con framebuffer**: las regiones con scroll se capturan fuera de pantalla y se componen
  con una máscara de desvanecido, para que el degradado sea correcto a resolución nativa.
- **Tipografía MSDF** (inter) e **iconos Tabler** cargados como SVG y subidos a textura dinámica.
- **Una sola ventana**: el sidebar es una zona interna del mismo contenedor, no un panel independiente.
- **Animaciones de apertura y cierre** con envolvente exponencial, sin saltos ni parpadeos.
- **Escala de GUI** como zoom interno del contenido, con recorte y scroll correctos.

Lo que **no** cambia: la lógica de los módulos, los valores y nombres de los ajustes, los keybinds, el
formato de los ficheros de configuración y las claves persistidas (`X`, `Y`, `Width`, `Height`,
`WindowX`, `WindowY`, `WindowWidth`, `WindowHeight`, `Visible`, `FullScreen`).

---

## Estructura

```
OpenAugustus/
├── launcher/          # lanzador de Windows (Iniciar-Augustus-Redesing.bat + launcher.ps1)
├── src/main/java/     # código del cliente (net/augustus/** es el cliente; net/minecraft/** la base 1.8.9)
├── tools/             # scripts de verificación usados durante el desarrollo
└── build.gradle       # tareas: runClient, runClientMinecraftDir, fatJar, compatibilityJar
```
