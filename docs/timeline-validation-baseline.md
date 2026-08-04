# Timeline validation baseline

## Baseline

- Fecha: 2026-07-25
- Commit de partida: `2b350d6`
- Rama: `feat/timeline-hardening`
- Objetivo: congelar el estado verificable antes de extraer coordenadas y añadir
  pruebas de interacción.

## Estado del árbol

El submódulo `app/src/main/cpp/oboe` contiene modificaciones previas ajenas a
este trabajo. Se mantiene fuera de staging y de todos los commits de Timeline.

El plan de trabajo está en `docs/timeline-hardening-plan.md` y tampoco forma
parte de la base de código funcional.

## Validación ejecutada

| Comando | Resultado |
| --- | --- |
| `./gradlew testDebugUnitTest` | PASS |
| `./gradlew compileDebugKotlin` | PASS |
| `./gradlew lintDebug` | PASS |
| `./gradlew assembleDebug` | PASS |
| `./gradlew connectedDebugAndroidTest` | No ejecutado: no había dispositivo/emulador conectado |

## Criterio de salida del Módulo 0

- [x] El proyecto compila desde `2b350d6`.
- [x] Los cuatro gates locales pasan.
- [x] Oboe está identificado y se mantendrá fuera de staging.
- [x] La rama de hardening parte de `2b350d6`.

## Próximo paso

Crear el commit independiente:

```text
docs(timeline): record validation baseline
```

Después del commit se puede iniciar el Módulo 1: centralizar la transformación
entre ticks musicales y coordenadas del viewport.
