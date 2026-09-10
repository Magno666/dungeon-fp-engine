# Cómo se arma este build

Se combinan dos repos. Ninguno solo es suficiente.

1. **Código de juego** — `dachhack/SproutedPixelDungeon`, rama `v0.1.1`.
   Su `master` no tiene código. 872 archivos, el vainilla.
2. **Motor + Gradle** — `esunsatyr/SproutedPixelDungeon-Gradle`, rama
   `master`. Aporta los 67 archivos de `com.watabou` y el layout Gradle.
   Su código de juego se descarta: añade ~40 hechizos que no compilan.

Sobre ese andamio:

- `settings.gradle` / `build.gradle` a AGP 8.3.2 y Gradle 8.7.
  **`jcenter()` está muerto** desde 2021 — reemplazar con
  `google()` + `mavenCentral()`.
- `compileSdk` / `targetSdk` 35, `minSdk` 21, `namespace` en gradle.
- `android:exported="true"` en la activity del lanzador (Android 12+).
- `android:windowOptOutEdgeToEdgeEnforcement="true"`, o Android 15
  dibuja bajo las barras del sistema y se come la toolbar.
- **`sourceCompatibility` debe quedarse en 1.8.** Los layouts del Sokoban
  usan `_` como nombre de variable y es palabra reservada desde Java 9:
  4722 errores si se sube.
- `android.util.FloatMath` se eliminó en el API 23. Parchado en tres
  archivos (`watabou/utils/PointF`, `effects/Flare`, `effects/Speck`)
  a `(float) Math.*`. Era un envoltorio de Math, no cambia nada.
- **Subir `versionCode` en cada build.** Android trata el mismo número
  como "ya instalado" y se queda con el viejo, en silencio.

Compilar:

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
ANDROID_HOME=/root/tools/android-sdk \
./gradlew :app:assembleDebug
```

# tools — off-device helpers

This machine has no KVM, so the Android emulator is unusable and the game
cannot be run here. These let the parts that matter be checked anyway.

## MatrixTest — camera maths

Verifies `com.watabou.glwrap.Matrix` perspective/rotate/translate and the
exact composition `Camera3D.updateMatrix()` uses. Nine checks: projection
centring, axis directions, points behind the camera, yaw/pitch signs, eye
offset, aspect handling.

```sh
javac -d /tmp/mt -sourcepath app/src/main/java tools/MatrixTest.java
java -cp /tmp/mt MatrixTest
```

## GridMeshTest — dungeon mesh builder

Eleven checks on `GridMesh`: quad counts for known grids, that fully
enclosed rock emits nothing, that adjacent open cells never duplicate a
shared face, that geometry lands on the grid, that no index points past
the vertex array, that UVs stay in the unit square, and that a 48x48
level fits under the 16-bit index limit.

```sh
javac -d /tmp/gm -sourcepath app/src/main/java tools/GridMeshTest.java
java -cp /tmp/gm GridMeshTest
```

## MeshFromLevel — the whole chain on a real dungeon

Generates a real level with the game's generator, classifies it with the
game's own `Terrain.SOLID` flags, and builds the mesh. Nothing synthetic.
Same classpath needs as DumpLevel.

Measured: 1600-2500 quads per level, 126-198 KB of vertices, built in
2-3 ms. Most wall faces are interior and never emitted.

## TilemapTest — the bridge from game map to mesh

Seven checks that `DungeonTilemap3D` agrees with the game rather than
guessing: it classifies with the game's own `Terrain.SOLID`, every open
cell gets at least a floor and a ceiling, cell-to-world coordinates match
the grid the mesh was built on, the entrance is somewhere a camera can
stand, and the geometry spans the whole level rather than a corner.

## ViewTest — camera and mesh in the same world

Catches the classic unit mismatch, where the camera is placed in cell
coordinates and the mesh is built in world coordinates. Puts the camera
exactly where `FirstPerson.update()` would — the entrance cell — and
projects the real mesh through the real projection at all four facings,
checking geometry is on screen each way and that the eye is not inside rock.

## DumpLevel — real dungeons as ASCII

Runs Sprouted's own level generator off-device and prints the terrain, so
the Godot spike can render real levels instead of a hand-written room.
Needs `shim/` (desktop stand-ins for android.util.SparseArray and Log)
ahead of android.jar on the classpath.

```sh
AJ=$ANDROID_HOME/platforms/android-35/android.jar
javac -source 8 -target 8 -cp "tools/shimclasses:$AJ" -d /tmp/dl \
      -sourcepath app/src/main/java tools/DumpLevel.java
java -cp "tools/shimclasses:/tmp/dl:$AJ" DumpLevel CavesLevel 12
```

Level classes that are known to generate: SewerLevel, PrisonLevel,
CavesLevel, CityLevel, HallsLevel, CatacombLevel, FortressLevel,
DragonCaveLevel.

## SceneOrderTest — the crash the other six could not see

v17 shipped a game that opened its menu and died on "play". The first
person hooks hid `GameScene.statuses` and `.spells` from a block that
sits *above* the lines building them; they are instance fields, so they
were null on every entry. javac does not flag it — definite-assignment
analysis covers locals, not fields — and the other six suites are pure
logic that never builds a scene.

This one lints the source of `create()`: every scene field dereferenced
there must have been assigned on an earlier line. Verified by putting the
v17 bug back and watching it fail.

```sh
javac -d /tmp/so tools/SceneOrderTest.java
java -cp /tmp/so SceneOrderTest      # run from the repo root
```
