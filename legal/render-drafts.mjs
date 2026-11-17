// Local review-page generation only. No Firebase calls, uploads, or app builds.
// Markdown drafts remain the only source of policy text. Run: node legal/render-drafts.mjs
import { mkdir, readFile, writeFile } from "node:fs/promises";

const pages = [
  { source: "privacy-policy.draft.md", output: "privacy-policy.html", title: "Kanako Privacy Policy" },
  { source: "account-deletion.draft.md", output: "account-deletion.html", title: "Delete your Kanako account" },
];
const directory = new URL("./site-draft/", import.meta.url);
const escape = value => value.replaceAll("&", "&amp;").replaceAll("<", "&lt;")
  .replaceAll(">", "&gt;").replaceAll('"', "&quot;").replaceAll("'", "&#39;");

function link(destination) {
  const local = pages.find(page => page.source === destination);
  if (local) return local.output;
  if (/^(https:\/\/|mailto:)/.test(destination)) return destination;
  throw new Error("Unsupported policy link: " + destination);
}

function inline(value) {
  const tokens = /\[([^\]]+)\]\(([^)\s]+)\)|\*\*([^*]+)\*\*|`([^`]+)`/g;
  let result = "";
  let cursor = 0;
  for (const token of value.matchAll(tokens)) {
    result += escape(value.slice(cursor, token.index));
    if (token[1] !== undefined)
      result += '<a href="' + escape(link(token[2])) + '">' + escape(token[1]) + "</a>";
    else if (token[3] !== undefined) result += "<strong>" + inline(token[3]) + "</strong>";
    else result += "<code>" + escape(token[4]) + "</code>";
    cursor = token.index + token[0].length;
  }
  return (result + escape(value.slice(cursor))).replace(/ {2}\n/g, "<br>\n");
}

function markdown(text) {
  const blocks = [];
  let paragraph = [];
  let list = null;
  const flush = () => {
    if (paragraph.length) { blocks.push("<p>" + inline(paragraph.join("\n")) + "</p>"); paragraph = []; }
  };
  const closeList = () => { if (list) { blocks.push("</" + list + ">"); list = null; } };
  for (const line of text.replaceAll("\r\n", "\n").split("\n")) {
    if (!line.trim()) { flush(); closeList(); continue; }
    const heading = /^(#{1,3}) (.+)$/.exec(line);
    const item = /^(?:([-*])|([0-9]+)\.) (.+)$/.exec(line);
    if (heading) {
      flush(); closeList();
      const tag = "h" + heading[1].length;
      blocks.push("<" + tag + ">" + inline(heading[2]) + "</" + tag + ">");
    } else if (line.startsWith("> ")) {
      flush(); closeList();
      blocks.push("<aside>" + inline(line.slice(2)) + "</aside>");
    } else if (item) {
      flush();
      const next = item[1] ? "ul" : "ol";
      if (list !== next) { closeList(); blocks.push("<" + next + ">"); list = next; }
      blocks.push("<li>" + inline(item[3]) + "</li>");
    } else { closeList(); paragraph.push(line); }
  }
  flush(); closeList();
  return blocks.join("\n");
}

function document(title, content) {
  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex, nofollow">
<title>${escape(title)} — review draft</title>
<style>
:root { color-scheme: light; font-family: system-ui, sans-serif; line-height: 1.65; color: #202020; background: #fafafa; }
* { box-sizing: border-box; }
body { margin: 0; }
a { color: inherit; text-underline-offset: .2em; overflow-wrap: anywhere; }
a:focus-visible { outline: 3px solid currentColor; outline-offset: 4px; }
.skip { position: absolute; left: 1rem; top: -5rem; background: white; padding: .75rem; }
.skip:focus { top: 1rem; }
header, main, footer { max-width: 52rem; margin-inline: auto; padding: 1.25rem; }
nav { display: flex; flex-wrap: wrap; gap: .5rem 1.5rem; }
nav a { display: inline-block; padding-block: .5rem; }
main { background: white; overflow-wrap: anywhere; }
h1 { font-size: clamp(1.65rem, 5vw, 2.3rem); line-height: 1.2; }
h2, h3 { line-height: 1.35; margin-top: 2rem; }
aside, .draft { border: 1px solid #888; padding: 1rem; border-radius: .5rem; background: #f3f3f3; }
li + li { margin-top: .6rem; }
code { font-family: ui-monospace, monospace; }
footer { color: #505050; }
</style>
</head>
<body>
<a class="skip" href="#content">Skip to content</a>
<header>
<strong>Kanako · Samoyed Dev Studio</strong>
<nav aria-label="Policy pages">
<a href="index.html">Overview</a>
<a href="privacy-policy.html">Privacy policy</a>
<a href="account-deletion.html">Account deletion</a>
</nav>
<p class="draft"><strong>Review draft — not published.</strong> Retention periods are approved; cloud configuration and final verification are incomplete. This is not the live policy.</p>
</header>
<main id="content" tabindex="-1">
${content}
</main>
<footer>Questions: <a href="mailto:smyddevstudio@gmail.com">smyddevstudio@gmail.com</a></footer>
</body>
</html>
`;
}

const rendered = [];
for (const page of pages) {
  const text = await readFile(new URL(page.source, import.meta.url), "utf8");
  if (!text.startsWith("# ") || !text.includes("> Not published."))
    throw new Error("Expected an unpublished policy draft: " + page.source);
  rendered.push({ name: page.output, html: document(page.title, markdown(text)) });
}
const overview = '<h1>Kanako policy drafts</h1><p>Review the intended-release privacy and account-deletion information. These pages are not ready for publication or Google Play submission.</p><ul><li><a href="privacy-policy.html">Privacy policy draft</a></li><li><a href="account-deletion.html">Account-deletion draft</a></li></ul>';
rendered.push({ name: "index.html", html: document("Kanako policy drafts", overview) });
await mkdir(directory, { recursive: true });
for (const page of rendered) await writeFile(new URL(page.name, directory), page.html, "utf8");
console.log("Generated three local review pages in legal/site-draft/. No deployment performed.");
