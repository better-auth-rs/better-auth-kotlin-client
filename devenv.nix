{ pkgs, ... }:

{
  languages.java = {
    enable = true;
    jdk.package = pkgs.jdk17;
    gradle.enable = true;
    lsp.enable = false;
  };

  scripts.check.exec = "./gradlew check";
  enterTest = "check";
}
