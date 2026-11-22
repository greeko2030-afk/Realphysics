# RealPhysics (Fabric 1.21 / 1.21.1)

Mojang mappings, Fabric API 0.116.7+1.21.1, Java 21.

## Build
1. Install JDK 21 + Gradle 8.14+
2. In this folder: `gradle wrapper --gradle-version 8.14`
3. `./gradlew build`  (Windows: `gradlew.bat build`)
4. Take `build/libs/realphysics-1.0.0.jar` -> `mods/` (needs Fabric Loader + Fabric API)

Dev test: `./gradlew runClient`

If a version is not found, check https://fabricmc.net/develop and edit gradle.properties.

## Config
`config/realphysics.properties` is generated on first start. Every module has an on/off switch.

## Modules (package com.realphysics)
| File | Physics |
|---|---|
| MotionPhysics | gravitation vs altitude, air drag / terminal velocity, friction, buoyancy (Archimedes), fluid drag, Venturi flow, simple harmonic bobbing |
| MassUtil / MassPhysics | mass of body+armor+inventory, load slows you (F=ma), heavier = more fall damage (kinetic energy) |
| CombatPhysics | Newton 1/2/3, momentum, impulse, recoil |
| StructuralPhysics | gravity for floating blocks, stress / cantilever collapse, progressive collapse |
| FluidPhysics | hydrostatic pressure, boiling/scalding |
| ThermalPhysics | temperature, radiation (1/r^2), conduction, greenhouse effect, Newton's cooling, heat capacity |
| ChangeOfStatePhysics | melting, freezing, latent heat, evaporation |
| ElectricPhysics | lightning discharge through metal + water, Ohm's law |
| MagnetPhysics | lodestone pulls iron items (1/r^2) |
| WavePhysics | sonic boom, resonance (glass shatters) |
| OpticsPhysics | lens focusing sunlight ignites flammable blocks |

See PHYSICS_COVERAGE.md for your full list.
