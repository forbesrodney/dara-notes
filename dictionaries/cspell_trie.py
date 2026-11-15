#!/usr/bin/env python3
"""
Reads cSpell's compiled dictionaries (the .trie.gz files in its @cspell/dict-* packages)
into a list of words.

    python3 dictionaries/cspell_trie.py en_US.trie.gz      (prints the words)

The format is "TrieXv3": a trie written out depth first, one character at a time. A port of
importV3.ts in cSpell's cspell-trie-lib:

    $        end of a word, then up one level
    <        up one level; a digit n (2-9) after $ or < goes up n - 1 more
    #n;      this node is the same as node n (in base `base=`, counted as they were made)
    \\c      the character c, literally (\\n, \\r and \\\\ are newline, return and backslash)

Entries starting with ~ are cSpell's lowercase, accent-free forms; ! marks forbidden words;
+ marks parts only used in compound words.
"""
import gzip
import sys

# The node every word without children ends at; copied before anything is added under it.
_END = {"f": 1, "locked": True}


def read_trie(text):
    lines = text.split("\n")
    start = next((i for i, line in enumerate(lines) if "__DATA__" in line), -1)
    if start < 0:
        raise ValueError("not a cSpell trie")
    header = [line.strip() for line in lines[:start] if line.strip() and not line.strip().startswith("#")]
    if len(header) < 2 or header[0] != "TrieXv3" or not header[1].startswith("base="):
        raise ValueError("unknown trie format: " + " ".join(header[:2]))
    radix = int(header[1].split("=", 1)[1])

    root = {"c": {}}
    nodes = [root, _END]  # in the order they were made, for #n; references
    stack = [[root, ""]]  # (parent, character) for each level
    state = {"node": root, "depth": 0}

    def insert(ch):
        node, depth = state["node"], state["depth"]
        if node.get("locked"):
            parent, key = stack[depth]
            copy = {k: v for k, v in node.items() if k not in ("locked", "c")}
            if "c" in node:
                copy["c"] = dict(node["c"])
            parent["c"][key] = copy
            node = copy
            nodes.append(node)
        children = node.setdefault("c", {})
        child = children.setdefault(ch, {})
        nodes.append(child)
        depth += 1
        if depth < len(stack):
            stack[depth][0], stack[depth][1] = node, ch
        else:
            stack.append([node, ch])
        state["node"], state["depth"] = child, depth

    def end_word():
        node, depth = state["node"], state["depth"]
        if not node.get("c"):
            parent, key = stack[depth]
            parent["c"][key] = _END
            if nodes[-1] is node:
                nodes.pop()
            state["node"] = _END
        else:
            node["f"] = 1

    def reference(n):
        parent, key = stack[state["depth"]]
        parent["c"][key] = nodes[n]
        nodes.pop()

    def back(n):
        if not 0 < n <= state["depth"]:
            raise ValueError("bad trie: up past the root")
        state["depth"] -= n
        state["node"] = stack[state["depth"] + 1][0]

    data = "\n".join(lines[start + 1:])
    escapes = {"n": "\n", "r": "\r", "\\": "\\"}
    i, length, going_up = 0, len(data), False
    while i < length:
        c = data[i]
        if going_up:
            if c == "<" or c in "23456789":
                back(1 if c == "<" else int(c) - 1)
                i += 1
                continue
            going_up = False
        if c == "$":
            end_word()
            back(1)
            going_up = True
            i += 1
        elif c == "<":
            back(1)
            going_up = True
            i += 1
        elif c == "#":
            j = data.index(";", i)
            reference(int(data[i + 1:j], radix) + 1)
            i = j + 1
        elif c == "\\":
            nxt = data[i + 1]
            if nxt == "\\" and i + 2 < length and data[i + 2] in escapes:
                insert(escapes[data[i + 2]])
                i += 3
            else:
                insert(nxt)
                i += 2
        elif c in "\r\n":
            i += 1
        else:
            insert(c)
            i += 1

    words = []
    todo = [(root, "")]
    while todo:
        node, prefix = todo.pop()
        if node.get("f"):
            words.append(prefix)
        for ch, child in (node.get("c") or {}).items():
            todo.append((child, prefix + ch))
    return words


def plain_words(words):
    """The words a speller should accept: no lowercase copies (~), forbidden or compound-only ones."""
    return [w for w in words if w and w[0] not in "~!" and "+" not in w and " " not in w]


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    data = open(sys.argv[1], "rb").read()
    if sys.argv[1].endswith(".gz"):
        data = gzip.decompress(data)
    for word in sorted(read_trie(data.decode("utf-8"))):
        print(word)
