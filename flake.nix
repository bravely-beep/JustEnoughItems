{
  description = "JustEnoughItems (JEI) development environment";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-26.05";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs { inherit system; };
        jdk = pkgs.jdk21;
      in
      {
        devShells.default = pkgs.mkShell {
          packages = [
            jdk
            pkgs.git
            pkgs.gh
          ];
          JAVA_HOME = jdk.home;
        };
      });
}
