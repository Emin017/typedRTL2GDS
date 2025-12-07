# Backend Flow DSL

This document describes the envisioned features of a Domain-Specific Language (DSL) for defining backend design flows in EDA. The goal is to create a high-level, expressive language that allows designers to easily specify and manage complex backend processes.

We have implemented a prototype of this DSL in Scala 3, leveraging its strong type system and functional programming capabilities. The DSL is designed to be intuitive and user-friendly, enabling designers to focus on the design process rather than the intricacies of tool invocation. See [DSL.scala](../typedRTL2GDS/src/DSL.scala) for the implementation details.

## Example Usage
Here is an example of how the DSL can be used to define a backend flow for a chip design:

```scala
val myChipFlow = Design("GCD_Chip")
  .input(verilog = "src/gcd.v", sdc = "constraints.sdc")
  .synthesize(target = "sky130")
  .floorplan(utilization = 0.7, aspectRatio = 1.0)

// Run the flow
myChipFlow.run(outputDir = "output/gcd_chip")
```

## Future Enhancements

- [ ] More parameterized flow configurations
- [ ] Parallel stage execution where possible, e.g.,
```scala3
val explorationFlow = Design("GCD_DSE")
  .input(...)
  .synthesize(...)
  .explore("utilization_sweep") { baseState =>
    // Define a sweep over different utilization targets
    val utils = List(0.6, 0.7, 0.8)

    // Map over the utilization targets to create parallel branches
    utils.map { u =>
      baseState
        .floorplan(utilization = u)
        .place()
        .route()
        .reportMetrics("wns", "tns", "area")
    }
  }
```
- [ ] Support quality gates, such as:
```scala3
.place().check {
    metrics =>
      metrics.maxUtilization < 0.8 && metrics.totalWireLength < 1_000_000
}
```
- [ ] Tool Agnostic Backends: `flow.run(synthesizer = YosysTool, placer = DreamPlace, router = TritonRoute)`