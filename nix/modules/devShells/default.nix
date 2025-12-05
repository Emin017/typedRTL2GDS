{ pkgs, config, ... }:
{
  devShells.default = pkgs.mkShell {
    buildInputs =
      with pkgs;
      [
        mill
        yosys
      ]
      ++ pkgs.lib.optionals (!pkgs.stdenv.hostPlatform.isDarwin) [
        iEDA
      ];
  };
}
