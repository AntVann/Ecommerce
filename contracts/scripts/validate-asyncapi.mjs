import { readFile } from "node:fs/promises";
import asyncApiParser from "@asyncapi/parser";

const { Parser } = asyncApiParser;

const contractPath = new URL("../asyncapi/marketflow.yaml", import.meta.url);
const source = await readFile(contractPath, "utf8");

try {
  const parser = new Parser();
  const { document, diagnostics } = await parser.parse(source, {
    source: contractPath.pathname,
  });
  if (!document) {
    throw new Error(`AsyncAPI parser diagnostics: ${JSON.stringify(diagnostics)}`);
  }
  if (document.version() !== "3.0.0") {
    throw new Error(`Expected AsyncAPI 3.0.0, received ${document.version()}`);
  }
  console.log("AsyncAPI contract is valid: asyncapi/marketflow.yaml");
} catch (error) {
  console.error("AsyncAPI contract validation failed.");
  console.error(error);
  process.exitCode = 1;
}
