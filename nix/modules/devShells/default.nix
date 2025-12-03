{ pkgs, config, ... }:
{
  devShells.default = pkgs.mkShell {
    buildInputs = with pkgs; [
      mill
    ];
  };
}
