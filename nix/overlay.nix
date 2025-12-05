final: prev: {
  mill =
    let
      jre = final.jdk21;
    in
    (prev.mill.override { inherit jre; }).overrideAttrs rec {
      version = "1.1.0-RC3";
      src = final.fetchurl {
        url = "https://repo1.maven.org/maven2/com/lihaoyi/mill-dist/${version}/mill-dist-${version}.exe";
        hash = "sha256-TmAN19M80CiiQHSTe4BrPkeicEwVNCYl5XgOfPXQwIY=";
      };
      propagatedBuildInputs = with prev; [ which ];
      doInstallCheck = false;
      passthru = { inherit jre; };
    };
}
