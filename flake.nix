{
  description = "Jcmdshell - A cross-platform command-line shell built in pure Java, designed around the WORE principle (Write Once, Run Everywhere).";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs =
    {
      self,
      nixpkgs,
      flake-utils,
    }:
    flake-utils.lib.eachDefaultSystem (
      system:
      let
        pkgs = nixpkgs.legacyPackages.${system};
        jcmdshell = pkgs.maven.buildMavenPackage rec {
          pname = "jcmdshell";
          version = "0.1.0";

          src = ./.;

          nativeBuildInputs = [ pkgs.makeWrapper ];
          buildInputs = [ pkgs.jdk25 ];
          mvnJdk = pkgs.jdk25;
          mvnHash = "sha256-J7Nkc4MMQm6h32h9/8ik0Tzv7EQkYJorCa23I0Z8xsY=";

          installPhase = ''
            runHook preInstall

            mkdir -p $out/bin $out/share/jcmdshell
            install -Dm644 target/Jcmdshell-fat.jar $out/share/jcmdshell/jcmdshell.jar

            makeWrapper ${pkgs.jdk25}/bin/java $out/bin/jcmdshell \
              --add-flags "--enable-native-access=ALL-UNNAMED" \
              --add-flags "-jar $out/share/jcmdshell/jcmdshell.jar"

            runHook postInstall
          '';

          meta = with pkgs.lib; {
            description = "Cross-platform command-line shell built in pure Java, designed around the WORE principle (Write Once, Run Everywhere).";
            homepage = "https://github.com/StackPancakes/Jcmdshell";
            changelog = "https://github.com/StackPancakes/Jcmdshell/releases/tag/${version}";
            license = licenses.mit;
            maintainers = [ ];
            platforms = platforms.all;
            mainProgram = "jcmdshell";
          };
        };
      in
      {
        packages = {
          default = jcmdshell;
          jcmdshell = jcmdshell;
        };

        devShells.default = pkgs.mkShell {
          buildInputs = with pkgs; [
            jdk25 maven
          ];
        };
        apps.default = {
          type = "app";
          program = "${jcmdshell}/bin/jcmdshell";
          description = jcmdshell.meta.description;
          homepage = jcmdshell.meta.homepage;
          license = jcmdshell.meta.license;
          category = "Development";
        };
      }
    );
}
