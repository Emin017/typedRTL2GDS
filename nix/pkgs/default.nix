{
  lib,
  stdenv,
  ivy-gather,
  mill,
  add-determinism,
  makeWrapper,
}:
stdenv.mkDerivation {
  name = "typedRTL2GDS";
  version = "unstable-2025-12-05";

  nativeBuildInputs = [
    makeWrapper
    mill
    mill.jre
    add-determinism
  ];

  buildInputs = [ (ivy-gather ./lock.nix) ];

  src =
    with lib.fileset;
    toSource {
      root = ./../..;
      fileset = unions [
        ./../../build.mill
        ./../../typedRTL2GDS
      ];
    };

  outputs = [ "out" ];

  buildPhase = ''
    mill --offline '__.assembly'
  '';

  installPhase = ''
    mkdir -p $out/share/java

    add-determinism -j $NIX_BUILD_CORES out/typedRTL2GDS/assembly.dest/out.jar

    mv out/typedRTL2GDS/assembly.dest/out.jar $out/share/java/typedRTL2GDS.jar

    mkdir -p $out/bin
    makeWrapper ${mill.jre}/bin/java $out/bin/rtl2gds \
      --add-flags "-jar $out/share/java/typedRTL2GDS.jar"
  '';
}
