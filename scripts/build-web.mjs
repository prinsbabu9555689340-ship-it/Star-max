// Copies the static placeholder page into dist/ for Netlify to publish.
import { cpSync, mkdirSync, rmSync } from "node:fs";

rmSync("dist", { recursive: true, force: true });
mkdirSync("dist", { recursive: true });
cpSync("web", "dist", { recursive: true });
cpSync("app/src/main/res/drawable/ic_starmax_mark.jpg", "dist/logo.jpg");

console.log("Built placeholder site into dist/");
