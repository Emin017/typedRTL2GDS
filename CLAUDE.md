# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

TypedRTL2GDS is a **Scala 3** EDA (Electronic Design Automation) flow orchestrator that transforms RTL (Verilog) to GDSII through type-safe pipeline stages. The project uses **opaque types** to enforce file format constraints at compile time and **Cats Effect IO** for functional effect management.

### Core Architecture: Typed Flow Context Pattern

The flow uses a **context-carrying architecture** where each pipeline stage (`InitialContext` → `SynContext` → `FloorplanContext` → `PlaceContext` → `CTSContext` → `LegalizationContext` → `RouteContext`) validates inputs and carries forward configuration. Each context:
- Extends `FlowContext` trait with `config` and `validate` methods
- Contains opaque types (`VerilogPath`, `DefPath`) preventing invalid file paths at compile time
- Nests previous context (e.g., `SynContext` contains `InitialContext`)

The flow is implemented in [`Flow.scala`](typedRTL2GDS/src/Flow.scala) with type-safe steps defined in [`FlowStep.scala`](typedRTL2GDS/src/FlowStep.scala).

## Build System: Mill

This project uses **Mill** as the build system, not sbt. All development commands use `mill`.

### Common Commands

**Development (requires Nix):**
```bash
nix develop --command mill typedRTL2GDS.compile
nix develop --command mill typedRTL2GDS.assembly  # generate assembly files
nix develop --command mill -i typedRTL2GDS.run --config typedRTL2GDS/test/example.yaml
```

**Build & Test:**
```bash
nix develop --command mill -i typedRTL2GDS.compile
nix develop --command mill -i typedRTL2GDS.test
nix develop --command mill -i typedRTL2GDS.reformat
nix develop --command mill -i typedRTL2GDS.fix
```

**CI Checks:**
```bash
nix develop --command mill -i __.checkFormat  # verify formatting
nix develop --command mill -i __.fix --check  # verify scalafix rules
nix build -L  # full Nix build
```

### Mill Build Configuration ([`build.mill`](build.mill))

- **Scala version**: 3.6.4 (not Scala 2)
- **Multi-module build**: `typedRTL2GDS` object extends `ScalaModule with ScalafmtModule with ScalafixModule`
- **Dependencies**: Cats Effect, Circe (YAML parsing), mainargs (CLI), JGit, Scalatags
- **Test module**: `test` object nested inside main module

## Environment Setup

### Nix Flake Setup

Project uses **Nix flakes** for reproducible development environments:
- `nix develop` - enter dev shell with Mill and all tools
- `nix flake check` - run formatting checks
- **treefmt-nix**: Auto-format Nix (`nixfmt`) and YAML (`yamlfmt`)

The Nix configuration is in [`flake.nix`](flake.nix) with support for Linux (x86_64/aarch64) and macOS (aarch64).

## Code Style & Conventions

### Functional Programming Patterns

- **Effect handling**: All side effects in `IO[A]` (Cats Effect)
- **Error handling**: `Either[String, A]` for validation, `IO.fromEither` to lift to `IO`
- **Resource management**: `Resource.make(acquire)(release).use { ... }` (see `loadConfig` for file reading)
- **No exceptions**: Use `IO.raiseError(new RuntimeException(...))` instead of `throw`
- **Avoid imperative style**: Minimize `if-else` statements; prefer pattern matching, `Either`, `Option`, and combinators

### Scalafix Rules

**Strict rules enforced** - no `var`, `null`, `return`, `asInstanceOf`, `throw` without wrapping:
- Use `IO` for effects, not imperative code
- Use `Either` for validation errors
- Use `IO.raiseError` instead of `throw`
- Organize imports automatically with `OrganizeImports`

### Opaque Types: File Path Safety

All file paths use **opaque types** with smart constructors that validate extensions (defined in [`EDATypes.scala`](typedRTL2GDS/src/types/EDATypes.scala)):
- `VerilogPath.from(path)` - only accepts `.v` or `.sv`
- `DefPath.from(path)` - only accepts `.def`

These return `Either[String, T]` for error handling. Access the underlying string with `.value` extension method.

## Configuration

The application is configured via YAML files. See [`typedRTL2GDS/test/example.yaml`](typedRTL2GDS/test/example.yaml) for the format. Configuration is loaded in [`Main.scala`](typedRTL2GDS/src/Main.scala) using Circe YAML parser.

Key configuration sections:
- `designName`, `rtlFile`, `resultDir` - basic design info
- `foundry` - PDK and technology file paths
- `designInfo` - clock settings, utilization, etc.

## Testing

- **Framework**: ScalaTest 3.2.18
- **Location**: `typedRTL2GDS/test/src/` directory
- **Run**: `nix develop --command mill -i typedRTL2GDS.test`

## EDA Tool Integration

The project orchestrates external EDA tools:
- **Yosys** for synthesis (see [`Yosys.scala`](typedRTL2GDS/src/Yosys.scala))
- **iEDA** for place & route flow (floorplanning, placement, CTS, legalization, routing)

Each flow step is defined as a `FlowStep` with environment variables and script paths.

## Additional Features

### Tcl DSL Architecture

The project includes a type-safe Tcl DSL implementation in the `dsl` package with a three-layer architecture:

#### Current Implementation
- [`TclTypes.scala`](typedRTL2GDS/src/dsl/TclTypes.scala) - core Tcl value types (TclNode, Set, AddCommand, IfStatement)
- [`TclApi.scala`](typedRTL2GDS/src/dsl/TclApi.scala) - high-level API (puts, set, addCommand)
- [`TclCompiler.scala`](typedRTL2GDS/src/dsl/TclCompiler.scala) - compiles AST to Tcl strings
- Supports if statements, variables, and operations with type safety
- Source location tracking via Scala 3 macros

#### Future Design: Scala3 → MLIR → TCL
The project is evolving toward a three-layer architecture:
1. **Scala3 Frontend** - Declarative DSL with type-safe variables
   ```scala
   val timeout = TclVar("timeout") := 10
   If(timeout === 10) {
     puts("Default timeout")
   }
   ```
2. **MLIR Middle Layer** - Semantic representation, SSA form, optimizations
3. **TCL Backend** - Simple execution layer for EDA tools

#### Design Philosophy
- **Scala3**: Responsible for expression and ergonomics
- **MLIR**: Responsible for semantics, optimization, and analysis
- **TCL**: Degraded to "target description language"

#### Planned API Improvements
- Declared variables: `val timeout = TclVar("timeout") := 10`
- Type-safe references: Use `timeout.value` instead of string references
- Natural operators: `:=` for assignment, `===` for comparison
- Composable scripts: Combine script fragments with `++`

## Common Pitfalls

1. **Don't use Scala 2 syntax** - this is Scala 3 (no implicit, use given/using)
2. **File paths must be validated** - always use opaque type constructors (`VerilogPath.from(...)`)
3. **Don't use sbt** - this project uses Mill
4. **No imperative code** - wrap in `IO`, use for-comprehensions
5. **Shell commands use scala.sys.process** - see `runSynthesis` for `cmd.!` pattern
6. **TCL DSL**: When working with the TCL DSL, remember it's a type-safe wrapper around TCL - use the DSL constructs, not raw strings