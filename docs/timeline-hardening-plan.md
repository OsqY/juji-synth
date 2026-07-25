# Timeline hardening plan

Este documento define el flujo de trabajo para convertir la Timeline en una base
verificable antes de añadir nuevas funciones visuales o de interacción.

## Reglas de ejecución

1. Partir exactamente desde `2b350d6`.
2. No modificar ni incluir en commits `app/src/main/cpp/oboe`.
3. Cada módulo produce un commit independiente.
4. No mezclar correcciones funcionales con mejoras visuales.
5. No avanzar al siguiente módulo si el módulo actual no pasa su validación.
6. Cada gesto completo genera una sola operación de undo/redo.
7. Los cálculos temporales usan ticks, steps u otra unidad musical interna.
8. Los píxeles se usan sólo para renderizado y detección de gestos.
9. Se mantiene la virtualización por viewport; no se recrea contenido con ancho total.
10. Al cerrar cada módulo se ejecuta:

```bash
./gradlew testDebugUnitTest
./gradlew compileDebugKotlin
./gradlew lintDebug
./gradlew assembleDebug
```

Si existe un dispositivo o emulador conectado, también:

```bash
./gradlew connectedDebugAndroidTest
```

## Módulo 0 — Congelar la línea base

### Objetivo

Confirmar el punto de partida y evitar que Oboe contamine los cambios.

### Trabajo

```bash
git checkout 2b350d6
git checkout -b feat/timeline-hardening
git status --short
git diff --submodule=log -- app/src/main/cpp/oboe
```

Ejecutar la validación actual y registrar los resultados en
`docs/timeline-validation-baseline.md`. Documentar que Oboe debe permanecer
fuera de todos los commits.

### Aceptación

- El proyecto compila desde `2b350d6`.
- Pasan los cuatro comandos de validación.
- No hay archivos de Oboe en staging.

### Commit

`docs(timeline): record validation baseline`

## Módulo 1 — Extraer el sistema de coordenadas

### Objetivo

Centralizar la matemática de tiempo, scroll, zoom y viewport en una abstracción
independiente de Compose.

### Diseño mínimo

```kotlin
data class TimelineTransform(
    val viewportWidthPx: Float,
    val horizontalScrollPx: Float,
    val pixelsPerBeat: Float,
    val zoom: Float,
    val density: Float,
)
```

Debe cubrir:

```kotlin
fun tickToViewportPx(tick: Long): Float
fun viewportPxToTick(x: Float): Long
fun durationToPx(durationTicks: Long): Float
fun pxToDuration(px: Float): Long
fun snapTick(tick: Long, snapDivision: SnapDivision): Long
fun clampTick(tick: Long): Long
fun visibleTickRange(): LongRange
fun zoomAroundAnchor(anchorViewportPx: Float, previousZoom: Float, newZoom: Float): TimelineTransform
```

Localizar y eliminar fórmulas duplicadas de clips, grid, ruler, playhead, taps,
resize, scroll y zoom. Los clips permanecen almacenados en unidades musicales,
nunca en píxeles.

### Aceptación

- Grid, ruler, playhead, clips y taps usan la misma transformación.
- La transformación no depende de Compose.
- No hay conversiones alternativas en componentes distintos.

### Commit

`refactor(timeline): centralize viewport coordinate transforms`

## Módulo 2 — Pruebas unitarias de coordenadas

### Objetivo

Detectar errores de columna, zoom, scroll y redondeo antes de las pruebas táctiles.

### Cobertura mínima

- Zoom: 25%, 50%, 100%, 200%, 300% y 500%.
- Densidades: 1.0, 2.0, 3.0 y 4.0.
- Viewports pequeños y grandes.
- Scroll al inicio, intermedio y final.
- Timeline larga.
- Clips de una celda y clips muy pequeños.
- Bordes izquierdo y derecho.

### Invariantes

```text
viewportPxToTick(tickToViewportPx(tick)) ≈ tick
```

Después de `zoomAroundAnchor`, el tick debajo del dedo permanece igual. Un tap
en el centro de una celda produce el tick de esa celda. Playhead y clip en el
mismo tick coinciden. Ningún caso produce infinito, overflow o ticks inválidos.

### Aceptación

- Las pruebas cubren zoom hasta 500% y varias densidades.
- Se prueba tap después de scroll y zoom.
- Se usa tolerancia sólo para redondeo de píxeles.

### Commit

`test(timeline): cover zoom scroll and coordinate mapping`

## Módulo 3 — Harness instrumentado de Compose

### Objetivo

Crear un escenario determinista para encontrar elementos reales de la Timeline.

### Tags requeridos

```text
timeline-viewport
timeline-grid
timeline-ruler
timeline-playhead
timeline-clip-{id}
timeline-clip-start-handle-{id}
timeline-clip-end-handle-{id}
timeline-delete-tool
timeline-pad-selector
timeline-pattern-selector
timeline-zoom-indicator
timeline-snap-indicator
```

El fixture tendrá viewport conocido, pads, patrones, clips cortos y largos,
filas distintas, playhead controlable, zoom y scroll configurables, y persistencia
en memoria.

### Pruebas iniciales

1. La Timeline aparece antes y después de la primera medición.
2. Grid, ruler y playhead son visibles.
3. Un clip visible aparece.
4. Un clip fuera del viewport puede quedar sin componer.
5. La Timeline nunca queda vacía durante la medición inicial.

### Aceptación

- Las pruebas no usan autosave ni datos reales.
- Los elementos principales se encuentran por tag.
- El fixture controla zoom, scroll y playhead.

### Commit

`test(timeline): add compose instrumentation harness`

## Módulo 4 — Validar zoom, scroll y playhead

### Objetivo

Cubrir los fallos de mayor riesgo sin cambiar todavía la ergonomía de gestos.

### Escenarios

- Pinch progresivo de 100% a 500% manteniendo visibles grid, ruler, clips y playhead.
- Scroll horizontal después de zoom alto sin viewport vacío.
- Zoom anclado al tick bajo el gesto.
- Playback simulado combinado con zoom y scroll.
- Tap después de zoom y scroll colocando el clip en la columna exacta.

### Restricción

Sólo se corrigen transformación, viewport o sincronización.

### Commit

`fix(timeline): stabilize zoom scroll and playhead mapping`

## Módulo 5 — Máquina de estados de gestos

### Objetivo

Evitar conflictos entre scroll, move, resize, delete, scrubber y pinch.

### Estados

```kotlin
sealed interface TimelineGestureState {
    data object Idle : TimelineGestureState
    data object Scrolling : TimelineGestureState
    data object Pinching : TimelineGestureState
    data class MovingClip(val clipIds: Set<String>) : TimelineGestureState
    data class ResizingStart(val clipId: String) : TimelineGestureState
    data class ResizingEnd(val clipId: String) : TimelineGestureState
    data class Deleting(val deletedIds: Set<String>) : TimelineGestureState
    data object Scrubbing : TimelineGestureState
}
```

### Prioridad y reglas

1. Pinch con dos dedos.
2. Delete activo.
3. Handle de resize.
4. Cuerpo de clip.
5. Scrubber/ruler.
6. Fondo para scroll o colocación.

Resize sólo comienza en handle; el cuerpo mueve. Un gesto no cambia de tipo.
Pinch cancela taps pendientes. Delete consume gestos sobre clips. El selector
horizontal no desplaza la Timeline. La operación se confirma al terminar el gesto.

### Pruebas

Drag sobre clip mueve; drag sobre handle redimensiona; drag sobre fondo desplaza;
pinch no mueve clips; Delete no inicia resize; touch slop separa tap de drag.

### Commit

`refactor(timeline): introduce explicit gesture state machine`

## Módulo 6 — Resize de clips

### Objetivo

Hacer el resize predecible, especialmente en clips pequeños.

### Trabajo

- Handles visuales independientes con zona táctil mínima.
- El cuerpo del clip no se amplía artificialmente.
- Tamaño mínimo musical, no mínimo en píxeles.
- Snap continuo durante resize.
- Preview sin mutar el clip hasta soltar.
- Feedback de inicio, final y duración musical.
- Validación repetida desde ambos extremos.

Todo gesto usa una sola transacción:

```kotlin
ResizeClipCommand(clipId, previousStart, previousDuration, newStart, newDuration)
```

### Commit

`feat(timeline): improve clip resize handles and feedback`

## Módulo 7 — Undo/redo transaccional

### Objetivo

Garantizar que operaciones complejas sean atómicas.

### Comandos

```text
AddClipCommand
MoveClipsCommand
ResizeClipCommand
DeleteClipsCommand
DuplicateClipsCommand
PasteClipsCommand
MuteClipsCommand
RestoreTrashClipCommand
```

Delete múltiple, movimiento de grupos, resize y restauración deben producir una
sola entrada de historial y conservar IDs, filas, ticks, duración, mute y fuente.
Cada comando debe probar `execute -> undo -> redo`. También probar secuencias mixtas.
Autosave ocurre al confirmar la transacción, no durante cada frame.

### Commit

`fix(timeline): make edit history operations atomic`

## Módulo 8 — Delete y Trash

### Objetivo

Cerrar los casos de clips pequeños y borrado por arrastre.

### Casos

Tap sobre clip normal, clip de una celda y clip menor que el dedo; drag sobre
varios clips; cruce repetido del mismo clip sin duplicar IDs; undo de borrado;
restauración, undo y redo de restauración; clips muteados o redimensionados.

Durante el drag se usa un `MutableSet<ClipId>`.

### Commit

`test(timeline): cover multi-delete and trash restoration`

## Módulo 9 — Aislar selector horizontal

### Objetivo

Evitar que pads/patrones interfieran con gestos de la Timeline.

### Validación

- Cinco opciones visibles y scroll propio.
- Scroll del selector no mueve Timeline y viceversa.
- Selección A1–A32 y P1–P16.
- Tap y scroll corto no seleccionan accidentalmente.
- Eventos consumidos sólo por la superficie que inició el gesto.

### Commit

`fix(timeline): isolate source selector gestures`

## Módulo 10 — Auto-scroll al mover clips

Implementar sólo después de estabilizar gestos normales. Crear zonas de borde,
desplazamiento gradual, velocidad proporcional a proximidad, límites y soporte
para filas y zoom alto. Resize no activa auto-scroll salvo decisión explícita.

### Commit

`feat(timeline): add edge auto-scroll while moving clips`

## Módulo 11 — Indicadores visuales

Implementar después de estabilizar la interacción: estado Delete, snap, zoom,
handles, contraste del playhead, compás/beat/subdivisión, preview y mute.
Los indicadores no deben interceptar gestos ni aumentar sustancialmente nodos.

### Commit

`feat(timeline): add editing state indicators`

## Módulo 12 — Matriz de dispositivos y densidades

Validar teléfono pequeño, estándar, alta densidad, tablet, portrait y landscape,
en mdpi, xhdpi, xxhdpi y xxxhdpi. En cada perfil probar apertura, creación,
zoom 500%, scroll, move, resize, delete, undo/redo, playback y selector.

Guardar evidencia en `docs/timeline-device-validation/` con perfil, densidad,
resolución, Android, resultado, capturas e incidencias.

### Commit

`test(timeline): add device density validation matrix`

## Módulo 13 — Rendimiento y recomposición

Medir clips compuestos, marcas de grid, recomposiciones durante playback,
asignaciones durante drag, objetos por frame, rangos visibles, lecturas de estado
y estabilidad de keys. Mantener margen pequeño de viewport y evitar reconstruir
controles no relacionados con playback.

### Commit

`perf(timeline): reduce viewport recomposition overhead`

## Módulo 14 — Documentación técnica

Actualizar `docs/pads-timeline-workflow.md` para describir el modelo viewport-based:

1. Estado musical de clips.
2. Transformación tick ↔ viewport.
3. Rango visible.
4. Virtualización.
5. Grid y ruler.
6. Playhead.
7. Zoom anclado.
8. Scroll.
9. Máquina de gestos.
10. Historial de comandos.
11. Autosave.
12. Restricciones conocidas.

Incluir este flujo:

```text
Timeline state
     |
     v
Visible tick range
     |
     +--> Grid marks
     +--> Ruler marks
     +--> Visible clips
     +--> Playhead viewport position
```

### Commit

`docs(timeline): document viewport-based rendering model`

## Módulo 15 — Cierre técnico

Crear el PR sólo cuando los módulos estén completos. Incluir problema original,
arquitectura viewport, coordenadas, gestos, undo/redo, pruebas unitarias e
instrumentadas, matriz de dispositivos, riesgos y confirmación de que Oboe no fue
modificado.

Actualizar Linear `OSQ-5` con commit, PR, pruebas y dispositivos; mover a Done
únicamente después de aprobación y merge. Actualizar Notion a Validado sólo con
PR, revisión técnica y evidencia de dispositivo/emulador.

La revisión de seguridad debe cubrir IDs, límites temporales, autosave corrupto,
proyectos malformados, audio nativo, permisos, rutas, restauración, valores
extremos y separación de Oboe.

### Commit opcional

`chore(timeline): finalize validation and review artifacts`

## Orden obligatorio

```text
0. Línea base
1. Sistema de coordenadas
2. Pruebas unitarias de coordenadas
3. Harness de Compose
4. Zoom, scroll y playhead
5. Máquina de estados de gestos
6. Resize
7. Undo/redo transaccional
8. Delete y Trash
9. Selector horizontal
10. Auto-scroll
11. Indicadores visuales
12. Matriz de dispositivos
13. Rendimiento
14. Documentación
15. PR, Linear, Notion y seguridad
```

## Definición global de terminado

La Timeline queda lista cuando cumple todo lo siguiente:

- Zoom estable hasta 500%.
- Scroll estable después de zoom alto.
- Tap exacto después de scroll.
- Playhead correcto durante playback, scroll y zoom.
- Resize fiable desde ambos extremos sin resize accidental desde el cuerpo.
- Delete múltiple para clips pequeños.
- Undo/redo para operaciones individuales y agrupadas.
- Trash conserva y restaura el estado completo.
- Selector aislado de la Timeline.
- Ningún layout de ancho total gigantesco.
- Unit tests, lint, compilación y build pasan.
- Instrumentation tests pasan en al menos un emulador.
- Evidencia en varias densidades.
- Documentación actualizada al modelo viewport.
- Oboe sin modificaciones.
- PR, Linear, Notion y seguridad cerrados.
