# Ultra Sentinel — prevención local rápida

La revisión local es **opcional**, no se instala desde la CI y no modifica archivos.

En la raíz del repositorio, en un equipo con Git y Node.js 18+:

~~~sh
sh .github/scripts/install_sentinel_hook.sh
~~~

Tras instalarlo, cada \`git commit\` ejecuta un escaneo **offline** del diff staged.
Predeterminado: advierte. Modo bloqueante para candidatos BLOCKER:
\`SENTINEL_PRECOMMIT_STRICT=1 git commit -m "..." \`.

**Límites**: este escaneo no usa AST Kotlin, no compila el APK y no puede prometer una
probabilidad calibrada de fallo o cero CI rojo. Mantén los tests de Android, coverage,
ArchitectureBoundaryGuardTest y las revisiones de GitHub. El hook no modifica código
ni cambia las reglas de protección de main.

**Rendimiento**: no descarga modelos, no ejecuta Gradle ni consulta APIs;
limita el tamaño del diff y analiza solo archivos modificados.
