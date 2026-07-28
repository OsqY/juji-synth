# Timeline Hardening — Plan ejecutable de pendientes

> **Fuente de verdad actual.** Actualizado para `feat/timeline-hardening` en
> `42129c9` el 2026-07-27. El contenido histórico posterior a esta cabecera se
> conserva como referencia de arquitectura, pero no debe usarse para elegir el
> siguiente módulo ni para ejecutar Git. `AGENTS.md` y `CONVENTIONS.md` definen
> el flujo obligatorio vigente.

Este documento es el punto de entrada para el siguiente agente que continúe el
hardening de Timeline. Describe el estado comprobable, dónde está cada pieza,
qué falta y el procedimiento obligatorio para implementar, validar, revisar y
publicar cada fase.

## 1. Estado verificable

| Propiedad | Estado |
| --- | --- |
| Rama de trabajo | `feat/timeline-hardening` |
| Commit base | `2b350d6` — `fix(timeline): prevent zoom layout overflow` |
| Último hito funcional validado | R1 — movimiento de selección múltiple; el SHA queda registrado por el commit de la fase |
| Módulos terminados | 0–9; regresión R1 corregida y validada |
| Próxima fase | R2 — cancelación segura de pinch durante edición |
| Rama remota | Confirmar antes de publicar; no asumir su posición desde este documento |
| Pull request | Pendiente; no crear hasta terminar los módulos 9–14 |
| Linear | Último estado documentado: `OSQ-5` en `In Review`; comprobarlo antes de escribir |
| Notion | Último estado documentado: `En curso/listo para revisión`; comprobarlo antes de escribir |

Validación local más reciente registrada para `42129c9`:

| Comando | Resultado |
| --- | --- |
| `./gradlew testDebugUnitTest` | PASS |
| `./gradlew compileDebugKotlin` | PASS |
| `./gradlew lintDebug` | PASS |
| `./gradlew assembleDebug` | PASS |
| `./gradlew connectedDebugAndroidTest` | No repetido en esta fase documental; ejecutar si ADB tiene un dispositivo `device` |

La compilación de los tests instrumentados pasó. La ausencia de una ejecución
en dispositivo no debe presentarse como un PASS ni como un fallo funcional:
debe registrarse como prueba no ejecutada por limitación del entorno.

### Archivos ajenos que deben preservarse

- `app/src/main/cpp/oboe` tiene modificaciones locales previas. Es un submódulo
  ajeno al trabajo de Timeline. Nunca restaurarlo, editarlo, añadirlo a staging
  ni incluirlo en commits.
- `.commandcode/` apareció como contenido no versionado ajeno. No modificarlo ni
  añadirlo salvo una instrucción explícita del usuario.
- `AGENTS.md`, `CONVENTIONS.md` y `DESIGN.md` son documentación de onboarding
  vigente. Leerlos antes de elegir trabajo, editar, o ejecutar Git.

## 2. Qué ya está hecho

| Módulo | Commit | Resultado |
| --- | --- | --- |
| 0. Línea base | `68179d5` | Rama creada desde `2b350d6`, gates registrados y Oboe excluido |
| 1. Coordenadas | `6edd80b` | `TimelineTransform` centraliza tick, viewport, scroll y zoom |
| 2. Tests de coordenadas | `fa1ca7e` | Zoom 25–500%, densidades, scroll, bordes y round trips cubiertos |
| 3. Harness Compose | `33bc1fc` | Tags estables y escenario instrumentado inicial |
| 4. Zoom/scroll/playhead | `55d0a45` | Mapeo estabilizado sobre viewport virtualizado |
| 5. Máquina de gestos | `c7a44a3` | Estados explícitos y gestos mutuamente excluyentes |
| 6. Resize | `f07c389` | Handles, preview musical y una transacción por gesto |
| 7. Undo/redo | `f17e75a` | Comandos atómicos y snapshots antes/después |
| 8. Delete/Trash | `7fa5510` | Hit-testing de clips pequeños, IDs deduplicados y tests de restauración |
| 9. Selector horizontal | `42129c9` | Estados de scroll separados y tags de selector; pendiente de regresión de restauración de scroll |

No reimplementar estos módulos. Las siguientes regresiones bloquean el avance:

1. Un drag colapsa una selección múltiple y mueve sólo el clip ancla.
2. Un pinch puede confirmar un move o resize que debía cancelar.
3. La matemática temporal no es segura ante `Long.MAX_VALUE`, `NaN` e infinito.
4. Cambiar entre Pads y Patterns recentra el selector y las pruebas no verifican
   su scroll real.

Corregirlas en el siguiente orden, una fase por commit, antes de los módulos
10–15.

### R1 — Movimiento de selección múltiple

**Estado.** Completado el 2026-07-27. Cuatro gates locales PASS, 14/14 tests
instrumentados PASS en SM-G998W con Android 15, y revisión independiente PASS.

**Objetivo.** Un drag iniciado sobre un clip ya seleccionado mueve el grupo
completo, sin colapsar la selección antes de confirmar la transacción.

**Alcance.** `TimelineScreen.kt`, `TimelineViewModel.kt`, helpers puros y sus
tests. No cambiar zoom, resize, Delete ni apariencia visual.

**Aceptación.**

- Los IDs del grupo se capturan al inicio del gesto y se entregan explícitamente
  al commit de movimiento.
- Arrastrar un clip perteneciente a una selección múltiple conserva todos los
  IDs seleccionados y sus offsets relativos de tick/fila.
- Arrastrar un clip no seleccionado mueve únicamente ese clip y lo selecciona.
- Todo el grupo genera una sola `MoveClipsCommand`; un undo y redo restauran
  exactamente los dos estados.

**Pruebas.** Una prueba unitaria de la selección de drag y una prueba de
integración de ViewModel o Compose para move grupal + undo/redo.

**Validación.** Los cuatro gates de `CONVENTIONS.md`; `connectedDebugAndroidTest`
si ADB informa un dispositivo `device`.

**Commit.** `fix(timeline): preserve multi-clip move selection`

### R2 — Cancelación segura de pinch durante edición

**Objetivo.** Un pinch cancela de forma explícita un move o resize en curso y
nunca confirma su preview ni registra historial.

**Alcance.** Máquina de estados de gestos, limpieza de estado temporal de UI y
pruebas. No modificar matemática de zoom ni ergonomía visual.

**Aceptación.**

- Cada gesto tiene propietario y sólo confirma si conserva ese estado al soltar.
- Al comenzar pinch se limpian offsets y previews de move/resize previos.
- Pinch sobre move o resize no cambia clips, no crea comando y no autosavea una
  transacción cancelada.
- Los gestos normales de move y resize continúan produciendo un solo comando.

**Pruebas.** Reducer de estados para cancelación y pruebas Compose de pinch
iniciado durante move y resize.

**Validación.** Los cuatro gates; instrumentación en dispositivo si disponible.

**Commit.** `fix(timeline): cancel active edits before pinch zoom`

### R3 — Aritmética temporal total y segura

**Objetivo.** Ningún valor temporal extremo o no finito produce overflow,
duraciones inválidas, viewport vacío o crash.

**Alcance.** `TimelineEditingMath.kt`, consumidores de extremos de clips en
Screen/ViewModel, y pruebas unitarias. No cambiar UX de edición.

**Aceptación.**

- Sumas de inicio/duración y redondeos de duración son saturados o validados.
- `Long.MAX_VALUE`, límites de scroll, rangos visibles y clip ends no desbordan.
- Zoom, scroll y transformaciones rechazan o normalizan `NaN` e infinito.
- ViewModel valida datos públicos antes de construir clips inválidos.

**Pruebas.** Casos de `Long.MAX_VALUE`, valores negativos, `NaN`, infinito y
round trips de coordenadas que no generen resultados inválidos.

**Validación.** Los cuatro gates; instrumentación si está disponible.

**Commit.** `fix(timeline): harden temporal coordinate bounds`

### R4 — Restauración real de scroll del selector

**Objetivo.** Pads y Patterns conservan su propio scroll y los gestos del
selector no alteran la Timeline.

**Alcance.** `TimelineEditorToolbar` y pruebas Compose. No modificar la
transformación del viewport ni estilos fuera del selector.

**Aceptación.**

- Cambiar entre Pads y Patterns restaura la posición anterior de cada lista.
- El auto-scroll ocurre sólo al seleccionar una fuente en el modo activo, no al
  alternar de modo.
- A32 y P16 se seleccionan mediante scroll y tap reales.
- Un swipe del selector no cambia el viewport; un swipe del viewport no cambia
  la posición del selector; un drag no selecciona accidentalmente.

**Pruebas.** Instrumentación que observe índices/rangos de ambas `LazyRow` y el
estado de scroll del viewport, además de clicks reales en A32/P16.

**Validación.** Los cuatro gates; instrumentación en dispositivo si disponible.

**Commit.** `fix(timeline): preserve selector scroll isolation`

## 3. Cómo navegar el código

### Ruta de lectura recomendada

1. `docs/plans/timeline-hardening-pending.md`
2. `docs/timeline-hardening-plan.md`
3. `app/src/main/java/com/jujidaw/model/ClipModel.kt`
4. `app/src/main/java/com/jujidaw/ui/timeline/TimelineEditingMath.kt`
5. `app/src/main/java/com/jujidaw/ui/timeline/TimelineGestureState.kt`
6. `app/src/main/java/com/jujidaw/ui/timeline/TimelineViewModel.kt`
7. `app/src/main/java/com/jujidaw/ui/timeline/TimelineScreen.kt`
8. Tests unitarios e instrumentados correspondientes

### Responsabilidad de cada archivo

| Archivo | Responsabilidad |
| --- | --- |
| `TimelineScreen.kt` | Compose UI, viewport, grid, ruler, playhead, clips, toolbar y detección táctil |
| `TimelineViewModel.kt` | Estado observable, edición, selección, snapshots, autosave y transporte |
| `TimelineEditingMath.kt` | Fuente de verdad tick ↔ píxel, snap, hit-testing, rango visible y resize |
| `TimelineGestureState.kt` | Prioridad y exclusividad de pinch, scroll, move, resize, delete y scrub |
| `TimelineEditCommands.kt` | Tipos de transacciones para undo/redo |
| `TimelineEditHistory.kt` | Pilas acotadas de undo/redo |
| `TimelineDeleteSession.kt` | Conjunto ordenado de IDs borrados por un solo trazo |
| `ClipModel.kt` | `PadClip`, `PatternClip`, `AudioClip` y `Arrangement` en ticks musicales |
| `TransportController.kt` | Reproducción, playhead, loop, punch y scheduling |
| `ProjectAutosave.kt` | Persistencia diferida después de confirmar una operación |

Tests principales:

| Archivo | Qué protege |
| --- | --- |
| `TimelineTransformTest.kt` | Coordenadas, zoom anclado, densidad y overflow |
| `TimelineEditingMathTest.kt` | Snap, resize, hit-testing y conversiones auxiliares |
| `TimelineGestureStateTest.kt` | Exclusividad y cancelación de gestos |
| `TimelineEditHistoryTest.kt` | Atomicidad, orden y round trip de comandos |
| `TimelineDeleteSessionTest.kt` | Deduplicación durante Delete |
| `TimelineComposeHarnessTest.kt` | Superficies reales de Compose, zoom, scroll, playhead y Delete/Trash |

Tags Compose disponibles:

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

### Invariantes que no deben romperse

1. La posición, inicio y duración de clips viven en ticks, nunca en píxeles.
2. Los píxeles sólo sirven para renderizado, hit-testing y deltas temporales de
   un gesto.
3. Grid, ruler, playhead, clips y taps usan `TimelineTransform`.
4. Sólo se componen clips y marcas del rango visible; clips en drag o resize se
   mantienen compuestos aunque su posición original salga del rango.
5. Un gesto completo produce una sola transacción de historial.
6. Autosave ocurre al confirmar la transacción, no en cada frame.
7. El cuerpo del clip mueve; únicamente los handles inician resize.
8. Pinch tiene prioridad sobre los gestos de un dedo.
9. Los IDs de clips se conservan durante undo, redo y Trash restore.
10. No se crean contenedores con el ancho total de una Timeline zoomed.

## 4. Flujo obligatorio por módulo

El orden es Notion → Linear → implementación → GitHub → revisión → seguridad.
No avanzar al módulo siguiente si el actual no cumple su validación.

### Antes de editar

1. Leer este documento y la sección del módulo en
   `docs/timeline-hardening-plan.md`.
2. Confirmar sus criterios de aceptación en el comentario de trabajo.
3. Verificar la rama y el árbol.
4. Leer `CONVENTIONS.md`.
5. Inspeccionar Oboe sin modificarlo:

```bash
git status --short
git diff --submodule=log -- app/src/main/cpp/oboe
```

### Implementación

1. Trabajar únicamente en el módulo activo.
2. Separar correcciones funcionales de cambios visuales.
3. Extraer lógica temporal o geométrica a funciones puras antes de probar
   gestos en Compose.
4. Añadir tests de regresión que fallen sin el cambio.
5. No añadir dependencias salvo que el módulo las requiera y se documente la
   razón.

### Gates

Ejecutar individualmente y guardar el resultado:

```bash
./gradlew testDebugUnitTest
./gradlew compileDebugKotlin
./gradlew lintDebug
./gradlew assembleDebug
./gradlew compileDebugAndroidTestKotlin
```

Si `adb devices -l` muestra un dispositivo `device`:

```bash
./gradlew connectedDebugAndroidTest
```

Si ADB no funciona, registrar el mensaje exacto y no inventar un resultado. En
un teléfono físico comprobar desbloqueo, depuración USB y autorización del
equipo. En un entorno sandbox puede ser necesario ejecutar ADB fuera del
sandbox para acceder a `/dev/bus/usb`.

### Revisión, commit y publicación

1. Ejecutar una revisión adversarial sobre el diff.
2. Corregir hallazgos bloqueantes y repetir todos los gates afectados.
3. Verificar whitespace y staging:

```bash
git diff --check
git diff --cached --check
git diff --cached --name-only
```

4. Añadir únicamente rutas explícitas. Está prohibido usar `git add -A`,
   `git add .` o cualquier comando que pueda capturar Oboe.
5. Crear exactamente un commit con el mensaje definido para el módulo.
6. Publicar `feat/timeline-hardening`; no hacer merge a `main`.
7. Actualizar este documento con commit, fecha, gates y limitaciones reales.

## 5. Módulos pendientes

### Pendiente inmediato — validar Módulo 8 en dispositivo

Objetivo: ejecutar el test Compose de Delete/Trash ya compilado.

Pasos:

1. Confirmar que `adb devices -l` muestra el teléfono como `device`.
2. Ejecutar `./gradlew connectedDebugAndroidTest`.
3. Verificar específicamente
   `TimelineComposeHarnessTest.deleteToolRemovesShortClipAndRestoresItFromTrash`.
4. Registrar dispositivo, Android, resolución, densidad y resultado.
5. Si falla, clasificar entre instalación/ADB, aserción Compose o fallo
   funcional. No iniciar el Módulo 9 hasta resolverlo o demostrar que el fallo
   es exclusivamente de infraestructura.

No crear otro commit si sólo se registra una ejecución exitosa y no cambia
ningún archivo. Si se añade evidencia documental, incluirla más adelante en el
commit del Módulo 12.

### Módulo 9 — Aislar selector horizontal

Commit requerido:

```text
fix(timeline): isolate source selector gestures
```

Implementación:

1. Mantener el selector como `LazyRow` dentro de
   `TimelineEditorToolbar`.
2. Usar estados de scroll independientes para Pads y Patterns para que cambiar
   de modo no reutilice accidentalmente la posición del otro selector.
3. La superficie que recibe el primer down conserva el gesto hasta up/cancel:
   el selector consume su drag horizontal, pero no bloquea pinch o scroll
   iniciados dentro del viewport.
4. Mantener exactamente cinco opciones visibles calculando el ancho desde
   `BoxWithConstraints`.
5. Mantener las selecciones A1–A32 y P1–P16 y el auto-scroll hacia la opción
   seleccionada.
6. No modificar la matemática de Timeline ni el scroll del viewport.

Pruebas:

- El selector muestra cinco opciones completas.
- Un swipe del selector cambia su rango visible sin mover Timeline.
- Un swipe del viewport no cambia la posición del selector.
- A1, A32, P1 y P16 pueden seleccionarse.
- Un tap selecciona una vez.
- Un drag que supera touch slop no selecciona una opción al soltar.
- Alternar Pads/Patterns conserva el scroll de cada modo.

Aceptación: tests unitarios, Compose compile, gates y prueba instrumentada en
dispositivo pasan.

### Módulo 10 — Auto-scroll al mover clips

Commit requerido:

```text
feat(timeline): add edge auto-scroll while moving clips
```

Implementación:

1. Extraer una función pura que reciba `pointerViewportX`,
   `viewportWidthPx`, ancho de zona de borde y velocidad máxima, y devuelva
   velocidad horizontal firmada.
2. Usar una zona de borde configurable en píxeles sólo para detección. El
   resultado final del movimiento sigue calculándose en ticks.
3. Activar un loop por frame únicamente durante
   `TimelineGestureState.MovingClip`.
4. Actualizar `scrollX` dentro de `0..maxScrollX`.
5. Recalcular el tick debajo del dedo después de cada cambio de scroll para que
   el clip permanezca anclado visualmente.
6. Detener el loop al salir de la zona, al soltar, cancelar, iniciar pinch o
   cambiar de herramienta.
7. No activar auto-scroll durante resize.
8. Confirmar todo el movimiento con un solo `MoveClipsCommand`.

Pruebas:

- Velocidad cero fuera de las zonas.
- Dirección y velocidad proporcionales en ambos bordes.
- Límites inicial/final sin overscroll.
- Movimiento a izquierda/derecha a zoom 100% y 500%.
- Cambio de fila durante auto-scroll.
- Undo revierte el gesto completo con una sola operación.

### Módulo 11 — Indicadores visuales

Commit requerido:

```text
feat(timeline): add editing state indicators
```

Este módulo es visual; no mezclar correcciones de gestos.

Implementación:

1. Auditar lo ya existente antes de añadir nodos duplicados.
2. Mostrar Delete activo en toolbar.
3. Mostrar siempre el porcentaje real de zoom, también en layout no compacto.
4. Mostrar la resolución real de snap.
5. Mantener handles visibles sólo para la selección editable.
6. Mejorar contraste del playhead sin cambiar su posición ni interceptar input.
7. Diferenciar compás, beat y subdivisión mediante grosor/alpha.
8. Mantener preview de move/resize y feedback de duración musical.
9. Mantener clips muteados visualmente diferenciados.
10. Aplicar `clearAndSetSemantics` o contenido equivalente cuando un indicador
    puramente decorativo no deba aumentar el árbol semántico.

Pruebas:

- Los valores mostrados coinciden con zoom y snap efectivos.
- Los indicadores no reciben taps destinados al viewport.
- Delete activo/inactivo es localizable por tag.
- Playhead y clips no cambian de coordenada por el rediseño.

### Módulo 12 — Matriz de dispositivos y densidades

Commit requerido:

```text
test(timeline): add device density validation matrix
```

Crear `docs/timeline-device-validation/` y un registro por perfil.

Matriz mínima:

| Perfil | Orientación | Densidad objetivo |
| --- | --- | --- |
| Teléfono pequeño | Portrait y landscape | mdpi/xhdpi |
| Teléfono estándar | Portrait y landscape | xxhdpi |
| Teléfono alta densidad | Portrait | xxxhdpi |
| Tablet | Portrait y landscape | xhdpi/xxhdpi |

Para cada perfil guardar:

- Nombre de AVD o modelo físico.
- Android/API.
- Resolución y densidad obtenidas con `adb shell wm size` y
  `adb shell wm density`.
- Resultado de abrir, colocar clips, zoom 500%, scroll largo, move, resize,
  multi-delete, undo/redo, playback y selector.
- Captura o grabación y cualquier incidencia.

No alterar densidad/resolución de un teléfono físico sin autorización. Preferir
AVDs para perfiles artificiales.

### Módulo 13 — Rendimiento y recomposición

Commit requerido:

```text
perf(timeline): reduce viewport recomposition overhead
```

Implementación:

1. Extraer y probar el filtrado de clips visibles si continúa embebido en el
   composable.
2. Calcular grid y ruler sólo para `visibleTickRange`.
3. Mantener keys estables por `clip.id`.
4. Evitar crear listas, pares u objetos repetitivos dentro de cada frame de
   playback o drag cuando puedan derivarse una vez.
5. Reducir el alcance del estado observado por controles no relacionados.
6. Aislar la actualización frecuente del playhead de toolbar, selector y
   controles estáticos.
7. Usar `derivedStateOf` sólo si evita cálculo o recomposición medible.
8. No sustituir virtualización por un contenedor de ancho completo.

Escenario de estrés:

- Timeline de 200 barras.
- Miles de clips distribuidos en las 16 filas.
- Zoom 500%.
- Playback, scroll y movimiento simultáneos.

Evidencia mínima:

- Número de nodos de clip compuesto permanece acotado al viewport más margen.
- No se componen todas las marcas del grid.
- No aparece layout overflow.
- Registrar método de medición y resultados antes/después; no declarar mejora
  sin datos.

### Módulo 14 — Documentación técnica

Commit requerido:

```text
docs(timeline): document viewport-based rendering model
```

Actualizar `docs/pads-timeline-workflow.md` para reflejar:

- Estado musical de clips.
- `TimelineTransform`.
- Rango visible y virtualización.
- Grid, ruler y playhead en coordenadas de viewport.
- Zoom anclado y scroll.
- Máquina de estados de gestos.
- Comandos e historial.
- Delete/Trash.
- Auto-scroll.
- Autosave y restricciones conocidas.

Incluir:

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

Corregir también rutas erróneas de paquetes en `docs/backlog.md` o reemplazar
ese documento por un enlace hacia este plan.

### Módulo 15 — Cierre técnico

Commit opcional:

```text
chore(timeline): finalize validation and review artifacts
```

Orden:

1. Ejecutar todos los gates desde un árbol limpio salvo Oboe.
2. Ejecutar instrumentación en al menos un emulador/dispositivo.
3. Completar la matriz de dispositivos.
4. Ejecutar revisión de código final.
5. Ejecutar revisión formal de seguridad.
6. Crear PR contra `main`; no hacer merge directo.
7. Actualizar Linear `OSQ-5` con commits, PR, gates, dispositivos y riesgos.
8. Actualizar Notion a Validado sólo con PR, revisión y evidencia.
9. Mover Linear a Done únicamente después de aprobación y merge.

La revisión de seguridad debe cubrir:

- IDs de clips vacíos, repetidos o desconocidos.
- Límites de fila, tick y duración.
- Overflow y valores extremos.
- Autosave o proyectos corruptos/malformados.
- Rutas de audio y archivos externos.
- Restauración inconsistente de Trash.
- Permisos de almacenamiento y micrófono.
- JNI/audio nativo y crashes provocables.
- Ausencia de secretos o datos personales en capturas/logs.
- Confirmación de que Oboe no cambió en ningún commit de la rama.

## 6. Definición global de terminado

- [ ] Módulo 8 ejecutado en dispositivo.
- [ ] Selector aislado y probado.
- [ ] Auto-scroll estable y undo atómico.
- [ ] Indicadores terminados sin interceptar gestos.
- [ ] Zoom estable hasta 500%.
- [ ] Scroll, tap y playhead correctos después de zoom.
- [ ] Resize fiable desde ambos extremos.
- [ ] Delete/Trash y undo/redo cubiertos.
- [ ] Virtualización y rendimiento medidos.
- [ ] Evidencia en varias densidades.
- [ ] Documentación técnica actualizada.
- [ ] Todos los gates pasan.
- [ ] Revisión de código y seguridad cerradas.
- [ ] Oboe fuera de todos los commits.
- [ ] PR aprobado y mergeado.
- [ ] Linear y Notion actualizados.

## 7. Plantilla de handoff por módulo

```text
Módulo:
Commit:
Objetivo:
Archivos cambiados:
Criterios cumplidos:
Tests añadidos:
testDebugUnitTest:
compileDebugKotlin:
lintDebug:
assembleDebug:
compileDebugAndroidTestKotlin:
connectedDebugAndroidTest:
Dispositivo:
Hallazgos de revisión:
Riesgo residual:
Oboe fuera de staging: sí/no
Próximo módulo:
```

## 8. Referencias

- `docs/timeline-hardening-plan.md`: plan original completo.
- `docs/timeline-validation-baseline.md`: validación de la línea base.
- `docs/handoffs/timeline-interactions-e37s15.md`: handoff funcional anterior.
- `docs/pads-timeline-workflow.md`: flujo actual de usuario y documentación a
  actualizar en el Módulo 14.
- `docs/backlog.md`: backlog previo; contiene rutas antiguas que no deben usarse
  para navegar el código hasta corregirlas.
