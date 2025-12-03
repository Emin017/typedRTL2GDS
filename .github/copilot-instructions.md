# TypedRTL2GDS AI Coding Instructions

## Project Overview

TypedRTL2GDS is a **Scala 3** EDA (Electronic Design Automation) flow orchestrator that transforms RTL (Verilog) to GDSII through type-safe pipeline stages. The project uses **opaque types** to enforce file format constraints at compile time and **Cats Effect IO** for functional effect management.

### Core Architecture: Typed Flow Context Pattern

The flow uses a **context-carrying architecture** where each pipeline stage (`InitialContext` → `SynContext` → `PnrContext`) validates inputs and carries forward configuration. Each context:
- Extends `FlowContext` trait with `config` and `validate` methods
- Contains opaque types (`VerilogPath`, `DefPath`, `GdsPath`) preventing invalid file paths at compile time
- Nests previous context (e.g., `SynContext` contains `InitialContext`)

Example from `Main.scala`:
```scala
case class InitialContext(config: InputConfig, inputRtl: VerilogPath)
case class SynContext(initial: InitialContext, defFile: DefPath)
case class PnrContext(syn: SynContext, gdsFile: GdsPath)
```

### Opaque Types: File Path Safety

All file paths use **opaque types** with smart constructors that validate extensions:
- `VerilogPath.from(path)` - only accepts `.v` or `.sv`
- `DefPath.from(path)` - only accepts `.def`
- `GdsPath.from(path)` - only accepts `.gds`

These return `Either[String, T]` for error handling. Access the underlying string with `.value` extension method.

## Build System: Mill

**Commands** (use `mill` not `sbt`):
- `mill -i typedRTL2GDS.compile` - compile main sources
- `mill -i typedRTL2GDS.reformat` - auto-format with scalafmt
- `mill -i typedRTL2GDS.fix` - apply scalafix rules
- `mill -i typedRTL2GDS.test` - run ScalaTest suite
- `mill -i __.checkFormat` - verify formatting (CI check)
- `mill -i __.fix --check` - verify scalafix rules (CI check)

### Mill Build Configuration (`build.mill`)

- **Scala version**: 3.6.4 (not Scala 2)
- **Multi-module build**: `typedRTL2GDS` object extends `ScalaModule with ScalafmtModule with ScalafixModule`
- **Dependencies**: Cats Effect, Circe (YAML parsing), mainargs (CLI), JGit, Scalatags
- **Test module**: `test` object nested inside main module

## Code Style & Conventions

### Scalafix Rules (`.scalafix.conf`)
**Strict rules enforced** - no `var`, `null`, `return`, `asInstanceOf`, `throw` without wrapping:
- Use `IO` for effects, not imperative code
- Use `Either` for validation errors
- Use `IO.raiseError` instead of `throw`
- Organize imports automatically with `OrganizeImports`

### Functional Programming Patterns
- **Effect handling**: All side effects in `IO[A]` (Cats Effect)
- **Error handling**: `Either[String, A]` for validation, `IO.fromEither` to lift to `IO`
- **Resource management**: `Resource.make(acquire)(release).use { ... }` (see `loadConfig` for file reading)
- **No exceptions**: Use `IO.raiseError(new RuntimeException(...))` instead of `throw`
- **Avoid imperative style**: Minimize `if-else` statements; prefer pattern matching, `Either`, `Option`, and combinators
  - Use `Either.cond`, `Option.when`, `validateNel` for conditional logic
  - Chain operations with `flatMap`, `map`, `fold`, `orElse`
  - Use pattern matching over nested `if-else`
  - Leverage Cats syntax: `.ensure`, `.whenA`, `.raiseWhen`, `.flatTap`

Examples:
```scala
// ✅ Functional style - using Either.cond
Either.cond(
  path.endsWith(".v") || path.endsWith(".sv"),
  path,
  s"Invalid Verilog file: '$path'"
)

// ✅ Using IO.raiseWhen (from existing code)
IO.raiseWhen(exitCode != 0)(
  new RuntimeException(s"Yosys failed with exit code $exitCode")
)

// ✅ Pattern matching over if-else
config.designInfo.coreUtilization match {
  case u if u <= 0 || u >= 1.0 => Left("Core utilization must be between 0.0 and 1.0")
  case _ => initial.validate
}

// ❌ Avoid imperative if-else chains
if (condition1) {
  doThing1()
} else if (condition2) {
  doThing2()
} else {
  doThing3()
}
```

### Configuration Loading
- YAML configs parsed with **Circe YAML** (`io.circe.yaml.parser`)
- Auto-derivation with `import io.circe.generic.auto._`
- Case classes like `InputConfig` and `DesignInfo` define schema (see `test.yaml` for example)

## Development Environment

### Nix Flake Setup
Project uses **Nix flakes** for reproducible builds:
- `nix develop` - enter dev shell with Mill
- `nix flake check` - run formatting checks
- **treefmt-nix**: Auto-format Nix (`nixfmt`) and YAML (`yamlfmt`)

### Tool Versions
- Scala 3.6.4
- Mill 0.12.10 (`.mill-version`)
- scalafmt 3.7.15 (Scala 3 dialect)

## Testing

- **Framework**: ScalaTest 3.2.18
- **Location**: `typedRTL2GDS/test` module in Mill build
- **Run**: `mill -i typedRTL2GDS.test`

## Common Pitfalls

1. **Don't use Scala 2 syntax** - this is Scala 3 (no implicit, use given/using)
2. **File paths must be validated** - always use opaque type constructors (`VerilogPath.from(...)`)
3. **Don't use sbt** - this project uses Mill
4. **No imperative code** - wrap in `IO`, use for-comprehensions
5. **Shell commands use scala.sys.process** - see `runSynthesis` for `cmd.!` pattern

## Example: Adding a New Flow Stage

To add a new stage after PnR:
1. Define opaque type in `EDATypes` object (e.g., `LefPath`)
2. Create context case class extending `FlowContext`
3. Implement validation in `validate` method
4. Add stage function returning `IO[NewContext]`
5. Chain in `runFlow` using for-comprehension

When modifying code, **always follow the existing patterns** - opaque types for file paths, `IO` for effects, `Either` for validation, and context nesting for flow stages.
