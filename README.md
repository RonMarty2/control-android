# Control Remoto (Android)

App de control remoto universal para Android. Contempla dos mundos:

1. **Infrarrojo (IR)**: para TVs y aparatos "de toda la vida" que se manejan con control remoto infrarrojo (aires acondicionados, equipos de audio, TVs viejos, etc). **Solo funciona si tu celular tiene un emisor IR físico** (ver más abajo).
2. **Wi-Fi / red local**: para Smart TVs y TV boxes conectados a tu red, con soporte real para:
   - **Roku** (TVs Roku y TVs con Roku TV integrado)
   - **LG Smart TV (webOS)**
   - **Samsung Smart TV (Tizen)**
   - **Wake-on-LAN**: para encender por red cualquier equipo que lo soporte (PCs, algunos TV box, NAS, etc)

No sabés nada de Android ni de programación, así que esta guía asume eso: vamos paso a paso, desde instalar el programa hasta probar la app en tu celular.

---

## 0. Qué vas a necesitar

- **Una computadora** (Windows, Mac o Linux). Una app de Android **no se puede compilar solo desde el celular**, hace falta una PC con Android Studio.
- **Tu celular Android**, con cable USB (o en la misma red Wi-Fi que la PC si preferís instalar sin cable).
- Conexión a internet en la PC (Android Studio descarga varios componentes la primera vez).

### Sobre el infrarrojo (IR): leé esto antes de ilusionarte

La mayoría de los celulares modernos **NO tienen** emisor de infrarrojo físico (Apple nunca lo puso, y la mayoría de Samsung/Google/Motorola tampoco desde hace años). Algunos modelos Xiaomi, POCO, Huawei y algún Samsung viejo sí lo tienen (buscá "blaster IR" o "control remoto" en las specs de tu modelo). La app detecta automáticamente si tu celular tiene esta función y te avisa si no la tiene — en ese caso, la parte de infrarrojo simplemente no te va a servir, pero toda la parte de Wi-Fi funciona en cualquier celular.

---

## 1. Instalar Android Studio

1. Andá a https://developer.android.com/studio
2. Descargá el instalador para tu sistema operativo y ejecutalo.
3. Dejá todas las opciones por defecto en el instalador ("Standard" install type).
4. La primera vez que lo abras, va a descargar el SDK de Android (puede tardar varios minutos, necesita internet).

---

## 2. Abrir este proyecto

1. Descargá o cloná este repositorio en tu PC.
2. Abrí Android Studio.
3. Elegí **"Open"** (u "Open an existing project") y seleccioná la carpeta `control-android` (la carpeta que tiene el archivo `settings.gradle.kts` adentro).
4. Al abrirlo por primera vez, es posible que te aparezca un aviso tipo *"Gradle wrapper not found"* — aceptá que Android Studio lo genere automáticamente (o hacé clic en "OK"/"Sync now" si te lo pregunta). Es un paso automático, no tenés que hacer nada más.
5. Esperá a que termine el **"Gradle Sync"** (barra de progreso abajo). La primera vez puede tardar bastante porque descarga dependencias.

Si Gradle Sync se queja de que falta un SDK o "Build Tools", Android Studio te va a mostrar un link tipo "Install missing SDK package(s)" — hacé clic y dejá que se instale.

---

## 3. Preparar tu celular para probar la app

1. En tu celular, andá a **Ajustes > Acerca del teléfono**.
2. Buscá **"Número de compilación"** (o "Número de versión") y tocalo **7 veces seguidas**. Te va a avisar "Ya sos desarrollador".
3. Volvé a Ajustes, ahora vas a ver una opción nueva: **"Opciones de desarrollador"** (a veces dentro de "Sistema").
4. Entrá y activá **"Depuración USB" (USB Debugging)**.

---

## 4. Ejecutar la app en tu celular

### Opción A: con cable USB (la más fácil para probar)

1. Conectá el celular a la PC con el cable USB.
2. En el celular va a aparecer un mensaje "¿Permitir depuración USB?" — aceptalo (marcá "Confiar siempre en esta computadora" si querés que no te pregunte de nuevo).
3. En Android Studio, arriba vas a ver un desplegable de dispositivos (al lado del botón ▶ verde). Elegí tu celular de la lista.
4. Apretá el botón ▶ verde ("Run 'app'"). Android Studio va a compilar la app e instalarla automáticamente en tu celular. La primera compilación puede tardar unos minutos.
5. La app se abre sola en el celular cuando termina.

### Opción B: generar el instalador (APK) y pasarlo al celular

Útil si no tenés cable a mano, o querés compartir la app con otra persona.

1. En Android Studio: menú **Build > Build App Bundle(s) / APK(s) > Build APK(s)**.
2. Cuando termine, te va a aparecer una notificación abajo a la derecha con un link **"locate"** — te lleva a la carpeta donde quedó el archivo `app-debug.apk` (normalmente en `app/build/outputs/apk/debug/`).
3. Pasá ese archivo `.apk` a tu celular (por cable, WhatsApp, Drive, lo que sea).
4. En el celular, abrí el archivo `.apk` con el explorador de archivos. Te va a pedir permiso para **"instalar apps de origen desconocido"** — aceptalo (es normal, pasa con cualquier app que no venga de Google Play).
5. Instalá y abrí la app.

---

## 5. Cómo se usa la app (menú y pantallas)

### Pantalla principal ("Mis dispositivos")

Lista de todos los controles que guardaste. Tocá cualquiera para abrir su control remoto. El botón de basurita lo borra. El botón **+** (abajo a la derecha) agrega un dispositivo nuevo.

### Agregar dispositivo

1. Elegí el **tipo**: Infrarrojo, Roku, LG Smart TV, Samsung Smart TV, o "Encender por red" (Wake-on-LAN).
2. Ponele un nombre (ej: "TV del living").
3. Según el tipo:
   - **Roku / LG / Samsung**: necesitás la IP del TV en tu red Wi-Fi. Tocá **"Buscar en mi red Wi-Fi"** y la app va a rastrear tu red local buscando estos equipos automáticamente (tarda unos segundos); tocá el resultado para usarlo. Si no aparece nada, podés escribir la IP a mano (la encontrás en el menú de Ajustes de red del TV, normalmente en "Ajustes > General > Red").
   - **Wake-on-LAN**: necesitás la dirección **MAC** del equipo a encender (la encontrás en la configuración de red del dispositivo). Ese dispositivo tiene que tener Wake-on-LAN habilitado y estar conectado por cable o con esa función soportada por Wi-Fi.
   - **Infrarrojo**: solo el nombre; los códigos de cada botón se cargan después, dentro del control.
4. Tocá **Guardar**.

### Pantalla de control remoto

- **Roku / LG / Samsung**: cruceta de navegación (flechas + OK), volumen, mute, canal, encendido, home, atrás, play/pausa.
  - **LG y Samsung**: la primera vez que aprietes un botón, **en la pantalla del TV** va a aparecer un mensaje pidiendo permiso ("¿Permitir esta conexión?") — aceptalo ahí, en el control del TV o con el remoto físico. Una vez aceptado, la app lo recuerda y no te lo vuelve a pedir.
  - **Roku**: no pide ningún permiso, funciona directo.
- **Wake-on-LAN**: un solo botón para encender el equipo.
- **Infrarrojo**: botones de Encendido, Volumen +/-, Canal +/-, Mute. Si un botón dice "sin código", tocá "Editar código" para cargarlo (ver siguiente sección).

---

## 6. Cargar códigos infrarrojos (solo para IR)

Esta app **no trae una base de datos de miles de controles** cargada de fábrica (eso requeriría descargar códigos de miles de marcas y modelos, algo que queda fuera de este proyecto inicial). En cambio, usa el protocolo estándar **NEC** y vos cargás el código de **dirección** y **comando** de tu control original, en hexadecimal, con el formato:

```
direccion,comando
```

Por ejemplo `07,02`.

¿Cómo conseguís esos códigos?
- Buscá en internet `"[marca] [modelo] NEC IR codes"` o `"[marca] IR protocol codes"`.
- Bases de datos abiertas como el proyecto **LIRC** (`lirc-remotes`) tienen configuraciones de miles de controles con estos valores.
- Si tu control es de una marca común (Samsung, LG, Sony, etc.), buscar "[marca] NEC address" suele traer resultados rápido.

Una vez que tengas el par dirección/comando de cada botón (power, volumen, etc.), cargalos desde la pantalla de control tocando "Editar código" en cada botón.

---

## 7. Notas técnicas y limitaciones (para tener en cuenta)

- **Todo el control por Wi-Fi funciona solo dentro de tu misma red local** (el celular y el TV conectados al mismo router/Wi-Fi). No es control remoto por internet.
- **Samsung**: la conexión usa un certificado autofirmado propio del TV (así lo implementa Samsung); la app confía en ese certificado únicamente para conectarse al TV en tu red local, es el mismo enfoque que usan las apps de control remoto de código abierto para Samsung.
- **LG**: algunos modelos webOS más nuevos podrían requerir ajustes adicionales de emparejamiento no cubiertos en esta primera versión; si el emparejamiento falla, avisá para agregar soporte específico de tu modelo.
- El buscador de red asume una red hogareña típica (rango 192.168.x.0/24); si tu router usa otro esquema, cargá la IP a mano.
- El proyecto no incluye el archivo binario del "Gradle Wrapper" (`gradle-wrapper.jar`) porque el entorno donde se generó este proyecto no tenía acceso a internet para descargarlo. **No afecta en nada tu compilación**: Android Studio lo genera solo la primera vez que abrís el proyecto (paso 2.4 de esta guía).

---

## 8. Estructura del proyecto (por si en algún momento programás vos)

```
app/src/main/java/com/rnd/remoto/
├── MainActivity.kt              punto de entrada de la app
├── data/                        modelo de datos y guardado de dispositivos
├── ir/                          protocolo NEC + envío por infrarrojo
├── network/                     clientes Roku, LG, Samsung, Wake-on-LAN, escaneo de red
└── ui/                          pantallas (Compose): lista, agregar, control remoto
```
