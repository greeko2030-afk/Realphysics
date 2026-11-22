# Coverage of the requested physics list
Legend: DONE = implemented in v1.0.0, PARTIAL = approximated, TODO = possible later
(needs custom blocks/items/rendering), NO = cannot be simulated meaningfully in Minecraft.

## Mechanics
- Newton 1/2/3, Inertia, Momentum, Impulse ..... DONE (CombatPhysics, MassUtil)
- Law of Universal Gravitation ................. DONE (altitude gravity)
- Kinetic energy / Work-Energy ................. PARTIAL (fall damage vs mass, falling blocks hurt)
- Potential energy / Conservation of energy .... PARTIAL (falling blocks)
- Friction (static/kinetic/rolling) ............ PARTIAL (surface friction; rolling friction TODO)
- Air resistance / Terminal velocity / Free fall DONE
- Projectile motion ............................ TODO
- Circular / Centripetal / Centrifugal / Rotational / Torque / Angular momentum ... TODO (minecarts, boats)
- SHM / Oscillation ............................ PARTIAL (bobbing in water)
- Resonance .................................... PARTIAL (glass shatters from sonic boom)
- Hooke's law / Elasticity / Stress & strain ... PARTIAL (cantilever stress limit by hardness)
- Centre of mass / gravity ..................... TODO
- Lever / Pulley / Inclined plane / Mech. adv. . TODO (custom blocks)

## Fluid mechanics
- Archimedes / Buoyancy ........................ DONE (items sink or float, heavy armor sinks)
- Continuity / Venturi / Bernoulli ............. PARTIAL (narrow channel flows faster)
- Hydrostatic pressure ......................... DONE (deep water)
- Drag / Viscosity ............................. DONE (quadratic fluid drag)
- Pascal / Hydraulic ........................... TODO
- Atmospheric pressure, Surface tension, Capillary, Laminar/Turbulent ... NO / TODO

## Thermodynamics
- Heat transfer: conduction / convection / radiation ... DONE (conduction via armor, radiation 1/r^2)
- Thermal equilibrium, Specific heat ........... DONE (Newton cooling, water exchanges faster)
- Latent heat, Melting, Freezing, Boiling ...... DONE
- Evaporation .................................. DONE (config evaporateSources, default off)
- Condensation / Sublimation ................... TODO
- Greenhouse effect ............................ DONE (glass roof + sun)
- Thermal expansion, Blackbody, 0th-3rd laws ... PARTIAL / NO

## Optics
- Lens principle ............................... DONE (spyglass / glass bottle ignite)
- Reflection, Refraction, Snell, TIR, Dispersion, Diffraction, Interference, Polarization,
  Scattering, Rainbow, Mirage, Laser, Photoelectric ... TODO (needs client rendering / shaders)

## Electricity and magnetism
- Electric discharge / Current / Resistance / Ohm's law ... DONE (lightning through metal + water)
- Magnetism / Magnetic field ................... DONE (lodestone pulls iron)
- Coulomb, Kirchhoff, Capacitance, Induction, Faraday, Lenz, Ampere, Lorentz, Eddy, Hall,
  Transformer, Motor, Generator, Wireless power ... TODO (needs redstone / custom blocks)

## Waves and sound
- Sonic boom ................................... DONE
- Sound waves, Echo, Doppler, Reverberation, Absorption, Beats, Standing waves ... TODO (client audio engine)

## Modern physics, Astronomy, Applied
- Relativity, Quantum, Nuclear, Radioactivity, Black holes, Dark matter, Gravitational waves,
  CMB, Redshift ... NO (no meaningful Minecraft equivalent; radioactivity/fission TODO with custom blocks)
- Orbital motion, Kepler, Tides, Escape velocity ... TODO
- Solar radiation .............................. DONE (part of temperature model)
- Refrigerator / AC / Heat pump / Solar cell / Diode / Transistor / LED / GPS / Radar / Sonar /
  Piezo / Seebeck ... TODO (custom blocks and items)
