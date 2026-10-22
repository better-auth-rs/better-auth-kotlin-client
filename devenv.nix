{ pkgs, ... }:

{
  packages = [ pkgs.actionlint pkgs.redis pkgs.valkey ];

  languages.java = {
    enable = true;
    jdk.package = pkgs.jdk17;
    gradle.enable = true;
    lsp.enable = false;
  };

  languages.javascript = {
    enable = true;
    package = pkgs.nodejs_24;
  };

  scripts.check.exec = "actionlint && ./gradlew check";
  enterTest = "check";
}
