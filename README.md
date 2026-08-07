# EventosFamiliaresTV

Aplicación Android para teléfonos, tablets, Android TV y TV Box. Muestra seis canales configurados desde Firebase Realtime Database y reproduce cada señal con LibVLC.

## Compatibilidad

- Android 5.0 (API 21) o posterior.
- Pantalla táctil y control remoto/D-pad.
- Reproducción horizontal a pantalla completa.
- LibVLC 3.6.5 para enlaces HTTP/HTTPS, incluidos `.m3u8` y `.ts` compatibles.

## Configuración de cada canal

En Firebase Realtime Database, cada nodo `canales/canal1` a `canales/canal6` contiene:

```json
{
  "nombre": "Canal 1",
  "url": "https://servidor/stream.m3u8",
  "activo": true,
  "claveHash": "HASH_SHA_256"
}
```

1. Abre `herramientas/generar-clave.html` en un navegador.
2. Escribe la clave del canal y genera el hash.
3. Copia el resultado en `claveHash`.
4. Agrega el enlace en `url` y cambia `activo` a `true`.

No escribas la clave normal en Firebase. Esta validación es una barrera de acceso local, no un sistema de DRM: una persona con conocimientos técnicos podría analizar la APK o su tráfico. Para protección fuerte se necesita validación en servidor y enlaces temporales.

## Reglas de Realtime Database

```json
{
  "rules": {
    "canales": {
      ".read": true,
      ".write": false
    }
  }
}
```

## Generar APK en GitHub

1. Sube el contenido de esta carpeta a un repositorio privado de GitHub.
2. Abre **Actions** y selecciona **Generar APK**.
3. Presiona **Run workflow**.
4. Descarga `EventosFamiliaresTV-APK` cuando termine.

El APK de prueba estará dentro del ZIP descargado como `app-debug.apk`.
