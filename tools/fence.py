"""Import fence for common/ (docs/MULTIVERSION.md, slice 5), after MyCompanion's tools/fence.py.

    python tools/fence.py <tree>           check against <tree>/common/fence.tsv
    python tools/fence.py <tree> --write   rewrite fence.tsv with today's counts (only after a drop)
    ./gradlew :common:fence                    the same check, also run by :common:check

Counts, per file under common/src/main/java/dev/dryaides/minuit, the distinct Minecraft names it
uses: net.minecraft and com.mojang imports, plus the fully qualified ones written in code (comments
and strings skipped). No folder is free: mixin/ and port/mc/ live in the port dirs. The port
variant folders (common/src/port<dir>/java) are not part of that count.
Fails if a file goes over its row or a file with no row has any Minecraft name.

src/main stays Java 17 source, because common compiles at Java 25 in this build: no Math.clamp,
getFirst/getLast/removeFirst/removeLast, guarded or record patterns in case, unnamed _.

Variant dirs (common/src/port<dir>/java): fails when a variant file names no Minecraft type (except a
main_overrides stand-in), when two version dirs of
one generation (port_dirs in gradle/versions/*.properties) hold a file with the same code, or when
a class sits in both the generation dir and a version dir of one port_dirs list.
"""
import glob, os, re, sys

FREE = ()
IMPORT = re.compile(r'^import\s+(?:static\s+)?((?:net\.minecraft|com\.mojang)\.[\w.*]+)\s*;', re.M)
FQN = re.compile(r'\b((?:net\.minecraft|com\.mojang)\.(?:[a-z_]\w*\.)+[A-Z]\w*)')
COMMENT = re.compile(r'//[^\n]*|/\*.*?\*/|"(?:\.|[^"\\n])*"', re.S)
# Comments, text blocks, strings and char literals, for the checks added after fence.tsv.
NOT_CODE = re.compile(r'//[^\n]*|/\*.*?\*/|""".*?"""|"(?:\\.|[^"\\\n])*"|\'(?:\\.|[^\'\\\n])+\'', re.S)
COMMENTS = re.compile(r'//[^\n]*|/\*.*?\*/', re.S)
JAVA17 = [
    (re.compile(r'\bMath\.clamp\s*\('), 'Math.clamp (Java 21), use internal.Maths.clamp'),
    (re.compile(r'\.(?:getFirst|getLast|removeFirst|removeLast)\s*\(\s*\)'), 'SequencedCollection call (Java 21)'),
    (re.compile(r'\bcase\b[^:;]*?\bwhen\b'), 'guarded case pattern (Java 21)'),
    (re.compile(r'\bcase\s+[A-Z]\w*(?:<[^>]*>)?\s*\('), 'record pattern in case (Java 21)'),
    (re.compile(r'(?<![\w$])_(?![\w$])'), 'unnamed _ (Java 22)'),
]
# Not yet Java 17 and leaving src/main for port26x (SEPARATION.md section 9): empty since lot 3;
# FREE folders empty since lot 6. Only goes down.
JAVA17_LATER = ()


def counts(tree):
    src = os.path.join(tree, 'common', 'src', 'main', 'java', 'dev', 'dryaides', 'minuit')
    out = {}
    for dp, _, fs in os.walk(src):
        for f in fs:
            if not f.endswith('.java'):
                continue
            rel = os.path.relpath(os.path.join(dp, f), src).replace(os.sep, '/')
            if rel.startswith(FREE):
                continue
            with open(os.path.join(dp, f), encoding='utf-8') as h:
                text = h.read()
            names = set(IMPORT.findall(text))
            names |= set(FQN.findall(COMMENT.sub(' ', IMPORT.sub('', text))))
            if names:
                out[rel[:-5]] = len(names)
    return out


def blank(m):
    return re.sub(r'[^\n]', ' ', m.group())


def java17(tree):
    src = os.path.join(tree, 'common', 'src', 'main', 'java')
    bad = []
    for dp, _, fs in os.walk(src):
        for f in fs:
            if not f.endswith('.java'):
                continue
            path = os.path.join(dp, f)
            rel = os.path.relpath(path, os.path.join(src, 'dev', 'dryaides', 'minuit')).replace(os.sep, '/')
            if rel.startswith(FREE) or rel in JAVA17_LATER:
                continue
            with open(path, encoding='utf-8') as h:
                code = NOT_CODE.sub(blank, h.read())
            for rx, why in JAVA17:
                for m in rx.finditer(code):
                    bad.append(f'common/src/main/java/.../{rel}:{code.count(chr(10), 0, m.start()) + 1}: {why}')
    return bad


def port_lists(tree):
    lists = []
    for p in sorted(glob.glob(os.path.join(tree, 'gradle', 'versions', '*.properties'))):
        with open(p, encoding='utf-8') as h:
            for line in h:
                if line.startswith('port_dirs='):
                    lists.append([d.strip() for d in line.split('=', 1)[1].split(',')])
    return lists


def variant_files(tree):
    """{dir suffix: {class path: text}} for every common/src/port<dir>/java."""
    out = {}
    for d in sorted(glob.glob(os.path.join(tree, 'common', 'src', 'port*'))):
        java = os.path.join(d, 'java')
        files = {}
        for dp, _, fs in os.walk(java):
            for f in fs:
                if f.endswith('.java'):
                    rel = os.path.relpath(os.path.join(dp, f), java).replace(os.sep, '/')
                    with open(os.path.join(dp, f), encoding='utf-8') as h:
                        files[rel] = h.read()
        out[os.path.basename(d)[4:]] = files
    return out


def code_lines(text):
    return [l.rstrip() for l in COMMENTS.sub('', text).split('\n') if l.strip()]


def port_check(tree):
    dirs = variant_files(tree)
    bad = []
    # A version dir's stand-in for a src/main class (main_overrides) may name no Minecraft type.
    overrides = set()
    vdir = os.path.join(tree, 'gradle', 'versions')
    for f in os.listdir(vdir):
        with open(os.path.join(vdir, f), encoding='utf-8') as h:
            for line in h:
                if line.startswith('main_overrides='):
                    overrides.update(x.strip() for x in line.split('=', 1)[1].split(',') if x.strip())
    for d, files in dirs.items():
        for rel, text in sorted(files.items()):
            if rel not in overrides and not IMPORT.search(text) and not FQN.search(NOT_CODE.sub(' ', text)):
                bad.append(f'port{d}/{rel}: names no Minecraft type, belongs in core or src/main')
    gens = {}
    for lst in port_lists(tree):
        gens.setdefault(lst[0], set()).update(lst[1:])
        for v in lst[1:]:
            for rel in sorted(set(dirs.get(lst[0], {})) & set(dirs.get(v, {}))):
                bad.append(f'{rel}: in both port{lst[0]} and port{v} (port_dirs={",".join(lst)})')
    for g, vs in sorted(gens.items()):
        vs = sorted(vs)
        for i, a in enumerate(vs):
            for b in vs[i + 1:]:
                for rel in sorted(set(dirs.get(a, {})) & set(dirs.get(b, {}))):
                    if code_lines(dirs[a][rel]) == code_lines(dirs[b][rel]):
                        bad.append(f'{rel}: same code in port{a} and port{b}, belongs in port{g}')
    lines = {d: sum(len(code_lines(t)) for t in fs.values()) for d, fs in dirs.items()}
    return bad, lines


def port_fence(tree):
    bad, now = port_check(tree)
    for b in bad:
        print(f'port-fence: {b}')
    print(f'port-fence: {len(now)} dirs, {sum(now.values())} code lines, ' + ('FAIL' if bad else 'ok'))
    return 1 if bad else 0


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    tree = sys.argv[1]
    write = '--write' in sys.argv[2:]
    j17 = java17(tree)
    for b in j17:
        print(f'java17: {b}')
    print(f'java17: {len(j17)} problems in common/src/main' + (' FAIL' if j17 else ' ok'))
    port = port_fence(tree)
    tsv = os.path.join(tree, 'common', 'fence.tsv')
    now = counts(tree)
    if write:
        with open(tsv, 'w', encoding='utf-8', newline='\n') as h:
            h.write('# file\tallowed Minecraft names (imports and FQN classes); only goes down\n')
            for k in sorted(now):
                h.write(f'{k}\t{now[k]}\n')
        print(f'fence: wrote {len(now)} files, {sum(now.values())} names')
        return 1 if j17 or port else 0
    allowed = {}
    with open(tsv, encoding='utf-8') as h:
        for line in h:
            if line.strip() and not line.startswith('#'):
                k, v = line.rstrip('\n').split('\t')
                allowed[k] = int(v)
    bad = [(k, n, allowed.get(k, 0)) for k, n in sorted(now.items()) if n > allowed.get(k, 0)]
    for k, n, a in bad:
        print(f'fence: {k} has {n} Minecraft names, allowed {a}')
    drops = sum(1 for k, a in allowed.items() if now.get(k, 0) < a)
    print(f'fence: {len(now)} files, {sum(now.values())} names, ceiling {sum(allowed.values())}'
          + (f', {drops} rows can go down (--write)' if drops else '')
          + (' FAIL' if bad else ' ok'))
    return 1 if bad or j17 or port else 0


if __name__ == '__main__':
    sys.exit(main())
