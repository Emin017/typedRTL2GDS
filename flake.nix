{
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
    parts.url = "github:hercules-ci/flake-parts";
    treefmt-nix.url = "github:numtide/treefmt-nix";
    treefmt-nix.inputs.nixpkgs.follows = "nixpkgs";
    mill-ivy-fetcher.url = "github:Avimitin/mill-ivy-fetcher";
  };

  outputs =
    inputs@{
      self,
      nixpkgs,
      parts,
      treefmt-nix,
      mill-ivy-fetcher,
      ...
    }:
    parts.lib.mkFlake { inherit inputs; } {
      imports = [
        treefmt-nix.flakeModule
      ];
      systems = [
        "x86_64-linux"
        "aarch64-linux"
        "aarch64-darwin"
      ];
      perSystem =
        {
          inputs',
          pkgs,
          system,
          ...
        }:
        let
          treefmtEval = treefmt-nix.lib.evalModule pkgs {
            projectRootFile = "build.mill";
            programs = {
              nixfmt.enable = true; # nix
              yamlfmt.enable = true; # yaml
            };
          };
          localOverlay = import ./nix/overlay.nix;
        in
        {
          _module.args.pkgs = import inputs.nixpkgs {
            inherit system;
            overlays = [
              mill-ivy-fetcher.overlays.mill-ivy-fetcher-overlay
              localOverlay
            ];
          };
          imports = [
            ./nix
          ];
          packages.default = pkgs.callPackage ./nix/pkgs { };
          formatter = treefmtEval.config.build.wrapper;
          checks = {
            formatting = treefmtEval.config.build.check self;
          };
        };
    };
}
