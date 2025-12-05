{ pkgs, ... }:
with pkgs;
{
  devShells.default = mkShell {
    buildInputs =
      [
        mill
        yosys
      ]
      ++ lib.optionals (!stdenv.hostPlatform.isDarwin) [
        ieda
      ];
  };
}
