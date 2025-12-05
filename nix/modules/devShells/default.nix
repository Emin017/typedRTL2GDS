{ pkgs, ... }:
with pkgs;
{
  devShells.default = mkShell {
    buildInputs = [
      mill
      mill-ivy-fetcher
    ]
    ++ lib.optionals (!stdenv.hostPlatform.isDarwin) [
      ieda
      yosys
    ];
  };
}
