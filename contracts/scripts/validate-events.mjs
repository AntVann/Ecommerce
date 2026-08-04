import { readFile } from "node:fs/promises";
import Ajv2020 from "ajv/dist/2020.js";
import addFormats from "ajv-formats";

const schemaPath = new URL("../events/event-envelope.v1.schema.json", import.meta.url);
const examplePath = new URL("../events/examples/foundation-event.json", import.meta.url);

const schema = JSON.parse(await readFile(schemaPath, "utf8"));
const example = JSON.parse(await readFile(examplePath, "utf8"));
const ajv = new Ajv2020({ allErrors: true, strict: true });
addFormats(ajv);

const validate = ajv.compile(schema);
if (!validate(example)) {
  console.error("Event contract validation failed.");
  console.error(JSON.stringify(validate.errors, null, 2));
  process.exitCode = 1;
} else {
  console.log("Event schema and example are valid: events/event-envelope.v1.schema.json");
}
