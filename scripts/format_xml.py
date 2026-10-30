#!/usr/bin/env python3
"""Format Android XML with four-space indents and one attribute per line.

Run from any directory: python3 scripts/format_xml.py
Only source XML is changed; generated files under build/ are left alone.
"""
from pathlib import Path
from xml.dom import Node, minidom

PROJECT = Path(__file__).resolve().parents[1]


def render(node, depth=0):
    indent = '    ' * depth
    if node.nodeType == Node.COMMENT_NODE:
        return indent + node.toxml()
    if node.nodeType != Node.ELEMENT_NODE:
        return indent + node.toxml()

    # Keep value text exactly as written (including Android string escapes).
    children = [child for child in node.childNodes
                if child.nodeType != Node.TEXT_NODE or child.data.strip()]
    if children and all(child.nodeType in (Node.TEXT_NODE, Node.CDATA_SECTION_NODE) for child in children):
        return indent + node.toxml()
    # Mixed-content strings can contain formatting tags; preserve their whitespace.
    if node.tagName in ('string', 'item') and any(child.nodeType == Node.TEXT_NODE and child.data.strip() for child in children):
        return indent + node.toxml()

    lines = [indent + '<' + node.tagName]
    for index in range(node.attributes.length):
        attribute = node.attributes.item(index)
        # Let the XML library escape attribute values without changing their meaning.
        holder = minidom.Document().createElement('v')
        holder.setAttribute(attribute.name, attribute.value)
        serialized = holder.toxml()[3:-2]
        lines.append(indent + '    ' + serialized)
    lines[-1] += '>' if children else ' />'
    for index, child in enumerate(children):
        # Separate controls visually, while keeping resource values compact.
        if index > 0 and child.nodeType == Node.ELEMENT_NODE and node.tagName not in ('resources', 'style', 'selector', 'vector', 'group'):
            lines.append('')
        lines.append(render(child, depth + 1))
    if children:
        lines.append(indent + '</' + node.tagName + '>')
    return '\n'.join(lines)


def signature(node):
    """Ignore formatting whitespace, but check names, values, comments and content."""
    if node.nodeType == Node.ELEMENT_NODE:
        attrs = sorted((node.attributes.item(i).name, node.attributes.item(i).value)
                       for i in range(node.attributes.length))
        return node.tagName, attrs, [signature(c) for c in node.childNodes
                                   if c.nodeType != Node.TEXT_NODE or c.data.strip()]
    return node.nodeType, node.nodeValue


def main():
    files = sorted((PROJECT / 'app/src/main/res').rglob('*.xml'))
    files.append(PROJECT / 'app/src/main/AndroidManifest.xml')
    changed = 0
    for path in files:
        original = path.read_text()
        document = minidom.parseString(original)
        formatted = '<?xml version="1.0" encoding="utf-8"?>\n' + '\n\n'.join(
            render(node) for node in document.childNodes
            if node.nodeType in (Node.ELEMENT_NODE, Node.COMMENT_NODE)) + '\n'
        assert signature(document.documentElement) == signature(minidom.parseString(formatted).documentElement), path
        if formatted != original:
            path.write_text(formatted)
            changed += 1
    print(f'Formatted {changed} source XML files; checked {len(files)} files.')


if __name__ == '__main__':
    main()
