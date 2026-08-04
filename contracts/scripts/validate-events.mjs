import { readdir, readFile } from "node:fs/promises";
import Ajv2020 from "ajv/dist/2020.js";
import addFormats from "ajv-formats";

const schemaPath = new URL("../events/event-envelope.v1.schema.json", import.meta.url);
const schema = JSON.parse(await readFile(schemaPath, "utf8"));
const ajv = new Ajv2020({ allErrors: true, strict: true });
addFormats(ajv);

const validate = ajv.compile(schema);
const examplesDirectory = new URL("../events/examples/", import.meta.url);
for (const file of (await readdir(examplesDirectory)).filter((name) => name.endsWith(".json"))) {
  const example = JSON.parse(await readFile(new URL(file, examplesDirectory), "utf8"));
  if (!validate(example)) {
    console.error(`Event envelope validation failed: events/examples/${file}`);
    console.error(JSON.stringify(validate.errors, null, 2));
    process.exitCode = 1;
  }
}
if (!process.exitCode) console.log("All event examples satisfy the shared envelope.");
