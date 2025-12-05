# TypedRTL2GDS

![main](https://github.com/Emin017/typedRTL2GDS/actions/workflows/ci.yml/badge.svg?branch=main)

TypedRTL2GDS is a type-safe EDA flow orchestrator implemented in Scala 3, designed to automate the transformation of RTL (Verilog) designs to GDSII by using Open Source EDA tools (iEDA, Yosys, etc.).
It is inspired by the original [RTL2GDS](https://github.com/0xharry/RTL2GDS) project but reimagined with a strong emphasis on leveraging Scala 3's advanced type system to ensure compile-time safety and maintainability.

> [!WARNING]
> This project is currently under prototype development. While the core functionalities are in place, users may encounter bugs or incomplete features. Contributions and feedback are welcome to help improve the project.
>
> The core flow has been successfully tested with the [ics55 PDK](https://github.com/openecos-projects/icsprout55-pdk).

## Getting Started

### Build

```shell
nix develop --command mill typedRTL2GDS.compile

# To generate assembly files
nix develop --command mill typedRTL2GDS.assembly 
```

### Run

```shell
nix develop -L --command mill -i typedRTL2GDS.run --config typedRTL2GDS/test/example.yaml
```
