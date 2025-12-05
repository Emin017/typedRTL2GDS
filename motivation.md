# TypedRTL2GDS: Type-Safe EDA Flow Orchestrator

A **type-safe RTL-to-GDSII flow orchestrator** written in **Scala 3**, featuring compile-time file format validation and functional effect management using **Cats Effect**.

## Core Strengths

### 1. **Compile-Time Type Safety**
Unlike Python-based flows that catch file format errors at runtime, TypedRTL2GDS uses **opaque types** to enforce file constraints **at compile time**:
```scala
// Impossible to pass wrong file type - caught by compiler
VerilogPath.from("design.v")    // ✅ Valid
VerilogPath.from("design.def")  // ❌ Compile error
```
**Advantage**: Eliminates entire categories of bugs before execution, we can ensure that only valid files are passed between stages.

### 2. **Functional & Immutable Pipeline**
- **Purely functional**: All effects wrapped in `IO` monad (Cats Effect)
- **Composable stages**: Each pipeline stage (Synthesis → Floorplan → Placement → CTS -> ...) is a type-safe function
- **No imperative pitfalls**: No hidden state mutation, no race conditions
- **Explicit error handling**: Using `Either[String, T]` and `IO.raiseError` instead of exceptions


### 3. **Context-Carrying Validation**
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

This design helps catch misconfigurations early and ensures that each stage receives exactly what it expects.
We use these types to carry both data and validation logic together, and this also helps type-safe flow control (next section).
In the future, We can also use this context-carrying mechanism to implement advanced features like checkpointing and stage re-execution in the future.


### 4. Type-Safe Flow Control
Thanks to Scala 3's powerful type system, we can enforce the correct execution order of EDA flow stages by using [typeclass](https://docs.scala-lang.org/scala3/book/ca-type-classes.html) abstractions.
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

### 5. FLow step abstraction and modularity (Easy to extend more stages)
- Each stage implements `FlowStep[In, Out]` trait
- New stages can be added by defining new context types and `given` instances
- Promotes code reuse and separation of concerns
- Each stage is independently testable and verifiable
- Each stage's verification logic is encapsulated within its context type (Type-Safe Validation)

### 6. Zero Runtime Surprise
- **JVM guarantees**: Deterministic object layout, no C segfaults
- **Memory safety**: Scala's type system prevents null pointer errors
- **Cross-platform**: Runs identically on Linux/macOS/Windows (Java guarantee)

**vs. RTL2GDS**:
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

---

## Future Enhancements

- [x] Route stage (to GDS)
- [ ] More parameterized flow configurations
- [ ] Parallel stage execution where possible
- [ ] Support more features from iEDA tools (such as marco placement and power grid insertion)
- [ ] DRC/LVS integration
- [ ] GUI dashboard for flow monitoring

