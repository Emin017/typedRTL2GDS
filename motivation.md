# TypedRTL2GDS: Type-Safe EDA Flow Orchestrator

A **type-safe RTL-to-GDSII flow orchestrator** written in **Scala 3**, featuring compile-time file format validation and functional effect management using **Cats Effect**.

## Core Strengths vs. Competitors

### 1. **Compile-Time Type Safety** 🔐
Unlike Python-based flows that catch file format errors at runtime, TypedRTL2GDS uses **opaque types** to enforce file constraints **at compile time**:
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


### 4. Type-Safe Flow Control 🚦
The execution order is enforced by the type system via the `FlowStep[In, Out]` trait, ensuring that stages execute in the correct sequence at **compile time**.

- **Enforced Execution Order**: The type parameters `In` and `Out` define a strict dependency graph.
  - You cannot run `Routing` immediately after `Synthesis` because no `FlowStep[SynContext, RouteContext]` instance exists.
  - The compiler prevents invalid stage skipping or reordering.
- **Implicit Step Resolution**: Scala 3's `given`/`using` mechanism automatically resolves the correct logic for transitioning between states (e.g., `SynContext` → `FloorplanContext`).
- **Self-Documenting Pipeline**: The code structure itself documents the flow.
  ```scala
  // From FlowStep.scala:
  given FlowStep[SynContext, FloorplanContext] with { ... }
  given FlowStep[FloorplanContext, PlaceContext] with { ... }
  // The compiler guarantees the pipeline follows this exact path
  ```

### 5. Zero Runtime Surprise ⚡
- **JVM guarantees**: Deterministic object layout, no C segfaults
- **Memory safety**: Scala's type system prevents null pointer errors
- **Cross-platform**: Runs identically on Linux/macOS/Windows (Java guarantee)

**vs. OpenLane/LibreGDS**:
- Python scripts → environment/version dependency hell
- Shell script layers → subtle platform differences

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

