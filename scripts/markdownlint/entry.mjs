// Bundle entry: exposes what assets/lint.html needs as a global.
import { lint } from "markdownlint/sync";
import { applyFixes, getVersion } from "markdownlint";
import yaml from "js-yaml";
globalThis.markdownlint = { lint, applyFixes, getVersion, parseYaml: (s) => yaml.load(s) };
