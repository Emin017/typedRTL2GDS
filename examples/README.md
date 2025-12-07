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