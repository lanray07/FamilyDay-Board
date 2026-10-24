"""Audit, draft, review and compile public UI translations. Python 3.10+, stdlib only.

The optional service receives static application strings, never household data.
Protocol: POST {source_language,target_language,strings}; response {strings:{key:text}}.
Set FAMILYDAY_TRANSLATION_URL (HTTPS) and FAMILYDAY_TRANSLATION_KEY in the environment.
"""
import argparse
import json
import os
import re
import sys
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/res/values/strings.xml"
PRIORITY = ["en-GB", "en-US", "de", "fr", "es", "it", "pt-BR", "nl", "ja"]
SENSITIVE = re.compile(r"child|pin|privacy|gate|purchase|reward|consent|erase|delete|restore|sync", re.I)
PLACEHOLDERS = re.compile(r"%(?:\d+\$)?[a-zA-Z]")


def strings():
    result = {}
    for node in ET.parse(SOURCE).getroot():
        if node.tag == "string":
            result[node.attrib["name"]] = "".join(node.itertext())
        elif node.tag == "plurals":
            for item in node:
                result[node.attrib["name"] + "." + item.attrib["quantity"]] = "".join(item.itertext())
    return result


def validate(source, translated):
    errors = []
    for key, value in source.items():
        target = translated.get(key)
        if not isinstance(target, str) or not target.strip():
            errors.append(f"{key}: missing translation")
        elif sorted(PLACEHOLDERS.findall(value)) != sorted(PLACEHOLDERS.findall(target)):
            errors.append(f"{key}: placeholders differ")
        elif value.count("\\n") != target.count("\\n"):
            errors.append(f"{key}: line break markers differ")
    errors.extend(f"{key}: unknown source key" for key in translated.keys() - source.keys())
    return errors


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["audit", "extract", "draft", "compile"])
    parser.add_argument("--locale", choices=PRIORITY)
    args = parser.parse_args()
    source = strings()
    folder = ROOT / "localization"
    if args.command == "extract":
        folder.mkdir(exist_ok=True)
        (folder / "source.json").write_text(json.dumps(source, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"Extracted {len(source)} static UI strings")
        return
    if args.command == "audit":
        print(f"Source: {len(source)} English strings")
        for locale in PRIORITY:
            path = folder / f"{locale}.json"
            if locale.startswith("en"):
                print(f"{locale}: source fallback (regional wording review pending)")
            elif path.exists():
                data = json.loads(path.read_text(encoding="utf-8"))
                errors = validate(source, data["strings"])
                print(f"{locale}: {len(errors)} issues; human reviewed={data.get('human_reviewed', False)}")
            else:
                print(f"{locale}: {len(source)} missing; not advertised as translated")
        return
    if not args.locale:
        parser.error("--locale is required for draft and compile")
    path = folder / f"{args.locale}.json"
    if args.command == "draft":
        url = os.environ["FAMILYDAY_TRANSLATION_URL"]
        if not url.startswith("https://"):
            parser.error("Translation service must use HTTPS")
        request = urllib.request.Request(url, data=json.dumps({"source_language": "en-GB", "target_language": args.locale, "strings": source}).encode(),
                                         headers={"Content-Type": "application/json", "Authorization": "Bearer " + os.environ["FAMILYDAY_TRANSLATION_KEY"]}, method="POST")
        with urllib.request.urlopen(request, timeout=60) as response:
            data = json.loads(response.read(2_000_000))
        errors = validate(source, data["strings"])
        if errors:
            sys.exit("\n".join(errors))
        data.update({"locale": args.locale, "human_reviewed": False, "sensitive_review_keys": [key for key in source if SENSITIVE.search(key)]})
        folder.mkdir(exist_ok=True)
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"Draft saved to {path}; review all strings and sensitive_review_keys before compiling")
    else:
        data = json.loads(path.read_text(encoding="utf-8"))
        errors = validate(source, data["strings"])
        if errors:
            sys.exit("\n".join(errors))
        if not data.get("human_reviewed"):
            sys.exit("Human review must be recorded before shipping translations")
        if not data.get("sensitive_reviewed"):
            sys.exit("Sensitive child/security strings need a separate review")
        qualifier = {"pt-BR": "pt-rBR", "en-US": "en-rUS", "en-GB": "en-rGB"}.get(args.locale, args.locale)
        destination = ROOT / f"app/src/main/res/values-{qualifier}/strings.xml"
        root = ET.Element("resources")
        plurals = {}
        for key, value in data["strings"].items():
            # Android uses backslash escapes in resource text, independently of XML escaping.
            escaped = value.replace("'", "\\'").replace('"', '\\"')
            if "." in key:
                name, quantity = key.split(".", 1)
                if name not in plurals:
                    plurals[name] = ET.SubElement(root, "plurals", name=name)
                ET.SubElement(plurals[name], "item", quantity=quantity).text = escaped
            else:
                ET.SubElement(root, "string", name=key).text = escaped
        ET.indent(root)
        destination.parent.mkdir(exist_ok=True)
        ET.ElementTree(root).write(destination, encoding="utf-8", xml_declaration=True)
        print(f"Generated {destination}; check expansion, RTL and large-font layouts on a device")


if __name__ == "__main__":
    main()
