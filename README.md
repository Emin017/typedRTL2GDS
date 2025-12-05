# TypedRTL2GDS

![main](https://github.com/Emin017/typedRTL2GDS/actions/workflows/ci.yml/badge.svg?branch=main)

Another implementation of [RTL2GDS](https://github.com/0xharry/RTL2GDS) in Scala3, built with strong type safety and modularity in mind.

See [Motivation](motivation.md) for more details.

```shell
nix develop --command mill typedRTL2GDS.compile

nix develop -L --command mill -i typedRTL2GDS.run --config typedRTL2GDS/test/example.yaml
```
