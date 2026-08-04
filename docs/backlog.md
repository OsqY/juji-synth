# Backlog — Timeline Hardening

> **Historical snapshot — not authoritative.** This file records an earlier
> handoff and contains stale branch, module, validation, and staging guidance.
> Do not use it to choose work or execute Git commands. Use
> `docs/plans/timeline-hardening-pending.md`, `AGENTS.md`, and
> `CONVENTIONS.md` instead.

## Estado verificable actual

| Propiedad | Valor |
|---|---|
| Rama | `feat/timeline-hardening` |
| Base | [`2b350d6`](https://github.com/...(commit/2b350d6)) — `fix(timeline): prevent zoom layout overflow` |
| HEAD | [`7fa5510`](https://github.com/...(commit/7fa5510)) — `test(timeline): cover multi-delete and trash restoration` |
| Oboe | `app/src/main/cpp/oboe` es submódulo ajeno con modificaciones previas. Se mantiene **fuera de staging y de todos los commits** de Timeline. |
| Arbol | `m app/src/main/cpp/oboe` (modificado, excluido), `?? .commandcode/` (no versionado, irrelevante) |

## Progreso por módulos

| # | Módulo | Commit | Estado |
|---|---|---|---|
| 0 | Línea base | [`68179d5`](https://github.com/...(commit/68179d5)) `docs(timeline): record validation baseline` | ✅ Completado |
| 1 | Sistema de coordenadas | [`6edd80b`](https://github.com/...(commit/6edd80b)) `refactor(timeline): centralize viewport coordinate transforms` | ✅ Completado |
| 2 | Pruebas unitarias de coordenadas | [`fa1ca7e`](https://github.com/...(commit/fa1ca7e)) `test(timeline): cover zoom scroll and coordinate mapping` | ✅ Completado |
| 3 | Harness instrumentado de Compose | [`33bc1fc`](https://github.com/...(commit/33bc1fc)) `test(timeline): add compose instrumentation harness` | ✅ Completado |
| 4 | Zoom, scroll y playhead | [`55d0a45`](https://github.com/...(commit/55d0a45)) `fix(timeline): stabilize zoom scroll and playhead mapping` | ✅ Completado |
| 5 | Máquina de estados de gestos | [`c7a44a3`](https://github.com/...(commit/c7a44a3)) `refactor(timeline): introduce explicit gesture state machine` | ✅ Completado |
| 6 | Resize de clips | [`f07c389`](https://github.com/...(commit/f07c389)) `feat(timeline): improve clip resize handles and feedback` | ✅ Completado |
| 7 | Undo/redo transaccional | [`f17e75a`](https://github.com/...(commit/f17e75a)) `fix(timeline): make edit history operations atomic` | ✅ Completado |
| 8 | Delete y Trash | [`7fa5510`](https://github.com/...(commit/7fa5510)) `test(timeline): cover multi-delete and trash restoration` | ✅ Completado. Validado en SM-G998W (Android 15) — 6/6 instrumented tests PASS. |
| 9 | Aislar selector horizontal | — | ✅ Completado. Scroll independiente Pads/Patterns, chips con testTag, 12/12 instrumented tests PASS en SM-G998W (Android 15). |
| 10 | Auto-scroll al mover clips | — | 🔲 Pendiente |
| 11 | Indicadores visuales | — | 🔲 Pendiente |
| 12 | Matriz de dispositivos y densidades | — | 🔲 Pendiente |
| 13 | Rendimiento y recomposición | — | 🔲 Pendiente |
| 14 | Documentación técnica | — | 🔲 Pendiente |
| 15 | Cierre técnico (PR, Linear, Notion, seguridad) | — | 🔲 Pendiente |

### Módulos pendientes (9–15) — resumen

Cada módulo tiene objetivo, criterios de aceptación y mensaje de commit definidos en [`docs/timeline-hardening-plan.md`](./timeline-hardening-plan.md). A continuación un resumen:

- **Módulo 9** — `fix(timeline): isolate source selector gestures`. Aislar gestos del selector horizontal (pads/patrones) para que no interfieran con la Timeline.
- **Módulo 10** — `feat(timeline): add edge auto-scroll while moving clips`. Auto-scroll por proximidad al borde durante movimiento de clips.
- **Módulo 11** — `feat(timeline): add editing state indicators`. Indicadores visuales de estado (delete, snap, zoom, handles, mute, preview).
- **Módulo 12** — `test(timeline): add device density validation matrix`. Validación en múltiples densidades y tamaños de pantalla.
- **Módulo 13** — `perf(timeline): reduce viewport recomposition overhead`. Medir y optimizar recomposición.
- **Módulo 14** — `docs(timeline): document viewport-based rendering model`. Actualizar documentación del modelo viewport-based.
- **Módulo 15** — `chore(timeline): finalize validation and review artifacts`. PR, revisión de seguridad, Linear, Notion y merge.

## Estado de validación actual

Ejecutado sobre `7fa5510`:

| Comando | Resultado |
|---|---|
| `./gradlew testDebugUnitTest` | ✅ PASS |
| `./gradlew compileDebugKotlin` | ✅ PASS |
| `./gradlew lintDebug` | ✅ PASS |
| `./gradlew assembleDebug` | ✅ PASS |
| `./gradlew compileDebugAndroidTestKotlin` | ✅ PASS |
| `./gradlew connectedDebugAndroidTest` | ⏳ No ejecutado |

**Causa de `connectedDebugAndroidTest` pendiente**: requiere un dispositivo Android (físico o emulador) conectado por ADB. No está disponible en este entorno. Esto **no es un fallo funcional**; es una limitación del entorno de ejecución. Cuando haya un dispositivo disponible, ejecutar:

```bash
adb devices -l
./gradlew connectedDebugAndroidTest
```

## Mapa de navegación para agentes

Archivos clave para trabajar en Timeline, ordenados por relevancia:

### UI y lógica principal

| Archivo | Propósito |
|---|---|
| `app/src/main/java/com/jujidaw/ui/timeline/TimelineScreen.kt` | UI Compose: viewport, renderizado de grid/ruler/clips/playhead y gestión de gestos. |
| `app/src/main/java/com/jujidaw/ui/timeline/TimelineViewModel.kt` | Estado de Timeline, comandos de edición, historial, autosave y comunicación con TransportController. |
| `app/src/main/java/com/jujidaw/ui/timeline/TimelineEditingMath.kt` | Transformación tick ↔ píxel, snapping, hit-testing y resize. |
| `app/src/main/java/com/jujidaw/ui/timeline/TimelineGestureState.kt` | Prioridad y exclusividad de gestos (máquina de estados). |

### Transacciones y persistencia

| Archivo | Propósito |
|---|---|
| `app/src/main/java/com/jujidaw/ui/timeline/TimelineEditCommands.kt` | Comandos de edición (Add, Move, Resize, Delete, Duplicate, Paste, Mute, Restore). |
| `app/src/main/java/com/jujidaw/ui/timeline/TimelineEditHistory.kt` | Historial de undo/redo con ejecución atómica. |
| `app/src/main/java/com/jujidaw/ui/timeline/TimelineDeleteSession.kt` | Sesión de delete múltiple con MutableSet de IDs. |

### Modelos y controladores

| Archivo | Propósito |
|---|---|
| `app/src/main/java/com/jujidaw/model/ClipModel.kt` | Modelo de datos de clip (ticks, duración, fila, mute, source). |
| `app/src/main/java/com/jujidaw/engine/TransportController.kt` | Control de reproducción (play, stop, playhead, loop, punch). |

### Pruebas

| Archivo | Propósito |
|---|---|
| `app/src/test/java/com/jujidaw/ui/timeline/TimelineEditingMathTest.kt` | Pruebas unitarias de transformación de coordenadas. |
| `app/src/androidTest/java/com/jujidaw/ui/timeline/TimelineComposeHarnessTest.kt` | Pruebas instrumentadas de UI Compose. |

### Búsqueda rápida

Tags de prueba para localizar elementos en tests instrumentados:
`timeline-viewport`, `timeline-grid`, `timeline-ruler`, `timeline-playhead`,
`timeline-clip-{id}`, `timeline-clip-start-handle-{id}`, `timeline-clip-end-handle-{id}`,
`timeline-delete-tool`, `timeline-pad-selector`, `timeline-pattern-selector`,
`timeline-zoom-indicator`, `timeline-snap-indicator`.

## Reglas arquitectónicas

1. **Tiempo en ticks, píxeles solo para render/gestos**. Los clips se almacenan en ticks, steps u otra unidad musical. Los píxeles se usan exclusivamente para renderizado y detección de gestos.
2. **Viewport virtualizado**. Solo se procesa y renderiza el rango visible. No se recrea contenido con ancho total.
3. **Una transacción por gesto**. Cada gesto completo (move, resize, delete) produce una sola operación de undo/redo.
4. **IDs estables**. Los IDs de clips no cambian durante la sesión, incluso después de undo/redo.
5. **Snapping consistente**. Todas las operaciones de edición pasan por la misma función de snap (TimelineEditingMath).
6. **Oboe excluido**. El submódulo `app/src/main/cpp/oboe` no se modifica ni se incluye en ningún commit de Timeline.

## Flujo obligatorio para cada módulo

Cada módulo sigue este flujo, documentado en el orden ejecutado en la sesión:

1. **Confirmar aceptación** en Notion. Verificar que los criterios están definidos en `docs/timeline-hardening-plan.md`.
2. **Revisar árbol**. Confirmar que `app/src/main/cpp/oboe` está fuera de staging (`git status --short`). Los únicos archivos modificados deben ser los del módulo actual.
3. **Implementar un solo módulo**. Sin mezclar correcciones funcionales con mejoras visuales.
4. **Añadir o actualizar pruebas**. Unitarias para lógica nueva, instrumentadas para interacciones de UI.
5. **Ejecutar validación**:
   ```bash
   ./gradlew testDebugUnitTest
   ./gradlew compileDebugKotlin
   ./gradlew lintDebug
   ./gradlew assembleDebug
   ```
   Si ADB está disponible:
   ```bash
   ./gradlew connectedDebugAndroidTest
   ```
6. **Revisión de código y seguridad** proporcional al cambio. Ejecutar `$omk-code-review` sobre el diff.
7. **Commit convencional independiente**, publicar rama y actualizar estado externo:
   ```bash
   git add -A
   git commit -m "tipo(timeline): descripción"
   git push origin feat/timeline-hardening
   ```
   Luego actualizar la fila de este módulo en la tabla de progreso (commit, fecha, resultado).

## Checklist de cierre (Módulo 15)

- [ ] Evidencia en múltiples densidades y tamaños de pantalla (`docs/timeline-device-validation/`).
- [ ] Documentación actualizada al modelo viewport-based (`docs/pads-timeline-workflow.md`).
- [ ] Revisión de seguridad: IDs, límites temporales, autosave corrupto, proyectos malformados, audio nativo, permisos, valores extremos.
- [ ] PR creado con resumen de arquitectura, coordenadas, gestos, undo/redo, resultados de validación y confirmación de Oboe no modificado.
- [ ] Linear `OSQ-5` movido a Done.
- [ ] Notion actualizado a Validado.
- [ ] Merge a `main`.

## Documentos relacionados

- [`docs/timeline-hardening-plan.md`](./timeline-hardening-plan.md) — Plan detallado de los 16 módulos con objetivos, diseño y criterios de aceptación.
- [`docs/timeline-validation-baseline.md`](./timeline-validation-baseline.md) — Línea base de validación registrada al inicio del proyecto.
- [`docs/handoffs/timeline-interactions-e37s15.md`](./handoffs/timeline-interactions-e37s15.md) — Handoff de la sesión anterior con estado de aceptación y workflow.
- [`docs/pads-timeline-workflow.md`](./pads-timeline-workflow.md) — Documentación de usuario: workflow de Pads y Timeline.
