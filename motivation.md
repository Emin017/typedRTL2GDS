# TypedRTL2GDS: Type-Safe EDA Flow Orchestrator

A **type-safe RTL-to-GDSII flow orchestrator** written in **Scala 3**, featuring compile-time file format validation and functional effect management using **Cats Effect**.

## Core Strengths vs. Competitors (OpenLane, LibreGDS)

### 1. **Compile-Time Type Safety** 🔐
Unlike Python-based flows (OpenLane, LibreGDS) that catch file format errors at runtime, TypedRTL2GDS uses **opaque types** to enforce file constraints **at compile time**:
```scala
// Impossible to pass wrong file type - caught by compiler
VerilogPath.from("design.v")    // ✅ Valid
VerilogPath.from("design.def")  // ❌ Compile error
```
**Advantage**: Eliminates entire categories of bugs before execution.

### 2. **Functional & Immutable Pipeline** 🔄
- **Purely functional**: All effects wrapped in `IO` monad (Cats Effect)
- **Composable stages**: Each pipeline stage (Synthesis → Floorplan → Placement → CTS) is a type-safe function
- **No imperative pitfalls**: No hidden state mutation, no race conditions
- **Explicit error handling**: Using `Either[String, T]` and `IO.raiseError` instead of exceptions

**vs. OpenLane/LibreGDS**:
- Both rely on imperative Python scripts and shell execution
- State scattered across filesystem and environment variables
- Harder to reason about dataflow and dependencies

### 3. **Context-Carrying Validation** ✓
Each stage validates inputs via nested context objects:
```scala
case class InitialContext(config: InputConfig, inputRtl: VerilogPath)
  extends FlowContext { validate: Either[String, Unit] }

case class SynContext(initial: InitialContext, netlist: VerilogPath)
  extends FlowContext { validate: Either[String, Unit] }
```
- Guarantees **configuration continuity** across stages
- **No silent failures**: Validation happens explicitly before each stage
- Full **audit trail** through context nesting

### 4. **Backend-Agnostic Orchestration** 🎯
- **Tool-agnostic**: Yosys for synthesis, iEDA for place & route (easily swappable)
- **Configuration-driven**: Single YAML file controls entire flow
- **Environment isolation**: Each stage gets explicit environment variables via Scala, not shell pollution

**vs. OpenLane/LibreGDS**:
- These are tightly coupled to specific tool chains (OpenLane → Yosys-specific, LibreGDS → specific tools)
- Harder to adapt to different PDKs or tool combinations

### 5. **Production-Grade Tooling** 🛠
- **Mill build system**: Fast, incremental, reproducible
- **Nix flakes**: Bit-for-bit reproducible dev environment (no "works on my machine")
- **Scalafix + scalafmt**: Automated code quality, no linting overhead
- **ScalaTest**: Type-safe property-based testing

### 6. **Zero Runtime Surprise** ⚡
- **JVM guarantees**: Deterministic object layout, no C segfaults
- **Memory safety**: Scala's type system prevents null pointer errors
- **Cross-platform**: Runs identically on Linux/macOS/Windows (Java guarantee)

**vs. OpenLane/LibreGDS**:
- Python scripts → environment/version dependency hell
- Shell script layers → subtle platform differences

---

## Typical Usage

```bash
# Configure design
cat <<EOF > design.yaml
designName: gcd
rtlFile: ./gcd.v
designInfo:
  clkPortName: clk
  clkFreqMHz: 100
  coreUtilization: 0.7
foundry:
  name: ics55
  pdkDir: /path/to/pdk
EOF

# Run flow
java -jar typedRTL2GDS.jar -c design.yaml
```

**Output stages**:
1. Synthesis (Yosys) → `result_gcd/synthesis/gcd_nl.v`
2. Floorplanning (iEDA) → `.def`
3. Placement (iEDA) → `.def`
4. Clock Tree Synthesis (iEDA) → `.def` → (future: GDSII)

---

## Why Scala 3?

| Feature | Benefit |
|---------|---------|
| **Opaque types** | Enforce file formats at compile time |
| **Givens/Using** | Implicit context (like typeclass witnesses) without cognitive overhead |
| **Pattern matching** | Eliminates if-else spaghetti in EDA logic |
| **Union types** | Type-safe error handling without exceptions |
| **Enums** | Exhaustiveness checking for flow stages |

---

## Future Enhancements

- [ ] Route stage (to GDS)
- [ ] Multi-corner STA integration
- [ ] Power intent (UPF) support
- [ ] Hierarchical design decomposition
- [ ] Real-time DAG visualization

