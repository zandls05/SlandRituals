# SlandRituals

Altares mágicos para **Paper / Purpur 1.21.x** (Java 21): los jugadores entregan ofrendas, se reproduce una cinemática
de partículas, sonidos y títulos, y aparece un jefe. Todo se crea y edita desde menús en español.

## Instalación
1. Compila con `mvn -B clean package` (o usa el workflow de GitHub Actions) y copia `target/SlandRituals-1.0.0.jar` a `plugins/`.
2. Arranca el servidor: se generan `config.yml`, `messages.yml`, `animaciones.yml`, `rituals.yml` y `altars.yml`.
3. **Si actualizas desde la versión anterior**, borra `plugins/SlandRituals/config.yml` (los mensajes ahora están en `messages.yml`).
   Tu `rituals.yml` antiguo sigue funcionando (`countdown` se usa como duración de la carga).

Dependencias opcionales (no hacen falta): **MythicMobs** (jefes personalizados) y **PlaceholderAPI** (placeholders en títulos y mensajes).

## Comandos (`/rituales`, alias `/ritual`) — permiso `slandrituals.admin`
| Comando | Acción |
|---|---|
| `/rituales` | Abre el Archivo de Rituales |
| `/rituales listar` | Lista rituales, altares y estado |
| `/rituales crear <id>` | Crea un ritual y abre el editor |
| `/rituales editar <id>` | Abre el editor |
| `/rituales duplicar <id> <nuevo>` | Copia un ritual |
| `/rituales eliminar <id> confirmar` | Elimina el ritual y sus altares |
| `/rituales altar <id>` | Convierte en altar el bloque que miras |
| `/rituales quitaraltar` | Quita el altar que miras |
| `/rituales reiniciar` | Cancela y reinicia el altar que miras (devuelve ofrendas) |
| `/rituales probar <id>` | Reproduce la cinemática sin gastar ofrendas |
| `/rituales guardar` / `recargar` | Guarda / recarga todos los archivos |

Permiso `slandrituals.use` (por defecto, todos): hacer ofrendas en los altares.

## Menús
- **Archivo de Rituales**: lista paginada. Clic izquierdo = editar, clic derecho = duplicar, Shift + clic derecho = eliminar.
- **Editor** (9 categorías): información, altares, objeto de activación, jefe y aparición, animaciones, cinemáticas y sonidos,
  tiempos, condiciones, recompensas; más *Guardar*, *Probar* y *Cancelar cambios*.
- Los valores que no se eligen con clics se escriben **por chat** (se validan; escribe `cancelar` para salir).
  Shift + clic derecho en un campo lo restablece al valor por defecto.
- Los cambios se guardan en `rituals.yml` al instante; *Cancelar cambios* vuelve a como estaba al abrir el editor.

## Cinemática (4 fases, cada una se puede desactivar)
1. **Activación** → 2. **Carga** (cuenta atrás, sonido que sube de tono) → 3. **Portal** → *aparece el jefe* → 4. **Aparición** (título con el nombre del jefe, explosión final).

Animaciones: espiral arcana, círculo mágico, columnas de energía, pulso de energía, runas mágicas, vórtice de invocación,
sobrecarga arcana y explosión final. Partícula, color (`DUST`), cantidad, radio, velocidad, altura y frecuencia son configurables.

## Configuración
- `config.yml`: ajustes generales, integraciones y límites de rendimiento.
- `messages.yml`: todos los textos.
- `animaciones.yml`: valores por defecto de animaciones, sonidos y textos de cada fase.
- `rituals.yml`: rituales (cada uno puede sobrescribir lo anterior en su sección `cinematic:`).
- `altars.yml`: datos de los altares (ofrendas entregadas y enfriamientos persisten al reiniciar).

## Notas
- Las ofrendas se consumen al entregarlas, pero **antes** se comprueban permiso, hora y jugadores cercanos. Si el ritual se cancela
  o el jefe no puede aparecer, las ofrendas caen junto al altar (`settings.refund-on-abort`).
- Un altar no admite ofrendas mientras otro ritual está en marcha en él (sin activaciones simultáneas).
- **MythicMobs**: se invoca por reflexión. Si no está o falla, se usa el jefe vanilla (`boss.type`) y se avisa en consola.
  Con MythicMobs pon `rewards.vanilla-drops: true` para conservar sus drops; vida y daño de `rituals.yml` solo afectan a jefes vanilla.

## Limitaciones conocidas
- Las ofrendas se identifican por material (no por nombre ni etiquetas persistentes); está previsto como mejora.
- No se registran placeholders propios de PlaceholderAPI (solo se resuelven los de otros plugins en los textos).
- Los altares son bloques vanilla; no hay integración con bloques de plugins de objetos personalizados.
- Esta versión se ha revisado compilando contra una API simulada, **pero no se ha probado en un servidor real**: pueden aparecer fallos de ejecución.
